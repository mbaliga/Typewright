package dev.aarso.typewright.core.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExtremaTest {
    @Test
    fun aLineHasNoInteriorExtremaInEitherAxis() {
        val line = CurveSegment.Line(Vec2(0.0, 0.0), Vec2(10.0, 5.0))
        assertEquals(emptyList(), line.extremaT(Axis.X))
        assertEquals(emptyList(), line.extremaT(Axis.Y))
    }

    @Test
    fun aQuadraticWhoseControlSitsOnTheChordHasNoInteriorExtrema() {
        // control at the exact midpoint of start and end in both axes: the curve degenerates to
        // a straight line, so neither axis has an interior critical point.
        val quad = CurveSegment.Quadratic(Vec2(0.0, 0.0), Vec2(5.0, 0.0), Vec2(10.0, 0.0))
        assertEquals(emptyList(), quad.extremaT(Axis.X))
        assertEquals(emptyList(), quad.extremaT(Axis.Y))
    }

    @Test
    fun aQuadraticExtremumInYOnly() {
        // x(t) is linear here (control on the x-midpoint), y(t) peaks at t = 0.5.
        val quad = CurveSegment.Quadratic(Vec2(0.0, 0.0), Vec2(10.0, 10.0), Vec2(20.0, 0.0))
        assertEquals(emptyList(), quad.extremaT(Axis.X))
        assertEquals(listOf(0.5), quad.extremaT(Axis.Y))
    }

    @Test
    fun aQuadraticExtremumInXOnly() {
        // the mirror image of the previous case: y(t) is linear, x(t) peaks at t = 0.5.
        val quad = CurveSegment.Quadratic(Vec2(0.0, 0.0), Vec2(10.0, 5.0), Vec2(0.0, 10.0))
        assertEquals(listOf(0.5), quad.extremaT(Axis.X))
        assertEquals(emptyList(), quad.extremaT(Axis.Y))
    }

    // Endpoints are never reported: a cubic whose x-derivative roots are exactly t=0 and t=1
    // (the anchors themselves) has no *interior* x extremum, even though x'(t) is zero there.
    @Test
    fun cubicRootsAtTheEndpointsAreNotInteriorExtrema() {
        val cubic = CurveSegment.Cubic(Vec2(0.0, 0.0), Vec2(0.0, 1.0), Vec2(1.0, 1.0), Vec2(1.0, 0.0))
        assertEquals(emptyList(), cubic.extremaT(Axis.X))
    }

    // Verified by symbolic differentiation (sympy): y'(t) = 3 - 6t, a single linear root at
    // t = 0.5, giving y = 3/4 there.
    @Test
    fun cubicWithASingleInteriorRoot() {
        val cubic = CurveSegment.Cubic(Vec2(0.0, 0.0), Vec2(0.0, 1.0), Vec2(1.0, 1.0), Vec2(1.0, 0.0))
        val roots = cubic.extremaT(Axis.Y)
        assertEquals(1, roots.size)
        assertEquals(0.5, roots[0], absoluteTolerance = 1e-12)
        val point = cubic.pointAt(roots[0])
        assertEquals(0.75, point.y, absoluteTolerance = 1e-12)
    }

    // Verified by symbolic differentiation: y'(t) = 180t^2 - 180t + 30, roots at
    // 1/2 ± sqrt(3)/6, both interior.
    @Test
    fun cubicWithTwoInteriorRoots() {
        val cubic = CurveSegment.Cubic(Vec2(0.0, 0.0), Vec2(0.0, 10.0), Vec2(10.0, -10.0), Vec2(10.0, 0.0))
        val roots = cubic.extremaT(Axis.Y)
        assertEquals(2, roots.size)
        assertEquals(0.21132486540518712, roots[0], absoluteTolerance = 1e-12)
        assertEquals(0.7886751345948129, roots[1], absoluteTolerance = 1e-12)
        assertEquals(emptyList(), cubic.extremaT(Axis.X))
    }

    // Verified by symbolic differentiation: A=7, B=0, C=1, discriminant = -28 < 0.
    @Test
    fun cubicWithNoRealRoots() {
        val cubic = CurveSegment.Cubic(Vec2(0.0, 0.0), Vec2(0.0, 1.0), Vec2(0.0, 2.0), Vec2(0.0, 10.0))
        assertEquals(emptyList(), cubic.extremaT(Axis.Y))
    }

    @Test
    fun contourExtremaCollectsEverySegmentsExtremaWithAxisTagged() {
        val contour =
            Contour(
                listOf(
                    ContourPoint(Point(0, 0), onCurve = true),
                    ContourPoint(Point(10, 10), onCurve = false),
                    ContourPoint(Point(20, 0), onCurve = true),
                ),
                CurveFormat.QUADRATIC,
            )
        val extrema = contour.extrema()
        assertEquals(1, extrema.size)
        assertEquals(Axis.Y, extrema[0].axis)
        assertEquals(0.5, extrema[0].t, absoluteTolerance = 1e-12)
        assertTrue(extrema[0].segment is CurveSegment.Quadratic)
    }
}
