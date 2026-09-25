// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `TypewrightApp` stopped being a placeholder in task P4b -- it now renders the real one-sheet UI
 * (`com.asoc.typewright.ui.sheet.TypewrightSheet`). This test is kept (not deleted, per this
 * task's own instruction to extend, not replace, the screenshot harness) with assertions updated
 * for what actually renders now: the paper canvas background, and a meaningful amount of ink (the
 * grid, the room label, the puck) rather than literally the word "Typewright". See
 * `SheetScreenshotTest` for the task's own dedicated room/texture/gesture-state screenshots.
 */
class PlaceholderScreenshotTest {
    @Test
    fun appRendersInkOnThePaperCanvas() {
        val shot = ScreenshotHarness.capture(name = "placeholder", width = 720, height = 480) { TypewrightApp() }
        val pixels = shot.bitmap.toPixelMap()

        assertTrue(shot.file.length() > 0, "PNG written to ${shot.file}")
        // Near, not exact: the sheet's grid starts right at the world origin, so a pixel this
        // close to the corner can pick up a whisper of grid-line antialiasing (unlike the old
        // placeholder, which drew nothing but flat background + text).
        assertTrue(pixels[4, 4].isNear(PaperTokens.Canvas), "corner is near the paper canvas, was ${pixels[4, 4]}")
        val inkPixels =
            (0 until pixels.width).sumOf { x ->
                (0 until pixels.height).count { y -> pixels[x, y].isNear(PaperTokens.Ink) }
            }
        assertTrue(inkPixels > 200, "the sheet draws a meaningful amount of ink (grid, labels, puck): $inkPixels ink pixels")
    }

    private fun Color.isNear(other: Color): Boolean =
        abs(red - other.red) < 0.06f && abs(green - other.green) < 0.06f && abs(blue - other.blue) < 0.06f
}
