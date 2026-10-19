// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import com.asoc.typewright.ui.tokens.CanvasTextures
import com.asoc.typewright.ui.tokens.toColor
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Real renders of [TypewrightApp] through the router: Home with the deck and no corner buttons,
 * every section inside the deck, the Map, and the Learn and Workbook screens full screen.
 */
class TypewrightAppEntryPointScreenshotTest {
    private val phoneWidthPx = 720
    private val phoneHeightPx = 1560
    private val phoneDensity = 2f

    @Test
    fun theAppOpensOnHomeWithTheDeckAndNoCornerButtons() {
        val shot =
            ScreenshotHarness.capture(name = "app-home", width = phoneWidthPx, height = phoneHeightPx, density = phoneDensity) {
                TypewrightApp()
            }
        assertTrue(shot.file.length() > 0, "PNG written to ${shot.file}")
        val pixels = shot.bitmap.toPixelMap()
        val canvasColor = CanvasTextures.PAPER.canvas.toColor()
        // The old Learn and Workbook corner buttons sat as ink blocks at the bottom-end corner; Home has the deck there.
        assertTrue(pixels[phoneWidthPx - 40, phoneHeightPx - 330].isNear(canvasColor), "no ink block where the corner buttons were")
    }

    @Test
    fun everySectionRendersInsideTheDeck() {
        for (section in com.asoc.typewright.ui.deck.DeckSection.entries) {
            val shot =
                ScreenshotHarness.capture(
                    name = "app-section-${section.name.lowercase()}",
                    width = phoneWidthPx,
                    height = phoneHeightPx,
                    density = phoneDensity,
                ) {
                    TypewrightApp(navState = TypewrightAppNavState(initialSection = section))
                }
            assertTrue(shot.file.length() > 0, section.name)
        }
    }

    @Test
    fun theMapRendersAllNineSections() {
        val state = TypewrightAppNavState()
        state.mapOpen = true
        val shot =
            ScreenshotHarness.capture(name = "app-map", width = phoneWidthPx, height = phoneHeightPx, density = phoneDensity) {
                TypewrightApp(navState = state)
            }
        assertTrue(shot.file.length() > 0)
    }

    @Test
    fun openStateShowsLearnScreenFullScreenInsteadOfTheEntryButton() {
        val shot =
            ScreenshotHarness.capture(name = "app-learn-open", width = phoneWidthPx, height = phoneHeightPx, density = phoneDensity) {
                TypewrightApp(navState = TypewrightAppNavState(initialShowLearn = true))
            }
        assertTrue(shot.file.length() > 0, "PNG written to ${shot.file}")

        val pixels = shot.bitmap.toPixelMap()
        val canvasColor = CanvasTextures.PAPER.canvas.toColor()
        // LearnScreen's own bottom corner (well clear of its header/tabs/tab content) reads as
        // the plain paper canvas colour -- the same corner PlaceholderScreenshotTest's own sheet
        // render would instead have picked up sheet-grid ink near, proving LearnScreen (not the
        // sheet) is what actually occupies the screen once open.
        assertTrue(
            pixels[8, phoneHeightPx - 8].isNear(canvasColor),
            "bottom-start corner should read as LearnScreen's own paper canvas colour once open, was ${pixels[8, phoneHeightPx - 8]}",
        )
    }

    @Test
    fun workbookOpenStateShowsWorkbookScreenFullScreenInsteadOfTheEntryButtons() {
        val shot =
            ScreenshotHarness.capture(name = "app-workbook-open", width = phoneWidthPx, height = phoneHeightPx, density = phoneDensity) {
                TypewrightApp(navState = TypewrightAppNavState(initialShowWorkbook = true))
            }
        assertTrue(shot.file.length() > 0, "PNG written to ${shot.file}")

        val pixels = shot.bitmap.toPixelMap()
        val canvasColor = CanvasTextures.PAPER.canvas.toColor()
        assertTrue(
            pixels[8, phoneHeightPx - 8].isNear(canvasColor),
            "bottom-start corner should read as WorkbookScreen's own paper canvas colour once open, was ${pixels[8, phoneHeightPx - 8]}",
        )
    }

    private fun Color.isNear(other: Color): Boolean =
        abs(red - other.red) < 0.08f && abs(green - other.green) < 0.08f && abs(blue - other.blue) < 0.08f
}
