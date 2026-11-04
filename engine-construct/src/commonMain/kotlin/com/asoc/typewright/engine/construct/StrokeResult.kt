// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.construct

import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.ContourPoint
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Direction
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.core.geometry.direction
import com.asoc.typewright.core.geometry.reverse
import com.asoc.typewright.core.geometry.signedArea
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** What [strokeCentreline] or [strokeRing] made, or why it made nothing. A refusal is a sentence a person can act on. */
sealed interface StrokeResult {
    /** The closed outline(s) the stroke fills: one contour for an open centreline, two (outer, inner) for a ring; wound outer counter-clockwise, inner clockwise. */
    data class Done(
        val contours: List<Contour>,
    ) : StrokeResult

    /** Nothing was made, for [reason]. */
    data class Refused(
        val reason: String,
    ) : StrokeResult
}

/** The narrowest stroke that survives rounding to whole font units. */
const val MIN_STROKE_WIDTH_UNITS: Double = 2.0

/** The widest stroke accepted, in font units: our heuristic, a stroke wider than two ems is a typing slip, not a letter. */
const val MAX_STROKE_WIDTH_UNITS: Double = 2000.0

/** Why [widthUnits] cannot be a stroke width, in a sentence, or null when it can. */
fun strokeWidthProblem(widthUnits: Double): String? =
    when {
        widthUnits.isNaN() || widthUnits < MIN_STROKE_WIDTH_UNITS -> {
            "A stroke needs a width of at least ${MIN_STROKE_WIDTH_UNITS.toInt()} units."
        }

        widthUnits > MAX_STROKE_WIDTH_UNITS -> {
            "A stroke wider than ${MAX_STROKE_WIDTH_UNITS.toInt()} units is not a letter's stroke."
        }

        else -> {
            null
        }
    }

/**
 * Turns an open, straight-sided [centreline] (two or more distinct points, in order) into the closed cubic outline of a
 * stroke of [params]'s width: butt, round or square ends as [StrokeParameters.cap] says, and miter, round or bevel
 * corners as [StrokeParameters.join] says. The outline is built directly, not fitted: a side is a straight line (a cubic
 * whose controls sit on its own ends, the way a `.glif` stores a line), a round join or cap is an exact quarter-circle
 * cubic, and a corner that is not one is not a node, so a stroke costs only the nodes its shape needs. It winds
 * counter-clockwise.
 *
 * Refused, with the reason, when the width is not a usable one, the centreline has fewer than two distinct points, the
 * centreline crosses itself (its stroke would overlap itself; draw it as separate strokes and join them with a union),
 * or the width is too wide for the centreline and the outline folds over itself.
 */
fun strokeCentreline(
    centreline: List<Point>,
    params: StrokeParameters,
): StrokeResult {
    strokeWidthProblem(params.width)?.let { return StrokeResult.Refused(it) }
    val distinct = centreline.fold(emptyList<Point>()) { acc, p -> if (acc.lastOrNull() == p) acc else acc + p }
    if (distinct.size < 2) return StrokeResult.Refused("A centreline needs two different points.")
    val points = distinct.map { it.toVec2() }
    if (polylineCrossesItself(points)) {
        return StrokeResult.Refused(
            "The centreline crosses itself, so its stroke would overlap itself. Draw it as separate strokes and join them with a union.",
        )
    }
    val outline = outlineOf(points, params)
    if (outline.selfCrosses(params.flattenTolerance)) return StrokeResult.Refused(tooWide(params.width))
    return StrokeResult.Done(listOf(if (outline.direction() == Direction.CLOCKWISE) outline.reverse() else outline))
}

/**
 * Strokes the closed cubic [centreline] into a ring: an outer contour half the width outside it and an inner one half
 * the width inside it, wound outer counter-clockwise and inner clockwise. Refused, with the reason, for a quadratic
 * centreline, an unusable width, or a width too wide for the shape (the inner side would fold over itself). Caps do not
 * apply to a closed centreline. Sides of the result that are straight are written as lines.
 */
fun strokeRing(
    centreline: Contour,
    params: StrokeParameters,
): StrokeResult {
    strokeWidthProblem(params.width)?.let { return StrokeResult.Refused(it) }
    if (centreline.format != CurveFormat.CUBIC) return StrokeResult.Refused("Strokes are cubic curves; this outline is quadratic.")
    val rings =
        try {
            strokeClosedContour(centreline, params)
        } catch (_: IllegalArgumentException) {
            return StrokeResult.Refused(tooWide(params.width))
        }
    if (rings.any { it.selfCrosses(params.flattenTolerance) }) return StrokeResult.Refused(tooWide(params.width))
    val (outer, inner) = rings
    // The outer ring keeps the centreline's winding; a clockwise one is turned so the pair follows the outer-anticlockwise rule.
    val turn = outer.direction() == Direction.CLOCKWISE
    val pair = (if (turn) listOf(outer.reverse(), inner.reverse()) else listOf(outer, inner)).map { it.withStraightSidesAsLines() }
    // A width beyond the shape turns the inner ring inside out (a point reflection keeps its winding, so only its size gives it
    // away): the hole of a ring is always smaller than the centreline it is inset from.
    if (pair[0].signedArea() <= 0.0 || pair[1].signedArea() >= 0.0 || -pair[1].signedArea() >= abs(centreline.signedArea())) {
        return StrokeResult.Refused(tooWide(params.width))
    }
    return StrokeResult.Done(pair)
}

private fun tooWide(width: Double): String = "A stroke ${width.toInt()} units wide folds over itself on this shape. Try a narrower width."

private fun Contour.selfCrosses(tolerance: Double): Boolean = polylineCrossesItself(flattenToPolyline(tolerance), closed = true)

// ---- building the outline directly --------------------------------------------------------------

/** A corner of the outline and the controls of the curve that leaves it for the next ([out]); null is a straight side. */
private class Corner(
    val at: Vec2,
    var out: Pair<Vec2, Vec2>? = null,
)

private const val QUARTER_ARC_HANDLE = 0.5522847498307936

private fun outlineOf(
    points: List<Vec2>,
    params: StrokeParameters,
): Contour {
    val half = params.width / 2.0
    val corners = mutableListOf<Corner>()
    corners += rightSide(points, half, params)
    val last = points.size - 1
    val endDirection = (points[last] - points[last - 1]).unit()
    capAt(corners, points[last], endDirection, half, params.cap)
    corners += rightSide(points.reversed(), half, params)
    val startDirection = (points[0] - points[1]).unit()
    capAt(corners, points[0], startDirection, half, params.cap)
    return contourOf(corners)
}

/** The offset of the polyline by [half] on its right-hand side, from its start to its end, with [StrokeParameters.join] at every corner. */
private fun rightSide(
    points: List<Vec2>,
    half: Double,
    params: StrokeParameters,
): List<Corner> {
    val directions = (0 until points.size - 1).map { (points[it + 1] - points[it]).unit() }
    val out = mutableListOf<Corner>()
    out += Corner(points[0] + directions[0].rightNormal() * half)
    for (i in 1 until points.size - 1) {
        val before = directions[i - 1]
        val after = directions[i]
        val n1 = before.rightNormal()
        val n2 = after.rightNormal()
        val turn = before.cross(after)
        val dot = before.dot(after)
        val vertex = points[i]
        // The right-hand side is the outside of a left turn (positive cross) and the inside of a right turn.
        val outside = turn > TURN_EPSILON
        val reversal = 1.0 + n1.dot(n2) < REVERSAL_EPSILON
        when {
            reversal -> {
                out += Corner(vertex + n1 * half)
                out += Corner(vertex + n2 * half)
            }

            !outside || (params.join == LineJoin.MITER && withinMiterLimit(n1, n2, params.miterLimit)) -> {
                out += Corner(vertex + (n1 + n2) * (half / (1.0 + n1.dot(n2))))
            }

            params.join == LineJoin.ROUND -> {
                roundJoin(out, vertex, before, n1, atan2(turn, dot), half)
            }

            else -> {
                out += Corner(vertex + n1 * half)
                out += Corner(vertex + n2 * half)
            }
        }
    }
    out += Corner(points.last() + directions.last().rightNormal() * half)
    return out
}

private fun withinMiterLimit(
    n1: Vec2,
    n2: Vec2,
    miterLimit: Double,
): Boolean = sqrt(2.0 / (1.0 + n1.dot(n2))) <= miterLimit

/** An arc round [vertex] from the offset of the incoming side to the offset of the outgoing one, [sweep] radians anticlockwise, in cubic pieces of at most a quarter turn. */
private fun roundJoin(
    out: MutableList<Corner>,
    vertex: Vec2,
    incoming: Vec2,
    incomingNormal: Vec2,
    sweep: Double,
    radius: Double,
) {
    val pieces = ceil(sweep / (PI / 2.0) - PIECE_SLACK).toInt().coerceAtLeast(1)
    val piece = sweep / pieces
    val handle = 4.0 / 3.0 * tan(piece / 4.0) * radius
    out += Corner(vertex + incomingNormal * radius)
    for (j in 0 until pieces) {
        val startTangent = incoming.rotated(j * piece)
        val endTangent = incoming.rotated((j + 1) * piece)
        val end = vertex + incomingNormal.rotated((j + 1) * piece) * radius
        out.last().out = (out.last().at + startTangent * handle) to (end - endTangent * handle)
        out += Corner(end)
    }
}

/** The end of the stroke at [at], where the outline leaves its right-hand side for the other: [direction] is the way the stroke runs out. */
private fun capAt(
    corners: MutableList<Corner>,
    at: Vec2,
    direction: Vec2,
    half: Double,
    cap: LineCap,
) {
    val normal = direction.rightNormal()
    when (cap) {
        LineCap.BUTT -> {
            Unit
        }

        LineCap.SQUARE -> {
            corners += Corner(corners.last().at + direction * half)
            corners += Corner(at - normal * half + direction * half)
        }

        LineCap.ROUND -> {
            val handle = QUARTER_ARC_HANDLE * half
            val tip = at + direction * half
            val from = corners.last()
            from.out = (from.at + direction * handle) to (tip + normal * handle)
            val toward = Corner(tip)
            toward.out = (tip - normal * handle) to (at - normal * half + direction * handle)
            corners += toward
        }
    }
}

private fun contourOf(corners: List<Corner>): Contour {
    val kept = withoutRedundantCorners(corners)
    val points = mutableListOf<ContourPoint>()
    for ((i, corner) in kept.withIndex()) {
        val next = kept[(i + 1) % kept.size]
        val start = corner.at.rounded()
        val end = next.at.rounded()
        points += ContourPoint(start, onCurve = true)
        val controls = corner.out
        points += ContourPoint(controls?.first?.rounded() ?: start, onCurve = false)
        points += ContourPoint(controls?.second?.rounded() ?: end, onCurve = false)
    }
    return Contour(points, CurveFormat.CUBIC)
}

/** Corners that are not corners: a repeat of the one before, or a point straight on between two straight sides. */
private fun withoutRedundantCorners(corners: List<Corner>): List<Corner> {
    val current = corners.toMutableList()
    var changed = true
    while (changed && current.size > 3) {
        changed = false
        for (i in current.indices) {
            val before = current[(i - 1 + current.size) % current.size]
            val here = current[i]
            val after = current[(i + 1) % current.size]
            val repeat = before.out == null && (here.at - before.at).length() < REPEAT_EPSILON
            val straightOn =
                before.out == null && here.out == null &&
                    abs((here.at - before.at).cross(after.at - here.at)) < COLLINEAR_EPSILON &&
                    (here.at - before.at).dot(after.at - here.at) > 0.0
            if (repeat || straightOn) {
                current.removeAt(i)
                changed = true
                break
            }
        }
    }
    return current
}

/** This contour with every segment whose controls lie on its own chord rewritten as a straight line (controls on the ends). */
private fun Contour.withStraightSidesAsLines(): Contour {
    val rewritten = points.toMutableList()
    for (i in 0 until points.size / 3) {
        val start = points[3 * i]
        val first = points[3 * i + 1]
        val second = points[3 * i + 2]
        val end = points[(3 * i + 3) % points.size]
        val chord = end.point.toVec2() - start.point.toVec2()
        val length = chord.length()
        if (length < 1.0) continue
        val straight =
            listOf(first, second).all { control ->
                abs(chord.cross(control.point.toVec2() - start.point.toVec2())) / length <= STRAIGHT_WITHIN_UNITS
            }
        if (straight) {
            rewritten[3 * i + 1] = first.copy(point = start.point)
            rewritten[3 * i + 2] = second.copy(point = end.point)
        }
    }
    return copy(points = rewritten)
}

private fun Vec2.unit(): Vec2 = this * (1.0 / length())

private fun Vec2.rightNormal(): Vec2 = Vec2(y, -x)

private fun Vec2.rotated(angle: Double): Vec2 = Vec2(x * cos(angle) - y * sin(angle), x * sin(angle) + y * cos(angle))

private fun Vec2.rounded(): Point = Point(x.roundToInt(), y.roundToInt())

private const val TURN_EPSILON = 1e-9
private const val REVERSAL_EPSILON = 1e-6
private const val PIECE_SLACK = 1e-9
private const val REPEAT_EPSILON = 0.5
private const val COLLINEAR_EPSILON = 0.5
private const val STRAIGHT_WITHIN_UNITS = 0.75

// ---- crossing --------------------------------------------------------------------------------------

/** True when two non-neighbouring sides of the polyline properly cross each other (closed: the last point joins the first). */
internal fun polylineCrossesItself(
    points: List<Vec2>,
    closed: Boolean = false,
): Boolean {
    val n = points.size
    val sides = if (closed) n else n - 1
    for (i in 0 until sides) {
        for (j in i + 1 until sides) {
            val neighbours = j == i + 1 || (closed && i == 0 && j == sides - 1)
            if (neighbours) continue
            if (properlyCross(points[i], points[(i + 1) % n], points[j], points[(j + 1) % n])) return true
        }
    }
    return false
}

private fun properlyCross(
    a: Vec2,
    b: Vec2,
    c: Vec2,
    d: Vec2,
): Boolean {
    val d1 = orientation(c, d, a)
    val d2 = orientation(c, d, b)
    val d3 = orientation(a, b, c)
    val d4 = orientation(a, b, d)
    return d1 * d2 < 0.0 && d3 * d4 < 0.0
}

private fun orientation(
    a: Vec2,
    b: Vec2,
    p: Vec2,
): Double = (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)
