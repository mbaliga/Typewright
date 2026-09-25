// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The JSON codec for the app's `recent-projects.json` (docs/PROJECT_MODEL.md §5.4), outside any
 * project. [ProjectLocation]'s own `@Serializable` shape (`{"kind": …, …}`) already matches the
 * schema, so its object is delegated to [ProjectJson]'s serializer; everything else is written
 * through [CanonicalJson] like a project file, for the same byte-stable reason.
 */
internal object RecentProjectsCodec {
    const val FILE_NAME: String = "recent-projects.json"
    private const val FORMAT: String = "typewright-recent-projects"
    private const val FORMAT_VERSION: Int = 1

    fun encode(file: RecentProjectsFile): String {
        val obj =
            buildJsonObject {
                put(ProjectJson.FORMAT_KEY, FORMAT)
                put(ProjectJson.FORMAT_VERSION_KEY, FORMAT_VERSION)
                put("last_open", file.lastOpen)
                putJsonArray("projects") {
                    for (project in file.projects) {
                        addJsonObject { writeProject(project) }
                    }
                }
            }
        return CanonicalJson.encode(obj)
    }

    fun decode(text: String): RecentProjectsFile {
        val what = FILE_NAME
        val obj = ProjectJson.parseObject(text, what)
        ProjectJson.requireFormat(obj, FORMAT, what)
        ProjectJson.formatVersion(obj, FORMAT_VERSION, what)
        val lastOpen = (obj["last_open"] as? JsonPrimitive)?.intOrNull
        val projectsArray = obj["projects"] as? kotlinx.serialization.json.JsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())
        val projects = projectsArray.map { readProject(it as JsonObject) }
        return RecentProjectsFile(projects, lastOpen)
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.writeProject(project: RecentProject) {
        put("location", ProjectJson.json.encodeToJsonElement(ProjectLocation.serializer(), project.location))
        put("name", project.name)
        put("last_opened", ProjectTimestamps.format(project.lastOpened))
        put("last_edited", project.lastEdited?.let(ProjectTimestamps::format))
        putJsonObject("resume") {
            put("section", project.resume?.section)
            put("master", project.resume?.master)
            put("glyph", project.resume?.glyph)
        }
        putJsonObject("summary") {
            put("glyphs", project.summary?.glyphs)
            put("outliers", project.summary?.outliers)
            put("style_class", project.summary?.styleClass)
        }
    }

    private fun readProject(obj: JsonObject): RecentProject {
        val what = "projects"
        val location = ProjectJson.json.decodeFromJsonElement(ProjectLocation.serializer(), obj.getValue("location"))
        val resumeObj = obj["resume"] as? JsonObject
        val resume =
            resumeObj?.let {
                val section = it.stringOrNull("section")
                if (section == null) null else ResumePoint(section, it.stringOrNull("master"), it.stringOrNull("glyph"))
            }
        val summaryObj = obj["summary"] as? JsonObject
        val summary =
            summaryObj?.let {
                val glyphs = (it["glyphs"] as? JsonPrimitive)?.intOrNull
                if (glyphs ==
                    null
                ) {
                    null
                } else {
                    ProjectSummary(glyphs, (it["outliers"] as? JsonPrimitive)?.intOrNull ?: 0, it.stringOrNull("style_class"))
                }
            }
        return RecentProject(
            location = location,
            name = obj.string("name", what),
            lastOpened = ProjectTimestamps.parse(obj.string("last_opened", what)),
            lastEdited = obj.stringOrNull("last_edited")?.let(ProjectTimestamps::parse),
            resume = resume,
            summary = summary,
        )
    }

    private fun JsonObject.string(
        key: String,
        where: String,
    ): String {
        val value = this[key] as? JsonPrimitive
        require(value != null && value.isString) { "$where needs a string \"$key\"" }
        return value.content
    }

    private fun JsonObject.stringOrNull(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
