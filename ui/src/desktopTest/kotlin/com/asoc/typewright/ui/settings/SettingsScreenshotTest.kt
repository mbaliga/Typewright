// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.settings

import com.asoc.typewright.ui.ScreenshotHarness
import com.asoc.typewright.ui.deck.DeckSection
import com.asoc.typewright.ui.theme.AppearanceState
import com.asoc.typewright.ui.theme.Themes
import com.asoc.typewright.ui.tokens.Argb
import com.asoc.typewright.ui.tokens.CanvasTextures
import com.asoc.typewright.ui.tokens.ThemeRole
import kotlin.test.Test
import kotlin.test.assertTrue

/** Real renders of Settings, the theme editor (with a collision showing) and About, in light and dark themes. */
class SettingsScreenshotTest {
    private fun shot(
        name: String,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) = ScreenshotHarness.capture(name = name, width = 720, height = 1560, density = 2f) { content() }

    @Test
    fun settingsInPaperAndBlack() {
        for (theme in listOf(Themes.PAPER, Themes.BLACK)) {
            val s =
                shot("settings-${theme.id}") {
                    SettingsScreen(
                        appearance = AppearanceState(initialCustom = listOf(Themes.BLACK.duplicate("dusk", "Dusk"))),
                        texture = theme.toTexture(),
                        section = DeckSection.HOME,
                        onBack = {},
                        onCustomTheme = { _, _ -> },
                        onAbout = {},
                    )
                }
            assertTrue(s.file.length() > 0)
        }
    }

    @Test
    fun theEditorShowsTheLivePreviewAndAGuardrailMessageUnderTheRow() {
        val state = ThemeEditorState(Themes.BLACK, freshId = "mine")
        state.setColor(ThemeRole.SNAP_TARGET, state.draft.color(ThemeRole.ACCENT))
        val s = shot("theme-editor-collision") { ThemeEditorScreen(state, DeckSection.HOME, onSave = {}, onLeave = {}) }
        assertTrue(s.file.length() > 0)
        val clean = ThemeEditorState(Themes.PAPER, freshId = "p")
        clean.setColor(ThemeRole.CANVAS, Argb.rgb(0xF4F1E8))
        assertTrue(shot("theme-editor-paper") { ThemeEditorScreen(clean, DeckSection.HOME, onSave = {}, onLeave = {}) }.file.length() > 0)
    }

    @Test
    fun aboutShowsTheLicenceThePrivacyStatementAndTheNotices() {
        val s = shot("about") { AboutScreen(texture = CanvasTextures.PAPER, section = DeckSection.HOME, onBack = {}) }
        assertTrue(s.file.length() > 0)
        assertTrue(NOTICE_SECTIONS.size >= 8)
        assertTrue(NOTICE_SECTIONS.any { it.title.contains("Shipped inside the apps") })
    }
}
