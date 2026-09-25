// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.ui.tokens

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Every value transcribed from UI_SPEC.md §2's tables; see that file for the source of truth. */
class CanvasTokensTest {
    @Test
    fun paperMatchesUiSpec() {
        val paper = CanvasTextures.PAPER
        assertEquals(Argb.rgb(0xEFE9DC), paper.canvas)
        assertEquals(Argb.rgb(0x17150F), paper.ink)
        assertEquals(Argb.rgb(0x1B1915), paper.fg)
        assertEquals(Argb.rgb(0x6F6A5E), paper.muted)
        assertEquals(paper.ink.red, paper.line.red)
        assertEquals(paper.ink.green, paper.line.green)
        assertEquals(paper.ink.blue, paper.line.blue)
        assertEquals(51, paper.line.alpha) // "ink 20%": round(0.20 * 255)
        assertEquals(0.16, paper.grainOpacity)
        assertNull(paper.fineGrid)
    }

    @Test
    fun vellumMatchesUiSpec() {
        val vellum = CanvasTextures.VELLUM
        assertEquals(Argb.rgb(0xE6DCC1), vellum.canvas)
        assertEquals(Argb.rgb(0x1A160F), vellum.ink)
        assertEquals(Argb.rgb(0x1B1712), vellum.fg)
        assertEquals(Argb.rgb(0x766D5A), vellum.muted)
        assertEquals(56, vellum.line.alpha) // "ink 22%": round(0.22 * 255)
        assertEquals(0.30, vellum.grainOpacity)
    }

    @Test
    fun blueprintMatchesUiSpecIncludingTheFineGrid() {
        val blueprint = CanvasTextures.BLUEPRINT
        assertEquals(Argb.rgb(0x17417C), blueprint.canvas)
        assertEquals(Argb.rgb(0xFFFFFF), blueprint.ink)
        assertEquals(Argb.rgb(0xEAF1FF), blueprint.fg)
        assertEquals(Argb.rgb(0xA6B8DA), blueprint.muted)
        assertEquals(0xFFFFFF, rgbBits(blueprint.line))
        assertEquals(97, blueprint.line.alpha) // "white 38%": round(0.38 * 255)
        assertEquals(0.0, blueprint.grainOpacity)
        val fineGrid = assertNotNull(blueprint.fineGrid, "blueprint has its own second grid")
        assertEquals(24.0, fineGrid.sizeDp)
        assertEquals(15, fineGrid.color.alpha) // "white 6%": round(0.06 * 255)
    }

    @Test
    fun blackMatchesUiSpec() {
        val black = CanvasTextures.BLACK
        assertEquals(Argb.rgb(0x000000), black.canvas)
        assertEquals(Argb.rgb(0xF3F3F0), black.ink)
        assertEquals(Argb.rgb(0xECECEA), black.fg)
        assertEquals(Argb.rgb(0x8E8E89), black.muted)
        assertEquals(56, black.line.alpha) // "white 22%": round(0.22 * 255)
        assertEquals(0.0, black.grainOpacity)
    }

    @Test
    fun whiteMatchesUiSpec() {
        val white = CanvasTextures.WHITE
        assertEquals(Argb.rgb(0xFFFFFF), white.canvas)
        assertEquals(Argb.rgb(0x000000), white.ink)
        assertEquals(Argb.rgb(0x141414), white.fg)
        assertEquals(Argb.rgb(0x6B6B6B), white.muted)
        assertEquals(46, white.line.alpha) // "ink 18%": round(0.18 * 255)
        assertEquals(0.0, white.grainOpacity)
    }

    @Test
    fun defaultTextureIsPaper() {
        assertEquals(CanvasTextures.PAPER, CanvasTextures.DEFAULT)
    }

    @Test
    fun byIdFindsEveryTexture() {
        for (texture in CanvasTextures.ALL) {
            assertEquals(texture, CanvasTextures.byId(texture.id))
        }
    }

    @Test
    fun meaningColorsMatchUiSpec() {
        assertEquals(Argb.rgb(0x5F4BE0), MeaningColors.VIOLET.light)
        assertEquals(Argb.rgb(0x8E7BFF), MeaningColors.VIOLET.dark)
        assertEquals(Argb.rgb(0xB3A6FF), MeaningColors.VIOLET.blueprint)

        assertEquals(Argb.rgb(0x0A9D8E), MeaningColors.CYAN.light)
        assertEquals(Argb.rgb(0x08FED5), MeaningColors.CYAN.dark)
        // Cyan has no distinct blueprint value in UI_SPEC §2: falls back to dark.
        assertEquals(MeaningColors.CYAN.dark, MeaningColors.CYAN.blueprint)

        assertEquals(Argb.rgb(0xB57A00), MeaningColors.AMBER.light)
        assertEquals(Argb.rgb(0xFFB300), MeaningColors.AMBER.dark)
        assertEquals(Argb.rgb(0xFFC247), MeaningColors.AMBER.blueprint)

        assertEquals(Argb.rgb(0xB8248F), MeaningColors.MAGENTA.light)
        assertEquals(Argb.rgb(0xFF5FD2), MeaningColors.MAGENTA.dark)
        assertEquals(Argb.rgb(0xFF7ADB), MeaningColors.MAGENTA.blueprint)
    }

    @Test
    fun meaningColorForTexturePicksTheRightGround() {
        val violet = MeaningColors.VIOLET
        assertEquals(violet.light, violet.forTexture(TextureId.PAPER))
        assertEquals(violet.light, violet.forTexture(TextureId.VELLUM))
        assertEquals(violet.light, violet.forTexture(TextureId.WHITE))
        assertEquals(violet.dark, violet.forTexture(TextureId.BLACK))
        assertEquals(violet.blueprint, violet.forTexture(TextureId.BLUEPRINT))
    }

    @Test
    fun gridTokenMatchesUiSpec() {
        assertEquals(28.0, GridToken.SIZE_DP)
        assertEquals(0.085, GridToken.INK_OPACITY)
        assertEquals(0.09, GridToken.WHITE_OPACITY_ON_DARK)
    }

    @Test
    fun gridColorForUsesInkExceptOnBlack() {
        val paperGrid = GridToken.colorFor(CanvasTextures.PAPER)
        assertEquals(CanvasTextures.PAPER.ink.red, paperGrid.red)
        assertEquals(22, paperGrid.alpha) // round(0.085 * 255)

        val blackGrid = GridToken.colorFor(CanvasTextures.BLACK)
        assertEquals(0xFFFFFF, rgbBits(blackGrid))
        assertEquals(23, blackGrid.alpha) // round(0.09 * 255)
    }

    private fun rgbBits(color: Argb): Int = (color.red shl 16) or (color.green shl 8) or color.blue

    @Test
    fun bloomStopsShareRgbAndDifferOnlyInAlpha() {
        for (texture in CanvasTextures.ALL) {
            val (inner, outer) = BloomToken.stopsFor(texture)
            // The review's own fix: both stops carry the *same* RGB as the canvas colour --
            // never `Argb.TRANSPARENT` (RGB 0x000000), which is what caused the grey halo.
            assertEquals(texture.canvas.red, inner.red)
            assertEquals(texture.canvas.green, inner.green)
            assertEquals(texture.canvas.blue, inner.blue)
            assertEquals(texture.canvas.red, outer.red)
            assertEquals(texture.canvas.green, outer.green)
            assertEquals(texture.canvas.blue, outer.blue)
            assertEquals(107, inner.alpha) // round(0.42 * 255)
            assertEquals(0, outer.alpha)
        }
    }

    @Test
    fun spacingTokensMatchUiSpec() {
        assertEquals(18.0, SpacingTokens.GUTTER_DP)
        assertEquals(14.0, SpacingTokens.VERTICAL_RHYTHM_MIN_DP)
        assertEquals(22.0, SpacingTokens.VERTICAL_RHYTHM_MAX_DP)
        assertEquals(16.0, SpacingTokens.INSPECTOR_FROM_BOTTOM_DP)
        assertEquals(44.0, SpacingTokens.MIN_TOUCH_TARGET_DP)
    }
}
