package dev.aarso.typewright.core.geometry

import kotlin.math.sqrt

/** Which coordinate an [Extremum] is extreme in. */
enum class Axis { X, Y }

/** One place a [Contour] is locally extreme: [segment] at parameter [t], extreme in [axis]. Evaluate it with [pointAt]. */
data class Extremum(
    val segment: CurveSegment,
    val t: Double,
    val axis: Axis,
)

/**
 * Every place this contour is locally extreme in x or y: the interior parameter values (`0 < t <
 * 1`) of each [Contour.segments] segment where that axis's derivative is zero. [CurveSegment.Line]
 * contributes nothing (a line's x and y are each monotonic or constant along it, so it has no
 * interior extremum to speak of; its own endpoints are anchors already). A degenerate segment
 * whose whole extent is linear or constant in an axis (for example a quadratic whose control
 * point sits exactly on the chord) also contributes nothing for that axis, for the same reason.
 *
 * This is deliberately a t-value primitive rather than a mix of points and t-values: every
 * segment kind can report "where", uniformly, and a caller who wants the actual location calls
 * [pointAt] on `(extremum.segment, extremum.t)` — which is exact for every segment kind, whereas
 * hard-coding integer [Point] results here would either lose precision or misrepresent a
 * genuinely fractional location as if it were font-unit data at rest (see the module overview).
 * Later work that forces extrema onto the curve (docs/ARCHITECTURE_REVIEW.md section 3
 * `:core-geometry`, risk 2) is exactly this: insert [pointAt] of each [Extremum] as a new anchor,
 * then round only once, at the point where it actually gets written back.
 */
fun Contour.extrema(): List<Extremum> =
    segments().flatMap { segment ->
        segment.extremaT(Axis.X).map { Extremum(segment, it, Axis.X) } +
            segment.extremaT(Axis.Y).map { Extremum(segment, it, Axis.Y) }
    }

/** This segment's interior t-values (`0 < t < 1`) where it is locally extreme in [axis]. */
fun CurveSegment.extremaT(axis: Axis): List<Double> =
    when (this) {
        is CurveSegment.Line -> {
            emptyList()
        }

        is CurveSegment.Quadratic -> {
            quadraticExtremaT(component(start, axis), component(control, axis), component(end, axis))
        }

        is CurveSegment.Cubic -> {
            cubicExtremaT(component(start, axis), component(control1, axis), component(control2, axis), component(end, axis))
        }
    }

private fun component(
    v: Vec2,
    axis: Axis,
): Double = if (axis == Axis.X) v.x else v.y

/**
 * A quadratic Bezier's coordinate is `x(t) = a t² + b t + c` with `a = p0 − 2·c + p1`,
 * `b = 2(c − p0)`; its derivative `x'(t) = 2a t + b` has one root, `t = (p0 − c) / a`, when
 * `a ≠ 0`. `a == 0` means this coordinate is linear (or constant) along the whole segment, so
 * there is no interior local extremum to report.
 */
private fun quadraticExtremaT(
    p0: Double,
    control: Double,
    p1: Double,
): List<Double> {
    val a = p0 - 2 * control + p1
    if (a == 0.0) return emptyList()
    val t = (p0 - control) / a
    return if (t > 0.0 && t < 1.0) listOf(t) else emptyList()
}

/**
 * A cubic Bezier's derivative in one coordinate is the quadratic `A t² + B t + C`, with
 * `d0 = p1 − p0`, `d1 = p2 − p1`, `d2 = p3 − p2`, `A = d0 − 2 d1 + d2`, `B = 2(d1 − d0)`,
 * `C = d0` (the standard result for differentiating a cubic Bezier). `A == 0` degrades to at
 * most one linear root; a negative discriminant means no real root; otherwise there are up to
 * two, both kept when they land inside `(0, 1)`.
 */
private fun cubicExtremaT(
    p0: Double,
    p1: Double,
    p2: Double,
    p3: Double,
): List<Double> {
    val d0 = p1 - p0
    val d1 = p2 - p1
    val d2 = p3 - p2
    val a = d0 - 2 * d1 + d2
    val b = 2 * (d1 - d0)
    val c = d0
    val roots =
        if (a == 0.0) {
            if (b == 0.0) emptyList() else listOf(-c / b)
        } else {
            val discriminant = b * b - 4 * a * c
            if (discriminant < 0.0) {
                emptyList()
            } else {
                val sqrtDiscriminant = sqrt(discriminant)
                listOf((-b + sqrtDiscriminant) / (2 * a), (-b - sqrtDiscriminant) / (2 * a))
            }
        }
    return roots.filter { it > 0.0 && it < 1.0 }.sorted()
}
