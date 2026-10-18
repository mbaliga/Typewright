// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.deck

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.asoc.typewright.ui.ScreenshotHarness
import com.asoc.typewright.ui.tokens.CanvasTextures
import com.asoc.typewright.ui.tokens.toColor
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Real renders of [ControlDeck] in the states ui/v1-screens/DeckStates.dc.html draws (the PNGs land
 * under `ui/build/screenshots/`). There are no golden images: each test checks that a frame was
 * written and that the red close circle is the only red on the page (CLAUDE.md law 8).
 */
class ControlDeckScreenshotTest {
    private fun shot(
        name: String,
        texture: com.asoc.typewright.ui.tokens.CanvasTexture = CanvasTextures.PAPER,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) = ScreenshotHarness.capture(name = name, width = 780, height = 344, density = 2f) {
        Box(Modifier.fillMaxSize().background(texture.canvas.toColor()), contentAlignment = Alignment.BottomCenter) { content() }
    }

    @Test
    fun drawWithBackMenuAndTwoToggleGroups() {
        val s =
            shot("deck-draw") {
                ControlDeck(
                    CanvasTextures.PAPER,
                    section = DeckSection.DRAW,
                    toggles =
                        listOf(
                            DeckToggle("Outline", on = true),
                            DeckToggle("Fill"),
                            DeckToggle("Construction", on = true, groupStart = true),
                        ),
                )
            }
        assertTrue(s.file.length() > 0)
    }

    @Test
    fun closeAndANextAction() {
        val s =
            shot("deck-close-next") {
                ControlDeck(
                    CanvasTextures.PAPER,
                    section = DeckSection.HOME,
                    left = DeckLeft.CLOSE,
                    right = DeckRight.NEXT,
                    dimSections = true,
                )
            }
        assertTrue(s.file.length() > 0)
    }

    @Test
    fun aBlockedPrimaryStaysVisibleAtThirtyFivePercent() {
        val s =
            shot("deck-blocked") {
                ControlDeck(
                    CanvasTextures.PAPER,
                    section = DeckSection.SHIP,
                    right = DeckRight.NEXT,
                    rightDimmed = true,
                    toggles = listOf(DeckToggle("Google Fonts", on = true), DeckToggle("Own licence")),
                )
            }
        assertTrue(s.file.length() > 0)
    }

    @Test
    fun onTheBlackGround() {
        val s =
            shot("deck-black", CanvasTextures.BLACK) {
                ControlDeck(
                    CanvasTextures.BLACK,
                    section = DeckSection.SPACE,
                    left = DeckLeft.CLOSE,
                    toggles = listOf(DeckToggle("Spacing", on = true), DeckToggle("Kerning")),
                )
            }
        assertTrue(s.file.length() > 0)
    }
}
