// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.geometry

/**
 * Before/after node-economy counts for one fitted contour (P2b item 3): on-curve-equivalent and
 * off-curve counts on each side, reusing P1a's [Contour.count]/[Glyph.count] exactly — no counting
 * rule of its own — plus the derived totals and off-curve ratios a report or a UI would show
 * (`TYPEWRIGHT_BUILD_BRIEF.md` §7's count collapsing from, for example, "T 1,763 -> 8").
 */
data class NodeEconomyReport(
    val beforeOnCurve: Int,
    val beforeOffCurve: Int,
    val afterOnCurve: Int,
    val afterOffCurve: Int,
) {
    val beforeTotal: Int get() = beforeOnCurve + beforeOffCurve
    val afterTotal: Int get() = afterOnCurve + afterOffCurve

    /** The fraction of `before`'s points that were off-curve; `0.0` for a contourless or point-free `before` (nothing to divide by). */
    val beforeOffCurveRatio: Double get() = if (beforeTotal == 0) 0.0 else beforeOffCurve.toDouble() / beforeTotal

    /** The fraction of `after`'s points that are off-curve; `0.0` for a degenerate empty `after`. */
    val afterOffCurveRatio: Double get() = if (afterTotal == 0) 0.0 else afterOffCurve.toDouble() / afterTotal
}

/**
 * One contour's before/after node-economy report. [beforePolyline] is the dense polyline the P2
 * pipeline started from — a raster trace's own boundary, or (per this task's shared instructions)
 * a pure-polygon TrueType glyph's own on-curve points — read here as an ordinary all-on-curve
 * [CurveFormat.QUADRATIC] [Contour] purely so [Contour.count] can be reused unchanged rather than
 * re-deriving "count of a dense polyline" as a special case: every point in a dense trace is, by
 * construction, a point the trace actually passed through, i.e. on-curve, and an
 * all-on-curve QUADRATIC contour has no off-curve run to imply anything from, so this reads back
 * exactly `(beforePolyline.size, 0)` — matching how CLAUDE.md's own fixtures state a shipped
 * glyph's starting count (for example "T 1,763 on-curve / 0 off-curve").
 *
 * [afterFit] is the finished contour — typically [fitClosedContourToCubics]'s output after
 * [applyTypeConstraints], but this function does not require that; it only reads [afterFit]'s own
 * [Contour.count].
 */
fun nodeEconomyReport(
    beforePolyline: List<Point>,
    afterFit: Contour,
): NodeEconomyReport {
    val before = Contour(beforePolyline.map { ContourPoint(it, onCurve = true) }, CurveFormat.QUADRATIC).count()
    val after = afterFit.count()
    return NodeEconomyReport(
        beforeOnCurve = before.onCurveEquivalent,
        beforeOffCurve = before.offCurve,
        afterOnCurve = after.onCurveEquivalent,
        afterOffCurve = after.offCurve,
    )
}

/** [nodeEconomyReport], summed across every contour of a multi-contour glyph (`o`'s outer and inner ring together, for example). */
fun nodeEconomyReportForGlyph(
    beforePolylines: List<List<Point>>,
    afterFit: List<Contour>,
): NodeEconomyReport {
    require(beforePolylines.size == afterFit.size) {
        "before/after contour counts must match: had ${beforePolylines.size} before, ${afterFit.size} after"
    }
    val perContour = beforePolylines.indices.map { index -> nodeEconomyReport(beforePolylines[index], afterFit[index]) }
    return NodeEconomyReport(
        beforeOnCurve = perContour.sumOf { it.beforeOnCurve },
        beforeOffCurve = perContour.sumOf { it.beforeOffCurve },
        afterOnCurve = perContour.sumOf { it.afterOnCurve },
        afterOffCurve = perContour.sumOf { it.afterOffCurve },
    )
}
