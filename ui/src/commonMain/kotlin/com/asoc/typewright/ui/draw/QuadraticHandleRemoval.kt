// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import com.asoc.typewright.core.geometry.Contour

/**
 * What taking control points out of a quadratic (TrueType-style) contour did, worked out from the
 * contour as it was so the note can say it plainly. A lone control between two on-curve points is the
 * whole curve of that side: take it out and the side is a straight line. Two or more controls in a row
 * imply an on-curve point between each pair; take one out of the row and the curve goes on bending
 * through the controls that are left, but the on-curve point that was implied between it and its
 * neighbour is gone, so the curve is reshaped.
 *
 * @property removed how many control points were taken out.
 * @property straightSides sides whose every control went, so they are now straight lines.
 * @property impliedGone implied on-curve points that disappeared from sides that still curve.
 */
data class QuadraticHandleRemoval(
    val removed: Int,
    val straightSides: Int,
    val impliedGone: Int,
) {
    /** The plain sentence for the notice bar, or null when nothing was removed. */
    fun sentence(): String? {
        if (removed == 0) return null
        val head = "Removed $removed control point${if (removed == 1) "" else "s"}"
        val parts = mutableListOf<String>()
        if (straightSides > 0) parts += "the side${if (straightSides == 1) " is" else "s are"} now straight"
        if (impliedGone > 0) {
            parts +=
                "the on-curve point implied between the controls ${if (impliedGone == 1) "is" else "are"} gone, so the curve is reshaped"
        }
        return if (parts.isEmpty()) "$head." else "$head; ${parts.joinToString("; ")}."
    }

    companion object {
        /** The effect of removing the off-curve points [picked] from [contour]; indices that are not off-curve points are ignored. */
        fun of(
            contour: Contour,
            picked: Set<Int>,
        ): QuadraticHandleRemoval {
            val points = contour.points
            val removedSet = picked.filter { it in points.indices && !points[it].onCurve }.toSet()
            if (removedSet.isEmpty()) return QuadraticHandleRemoval(0, 0, 0)
            val firstOn = points.indexOfFirst { it.onCurve }
            if (firstOn == -1) {
                // A ring of controls only: every pair implies a point, and taking some out reshapes the curve.
                return QuadraticHandleRemoval(removedSet.size, 0, removedSet.size)
            }
            var straight = 0
            var gone = 0
            var run = mutableListOf<Int>()
            for (step in 1..points.size) {
                val index = (firstOn + step) % points.size
                if (points[index].onCurve) {
                    if (run.isNotEmpty()) {
                        val out = run.count { it in removedSet }
                        if (out == run.size) straight++ else gone += out
                    }
                    run = mutableListOf()
                } else {
                    run += index
                }
            }
            return QuadraticHandleRemoval(removedSet.size, straight, gone)
        }
    }
}
