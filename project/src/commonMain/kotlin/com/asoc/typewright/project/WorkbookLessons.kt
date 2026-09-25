// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import com.asoc.typewright.project.scrapbook.ScrapbookManifest
import com.asoc.typewright.project.scrapbook.ScrapbookPin
import com.asoc.typewright.project.scrapbook.ScrapbookPinKind
import com.asoc.typewright.project.scrapbook.stablePinRotationDegrees
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.time.Instant

/**
 * One workbook's saved reflections: the content of `lessons/workbook-<workbook>.json`
 * (docs/PROJECT_MODEL.md §3.3). [workbook] is the lower-case script key, `latn` for Latin.
 */
data class WorkbookLessons(
    val workbook: String,
    val reflections: List<Reflection>,
) {
    /** The reflections saved on [task], oldest first. */
    fun reflectionsFor(task: Int): List<Reflection> = reflections.filter { it.task == task }

    /** The id the next reflection on [task] gets: `r-<task>-<n>`, n one more than the highest for that task. */
    fun nextReflectionId(task: Int): String {
        val pattern = Regex("r-$task-([0-9]{1,9})")
        val highest =
            reflections
                .mapNotNull {
                    pattern
                        .matchEntire(it.id)
                        ?.groupValues
                        ?.get(1)
                        ?.toIntOrNull()
                }.maxOrNull() ?: 0
        return "r-$task-${highest + 1}"
    }

    /** These lessons with a new reflection on [task], saved at [at], appended. */
    fun withReflection(
        task: Int,
        at: Instant,
        text: String,
    ): WorkbookLessons {
        require(task >= 1) { "Workbook tasks are numbered from 1; got $task" }
        require(text.isNotBlank()) { "A reflection needs some text" }
        return copy(reflections = reflections + Reflection(nextReflectionId(task), task, at, text))
    }

    companion object {
        /** An empty lessons record for [workbook]. */
        fun empty(workbook: String): WorkbookLessons = WorkbookLessons(workbook, emptyList())
    }
}

/** One saved reflection: its id `r-<task>-<n>`, the workbook task it answers, when it was saved (UTC, whole seconds) and its text. */
data class Reflection(
    val id: String,
    val task: Int,
    val at: Instant,
    val text: String,
)

/**
 * The JSON codec for `lessons/workbook-<workbook>.json`: keys in schema order, 2-space indent, a
 * trailing newline, byte-identical on every platform. JSON rather than Markdown because a
 * project is UFO 3 and JSON (law 7).
 */
object WorkbookLessonsCodec {
    /** The file's `format` value. */
    const val FORMAT: String = "typewright-lessons"

    /** The newest version this build reads and the one it writes. */
    const val FORMAT_VERSION: Int = 1

    private val WORKBOOK_KEY = Regex("[a-z0-9]+(-[a-z0-9]+)*")

    /** Where [workbook]'s lessons live in a project: `lessons/workbook-<workbook>.json`. */
    fun path(workbook: String): String {
        require(WORKBOOK_KEY.matches(workbook)) { "Workbook keys are lower-case letters and digits; got \"$workbook\"" }
        return "lessons/workbook-$workbook.json"
    }

    /** The file's text for [lessons]. */
    fun encode(lessons: WorkbookLessons): String {
        path(lessons.workbook)
        val obj =
            buildJsonObject {
                put(ProjectJson.FORMAT_KEY, FORMAT)
                put(ProjectJson.FORMAT_VERSION_KEY, FORMAT_VERSION)
                put("workbook", lessons.workbook)
                putJsonArray("reflections") {
                    for (reflection in lessons.reflections) {
                        addJsonObject {
                            put("id", reflection.id)
                            put("task", reflection.task)
                            put("at", ProjectTimestamps.format(reflection.at))
                            put("text", reflection.text)
                        }
                    }
                }
            }
        return CanonicalJson.encode(obj)
    }

    /**
     * Parses a lessons file. Unknown keys are ignored; a missing or mistyped field, or a
     * timestamp not in project form, throws [IllegalArgumentException]; a newer
     * `format_version` throws [NewerFormatException].
     */
    fun decode(text: String): WorkbookLessons {
        val what = "lessons file"
        val obj = ProjectJson.parseObject(text, what)
        ProjectJson.requireFormat(obj, FORMAT, what)
        ProjectJson.formatVersion(obj, FORMAT_VERSION, what)
        val workbook = obj.string("workbook", what)
        path(workbook)
        val reflections =
            (obj["reflections"] as? JsonArray ?: throw IllegalArgumentException("$what has no reflections array")).mapIndexed {
                index,
                element,
                ->
                val item = element as? JsonObject ?: throw IllegalArgumentException("$what reflection $index is not an object")
                val where = "$what reflection $index"
                Reflection(
                    id = item.string("id", where),
                    task = item.int("task", where),
                    at = ProjectTimestamps.parse(item.string("at", where)),
                    text = item.string("text", where),
                )
            }
        return WorkbookLessons(workbook, reflections)
    }

    private fun JsonObject.string(
        key: String,
        where: String,
    ): String {
        val value = this[key] as? JsonPrimitive
        require(value != null && value.isString) { "$where needs a string \"$key\"" }
        return value.content
    }

    private fun JsonObject.int(
        key: String,
        where: String,
    ): Int {
        val value = this[key] as? JsonPrimitive
        val number = if (value != null && !value.isString) value.intOrNull else null
        require(number != null) { "$where needs an integer \"$key\"" }
        return number
    }
}

/**
 * [reflection] as the note pin the scrapbook board shows for it: caption
 * "Reflection · Task N", rotation [stablePinRotationDegrees] of its id. Reflections are never
 * copied into the manifest; this view is derived each time.
 */
fun reflectionPin(reflection: Reflection): ScrapbookPin =
    ScrapbookPin(
        id = reflection.id,
        kind = ScrapbookPinKind.NOTE,
        captionTitle = "Reflection · Task ${reflection.task}",
        captionSource = "note",
        noteText = reflection.text,
        rotationDegrees = stablePinRotationDegrees(reflection.id),
        drivesDesign = false,
    )

/** Everything the scrapbook board shows: the manifest's pins, then each workbook's reflections (workbooks in [lessons] order) as note pins. */
fun scrapbookBoard(
    manifest: ScrapbookManifest,
    lessons: Collection<WorkbookLessons>,
): List<ScrapbookPin> = manifest.pins + lessons.flatMap { it.reflections.map(::reflectionPin) }
