// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.ui.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.dp
import dev.aarso.typewright.learn.scenes.LineagesResources
import dev.aarso.typewright.learn.scenes.SceneRenderer
import dev.aarso.typewright.learn.scenes.StrandSequencer
import dev.aarso.typewright.ui.ScreenshotHarness
import dev.aarso.typewright.ui.tokens.CanvasTextures
import dev.aarso.typewright.ui.tokens.toColor
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A real render of [LineagesTab] (`ui/typewright-explorer.html`'s `#ln-lin`), same
 * `ScreenshotHarness` pattern `LearnFaceFontsScreenshotTest`/`SheetScreenshotTest` already
 * established — captured at a phone-ish portrait size, opened and eyeballed against the
 * explorer's own look before this task was reported done (this task's final report says what was
 * actually seen; PNGs land under `ui/build/screenshots/`).
 */
class LineagesTabScreenshotTest {
    @Test
    fun rendersTheOpeningEraOnPaperWithoutCrashing() {
        val shot =
            ScreenshotHarness.capture(name = "lineages-tab-paper", width = 420, height = 860, density = 2f) {
                LineagesTab(texture = CanvasTextures.PAPER)
            }
        assertTrue(shot.file.length() > 0, "PNG written to ${shot.file}")

        val pixels = shot.bitmap.toPixelMap()
        val canvasColor = CanvasTextures.PAPER.canvas.toColor()
        val inkColor = CanvasTextures.PAPER.ink.toColor()

        // Background reads as the paper canvas colour near a corner well clear of any content.
        assertTrue(pixels[4, 4].isNear(canvasColor), "corner should read as the paper canvas colour, was ${pixels[4, 4]}")

        // The crossfading "ago" (era 1, Blackletter: both from/to are the same face, so the
        // crossfade opacities sum to a fully-opaque render) puts a meaningful amount of ink on
        // screen -- this is the strongest single check that real font bytes, not an empty
        // fallback, actually rendered.
        var inkPixels = 0
        for (x in 0 until pixels.width) {
            for (y in 0 until pixels.height) {
                if (pixels[x, y].isNear(inkColor)) inkPixels++
            }
        }
        assertTrue(inkPixels > 300, "expected a meaningful amount of ink (stage word, eras strip, caption): $inkPixels ink-coloured pixels")
    }

    @Test
    fun rendersOnEveryCanvasTextureWithoutCrashing() {
        // Not a visual assertion (each texture's own look is eyeballed from the PNGs), just proof
        // the composable does not assume a particular texture -- the same threading
        // `CanvasTexture` already gets everywhere else in `ui`.
        for (texture in CanvasTextures.ALL) {
            val shot =
                ScreenshotHarness.capture(name = "lineages-tab-${texture.id.name.lowercase()}", width = 420, height = 860, density = 2f) {
                    LineagesTab(texture = texture)
                }
            assertTrue(shot.file.length() > 0, "PNG written to ${shot.file} for texture ${texture.id}")
        }
    }

    @Test
    fun rendersAMidCrossfadeWithARotatedStressDial() {
        // Era 3 (Transitional, stage.stress = [30, 12]) at t=0.5: a real, visibly rotated stress
        // dial (partway between 30 deg and 12 deg) and a real crossfade blend of two different
        // faces (EB Garamond fading out, Libre Baskerville fading in) -- the strongest single
        // visual proof of frameAt/crossfadeAt/stressAngleAt actually driving the render, not just
        // the "both faces the same" era-1 case the opening screenshot alone would prove.
        val scene = LineagesResources.loadFullBlockInPlayOrder().single { it.id == "lineages.transitional" }
        val frame = SceneRenderer.frameAt(scene, t = 0.5)

        val shot =
            ScreenshotHarness.capture(name = "lineages-stage-mid-crossfade", width = 420, height = 220, density = 2f) {
                StageArea(scene = scene, frame = frame, texture = CanvasTextures.PAPER)
            }
        assertTrue(shot.file.length() > 0, "PNG written to ${shot.file}")
    }

    @Test
    fun rendersTheIdentifyItQuizSection() {
        // QuizSection on the real, checked-in identify-it content (7 eligible bank cards --
        // LineagesRealContentTest pins the exact count) -- the block-finished state LineagesTab
        // itself only reaches after the eras strip reaches its last scene.
        val scenes = LineagesResources.loadFullBlockInPlayOrder()
        val bank = LineagesResources.loadIdentifyItBank()
        val plan = StrandSequencer.plan(scenes, bank)
        val classNames = scenes.filter { it.era != null }.map { it.title }
        val items = buildQuizItems(plan, classNames)

        val shot =
            ScreenshotHarness.capture(name = "lineages-quiz-section", width = 420, height = 1400, density = 2f) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.fillMaxSize().background(CanvasTextures.PAPER.canvas.toColor()).padding(18.dp),
                ) {
                    QuizSection(items = items, texture = CanvasTextures.PAPER)
                }
            }
        assertTrue(shot.file.length() > 0, "PNG written to ${shot.file}")
    }

    private fun Color.isNear(other: Color): Boolean =
        abs(red - other.red) < 0.08f && abs(green - other.green) < 0.08f && abs(blue - other.blue) < 0.08f
}
