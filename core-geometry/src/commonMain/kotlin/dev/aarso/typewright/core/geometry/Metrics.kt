package dev.aarso.typewright.core.geometry

import kotlin.math.atan2

/**
 * A straight line between two [Point]s: one of a contour's explicit line segments, or the chord
 * of a curve segment. This is the primitive the fontbakery-style outline heuristics named in
 * docs/RESEARCH_font_quality.md (collinear, jaggy, short, semi-vertical) are built from —
 * `core-geometry` exposes the metrics below, not the checks themselves (P1/P1b's job).
 */
data class Segment(
    val start: Point,
    val end: Point,
) {
    /** This segment's Euclidean length in font units. */
    fun length(): Double = (end.toVec2() - start.toVec2()).length()

    /** This segment's direction as a unit [Vec2]; a zero-length segment gives `(0, 0)`. */
    fun unitVector(): Vec2 {
        val d = end.toVec2() - start.toVec2()
        val len = d.length()
        return if (len == 0.0) Vec2(0.0, 0.0) else Vec2(d.x / len, d.y / len)
    }
}

/**
 * The signed angle in radians, in `(-PI, PI]`, turning from [a]'s direction to [b]'s direction
 * (via `atan2(cross, dot)` of their direction vectors). `0` means the two segments point the same
 * way (collinear, no turn); `PI` (or `-PI`) means [b] doubles back on [a]; `±PI/2` means a right
 * angle. A zero-length segment's direction is `(0, 0)`, which makes every angle against it `0` by
 * `atan2`'s convention — callers measuring a real corner should not feed it a degenerate segment.
 *
 * This is deliberately unsigned-agnostic: a caller wanting fontbakery's "jaggy" turn angle (the
 * deviation from straight-through at a shared node) uses this directly on the two segments meeting
 * there; a caller wanting "collinear" compares [angleBetween]'s magnitude (`kotlin.math.abs`) to a
 * threshold, since collinearity does not care which way the turn goes.
 */
fun angleBetween(
    a: Segment,
    b: Segment,
): Double {
    val da = a.end.toVec2() - a.start.toVec2()
    val db = b.end.toVec2() - b.start.toVec2()
    return atan2(da.cross(db), da.dot(db))
}
