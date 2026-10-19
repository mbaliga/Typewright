// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.settings

import com.asoc.typewright.ui.theme.Themes
import com.asoc.typewright.ui.tokens.Argb
import com.asoc.typewright.ui.tokens.ThemeRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThemeEditorStateTest {
    // Black meets every guardrail, so a copy of it can be saved until something is broken.
    private fun editor() = ThemeEditorState(Themes.BLACK, freshId = "mine")

    @Test
    fun itStartsFromACopyOfTheBuiltInAndNothingIsLostIfItIsClosed() {
        val state = editor()
        assertEquals("mine", state.draft.id)
        assertFalse(state.draft.builtIn)
        assertEquals("Black copy", state.draft.name)
        assertFalse(state.dirty)
        assertNull(state.blockedBecause)
    }

    @Test
    fun typingAWholeHexChangesTheColourAndAHalfTypedOneWaits() {
        val state = editor()
        state.typeHex(ThemeRole.ACCENT, "#33")
        assertEquals("#33", state.hexText(ThemeRole.ACCENT))
        assertEquals(Themes.BLACK.color(ThemeRole.ACCENT), state.draft.color(ThemeRole.ACCENT))
        state.typeHex(ThemeRole.ACCENT, "#33AAFF")
        assertEquals(Argb.rgb(0x33AAFF), state.draft.color(ThemeRole.ACCENT))
        assertTrue(state.dirty)
    }

    @Test
    fun aCollisionBlocksSavingAndTheMessageSaysWhichRolesCollide() {
        val state = editor()
        state.setColor(ThemeRole.SNAP_TARGET, state.draft.color(ThemeRole.ACCENT))
        val reason = assertNotNull(state.blockedBecause)
        assertTrue(reason.contains("Accent") && reason.contains("Snap target"), reason)
        state.resetRole(ThemeRole.SNAP_TARGET)
        assertNull(state.blockedBecause)
    }

    @Test
    fun aBlankNameBlocksSaving() {
        val state = editor()
        state.rename("  ")
        assertEquals("Give the theme a name.", state.blockedBecause)
    }

    @Test
    fun resetThemeKeepsTheNameAndRestoresEveryRole() {
        val state = editor()
        state.rename("Dusk")
        state.setColor(ThemeRole.ACCENT, Argb.rgb(0x112233))
        state.setGrain(0.3)
        state.resetTheme()
        assertEquals("Dusk", state.draft.name)
        assertEquals(Themes.BLACK.color(ThemeRole.ACCENT), state.draft.color(ThemeRole.ACCENT))
        assertEquals(state.original.grain, state.draft.grain)
    }

    @Test
    fun anExportedThemeReimportsIdentically() {
        val a = editor()
        a.setColor(ThemeRole.ACCENT, Argb.rgb(0x33AAFF))
        a.setGridOpacity(0.15)
        val json = a.export()
        val b = ThemeEditorState(Themes.PAPER, freshId = "other")
        assertNull(b.import(json))
        assertEquals(a.draft.copy(id = "other"), b.draft)
        assertEquals("not a theme file", b.import("{")?.substringBefore(":"))
    }

    @Test
    fun anExistingCustomThemeIsEditedUnderItsOwnId() {
        val existing = Themes.BLACK.duplicate("dusk", "Dusk")
        val state = ThemeEditorState(existing, freshId = "unused", existing = existing)
        assertEquals("dusk", state.draft.id)
        assertEquals("Dusk", state.draft.name)
    }
}
