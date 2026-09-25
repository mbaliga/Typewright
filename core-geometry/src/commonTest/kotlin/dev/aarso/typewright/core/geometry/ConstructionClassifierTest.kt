// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.core.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [classifyConstruction] and [fitSuperellipseExponent], each against a shape whose true
 * construction is known by how it was built (`TYPEWRIGHT_BUILD_BRIEF.md` §8.5's four classes).
 */
class ConstructionClassifierTest {
    @Test
    fun classifyConstructionRejectsANonCubicContour() {
        val quadratic =
            Contour(
                listOf(
                    ContourPoint(Point(0, 0), onCurve = true),
                    ContourPoint(Point(10, 10), onCurve = false),
                    ContourPoint(Point(20, 0), onCurve = true),
                ),
                CurveFormat.QUADRATIC,
            )
        assertFailsWith<IllegalArgumentException> { classifyConstruction(quadratic) }
    }

    @Test
    fun aFittedSquareIsPolygonal() {
        val square = fitClosedContourToCubics(denseSquare(x0 = 0, y0 = 0, side = 400, step = 4))
        val result = classifyConstruction(square)
        assertEquals(ConstructionKind.POLYGONAL, result.kind)
        assertEquals(1.0, result.straightPerimeterFraction)
        assertNull(result.superellipseExponent, "a polygon has no curvature to fit a superellipse exponent to")
    }

    @Test
    fun aFittedCircleIsElliptical() {
        val circle = denseCircle(centerX = 0.0, centerY = 0.0, radius = 250.0, angleStepDegrees = 1.0)
        val fitted = fitClosedContourToCubics(circle)
        val result = classifyConstruction(fitted)
        assertEquals(ConstructionKind.ELLIPTICAL, result.kind)
        assertNotNull(result.superellipseExponent)
        assertTrue(
            abs(result.superellipseExponent - 2.0) < 0.3,
            "a true circle's exponent must land close to 2.0, got ${result.superellipseExponent}",
        )
        assertTrue(result.straightPerimeterFraction < 0.05, "a smooth circle has no long straight run")
    }

    @Test
    fun aFittedEllipseWithUnequalAxesIsAlsoElliptical() {
        val ellipse = denseEllipse(centerX = 0.0, centerY = 0.0, radiusX = 300.0, radiusY = 150.0, angleStepDegrees = 1.0)
        val fitted = fitClosedContourToCubics(ellipse)
        val result = classifyConstruction(fitted)
        assertEquals(ConstructionKind.ELLIPTICAL, result.kind)
    }

    @Test
    fun aFittedSuperellipseWithAModeratelyHighExponentIsSuperelliptical() {
        // n=3: markedly squarer than a true ellipse (n=2), but still curving measurably
        // everywhere -- no long, genuinely flat run -- so this must read as SUPERELLIPTICAL, not
        // ROUNDED_RECTANGLE. (A much higher exponent, e.g. n=8 near this fitter's own search
        // ceiling, legitimately approaches a rounded rectangle geometrically -- a real
        // "squircle" -- so that end of the range is not tested here as a clean SUPERELLIPTICAL
        // case; see this class's own KDoc and ConstructionClassifier.kt's for the acknowledged
        // fuzziness at the high-exponent end.)
        val superellipse = denseSuperellipse(a = 250.0, b = 250.0, n = 3.0, samples = 360)
        val fitted = fitClosedContourToCubics(superellipse, params = CubicFitParameters(errorTolerance = 1.0))
        val result = classifyConstruction(fitted)
        assertEquals(ConstructionKind.SUPERELLIPTICAL, result.kind)
        assertNotNull(result.superellipseExponent)
        assertTrue(result.superellipseExponent > 2.3, "expected a clearly-above-2 exponent, got ${result.superellipseExponent}")
    }

    @Test
    fun aRoundedRectangleWithFlatSidesIsRoundedRectangleNotSuperelliptical() {
        val roundedRect = denseRoundedRectangle(halfWidth = 200.0, halfHeight = 120.0, cornerRadius = 40.0, samplesPerCorner = 30)
        val fitted = fitClosedContourToCubics(roundedRect)
        val result = classifyConstruction(fitted)
        assertEquals(ConstructionKind.ROUNDED_RECTANGLE, result.kind)
        assertTrue(
            result.straightPerimeterFraction >= DEFAULT_ROUNDED_RECTANGLE_STRAIGHT_FRACTION,
            "expected a meaningful straight-side fraction, got ${result.straightPerimeterFraction}",
        )
        assertNull(result.superellipseExponent, "no superellipse fit is attempted once a long straight run is found")
    }

    @Test
    fun anEmptyContourReadsAsPolygonal() {
        // Structurally unreachable through fitClosedContourToCubics (which always emits at least
        // one segment), but classifyConstruction must still be a total function over any
        // well-formed CUBIC contour it is handed directly.
        val single =
            Contour(
                listOf(ContourPoint(Point(0, 0), true), ContourPoint(Point(0, 0), false), ContourPoint(Point(0, 0), false)),
                CurveFormat.CUBIC,
            )
        val result = classifyConstruction(single)
        assertEquals(ConstructionKind.POLYGONAL, result.kind, "a zero-length degenerate loop has nothing to call curved")
    }

    // -------------------------------------------------------------------------------------------
    // fitSuperellipseExponent directly.
    // -------------------------------------------------------------------------------------------

    @Test
    fun fitSuperellipseExponentOnACircleIsCloseToTwo() {
        val circle = denseCircle(centerX = 10.0, centerY = -5.0, radius = 250.0, angleStepDegrees = 1.0)
        val fitted = fitClosedContourToCubics(circle)
        val exponent = fitSuperellipseExponent(fitted)
        assertNotNull(exponent)
        assertTrue(abs(exponent - 2.0) < 0.3, "got $exponent")
    }

    @Test
    fun fitSuperellipseExponentOnADiamondPushesTowardTheLowExponentEnd() {
        // A diamond (a superellipse with n=1) is the opposite direction from a rounded rectangle;
        // the coarse grid search is clamped to [1.2, 8.0] (matching qa/corpus's Roundness.kt), so
        // this should land at or very near the low end of that range, not somewhere in the middle.
        val diamond = fitClosedContourToCubics(denseSuperellipse(a = 200.0, b = 200.0, n = 1.0, samples = 360))
        val exponent = fitSuperellipseExponent(diamond)
        assertNotNull(exponent)
        assertTrue(exponent < 2.0, "expected an exponent below 2 for a diamond-like shape, got $exponent")
    }
}

// -------------------------------------------------------------------------------------------
// Test fixtures: synthetic dense polylines for shapes not already covered by
// CornerDetectionTest's denseSquare/denseTriangle/denseCircle.
// -------------------------------------------------------------------------------------------

/**
 * A dense, closed, counter-clockwise polyline around the mathematically exact ellipse of
 * semi-axes [radiusX]/[radiusY] centred at ([centerX], [centerY]), sampled every
 * [angleStepDegrees] of parametric angle.
 */
internal fun denseEllipse(
    centerX: Double,
    centerY: Double,
    radiusX: Double,
    radiusY: Double,
    angleStepDegrees: Double,
): List<Point> {
    val steps = (360.0 / angleStepDegrees).roundToInt()
    return (0 until steps).map { i ->
        val angle = (i.toDouble() / steps) * 2.0 * PI
        Point((centerX + radiusX * cos(angle)).roundToInt(), (centerY + radiusY * sin(angle)).roundToInt())
    }
}

/**
 * A dense, closed, counter-clockwise polyline around the superellipse `|x/a|^n + |y/b|^n = 1`,
 * sampled at [samples] evenly-angle-spaced points via the standard signed-power parametrisation
 * `x = a * sign(cos) * |cos|^(2/n)`, `y = b * sign(sin) * |sin|^(2/n)`.
 */
internal fun denseSuperellipse(
    a: Double,
    b: Double,
    n: Double,
    samples: Int,
): List<Point> {
    val exponent = 2.0 / n
    return (0 until samples).map { i ->
        val angle = (i.toDouble() / samples) * 2.0 * PI
        val c = cos(angle)
        val s = sin(angle)
        val x = a * sign(c) * abs(c).pow(exponent)
        val y = b * sign(s) * abs(s).pow(exponent)
        Point(x.roundToInt(), y.roundToInt())
    }
}

/**
 * A dense, closed, counter-clockwise polyline around an axis-aligned rounded rectangle: straight
 * sides plus a quarter-circle arc of [cornerRadius] at each corner, [samplesPerCorner] points per
 * corner arc.
 */
internal fun denseRoundedRectangle(
    halfWidth: Double,
    halfHeight: Double,
    cornerRadius: Double,
    samplesPerCorner: Int,
): List<Point> {
    val points = mutableListOf<Point>()

    // Corner centres, in traversal order starting at the right side's corner near the top,
    // going counter-clockwise: (+x,+y-ish) -> (-x,+y-ish) -> (-x,-y-ish) -> (+x,-y-ish).
    data class CornerArc(
        val centerX: Double,
        val centerY: Double,
        val startAngleDegrees: Double,
    )
    val corners =
        listOf(
            CornerArc(halfWidth - cornerRadius, halfHeight - cornerRadius, 0.0),
            CornerArc(-(halfWidth - cornerRadius), halfHeight - cornerRadius, 90.0),
            CornerArc(-(halfWidth - cornerRadius), -(halfHeight - cornerRadius), 180.0),
            CornerArc(halfWidth - cornerRadius, -(halfHeight - cornerRadius), 270.0),
        )
    for (corner in corners) {
        for (s in 0 until samplesPerCorner) {
            val angleDegrees = corner.startAngleDegrees + 90.0 * s / samplesPerCorner
            val angle = angleDegrees * PI / 180.0
            val x = corner.centerX + cornerRadius * cos(angle)
            val y = corner.centerY + cornerRadius * sin(angle)
            points += Point(x.roundToInt(), y.roundToInt())
        }
    }
    return points
}
