// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.theme

import com.asoc.typewright.ui.tokens.Argb
import com.asoc.typewright.ui.tokens.ThemeRole
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

/** What reading a theme file gave: the [Theme], or why it could not be read. */
sealed interface ThemeRead {
    data class Ok(
        val theme: Theme,
    ) : ThemeRead

    data class Bad(
        val reason: String,
    ) : ThemeRead
}

/**
 * A theme as a small JSON file, so it can be exported, imported and shared (law 7):
 * `{"format":"typewright-theme","version":1,"id","name","dark","grain","gridOpacity","colors":{"canvas":"#EFE9DC",…}}`.
 * Reading is strict about what must be there (a ground, an ink and a muted, and valid values) and
 * lenient about the rest: an unknown role is ignored and a missing one takes the built-in
 * default for the theme's ground, so an older or hand-written file still opens. A file read from
 * anywhere is never a built-in.
 */
object ThemeCodec {
    const val FORMAT = "typewright-theme"
    const val VERSION = 1

    private val pretty = Json { prettyPrint = true }

    fun encode(theme: Theme): String = pretty.encodeToString(JsonObject.serializer(), toJson(theme))

    fun toJson(theme: Theme): JsonObject =
        buildJsonObject {
            put("format", FORMAT)
            put("version", VERSION)
            put("id", theme.id)
            put("name", theme.name)
            put("dark", theme.dark)
            put("grain", theme.grain)
            put("gridOpacity", theme.gridOpacity)
            put(
                "colors",
                buildJsonObject { for (role in ThemeRole.entries) put(role.key, hex(theme.color(role))) },
            )
        }

    /** Encodes several themes as one JSON array (the app's saved custom themes). */
    fun encodeAll(themes: List<Theme>): String = pretty.encodeToString(JsonArray.serializer(), JsonArray(themes.map(::toJson)))

    fun decode(text: String): ThemeRead =
        try {
            fromJson(Json.parseToJsonElement(text))
        } catch (e: Exception) {
            ThemeRead.Bad("not a theme file: ${e.message?.lineSequence()?.firstOrNull() ?: "unreadable"}")
        }

    /** Reads an array written by [encodeAll], dropping any entry that cannot be read. */
    fun decodeAll(text: String): List<Theme> =
        try {
            (Json.parseToJsonElement(text) as? JsonArray).orEmpty().mapNotNull { (fromJson(it) as? ThemeRead.Ok)?.theme }
        } catch (_: Exception) {
            emptyList()
        }

    fun fromJson(element: JsonElement): ThemeRead {
        val obj = element as? JsonObject ?: return ThemeRead.Bad("a theme is a JSON object")
        if (obj.string("format") != FORMAT) return ThemeRead.Bad("this is not a Typewright theme (format is not \"$FORMAT\")")
        val id = obj.string("id")?.takeIf { it.isNotBlank() } ?: return ThemeRead.Bad("the theme has no id")
        val name = obj.string("name")?.takeIf { it.isNotBlank() } ?: return ThemeRead.Bad("the theme has no name")
        val dark = (obj["dark"] as? JsonPrimitive)?.booleanOrNull ?: return ThemeRead.Bad("\"dark\" must be true or false")
        val grain = obj.number("grain") ?: return ThemeRead.Bad("\"grain\" must be a number")
        val grid = obj.number("gridOpacity") ?: return ThemeRead.Bad("\"gridOpacity\" must be a number")
        if (grain !in 0.0..Theme.MAX_GRAIN) return ThemeRead.Bad("grain must be between 0 and ${(Theme.MAX_GRAIN * 100).toInt()}%")
        if (grid !in
            0.0..Theme.MAX_GRID_OPACITY
        ) {
            return ThemeRead.Bad("grid opacity must be between 0 and ${(Theme.MAX_GRID_OPACITY * 100).toInt()}%")
        }
        val colorsObj = obj["colors"] as? JsonObject ?: return ThemeRead.Bad("the theme has no colors")
        val read = mutableMapOf<ThemeRole, Argb>()
        for ((key, value) in colorsObj) {
            val role = ThemeRole.byKey(key) ?: continue
            val text = (value as? JsonPrimitive)?.contentOrNull ?: return ThemeRead.Bad("${role.label} is not a colour")
            read[role] = parseHex(text) ?: return ThemeRead.Bad("${role.label} is \"$text\", which is not a #RRGGBB colour")
        }
        for (required in listOf(ThemeRole.CANVAS, ThemeRole.INK, ThemeRole.MUTED)) {
            if (required !in read) return ThemeRead.Bad("${required.label} is missing")
        }
        val base = Themes.defaultRoles(texture = (if (dark) Themes.BLACK else Themes.PAPER).toTexture())
        return ThemeRead.Ok(Theme(id, name, builtIn = false, dark = dark, grain = grain, gridOpacity = grid, colors = base + read))
    }

    fun hex(color: Argb): String {
        val rgb = color.value and 0xFFFFFFL
        return "#" + rgb.toString(16).uppercase().padStart(6, '0')
    }

    /** `#RRGGBB` (case-insensitive) as an opaque colour, or null. */
    fun parseHex(text: String): Argb? {
        val body = text.trim().removePrefix("#")
        if (body.length != 6 || body.any { it !in "0123456789abcdefABCDEF" }) return null
        return Argb.rgb(body.toLong(16))
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.number(key: String): Double? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
}
