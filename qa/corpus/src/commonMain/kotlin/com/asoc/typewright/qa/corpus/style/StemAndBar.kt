// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.corpus.style

import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Vec2

/**
 * Stem weight: the thickness of [glyph]'s left upright stem divided by its ink height, read with
 * horizontal probes at a few heights that stay clear of a crossbar and of serifs. Only a probe
 * that crosses at least two separate runs of ink counts (the left stem is the first run), so a
 * probe that lands on a low or high crossbar is ignored rather than read as one wide stem. On `H`
 * this is the classic weight measure: ADA 703.5 states stroke width as a share of the height of
 * `I`, and type designers quote stem to cap height. Null when no probe finds two runs.
 */
fun stemToHeightRatio(glyph: Glyph): Double? {
    val bounds = glyph.inkBounds() ?: return null
    if (bounds.height <= 0.0) return null
    val widths =
        STEM_PROBE_HEIGHTS.mapNotNull { fraction ->
            val y = bounds.minY + bounds.height * fraction
            val runs = inkIntervals(glyph.lineCrossings(Vec2(bounds.minX - PROBE_MARGIN, y), Vec2(1.0, 0.0)))
            if (runs.size >= 2) runs.first().length.takeIf { it > 0.0 } else null
        }
    if (widths.isEmpty()) return null
    return widths.sorted()[(widths.size - 1) / 2] / bounds.height
}

/**
 * Where [glyph]'s crossbar sits, as a share of its ink height (0 = baseline, 1 = top), read on a
 * vertical probe through the glyph's centre. On `H` the centre line crosses the bar and nothing
 * else; the longest run is taken in case a decorative stroke also reaches the centre. 0.5 is a bar
 * at mid height; art deco often moves it well above or below (a "high-waisted" or "low-waisted"
 * letter), which none of the other style features can see. Null when the centre line meets no ink.
 */
fun crossbarHeightRatio(glyph: Glyph): Double? {
    val bounds = glyph.inkBounds() ?: return null
    if (bounds.height <= 0.0) return null
    val originY = bounds.minY - PROBE_MARGIN
    val runs = inkIntervals(glyph.lineCrossings(Vec2(bounds.centerX, originY), Vec2(0.0, 1.0)))
    val bar = runs.maxByOrNull { it.length } ?: return null
    val barCentreY = originY + (bar.start + bar.end) / 2.0
    return ((barCentreY - bounds.minY) / bounds.height).coerceIn(0.0, 1.0)
}

/** Probe heights for [stemToHeightRatio]: clear of serifs (below 0.15) and of a mid-height bar. */
private val STEM_PROBE_HEIGHTS = listOf(0.18, 0.3, 0.7, 0.82)

/** How far outside the ink bounds a probe line starts, in font units, so it begins outside the glyph. */
private const val PROBE_MARGIN = 10.0
