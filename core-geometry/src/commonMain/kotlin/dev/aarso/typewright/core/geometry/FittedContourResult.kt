// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.core.geometry

/**
 * One contour's result from the full P2 pipeline ([fitPolylineToFinishedContour]): the finished,
 * type-constrained [Contour], its [ConstructionClassification], and its [NodeEconomyReport].
 */
data class FittedContourResult(
    val fitted: Contour,
    val classification: ConstructionClassification,
    val nodeEconomy: NodeEconomyReport,
)

/**
 * P2b item 4's "wire together the full pipeline": dense polyline -> corner-detect + Schneider fit
 * ([fitClosedContourToCubics], P2a) -> type constraints ([applyTypeConstraints], the "Snap" stage)
 * -> construction classification ([classifyConstruction]) -> a node-economy report
 * ([nodeEconomyReport]), for one closed contour.
 *
 * [metricLines] is passed straight through to [applyTypeConstraints]; an empty list (the default)
 * runs the pipeline with no metric-line snapping at all, useful for a contour with no established
 * font metrics yet (this function's own unit tests; the genericity check on a glyph the caller has
 * not decided metrics for).
 */
fun fitPolylineToFinishedContour(
    polyline: List<Point>,
    metricLines: List<Int> = emptyList(),
    fitParams: CubicFitParameters = CubicFitParameters(),
    constraintParams: TypeConstraintParameters = TypeConstraintParameters(),
    classifierParams: ConstructionClassifierParameters = ConstructionClassifierParameters(),
): FittedContourResult {
    val rawFit = fitClosedContourToCubics(polyline, fitParams)
    val finished = applyTypeConstraints(rawFit, metricLines, constraintParams)
    return FittedContourResult(
        fitted = finished,
        classification = classifyConstruction(finished, classifierParams),
        nodeEconomy = nodeEconomyReport(polyline, finished),
    )
}

/**
 * [fitPolylineToFinishedContour], applied to every contour of a multi-contour glyph (`o`'s outer
 * and inner ring), with direction ([applyTypeConstraintsToContours]'s
 * [enforceContourDirections]) resolved across the whole glyph rather than per-contour — the one
 * step of the pipeline that genuinely needs every contour together (see
 * [applyTypeConstraintsToContours]'s KDoc).
 */
fun fitGlyphPolylinesToFinishedContours(
    polylines: List<List<Point>>,
    metricLines: List<Int> = emptyList(),
    fitParams: CubicFitParameters = CubicFitParameters(),
    constraintParams: TypeConstraintParameters = TypeConstraintParameters(),
    classifierParams: ConstructionClassifierParameters = ConstructionClassifierParameters(),
): List<FittedContourResult> {
    val rawFits = fitGlyphContoursToCubics(polylines, fitParams)
    val finished = applyTypeConstraintsToContours(rawFits, metricLines, constraintParams)
    return finished.mapIndexed { index, contour ->
        FittedContourResult(
            fitted = contour,
            classification = classifyConstruction(contour, classifierParams),
            nodeEconomy = nodeEconomyReport(polylines[index], contour),
        )
    }
}
