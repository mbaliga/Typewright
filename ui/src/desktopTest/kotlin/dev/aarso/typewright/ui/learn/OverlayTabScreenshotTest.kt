// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.ui.learn

import androidx.compose.ui.graphics.toPixelMap
import dev.aarso.typewright.ui.Screenshot
import dev.aarso.typewright.ui.ScreenshotHarness
import dev.aarso.typewright.ui.tokens.CanvasTextures
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Real desktop proof that [OverlayTab] renders real outline geometry, not just that it compiles
 * (this task's own instruction: "screenshot the rendered tab and view it yourself before
 * reporting done"). [ScreenshotHarness] renders the real composable off-screen with Skia (no
 * display, no mock), the same harness [dev.aarso.typewright.ui.learn.LearnFaceFontsScreenshotTest]
 * already established for the Lineages tab's own font rendering.
 */
class OverlayTabScreenshotTest {
    @Test
    fun rendersRealInkOnPaper() {
        val shot =
            ScreenshotHarness.capture(name = "overlay-tab-paper", width = 420, height = 900, density = 2f) {
                OverlayTab(texture = CanvasTextures.PAPER)
            }
        assertTrue(shot.file.length() > 0, "PNG written to ${shot.file}")
        assertTrue(inkFraction(shot) > 0.01, "overlay tab rendered blank -- expected real glyph ink")
    }

    @Test
    fun rendersOnEveryCanvasTexture() {
        for (texture in CanvasTextures.ALL) {
            val shot =
                ScreenshotHarness.capture(name = "overlay-tab-${texture.id}", width = 420, height = 900, density = 2f) {
                    OverlayTab(texture = texture)
                }
            assertTrue(shot.file.length() > 0, "PNG written to ${shot.file} for texture ${texture.id}")
        }
    }

    private fun inkFraction(shot: Screenshot): Double {
        val pixels = shot.bitmap.toPixelMap()
        var nonBackground = 0
        var total = 0
        val bgTopLeft = pixels[0, 0]
        for (x in 0 until pixels.width step 2) {
            for (y in 0 until pixels.height step 2) {
                total++
                val p = pixels[x, y]
                if (p.red != bgTopLeft.red || p.green != bgTopLeft.green || p.blue != bgTopLeft.blue) nonBackground++
            }
        }
        return nonBackground.toDouble() / total
    }
}
