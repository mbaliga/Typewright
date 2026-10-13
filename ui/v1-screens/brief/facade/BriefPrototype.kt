// SPDX-License-Identifier: FSL-1.1-ALv2

@file:OptIn(ExperimentalJsExport::class, ExperimentalEncodingApi::class)

package com.asoc.typewright.brief.prototype

import com.asoc.typewright.brief.AskPosition
import com.asoc.typewright.brief.BriefModel
import com.asoc.typewright.brief.Cue
import com.asoc.typewright.brief.Dimension
import com.asoc.typewright.brief.Door
import com.asoc.typewright.brief.Drawing
import com.asoc.typewright.brief.FEELINGS
import com.asoc.typewright.brief.FontView
import com.asoc.typewright.brief.GENRES
import com.asoc.typewright.brief.GenreGroup
import com.asoc.typewright.brief.GuideKind
import com.asoc.typewright.brief.Level
import com.asoc.typewright.brief.Source
import com.asoc.typewright.brief.Step
import com.asoc.typewright.brief.Tension
import com.asoc.typewright.brief.USES
import com.asoc.typewright.brief.WrapUp
import com.asoc.typewright.brief.answering
import com.asoc.typewright.brief.askPosition
import com.asoc.typewright.brief.cueFaceOf
import com.asoc.typewright.brief.describeFace
import com.asoc.typewright.brief.facesLike
import com.asoc.typewright.brief.guideFor
import com.asoc.typewright.brief.needsConfirm
import com.asoc.typewright.brief.offersNotSure
import com.asoc.typewright.brief.planFor
import com.asoc.typewright.brief.previousStepId
import com.asoc.typewright.brief.prompt
import com.asoc.typewright.brief.questionFor
import com.asoc.typewright.brief.reopening
import com.asoc.typewright.brief.resolveBrief
import com.asoc.typewright.brief.searchFaces
import com.asoc.typewright.brief.skipping
import com.asoc.typewright.brief.useById
import com.asoc.typewright.brief.why
import com.asoc.typewright.brief.withFeelingToggled
import com.asoc.typewright.brief.withGenre
import com.asoc.typewright.brief.withLevel
import com.asoc.typewright.brief.withMeasuredLevels
import com.asoc.typewright.brief.withReferenceToggled
import com.asoc.typewright.brief.withSignatureToggled
import com.asoc.typewright.brief.withUseOption
import com.asoc.typewright.brief.withUseToggled
import com.asoc.typewright.brief.withWordmark
import com.asoc.typewright.brief.wrapUp
import com.asoc.typewright.core.font.sfnt.readSfntFont
import com.asoc.typewright.project.Ethos
import com.asoc.typewright.qa.corpus.StyleAtlas
import com.asoc.typewright.qa.corpus.decodeStyleAtlas
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/*
 * The Brief's design prototype runs the real engine, compiled to JavaScript. This file is the
 * thin layer between them: it holds what the app's BriefScreenUiState holds (the answers, the
 * door, the tab, a revisited question) and hands the page one JSON view of the current screen,
 * built from the engine's own words. The page only draws it and sends actions back.
 */

private var atlas: StyleAtlas? = null
private var example: Drawing = Drawing.EMPTY
private var exampleOn = false
private var ethos = Ethos()
private var model: BriefModel = resolveBrief(ethos, null)
private var door: Door? = null
private var tab = "ask"
private var refining = false
private var focus: Dimension? = null
private var query = ""
private var handoff = false

private val drawing: Drawing get() = if (exampleOn) example else Drawing.EMPTY

/** Loads the style atlas and the example letters (a TTF as base64), then returns the first view. */
@JsExport
fun start(
    atlasJson: String,
    exampleFontBase64: String,
): String {
    atlas = decodeStyleAtlas(atlasJson).let { StyleAtlas(it) }
    val font = readSfntFont(Base64.decode(exampleFontBase64))
    val chars = ('a'..'z') + ('A'..'Z') + ('0'..'9')
    example =
        Drawing.of(FontView(font.head.unitsPerEm, chars.mapNotNull { c -> font.glyphForCodePoint(c.code)?.let { c to it } }.toMap()))
    commit(Ethos())
    return view()
}

/** Applies one action (JSON: `{"type": …}`) and returns the new view. */
@JsExport
fun act(actionJson: String): String {
    val a: dynamic = JSON.parse(actionJson)
    val type = a.type as String

    fun s(key: String): String = a[key].toString()
    when (type) {
        "tab" -> {
            tab = s("id")
            handoff = false
        }

        "door" -> {
            door = Door.byId(a.id as String?)
            refining = false
            focus = null
            query = ""
        }

        "genre" -> {
            commit(ethos.withGenre(s("key")))
        }

        "use" -> {
            commit(ethos.withUseToggled(s("key")))
        }

        "useOption" -> {
            val use = useById(s("use")) ?: return view()
            val question = use.questions.firstOrNull { it.id == s("question") } ?: return view()
            commit(ethos.withUseOption(use, question, s("key")))
        }

        "feeling" -> {
            commit(ethos.withFeelingToggled(s("key")))
        }

        "reference" -> {
            commit(ethos.withReferenceToggled(s("key")))
        }

        "level" -> {
            val dimension = Dimension.byId(s("dim")) ?: return view()
            val level = Level.of(dimension, s("key"))
            if (focus == dimension) focus = null
            commit(ethos.withLevel(dimension, level))
        }

        "adopt" -> {
            commit(
                ethos.withMeasuredLevels(
                    atlas
                        ?.let {
                            com.asoc.typewright.brief
                                .levelsOf(drawing.measurement, it)
                        }.orEmpty(),
                ),
            )
        }

        "wordmark" -> {
            commit(ethos.withWordmark(s("text")))
        }

        "signature" -> {
            Dimension.byId(s("dim"))?.let { commit(ethos.withSignatureToggled(it)) }
        }

        "confirm" -> {
            position()?.step?.let { commit(ethos.answering(it.id)) }
        }

        "skip" -> {
            val step = position()?.step ?: return view()
            if (step is Step.AskDimension && step.dimension == focus) focus = null
            commit(ethos.skipping(step.id))
        }

        "back" -> {
            goBack()
        }

        "askMore" -> {
            refining = true
            focus = null
            tab = "ask"
        }

        "askAbout" -> {
            focus = Dimension.byId(s("dim"))
            tab = "ask"
            handoff = false
        }

        "leaveFocus" -> {
            focus = null
        }

        "startOver" -> {
            door = null
            refining = false
            focus = null
            tab = "ask"
            exampleOn = false
            handoff = false
            commit(Ethos())
        }

        "query" -> {
            query = s("text")
        }

        "example" -> {
            exampleOn = a.on == true
            handoff = false
        }

        "startDrawing" -> {
            handoff = true
        }

        "closeHandoff" -> {
            handoff = false
        }
    }
    return view()
}

private fun commit(next: Ethos) {
    ethos = next
    model = resolveBrief(next, atlas)
}

private fun position(): AskPosition? {
    val revisit = focus
    val chosen = door ?: return revisit?.let { AskPosition(questionFor(it, model), 0, 0, focused = true) }
    return askPosition(chosen, model, drawing, refining, revisit)
}

private fun goBack() {
    when {
        handoff -> {
            handoff = false
        }

        focus != null -> {
            focus = null
        }

        tab != "ask" -> {
            tab = "ask"
        }

        else -> {
            val chosen = door ?: return
            val last = previousStepId(chosen, model, drawing, refining)
            if (last == null) door = null else commit(ethos.reopening(last))
        }
    }
}

// ---- the view -----------------------------------------------------------------------------

private fun obj(vararg pairs: Pair<String, Any?>): dynamic {
    val o: dynamic = js("({})")
    for ((k, v) in pairs) o[k] = v
    return o
}

private fun arr(items: List<Any?>): Array<Any?> = items.toTypedArray()

private fun cue(c: Cue?): dynamic = c?.let { obj("face" to it.face.key, "text" to it.text, "tabular" to it.tabular) }

private fun source(s: Source?): String? = s?.let { "${it.kind.label} · ${it.citation}" }

private fun option(
    key: String,
    title: String,
    detail: String,
    cue: Cue?,
    chosen: Boolean,
    extra: String? = null,
): dynamic = obj("key" to key, "title" to title, "detail" to detail, "cue" to cue(cue), "chosen" to chosen, "extra" to extra)

private fun tension(t: Tension): dynamic = tensionCard(t.title, t.body, t.source, t.signature, t.dimensions.firstOrNull())

private fun tensionCard(
    title: String,
    body: String,
    src: Source?,
    signature: Dimension?,
    dimension: Dimension?,
): dynamic =
    obj(
        "title" to title,
        "body" to body,
        "source" to source(src),
        "signature" to signature?.id,
        "kept" to (signature != null && signature in model.signatures),
        "dimension" to dimension?.id,
        "answered" to (dimension != null && model.answer(dimension) != null),
    )

/** The current screen, as JSON. */
@JsExport
fun view(): String {
    val v =
        obj(
            "tab" to tab,
            "handoff" to if (handoff) handoffView() else null,
            "genreFace" to
                model.genre
                    ?.cue
                    ?.face
                    ?.key,
            "exampleOn" to exampleOn,
            "empty" to ethos.isEmpty,
            "ask" to askView(),
            "brief" to briefView(),
            "guide" to guideView(),
        )
    return JSON.stringify(v)
}

private fun handoffView(): dynamic {
    val first = planFor(model).firstOrNull { it.glyphs.isNotEmpty() && it.remaining(drawing.font).isNotEmpty() }
    return obj("glyphs" to first?.glyphs?.joinToString(" "), "title" to first?.title)
}

private fun askView(): dynamic {
    val p = position() ?: return obj("mode" to "doors", "doors" to arr(doorsView()), "carryOver" to !ethos.isEmpty)
    val header =
        obj(
            "focused" to p.focused,
            "door" to door?.label,
            "progress" to if (p.step == null) "${p.total} asked" else "question ${p.answered + 1}",
        )
    val step = p.step ?: return obj("mode" to "wrap", "header" to header, "wrap" to door?.let { wrapView(wrapUp(it, model, drawing)) })
    val buttons =
        obj(
            "back" to (!p.focused && p.answered > 0),
            "done" to step.needsConfirm,
            "notSure" to (!p.focused && step.offersNotSure),
            "skip" to !p.focused,
            "keep" to p.focused,
        )
    return obj("mode" to "step", "header" to header, "step" to stepView(step), "buttons" to buttons)
}

private fun doorsView(): List<dynamic> =
    Door.entries.map {
        obj(
            "id" to it.id,
            "label" to it.label,
            "caption" to it.caption,
            "cue" to cue(it.cue),
        )
    }

private fun wrapView(w: WrapUp): dynamic {
    val targets = if (w.targets == 1) "1 target" else "${w.targets} targets"
    val settle =
        when (w.toSettle.size) {
            0 -> "nothing to settle"
            1 -> "1 thing to settle"
            else -> "${w.toSettle.size} things to settle"
        }
    val first = w.drawFirst
    return obj(
        "sentence" to w.sentence,
        "summary" to "$targets · $settle",
        "toSettle" to arr(w.toSettle.take(3).map { tension(it) }),
        "moreInBrief" to (w.toSettle.size - 3).coerceAtLeast(0),
        "drawFirst" to first?.let { obj("glyphs" to it.glyphs.joinToString(" "), "title" to it.title, "why" to it.why) },
        "drawLabel" to if (drawing.font.glyphs.isEmpty()) "Draw first" else "Draw next",
        "more" to w.moreQuestions,
    )
}

private fun stepView(step: Step): dynamic {
    val base = obj("id" to step.id, "prompt" to step.prompt, "why" to step.why)
    when (step) {
        is Step.PickGenre -> {
            val chosen = model.genre?.key
            val groups = mutableListOf<dynamic>()
            if (step.suggestions.isNotEmpty()) {
                groups.add(
                    obj(
                        "label" to "Suggested",
                        "options" to
                            arr(
                                step.suggestions.map {
                                    option(
                                        it.genre.key,
                                        it.genre.name,
                                        it.reason,
                                        it.genre.cue,
                                        chosen == it.genre.key,
                                    )
                                },
                            ),
                    ),
                )
            }
            for (group in GenreGroup.entries) {
                val genres = GENRES.filter { it.group == group }
                if (genres.isEmpty()) continue
                groups.add(
                    obj(
                        "label" to group.title,
                        "options" to arr(genres.map { option(it.key, it.name, it.oneLine, it.cue, chosen == it.key) }),
                    ),
                )
            }
            base.kind = "genre"
            base.groups = arr(groups)
        }

        Step.PickUses -> {
            base.kind = "uses"
            base.options = arr(USES.map { option(it.id, it.name, it.oneLine, it.cue, it.id in ethos.uses) })
        }

        is Step.AskUse -> {
            val chosen = ethos.useOptions["${step.use.id}.${step.question.id}"]
            base.kind = "useOption"
            base.section = "For ${step.use.forWhat}"
            base.use = step.use.id
            base.question = step.question.id
            base.options = arr(step.question.options.map { option(it.id, it.label, it.caption, null, chosen == it.id) })
        }

        Step.EnterWordmark -> {
            base.kind = "wordmark"
            base.value = ethos.wordmark.orEmpty()
        }

        is Step.PickFeelings -> {
            base.kind = "feelings"
            base.count = "${ethos.feelings.size} of 3 chosen"
            base.options = arr(FEELINGS.map { option(it.key, it.word, it.gloss, null, it.key in ethos.feelings) })
        }

        is Step.PickReferences -> {
            val a = atlas
            val searching = query.isNotBlank()
            val found = if (a == null || !searching) emptyList() else searchFaces(a, query, limit = 8)
            val shown =
                when {
                    searching -> found
                    step.suggestions.isNotEmpty() -> step.suggestions.map { it.family }
                    else -> step.starters
                }.filter { it !in ethos.references }
            base.kind = "references"
            base.count = "${ethos.references.size} of 3 chosen"
            base.query = query
            base.placeholder = "Search ${a?.pack?.faces?.size ?: 0} measured faces by name"
            base.label =
                when {
                    searching && found.isEmpty() -> "No measured face by that name"
                    searching -> "Found"
                    step.suggestions.isNotEmpty() -> "Fit your brief so far"
                    else -> "Well-known faces to start from"
                }
            base.chosen = arr(ethos.references.map { referenceOption(it) })
            base.options = arr(shown.map { referenceOption(it) })
        }

        is Step.ReadDrawing -> {
            val reading = step.reading
            base.kind = "mirror"
            base.nothingDrawn = drawing.font.glyphs.isEmpty()
            base.toDraw = step.toDraw.toList().joinToString(" ")
            base.reading = arr(reading?.genres.orEmpty().map { "${it.genre.name}: ${it.votes} of the ${reading?.k} nearest faces" })
            base.levels = arr(step.levels.map { (d, l) -> option(d.id, d.title, l.label, l.cue, false) })
        }

        is Step.AskDimension -> {
            val chosen = model.answer(step.dimension)
            val genreName = model.genre?.name
            base.kind = "dimension"
            base.dim = step.dimension.id
            base.options =
                arr(
                    step.options.map { level ->
                        val count = step.genreCounts[level] ?: 0
                        val notes =
                            listOfNotNull(
                                if (level == step.suggested) suggestedNote(step) else null,
                                if (step.genreTotal > 0 &&
                                    genreName != null
                                ) {
                                    "$count of ${step.genreTotal} $genreName faces measured"
                                } else {
                                    null
                                },
                            ).joinToString(" · ")
                        option(level.id, level.label, level.phrase, level.cue, chosen == level, notes.ifEmpty { null })
                    },
                )
        }
    }
    return base
}

private fun suggestedNote(step: Step.AskDimension): String =
    step.suggestedBy?.let { "suggested by ${it.kind.word}: ${it.label}" } ?: "suggested"

private fun referenceOption(family: String): dynamic {
    val a = atlas
    val detail =
        a
            ?.pack
            ?.faces
            ?.get(family)
            ?.let { describeFace(it, a) }
            .orEmpty()
    return option(family, family, detail, cueFaceOf(family)?.let { Cue(it, "Aa") }, family in ethos.references)
}

private fun briefView(): dynamic {
    val order = Dimension.entries.flatMap { it.features }
    val targets =
        model.targets.values.sortedBy { order.indexOf(it.feature) }.map { t ->
            val evidence =
                when {
                    t.origin.faces > 0 -> "${t.origin.faces} faces measured"
                    t.range != null -> "our heuristic"
                    else -> null
                }
            obj(
                "feature" to t.feature.label,
                "target" to t.describe(),
                "origin" to "${t.origin.kind.word} · ${t.origin.label}",
                "evidence" to evidence,
            )
        }
    val a = atlas
    val faces =
        facesLike(model).map { m ->
            obj(
                "family" to m.family,
                "detail" to (
                    a
                        ?.pack
                        ?.faces
                        ?.get(m.family)
                        ?.let { describeFace(it, a) }
                        .orEmpty()
                ),
                "misses" to if (m.misses.isEmpty()) null else "misses: " + m.misses.joinToString(", ") { it.label },
                "cue" to cue(cueFaceOf(m.family)?.let { Cue(it, "Aa") }),
            )
        }
    return obj(
        "sentence" to model.sentence,
        "empty" to model.isEmpty,
        "note" to "Prototype: answers live in this page only",
        "targets" to arr(targets),
        "tensions" to arr(model.tensions.map { tension(it) }),
        "faces" to arr(faces),
    )
}

private fun guideView(): dynamic {
    val items = guideFor(model, drawing)
    val groups =
        GuideKind.entries.mapNotNull { kind ->
            val ofKind = items.filter { it.kind == kind }
            if (ofKind.isEmpty()) return@mapNotNull null
            obj(
                "word" to kind.word,
                "kind" to kind.name,
                "items" to
                    arr(
                        ofKind.map { item ->
                            val card =
                                if (kind == GuideKind.TENSION) {
                                    tensionCard(item.title, item.body, item.source, item.signature, item.dimension)
                                } else {
                                    null
                                }
                            val action =
                                when {
                                    kind == GuideKind.NEXT -> "draw"
                                    kind == GuideKind.DRIFT && item.dimension != null -> "change"
                                    kind == GuideKind.LEARN -> "learn"
                                    else -> null
                                }
                            obj(
                                "title" to item.title,
                                "body" to item.body,
                                "glyphs" to item.glyphs,
                                "check" to item.check?.status?.word,
                                "source" to source(item.source),
                                "action" to action,
                                "dim" to item.dimension?.id,
                                "tension" to card,
                            )
                        },
                    ),
            )
        }
    val font = drawing.font
    val plan =
        planFor(model).map { step ->
            val remaining = step.remaining(font)
            val status =
                when {
                    step.glyphs.isEmpty() -> "action"
                    remaining.isEmpty() -> "all drawn"
                    else -> "${remaining.size} of ${step.glyphs.size} still to draw"
                }
            obj(
                "title" to step.title,
                "glyphs" to step.glyphs.joinToString(" "),
                "status" to status,
                "why" to step.why,
                "source" to source(step.source),
            )
        }
    return obj("noDrawing" to font.glyphs.isEmpty(), "groups" to arr(groups), "plan" to arr(plan))
}
