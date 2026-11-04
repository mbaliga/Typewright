// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.geometry

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [tidyReport], [outlineDistance] and [tidyKeeping]: the count before and after, what goes, and the measured drift. */
class TidyReportTest {
    /** A 200 by 100 rectangle with an extra, collinear node on every side. */
    private fun withMidpoints(): Contour =
        polygonContour(
            listOf(
                Point(0, 0),
                Point(100, 0),
                Point(200, 0),
                Point(200, 50),
                Point(200, 100),
                Point(100, 100),
                Point(0, 100),
                Point(0, 50),
            ),
            CurveFormat.CUBIC,
        )

    private fun square(): Contour = polygonContour(listOf(Point(0, 0), Point(100, 0), Point(100, 100), Point(0, 100)), CurveFormat.CUBIC)

    @Test
    fun collinearNodesGoAndTheReportSaysWhichAndHowMany() {
        val report = Glyph("r", 300, listOf(withMidpoints())).tidyReport()
        assertEquals(8, report.before.onCurveEquivalent)
        assertEquals(4, report.after.onCurveEquivalent)
        assertEquals(setOf(Point(100, 0), Point(200, 50), Point(100, 100), Point(0, 50)), report.removed.toSet())
        assertTrue(report.maxShift < 1e-6)
        assertEquals(0.0, report.areaChange, 1e-6)
        assertEquals(false, report.isNoop)
    }

    @Test
    fun aGlyphOfNeededNodesIsANoopAndOnlyTheNamedContourIsTouched() {
        assertTrue(Glyph("t", 200, listOf(square())).tidyReport().isNoop)
        val glyph = Glyph("two", 600, listOf(square(), withMidpoints()))
        val onlyFirst = glyph.tidyReport(setOf(0))
        assertTrue(onlyFirst.isNoop)
        assertEquals(glyph.contours, onlyFirst.contours)
        val onlySecond = glyph.tidyReport(setOf(1))
        assertEquals(glyph.contours[0], onlySecond.contours[0])
        assertEquals(4, onlySecond.after.onCurveEquivalent)
    }

    @Test
    fun aQuadraticContourIsLeftAlone() {
        val quad =
            Contour(
                listOf(ContourPoint(Point(0, 0), true), ContourPoint(Point(50, 0), true), ContourPoint(Point(50, 50), true)),
                CurveFormat.QUADRATIC,
            )
        val report = Glyph("q", 100, listOf(quad)).tidyReport()
        assertTrue(report.isNoop)
        assertEquals(0, report.before.onCurveEquivalent)
    }

    @Test
    fun theDistanceBetweenOutlinesIsMeasuredBothWays() {
        val a = square()
        val b = polygonContour(listOf(Point(3, 0), Point(103, 0), Point(103, 100), Point(3, 100)), CurveFormat.CUBIC)
        assertEquals(3.0, outlineDistance(a, b), 0.01)
        assertEquals(0.0, outlineDistance(a, a), 1e-9)
        val spike = polygonContour(listOf(Point(0, 0), Point(100, 0), Point(100, 100), Point(50, 140), Point(0, 100)), CurveFormat.CUBIC)
        assertTrue(outlineDistance(a, spike) > 30.0)
    }

    @Test
    fun aGentleRunOfSmallBumpsIsNotTidiedPastTheBound() {
        // 21 nodes along a very shallow arc: every single removal is within 1.5 units, the sum of them may not be.
        val top = (0..20).map { i -> Point(i * 20, -((i - 10) * (i - 10)) / 6) }
        val contour = polygonContour(top + listOf(Point(400, -400), Point(0, -400)), CurveFormat.CUBIC)
        val tidied = tidyKeeping(contour, 1.5)
        assertTrue(outlineDistance(contour, tidied) <= 1.5 + 1e-9)
        assertTrue(abs(tidied.signedArea() - contour.signedArea()) <= 1.5 * 1500)
    }
}
