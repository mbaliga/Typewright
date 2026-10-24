// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OutlineBoundsTest {
    private fun on(
        x: Int,
        y: Int,
    ) = ContourPoint(Point(x, y), onCurve = true)

    private fun off(
        x: Int,
        y: Int,
    ) = ContourPoint(Point(x, y), onCurve = false)

    /** A bowl whose off-curve handles reach x = -100 and x = 400 although the curve itself only reaches about -37 and 338. */
    private fun bowlWithLongHandles() =
        Glyph(
            "o",
            500,
            listOf(
                Contour(
                    listOf(on(150, 0), off(-100, 0), off(-100, 400), on(150, 400), off(400, 400), off(400, 0)),
                    CurveFormat.CUBIC,
                ),
            ),
        )

    @Test
    fun theBoundsAreTheCurvesNotTheirControlPoints() {
        val bounds = requireNotNull(bowlWithLongHandles().outlineBounds())
        // x(0.5) of the left curve is 0.125*150 + 0.375*(-100) + 0.375*(-100) + 0.125*150 = -37.5, which rounds half up to -37;
        // the right curve's is 0.125*150 + 0.375*400 + 0.375*400 + 0.125*150 = 337.5, which rounds half up to 338.
        assertEquals(-37, bounds.xMin)
        assertEquals(338, bounds.xMax)
        assertEquals(0, bounds.yMin)
        assertEquals(400, bounds.yMax)
    }

    @Test
    fun sidebearingsAreMeasuredFromTheInkAndTheAdvance() {
        val sb = requireNotNull(bowlWithLongHandles().sidebearings())
        assertEquals(-37, sb.left)
        assertEquals(500 - 338, sb.right)
    }

    @Test
    fun aGlyphWithNoContoursHasNoBoundsAndNoSidebearings() {
        val empty = Glyph("space", 250, emptyList())
        assertNull(empty.outlineBounds())
        assertNull(empty.sidebearings())
    }

    @Test
    fun aSquareMeasuresItsCorners() {
        val square = Glyph("H", 400, listOf(closePolylineToContour(listOf(Point(50, 0), Point(350, 0), Point(350, 700), Point(50, 700)))))
        assertEquals(OutlineBounds(50, 0, 350, 700), square.outlineBounds())
        assertEquals(Sidebearings(50, 50), square.sidebearings())
    }

    @Test
    fun aQuadraticOutlineIsMeasuredToo() {
        val quad = Glyph("q", 200, listOf(Contour(listOf(on(10, 0), off(110, 200), on(210, 0)), CurveFormat.QUADRATIC)))
        val bounds = requireNotNull(quad.outlineBounds())
        assertEquals(10, bounds.xMin)
        assertEquals(210, bounds.xMax)
        assertEquals(100, bounds.yMax)
    }
}
