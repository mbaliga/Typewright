// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.withHandleMoved
import com.asoc.typewright.core.geometry.withSegmentBent
import com.asoc.typewright.core.geometry.withSelectionMoved
import com.asoc.typewright.project.PointRef
import kotlin.math.floor

/** What a drag on the glyph does. */
sealed interface DragOp {
    /** Moves [points] (nodes carry their own handles; see `withSelectionMoved`). */
    data class MoveSelection(
        val points: Set<PointRef>,
    ) : DragOp

    /** Moves one handle; a smooth node swings its other handle to stay in line unless [breakSymmetry] (Alt). */
    data class MoveHandle(
        val handle: PointRef,
        val breakSymmetry: Boolean = false,
    ) : DragOp

    /** Pulls segment [key] at parameter [t]: handle lengths change, their angles stay. */
    data class BendSegment(
        val key: SegmentKey,
        val t: Double,
    ) : DragOp

    /** The label of the undo step this drag becomes. */
    val label: String
        get() =
            when (this) {
                is MoveSelection -> if (points.size == 1) "Move point" else "Move points"
                is MoveHandle -> "Move handle"
                is BendSegment -> "Bend segment"
            }
}

/**
 * Plans a drag as the outline it produces. Always computed from the glyph as it was when the drag
 * began and the drag's total movement so far, never from the previous frame, so a drag that ends
 * where it began is exactly the original and rounding cannot build up.
 */
object DragPlan {
    fun apply(
        start: Glyph,
        op: DragOp,
        dxUnits: Double,
        dyUnits: Double,
    ): List<Contour> {
        val dx = roundUnits(dxUnits)
        val dy = roundUnits(dyUnits)
        val contours = start.contours.toMutableList()
        when (op) {
            is DragOp.MoveSelection -> {
                for (contourIndex in op.points.map { it.contour }.toSet()) {
                    val indices =
                        op.points
                            .filter { it.contour == contourIndex }
                            .map { it.point }
                            .toSet()
                    contours[contourIndex] = contours[contourIndex].withSelectionMoved(indices, dx, dy)
                }
            }

            is DragOp.MoveHandle -> {
                contours[op.handle.contour] =
                    contours[op.handle.contour].withHandleMoved(op.handle.point, dx, dy, keepSmooth = !op.breakSymmetry)
            }

            is DragOp.BendSegment -> {
                contours[op.key.contour] = contours[op.key.contour].withSegmentBent(op.key.segment, op.t, dxUnits, dyUnits)
            }
        }
        return contours
    }

    private fun roundUnits(value: Double): Int = floor(value + 0.5).toInt()
}
