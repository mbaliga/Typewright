// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.asoc.typewright.project.AppConfigStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The app's appearance: the settings and the custom themes, read from and written to the app's
 * config ([store]: `settings.json` and `themes.json`, so every change persists and applies without
 * a restart). With no [store] (a test, a screenshot) they live in memory. [resolve] picks the theme
 * for the system's own light or dark.
 */
class AppearanceState(
    private val store: AppConfigStore? = null,
    private val scope: CoroutineScope? = null,
    initialSettings: AppSettings = AppSettings(),
    initialCustom: List<Theme> = emptyList(),
) {
    var settings: AppSettings by mutableStateOf(initialSettings)
        private set

    /** The person's own themes, in the order they were made. */
    var custom: List<Theme> by mutableStateOf(initialCustom)
        private set

    /** Reads the saved settings and themes; call once at start. */
    suspend fun load() {
        val config = store ?: return
        settings = SettingsCodec.decode(config.read(AppSettings.FILE_NAME)?.decodeToString())
        custom =
            config
                .read(THEMES_FILE)
                ?.decodeToString()
                ?.let(ThemeCodec::decodeAll)
                .orEmpty()
    }

    fun update(next: AppSettings) {
        settings = next
        persist(AppSettings.FILE_NAME, SettingsCodec.encode(next))
    }

    /** Saves [theme] as a custom theme (replacing one with the same id) and makes it the chosen theme. */
    fun saveTheme(theme: Theme) {
        require(!theme.builtIn) { "a built-in theme is read-only; duplicate it" }
        custom = custom.filter { it.id != theme.id } + theme
        persist(THEMES_FILE, ThemeCodec.encodeAll(custom))
        update(settings.copy(themeId = theme.id))
    }

    /** Deletes a custom theme; if it was the chosen one the theme goes back to System. */
    fun deleteTheme(id: String) {
        custom = custom.filter { it.id != id }
        persist(THEMES_FILE, ThemeCodec.encodeAll(custom))
        if (settings.themeId == id) update(settings.copy(themeId = AppSettings.SYSTEM_THEME))
    }

    /** The theme in force: the chosen built-in or custom theme, or for System, Paper by day and Black by night. */
    fun resolve(systemDark: Boolean): Theme =
        when (val id = settings.themeId) {
            AppSettings.SYSTEM_THEME -> if (systemDark) Themes.BLACK else Themes.PAPER
            else -> Themes.byId(id) ?: custom.firstOrNull { it.id == id } ?: if (systemDark) Themes.BLACK else Themes.PAPER
        }

    /** A fresh id for a custom theme named [name] that no existing theme has. */
    fun freshId(name: String): String {
        val base = name.lowercase().filter { it.isLetterOrDigit() }.ifEmpty { "theme" }
        val taken = (Themes.BUILT_IN + custom).map { it.id }.toSet()
        if (base !in taken) return base
        return generateSequence(2) { it + 1 }.map { "$base$it" }.first { it !in taken }
    }

    private fun persist(
        name: String,
        text: String,
    ) {
        val config = store ?: return
        scope?.launch { config.writeAtomic(name, text.encodeToByteArray()) }
    }

    companion object {
        const val THEMES_FILE = "themes.json"
    }
}

/** The appearance every screen reads; null where nothing provided one (a test), which means the defaults. */
val LocalAppearance = staticCompositionLocalOf<AppearanceState?> { null }
