// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

/** Thrown by [CrashingDocumentOps] where a real process would have died. */
class SimulatedCrash : RuntimeException("simulated crash")

/**
 * A [DocumentOps] over plain maps that dies at its [crashAt]th operation (1-based), the way a
 * process killed mid-save would: that operation doesn't happen, except a write, which leaves the
 * first half of its bytes behind, and every later operation fails too. [restart] gives the
 * storage as it survived, working normally, for recovery to run on.
 */
class CrashingDocumentOps(
    files: Map<String, ByteArray> = emptyMap(),
    directories: Set<String> = emptySet(),
    private val crashAt: Int = Int.MAX_VALUE,
) : DocumentOps {
    val files: MutableMap<String, ByteArray> = files.mapValuesTo(mutableMapOf()) { it.value.copyOf() }
    val directories: MutableSet<String> = directories.toMutableSet()

    /** Operations started so far, including the one that crashed. */
    var operations: Int = 0
        private set

    /** Whether the crash has happened. */
    var crashed: Boolean = false
        private set

    /** The storage after a restart: what survived, with no crash pending. */
    fun restart(crashAt: Int = Int.MAX_VALUE): CrashingDocumentOps = CrashingDocumentOps(files, directories, crashAt)

    override suspend fun list(): List<String> {
        step()
        return files.keys.toList()
    }

    override suspend fun read(path: String): ByteArray? {
        step()
        return files[path]?.copyOf()
    }

    override suspend fun writeTruncate(
        path: String,
        bytes: ByteArray,
    ) {
        check(parentOf(path).let { it.isEmpty() || it in directories }) { "No directory for $path" }
        if (isCrashPoint()) {
            files[path] = bytes.copyOf(bytes.size / 2)
            throw SimulatedCrash()
        }
        files[path] = bytes.copyOf()
    }

    override suspend fun rename(
        path: String,
        newName: String,
    ) {
        step()
        val parent = parentOf(path)
        val target = if (parent.isEmpty()) newName else "$parent/$newName"
        check(target !in files && target !in directories) { "$target exists" }
        files[target] = checkNotNull(files.remove(path)) { "$path is missing" }
    }

    override suspend fun delete(path: String) {
        step()
        if (files.remove(path) == null && path in directories) {
            check(files.keys.none { it.startsWith("$path/") } && directories.none { it.startsWith("$path/") }) { "$path is not empty" }
            directories.remove(path)
        }
    }

    override suspend fun ensureDirectory(path: String) {
        step()
        var slash = path.indexOf('/')
        while (slash >= 0) {
            directories += path.substring(0, slash)
            slash = path.indexOf('/', slash + 1)
        }
        directories += path
    }

    private fun step() {
        if (isCrashPoint()) throw SimulatedCrash()
    }

    private fun isCrashPoint(): Boolean {
        if (crashed) throw SimulatedCrash()
        operations++
        if (operations == crashAt) crashed = true
        return crashed
    }

    private fun parentOf(path: String): String = if ('/' in path) path.substringBeforeLast('/') else ""
}
