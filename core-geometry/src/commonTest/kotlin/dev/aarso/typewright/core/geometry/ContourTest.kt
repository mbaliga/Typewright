// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.core.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private fun on(
    x: Int,
    y: Int,
) = ContourPoint(Point(x, y), onCurve = true)

private fun off(
    x: Int,
    y: Int,
) = ContourPoint(Point(x, y), onCurve = false)

class ContourTest {
    @Test
    fun rejectsAnEmptyContour() {
        assertFailsWith<IllegalArgumentException> { Contour(emptyList(), CurveFormat.QUADRATIC) }
    }

    @Test
    fun rejectsACubicContourWhoseSizeIsNotAMultipleOfThree() {
        assertFailsWith<IllegalArgumentException> {
            Contour(listOf(on(0, 0), off(1, 1), off(2, 2), on(3, 3)), CurveFormat.CUBIC)
        }
    }

    @Test
    fun rejectsACubicContourThatDoesNotReadAsOnOffOffTriples() {
        assertFailsWith<IllegalArgumentException> {
            // starts off-curve
            Contour(listOf(off(0, 0), on(1, 1), off(2, 2)), CurveFormat.CUBIC)
        }
        assertFailsWith<IllegalArgumentException> {
            // second triple does not start on-curve
            Contour(listOf(on(0, 0), off(1, 1), off(2, 2), off(3, 3), on(4, 4), off(5, 5)), CurveFormat.CUBIC)
        }
    }

    @Test
    fun acceptsAWellFormedCubicContour() {
        val c = Contour(listOf(on(0, 0), off(1, 1), off(2, 2), on(3, 3), off(4, 4), off(5, 5)), CurveFormat.CUBIC)
        assertEquals(6, c.points.size)
    }

    // A unit square, on-curve corners only, straight (line) segments: the simplest possible
    // sanity check for signed area and direction, independent of any curve math.
    private val unitSquareCcw = Contour(listOf(on(0, 0), on(1, 0), on(1, 1), on(0, 1)), CurveFormat.QUADRATIC)

    @Test
    fun signedAreaOfAPolygonMatchesTheShoelaceFormula() {
        assertEquals(1.0, unitSquareCcw.signedArea(), absoluteTolerance = 1e-12)
    }

    @Test
    fun reversingAPolygonNegatesItsSignedArea() {
        assertEquals(-1.0, unitSquareCcw.reverse().signedArea(), absoluteTolerance = 1e-12)
    }

    @Test
    fun directionFollowsTheSignOfSignedArea() {
        assertEquals(Direction.COUNTER_CLOCKWISE, unitSquareCcw.direction())
        assertEquals(Direction.CLOCKWISE, unitSquareCcw.reverse().direction())
    }

    // A closed quadratic "leaf": two on-curve anchors, one off-curve control on each side.
    // Expected area (-8.0) derived independently by symbolic integration (sympy) of the
    // quadratic Bezier area formula and cross-checked by dense numerical integration; see the
    // commit that added this module for the derivation.
    private val quadraticLeaf =
        Contour(listOf(on(0, 0), off(2, 4), on(4, 0), off(2, -2)), CurveFormat.QUADRATIC)

    @Test
    fun signedAreaOfAQuadraticContourAccountsForCurveBulge() {
        assertEquals(-8.0, quadraticLeaf.signedArea(), absoluteTolerance = 1e-9)
    }

    @Test
    fun reversingAQuadraticContourNegatesItsSignedArea() {
        assertEquals(8.0, quadraticLeaf.reverse().signedArea(), absoluteTolerance = 1e-9)
    }

    @Test
    fun reversingAQuadraticContourTwiceIsTheIdentity() {
        assertEquals(quadraticLeaf, quadraticLeaf.reverse().reverse())
    }

    // A fully off-curve quadratic contour (four off-curve points, e.g. a compact circle-like
    // encoding): every point is part of one all-off-curve run, so the whole contour decodes into
    // four implied-anchor quadratic segments. Expected area (20/3) derived and cross-checked the
    // same way as quadraticLeaf's.
    private val allOffCurveDiamond =
        Contour(listOf(off(2, 0), off(0, 2), off(-2, 0), off(0, -2)), CurveFormat.QUADRATIC)

    @Test
    fun signedAreaOfAFullyOffCurveContourUsesImpliedAnchorsThroughout() {
        assertEquals(20.0 / 3.0, allOffCurveDiamond.signedArea(), absoluteTolerance = 1e-9)
    }

    @Test
    fun reversingAFullyOffCurveContourNegatesItsSignedArea() {
        assertEquals(-20.0 / 3.0, allOffCurveDiamond.reverse().signedArea(), absoluteTolerance = 1e-9)
    }

    // A closed cubic "lens": two on-curve anchors, two off-curve controls on each side. Expected
    // area (-8.4) derived and cross-checked the same way.
    private val cubicLens =
        Contour(
            listOf(on(0, 0), off(1, 2), off(3, 2), on(4, 0), off(3, -2), off(1, -2)),
            CurveFormat.CUBIC,
        )

    @Test
    fun signedAreaOfACubicContourAccountsForCurveBulge() {
        assertEquals(-8.4, cubicLens.signedArea(), absoluteTolerance = 1e-9)
    }

    @Test
    fun reversingACubicContourNegatesItsSignedAreaAndKeepsTheOnOffOffContract() {
        val reversed = cubicLens.reverse()
        assertEquals(8.4, reversed.signedArea(), absoluteTolerance = 1e-9)
        assertEquals(CurveFormat.CUBIC, reversed.format)
        assertTrue(reversed.points[0].onCurve, "a reversed CUBIC contour must still start on-curve")
        assertEquals(listOf(on(0, 0), off(1, -2), off(3, -2), on(4, 0), off(3, 2), off(1, 2)), reversed.points)
    }

    @Test
    fun reversingACubicContourTwiceIsTheIdentity() {
        assertEquals(cubicLens, cubicLens.reverse().reverse())
    }
}
