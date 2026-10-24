// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.core.geometry.pointAt
import com.asoc.typewright.core.geometry.segments
import com.asoc.typewright.core.geometry.writtenCount
import com.asoc.typewright.project.PointRef

/** One segment of one contour, as the index into that contour's `segmentLayout()`. */
data class SegmentKey(
    val contour: Int,
    val segment: Int,
)

/**
 * What is selected in the glyph being drawn: points (on-curve nodes and off-curve handles, by
 * [PointRef]) and at most one segment, with the parameter along it where it was picked ([segmentT]),
 * so a command that works on a spot of a segment ("add a node here") has the spot.
 * Immutable: every change returns a new selection.
 */
data class DrawSelection(
    val points: Set<PointRef> = emptySet(),
    val segment: SegmentKey? = null,
    val segmentT: Double = 0.5,
) {
    val isEmpty: Boolean get() = points.isEmpty() && segment == null

    /** The contours the selection touches, so contour-scope commands know which they act on. */
    val contours: Set<Int> get() = points.map { it.contour }.toSet() + listOfNotNull(segment?.contour)

    /** The selected point indices of contour [contour]. */
    fun indicesIn(contour: Int): Set<Int> = points.filter { it.contour == contour }.map { it.point }.toSet()

    operator fun contains(ref: PointRef): Boolean = ref in points

    /** Only [ref] selected. */
    fun only(ref: PointRef): DrawSelection = DrawSelection(setOf(ref))

    /** [ref] added to, or taken out of, what is selected (an additive tap). */
    fun toggled(ref: PointRef): DrawSelection =
        if (ref in
            points
        ) {
            copy(points = points - ref, segment = null)
        } else {
            copy(points = points + ref, segment = null)
        }

    /** Only segment [key], picked at [t]. */
    fun onlySegment(
        key: SegmentKey,
        t: Double,
    ): DrawSelection = DrawSelection(emptySet(), key, t)

    /** Every point of contour [contour] of [glyph]. */
    fun wholeContour(
        glyph: Glyph,
        contour: Int,
    ): DrawSelection =
        DrawSelection(
            glyph.contours[contour]
                .points.indices
                .map { PointRef(contour, it) }
                .toSet(),
        )

    /** Every point of [glyph]. */
    fun all(glyph: Glyph): DrawSelection =
        DrawSelection(glyph.contours.flatMapIndexed { c, contour -> contour.points.indices.map { PointRef(c, it) } }.toSet())

    /** This selection with anything that no longer exists in [glyph] dropped (after an edit that removed points). */
    fun clippedTo(glyph: Glyph): DrawSelection =
        DrawSelection(
            points.filter { it.contour in glyph.contours.indices && it.point in glyph.contours[it.contour].points.indices }.toSet(),
            segment?.takeIf { it.contour in glyph.contours.indices },
            segmentT,
        )

    /** The selected on-curve nodes of [glyph], in contour then point order. */
    fun nodes(glyph: Glyph): List<PointRef> =
        points
            .filter {
                glyph.contours
                    .getOrNull(it.contour)
                    ?.points
                    ?.getOrNull(it.point)
                    ?.onCurve == true
            }.sortedWith(compareBy({ it.contour }, { it.point }))

    /** The selected off-curve points of [glyph]. */
    fun handles(glyph: Glyph): List<PointRef> =
        points
            .filter {
                glyph.contours
                    .getOrNull(it.contour)
                    ?.points
                    ?.getOrNull(it.point)
                    ?.onCurve == false
            }.sortedWith(compareBy({ it.contour }, { it.point }))

    companion object {
        val EMPTY = DrawSelection()
    }
}

/**
 * The one line that says what a menu or the header is about, as INTERACTION_V1 §5 writes it:
 * "1 node · 427, 382 · smooth" for a node, "3 nodes" for several, "o · outer contour · 8 on · 8 off"
 * for a contour, "o · 16 on · 16 off" for the glyph. Counts are measured on the glyph, not guessed.
 */
object DrawSubject {
    fun describe(
        glyph: Glyph,
        selection: DrawSelection,
    ): String {
        val nodes = selection.nodes(glyph)
        val handles = selection.handles(glyph)
        return when {
            nodes.size == 1 && handles.isEmpty() -> {
                val p = glyph.contours[nodes[0].contour].points[nodes[0].point]
                "1 node · ${p.point.x}, ${p.point.y} · ${if (p.smooth) "smooth" else "corner"}"
            }

            nodes.size > 1 -> {
                "${nodes.size} nodes" + if (handles.isNotEmpty()) " · ${handles.size} handle${plural(handles.size)}" else ""
            }

            handles.isNotEmpty() -> {
                "${handles.size} handle${plural(handles.size)}"
            }

            selection.segment != null -> {
                "1 segment · ${contourWord(glyph, selection.segment.contour)}"
            }

            else -> {
                glyphLine(glyph)
            }
        }
    }

    /**
     * The header while a radial is open (S12): the glyph and what the radial is about, "o · node 427, 382"
     * for one node, "o · handle 430, 391" for one handle, "o · 3 points" for several, "o · segment" for a
     * segment, and the plain glyph line when nothing is selected.
     */
    fun headerLine(
        glyph: Glyph,
        selection: DrawSelection,
    ): String {
        val only = selection.points.singleOrNull()
        val point =
            only?.let {
                glyph.contours
                    .getOrNull(it.contour)
                    ?.points
                    ?.getOrNull(it.point)
            }
        return when {
            only != null && point != null -> "${glyph.name} · ${if (point.onCurve) "node" else "handle"} ${point.point.x}, ${point.point.y}"
            selection.points.size > 1 -> "${glyph.name} · ${selection.points.size} points"
            selection.segment != null -> "${glyph.name} · segment"
            else -> glyphLine(glyph)
        }
    }

    /** "o · 16 on · 16 off": the glyph's name and its measured point counts. */
    fun glyphLine(glyph: Glyph): String {
        val count = glyph.writtenCount()
        return "${glyph.name} · ${count.onCurveEquivalent} on · ${count.offCurve} off"
    }

    /** "outer contour" or "inner contour" (a counter: one that sits inside another) with its counts. */
    fun contourWord(
        glyph: Glyph,
        contourIndex: Int,
    ): String {
        val contour: Contour = glyph.contours[contourIndex]
        val kind = if (isInner(glyph, contourIndex)) "inner contour" else "outer contour"
        val count = contour.writtenCount()
        return "$kind · ${count.onCurveEquivalent} on · ${count.offCurve} off"
    }

    /**
     * True when contour [index] lies inside another contour of [glyph], judged by where it starts:
     * nesting, not winding, because a TrueType outline winds the other way round from a UFO one.
     */
    fun isInner(
        glyph: Glyph,
        index: Int,
    ): Boolean {
        val probe =
            glyph.contours[index]
                .segments()
                .firstOrNull()
                ?.start ?: return false
        return glyph.contours.withIndex().any { (i, other) -> i != index && other.encloses(probe) }
    }

    /** True when [outer] encloses [inner], judged by where [inner] starts. */
    fun encloses(
        outer: Contour,
        inner: Contour,
    ): Boolean {
        val probe = inner.segments().firstOrNull()?.start ?: return false
        return outer.encloses(probe)
    }

    private fun Contour.encloses(point: Vec2): Boolean {
        var inside = false
        for (segment in segments()) {
            val polyline = (0..SAMPLES).map { segment.pointAt(it.toDouble() / SAMPLES) }
            for (k in 0 until polyline.size - 1) {
                val a = polyline[k]
                val b = polyline[k + 1]
                if ((a.y > point.y) != (b.y > point.y) && point.x < (b.x - a.x) * (point.y - a.y) / (b.y - a.y) + a.x) inside = !inside
            }
        }
        return inside
    }

    private const val SAMPLES = 16

    private fun plural(n: Int) = if (n == 1) "" else "s"
}
