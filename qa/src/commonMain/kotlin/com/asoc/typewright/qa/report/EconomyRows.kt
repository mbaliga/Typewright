// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.report

import com.asoc.typewright.qa.NodeEconomyReport
import com.asoc.typewright.qa.corpus.NodeEconomyCorpus
import com.asoc.typewright.qa.corpus.NodeEconomyVerdict
import com.asoc.typewright.qa.corpus.Quartiles
import com.asoc.typewright.qa.corpus.onCurve
import com.asoc.typewright.qa.corpus.outlierFence
import kotlin.math.ln

/** The two orders of the Economy view (brief §8.3): what is furthest above its box first, or by name. */
enum class EconomySort(
    val label: String,
) {
    WORST_FIRST("Worst first"),
    ALPHABETICAL("A–Z"),
}

/** The shape that goes with a node-economy verdict (a word and a shape, never a colour: brief §8.2's words, law 8). */
val NodeEconomyVerdict.shape: String
    get() =
        when (this) {
            NodeEconomyVerdict.OUTLIER -> "●"
            NodeEconomyVerdict.ABOVE -> "◐"
            NodeEconomyVerdict.IN_RANGE -> "✓"
        }

/** The word for a node-economy verdict: outlier, above, in range (never fail, warn or pass; brief §8.2). */
val NodeEconomyVerdict.word: String
    get() =
        when (this) {
            NodeEconomyVerdict.OUTLIER -> "outlier"
            NodeEconomyVerdict.ABOVE -> "above"
            NodeEconomyVerdict.IN_RANGE -> "in range"
        }

/** One line of the Economy view: a glyph judged against a box ([EconomyRow]), or one the corpus has no box for ([UnjudgedRow]). */
sealed interface EconomyEntry {
    val glyph: String

    /** The glyph's on-curve count, which every line shows whether or not it can be judged. */
    val count: Int
}

/** One glyph's row on the Economy view: its on-curve [count] against the [box] it was judged by, the [verdict], and the fence that decided it. */
data class EconomyRow(
    override val glyph: String,
    override val count: Int,
    val box: Quartiles,
    val verdict: NodeEconomyVerdict,
    val fence: Double,
) : EconomyEntry {
    /** How far above the top of the box the count is, as a natural log of count over Q3 (negative below it): the worst-first key. */
    val logAboveBox: Double get() = ln(count.coerceAtLeast(1).toDouble() / box.q3.coerceAtLeast(1.0))
}

/** A glyph of the project that the class being compared against has no box for: it is listed with its count and no verdict. */
data class UnjudgedRow(
    override val glyph: String,
    override val count: Int,
) : EconomyEntry {
    /** What the row says in place of a verdict. */
    val sentence: String get() = NO_BOX_SENTENCE
}

/** What a row says about a glyph that has no box in the corpus. */
const val NO_BOX_SENTENCE: String = "no box for this glyph in the corpus"

/**
 * Every glyph of the project as a line of the Economy view, in order, and how many of them could be judged.
 * [entries] has one line per glyph; [judged] and [total] are the counts the page states.
 */
class EconomyListing(
    val entries: List<EconomyEntry>,
) {
    /** How many glyphs have a box and a verdict. */
    val judged: Int get() = entries.count { it is EconomyRow }

    /** How many glyphs the project has. */
    val total: Int get() = entries.size

    /** The lines that have a box, in the listing's order. */
    val rows: List<EconomyRow> get() = entries.filterIsInstance<EconomyRow>()

    /** The sentence the page states about how much of the project was judged. */
    fun coverage(): String = economyCoverage(judged, total)
}

/** `52 of 338 glyphs are judged against this class's faces. The other 286 have no box in the corpus, so each shows its count and no verdict.` */
fun economyCoverage(
    judged: Int,
    total: Int,
): String =
    when {
        total == 0 -> {
            "This project has no glyphs yet."
        }

        judged == total -> {
            "All ${plural(total, "glyph")} are judged against this class's faces."
        }

        judged == 0 -> {
            "None of the ${plural(total, "glyph")} has a box in this class's faces, so none is judged."
        }

        else -> {
            "${judged.withThousands()} of ${plural(total, "glyph")} are judged against this class's faces. " +
                "The other ${(total - judged).withThousands()} have no box in the corpus, so each shows its count and no verdict."
        }
    }

/**
 * The lines of the Economy view for [report], one per glyph of the project, in [sort] order. Worst first is the log
 * distance above Q3, largest first (brief §8.3), with ties broken by name so the order never wobbles, and the glyphs
 * with no box last; alphabetical puts a capital before its lower-case twin and keeps every glyph in its place.
 */
fun economyListing(
    report: NodeEconomyReport,
    sort: EconomySort,
): EconomyListing {
    val entries: List<EconomyEntry> =
        report.glyphs.map { result ->
            val box = result.onCurveBox
            val verdict = result.onCurveVerdict
            if (box != null && verdict != null) {
                EconomyRow(result.glyphName, result.onCurve, box, verdict, outlierFence(box))
            } else {
                UnjudgedRow(result.glyphName, result.onCurve)
            }
        }
    val byName = compareBy<EconomyEntry> { it.glyph.lowercase() }.thenBy { it.glyph }
    val ordered =
        when (sort) {
            EconomySort.WORST_FIRST -> {
                val judged = entries.filterIsInstance<EconomyRow>()
                judged.sortedWith(compareByDescending<EconomyRow> { it.logAboveBox }.thenBy { it.glyph }) +
                    entries.filterIsInstance<UnjudgedRow>().sortedWith(byName)
            }

            EconomySort.ALPHABETICAL -> {
                entries.sortedWith(byName)
            }
        }
    return EconomyListing(ordered)
}

/** The rows of the Economy view that have a box, in [sort] order: [economyListing] without the glyphs it could not judge. */
fun economyRows(
    report: NodeEconomyReport,
    sort: EconomySort,
): List<EconomyRow> = economyListing(report, sort).rows

/** One reference face's on-curve count for a glyph. */
data class FaceCount(
    val family: String,
    val count: Int,
)

/**
 * The faces behind one box (brief §8.3: tapping a row opens it to show the faces as points, with the fewest and
 * the most named). [fewestAlso] and [mostAlso] are how many other faces share that count (the board's
 * "ALATA + 21"). [faces] is in the corpus's rank order.
 */
data class FaceSpread(
    val faces: List<FaceCount>,
    val fewest: FaceCount,
    val fewestAlso: Int,
    val most: FaceCount,
    val mostAlso: Int,
)

/** The faces of [styleKey] that have a count for [glyph], with the fewest and the most named, or null when none has. */
fun faceSpread(
    corpus: NodeEconomyCorpus,
    styleKey: String,
    glyph: String,
): FaceSpread? {
    val faces = corpus.families(styleKey).mapNotNull { face -> face.onCurve(glyph)?.let { FaceCount(face.family, it) } }
    if (faces.isEmpty()) return null
    val low = faces.minOf { it.count }
    val high = faces.maxOf { it.count }
    return FaceSpread(
        faces = faces,
        fewest = faces.first { it.count == low },
        fewestAlso = faces.count { it.count == low } - 1,
        most = faces.first { it.count == high },
        mostAlso = faces.count { it.count == high } - 1,
    )
}

/** The legend line of the Economy view, always visible: what the box, the point, the whiskers and the axis are. */
fun economyLegend(faces: Int): String = "▮ middle half of the $faces faces · ● your glyph · ─ whiskers: full range · log scale"

/** The footer sentence that states the fence (brief §8.3), and which part of it is our own. */
const val ECONOMY_FENCE_SENTENCE: String =
    "Outlier: above the top of the box by more than 1.5 box-widths or a quarter of its value, whichever is larger. " +
        "The quarter of its value is our heuristic."
