// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * A project file written by a newer Typewright than this one: [found] is its `format_version`,
 * [supported] the newest this build reads. Opening such a project read-only keeps the newer
 * file intact.
 */
class NewerFormatException(
    val found: Int,
    val supported: Int,
    what: String,
) : IllegalArgumentException("$what has format_version $found; this build reads up to $supported")

/** JSON reading shared by the project-file codecs. */
internal object ProjectJson {
    /** Lenient on unknown keys, strict on everything else. */
    val json: Json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = true
        }

    /** [text] parsed as a JSON object, or [IllegalArgumentException] naming [what] when it isn't one. */
    fun parseObject(
        text: String,
        what: String,
    ): JsonObject {
        val element: JsonElement =
            try {
                json.parseToJsonElement(text)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("$what is not valid JSON: ${e.message}", e)
            }
        return element as? JsonObject ?: throw IllegalArgumentException("$what is not a JSON object")
    }

    /**
     * The `format_version` of [obj]: [absentMeans] when the key is missing, otherwise a positive
     * integer no newer than [supported] (a newer one throws [NewerFormatException]).
     */
    fun formatVersion(
        obj: JsonObject,
        supported: Int,
        what: String,
        absentMeans: Int? = null,
    ): Int {
        val raw = obj[FORMAT_VERSION_KEY]
        val version =
            when {
                raw == null && absentMeans != null -> absentMeans
                raw is JsonPrimitive && !raw.isString -> raw.intOrNull
                else -> null
            }
        require(version != null && version >= 1) { "$what has no valid $FORMAT_VERSION_KEY" }
        if (version > supported) throw NewerFormatException(version, supported, what)
        return version
    }

    /** Checks that [obj]'s `format` names [expected]. */
    fun requireFormat(
        obj: JsonObject,
        expected: String,
        what: String,
    ) {
        val format = (obj[FORMAT_KEY] as? JsonPrimitive)?.takeIf { it.isString }?.content
        require(format == expected) { "$what has format \"$format\"; expected \"$expected\"" }
    }

    const val FORMAT_KEY: String = "format"
    const val FORMAT_VERSION_KEY: String = "format_version"
}
