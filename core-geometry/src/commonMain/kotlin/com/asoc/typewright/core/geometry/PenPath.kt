// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.geometry

import kotlin.math.abs

/**
 * One anchor of a pen path: where the person clicked ([point], whole font units) and, when they
 * dragged instead of clicking, how far ([pull], also whole units). A click is a corner with no
 * handles; a drag is a smooth node whose outgoing handle sits at `point + pull` and whose incoming
 * handle is the mirror image, `point - pull`.
 */
data class PenAnchor(
    val point: Point,
    val pull: Point? = null,
) {
    /** True when the anchor was dragged out far enough to have handles at all. */
    val hasHandles: Boolean get() = pull != null && (pull.x != 0 || pull.y != 0)
}

/** Why a pen path cannot be closed into a contour yet. */
enum class PenRefusal(
    val reason: String,
) {
    TOO_FEW("A closed shape needs at least three corners, or two with a curve between them."),
    NO_AREA("That path has no area: its points lie on one line."),
    NEEDS_CUBIC("This project's outlines are quadratic, which cannot hold a curve drawn with handles. Click corners only."),
}

/** The result of closing a pen path. */
sealed interface PenClosed {
    data class Shape(
        val contour: Contour,
    ) : PenClosed

    data class Refused(
        val why: PenRefusal,
    ) : PenClosed
}

/**
 * The path the pen tool is building: a list of [PenAnchor]s that is not a contour until it is
 * closed (contours are always closed in the model, so an unfinished path is only a preview and is
 * committed only by [closed]). Immutable: every click returns a new path, which makes cancelling
 * (Esc, undo) a matter of dropping it.
 */
data class PenPath(
    val anchors: List<PenAnchor> = emptyList(),
) {
    val isEmpty: Boolean get() = anchors.isEmpty()

    /** This path with a new anchor at [point] added, dragged out by [pull] when it is not null. */
    fun plus(
        point: Point,
        pull: Point? = null,
    ): PenPath = PenPath(anchors + PenAnchor(point, pull?.takeIf { it.x != 0 || it.y != 0 }))

    /** This path without its last anchor (stepping back while drawing). */
    fun withoutLast(): PenPath = PenPath(anchors.dropLast(1))

    /**
     * The open path as the segments it draws so far, for the preview: one cubic per pair of
     * consecutive anchors, with each end's handle where the person pulled it and on the anchor itself
     * where they did not. [liveEnd], when set, is the anchor currently being dragged out.
     */
    fun previewSegments(liveEnd: PenAnchor? = null): List<CurveSegment.Cubic> {
        val all = if (liveEnd == null) anchors else anchors + liveEnd
        return all.zipWithNext { a, b -> segmentBetween(a, a.pull, b, b.pull) }
    }

    /**
     * Closes the path with a segment from the last anchor back to the first and returns it as a
     * cubic contour (polygon sides use the line encoding `.glif` stores as a plain `line`), or says why not.
     *
     * [closingPull] is the drag made on the first anchor while closing: it sets that anchor's
     * incoming handle to `start - closingPull`. Without it a first anchor that was dragged out closes
     * smoothly (its incoming handle is the mirror of its outgoing one) and a first anchor that was
     * clicked closes with a straight side. A node is flagged smooth exactly when it has handles on both
     * sides and they are in line with it ([smoothHolds]), so the flag is never a claim the outline contradicts.
     * [cubicOnly] false means the project's outlines are quadratic: handles then refuse
     * ([PenRefusal.NEEDS_CUBIC]) and only straight sides are written, as a quadratic polygon.
     */
    fun closed(
        closingPull: Point? = null,
        format: CurveFormat = CurveFormat.CUBIC,
    ): PenClosed {
        val any = anchors.any { it.hasHandles } || closingPull != null
        if (anchors.size < 2 || (anchors.size == 2 && !any)) return PenClosed.Refused(PenRefusal.TOO_FEW)
        if (format == CurveFormat.QUADRATIC) {
            if (any) return PenClosed.Refused(PenRefusal.NEEDS_CUBIC)
            val polygon = polygonContour(anchors.map { it.point }, format)
            return if (abs(polygon.signedArea()) < MIN_AREA) PenClosed.Refused(PenRefusal.NO_AREA) else PenClosed.Shape(polygon)
        }
        val n = anchors.size
        val points = mutableListOf<ContourPoint>()
        for (i in 0 until n) {
            val a = anchors[i]
            val b = anchors[(i + 1) % n]
            val incomingOfB = if (i + 1 == n) closingPull ?: b.pull else b.pull
            val start = a.point
            val end = b.point
            points += ContourPoint(start, onCurve = true)
            points += ContourPoint(a.pull.plusOrSelf(start), onCurve = false)
            points += ContourPoint(incomingOfB.minusFrom(end), onCurve = false)
        }
        val raw = Contour(points, CurveFormat.CUBIC)
        val flagged =
            raw.copy(
                points =
                    raw.points.mapIndexed { index, p ->
                        if (p.onCurve && anchors[index / 3].hasHandles) p.copy(smooth = raw.smoothHolds(index)) else p
                    },
            )
        if (abs(flagged.signedArea()) < MIN_AREA) return PenClosed.Refused(PenRefusal.NO_AREA)
        return PenClosed.Shape(flagged)
    }

    private fun Point?.plusOrSelf(base: Point): Point = if (this == null) base else Point(base.x + x, base.y + y)

    private fun Point?.minusFrom(base: Point): Point = if (this == null) base else Point(base.x - x, base.y - y)

    private fun segmentBetween(
        a: PenAnchor,
        out: Point?,
        b: PenAnchor,
        incoming: Point?,
    ): CurveSegment.Cubic {
        val start = a.point.toVec2()
        val end = b.point.toVec2()
        val c1 = out?.let { start + it.toVec2() } ?: start
        val c2 = incoming?.let { end - it.toVec2() } ?: end
        return CurveSegment.Cubic(start, c1, c2, end)
    }

    companion object {
        /** Under this many square font units a closed path counts as having no area (a sliver of one unit is not a shape). */
        const val MIN_AREA: Double = 4.0
    }
}
