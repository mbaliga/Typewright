// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.engine.construct

import dev.aarso.typewright.core.geometry.Contour
import dev.aarso.typewright.core.geometry.CurveSegment
import dev.aarso.typewright.core.geometry.Point
import dev.aarso.typewright.core.geometry.Vec2
import dev.aarso.typewright.core.geometry.fitClosedContourToCubics
import dev.aarso.typewright.core.geometry.midpoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * The construction grammar's Ellipse primitive (brief section 10 M2;
 * `docs/TYPEWRIGHT_HANDOFF.md`'s own M2 list, the "circle, ellipse, superellipse" family). **Method:**
 * an ellipse is an affine (non-uniform scale plus rotation) image of a unit circle, so [CentreRadii]
 * builds the unit circle's own exact cubic approximation ([CircularArc.toCubicSegments], the same
 * machinery [CirclePrimitive] uses) once and then maps every one of its points through an
 * [AffineTransform] ([scale] then [rotate] then translate to [CentreRadii.center]) — an ellipse
 * realized this way is *exactly* as accurate as [CirclePrimitive]'s own circle (no additional
 * approximation error from the affine map, which is itself exact), rather than being resampled and
 * refit from scratch.
 */
sealed class EllipsePrimitive {
    /** This primitive's current parameters, realized as a closed, counter-clockwise [Contour]. */
    abstract fun realize(): Contour

    /** Entry method 1: centred at [center], semi-axes [semiMajor] (along the unrotated x-axis) and [semiMinor] (y-axis), the whole shape then rotated [rotationRadians] about [center]. */
    data class CentreRadii(
        val center: Point,
        val semiMajor: Double,
        val semiMinor: Double,
        val rotationRadians: Double = 0.0,
    ) : EllipsePrimitive() {
        override fun realize(): Contour = ellipseContour(center.toVec2(), semiMajor, semiMinor, rotationRadians)
    }

    /**
     * Entry method 2: the three-point ellipse construction AutoCAD's own `ELLIPSE` command uses
     * ("Specify axis endpoint of ellipse", "Specify other endpoint of axis", "Specify distance to
     * other axis"): [axisEnd1] and [axisEnd2] are the two ends of one axis (fixing the centre as their
     * midpoint, that axis's own semi-length as half their distance, and the rotation as that axis's
     * own direction), and [otherAxisPoint]'s perpendicular distance from the centre (its component
     * along the normal of the `axisEnd1 -> axisEnd2` direction) fixes the other semi-axis.
     */
    data class ThreePoints(
        val axisEnd1: Point,
        val axisEnd2: Point,
        val otherAxisPoint: Point,
    ) : EllipsePrimitive() {
        override fun realize(): Contour {
            val (center, semiA, semiB, rotation) = threePointAxes(axisEnd1, axisEnd2, otherAxisPoint)
            return ellipseContour(center, semiA, semiB, rotation)
        }
    }
}

/** Shared by [EllipsePrimitive.ThreePoints] and [SuperellipsePrimitive.ThreePoints]: derives (centre, semi-axis-1, semi-axis-2, rotation) from AutoCAD's own three-point axis-endpoint ellipse construction — see [EllipsePrimitive.ThreePoints]'s KDoc for the method. */
internal fun threePointAxes(
    axisEnd1: Point,
    axisEnd2: Point,
    otherAxisPoint: Point,
): AxisParameters {
    val p1 = axisEnd1.toVec2()
    val p2 = axisEnd2.toVec2()
    require((p2 - p1).length() > EPSILON) { "axisEnd1 $axisEnd1 and axisEnd2 $axisEnd2 must be distinct" }
    val center = midpoint(p1, p2)
    val semiA = (p2 - p1).length() / 2.0
    val axisDir = (p2 - p1).normalizedOrNull()!!
    val rotation = atan2(axisDir.y, axisDir.x)
    val semiB = abs((otherAxisPoint.toVec2() - center).dot(leftNormal(axisDir)))
    require(semiB > EPSILON) { "otherAxisPoint $otherAxisPoint lies on the first axis; the second semi-axis would be zero" }
    return AxisParameters(center, semiA, semiB, rotation)
}

/** (centre, semi-axis-1, semi-axis-2, rotation), the shape [threePointAxes] resolves a three-point axis construction to. */
internal data class AxisParameters(
    val center: Vec2,
    val semiA: Double,
    val semiB: Double,
    val rotationRadians: Double,
)

/** An ellipse of [semiMajor]/[semiMinor] semi-axes centred at [center], rotated [rotationRadians], as an affine image of the unit circle — see [EllipsePrimitive]'s own KDoc. */
internal fun ellipseContour(
    center: Vec2,
    semiMajor: Double,
    semiMinor: Double,
    rotationRadians: Double,
): Contour {
    require(semiMajor > 0.0 && semiMinor > 0.0) { "semi-axes must be positive, were $semiMajor, $semiMinor" }
    val unitCircleSegments = CircularArc(Vec2(0.0, 0.0), 1.0, 0.0, TWO_PI).toCubicSegments()
    val transform = scale(semiMajor, semiMinor).andThen(rotate(rotationRadians)).andThen(translate(center.x, center.y))
    val mapped =
        unitCircleSegments.map { segment ->
            CurveSegment.Cubic(
                transform.apply(segment.start),
                transform.apply(segment.control1),
                transform.apply(segment.control2),
                transform.apply(segment.end),
            )
        }
    return buildClosedCubicContour(mapped)
}

// -------------------------------------------------------------------------------------------
// Superellipse: same axis/rotation model as Ellipse, plus an exponent. No closed-form
// circular-arc approximation exists for a general Lame curve, so this samples the exact
// parametric equation densely and refits through core-geometry's own fitClosedContourToCubics
// (task item 3's own "not hardcoded per-shape magic numbers": one general fitter, reused, never
// a second one written here).
// -------------------------------------------------------------------------------------------

/** How many points [SuperellipsePrimitive] samples around its exact parametric curve before refitting to cubics — generous next to `core-geometry`'s own [fitClosedContourToCubics] corner/extrema detection, which then reduces that sampling to a small handful of fitted segments (see this file's own tests for what it actually produces). */
const val DEFAULT_SUPERELLIPSE_SAMPLE_COUNT: Int = 360

/**
 * The construction grammar's Superellipse primitive: the "o" family's underlying shape (brief
 * section 10 M2's own "Bowl: a superellipse with a stroke and a contrast axis"; [BowlPrimitive]
 * builds directly on this file). A superellipse (Lame curve) of semi-axes `(a, b)` and exponent `n`
 * is the set of points `(a * sign(cos t) * |cos t|^(2/n), b * sign(sin t) * |sin t|^(2/n))` for `t`
 * in `[0, 2*PI)` — `n = 2` is exactly an ellipse, `n > 2` bulges towards a rounded rectangle, `n < 2`
 * pinches towards a rounded diamond (Gabriel Lame's original 1818 curve family; Piet Hein's
 * "superellipse" naming and popularization for `n` around `2.5`). There is no closed-form Bezier
 * approximation of a general Lame curve (unlike a true ellipse's affine-image trick — see
 * [EllipsePrimitive]'s KDoc), so [realize] samples the exact parametric equation at
 * [CentreRadii.sampleCount] points and refits through `core-geometry`'s own
 * [fitClosedContourToCubics].
 */
sealed class SuperellipsePrimitive {
    abstract fun realize(): Contour

    /** Entry method 1: centred at [center], semi-axes [semiMajor]/[semiMinor], Lame [exponent], rotated [rotationRadians]. */
    data class CentreRadii(
        val center: Point,
        val semiMajor: Double,
        val semiMinor: Double,
        val exponent: Double,
        val rotationRadians: Double = 0.0,
        val sampleCount: Int = DEFAULT_SUPERELLIPSE_SAMPLE_COUNT,
    ) : SuperellipsePrimitive() {
        override fun realize(): Contour = superellipseContour(center.toVec2(), semiMajor, semiMinor, exponent, rotationRadians, sampleCount)
    }

    /** Entry method 2: [EllipsePrimitive.ThreePoints]'s own axis-endpoint construction ([threePointAxes]), plus an explicit Lame [exponent] (no finite point count fixes a general superellipse's own curvature, so the exponent is always given directly, never derived from points). */
    data class ThreePoints(
        val axisEnd1: Point,
        val axisEnd2: Point,
        val otherAxisPoint: Point,
        val exponent: Double,
        val sampleCount: Int = DEFAULT_SUPERELLIPSE_SAMPLE_COUNT,
    ) : SuperellipsePrimitive() {
        override fun realize(): Contour {
            val axes = threePointAxes(axisEnd1, axisEnd2, otherAxisPoint)
            return superellipseContour(axes.center, axes.semiA, axes.semiB, exponent, axes.rotationRadians, sampleCount)
        }
    }
}

/** The dense parametric sample of a Lame curve of semi-axes [semiMajor]/[semiMinor], [exponent], rotated [rotationRadians] about [center] — [superellipseContour]'s own fitting input, exposed separately so this file's tests can check individual sampled points against the exact parametric equation. */
internal fun superellipsePolyline(
    center: Vec2,
    semiMajor: Double,
    semiMinor: Double,
    exponent: Double,
    rotationRadians: Double,
    sampleCount: Int,
): List<Point> {
    require(semiMajor > 0.0 && semiMinor > 0.0) { "semi-axes must be positive, were $semiMajor, $semiMinor" }
    require(exponent > 0.0) { "exponent must be positive, was $exponent" }
    require(sampleCount >= 8) { "sampleCount must be at least 8, was $sampleCount" }
    val cosR = cos(rotationRadians)
    val sinR = sin(rotationRadians)
    return (0 until sampleCount).map { i ->
        val t = TWO_PI * i / sampleCount
        val x = semiMajor * sign(cos(t)) * abs(cos(t)).pow(2.0 / exponent)
        val y = semiMinor * sign(sin(t)) * abs(sin(t)).pow(2.0 / exponent)
        // Rotate (x, y) by rotationRadians, then translate to center -- the same affine composition
        // EllipsePrimitive.CentreRadii applies via a real AffineTransform; done inline here (rather
        // than allocating one) since this loop is per-sample-point, not per-shape.
        val rx = x * cosR - y * sinR
        val ry = x * sinR + y * cosR
        Vec2(center.x + rx, center.y + ry).toRoundedPoint()
    }
}

private fun superellipseContour(
    center: Vec2,
    semiMajor: Double,
    semiMinor: Double,
    exponent: Double,
    rotationRadians: Double,
    sampleCount: Int,
): Contour {
    val polyline = superellipsePolyline(center, semiMajor, semiMinor, exponent, rotationRadians, sampleCount)
    return fitClosedContourToCubics(polyline)
}
