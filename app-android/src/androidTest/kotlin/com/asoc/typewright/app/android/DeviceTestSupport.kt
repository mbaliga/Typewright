// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.android

import android.content.Context
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.core.font.ufo.UfoProject
import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.ContourPoint
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.project.Brief
import com.asoc.typewright.project.BriefSource
import com.asoc.typewright.project.NewMaster
import com.asoc.typewright.project.NewProjectSpec
import com.asoc.typewright.project.ProjectLocation
import com.asoc.typewright.project.StyleClass
import java.io.File
import java.security.MessageDigest
import java.util.regex.Pattern

/**
 * Support shared by [ProjectSaveDeviceTest] and [ProjectRestoreDeviceTest] (docs/PROJECT_MODEL.md
 * §13, owner-run device tests, CLAUDE.md law 4): the fixture project, hashing the files the save
 * test leaves behind, the small on-device record the restore test reads back, and driving SAF's
 * system folder-picker with UiAutomator.
 */
internal object DeviceTestSupport {
    /** The project name a save run creates: `TypewrightDeviceTest-<epoch millis>`. */
    fun freshProjectName(clock: () -> Long = System::currentTimeMillis): String = "TypewrightDeviceTest-${clock()}"

    /** A minimal but real two-glyph, one-master [NewProjectSpec] named [name] -- enough shape to approve, unlock, edit and kern. */
    fun fixtureSpec(name: String): NewProjectSpec =
        NewProjectSpec(
            name = name,
            brief = Brief(source = BriefSource.BLANK, styleClass = StyleClass(declared = "sans-geometric", confirmed = null)),
            scripts = listOf("Latn"),
            masters = listOf(NewMaster(id = "regular", styleName = "Regular", ufo = fixtureUfo())),
        )

    private fun fixtureUfo(): UfoProject =
        UfoProject(
            fontInfo = UfoFontInfo(familyName = "Device Test", styleName = "Regular", unitsPerEm = 1000),
            glyphs = listOf(fixtureGlyph("A", dx = 0), fixtureGlyph("B", dx = 20)),
        )

    private fun fixtureGlyph(
        name: String,
        dx: Int,
    ): Glyph = Glyph(name, advanceWidth = 500, contours = listOf(squareContour(50 + dx, 0, 450 + dx, 700)))

    /** A different square (bigger) for [ProjectSaveDeviceTest]'s own edit step, so the replaced outline is really a different shape. */
    fun editedContour(): Contour = squareContour(40, 0, 460, 720)

    private fun squareContour(
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
    ): Contour {
        val corners = listOf(Point(x0, y0), Point(x1, y0), Point(x1, y1), Point(x0, y1))
        val points = mutableListOf<ContourPoint>()
        for (index in corners.indices) {
            val start = corners[index]
            val end = corners[(index + 1) % corners.size]
            points += ContourPoint(start, onCurve = true)
            points += ContourPoint(start, onCurve = false)
            points += ContourPoint(end, onCurve = false)
        }
        return Contour(points, CurveFormat.CUBIC)
    }

    /** SHA-256 of [paths]' content, read from [store], as lowercase hex. */
    suspend fun hashFiles(
        paths: List<String>,
        read: suspend (String) -> ByteArray?,
    ): Map<String, String> {
        val digest = MessageDigest.getInstance("SHA-256")
        return paths.associateWith { path ->
            val bytes = read(path) ?: error("\"$path\" was listed but could not be read")
            digest.reset()
            digest.digest(bytes).joinToString("") { "%02x".format(it) }
        }
    }

    /** Where the save test's record lives: `noBackupFilesDir/device-test/p11.json`. */
    fun recordFile(context: Context): File = File(context.noBackupFilesDir, "device-test/p11.json")

    /** Writes [DeviceTestRecord] as small, hand-rolled JSON (no need for a JSON library just for this bookkeeping file). */
    fun writeRecord(
        context: Context,
        record: DeviceTestRecord,
    ) {
        val file = recordFile(context)
        file.parentFile?.mkdirs()
        val hashesJson = record.fileHashes.entries.joinToString(",") { (path, hash) -> "\"${escape(path)}\":\"$hash\"" }
        val json =
            """
            {"tree_uri":"${escape(record.location.treeUri)}","document_id":"${escape(record.location.documentId)}",
            "name":"${escape(record.projectName)}","hashes":{$hashesJson}}
            """.trimIndent().replace("\n", " ")
        file.writeText(json)
    }

    /** Reads back what [writeRecord] wrote. */
    fun readRecord(context: Context): DeviceTestRecord {
        val json = recordFile(context).readText()
        val treeUri = field(json, "tree_uri")
        val documentId = field(json, "document_id")
        val name = field(json, "name")
        val hashesBlock = json.substringAfter("\"hashes\":{").substringBeforeLast("}")
        val hashes =
            if (hashesBlock.isBlank()) {
                emptyMap()
            } else {
                Regex("\"((?:[^\"\\\\]|\\\\.)*)\":\"([0-9a-f]+)\"")
                    .findAll(hashesBlock)
                    .associate { unescape(it.groupValues[1]) to it.groupValues[2] }
            }
        return DeviceTestRecord(ProjectLocation.SafTree(treeUri, documentId), name, hashes)
    }

    private fun field(
        json: String,
        name: String,
    ): String {
        val match = Regex("\"$name\":\"((?:[^\"\\\\]|\\\\.)*)\"").find(json) ?: error("\"$name\" missing from the device-test record")
        return unescape(match.groupValues[1])
    }

    private fun escape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun unescape(value: String): String = value.replace("\\\"", "\"").replace("\\\\", "\\")

    /**
     * Taps a control found by the first of [resourceIds] that appears, or (SAF/DocumentsUI's
     * resource ids vary across OEM and OS version) by case-insensitive [text] if none of them do.
     * Fails, naming what it looked for, if neither turns anything up within [timeoutMs].
     */
    fun clickByIdOrText(
        device: UiDevice,
        resourceIds: List<String>,
        text: String,
        timeoutMs: Long = 10_000,
    ) {
        val target: UiObject2? =
            resourceIds.firstNotNullOfOrNull { id -> device.wait(Until.findObject(By.res(id)), timeoutMs / resourceIds.size) }
                ?: device.wait(Until.findObject(By.text(Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE))), timeoutMs)
        checkNotNull(target) { "Could not find a control by resource id $resourceIds or by text \"$text\" within ${timeoutMs}ms" }
        target.click()
    }
}

/** What [DeviceTestSupport.writeRecord]/[DeviceTestSupport.readRecord] persist across the save/restore processes. */
internal data class DeviceTestRecord(
    val location: ProjectLocation.SafTree,
    val projectName: String,
    val fileHashes: Map<String, String>,
)
