package dev.aarso.typewright.engine.construct

import dev.aarso.typewright.core.geometry.CurveSegment
import dev.aarso.typewright.core.geometry.Point
import dev.aarso.typewright.core.geometry.Vec2
import dev.aarso.typewright.core.geometry.midpoint
import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** [LinePrimitive]'s three entry methods. */
class LineTest {
    @Test
    fun twoPointsIsTakenDirectly() {
        val line = LinePrimitive.TwoPoints(Point(1, 2), Point(9, 20)).realize()
        assertVec2Approx(Vec2(1.0, 2.0), line.start)
        assertVec2Approx(Vec2(9.0, 20.0), line.end)
    }

    @Test
    fun twoPointsRejectsTwoIdenticalPoints() {
        assertFailsWith<IllegalArgumentException> { LinePrimitive.TwoPoints(Point(5, 5), Point(5, 5)) }
    }

    @Test
    fun pointAngleLengthReachesTheExactTrigonometricEndpoint() {
        val line = LinePrimitive.PointAngleLength(Point(0, 0), angleRadians = PI / 4.0, length = 10.0).realize()
        val expectedEnd = Vec2(10.0 * sqrt(2.0) / 2.0, 10.0 * sqrt(2.0) / 2.0)
        assertVec2Approx(Vec2(0.0, 0.0), line.start)
        assertVec2Approx(expectedEnd, line.end, tolerance = 1e-9)
        assertApprox(10.0, (line.end - line.start).length(), tolerance = 1e-9)
    }

    @Test
    fun pointAngleLengthAlongTheXAxis() {
        val line = LinePrimitive.PointAngleLength(Point(5, 5), angleRadians = 0.0, length = 20.0).realize()
        assertVec2Approx(Vec2(25.0, 5.0), line.end, tolerance = 1e-9)
    }

    @Test
    fun tangentToAStraightLineCurveIsParallelToIt() {
        val curve = CurveSegment.Line(Vec2(0.0, 0.0), Vec2(10.0, 10.0))
        val tangentLine = LinePrimitive.TangentToCurve(curve, t = 0.5, length = 4.0).realize()
        val direction = (tangentLine.end - tangentLine.start).normalizedOrNull()!!
        val curveDirection = (curve.end - curve.start).normalizedOrNull()!!
        assertVec2Approx(curveDirection, direction, tolerance = 1e-9)
        // Centred on the curve's own midpoint.
        assertVec2Approx(Vec2(5.0, 5.0), midpoint(tangentLine.start, tangentLine.end), tolerance = 1e-9)
        assertApprox(4.0, (tangentLine.end - tangentLine.start).length(), tolerance = 1e-9)
    }

    @Test
    fun tangentToACircularArcIsPerpendicularToItsOwnRadius() {
        // A quarter-turn arc from (10, 0): at t=0 (the arc's own start point) the tangent must be
        // exactly perpendicular to the radius from the arc's centre -- the defining property of a
        // circle's own tangent line, checked directly rather than merely re-deriving the same formula.
        val arc = CircularArc(Vec2(0.0, 0.0), 10.0, 0.0, PI / 2.0)
        val segment = arc.toCubicSegments().single()
        val tangentLine = LinePrimitive.TangentToCurve(segment, t = 0.0, length = 6.0).realize()
        val direction = (tangentLine.end - tangentLine.start).normalizedOrNull()!!
        val radiusDirection = (segment.start - arc.center).normalizedOrNull()!!
        assertApprox(0.0, direction.dot(radiusDirection), tolerance = 1e-6, message = "tangent must be perpendicular to the radius")
    }

    @Test
    fun tangentToACurveRequiresTInRange() {
        val curve = CurveSegment.Line(Vec2(0.0, 0.0), Vec2(10.0, 0.0))
        assertFailsWith<IllegalArgumentException> { LinePrimitive.TangentToCurve(curve, t = 1.5, length = 1.0) }
    }
}
