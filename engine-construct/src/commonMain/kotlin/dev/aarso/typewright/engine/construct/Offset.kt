package dev.aarso.typewright.engine.construct

import dev.aarso.typewright.core.geometry.Contour
import dev.aarso.typewright.core.geometry.ContourPoint
import dev.aarso.typewright.core.geometry.CubicFitParameters
import dev.aarso.typewright.core.geometry.CurveFormat
import dev.aarso.typewright.core.geometry.CurveSegment
import dev.aarso.typewright.core.geometry.Direction
import dev.aarso.typewright.core.geometry.Point
import dev.aarso.typewright.core.geometry.Vec2
import dev.aarso.typewright.core.geometry.direction
import dev.aarso.typewright.core.geometry.fitClosedContourToCubics
import dev.aarso.typewright.core.geometry.pointAt
import dev.aarso.typewright.core.geometry.segments
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Tunable parameters for [offsetContour]. Defaults are one fixed, global choice, in the same
 * anti-gaming spirit as `core-geometry`'s own [CubicFitParameters]: never adjusted per shape.
 */
data class OffsetParameters(
    /** How finely each cubic segment is flattened to a polyline before offsetting; see [CurveSegment.Cubic.flatten]'s KDoc. */
    val flattenTolerance: Double = DEFAULT_FLATTEN_TOLERANCE,
    /**
     * The miter-limit ratio (SVG/Cairo/Skia's own `stroke-miterlimit` convention; their shared
     * default, `4`, is this parameter's default too): the most a corner's offset point is allowed
     * to extend, as a multiple of the offset distance, before [offsetContour] falls back from a
     * single sharp miter point to a two-point bevel. See [cornerOffset]'s KDoc for exactly how and
     * why — this is the "clipped or capped sanely" rule task P5a-foundations asks this file to
     * document.
     */
    val miterLimit: Double = DEFAULT_MITER_LIMIT,
    /** Passed through to [fitClosedContourToCubics] to refit the offset polyline back to cubics. */
    val fitParameters: CubicFitParameters = CubicFitParameters(),
)

/** [OffsetParameters.flattenTolerance]'s default: a quarter font unit, fine next to the 1-2 unit tolerances `core-geometry`'s own fitter (`DEFAULT_FIT_ERROR_TOLERANCE`) already works at, so flattening itself is never the dominant source of error in an offset's final fitted shape. */
const val DEFAULT_FLATTEN_TOLERANCE: Double = 0.25

/** [OffsetParameters.miterLimit]'s default, matching SVG/Cairo/Skia's own shared stroking default. */
const val DEFAULT_MITER_LIMIT: Double = 4.0

/** How many recursive halvings [flattenCubic] allows before giving up on reaching [DEFAULT_FLATTEN_TOLERANCE] and accepting whatever flatness the last split reached (guards against infinite recursion on a degenerate, zero-length segment). */
private const val MAX_FLATTEN_DEPTH = 24

internal const val EPSILON = 1e-9

// -------------------------------------------------------------------------------------------
// 1. Flattening: a cubic segment (or a whole closed Contour) to a dense Vec2 polyline.
// -------------------------------------------------------------------------------------------

/**
 * Flattens one cubic Bezier segment to a polyline (inclusive of both [CurveSegment.Cubic.start]
 * and [CurveSegment.Cubic.end]) by recursive de Casteljau bisection: a segment is accepted as flat
 * enough once both its control points sit within [tolerance] font units of the straight chord from
 * [CurveSegment.Cubic.start] to [CurveSegment.Cubic.end] (the standard flatness test used by every
 * production Bezier-to-polyline flattener — Skia's and FreeType's rasterizers both test control
 * points against the chord this same way); otherwise it is split exactly at its own midpoint
 * (`t = 0.5`, de Casteljau) and each half is flattened independently and concatenated (dropping the
 * shared midpoint's duplicate).
 */
internal fun CurveSegment.Cubic.flatten(
    tolerance: Double,
    depth: Int = 0,
): List<Vec2> {
    if (depth >= MAX_FLATTEN_DEPTH || isFlatEnough(tolerance)) return listOf(start, end)
    val (left, right) = subdivide(0.5)
    return left.flatten(tolerance, depth + 1) + right.flatten(tolerance, depth + 1).drop(1)
}

private fun CurveSegment.Cubic.isFlatEnough(tolerance: Double): Boolean =
    distanceToLine(control1, start, end) <= tolerance && distanceToLine(control2, start, end) <= tolerance

private fun distanceToLine(
    point: Vec2,
    lineA: Vec2,
    lineB: Vec2,
): Double {
    val direction = lineB - lineA
    val length = direction.length()
    if (length <= EPSILON) return (point - lineA).length()
    return kotlin.math.abs(direction.cross(point - lineA)) / length
}

/** De Casteljau subdivision of this cubic at parameter [t], returning the two resulting cubics (`[0, t]` and `[t, 1]`) that together retrace the same curve exactly. */
internal fun CurveSegment.Cubic.subdivide(t: Double): Pair<CurveSegment.Cubic, CurveSegment.Cubic> {
    val p01 = lerp(start, control1, t)
    val p12 = lerp(control1, control2, t)
    val p23 = lerp(control2, end, t)
    val p012 = lerp(p01, p12, t)
    val p123 = lerp(p12, p23, t)
    val p0123 = lerp(p012, p123, t)
    return CurveSegment.Cubic(start, p01, p012, p0123) to CurveSegment.Cubic(p0123, p123, p23, end)
}

private fun lerp(
    a: Vec2,
    b: Vec2,
    t: Double,
): Vec2 = a + (b - a) * t

/**
 * This closed [Contour] flattened to a dense, closed [Vec2] polyline (implicitly closed: the last
 * returned point connects back to the first, exactly like [Contour] itself) at [tolerance] font
 * units, by [flatten]ing every one of its [Contour.segments] and concatenating them (each
 * segment's own leading point is dropped after the first, since it duplicates the previous
 * segment's trailing point).
 */
internal fun Contour.flattenToPolyline(tolerance: Double): List<Vec2> {
    val segs = segments()
    val points = mutableListOf<Vec2>()
    for ((index, segment) in segs.withIndex()) {
        val flat =
            when (segment) {
                is CurveSegment.Cubic -> segment.flatten(tolerance)
                is CurveSegment.Line -> listOf(segment.start, segment.end)
                is CurveSegment.Quadratic -> flattenQuadratic(segment, tolerance)
            }
        points += if (index == 0) flat else flat.drop(1)
    }
    // The loop above ends at the first segment's own start point again (segments() wraps around),
    // so the trailing duplicate of points[0] is dropped to keep the "implicitly closed" contract.
    if (points.size > 1 && (points.last() - points.first()).length() <= EPSILON) points.removeAt(points.size - 1)
    return points
}

private fun flattenQuadratic(
    segment: CurveSegment.Quadratic,
    tolerance: Double,
): List<Vec2> {
    // Promote to an equivalent cubic (the standard degree-elevation formula) and reuse the one
    // flattener rather than writing a second one: C1 = Q0 + 2/3*(Qc-Q0), C2 = Q2 + 2/3*(Qc-Q2).
    val c1 = segment.start + (segment.control - segment.start) * (2.0 / 3.0)
    val c2 = segment.end + (segment.control - segment.end) * (2.0 / 3.0)
    return CurveSegment.Cubic(segment.start, c1, c2, segment.end).flatten(tolerance)
}

// -------------------------------------------------------------------------------------------
// 2. Per-vertex offsetting: the local normal at a vertex, with a documented, uniform corner rule.
// -------------------------------------------------------------------------------------------

/** The unit right-hand-of-travel normal of unit direction [direction]: rotate 90 degrees clockwise, `(dy, -dx)`. For a counter-clockwise polyline (CLAUDE.md's outer-contour convention) this points outward; see [offsetContour]'s KDoc for how a clockwise (inner) contour's sign is handled. Reused by `Stroke.kt`'s join geometry (`internal`, same module). */
internal fun rightNormal(direction: Vec2): Vec2 = Vec2(direction.y, -direction.x)

internal fun Vec2.normalizedOrNull(): Vec2? {
    val len = length()
    return if (len > EPSILON) Vec2(x / len, y / len) else null
}

/** [rightNormal] of [direction], normalized first; `null` when [direction] is degenerate (zero length). Reused by `Stroke.kt`'s round/bevel join geometry so every join style shares the same normal computation as [cornerOffset]'s own miter. */
internal fun unitRightNormal(direction: Vec2): Vec2? = direction.normalizedOrNull()?.let { rightNormal(it) }

/** The signed angle in radians, in `(-PI, PI]`, from unit vector [a] to unit vector [b] (`atan2(cross, dot)`) — the same convention as `core-geometry`'s [dev.aarso.typewright.core.geometry.angleBetween], restated here over [Vec2] directly since that one is expressed over integer [Point]-based [dev.aarso.typewright.core.geometry.Segment]s. */
internal fun signedAngleBetween(
    a: Vec2,
    b: Vec2,
): Double = kotlin.math.atan2(a.cross(b), a.dot(b))

/**
 * The offset delta(s) for one polyline vertex, given the unit directions of its incoming edge
 * ([prevDirection], `null` at an open polyline's first point) and outgoing edge ([nextDirection],
 * `null` at an open polyline's last point), offsetting by [distance] (signed: positive is always
 * along [rightNormal] of the local direction) with miter behaviour bounded by [miterLimit].
 *
 * **The exact corner-handling rule** (P5a-foundations asks this to be documented explicitly):
 * - **An open polyline's endpoint** (exactly one of [prevDirection]/[nextDirection] is `null`) has
 *   only one adjacent edge, so it offsets by a single, simple perpendicular delta:
 *   `distance * rightNormal(direction)`. No corner geometry applies.
 * - **An ordinary interior vertex** offsets to the point where the two edges' own offset lines
 *   (each edge's line, translated by [distance] along its own [rightNormal]) actually intersect —
 *   the standard **miter join**. Writing `n1`/`n2` for the incoming/outgoing edges' unit normals
 *   and `phi` for the angle between them (`cos(phi) = n1 . n2`), that intersection sits at
 *   `vertex + L * bisector`, where `bisector = normalize(n1 + n2)` and
 *   `L = distance / cos(phi / 2)` (derived from the half-angle identity
 *   `cos(phi/2) = sqrt((1 + cos(phi)) / 2)`, itself a direct consequence of `bisector` splitting
 *   the angle between `n1` and `n2` exactly in half whenever both are unit vectors). This is exact:
 *   a square's offset corner, for example, lands exactly `distance * sqrt(2)` from the original
 *   corner along its 45-degree bisector, which is exactly `(distance, distance)` — the square grows
 *   by exactly `distance` on every side with its corners preserved square, matching
 *   `OffsetTest.squareOffsetOutwardGrowsBySideAndKeepsSquareCorners`.
 * - **A near-reversal (a very sharp reflex turn, `phi` approaching PI)** makes `L` approach
 *   infinity — the two offset edges would meet arbitrarily far from the vertex, a classic
 *   self-intersecting-offset failure mode. This function **clips** it: whenever the plain miter
 *   length would exceed [miterLimit] times [distance] (equivalently, `cos(phi/2)` would need to be
 *   smaller than `1 / miterLimit`), it does not emit that single runaway point at all; instead it
 *   emits the **two** simple per-edge perpendicular offsets (`vertex + distance * n1` and
 *   `vertex + distance * n2`), connected by a straight **bevel** edge when the caller walks the
 *   returned points in order — precisely SVG/Cairo/Skia's own `stroke-miterlimit` fallback,
 *   reused here as one general, uniform, glyph-agnostic rule (never a per-shape branch).
 * - **A degenerate adjacent edge** (`prevDirection`/`nextDirection` itself non-null but
 *   zero-length, which [normalizedOrNull] reports as `null`) is treated the same as a missing
 *   neighbour: this function falls back to whichever single direction is actually available, and
 *   to a zero delta (no offset) only when neither is.
 */
internal fun cornerOffset(
    vertex: Vec2,
    prevDirection: Vec2?,
    nextDirection: Vec2?,
    distance: Double,
    miterLimit: Double,
): List<Vec2> {
    val din = prevDirection?.normalizedOrNull()
    val dout = nextDirection?.normalizedOrNull()
    if (din == null && dout == null) return listOf(vertex)
    if (din == null) return listOf(vertex + rightNormal(dout!!) * distance)
    if (dout == null) return listOf(vertex + rightNormal(din) * distance)

    val n1 = rightNormal(din)
    val n2 = rightNormal(dout)
    val cosPhi = (n1.dot(n2)).coerceIn(-1.0, 1.0)
    val bisectorSum = n1 + n2
    val bisector = bisectorSum.normalizedOrNull()
    // cos(phi/2) via the half-angle identity; phi in [0, PI] from acos's own range, so cos(phi/2)
    // is never negative and this sqrt argument is never negative either.
    val halfAngleCos = sqrt((1.0 + cosPhi) / 2.0)
    if (bisector == null || halfAngleCos <= EPSILON || 1.0 / halfAngleCos > miterLimit) {
        // Bevel fallback: either n1 and n2 are (near-)exact opposites (bisectorSum ~ 0, halfAngleCos
        // ~ 0 -- an almost-full reversal with no well-defined miter direction at all) or the miter
        // would simply overshoot the configured limit. Either way, emit both edges' own simple
        // offsets rather than one runaway point.
        return listOf(vertex + n1 * distance, vertex + n2 * distance)
    }
    val miterLength = distance / halfAngleCos
    return listOf(vertex + bisector * miterLength)
}

// -------------------------------------------------------------------------------------------
// 3. offsetContour: the public entry point (item 1).
// -------------------------------------------------------------------------------------------

private fun centroid(points: List<Vec2>): Vec2 {
    var sx = 0.0
    var sy = 0.0
    for (p in points) {
        sx += p.x
        sy += p.y
    }
    return Vec2(sx / points.size, sy / points.size)
}

/**
 * A degenerate single-point [Contour]: [buildDegenerateContour]'s own KDoc explains why this is
 * [offsetContour]'s defined "sensible" answer when an offset consumes a shape entirely, rather
 * than a self-intersecting polygon this module makes no attempt to clean up (booleans are a later
 * task's job — task P5a-foundations, "Do NOT implement... booleans").
 */
private fun buildDegenerateContour(at: Point): Contour =
    Contour(
        listOf(
            ContourPoint(at, onCurve = true),
            ContourPoint(at, onCurve = false),
            ContourPoint(at, onCurve = false),
        ),
        CurveFormat.CUBIC,
    )

/**
 * Offsets a closed cubic [Contour] by signed [distance] font units: **positive expands outward**
 * for a counter-clockwise contour (CLAUDE.md's outer-CCW convention); for a clockwise contour (an
 * inner ring, like `o`'s counter, or a stroke's inner ring — CLAUDE.md's inner-CW convention),
 * positive still means "outward from the ring's own enclosed area", achieved by flipping the raw
 * per-vertex normal sign whenever [Contour.direction] reads [Direction.CLOCKWISE] — so a caller
 * never has to think about winding to get "grow" versus "shrink" right on any ring.
 *
 * **Method** (task P5a-foundations item 1, in order):
 * 1. [Contour.flattenToPolyline] at [OffsetParameters.flattenTolerance].
 * 2. Offset every polyline vertex along its local normal via [cornerOffset] (angle-bisector miter,
 *    with the documented bevel fallback for a sharp reflex corner).
 * 3. Round the offset polyline back to integer [Point]s ("integers at rest").
 * 4. **Degenerate-collapse check.** An inward offset larger than the shape's own local half-width
 *    (the named test: "a square offset inward by more than half the side") makes a naive per-vertex
 *    offset polygon fold back on itself — this function does not attempt real self-intersection
 *    removal (that is a boolean operation, explicitly out of this task's scope). The general,
 *    glyph-agnostic signal used to detect it: for every original edge (`flattened[i]` to
 *    `flattened[i + 1]`), compare its direction to its own offset counterpart's direction (the
 *    offset point [cornerOffset] attributes to that edge's start, to the one it attributes to that
 *    edge's end — see [edgeOffsetEndpoints]). A valid offset, inward or outward, keeps every edge
 *    pointing the *same* general way it started (`dot(original, offset) > 0`); once an inward
 *    offset exceeds the local feature size, at least one edge's own offset counterpart reverses
 *    direction entirely (`dot <= 0`) — it has been offset straight through and past the shape's own
 *    opposite side. (A whole-polygon shoelace-area sign check was tried first and rejected: a
 *    fully symmetric shape, a square's four corners in particular, offsets *through* its own centre
 *    and back out the other side as a still-simple, still-same-winding, same-signed-area polygon,
 *    so a global area check alone misses exactly the case this needs to catch — verified against a
 *    square in this file's own development notes; the per-edge direction check catches it exactly
 *    at the point half the side is crossed.) When any edge reverses, this function **collapses the
 *    result to a single degenerate point** at the original polyline's own centroid (see
 *    [buildDegenerateContour]) rather than returning self-crossing geometry. This is "sensibly" for
 *    this primitive: no ink, cleanly, rather than a self-intersecting shape; verified in
 *    `OffsetTest.squareOffsetInwardPastHalfSideCollapsesToAPoint`.
 * 5. Otherwise, refit with `core-geometry`'s existing [fitClosedContourToCubics] (corner-detect plus
 *    Schneider fitting) — never a second fitter written here.
 */
fun offsetContour(
    contour: Contour,
    distance: Double,
    params: OffsetParameters = OffsetParameters(),
): Contour {
    val flattened = contour.flattenToPolyline(params.flattenTolerance)
    require(flattened.size >= 3) { "a contour needs at least 3 distinct flattened points to offset, had ${flattened.size}" }
    val signedDistance = if (contour.direction() == Direction.CLOCKWISE) -distance else distance

    val n = flattened.size
    val perVertexOffsets =
        (0 until n).map { i ->
            val prev = flattened[(i - 1 + n) % n]
            val curr = flattened[i]
            val next = flattened[(i + 1) % n]
            cornerOffset(curr, curr - prev, next - curr, signedDistance, params.miterLimit)
        }

    for (i in 0 until n) {
        val (offsetStart, offsetEnd) = edgeOffsetEndpoints(perVertexOffsets, i, n)
        val originalEdge = flattened[(i + 1) % n] - flattened[i]
        val offsetEdge = offsetEnd - offsetStart
        if (originalEdge.dot(offsetEdge) <= 0.0) {
            return buildDegenerateContour(centroid(flattened).roundToPointLocal())
        }
    }

    val offsetPoints = perVertexOffsets.flatten()
    val rounded = offsetPoints.map { it.roundToPointLocal() }
    val deduped = dedupeCyclicConsecutive(rounded)
    if (deduped.size < 3) {
        return buildDegenerateContour(centroid(flattened).roundToPointLocal())
    }
    return fitClosedContourToCubics(deduped, params.fitParameters)
}

/**
 * The two offset points that represent original edge `i`'s own start and end, out of
 * [cornerOffset]'s per-vertex results ([perVertexOffsets], one list per original vertex, `n`
 * vertices total): edge `i` runs from vertex `i` to vertex `i + 1`, so its offset start is the
 * *last* point [cornerOffset] emitted at vertex `i` (an ordinary vertex emits one point that is
 * simultaneously the previous edge's end and this edge's start; a bevelled vertex emits two, the
 * first belonging to the incoming edge and the last to the outgoing one — see [cornerOffset]'s
 * KDoc), and its offset end is the *first* point emitted at vertex `i + 1`.
 */
private fun edgeOffsetEndpoints(
    perVertexOffsets: List<List<Vec2>>,
    edgeIndex: Int,
    vertexCount: Int,
): Pair<Vec2, Vec2> {
    val start = perVertexOffsets[edgeIndex].last()
    val end = perVertexOffsets[(edgeIndex + 1) % vertexCount].first()
    return start to end
}

private fun Vec2.roundToPointLocal(): Point = Point(x.roundToInt(), y.roundToInt())

/** Drops a point that exactly repeats its cyclic predecessor (can happen at a bevel whose two emitted points rounded to the same integer font unit). */
private fun dedupeCyclicConsecutive(points: List<Point>): List<Point> {
    if (points.isEmpty()) return points
    val result = mutableListOf<Point>()
    for (p in points) {
        if (result.isEmpty() || result.last() != p) result += p
    }
    if (result.size > 1 && result.first() == result.last()) result.removeAt(result.size - 1)
    return result
}
