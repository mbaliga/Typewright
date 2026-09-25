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
 * Task P6 ("Shell" phase): a real render of [TypewrightApp]'s own minimal entry point to
 * [com.asoc.typewright.ui.learn.LearnScreen], in both [TypewrightAppNavState] states, via the
 * same [ScreenshotHarness] pattern every other screenshot test in this module already uses.
 * [PlaceholderScreenshotTest] already covers the closed/default state's own sheet content; this
 * file is specifically about the entry point this task adds: the "Learn" corner tab, and
 * [com.asoc.typewright.ui.learn.LearnScreen] actually appearing full-screen once
 * [TypewrightAppNavState.showLearn] is true.
 *
 * **Task P7 additions.** [com.asoc.typewright.ui.workbook.WorkbookEntryButton] now stacks above
 * the Learn button in the same bottom-end corner (this file's own top-level KDoc, "Task P7") --
 * [closedStateShowsBothEntryButtonsStackedWithoutOverlapping] confirms by real pixel sampling
 * (this task's own instruction: "verify this by screenshot, not by eye alone") that the two ink
 * blocks do not collide at this phone size, and
 * [workbookOpenStateShowsWorkbookScreenFullScreenInsteadOfTheEntryButtons] mirrors the existing
 * Learn open-state test for the sibling screen.
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
        // The Learn button is the *lower* of the two stacked ink blocks, still pinned to the
        // bottom-end corner (16 dp inset) -- sample a pixel well inside where it must be, at this
        // exact phone size/density, and expect ink there.
        val sampleX = phoneWidthPx - 40
        val sampleY = phoneHeightPx - 40
        assertTrue(
            pixels[sampleX, sampleY].isNear(ink),
            "expected the Learn entry button's own ink block near the bottom-end corner ($sampleX, $sampleY), was ${pixels[sampleX, sampleY]}",
        )
    }

    @Test
    fun closedStateShowsBothEntryButtonsStackedWithoutOverlapping() {
        val shot =
            ScreenshotHarness.capture(
                name = "app-entry-buttons-closed",
                width = phoneWidthPx,
                height = phoneHeightPx,
                density = phoneDensity,
            ) {
                TypewrightApp()
            }
        assertTrue(shot.file.length() > 0, "PNG written to ${shot.file}")

        val pixels = shot.bitmap.toPixelMap()
        val ink = CanvasTextures.PAPER.ink.toColor()
        val canvasColor = CanvasTextures.PAPER.canvas.toColor()
        val sampleX = phoneWidthPx - 40
        // Exact boundaries measured directly from this render (a vertical pixel scan at this same
        // x, not guessed): Workbook button ink y in [1392, 1450), a real canvas-coloured gap in
        // [1450, 1466), Learn button ink in [1466, 1528) -- this task's own instruction to verify
        // no collision by screenshot, not by eye alone.
        val workbookSampleY = 1420
        assertTrue(
            pixels[sampleX, workbookSampleY].isNear(ink),
            "expected the Workbook entry button's own ink block above the Learn button ($sampleX, $workbookSampleY), was ${pixels[sampleX, workbookSampleY]}",
        )
        val gapY = 1458
        assertTrue(
            pixels[sampleX, gapY].isNear(canvasColor),
            "expected a clear canvas-coloured gap between the two stacked entry buttons ($sampleX, $gapY), was ${pixels[sampleX, gapY]}",
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
