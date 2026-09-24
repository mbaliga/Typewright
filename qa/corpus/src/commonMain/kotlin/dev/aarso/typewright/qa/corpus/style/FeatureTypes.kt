package dev.aarso.typewright.qa.corpus.style

import dev.aarso.typewright.core.font.sfnt.SfntFont
import dev.aarso.typewright.core.geometry.Glyph

/**
 * The glyph set [extractFeatures] needs (TYPEWRIGHT_BUILD_BRIEF.md 8.4): o n a g e H T c s. A
 * font missing one of them still extracts every feature that does not need it -- "skip any glyph
 * missing from a given font without failing the whole extraction" (P1b's own instruction) -- so
 * [glyphs] is keyed by the character it stands for and simply omits an entry it could not fill,
 * rather than every caller re-deriving that from a font reader.
 */
internal data class StyleGlyphSet(
    val unitsPerEm: Int,
    val glyphs: Map<Char, Glyph>,
) {
    companion object
}

/** The nine characters [StyleGlyphSet.fromSfntFont] looks up (brief 8.4's own glyph list). */
internal const val STYLE_DETECTOR_GLYPHS = "onageHTcs"

/**
 * Builds a [StyleGlyphSet] from [font]'s `cmap`, looking up [STYLE_DETECTOR_GLYPHS] by Unicode
 * code point and silently leaving out any character the font has no mapping for -- never
 * throwing, per P1b's "skip any glyph missing... without failing the whole extraction".
 */
internal fun StyleGlyphSet.Companion.fromSfntFont(font: SfntFont): StyleGlyphSet {
    val glyphs =
        STYLE_DETECTOR_GLYPHS
            .mapNotNull { ch -> font.glyphForCodePoint(ch.code)?.let { ch to it } }
            .toMap()
    return StyleGlyphSet(font.head.unitsPerEm, glyphs)
}

// Kotlin has no static-extension-on-companion syntax without one; this empty companion is what
// `StyleGlyphSet.fromSfntFont` above and `StyleGlyphSetTest` attach to.
internal object StyleGlyphSetCompanionAnchor

/** Single- versus double-storey construction of `a`/`g` (brief 8.4). [UNKNOWN] means the glyph needed to tell was missing or had no usable contours -- never guessed. */
internal enum class Storeys { SINGLE, DOUBLE, UNKNOWN }

/** How an open terminal (on `c`, brief 8.4) is cut, from [Geometry2D.narrowestThroat]'s two near points -- see [terminalStyle]'s KDoc for the geometric rule. [UNKNOWN] means the terminal could not be located. */
internal enum class TerminalStyle { FLAT, ROUND, ANGLED, UNKNOWN }

/**
 * Serif presence and bracket for one glyph (brief 8.4, measured on `T`): [hasSerif] from the
 * flare at the stem's foot; [flareRatio] is the foot width divided by the clean mid-stem width
 * (1.0 = no flare at all); [bracketScore] is only meaningful when [hasSerif] is true -- `null`
 * otherwise -- and runs roughly 0 (an abrupt, unbracketed step, e.g. a slab serif) to 1 (a smooth,
 * fully bracketed curve, e.g. a garalde serif); see [serifMetrics]'s KDoc for exactly how it is
 * computed and why it is "our heuristic" (law 5).
 */
internal data class SerifMetrics(
    val hasSerif: Boolean,
    val flareRatio: Double,
    val bracketScore: Double?,
)

/**
 * The style-detector feature vector (brief 8.4): nine measurable features, each nullable because
 * the glyph(s) it needs may be missing from the font (P1b's "skip... without failing the whole
 * extraction") or, for [oRoundnessExponent] and the stroke-probe features, degenerate (a
 * zero-width probe, an empty contour). [StyleScorer.kt] is the only file that interprets these
 * numbers as evidence for a style class; this type just carries the measurements.
 *
 * - [contrastRatio]: thickest/thinnest stroke width sampled around `o`'s ring (>= 1.0; 1.0 is
 *   perfectly monoline).
 * - [stressAngleDegrees]: the angle, from vertical, of the diameter through `o` where the ring is
 *   thickest -- `0` is vertical stress, folded into `(-90, 90]`. `null` when `o` is missing or the
 *   stroke probe found no usable width anywhere (a degenerate outline).
 * - [hasSerif] / [bracketScore]: from `T`'s stem foot (see [SerifMetrics]).
 * - [storeys]: `a`/`g`'s single- versus double-storey construction (see [combineStoreys]).
 * - [terminalStyle]: `c`'s open terminal, flat / round / angled.
 * - [apertureOpenness]: the narrowest throat of `c`/`e`/`s`'s open counter(s), averaged over
 *   whichever of the three are present, divided by x-height.
 * - [oRoundnessExponent]: the best-fit superellipse exponent `n` for `o`'s outer contour (`n = 2`
 *   is a true ellipse; higher trends toward a rounded rectangle/square).
 * - [xHeightToCapHeightRatio]: `x`'s ink height divided by `H`'s ink height.
 * - [widthClass]: the mean advance width of every glyph present in the set, divided by the font's
 *   units-per-em -- a proportion measure, not an absolute width.
 */
internal data class FeatureVector(
    val contrastRatio: Double?,
    val stressAngleDegrees: Double?,
    val hasSerif: Boolean?,
    val bracketScore: Double?,
    val storeys: Storeys,
    val terminalStyle: TerminalStyle,
    val apertureOpenness: Double?,
    val oRoundnessExponent: Double?,
    val xHeightToCapHeightRatio: Double?,
    val widthClass: Double?,
)
