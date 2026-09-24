package dev.aarso.typewright.core.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NodeEconomyReportTest {
    @Test
    fun reportsTheClaudeMdTShapeCollapse() {
        // The shape of CLAUDE.md's own headline fixture, at a much smaller scale: a polygon
        // "shipped" with many on-curve points and no off-curve ones, fitted down to a CUBIC
        // rectangle.
        val before = denseSquare(x0 = 0, y0 = 0, side = 400, step = 4)
        val after = fitClosedContourToCubics(before)

        val report = nodeEconomyReport(before, after)

        assertEquals(before.size, report.beforeOnCurve)
        assertEquals(0, report.beforeOffCurve)
        assertEquals(0.0, report.beforeOffCurveRatio)
        assertEquals(after.count().onCurveEquivalent, report.afterOnCurve)
        assertEquals(after.count().offCurve, report.afterOffCurve)
        assertTrue(report.afterTotal < report.beforeTotal, "the fitted contour must have far fewer points than the source polygon")
    }

    @Test
    fun offCurveRatiosAreComputedCorrectly() {
        val report = NodeEconomyReport(beforeOnCurve = 10, beforeOffCurve = 0, afterOnCurve = 8, afterOffCurve = 8)
        assertEquals(10, report.beforeTotal)
        assertEquals(16, report.afterTotal)
        assertEquals(0.0, report.beforeOffCurveRatio)
        assertEquals(0.5, report.afterOffCurveRatio)
    }

    @Test
    fun anEmptyBeforeAndAfterHasAZeroRatioRatherThanDividingByZero() {
        val report = NodeEconomyReport(beforeOnCurve = 0, beforeOffCurve = 0, afterOnCurve = 0, afterOffCurve = 0)
        assertEquals(0.0, report.beforeOffCurveRatio)
        assertEquals(0.0, report.afterOffCurveRatio)
    }

    @Test
    fun forGlyphSumsAcrossEveryContour() {
        val outerBefore = denseCircle(centerX = 0.0, centerY = 0.0, radius = 250.0, angleStepDegrees = 1.0)
        val innerBefore = denseCircle(centerX = 0.0, centerY = 0.0, radius = 150.0, angleStepDegrees = 1.0, clockwise = true)
        val fitted = fitGlyphContoursToCubics(listOf(outerBefore, innerBefore))

        val report = nodeEconomyReportForGlyph(listOf(outerBefore, innerBefore), fitted)

        val outerReport = nodeEconomyReport(outerBefore, fitted[0])
        val innerReport = nodeEconomyReport(innerBefore, fitted[1])
        assertEquals(outerReport.beforeOnCurve + innerReport.beforeOnCurve, report.beforeOnCurve)
        assertEquals(outerReport.afterOnCurve + innerReport.afterOnCurve, report.afterOnCurve)
        assertEquals(outerReport.afterOffCurve + innerReport.afterOffCurve, report.afterOffCurve)
    }

    @Test
    fun forGlyphRejectsMismatchedContourCounts() {
        val before = listOf(denseSquare(x0 = 0, y0 = 0, side = 100, step = 10))
        val after = fitGlyphContoursToCubics(before) + fitGlyphContoursToCubics(before)
        assertFailsWith<IllegalArgumentException> { nodeEconomyReportForGlyph(before, after) }
    }
}
