// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.Vec2

/**
 * Which of the nodes a tidy would remove get a cross drawn on them. A cross every few font units on a glyph of a
 * thousand nodes is a smear, so one is drawn per [GAP_DP] of screen along the outline, and the proposal says how many
 * of the removals carry one; zooming in shows more, because the gap is in screen units. Pure, so the canvas and the
 * words beside it use the same answer.
 */
object TidyCrosses {
    /** The least screen distance, in dp, between two crosses. */
    const val GAP_DP = 22.0

    /** The nodes among [removed] that carry a cross at the scale of [space]: the first, then each one at least [GAP_DP] from the last shown. */
    fun shown(
        removed: List<Point>,
        space: DrawSpace,
    ): List<Point> {
        val kept = mutableListOf<Point>()
        var last: Vec2? = null
        for (node in removed) {
            val at = space.toScreen(node.toVec2())
            val previous = last
            if (previous != null && (at - previous).length() < GAP_DP) continue
            kept += node
            last = at
        }
        return kept
    }

    /** "crosses on 40 of 1,755; zoom in for the rest", or null when every removal has its cross. */
    fun caption(
        removed: List<Point>,
        space: DrawSpace,
    ): String? {
        val count = shown(removed, space).size
        return if (count >= removed.size) null else "crosses on $count of ${removed.size}; zoom in for the rest"
    }
}
