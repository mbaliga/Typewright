// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.asoc.typewright.ui.brief.Chip
import com.asoc.typewright.ui.brief.Chips
import com.asoc.typewright.ui.brief.bodyStyle
import com.asoc.typewright.ui.brief.monoStyle
import com.asoc.typewright.ui.deck.DeckSection
import com.asoc.typewright.ui.shell.SectionScaffold
import com.asoc.typewright.ui.theme.AppSettings
import com.asoc.typewright.ui.theme.AppearanceState
import com.asoc.typewright.ui.theme.Theme
import com.asoc.typewright.ui.theme.Themes
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.accent
import com.asoc.typewright.ui.tokens.toColor

/**
 * Settings (S21): the app-level settings that something reads today, in groups. Every change is
 * written to the app's config and applies at once, with no restart: the theme (System, a built-in,
 * or one of your own, and Custom… to make one), the UI text size, and the deck's haptics. The deck
 * keeps the section the person came from, dimmed and inert; Back leaves.
 */
@Composable
fun SettingsScreen(
    appearance: AppearanceState,
    texture: CanvasTexture,
    section: DeckSection,
    onBack: () -> Unit,
    onCustomTheme: (base: Theme, existing: Theme?) -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = appearance.settings
    val current = appearance.resolve(systemDark = texture.dark)
    SectionScaffold(
        texture = texture,
        section = section,
        title = "Settings",
        subtitle = "Every change applies at once",
        modifier = modifier,
        onBack = onBack,
        dimSections = true,
        dimReason = "Leave Settings first",
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, bottom = 200.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GroupLabel("Appearance", texture)
            RowLabel("Theme", "System is Paper by day and Black by night", texture)
            Chips {
                Chip(label = "System", on = settings.themeId == AppSettings.SYSTEM_THEME, texture = texture) {
                    appearance.update(settings.copy(themeId = AppSettings.SYSTEM_THEME))
                }
                for (theme in Themes.BUILT_IN + appearance.custom) {
                    Chip(label = theme.name, on = settings.themeId == theme.id, texture = texture) {
                        appearance.update(settings.copy(themeId = theme.id))
                    }
                }
            }
            val chosenCustom = appearance.custom.firstOrNull { it.id == settings.themeId }
            Chips {
                Link(label = "Custom… ›", texture = texture) { onCustomTheme(current, null) }
                if (chosenCustom != null) {
                    Link(label = "Edit ${chosenCustom.name} ›", texture = texture) { onCustomTheme(chosenCustom, chosenCustom) }
                    Link(label = "Delete ›", texture = texture) { appearance.deleteTheme(chosenCustom.id) }
                }
            }
            RowLabel("UI text size", null, texture)
            Chips {
                for ((label, scale) in TEXT_SIZES) {
                    Chip(
                        label = label,
                        on = settings.textScale == scale,
                        texture = texture,
                    ) { appearance.update(settings.copy(textScale = scale)) }
                }
            }
            GroupLabel("Input", texture)
            RowLabel("Haptics", "A tap or a tick on the deck, on a phone", texture)
            Chips {
                Chip(label = "On", on = settings.haptics, texture = texture) { appearance.update(settings.copy(haptics = true)) }
                Chip(label = "Off", on = !settings.haptics, texture = texture) { appearance.update(settings.copy(haptics = false)) }
            }
            GroupLabel("About & licences", texture)
            Link(label = "About Typewright ›", texture = texture, onClick = onAbout)
        }
    }
}

private val TEXT_SIZES = listOf("Small" to 0.9, "Default" to 1.0, "Large" to 1.15, "Larger" to 1.3)

@Composable
private fun GroupLabel(
    text: String,
    texture: CanvasTexture,
) {
    BasicText(text = text.uppercase(), style = monoStyle(texture.muted.toColor()), modifier = Modifier.padding(top = 14.dp))
}

@Composable
private fun RowLabel(
    title: String,
    note: String?,
    texture: CanvasTexture,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        BasicText(text = title, style = bodyStyle(texture.fg.toColor(), sizeSp = 14.0, weight = FontWeight.SemiBold))
        if (note != null) BasicText(text = note, style = bodyStyle(texture.muted.toColor(), sizeSp = 12.0))
    }
}

@Composable
private fun Link(
    label: String,
    texture: CanvasTexture,
    onClick: () -> Unit,
) {
    BasicText(
        text = label,
        style = bodyStyle(texture.accent.toColor(), weight = FontWeight.SemiBold),
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 6.dp),
    )
}
