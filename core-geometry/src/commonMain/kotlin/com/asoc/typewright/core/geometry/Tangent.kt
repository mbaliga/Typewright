// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.geometry

import kotlin.math.abs
import kotlin.math.floor

/** What turning a handle tangent did: the new contour, or the plain reason it could not be done. */
sealed interface TangentResult {
    data class Done(
        val contour: Contour,
    ) : TangentResult

    data class Refused(
        val reason: String,
    ) : TangentResult
}

/**
 * [handleIndex] turned about its node so it runs in line with the curve or straight side on the node's other side,
 * pointing away from it, keeping its length: the curve then goes through the node without a corner. The node becomes
 * smooth when [smoothHolds] says so afterwards. Refused, with the reason, for a quadratic outline (its controls are
 * shared between segments), a point that is not a handle of a node, a handle pulled in to nothing (it has no
 * direction to turn), a node whose other side has no direction (it sits on its neighbour), and a handle that is
 * already in line.
 */
fun Contour.withHandleTangentToNeighbour(handleIndex: Int): TangentResult {
    val checked = tangentPreconditions(handleIndex)
    if (checked != null) return checked
    val owner = handleOwner(handleIndex) ?: return TangentResult.Refused(NOT_A_HANDLE)
    val n = points.size
    val pivot = points[owner].point.toVec2()
    val outgoing = handleIndex == (owner + 1) % n
    // The other side: its own handle if it has one pulled out, else the straight run to the neighbouring node.
    val otherHandle = if (outgoing) (owner - 1 + n) % n else (owner + 1) % n
    val otherNode = if (outgoing) (owner - 3 + n) % n else (owner + 3) % n
    val away =
        listOf(points[otherHandle].point.toVec2() - pivot, points[otherNode].point.toVec2() - pivot)
            .firstOrNull { it.length() > ZERO }
            ?: return TangentResult.Refused("The other side of this node has no direction to follow: it sits on its neighbour.")
    val length = (points[handleIndex].point.toVec2() - pivot).length()
    val target = pivot - away * (length / away.length())
    val tip = points[handleIndex].point.toVec2()
    val offLine = distanceFromLine(tip, pivot, pivot - away)
    if (offLine <= SMOOTH_TOLERANCE_UNITS && (tip - pivot).dot(away) < 0.0) {
        return TangentResult.Refused("This handle is already tangent: it runs in line with the other side of its node.")
    }
    val moved = points.toMutableList()
    moved[handleIndex] = moved[handleIndex].copy(point = target.roundedToPoint())
    var result = copy(points = moved)
    if (!points[owner].smooth) {
        val flagged = result.copy(points = result.points.mapIndexed { i, p -> if (i == owner) p.copy(smooth = true) else p })
        if (flagged.smoothHolds(owner)) result = flagged
    }
    return TangentResult.Done(result)
}

/**
 * [handleIndex] turned about its node to run parallel to the line from [lineFrom] to [lineTo], keeping its length and
 * choosing the way along the line that is nearest to where it points now. When the node is smooth its other handle
 * swings to stay in line ([withHandleMoved]); when that cannot turn (the other side is a straight side) the handle
 * cannot reach the line and the edit is refused rather than leaving a node that claims to be smooth and is not.
 */
fun Contour.withHandleTangentToLine(
    handleIndex: Int,
    lineFrom: Vec2,
    lineTo: Vec2,
): TangentResult {
    val checked = tangentPreconditions(handleIndex)
    if (checked != null) return checked
    val owner = handleOwner(handleIndex) ?: return TangentResult.Refused(NOT_A_HANDLE)
    val direction = lineTo - lineFrom
    if (direction.length() <= ZERO) return TangentResult.Refused("That line has no direction: its two ends are the same point.")
    val pivot = points[owner].point.toVec2()
    val tip = points[handleIndex].point.toVec2()
    val length = (tip - pivot).length()
    val unit = direction * (1.0 / direction.length())
    val signed = if ((tip - pivot).dot(unit) >= 0.0) unit else unit * -1.0
    if (distanceFromLine(tip, pivot, pivot + unit) <= SMOOTH_TOLERANCE_UNITS) {
        return TangentResult.Refused("This handle already runs along that line.")
    }
    val target = (pivot + signed * length).roundedToPoint()
    val result = withHandleMoved(handleIndex, target.x - points[handleIndex].point.x, target.y - points[handleIndex].point.y)
    val landed = result.points[handleIndex].point.toVec2()
    // A handle slid back onto its node is trivially on every line through it, and is not what was asked for.
    if (distanceFromLine(landed, pivot, pivot + unit) > SMOOTH_TOLERANCE_UNITS || (landed - pivot).length() < length / 2.0) {
        return TangentResult.Refused(
            "This handle cannot turn onto that line: its node is smooth and the straight side on its other side cannot turn. Make the node a corner first.",
        )
    }
    return TangentResult.Done(result)
}

private fun Contour.tangentPreconditions(handleIndex: Int): TangentResult? {
    if (format != CurveFormat.CUBIC) return TangentResult.Refused("Tangents need a cubic outline; this contour is quadratic.")
    if (handleIndex !in points.indices || points[handleIndex].onCurve) return TangentResult.Refused(NOT_A_HANDLE)
    if (handleOwner(handleIndex) == null) return TangentResult.Refused(NOT_A_HANDLE)
    if (isRetracted(
            handleIndex,
        )
    ) {
        return TangentResult.Refused("This handle is pulled in to its node and has no direction. Pull it out first.")
    }
    return null
}

private fun distanceFromLine(
    p: Vec2,
    a: Vec2,
    b: Vec2,
): Double {
    val d = b - a
    val length = d.length()
    return if (length <= ZERO) (p - a).length() else abs(d.cross(p - a)) / length
}

private fun Vec2.roundedToPoint(): Point = Point(floor(x + 0.5).toInt(), floor(y + 0.5).toInt())

private const val NOT_A_HANDLE = "Select a handle (an off-curve point) to make tangent."
private const val ZERO = 1e-9
