// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.check

import com.asoc.typewright.qa.report.FaceCount
import com.asoc.typewright.qa.report.FaceSpread
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BoxPlotLayoutTest {
    private fun assertNear(
        expected: Double,
        actual: Double,
        tolerance: Double = 0.05,
        what: String = "",
    ) = assertTrue(abs(expected - actual) <= tolerance, "$what: expected about $expected, got $actual")

    @Test
    fun theAxisRunsFromThreeToThreeThousandOnALogScale() {
        assertEquals(0.0, LogAxis.position(3.0), 1e-9)
        assertEquals(1.0, LogAxis.position(3000.0), 1e-9)
        // A factor of ten is a third of the way, because the axis spans three decades.
        assertEquals(1.0 / 3.0, LogAxis.position(30.0), 1e-9)
        assertEquals(2.0 / 3.0, LogAxis.position(300.0), 1e-9)
    }

    @Test
    fun aValueOffEitherEndIsHeldAtThatEnd() {
        assertEquals(0.0, LogAxis.position(1.0), 1e-9)
        assertEquals(0.0, LogAxis.position(0.0), 1e-9)
        assertEquals(1.0, LogAxis.position(50_000.0), 1e-9)
        assertTrue(LogAxis.isClipped(3001.0))
        assertFalse(LogAxis.isClipped(3000.0))
    }

    @Test
    fun theTicksAreLabelledAsTheBoardPrintsThem() {
        assertEquals("4 8 16 32 64 128 256 512 1k 2k", LogAxis.TICKS.joinToString(" ") { LogAxis.label(it) })
    }

    @Test
    fun theTsMarksLandWhereTheBoardDrawsThem() {
        // The board draws the T in a 282-wide axis starting at x = 58: point 318.3, box 98 to 116.3, whisker end 149.2.
        val marks = BoxPlotSpec(8.0, 8.0, 8.0, 12.5, 28.0, 1763).marks()

        fun board(fraction: Double) = 58.0 + fraction * 282.0
        assertNear(98.0, board(marks.whiskerLow), what = "whisker low")
        assertNear(98.0, board(marks.q1), what = "q1")
        assertNear(98.0, board(marks.median), what = "median")
        assertNear(116.3, board(marks.q3), what = "q3")
        assertNear(149.2, board(marks.whiskerHigh), what = "whisker high")
        assertNear(318.3, board(marks.point), what = "your point")
        assertFalse(marks.pointClipped)
    }

    @Test
    fun aCountBeyondTheAxisIsClippedAndSaysSo() {
        val marks = BoxPlotSpec(8.0, 8.0, 8.0, 12.5, 28.0, 5000).marks()
        assertEquals(1.0, marks.point, 1e-9)
        assertTrue(marks.pointClipped)
    }

    @Test
    fun aBoxWhoseQuartilesCoincideIsStillDrawnAtTheMinimumWidth() {
        val marks = BoxPlotSpec(12.0, 12.0, 12.0, 12.0, 44.0, 12).marks()
        assertEquals(3.0, marks.boxWidth(axisWidth = 300.0, minimum = 3.0), 1e-9)
        val wide = BoxPlotSpec(8.0, 8.0, 8.0, 12.5, 28.0, 8).marks()
        assertNear(300.0 * (wide.q3 - wide.q1), wide.boxWidth(300.0, 3.0), 1e-9, "a real box keeps its width")
    }

    @Test
    fun aRowIsDescribedWithTheNumbersAPersonReads() {
        assertEquals(
            "T: 1,763 against a box of 8 to 12.5, whiskers 8 to 28",
            BoxPlotSpec(8.0, 8.0, 8.0, 12.5, 28.0, 1763).description("T"),
        )
    }

    private fun spread(vararg counts: Pair<String, Int>): FaceSpread {
        val faces = counts.map { FaceCount(it.first, it.second) }
        val low = faces.minOf { it.count }
        val high = faces.maxOf { it.count }
        return FaceSpread(
            faces,
            faces.first { it.count == low },
            faces.count { it.count == low } - 1,
            faces.first { it.count == high },
            faces.count { it.count == high } - 1,
        )
    }

    @Test
    fun facesWithTheSameCountShareAColumnAndAreNeverNudgedSideways() {
        val dots = faceDots(spread("Alata" to 8, "Bata" to 8, "Cata" to 8, "Monoton" to 28), top = 26.0, bottom = 56.0)
        assertEquals(4, dots.size)
        val eights = dots.filter { it.count == 8 }
        assertEquals(1, eights.map { it.fraction }.toSet().size, "one x position for one count")
        assertEquals(LogAxis.position(8.0), eights.first().fraction, 1e-9)
        assertEquals(3, eights.map { it.y }.toSet().size, "stacked, not on top of each other")
        assertEquals(3.0, eights[1].y - eights[0].y, 1e-9)
    }

    @Test
    fun facesAloneAtTheirCountAreSpreadThroughTheRowAndTheSameEveryTime() {
        val singles = spread(*((9..17).map { "Face$it" to it }.toTypedArray()))
        val dots = faceDots(singles, top = 26.0, bottom = 56.0)
        assertEquals(9, dots.size)
        assertTrue(dots.all { it.y in 26.0..56.0 })
        assertTrue(dots.map { it.y }.toSet().size >= 6, "neighbouring faces are not all on one line: ${dots.map { it.y }}")
        assertEquals(dots, faceDots(singles, top = 26.0, bottom = 56.0), "the layout never changes between draws")
        assertEquals(dots.map { it.count }, dots.map { it.count }.sorted(), "ordered by count")
    }

    @Test
    fun aCrowdedColumnIsSqueezedToFitAndNeverRunsPastTheBottom() {
        val crowd = (1..22).map { "Face$it" to 8 }.toTypedArray()
        val dots = faceDots(spread(*crowd, "Monoton" to 28), top = 26.0, bottom = 56.0)
        assertTrue(dots.all { it.y in 26.0..56.0 }, "every dot stays inside the row")
        val column = dots.filter { it.count == 8 }
        assertEquals(22, column.size)
        assertTrue(column[1].y - column[0].y < 3.0, "closer than the usual step")
        assertEquals(56.0, column.last().y, 1e-9)
    }

    @Test
    fun theCaptionNamesTheFewestAndTheMostAsTheBoardDoes() {
        val s = spread(*((1..22).map { "Face$it" to 8 }.toTypedArray()), "Monoton" to 28).copy(fewest = FaceCount("Alata", 8))
        assertEquals("FEWEST 8 · ALATA + 21 · MOST 28 · MONOTON", facesCaption(s))
        assertEquals("FEWEST 8 · ALATA · MOST 28 · MONOTON", facesCaption(spread("Alata" to 8, "Monoton" to 28)))
    }
}
