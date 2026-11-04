// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.construct

import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.Direction
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.direction
import com.asoc.typewright.core.geometry.signedArea
import com.asoc.typewright.core.geometry.writtenCount
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The box primitives: the node counts a type designer expects, on the box's own edges. */
class BoxPrimitivesTest {
    private fun onCurve(c: Contour): List<Point> = c.points.filter { it.onCurve }.map { it.point }

    @Test
    fun aCircleIsFourOnCurvePointsOnTheExtremesWithHandlesAndNothingElse() {
        val circle = ellipseInBox(0.0, 0.0, 500.0, 500.0)
        assertEquals(4, onCurve(circle).size)
        assertEquals(8, circle.points.count { !it.onCurve })
        assertEquals(setOf(Point(500, 250), Point(250, 500), Point(0, 250), Point(250, 0)), onCurve(circle).toSet())
        assertEquals(Direction.COUNTER_CLOCKWISE, circle.direction())
        // The area of a four-cubic circle is within 0.1% of pi r^2.
        assertEquals(PI * 250.0 * 250.0, circle.signedArea(), PI * 250.0 * 250.0 * 0.001)
        val count = circle.writtenCount()
        assertEquals(4, count.onCurveEquivalent)
        assertEquals(8, count.offCurve)
    }

    @Test
    fun anEllipseFillsItsBoxAndKeepsFourNodes() {
        val ellipse = ellipseInBox(-100.0, -10.0, 300.0, 490.0)
        assertEquals(setOf(Point(300, 240), Point(100, 490), Point(-100, 240), Point(100, -10)), onCurve(ellipse).toSet())
    }

    @Test
    fun aRectangleIsFourCornersWithStraightSides() {
        val rect = rectangleInBox(10.0, 0.0, 210.0, 400.0)
        assertEquals(setOf(Point(10, 0), Point(210, 0), Point(210, 400), Point(10, 400)), onCurve(rect).toSet())
        val count = rect.writtenCount()
        assertEquals(4, count.onCurveEquivalent)
        assertEquals(0, count.offCurve, "straight sides are written as lines, with no off-curve points")
        assertEquals(200.0 * 400.0, rect.signedArea(), 1e-6)
    }

    @Test
    fun aRoundedRectangleIsEightNodesAndTheRadiusIsHeldToHalfTheShorterSide() {
        val rounded = roundedRectangleInBox(0.0, 0.0, 200.0, 100.0, 24.0)
        assertEquals(8, onCurve(rounded).size)
        assertTrue(onCurve(rounded).contains(Point(24, 0)) && onCurve(rounded).contains(Point(0, 24)))
        // Asked for more than fits, the corners meet in the middle of the short sides: 50 units.
        val capped = roundedRectangleInBox(0.0, 0.0, 200.0, 100.0, 500.0)
        assertTrue(onCurve(capped).contains(Point(50, 0)) && onCurve(capped).contains(Point(0, 50)))
        // A radius of nothing is the plain rectangle.
        assertEquals(4, onCurve(roundedRectangleInBox(0.0, 0.0, 200.0, 100.0, 0.0)).size)
    }

    @Test
    fun aBoxWithoutAreaIsRefused() {
        assertFailsWith<IllegalArgumentException> { ellipseInBox(0.0, 0.0, 0.0, 10.0) }
        assertFailsWith<IllegalArgumentException> { rectangleInBox(5.0, 5.0, 4.0, 9.0) }
        assertFailsWith<IllegalArgumentException> { roundedRectangleInBox(0.0, 0.0, 10.0, 10.0, -1.0) }
    }
}
