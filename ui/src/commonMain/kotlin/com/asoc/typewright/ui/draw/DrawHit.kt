// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.core.geometry.closestTo
import com.asoc.typewright.core.geometry.isRetracted
import com.asoc.typewright.core.geometry.segmentLayout
import com.asoc.typewright.project.PointRef
import kotlin.math.max
import kotlin.math.min

/** How close, in screen dp, a pointer must be to a point or a curve to pick it. A finger needs far more than a mouse. */
data class HitTolerance(
    val pointDp: Double,
    val segmentDp: Double,
) {
    companion object {
        /** A finger: targets of 44 dp across (INTERACTION_V1 T3). */
        val FINGER = HitTolerance(pointDp = 22.0, segmentDp = 16.0)

        /** A stylus tip. */
        val STYLUS = HitTolerance(pointDp = 14.0, segmentDp = 10.0)

        /** A mouse: direct pointing is exact (D2). */
        val MOUSE = HitTolerance(pointDp = 9.0, segmentDp = 6.0)
    }
}

/** What is under a pointer. */
sealed interface Hit {
    /** A point of the outline: [onCurve] says node or handle. */
    data class OnPoint(
        val ref: PointRef,
        val onCurve: Boolean,
        val distanceDp: Double,
    ) : Hit

    /** A curve between nodes, at parameter [t], where it is nearest the pointer ([at], in font units). */
    data class OnSegment(
        val key: SegmentKey,
        val t: Double,
        val at: Vec2,
        val distanceDp: Double,
    ) : Hit

    /** Nothing within reach. */
    data object Nothing : Hit
}

/** Hit-testing the pointer against a glyph's outline, in screen dp through a [DrawSpace]. */
object DrawHit {
    /**
     * The nearest point of [glyph] within [tolerance] of [screen] (handles that sit on their node are
     * skipped: there is nothing to grab), else the nearest curve within the segment tolerance, else
     * [Hit.Nothing]. A point always wins over a curve it lies on.
     */
    fun hit(
        glyph: Glyph,
        space: DrawSpace,
        screen: Vec2,
        tolerance: HitTolerance,
    ): Hit {
        var best: Hit.OnPoint? = null
        for ((ci, contour) in glyph.contours.withIndex()) {
            for ((pi, point) in contour.points.withIndex()) {
                if (!point.onCurve && contour.isRetracted(pi)) continue
                val d = (space.toScreen(point.point.toVec2()) - screen).length()
                if (d <= tolerance.pointDp && (best == null || d < best.distanceDp)) best = Hit.OnPoint(PointRef(ci, pi), point.onCurve, d)
            }
        }
        best?.let { return it }
        val font = space.toFont(screen)
        var nearest: Hit.OnSegment? = null
        for ((ci, contour) in glyph.contours.withIndex()) {
            for ((si, info) in contour.segmentLayout().withIndex()) {
                val closest = info.segment.closestTo(font)
                val d = closest.distance * space.dpPerUnit
                if (d <= tolerance.segmentDp && (nearest == null || d < nearest.distanceDp)) {
                    nearest = Hit.OnSegment(SegmentKey(ci, si), closest.t, closest.point, d)
                }
            }
        }
        return nearest ?: Hit.Nothing
    }

    /** Every pickable point (a retracted handle is not) whose screen position is inside the rectangle spanned by [a] and [b]. */
    fun pointsInRect(
        glyph: Glyph,
        space: DrawSpace,
        a: Vec2,
        b: Vec2,
    ): Set<PointRef> {
        val left = min(a.x, b.x)
        val right = max(a.x, b.x)
        val top = min(a.y, b.y)
        val bottom = max(a.y, b.y)
        return pickable(glyph)
            .filter { (_, p) ->
                val s = space.toScreen(p)
                s.x in left..right && s.y in top..bottom
            }.map { it.first }
            .toSet()
    }

    /** Every pickable point inside the closed [polygon] of screen points (even-odd), for a finger's lasso. */
    fun pointsInPolygon(
        glyph: Glyph,
        space: DrawSpace,
        polygon: List<Vec2>,
    ): Set<PointRef> {
        if (polygon.size < 3) return emptySet()
        return pickable(glyph).filter { (_, p) -> polygon.encloses(space.toScreen(p)) }.map { it.first }.toSet()
    }

    private fun pickable(glyph: Glyph): List<Pair<PointRef, Vec2>> =
        glyph.contours.flatMapIndexed { ci, contour ->
            contour.points.mapIndexedNotNull { pi, point ->
                if (!point.onCurve && contour.isRetracted(pi)) null else PointRef(ci, pi) to point.point.toVec2()
            }
        }

    private fun List<Vec2>.encloses(p: Vec2): Boolean {
        var inside = false
        var j = size - 1
        for (i in indices) {
            val a = this[i]
            val b = this[j]
            if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) inside = !inside
            j = i
        }
        return inside
    }
}
