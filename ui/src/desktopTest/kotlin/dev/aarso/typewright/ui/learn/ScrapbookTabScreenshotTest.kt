// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.ui.learn

import androidx.compose.ui.graphics.toPixelMap
import dev.aarso.typewright.ui.Screenshot
import dev.aarso.typewright.ui.ScreenshotHarness
import dev.aarso.typewright.ui.tokens.CanvasTextures
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Real desktop proof that [ScrapbookTab] renders (this task's own instruction: "screenshot the
 * rendered tab and view it yourself before reporting done"). Same [ScreenshotHarness] pattern
 * every sibling Learn tab's own screenshot test already established
 * ([LineagesTabScreenshotTest], [OverlayTabScreenshotTest], [LensTabHyleDecoTest]).
 */
class ScrapbookTabScreenshotTest {
    @Test
    fun rendersTheSampleBoardOnPaperWithoutCrashing() {
        val shot =
            ScreenshotHarness.capture(name = "scrapbook-tab-paper", width = 420, height = 900, density = 2f) {
                ScrapbookTab(texture = CanvasTextures.PAPER)
            }
        assertTrue(shot.file.length() > 0, "PNG written to ${shot.file}")
        assertTrue(inkFraction(shot) > 0.01, "scrapbook tab rendered blank -- expected real pin ink")
    }

    @Test
    fun rendersOnEveryCanvasTextureWithoutCrashing() {
        for (texture in CanvasTextures.ALL) {
            val shot =
                ScreenshotHarness.capture(name = "scrapbook-tab-${texture.id.name.lowercase()}", width = 420, height = 900, density = 2f) {
                    ScrapbookTab(texture = texture)
                }
            assertTrue(shot.file.length() > 0, "PNG written to ${shot.file} for texture ${texture.id}")
        }
    }

    @Test
    fun rendersTheNoteDraftRowOnItsOwnWithoutCrashing() {
        // The real inline "+ note" composer NoteDraftRow builds -- ScrapbookTab only shows this
        // once "+ note" is tapped, so this exercises the row directly, the same
        // render-a-sub-composable-standalone pattern LineagesTabScreenshotTest's own
        // "rendersTheIdentifyItQuizSection" test already uses.
        val shot =
            ScreenshotHarness.capture(name = "scrapbook-note-draft-row", width = 420, height = 60, density = 2f) {
                NoteDraftRow(text = "a draft note", texture = CanvasTextures.PAPER, onTextChange = {}, onConfirm = {}, onCancel = {})
            }
        assertTrue(shot.file.length() > 0, "PNG written to ${shot.file}")
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
