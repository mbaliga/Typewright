// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.brief

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import com.asoc.typewright.brief.Dimension
import com.asoc.typewright.brief.Door
import com.asoc.typewright.brief.Level
import com.asoc.typewright.project.Ethos
import com.asoc.typewright.qa.corpus.loadStyleAtlas
import com.asoc.typewright.ui.ScreenshotHarness
import com.asoc.typewright.ui.tokens.CanvasTextures
import com.asoc.typewright.ui.tokens.toColor
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Real renders of [BriefScreen] on the desktop target, one per tab, with the real style atlas and
 * the real cue fonts; the PNGs land under `ui/build/screenshots/`. There are no golden images to
 * compare against: each test checks that a frame was written and that the screen paints its own
 * canvas colour.
 */
class BriefScreenScreenshotTest {
    private val seeded =
        Ethos(
            genre = "sans-geometric",
            uses = listOf("watch-face"),
            levels = mapOf(Dimension.CONTRAST.id to Level.CONTRAST_EVEN.id),
        )

    private fun stateOn(
        tab: BriefScreenTab,
        door: Door? = null,
    ) = BriefScreenUiState(initialEthos = seeded, atlas = loadStyleAtlas(), initialTab = tab, initialDoor = door)

    @Test
    fun rendersTheDoorsOnTheAskTab() {
        val shot =
            ScreenshotHarness.capture(name = "brief-screen-ask-doors", width = 420, height = 860, density = 2f) {
                BriefScreen(texture = CanvasTextures.PAPER, state = stateOn(BriefScreenTab.ASK))
            }
        assertTrue(shot.file.length() > 0, "PNG written to ${shot.file}")
        val pixels = shot.bitmap.toPixelMap()
        assertTrue(pixels[4, 4].isNear(CanvasTextures.PAPER.canvas.toColor()), "top corner should read as the paper canvas colour")
    }

    @Test
    fun rendersAQuestionOnTheAskTab() {
        val state = stateOn(BriefScreenTab.ASK, door = Door.STYLE)
        assertNotNull(state.askPosition()?.step)
        val shot =
            ScreenshotHarness.capture(name = "brief-screen-ask-question", width = 420, height = 860, density = 2f) {
                BriefScreen(texture = CanvasTextures.PAPER, state = state)
            }
        assertTrue(shot.file.length() > 0, "PNG written to ${shot.file}")
    }

    @Test
    fun rendersTheBriefTab() {
        val shot =
            ScreenshotHarness.capture(name = "brief-screen-brief", width = 420, height = 860, density = 2f) {
                BriefScreen(texture = CanvasTextures.PAPER, state = stateOn(BriefScreenTab.BRIEF))
            }
        assertTrue(shot.file.length() > 0, "PNG written to ${shot.file}")
    }

    @Test
    fun rendersTheGuideTab() {
        val shot =
            ScreenshotHarness.capture(name = "brief-screen-guide", width = 420, height = 860, density = 2f) {
                BriefScreen(texture = CanvasTextures.PAPER, state = stateOn(BriefScreenTab.GUIDE))
            }
        assertTrue(shot.file.length() > 0, "PNG written to ${shot.file}")
    }

    @Test
    fun rendersEveryTabWithoutTheAtlas() {
        for (tab in BriefScreenTab.ORDERED) {
            val state = BriefScreenUiState(initialEthos = seeded, atlas = null, initialTab = tab)
            val shot =
                ScreenshotHarness.capture(name = "brief-screen-no-atlas-${tab.name.lowercase()}", width = 420, height = 860, density = 2f) {
                    BriefScreen(texture = CanvasTextures.PAPER, state = state)
                }
            assertTrue(shot.file.length() > 0, "PNG written to ${shot.file} for tab ${tab.label}")
        }
    }

    @Test
    fun onBackIsNotInvokedByAPlainRender() {
        var backCalls = 0
        ScreenshotHarness.capture(name = "brief-screen-back-not-yet-tapped", width = 420, height = 860, density = 2f) {
            BriefScreen(texture = CanvasTextures.PAPER, onBack = { backCalls++ }, state = stateOn(BriefScreenTab.ASK))
        }
        assertTrue(backCalls == 0, "onBack must not fire just from rendering")
    }

    private fun Color.isNear(other: Color): Boolean =
        abs(red - other.red) < 0.08f && abs(green - other.green) < 0.08f && abs(blue - other.blue) < 0.08f
}
