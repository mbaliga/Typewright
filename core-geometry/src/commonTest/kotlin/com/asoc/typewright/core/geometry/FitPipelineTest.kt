// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [fitPolylineToFinishedContour]/[fitGlyphPolylinesToFinishedContours] — P2b's full wired
 * pipeline (dense polyline -> corner-detect + Schneider fit -> type constraints -> classify ->
 * node-economy report) — against synthetic shapes whose end-to-end behaviour can be checked
 * directly. The real `fonts/HyleDeco-Regular.ttf` T/o/n/H fixtures are covered separately, on the
 * JVM only, in `FitPipelineHyleDecoValidationTest`.
 */
class FitPipelineTest {
    @Test
    fun oneContourPipelineClassifiesAndSnapsASquareEndToEnd() {
        // A square whose baseline sits 1 unit off y=0: the full pipeline should fit it to a
        // POLYGONAL contour and snap its bottom edge exactly onto the metric line.
        val square = denseSquare(x0 = 0, y0 = 1, side = 400, step = 4)
        val result = fitPolylineToFinishedContour(square, metricLines = listOf(0))

        assertEquals(CurveFormat.CUBIC, result.fitted.format)
        assertEquals(ConstructionKind.POLYGONAL, result.classification.kind)
        assertEquals(square.size, result.nodeEconomy.beforeOnCurve)
        assertEquals(0, result.nodeEconomy.beforeOffCurve)
        assertEquals(result.fitted.count().onCurveEquivalent, result.nodeEconomy.afterOnCurve)
        assertTrue(result.nodeEconomy.afterTotal < result.nodeEconomy.beforeTotal)

        val bottomOnCurveYs =
            result.fitted.points
                .filter { it.onCurve && it.point.y < 200 }
                .map { it.point.y }
        assertTrue(bottomOnCurveYs.isNotEmpty())
        assertTrue(bottomOnCurveYs.all { it == 0 }, "the near-baseline bottom edge must snap exactly to y=0, got $bottomOnCurveYs")
    }

    @Test
    fun glyphPipelineOnARingClassifiesBothContoursAsEllipticalWithCorrectDirections() {
        val outer = denseCircle(centerX = 0.0, centerY = 0.0, radius = 250.0, angleStepDegrees = 1.0)
        val inner = denseCircle(centerX = 0.0, centerY = 0.0, radius = 150.0, angleStepDegrees = 1.0, clockwise = true)

        val results = fitGlyphPolylinesToFinishedContours(listOf(outer, inner))

        assertEquals(2, results.size)
        assertEquals(ConstructionKind.ELLIPTICAL, results[0].classification.kind)
        assertEquals(ConstructionKind.ELLIPTICAL, results[1].classification.kind)
        assertEquals(Direction.COUNTER_CLOCKWISE, results[0].fitted.direction(), "the outer ring must read counter-clockwise")
        assertEquals(Direction.CLOCKWISE, results[1].fitted.direction(), "the inner ring (the counter) must read clockwise")
    }

    @Test
    fun aDefaultMetricLinesPipelineRunsWithNoSnapping() {
        val square = denseSquare(x0 = 0, y0 = 1, side = 400, step = 4)
        val result = fitPolylineToFinishedContour(square)
        val bottomOnCurveYs =
            result.fitted.points
                .filter { it.onCurve && it.point.y < 200 }
                .map { it.point.y }
        assertTrue(bottomOnCurveYs.all { it == 1 }, "with no metric lines supplied, nothing should snap, got $bottomOnCurveYs")
    }
}
