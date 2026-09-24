package dev.aarso.typewright.core.geometry

import kotlin.math.roundToInt

/**
 * One curve segment between two anchor points, in algorithm space ([Vec2]). Produced by
 * [Contour.segments], which resolves [CurveFormat.QUADRATIC]'s implied on-curve points into real
 * anchors, so a [CurveSegment] is never ambiguous about where a segment starts or ends.
 */
sealed class CurveSegment {
    abstract val start: Vec2
    abstract val end: Vec2

    /** A straight line between two anchors (no control point). */
    data class Line(
        override val start: Vec2,
        override val end: Vec2,
    ) : CurveSegment()

    /** A quadratic Bezier: one control point pulling the curve between two anchors. */
    data class Quadratic(
        override val start: Vec2,
        val control: Vec2,
        override val end: Vec2,
    ) : CurveSegment()

    /** A cubic Bezier: two control points between two anchors. */
    data class Cubic(
        override val start: Vec2,
        val control1: Vec2,
        val control2: Vec2,
        override val end: Vec2,
    ) : CurveSegment()
}

/** This segment's position at parameter [t] (`t` in `[0, 1]`; `0` is [CurveSegment.start], `1` is [CurveSegment.end]). */
fun CurveSegment.pointAt(t: Double): Vec2 =
    when (this) {
        is CurveSegment.Line -> {
            start + (end - start) * t
        }

        is CurveSegment.Quadratic -> {
            val u = 1.0 - t
            start * (u * u) + control * (2.0 * u * t) + end * (t * t)
        }

        is CurveSegment.Cubic -> {
            val u = 1.0 - t
            start * (u * u * u) + control1 * (3.0 * u * u * t) + control2 * (3.0 * u * t * t) + end * (t * t * t)
        }
    }

/**
 * This contour's segments, in point order, decoded from the flat tagged point list. For
 * [CurveFormat.QUADRATIC], a run of two or more consecutive off-curve points is split into one
 * [CurveSegment.Quadratic] per implied on-curve point, exactly as a rasterizer would draw it (see
 * the module overview). For [CurveFormat.CUBIC], each (on, off, off) triple is one
 * [CurveSegment.Cubic].
 */
fun Contour.segments(): List<CurveSegment> =
    when (format) {
        CurveFormat.QUADRATIC -> decodeQuadraticSegments(points)
        CurveFormat.CUBIC -> decodeCubicSegments(points)
    }

/**
 * This contour's [Segment] chords, one per [Contour.segments] entry, in point order: the straight
 * line from a curve segment's anchor to the next anchor, ignoring any control points in between —
 * exactly what [Segment]'s own KDoc means by "the chord of a curve segment". This is the primitive
 * the `qa` module's fontbakery-style outline heuristics (alignment miss, collinear, short, jaggy,
 * semi-vertical — docs/RESEARCH_font_quality.md's outline section) are built from: each check
 * reasons about the straight node-to-node polygon a designer actually placed, not about how much a
 * curve bulges between two nodes.
 *
 * A [CurveFormat.CUBIC] anchor is always one of the contour's own explicit on-curve [Point]s (see
 * [Contour]'s "starts on-curve, (on, off, off) triples" contract), so its chord endpoints are
 * exact integers with no rounding. A [CurveFormat.QUADRATIC] anchor can also be an *implied*
 * on-curve point — the midpoint of two consecutive off-curve points — which is not generally an
 * integer (for example the midpoint of `(3, 5)` and `(4, 5)` is `(3.5, 5)`); [roundToNearestPoint]
 * rounds that midpoint to the nearest font unit for reporting purposes only. That is a half-unit
 * approximation at most, small next to the 2-3 unit thresholds these checks compare against, and
 * it is never written back as a glyph's coordinate (see the module overview's "integers at rest"
 * note) — only used to describe *where* a heuristic fired.
 */
fun Contour.chords(): List<Segment> = segments().map { Segment(it.start.roundToNearestPoint(), it.end.roundToNearestPoint()) }

/** [v] rounded to the nearest integer [Point], each axis independently. */
private fun Vec2.roundToNearestPoint(): Point = Point(x.roundToInt(), y.roundToInt())

/**
 * This segment's exact contribution to `2 x signedArea` (`∮ x dy − y dx` over the segment's own
 * parameter range), by Green's theorem. Summed across a whole [Contour]'s [Contour.segments] and
 * halved, this gives [Contour.signedArea] exactly — see that function's KDoc for how each
 * segment's closed form was derived and checked.
 */
internal fun CurveSegment.signedAreaContribution(): Double =
    when (this) {
        is CurveSegment.Line -> (start.x * end.y - end.x * start.y) / 2.0
        is CurveSegment.Quadratic -> quadraticAreaContribution(start, control, end)
        is CurveSegment.Cubic -> cubicAreaContribution(start, control1, control2, end)
    }

/**
 * `Area = raw / 6`, where `raw` is the exact symbolic integral of `x(t) y'(t) − y(t) x'(t)` over
 * `t ∈ [0, 1]` for a quadratic Bezier with control points [p0], [p1] (the control point), [p2].
 * Derived by direct integration and cross-checked against dense numerical integration of several
 * concrete segments (see this file's unit tests).
 */
private fun quadraticAreaContribution(
    p0: Vec2,
    p1: Vec2,
    p2: Vec2,
): Double {
    val raw =
        2 * p0.x * p1.y + p0.x * p2.y - 2 * p1.x * p0.y + 2 * p1.x * p2.y -
            p2.x * p0.y - 2 * p2.x * p1.y
    return raw / 6.0
}

/**
 * `Area = raw / 20`, the cubic analogue of [quadraticAreaContribution] for control points [p0],
 * [p1], [p2] (the two control points) and [p3], derived and checked the same way.
 */
private fun cubicAreaContribution(
    p0: Vec2,
    p1: Vec2,
    p2: Vec2,
    p3: Vec2,
): Double {
    val raw =
        6 * p0.x * p1.y + 3 * p0.x * p2.y + p0.x * p3.y -
            6 * p1.x * p0.y + 3 * p1.x * p2.y + 3 * p1.x * p3.y -
            3 * p2.x * p0.y - 3 * p2.x * p1.y + 6 * p2.x * p3.y -
            p3.x * p0.y - 3 * p3.x * p1.y - 6 * p3.x * p2.y
    return raw / 20.0
}

private fun decodeQuadraticSegments(points: List<ContourPoint>): List<CurveSegment> {
    val n = points.size
    val startIdx = points.indexOfFirst { it.onCurve }
    var current: Vec2
    val ordered: List<ContourPoint>
    if (startIdx == -1) {
        // Fully off-curve: the spec's starting point is the implied midpoint of the last and
        // first points, and every one of the n points is then walked in its original order.
        current = midpoint(points[n - 1].point.toVec2(), points[0].point.toVec2())
        ordered = points
    } else {
        current = points[startIdx].point.toVec2()
        ordered = (1 until n).map { points[(startIdx + it) % n] }
    }
    val start = current
    var pendingControl: Vec2? = null
    val segments = mutableListOf<CurveSegment>()
    for (cp in ordered) {
        if (cp.onCurve) {
            val anchor = cp.point.toVec2()
            val control = pendingControl
            segments += if (control == null) CurveSegment.Line(current, anchor) else CurveSegment.Quadratic(current, control, anchor)
            pendingControl = null
            current = anchor
        } else {
            val off = cp.point.toVec2()
            val control = pendingControl
            if (control == null) {
                pendingControl = off
            } else {
                val implied = midpoint(control, off)
                segments += CurveSegment.Quadratic(current, control, implied)
                current = implied
                pendingControl = off
            }
        }
    }
    val control = pendingControl
    segments += if (control == null) CurveSegment.Line(current, start) else CurveSegment.Quadratic(current, control, start)
    return segments
}

private fun decodeCubicSegments(points: List<ContourPoint>): List<CurveSegment> {
    val n = points.size
    val start = points[0].point.toVec2()
    var current = start
    val segments = mutableListOf<CurveSegment>()
    var i = 0
    while (i < n) {
        val c1 = points[i + 1].point.toVec2()
        val c2 = points[i + 2].point.toVec2()
        val nextIdx = (i + 3) % n
        val anchor = if (nextIdx == 0) start else points[nextIdx].point.toVec2()
        segments += CurveSegment.Cubic(current, c1, c2, anchor)
        current = anchor
        i += 3
    }
    return segments
}
