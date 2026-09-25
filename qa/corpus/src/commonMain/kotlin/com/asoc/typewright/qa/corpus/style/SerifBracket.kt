// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.corpus.style

import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Vec2

/**
 * Serif presence and bracket, measured on `T` (brief 8.4: "detect via terminal geometry on `T` or
 * `n`"). `T` is used here rather than `n` because its stem is a single straight run with no arch
 * to avoid: everything from the baseline up to well below the crossbar is pure stem, so a
 * horizontal probe at any height in that range crosses exactly the stem.
 *
 * The method: [widthAt] probes horizontally at a height fraction of `T`'s own ink height and
 * takes the *widest* ink interval at that height (this is what keeps the probe on the stem rather
 * than, at very low fractions, a serif's separate-looking wing if the probe geometry ever split
 * it into more than one interval). [serifMetrics] then compares the width just above the baseline
 * (where a serif's flare, if any, is close to its widest) against the width at mid-stem (clean,
 * unflared):
 * - [SerifMetrics.flareRatio] = serif-zone width / mid-stem width. Near 1.0 means no serif; this
 *   package calls it a serif once the ratio passes [SERIF_THRESHOLD].
 * - [SerifMetrics.bracketScore], only computed when a serif is present, samples the width at
 *   eight heights from just above the baseline up to 20% of the stem's ink height and asks how
 *   *spread out* the narrowing is as the probe rises: a bracketed serif (garalde, transitional)
 *   narrows gradually across most of those samples; an unbracketed one (slab) narrows almost
 *   entirely within one or two of them, close to a step function. The score is
 *   `1 - (biggest single step / total narrowing)`, so a single-step narrowing scores close to 0
 *   and an evenly-spread narrowing scores close to `1 - 1/(samples-1)`.
 *
 * This is `qa/corpus`'s own heuristic (law 5), not a published measurement: it assumes the serif
 * flare (if any) is complete well before 20% of the stem's own ink height, which held for every
 * font this package was checked against (see the module's validation notes) but is not a
 * guarantee for an unusually tall or unusually short serif.
 */
private const val SERIF_THRESHOLD = 1.15

// Public (P6, `ui`'s Anatomy Lens): com.asoc.typewright.ui.learn.AnatomyLensData wires this
// same function to the "serif" lens term on the user's own `T`, so it has to cross the
// `:qa:corpus` module boundary -- see that file's KDoc for the rest of the wiring.
fun serifMetrics(t: Glyph): SerifMetrics? {
    val bounds = t.inkBounds() ?: return null
    val stemHeight = bounds.height
    if (stemHeight <= 0.0) return null

    fun widthAt(heightFraction: Double): Double? {
        val y = bounds.minY + heightFraction * stemHeight
        val intervals = inkIntervals(t.lineCrossings(Vec2(bounds.minX - 1.0, y), Vec2(1.0, 0.0)))
        return intervals.maxByOrNull { it.length }?.length?.takeIf { it > 0.0 }
    }

    val stemWidth = widthAt(0.50) ?: return null
    val flareFractions = listOf(0.015, 0.04, 0.06, 0.08, 0.11, 0.14, 0.17, 0.20)
    val flareWidths = flareFractions.mapNotNull { f -> widthAt(f)?.let { f to it } }
    val serifWidth = flareWidths.firstOrNull()?.second ?: return null

    val flareRatio = serifWidth / stemWidth
    val hasSerif = flareRatio > SERIF_THRESHOLD
    val bracketScore =
        if (hasSerif && flareWidths.size >= 3) {
            val narrowingSteps = flareWidths.zipWithNext { a, b -> a.second - b.second }.filter { it > 0.0 }
            val total = narrowingSteps.sum()
            if (total <= 0.0) null else (1.0 - narrowingSteps.max() / total).coerceIn(0.0, 1.0)
        } else {
            null
        }
    return SerifMetrics(hasSerif, flareRatio, bracketScore)
}
