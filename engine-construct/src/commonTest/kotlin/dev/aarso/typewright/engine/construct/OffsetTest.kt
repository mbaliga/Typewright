// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.engine.construct

import dev.aarso.typewright.core.geometry.Contour
import dev.aarso.typewright.core.geometry.Vec2
import dev.aarso.typewright.core.geometry.reverse
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Task P5a-foundations item 1: offsetting a closed cubic Contour, on shapes whose correct offset is computable analytically. */
class OffsetTest {
    // -----------------------------------------------------------------------------------------
    // cornerOffset: the documented corner rule, tested directly and exactly.
    // -----------------------------------------------------------------------------------------

    @Test
    fun cornerOffsetAtARightAngleGivesTheExactMiterLength() {
        // Two edges meeting at a right angle: incoming along +x, outgoing along +y (a CCW
        // "bottom-left" corner, like a square's own corner). The exact miter length is
        // distance * sqrt(2), along the 45-degree bisector.
        val result = cornerOffset(Vec2(0.0, 0.0), Vec2(1.0, 0.0), Vec2(0.0, 1.0), 10.0, miterLimit = 4.0)
        assertEquals(1, result.size)
        // rightNormal((1,0)) = (0,-1); rightNormal((0,1)) = (1,0); bisector = normalize((1,-1));
        // L = distance / cos(45deg) = 10*sqrt(2) -> vertex + L*bisector = (10, -10) exactly.
        assertVec2Approx(Vec2(10.0, -10.0), result[0])
    }

    @Test
    fun cornerOffsetOnAStraightRunGivesTheSimplePerpendicularOffset() {
        // No turn at all: incoming and outgoing directions equal.
        val result = cornerOffset(Vec2(5.0, 5.0), Vec2(1.0, 0.0), Vec2(1.0, 0.0), 3.0, miterLimit = 4.0)
        assertEquals(1, result.size)
        assertVec2Approx(Vec2(5.0, 5.0 - 3.0), result[0])
    }

    @Test
    fun cornerOffsetAtAnOpenEndpointUsesTheOneAvailableDirection() {
        val result = cornerOffset(Vec2(0.0, 0.0), null, Vec2(1.0, 0.0), 5.0, miterLimit = 4.0)
        assertEquals(1, result.size)
        assertVec2Approx(Vec2(0.0, -5.0), result[0])
    }

    @Test
    fun cornerOffsetPastTheMiterLimitFallsBackToABevel() {
        // A near-reversal: incoming +x, outgoing almost -x (179 degrees), which would need a
        // miter length far beyond any reasonable limit.
        val nearlyReversed = Vec2(-0.999, 0.045)
        val result = cornerOffset(Vec2(0.0, 0.0), Vec2(1.0, 0.0), nearlyReversed, 10.0, miterLimit = 4.0)
        assertEquals(2, result.size, "a near-reversal must fall back to the two-point bevel")
        for (p in result) {
            assertTrue(hypot(p.x, p.y) <= 10.0 + 1e-6, "a bevel point must stay exactly at the offset distance from the vertex: $p")
        }
    }

    private fun assertVec2Approx(
        expected: Vec2,
        actual: Vec2,
        tolerance: Double = 1e-9,
    ) {
        assertTrue(abs(expected.x - actual.x) <= tolerance && abs(expected.y - actual.y) <= tolerance, "expected $expected, got $actual")
    }

    // -----------------------------------------------------------------------------------------
    // offsetContour: circle outward -> radius + d.
    // -----------------------------------------------------------------------------------------

    @Test
    fun circleOffsetOutwardMatchesRadiusPlusDistanceWithinTolerance() {
        val radius = 200.0
        val distance = 50.0
        val circle = circleContour(cx = 0.0, cy = 0.0, radius = radius)
        val offset = offsetContour(circle, distance)
        for (p in offset.flattenToPolyline(0.5)) {
            val r = hypot(p.x, p.y)
            assertTrue(abs(r - (radius + distance)) < 3.0, "offset point $p at radius $r, expected close to ${radius + distance}")
        }
    }

    @Test
    fun circleOffsetInwardMatchesRadiusMinusDistanceWithinTolerance() {
        val radius = 200.0
        val distance = 50.0
        val circle = circleContour(cx = 0.0, cy = 0.0, radius = radius)
        val offset = offsetContour(circle, -distance)
        for (p in offset.flattenToPolyline(0.5)) {
            val r = hypot(p.x, p.y)
            assertTrue(abs(r - (radius - distance)) < 3.0, "offset point $p at radius $r, expected close to ${radius - distance}")
        }
    }

    // -----------------------------------------------------------------------------------------
    // offsetContour: square outward -> side + 2d, exact square corners preserved.
    // -----------------------------------------------------------------------------------------

    @Test
    fun squareOffsetOutwardGrowsBySideAndKeepsSquareCorners() {
        val side = 100
        val distance = 20.0
        val square = rectangleContour(0, 0, side, side)
        val offset = offsetContour(square, distance)
        val onCurve = offset.points.filter { it.onCurve }.map { it.point }
        val expectedCorners = setOf(Pair(-20, -20), Pair(120, -20), Pair(120, 120), Pair(-20, 120))
        assertEquals(4, onCurve.size, "a square offset outward keeps exactly 4 corners: $onCurve")
        for (c in onCurve) {
            assertTrue(Pair(c.x, c.y) in expectedCorners, "corner $c is not one of the expected exact corners $expectedCorners")
        }
        // Every off-curve control must be on-line with its own segment (still perfectly straight
        // sides), exactly like a fitted straight cubic elsewhere in this codebase.
        for (i in offset.points.indices step 3) {
            val start = offset.points[i].point.toVec2()
            val c1 = offset.points[i + 1].point.toVec2()
            val c2 = offset.points[i + 2].point.toVec2()
            val end = offset.points[(i + 3) % offset.points.size].point.toVec2()
            val chord = end - start
            if (chord.length() > 0.0) {
                assertTrue(abs(chord.cross(c1 - start)) / chord.length() < 1.5, "control1 $c1 not on line $start->$end")
                assertTrue(abs(chord.cross(c2 - start)) / chord.length() < 1.5, "control2 $c2 not on line $start->$end")
            }
        }
    }

    @Test
    fun squareOffsetInwardByLessThanHalfSideShrinksCorrectly() {
        val side = 100
        val distance = 30.0
        val square = rectangleContour(0, 0, side, side)
        val offset = offsetContour(square, -distance)
        val onCurve = offset.points.filter { it.onCurve }.map { it.point }
        val expectedCorners = setOf(Pair(30, 30), Pair(70, 30), Pair(70, 70), Pair(30, 70))
        assertEquals(4, onCurve.size)
        for (c in onCurve) {
            assertTrue(Pair(c.x, c.y) in expectedCorners, "corner $c is not one of the expected exact corners $expectedCorners")
        }
    }

    // -----------------------------------------------------------------------------------------
    // offsetContour: square inward past half the side degenerates sensibly.
    // -----------------------------------------------------------------------------------------

    @Test
    fun squareOffsetInwardPastHalfSideCollapsesToAPoint() {
        val side = 100
        val square = rectangleContour(0, 0, side, side)
        val offset = offsetContour(square, -60.0) // more than half of 100
        // "Sensibly" (offsetContour's own documented rule): a degenerate single-point contour at
        // the original polyline's own centroid, rather than a self-intersecting shape.
        assertEquals(3, offset.points.size, "a degenerate offset is exactly 3 repeated points, one cubic (on, off, off) triple")
        val onlyPoint = offset.points[0].point
        assertTrue(offset.points.all { it.point == onlyPoint }, "every point of a degenerate offset is the same point")
        assertTrue(abs(onlyPoint.x - 50) <= 1 && abs(onlyPoint.y - 50) <= 1, "collapses near the square's own centroid: $onlyPoint")
    }

    @Test
    fun squareOffsetInwardByExactlyHalfSideAlsoCollapses() {
        val side = 100
        val square = rectangleContour(0, 0, side, side)
        val offset = offsetContour(square, -50.0)
        assertEquals(3, offset.points.size)
    }

    // -----------------------------------------------------------------------------------------
    // A clockwise (inner) ring: positive distance still means "outward from its own material".
    // -----------------------------------------------------------------------------------------

    @Test
    fun clockwiseInnerRingOffsetOutwardGrowsTheHole() {
        // A CW circle (reverse of the CCW default) represents an inner ring/counter, per
        // CLAUDE.md's inner-CW convention. "Outward" for a hole means growing it -- expanding the
        // counter, thinning the material around it -- so a positive distance here should *grow*
        // the ring's own radius, matching offsetContour's own documented winding-aware sign rule
        // (never shrink it, which is what a naive, winding-unaware sign convention would do).
        val radius = 150.0
        val distance = 20.0
        val ccw = circleContour(cx = 0.0, cy = 0.0, radius = radius)
        val cw = reverseToClockwise(ccw)
        val offset = offsetContour(cw, distance)
        for (p in offset.flattenToPolyline(0.5)) {
            val r = hypot(p.x, p.y)
            assertTrue(abs(r - (radius + distance)) < 3.0, "CW ring offset point $p at radius $r, expected close to ${radius + distance}")
        }
    }

    private fun reverseToClockwise(contour: Contour): Contour = contour.reverse()
}
