// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import com.asoc.typewright.core.geometry.Anchor
import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.ContourPoint
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Point

/**
 * Law 1's "shape" for a glyph: its contours and anchors, never its advance width, unicodes,
 * guidelines or name (docs/PROJECT_MODEL.md §7.1). A locked glyph may still have those other
 * things changed; only a shape change is refused.
 */
internal object GlyphShape {
    /**
     * The single integer horizontal offset that turns [before]'s shape into [after]'s (every
     * contour point and anchor shifted by `(dx, 0)`, nothing else different), or null when no
     * such offset exists. `0` when the shapes are identical, including when both have no
     * contours and no anchors.
     */
    fun translationDx(
        before: Glyph,
        after: Glyph,
    ): Int? {
        if (before.contours.size != after.contours.size) return null
        if (before.anchors.size != after.anchors.size) return null
        var dx: Int? = null

        fun consider(
            beforeX: Int,
            afterX: Int,
        ): Boolean {
            val delta = afterX - beforeX
            val known = dx
            return if (known == null) {
                dx = delta
                true
            } else {
                known == delta
            }
        }

        for ((beforeContour, afterContour) in before.contours.zip(after.contours)) {
            if (!contoursMatch(beforeContour, afterContour, ::consider)) return null
        }
        for ((beforeAnchor, afterAnchor) in before.anchors.zip(after.anchors)) {
            if (beforeAnchor.name != afterAnchor.name) return null
            if (beforeAnchor.point.y != afterAnchor.point.y) return null
            if (!consider(beforeAnchor.point.x, afterAnchor.point.x)) return null
        }
        return dx ?: 0
    }

    private fun contoursMatch(
        before: Contour,
        after: Contour,
        consider: (Int, Int) -> Boolean,
    ): Boolean {
        if (before.format != after.format) return false
        if (before.points.size != after.points.size) return false
        for ((beforePoint, afterPoint) in before.points.zip(after.points)) {
            if (beforePoint.onCurve != afterPoint.onCurve) return false
            if (beforePoint.point.y != afterPoint.point.y) return false
            if (!consider(beforePoint.point.x, afterPoint.point.x)) return false
        }
        return true
    }

    /** [glyph] with every contour point and anchor shifted by `(dx, 0)`; guidelines, advance width, unicodes and name are untouched. */
    fun shiftX(
        glyph: Glyph,
        dx: Int,
    ): Glyph {
        if (dx == 0) return glyph
        return glyph.copy(
            contours = glyph.contours.map { contour -> contour.copy(points = contour.points.map { it.shiftX(dx) }) },
            anchors = glyph.anchors.map { it.shiftX(dx) },
        )
    }

    private fun ContourPoint.shiftX(dx: Int): ContourPoint = copy(point = point.shiftX(dx))

    private fun Anchor.shiftX(dx: Int): Anchor = copy(point = point.shiftX(dx))

    private fun Point.shiftX(dx: Int): Point = Point(x + dx, y)
}
