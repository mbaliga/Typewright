// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

/**
 * A synchronous, read-only snapshot of a project's files: what a build, an export or a zip
 * reads. It is deliberately separate from the writable [ProjectStore], because Android's and the
 * browser's storage can't offer synchronous reads; a session hands out a consistent in-memory
 * snapshot instead, so an autosave can never race a build.
 */
interface ProjectFiles {
    /** The project's name as the UI shows it. */
    val displayName: String

    /** Every file as a relative, `/`-separated path (see [ProjectPath]), sorted. */
    fun listFiles(): List<String>

    /** The bytes of one file; [path] is one of [listFiles]. */
    fun readBytes(path: String): ByteArray
}

/**
 * A [ProjectFiles] held in memory. Every key must be a valid [ProjectPath]. [readBytes] returns a
 * copy, so a caller can't change the snapshot through it.
 */
class InMemoryProjectFiles(
    override val displayName: String,
    val files: Map<String, ByteArray>,
) : ProjectFiles {
    private val sortedPaths: List<String>

    init {
        files.keys.forEach(ProjectPath::validate)
        sortedPaths = files.keys.sorted()
    }

    override fun listFiles(): List<String> = sortedPaths

    override fun readBytes(path: String): ByteArray {
        val bytes = files[path] ?: throw NoSuchElementException("No file \"$path\" in \"$displayName\"")
        return bytes.copyOf()
    }
}
