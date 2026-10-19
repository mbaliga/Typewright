// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.corpus.style

import com.asoc.typewright.core.font.sfnt.SfntFont
import com.asoc.typewright.core.geometry.Glyph
import kotlin.math.abs

/**
 * A font's measured style: the detector's feature vector (brief 8.4) plus two measures the Brief
 * needs and the class scorer does not use, stem weight and crossbar height. This is the public
 * face of the detector: the style atlas is built from it, and the Brief compares a project's own
 * letters against a target with it, so both always mean the same thing by every number.
 *
 * Every field is null (or UNKNOWN) when the glyph it is read from is missing or degenerate; a
 * caller shows that as "not measured yet", never as a mismatch.
 *
 * - [contrastRatio]: thickest over thinnest stroke around `o` (1.0 = monoline).
 * - [stressAngleDegrees]: tilt of `o`'s thickest diameter from vertical, folded to (-90, 90].
 * - [hasSerif], [bracketScore]: from `T`'s stem foot; the bracket score only when there is a serif.
 * - [storeys]: single- or double-storey `a`/`g` combined (`g` preferred), as the class scorer reads it;
 *   [aStoreys] and [gStoreys] are the two letters on their own.
 * - [terminalStyle]: how `c`'s open terminal is cut.
 * - [apertureOpenness]: narrowest throat of `c`/`e`/`s` over x-height.
 * - [oRoundnessExponent]: superellipse exponent of `o`'s outer contour (2 = ellipse).
 * - [xHeightToCapHeightRatio]: `x` ink height over `H` ink height.
 * - [widthClass]: mean advance of the measured glyphs over units per em.
 * - [stemToCapHeight]: `H`'s stem thickness over its height ([stemToHeightRatio]).
 * - [crossbarHeight]: `H`'s bar position over its height ([crossbarHeightRatio]).
 */
data class StyleMeasurement(
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
    val stemToCapHeight: Double?,
    val crossbarHeight: Double?,
    val aStoreys: Storeys = Storeys.UNKNOWN,
    val gStoreys: Storeys = Storeys.UNKNOWN,
) {
    /** The stress angle without its sign: 0 is vertical stress, 90 horizontal. */
    val stressAngleAbs: Double? get() = stressAngleDegrees?.let { abs(it) }

    /** True when nothing at all could be measured (no style glyphs drawn yet). */
    val isEmpty: Boolean
        get() =
            contrastRatio == null && stressAngleDegrees == null && hasSerif == null && bracketScore == null &&
                storeys == Storeys.UNKNOWN && terminalStyle == TerminalStyle.UNKNOWN && apertureOpenness == null &&
                oRoundnessExponent == null && xHeightToCapHeightRatio == null && widthClass == null &&
                stemToCapHeight == null && crossbarHeight == null && aStoreys == Storeys.UNKNOWN && gStoreys == Storeys.UNKNOWN

    companion object {
        /** A measurement of nothing. */
        val EMPTY =
            StyleMeasurement(
                contrastRatio = null,
                stressAngleDegrees = null,
                hasSerif = null,
                bracketScore = null,
                storeys = Storeys.UNKNOWN,
                terminalStyle = TerminalStyle.UNKNOWN,
                apertureOpenness = null,
                oRoundnessExponent = null,
                xHeightToCapHeightRatio = null,
                widthClass = null,
                stemToCapHeight = null,
                crossbarHeight = null,
            )
    }
}

/** The characters [measureStyle] reads; a caller can show which of them are still undrawn. */
const val STYLE_MEASUREMENT_GLYPHS: String = STYLE_DETECTOR_GLYPHS

/**
 * Measures [glyphs] (keyed by the character each one draws) at [unitsPerEm]. Only the characters
 * in [STYLE_MEASUREMENT_GLYPHS] are read; anything else in the map is ignored, so a caller can
 * pass a whole project's cmap.
 */
fun measureStyle(
    glyphs: Map<Char, Glyph>,
    unitsPerEm: Int,
): StyleMeasurement {
    val set = StyleGlyphSet(unitsPerEm, glyphs.filterKeys { it in STYLE_DETECTOR_GLYPHS })
    return measure(set)
}

/** Measures a compiled font: the same glyphs, looked up through its `cmap`. */
fun measureStyle(font: SfntFont): StyleMeasurement = measure(StyleGlyphSet.fromSfntFont(font))

private fun measure(set: StyleGlyphSet): StyleMeasurement {
    val f = extractFeatures(set)
    val capH = set.glyphs['H']
    return StyleMeasurement(
        contrastRatio = f.contrastRatio,
        stressAngleDegrees = f.stressAngleDegrees,
        hasSerif = f.hasSerif,
        bracketScore = f.bracketScore,
        storeys = f.storeys,
        terminalStyle = f.terminalStyle,
        apertureOpenness = f.apertureOpenness,
        oRoundnessExponent = f.oRoundnessExponent,
        xHeightToCapHeightRatio = f.xHeightToCapHeightRatio,
        widthClass = f.widthClass,
        stemToCapHeight = capH?.let { stemToHeightRatio(it) },
        crossbarHeight = capH?.let { crossbarHeightRatio(it) },
        aStoreys = f.aStoreys,
        gStoreys = f.gStoreys,
    )
}

/** One style class the detector considers, with its confidence (0..1) and the evidence for it. */
data class StyleClassMatch(
    val styleKey: String,
    val confidence: Double,
    val evidence: List<String>,
)

/**
 * The detector's ranking of the ten corpus classes for [measurement], most likely first, with
 * the plain-language evidence behind each score (brief 8.4's "infers a class ... and shows its
 * evidence"). The scorer is a hand-written heuristic, so the UI labels it "our heuristic".
 */
fun rankStyleClasses(measurement: StyleMeasurement): List<StyleClassMatch> =
    rankStyleClasses(
        FeatureVector(
            contrastRatio = measurement.contrastRatio,
            stressAngleDegrees = measurement.stressAngleDegrees,
            hasSerif = measurement.hasSerif,
            bracketScore = measurement.bracketScore,
            storeys = measurement.storeys,
            terminalStyle = measurement.terminalStyle,
            apertureOpenness = measurement.apertureOpenness,
            oRoundnessExponent = measurement.oRoundnessExponent,
            xHeightToCapHeightRatio = measurement.xHeightToCapHeightRatio,
            widthClass = measurement.widthClass,
            aStoreys = measurement.aStoreys,
            gStoreys = measurement.gStoreys,
        ),
    ).map { StyleClassMatch(it.styleKey, it.confidence, it.evidence) }
