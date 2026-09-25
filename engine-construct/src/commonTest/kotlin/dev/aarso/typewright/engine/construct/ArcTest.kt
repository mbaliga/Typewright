// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.engine.construct

import dev.aarso.typewright.core.geometry.Point
import dev.aarso.typewright.core.geometry.Vec2
import dev.aarso.typewright.core.geometry.pointAt
import dev.aarso.typewright.core.geometry.segments
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every one of [ArcPrimitive]'s six entry methods, each checked against a closed-form circle/arc
 * fact (a swept angle, a chord-length/radius relationship, an exact tangency), never merely "it
 * compiled" (this task's own instruction, since arc geometry is its most failure-prone corner).
 */
class ArcTest {
    // -----------------------------------------------------------------------------------------
    // Entry method 1: three points.
    // -----------------------------------------------------------------------------------------

    @Test
    fun threePointsFindsTheCircleAndSweepsCounterClockwiseThroughTheMiddlePoint() {
        // Three exact integer points on the radius-5 circle at the origin (3-4-5 triangle): 0, ~53.13
        // and 90 degrees.
        val arc =
            ArcPrimitive
                .ThreePoints(
                    start = Point(5, 0),
                    through = Point(3, 4),
                    end = Point(0, 5),
                ).realize()
        assertVec2Approx(Vec2(0.0, 0.0), arc.center, tolerance = 1e-9)
        assertApprox(5.0, arc.radius, tolerance = 1e-9)
        assertTrue(arc.sweepRadians > 0.0, "through lies between start and end going counter-clockwise, so the sweep must be positive")
        assertApprox(PI / 2.0, arc.sweepRadians, tolerance = 1e-9)
    }

    @Test
    fun threePointsGoesClockwiseWhenThroughIsOnTheOtherSide() {
        // Same three points, through swapped to the far (clockwise) side: the arc must go the long way.
        val arc =
            ArcPrimitive
                .ThreePoints(
                    start = Point(10, 0),
                    through = Point(-7, -7),
                    end = Point(0, 10),
                ).realize()
        assertTrue(arc.sweepRadians < 0.0, "through only lies on the clockwise side, so the sweep must be negative")
        // The clockwise route from 0 degrees through ~225 degrees to 90 degrees sweeps 270 degrees.
        assertApprox(-3.0 * PI / 2.0, arc.sweepRadians, tolerance = 0.1)
    }

    @Test
    fun threePointsActuallyPassesThroughAllThreePoints() {
        val start = Point(0, 5)
        val through = Point(5, 0)
        val end = Point(0, -5)
        val arc = ArcPrimitive.ThreePoints(start, through, end).realize()
        assertVec2Approx(start.toVec2(), arc.pointAt(0.0), tolerance = 1e-6)
        assertVec2Approx(end.toVec2(), arc.pointAt(1.0), tolerance = 1e-6)
        // through must land exactly on the arc's own circle, at some interior parameter.
        assertApprox(arc.radius, (through.toVec2() - arc.center).length(), tolerance = 1e-6)
        val throughAngle = atan2(through.toVec2().y - arc.center.y, through.toVec2().x - arc.center.x)
        val expectedPoint = arc.center + Vec2(cos(throughAngle), sin(throughAngle)) * arc.radius
        assertVec2Approx(through.toVec2(), expectedPoint, tolerance = 1e-6)
    }

    // -----------------------------------------------------------------------------------------
    // Entry method 2: start, centre, end.
    // -----------------------------------------------------------------------------------------

    @Test
    fun startCenterEndCounterClockwiseSweepsAQuarterTurn() {
        val arc = ArcPrimitive.StartCenterEnd(Point(10, 0), Point(0, 0), Point(0, 10)).realize()
        assertApprox(10.0, arc.radius)
        assertApprox(PI / 2.0, arc.sweepRadians, tolerance = 1e-9)
        assertVec2Approx(Vec2(0.0, 10.0), arc.pointAt(1.0))
    }

    @Test
    fun startCenterEndClockwiseSweepsTheComplementaryThreeQuarterTurn() {
        val arc =
            ArcPrimitive
                .StartCenterEnd(
                    Point(10, 0),
                    Point(0, 0),
                    Point(0, 10),
                    direction = ArcPrimitive.Direction.CLOCKWISE,
                ).realize()
        assertApprox(-3.0 * PI / 2.0, arc.sweepRadians, tolerance = 1e-9)
        // Both directions must still end at exactly the same point (the ray towards `end`).
        assertVec2Approx(Vec2(0.0, 10.0), arc.pointAt(1.0), tolerance = 1e-6)
    }

    // -----------------------------------------------------------------------------------------
    // Entry method 3: start, end, radius (the four largeArc/direction combinations).
    // -----------------------------------------------------------------------------------------

    @Test
    fun startEndRadiusEndpointsAlwaysLandExactlyOnStartAndEnd() {
        val start = Point(0, 0)
        val end = Point(20, 0)
        for (largeArc in listOf(false, true)) {
            for (ccw in listOf(false, true)) {
                val arc = ArcPrimitive.StartEndRadius(start, end, radius = 15.0, largeArc = largeArc, counterClockwise = ccw).realize()
                assertVec2Approx(start.toVec2(), arc.pointAt(0.0), tolerance = 1e-6, message = "largeArc=$largeArc ccw=$ccw start")
                assertVec2Approx(end.toVec2(), arc.pointAt(1.0), tolerance = 1e-6, message = "largeArc=$largeArc ccw=$ccw end")
                assertApprox(15.0, arc.radius, tolerance = 1e-9)
                assertEquals(ccw, arc.sweepRadians > 0.0, "largeArc=$largeArc ccw=$ccw sweep sign")
                assertEquals(largeArc, kotlin.math.abs(arc.sweepRadians) > PI, "largeArc=$largeArc ccw=$ccw sweep magnitude")
            }
        }
    }

    @Test
    fun startEndRadiusSweepMatchesTheClosedFormChordLengthRelationship() {
        // chordLength = 2 * radius * sin(|sweep| / 2) for any circular arc -- the standard relationship
        // between a chord and its own central angle, checked directly against the solved arc.
        val start = Point(3, 4)
        val end = Point(-5, 12)
        val chordLength = (end.toVec2() - start.toVec2()).length()
        val arc = ArcPrimitive.StartEndRadius(start, end, radius = 20.0, largeArc = false, counterClockwise = true).realize()
        assertApprox(chordLength, 2.0 * arc.radius * sin(kotlin.math.abs(arc.sweepRadians) / 2.0), tolerance = 1e-6)
    }

    @Test
    fun startEndRadiusRejectsARadiusSmallerThanHalfTheChord() {
        assertTrue(
            runCatching {
                ArcPrimitive.StartEndRadius(Point(0, 0), Point(20, 0), radius = 5.0).realize()
            }.isFailure,
        )
    }

    // -----------------------------------------------------------------------------------------
    // Entry method 4: start, end, bulge (DXF convention).
    // -----------------------------------------------------------------------------------------

    @Test
    fun bulgeOfOneIsExactlyASemicircle() {
        val arc = ArcPrimitive.StartEndBulge(Point(-10, 0), Point(10, 0), bulge = 1.0).realize()
        assertApprox(PI, arc.sweepRadians, tolerance = 1e-9)
        assertApprox(10.0, arc.radius, tolerance = 1e-6)
    }

    @Test
    fun bulgeOfTanPiOverEightIsExactlyAQuarterTurnCounterClockwise() {
        val bulge = kotlin.math.tan(PI / 8.0)
        val arc = ArcPrimitive.StartEndBulge(Point(0, 0), Point(10, 10), bulge = bulge).realize()
        assertApprox(PI / 2.0, arc.sweepRadians, tolerance = 1e-6)
        assertTrue(arc.sweepRadians > 0.0)
    }

    @Test
    fun negativeBulgeSweepsClockwise() {
        val bulge = -kotlin.math.tan(PI / 8.0)
        val arc = ArcPrimitive.StartEndBulge(Point(0, 0), Point(10, 10), bulge = bulge).realize()
        assertApprox(-PI / 2.0, arc.sweepRadians, tolerance = 1e-6)
    }

    @Test
    fun bulgeRoundTripsThroughItsOwnClosedFormChordRelationship() {
        val start = Point(0, 0)
        val end = Point(10, 0)
        val bulge = 0.35
        val theta = 4.0 * kotlin.math.atan(bulge)
        val arc = ArcPrimitive.StartEndBulge(start, end, bulge).realize()
        assertApprox(theta, arc.sweepRadians, tolerance = 1e-9)
        val chordLength = (end.toVec2() - start.toVec2()).length()
        assertApprox(chordLength, 2.0 * arc.radius * sin(kotlin.math.abs(arc.sweepRadians) / 2.0), tolerance = 1e-6)
    }

    // -----------------------------------------------------------------------------------------
    // Entry method 5: tangent, tangent, radius (the fillet).
    // -----------------------------------------------------------------------------------------

    @Test
    fun filletIsExactlyTangentToBothLinesAndItsOwnTangentPointsLieOnThem() {
        // A right-angle corner: a horizontal line along y=0 and a vertical line along x=10, meeting at (10, 0).
        val horizontal = TwoPointLine(Point(0, 0), Point(10, 0))
        val vertical = TwoPointLine(Point(10, 0), Point(10, 10))
        val arc =
            ArcPrimitive
                .TangentTangentRadius(
                    horizontal,
                    vertical,
                    radius = 2.0,
                    centerSide1 = FilletSide.LEFT,
                    centerSide2 = FilletSide.LEFT,
                ).realize()
        assertApprox(2.0, arc.radius)
        // Tangency: the centre sits exactly `radius` away from each original (infinite) line.
        assertApprox(2.0, distanceFromPointToLine(arc.center, horizontal.a.toVec2(), horizontal.direction()), tolerance = 1e-9)
        assertApprox(2.0, distanceFromPointToLine(arc.center, vertical.a.toVec2(), vertical.direction()), tolerance = 1e-9)
        // A fillet is always the minor (convex, corner-rounding) arc.
        assertTrue(kotlin.math.abs(arc.sweepRadians) <= PI + 1e-9)
        // Its own two endpoints must themselves sit exactly on the two original lines.
        assertApprox(0.0, distanceFromPointToLine(arc.pointAt(0.0), horizontal.a.toVec2(), horizontal.direction()), tolerance = 1e-6)
        assertApprox(0.0, distanceFromPointToLine(arc.pointAt(1.0), vertical.a.toVec2(), vertical.direction()), tolerance = 1e-6)
    }

    @Test
    fun filletBetweenParallelLinesFails() {
        val a = TwoPointLine(Point(0, 0), Point(10, 0))
        val b = TwoPointLine(Point(0, 5), Point(10, 5))
        assertTrue(
            runCatching {
                ArcPrimitive
                    .TangentTangentRadius(
                        a,
                        b,
                        radius = 2.0,
                        centerSide1 = FilletSide.LEFT,
                        centerSide2 = FilletSide.LEFT,
                    ).realize()
            }.isFailure,
        )
    }

    // -----------------------------------------------------------------------------------------
    // Entry method 6: centre, radius, start angle, sweep -- the canonical form itself.
    // -----------------------------------------------------------------------------------------

    @Test
    fun centerRadiusStartSweepIsTakenDirectly() {
        val arc = ArcPrimitive.CenterRadiusStartSweep(Point(5, 5), radius = 3.0, startAngleRadians = 0.0, sweepRadians = PI).realize()
        assertVec2Approx(Vec2(8.0, 5.0), arc.pointAt(0.0))
        assertVec2Approx(Vec2(2.0, 5.0), arc.pointAt(1.0), tolerance = 1e-6)
    }

    // -----------------------------------------------------------------------------------------
    // CircularArc.toCubicSegments: the shared arc-to-Bezier machinery every shape primitive uses.
    // -----------------------------------------------------------------------------------------

    @Test
    fun fullCircleCubicSegmentsMatchTheIndependentFourArcKappaFixture() {
        val produced = CircularArc(Vec2(50.0, -20.0), 30.0, 0.0, TWO_PI).toCubicSegments()
        assertEquals(4, produced.size)
        val fixture = circleContour(50.0, -20.0, 30.0)
        val fixtureSegments = fixture.segments().filterIsInstance<dev.aarso.typewright.core.geometry.CurveSegment.Cubic>()
        assertEquals(fixtureSegments.size, produced.size)
        for (i in produced.indices) {
            assertVec2Approx(fixtureSegments[i].start, produced[i].start, tolerance = 1.0)
            assertVec2Approx(fixtureSegments[i].control1, produced[i].control1, tolerance = 1.0)
            assertVec2Approx(fixtureSegments[i].control2, produced[i].control2, tolerance = 1.0)
            assertVec2Approx(fixtureSegments[i].end, produced[i].end, tolerance = 1.0)
        }
    }

    @Test
    fun cubicSegmentsStayCloseToTheExactCircleAtSampledParameters() {
        val radius = 100.0
        val arc = CircularArc(Vec2(0.0, 0.0), radius, 0.0, PI / 2.0)
        val segment = arc.toCubicSegments().single()
        for (t in listOf(0.0, 0.25, 0.5, 0.75, 1.0)) {
            val bezierPoint = segment.pointAt(t)
            // The Bezier's own parameter does not correspond linearly to the circle's angle, so check
            // that the point lands close to the true circle (distance from centre close to radius)
            // rather than at one specific angle.
            assertApprox(radius, bezierPoint.length(), tolerance = radius * 0.003, message = "t=$t")
        }
    }

    @Test
    fun clockwiseArcCubicSegmentIsTheMirrorOfTheEquivalentCounterClockwiseOne() {
        val ccw = CircularArc(Vec2(0.0, 0.0), 10.0, 0.0, PI / 2.0).toCubicSegments().single()
        val cw = CircularArc(Vec2(0.0, 0.0), 10.0, PI / 2.0, -PI / 2.0).toCubicSegments().single()
        // The clockwise arc from 90 to 0 degrees is exactly the counter-clockwise one from 0 to 90,
        // reversed -- same points, opposite order (and its two control points swapped, as reversing a
        // cubic always does).
        assertVec2Approx(ccw.start, cw.end, tolerance = 1e-9)
        assertVec2Approx(ccw.end, cw.start, tolerance = 1e-9)
        assertVec2Approx(ccw.control1, cw.control2, tolerance = 1e-9)
        assertVec2Approx(ccw.control2, cw.control1, tolerance = 1e-9)
    }

    @Test
    fun toCubicSegmentsSplitsALargeSweepIntoMultiplePieces() {
        val arc = CircularArc(Vec2(0.0, 0.0), 10.0, 0.0, PI) // a half turn
        val segments = arc.toCubicSegments(maxSegmentSweepRadians = PI / 2.0)
        assertEquals(2, segments.size)
        assertVec2Approx(segments[0].end, segments[1].start, tolerance = 1e-9)
    }

    // -----------------------------------------------------------------------------------------
    // arcThroughChordAndRadius / arcFromBulge exercised directly (this file's own internal solvers).
    // -----------------------------------------------------------------------------------------

    @Test
    fun arcThroughChordAndRadiusAtExactlyHalfTheChordGivesASemicircleRegardlessOfSide() {
        val start = Vec2(0.0, 0.0)
        val end = Vec2(10.0, 0.0)
        val arc = arcThroughChordAndRadius(start, end, radius = 5.0, largeArc = false, counterClockwise = true)
        assertApprox(PI, arc.sweepRadians, tolerance = 1e-9)
        assertVec2Approx(Vec2(5.0, 0.0), arc.center, tolerance = 1e-9)
    }
}

private fun distanceFromPointToLine(
    point: Vec2,
    linePoint: Vec2,
    lineDirection: Vec2,
): Double {
    val toPoint = point - linePoint
    val cross = lineDirection.cross(toPoint)
    return sqrt(cross * cross)
}
