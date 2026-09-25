// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.learn

import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.qa.corpus.style.contrastRatio
import com.asoc.typewright.qa.corpus.style.inkBounds
import com.asoc.typewright.qa.corpus.style.inkIntervals
import com.asoc.typewright.qa.corpus.style.lineCrossings

/**
 * The Overlay tab's stroke probe (`ui/typewright-explorer.html`'s `#ln-ov` `.ovctl` readout:
 * "stem 44 · bar 44 · contrast 1.00"), read off the *current project layer's own glyphs*
 * (TYPEWRIGHT_BUILD_BRIEF.md section 9's own wording), never a comparison layer -- CLAUDE.md
 * law 5: every number here is a real measurement, not invented, and [StrokeProbeReading]'s three
 * fields are `null`, not a guessed placeholder, whenever the underlying probe found nothing
 * usable.
 *
 * **Contrast** reuses `qa/corpus`'s [contrastRatio] on `o` directly (P1b, already built and
 * tested; also already wired once for the Lens tab by
 * [com.asoc.typewright.ui.learn.contrastEntry] -- checked before writing this file, per this
 * task's own instruction to look for a reusable API first). **Stem** and **bar** are this file's
 * own small addition on top of the exact same even-odd ray-probe primitives `ContrastStress.kt`
 * already uses internally ([Glyph.lineCrossings]/[inkIntervals], `Geometry2D.kt`, widened public
 * for this file -- see that file's KDoc): [contrastRatio] only ever returns a *ratio*
 * (thickest/thinnest around `o`'s own ring), never an absolute width in font units, and the
 * explorer's own readout needs both a `stem` and a `bar` as real numbers, not a ratio.
 *
 * "Stem" is measured as the width of the leftmost ink interval a horizontal probe ray crosses
 * through the stem glyph's own ink-bounds vertical mid-point -- its left stroke, the same "main
 * vertical stroke" `ui/typewright-explorer.html`'s own Lens tab calls "the font's most important
 * number". [OverlayTab] passes `b` (`OVERLAY_DEFAULT_WORD`'s own fourth letter -- "Hamburg" --
 * so the probed glyph is one the word field already draws, not a letter pulled in only for the
 * probe). "Bar" is the height of the ink interval a vertical probe ray crosses through the bar
 * glyph's own ink-bounds horizontal centre -- a crossbar, the one place a vertical ray through a
 * capital H crosses ink exactly once between its two stems; [OverlayTab] passes `H`, the word's
 * own first letter. Both are one probe line each, not an average over several, matching this
 * package's own "one real measurement on one of the user's own letters" convention
 * ([com.asoc.typewright.ui.learn.apertureEntry]'s own KDoc).
 *
 * Verified against `fonts/HyleDeco-Regular.ttf` (this build's own real project-font fixture, a
 * pure-polygon font per CLAUDE.md's own fixture note, so these probes have no curve-sampling
 * error to worry about there): `b`'s left stem is exactly 44.0 units (every one of `b`/`m`/`n`/`r`/`u`'s
 * own left stems comes out 44.0 too -- checked while building this file -- matching
 * `ui/typewright-explorer.html`'s own Lens-tab illustration text, "Its width, 44 units here") and
 * `H`'s crossbar is exactly 43.0 units -- both cross-checked against an independent probe written
 * from scratch against the raw `glyf` points with fontTools while building this file (not derived
 * from, or copied out of, this file's own code), and pinned as [StrokeProbeTest]'s own real-data
 * regression numbers.
 */
data class StrokeProbeReading(
    val stemUnits: Double?,
    val barUnits: Double?,
    val contrast: Double?,
)

/** The horizontal-probe interval nearest [stemGlyph]'s own left edge -- its stem -- or `null` if [stemGlyph] has no usable ink bounds or the probe crosses nothing. */
internal fun stemWidth(stemGlyph: Glyph): Double? {
    val bounds = stemGlyph.inkBounds() ?: return null
    val y = bounds.centerY
    val origin = Vec2(bounds.minX - 1.0, y)
    val intervals = inkIntervals(stemGlyph.lineCrossings(origin, Vec2(1.0, 0.0)))
    return intervals.minByOrNull { it.start }?.length
}

/** The vertical-probe interval through [barGlyph]'s own ink-bounds horizontal centre -- its crossbar -- or `null` if [barGlyph] has no usable ink bounds or the probe crosses nothing. */
internal fun barWidth(barGlyph: Glyph): Double? {
    val bounds = barGlyph.inkBounds() ?: return null
    val x = bounds.centerX
    val origin = Vec2(x, bounds.minY - 1.0)
    val intervals = inkIntervals(barGlyph.lineCrossings(origin, Vec2(0.0, 1.0)))
    return intervals.firstOrNull()?.length
}

/**
 * The Overlay tab's whole probe readout for one font: [stemGlyph]/[barGlyph]/[oGlyph] are the
 * caller's own choice of glyph for each measurement (`null` when the font has no mapping for one
 * of them -- the reading's matching field is then `null` too, never a guess). See this file's own
 * top-level KDoc for what each field measures, and which real glyph [OverlayTab] passes for each.
 */
fun strokeProbe(
    stemGlyph: Glyph?,
    barGlyph: Glyph?,
    oGlyph: Glyph?,
): StrokeProbeReading =
    StrokeProbeReading(
        stemUnits = stemGlyph?.let { stemWidth(it) },
        barUnits = barGlyph?.let { barWidth(it) },
        contrast = oGlyph?.let { contrastRatio(it) },
    )
