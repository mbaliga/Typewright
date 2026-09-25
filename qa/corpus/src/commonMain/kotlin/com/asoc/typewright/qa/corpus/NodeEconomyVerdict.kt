// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.corpus

/**
 * The three words brief 8.2 allows for the node-economy check -- never "fail" / "warn" / "pass"
 * (law 5's language rule; brief 8.2 itself: "the words 'outlier / above / in range' are the UI's
 * words; never 'fail / warn / pass' for this check"). A glyph's count is compared against a
 * style class's [Quartiles] box with [verdictFor]:
 * - [OUTLIER]: the count exceeds [outlierFence].
 * - [ABOVE]: the count is above the box's Q3 but at or inside the fence.
 * - [IN_RANGE]: the count is at or below Q3.
 */
enum class NodeEconomyVerdict {
    OUTLIER,
    ABOVE,
    IN_RANGE,
}

/**
 * Tukey's fence with Typewright's own minimum slack (brief 8.2): `Q3 + max(1.5 * IQR, 0.25 *
 * Q3)`. The `0.25 * Q3` term is not part of the standard Tukey fence -- it is Typewright's own
 * addition, needed because several boxes have a zero (or near-zero) interquartile range (the
 * sans-geometric T's Q1 and Q3 are both 8), which would otherwise put almost every count above
 * Q3 outside the fence.
 *
 * Per law 5 ("if a check has no measurement behind it, its UI says 'our heuristic'") and
 * docs/ARCHITECTURE_REVIEW.md section 5 item 17, any UI-facing text that names this fence must
 * call the 0.25 * Q3 minimum **our heuristic**, never "the standard fence" -- the explorer
 * currently makes that wrong claim ("the standard fence. Not a threshold we invented.") and is
 * due to be corrected under law 6, but that correction is out of this module's scope. This
 * function's own contract: the minimum below is Typewright's, not a textbook one.
 */
fun outlierFence(box: Quartiles): Double {
    val iqr = box.q3 - box.q1
    return box.q3 + maxOf(1.5 * iqr, 0.25 * box.q3)
}

/**
 * The node-economy verdict for [count] against [box] (brief 8.2). Written for on-curve counts,
 * the axis brief 8.2 discusses, but it takes any [Quartiles] box, so a caller can pass an
 * off-curve box (e.g. from [NodeEconomyCorpus.offCurveBox]) just as well.
 */
fun verdictFor(
    count: Int,
    box: Quartiles,
): NodeEconomyVerdict =
    when {
        count > outlierFence(box) -> NodeEconomyVerdict.OUTLIER
        count > box.q3 -> NodeEconomyVerdict.ABOVE
        else -> NodeEconomyVerdict.IN_RANGE
    }
