// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import com.asoc.typewright.core.geometry.Axis
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.core.geometry.extremaT
import com.asoc.typewright.core.geometry.indicesMovedWith
import com.asoc.typewright.core.geometry.pointAt
import com.asoc.typewright.core.geometry.segmentLayout
import com.asoc.typewright.project.PointRef
import kotlin.math.abs

/** What a dragged point can snap to. */
enum class SnapKind { METRIC, GLYPH_EDGE, NODE, EXTREME }

/**
 * One thing the dragged point snapped to, to be shown in the snap colour with a crosshair and a
 * [label] ("x-height 500"). [axis] says which coordinate it fixed (X for a vertical alignment, Y for
 * a horizontal one); [at] is where to draw the crosshair, in font units.
 */
data class SnapTarget(
    val kind: SnapKind,
    val label: String,
    val axis: Axis,
    val at: Vec2,
)

/** The correction a snap makes to a dragged position ([dx], [dy] in font units, 0 where nothing snapped) and the targets responsible. */
data class SnapResult(
    val dx: Double,
    val dy: Double,
    val targets: List<SnapTarget>,
) {
    val snapped: Boolean get() = targets.isNotEmpty()

    companion object {
        val NONE = SnapResult(0.0, 0.0, emptyList())
    }
}

/**
 * Snapping for one drag (INTERACTION_V1 §4.2). Candidates are gathered once from the glyph as it
 * stood when the drag began, leaving out everything that is moving: the metric lines, the glyph's
 * origin and advance, every other node's x and y, and the points where a curve not being moved is
 * extreme in x or y even though no node sits there. [snap] then pulls a dragged position onto the
 * nearest candidate within [bandUnits] on each axis separately, and lets go the moment the position
 * is further than that, with no hidden "hold still to escape".
 */
class Snapper(
    glyph: Glyph,
    metrics: DrawMetrics,
    advance: Int,
    moving: Set<PointRef>,
    private val bandUnits: Double,
) {
    private class Candidate(
        val value: Double,
        val kind: SnapKind,
        val label: String,
        val at: Vec2?,
    )

    private val xs = mutableListOf<Candidate>()
    private val ys = mutableListOf<Candidate>()

    init {
        for (line in metrics.lines) ys += Candidate(line.y.toDouble(), SnapKind.METRIC, line.text, null)
        xs += Candidate(0.0, SnapKind.GLYPH_EDGE, "origin 0", null)
        xs += Candidate(advance.toDouble(), SnapKind.GLYPH_EDGE, "advance $advance", null)
        for ((ci, contour) in glyph.contours.withIndex()) {
            val movedHere = contour.indicesMovedWith(moving.filter { it.contour == ci }.map { it.point }.toSet())
            for ((pi, point) in contour.points.withIndex()) {
                if (!point.onCurve || pi in movedHere) continue
                val at = point.point.toVec2()
                xs += Candidate(at.x, SnapKind.NODE, "node x ${point.point.x}", at)
                ys += Candidate(at.y, SnapKind.NODE, "node y ${point.point.y}", at)
            }
            for (info in contour.segmentLayout()) {
                val touched = listOfNotNull(info.startPoint, info.endPoint) + info.controlPoints
                if (touched.any { it in movedHere }) continue
                for (t in info.segment.extremaT(Axis.X)) {
                    val at = info.segment.pointAt(t)
                    xs += Candidate(at.x, SnapKind.EXTREME, "extreme x ${at.x.toInt()}", at)
                }
                for (t in info.segment.extremaT(Axis.Y)) {
                    val at = info.segment.pointAt(t)
                    ys += Candidate(at.y, SnapKind.EXTREME, "extreme y ${at.y.toInt()}", at)
                }
            }
        }
    }

    /** The correction that lands [position] (font units, where the dragged point would be) on the nearest candidates, per axis. */
    fun snap(position: Vec2): SnapResult {
        val x = nearest(xs, position.x)
        val y = nearest(ys, position.y)
        if (x == null && y == null) return SnapResult.NONE
        val snappedX = x?.value ?: position.x
        val snappedY = y?.value ?: position.y
        val targets = mutableListOf<SnapTarget>()
        if (x != null) targets += SnapTarget(x.kind, x.label, Axis.X, x.at ?: Vec2(x.value, snappedY))
        if (y != null) targets += SnapTarget(y.kind, y.label, Axis.Y, y.at ?: Vec2(snappedX, y.value))
        return SnapResult(snappedX - position.x, snappedY - position.y, targets)
    }

    private fun nearest(
        candidates: List<Candidate>,
        value: Double,
    ): Candidate? = candidates.filter { abs(it.value - value) <= bandUnits }.minByOrNull { abs(it.value - value) }
}

/**
 * [dx], [dy] held to the horizontal, the vertical or the 45 degree diagonal, whichever the move is
 * nearest (Shift while dragging, or the puck held under a thumb; INTERACTION_V1 D5, §4.3).
 */
fun constrainToAxes(
    dx: Double,
    dy: Double,
): Pair<Double, Double> {
    val ax = abs(dx)
    val ay = abs(dy)
    return when {
        ax >= ay * TAN_67_5 -> {
            dx to 0.0
        }

        ay >= ax * TAN_67_5 -> {
            0.0 to dy
        }

        else -> {
            val d = (ax + ay) / 2.0
            (if (dx < 0) -d else d) to (if (dy < 0) -d else d)
        }
    }
}

// tan(67.5 degrees): beyond it a move is nearer the axis than the diagonal.
private const val TAN_67_5 = 2.414213562373095
