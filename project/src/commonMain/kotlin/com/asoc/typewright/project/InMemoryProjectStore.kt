// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A [ProjectStore] held in memory: the web's fallback where the File System Access API is
 * missing (the project lives only as long as the tab, [Durability.TAB_ONLY]) and the store tests
 * use. A put into a map is already atomic, so a write is wholly old or wholly new. [initialFiles]
 * seeds it, for example with an unzipped project; directories are tracked so empty ones survive.
 */
class InMemoryProjectStore(
    name: String,
    initialFiles: Map<String, ByteArray> = emptyMap(),
) : ProjectStore {
    override val location: ProjectLocation = ProjectLocation.InMemory(name)
    override val displayName: String = name
    override val durability: Durability = Durability.TAB_ONLY

    private val mutex = Mutex()
    private val files = mutableMapOf<String, ByteArray>()
    private val directories = mutableSetOf<String>()
    private var leased = false

    init {
        for ((path, bytes) in initialFiles) {
            ProjectPath.validate(path)
            files[path] = bytes.copyOf()
            addAncestors(path)
        }
    }

    override suspend fun list(): List<String> = mutex.withLock { files.keys.filterNot(ProjectPath::isInGitDirectory).sorted() }

    override suspend fun read(path: String): ByteArray? {
        ProjectPath.validate(path)
        return mutex.withLock { files[path]?.copyOf() }
    }

    override suspend fun writeAtomic(
        path: String,
        bytes: ByteArray,
    ) {
        ProjectPath.validateWritable(path)
        mutex.withLock {
            require(path !in directories) { "\"$path\" is a directory" }
            requireNoFileAncestor(path)
            files[path] = bytes.copyOf()
            addAncestors(path)
        }
    }

    override suspend fun delete(path: String) {
        ProjectPath.validate(path)
        mutex.withLock {
            files.remove(path)
            var directory = ProjectPath.parent(path)
            while (isUnderLocks(directory) && isEmptyDirectory(directory)) {
                directories.remove(directory)
                directory = if ('/' in directory) ProjectPath.parent(directory) else ""
            }
        }
    }

    override suspend fun ensureDirectory(path: String) {
        ProjectPath.validate(path)
        mutex.withLock {
            require(path !in files) { "\"$path\" is a file" }
            requireNoFileAncestor(path)
            directories += path
            addAncestors(path)
        }
    }

    override suspend fun recover(): RecoveryReport =
        mutex.withLock {
            val plan = RecoveryPlan.of(files.keys)
            plan.discard.forEach(files::remove)
            for (newPath in plan.rollForward) {
                files[RecoveryPlan.targetOf(newPath)] = files.remove(newPath)!!
            }
            RecoveryReport(rolledForward = plan.rollForward.map(RecoveryPlan::targetOf), discarded = plan.discard)
        }

    override suspend fun acquireWriteLease(): WriteLease? =
        mutex.withLock {
            if (leased) {
                null
            } else {
                leased = true
                OnceLease { mutex.withLock { leased = false } }
            }
        }

    /** Every directory, including empty ones, sorted. */
    suspend fun directories(): List<String> = mutex.withLock { directories.sorted() }

    /** A consistent copy of every file. */
    suspend fun snapshot(): InMemoryProjectFiles =
        mutex.withLock { InMemoryProjectFiles(displayName, files.mapValues { it.value.copyOf() }) }

    private fun addAncestors(path: String) {
        var slash = path.indexOf('/')
        while (slash >= 0) {
            directories += path.substring(0, slash)
            slash = path.indexOf('/', slash + 1)
        }
    }

    private fun requireNoFileAncestor(path: String) {
        var slash = path.indexOf('/')
        while (slash >= 0) {
            val ancestor = path.substring(0, slash)
            require(ancestor !in files) { "\"$ancestor\" is a file, so \"$path\" can't be inside it" }
            slash = path.indexOf('/', slash + 1)
        }
    }

    private fun isEmptyDirectory(directory: String): Boolean {
        val prefix = "$directory/"
        return files.keys.none { it.startsWith(prefix) } && directories.none { it.startsWith(prefix) }
    }
}
