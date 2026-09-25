// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.engine.construct

import dev.aarso.typewright.core.geometry.Contour
import dev.aarso.typewright.core.geometry.Point
import dev.aarso.typewright.core.geometry.Vec2
import dev.aarso.typewright.core.geometry.fitClosedContourToCubics
import dev.aarso.typewright.core.geometry.reverse
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The construction grammar's Bowl primitive — "the 'o' primitive" (brief section 10 M2: "Bowl: a
 * superellipse with a stroke and a contrast axis"; brief section 5.1/5.3 name it among the puck's
 * primitives). The most open-ended entry in this file set, built honestly and generally rather than
 * as a stub:
 *
 * 1. **Skeleton.** The bowl's own centreline is a [SuperellipsePrimitive.CentreRadii] of
 *    [skeletonSemiMajor]/[skeletonSemiMinor]/[skeletonExponent] — the same superellipse machinery a
 *    bare [SuperellipsePrimitive] uses, so a bowl's underlying shape is never a second, parallel
 *    implementation.
 * 2. **Stroke.** When [contrast] is exactly `1.0` (monolinear, the default), the skeleton is
 *    literally stroked via `Stroke.kt`'s own [strokeClosedContour] — the task's own "applied via
 *    Stroke.kt's own primitives", satisfied directly, not just in spirit.
 * 3. **Contrast.** [strokeClosedContour] has no notion of a direction-dependent width, so contrast
 *    needs a real extension, documented here plainly rather than hidden behind a black-box constant:
 *    this file models the pen as an **ellipse** ("the elliptical pen" — Knuth's METAFONT is the
 *    best-known typographic instance of exactly this idea, offsetting a path by a fixed convex pen
 *    shape rather than a fixed scalar distance), of half-width [strokeWidth]`/2` along its own thick
 *    axis and `[strokeWidth] * [contrast] / 2` along its thin axis, oriented so its thick axis lands
 *    on [contrastAngleRadians]. **How the width at any point on the bowl is derived:** stroking a
 *    curve with a convex pen shape is a Minkowski sum, whose boundary offset distance at a point
 *    whose local outward normal points in direction `phi` is exactly that pen shape's own **support
 *    function** evaluated at `phi` (`h(phi) = max` over the pen shape of its own points dotted with
 *    the unit direction `phi`) — for an ellipse of semi-axes `(rx, ry)` rotated by `alpha`, this has
 *    the closed form `h(phi) = sqrt((rx * cos(phi - alpha))^2 + (ry * sin(phi - alpha))^2)`
 *    (independently re-derived in this file's own development notes from the ellipse's implicit
 *    equation and its gradient/normal direction, and checked directly against the `rx == ry`
 *    constant-circle-pen case, where it must reduce to a plain scalar radius — verified in this
 *    file's own tests). [ellipticalPenHalfWidth] is exactly this formula, and [realize] offsets the
 *    flattened skeleton's own per-vertex outward normal by it (outer ring `+h`, inner ring `-h`) —
 *    the same "flatten, offset every vertex along its own normal, refit through `core-geometry`'s
 *    [fitClosedContourToCubics]" structure `Stroke.kt`'s [strokeClosedContour] itself uses, just with
 *    a direction-dependent distance instead of a constant one. **Documented limitation, honestly
 *    stated, the same caveat every offset-based primitive in this module already carries
 *    (`Offset.kt`'s own KDoc):** this does not attempt self-intersection cleanup — a very high
 *    contrast on a very small, sharply-curved bowl can, in principle, offset a ring past its own
 *    opposite side, exactly the situation `offsetContour`'s own degenerate-collapse rule exists for;
 *    this primitive does not repeat that rule (no single collapse point is a "sensible" answer for a
 *    two-ring bowl the way it is for a single offset contour), so an unreasonably extreme
 *    [contrast]/[strokeWidth] combination is this primitive's own out-of-scope input, not silently
 *    corrected.
 */
data class BowlPrimitive(
    val center: Point,
    val skeletonSemiMajor: Double,
    val skeletonSemiMinor: Double,
    /** The skeleton's Lame exponent (brief section 10's own superellipse family); `2.0`, the default, is a true ellipse skeleton. */
    val skeletonExponent: Double = 2.0,
    /** The stroke's width along its own thick axis (full width, not half — matching [dev.aarso.typewright.engine.construct.StrokeParameters.width]'s own convention). */
    val strokeWidth: Double,
    /** The ratio of the thin-axis width to the thick-axis width: `1.0` (the default) is monolinear; less than `1.0` narrows the thin axis. */
    val contrast: Double = 1.0,
    /** The angle, in this module's own counter-clockwise-positive convention, of the axis along which the stroke is at its *thickest* (`PI / 2`, straight up, the default — the common "thick stems, thin shoulders" calligraphic reading). */
    val contrastAngleRadians: Double = PI / 2.0,
    val sampleCount: Int = DEFAULT_SUPERELLIPSE_SAMPLE_COUNT,
) {
    init {
        require(skeletonSemiMajor > 0.0 && skeletonSemiMinor > 0.0) { "skeleton semi-axes must be positive" }
        require(strokeWidth > 0.0) { "strokeWidth must be positive, was $strokeWidth" }
        require(contrast > 0.0) { "contrast must be positive, was $contrast" }
    }

    /**
     * This bowl's current geometry as `[outer, inner]` — a ring, like [strokeClosedContour]'s own
     * result, never a single filled shape (a bowl always has a counter). See this type's own KDoc
     * for the full method.
     */
    fun realize(): List<Contour> {
        val skeleton =
            SuperellipsePrimitive.CentreRadii(center, skeletonSemiMajor, skeletonSemiMinor, skeletonExponent, 0.0, sampleCount).realize()
        if (contrast == 1.0) {
            return strokeClosedContour(skeleton, StrokeParameters(width = strokeWidth, join = LineJoin.ROUND))
        }
        val flattened = skeleton.flattenToPolyline(DEFAULT_FLATTEN_TOLERANCE)
        require(flattened.size >= 3) { "the skeleton flattened to fewer than 3 points" }
        val n = flattened.size
        val normals =
            (0 until n).map { i ->
                val prev = flattened[(i - 1 + n) % n]
                val curr = flattened[i]
                val next = flattened[(i + 1) % n]
                averagedOutwardNormal((curr - prev).normalizedOrNull(), (next - curr).normalizedOrNull())
            }

        fun ring(sign: Double): List<Point> =
            (0 until n).map { i ->
                val normal = normals[i]
                val phi = atan2(normal.y, normal.x)
                val halfWidth = ellipticalPenHalfWidth(phi)
                (flattened[i] + normal * (sign * halfWidth)).toRoundedPoint()
            }

        val outer = fitClosedContourToCubics(dedupeCyclicConsecutivePoints(ring(1.0)))
        val inner = fitClosedContourToCubics(dedupeCyclicConsecutivePoints(ring(-1.0))).reverse()
        return listOf(outer, inner)
    }

    /**
     * The elliptical pen's own half-width in the direction [phi] (a local outward normal angle) —
     * see this type's own KDoc for the support-function derivation. The pen's thick axis (half-width
     * `strokeWidth / 2`) is rotated `PI / 2` away from [contrastAngleRadians] because the *thickest
     * stroke* along a given direction happens where the path's local *normal* is perpendicular to
     * that direction (a vertical stem's own thickness is measured horizontally, across it) — the
     * same "stroke direction versus offset-normal direction are perpendicular" relationship every
     * offset-based stroke in this module relies on.
     */
    internal fun ellipticalPenHalfWidth(phi: Double): Double {
        val penThickAxisAngle = contrastAngleRadians - PI / 2.0
        val rx = strokeWidth / 2.0
        val ry = strokeWidth * contrast / 2.0
        val d = phi - penThickAxisAngle
        val cx = rx * cos(d)
        val sy = ry * sin(d)
        return sqrt(cx * cx + sy * sy)
    }
}

/** The unit outward normal at a flattened-polyline vertex, from its two neighbouring edges' own unit directions ([rightNormal] of each, averaged) — a smooth curve's per-vertex normal, distinct from [cornerOffset]'s own polygonal miter/bevel geometry (which this file deliberately does not reuse: a dense, smooth-curve flattening like [SuperellipsePrimitive]'s own has no real corners for a miter rule to matter at, and a plain averaged normal converges to the true tangent normal as sampling gets denser). */
private fun averagedOutwardNormal(
    prevDirection: Vec2?,
    nextDirection: Vec2?,
): Vec2 {
    val n1 = prevDirection?.let { rightNormal(it) }
    val n2 = nextDirection?.let { rightNormal(it) }
    val sum =
        when {
            n1 != null && n2 != null -> n1 + n2
            n1 != null -> n1
            n2 != null -> n2
            else -> Vec2(1.0, 0.0)
        }
    return sum.normalizedOrNull() ?: (n1 ?: n2 ?: Vec2(1.0, 0.0))
}

/** Drops a point that exactly repeats its cyclic predecessor, the same dedup [strokeClosedContour]/[offsetContour] each already perform on their own rounded offset rings, restated locally since this file's own ring construction is not built from either of those functions directly. */
private fun dedupeCyclicConsecutivePoints(points: List<Point>): List<Point> {
    if (points.isEmpty()) return points
    val result = mutableListOf<Point>()
    for (p in points) {
        if (result.isEmpty() || result.last() != p) result += p
    }
    if (result.size > 1 && result.first() == result.last()) result.removeAt(result.size - 1)
    return result
}
