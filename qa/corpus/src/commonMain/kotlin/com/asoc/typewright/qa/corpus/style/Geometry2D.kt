// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.corpus.style

import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.CurveSegment
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.core.geometry.extrema
import com.asoc.typewright.core.geometry.pointAt
import com.asoc.typewright.core.geometry.segments
import com.asoc.typewright.core.geometry.signedArea
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * A tight axis-aligned box, in font units (as [Double] because it is built from curve extrema,
 * not from at-rest [com.asoc.typewright.core.geometry.Point]s).
 *
 * This file holds the shared geometric primitives the style-detector features
 * (`ContrastStress.kt`, `SerifBracket.kt`, `Storeys.kt`, `Terminal.kt`, `Aperture.kt`,
 * `Roundness.kt`, `Proportions.kt`) build on: [Bounds] itself, a line-against-outline
 * intersection probe ([lineCrossings]/[inkIntervals]/[isInkAt]), and a "narrowest throat" gap
 * finder ([narrowestThroat]). Everything here is a measurement primitive, not a classifier --
 * `StyleScorer.kt` is the only file in this package that reads a feature and decides what it
 * implies about a class (TYPEWRIGHT_BUILD_BRIEF.md 8.4).
 *
 * [Bounds] and [Glyph.inkBounds] are public (P6, `ui`'s Anatomy Lens): it reads its own `x`/`H`
 * glyphs' ink height the same way `Proportions.kt`'s [xHeightToCapHeightRatio] already does
 * internally, so this type has to cross the `:qa:corpus` module boundary too. [LineCrossing],
 * [Glyph.lineCrossings], [InkInterval] and [inkIntervals] are public for the same reason, a
 * second time (P6, `ui`'s Overlay tab): its stroke probe needs a stem/bar *width in font units* at
 * a caller-chosen probe line (e.g. a horizontal ray through `n`'s stem, a vertical ray through
 * `H`'s crossbar), which is exactly what these two functions already compute for every style
 * probe in this package -- [com.asoc.typewright.ui.learn.strokeProbe] calls them directly rather
 * than re-deriving the same even-odd ray-cast logic a second time.
 */
data class Bounds(
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double,
) {
    val width: Double get() = maxX - minX
    val height: Double get() = maxY - minY
    val centerX: Double get() = (minX + maxX) / 2.0
    val centerY: Double get() = (minY + maxY) / 2.0

    fun union(other: Bounds): Bounds = Bounds(min(minX, other.minX), min(minY, other.minY), max(maxX, other.maxX), max(maxY, other.maxY))
}

/**
 * This contour's exact ink bounds: the endpoints of every [Contour.segments] segment plus every
 * [com.asoc.typewright.core.geometry.Extremum] the contour has (via [extrema], `core-geometry`'s
 * own extrema detector -- see its KDoc), so a curve that bulges past its own anchors is measured
 * correctly rather than approximated by the control-point polygon. Returns `null` for a contour
 * that somehow has no segments (never happens for a well-formed [Contour], kept only so this is a
 * total function).
 */
internal fun Contour.tightBounds(): Bounds? {
    var minX = Double.POSITIVE_INFINITY
    var minY = Double.POSITIVE_INFINITY
    var maxX = Double.NEGATIVE_INFINITY
    var maxY = Double.NEGATIVE_INFINITY
    var any = false

    fun include(p: Vec2) {
        any = true
        minX = min(minX, p.x)
        minY = min(minY, p.y)
        maxX = max(maxX, p.x)
        maxY = max(maxY, p.y)
    }
    val segs = segments()
    for (seg in segs) {
        include(seg.start)
        include(seg.end)
    }
    for (ext in extrema()) include(ext.segment.pointAt(ext.t))
    return if (!any) null else Bounds(minX, minY, maxX, maxY)
}

/**
 * This glyph's exact ink bounds, the union of [tightBounds] over every contour, or `null` for an
 * empty (contourless) glyph. Public: see [Bounds]'s KDoc for why.
 */
fun Glyph.inkBounds(): Bounds? {
    var result: Bounds? = null
    for (c in contours) {
        val b = c.tightBounds() ?: continue
        result = result?.union(b) ?: b
    }
    return result
}

/**
 * This glyph's outer contour: the one with the largest absolute [Contour.signedArea] (the
 * silhouette; a counter or eye is always smaller). `null` for an empty glyph. Public (P6, `ui`'s
 * Anatomy Lens): [com.asoc.typewright.ui.learn.AnatomyLensData] needs a glyph's outer contour to
 * call [terminalStyle], so this one navigational helper has to cross the `:qa:corpus` module
 * boundary too -- everything else in this file stays internal (no other public function's
 * signature needs it).
 */
fun Glyph.outerContour(): Contour? = contours.maxByOrNull { abs(it.signedArea()) }

/** One crossing of a probe line against an outline: [tAlongLine] is the crossing's signed distance from the line's own origin, along the line's own direction -- the line's parametrization, not any segment's. Public: see [Bounds]'s KDoc, "Overlay tab" paragraph. */
data class LineCrossing(
    val tAlongLine: Double,
    val point: Vec2,
)

/**
 * Every place the infinite line through [origin] in unit direction [direction] crosses this
 * glyph's outline, sorted along the line. A [CurveSegment.Line] crosses where its own signed
 * distance to the line changes sign (solved exactly); a [CurveSegment.Quadratic] or
 * [CurveSegment.Cubic] is sampled at [curveSteps] parameter steps and each sign change is
 * refined by 20 rounds of bisection -- adequate for the low-degree, small-curvature glyph
 * outlines this package probes (a stroke or a bowl, never a hairline self-intersection), and
 * simpler than solving the cubic/quartic crossing equation exactly. A tangential touch (the
 * line grazing a curve without truly crossing) is not filtered out here; [inkIntervals] treats a
 * pair of nearly-coincident crossings as a zero-width interval, which callers already skip.
 */
fun Glyph.lineCrossings(
    origin: Vec2,
    direction: Vec2,
    curveSteps: Int = 64,
): List<LineCrossing> {
    val normal = Vec2(-direction.y, direction.x)

    fun signedDistance(p: Vec2): Double = (p - origin).dot(normal)

    fun alongLine(p: Vec2): Double = (p - origin).dot(direction)

    val crossings = mutableListOf<LineCrossing>()
    for (contour in contours) {
        for (segment in contour.segments()) {
            if (segment is CurveSegment.Line) {
                val d0 = signedDistance(segment.start)
                val d1 = signedDistance(segment.end)
                // The standard "vertex on the scanline" rule (treat exactly 0 as the non-negative
                // side, consistently): a vertex that lies exactly on the probe is then a crossing
                // for exactly one of its two adjacent segments when the outline truly crosses
                // there, and for both or neither when it merely touches -- either way the total
                // crossing count comes out right without a special case for the touch itself.
                if ((d0 < 0.0) != (d1 < 0.0)) {
                    val t = d0 / (d0 - d1)
                    val p = segment.pointAt(t)
                    crossings += LineCrossing(alongLine(p), p)
                }
            } else {
                crossings += sampledCurveCrossings(segment, curveSteps, ::signedDistance, ::alongLine)
            }
        }
    }
    return crossings.sortedBy { it.tAlongLine }
}

private fun sampledCurveCrossings(
    segment: CurveSegment,
    steps: Int,
    signedDistance: (Vec2) -> Double,
    alongLine: (Vec2) -> Double,
): List<LineCrossing> {
    val result = mutableListOf<LineCrossing>()
    var prevT = 0.0
    var prevD = signedDistance(segment.pointAt(0.0))
    for (i in 1..steps) {
        val t = i.toDouble() / steps
        val d = signedDistance(segment.pointAt(t))
        if ((prevD < 0.0) != (d < 0.0)) {
            var lo = prevT
            var hi = t
            var loD = prevD
            repeat(20) {
                val mid = (lo + hi) / 2.0
                val midD = signedDistance(segment.pointAt(mid))
                if ((loD < 0.0) == (midD < 0.0)) {
                    lo = mid
                    loD = midD
                } else {
                    hi = mid
                }
            }
            val mid = (lo + hi) / 2.0
            val p = segment.pointAt(mid)
            result += LineCrossing(alongLine(p), p)
        }
        prevT = t
        prevD = d
    }
    return result
}

/** A closed interval of "ink" along a probe line, between two consecutive [LineCrossing]s. Public: see [Bounds]'s KDoc, "Overlay tab" paragraph. */
data class InkInterval(
    val start: Double,
    val end: Double,
) {
    val length: Double get() = end - start
}

/**
 * Turns [crossings] (already sorted along the probe line) into ink intervals by the even-odd
 * rule: the probe starts outside the glyph, so the 1st-2nd crossing is ink, 2nd-3rd is a gap,
 * 3rd-4th is ink again, and so on. This assumes the probe line fully exits the glyph on both
 * ends (true for any probe this package casts, which always spans well past the glyph's own
 * bounds) and that the outline has no self-intersections and no exact tangential touches; a
 * stray near-duplicate crossing (within [epsilon]) is merged away first so it cannot desync the
 * parity, which is this function's one piece of real robustness -- the rest is the textbook rule.
 */
fun inkIntervals(
    crossings: List<LineCrossing>,
    epsilon: Double = 1e-6,
): List<InkInterval> {
    val merged = mutableListOf<Double>()
    for (c in crossings) {
        if (merged.isEmpty() || c.tAlongLine - merged.last() > epsilon) merged += c.tAlongLine
    }
    val intervals = mutableListOf<InkInterval>()
    var i = 0
    while (i + 1 < merged.size) {
        intervals += InkInterval(merged[i], merged[i + 1])
        i += 2
    }
    return intervals
}

/** [contour]'s points, resampled to [perSegment] evenly-`t`-spaced points per [Contour.segments] segment, in contour order. Used by [narrowestThroat] and [com.asoc.typewright.qa.corpus.style.superellipseExponent] as a cheap stand-in for arc-length sampling -- uniform in curve parameter, not in arc length, which is an acceptable approximation for this package's coarse ratios. */
internal fun sampleContourPoints(
    contour: Contour,
    perSegment: Int = 8,
): List<Vec2> {
    val pts = mutableListOf<Vec2>()
    for (seg in contour.segments()) {
        for (i in 0 until perSegment) pts += seg.pointAt(i.toDouble() / perSegment)
    }
    return pts
}

/** The result of [narrowestThroat]: how close the two near points come, and where they are. */
internal data class ThroatResult(
    val width: Double,
    val pointA: Vec2,
    val pointB: Vec2,
)

/**
 * Whether [point] sits inside [this] glyph's ink, by the even-odd rule: cast a ray from [point]
 * in an arbitrary fixed direction (`+x`) and reuse [lineCrossings]/[inkIntervals] on the *whole*
 * infinite line through it -- [inkIntervals]' even-odd pairing is a property of the whole line's
 * crossings, so it pairs up correctly regardless of where [point] itself sits along it, and
 * [point] is inside exactly when it falls inside one of the resulting intervals. This is the
 * standard ray-casting point-in-polygon test, generalised to curved segments via [lineCrossings]'
 * own sampling.
 */
internal fun Glyph.isInkAt(point: Vec2): Boolean {
    val intervals = inkIntervals(lineCrossings(point, Vec2(1.0, 0.0)))
    return intervals.any { it.start <= 0.0 && 0.0 <= it.end }
}

/**
 * The narrowest place [contour] comes close to itself across an open counter (an aperture) or,
 * for a fully closed ring, across the counter it encloses: the minimum Euclidean distance between
 * two of [sampleContourPoints]' points that are at least [minCyclicSeparationFraction] of the
 * point list apart, measured the short way around the cycle, *and* whose connecting straight
 * segment ([connectingSegmentCrossesInk]) stays outside the glyph's own ink (per [insideTest],
 * typically [Glyph.isInkAt]) when [insideTest] is given.
 *
 * Both filters exist for the same reason: a pair of points close together *in parametrization*
 * is almost always on the same short stroke, measuring the stroke's own thickness at a thin
 * point rather than a counter's throat, so a purely topological cutoff alone is not enough --
 * on a simple, roughly uniform ring (an `o`, or `c`/`e`/`s`'s outer boundary), a point on one
 * side and its near-opposite on the other side of the *stroke itself* can easily be exactly as
 * topologically far apart as the true aperture's two lips are (this was found and fixed while
 * building this package's own synthetic test fixtures: a first version without [insideTest]
 * measured a square-bracket test shape's wall thickness instead of its opening). Requiring the
 * connecting segment to stay in the background rules that out directly: a true aperture's two
 * lips face across open counter space, so the segment between them is background all the way;
 * two points across a stroke's own width have a segment that dips back into the ink.
 * [insideTest] is optional (and the topological
 * filter alone is kept as the fallback) so [com.asoc.typewright.qa.corpus.style.superellipseExponent]'s
 * unrelated use of [sampleContourPoints] is untouched and a caller without a whole [Glyph] handy
 * can still call this. This is `qa/corpus`'s own heuristic either way (law 5) for aperture
 * openness ([apertureOpenness]) and terminal style ([terminalStyle]) alike; see both functions'
 * KDoc for what still is not guaranteed (an extremely tight counter elsewhere in the glyph could
 * still out-compete a genuinely wide-open aperture).
 */
internal fun narrowestThroat(
    contour: Contour,
    perSegment: Int = 8,
    minCyclicSeparationFraction: Double = 0.22,
    insideTest: ((Vec2) -> Boolean)? = null,
): ThroatResult? {
    val pts = sampleContourPoints(contour, perSegment)
    val n = pts.size
    if (n < 8) return null
    val minSep = (n * minCyclicSeparationFraction).toInt().coerceAtLeast(2)
    var best = Double.POSITIVE_INFINITY
    var bestI = -1
    var bestJ = -1
    for (i in 0 until n) {
        for (j in i + 1 until n) {
            val forward = j - i
            val cyclic = min(forward, n - forward)
            if (cyclic < minSep) continue
            val d = (pts[i] - pts[j]).length()
            if (d >= best) continue
            if (insideTest != null && connectingSegmentCrossesInk(pts[i], pts[j], insideTest)) continue
            best = d
            bestI = i
            bestJ = j
        }
    }
    return if (bestI < 0) null else ThroatResult(best, pts[bestI], pts[bestJ])
}

/**
 * Whether the straight segment from [a] to [b] passes through ink anywhere along
 * [MIDPOINT_CHECK_FRACTIONS], per [insideTest]. Checking only the exact midpoint once let a
 * candidate through whose connecting path grazed a wall at a point other than its midpoint (found
 * while building this package's own test fixtures: a pair landing exactly on a wall's own edge, a
 * floating-point coin flip at the one point checked) -- checking several points along the segment
 * is cheap and closes that gap without needing exact boundary arithmetic.
 */
private fun connectingSegmentCrossesInk(
    a: Vec2,
    b: Vec2,
    insideTest: (Vec2) -> Boolean,
): Boolean =
    MIDPOINT_CHECK_FRACTIONS.any { t ->
        insideTest(Vec2(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t))
    }

private val MIDPOINT_CHECK_FRACTIONS = listOf(0.2, 0.35, 0.5, 0.65, 0.8)

/** [narrowestThroat] on [glyph]'s [contour], using [Glyph.isInkAt] as the midpoint-outside-ink filter -- the version [apertureOpenness] and [terminalStyle] actually call. */
internal fun narrowestThroat(
    glyph: Glyph,
    contour: Contour,
    perSegment: Int = 8,
    minCyclicSeparationFraction: Double = 0.22,
): ThroatResult? = narrowestThroat(contour, perSegment, minCyclicSeparationFraction, insideTest = glyph::isInkAt)
