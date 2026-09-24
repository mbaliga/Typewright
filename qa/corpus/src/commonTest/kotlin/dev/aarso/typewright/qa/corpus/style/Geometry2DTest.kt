package dev.aarso.typewright.qa.corpus.style

import dev.aarso.typewright.core.geometry.Vec2
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Geometry2DTest {
    @Test
    fun tightBoundsOfARectangleIsExact() {
        val square = polygon(10 to 20, 110 to 20, 110 to 120, 10 to 120)
        val bounds = assertNotNull(square.tightBounds())
        assertEquals(10.0, bounds.minX)
        assertEquals(20.0, bounds.minY)
        assertEquals(110.0, bounds.maxX)
        assertEquals(120.0, bounds.maxY)
    }

    @Test
    fun tightBoundsOfACircleReachesItsTrueExtrema() {
        // A cubic circle approximation's own anchor points already sit at the axis extrema
        // (see ellipseContour's KDoc), so this also proves extrema() finds them independently:
        // the anchors ARE the segments' own start/end points here, but the off-curve control
        // points overshoot the radius, so a bounds computed from raw points alone would be wrong.
        val circle = ellipseContour(0.0, 0.0, 100.0, 100.0)
        val bounds = assertNotNull(circle.tightBounds())
        assertTrue(abs(bounds.width - 200.0) < 0.5, "width was ${bounds.width}")
        assertTrue(abs(bounds.height - 200.0) < 0.5, "height was ${bounds.height}")
    }

    @Test
    fun lineCrossingsFindsBothSidesOfARectangle() {
        val square = polygon(0 to 0, 100 to 0, 100 to 100, 0 to 100)
        val glyph =
            dev.aarso.typewright.core.geometry
                .Glyph("sq", 100, listOf(square))
        val crossings = glyph.lineCrossings(Vec2(-50.0, 50.0), Vec2(1.0, 0.0))
        val ts = crossings.map { it.tAlongLine }.sorted()
        assertEquals(2, ts.size)
        assertTrue(abs(ts[0] - 50.0) < 1e-6) // x = 0, at t = 50 from origin x = -50
        assertTrue(abs(ts[1] - 150.0) < 1e-6) // x = 100
    }

    @Test
    fun inkIntervalsPairsCrossingsEvenOdd() {
        val square = polygon(0 to 0, 100 to 0, 100 to 100, 0 to 100)
        val glyph =
            dev.aarso.typewright.core.geometry
                .Glyph("sq", 100, listOf(square))
        val intervals = inkIntervals(glyph.lineCrossings(Vec2(-50.0, 50.0), Vec2(1.0, 0.0)))
        assertEquals(1, intervals.size)
        assertTrue(abs(intervals[0].length - 100.0) < 1e-6)
    }

    @Test
    fun inkIntervalsOnARingGiveTwoBands() {
        val glyph = circleRingGlyph(outerRadius = 100.0, innerRadius = 60.0)
        val intervals = inkIntervals(glyph.lineCrossings(Vec2(-200.0, 0.0), Vec2(1.0, 0.0)))
        // a horizontal probe through the centre of a ring crosses ink, gap, ink: two bands.
        assertEquals(2, intervals.size)
        for (band in intervals) assertTrue(abs(band.length - 40.0) < 1.0, "band length was ${band.length}")
    }

    @Test
    fun isInkAtDistinguishesInsideFromOutside() {
        val square = polygon(0 to 0, 100 to 0, 100 to 100, 0 to 100)
        val glyph =
            dev.aarso.typewright.core.geometry
                .Glyph("sq", 100, listOf(square))
        assertTrue(glyph.isInkAt(Vec2(50.0, 50.0)))
        assertTrue(!glyph.isInkAt(Vec2(-10.0, 50.0)))
        assertTrue(!glyph.isInkAt(Vec2(150.0, 50.0)))
    }

    @Test
    fun narrowestThroatFindsTheApertureNotTheWallOnABracketShape() {
        // A "[" bracket: three 60-unit-thick walls (bottom, left, top) around a 300x300 square,
        // open on the right for y in [60, 240] (a 180-unit-tall aperture). Wall thickness (60) is
        // narrower than the aperture (180) -- exactly the case that, without the inside-ink
        // filter, finds the wall instead (see narrowestThroat's KDoc).
        val bracket =
            polygon(
                0 to 0,
                300 to 0,
                300 to 60,
                60 to 60,
                60 to 240,
                300 to 240,
                300 to 300,
                0 to 300,
            )
        val glyph =
            dev.aarso.typewright.core.geometry
                .Glyph("c", 320, listOf(bracket))
        val throat = assertNotNull(narrowestThroat(glyph, bracket))
        assertTrue(throat.width > 150.0, "found width ${throat.width}, expected the ~180-unit aperture, not the 60-unit wall")
    }

    @Test
    fun narrowestThroatReturnsNullForTooFewPoints() {
        val tiny = polygon(0 to 0, 10 to 0)
        assertNull(narrowestThroat(tiny, perSegment = 1))
    }
}
