// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The outline fitter against shapes whose right answer is known: a rectangle is four straight sides, a rounded
 * rectangle four sides and four corner curves, a circle four curves cut at its extrema. Each is traced densely
 * (a point every unit or so, as a raster's boundary is), optionally with deterministic jitter, and the fit must
 * come back with exactly the nodes a designer would draw and stay within tolerance of the boundary.
 */
class OutlineFitTest {
    @Test
    fun aRectangleIsFourCornersAndFourStraightSidesWithNoOffCurvePoints() {
        val fit = fitClosedOutline(roundedRect(0.0, 0.0, 400.0, 300.0, 0.0))

        assertEquals(4, fit.cornerCount)
        assertEquals(4, fit.lineCount)
        assertEquals(ContourCount(onCurveEquivalent = 4, offCurve = 0), fit.contour.writtenCount())
        assertTrue(fit.maxDeviation <= 2.0, "deviation ${fit.maxDeviation}")
        assertSameBounds(fit.contour, 0.0, 0.0, 400.0, 300.0, 1.0)
    }

    @Test
    fun aRoundedRectangleIsFourStraightSidesAndFourSingleCurvesJoinedWithoutCorners() {
        val fit = fitClosedOutline(roundedRect(0.0, 0.0, 380.0, 500.0, 118.0))

        assertEquals(0, fit.cornerCount, "a rounded corner of radius 118 is not a corner")
        assertEquals(4, fit.lineCount)
        assertEquals(4, fit.curveCount, "each rounded corner is one cubic")
        assertEquals(ContourCount(onCurveEquivalent = 8, offCurve = 8), fit.contour.writtenCount())
        assertTrue(fit.maxDeviation <= 2.0, "deviation ${fit.maxDeviation}")
        assertTrue(
            fit.contour.points
                .filter { it.onCurve }
                .count { it.smooth } == 8,
            "every join is smooth by design",
        )
    }

    @Test
    fun aCircleIsFourCurvesCutAtItsExtremaWithExactlyHorizontalAndVerticalHandles() {
        val fit = fitClosedOutline(circle(0.0, 0.0, 250.0))

        assertEquals(0, fit.lineCount)
        assertEquals(ContourCount(onCurveEquivalent = 4, offCurve = 8), fit.contour.writtenCount())
        val snapped = snapOutline(fit.contour)
        val onCurve = snapped.points.filter { it.onCurve }.map { it.point }
        for (extremum in listOf(Point(0, 250), Point(0, -250), Point(250, 0), Point(-250, 0))) {
            assertTrue(
                onCurve.any { hypot((it.x - extremum.x).toDouble(), (it.y - extremum.y).toDouble()) <= 2.0 },
                "a node at $extremum: $onCurve",
            )
        }
        assertTrue(fit.maxDeviation <= 2.0, "deviation ${fit.maxDeviation}")
        assertEquals(PI * 250.0 * 250.0, abs(fit.contour.signedArea()), PI * 250.0 * 250.0 * 0.01)
    }

    @Test
    fun anLShapeKeepsItsSixCornersAndAnInnerCornerIsNotLost() {
        val l =
            listOf(Vec2(0.0, 0.0), Vec2(300.0, 0.0), Vec2(300.0, 60.0), Vec2(60.0, 60.0), Vec2(60.0, 500.0), Vec2(0.0, 500.0))
        val fit = fitClosedOutline(densify(l, 1.0))

        assertEquals(6, fit.cornerCount)
        assertEquals(ContourCount(onCurveEquivalent = 6, offCurve = 0), fit.contour.writtenCount())
        val nodes =
            fit.contour.points
                .filter { it.onCurve }
                .map { it.point }
        for (corner in l) {
            assertTrue(nodes.any { hypot((it.x - corner.x), (it.y - corner.y)) <= 1.5 }, "corner $corner is a node: $nodes")
        }
    }

    @Test
    fun aCornerIsPlacedWhereTheTwoSidesMeetNotWhereARasterChamferedIt() {
        // The same rectangle with every corner cut off by a 2-unit diagonal, as marching squares leaves it.
        val chamfered = chamferedPolygon(listOf(Vec2(0.0, 0.0), Vec2(400.0, 0.0), Vec2(400.0, 300.0), Vec2(0.0, 300.0)), 2.0)
        val fit = fitClosedOutline(chamfered)

        val nodes =
            fit.contour.points
                .filter { it.onCurve }
                .map { it.point }
        assertEquals(4, nodes.size)
        for (corner in listOf(Point(0, 0), Point(400, 0), Point(400, 300), Point(0, 300))) {
            assertTrue(nodes.contains(corner), "corner $corner restored exactly, got $nodes")
        }
    }

    @Test
    fun jitterOfAFractionOfAUnitDoesNotChangeTheNodeCount() {
        for (seed in 1..6) {
            val fit = fitClosedOutline(jitter(roundedRect(0.0, 0.0, 380.0, 500.0, 118.0), 0.35, seed))
            assertEquals(ContourCount(8, 8), fit.contour.writtenCount(), "seed $seed: ${fit.lineCount} lines, ${fit.curveCount} curves")
            assertTrue(fit.maxDeviation <= 2.0, "seed $seed deviation ${fit.maxDeviation}")
        }
    }

    @Test
    fun aLooserToleranceNeverGivesMoreNodesThanATighterOne() {
        val polyline = jitter(circle(0.0, 0.0, 200.0, stretchY = 0.6), 0.3, 3)
        val tight = fitClosedOutline(polyline, OutlineFitParameters(fitTolerance = 0.8)).contour.writtenCount()
        val loose = fitClosedOutline(polyline, OutlineFitParameters(fitTolerance = 4.0)).contour.writtenCount()
        assertTrue(
            loose.onCurveEquivalent + loose.offCurve <= tight.onCurveEquivalent + tight.offCurve,
            "tight $tight loose $loose",
        )
    }

    @Test
    fun theCornerTurnThresholdDecidesWhatCountsAsACorner() {
        // A 20-degree bend in an otherwise straight side: below the default 32, so it is not a corner; above a 10-degree setting.
        val bent = densify(listOf(Vec2(0.0, 0.0), Vec2(200.0, 0.0), Vec2(400.0, 73.0), Vec2(400.0, 400.0), Vec2(0.0, 400.0)), 1.0)
        val strict = detectOutlineCorners(bent, OutlineFitParameters(cornerTurnDegrees = 60.0))
        val sensitive = detectOutlineCorners(bent, OutlineFitParameters(cornerTurnDegrees = 10.0))
        assertTrue(
            sensitive.cornerIndices.size > strict.cornerIndices.size,
            "${sensitive.cornerIndices.size} vs ${strict.cornerIndices.size}",
        )
    }

    @Test
    fun snappingMakesNearlyAxisAlignedSidesExactlyAxisAligned() {
        val tilted = densify(listOf(Vec2(0.0, 0.0), Vec2(400.0, 3.0), Vec2(399.0, 300.0), Vec2(1.0, 300.0)), 1.0)
        val fit = fitClosedOutline(tilted)
        val snapped = snapLinesToAxes(fit.contour)

        val nodes = snapped.points.filter { it.onCurve }.map { it.point }
        assertEquals(2, nodes.map { it.x }.distinct().size, "two distinct x values: $nodes")
        assertEquals(2, nodes.map { it.y }.distinct().size, "two distinct y values: $nodes")
        assertEquals(4, snapped.lineSegmentCount(), "the sides stay plain lines")
    }

    @Test
    fun lineSegmentCountAndLongestLineReadTheCanonicalLineTriples() {
        val fit = fitClosedOutline(roundedRect(0.0, 0.0, 380.0, 500.0, 118.0))
        assertEquals(4, fit.contour.lineSegmentCount())
        assertEquals(264.0, fit.contour.longestLine(), 6.0, "the long sides are 500 - 2 * 118 = 264 units")
        assertEquals(0, circleContourForLineCount().lineSegmentCount())
        assertEquals(
            0.0,
            Contour(listOf(ContourPoint(Point(0, 0), true), ContourPoint(Point(5, 5), true)), CurveFormat.QUADRATIC).longestLine(),
        )
    }

    private fun circleContourForLineCount(): Contour = fitClosedOutline(circle(0.0, 0.0, 100.0)).contour

    private fun assertSameBounds(
        contour: Contour,
        xMin: Double,
        yMin: Double,
        xMax: Double,
        yMax: Double,
        tolerance: Double,
    ) {
        val points = contour.points.filter { it.onCurve }.map { it.point }
        assertEquals(xMin, points.minOf { it.x }.toDouble(), tolerance)
        assertEquals(yMin, points.minOf { it.y }.toDouble(), tolerance)
        assertEquals(xMax, points.maxOf { it.x }.toDouble(), tolerance)
        assertEquals(yMax, points.maxOf { it.y }.toDouble(), tolerance)
    }

    companion object {
        /** A counter-clockwise rounded rectangle traced about every unit. */
        fun roundedRect(
            x0: Double,
            y0: Double,
            w: Double,
            h: Double,
            r: Double,
            step: Double = 1.0,
        ): List<Vec2> {
            val out = mutableListOf<Vec2>()

            fun arc(
                cx: Double,
                cy: Double,
                from: Double,
            ) {
                if (r <= 0.0) return
                val steps = max(2, (r * PI / 2.0 / step).toInt())
                for (s in 0..steps) {
                    val a = from + PI / 2.0 * s / steps
                    out += Vec2(cx + r * cos(a), cy + r * sin(a))
                }
            }
            if (r <= 0.0) {
                return densify(listOf(Vec2(x0, y0), Vec2(x0 + w, y0), Vec2(x0 + w, y0 + h), Vec2(x0, y0 + h)), step)
            }
            arc(x0 + w - r, y0 + r, -PI / 2.0)
            arc(x0 + w - r, y0 + h - r, 0.0)
            arc(x0 + r, y0 + h - r, PI / 2.0)
            arc(x0 + r, y0 + r, PI)
            // The straight sides are the gaps between consecutive arcs; densify the whole thing.
            return densifyClosed(out, step)
        }

        fun circle(
            cx: Double,
            cy: Double,
            radius: Double,
            stretchY: Double = 1.0,
            step: Double = 1.0,
        ): List<Vec2> {
            val steps = max(16, (2.0 * PI * radius / step).toInt())
            return List(steps) { s ->
                val a = 2.0 * PI * s / steps
                Vec2(cx + radius * cos(a), cy + radius * stretchY * sin(a))
            }
        }

        fun densify(
            corners: List<Vec2>,
            step: Double,
        ): List<Vec2> = densifyClosed(corners, step)

        private fun densifyClosed(
            points: List<Vec2>,
            step: Double,
        ): List<Vec2> {
            val out = mutableListOf<Vec2>()
            for (i in points.indices) {
                val a = points[i]
                val b = points[(i + 1) % points.size]
                val length = (b - a).length()
                val pieces = max(1, (length / step).toInt())
                for (s in 0 until pieces) out += a + (b - a) * (s.toDouble() / pieces)
            }
            return out
        }

        /** [corners] as a polygon with every corner cut off by a diagonal [size] units along each side, traced densely. */
        fun chamferedPolygon(
            corners: List<Vec2>,
            size: Double,
        ): List<Vec2> {
            val n = corners.size
            val cut = mutableListOf<Vec2>()
            for (i in 0 until n) {
                val c = corners[i]
                val toPrev = corners[(i - 1 + n) % n] - c
                val toNext = corners[(i + 1) % n] - c
                cut += c + toPrev * (size / toPrev.length())
                cut += c + toNext * (size / toNext.length())
            }
            return densifyClosed(cut, 1.0)
        }

        /** Deterministic jitter: each point moved by up to [amount] units, from a seeded linear congruential generator. */
        fun jitter(
            polyline: List<Vec2>,
            amount: Double,
            seed: Int,
        ): List<Vec2> {
            var state = seed * 2654435761L + 12345L

            fun next(): Double {
                state = (state * 6364136223846793005L + 1442695040888963407L)
                return ((state ushr 33) % 20001L) / 10000.0 - 1.0
            }
            return polyline.map { Vec2(it.x + next() * amount, it.y + next() * amount) }
        }
    }
}
