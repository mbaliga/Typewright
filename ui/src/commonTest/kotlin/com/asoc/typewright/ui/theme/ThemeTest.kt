// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.theme

import com.asoc.typewright.ui.tokens.Argb
import com.asoc.typewright.ui.tokens.CanvasTextures
import com.asoc.typewright.ui.tokens.TextureId
import com.asoc.typewright.ui.tokens.ThemeRole
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThemeTest {
    private val custom = Themes.PAPER.duplicate("mine", "Mine")

    @Test
    fun oklabRoundTripsAColour() {
        for (hex in listOf(0xEFE9DC, 0x17150F, 0x5F4BE0, 0x0A9D8E, 0xD0342C, 0xFFFFFF, 0x000000)) {
            val c = Argb.rgb(hex.toLong())
            val back = c.toOkLab().toArgb()
            assertTrue(abs(c.red - back.red) <= 1 && abs(c.green - back.green) <= 1 && abs(c.blue - back.blue) <= 1, "$c -> $back")
        }
    }

    @Test
    fun contrastAndDeltaEAreWhatTheSpecSays() {
        assertEquals(21.0, contrastRatio(Argb.rgb(0x000000), Argb.rgb(0xFFFFFF)), 0.01)
        assertEquals(0.0, deltaE(Argb.rgb(0x5F4BE0), Argb.rgb(0x5F4BE0)), 1e-9)
        assertTrue(lightnessGap(Argb.rgb(0x000000), Argb.rgb(0xFFFFFF)) > 99.0)
    }

    @Test
    fun theBuiltInThemesMeetTheGuardrailsExceptWhereUiSpecsOwnValuesFallShort() {
        val found = Themes.BUILT_IN.associate { it.id to ThemeGuardrails.check(it).map { p -> p.message } }
        for ((id, messages) in found) {
            // Written down so it cannot get worse unnoticed; docs/OPEN_QUESTIONS.md says why.
            val known = KNOWN_BUILT_IN_SHORTFALLS[id].orEmpty()
            assertEquals(known, messages, id)
        }
    }

    @Test
    fun aBuiltInIsItsOwnTextureAndACustomThemeGetsItsOwn() {
        assertEquals(CanvasTextures.PAPER, Themes.PAPER.toTexture())
        assertEquals(CanvasTextures.BLUEPRINT, Themes.BLUEPRINT.toTexture())
        val texture = custom.with(ThemeRole.CANVAS, Argb.rgb(0xF4F4F0)).toTexture()
        assertEquals(TextureId.CUSTOM, texture.id)
        assertEquals(Argb.rgb(0xF4F4F0), texture.canvas)
        assertFalse(texture.dark)
        assertEquals(Themes.PAPER.color(ThemeRole.ACCENT), texture.roles[ThemeRole.ACCENT])
    }

    @Test
    fun aThemeRoundTripsThroughItsJsonIdentically() {
        val theme = custom.with(ThemeRole.ACCENT, Argb.rgb(0x2255CC)).copy(grain = 0.25, gridOpacity = 0.1)
        val read = ThemeCodec.decode(ThemeCodec.encode(theme))
        assertIs<ThemeRead.Ok>(read)
        assertEquals(theme, read.theme)
        assertEquals(listOf(theme), ThemeCodec.decodeAll(ThemeCodec.encodeAll(listOf(theme))))
    }

    @Test
    fun aMissingRoleTakesTheDefaultAndAnUnknownRoleIsIgnored() {
        val text =
            """{"format":"typewright-theme","version":1,"id":"x","name":"X","dark":true,"grain":0,"gridOpacity":0.09,
              "colors":{"canvas":"#000000","ink":"#F3F3F0","muted":"#8E8E89","future":"#123456"}}"""
        val read = ThemeCodec.decode(text)
        assertIs<ThemeRead.Ok>(read)
        assertEquals(Themes.BLACK.color(ThemeRole.ACCENT), read.theme.color(ThemeRole.ACCENT))
        assertFalse(read.theme.builtIn)
    }

    @Test
    fun aBadFileSaysWhy() {
        for (text in listOf(
            "not json",
            "{}",
            """{"format":"typewright-theme","id":"a","name":"A","dark":false,"grain":9,"gridOpacity":0,"colors":{}}""",
        )) {
            assertIs<ThemeRead.Bad>(ThemeCodec.decode(text), text)
        }
        val badColour =
            """{"format":"typewright-theme","id":"a","name":"A","dark":false,"grain":0,"gridOpacity":0,"colors":{"canvas":"red","ink":"#000000","muted":"#444444"}}"""
        assertTrue((ThemeCodec.decode(badColour) as ThemeRead.Bad).reason.contains("Canvas"))
    }

    @Test
    fun lowContrastTextAndLinesAreBlockedWithAReason() {
        val faint = custom.with(ThemeRole.INK, Argb.rgb(0xD8D2C4)).with(ThemeRole.GUIDES, Argb.rgb(0xE5DFD0))
        val problems = ThemeGuardrails.check(faint)
        assertTrue(problems.any { it.roles == listOf(ThemeRole.INK) && it.message.contains("4.5:1") })
        assertTrue(problems.any { it.roles == listOf(ThemeRole.GUIDES) && it.message.contains("3.0:1") })
    }

    @Test
    fun aCollisionBetweenSelectionSnapAndAccentIsBlockedAndNamesBothRoles() {
        val same = custom.with(ThemeRole.SNAP_TARGET, custom.color(ThemeRole.ACCENT))
        val problem = ThemeGuardrails.check(same).single { it.roles == listOf(ThemeRole.ACCENT, ThemeRole.SNAP_TARGET) }
        assertTrue(problem.message.contains("Accent") && problem.message.contains("Snap target"))
        val alikeNode = custom.with(ThemeRole.SELECTED_NODE, Themes.PAPER.color(ThemeRole.INK))
        assertTrue(ThemeGuardrails.check(alikeNode).any { ThemeRole.SELECTED_NODE in it.roles && ThemeRole.CORNER_NODE in it.roles })
    }

    @Test
    fun noRoleMayBeNearTheCloseRedAndTheNearestAllowedColourIsOffered() {
        val red = ThemeGuardrails.closeRed(dark = false)
        val nearRed = custom.with(ThemeRole.COMPARISON_3, Argb.rgb(0xD6382E))
        val problem = ThemeGuardrails.check(nearRed).single { it.roles == listOf(ThemeRole.COMPARISON_3) }
        val suggestion = assertNotNull(problem.suggestion)
        assertTrue(deltaE(suggestion, red) >= ThemeGuardrails.RED_DELTA_E)
        val fixed = nearRed.with(ThemeRole.COMPARISON_3, suggestion)
        assertTrue(ThemeGuardrails.check(fixed).none { it.roles == listOf(ThemeRole.COMPARISON_3) && it.suggestion != null })
        assertEquals(
            ThemeGuardrails.closeRed(dark = true),
            com.asoc.typewright.ui.tokens.CloseToken
                .red(true),
        )
    }

    @Test
    fun resetRestoresARoleFromTheThemeItWasMadeFrom() {
        val changed = custom.with(ThemeRole.ACCENT, Argb.rgb(0x112233))
        assertEquals(Themes.PAPER.color(ThemeRole.ACCENT), changed.reset(ThemeRole.ACCENT, Themes.PAPER).color(ThemeRole.ACCENT))
    }

    @Test
    fun settingsRoundTripAndAnythingUnreadableIsTheDefault() {
        val s = AppSettings(themeId = "mine", textScale = 1.2, haptics = false)
        assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
        assertEquals(AppSettings(), SettingsCodec.decode(null))
        assertEquals(AppSettings(), SettingsCodec.decode("{"))
        assertEquals(AppSettings.MAX_TEXT_SCALE, SettingsCodec.decode("""{"textScale":9}""").textScale)
    }

    @Test
    fun systemMeansPaperByDayAndBlackByNightAndACustomThemeIsFoundById() {
        val state = AppearanceState(initialCustom = listOf(custom))
        assertEquals(Themes.PAPER, state.resolve(systemDark = false))
        assertEquals(Themes.BLACK, state.resolve(systemDark = true))
        state.update(AppSettings(themeId = "mine"))
        assertEquals(custom, state.resolve(systemDark = true))
        state.update(AppSettings(themeId = "gone"))
        assertEquals(Themes.PAPER, state.resolve(systemDark = false))
    }

    @Test
    fun savingMakesTheThemeTheChosenOneAndABuiltInCannotBeSaved() {
        val state = AppearanceState()
        state.saveTheme(custom)
        assertEquals("mine", state.settings.themeId)
        assertEquals(listOf(custom), state.custom)
        state.deleteTheme("mine")
        assertEquals(AppSettings.SYSTEM_THEME, state.settings.themeId)
        assertTrue(runCatching { state.saveTheme(Themes.PAPER) }.isFailure)
        assertEquals("mine2", AppearanceState(initialCustom = listOf(custom)).freshId("Mine"))
        assertNull(Themes.byId("mine"))
    }

    private companion object {
        val KNOWN_BUILT_IN_SHORTFALLS: Map<String, List<String>> =
            mapOf(
                "paper" to
                    listOf(
                        "Muted is 4.4:1 on the canvas; text needs 4.5:1.",
                        "Snap target is 2.7:1 on the canvas; lines and nodes need 3.0:1.",
                    ),
                "vellum" to
                    listOf(
                        "Muted is 3.7:1 on the canvas; text needs 4.5:1.",
                        "Snap target is 2.4:1 on the canvas; lines and nodes need 3.0:1.",
                        "Layer 1 is 2.6:1 on the canvas; lines and nodes need 3.0:1.",
                    ),
            )
    }
}
