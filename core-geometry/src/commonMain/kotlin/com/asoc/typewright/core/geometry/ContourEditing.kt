// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.geometry

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/*
 * Editing a contour by point index, for the Draw room: moving nodes and handles the way a designer
 * expects (a node drags its own handles along, a smooth node keeps its handles in line), adding a
 * node to a segment without changing the curve, deleting one while keeping the curve close, and
 * bending a segment by pulling on it. Everything here is pure, total on valid input, and returns a
 * new [Contour]; nothing mutates. Coordinates stay integers at rest: float maths happens inside a
 * function and is rounded once on the way out.
 *
 * Both curve formats are handled. A cubic contour reads as (on, off, off) triples, so node `3k` owns
 * the handle after it (`3k + 1`) and the handle before it (`3k - 1`). A quadratic contour's off-curve
 * point is a control shared by the segment it sits in: when it has an on-curve point on one side and
 * another off-curve point on the other it belongs to that on-curve point, but a lone control between
 * two on-curve points belongs to neither (moving either node leaves it where it is, because
 * dragging it along would bend the segment on the far side as well).
 */

/**
 * One segment of a contour together with the point indices it was read from. [startPoint] and
 * [endPoint] are `null` when that end is an implied on-curve point of a quadratic contour (the
 * midpoint of two consecutive off-curve points), which has no entry in [Contour.points].
 */
data class SegmentInfo(
    val segment: CurveSegment,
    val startPoint: Int?,
    val endPoint: Int?,
    val controlPoints: List<Int>,
)

/** The result of adding a node: the new contour and the index of the node that was added. */
data class NodeInsertion(
    val contour: Contour,
    val nodeIndex: Int,
)

/** [Contour.segments] with the point indices each segment came from, in the same order. */
fun Contour.segmentLayout(): List<SegmentInfo> =
    when (format) {
        CurveFormat.CUBIC -> cubicLayout()
        CurveFormat.QUADRATIC -> quadraticLayout()
    }

private fun Contour.cubicLayout(): List<SegmentInfo> {
    val n = points.size
    val segs = segments()
    return segs.mapIndexed { k, segment ->
        SegmentInfo(segment, startPoint = 3 * k, endPoint = (3 * k + 3) % n, controlPoints = listOf(3 * k + 1, 3 * k + 2))
    }
}

private fun Contour.quadraticLayout(): List<SegmentInfo> {
    val n = points.size
    val firstOn = points.indexOfFirst { it.onCurve }
    val out = mutableListOf<SegmentInfo>()
    var current: Vec2
    var currentIdx: Int?
    val order: List<Int>
    if (firstOn == -1) {
        current = midpoint(points[n - 1].point.toVec2(), points[0].point.toVec2())
        currentIdx = null
        order = points.indices.toList()
    } else {
        current = points[firstOn].point.toVec2()
        currentIdx = firstOn
        order = (1 until n).map { (firstOn + it) % n }
    }
    val startAnchor = current
    val startAnchorIdx = currentIdx
    var pending: Vec2? = null
    var pendingIdx = -1
    for (i in order) {
        val cp = points[i]
        if (cp.onCurve) {
            val anchor = cp.point.toVec2()
            val control = pending
            val segment = if (control == null) CurveSegment.Line(current, anchor) else CurveSegment.Quadratic(current, control, anchor)
            out += SegmentInfo(segment, currentIdx, i, if (control == null) emptyList() else listOf(pendingIdx))
            pending = null
            current = anchor
            currentIdx = i
        } else {
            val off = cp.point.toVec2()
            val control = pending
            if (control == null) {
                pending = off
                pendingIdx = i
            } else {
                val implied = midpoint(control, off)
                out += SegmentInfo(CurveSegment.Quadratic(current, control, implied), currentIdx, null, listOf(pendingIdx))
                current = implied
                currentIdx = null
                pending = off
                pendingIdx = i
            }
        }
    }
    val control = pending
    val last = if (control == null) CurveSegment.Line(current, startAnchor) else CurveSegment.Quadratic(current, control, startAnchor)
    out += SegmentInfo(last, currentIdx, startAnchorIdx, if (control == null) emptyList() else listOf(pendingIdx))
    return out
}

// ---- handle ownership ------------------------------------------------------------------------

/**
 * The on-curve point [handleIndex] is a handle of, or `null` for an on-curve point and for a
 * quadratic control that is shared between two segments (see the file comment).
 */
fun Contour.handleOwner(handleIndex: Int): Int? {
    val n = points.size
    require(handleIndex in 0 until n) { "no point $handleIndex in a contour of $n points" }
    if (points[handleIndex].onCurve) return null
    return when (format) {
        CurveFormat.CUBIC -> {
            if (handleIndex % 3 == 1) handleIndex - 1 else (handleIndex + 1) % n
        }

        CurveFormat.QUADRATIC -> {
            val before = (handleIndex - 1 + n) % n
            val after = (handleIndex + 1) % n
            when {
                points[before].onCurve && !points[after].onCurve -> before
                !points[before].onCurve && points[after].onCurve -> after
                else -> null
            }
        }
    }
}

/** The off-curve point indices that move with on-curve point [nodeIndex] (its handles), empty for a node with none. */
fun Contour.ownedHandles(nodeIndex: Int): List<Int> {
    val n = points.size
    require(nodeIndex in 0 until n && points[nodeIndex].onCurve) { "point $nodeIndex is not an on-curve point" }
    val candidates = listOf((nodeIndex + 1) % n, (nodeIndex - 1 + n) % n).distinct()
    return candidates.filter { !points[it].onCurve && handleOwner(it) == nodeIndex }
}

/** The other handle of [handleIndex]'s owner, or `null` when it has no owner or no second handle. */
fun Contour.oppositeHandle(handleIndex: Int): Int? {
    val owner = handleOwner(handleIndex) ?: return null
    return ownedHandles(owner).firstOrNull { it != handleIndex }
}

/** True when the off-curve point [handleIndex] sits exactly on its owner, so the line it ends is straight there and nothing is drawn to grab. */
fun Contour.isRetracted(handleIndex: Int): Boolean {
    val owner = handleOwner(handleIndex) ?: return false
    return points[owner].point == points[handleIndex].point
}

// ---- moving ----------------------------------------------------------------------------------

/**
 * The indices that actually move when [selection] is dragged: each selected on-curve point with the
 * handles it owns, and each selected off-curve point on its own. A handle selected together with
 * its node moves once, not twice.
 */
fun Contour.indicesMovedWith(selection: Set<Int>): Set<Int> {
    val moved = mutableSetOf<Int>()
    for (index in selection) {
        if (index !in points.indices) continue
        moved += index
        if (points[index].onCurve) moved += ownedHandles(index)
    }
    return moved
}

/**
 * This contour with [selection] translated by (`dx`, `dy`) as [indicesMovedWith] says. Straight cubic
 * sides stay straight, and every smooth node the move touches stays smooth ([smoothHolds]): a node
 * dragged away from the straight side its curve handle was in line with takes the handle round onto
 * the new line, as every drawing program does.
 */
fun Contour.withSelectionMoved(
    selection: Set<Int>,
    dx: Int,
    dy: Int,
): Contour {
    if (dx == 0 && dy == 0) return this
    val moving = indicesMovedWith(selection)
    val moved =
        copy(
            points = points.mapIndexed { i, p -> if (i in moving) p.copy(point = Point(p.point.x + dx, p.point.y + dy)) else p },
        )
    val shaped = if (format == CurveFormat.CUBIC) moved.straightSidesKeptStraight(this, moving) else moved
    return shaped.withSmoothKept(this, changedIndices(this, shaped))
}

/** The indices at which [after] has a different coordinate from [before] (same point count). */
private fun changedIndices(
    before: Contour,
    after: Contour,
): Set<Int> =
    after.points.indices
        .filter { after.points[it].point != before.points[it].point }
        .toSet()

/**
 * A cubic side that was straight before a node of it moved is straight afterwards: its two controls
 * keep the same relative places along the new chord (on its ends for the `line` form, at thirds for
 * a polyline closed by [closePolylineToContour]), so dragging or typing a corner's position never
 * turns a straight side into an S.
 */
private fun Contour.straightSidesKeptStraight(
    before: Contour,
    moved: Set<Int>,
): Contour {
    val n = points.size
    val beforeSegments = before.segments()
    val updated = points.toMutableList()
    for (k in beforeSegments.indices) {
        val old = beforeSegments[k] as CurveSegment.Cubic
        val startIdx = 3 * k
        val endIdx = (3 * k + 3) % n
        val touched = startIdx in moved || endIdx in moved || (startIdx + 1) in moved || (startIdx + 2) in moved
        if (!touched || !old.isStraight()) continue
        val oldChord = old.end - old.start
        val lengthSquared = oldChord.dot(oldChord)
        val r1 = if (lengthSquared > EPSILON) (old.control1 - old.start).dot(oldChord) / lengthSquared else 0.0
        val r2 = if (lengthSquared > EPSILON) (old.control2 - old.start).dot(oldChord) / lengthSquared else 1.0
        val start = points[startIdx].point.toVec2()
        val chord = points[endIdx].point.toVec2() - start
        updated[startIdx + 1] = updated[startIdx + 1].copy(point = (start + chord * r1).roundToPoint())
        updated[startIdx + 2] = updated[startIdx + 2].copy(point = (start + chord * r2).roundToPoint())
    }
    return copy(points = updated)
}

/**
 * Moves the single handle [handleIndex] by (`dx`, `dy`) and keeps its owner smooth ([smoothHolds]):
 * when the owner has a second handle, that one swings round the node to stay in line with the moved
 * one and keeps its length; when the other side of the node is a straight side (or has no handle to
 * swing), the moved handle slides along that side's line instead, because the line cannot turn. With
 * [keepSmooth] false (a handle dragged with Alt held) the handle goes where it is pulled and its owner
 * becomes a corner, so the flag never claims handles that are not in line.
 */
fun Contour.withHandleMoved(
    handleIndex: Int,
    dx: Int,
    dy: Int,
    keepSmooth: Boolean = true,
): Contour {
    require(handleIndex in points.indices && !points[handleIndex].onCurve) { "point $handleIndex is not an off-curve point" }
    if (dx == 0 && dy == 0) return this
    var moved = Point(points[handleIndex].point.x + dx, points[handleIndex].point.y + dy)
    val updated = points.toMutableList()
    val owner = handleOwner(handleIndex)
    val touched = mutableSetOf(handleIndex)
    if (owner != null && points[owner].smooth) {
        if (!keepSmooth) {
            updated[owner] = updated[owner].copy(smooth = false)
        } else {
            val sides = nodeSides(owner)
            val pivot = points[owner].point.toVec2()
            if (sides != null) {
                val other = if (handleIndex == (owner + 1) % points.size) sides.into else sides.out
                val swingable = other.handle
                if (swingable != null && !other.straight) {
                    val direction = moved.toVec2() - pivot
                    val length = (points[swingable].point.toVec2() - pivot).length()
                    if (direction.length() > EPSILON) {
                        val swung = pivot - direction * (length / direction.length())
                        updated[swingable] = updated[swingable].copy(point = swung.roundToPoint())
                        touched += swingable
                    }
                } else if (other.tangent.length() > EPSILON) {
                    // The other side is a line that cannot turn: this handle may only slide along it, away from the node.
                    val away = other.tangent * (-1.0 / other.tangent.length())
                    val along = (moved.toVec2() - pivot).dot(away).coerceAtLeast(0.0)
                    moved = (pivot + away * along).roundToPoint()
                }
            }
        }
    }
    updated[handleIndex] = updated[handleIndex].copy(point = moved)
    return copy(points = updated).withSmoothKept(this, touched)
}

/**
 * [nodeIndex] made smooth or a corner. Making it smooth turns the shorter of its two handles onto the
 * line of the longer one (keeping its own length), or the one handle it has onto the line of the
 * straight side or curve on its other side, so the outline changes as little as it can. A node that
 * cannot be smooth without moving something it does not own (two straight sides that meet at an angle,
 * or handles that belong to its neighbours) stays as it is, a corner: the flag is never set where
 * [smoothHolds] would be false. Making it a corner changes no coordinate.
 */
fun Contour.withSmooth(
    nodeIndex: Int,
    smooth: Boolean,
): Contour {
    require(nodeIndex in points.indices && points[nodeIndex].onCurve) { "point $nodeIndex is not an on-curve point" }
    if (points[nodeIndex].smooth == smooth) return this
    val flagged = copy(points = points.mapIndexed { i, p -> if (i == nodeIndex) p.copy(smooth = smooth) else p })
    if (!smooth || flagged.smoothHolds(nodeIndex)) return flagged
    return flagged.withNodeStraightened(nodeIndex, emptySet())?.takeIf { it.smoothHolds(nodeIndex) } ?: this
}

// ---- the smooth invariant --------------------------------------------------------------------

/**
 * What [ContourPoint.smooth] promises, stated once: the on-curve point [nodeIndex] has no corner. Its
 * two neighbouring control points are in line with it on opposite sides of it, or one neighbour is a
 * straight side and the other control lies on that straight side's line (a node where a line meets a
 * curve, as every node of a rounded rectangle does), or both neighbours are straight sides that run on
 * as one line. Said in directions: the way the curve leaves the node and the way it arrives are the same
 * line to within [SMOOTH_TOLERANCE_UNITS], the tip of the shorter being no further than that from the
 * line of the longer. The direction of a side is that of its first control point that is not on the
 * node, else of its far end, so a handle pulled in to nothing does not make a node a corner by itself.
 * A node with nothing on one side to point along (a zero-length side) holds vacuously.
 *
 * Every editing function of this file keeps it for every smooth node it touches: an edit that moves a
 * node or a handle turns the other handle of that node (or, at a line-to-curve junction, the curve's
 * handle) to follow, the way a smooth node behaves in every drawing program, and an edit that cannot
 * keep it without moving points the node does not own (or that breaks it on purpose, like deleting a
 * handle) makes the node a corner. The tolerance is the rounding of whole-unit coordinates.
 */
fun Contour.smoothHolds(nodeIndex: Int): Boolean {
    require(nodeIndex in points.indices && points[nodeIndex].onCurve) { "point $nodeIndex is not an on-curve point" }
    val sides = nodeSides(nodeIndex) ?: return true
    return sides.inLine()
}

/** The on-curve points of this contour that are flagged smooth while [smoothHolds] is false for them: what a flag claims and the outline contradicts. */
fun Contour.brokenSmoothNodes(): List<Int> = points.indices.filter { points[it].onCurve && points[it].smooth && !smoothHolds(it) }

/**
 * This contour, which is [before] after an edit that changed the points listed in [touched] (all of
 * them when null) without adding or removing any, with every smooth node that the edit disturbed kept
 * smooth: an on-curve node whose [smoothHolds] was true in [before] and is false now has the one handle
 * it can turn (the one the edit did not move, else the shorter) turned onto the line of the other side,
 * keeping its length, or becomes a corner when nothing it owns can turn. A smooth node that did not hold
 * in [before] is left alone: an edit does not mend what it did not break. This is the function for a
 * transform of a whole contour (a scale, a shear) as much as for a drag.
 */
fun Contour.withSmoothKept(
    before: Contour,
    touched: Set<Int>? = null,
): Contour {
    require(before.points.size == points.size && before.format == format) { "withSmoothKept compares the same contour before and after" }
    if (points.none { it.smooth }) return this
    val nodes = if (touched == null) points.indices.filter { points[it].onCurve } else affectedNodes(touched)
    return withSmoothRestored(nodes, before.heldAcross { it }, touched ?: emptySet())
}

/** One side of an on-curve node: the segment that arrives at it or leaves it. */
private class NodeSide(
    /** The segment and the point indices it was read from. */
    val info: SegmentInfo,
    /** The way the curve goes from the node on this side, not normalised; zero for a segment that is only a point. */
    val tangent: Vec2,
    /** The off-curve point of this side that belongs to the node and is not on it, or null when this side has none to turn. */
    val handle: Int?,
    /** True when the segment is straight, so its direction is its chord and nothing on it may be turned. */
    val straight: Boolean,
)

private class NodeSides(
    val into: NodeSide,
    val out: NodeSide,
) {
    fun inLine(): Boolean {
        val a = into.tangent
        val b = out.tangent
        val la = a.length()
        val lb = b.length()
        if (la <= EPSILON || lb <= EPSILON) return true
        return abs(a.cross(b)) <= SMOOTH_TOLERANCE_UNITS * max(la, lb) && a.dot(b) < 0.0
    }
}

private fun Contour.nodeSides(nodeIndex: Int): NodeSides? {
    val layout = segmentLayout()
    val incoming = layout.firstOrNull { it.endPoint == nodeIndex } ?: return null
    val outgoing = layout.firstOrNull { it.startPoint == nodeIndex } ?: return null
    val owned = ownedHandles(nodeIndex)
    val here = points[nodeIndex].point

    fun side(
        info: SegmentInfo,
        leaving: Boolean,
    ): NodeSide {
        val segment = info.segment
        val control =
            when (format) {
                CurveFormat.CUBIC -> info.controlPoints.let { if (leaving) it.firstOrNull() else it.lastOrNull() }
                CurveFormat.QUADRATIC -> info.controlPoints.singleOrNull()
            }
        val handle = control?.takeIf { it in owned && points[it].point != here }
        val tangent =
            when (segment) {
                is CurveSegment.Line -> {
                    if (leaving) segment.end - segment.start else segment.start - segment.end
                }

                is CurveSegment.Quadratic -> {
                    if (leaving) {
                        firstNonZero(segment.control - segment.start, segment.end - segment.start)
                    } else {
                        firstNonZero(segment.control - segment.end, segment.start - segment.end)
                    }
                }

                is CurveSegment.Cubic -> {
                    if (leaving) segment.firstTangent() ?: ZERO else segment.lastTangentBackward() ?: ZERO
                }
            }
        return NodeSide(info, tangent, handle, segment.isStraight())
    }
    return NodeSides(into = side(incoming, leaving = false), out = side(outgoing, leaving = true))
}

private fun firstNonZero(vararg candidates: Vec2): Vec2 = candidates.firstOrNull { it.length() > EPSILON } ?: ZERO

/** The on-curve nodes whose smoothness an edit of the points [moved] can have disturbed: the ends of every segment that has one of them. */
private fun Contour.affectedNodes(moved: Set<Int>): Set<Int> {
    val nodes = mutableSetOf<Int>()
    for (info in segmentLayout()) {
        val ends = listOfNotNull(info.startPoint, info.endPoint)
        if (ends.any { it in moved } || info.controlPoints.any { it in moved }) nodes += ends
    }
    nodes += moved.filter { it in points.indices && points[it].onCurve }
    return nodes
}

/** The nodes that share a segment with [nodeIndex]. */
private fun Contour.neighbourNodes(nodeIndex: Int): List<Int> =
    segmentLayout().flatMap { info ->
        when (nodeIndex) {
            info.startPoint -> listOfNotNull(info.endPoint)
            info.endPoint -> listOfNotNull(info.startPoint)
            else -> emptyList()
        }
    }

/**
 * Whether the smooth invariant held at a node of the edited contour before the edit: asks this
 * (the contour before) about the node [oldOf] names, and counts a node the edit made as having held.
 */
private fun Contour.heldAcross(oldOf: (Int) -> Int?): (Int) -> Boolean =
    { index ->
        val old = oldOf(index)
        old == null || old !in points.indices || !points[old].onCurve || smoothHolds(old)
    }

/**
 * For each smooth node of [nodes] that [held] says was in line before the edit and no longer is: turns
 * the handle that can turn onto the line ([withNodeStraightened]), moving on to that node's neighbours
 * whose side may have shared the handle, and finally makes a corner of any node that could not be mended.
 * The loop is bounded, so a pathological outline cannot spin it.
 */
private fun Contour.withSmoothRestored(
    nodes: Collection<Int>,
    held: (Int) -> Boolean,
    moved: Set<Int> = emptySet(),
): Contour {
    var current = this
    val queue = ArrayDeque(nodes.distinct())
    val seen = nodes.toMutableSet()
    var budget = RESTORE_BUDGET_PER_NODE * (nodes.size + 1)
    while (queue.isNotEmpty() && budget-- > 0) {
        val node = queue.removeFirst()
        val point = current.points.getOrNull(node) ?: continue
        if (!point.onCurve || !point.smooth || !held(node) || current.smoothHolds(node)) continue
        val repaired = current.withNodeStraightened(node, moved) ?: continue
        if (repaired == current) continue
        current = repaired
        for (next in current.neighbourNodes(node)) {
            seen += next
            queue.addLast(next)
        }
    }
    return current.withBrokenSmoothCleared(seen, held)
}

/** [nodes] that are flagged smooth, [held] before and no longer in line become corners; no coordinate changes. */
private fun Contour.withBrokenSmoothCleared(
    nodes: Collection<Int>,
    held: (Int) -> Boolean,
): Contour {
    val broken = nodes.filter { it in points.indices && points[it].onCurve && points[it].smooth && held(it) && !smoothHolds(it) }.toSet()
    if (broken.isEmpty()) return this
    return copy(points = points.mapIndexed { i, p -> if (i in broken) p.copy(smooth = false) else p })
}

/**
 * This contour with the node's one turnable handle swung about the node onto the line of the other side,
 * opposite to it and at its own length; null when the node has no handle that can turn (both sides are
 * straight or have nothing of the node's own to move). When both sides have a turnable handle the one the edit
 * did not move ([moved]) turns, else the shorter.
 */
private fun Contour.withNodeStraightened(
    nodeIndex: Int,
    moved: Set<Int>,
): Contour? {
    val sides = nodeSides(nodeIndex) ?: return this
    val pivot = points[nodeIndex].point.toVec2()
    val turnable = listOf(sides.into, sides.out).filter { it.handle != null && !it.straight }

    fun reach(side: NodeSide) = (points[checkNotNull(side.handle)].point.toVec2() - pivot).length()
    val turning =
        when (turnable.size) {
            0 -> return null
            1 -> turnable[0]
            else -> turnable.filter { it.handle !in moved }.takeIf { it.size == 1 }?.single() ?: turnable.minBy(::reach)
        }
    val fixed = if (turning === sides.into) sides.out else sides.into
    val fixedLength = fixed.tangent.length()
    if (fixedLength <= EPSILON) return this
    val handle = checkNotNull(turning.handle)
    val target = pivot - fixed.tangent * (reach(turning) / fixedLength)
    return copy(points = points.mapIndexed { i, p -> if (i == handle) p.copy(point = target.roundToPoint()) else p })
}

// ---- adding a node ---------------------------------------------------------------------------

/**
 * Adds a node to segment [segmentIndex] of [segmentLayout] at parameter [t] (`0 < t < 1`) without
 * changing the drawn curve: a cubic or quadratic curve is split there (de Casteljau), a straight
 * segment just gets a point on it. The new node is smooth when it splits a curve and a corner when
 * it sits on a straight run. Splitting a quadratic segment whose end is an implied on-curve point
 * writes that point out as a real one first, so the shape is kept; it is rounded to a whole unit,
 * which moves it by at most half a unit.
 */
fun Contour.withNodeInserted(
    segmentIndex: Int,
    t: Double,
): NodeInsertion {
    val layout = segmentLayout()
    require(segmentIndex in layout.indices) { "no segment $segmentIndex in a contour of ${layout.size} segments" }
    require(t > 0.0 && t < 1.0) { "t must be strictly inside (0, 1), was $t" }
    val info = layout[segmentIndex]
    return when (format) {
        CurveFormat.CUBIC -> insertIntoCubic(segmentIndex, info.segment as CurveSegment.Cubic, t)
        CurveFormat.QUADRATIC -> insertIntoQuadratic(info, t)
    }
}

/**
 * The out handle of a node split off a curve, [out], turned about [node] onto the line from [into] through it
 * and rounded, so the three points are in line to the unit and the new node really is smooth. The handle keeps
 * its length; it moves by at most half a unit from where the exact split put it.
 */
private fun alignedOutHandle(
    node: Point,
    into: Point,
    out: Vec2,
): Point {
    val back = node.toVec2() - into.toVec2()
    val reach = (out - node.toVec2()).length()
    if (back.length() <= EPSILON || reach <= EPSILON) return out.roundToPoint()
    return (node.toVec2() + back * (reach / back.length())).roundToPoint()
}

private fun Contour.insertIntoCubic(
    k: Int,
    segment: CurveSegment.Cubic,
    t: Double,
): NodeInsertion {
    val size = points.size
    val straight = points[3 * k + 1].point == points[3 * k].point && points[3 * k + 2].point == points[(3 * k + 3) % size].point
    val updated = points.toMutableList()
    val inserted: List<ContourPoint>
    if (straight) {
        val mid = segment.pointAt(t).roundToPoint()
        updated[3 * k + 2] = updated[3 * k + 2].copy(point = mid)
        inserted =
            listOf(
                ContourPoint(mid, onCurve = true),
                ContourPoint(mid, onCurve = false),
                ContourPoint(segment.end.roundToPoint(), onCurve = false),
            )
    } else {
        val (left, right) = splitCubicAt(segment, t)
        val node = left.end.roundToPoint()
        val into = left.control2.roundToPoint()
        updated[3 * k + 1] = updated[3 * k + 1].copy(point = left.control1.roundToPoint())
        updated[3 * k + 2] = updated[3 * k + 2].copy(point = into)
        inserted =
            listOf(
                ContourPoint(node, onCurve = true, smooth = true),
                ContourPoint(alignedOutHandle(node, into, right.control1), onCurve = false),
                ContourPoint(right.control2.roundToPoint(), onCurve = false),
            )
    }
    updated.addAll(3 * k + 3, inserted)
    // The nodes at the ends of the split segment kept their handles' directions up to rounding; keep them smooth to the unit.
    val endNew = if (3 * k + 3 < size) 3 * k + 6 else 0
    val result =
        copy(points = updated).withSmoothRestored(
            nodes = listOf(3 * k, endNew, 3 * k + 3),
            held =
                heldAcross { i ->
                    if (i < 3 * k + 3) {
                        i
                    } else if (i >= 3 * k + 6) {
                        i - 3
                    } else {
                        null
                    }
                },
            moved = setOf(3 * k + 1, 3 * k + 2),
        )
    return NodeInsertion(result, nodeIndex = 3 * k + 3)
}

private fun Contour.insertIntoQuadratic(
    info: SegmentInfo,
    t: Double,
): NodeInsertion {
    val updated = points.toMutableList()
    val segment = info.segment
    if (segment is CurveSegment.Line) {
        val start = checkNotNull(info.startPoint) { "a straight quadratic segment starts at a real point" }
        val mid = segment.pointAt(t).roundToPoint()
        updated.add(start + 1, ContourPoint(mid, onCurve = true))
        return NodeInsertion(copy(points = updated), nodeIndex = start + 1)
    }
    segment as CurveSegment.Quadratic
    val controlIndex = info.controlPoints.single()
    val u = 1.0 - t
    val left = segment.start * u + segment.control * t
    val right = segment.control * u + segment.end * t
    val mid = segment.pointAt(t)
    // Replace the control by: [start if implied], left control, new node, right control, [end if implied].
    val replacement = mutableListOf<ContourPoint>()
    if (info.startPoint == null) replacement += ContourPoint(segment.start.roundToPoint(), onCurve = true)
    val leftControl = left.roundToPoint()
    replacement += ContourPoint(leftControl, onCurve = false)
    val nodeOffset = replacement.size
    val node = mid.roundToPoint()
    replacement += ContourPoint(node, onCurve = true, smooth = true)
    replacement += ContourPoint(alignedOutHandle(node, leftControl, right), onCurve = false)
    if (info.endPoint == null) replacement += ContourPoint(segment.end.roundToPoint(), onCurve = true)
    updated.removeAt(controlIndex)
    updated.addAll(controlIndex, replacement)

    // The existing nodes at the ends of the split segment moved with the index shift; their controls are shared, so a node
    // whose smoothness the rounding of its control upsets can only lose the flag.
    fun shifted(old: Int?): Int? = old?.let { if (it < controlIndex) it else it + replacement.size - 1 }
    val result =
        copy(points = updated).withSmoothRestored(
            nodes = listOfNotNull(shifted(info.startPoint), shifted(info.endPoint), controlIndex + nodeOffset),
            held =
                heldAcross { i ->
                    if (i < controlIndex) {
                        i
                    } else if (i >= controlIndex + replacement.size) {
                        i - replacement.size + 1
                    } else {
                        null
                    }
                },
            moved = setOf(controlIndex),
        )
    return NodeInsertion(result, nodeIndex = controlIndex + nodeOffset)
}

// ---- deleting a node -------------------------------------------------------------------------

/**
 * This contour without on-curve point [nodeIndex], keeping the curve as close as one segment can:
 * two cubic segments that met at the node are replaced by the single cubic that best fits both (the
 * outer handle directions kept, only their lengths solved), and a quadratic node between two
 * curves is replaced by the single control that best fits them. Straight neighbours just join up.
 * Returns `null` when what is left could not be a contour: fewer than two nodes, or two nodes
 * joined only by straight lines, which has no ink.
 */
fun Contour.withNodeDeleted(nodeIndex: Int): Contour? {
    require(nodeIndex in points.indices && points[nodeIndex].onCurve) { "point $nodeIndex is not an on-curve point" }
    val result =
        when (format) {
            CurveFormat.CUBIC -> deleteCubicNode(nodeIndex)
            CurveFormat.QUADRATIC -> deleteQuadraticNode(nodeIndex)
        } ?: return null
    return result.takeIf { it.hasInk() }
}

private fun Contour.hasInk(): Boolean {
    val nodes = points.count { it.onCurve }
    if (nodes < 2) return false
    if (nodes > 2) return true
    return segments().any { it !is CurveSegment.Line && !it.isStraight() }
}

private fun CurveSegment.isStraight(): Boolean =
    when (this) {
        is CurveSegment.Line -> {
            true
        }

        is CurveSegment.Quadratic -> {
            distanceToLine(control, start, end) <= STRAIGHT_TOLERANCE
        }

        is CurveSegment.Cubic -> {
            distanceToLine(control1, start, end) <= STRAIGHT_TOLERANCE &&
                distanceToLine(control2, start, end) <= STRAIGHT_TOLERANCE
        }
    }

private fun Contour.deleteCubicNode(nodeIndex: Int): Contour? {
    val n = points.size
    if (n / 3 <= 2) return null
    val k = nodeIndex / 3
    val prevK = (k - 1 + n / 3) % (n / 3)
    val layout = segmentLayout()
    val incoming = layout[prevK].segment as CurveSegment.Cubic
    val outgoing = layout[k].segment as CurveSegment.Cubic
    val merged = mergeCubics(incoming, outgoing)
    val updated = points.toMutableList()
    updated[3 * prevK + 1] = updated[3 * prevK + 1].copy(point = merged.control1.roundToPoint())
    updated[3 * prevK + 2] = updated[3 * prevK + 2].copy(point = merged.control2.roundToPoint())
    // Drop the deleted node's triple; remove from the highest index so the others stay valid.
    for (index in (3 * k + 2) downTo 3 * k) updated.removeAt(index)
    // The two nodes the merged side now joins keep their smoothness (the merged side may be a straight line where two bent ones were).
    val m = n / 3
    val joined = listOf(prevK, (k + 1) % m).map { if (it < k) 3 * it else 3 * (it - 1) }
    return copy(points = updated).withSmoothRestored(
        nodes = joined,
        held = heldAcross { i -> if (i < 3 * k) i else i + 3 },
        moved = setOf(joined[0] + 1, joined[0] + 2),
    )
}

/** The one cubic from [first]'s start to [second]'s end that best follows both, keeping their outer tangent directions. */
private fun mergeCubics(
    first: CurveSegment.Cubic,
    second: CurveSegment.Cubic,
): CurveSegment.Cubic {
    val a = first.start
    val c = second.end
    if (first.isStraight() && second.isStraight()) return CurveSegment.Cubic(a, a, c, c)
    val samples = mutableListOf<Vec2>()
    for (i in 0..MERGE_SAMPLES) samples += first.pointAt(i.toDouble() / MERGE_SAMPLES)
    for (i in 1..MERGE_SAMPLES) samples += second.pointAt(i.toDouble() / MERGE_SAMPLES)
    val chord = c - a
    val tHat1 = (first.firstTangent() ?: chord).normalizedOr(chord)
    val tHat2 = (second.lastTangentBackward() ?: (a - c)).normalizedOr(a - c)
    var u = chordLengthParameterize(samples)
    var best = generateBezier(samples, u, tHat1, tHat2)
    repeat(MERGE_REFINEMENTS) {
        u = reparameterize(samples, u, best)
        best = generateBezier(samples, u, tHat1, tHat2)
    }
    return best
}

/** The direction this segment leaves its start in: its first control, else its second, else `null` for a segment that is a point. */
private fun CurveSegment.Cubic.firstTangent(): Vec2? =
    listOf(control1 - start, control2 - start, end - start).firstOrNull { it.length() > EPSILON }

/** The direction this segment arrives at its end from, pointing back into the segment. */
private fun CurveSegment.Cubic.lastTangentBackward(): Vec2? =
    listOf(control2 - end, control1 - end, start - end).firstOrNull { it.length() > EPSILON }

private fun Contour.deleteQuadraticNode(nodeIndex: Int): Contour? {
    if (points.count { it.onCurve } <= 2) return null
    val layout = segmentLayout()
    val incoming = layout.indexOfFirst { it.endPoint == nodeIndex }
    val outgoing = layout.indexOfFirst { it.startPoint == nodeIndex }
    val inInfo = layout[incoming]
    val outInfo = layout[outgoing]
    val remove = mutableSetOf(nodeIndex)
    // Handles the node owns go with it.
    remove += ownedHandles(nodeIndex)
    val updated = points.toMutableList()
    val sharedControls = (inInfo.controlPoints + outInfo.controlPoints).filter { it !in remove }
    if (sharedControls.isNotEmpty() && inInfo.segment !is CurveSegment.Line && outInfo.segment !is CurveSegment.Line) {
        val a = inInfo.segment.start
        val c = outInfo.segment.end
        val mid = nodeIndex.let { points[it].point.toVec2() }
        // Exact when the node is the midpoint of its two neighbouring controls: it was implied already.
        val controls = sharedControls.map { points[it].point.toVec2() }
        val impliedAlready = controls.size == 2 && (controls[0] + controls[1]) * 0.5 == mid
        if (!impliedAlready) {
            val fitted = fitQuadraticControl(a, c, listOf(inInfo.segment, outInfo.segment))
            val keep = sharedControls.first()
            updated[keep] = updated[keep].copy(point = fitted.roundToPoint())
            remove += sharedControls.drop(1)
        }
    }
    for (index in remove.sortedDescending()) updated.removeAt(index)
    if (updated.isEmpty()) return null
    // The nodes the joined sides now meet at keep their smoothness where they can; their controls are shared, so otherwise they lose the flag.
    val kept = points.indices.filter { it !in remove }
    val oldEnds = listOfNotNull(inInfo.startPoint, outInfo.endPoint).filter { it !in remove }
    return copy(points = updated).withSmoothRestored(
        nodes = oldEnds.map { kept.indexOf(it) },
        held = heldAcross { i -> kept.getOrNull(i) },
        moved = sharedControls.map { kept.indexOf(it) }.filter { it >= 0 }.toSet(),
    )
}

/** The single quadratic control, for fixed ends [a] and [c], that best fits samples of [segments] (least squares, chord-length parameters). */
private fun fitQuadraticControl(
    a: Vec2,
    c: Vec2,
    segments: List<CurveSegment>,
): Vec2 {
    val samples = mutableListOf<Vec2>()
    for ((s, segment) in segments.withIndex()) {
        for (i in (if (s == 0) 0 else 1)..MERGE_SAMPLES) samples += segment.pointAt(i.toDouble() / MERGE_SAMPLES)
    }
    val u = chordLengthParameterize(samples)
    var numerator = Vec2(0.0, 0.0)
    var denominator = 0.0
    for (i in samples.indices) {
        val t = u[i]
        val w = 2.0 * t * (1.0 - t)
        val rest = samples[i] - a * ((1.0 - t) * (1.0 - t)) - c * (t * t)
        numerator += rest * w
        denominator += w * w
    }
    return if (denominator > EPSILON) numerator * (1.0 / denominator) else (a + c) * 0.5
}

// ---- removing handles ------------------------------------------------------------------------

/**
 * Removes the off-curve points in [handles]. On a cubic contour that retracts each handle onto its
 * node (the segment keeps its place in the (on, off, off) pattern, the handle just has no length);
 * on a quadratic contour the control point is removed, so a segment with no control left is a
 * straight line. The caller says which of the two happened, so the message is plain. Deleting a
 * handle is an explicit break with the node it belonged to: a smooth node whose two sides no longer
 * meet in line ([smoothHolds]) becomes a corner, and nothing else is moved to keep it smooth.
 */
fun Contour.withHandlesRemoved(handles: Set<Int>): Contour {
    val offCurve = handles.filter { it in points.indices && !points[it].onCurve }.toSet()
    if (offCurve.isEmpty()) return this
    val touchedNodes = affectedNodes(offCurve)
    return when (format) {
        CurveFormat.CUBIC -> {
            copy(
                points =
                    points.mapIndexed { i, p ->
                        if (i in offCurve) {
                            // The control of triple k's start is its node; the control that ends the segment is the next node.
                            val anchor = if (i % 3 == 1) i - 1 else (i + 1) % points.size
                            p.copy(point = points[anchor].point)
                        } else {
                            p
                        }
                    },
            ).withBrokenSmoothCleared(touchedNodes, heldAcross { it })
        }

        CurveFormat.QUADRATIC -> {
            val keptIndices = points.indices.filter { it !in offCurve }
            if (keptIndices.isEmpty()) {
                this
            } else {
                copy(points = keptIndices.map { points[it] })
                    .withBrokenSmoothCleared(
                        touchedNodes.map { keptIndices.indexOf(it) }.filter { it >= 0 },
                        heldAcross { keptIndices.getOrNull(it) },
                    )
            }
        }
    }
}

// ---- bending a segment -----------------------------------------------------------------------

/**
 * Bends segment [segmentIndex] by pulling its point at parameter [t] by (`dx`, `dy`): on a cubic
 * the two handles keep their angles and change length so the curve passes through the pulled point,
 * on a quadratic the control moves, and a straight segment moves both its end nodes (with their
 * handles), since a line has nothing to bend. Computed from this contour each time, never from a
 * previous result, so a drag that re-plans every frame does not accumulate rounding.
 */
fun Contour.withSegmentBent(
    segmentIndex: Int,
    t: Double,
    dx: Double,
    dy: Double,
): Contour {
    val layout = segmentLayout()
    require(segmentIndex in layout.indices) { "no segment $segmentIndex in a contour of ${layout.size} segments" }
    val info = layout[segmentIndex]
    val delta = Vec2(dx, dy)
    val tt = t.coerceIn(BEND_MIN_T, 1.0 - BEND_MIN_T)
    return when (val segment = info.segment) {
        is CurveSegment.Line -> {
            moveSegmentEnds(info, dx.roundUnits(), dy.roundUnits())
        }

        is CurveSegment.Quadratic -> {
            val control = info.controlPoints.single()
            val pulled = segment.control + delta * (1.0 / (2.0 * tt * (1.0 - tt)))
            copy(points = points.mapIndexed { i, p -> if (i == control) p.copy(point = pulled.roundToPoint()) else p })
                .withSmoothKept(this, setOf(control))
        }

        is CurveSegment.Cubic -> {
            if (segment.isStraight()) {
                moveSegmentEnds(info, dx.roundUnits(), dy.roundUnits())
            } else {
                val bent = bendCubic(info, segment, tt, delta)
                bent.withSmoothKept(this, changedIndices(this, bent))
            }
        }
    }
}

private fun Contour.moveSegmentEnds(
    info: SegmentInfo,
    dx: Int,
    dy: Int,
): Contour {
    val ends = listOfNotNull(info.startPoint, info.endPoint)
    return withSelectionMoved(ends.toSet(), dx, dy)
}

private fun Contour.bendCubic(
    info: SegmentInfo,
    segment: CurveSegment.Cubic,
    t: Double,
    delta: Vec2,
): Contour {
    val d1 = (segment.control1 - segment.start).normalizedOr(segment.end - segment.start)
    val d2 = (segment.control2 - segment.end).normalizedOr(segment.start - segment.end)
    val u = 1.0 - t
    val w1 = 3.0 * u * u * t
    val w2 = 3.0 * u * t * t
    val base = segment.start * (u * u * u + w1) + segment.end * (w2 + t * t * t)
    val target = segment.pointAt(t) + delta
    val residual = target - base
    val colA = d1 * w1
    val colB = d2 * w2
    val determinant = colA.x * colB.y - colA.y * colB.x
    val newC1: Vec2
    val newC2: Vec2
    if (abs(determinant) > BEND_EPSILON * colA.length() * colB.length()) {
        val a = ((residual.x * colB.y - residual.y * colB.x) / determinant).coerceAtLeast(0.0)
        val b = ((colA.x * residual.y - colA.y * residual.x) / determinant).coerceAtLeast(0.0)
        newC1 = segment.start + d1 * a
        newC2 = segment.end + d2 * b
    } else {
        // The two handles are parallel, so lengths alone cannot reach the point: pull both controls the same way.
        val shift = delta * (1.0 / (3.0 * t * (1.0 - t)))
        newC1 = segment.control1 + shift
        newC2 = segment.control2 + shift
    }
    val c1 = info.controlPoints[0]
    val c2 = info.controlPoints[1]
    return copy(
        points =
            points.mapIndexed { i, p ->
                when (i) {
                    c1 -> p.copy(point = newC1.roundToPoint())
                    c2 -> p.copy(point = newC2.roundToPoint())
                    else -> p
                }
            },
    )
}

// ---- closest point on a curve ----------------------------------------------------------------

/** The point of a segment nearest a query point: the parameter [t], the [point] there and its [distance] from the query. */
data class ClosestOnSegment(
    val t: Double,
    val point: Vec2,
    val distance: Double,
)

/**
 * The point of this segment nearest [query]. Found by sampling the curve densely and then
 * narrowing the best bracket by golden-section search, which is plenty for hit-testing a pointer
 * against a glyph curve and has no case where it fails to answer.
 */
fun CurveSegment.closestTo(query: Vec2): ClosestOnSegment {
    var bestT = 0.0
    var bestD = Double.MAX_VALUE
    for (i in 0..CLOSEST_SAMPLES) {
        val t = i.toDouble() / CLOSEST_SAMPLES
        val d = (pointAt(t) - query).length()
        if (d < bestD) {
            bestD = d
            bestT = t
        }
    }
    var lo = max(0.0, bestT - 1.0 / CLOSEST_SAMPLES)
    var hi = min(1.0, bestT + 1.0 / CLOSEST_SAMPLES)
    repeat(CLOSEST_REFINEMENTS) {
        val m1 = lo + (hi - lo) * GOLDEN_LOW
        val m2 = lo + (hi - lo) * GOLDEN_HIGH
        if ((pointAt(m1) - query).length() < (pointAt(m2) - query).length()) hi = m2 else lo = m1
    }
    val t = (lo + hi) / 2.0
    val point = pointAt(t)
    return ClosestOnSegment(t, point, (point - query).length())
}

// ---- operations that rebuild a contour from segments, keeping the smooth flags ----------------

/** [insertExtremaOnCurvePoints] that keeps every existing node's smooth flag; a node added at an extreme splits one curve, so it is smooth. */
fun Contour.withExtremesAdded(): Contour {
    require(format == CurveFormat.CUBIC) { "adding extremes works on a cubic contour, this one is $format" }
    return carrySmooth(this, insertExtremaOnCurvePoints(this), newNodesSmooth = true)
}

/** [simplifyContour] that keeps the smooth flags of the nodes it leaves. */
fun Contour.tidied(): SimplifyResult {
    val result = simplifyContour(this)
    return result.copy(contour = carrySmooth(this, result.contour, newNodesSmooth = false))
}

private fun carrySmooth(
    from: Contour,
    to: Contour,
    newNodesSmooth: Boolean,
): Contour {
    val flags = from.points.filter { it.onCurve }.associate { it.point to it.smooth }
    val flagged =
        to.copy(
            points =
                to.points.map { p ->
                    if (!p.onCurve) p else p.copy(smooth = flags[p.point] ?: newNodesSmooth)
                },
        )
    // A rebuilt outline has rounded its nodes and handles afresh: keep every flagged node in line, as an edit would.
    val sourceOf = { index: Int -> from.points.indexOfFirst { it.onCurve && it.point == flagged.points[index].point }.takeIf { it >= 0 } }
    return flagged.withSmoothRestored(
        nodes = flagged.points.indices.filter { flagged.points[it].onCurve && flagged.points[it].smooth },
        held = from.heldAcross(sourceOf),
    )
}

// ---- degree elevation ------------------------------------------------------------------------

/**
 * This quadratic contour as the exactly equivalent cubic one: each line becomes a degenerate cubic
 * (controls on its own ends, the form `.glif` writes back as a plain `line` point) and each
 * quadratic `(P0, Q, P2)` becomes `(P0, P0 + 2/3 (Q - P0), P2 + 2/3 (Q - P2), P2)`. Implied on-curve
 * points become real nodes. Coordinates are rounded to whole units (half a unit at most). A cubic
 * contour is returned as it is.
 */
fun Contour.degreeElevatedToCubic(): Contour {
    if (format == CurveFormat.CUBIC) return this
    val layout = quadraticLayout()
    val out = mutableListOf<ContourPoint>()
    for (info in layout) {
        val segment = info.segment
        val (c1, c2) =
            when (segment) {
                is CurveSegment.Line -> {
                    segment.start to segment.end
                }

                is CurveSegment.Quadratic -> {
                    (segment.start + (segment.control - segment.start) * (2.0 / 3.0)) to
                        (segment.end + (segment.control - segment.end) * (2.0 / 3.0))
                }

                is CurveSegment.Cubic -> {
                    error("a quadratic contour never decodes to a cubic segment")
                }
            }
        val smooth = info.startPoint?.let { points[it].smooth } ?: false
        out += ContourPoint(segment.start.roundToPoint(), onCurve = true, smooth = smooth)
        out += ContourPoint(c1.roundToPoint(), onCurve = false)
        out += ContourPoint(c2.roundToPoint(), onCurve = false)
    }
    val elevated = Contour(out, CurveFormat.CUBIC)
    // The same outline in whole units: a node that was in line as a quadratic stays in line as a cubic.
    return elevated.withSmoothRestored(
        nodes = layout.indices.map { 3 * it }.filter { elevated.points[it].smooth },
        held = heldAcross { index -> if (index % 3 == 0) layout.getOrNull(index / 3)?.startPoint else null },
    )
}

// ---- new contours ----------------------------------------------------------------------------

/**
 * A closed contour of straight sides through [points], in [format]: a quadratic contour is just its
 * on-curve corners; a cubic one writes each side as the degenerate triple (controls on its own ends)
 * that `.glif` stores as a plain `line` point. Every corner is a corner, none smooth.
 */
fun polygonContour(
    points: List<Point>,
    format: CurveFormat,
): Contour {
    require(points.isNotEmpty()) { "a contour needs at least one point" }
    return when (format) {
        CurveFormat.QUADRATIC -> {
            Contour(points.map { ContourPoint(it, onCurve = true) }, format)
        }

        CurveFormat.CUBIC -> {
            Contour(
                points.flatMapIndexed { i, p ->
                    listOf(
                        ContourPoint(p, onCurve = true),
                        ContourPoint(p, onCurve = false),
                        ContourPoint(points[(i + 1) % points.size], onCurve = false),
                    )
                },
                format,
            )
        }
    }
}

// ---- counting as the file does ---------------------------------------------------------------

/**
 * This contour's point counts as the `.glif` file stores it and as the node-economy corpus counts it: a
 * cubic segment whose two controls sit on its own two ends is a straight line, written as a plain
 * `line` point with no off-curve points, so its two controls do not count. [count] counts every point
 * in the in-memory triple form, which reads two high for each straight side of a cubic contour; a
 * quadratic contour has no such form and counts the same either way.
 */
fun Contour.writtenCount(): ContourCount {
    val plain = count()
    if (format != CurveFormat.CUBIC) return plain
    val n = points.size
    var lines = 0
    for (i in 0 until n / 3) {
        val start = points[3 * i]
        val first = points[3 * i + 1]
        val second = points[3 * i + 2]
        val end = points[(3 * i + 3) % n]
        if (first.point == start.point && second.point == end.point) lines++
    }
    return ContourCount(plain.onCurveEquivalent, plain.offCurve - 2 * lines)
}

/** [writtenCount] summed over the glyph's contours. */
fun Glyph.writtenCount(): GlyphCount {
    var on = 0
    var off = 0
    for (contour in contours) {
        val c = contour.writtenCount()
        on += c.onCurveEquivalent
        off += c.offCurve
    }
    return GlyphCount(onCurveEquivalent = on, offCurve = off, contourCount = contours.size)
}

// ---- small helpers ---------------------------------------------------------------------------

private fun distanceToLine(
    p: Vec2,
    a: Vec2,
    b: Vec2,
): Double {
    val direction = b - a
    val length = direction.length()
    if (length <= EPSILON) return (p - a).length()
    return abs(direction.cross(p - a)) / length
}

private fun Vec2.normalizedOr(fallback: Vec2): Vec2 {
    val own = length()
    if (own > EPSILON) return this * (1.0 / own)
    val other = fallback.length()
    return if (other > EPSILON) fallback * (1.0 / other) else Vec2(1.0, 0.0)
}

private fun Double.roundUnits(): Int = kotlin.math.floor(this + 0.5).toInt()

private const val EPSILON = 1e-9

/** How far, in font units, the tip of the shorter of a smooth node's two sides may stand from the line of the longer: a whole unit's rounding. */
const val SMOOTH_TOLERANCE_UNITS: Double = 0.75
private const val RESTORE_BUDGET_PER_NODE = 8
private val ZERO = Vec2(0.0, 0.0)
private const val STRAIGHT_TOLERANCE = 0.75
private const val MERGE_SAMPLES = 12
private const val MERGE_REFINEMENTS = 4
private const val BEND_MIN_T = 0.1
private const val BEND_EPSILON = 1e-6
private const val CLOSEST_SAMPLES = 32
private const val CLOSEST_REFINEMENTS = 24
private const val GOLDEN_LOW = 0.38196601125010515
private const val GOLDEN_HIGH = 0.6180339887498949
