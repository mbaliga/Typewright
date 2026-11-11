// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.construct

import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.ContourPoint
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.signedArea
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** [booleanMany]: the four operations across two and three rectangles, whose areas are computable by hand. */
class BooleanManyTest {
    private fun area(contours: List<Contour>): Double = contours.sumOf { it.signedArea() }

    private fun done(result: BooleanManyResult): List<Contour> = assertIs<BooleanManyResult.Done>(result).contours

    private fun assertAreaIs(
        expected: Double,
        contours: List<Contour>,
    ) = assertTrue(abs(area(contours) - expected) < 1.0, "area ${area(contours)}, expected $expected")

    // A 100 x 100 square at the origin and a 100 x 100 square shifted by (50, 50): they overlap in a 50 x 50 square.
    private val a = rectangleContour(0, 0, 100, 100)
    private val b = rectangleContour(50, 50, 150, 150)

    @Test
    fun unionOfTwoOverlappingSquaresHasTheAreaOfBothMinusTheOverlap() {
        val result = done(booleanMany(listOf(a, b), BooleanOp.UNION))
        assertEquals(1, result.size)
        assertAreaIs(10_000.0 + 10_000.0 - 2_500.0, result)
    }

    @Test
    fun subtractTakesTheLaterContoursFromTheFirstAndTheOrderMatters() {
        assertAreaIs(10_000.0 - 2_500.0, done(booleanMany(listOf(a, b), BooleanOp.SUBTRACT)))
        assertAreaIs(10_000.0 - 2_500.0, done(booleanMany(listOf(b, a), BooleanOp.SUBTRACT)))
        val aMinusB = done(booleanMany(listOf(a, b), BooleanOp.SUBTRACT))
        val bMinusA = done(booleanMany(listOf(b, a), BooleanOp.SUBTRACT))
        val minX = { cs: List<Contour> -> cs.minOf { c -> c.points.minOf { it.point.x } } }
        assertEquals(0, minX(aMinusB), "a minus b stays on a's side")
        assertEquals(50, minX(bMinusA), "b minus a stays on b's side")
    }

    @Test
    fun intersectKeepsOnlyTheOverlapAsOneSquare() {
        val result = done(booleanMany(listOf(a, b), BooleanOp.INTERSECT))
        assertEquals(1, result.size)
        assertAreaIs(2_500.0, result)
        assertEquals(4, result.single().points.count { it.onCurve }, "a square stays four nodes")
    }

    @Test
    fun excludeKeepsEverythingButTheOverlap() {
        assertAreaIs(10_000.0 + 10_000.0 - 2 * 2_500.0, done(booleanMany(listOf(a, b), BooleanOp.EXCLUDE)))
    }

    @Test
    fun unionOfThreeOverlappingSquaresFoldsInOrder() {
        val c = rectangleContour(100, 0, 200, 100)
        val result = done(booleanMany(listOf(a, b, c), BooleanOp.UNION))
        // b overlaps a and c by 50 x 50 each, and a and c only touch: 3 * 10,000 - 2 * 2,500.
        assertEquals(1, result.size)
        assertAreaIs(25_000.0, result)
    }

    @Test
    fun subtractingASquareFromInsideAnotherLeavesACounterWoundTheOtherWay() {
        val hole = rectangleContour(25, 25, 75, 75)
        val result = done(booleanMany(listOf(a, hole), BooleanOp.SUBTRACT))
        assertEquals(2, result.size)
        assertAreaIs(10_000.0 - 2_500.0, result)
        assertEquals(1, result.count { it.signedArea() > 0 }, "one outer, anticlockwise")
        assertEquals(1, result.count { it.signedArea() < 0 }, "one counter, clockwise")
    }

    @Test
    fun unionOfCirclesKeepsTheirCurvesWithoutTurningThemIntoManyNodes() {
        val result = done(booleanMany(listOf(circleContour(0.0, 0.0, 100.0), circleContour(120.0, 0.0, 100.0)), BooleanOp.UNION))
        assertEquals(1, result.size)
        val nodes = result.single().points.count { it.onCurve }
        assertTrue(nodes <= 8, "two circles joined are $nodes nodes: the original curves are kept, not refitted")
    }

    @Test
    fun fewerThanTwoContoursIsRefusedWithAReason() {
        val refused = assertIs<BooleanManyResult.Refused>(booleanMany(listOf(a), BooleanOp.UNION))
        assertTrue(refused.reason.contains("two or more"))
    }

    @Test
    fun intersectingShapesThatDoNotOverlapIsRefusedNotEmpty() {
        val far = rectangleContour(500, 500, 600, 600)
        val refused = assertIs<BooleanManyResult.Refused>(booleanMany(listOf(a, far), BooleanOp.INTERSECT))
        assertTrue(refused.reason.contains("do not all overlap"), refused.reason)
    }

    @Test
    fun subtractingASquareThatCoversTheWholeIsRefused() {
        val cover = rectangleContour(-10, -10, 110, 110)
        val refused = assertIs<BooleanManyResult.Refused>(booleanMany(listOf(a, cover), BooleanOp.SUBTRACT))
        assertTrue(refused.reason.contains("Nothing is left"), refused.reason)
    }

    @Test
    fun aQuadraticContourIsRefused() {
        val quadratic =
            Contour(
                listOf(
                    ContourPoint(Point(0, 0), true),
                    ContourPoint(Point(50, 80), false),
                    ContourPoint(Point(100, 0), true),
                ),
                CurveFormat.QUADRATIC,
            )
        val refused = assertIs<BooleanManyResult.Refused>(booleanMany(listOf(a, quadratic), BooleanOp.UNION))
        assertTrue(refused.reason.contains("quadratic"))
    }
}
