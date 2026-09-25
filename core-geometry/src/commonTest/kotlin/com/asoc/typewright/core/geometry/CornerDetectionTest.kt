// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.geometry

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [detectCorners] and [arcsBetweenCorners] on constructed inputs where the right answer is known
 * by construction: a dense polygon (sharp corners only) and a dense circle (no sharp corner at
 * all).
 */
class CornerDetectionTest {
    @Test
    fun findsExactlyFourCornersOnADenseSquare() {
        val square = denseSquare(x0 = 100, y0 = 100, side = 500, step = 4)
        val corners = detectCorners(square)
        assertEquals(4, corners.size, "a square has exactly 4 sharp corners: $corners")
        // Every detected corner must actually sit at one of the square's 4 true corner points.
        val trueCorners = setOf(Point(100, 100), Point(600, 100), Point(600, 600), Point(100, 600))
        for (index in corners) {
            assertTrue(square[index] in trueCorners, "corner index $index (${square[index]}) is not one of the square's true corners")
        }
    }

    @Test
    fun findsNoCornersOnADenseCircle() {
        val circle = denseCircle(centerX = 0.0, centerY = 0.0, radius = 250.0, angleStepDegrees = 1.0)
        val corners = detectCorners(circle)
        assertTrue(corners.isEmpty(), "a dense circle has no sharp corner, found: $corners")
    }

    @Test
    fun findsExactlyThreeCornersOnADenseTriangle() {
        val triangle = denseTriangle(Point(0, 0), Point(400, 0), Point(200, 400), step = 5)
        val corners = detectCorners(triangle)
        assertEquals(3, corners.size, "a triangle has exactly 3 sharp corners: $corners")
    }

    @Test
    fun arcsBetweenCornersPartitionTheWholeLoopExactlyOnce() {
        val square = denseSquare(x0 = 0, y0 = 0, side = 400, step = 8)
        val corners = detectCorners(square)
        val arcs = arcsBetweenCorners(square, corners)
        assertEquals(corners.size, arcs.size, "one arc per corner-to-corner span")
        // Each arc starts at a corner and ends at the next corner (both endpoints inclusive), and
        // consecutive arcs share their joining point, so summing (arc.size - 1) across all arcs
        // recovers the original polyline's point count exactly once around the loop.
        val totalSpan = arcs.sumOf { it.size - 1 }
        assertEquals(square.size, totalSpan, "arcs must partition the whole loop exactly once, with no gap or overlap")
        for (k in arcs.indices) {
            val thisArcEnd = arcs[k].last()
            val nextArcStart = arcs[(k + 1) % arcs.size].first()
            assertEquals(thisArcEnd, nextArcStart, "arc $k's end must be the next arc's start")
        }
    }

    @Test
    fun arcsBetweenCornersOnANoCornerLoopIsTheWholeLoopAsOneClosedArc() {
        val circle = denseCircle(centerX = 0.0, centerY = 0.0, radius = 100.0, angleStepDegrees = 5.0)
        val arcs = arcsBetweenCorners(circle, detectCorners(circle))
        assertEquals(1, arcs.size, "a smooth loop with no detected corner is one single closed arc")
        assertEquals(circle.size + 1, arcs[0].size, "the one arc is the whole loop plus its closing repeat of the start point")
        assertEquals(arcs[0].first(), arcs[0].last(), "a single closed arc must start and end at the same point")
    }
}

/**
 * A dense, closed, counter-clockwise polyline around the axis-aligned square with corners
 * `(x0,y0)`, `(x0+side,y0)`, `(x0+side,y0+side)`, `(x0,y0+side)`, sampling each of the 4 sides
 * roughly every [step] font units (the last sample of each side, which would duplicate the next
 * side's first point, is dropped so the whole polyline has no duplicate point).
 */
internal fun denseSquare(
    x0: Int,
    y0: Int,
    side: Int,
    step: Int,
): List<Point> {
    val corners = listOf(Point(x0, y0), Point(x0 + side, y0), Point(x0 + side, y0 + side), Point(x0, y0 + side))
    val points = mutableListOf<Point>()
    for (i in corners.indices) {
        val a = corners[i]
        val b = corners[(i + 1) % corners.size]
        val samples = (side / step).coerceAtLeast(1)
        for (s in 0 until samples) {
            val t = s.toDouble() / samples
            points += Point((a.x + (b.x - a.x) * t).roundToInt(), (a.y + (b.y - a.y) * t).roundToInt())
        }
    }
    return points
}

/** The same idea as [denseSquare], for an arbitrary triangle. */
internal fun denseTriangle(
    a: Point,
    b: Point,
    c: Point,
    step: Int,
): List<Point> {
    val corners = listOf(a, b, c)
    val points = mutableListOf<Point>()
    for (i in corners.indices) {
        val p = corners[i]
        val q = corners[(i + 1) % corners.size]
        val length = (q.toVec2() - p.toVec2()).length()
        val samples = (length / step).roundToInt().coerceAtLeast(1)
        for (s in 0 until samples) {
            val t = s.toDouble() / samples
            points += Point((p.x + (q.x - p.x) * t).roundToInt(), (p.y + (q.y - p.y) * t).roundToInt())
        }
    }
    return points
}

/**
 * A dense, closed, counter-clockwise (or, with [clockwise], clockwise) polyline around the
 * mathematically exact circle of [radius] centred at ([centerX], [centerY]), sampled every
 * [angleStepDegrees] of arc and rounded to the nearest integer font-unit [Point] -- exactly how a
 * raster trace's own sub-pixel boundary extraction would hand this fitter a circle (P2a's shared
 * task instructions: "generate the dense polyline yourself from the mathematical circle at your
 * chosen sample density").
 */
internal fun denseCircle(
    centerX: Double,
    centerY: Double,
    radius: Double,
    angleStepDegrees: Double,
    clockwise: Boolean = false,
): List<Point> {
    val steps = (360.0 / angleStepDegrees).roundToInt()
    val points = mutableListOf<Point>()
    for (i in 0 until steps) {
        val angle = (if (clockwise) -1.0 else 1.0) * (i.toDouble() / steps) * 2.0 * PI
        val x = centerX + radius * cos(angle)
        val y = centerY + radius * sin(angle)
        points += Point(x.roundToInt(), y.roundToInt())
    }
    return points
}
