// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.corpus.style

import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.ContourPoint
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Point
import kotlin.math.roundToInt

/**
 * A straight-sided polygon contour (all on-curve, [CurveFormat.QUADRATIC] -- decodes to plain
 * [com.asoc.typewright.core.geometry.CurveSegment.Line]s between consecutive points).
 *
 * This file holds hand-built glyphs for the style-detector unit tests: no font file, just
 * `core-geometry` shapes with a known, geometrically-obvious answer (P1b's own instruction:
 * "unit-tested on a synthetic/constructed glyph with a known expected value"). Every builder
 * documents which real feature it is standing in for.
 */
internal fun polygon(vararg pts: Pair<Int, Int>): Contour =
    Contour(pts.map { (x, y) -> ContourPoint(Point(x, y), onCurve = true) }, CurveFormat.QUADRATIC)

private const val KAPPA = 0.5522847498

/**
 * A 4-arc cubic Bezier approximation of an ellipse centred at ([cx], [cy]) with radii [rx]/[ry]
 * (a true circle when `rx == ry`), the textbook "kappa" construction (error ~0.02% of the radius
 * for a circle -- more than accurate enough for this package's coarse probes). Anchors sit at the
 * ellipse's own axis extrema (E/N/W/S), which is also exactly where [tightBounds] needs them to
 * read the ellipse's true width/height back out via `core-geometry`'s own extrema detector.
 */
internal fun ellipseContour(
    cx: Double,
    cy: Double,
    rx: Double,
    ry: Double,
): Contour {
    fun p(
        x: Double,
        y: Double,
    ) = ContourPoint(Point(x.roundToInt(), y.roundToInt()), onCurve = true)

    fun c(
        x: Double,
        y: Double,
    ) = ContourPoint(Point(x.roundToInt(), y.roundToInt()), onCurve = false)
    val e = p(cx + rx, cy)
    val n = p(cx, cy + ry)
    val w = p(cx - rx, cy)
    val s = p(cx, cy - ry)
    val points =
        listOf(
            e,
            c(cx + rx, cy + KAPPA * ry),
            c(cx + KAPPA * rx, cy + ry),
            n,
            c(cx - KAPPA * rx, cy + ry),
            c(cx - rx, cy + KAPPA * ry),
            w,
            c(cx - rx, cy - KAPPA * ry),
            c(cx - KAPPA * rx, cy - ry),
            s,
            c(cx + KAPPA * rx, cy - ry),
            c(cx + rx, cy - KAPPA * ry),
        )
    return Contour(points, CurveFormat.CUBIC)
}

/** A perfectly round ring `o`: outer circle radius [outerRadius], inner circle radius [innerRadius], both centred at the origin. Uniform stroke width at every angle. */
internal fun circleRingGlyph(
    outerRadius: Double = 250.0,
    innerRadius: Double = 170.0,
    advanceWidth: Int = 550,
): Glyph =
    Glyph(
        "o",
        advanceWidth,
        listOf(
            ellipseContour(0.0, 0.0, outerRadius, outerRadius),
            ellipseContour(0.0, 0.0, innerRadius, innerRadius),
        ),
    )

/**
 * A ring whose stroke is thicker at top/bottom than at the sides (vertical stress, angle ~0):
 * outer circle radius [outerRadius], inner *ellipse* narrower vertically ([innerRy] < [innerRx]),
 * so the vertical gap (top/bottom thickness) is larger than the horizontal gap (side thickness).
 */
internal fun verticalStressRingGlyph(
    outerRadius: Double = 250.0,
    innerRx: Double = 180.0,
    innerRy: Double = 140.0,
    advanceWidth: Int = 550,
): Glyph =
    Glyph(
        "o",
        advanceWidth,
        listOf(ellipseContour(0.0, 0.0, outerRadius, outerRadius), ellipseContour(0.0, 0.0, innerRx, innerRy)),
    )

/** As [verticalStressRingGlyph], but rotated a quarter turn: the sides are thicker than top/bottom (stress ~horizontal). */
internal fun horizontalStressRingGlyph(
    outerRadius: Double = 250.0,
    innerRx: Double = 140.0,
    innerRy: Double = 180.0,
    advanceWidth: Int = 550,
): Glyph =
    Glyph(
        "o",
        advanceWidth,
        listOf(ellipseContour(0.0, 0.0, outerRadius, outerRadius), ellipseContour(0.0, 0.0, innerRx, innerRy)),
    )

/** A rounded-rectangle-ish `o`, approximated as a straight-sided octagon (chamfered corners) -- superelliptical, `n` well above 2. Standing in for CLAUDE.md's Hyle Deco `o` ("rounded rectangle"), without needing a real font. */
internal fun squarishRingGlyph(
    halfWidth: Int = 230,
    halfHeight: Int = 250,
    chamfer: Int = 60,
    thickness: Int = 80,
    advanceWidth: Int = 580,
): Glyph {
    fun octagon(
        hw: Int,
        hh: Int,
        ch: Int,
    ): Contour =
        polygon(
            hw - ch to hh,
            hw to hh - ch,
            hw to -hh + ch,
            hw - ch to -hh,
            -hw + ch to -hh,
            -hw to -hh + ch,
            -hw to hh - ch,
            -hw + ch to hh,
        )
    val outer = octagon(halfWidth, halfHeight, chamfer)
    val inner = octagon(halfWidth - thickness, halfHeight - thickness, (chamfer - thickness).coerceAtLeast(10))
    return Glyph("o", advanceWidth, listOf(outer, inner))
}

/** A plain rectangular stem `T`, no serif: a vertical bar of [stemWidth] from `y=0` to [capHeight], with a horizontal crossbar at the top. */
internal fun sansTGlyph(
    capHeight: Int = 700,
    stemWidth: Int = 90,
    barWidth: Int = 500,
    barHeight: Int = 90,
    advanceWidth: Int = 560,
): Glyph {
    val stemLeft = (barWidth - stemWidth) / 2
    val stemRight = stemLeft + stemWidth
    val barTop = capHeight
    val barBottom = capHeight - barHeight
    val outline =
        polygon(
            stemLeft to 0,
            stemRight to 0,
            stemRight to barBottom,
            barWidth to barBottom,
            barWidth to barTop,
            0 to barTop,
            0 to barBottom,
            stemLeft to barBottom,
        )
    return Glyph("T", advanceWidth, listOf(outline))
}

/** A gradual, evenly-spread taper from full overhang to none across `[0, rise]` -- [serifTGlyph]'s bracketed (garalde/transitional-like) profile. */
internal fun gradualSerifProfile(
    overhang: Int,
    rise: Int,
    steps: Int = 8,
): List<Pair<Int, Int>> = (0..steps).map { i -> (rise * i) / steps to overhang - (overhang * i) / steps }

/** A profile that stays at full overhang almost all the way up [rise], then drops to none in the last [dropFraction] of it -- [serifTGlyph]'s unbracketed (slab-like) profile: nearly a step function. */
internal fun abruptSerifProfile(
    overhang: Int,
    rise: Int,
    dropFraction: Double = 0.15,
): List<Pair<Int, Int>> {
    val dropStart = (rise * (1.0 - dropFraction)).roundToInt()
    return listOf(0 to overhang, dropStart to overhang, rise to 0)
}

/**
 * A serif `T`: [sansTGlyph]'s stem, but flared at the foot per [profile] -- a list of
 * `(heightAboveBaseline, overhang)` breakpoints, first at height 0 with the full overhang, last
 * at the height the flare completes with overhang 0, connected by straight lines. Use
 * [gradualSerifProfile] for a smooth, well-bracketed foot or [abruptSerifProfile] for a sudden,
 * unbracketed one -- see [SerifBracketTest] for why the shape of the profile (not just how many
 * points it has) is what [serifMetrics]'s bracket score actually reads. The crossbar keeps
 * [sansTGlyph]'s plain top so only the foot is under test.
 */
internal fun serifTGlyph(
    profile: List<Pair<Int, Int>>,
    capHeight: Int = 700,
    stemWidth: Int = 90,
    barWidth: Int = 500,
    barHeight: Int = 90,
    advanceWidth: Int = 560,
): Glyph {
    val stemLeft = (barWidth - stemWidth) / 2
    val stemRight = stemLeft + stemWidth
    val barTop = capHeight
    val barBottom = capHeight - barHeight

    // Right side of the stem, walked bottom (widest) to top (narrowest -- back to the plain stem).
    val rightSide = profile.map { (y, overhang) -> (stemRight + overhang) to y }
    val leftSide = profile.map { (y, overhang) -> (stemLeft - overhang) to y }

    val outline =
        polygon(
            *leftSide.reversed().toTypedArray(),
            *rightSide.toTypedArray(),
            stemRight to barBottom,
            barWidth to barBottom,
            barWidth to barTop,
            0 to barTop,
            0 to barBottom,
            stemLeft to barBottom,
        )
    return Glyph("T", advanceWidth, listOf(outline))
}

/** A simple two-contour `a`/`g`-shaped glyph: an outer silhouette and one counter whose ink height is [counterHeightFraction] of the outer's, for [storeysFromA]'s bounding-box-ratio test. */
internal fun singleCounterGlyph(
    name: String,
    totalHeight: Int = 500,
    totalWidth: Int = 460,
    counterHeightFraction: Double,
): Glyph {
    val counterTop = (totalHeight * counterHeightFraction).roundToInt()
    val outer = polygon(0 to 0, totalWidth to 0, totalWidth to totalHeight, 0 to totalHeight)
    val counter = polygon(60 to 20, totalWidth - 60 to 20, totalWidth - 60 to counterTop, 60 to counterTop)
    return Glyph(name, totalWidth + 80, listOf(outer, counter))
}

/** A `g`-shaped glyph with [contourCount] total contours (1 outline + `contourCount - 1` placeholder counters), for [storeysFromG]'s contour-count test. Geometry is not meaningful beyond the count. */
internal fun placeholderContourGlyph(
    name: String,
    contourCount: Int,
): Glyph {
    val outer = polygon(0 to 0, 400 to 0, 400 to 500, 0 to 500)
    val counters =
        (1 until contourCount).map { i ->
            polygon(50 to 50 + i * 10, 150 to 50 + i * 10, 150 to 120 + i * 10, 50 to 120 + i * 10)
        }
    return Glyph(name, 480, listOf(outer) + counters)
}

/**
 * A `c`-shaped ring with a wedge missing on the right (the aperture), built entirely from
 * straight polygon segments discretising the outer/inner arcs. The outer arc spans
 * `[gapHalfAngleDegrees, 360 - gapHalfAngleDegrees]`; the inner arc spans the same range widened
 * by [innerGapHalfAngleExtraDegrees] on each side. At `0` extra degrees the two terminal-closing
 * segments (outer arc's end to inner arc's start, and back) are purely radial, close to
 * horizontal since the gap is centred on angle 0 -- a flat, axis-aligned cut. A positive extra
 * angle offsets the inner ring's end from the outer ring's end, so the closing chord is no longer
 * radial -- a diagonal, angled cut.
 */
internal fun straightCutCGlyph(
    outerRadius: Double = 250.0,
    innerRadius: Double = 160.0,
    gapHalfAngleDegrees: Double = 12.0,
    innerGapHalfAngleExtraDegrees: Double = 0.0,
    arcSteps: Int = 40,
): Glyph {
    val outerStart = gapHalfAngleDegrees
    val outerEnd = 360.0 - gapHalfAngleDegrees
    val innerStart = gapHalfAngleDegrees + innerGapHalfAngleExtraDegrees
    val innerEnd = 360.0 - gapHalfAngleDegrees - innerGapHalfAngleExtraDegrees

    fun rad(deg: Double) = deg * kotlin.math.PI / 180.0
    val outerArc =
        (0..arcSteps).map { i ->
            val deg = outerStart + (outerEnd - outerStart) * i / arcSteps
            (outerRadius * kotlin.math.cos(rad(deg))).roundToInt() to (outerRadius * kotlin.math.sin(rad(deg))).roundToInt()
        }
    val innerArc =
        (0..arcSteps).map { i ->
            val deg = innerEnd - (innerEnd - innerStart) * i / arcSteps
            (innerRadius * kotlin.math.cos(rad(deg))).roundToInt() to (innerRadius * kotlin.math.sin(rad(deg))).roundToInt()
        }
    // outerArc: from just-past-gap (bottom) round the long way to just-before-gap (top);
    // then a straight terminal cut to the inner ring's matching end; innerArc back the long way;
    // then a straight terminal cut back to the outer ring's start -- the contour closes itself.
    val points = (outerArc + innerArc).map { (x, y) -> x to y }
    return Glyph("c", 520, listOf(polygon(*points.toTypedArray())))
}

/**
 * A "[" bracket (three solid walls around a square cavity, open on the right -- the same,
 * hand-verified shape [com.asoc.typewright.qa.corpus.style.Geometry2DTest] uses to prove
 * [narrowestThroat] finds a real aperture rather than the walls' own thickness), but with the
 * bottom-right and top-right corners cut on a long diagonal instead of squared off -- a clean,
 * unambiguous angled terminal: the throat search lands exactly on the two diagonal cuts (verified
 * against a standalone Python port of the algorithm while building this fixture), each at ~32
 * degrees from horizontal.
 */
internal fun angledCutBracketGlyph(): Glyph {
    val outline =
        polygon(
            0 to 0,
            300 to 0,
            300 to 120,
            140 to 220,
            60 to 220,
            60 to 380,
            140 to 380,
            300 to 480,
            300 to 600,
            0 to 600,
        )
    return Glyph("c", 620, listOf(outline))
}

/**
 * The same bracket as [angledCutBracketGlyph], but with the two diagonal corners replaced by
 * genuinely curved (quadratic) segments bulging outward -- a round terminal, no straight cut at
 * all. Built from the same validated topology so the throat search lands on the curves for the
 * same reason it lands on the diagonals above.
 */
internal fun roundCutBracketGlyph(): Glyph {
    fun on(
        x: Int,
        y: Int,
    ) = ContourPoint(Point(x, y), onCurve = true)

    fun off(
        x: Int,
        y: Int,
    ) = ContourPoint(Point(x, y), onCurve = false)
    val points =
        listOf(
            on(0, 0),
            on(300, 0),
            on(300, 120),
            off(230, 280), // bulges well off the (300,120)-(60,220) chord
            on(60, 220),
            on(60, 380),
            off(230, 220), // mirrors the bulge, off the (60,380)-(300,480) chord
            on(300, 480),
            on(300, 600),
            on(0, 600),
        )
    val contour = Contour(points, CurveFormat.QUADRATIC)
    return Glyph("c", 620, listOf(contour))
}

/** Two rectangles of ink height [xHeight] and [capHeightValue] respectively, standing in for `x` and `H` ([xHeightToCapHeightRatio]'s inputs). */
internal fun proportionGlyphs(
    xHeight: Int,
    capHeightValue: Int,
): Pair<Glyph, Glyph> {
    val x = Glyph("x", 420, listOf(polygon(0 to 0, 380 to 0, 380 to xHeight, 0 to xHeight)))
    val h = Glyph("H", 480, listOf(polygon(0 to 0, 420 to 0, 420 to capHeightValue, 0 to capHeightValue)))
    return x to h
}
