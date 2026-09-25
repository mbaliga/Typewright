// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.construct

import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.CubicFitParameters
import com.asoc.typewright.core.geometry.Direction
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.core.geometry.direction
import com.asoc.typewright.core.geometry.fitClosedContourToCubics
import com.asoc.typewright.core.geometry.reverse
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** How an open [strokeOpenPolyline] centreline's two ends are finished. */
enum class LineCap { BUTT, ROUND, SQUARE }

/** How a stroke's interior corners are joined. */
enum class LineJoin { MITER, ROUND, BEVEL }

/** Tunable parameters for [strokeOpenPolyline] and [strokeClosedContour]. */
data class StrokeParameters(
    /** The stroke's full width in font units; each side offsets by exactly half of it (task P5a-foundations item 2). */
    val width: Double,
    val cap: LineCap = LineCap.BUTT,
    val join: LineJoin = LineJoin.MITER,
    /** Passed through to [cornerOffset] for [LineJoin.MITER]. */
    val miterLimit: Double = DEFAULT_MITER_LIMIT,
    /** How finely a [LineJoin.ROUND] join or a [LineCap.ROUND] cap is sampled; see [roundArcPoints]'s KDoc. */
    val roundStepRadians: Double = DEFAULT_ROUND_STEP_RADIANS,
    val flattenTolerance: Double = DEFAULT_FLATTEN_TOLERANCE,
    val fitParameters: CubicFitParameters = CubicFitParameters(),
) {
    init {
        require(width > 0.0) { "stroke width must be positive, was $width" }
    }
}

/** [StrokeParameters.roundStepRadians]'s default: about 10 degrees, fine enough that a round join or cap reads as smooth at any letterform scale without generating an excessive point count. */
const val DEFAULT_ROUND_STEP_RADIANS: Double = PI / 18.0

// -------------------------------------------------------------------------------------------
// Join geometry, shared by the open-polyline sides and the closed-contour rings below.
// -------------------------------------------------------------------------------------------

/**
 * The offset delta(s) for one interior polyline vertex under [join] (an extension of
 * [cornerOffset]'s own miter-with-bevel-fallback to also cover an explicit, caller-chosen
 * [LineJoin.BEVEL] or [LineJoin.ROUND] — task P5a-foundations item 2's "join style
 * [miter/round/bevel]"): [LineJoin.MITER] delegates to [cornerOffset] unchanged (including its own
 * miter-limit bevel fallback); [LineJoin.BEVEL] always emits the two simple per-edge perpendicular
 * offsets, regardless of angle; [LineJoin.ROUND] emits an arc between them via [roundArcPoints]. A
 * missing neighbour (an open polyline's own endpoint, where this is reused for the plain
 * perpendicular offset rather than a real join) falls back to [cornerOffset]'s own single-direction
 * handling, the same as every other offset in this file.
 */
internal fun strokeJoinOffset(
    vertex: Vec2,
    prevDirection: Vec2?,
    nextDirection: Vec2?,
    distance: Double,
    join: LineJoin,
    miterLimit: Double,
    roundStepRadians: Double,
): List<Vec2> {
    if (prevDirection == null || nextDirection == null) {
        return cornerOffset(vertex, prevDirection, nextDirection, distance, miterLimit)
    }
    return when (join) {
        LineJoin.MITER -> cornerOffset(vertex, prevDirection, nextDirection, distance, miterLimit)
        LineJoin.BEVEL -> bevelPoints(vertex, prevDirection, nextDirection, distance)
        LineJoin.ROUND -> roundArcPoints(vertex, prevDirection, nextDirection, distance, roundStepRadians)
    }
}

private fun bevelPoints(
    vertex: Vec2,
    prevDirection: Vec2,
    nextDirection: Vec2,
    distance: Double,
): List<Vec2> {
    val n1 = unitRightNormal(prevDirection) ?: return listOf(vertex)
    val n2 = unitRightNormal(nextDirection) ?: return listOf(vertex)
    return listOf(vertex + n1 * distance, vertex + n2 * distance)
}

/**
 * An arc of points around [vertex], radius `|distance|`, from `vertex + distance * rightNormal(prevDirection)`
 * to `vertex + distance * rightNormal(nextDirection)`, swept the *short* way (magnitude at most PI
 * — [signedAngleBetween]'s own range) and sampled every [stepRadians] (at least the two endpoints,
 * always included). This is the correct, standard round join for the turn angles an ordinary
 * letterform join or a semicircular end cap ([capArc], which is exactly a PI-magnitude special
 * case of this same construction) actually produces; an extreme reflex turn whose true round join
 * would need to sweep *more* than PI is out of this function's scope (documented, not silently
 * mishandled: [signedAngleBetween] always reports the short way, so this function's arc would cut
 * across the corner rather than bulge around it in that case) — task P5a-foundations' own named
 * round-join/round-cap tests (a circular ring; a moderate polygonal join) stay well inside it.
 */
internal fun roundArcPoints(
    vertex: Vec2,
    prevDirection: Vec2,
    nextDirection: Vec2,
    distance: Double,
    stepRadians: Double,
): List<Vec2> {
    val n1 = unitRightNormal(prevDirection) ?: return listOf(vertex)
    val n2 = unitRightNormal(nextDirection) ?: return listOf(vertex)
    return capArc(vertex, n1, signedAngleBetween(n1, n2), distance, stepRadians)
}

/**
 * The arc of points around [center] from `center + distance * fromNormal`, sweeping by the
 * explicit, caller-computed [sweep] radians (never re-derived from a second "to" vector — see
 * [capPoints]'s KDoc for exactly why an end cap's own sweep must be computed from
 * `tangentOutward`, not from the antipodal `-fromNormal`, which is ambiguous between `+PI` and
 * `-PI`), sampled every [stepRadians] font units of arc angle, both endpoints included.
 */
private fun capArc(
    center: Vec2,
    fromNormal: Vec2,
    sweep: Double,
    distance: Double,
    stepRadians: Double,
): List<Vec2> {
    val startAngle = kotlin.math.atan2(fromNormal.y, fromNormal.x)
    val steps = ceil(abs(sweep) / stepRadians).toInt().coerceAtLeast(1)
    return (0..steps).map { k ->
        val angle = startAngle + sweep * (k.toDouble() / steps)
        center + Vec2(cos(angle), sin(angle)) * distance
    }
}

/**
 * The intermediate cap points between `fromPoint` (`centerline endpoint + distance * rightNormal`
 * of the last/first edge direction) and `toPoint` (the same, negated distance — the stroke's other
 * side), for an open centreline's end under [cap]. Excludes `fromPoint`/`toPoint` themselves (the
 * caller's own side-offset lists already contain them); [LineCap.BUTT] therefore returns nothing
 * (the two sides connect directly, exactly a straight cross-edge).
 *
 * [tangentOutward] must be the unit direction pointing *away* from the stroke body at this end
 * (continuing forward past the last point at the end cap; continuing backward past the first point
 * at the start cap). **Why the round cap's sweep sign is computed from [tangentOutward], not from
 * `-fromNormal` directly**: `fromPoint - center` is `distance * rightNormal(travelDirection)` and
 * `tangentOutward` is `± travelDirection`; since `rightNormal` is a fixed 90-degree rotation,
 * `cross(rightNormal(d), d) = 1` for *any* unit `d` (a direction-independent identity — rotating
 * `d` by -90 degrees and crossing back with `d` always recovers `|d|^2 = 1`), so the signed angle
 * from `fromNormal` to `tangentOutward` is *always* exactly `+PI/2`, giving this function an
 * unambiguous sweep of `sign(cross(fromNormal, tangentOutward)) * PI`: the full semicircle always
 * bulges through `tangentOutward` — outward, away from the stroke body — regardless of which end
 * this is or which way the centreline happens to run. (Computing the sweep as
 * `signedAngleBetween(fromNormal, -fromNormal)` instead, the seemingly obvious approach, is
 * numerically ambiguous — `atan2` of two exactly-antipodal vectors can round to either `+PI` or
 * `-PI` — which is exactly the bug this explicit, `tangentOutward`-derived sign avoids.)
 */
internal fun capPoints(
    cap: LineCap,
    fromPoint: Vec2,
    toPoint: Vec2,
    center: Vec2,
    tangentOutward: Vec2,
    halfWidth: Double,
    stepRadians: Double,
): List<Vec2> =
    when (cap) {
        LineCap.BUTT -> {
            emptyList()
        }

        LineCap.SQUARE -> {
            listOf(fromPoint + tangentOutward * halfWidth, toPoint + tangentOutward * halfWidth)
        }

        LineCap.ROUND -> {
            val fromNormal = (fromPoint - center).normalizedOrNull() ?: return emptyList()
            val sweepSign = if (fromNormal.cross(tangentOutward) >= 0.0) 1.0 else -1.0
            val full = capArc(center, fromNormal, sweepSign * PI, halfWidth, stepRadians)
            full.drop(1).dropLast(1)
        }
    }

// -------------------------------------------------------------------------------------------
// strokeOpenPolyline (task item 2): an open, straight-segment centreline.
// -------------------------------------------------------------------------------------------

/**
 * Strokes an open, straight-segment centreline (at least 2 distinct [Point]s) into one filled,
 * closed [Contour]: each side offsets by `width / 2` (reusing [strokeJoinOffset], so interior
 * vertices get [StrokeParameters.join]'s corner treatment exactly like [offsetContour]'s own), the
 * two ends are finished with [StrokeParameters.cap] (see [capPoints]), and the whole loop is
 * refitted to cubics with `core-geometry`'s [fitClosedContourToCubics] — never a second fitter.
 *
 * A **curved** open centreline is out of this function's scope (`core-geometry` itself has no
 * "open `Contour`" type to accept one — see this file's own module notes); a caller with a curved
 * centreline flattens it to a dense polyline first (this module's own [CurveSegment.Cubic.flatten]
 * primitive) and passes that.
 */
fun strokeOpenPolyline(
    centerline: List<Point>,
    params: StrokeParameters,
): Contour {
    val points = dedupeConsecutive(centerline.map { it.toVec2() })
    require(points.size >= 2) { "an open centreline needs at least 2 distinct points, had ${points.size}" }
    val halfWidth = params.width / 2.0
    val m = points.size

    fun sideOffsets(distance: Double): List<Vec2> =
        (0 until m)
            .map { i ->
                val prevDir = if (i > 0) points[i] - points[i - 1] else null
                val nextDir = if (i < m - 1) points[i + 1] - points[i] else null
                strokeJoinOffset(points[i], prevDir, nextDir, distance, params.join, params.miterLimit, params.roundStepRadians)
            }.flatten()

    val rightSide = sideOffsets(halfWidth)
    val leftSide = sideOffsets(-halfWidth)

    val endTangent = (points[m - 1] - points[m - 2]).normalizedOrNull() ?: Vec2(1.0, 0.0)
    val startTangentOutward = (points[0] - points[1]).normalizedOrNull() ?: Vec2(-1.0, 0.0)

    val endCap = capPoints(params.cap, rightSide.last(), leftSide.last(), points[m - 1], endTangent, halfWidth, params.roundStepRadians)
    val startCap =
        capPoints(params.cap, leftSide.first(), rightSide.first(), points[0], startTangentOutward, halfWidth, params.roundStepRadians)

    val loop = rightSide + endCap + leftSide.reversed() + startCap
    val rounded = loop.map { it.roundToPointLocal() }
    val deduped = dedupeCyclicConsecutivePoints(rounded)
    require(
        deduped.size >= 3,
    ) { "stroking this centreline collapsed to fewer than 3 points; width ${params.width} may be degenerate for it" }
    return fitClosedContourToCubics(deduped, params.fitParameters)
}

// -------------------------------------------------------------------------------------------
// strokeClosedContour (task item 2): a closed centreline (polygon or curve) strokes to a ring.
// -------------------------------------------------------------------------------------------

/**
 * Strokes a closed centreline (a polygonal or curved [Contour], such as a circle) into **two**
 * nested [Contour]s — a ring, not one filled shape (task P5a-foundations item 2: "leaving it as two
 * nested contours... like a circle stroked into a ring"): flattens [centerline]
 * ([Contour.flattenToPolyline] at [StrokeParameters.flattenTolerance]), then offsets it by
 * `+width/2` for the outer boundary and by `-width/2` for the inner one, each with
 * [StrokeParameters.join]'s corner treatment ([strokeJoinOffset], cyclic — every vertex is an
 * interior join, an open centreline's endpoint case never applies here).
 *
 * **Winding.** The outer ring keeps [centerline]'s own winding (CLAUDE.md's outer-CCW convention,
 * assuming [centerline] is itself drawn CCW, as every outer contour in this app is); the inner ring
 * is the *same* offset construction, sign-flipped ([offsetContour]'s own outward/inward
 * convention: `-width/2` means "outward from the OUTER ring's material" already, which for the
 * inner boundary of a ring is a genuine shrink), then [reverse]d so it reads clockwise, matching
 * CLAUDE.md's inner-CW convention for a hole.
 */
fun strokeClosedContour(
    centerline: Contour,
    params: StrokeParameters,
): List<Contour> {
    val flattened = centerline.flattenToPolyline(params.flattenTolerance)
    require(flattened.size >= 3) { "a closed centreline needs at least 3 distinct flattened points, had ${flattened.size}" }
    val halfWidth = params.width / 2.0
    val signFlip = if (centerline.direction() == Direction.CLOCKWISE) -1.0 else 1.0

    fun ring(distance: Double): List<Point> {
        val n = flattened.size
        val offset =
            (0 until n).flatMap { i ->
                val prev = flattened[(i - 1 + n) % n]
                val curr = flattened[i]
                val next = flattened[(i + 1) % n]
                strokeJoinOffset(
                    curr,
                    curr - prev,
                    next - curr,
                    distance * signFlip,
                    params.join,
                    params.miterLimit,
                    params.roundStepRadians,
                )
            }
        return dedupeCyclicConsecutivePoints(offset.map { it.roundToPointLocal() })
    }

    val outerPoints = ring(halfWidth)
    val innerPoints = ring(-halfWidth)
    require(outerPoints.size >= 3 && innerPoints.size >= 3) {
        "stroking this centreline collapsed a ring to fewer than 3 points; width ${params.width} may exceed the centreline's own local size"
    }
    val outer = fitClosedContourToCubics(outerPoints, params.fitParameters)
    val inner = fitClosedContourToCubics(innerPoints, params.fitParameters).reverse()
    return listOf(outer, inner)
}

private fun dedupeConsecutive(points: List<Vec2>): List<Vec2> {
    val result = mutableListOf<Vec2>()
    for (p in points) {
        if (result.isEmpty() || (p - result.last()).length() > EPSILON) result += p
    }
    return result
}

private fun Vec2.roundToPointLocal(): Point = Point(x.roundToInt(), y.roundToInt())

private fun dedupeCyclicConsecutivePoints(points: List<Point>): List<Point> {
    if (points.isEmpty()) return points
    val result = mutableListOf<Point>()
    for (p in points) {
        if (result.isEmpty() || result.last() != p) result += p
    }
    if (result.size > 1 && result.first() == result.last()) result.removeAt(result.size - 1)
    return result
}
