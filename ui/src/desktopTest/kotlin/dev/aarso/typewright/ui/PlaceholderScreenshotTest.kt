package dev.aarso.typewright.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaceholderScreenshotTest {
    @Test
    fun placeholderRendersInkOnPaper() {
        val shot = ScreenshotHarness.capture(name = "placeholder", width = 720, height = 480) { TypewrightApp() }
        val pixels = shot.bitmap.toPixelMap()

        assertTrue(shot.file.length() > 0, "PNG written to ${shot.file}")
        assertEquals(PaperTokens.Canvas, pixels[4, 4], "corner is the paper canvas")
        val inkPixels =
            (0 until pixels.width).sumOf { x ->
                (0 until pixels.height).count { y -> pixels[x, y].isNear(PaperTokens.Ink) }
            }
        assertTrue(inkPixels > 200, "the word is drawn in ink ($inkPixels ink pixels)")
    }

    private fun Color.isNear(other: Color): Boolean =
        abs(red - other.red) < 0.06f && abs(green - other.green) < 0.06f && abs(blue - other.blue) < 0.06f
}
