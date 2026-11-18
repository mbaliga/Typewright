// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

/**
 * Path arithmetic for sandboxed platforms whose app-container directory is not stable across
 * launches: an iOS app's `Documents` folder lives under a container UUID that can change when the
 * app is updated or restored from a backup, so a [ProjectLocation.FileSystem] path saved in
 * recents can name a stale container. Pure string logic, so it is tested on the JVM and used by
 * the iOS storage provider (docs/IOS_PORT.md "Storage").
 */
object SandboxPaths {
    /** The folder name every sandboxed project lives under, directly inside the documents directory. */
    const val PROJECTS_FOLDER: String = "Typewright Projects"

    /**
     * Re-roots [savedPath] onto [documentsDir] when it points inside some other container's
     * `Documents` folder; returns [savedPath] unchanged when it already is under [documentsDir],
     * or when it contains no `/Documents/` segment to re-root from.
     */
    fun remapIntoDocuments(
        savedPath: String,
        documentsDir: String,
    ): String {
        val documents = documentsDir.trimEnd('/')
        if (savedPath == documents || savedPath.startsWith("$documents/")) return savedPath
        val marker = "/Documents/"
        val at = savedPath.lastIndexOf(marker)
        if (at < 0) return savedPath
        val tail = savedPath.substring(at + marker.length).trimStart('/')
        return if (tail.isEmpty()) documents else "$documents/$tail"
    }

    /** The last path component of [path], for a project's display name. */
    fun displayName(path: String): String = path.trimEnd('/').substringAfterLast('/')

    /** [name] with path separators and control characters replaced, so it names exactly one folder. */
    fun safeChildName(name: String): String {
        val cleaned = name.map { if (it == '/' || it == '\\' || it.code < 0x20) '-' else it }.joinToString("").trim()
        return if (cleaned.isEmpty() || cleaned == "." || cleaned == "..") "Untitled" else cleaned
    }
}
