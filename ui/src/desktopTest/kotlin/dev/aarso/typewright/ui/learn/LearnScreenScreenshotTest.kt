package dev.aarso.typewright.ui.learn

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import dev.aarso.typewright.ui.ScreenshotHarness
import dev.aarso.typewright.ui.tokens.CanvasTextures
import dev.aarso.typewright.ui.tokens.toColor
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A real render of [LearnScreen] (`ui/typewright-explorer.html`'s `#s-learn`), same
 * `ScreenshotHarness` pattern every sibling tab's own screenshot test already established --
 * captured at the same phone-ish portrait size ([LineagesTabScreenshotTest] etc. use), for all
 * four [LearnScreenTab] states via [LearnScreenUiState] pre-seeding (the same "GESTURE HONESTY"
 * precedent [LearnScreenUiState]'s own KDoc explains), opened and eyeballed against `#s-learn`'s
 * own header/tabs chrome before this task was reported done (the report itself says what was
 * actually seen; PNGs land under `ui/build/screenshots/`).
 */
class LearnScreenScreenshotTest {
    @Test
    fun rendersTheLineagesTabByDefaultOnPaper() {
        val shot =
            ScreenshotHarness.capture(name = "learn-screen-lineages-paper", width = 420, height = 860, density = 2f) {
                LearnScreen(texture = CanvasTextures.PAPER)
            }
        assertTrue(shot.file.length() > 0, "PNG written to ${shot.file}")

        val pixels = shot.bitmap.toPixelMap()
        val canvasColor = CanvasTextures.PAPER.canvas.toColor()
        // Corner (below the header/tabs chrome, clear of any content) reads as the paper canvas colour.
        assertTrue(
            pixels[4, pixels.height - 4].isNear(canvasColor),
            "bottom corner should read as the paper canvas colour, was ${pixels[4, pixels.height - 4]}",
        )
    }

    @Test
    fun rendersEachOfTheFourTabsWithoutCrashing() {
        for (tab in LearnScreenTab.ORDERED) {
            val shot =
                ScreenshotHarness.capture(
                    name = "learn-screen-${tab.paramValue}",
                    width = 420,
                    height = 860,
                    density = 2f,
                ) {
                    LearnScreen(texture = CanvasTextures.PAPER, uiState = LearnScreenUiState(initialTab = tab))
                }
            assertTrue(shot.file.length() > 0, "PNG written to ${shot.file} for tab ${tab.label}")
        }
    }

    @Test
    fun rendersOnEveryCanvasTextureWithoutCrashing() {
        for (texture in CanvasTextures.ALL) {
            val shot =
                ScreenshotHarness.capture(
                    name = "learn-screen-${texture.id.name.lowercase()}",
                    width = 420,
                    height = 860,
                    density = 2f,
                ) {
                    LearnScreen(texture = texture)
                }
            assertTrue(shot.file.length() > 0, "PNG written to ${shot.file} for texture ${texture.id}")
        }
    }

    @Test
    fun onBackIsNotInvokedSpuriouslyByAPlainRender() {
        // Not a click-simulation test -- no test anywhere in this codebase simulates a real
        // pointer event against a rendered composable (LearnScreenUiState's own KDoc explains why
        // a direct state assertion, not a simulated click, is this codebase's own established
        // equivalent for Compose state). This only guards against a regression where onBack fired
        // eagerly (e.g. from a LaunchedEffect) rather than from the header's own back button.
        var backCalls = 0
        val shot =
            ScreenshotHarness.capture(name = "learn-screen-back-not-yet-tapped", width = 420, height = 860, density = 2f) {
                LearnScreen(texture = CanvasTextures.PAPER, onBack = { backCalls++ })
            }
        assertTrue(shot.file.length() > 0, "PNG written to ${shot.file}")
        assertTrue(backCalls == 0, "onBack must not fire just from rendering")
    }

    private fun Color.isNear(other: Color): Boolean =
        abs(red - other.red) < 0.08f && abs(green - other.green) < 0.08f && abs(blue - other.blue) < 0.08f
}
