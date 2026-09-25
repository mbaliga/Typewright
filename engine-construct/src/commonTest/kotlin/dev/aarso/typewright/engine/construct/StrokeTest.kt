// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.engine.construct

import dev.aarso.typewright.core.geometry.Direction
import dev.aarso.typewright.core.geometry.Point
import dev.aarso.typewright.core.geometry.direction
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Task P5a-foundations item 2: stroke-to-outline, on shapes whose correct stroke is computable analytically. */
class StrokeTest {
    // -----------------------------------------------------------------------------------------
    // A straight centreline strokes to a rectangle (plus cap geometry).
    // -----------------------------------------------------------------------------------------

    @Test
    fun straightSegmentWithButtCapsStrokesToAnExactRectangle() {
        val centerline = listOf(Point(0, 0), Point(100, 0))
        val result = strokeOpenPolyline(centerline, StrokeParameters(width = 20.0, cap = LineCap.BUTT))
        val corners =
            result.points
                .filter { it.onCurve }
                .map { Pair(it.point.x, it.point.y) }
                .toSet()
        assertEquals(setOf(Pair(0, -10), Pair(100, -10), Pair(100, 10), Pair(0, 10)), corners)
    }

    @Test
    fun straightSegmentWithSquareCapsExtendsByHalfWidth() {
        val centerline = listOf(Point(0, 0), Point(100, 0))
        val result = strokeOpenPolyline(centerline, StrokeParameters(width = 20.0, cap = LineCap.SQUARE))
        val corners =
            result.points
                .filter { it.onCurve }
                .map { Pair(it.point.x, it.point.y) }
                .toSet()
        // A square cap extends the top/bottom edges collinearly with the (straight) centreline
        // itself, so the plain rectangle simply grows to the full extended length (100 + 2*10);
        // the original segment-boundary points (0,-10)/(100,-10)/(100,10)/(0,10) are no longer
        // real corners at all (each now sits exactly on a straight run between two other points)
        // and correctly do not survive corner-detect + fit -- only the 4 true corners of the
        // now-bigger rectangle do.
        val expected = setOf(Pair(110, -10), Pair(110, 10), Pair(-10, 10), Pair(-10, -10))
        assertEquals(expected, corners)
    }

    @Test
    fun straightSegmentWithRoundCapsBulgesByHalfWidthPastEachEnd() {
        val centerline = listOf(Point(0, 0), Point(100, 0))
        val result = strokeOpenPolyline(centerline, StrokeParameters(width = 20.0, cap = LineCap.ROUND))
        val flattened = result.flattenToPolyline(0.5)
        val maxX = flattened.maxOf { it.x }
        val minX = flattened.minOf { it.x }
        assertTrue(abs(maxX - 110.0) < 1.5, "round end cap should bulge to about x=110, got maxX=$maxX")
        assertTrue(abs(minX - (-10.0)) < 1.5, "round start cap should bulge to about x=-10, got minX=$minX")
        // Every flattened point should sit within [width/2] of its nearest centreline point (the
        // segment itself, or one of the two round-cap semicircle centres) -- i.e. never bulge out
        // further than the stroke's own half-width anywhere.
        for (p in flattened) {
            val distToSegment =
                if (p.x in 0.0..100.0) abs(p.y) else minOf(hypot(p.x, p.y), hypot(p.x - 100.0, p.y))
            assertTrue(distToSegment <= 10.0 + 1.0, "point $p is further than half-width from the centreline")
        }
    }

    // -----------------------------------------------------------------------------------------
    // A circular centreline strokes to two concentric circles (a ring) whose radii differ by
    // exactly the width.
    // -----------------------------------------------------------------------------------------

    @Test
    fun circularCentrelineStrokesToARingWithRadiiDifferingByTheWidth() {
        val radius = 200.0
        val width = 30.0
        val centerline = circleContour(cx = 0.0, cy = 0.0, radius = radius)
        val rings = strokeClosedContour(centerline, StrokeParameters(width = width))
        assertEquals(2, rings.size)
        val (outer, inner) = rings

        for (p in outer.flattenToPolyline(0.5)) {
            val r = hypot(p.x, p.y)
            assertTrue(abs(r - (radius + width / 2.0)) < 3.0, "outer ring point $p at radius $r")
        }
        for (p in inner.flattenToPolyline(0.5)) {
            val r = hypot(p.x, p.y)
            assertTrue(abs(r - (radius - width / 2.0)) < 3.0, "inner ring point $p at radius $r")
        }

        assertEquals(Direction.COUNTER_CLOCKWISE, outer.direction(), "outer ring keeps the centreline's own CCW winding")
        assertEquals(Direction.CLOCKWISE, inner.direction(), "inner ring is CW, CLAUDE.md's inner-hole convention")
    }

    // -----------------------------------------------------------------------------------------
    // Join style: miter (sharp point), bevel (two points, straight cross-edge), round (an arc) --
    // all on the same moderate (90-degree) corner, so every style is well inside its own valid
    // domain (see roundArcPoints' KDoc).
    // -----------------------------------------------------------------------------------------

    @Test
    fun rightAngleJoinDiffersByStyleAsDocumented() {
        // An L shape: right along +x, then up along +y -- a 90-degree turn at (100, 0).
        val centerline = listOf(Point(0, 0), Point(100, 0), Point(100, 100))
        val width = 20.0

        val miter = strokeOpenPolyline(centerline, StrokeParameters(width = width, join = LineJoin.MITER))
        val bevel = strokeOpenPolyline(centerline, StrokeParameters(width = width, join = LineJoin.BEVEL))
        val round = strokeOpenPolyline(centerline, StrokeParameters(width = width, join = LineJoin.ROUND))

        // The miter join's own outer corner is the single sharp point at (110, -10) (the outer
        // side of this right-angle turn, per rightNormal's own convention -- see cornerOffsetTest
        // for the same right-angle miter math).
        val miterCorners =
            miter.points
                .filter { it.onCurve }
                .map { Pair(it.point.x, it.point.y) }
                .toSet()
        assertTrue(Pair(110, -10) in miterCorners, "miter join's own sharp outer corner: $miterCorners")

        // The bevel join replaces that one sharp point with two, each exactly `width/2` from the
        // turn vertex (100, 0) along one of the two edges' own normals -- never a single point out
        // at the miter's own longer distance.
        val bevelOnCurve = bevel.points.filter { it.onCurve }.map { it.point }
        assertTrue(Pair(110, -10) !in bevelOnCurve.map { Pair(it.x, it.y) }, "bevel must not include the sharp miter point")
        val nearOuterCorner = bevelOnCurve.filter { hypot(it.x - 100.0, it.y - 0.0) <= width / 2.0 + 1.5 }
        assertTrue(nearOuterCorner.size >= 2, "bevel join should offer (at least) two distinct points near the turn: $nearOuterCorner")

        // The round join is smooth: it must include several points near radius width/2 around the
        // turn vertex (a wide-enough band to absorb the final Schneider refit's own tolerance,
        // core-geometry's DEFAULT_FIT_ERROR_TOLERANCE), and must never reach out anywhere near the
        // sharp miter point 10 units further out.
        val roundFlat = round.flattenToPolyline(0.5)
        val nearTurn = roundFlat.filter { hypot(it.x - 100.0, it.y - 0.0) in (width / 2.0 - 3.0)..(width / 2.0 + 3.0) }
        assertTrue(nearTurn.size >= 3, "round join should sample several points along its own arc: ${nearTurn.size}")
        assertTrue(roundFlat.none { hypot(it.x - 110.0, it.y - (-10.0)) < 2.0 }, "round join must not reach the sharp miter point")
    }

    // -----------------------------------------------------------------------------------------
    // Robustness / degenerate input.
    // -----------------------------------------------------------------------------------------

    @Test
    fun strokeParametersRejectsNonPositiveWidth() {
        var threw = false
        try {
            StrokeParameters(width = 0.0)
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun strokeOpenPolylineRejectsFewerThanTwoDistinctPoints() {
        var threw = false
        try {
            strokeOpenPolyline(listOf(Point(5, 5), Point(5, 5)), StrokeParameters(width = 10.0))
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }
}
