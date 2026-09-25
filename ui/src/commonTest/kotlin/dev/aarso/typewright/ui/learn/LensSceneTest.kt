// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.ui.learn

import dev.aarso.typewright.core.geometry.Contour
import dev.aarso.typewright.core.geometry.ContourPoint
import dev.aarso.typewright.core.geometry.CurveFormat
import dev.aarso.typewright.core.geometry.Glyph
import dev.aarso.typewright.core.geometry.Point
import dev.aarso.typewright.qa.corpus.style.Storeys
import dev.aarso.typewright.qa.corpus.style.TerminalStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Portable (JVM + Wasm/Node) unit tests for [LensScene.kt]'s pure term-selection logic: which real
 * glyph each term's leader line is drawn on ([heroGlyphFor], [termsSharingHeroChar]), where the
 * leader line's target point lands in font units ([lensSceneFor]), and the real-value formatting
 * shown in the definitions list ([formatAnatomyLensValue], [heuristicCaveat],
 * [unavailableReasonOf], [lensValueLine]). Uses small hand-built synthetic glyphs, the same style
 * [AnatomyLensDataTest] already uses for the sibling data-API file, so none of this needs a real
 * font off disk (the real-Hyle-Deco cross-check lives in `desktopTest`'s `LensTabHyleDecoTest`).
 * [LensRenderPlan.kt]'s own pixel-space math is tested separately in `LensRenderPlanTest`.
 */
class LensSceneTest {
    // ---- LENS_TAB_TERMS: the curated set itself ----

    @Test
    fun lensTabTermsStartsWithTheExplorersOwnFourTermWorkedExampleInOrder() {
        assertEquals(
            listOf(AnatomyTerm.BOWL, AnatomyTerm.COUNTER, AnatomyTerm.STEM, AnatomyTerm.TERMINAL),
            LENS_TAB_TERMS.take(4),
        )
    }

    @Test
    fun lensTabTermsHasNoDuplicatesAndAtLeastSixTerms() {
        assertEquals(LENS_TAB_TERMS.toSet().size, LENS_TAB_TERMS.size, "no term should appear twice")
        assertTrue(LENS_TAB_TERMS.size >= 6, "expected at least 6 wired terms, found ${LENS_TAB_TERMS.size}")
    }

    @Test
    fun everyLensTabTermHasARealHeroGlyphWhenTheFullGlyphSetIsPresent() {
        val glyphSet = fullSyntheticGlyphSet()
        for (term in LENS_TAB_TERMS) {
            assertNotNull(heroGlyphFor(term, glyphSet), "$term has no hero glyph even though its own candidate char(s) are present")
        }
    }

    // ---- heroGlyphFor: real glyph choice per term, with fallback preference ----

    @Test
    fun heroGlyphForPicksEachTermsOwnRealMeasurementGlyph() {
        val glyphSet = fullSyntheticGlyphSet()
        assertEquals('o', heroGlyphFor(AnatomyTerm.BOWL, glyphSet)?.first)
        assertEquals('o', heroGlyphFor(AnatomyTerm.COUNTER, glyphSet)?.first)
        assertEquals('o', heroGlyphFor(AnatomyTerm.CONTRAST, glyphSet)?.first)
        assertEquals('o', heroGlyphFor(AnatomyTerm.ROUNDNESS, glyphSet)?.first)
        assertEquals('n', heroGlyphFor(AnatomyTerm.STEM, glyphSet)?.first)
        assertEquals('T', heroGlyphFor(AnatomyTerm.SERIF, glyphSet)?.first)
        assertEquals('a', heroGlyphFor(AnatomyTerm.STOREYS, glyphSet)?.first)
    }

    @Test
    fun terminalAndApertureFallBackThroughCThenEThenS() {
        val cOnly = fullSyntheticGlyphSet().copy(glyphs = fullSyntheticGlyphSet().glyphs.filterKeys { it != 'e' && it != 's' })
        assertEquals('c', heroGlyphFor(AnatomyTerm.TERMINAL, cOnly)?.first)

        val eOnly = fullSyntheticGlyphSet().let { it.copy(glyphs = it.glyphs.filterKeys { ch -> ch != 'c' && ch != 's' }) }
        assertEquals('e', heroGlyphFor(AnatomyTerm.TERMINAL, eOnly)?.first)

        val sOnly = fullSyntheticGlyphSet().let { it.copy(glyphs = it.glyphs.filterKeys { ch -> ch != 'c' && ch != 'e' }) }
        assertEquals('s', heroGlyphFor(AnatomyTerm.APERTURE, sOnly)?.first)
    }

    @Test
    fun heroGlyphForIsNullWhenNoneOfATermsCandidateCharsArePresent() {
        val empty =
            AnatomyLensGlyphSet(
                unitsPerEm = 1000,
                glyphs = emptyMap(),
                ascender = null,
                descender = null,
                osXHeight = null,
                osCapHeight = null,
            )
        assertNull(heroGlyphFor(AnatomyTerm.STEM, empty))
        assertNull(heroGlyphFor(AnatomyTerm.TERMINAL, empty))
    }

    // ---- termsSharingHeroChar: the simultaneous-label grouping ----

    @Test
    fun oGroupsBowlCounterContrastAndRoundnessInLensTabTermsOrder() {
        val glyphSet = fullSyntheticGlyphSet()
        assertEquals(
            listOf(AnatomyTerm.BOWL, AnatomyTerm.COUNTER, AnatomyTerm.CONTRAST, AnatomyTerm.ROUNDNESS),
            termsSharingHeroChar(glyphSet, 'o'),
        )
    }

    @Test
    fun everyOtherHeroCharGroupsExactlyItsOwnTerm() {
        val glyphSet = fullSyntheticGlyphSet()
        assertEquals(listOf(AnatomyTerm.STEM), termsSharingHeroChar(glyphSet, 'n'))
        assertEquals(listOf(AnatomyTerm.SERIF), termsSharingHeroChar(glyphSet, 'T'))
        assertEquals(listOf(AnatomyTerm.STOREYS), termsSharingHeroChar(glyphSet, 'a'))
        assertEquals(listOf(AnatomyTerm.TERMINAL, AnatomyTerm.APERTURE), termsSharingHeroChar(glyphSet, 'c'))
    }

    // ---- lensSceneFor: real entry + real leader-line target math ----

    @Test
    fun lensSceneForComputesTheLeaderTargetAsTheDocumentedFractionOfTheHeroGlyphsRealInkBounds() {
        val glyphSet = fullSyntheticGlyphSet()
        // STEM's own anchor fraction is (0.14, 0.32) of the hero glyph's ink bounds -- 'n' here is
        // a plain 0..400 x 0..700 rectangle, so the target is exact, simple arithmetic.
        val scene = lensSceneFor(AnatomyTerm.STEM, glyphSet)
        assertEquals('n', scene.heroChar)
        val target = assertNotNull(scene.leaderTargetFontUnits, "STEM's leader target should be computable from a real rectangle 'n'")
        assertEquals(0.14 * 400.0, target.x, 1e-9)
        assertEquals(0.32 * 700.0, target.y, 1e-9)
    }

    @Test
    fun lensSceneForHasNoHeroGlyphOrLeaderTargetWhenTheFontIsMissingIt() {
        val glyphSet = fullSyntheticGlyphSet().let { it.copy(glyphs = it.glyphs.filterKeys { ch -> ch != 'n' }) }
        val scene = lensSceneFor(AnatomyTerm.STEM, glyphSet)
        assertNull(scene.heroChar)
        assertNull(scene.heroGlyph)
        assertNull(scene.leaderTargetFontUnits)
        // The definition itself is still real and present -- anatomyLensEntry's own "no glyph, no
        // guess" unavailable path, not a crash.
        val entry = assertIs<AnatomyLensEntry.DefinitionOnly>(scene.entry)
        assertEquals(AnatomyTerm.STEM, entry.term)
        assertTrue(entry.definition.isNotBlank())
    }

    @Test
    fun lensSceneForNeverThrowsForAnyCuratedTermOnTheFullSyntheticGlyphSet() {
        val glyphSet = fullSyntheticGlyphSet()
        for (term in LENS_TAB_TERMS) {
            val scene = lensSceneFor(term, glyphSet)
            assertEquals(term, scene.term)
            assertEquals(term, scene.entry.term)
        }
    }

    // ---- formatAnatomyLensValue: every AnatomyLensValue variant ----

    @Test
    fun formatsRatioToTwoDecimals() {
        assertEquals("1.25", formatAnatomyLensValue(AnatomyLensValue.Ratio(1.2513)))
    }

    @Test
    fun formatsDegreesFromVertical() {
        assertEquals("52.5° from vertical", formatAnatomyLensValue(AnatomyLensValue.DegreesFromVertical(52.51)))
    }

    @Test
    fun formatsFontUnits() {
        assertEquals("700 units", formatAnatomyLensValue(AnatomyLensValue.FontUnits(700)))
    }

    @Test
    fun formatsStoreyCount() {
        assertEquals("single storey", formatAnatomyLensValue(AnatomyLensValue.StoreyCount(Storeys.SINGLE)))
        assertEquals("double storey", formatAnatomyLensValue(AnatomyLensValue.StoreyCount(Storeys.DOUBLE)))
        assertEquals("storey count unknown", formatAnatomyLensValue(AnatomyLensValue.StoreyCount(Storeys.UNKNOWN)))
    }

    @Test
    fun formatsTerminalShape() {
        assertEquals("flat terminal", formatAnatomyLensValue(AnatomyLensValue.TerminalShape(TerminalStyle.FLAT)))
        assertEquals("round terminal", formatAnatomyLensValue(AnatomyLensValue.TerminalShape(TerminalStyle.ROUND)))
        assertEquals("angled terminal", formatAnatomyLensValue(AnatomyLensValue.TerminalShape(TerminalStyle.ANGLED)))
    }

    @Test
    fun formatsSerifShape() {
        assertEquals(
            "no serif",
            formatAnatomyLensValue(AnatomyLensValue.SerifShape(hasSerif = false, bracketScore = null, kind = SerifKind.NONE)),
        )
        assertEquals(
            "bracketed serif",
            formatAnatomyLensValue(AnatomyLensValue.SerifShape(hasSerif = true, bracketScore = 0.8, kind = SerifKind.BRACKETED)),
        )
    }

    // ---- heuristicCaveat / unavailableReasonOf / lensValueLine ----

    @Test
    fun heuristicCaveatIsOnlyShownForAMeasuredHeuristicEntryThatActuallyHasAValue() {
        val heuristicWithValue =
            AnatomyLensEntry.Measured(
                AnatomyTerm.TERMINAL,
                "def",
                listOf("c"),
                AnatomyLensValue.TerminalShape(TerminalStyle.FLAT),
                isHeuristic = true,
            )
        assertEquals("our heuristic", heuristicCaveat(heuristicWithValue))

        val nonHeuristicWithValue =
            AnatomyLensEntry.Measured(AnatomyTerm.CONTRAST, "def", listOf("o"), AnatomyLensValue.Ratio(1.2), isHeuristic = false)
        assertNull(heuristicCaveat(nonHeuristicWithValue))

        val heuristicNoValue =
            AnatomyLensEntry.Measured(
                AnatomyTerm.TERMINAL,
                "def",
                listOf("c"),
                null,
                isHeuristic = true,
                unavailableReason = "no glyph",
            )
        assertNull(heuristicCaveat(heuristicNoValue), "no value means no caveat to attach it to")

        assertNull(heuristicCaveat(AnatomyLensEntry.DefinitionOnly(AnatomyTerm.STEM, "def", listOf("n"))))
    }

    @Test
    fun unavailableReasonOfOnlyReadsAMeasuredEntryWithNoValue() {
        val unavailable =
            AnatomyLensEntry.Measured(
                AnatomyTerm.TERMINAL,
                "def",
                listOf("c"),
                null,
                isHeuristic = true,
                unavailableReason = "no c/e/s",
            )
        assertEquals("no c/e/s", unavailableReasonOf(unavailable))

        val available =
            AnatomyLensEntry.Measured(
                AnatomyTerm.CONTRAST,
                "def",
                listOf("o"),
                AnatomyLensValue.Ratio(1.2),
                isHeuristic = false,
            )
        assertNull(unavailableReasonOf(available))

        assertNull(unavailableReasonOf(AnatomyLensEntry.DefinitionOnly(AnatomyTerm.STEM, "def", listOf("n"))))
    }

    @Test
    fun lensValueLineIsNullForADefinitionOnlyTermAndCombinesValueAndCaveatForAMeasuredOne() {
        assertNull(lensValueLine(AnatomyLensEntry.DefinitionOnly(AnatomyTerm.STEM, "def", listOf("n"))))

        val plain = AnatomyLensEntry.Measured(AnatomyTerm.CONTRAST, "def", listOf("o"), AnatomyLensValue.Ratio(1.25), isHeuristic = false)
        assertEquals("1.25", lensValueLine(plain))

        val heuristic =
            AnatomyLensEntry.Measured(
                AnatomyTerm.TERMINAL,
                "def",
                listOf("c"),
                AnatomyLensValue.TerminalShape(TerminalStyle.FLAT),
                isHeuristic = true,
            )
        assertEquals("flat terminal · our heuristic", lensValueLine(heuristic))

        val unavailable =
            AnatomyLensEntry.Measured(
                AnatomyTerm.TERMINAL,
                "def",
                listOf("c"),
                null,
                isHeuristic = true,
                unavailableReason = "no c/e/s",
            )
        assertEquals("not measured: no c/e/s", lensValueLine(unavailable))
    }

    // ---- synthetic glyph set ----

    private fun rectangle(
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
    ): Contour {
        val pts = listOf(x0 to y0, x1 to y0, x1 to y1, x0 to y1)
        return Contour(pts.map { (x, y) -> ContourPoint(Point(x, y), onCurve = true) }, CurveFormat.QUADRATIC)
    }

    private fun rectGlyph(
        name: String,
        width: Int,
        height: Int,
    ): Glyph = Glyph(name, width, listOf(rectangle(0, 0, width, height)))

    /** One rectangle glyph per lens character (`onageHTcsx`), each with a distinct, plausible font-unit size -- enough for [heroGlyphFor]/[termsSharingHeroChar]/[lensSceneFor]'s own wiring and arithmetic; the real anatomical correctness of what each `qa/corpus` function returns on a shape this simple is [AnatomyLensDataTest]'s own job, not this file's. */
    private fun fullSyntheticGlyphSet(): AnatomyLensGlyphSet =
        AnatomyLensGlyphSet(
            unitsPerEm = 1000,
            glyphs =
                mapOf(
                    'o' to rectGlyph("o", 480, 500),
                    'n' to rectGlyph("n", 400, 700),
                    'a' to rectGlyph("a", 440, 500),
                    'g' to rectGlyph("g", 440, 700),
                    'e' to rectGlyph("e", 440, 500),
                    'H' to rectGlyph("H", 500, 700),
                    'T' to rectGlyph("T", 560, 700),
                    'c' to rectGlyph("c", 420, 500),
                    's' to rectGlyph("s", 400, 500),
                    'x' to rectGlyph("x", 420, 500),
                ),
            ascender = 800,
            descender = -200,
            osXHeight = 500,
            osCapHeight = 700,
        )
}
