// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.brief

/**
 * One step of the drawing plan: the [glyphs] to draw (empty for a step that is an action, like
 * spacing a wordmark), why now, and on whose authority.
 */
data class PlanStep(
    val id: String,
    val title: String,
    val glyphs: List<Char>,
    val why: String,
    val source: Source,
) {
    /** The glyphs of this step still undrawn in [font]. */
    fun remaining(font: FontView): List<Char> = glyphs.filter { !font.drawn(it) }
}

private val TRACY = Source(SourceKind.CONVENTION, "Walter Tracy, Letters of Credit (1986): n o H O as control characters")
private val PRACTICE = Source(SourceKind.CONVENTION, "Type design practice")

/** The capitals the English day and month abbreviations use (MON to SUN, JAN to DEC), with AM and PM. */
internal val DATE_CAPITALS: List<Char> =
    listOf(
        "MON",
        "TUE",
        "WED",
        "THU",
        "FRI",
        "SAT",
        "SUN",
        "JAN",
        "FEB",
        "MAR",
        "APR",
        "MAY",
        "JUN",
        "JUL",
        "AUG",
        "SEP",
        "OCT",
        "NOV",
        "DEC",
        "AM",
        "PM",
    ).flatMap { it.toList() }
        .distinct()
        .sorted()

/**
 * What to draw, in order, for this brief. The control characters come first because every
 * other letter takes its stem, curve, heights and spacing from them; then the letters where the
 * genre's voice lives; then the rest in families. A watch face starts from its figures and a
 * wordmark from its own letters, because that is the whole job. A glyph appears in one step only.
 */
fun planFor(model: BriefModel): List<PlanStep> {
    val steps = mutableListOf<PlanStep>()
    val useIds = model.uses.map { it.id }
    val primary = useIds.firstOrNull()
    val wordmark =
        model.ethos.wordmark
            ?.trim()
            .orEmpty()

    if (primary == "wordmark" && wordmark.isNotEmpty()) {
        steps +=
            PlanStep(
                "wordmark.letters",
                "The letters of the name",
                wordmark
                    .filter {
                        !it.isWhitespace()
                    }.toList(),
                "A wordmark is only its own letters: draw them first, together.",
                PRACTICE,
            )
        steps +=
            PlanStep(
                "wordmark.space",
                "Space the name as one word",
                emptyList(),
                "Only the pairs in the name matter: space them by eye, then lock them.",
                Source(SourceKind.CONVENTION, "Brand identity practice"),
            )
    }
    if (primary == "watch-face") {
        steps +=
            PlanStep(
                "watch.bones",
                "The bones of the figures",
                listOf('0', '1'),
                "0 is the round and 1 the straight every other figure is built from, as o and n are for letters.",
                PRACTICE,
            )
        steps +=
            PlanStep(
                "watch.figures",
                "The other figures and the colon, all on one width",
                "23456789:".toList(),
                "Tabular figures keep the time from shifting as it changes.",
                Source(
                    SourceKind.GUIDELINE,
                    "Wear OS Material 3 typography; Watch Face Format text",
                    "https://developer.android.com/training/wearables/wff/text",
                ),
            )
        if (model.ethos.useOptions["watch-face.date"] == "date") {
            steps +=
                PlanStep(
                    "watch.capitals",
                    "The capital control characters",
                    "HO".toList(),
                    "H and O set the capitals' stems, curves and height.",
                    TRACY,
                )
            steps +=
                PlanStep(
                    "watch.date",
                    "Capitals for the day and month names",
                    DATE_CAPITALS,
                    "MON to SUN, JAN to DEC, AM and PM use ${DATE_CAPITALS.size} capitals in all.",
                    Source(SourceKind.DERIVED, "Counted from the English abbreviations"),
                )
        }
    }
    val wordmarkOnly = primary == "wordmark" && wordmark.isNotEmpty()
    if (!wordmarkOnly) {
        steps +=
            PlanStep(
                "control",
                "The control characters",
                "noHO".toList(),
                "They set the stem, the curve, the heights and the spacing every other letter takes from.",
                TRACY,
            )
    }
    model.genre?.takeUnless { wordmarkOnly }?.let { genre ->
        val letters = genre.drawFirst.filter { !it.isWhitespace() }.toList()
        steps +=
            PlanStep(
                "genre.voice",
                "Where ${genre.name.lowercase()} lives",
                letters,
                "These letters carry the genre's signatures; draw them while the control characters are fresh.",
                PRACTICE,
            )
    }
    if ("code" in useIds) {
        steps +=
            PlanStep(
                "code.lookalikes",
                "The look-alikes",
                "0O1lI".toList(),
                "In code these must be unmistakable at 12 to 14 points; settle them before anything else.",
                Source(SourceKind.CONVENTION, "JetBrains Mono, Input design notes"),
            )
    }
    val capitalsFirst = primary == "signage" || primary == "headline"
    val lowercase =
        listOf(
            PlanStep("lower.straight", "Lowercase from n", "ilhmur".toList(), "They share n's stem, arch and spacing.", PRACTICE),
            PlanStep("lower.round", "Lowercase from o", "cedbpq".toList(), "They share o's curve and overshoot.", PRACTICE),
            PlanStep(
                "lower.rest",
                "Diagonals and the individualists",
                "vwxyzkagsftj".toList(),
                "The diagonals share one angle; a, g, s, f, t and j each need their own care.",
                PRACTICE,
            ),
        )
    val capitals =
        listOf(
            PlanStep(
                "upper.straight",
                "Capitals from H",
                "ILEFTHN".toList(),
                "They share H's stems and the bar height you chose.",
                PRACTICE,
            ),
            PlanStep("upper.round", "Capitals from O", "CGDQ".toList(), "They share O's curve.", PRACTICE),
            PlanStep(
                "upper.rest",
                "The other capitals",
                "BPRSAVWXYKMZJU".toList(),
                "Diagonals, bowls and the S: the hardest capitals, drawn once the others have set the rules.",
                PRACTICE,
            ),
        )
    val figures =
        PlanStep(
            "figures",
            "Figures",
            "0123456789".toList(),
            "Lining figures sit on the cap height and share the capitals' weight.",
            PRACTICE,
        )
    val punctuation = PlanStep("punctuation", "Punctuation", ".,:;!?'\"-()".toList(), "Small, but on every line of text.", PRACTICE)
    if (!wordmarkOnly) {
        steps += if (capitalsFirst) capitals + figures + lowercase else lowercase + capitals + figures
        steps += punctuation
    }
    if ("body-text" in useIds || "e-ink" in useIds) {
        steps +=
            PlanStep(
                "latin.core",
                "Accents and the rest of GF Latin Core",
                emptyList(),
                "Google Fonts accepts a Latin family only with GF Latin Core, about 324 glyphs; most of them are accented letters built from what you have drawn.",
                Source(SourceKind.RULE, "Google Fonts guide, requirements", "https://googlefonts.github.io/gf-guide/requirements.html"),
            )
    }
    return dedupe(steps)
}

/** Drops glyphs already planned by an earlier step, then steps left with nothing to draw (action steps stay). */
private fun dedupe(steps: List<PlanStep>): List<PlanStep> {
    val seen = mutableSetOf<Char>()
    return steps.mapNotNull { step ->
        if (step.glyphs.isEmpty()) return@mapNotNull step
        val fresh = step.glyphs.filter { seen.add(it) }
        if (fresh.isEmpty()) null else step.copy(glyphs = fresh)
    }
}
