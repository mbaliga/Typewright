package dev.aarso.typewright.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import dev.aarso.typewright.ui.tokens.CanvasTextures
import dev.aarso.typewright.ui.tokens.toColor
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Task P6 ("Shell" phase): a real render of [TypewrightApp]'s own minimal entry point to
 * [dev.aarso.typewright.ui.learn.LearnScreen], in both [TypewrightAppNavState] states, via the
 * same [ScreenshotHarness] pattern every other screenshot test in this module already uses.
 * [PlaceholderScreenshotTest] already covers the closed/default state's own sheet content; this
 * file is specifically about the entry point this task adds: the "Learn" corner tab, and
 * [dev.aarso.typewright.ui.learn.LearnScreen] actually appearing full-screen once
 * [TypewrightAppNavState.showLearn] is true.
 */
class TypewrightAppEntryPointScreenshotTest {
    private val phoneWidthPx = 720
    private val phoneHeightPx = 1560
    private val phoneDensity = 2f

    @Test
    fun closedStateShowsTheSheetWithTheLearnEntryButtonInTheBottomEndCorner() {
        val shot =
            ScreenshotHarness.capture(name = "app-learn-closed", width = phoneWidthPx, height = phoneHeightPx, density = phoneDensity) {
                TypewrightApp()
            }
        assertTrue(shot.file.length() > 0, "PNG written to ${shot.file}")

        val pixels = shot.bitmap.toPixelMap()
        val ink = CanvasTextures.PAPER.ink.toColor()
        // The entry button is an ink block pinned to the bottom-end corner (16 dp inset, this
        // task's own reasoned choice -- see TypewrightApp's own KDoc) -- sample a pixel well
        // inside where it must be, at this exact phone size/density, and expect ink there.
        val sampleX = phoneWidthPx - 40
        val sampleY = phoneHeightPx - 40
        assertTrue(
            pixels[sampleX, sampleY].isNear(ink),
            "expected the Learn entry button's own ink block near the bottom-end corner ($sampleX, $sampleY), was ${pixels[sampleX, sampleY]}",
        )
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

    private fun Color.isNear(other: Color): Boolean =
        abs(red - other.red) < 0.08f && abs(green - other.green) < 0.08f && abs(blue - other.blue) < 0.08f
}
