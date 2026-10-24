// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.core.geometry.handleOwner
import com.asoc.typewright.core.geometry.isRetracted
import com.asoc.typewright.core.geometry.segmentLayout
import com.asoc.typewright.project.PointRef

/**
 * A retracted handle, made visible: a handle with no length sits exactly on its node, so there is
 * nothing to see or to grab, and a straight side (every corner of a drawn polygon, every straight
 * side of a rounded letter) could never be turned into a curve. Its [at] is a short way along the side
 * from its node, where a small mark is drawn and where a pointer can pick it; picking it and pulling
 * drags the real handle out from the node, landing under the pointer. [offset] is that distance, from
 * the handle's own place to the mark, in font units.
 */
data class RetractedMark(
    val ref: PointRef,
    val owner: PointRef,
    val at: Vec2,
    val offset: Vec2,
)

/** Where retracted handles show and are picked. */
object RetractedHandles {
    /** How far from its node the mark stands, in screen dp: clear of the node and of the contour start triangle, inside a finger's reach. */
    const val MARK_DISTANCE_DP: Double = 26.0

    /** The most of its side the mark may take, so on a short side it stays between the two nodes. */
    private const val MAX_SHARE_OF_SIDE = 0.4

    /**
     * The marks to show for [glyph] under [selection]: those of a selected node, and of a selected handle
     * (so the mark just picked stays where it is while it is dragged). Not every retracted handle of the
     * glyph, which would put a mark by every corner of every straight side.
     */
    fun marks(
        glyph: Glyph,
        space: DrawSpace,
        selection: DrawSelection,
    ): List<RetractedMark> {
        if (selection.points.isEmpty()) return emptyList()
        val out = mutableListOf<RetractedMark>()
        for ((contourIndex, contour) in glyph.contours.withIndex()) {
            if (selection.indicesIn(contourIndex).isEmpty()) continue
            val layout = contour.segmentLayout()
            for (index in contour.points.indices) {
                if (contour.points[index].onCurve || !contour.isRetracted(index)) continue
                val owner = contour.handleOwner(index) ?: continue
                val ref = PointRef(contourIndex, index)
                val ownerRef = PointRef(contourIndex, owner)
                if (ref !in selection.points && ownerRef !in selection.points) continue
                val info = layout.firstOrNull { index in it.controlPoints } ?: continue
                val leaving = if (contour.format == CurveFormat.CUBIC) info.controlPoints.first() == index else info.startPoint == owner
                val far = if (leaving) info.segment.end else info.segment.start
                val from = contour.points[owner].point.toVec2()
                val along = far - from
                val side = along.length()
                if (side <= EPSILON) continue
                val reach = minOf(MARK_DISTANCE_DP / space.dpPerUnit, side * MAX_SHARE_OF_SIDE)
                val offset = along * (reach / side)
                out += RetractedMark(ref, ownerRef, from + offset, offset)
            }
        }
        return out
    }

    private const val EPSILON = 1e-9
}
