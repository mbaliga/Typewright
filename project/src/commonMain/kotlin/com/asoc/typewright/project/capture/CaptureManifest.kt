// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project.capture

import com.asoc.typewright.project.CanonicalJson
import com.asoc.typewright.project.ProjectJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** How a captured drawing reached the project: photographed, picked or dropped as an image file, or an SVG drawing from a vector tool. */
@Serializable
enum class CaptureKind {
    @SerialName("photo")
    PHOTO,

    @SerialName("file")
    FILE,

    @SerialName("svg")
    SVG,
}

/** A cell the person relabelled: cell number [cell] (from 0) of a sheet holds the glyph [glyph], whatever the template said. */
@Serializable
data class CaptureRelabel(
    val cell: Int,
    val glyph: String,
)

/**
 * One captured thing, stored as the picture or SVG exactly as the person gave it.
 *
 * - [name] is what the row calls it: the file's name, or "Photo" for a camera frame.
 * - [imagePath] is the stored file, `captures/<id>.<ext>`, relative to the project root.
 * - [batch] groups SVG drawings that arrived together, so Capture can show them as one row.
 * - [glyph] is the glyph an SVG drawing is for, resolved from its template file name; null for a sheet photograph, whose cells
 *   say which glyphs it holds.
 * - [relabels] are the cells of a sheet the person renamed.
 * - [script], [sheet] (from 0) and [sheetCount] say which printed sheet a photograph was read as, so the deck can tell how many are
 *   still to come without reading every picture; null for an SVG drawing.
 * - [qualityOverride] is true for a photograph taken with the camera's manual override, which skips the blur, light and angle
 *   limits; reading it again later must skip them too.
 *
 * Only the source is stored. The cells are read from it again on opening, which is deterministic, so what is on screen is
 * always what the picture says and the project holds nothing a person cannot open.
 */
@Serializable
data class CaptureRecord(
    val id: String,
    val kind: CaptureKind,
    val name: String,
    @SerialName("image_path") val imagePath: String,
    val batch: String? = null,
    val glyph: String? = null,
    val relabels: List<CaptureRelabel> = emptyList(),
    val script: String? = null,
    val sheet: Int? = null,
    @SerialName("sheet_count") val sheetCount: Int? = null,
    @SerialName("quality_override") val qualityOverride: Boolean = false,
)

/** A project's `captures/manifest.json`: its [records] in the order they were added. */
@Serializable
data class CaptureManifest(
    val records: List<CaptureRecord> = emptyList(),
) {
    /** The id for the next new record: `cap-<n>`, n one more than the highest existing number, so tests are deterministic. */
    fun nextId(): String {
        val highest =
            records
                .mapNotNull {
                    ID
                        .matchEntire(it.id)
                        ?.groupValues
                        ?.get(1)
                        ?.toIntOrNull()
                }.maxOrNull() ?: 0
        return "cap-${highest + 1}"
    }

    companion object {
        /** The newest manifest version this build reads and the one it writes. */
        const val FORMAT_VERSION: Int = 1

        /** Where the manifest lives in a project. */
        const val PATH: String = "captures/manifest.json"

        /** The file endings a stored capture may have. */
        val IMAGE_SUFFIXES: List<String> = listOf(".jpg", ".png", ".webp", ".bmp", ".gif", ".svg")

        private val ID = Regex("cap-([0-9]{1,9})")
    }
}

/** The JSON codec for `captures/manifest.json`, in the same canonical form as every other project file (law 7). */
object CaptureManifestCodec {
    private val serializer = ListSerializer(CaptureRecord.serializer())

    /** The file's text for [manifest]. */
    fun encode(manifest: CaptureManifest): String =
        CanonicalJson.encode(
            buildJsonObject {
                put(ProjectJson.FORMAT_VERSION_KEY, JsonPrimitive(CaptureManifest.FORMAT_VERSION))
                put(RECORDS_KEY, ProjectJson.json.encodeToJsonElement(serializer, manifest.records))
            },
        )

    /** Parses a manifest; unknown keys are ignored and a newer `format_version` throws [com.asoc.typewright.project.NewerFormatException]. */
    fun decode(text: String): CaptureManifest {
        val obj = ProjectJson.parseObject(text, CaptureManifest.PATH)
        ProjectJson.formatVersion(obj, CaptureManifest.FORMAT_VERSION, CaptureManifest.PATH, absentMeans = 1)
        val records = obj[RECORDS_KEY] ?: return CaptureManifest()
        require(records is JsonArray) { "${CaptureManifest.PATH}: \"$RECORDS_KEY\" is not a list" }
        return CaptureManifest(ProjectJson.json.decodeFromJsonElement(serializer, records))
    }

    private const val RECORDS_KEY = "records"
}
