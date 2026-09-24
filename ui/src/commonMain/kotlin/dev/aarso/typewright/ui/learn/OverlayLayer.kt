package dev.aarso.typewright.ui.learn

import dev.aarso.typewright.core.font.sfnt.SfntFont
import dev.aarso.typewright.core.geometry.Glyph
import dev.aarso.typewright.ui.tokens.MeaningColor

/**
 * One Overlay tab layer (`ui/typewright-explorer.html`'s `#ln-ov` `.layers`/`.ly`): a real font's
 * own glyphs plus the real metrics [OverlayTab] needs to align and probe it. Built once per font
 * via [buildOverlayLayer], pure and Compose-free (like every other file in this package) so it is
 * unit-testable without a Compose test harness.
 *
 * [capHeightUnits]/[xHeightUnits] reuse [dev.aarso.typewright.ui.learn.capHeightEntry]/
 * [dev.aarso.typewright.ui.learn.xHeightEntry] (this same package's own Anatomy Lens wiring,
 * task P6's other half, checked and reused rather than re-derived): the glyph's own drawn ink
 * height is primary, `OS/2`'s `sCapHeight`/`sxHeight` only a fallback (CLAUDE.md law 1, "the
 * user's drawing is the source of truth") -- both are `null` only when a font has neither its own
 * `H`/`x` glyph nor a positive `OS/2` fallback, which none of this build's own three demo layers
 * (Hyle Deco, EB Garamond, Libre Baskerville) ever hits, but [OverlayTab] still handles it
 * honestly (CLAUDE.md law 5) rather than assuming a font always has one.
 */
data class OverlayLayer(
    val id: String,
    val label: String,
    val sourceLabel: String,
    /** `null` for the project ("ink") layer, drawn as a solid fill per `ui/typewright-explorer.html`'s own `.lay.ink{fill:var(--ink)}`; a comparison layer's own dash intervals (dp, scaled independently of the outline's own font-unit-to-dp factor so the pattern reads at a consistent visual density regardless of glyph size) otherwise -- CLAUDE.md law 8: colour plus a line pattern, never colour alone. */
    val dashPattern: FloatArray?,
    /**
     * `null` for the project layer (drawn in the texture's own plain ink colour -- CLAUDE.md law
     * 8: "colour is meaning only", and the project layer is not a *meaning* distinction the way a
     * comparison layer's identity is). A comparison layer's own [dev.aarso.typewright.ui.tokens.MeaningColor],
     * one of [dev.aarso.typewright.ui.tokens.MeaningColors.AMBER]/[dev.aarso.typewright.ui.tokens.MeaningColors.MAGENTA]
     * -- `ui/tokens/CanvasTokens.kt`'s own two tokens already documented "comparison layers,
     * paired with a line pattern" (that object's own KDoc, written before this task), not
     * `ui/typewright-explorer.html`'s own literal violet/cyan swatches for its `l2`/`l3` layers:
     * this build's real token system already reserves violet for "selected node or contour" and
     * cyan for "the thing you are snapping to" elsewhere in the app (`MeaningColors.VIOLET`/`.CYAN`
     * own KDoc), so reusing either one here for "a different comparison font" would collide two
     * distinct meanings onto one colour -- exactly what law 8 rules out. Logged as an open
     * question (product owner: confirm amber/magenta over the mockup's own violet/cyan for this
     * one tab).
     */
    val meaningColor: MeaningColor?,
    val unitsPerEm: Int,
    val capHeightUnits: Double?,
    val xHeightUnits: Double?,
    val glyphs: Map<Char, Glyph>,
) {
    /** [glyphs] for every character in [word], in order, or `null` if any one of them is missing from this font -- [OverlayTab] falls back to an honest "this layer has no glyph for '…'" note rather than silently skipping a letter. */
    fun glyphsForWord(word: String): List<Glyph>? {
        val result = ArrayList<Glyph>(word.length)
        for (ch in word) {
            result += glyphs[ch] ?: return null
        }
        return result
    }
}

/** `H`'s own drawn ink height, or [os2CapHeight] (only when positive) if `H` is missing -- the exact fallback order [dev.aarso.typewright.ui.learn.capHeightEntry] already implements; unwrapped here to a plain nullable [Double] for [OverlayLayer]'s own alignment math. */
private fun realCapHeightUnits(
    hGlyph: Glyph?,
    os2CapHeight: Int?,
): Double? {
    val entry = capHeightEntry(hGlyph, os2CapHeight)
    val measured = entry as? AnatomyLensEntry.Measured ?: return null
    return (measured.value as? AnatomyLensValue.FontUnits)?.value?.toDouble()
}

/** `x`'s own drawn ink height, or [os2XHeight] (only when positive) if `x` is missing -- see [realCapHeightUnits]. */
private fun realXHeightUnits(
    xGlyph: Glyph?,
    os2XHeight: Int?,
): Double? {
    val entry = xHeightEntry(xGlyph, os2XHeight)
    val measured = entry as? AnatomyLensEntry.Measured ?: return null
    return (measured.value as? AnatomyLensValue.FontUnits)?.value?.toDouble()
}

/**
 * Builds one [OverlayLayer] from [font]'s own real glyphs and metrics: [chars] is every character
 * the caller will ever need from this layer (the word field's own letters, plus `n`/`H`/`o` for
 * the stroke probe on the project layer) -- collected once up front so [OverlayTab] never has to
 * decide, mid-composition, which glyph to decode next. A character [font] has no `cmap` mapping
 * for is simply left out of [OverlayLayer.glyphs] (never a synthesized placeholder, CLAUDE.md
 * law 5); `H`/`x` for the alignment metrics are looked up the same way, independent of whether
 * they were already in [chars].
 */
fun buildOverlayLayer(
    font: SfntFont,
    id: String,
    label: String,
    sourceLabel: String,
    dashPattern: FloatArray?,
    meaningColor: MeaningColor?,
    chars: String,
): OverlayLayer {
    val neededChars = (chars.toSet() + setOf('H', 'x', 'b', 'o')).sorted()
    val glyphs = neededChars.mapNotNull { ch -> font.glyphForCodePoint(ch.code)?.let { ch to it } }.toMap()
    val capHeight = realCapHeightUnits(glyphs['H'], font.os2?.sCapHeight)
    val xHeight = realXHeightUnits(glyphs['x'], font.os2?.sxHeight)
    return OverlayLayer(
        id = id,
        label = label,
        sourceLabel = sourceLabel,
        dashPattern = dashPattern,
        meaningColor = meaningColor,
        unitsPerEm = font.head.unitsPerEm,
        capHeightUnits = capHeight,
        xHeightUnits = xHeight,
        glyphs = glyphs,
    )
}
