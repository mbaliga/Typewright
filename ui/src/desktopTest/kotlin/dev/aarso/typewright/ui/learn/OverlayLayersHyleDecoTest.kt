package dev.aarso.typewright.ui.learn

import dev.aarso.typewright.core.font.sfnt.readSfntFont
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * [buildOverlayLayer] against `fonts/HyleDeco-Regular.ttf` (this build's own real project-font
 * fixture; JVM-only for the same reason [StrokeProbeHyleDecoTest] is). The real cap-height/
 * x-height numbers pinned here (700/500) are also the real regression proof for
 * `core-font`'s `Os2Table.kt` fix this task made (P6): before that fix, `font.os2?.sCapHeight`
 * read `500` and `sxHeight` read `0`, both wrong -- see `HyleDecoCrossCheckTest`'s own
 * `os2XHeightAndCapHeightAreReadFromTheRealSpecOffsets` for the direct `core-font` proof; this
 * test proves the fix reaches [OverlayLayer] through [dev.aarso.typewright.ui.learn.capHeightEntry]/
 * [dev.aarso.typewright.ui.learn.xHeightEntry]'s own "drawn ink first" priority, which for Hyle
 * Deco's own `H`/`x` glyphs gives the *same* 700/500 either way (drawn ink and, post-fix,
 * `OS/2` finally agree).
 */
class OverlayLayersHyleDecoTest {
    private val font by lazy { readSfntFont(File("../fonts/HyleDeco-Regular.ttf").readBytes()) }

    @Test
    fun realCapHeightAndXHeightAreReadFromTheDrawnGlyphs() {
        val layer =
            buildOverlayLayer(
                font = font,
                id = "project",
                label = "Hyle Deco",
                sourceLabel = "project",
                dashPattern = null,
                meaningColor = null,
                chars = OVERLAY_DEFAULT_WORD,
            )
        assertEquals(700.0, layer.capHeightUnits)
        assertEquals(500.0, layer.xHeightUnits)
        assertEquals(1000, layer.unitsPerEm)
    }

    @Test
    fun everyLetterOfHamburgHasARealGlyph() {
        val layer =
            buildOverlayLayer(
                font = font,
                id = "project",
                label = "Hyle Deco",
                sourceLabel = "project",
                dashPattern = null,
                meaningColor = null,
                chars = OVERLAY_DEFAULT_WORD,
            )
        val glyphs = assertNotNull(layer.glyphsForWord(OVERLAY_DEFAULT_WORD))
        assertEquals(OVERLAY_DEFAULT_WORD.length, glyphs.size)
        // The word's own real advance widths must be positive real numbers, not zero-width
        // placeholders (a zero-width glyph would collapse the whole word onto one point).
        for (g in glyphs) assertNotNull(g.contours.firstOrNull(), "glyph '${g.name}' has no contours")
    }

    @Test
    fun glyphsForWordIsNullWhenTheWordHasALetterThisFontDoesNotBuild() {
        val layer =
            buildOverlayLayer(
                font = font,
                id = "project",
                label = "Hyle Deco",
                sourceLabel = "project",
                dashPattern = null,
                meaningColor = null,
                chars = "H",
            )
        // "Hamburg" needs a/m/b/u/r/g too, none of which `chars = "H"` asked for up front, but
        // buildOverlayLayer always also fetches H/x/b/o -- so only a/m/u/r/g are genuinely
        // missing here. glyphsForWord must still fail closed (null, never a partial list).
        assertNull(layer.glyphs['a'])
        assertNull(layer.glyphsForWord(OVERLAY_DEFAULT_WORD))
    }
}
