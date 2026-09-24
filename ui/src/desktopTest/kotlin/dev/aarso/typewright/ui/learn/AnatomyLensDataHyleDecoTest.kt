package dev.aarso.typewright.ui.learn

import dev.aarso.typewright.core.font.sfnt.readSfntFont
import dev.aarso.typewright.qa.corpus.style.Storeys
import dev.aarso.typewright.qa.corpus.style.TerminalStyle
import dev.aarso.typewright.qa.corpus.style.apertureOpenness
import dev.aarso.typewright.qa.corpus.style.contrastRatio
import dev.aarso.typewright.qa.corpus.style.inkBounds
import dev.aarso.typewright.qa.corpus.style.outerContour
import dev.aarso.typewright.qa.corpus.style.serifMetrics
import dev.aarso.typewright.qa.corpus.style.storeysFromA
import dev.aarso.typewright.qa.corpus.style.storeysFromG
import dev.aarso.typewright.qa.corpus.style.stressAngleDegrees
import dev.aarso.typewright.qa.corpus.style.superellipseExponent
import dev.aarso.typewright.qa.corpus.style.terminalStyle
import dev.aarso.typewright.qa.corpus.style.xHeightToCapHeightRatio
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The honesty check this task's own instructions ask for: every measured [AnatomyLensData]
 * function, run against `fonts/HyleDeco-Regular.ttf` (this build's own real test-fixture font --
 * "the user's own letter" substitute, see this file's own module KDoc), and cross-checked in the
 * *same test* against a direct call to the exact `qa/corpus` function it wires -- a real
 * regression check, not a fresh, disconnected assertion: if this file's wiring ever diverged from
 * `qa/corpus`'s own real function (a stale cache, a copy-paste of the wrong glyph, an off-by-one in
 * the dispatcher), the direct-call side of the assertion would still report the true value and the
 * test would fail. JVM-only (`desktopTest`), the same reason `core-geometry`'s
 * `CubicFittingHyleDecoValidationTest` and `qa:corpus`'s `StyleDetectorRealFontValidationTest` are:
 * reading an arbitrary file off disk during a Gradle test run.
 *
 * `qa:corpus`'s own commonTest suite for these same functions (`ContrastStressTest`,
 * `SerifBracketTest`, `StoreysTest`, `TerminalTest`, `ApertureTest`, `RoundnessTest`,
 * `ProportionsTest`) tests them on synthetic glyphs with geometrically-obvious expected values, per
 * P1b's own instruction -- none of them load `fonts/HyleDeco-Regular.ttf`, so there is no existing
 * "qa/corpus already asserts Hyle Deco's contrast is X" fixture to match against directly (checked
 * before writing this file: `grep -rl HyleDeco qa/corpus/src` finds only
 * `HyleDecoNodeEconomyTest.kt`, node-economy, and `SyntheticGlyphs.kt`, an unrelated comment).
 * Logged as an open question. This file's own real-vs-real numbers below were read once via a
 * throwaway exploration test against the same functions (deleted before this commit) and are
 * pinned here as the actual, reproducible answer -- run this test yourself to regenerate them if
 * `fonts/HyleDeco-Regular.ttf` is ever redrawn.
 */
class AnatomyLensDataHyleDecoTest {
    private val font by lazy { readSfntFont(File("../fonts/HyleDeco-Regular.ttf").readBytes()) }
    private val glyphSet by lazy { AnatomyLensGlyphSet.fromSfntFont(font) }

    private val o get() = font.glyphForCodePoint('o'.code)
    private val a get() = font.glyphForCodePoint('a'.code)
    private val g get() = font.glyphForCodePoint('g'.code)
    private val t get() = font.glyphForCodePoint('T'.code)
    private val c get() = font.glyphForCodePoint('c'.code)
    private val e get() = font.glyphForCodePoint('e'.code)
    private val s get() = font.glyphForCodePoint('s'.code)
    private val x get() = font.glyphForCodePoint('x'.code)
    private val h get() = font.glyphForCodePoint('H'.code)

    // ---- unitsPerEm / OS2 sanity: what this test's other assertions assume about the fixture ----

    @Test
    fun hyleDecoIs1000UpmWithFourGlyphsInTheGlyphSet() {
        assertEquals(1000, glyphSet.unitsPerEm)
        assertEquals(setOf('o', 'n', 'a', 'g', 'e', 'H', 'T', 'c', 's', 'x'), glyphSet.glyphs.keys)
    }

    // ---- contrast / stress (ContrastStress.kt, on 'o') ----

    @Test
    fun contrastEntryMatchesContrastRatioDirectly() {
        val direct = assertNotNull(contrastRatio(assertNotNull(o)))
        val entry = assertIs<AnatomyLensEntry.Measured>(contrastEntry(o))
        val value = assertIs<AnatomyLensValue.Ratio>(entry.value)
        assertEquals(direct, value.value)
        assertEquals(direct, assertRatio(anatomyLensEntry(AnatomyTerm.CONTRAST, glyphSet)))
        assertFalse(entry.isHeuristic)
        // Real measured value on this build's own fixture, pinned so a future change is visible.
        assertEquals(1.2513158653716447, direct, 1e-9)
    }

    @Test
    fun stressEntryMatchesStressAngleDegreesDirectly() {
        val direct = assertNotNull(stressAngleDegrees(assertNotNull(o)))
        val entry = assertIs<AnatomyLensEntry.Measured>(stressEntry(o))
        val value = assertIs<AnatomyLensValue.DegreesFromVertical>(entry.value)
        assertEquals(direct, value.value)
        assertEquals(direct, assertDegrees(anatomyLensEntry(AnatomyTerm.STRESS, glyphSet)))
        assertFalse(entry.isHeuristic)
        assertEquals(52.5, direct, 1e-9)
    }

    // ---- roundness (Roundness.kt, on 'o') ----

    @Test
    fun roundnessEntryMatchesSuperellipseExponentDirectly() {
        val direct = assertNotNull(superellipseExponent(assertNotNull(o)))
        val entry = assertIs<AnatomyLensEntry.Measured>(roundnessEntry(o))
        val value = assertIs<AnatomyLensValue.Ratio>(entry.value)
        assertEquals(direct, value.value)
        assertEquals(direct, assertRatio(anatomyLensEntry(AnatomyTerm.ROUNDNESS, glyphSet)))
        assertFalse(entry.isHeuristic)
        assertEquals(4.199999999999999, direct, 1e-9)
    }

    // ---- serif (SerifBracket.kt, on 'T') ----

    @Test
    fun serifEntryMatchesSerifMetricsDirectly_hyleDecoTIsSansSoNoSerif() {
        val direct = assertNotNull(serifMetrics(assertNotNull(t)))
        val entry = assertIs<AnatomyLensEntry.Measured>(serifEntry(t))
        val value = assertIs<AnatomyLensValue.SerifShape>(entry.value)
        assertEquals(direct.hasSerif, value.hasSerif)
        assertEquals(direct.bracketScore, value.bracketScore)
        assertTrue(entry.isHeuristic)
        // Hyle Deco's T is the sans stem+crossbar CLAUDE.md's own fixture describes (no serif).
        assertFalse(direct.hasSerif)
        assertNull(direct.bracketScore)
        assertEquals(SerifKind.NONE, value.kind)
        val dispatched = assertIs<AnatomyLensEntry.Measured>(anatomyLensEntry(AnatomyTerm.SERIF, glyphSet))
        assertEquals(value, dispatched.value)
    }

    // ---- storeys (Storeys.kt, on 'a'/'g') ----

    @Test
    fun storeysEntryPrefersGOverAAndMatchesStoreysFromGDirectly() {
        val directA = assertNotNull(a).let { storeysFromA(it) }
        val directG = assertNotNull(g).let { storeysFromG(it) }
        // Real quirk of this fixture, worth pinning: 'a' and 'g' disagree on storey construction.
        assertEquals(Storeys.DOUBLE, directA)
        assertEquals(Storeys.SINGLE, directG)

        val entry = assertIs<AnatomyLensEntry.Measured>(storeysEntry(a, g))
        val value = assertIs<AnatomyLensValue.StoreyCount>(entry.value)
        assertEquals(directG, value.storeys, "storeysEntry should prefer g's (topological, non-heuristic) answer over a's")
        assertFalse(entry.isHeuristic)
        val dispatched = assertIs<AnatomyLensEntry.Measured>(anatomyLensEntry(AnatomyTerm.STOREYS, glyphSet))
        assertEquals(value, dispatched.value)
    }

    @Test
    fun storeysEntryFallsBackToAWhenGIsAbsentAndFlagsHeuristic() {
        val directA = assertNotNull(a).let { storeysFromA(it) }
        val entry = assertIs<AnatomyLensEntry.Measured>(storeysEntry(a, g = null))
        val value = assertIs<AnatomyLensValue.StoreyCount>(entry.value)
        assertEquals(directA, value.storeys)
        assertTrue(entry.isHeuristic, "the a-derived answer is qa/corpus's own heuristic (law 5), so this must be flagged")
    }

    // ---- terminal (Terminal.kt, on 'c'/'e'/'s') ----

    @Test
    fun terminalEntryOnCMatchesTerminalStyleDirectly() {
        val cGlyph = assertNotNull(c)
        val outer = assertNotNull(cGlyph.outerContour())
        val direct = terminalStyle(cGlyph, outer)
        val entry = assertIs<AnatomyLensEntry.Measured>(terminalEntry(c))
        val value = assertIs<AnatomyLensValue.TerminalShape>(entry.value)
        assertEquals(direct, value.style)
        assertTrue(entry.isHeuristic)
        assertEquals(TerminalStyle.FLAT, direct)
        val dispatched = assertIs<AnatomyLensEntry.Measured>(anatomyLensEntry(AnatomyTerm.TERMINAL, glyphSet))
        assertEquals(value, dispatched.value)
    }

    @Test
    fun terminalEntryAgreesOnEAndS() {
        for (glyph in listOf(e, s)) {
            val real = assertNotNull(glyph)
            val outer = assertNotNull(real.outerContour())
            val direct = terminalStyle(real, outer)
            val value = assertIs<AnatomyLensValue.TerminalShape>(assertIs<AnatomyLensEntry.Measured>(terminalEntry(real)).value)
            assertEquals(direct, value.style, "terminal style for '${real.name}'")
            assertEquals(TerminalStyle.FLAT, direct, "terminal style for '${real.name}'")
        }
    }

    // ---- aperture (Aperture.kt, on 'c'/'e'/'s', against x's own ink height) ----

    @Test
    fun apertureEntryOnCMatchesApertureOpennessDirectly() {
        val cGlyph = assertNotNull(c)
        val xHeight = assertNotNull(assertNotNull(x).inkBounds()).maxY
        val direct = assertNotNull(apertureOpenness(cGlyph, xHeight))
        val entry = assertIs<AnatomyLensEntry.Measured>(apertureEntry(c, xHeight))
        val value = assertIs<AnatomyLensValue.Ratio>(entry.value)
        assertEquals(direct, value.value)
        assertTrue(entry.isHeuristic)
        assertEquals(0.352, direct, 1e-9)
        val dispatched = assertIs<AnatomyLensEntry.Measured>(anatomyLensEntry(AnatomyTerm.APERTURE, glyphSet))
        assertEquals(
            value,
            dispatched.value,
            "the dispatcher must derive the same x-height ('x' ink bounds) apertureEntry was given directly above",
        )
    }

    // ---- x-height to cap-height ratio (Proportions.kt, on 'x'/'H') ----

    @Test
    fun xHeightToCapHeightRatioEntryMatchesDirectly() {
        val direct = assertNotNull(xHeightToCapHeightRatio(x, h))
        val entry = assertIs<AnatomyLensEntry.Measured>(xHeightToCapHeightRatioEntry(x, h))
        val value = assertIs<AnatomyLensValue.Ratio>(entry.value)
        assertEquals(direct, value.value)
        assertFalse(entry.isHeuristic)
        assertEquals(500.0 / 700.0, direct, 1e-9)
        val dispatched = assertIs<AnatomyLensEntry.Measured>(anatomyLensEntry(AnatomyTerm.X_HEIGHT_TO_CAP_HEIGHT_RATIO, glyphSet))
        assertEquals(value, dispatched.value)
    }

    // ---- x-height / cap-height / ascender / descender: core-font's own SfntFont tables ----

    @Test
    fun hyleDecosOwnOs2TableDoesNotMatchItsDrawnInkHeights() {
        // The real, disclosed data-quality finding xHeightEntry's own KDoc documents and this
        // test pins: OS/2's declared sxHeight/sCapHeight do not match what Hyle Deco actually
        // draws, which is exactly why the glyph's own ink height has to be primary (CLAUDE.md law
        // 1) and OS/2 only a fallback.
        assertEquals(0, font.os2?.sxHeight)
        assertEquals(500, font.os2?.sCapHeight)
        assertEquals(500, assertNotNull(assertNotNull(x).inkBounds()).maxY.toInt())
        assertEquals(700, assertNotNull(assertNotNull(h).inkBounds()).maxY.toInt())
    }

    @Test
    fun xHeightEntryPrefersDrawnInkOverTheMismatchedOs2Field() {
        val entry = assertIs<AnatomyLensEntry.Measured>(xHeightEntry(x, glyphSet.osXHeight))
        val value = assertIs<AnatomyLensValue.FontUnits>(entry.value)
        assertEquals(500, value.value, "must read x's own drawn ink height, not OS/2's sxHeight=0")
        assertFalse(entry.isHeuristic)
        val dispatched = assertIs<AnatomyLensEntry.Measured>(anatomyLensEntry(AnatomyTerm.X_HEIGHT, glyphSet))
        assertEquals(value, dispatched.value)
    }

    @Test
    fun capHeightEntryPrefersDrawnInkOverTheMismatchedOs2Field() {
        val entry = assertIs<AnatomyLensEntry.Measured>(capHeightEntry(h, glyphSet.osCapHeight))
        val value = assertIs<AnatomyLensValue.FontUnits>(entry.value)
        assertEquals(700, value.value, "must read H's own drawn ink height (700), not OS/2's sCapHeight=500")
        assertFalse(entry.isHeuristic)
        val dispatched = assertIs<AnatomyLensEntry.Measured>(anatomyLensEntry(AnatomyTerm.CAP_HEIGHT, glyphSet))
        assertEquals(value, dispatched.value)
    }

    @Test
    fun xHeightEntryFallsBackToOs2WhenTheGlyphItselfIsMissing() {
        val entry = assertIs<AnatomyLensEntry.Measured>(xHeightEntry(x = null, os2XHeight = 480))
        val value = assertIs<AnatomyLensValue.FontUnits>(entry.value)
        assertEquals(480, value.value)
    }

    @Test
    fun xHeightEntryIsUnavailableWhenBothTheGlyphAndAPositiveOs2ValueAreMissing() {
        val entry = assertIs<AnatomyLensEntry.Measured>(xHeightEntry(x = null, os2XHeight = 0))
        assertNull(entry.value)
        assertNotNull(entry.unavailableReason)
    }

    @Test
    fun ascenderAndDescenderEntriesMatchHheaDirectly() {
        assertEquals(984, font.hhea.ascender)
        assertEquals(-292, font.hhea.descender)
        val ascenderValue =
            assertIs<AnatomyLensValue.FontUnits>(assertIs<AnatomyLensEntry.Measured>(ascenderEntry(glyphSet.ascender)).value)
        assertEquals(984, ascenderValue.value)
        val descenderValue =
            assertIs<AnatomyLensValue.FontUnits>(assertIs<AnatomyLensEntry.Measured>(descenderEntry(glyphSet.descender)).value)
        assertEquals(-292, descenderValue.value)
        assertEquals(ascenderValue, assertRatioLikeFontUnits(anatomyLensEntry(AnatomyTerm.ASCENDER, glyphSet)))
        assertEquals(descenderValue, assertRatioLikeFontUnits(anatomyLensEntry(AnatomyTerm.DESCENDER, glyphSet)))
    }

    // ---- every AnatomyTerm resolves through the dispatcher without throwing ----

    @Test
    fun everyAnatomyTermResolvesThroughTheDispatcher() {
        for (term in AnatomyTerm.entries) {
            val entry = anatomyLensEntry(term, glyphSet)
            assertEquals(term, entry.term)
            assertTrue(entry.definition.isNotBlank(), "$term has a blank definition")
            assertTrue(entry.occursOn.isNotEmpty(), "$term has an empty occursOn list")
        }
    }

    @Test
    fun definitionOnlyTermsAreExactlyTheSeventeenStructuralTerms() {
        val definitionOnly =
            AnatomyTerm.entries.filter { anatomyLensEntry(it, glyphSet) is AnatomyLensEntry.DefinitionOnly }.toSet()
        val expected =
            setOf(
                AnatomyTerm.STEM,
                AnatomyTerm.BOWL,
                AnatomyTerm.COUNTER,
                AnatomyTerm.SPUR,
                AnatomyTerm.EAR,
                AnatomyTerm.LINK,
                AnatomyTerm.LOOP,
                AnatomyTerm.CROSSBAR,
                AnatomyTerm.ARM,
                AnatomyTerm.LEG,
                AnatomyTerm.SHOULDER,
                AnatomyTerm.SPINE,
                AnatomyTerm.APEX,
                AnatomyTerm.VERTEX,
                AnatomyTerm.TAIL,
                AnatomyTerm.TITTLE,
                AnatomyTerm.OVERSHOOT,
            )
        assertEquals(expected, definitionOnly)
    }

    private fun assertRatio(entry: AnatomyLensEntry): Double =
        assertIs<AnatomyLensValue.Ratio>(assertIs<AnatomyLensEntry.Measured>(entry).value).value

    private fun assertDegrees(entry: AnatomyLensEntry): Double =
        assertIs<AnatomyLensValue.DegreesFromVertical>(assertIs<AnatomyLensEntry.Measured>(entry).value).value

    private fun assertRatioLikeFontUnits(entry: AnatomyLensEntry): AnatomyLensValue.FontUnits =
        assertIs<AnatomyLensValue.FontUnits>(assertIs<AnatomyLensEntry.Measured>(entry).value)
}
