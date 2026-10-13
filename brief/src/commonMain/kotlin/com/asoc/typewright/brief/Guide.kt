// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.brief

/** What kind of advice a guide item is, as a word on the item. */
enum class GuideKind(
    val word: String,
) {
    NEXT("Next"),
    REQUIREMENT("Requirement"),
    TENSION("Tension"),
    DRIFT("Drift"),
    CRAFT("Craft"),
    LEARN("Learn"),
}

/**
 * One piece of advice: what to do ([title], [body]) and on whose authority ([source]). [glyphs]
 * are the letters it is about, [check] the measured result of a requirement, [dimension] the
 * question that settles a tension, [sceneId] the Learn scene to open.
 */
data class GuideItem(
    val kind: GuideKind,
    val title: String,
    val body: String,
    val source: Source?,
    val glyphs: String = "",
    val check: CheckResult? = null,
    val dimension: Dimension? = null,
    val signature: Dimension? = null,
    val sceneId: String? = null,
)

/**
 * The guide for this brief and drawing, most pressing first: the next thing to draw; the
 * requirements the letters fail; the tensions in the brief; where the letters drift from it;
 * the rest of the requirements; the genre's craft tips; and where to read more.
 */
fun guideFor(
    model: BriefModel,
    drawing: Drawing,
): List<GuideItem> {
    val out = mutableListOf<GuideItem>()
    val font = drawing.font

    planFor(model).firstOrNull { it.glyphs.isNotEmpty() && it.remaining(font).isNotEmpty() }?.let { step ->
        val remaining = step.remaining(font)
        out += GuideItem(GuideKind.NEXT, step.title, step.why, step.source, glyphs = remaining.joinToString(" "))
    }

    val requirements =
        model.uses.flatMap { use ->
            use.constraints.map { c -> Triple(use, c, c.check?.run(font)) }
        }
    for ((_, c, result) in requirements) {
        if (result?.status == CheckStatus.FAIL) out += GuideItem(GuideKind.REQUIREMENT, c.title, result.detail, c.source, check = result)
    }

    model.tensions.forEach { t ->
        out += GuideItem(GuideKind.TENSION, t.title, t.body, t.source, dimension = t.dimensions.firstOrNull(), signature = t.signature)
    }

    val drift = drift(model, drawing)
    (drift.filter { it.status == DriftStatus.OFF } + drift.filter { it.status == DriftStatus.NEAR }).forEach { item ->
        val target = item.target
        out +=
            GuideItem(
                GuideKind.DRIFT,
                "${item.feature.label}: ${item.measured}, ${item.status.word}",
                "The brief asks for ${target.describe()} (${target.origin.kind.word}: ${target.origin.label}). ${item.advice.orEmpty()}"
                    .trim(),
                Source(SourceKind.MEASURED, "Your letters against the style atlas"),
                glyphs = item.feature.glyphs,
                dimension = Dimension.entries.firstOrNull { item.feature in it.features },
            )
    }

    val rest = requirements.filter { it.third?.status != CheckStatus.FAIL }.sortedBy { statusOrder(it.third?.status) }
    rest.forEach { (_, c, result) ->
        out += GuideItem(GuideKind.REQUIREMENT, c.title, result?.let { "${c.body} ${it.detail}" } ?: c.body, c.source, check = result)
    }

    model.genre?.let { genre ->
        genre.tips.forEach { tip -> out += GuideItem(GuideKind.CRAFT, tip.title, tip.body, tip.source) }
        genre.lineagesScene?.let { scene ->
            out +=
                GuideItem(
                    GuideKind.LEARN,
                    "Where ${genre.name.lowercase()} comes from",
                    "The lineages lesson shows the faces this genre grew from, set side by side.",
                    null,
                    sceneId = scene,
                )
        }
    }
    return out
}

private fun statusOrder(status: CheckStatus?): Int =
    when (status) {
        CheckStatus.WARN -> 0
        CheckStatus.UNMEASURED -> 1
        null -> 2
        CheckStatus.PASS -> 3
        CheckStatus.FAIL -> -1
    }
