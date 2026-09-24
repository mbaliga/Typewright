package dev.aarso.typewright.engine.construct

import dev.aarso.typewright.core.geometry.CurveSegment
import dev.aarso.typewright.core.geometry.Point
import dev.aarso.typewright.core.geometry.Vec2
import dev.aarso.typewright.core.geometry.pointAt
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** The shared, cross-primitive geometry every entry method in this file set builds on. */
class PrimitiveGeometryTest {
    @Test
    fun circumcenterIsEquidistantFromAllThreePoints() {
        val a = Vec2(0.0, 0.0)
        val b = Vec2(10.0, 0.0)
        val c = Vec2(4.0, 8.0)
        val center = circumcenter(a, b, c)
        assertApprox((a - center).length(), (b - center).length(), tolerance = 1e-9)
        assertApprox((a - center).length(), (c - center).length(), tolerance = 1e-9)
    }

    @Test
    fun circumcenterOfARightTriangleIsItsHypotenuseMidpoint() {
        // A classic closed-form fact: the circumcenter of a right triangle sits exactly at the
        // midpoint of its hypotenuse.
        val a = Vec2(0.0, 0.0)
        val b = Vec2(6.0, 0.0)
        val c = Vec2(0.0, 8.0)
        val center = circumcenter(a, b, c)
        assertVec2Approx(Vec2(3.0, 4.0), center, tolerance = 1e-9)
    }

    @Test
    fun circumcenterRejectsCollinearPoints() {
        assertFailsWith<IllegalArgumentException> { circumcenter(Vec2(0.0, 0.0), Vec2(5.0, 0.0), Vec2(10.0, 0.0)) }
    }

    @Test
    fun lineLineIntersectionFindsTheExactCrossing() {
        val point = lineLineIntersection(Vec2(0.0, 0.0), Vec2(1.0, 1.0), Vec2(10.0, 0.0), Vec2(-1.0, 1.0))
        assertVec2Approx(Vec2(5.0, 5.0), point!!, tolerance = 1e-9)
    }

    @Test
    fun lineLineIntersectionIsNullForParallelLines() {
        val point = lineLineIntersection(Vec2(0.0, 0.0), Vec2(1.0, 0.0), Vec2(0.0, 5.0), Vec2(1.0, 0.0))
        kotlin.test.assertEquals(null, point)
    }

    @Test
    fun tangentAtMatchesACentralDifferenceEstimateOfPointAt() {
        val cubic = CurveSegment.Cubic(Vec2(0.0, 0.0), Vec2(10.0, 20.0), Vec2(30.0, 20.0), Vec2(40.0, 0.0))
        val h = 1e-5
        for (t in listOf(0.2, 0.5, 0.8)) {
            val numeric = (cubic.pointAt(t + h) - cubic.pointAt(t - h)) * (1.0 / (2.0 * h))
            val exact = cubic.tangentAt(t)
            assertApprox(numeric.x, exact.x, tolerance = 1e-2, message = "t=$t x")
            assertApprox(numeric.y, exact.y, tolerance = 1e-2, message = "t=$t y")
        }
    }

    @Test
    fun normalizeAngleToPositiveTwoPiStaysInRange() {
        assertApprox(PI / 2.0, normalizeAngleToPositiveTwoPi(PI / 2.0), tolerance = 1e-9)
        assertApprox(3.0 * PI / 2.0, normalizeAngleToPositiveTwoPi(-PI / 2.0), tolerance = 1e-9)
        assertApprox(TWO_PI, normalizeAngleToPositiveTwoPi(0.0), tolerance = 1e-9)
        assertApprox(PI, normalizeAngleToPositiveTwoPi(PI + TWO_PI), tolerance = 1e-9)
    }

    @Test
    fun straightCubicHasOnLineDegenerateControlPointsAtTheThirds() {
        val segment = straightCubic(Vec2(0.0, 0.0), Vec2(30.0, 0.0))
        assertVec2Approx(Vec2(10.0, 0.0), segment.control1, tolerance = 1e-9)
        assertVec2Approx(Vec2(20.0, 0.0), segment.control2, tolerance = 1e-9)
    }

    @Test
    fun buildClosedCubicContourRoundTripsSegmentEndpoints() {
        val segments =
            listOf(
                straightCubic(Vec2(0.0, 0.0), Vec2(10.0, 0.0)),
                straightCubic(Vec2(10.0, 0.0), Vec2(10.0, 10.0)),
                straightCubic(Vec2(10.0, 10.0), Vec2(0.0, 10.0)),
                straightCubic(Vec2(0.0, 10.0), Vec2(0.0, 0.0)),
            )
        val contour = buildClosedCubicContour(segments)
        kotlin.test.assertEquals(12, contour.points.size)
        kotlin.test.assertEquals(Point(0, 0), contour.points[0].point)
    }

    @Test
    fun filletCenterAndTangentsIsNullForParallelLines() {
        val a = TwoPointLine(Point(0, 0), Point(10, 0))
        val b = TwoPointLine(Point(0, 5), Point(10, 5))
        kotlin.test.assertEquals(null, filletCenterAndTangents(a, b, 2.0, FilletSide.LEFT, FilletSide.LEFT))
    }
}
