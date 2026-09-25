// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.corpus.style

/**
 * Extracts a [FeatureVector] from [glyphSet] (brief 8.4's nine features). Every sub-feature skips
 * gracefully when the glyph(s) it needs are missing -- P1b's "skip any glyph missing from a given
 * font without failing the whole extraction" -- so a font that, say, has no `g` still yields
 * every other feature. [apertureOpenness] is averaged over whichever of `c`, `e`, `s` are present.
 */
internal fun extractFeatures(glyphSet: StyleGlyphSet): FeatureVector {
    val g = glyphSet.glyphs
    val o = g['o']
    val a = g['a']
    val gGlyph = g['g']
    val e = g['e']
    val capH = g['H']
    val t = g['T']
    val c = g['c']
    val x = g['x']
    val s = g['s']

    val serif = t?.let { serifMetrics(it) }
    val storeysA = a?.let { storeysFromA(it) } ?: Storeys.UNKNOWN
    val storeysG = gGlyph?.let { storeysFromG(it) } ?: Storeys.UNKNOWN
    val terminal = c?.let { glyph -> glyph.outerContour()?.let { terminalStyle(glyph, it) } } ?: TerminalStyle.UNKNOWN

    val xHeight = x?.inkBounds()?.maxY
    val apertures =
        listOfNotNull(c, e, s).mapNotNull { glyph -> xHeight?.let { apertureOpenness(glyph, it) } }

    return FeatureVector(
        contrastRatio = o?.let { contrastRatio(it) },
        stressAngleDegrees = o?.let { stressAngleDegrees(it) },
        hasSerif = serif?.hasSerif,
        bracketScore = serif?.bracketScore,
        storeys = combineStoreys(storeysA, storeysG),
        terminalStyle = terminal,
        apertureOpenness = apertures.takeIf { it.isNotEmpty() }?.let { it.sum() / it.size },
        oRoundnessExponent = o?.let { superellipseExponent(it) },
        xHeightToCapHeightRatio = xHeightToCapHeightRatio(x, capH),
        widthClass = widthClass(g.values, glyphSet.unitsPerEm),
    )
}
