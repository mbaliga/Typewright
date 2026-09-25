// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.learn

import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.ContourPoint
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.learn.scenes.STYLE_FEATURE_VOCABULARY
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Portable (JVM + Wasm/Node) unit tests for [AnatomyLensData]'s logic that does not need a real
 * font file: the "never guessed, return unavailable" paths every measured-entry function has, the
 * verbatim reuse of [STYLE_FEATURE_VOCABULARY]'s definitions, [ANATOMY_TERM_INFO]'s coverage of
 * every [AnatomyTerm], and [SerifKind]'s own threshold logic on hand-built synthetic glyphs (the
 * same style `qa/corpus`'s own `SyntheticGlyphs.kt` uses for its tests, rebuilt small and locally
 * here since a test source set is not shared across Gradle modules). The real-Hyle-Deco
 * cross-checks against `qa/corpus`'s own functions live in `desktopTest`'s
 * `AnatomyLensDataHyleDecoTest` (JVM-only: reading a file off disk).
 */
class AnatomyLensDataTest {
    // ---- STYLE_FEATURE_VOCABULARY reuse: verbatim, never re-typed ----

    @Test
    fun eightTermsReuseStyleFeatureVocabularyVerbatim() {
        val expected =
            mapOf(
                AnatomyTerm.CONTRAST to "Contrast",
                AnatomyTerm.STRESS to "Stress",
                AnatomyTerm.SERIF to "Serif and bracket",
                AnatomyTerm.STOREYS to "Storeys",
                AnatomyTerm.TERMINAL to "Terminal",
                AnatomyTerm.ROUNDNESS to "Roundness",
                AnatomyTerm.X_HEIGHT_TO_CAP_HEIGHT_RATIO to "x-height ratio",
            )
        for ((term, vocabName) in expected) {
            val vocab = STYLE_FEATURE_VOCABULARY.first { it.termName == vocabName }
            val entry = definitionOnlyOrMeasuredInfo(term)
            assertEquals(
                vocab.plainLanguageDefinition,
                entry.definition,
                "$term's definition must match STYLE_FEATURE_VOCABULARY's \"$vocabName\" verbatim",
            )
            assertEquals(
                vocab.exampleGlyphs,
                entry.occursOn,
                "$term's occursOn must match STYLE_FEATURE_VOCABULARY's \"$vocabName\" verbatim",
            )
        }
    }

    @Test
    fun apertureReusesVocabularyDefinitionButUsesTheResearchDocsBroaderOccursOnList() {
        val vocab = STYLE_FEATURE_VOCABULARY.first { it.termName == "Aperture" }
        val entry = definitionOnlyOrMeasuredInfo(AnatomyTerm.APERTURE)
        assertEquals(vocab.plainLanguageDefinition, entry.definition)
        assertTrue(entry.occursOn.containsAll(listOf("a", "c", "e", "f", "h", "m", "n", "r", "s", "t", "u")))
        assertEquals(11, entry.occursOn.size)
    }

    // ---- every AnatomyTerm has real definition text and a non-empty occursOn ----

    @Test
    fun everyAnatomyTermHasANonBlankDefinitionAndANonEmptyOccursOnList() {
        for (term in AnatomyTerm.entries) {
            val info = definitionOnlyOrMeasuredInfo(term)
            assertTrue(info.definition.isNotBlank(), "$term has a blank definition")
            assertTrue(info.occursOn.isNotEmpty(), "$term has an empty occursOn list")
        }
    }

    @Test
    fun definitionOnlyEntryCarriesNoMeasuredValue() {
        val entry = definitionOnlyEntry(AnatomyTerm.COUNTER)
        assertEquals(AnatomyTerm.COUNTER, entry.term)
        assertTrue(entry.occursOn.contains("o"))
    }

    // ---- unavailable paths: every measured-entry function given nothing to measure ----

    @Test
    fun contrastAndStressAndRoundnessAreUnavailableWithoutAnOGlyph() {
        assertUnavailable(contrastEntry(null))
        assertUnavailable(stressEntry(null))
        assertUnavailable(roundnessEntry(null))
    }

    @Test
    fun serifIsUnavailableWithoutATGlyph() {
        val entry = assertUnavailable(serifEntry(null))
        assertTrue(entry.isHeuristic)
    }

    @Test
    fun storeysIsUnavailableWithoutAOrG() {
        val entry = assertUnavailable(storeysEntry(null, null))
        assertFalse(entry.isHeuristic)
    }

    @Test
    fun terminalIsUnavailableWithoutAGlyph() {
        assertUnavailable(terminalEntry(null))
    }

    @Test
    fun apertureIsUnavailableWithoutAGlyphOrWithoutAPositiveXHeight() {
        val triangle = Glyph("c", 500, listOf(triangle(0 to 0, 300 to 0, 150 to 300)))
        assertUnavailable(apertureEntry(null, 500.0))
        assertUnavailable(apertureEntry(triangle, null))
        assertUnavailable(apertureEntry(triangle, 0.0))
        assertUnavailable(apertureEntry(triangle, -10.0))
    }

    @Test
    fun xHeightToCapHeightRatioIsUnavailableWithoutBothGlyphs() {
        val box = Glyph("x", 400, listOf(rectangle(0, 0, 380, 500)))
        assertUnavailable(xHeightToCapHeightRatioEntry(null, box))
        assertUnavailable(xHeightToCapHeightRatioEntry(box, null))
    }

    @Test
    fun xHeightAndCapHeightEntriesFallBackToAPositiveOs2ValueWhenTheGlyphIsMissing() {
        val fromOs2 = assertIs<AnatomyLensEntry.Measured>(xHeightEntry(null, 512))
        assertEquals(AnatomyLensValue.FontUnits(512), fromOs2.value)
        assertUnavailable(xHeightEntry(null, null))
        assertUnavailable(xHeightEntry(null, 0))
        assertUnavailable(xHeightEntry(null, -5))

        val capFromOs2 = assertIs<AnatomyLensEntry.Measured>(capHeightEntry(null, 712))
        assertEquals(AnatomyLensValue.FontUnits(712), capFromOs2.value)
        assertUnavailable(capHeightEntry(null, null))
    }

    @Test
    fun xHeightEntryPrefersTheGlyphsOwnInkHeightOverAPresentOs2Value() {
        val box = Glyph("x", 400, listOf(rectangle(0, 0, 380, 480)))
        val entry = assertIs<AnatomyLensEntry.Measured>(xHeightEntry(box, os2XHeight = 999))
        assertEquals(AnatomyLensValue.FontUnits(480), entry.value, "the drawn glyph's own ink height must win over a present OS/2 value")
    }

    @Test
    fun ascenderAndDescenderAreUnavailableWithoutFontMetrics() {
        assertUnavailable(ascenderEntry(null))
        assertUnavailable(descenderEntry(null))
    }

    // ---- SerifKind: this file's own threshold logic on hand-built T-shaped glyphs ----

    @Test
    fun sansStemWithNoFlareIsSerifKindNone() {
        val entry = assertIs<AnatomyLensEntry.Measured>(serifEntry(sansTGlyph()))
        val value = assertIs<AnatomyLensValue.SerifShape>(entry.value)
        assertFalse(value.hasSerif)
        assertNull(value.bracketScore)
        assertEquals(SerifKind.NONE, value.kind)
    }

    @Test
    fun aGraduallyTaperedFootIsSerifKindBracketed() {
        val entry = assertIs<AnatomyLensEntry.Measured>(serifEntry(serifTGlyph(gradualSerifProfile(overhang = 40, rise = 140))))
        val value = assertIs<AnatomyLensValue.SerifShape>(entry.value)
        assertTrue(value.hasSerif)
        val bracketScore = assertNotNull(value.bracketScore)
        assertEquals(SerifKind.BRACKETED, value.kind, "bracketScore was $bracketScore")
    }

    @Test
    fun anAbruptlyTaperedFootIsSerifKindUnbracketed() {
        val entry = assertIs<AnatomyLensEntry.Measured>(serifEntry(serifTGlyph(abruptSerifProfile(overhang = 40, rise = 140))))
        val value = assertIs<AnatomyLensValue.SerifShape>(entry.value)
        assertTrue(value.hasSerif)
        val bracketScore = assertNotNull(value.bracketScore)
        assertEquals(SerifKind.UNBRACKETED, value.kind, "bracketScore was $bracketScore")
    }

    // ---- helpers ----

    /** [term]'s definition/occursOn, read via [definitionOnlyEntry] -- valid for *any* term (measured or not), since that function only ever reads [ANATOMY_TERM_INFO] and never touches a glyph. */
    private fun definitionOnlyOrMeasuredInfo(term: AnatomyTerm): InfoLike {
        val entry = definitionOnlyEntry(term)
        return InfoLike(entry.definition, entry.occursOn)
    }

    private data class InfoLike(
        val definition: String,
        val occursOn: List<String>,
    )

    private fun assertUnavailable(entry: AnatomyLensEntry): AnatomyLensEntry.Measured {
        val measured = assertIs<AnatomyLensEntry.Measured>(entry)
        assertNull(measured.value)
        assertNotNull(measured.unavailableReason)
        return measured
    }

    private fun rectangle(
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
    ): Contour = polygon(x0 to y0, x1 to y0, x1 to y1, x0 to y1)

    private fun triangle(
        a: Pair<Int, Int>,
        b: Pair<Int, Int>,
        c: Pair<Int, Int>,
    ): Contour = polygon(a, b, c)

    private fun polygon(vararg pts: Pair<Int, Int>): Contour =
        Contour(pts.map { (x, y) -> ContourPoint(Point(x, y), onCurve = true) }, CurveFormat.QUADRATIC)

    /** A plain sans stem T, no serif -- same shape `qa/corpus`'s own `SyntheticGlyphs.sansTGlyph` uses (rebuilt here, see this file's own KDoc for why). */
    private fun sansTGlyph(
        capHeight: Int = 700,
        stemWidth: Int = 90,
        barWidth: Int = 500,
        barHeight: Int = 90,
    ): Glyph {
        val stemLeft = (barWidth - stemWidth) / 2
        val stemRight = stemLeft + stemWidth
        val barBottom = capHeight - barHeight
        val outline =
            polygon(
                stemLeft to 0,
                stemRight to 0,
                stemRight to barBottom,
                barWidth to barBottom,
                barWidth to capHeight,
                0 to capHeight,
                0 to barBottom,
                stemLeft to barBottom,
            )
        return Glyph("T", 560, listOf(outline))
    }

    /** A gradual, evenly-spread taper -- a bracketed-looking foot. Same shape `qa/corpus`'s own `gradualSerifProfile` builds. */
    private fun gradualSerifProfile(
        overhang: Int,
        rise: Int,
        steps: Int = 8,
    ): List<Pair<Int, Int>> = (0..steps).map { i -> (rise * i) / steps to overhang - (overhang * i) / steps }

    /** A near-step-function taper -- an unbracketed-looking foot. Same shape `qa/corpus`'s own `abruptSerifProfile` builds. */
    private fun abruptSerifProfile(
        overhang: Int,
        rise: Int,
        dropFraction: Double = 0.15,
    ): List<Pair<Int, Int>> {
        val dropStart = (rise * (1.0 - dropFraction)).roundToInt()
        return listOf(0 to overhang, dropStart to overhang, rise to 0)
    }

    /** A serif T flared per [profile] at the foot -- same construction `qa/corpus`'s own `serifTGlyph` uses. */
    private fun serifTGlyph(
        profile: List<Pair<Int, Int>>,
        capHeight: Int = 700,
        stemWidth: Int = 90,
        barWidth: Int = 500,
        barHeight: Int = 90,
    ): Glyph {
        val stemLeft = (barWidth - stemWidth) / 2
        val stemRight = stemLeft + stemWidth
        val barBottom = capHeight - barHeight
        val rightSide = profile.map { (y, overhang) -> (stemRight + overhang) to y }
        val leftSide = profile.map { (y, overhang) -> (stemLeft - overhang) to y }
        val outline =
            polygon(
                *leftSide.reversed().toTypedArray(),
                *rightSide.toTypedArray(),
                stemRight to barBottom,
                barWidth to barBottom,
                barWidth to capHeight,
                0 to capHeight,
                0 to barBottom,
                stemLeft to barBottom,
            )
        return Glyph("T", 560, listOf(outline))
    }
}
