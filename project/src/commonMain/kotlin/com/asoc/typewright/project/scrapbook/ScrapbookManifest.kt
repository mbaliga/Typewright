// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project.scrapbook

import com.asoc.typewright.project.CanonicalJson
import com.asoc.typewright.project.ProjectDoubleSerializer
import com.asoc.typewright.project.ProjectJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * A scrapbook pin's kind: a photo, scan or swatch, or a written note. Finer distinctions
 * ("photo", "Devanagari", "scan", "drawn", …) are not kinds; they live in
 * [ScrapbookPin.captionSource], a free label. The kind only decides how a pin's image block is
 * drawn: the image for [PHOTO], the pin's [ScrapbookPin.noteText] for [NOTE].
 */
@Serializable
enum class ScrapbookPinKind {
    @SerialName("photo")
    PHOTO,

    @SerialName("note")
    NOTE,
}

/**
 * One pin on the scrapbook board.
 *
 * - [captionTitle] and [captionSource] are the caption's title and its small source label.
 * - [imagePath] is the pinned image, relative to the project root (`scrapbook/<id>.<ext>`,
 *   stored as the user gave it); null for a note.
 * - [noteText] is a note pin's text; null for a photo.
 * - [rotationDegrees] is the small hand-pinned tilt. It is stored, not recomputed: a new pin
 *   gets [stablePinRotationDegrees] of its id once, and a hand-edited value is kept as written.
 *   It is written and read by [ProjectDoubleSerializer], so its bytes are the same on every
 *   platform.
 * - [drivesDesign] marks a pin that drives the design. The board draws it with the violet
 *   selected mark (law 8), so it means something rather than decorating.
 */
@Serializable
data class ScrapbookPin(
    val id: String,
    val kind: ScrapbookPinKind,
    @SerialName("caption_title") val captionTitle: String,
    @SerialName("caption_source") val captionSource: String,
    @SerialName("image_path") val imagePath: String? = null,
    @SerialName("note_text") val noteText: String? = null,
    @SerialName("rotation_degrees") @Serializable(with = ProjectDoubleSerializer::class) val rotationDegrees: Double = 0.0,
    @SerialName("drives_design") val drivesDesign: Boolean = false,
)

/**
 * A project's `scrapbook/manifest.json` (docs/PROJECT_MODEL.md §3.2): its [pins], in the order
 * they were pinned. Workbook reflections are not stored here; the board adds them from
 * `lessons/`, so each fact has one source. The file's `format_version` belongs to the file, not
 * to the model: [ScrapbookManifestCodec] writes [FORMAT_VERSION] and reads a manifest without
 * one as version 1.
 */
@Serializable
data class ScrapbookManifest(
    val pins: List<ScrapbookPin> = emptyList(),
) {
    /** How many pins drive the design, for the board's "n pins · d driving the design" line. */
    val drivingCount: Int get() = pins.count { it.drivesDesign }

    /** The id for the next new pin: `pin-<n>`, n one more than the highest existing `pin-<n>` (so tests are deterministic). */
    fun nextPinId(): String {
        val highest =
            pins
                .mapNotNull {
                    PIN_ID
                        .matchEntire(it.id)
                        ?.groupValues
                        ?.get(1)
                        ?.toIntOrNull()
                }.maxOrNull() ?: 0
        return "pin-${highest + 1}"
    }

    companion object {
        /** The newest manifest version this build reads and the one it writes. */
        const val FORMAT_VERSION: Int = 1

        /** Where the manifest lives in a project. */
        const val PATH: String = "scrapbook/manifest.json"

        private val PIN_ID = Regex("pin-([0-9]{1,9})")
    }
}

/**
 * The JSON codec for `scrapbook/manifest.json`: `format_version` first, then `pins`, with
 * snake_case keys in schema order, 2-space indent, explicit nulls and a trailing newline,
 * byte-identical on every platform, because a person may open and hand-edit the file (law 7).
 */
object ScrapbookManifestCodec {
    private val pinsSerializer = ListSerializer(ScrapbookPin.serializer())

    /** The file's text for [manifest]. */
    fun encode(manifest: ScrapbookManifest): String =
        CanonicalJson.encode(
            buildJsonObject {
                put(ProjectJson.FORMAT_VERSION_KEY, JsonPrimitive(ScrapbookManifest.FORMAT_VERSION))
                put(PINS_KEY, ProjectJson.json.encodeToJsonElement(pinsSerializer, manifest.pins))
            },
        )

    /**
     * Parses a manifest. Unknown keys are ignored, so a newer minor addition doesn't break an
     * older build; a newer `format_version` throws [com.asoc.typewright.project.NewerFormatException].
     */
    fun decode(text: String): ScrapbookManifest {
        val obj = ProjectJson.parseObject(text, ScrapbookManifest.PATH)
        ProjectJson.formatVersion(obj, ScrapbookManifest.FORMAT_VERSION, ScrapbookManifest.PATH, absentMeans = 1)
        val pins = obj[PINS_KEY] ?: return ScrapbookManifest()
        require(pins is JsonArray) { "${ScrapbookManifest.PATH}: \"$PINS_KEY\" is not a list" }
        return ScrapbookManifest(ProjectJson.json.decodeFromJsonElement(pinsSerializer, pins))
    }

    private const val PINS_KEY = "pins"
}

/** How far either side of upright [stablePinRotationDegrees] tilts a pin: enough to look hand-pinned, not enough to hurt the caption. */
const val PIN_ROTATION_RANGE_DEGREES: Double = 4.0

/**
 * A small, stable hand-pinned tilt for the pin [id], within ±[PIN_ROTATION_RANGE_DEGREES]. The
 * same id always gives the same degrees on every platform, so a pin doesn't jump between
 * recompositions or reopens; a random source would. A new pin stores this value once.
 */
fun stablePinRotationDegrees(id: String): Double {
    val fraction = (stablePinHash(id) % 1000) / 1000.0
    return (fraction * 2.0 - 1.0) * PIN_ROTATION_RANGE_DEGREES
}

/**
 * A small, portable, non-negative hash of [id]: a base-31 polynomial over UTF-16 code units.
 * Not [String.hashCode], which only the JVM specifies; this value must agree on every target.
 */
fun stablePinHash(id: String): Int {
    var hash = 0
    for (unit in id) hash = (hash * 31 + unit.code) and 0x7fffffff
    return hash
}
