// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.construct

import com.asoc.typewright.core.geometry.Direction
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.core.geometry.direction
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Task P5a-foundations item 4: Hobby splines. `circleKnotsMatchKnownKappaRatio` is this task's own
 * named test ("points sampled from a true circle should produce a Hobby curve whose control
 * points closely approximate the well-known kappa~=0.5523 circle-cubic-approximation constant
 * (report the actual ratio you get, compare to the reference)").
 */
class HobbySplineTest {
    companion object {
        /** The textbook 4-arc cubic-Bezier circle approximation constant, `4/3 * tan(pi/8)`. */
        private const val KNOWN_KAPPA = 0.5522847498307936
    }

    // -----------------------------------------------------------------------------------------
    // The named test: circle knots -> the known kappa ratio.
    // -----------------------------------------------------------------------------------------

    @Test
    fun circleKnotsMatchKnownKappaRatioAtExactPrecisionInternally() {
        // Exact (unrounded) check of the internal tangent solve + hobbyVelocity pipeline, so
        // integer-rounding noise from the public Point-based API cannot mask a real error.
        val radius = 200.0
        val knots = listOf(0, 90, 180, 270).map { deg -> Vec2(radius * cos(deg * PI / 180.0), radius * sin(deg * PI / 180.0)) }
        val n = knots.size
        val tangents = solveCyclicTangents(knots)
        val ratios =
            (0 until n).map { i ->
                val ip1 = (i + 1) % n
                val segment = buildHobbySegment(knots[i], knots[ip1], tangents[i], tangents[ip1], 1.0, 1.0)
                (segment.control1 - segment.start).length() / radius
            }
        val actualRatio = ratios.average()
        println("Hobby closed-circle (n=4, exact) kappa ratio: actual=$actualRatio, known=$KNOWN_KAPPA")
        for (r in ratios) assertTrue(abs(r - KNOWN_KAPPA) < 1e-6, "ratio $r far from known kappa $KNOWN_KAPPA")
    }

    @Test
    fun circleKnotsMatchKnownKappaRatioThroughThePublicApi() {
        val radius = 200
        val knots =
            listOf(0, 90, 180, 270).map { deg ->
                Point((radius * cos(deg * PI / 180.0)).toInt(), (radius * sin(deg * PI / 180.0)).toInt())
            }
        val contour = hobbySplineClosed(knots)
        val ratios =
            (0 until contour.points.size step 3).map { i ->
                val start = contour.points[i].point.toVec2()
                val control1 = contour.points[i + 1].point.toVec2()
                (control1 - start).length() / radius.toDouble()
            }
        val actualRatio = ratios.average()
        println("Hobby closed-circle (n=4, through Point rounding) kappa ratio: actual=$actualRatio, known=$KNOWN_KAPPA")
        for (r in ratios) assertTrue(abs(r - KNOWN_KAPPA) < 0.01, "ratio $r far from known kappa $KNOWN_KAPPA")
    }

    @Test
    fun denserCircleKnotsMatchTheGeneralNGonKappaFormula() {
        // For n knots evenly spaced on a circle, the correct ratio generalizes to
        // (4/3) * tan(PI / (2n)) -- verified independently in this task's own development notes;
        // n=4 is the textbook special case (4/3 * tan(pi/8)).
        for (n in listOf(6, 8, 12)) {
            val radius = 300.0
            val knots = (0 until n).map { i -> Vec2(radius * cos(2 * PI * i / n), radius * sin(2 * PI * i / n)) }
            val tangents = solveCyclicTangents(knots)
            val segment = buildHobbySegment(knots[0], knots[1], tangents[0], tangents[1], 1.0, 1.0)
            val ratio = (segment.control1 - segment.start).length() / radius
            val expected = (4.0 / 3.0) * tan(PI / (2 * n))
            assertTrue(abs(ratio - expected) < 1e-6, "n=$n: ratio=$ratio expected=$expected")
        }
    }

    // -----------------------------------------------------------------------------------------
    // Structural properties: minimal nodes by construction, on-curve anchors at the input knots.
    // -----------------------------------------------------------------------------------------

    @Test
    fun closedSplineProducesExactlyOneSegmentPerKnotPairWithAnchorsAtTheKnots() {
        val knots = listOf(Point(0, 0), Point(100, 20), Point(120, 150), Point(-20, 130))
        val contour = hobbySplineClosed(knots)
        assertEquals(knots.size * 3, contour.points.size, "one (on, off, off) triple per knot -- minimal nodes, no fitting/splitting")
        val onCurve = contour.points.filter { it.onCurve }.map { it.point }
        assertEquals(knots.toSet(), onCurve.toSet())
    }

    @Test
    fun closedSplineOfCcwOrderedKnotsWindsCounterClockwise() {
        val radius = 100
        val knots = (0 until 6).map { i -> Point((radius * cos(2 * PI * i / 6)).toInt(), (radius * sin(2 * PI * i / 6)).toInt()) }
        val contour = hobbySplineClosed(knots)
        assertEquals(Direction.COUNTER_CLOCKWISE, contour.direction())
    }

    @Test
    fun openSplineProducesExactlyOneSegmentPerConsecutivePairWithAnchorsAtTheKnots() {
        val knots = listOf(Point(0, 0), Point(50, 80), Point(150, 60), Point(200, 0))
        val segments = hobbySplineOpen(knots)
        assertEquals(knots.size - 1, segments.size)
        for (i in segments.indices) {
            assertEquals(knots[i].toVec2(), segments[i].start)
            assertEquals(knots[i + 1].toVec2(), segments[i].end)
        }
    }

    @Test
    fun twoKnotOpenSplineDegeneratesToTheConventionalOneThirdChordPlacement() {
        val knots = listOf(Point(0, 0), Point(300, 0))
        val segments = hobbySplineOpen(knots)
        assertEquals(1, segments.size)
        val segment = segments[0]
        assertTrue(
            abs((segment.control1 - segment.start).length() - 100.0) < 1e-6,
            "control1 at one third of the chord: ${segment.control1}",
        )
        assertTrue(abs((segment.end - segment.control2).length() - 100.0) < 1e-6, "control2 at one third of the chord: ${segment.control2}")
        // Perfectly straight: both controls lie exactly on the chord.
        assertTrue(abs(segment.control1.y) < 1e-9 && abs(segment.control2.y) < 1e-9)
    }

    // -----------------------------------------------------------------------------------------
    // Curl and tension (accepted per knot; only the tested default curl=1/tension=1 is exhaustively
    // verified above -- these two tests exercise the documented, simplified non-default behaviour).
    // -----------------------------------------------------------------------------------------

    @Test
    fun curlZeroPointsTheBoundaryTangentExactlyAlongTheChord() {
        val p0 = Vec2(0.0, 0.0)
        val p1 = Vec2(100.0, 0.0)
        val p2 = Vec2(150.0, 80.0) // a sharp bend just past the boundary knot
        val d0 = (p1 - p0).length()
        val d1 = (p2 - p1).length()
        val straight = boundaryTangentDirection(p0, p1, p2, d0, d1, curl = 0.0)
        assertTrue(abs(straight.x - 1.0) < 1e-9 && abs(straight.y) < 1e-9, "curl=0 must point exactly along the chord: $straight")
        val natural = boundaryTangentDirection(p0, p1, p2, d0, d1, curl = 1.0)
        assertTrue(abs(natural.y) > 1e-6, "curl=1's natural estimate should bend toward the following point, unlike curl=0")
    }

    @Test
    fun higherTensionShortensTheHandleLength() {
        val knots = listOf(Point(0, 0), Point(100, 50), Point(200, 0))
        val plain = hobbySplineOpen(knots)
        val tense =
            hobbySplineOpen(
                knots,
                styles =
                    listOf(
                        HobbyKnotStyle(tensionOut = 3.0),
                        HobbyKnotStyle(tensionOut = 3.0, tensionIn = 3.0),
                        HobbyKnotStyle(tensionIn = 3.0),
                    ),
            )
        val plainLength = (plain[0].control1 - plain[0].start).length()
        val tenseLength = (tense[0].control1 - tense[0].start).length()
        assertTrue(tenseLength < plainLength, "tension=3 should give a shorter handle than tension=1: $tenseLength vs $plainLength")
        assertTrue(abs(tenseLength - plainLength / 3.0) < 1e-6, "Hobby's tension divides the handle length directly")
    }

    // -----------------------------------------------------------------------------------------
    // Validation
    // -----------------------------------------------------------------------------------------

    @Test
    fun closedSplineRejectsFewerThanThreeKnots() {
        var threw = false
        try {
            hobbySplineClosed(listOf(Point(0, 0), Point(10, 10)))
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun splinesRejectCoincidentConsecutiveKnots() {
        var threw = false
        try {
            hobbySplineOpen(listOf(Point(0, 0), Point(0, 0), Point(10, 10)))
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }
}
