// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.engine.construct

import dev.aarso.typewright.core.geometry.Contour
import dev.aarso.typewright.core.geometry.ContourPoint
import dev.aarso.typewright.core.geometry.CurveFormat
import dev.aarso.typewright.core.geometry.CurveSegment
import dev.aarso.typewright.core.geometry.Point
import dev.aarso.typewright.core.geometry.Vec2
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Shared, general-purpose geometry this task's construction primitives ([LinePrimitive],
 * [ArcPrimitive], [CirclePrimitive], [EllipsePrimitive], [SuperellipsePrimitive],
 * [RectanglePrimitive], [RoundedRectanglePrimitive], [StemPrimitive], [BowlPrimitive] — brief
 * section 10 M2, docs/TYPEWRIGHT_HANDOFF.md's own M2 list) all build on, so the same closed-form
 * facts are computed once and reused rather than re-derived per primitive. Nothing in this file
 * branches on which primitive is calling it.
 */
internal const val TWO_PI: Double = 2.0 * PI

/**
 * [v] rounded to the nearest integer [Point] ("integers at rest", CLAUDE.md). Named distinctly
 * from the `roundToPointLocal` helper several existing files in this module each already define
 * privately (`HobbySpline.kt`, `Offset.kt`, `Stroke.kt`) so this one — `internal`, shared by every
 * file this task adds — cannot collide with (and become ambiguous against) any of those
 * file-private copies at a call site inside this module.
 */
internal fun Vec2.toRoundedPoint(): Point = Point(x.roundToInt(), y.roundToInt())

/**
 * The tangent (velocity) vector of [this] segment at parameter [t], the derivative of
 * [dev.aarso.typewright.core.geometry.pointAt]'s own Bernstein-basis formula with respect to `t`
 * (the standard closed-form Bezier derivative: for a cubic, `B'(t) = 3(1-t)^2 (P1-P0) + 6(1-t)t
 * (P2-P1) + 3t^2 (P3-P2)`; the quadratic and line cases are the same construction one and two
 * degrees down). Not normalized — a caller that wants a direction calls [Vec2.normalizedOrNull]
 * on the result. Used by [LinePrimitive.TangentToCurve] (a line tangent to a curve at a point on
 * it) and independently checked in this file's own tests against a central-difference estimate of
 * [dev.aarso.typewright.core.geometry.pointAt].
 */
internal fun CurveSegment.tangentAt(t: Double): Vec2 =
    when (this) {
        is CurveSegment.Line -> {
            end - start
        }

        is CurveSegment.Quadratic -> {
            (control - start) * (2.0 * (1.0 - t)) + (end - control) * (2.0 * t)
        }

        is CurveSegment.Cubic -> {
            val u = 1.0 - t
            (control1 - start) * (3.0 * u * u) + (control2 - control1) * (6.0 * u * t) + (end - control2) * (3.0 * t * t)
        }
    }

/**
 * The centre of the unique circle through three non-collinear points [a], [b], [c] — the standard
 * determinant circumcenter formula (see e.g. Wikipedia, "Circumscribed circle", "Circumcenter
 * coordinates"; independently re-derivable from the two perpendicular-bisector lines' intersection,
 * which this file's own tests check it against directly). Shared by [ArcPrimitive.ThreePoints] and
 * [CirclePrimitive.ThreePoints] — the same circle-through-three-points fact, computed once.
 */
internal fun circumcenter(
    a: Vec2,
    b: Vec2,
    c: Vec2,
): Vec2 {
    val d = 2.0 * (a.x * (b.y - c.y) + b.x * (c.y - a.y) + c.x * (a.y - b.y))
    require(abs(d) > EPSILON) { "three points $a, $b, $c are collinear (or coincide); no unique circle passes through them" }
    val aSq = a.x * a.x + a.y * a.y
    val bSq = b.x * b.x + b.y * b.y
    val cSq = c.x * c.x + c.y * c.y
    val ux = (aSq * (b.y - c.y) + bSq * (c.y - a.y) + cSq * (a.y - b.y)) / d
    val uy = (aSq * (c.x - b.x) + bSq * (a.x - c.x) + cSq * (b.x - a.x)) / d
    return Vec2(ux, uy)
}

/**
 * Where the infinite line through [p1] in direction [d1] crosses the infinite line through [p2] in
 * direction [d2] (`null` when the two directions are parallel, including anti-parallel). Standard
 * 2D line-line intersection by the cross-product method: writing `w = p2 - p1`, the intersection is
 * `p1 + d1 * t` where `t = cross(w, d2) / cross(d1, d2)` (derived by solving `p1 + t*d1 = p2 + s*d2`
 * for `t` alone, eliminating `s`). Shared by every [ArcPrimitive.TangentTangentRadius] /
 * [CirclePrimitive.TangentTangentRadius] fillet ([filletCenterAndTangents]).
 */
internal fun lineLineIntersection(
    p1: Vec2,
    d1: Vec2,
    p2: Vec2,
    d2: Vec2,
): Vec2? {
    val denom = d1.cross(d2)
    if (abs(denom) <= EPSILON) return null
    val w = p2 - p1
    val t = w.cross(d2) / denom
    return p1 + d1 * t
}

/** The projection of [point] onto the infinite line through [linePoint] in unit direction [lineDirection]. */
internal fun projectOntoLine(
    point: Vec2,
    linePoint: Vec2,
    lineDirection: Vec2,
): Vec2 {
    val t = (point - linePoint).dot(lineDirection)
    return linePoint + lineDirection * t
}

/** Two points defining an infinite line (through [a] and [b]), the input shape [ArcPrimitive.TangentTangentRadius] and [CirclePrimitive.TangentTangentRadius] take for each of the two lines a fillet rounds between. */
data class TwoPointLine(
    val a: Point,
    val b: Point,
) {
    init {
        require(a != b) { "a line needs two distinct points, both were $a" }
    }

    /** The unit direction from [a] to [b]. */
    fun direction(): Vec2 = (b.toVec2() - a.toVec2()).normalizedOrNull() ?: error("unreachable: a != b guarantees a non-zero direction")
}

/** Which side of a [TwoPointLine]'s own `a -> b` direction a fillet's centre sits on ([leftNormal] versus its negation). */
enum class FilletSide { LEFT, RIGHT }

/** The unit left-hand-of-travel normal of unit [direction]: the negation of this module's own [rightNormal] (`Offset.kt`). */
internal fun leftNormal(direction: Vec2): Vec2 = Vec2(-direction.y, direction.x)

private fun FilletSide.normalOf(direction: Vec2): Vec2 = if (this == FilletSide.LEFT) leftNormal(direction) else rightNormal(direction)

/**
 * The classic CAD "fillet" construction: the centre of the circle of [radius] tangent to both
 * [line1] and [line2], together with its two tangent points (one on each line) — general for lines
 * at any angle, not just axis-aligned. **Method:** offset each line by [radius] to the side its own
 * [centerSide] names ([FilletSide.LEFT]/[FilletSide.RIGHT] of that line's own `a -> b` direction), then
 * intersect the two offset lines ([lineLineIntersection]) to get the centre (a point equidistant,
 * by construction, from both original lines, since it sits exactly [radius] away from each along
 * that line's own normal); the two tangent points are the centre's own perpendicular projections
 * back onto the two *original* lines ([projectOntoLine]) — the standard foot-of-perpendicular
 * definition of a tangent point on a line. Returns `null` when the two lines are parallel (no
 * finite fillet exists) or nearly so (the offset lines would intersect arbitrarily far away).
 */
internal fun filletCenterAndTangents(
    line1: TwoPointLine,
    line2: TwoPointLine,
    radius: Double,
    centerSide1: FilletSide,
    centerSide2: FilletSide,
): Triple<Vec2, Vec2, Vec2>? {
    require(radius > 0.0) { "fillet radius must be positive, was $radius" }
    val dir1 = line1.direction()
    val dir2 = line2.direction()
    val offsetPoint1 = line1.a.toVec2() + centerSide1.normalOf(dir1) * radius
    val offsetPoint2 = line2.a.toVec2() + centerSide2.normalOf(dir2) * radius
    val center = lineLineIntersection(offsetPoint1, dir1, offsetPoint2, dir2) ?: return null
    val tangent1 = projectOntoLine(center, line1.a.toVec2(), dir1)
    val tangent2 = projectOntoLine(center, line2.a.toVec2(), dir2)
    return Triple(center, tangent1, tangent2)
}

/** [angle] reduced into `(0, 2*PI]` — every "sweep from a known start angle to a known end angle, going counter-clockwise" computation in this task shares this one normalization rather than re-deriving it. */
internal fun normalizeAngleToPositiveTwoPi(angle: Double): Double {
    val wrapped = angle % TWO_PI
    val positive = if (wrapped <= 0.0) wrapped + TWO_PI else wrapped
    // A near-zero result after wrapping (the two angles coincide) reads as a full turn, not a
    // degenerate zero-sweep arc -- callers that want a genuine zero-length arc reject it themselves
    // (every entry method in this file requires two distinct points).
    return if (positive <= EPSILON) TWO_PI else positive
}

/**
 * Builds a closed [CurveFormat.CUBIC] [Contour] from [segments] (already forming one closed loop,
 * `segments[i].end == segments[(i + 1) % segments.size].start` up to this function's own rounding)
 * — the same "one (on, off, off) triple per segment, segment end never re-emitted" construction
 * `core-geometry`'s own (module-private) `buildCubicContour` and `HobbySpline.kt`'s own
 * `buildCubicContourFromSegments` already use, restated here as this task's one shared version so
 * every shape primitive in this file set ([CirclePrimitive], [EllipsePrimitive],
 * [SuperellipsePrimitive], [RectanglePrimitive], [RoundedRectanglePrimitive], [BowlPrimitive]'s
 * skeleton) goes through it rather than each writing its own copy.
 */
internal fun buildClosedCubicContour(segments: List<CurveSegment.Cubic>): Contour {
    require(segments.isNotEmpty()) { "a closed contour needs at least one segment" }
    val points =
        segments.flatMap { segment ->
            listOf(
                ContourPoint(segment.start.toRoundedPoint(), onCurve = true),
                ContourPoint(segment.control1.toRoundedPoint(), onCurve = false),
                ContourPoint(segment.control2.toRoundedPoint(), onCurve = false),
            )
        }
    return Contour(points, CurveFormat.CUBIC)
}

/**
 * A straight run from [start] to [end] as a cubic segment with degenerate, on-line control points
 * at the first and second thirds of the run — the only way a straight side is representable in
 * [CurveFormat.CUBIC] (see `core-geometry`'s `CubicFitting.kt`, `buildCubicContour`'s own KDoc:
 * "a `Contour` in `CurveFormat.CUBIC` has no 'line' point kind"). Used by every straight-sided
 * primitive here ([RectanglePrimitive], [RoundedRectanglePrimitive]'s straight edges,
 * [StemPrimitive]), the production equivalent of this module's own test-only
 * `TestShapes.rectangleContour`.
 */
internal fun straightCubic(
    start: Vec2,
    end: Vec2,
): CurveSegment.Cubic {
    val c1 = start + (end - start) * (1.0 / 3.0)
    val c2 = start + (end - start) * (2.0 / 3.0)
    return CurveSegment.Cubic(start, c1, c2, end)
}
