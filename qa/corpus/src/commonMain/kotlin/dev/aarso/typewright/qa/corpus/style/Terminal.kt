package dev.aarso.typewright.qa.corpus.style

import dev.aarso.typewright.core.geometry.Contour
import dev.aarso.typewright.core.geometry.CurveSegment
import dev.aarso.typewright.core.geometry.Glyph
import dev.aarso.typewright.core.geometry.Vec2
import dev.aarso.typewright.core.geometry.pointAt
import dev.aarso.typewright.core.geometry.segments
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min

/**
 * Terminal style at an open counter's throat (brief 8.4: "on an open terminal like `c` or the arm
 * of `r`/`f`"), found on `c`'s outer contour. [narrowestThroat] (`Geometry2D.kt`) already locates
 * the two points where the two "lips" of the aperture come closest together; this function looks
 * at which of the contour's actual segments those points genuinely sit on ([distanceToSegment],
 * a sampled nearest-point search rather than just the two endpoints -- see its own KDoc for why
 * endpoints alone tie, and misattribute, at a shared anchor):
 *
 * - Neither near segment is close to a straight line (both are genuinely curved, i.e. the ink
 *   simply narrows by curvature with no distinct cut) -- [TerminalStyle.ROUND].
 * - One near segment reads as a line (straight, or a curve so gently bowed [asLineLike]
 *   calls it one): its angle from horizontal, folded into `[0, 90]`, decides the rest --
 *   within [AXIS_ALIGNED_TOLERANCE_DEGREES] of horizontal or vertical is [TerminalStyle.FLAT] (an
 *   axis-aligned cut, as in a neo-grotesque's horizontal terminal or a geometric sans's cut
 *   perpendicular to the stroke); further from either axis is [TerminalStyle.ANGLED] (a
 *   calligraphic, diagonal cut).
 *
 * This is `qa/corpus`'s own heuristic (law 5): real terminals are not always this clean, and a
 * terminal built from several short segments can be misread as whichever one
 * [narrowestThroat]'s near points happen to land on.
 */
private const val AXIS_ALIGNED_TOLERANCE_DEGREES = 22.5
private const val STRAIGHTNESS_TOLERANCE_FRACTION = 0.06

internal fun terminalStyle(
    glyph: Glyph,
    contour: Contour,
): TerminalStyle {
    val throat = narrowestThroat(glyph, contour) ?: return TerminalStyle.UNKNOWN
    val segs = contour.segments()
    if (segs.isEmpty()) return TerminalStyle.UNKNOWN

    fun nearestSegment(p: Vec2): CurveSegment = segs.minBy { seg -> distanceToSegment(seg, p) }

    val segA = nearestSegment(throat.pointA)
    val segB = nearestSegment(throat.pointB)
    val lineLikeA = asLineLike(segA)
    val lineLikeB = asLineLike(segB)
    val lineLike = lineLikeA ?: lineLikeB ?: return TerminalStyle.ROUND

    val dx = abs(lineLike.end.x - lineLike.start.x)
    val dy = abs(lineLike.end.y - lineLike.start.y)
    if (dx == 0.0 && dy == 0.0) return TerminalStyle.ROUND
    val angleFromHorizontal = atan2(dy, dx) * 180.0 / kotlin.math.PI
    return if (angleFromHorizontal <= AXIS_ALIGNED_TOLERANCE_DEGREES ||
        angleFromHorizontal >= 90.0 - AXIS_ALIGNED_TOLERANCE_DEGREES
    ) {
        TerminalStyle.FLAT
    } else {
        TerminalStyle.ANGLED
    }
}

/** [segment] itself if it is a [CurveSegment.Line], or, for a curve whose control point(s) sit within [STRAIGHTNESS_TOLERANCE_FRACTION] of its own chord length, a synthetic line standing in for it -- some outlines draw an almost-straight cut as a very gently bowed curve. `null` if the curve is genuinely curved. */
private fun asLineLike(segment: CurveSegment): CurveSegment.Line? =
    when (segment) {
        is CurveSegment.Line -> {
            segment
        }

        is CurveSegment.Quadratic -> {
            val chord = (segment.end - segment.start).length()
            val bow = perpendicularDistance(segment.control, segment.start, segment.end)
            if (chord > 0.0 && bow / chord <= STRAIGHTNESS_TOLERANCE_FRACTION) {
                CurveSegment.Line(segment.start, segment.end)
            } else {
                null
            }
        }

        is CurveSegment.Cubic -> {
            val chord = (segment.end - segment.start).length()
            val bow1 = perpendicularDistance(segment.control1, segment.start, segment.end)
            val bow2 = perpendicularDistance(segment.control2, segment.start, segment.end)
            if (chord > 0.0 && bow1 / chord <= STRAIGHTNESS_TOLERANCE_FRACTION && bow2 / chord <= STRAIGHTNESS_TOLERANCE_FRACTION) {
                CurveSegment.Line(segment.start, segment.end)
            } else {
                null
            }
        }
    }

private fun perpendicularDistance(
    p: Vec2,
    lineStart: Vec2,
    lineEnd: Vec2,
): Double {
    val d = lineEnd - lineStart
    val len = d.length()
    if (len == 0.0) return (p - lineStart).length()
    val cross = d.cross(p - lineStart)
    return abs(cross) / len
}

/**
 * [p]'s distance to [segment]'s actual geometry -- sampled at [DISTANCE_SAMPLE_STEPS] points
 * along its curve (or its two endpoints, for a [CurveSegment.Line]) and taking the closest --
 * rather than only its two endpoints. Two adjacent segments sharing an anchor (every pair does)
 * are exactly as close to that shared point by an endpoints-only distance, so a point that
 * genuinely sits mid-curve on one of them could tie with, and lose to, its straight neighbour on
 * nothing more than list order; sampling the interior resolves the tie correctly (found and fixed
 * while building this package's own curved-terminal test fixture, which a first, endpoints-only
 * version of this function mis-attributed to the neighbouring straight wall).
 */
private fun distanceToSegment(
    segment: CurveSegment,
    p: Vec2,
): Double {
    if (segment is CurveSegment.Line) {
        return min((segment.start - p).length(), (segment.end - p).length())
    }
    var best = Double.POSITIVE_INFINITY
    for (i in 0..DISTANCE_SAMPLE_STEPS) {
        val d = (segment.pointAt(i.toDouble() / DISTANCE_SAMPLE_STEPS) - p).length()
        if (d < best) best = d
    }
    return best
}

private const val DISTANCE_SAMPLE_STEPS = 16
