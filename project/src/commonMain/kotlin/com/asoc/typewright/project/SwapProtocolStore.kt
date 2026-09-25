// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * A [ProjectStore] for backends that can't rename over an existing file (Android's SAF), built
 * on [DocumentOps] with the swap protocol of docs/PROJECT_MODEL.md §5:
 *
 * 1. write `X.tmp` and sync it;
 * 2. rename `X.tmp` to `X.new`;
 * 3. delete `X`;
 * 4. rename `X.new` to `X`.
 *
 * A `.tmp` may be partial, so [recover] always discards it. A `.new` exists only after a
 * completed, synced write, so [recover] always rolls it forward. A crash between any two steps
 * therefore leaves `X` wholly old or wholly new once [recover] has run, with no `.tmp` or `.new`
 * behind. [recover] must run before the first write after opening, as a session's open does.
 * It acts only where `X` is a file the project format writes ([RecoveryPlan]).
 *
 * Only one write lease per [location] is granted within the process.
 */
class SwapProtocolStore(
    private val ops: DocumentOps,
    override val location: ProjectLocation,
    override val displayName: String,
) : ProjectStore {
    override val durability: Durability = Durability.PERSISTENT

    private val mutex = Mutex()

    // Paths whose `.new` is complete but whose last two steps failed with an exception in this
    // instance (a crash leaves them to recover()). Every operation tries to finish them first.
    private val unfinished = mutableSetOf<String>()

    override suspend fun list(): List<String> =
        mutex.withLock {
            settle()
            val pendingNew = unfinished.map { it + ProjectPath.NEW_SUFFIX }.toSet()
            ops
                .list()
                .map { if (it in pendingNew) RecoveryPlan.targetOf(it) else it }
                .filter { ProjectPath.isValid(it) && !ProjectPath.isInGitDirectory(it) }
                .distinct()
                .sorted()
        }

    override suspend fun read(path: String): ByteArray? {
        ProjectPath.validate(path)
        return mutex.withLock {
            settle()
            if (path in unfinished) ops.read(path + ProjectPath.NEW_SUFFIX) else ops.read(path)
        }
    }

    override suspend fun writeAtomic(
        path: String,
        bytes: ByteArray,
    ) {
        ProjectPath.validateWritable(path)
        mutex.withLock {
            settle()
            check(path !in unfinished) { "The previous write of \"$path\" could not be finished; its .new is kept for recovery" }
            val parent = ProjectPath.parent(path)
            if (parent.isNotEmpty()) ops.ensureDirectory(parent)
            val name = ProjectPath.name(path)
            val tmp = path + ProjectPath.TMP_SUFFIX
            try {
                ops.writeTruncate(tmp, bytes)
                ops.rename(tmp, name + ProjectPath.NEW_SUFFIX)
            } catch (failure: Throwable) {
                withContext(NonCancellable) { runCatching { ops.delete(tmp) } }
                throw failure
            }
            // From here the .new is complete, so finishing is always correct; cancellation must
            // not stop half-way.
            unfinished += path
            withContext(NonCancellable) { finish(path) }
        }
    }

    override suspend fun delete(path: String) {
        ProjectPath.validate(path)
        mutex.withLock {
            settle()
            if (path in unfinished) {
                ops.delete(path + ProjectPath.NEW_SUFFIX)
                unfinished -= path
            }
            ops.delete(path)
            pruneEmptyLockDirectories(ProjectPath.parent(path))
        }
    }

    override suspend fun ensureDirectory(path: String) {
        ProjectPath.validate(path)
        mutex.withLock { ops.ensureDirectory(path) }
    }

    override suspend fun recover(): RecoveryReport =
        mutex.withLock {
            val plan = RecoveryPlan.of(ops.list().filter(ProjectPath::isValid))
            for (temp in plan.discard) ops.delete(temp)
            for (newPath in plan.rollForward) {
                val target = RecoveryPlan.targetOf(newPath)
                ops.delete(target)
                ops.rename(newPath, ProjectPath.name(target))
            }
            unfinished.clear()
            RecoveryReport(rolledForward = plan.rollForward.map(RecoveryPlan::targetOf), discarded = plan.discard)
        }

    override suspend fun acquireWriteLease(): WriteLease? = ProcessWriteLeases.tryAcquire(location)

    private suspend fun finish(path: String) {
        ops.delete(path)
        ops.rename(path + ProjectPath.NEW_SUFFIX, ProjectPath.name(path))
        unfinished -= path
    }

    // Best effort: a path that still can't be finished stays pending, and reads use its .new.
    private suspend fun settle() {
        for (path in unfinished.toList()) {
            runCatching { finish(path) }
        }
    }

    private suspend fun pruneEmptyLockDirectories(startDirectory: String) {
        if (!isUnderLocks(startDirectory)) return
        val remaining = ops.list()
        var directory = startDirectory
        while (isUnderLocks(directory) && remaining.none { it.startsWith("$directory/") }) {
            ops.delete(directory)
            directory = if ('/' in directory) ProjectPath.parent(directory) else ""
        }
    }
}

/** Whether [directory] is `locks/` or inside it: the only tree where stores prune directories left empty. */
internal fun isUnderLocks(directory: String): Boolean = directory == LOCKS_DIRECTORY || directory.startsWith("$LOCKS_DIRECTORY/")

/** The law-1 directory: approved outlines and their diffs. */
internal const val LOCKS_DIRECTORY: String = "locks"
