package dev.aarso.typewright.core.geometry

import kotlin.test.Test
import kotlin.test.assertEquals

private fun on(
    x: Int,
    y: Int,
) = ContourPoint(Point(x, y), onCurve = true)

private fun off(
    x: Int,
    y: Int,
) = ContourPoint(Point(x, y), onCurve = false)

class CurveSegmentTest {
    @Test
    fun aPolygonDecodesToLineSegments() {
        val square = Contour(listOf(on(0, 0), on(1, 0), on(1, 1), on(0, 1)), CurveFormat.QUADRATIC)
        val segments = square.segments()
        assertEquals(
            listOf(
                CurveSegment.Line(Vec2(0.0, 0.0), Vec2(1.0, 0.0)),
                CurveSegment.Line(Vec2(1.0, 0.0), Vec2(1.0, 1.0)),
                CurveSegment.Line(Vec2(1.0, 1.0), Vec2(0.0, 1.0)),
                CurveSegment.Line(Vec2(0.0, 1.0), Vec2(0.0, 0.0)),
            ),
            segments,
        )
    }

    @Test
    fun aSingleOffCurvePointBetweenTwoAnchorsIsOneQuadraticSegment() {
        val contour = Contour(listOf(on(0, 0), off(10, 10), on(20, 0)), CurveFormat.QUADRATIC)
        val segments = contour.segments()
        assertEquals(
            listOf(
                CurveSegment.Quadratic(Vec2(0.0, 0.0), Vec2(10.0, 10.0), Vec2(20.0, 0.0)),
                // the implicit closing segment back to the start
                CurveSegment.Line(Vec2(20.0, 0.0), Vec2(0.0, 0.0)),
            ),
            segments,
        )
    }

    // Same points and expected geometry as NodeEconomyTest.aFullyOffCurveContourImpliesOnePointPerCyclicPair
    // and ContourTest.allOffCurveDiamond: verified independently by hand (each implied point is the
    // exact midpoint of its off-curve pair).
    @Test
    fun aFullyOffCurveContourDecodesToOneQuadraticSegmentPerImpliedAnchor() {
        val contour = Contour(listOf(off(2, 0), off(0, 2), off(-2, 0), off(0, -2)), CurveFormat.QUADRATIC)
        val segments = contour.segments()
        assertEquals(
            listOf(
                CurveSegment.Quadratic(Vec2(1.0, -1.0), Vec2(2.0, 0.0), Vec2(1.0, 1.0)),
                CurveSegment.Quadratic(Vec2(1.0, 1.0), Vec2(0.0, 2.0), Vec2(-1.0, 1.0)),
                CurveSegment.Quadratic(Vec2(-1.0, 1.0), Vec2(-2.0, 0.0), Vec2(-1.0, -1.0)),
                CurveSegment.Quadratic(Vec2(-1.0, -1.0), Vec2(0.0, -2.0), Vec2(1.0, -1.0)),
            ),
            segments,
        )
    }

    @Test
    fun aCubicContourDecodesToOneCubicSegmentPerTriple() {
        val contour =
            Contour(
                listOf(on(0, 0), off(1, 2), off(3, 2), on(4, 0), off(3, -2), off(1, -2)),
                CurveFormat.CUBIC,
            )
        val segments = contour.segments()
        assertEquals(
            listOf(
                CurveSegment.Cubic(Vec2(0.0, 0.0), Vec2(1.0, 2.0), Vec2(3.0, 2.0), Vec2(4.0, 0.0)),
                CurveSegment.Cubic(Vec2(4.0, 0.0), Vec2(3.0, -2.0), Vec2(1.0, -2.0), Vec2(0.0, 0.0)),
            ),
            segments,
        )
    }

    @Test
    fun linePointAtInterpolatesLinearly() {
        val line = CurveSegment.Line(Vec2(0.0, 0.0), Vec2(10.0, 20.0))
        assertEquals(Vec2(0.0, 0.0), line.pointAt(0.0))
        assertEquals(Vec2(10.0, 20.0), line.pointAt(1.0))
        assertEquals(Vec2(5.0, 10.0), line.pointAt(0.5))
    }

    @Test
    fun quadraticPointAtMatchesTheBezierFormula() {
        val quad = CurveSegment.Quadratic(Vec2(0.0, 0.0), Vec2(10.0, 10.0), Vec2(20.0, 0.0))
        assertEquals(Vec2(0.0, 0.0), quad.pointAt(0.0))
        assertEquals(Vec2(20.0, 0.0), quad.pointAt(1.0))
        // hand-derived: x(t) is linear here (control sits on the x-midpoint) so x(0.5) = 10;
        // y(t) = -20t^2 + 20t so y(0.5) = 5.
        assertEquals(Vec2(10.0, 5.0), quad.pointAt(0.5))
    }

    @Test
    fun cubicPointAtMatchesTheBezierFormula() {
        val cubic = CurveSegment.Cubic(Vec2(0.0, 0.0), Vec2(0.0, 1.0), Vec2(1.0, 1.0), Vec2(1.0, 0.0))
        assertEquals(Vec2(0.0, 0.0), cubic.pointAt(0.0))
        assertEquals(Vec2(1.0, 0.0), cubic.pointAt(1.0))
        // hand-derived (and confirmed by symbolic integration): at t=0.5, x=1/2, y=3/4.
        val mid = cubic.pointAt(0.5)
        assertEquals(0.5, mid.x, absoluteTolerance = 1e-12)
        assertEquals(0.75, mid.y, absoluteTolerance = 1e-12)
    }

    @Test
    fun chordsOfACubicContourAreExactIntegerAnchorToAnchorSegments() {
        val contour =
            Contour(
                listOf(on(0, 0), off(1, 2), off(3, 2), on(4, 0), off(3, -2), off(1, -2)),
                CurveFormat.CUBIC,
            )
        assertEquals(
            listOf(Segment(Point(0, 0), Point(4, 0)), Segment(Point(4, 0), Point(0, 0))),
            contour.chords(),
        )
    }

    @Test
    fun chordsOfAPolygonAreItsOwnLineSegments() {
        val square = Contour(listOf(on(0, 0), on(1, 0), on(1, 1), on(0, 1)), CurveFormat.QUADRATIC)
        assertEquals(
            listOf(
                Segment(Point(0, 0), Point(1, 0)),
                Segment(Point(1, 0), Point(1, 1)),
                Segment(Point(1, 1), Point(0, 1)),
                Segment(Point(0, 1), Point(0, 0)),
            ),
            square.chords(),
        )
    }

    @Test
    fun chordsRoundAnImpliedQuadraticAnchorToTheNearestFontUnit() {
        // The off-curve pair (3, 1) / (4, 1) implies an on-curve midpoint at (3.5, 1), which is
        // not an integer; the chords touching it round that midpoint to the nearest unit.
        val contour = Contour(listOf(on(0, 0), off(3, 1), off(4, 1), on(10, 0)), CurveFormat.QUADRATIC)
        val chords = contour.chords()
        assertEquals(
            listOf(
                Segment(Point(0, 0), Point(4, 1)),
                Segment(Point(4, 1), Point(10, 0)),
                Segment(Point(10, 0), Point(0, 0)),
            ),
            chords,
        )
    }
}
