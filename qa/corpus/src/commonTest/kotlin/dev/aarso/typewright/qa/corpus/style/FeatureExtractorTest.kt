package dev.aarso.typewright.qa.corpus.style

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FeatureExtractorTest {
    private fun geometricLikeGlyphSet(): StyleGlyphSet {
        val (x, h) = proportionGlyphs(xHeight = 460, capHeightValue = 700)
        return StyleGlyphSet(
            unitsPerEm = 1000,
            glyphs =
                mapOf(
                    'o' to circleRingGlyph(),
                    'a' to singleCounterGlyph("a", counterHeightFraction = 0.90),
                    'g' to placeholderContourGlyph("g", contourCount = 2),
                    'H' to h,
                    'x' to x,
                    'T' to sansTGlyph(),
                    'c' to straightCutCGlyph(),
                ),
        )
    }

    @Test
    fun extractsEveryFeatureWhenAllGlyphsArePresent() {
        val features = extractFeatures(geometricLikeGlyphSet())
        assertNotNull(features.contrastRatio)
        assertNotNull(features.stressAngleDegrees)
        assertEquals(false, features.hasSerif)
        assertNull(features.bracketScore) // no serif -> not computed
        assertEquals(Storeys.SINGLE, features.storeys)
        assertNotNull(features.apertureOpenness)
        assertNotNull(features.oRoundnessExponent)
        assertNotNull(features.xHeightToCapHeightRatio)
        assertNotNull(features.widthClass)
    }

    @Test
    fun skipsMissingGlyphsWithoutFailing() {
        val full = geometricLikeGlyphSet()
        val missingG = full.copy(glyphs = full.glyphs - 'g')
        val features = extractFeatures(missingG)
        // g was the only glyph informing storeys here besides a (single); a alone still answers.
        assertEquals(Storeys.SINGLE, features.storeys)

        val missingEverythingButO = StyleGlyphSet(1000, mapOf('o' to circleRingGlyph()))
        val sparse = extractFeatures(missingEverythingButO)
        assertNotNull(sparse.contrastRatio)
        assertEquals(Storeys.UNKNOWN, sparse.storeys)
        assertEquals(TerminalStyle.UNKNOWN, sparse.terminalStyle)
        assertNull(sparse.apertureOpenness)
        assertNull(sparse.xHeightToCapHeightRatio)
    }

    @Test
    fun emptyGlyphSetExtractsAllNulls() {
        val features = extractFeatures(StyleGlyphSet(1000, emptyMap()))
        assertNull(features.contrastRatio)
        assertNull(features.stressAngleDegrees)
        assertNull(features.hasSerif)
        assertNull(features.bracketScore)
        assertEquals(Storeys.UNKNOWN, features.storeys)
        assertEquals(TerminalStyle.UNKNOWN, features.terminalStyle)
        assertNull(features.apertureOpenness)
        assertNull(features.oRoundnessExponent)
        assertNull(features.xHeightToCapHeightRatio)
        assertNull(features.widthClass)
    }
}
