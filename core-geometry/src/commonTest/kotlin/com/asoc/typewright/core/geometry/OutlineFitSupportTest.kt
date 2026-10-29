// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The small geometric tools the outline fitter is built from, each against a case whose answer is known. */
class OutlineFitSupportTest {
    private fun line(
        n: Int,
        dx: Double = 1.0,
        dy: Double = 0.0,
        noise: Double = 0.0,
    ): List<Vec2> = List(n) { Vec2(it * dx, it * dy + (if (it % 2 == 0) noise else -noise)) }

    @Test
    fun aTotalLeastSquaresLineThroughNoisyCollinearPointsRunsAlongThem() {
        val points = line(40, dx = 2.0, dy = 1.0, noise = 0.1)
        val fit = PointMoments(points).fit(0, 39)
        val length = kotlin.math.sqrt(5.0)
        assertEquals(2.0 / length, fit.direction.x, 0.01)
        assertEquals(1.0 / length, fit.direction.y, 0.01)
        assertTrue(PointMoments(points).maxDistance(fit, 0, 39) < 0.2)
        assertEquals(0.0, fit.distance(fit.origin + fit.direction * 25.0), 1e-9)
        val projected = fit.project(Vec2(10.0, 50.0))
        assertEquals(0.0, fit.distance(projected), 1e-9)
    }

    @Test
    fun theLineFitIsOrientedFromTheFirstPointTowardTheLast() {
        val backwards = line(10).reversed()
        assertTrue(PointMoments(backwards).fit(0, 9).direction.x < 0.0)
    }

    @Test
    fun resamplingASquareGivesEvenStepsAndKeepsTheShape() {
        val square = listOf(Vec2(0.0, 0.0), Vec2(100.0, 0.0), Vec2(100.0, 100.0), Vec2(0.0, 100.0))
        val samples = resampleClosed(square, 5.0)
        assertEquals(80, samples.size)
        for (i in samples.indices) {
            val step = (samples[(i + 1) % samples.size] - samples[i]).length()
            assertEquals(5.0, step, 0.01)
        }
        assertTrue(samples.all { p -> p.x in 0.0..100.0 && p.y in 0.0..100.0 })
    }

    @Test
    fun resamplingDropsRepeatedAndClosingPoints() {
        val triangle = listOf(Vec2(0.0, 0.0), Vec2(0.0, 0.0), Vec2(10.0, 0.0), Vec2(10.0, 10.0), Vec2(0.0, 0.0))
        val samples = resampleClosed(triangle, 1.0)
        assertTrue(samples.size >= 8)
        assertEquals(1, samples.count { (it - Vec2(0.0, 0.0)).length() < 1e-6 })
    }

    @Test
    fun smoothingMovesAZigzagTowardItsMeanAndHoldsOpenEndsStill() {
        val zigzag = List(21) { Vec2(it.toDouble(), if (it % 2 == 0) 1.0 else -1.0) }
        val open = smoothOpen(zigzag, 3)
        assertEquals(zigzag.first(), open.first())
        assertEquals(zigzag.last(), open.last())
        assertTrue(abs(open[10].y) < 0.2, "interior flattened: ${open[10].y}")
        val ring = List(20) { Vec2(cos(2 * PI * it / 20), sin(2 * PI * it / 20)) }
        val smooth = smoothClosed(ring, 1)
        assertEquals(20, smooth.size)
        assertTrue(smooth.all { (it).length() < 1.0 }, "smoothing a ring pulls it inward")
    }

    @Test
    fun turnAtIsSignedAndMeasuredInRadians() {
        assertEquals(PI / 2.0, turnAt(Vec2(0.0, 0.0), Vec2(1.0, 0.0), Vec2(1.0, 1.0)), 1e-9)
        assertEquals(-PI / 2.0, turnAt(Vec2(0.0, 0.0), Vec2(1.0, 0.0), Vec2(1.0, -1.0)), 1e-9)
        assertEquals(0.0, turnAt(Vec2(0.0, 0.0), Vec2(1.0, 0.0), Vec2(5.0, 0.0)), 1e-9)
    }

    @Test
    fun pathLengthSumsTheSteps() {
        assertEquals(7.0, pathLength(listOf(Vec2(0.0, 0.0), Vec2(3.0, 0.0), Vec2(3.0, 4.0))), 1e-9)
        assertEquals(0.0, pathLength(emptyList()))
    }

    @Test
    fun bowSeparatesAGentleCurveFromANoisyStraightRun() {
        val straight = line(60, noise = 0.2)
        val straightBow = bowOf(straight, 0, 59, PointMoments(straight).fit(0, 59))
        assertTrue(abs(straightBow) < 0.1, "noise averages out: $straightBow")
        // A circular arc of radius 250 over 60 units bows by about L * L / (18 R).
        val arc =
            List(31) { i ->
                val a = (i * 2.0) / 250.0
                Vec2(250.0 * sin(a), 250.0 * (1.0 - cos(a)))
            }
        val bow = bowOf(arc, 0, 30, PointMoments(arc).fit(0, 30))
        assertEquals(60.0 * 60.0 / (18.0 * 250.0), abs(bow), 0.15)
    }

    @Test
    fun findLineRunsFindsTheSidesOfARoundedBarAndNotItsEnds() {
        // A 400-long bar with semicircular ends of radius 40: two straight sides, no line in the ends.
        val points = mutableListOf<Vec2>()
        for (i in 0..200) points += Vec2(i * 2.0, 0.0)
        for (i in 1..49) {
            val a = -PI / 2 + PI * i / 50
            points += Vec2(400.0 + 40.0 * cos(a), 40.0 + 40.0 * sin(a))
        }
        val runs = findLineRuns(points, spacing = 2.0, lineTolerance = 1.2, trimTolerance = 0.8, minLength = 48.0, bowTolerance = 0.5)
        assertEquals(1, runs.size, "$runs")
        assertTrue(runs.single().first <= 2 && runs.single().last in 195..205, "the side runs 0..~200: ${runs.single()}")
    }

    @Test
    fun findLineRunsIgnoresACircle() {
        val circle = List(300) { i -> Vec2(250.0 * cos(2 * PI * i / 300), 250.0 * sin(2 * PI * i / 300)) }
        assertTrue(findLineRuns(circle + circle, 5.2, 1.2, 0.8, 48.0, 0.5).isEmpty())
    }

    @Test
    fun prominentExtremaKeepOnlyRealPeaksAwayFromTheEnds() {
        val values = DoubleArray(41) { 10.0 * sin(PI * it / 20.0) + (if (it % 2 == 0) 0.05 else -0.05) }
        val found = prominentExtrema(values, prominence = 2.0, margin = 2)
        assertEquals(listOf(10, 30), found, "one peak and one trough, not the noise")
        assertTrue(prominentExtrema(DoubleArray(10) { it.toDouble() }, 1.0, 2).isEmpty(), "a ramp has no extremum")
    }

    @Test
    fun directionAlongReadsTheTangentOverADistanceInEitherDirection() {
        val points = line(20, dx = 2.0, dy = 0.0)
        assertEquals(1.0, directionAlong(points, 0, 1, 8.0).x, 1e-9)
        assertEquals(-1.0, directionAlong(points, 19, -1, 8.0).x, 1e-9)
    }

    @Test
    fun metricSnapMovesANodeAndItsHandlesTogetherSoALineStaysALine() {
        // A rectangle whose bottom edge sits one unit above the baseline.
        val rectangle = polygonContour(listOf(Point(0, 1), Point(300, 1), Point(300, 200), Point(0, 200)), CurveFormat.CUBIC)
        val snapped = snapNodesToMetricLines(rectangle, listOf(0), 2)
        assertEquals(4, snapped.lineSegmentCount(), "all four sides are still plain lines")
        assertEquals(
            setOf(0, 200),
            snapped.points
                .filter { it.onCurve }
                .map { it.point.y }
                .toSet(),
        )
        assertEquals(rectangle, snapNodesToMetricLines(rectangle, emptyList(), 2))
        assertEquals(rectangle, snapNodesToMetricLines(rectangle, listOf(0), 0))
    }

    @Test
    fun metricSnapLeavesTheTopOfARoundShapeItsOvershoot() {
        val circle = fitClosedOutline(OutlineFitTest.circle(0.0, 0.0, 250.0)).contour
        val shifted = Contour(circle.points.map { it.copy(point = Point(it.point.x, it.point.y + 251)) }, circle.format)
        // The top is at y = 501: one unit off the 500 line, and a round peak, so it keeps its overshoot.
        val kept = snapNodesToMetricLines(shifted, listOf(500), 2)
        assertEquals(shifted.points.maxOf { it.point.y }, kept.points.maxOf { it.point.y })
    }

    @Test
    fun snapOutlineRunsAxisSnapExtremaMetricsAndTangentsInOrder() {
        val tilted = OutlineFitTest.densify(listOf(Vec2(0.0, 1.0), Vec2(300.0, 2.0), Vec2(299.0, 400.0), Vec2(1.0, 400.0)), 1.0)
        val fit = fitClosedOutline(tilted)
        val snapped = snapOutline(fit.contour, metricLines = listOf(0))
        val ys =
            snapped.points
                .filter { it.onCurve }
                .map { it.point.y }
                .toSet()
        assertEquals(setOf(0, 400), ys, "the near-level bottom is made level and then put on the baseline")
        assertEquals(4, snapped.lineSegmentCount())
    }
}
