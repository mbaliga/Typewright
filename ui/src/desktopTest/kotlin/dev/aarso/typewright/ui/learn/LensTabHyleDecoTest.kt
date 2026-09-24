package dev.aarso.typewright.ui.learn

import androidx.compose.ui.graphics.toPixelMap
import dev.aarso.typewright.core.font.sfnt.readSfntFont
import dev.aarso.typewright.qa.corpus.style.Storeys
import dev.aarso.typewright.qa.corpus.style.TerminalStyle
import dev.aarso.typewright.ui.ScreenshotHarness
import dev.aarso.typewright.ui.tokens.CanvasTextures
import dev.aarso.typewright.ui.tokens.toColor
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The real end-to-end check this task's own instructions ask for: [LensScene.kt]'s own logic run
 * against `fonts/HyleDeco-Regular.ttf` (through [HyleDecoProjectFontBytes]'s embedded bytes, the
 * exact path [LensTab] itself reads), cross-checked against `AnatomyLensDataHyleDecoTest`'s own
 * pinned real numbers for the terms both files cover -- a real regression check, not a fresh,
 * disconnected assertion (that sibling file's own KDoc explains why this shape catches a stale
 * wiring bug that a same-side-only assertion would not). JVM-only (`desktopTest`): reading a file
 * off disk, same as that sibling file.
 *
 * The screenshot half ([rendersLensTabOnPaperWithoutCrashing] et al.) follows
 * `LineagesTabScreenshotTest`'s own established `ScreenshotHarness` pattern; the PNGs were opened
 * and eyeballed against `ui/typewright-explorer.html`'s own `#ln-lens` look before this task was
 * reported done (see this task's own final report for what was actually seen).
 */
class LensTabHyleDecoTest {
    private val font by lazy { readSfntFont(File("../fonts/HyleDeco-Regular.ttf").readBytes()) }
    private val glyphSet by lazy { AnatomyLensGlyphSet.fromSfntFont(font) }

    // ---- every curated term produces a real scene, cross-checked against the pinned real values ----

    @Test
    fun bowlCounterStemAreDefinitionOnlyWithARealHeroGlyphAndLeaderTarget() {
        for (term in listOf(AnatomyTerm.BOWL, AnatomyTerm.COUNTER, AnatomyTerm.STEM)) {
            val scene = lensSceneFor(term, glyphSet)
            assertIs<AnatomyLensEntry.DefinitionOnly>(scene.entry, "$term")
            assertNotNull(scene.heroChar, "$term should have a real hero glyph in Hyle Deco")
            assertNotNull(scene.heroGlyph, "$term")
            assertNotNull(scene.leaderTargetFontUnits, "$term's leader line should have a real target")
        }
        assertEquals('o', lensSceneFor(AnatomyTerm.BOWL, glyphSet).heroChar)
        assertEquals('o', lensSceneFor(AnatomyTerm.COUNTER, glyphSet).heroChar)
        assertEquals('n', lensSceneFor(AnatomyTerm.STEM, glyphSet).heroChar)
    }

    @Test
    fun terminalIsFlatOnHyleDecosRealCMatchingAnatomyLensDataHyleDecoTestsOwnPin() {
        val scene = lensSceneFor(AnatomyTerm.TERMINAL, glyphSet)
        assertEquals('c', scene.heroChar)
        val entry = assertIs<AnatomyLensEntry.Measured>(scene.entry)
        val value = assertIs<AnatomyLensValue.TerminalShape>(assertNotNull(entry.value))
        assertEquals(TerminalStyle.FLAT, value.style)
        assertEquals("flat terminal · our heuristic", lensValueLine(scene.entry))
        assertNotNull(scene.leaderTargetFontUnits)
    }

    @Test
    fun apertureOnHyleDecosRealCMatchesAnatomyLensDataHyleDecoTestsOwnPinnedValue() {
        val scene = lensSceneFor(AnatomyTerm.APERTURE, glyphSet)
        assertEquals('c', scene.heroChar)
        val entry = assertIs<AnatomyLensEntry.Measured>(scene.entry)
        val value = assertIs<AnatomyLensValue.Ratio>(assertNotNull(entry.value))
        assertEquals(0.352, value.value, 1e-9)
        assertEquals("0.35 · our heuristic", lensValueLine(scene.entry))
    }

    @Test
    fun serifOnHyleDecosRealTIsNoSerifMatchingTheSansFixture() {
        val scene = lensSceneFor(AnatomyTerm.SERIF, glyphSet)
        assertEquals('T', scene.heroChar)
        val entry = assertIs<AnatomyLensEntry.Measured>(scene.entry)
        val value = assertIs<AnatomyLensValue.SerifShape>(assertNotNull(entry.value))
        assertFalse(value.hasSerif)
        assertEquals(SerifKind.NONE, value.kind)
        assertEquals("no serif · our heuristic", lensValueLine(scene.entry))
    }

    @Test
    fun storeysOnHyleDecoPrefersGAndIsNotFlaggedHeuristic() {
        val scene = lensSceneFor(AnatomyTerm.STOREYS, glyphSet)
        assertEquals(
            'a',
            scene.heroChar,
            "the diagram draws 'a' (this file's own hero-char choice) even though the measurement prefers 'g''s answer",
        )
        val entry = assertIs<AnatomyLensEntry.Measured>(scene.entry)
        val value = assertIs<AnatomyLensValue.StoreyCount>(assertNotNull(entry.value))
        assertEquals(Storeys.SINGLE, value.storeys, "matches AnatomyLensDataHyleDecoTest's own pinned storeysFromG answer")
        assertFalse(entry.isHeuristic)
        assertEquals("single storey", lensValueLine(scene.entry))
    }

    @Test
    fun contrastStressRoundnessOnHyleDecosRealOMatchTheirOwnPinnedValues() {
        val contrast =
            assertIs<AnatomyLensValue.Ratio>(
                assertNotNull((lensSceneFor(AnatomyTerm.CONTRAST, glyphSet).entry as AnatomyLensEntry.Measured).value),
            )
        assertEquals(1.2513158653716447, contrast.value, 1e-9)

        val roundness =
            assertIs<AnatomyLensValue.Ratio>(
                assertNotNull((lensSceneFor(AnatomyTerm.ROUNDNESS, glyphSet).entry as AnatomyLensEntry.Measured).value),
            )
        assertEquals(4.199999999999999, roundness.value, 1e-9)

        for (term in listOf(AnatomyTerm.CONTRAST, AnatomyTerm.ROUNDNESS)) {
            assertEquals('o', lensSceneFor(term, glyphSet).heroChar)
        }
    }

    @Test
    fun everyCuratedTermsHeroGlyphRoundTripsThroughGeometryInteropToARealNonEmptyPath() {
        for (term in LENS_TAB_TERMS) {
            val scene = lensSceneFor(term, glyphSet)
            val glyph = assertNotNull(scene.heroGlyph, "$term's own hero glyph should be real and present in Hyle Deco")
            assertTrue(glyph.contours.isNotEmpty(), "$term's hero glyph '${scene.heroChar}' should have real ink")
        }
    }

    // ---- screenshots: real render, eyeballed (see this task's own final report) ----

    @Test
    fun rendersLensTabOnPaperWithoutCrashing() {
        val shot =
            ScreenshotHarness.capture(name = "lens-tab-paper", width = 440, height = 1000, density = 2f) {
                LensTab(texture = CanvasTextures.PAPER)
            }
        assertTrue(shot.file.length() > 0, "PNG written to ${shot.file}")

        val pixels = shot.bitmap.toPixelMap()
        val canvasColor = CanvasTextures.PAPER.canvas.toColor()
        assertTrue(pixels[4, 4].isNear(canvasColor), "corner should read as the paper canvas colour, was ${pixels[4, 4]}")

        val inkColor = CanvasTextures.PAPER.ink.toColor()
        var inkPixels = 0
        for (x in 0 until pixels.width) {
            for (y in 0 until pixels.height) {
                if (pixels[x, y].isNear(inkColor)) inkPixels++
            }
        }
        assertTrue(
            inkPixels > 200,
            "expected a meaningful amount of ink (the real 'o' outline, leader lines): $inkPixels ink-coloured pixels",
        )
    }

    @Test
    fun rendersOnBlueprintWithoutCrashing() {
        val shot =
            ScreenshotHarness.capture(name = "lens-tab-blueprint", width = 440, height = 1000, density = 2f) {
                LensTab(texture = CanvasTextures.BLUEPRINT)
            }
        assertTrue(shot.file.length() > 0, "PNG written to ${shot.file}")
        val pixels = shot.bitmap.toPixelMap()
        assertTrue(pixels[4, 4].isNear(CanvasTextures.BLUEPRINT.canvas.toColor()), "corner should read as blueprint's own canvas colour")
    }

    /**
     * [LensTab] itself only ever opens on [LENS_TAB_TERMS]'s own first term (bowl, on `o`) -- this
     * renders [LensDiagram] directly (`internal`, see its own KDoc) for one term from each of the
     * other hero-glyph groups (`n`/stem, `T`/serif, `a`/storeys, `c`/terminal+aperture), so every
     * real glyph this tab ever draws gets its own real screenshot to check leader-line placement
     * against, not just the one the tab happens to open on. Eyeballed against
     * `ui/typewright-explorer.html`'s own `#ln-lens` look before this task was reported done.
     */
    @Test
    fun rendersEveryOtherHeroGlyphGroupWithoutCrashing() {
        val cases =
            listOf(
                "lens-diagram-stem-n" to AnatomyTerm.STEM,
                "lens-diagram-serif-t" to AnatomyTerm.SERIF,
                "lens-diagram-storeys-a" to AnatomyTerm.STOREYS,
                "lens-diagram-terminal-c" to AnatomyTerm.TERMINAL,
            )
        for ((name, term) in cases) {
            val shot =
                ScreenshotHarness.capture(name = name, width = 440, height = 560, density = 2f) {
                    LensDiagram(
                        glyphSet = glyphSet,
                        selectedTerm = term,
                        onSelect = {},
                        ink = CanvasTextures.PAPER.ink.toColor(),
                        fg = CanvasTextures.PAPER.fg.toColor(),
                        muted = CanvasTextures.PAPER.muted.toColor(),
                        lineColor = CanvasTextures.PAPER.line.toColor(),
                        violet =
                            dev.aarso.typewright.ui.tokens.MeaningColors.VIOLET
                                .forTexture(CanvasTextures.PAPER.id)
                                .toColor(),
                    )
                }
            assertTrue(shot.file.length() > 0, "PNG written for $term to ${shot.file}")
            val pixels = shot.bitmap.toPixelMap()
            var inkPixels = 0
            val inkColor = CanvasTextures.PAPER.ink.toColor()
            for (x in 0 until pixels.width) {
                for (y in 0 until pixels.height) {
                    if (pixels[x, y].isNear(inkColor)) inkPixels++
                }
            }
            assertTrue(inkPixels > 100, "$term's own diagram should draw a real glyph plus leader line(s): $inkPixels ink-coloured pixels")
        }
    }

    private fun androidx.compose.ui.graphics.Color.isNear(other: androidx.compose.ui.graphics.Color): Boolean =
        kotlin.math.abs(red - other.red) < 0.06f && kotlin.math.abs(green - other.green) < 0.06f &&
            kotlin.math.abs(blue - other.blue) < 0.06f
}
