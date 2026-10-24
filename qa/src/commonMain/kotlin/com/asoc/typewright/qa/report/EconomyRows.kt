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

/** One glyph's row on the Economy view: its on-curve [count] against the [box] it was judged by, the [verdict], and the fence that decided it. */
data class EconomyRow(
    val glyph: String,
    val count: Int,
    val box: Quartiles,
    val verdict: NodeEconomyVerdict,
    val fence: Double,
) {
    /** How far above the top of the box the count is, as a natural log of count over Q3 (negative below it): the worst-first key. */
    val logAboveBox: Double get() = ln(count.coerceAtLeast(1).toDouble() / box.q3.coerceAtLeast(1.0))
}

/**
 * The rows of the Economy view for [report]: one per glyph the corpus has a box for, in [sort] order. Worst
 * first is the log distance above Q3, largest first (brief §8.3), with ties broken by name so the order never
 * wobbles; alphabetical puts a capital before its lower-case twin.
 */
fun economyRows(
    report: NodeEconomyReport,
    sort: EconomySort,
): List<EconomyRow> {
    val rows =
        report.glyphs.mapNotNull { result ->
            val box = result.onCurveBox ?: return@mapNotNull null
            val verdict = result.onCurveVerdict ?: return@mapNotNull null
            EconomyRow(result.glyphName, result.onCurve, box, verdict, outlierFence(box))
        }
    return when (sort) {
        EconomySort.WORST_FIRST -> rows.sortedWith(compareByDescending<EconomyRow> { it.logAboveBox }.thenBy { it.glyph })
        EconomySort.ALPHABETICAL -> rows.sortedWith(compareBy<EconomyRow> { it.glyph.lowercase() }.thenBy { it.glyph })
    }
}

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
