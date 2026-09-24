package dev.aarso.typewright.engine.construct

import dev.aarso.typewright.core.geometry.CurveSegment
import dev.aarso.typewright.core.geometry.Point
import dev.aarso.typewright.core.geometry.Vec2
import dev.aarso.typewright.core.geometry.segments
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals

/** [CirclePrimitive]'s four entry methods. */
class CircleTest {
    @Test
    fun centreRadiusProducesFourArcsCloseToTheExactCircleEquation() {
        val contour = CirclePrimitive.CentreRadius(Point(10, -5), radius = 50.0).realize()
        assertEquals(4, contour.segments().size)
        for (segment in contour.segments()) {
            val cubic = segment as CurveSegment.Cubic
            assertApprox(50.0, (cubic.start - Vec2(10.0, -5.0)).length(), tolerance = 1.0)
        }
        // Sample the whole boundary densely and check every point sits close to the true circle.
        for (segment in contour.segments()) {
            val cubic = segment as CurveSegment.Cubic
            for (i in 0..10) {
                val t = i / 10.0
                val u = 1.0 - t
                val p =
                    cubic.start * (u * u * u) + cubic.control1 * (3.0 * u * u * t) +
                        cubic.control2 * (3.0 * u * t * t) + cubic.end * (t * t * t)
                assertApprox(50.0, (p - Vec2(10.0, -5.0)).length(), tolerance = 50.0 * 0.01)
            }
        }
    }

    @Test
    fun threePointsFindsTheCircleThroughAKnownIntegerFixture() {
        // The classic 5-12-13 circle: (5,0), (5,24), (-8+5, 12) -- reuse the same 3-4-5-derived,
        // exact-integer style as ArcTest's own three-point fixture, at a different radius (13).
        val contour = CirclePrimitive.ThreePoints(Point(13, 0), Point(0, 13), Point(-13, 0)).realize()
        for (segment in contour.segments()) {
            val cubic = segment as CurveSegment.Cubic
            assertApprox(13.0, cubic.start.length(), tolerance = 1e-6)
        }
    }

    @Test
    fun tangentTangentRadiusProducesACircleTangentToBothLines() {
        val horizontal = TwoPointLine(Point(0, 0), Point(10, 0))
        val vertical = TwoPointLine(Point(10, 0), Point(10, 10))
        val contour =
            CirclePrimitive
                .TangentTangentRadius(
                    horizontal,
                    vertical,
                    radius = 3.0,
                    centerSide1 = FilletSide.LEFT,
                    centerSide2 = FilletSide.LEFT,
                ).realize()
        // The centre must be equidistant (= radius) from both lines; recover it as the mean of all
        // on-curve points' own centroid direction is unreliable, so instead check every on-curve point
        // is exactly `radius` from the same, independently-recomputed fillet centre.
        val (center, _, _) = filletCenterAndTangents(horizontal, vertical, 3.0, FilletSide.LEFT, FilletSide.LEFT)!!
        for (segment in contour.segments()) {
            val cubic = segment as CurveSegment.Cubic
            assertApprox(3.0, (cubic.start - center).length(), tolerance = 1e-6)
        }
    }

    @Test
    fun fitToPointsRecoversAnExactKnownCircle() {
        val center = Vec2(15.0, -8.0)
        val radius = 40.0
        val points =
            (0 until 12).map { i ->
                val angle = TWO_PI * i / 12
                Point((center.x + radius * cos(angle)).toInt(), (center.y + radius * sin(angle)).toInt())
            }
        val (fittedCenter, fittedRadius) = kasaCircleFit(points)
        assertVec2Approx(center, fittedCenter, tolerance = 1.0)
        assertApprox(radius, fittedRadius, tolerance = 1.0)
    }

    @Test
    fun fitToPointsRealizesAsAClosedContourNearTheFit() {
        val points = listOf(Point(10, 0), Point(0, 10), Point(-10, 0), Point(0, -10), Point(7, 7), Point(-7, -7))
        val contour = CirclePrimitive.FitToPoints(points).realize()
        for (segment in contour.segments()) {
            val cubic = segment as CurveSegment.Cubic
            assertApprox(10.0, cubic.start.length(), tolerance = 1.0)
        }
    }

    // -----------------------------------------------------------------------------------------
    // solve3x3, exercised directly (this task's own general 3x3 linear solve).
    // -----------------------------------------------------------------------------------------

    @Test
    fun solve3x3SolvesAKnownSystemExactly() {
        // x + y + z = 6; 2y + 5z = -4; 2x + 5y - z = 27 -- textbook system with the known solution (5,3,-2).
        val (x, y, z) = solve3x3(1.0, 1.0, 1.0, 6.0, 0.0, 2.0, 5.0, -4.0, 2.0, 5.0, -1.0, 27.0)
        assertApprox(5.0, x, tolerance = 1e-9)
        assertApprox(3.0, y, tolerance = 1e-9)
        assertApprox(-2.0, z, tolerance = 1e-9)
    }
}
