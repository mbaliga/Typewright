// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.brief

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.asoc.typewright.brief.BriefModel
import com.asoc.typewright.brief.Dimension
import com.asoc.typewright.brief.Door
import com.asoc.typewright.brief.Drawing
import com.asoc.typewright.brief.FontView
import com.asoc.typewright.brief.Level
import com.asoc.typewright.brief.Step
import com.asoc.typewright.brief.UseCase
import com.asoc.typewright.brief.UseQuestion
import com.asoc.typewright.brief.answering
import com.asoc.typewright.brief.questionsFor
import com.asoc.typewright.brief.reopening
import com.asoc.typewright.brief.resolveBrief
import com.asoc.typewright.brief.skipping
import com.asoc.typewright.brief.withFeelingToggled
import com.asoc.typewright.brief.withGenre
import com.asoc.typewright.brief.withLevel
import com.asoc.typewright.brief.withMeasuredLevels
import com.asoc.typewright.brief.withReferenceToggled
import com.asoc.typewright.brief.withSignatureToggled
import com.asoc.typewright.brief.withUseOption
import com.asoc.typewright.brief.withUseToggled
import com.asoc.typewright.brief.withWordmark
import com.asoc.typewright.project.EditResult
import com.asoc.typewright.project.Ethos
import com.asoc.typewright.project.FontState
import com.asoc.typewright.project.MetaChange
import com.asoc.typewright.project.ProjectSession
import com.asoc.typewright.qa.corpus.StyleAtlas
import com.asoc.typewright.qa.corpus.loadStyleAtlas
import com.asoc.typewright.ui.project.LocalProjectWorkspace

/** The Brief screen's three tabs, left to right: the questions, what they add up to, and what to do about it. */
enum class BriefScreenTab(
    val label: String,
) {
    ASK("Ask"),
    BRIEF("Brief"),
    GUIDE("Guide"),
    ;

    companion object {
        /** Every tab, left to right. */
        val ORDERED: List<BriefScreenTab> = entries.toList()
    }
}

/** The line under the screen title. */
const val BRIEF_SCREEN_SUBTITLE: String = "ask · brief · guide"

/** Said whenever the answers live only in this session (CLAUDE.md law 4: a stub says so). */
const val BRIEF_NOT_SAVED_NOTE: String = "Not saved: no project is open"

/** Said when the answers went into the open project. */
const val BRIEF_SAVED_NOTE: String = "Saved in the project"

/** Said when the open project would not take the answers. */
const val BRIEF_READ_ONLY_NOTE: String = "Not saved: the project is read-only"

/** Said where the style atlas cannot load, so no range is measured (CLAUDE.md law 5). */
const val BRIEF_ATLAS_UNAVAILABLE_NOTE: String =
    "Style atlas unavailable on this platform: questions work, measured ranges don't"

/** Said by the guide when there are no letters to measure. */
const val BRIEF_NO_DRAWING_NOTE: String = "Draw some letters and the guide will measure them"

/**
 * Where the person is in a door's questions: the [step] still open (null when all are through),
 * and how many of the [total] have an answer or a skip.
 */
data class AskPosition(
    val step: Step?,
    val answered: Int,
    val total: Int,
)

/**
 * The Brief screen's state: the person's [ethos], what it resolves to ([model]), their own
 * letters ([drawing]), and which tab and door are showing. A plain class over Compose state, so a
 * test builds one and drives it with the same actions the screen's buttons call.
 *
 * Every change goes through one place, which resolves the model again and hands the new ethos to
 * [persist]. With no [persist] there is no open project: the answers stay in memory and
 * [saveNote] says so. [atlas] is null where the style atlas cannot load; the questions still work,
 * the measured ranges are absent.
 */
class BriefScreenUiState(
    initialEthos: Ethos = Ethos(),
    val atlas: StyleAtlas? = null,
    initialDrawing: Drawing = Drawing.EMPTY,
    initialTab: BriefScreenTab = BriefScreenTab.ASK,
    initialDoor: Door? = null,
    private val persist: ((Ethos) -> Boolean)? = null,
) {
    var tab: BriefScreenTab by mutableStateOf(initialTab)
        private set

    /** The door the person went in by, or null while they are still choosing one. */
    var door: Door? by mutableStateOf(initialDoor)
        private set

    var ethos: Ethos by mutableStateOf(initialEthos)
        private set

    /** What [ethos] and [atlas] resolve to, recomputed on every change. */
    var model: BriefModel by mutableStateOf(resolveBrief(initialEthos, atlas))
        private set

    /** The person's own letters, or [Drawing.EMPTY] with no project font. */
    var drawing: Drawing by mutableStateOf(initialDrawing)
        private set

    /** Whether the last change reached a project, in words. */
    var saveNote: String by mutableStateOf(if (persist == null) BRIEF_NOT_SAVED_NOTE else BRIEF_SAVED_NOTE)
        private set

    /** True when answers are written to an open project. */
    val saved: Boolean get() = persist != null

    /** Shows [tab]. */
    fun selectTab(tab: BriefScreenTab) {
        this.tab = tab
    }

    /** Goes in by [door]; null goes back to choosing one. */
    fun chooseDoor(door: Door?) {
        this.door = door
    }

    /** Replaces the letters the guide measures. */
    fun updateDrawing(drawing: Drawing) {
        if (drawing != this.drawing) this.drawing = drawing
    }

    /** The first open question of the chosen door, and how far through it the person is; null while no door is chosen. */
    fun askPosition(): AskPosition? {
        val chosen = door ?: return null
        val steps = questionsFor(chosen, model, drawing)
        val done = ethos.answered.toSet()
        return AskPosition(steps.firstOrNull { it.id !in done }, steps.count { it.id in done }, steps.size)
    }

    /** Sets the genre (null clears it). */
    fun chooseGenre(key: String?) = commit(ethos.withGenre(key))

    /** Adds or removes a use. */
    fun toggleUse(id: String) = commit(ethos.withUseToggled(id))

    /** Answers one of a use's own questions. */
    fun answerUse(
        use: UseCase,
        question: UseQuestion,
        optionId: String,
    ) = commit(ethos.withUseOption(use, question, optionId))

    /** Adds or removes a feeling; a fourth is refused. */
    fun toggleFeeling(key: String) = commit(ethos.withFeelingToggled(key))

    /** Adds or removes a reference face; a fourth is refused. */
    fun toggleReference(family: String) = commit(ethos.withReferenceToggled(family))

    /** Answers [dimension] with [level], or clears it. */
    fun answerLevel(
        dimension: Dimension,
        level: Level?,
    ) = commit(ethos.withLevel(dimension, level))

    /** Adopts what the person's own letters measure, keeping any answer already given. */
    fun adoptMeasured(measured: Map<Dimension, Level>) = commit(ethos.withMeasuredLevels(measured))

    /** Sets the wordmark's letters (blank clears them). */
    fun setWordmark(text: String?) = commit(ethos.withWordmark(text))

    /** Marks [dimension] as a deliberate departure from the genre, or unmarks it. */
    fun toggleSignature(dimension: Dimension) = commit(ethos.withSignatureToggled(dimension))

    /** Moves on from [step] with what is chosen so far (for the multiple-choice steps, which have no single answer). */
    fun confirm(step: Step) = commit(ethos.answering(step.id))

    /** Moves on from [step] without answering it ("I'm not sure"). */
    fun skip(step: Step) = commit(ethos.skipping(step.id))

    /** Reopens the last question the chosen door has had an answer or a skip for, so it is asked again; the answers stay. */
    fun goBack() {
        val chosen = door ?: return
        val done = ethos.answered.toSet()
        val last = questionsFor(chosen, model, drawing).lastOrNull { it.id in done } ?: return
        commit(ethos.reopening(last.id))
    }

    private fun commit(next: Ethos) {
        if (next == ethos) return
        ethos = next
        model = resolveBrief(next, atlas)
        val save = persist
        saveNote =
            when {
                save == null -> BRIEF_NOT_SAVED_NOTE
                save(next) -> BRIEF_SAVED_NOTE
                else -> BRIEF_READ_ONLY_NOTE
            }
    }
}

/**
 * The person's own letters as the Brief reads them: the default master's glyphs, each keyed by its
 * first code point. [Drawing.EMPTY] when there is no font or nothing in it has a code point in the
 * Basic Multilingual Plane. Measuring reads every style letter, so call this once per change of
 * the font, not per frame.
 */
fun briefDrawingOf(font: FontState?): Drawing {
    val master = font?.masters?.firstOrNull { it.isDefault } ?: font?.masters?.firstOrNull() ?: return Drawing.EMPTY
    val glyphs =
        master.ufo.glyphs
            .mapNotNull { glyph ->
                glyph.unicodes
                    .firstOrNull()
                    ?.takeIf { it in 0..MAX_BMP_CODE_POINT }
                    ?.let { it.toChar() to glyph }
            }.toMap()
    if (glyphs.isEmpty()) return Drawing.EMPTY
    return Drawing.of(FontView(master.ufo.fontInfo.unitsPerEm ?: DEFAULT_UNITS_PER_EM, glyphs))
}

private const val MAX_BMP_CODE_POINT = 0xFFFF
private const val DEFAULT_UNITS_PER_EM = 1000

/** The style atlas, or null on a target that cannot load it (the browser build today); never an empty atlas. */
fun loadBriefAtlas(): StyleAtlas? = runCatching { loadStyleAtlas() }.getOrNull()

/** Loads the style atlas once per composition. */
@Composable
fun rememberBriefAtlas(): StyleAtlas? = remember { loadBriefAtlas() }

/**
 * Remembers a [BriefScreenUiState] for the open project (re-made when the project changes): its
 * answers are read from the project's brief and written back with every change. With no project
 * open the answers stay in memory. The letters follow the project's font as it is drawn.
 */
@Composable
fun rememberBriefScreenUiState(atlas: StyleAtlas? = rememberBriefAtlas()): BriefScreenUiState {
    val session: ProjectSession? =
        LocalProjectWorkspace.current
            ?.current
            ?.collectAsState()
            ?.value
    val projectState = session?.state?.collectAsState()?.value
    val drawing = remember(projectState?.font) { briefDrawingOf(projectState?.font) }
    val state =
        remember(session, atlas) {
            BriefScreenUiState(
                initialEthos =
                    session
                        ?.state
                        ?.value
                        ?.meta
                        ?.manifest
                        ?.brief
                        ?.ethos ?: Ethos(),
                atlas = atlas,
                initialDrawing = drawing,
                persist = session?.let { live -> { ethos: Ethos -> saveEthos(live, ethos) } },
            )
        }
    SideEffect { state.updateDrawing(drawing) }
    return state
}

/** Writes [ethos] into [session]'s brief; false when the project refuses it. */
private fun saveEthos(
    session: ProjectSession,
    ethos: Ethos,
): Boolean {
    val brief = session.state.value.meta.manifest.brief
    return session.update(MetaChange.SetBrief(brief.copy(ethos = ethos))) !is EditResult.Refused
}
