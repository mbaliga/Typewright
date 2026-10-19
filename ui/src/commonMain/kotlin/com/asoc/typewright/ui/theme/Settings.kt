// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.theme

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

/**
 * The app-level settings that have an effect today (SCREENS_V1 S21). [themeId] is a built-in
 * theme's id, a custom theme's id, or [SYSTEM_THEME] (Paper by day, Black by night); [textScale]
 * scales the UI's text; [haptics] turns the deck's taps and ticks on or off. A setting is added
 * here when something reads it, never before.
 */
data class AppSettings(
    val themeId: String = SYSTEM_THEME,
    val textScale: Double = 1.0,
    val haptics: Boolean = true,
) {
    companion object {
        const val SYSTEM_THEME = "system"
        const val MIN_TEXT_SCALE = 0.85
        const val MAX_TEXT_SCALE = 1.30
        const val FILE_NAME = "settings.json"
    }
}

/** `settings.json` in the app's config. Reading never fails: anything unreadable or out of range is the default. */
object SettingsCodec {
    fun encode(settings: AppSettings): String =
        Json { prettyPrint = true }.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("theme", settings.themeId)
                put("textScale", settings.textScale)
                put("haptics", settings.haptics)
            },
        )

    fun decode(text: String?): AppSettings {
        if (text == null) return AppSettings()
        val obj = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return AppSettings()
        val defaults = AppSettings()
        return AppSettings(
            themeId = (obj["theme"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: defaults.themeId,
            textScale =
                (obj["textScale"] as? JsonPrimitive)
                    ?.doubleOrNull
                    ?.coerceIn(AppSettings.MIN_TEXT_SCALE, AppSettings.MAX_TEXT_SCALE) ?: defaults.textScale,
            haptics = (obj["haptics"] as? JsonPrimitive)?.booleanOrNull ?: defaults.haptics,
        )
    }
}
