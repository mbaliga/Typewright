// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.FileSystemException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes

/**
 * A project directory on the desktop filesystem, used by the desktop app and the golden-path
 * harness. Writes are atomic through [AtomicFiles] (temp file, `force(true)`, atomic rename over
 * the target, best-effort directory sync). Methods block on file I/O; call them from an I/O
 * dispatcher.
 *
 * **Links.** The project is the real directory tree under [root]. [root] itself may be a
 * symbolic link, and is resolved on every call, so [location] and [displayName] keep the path the
 * user chose. Inside the project, symbolic links are not followed: [list] leaves them out,
 * [read] treats a path through one as absent, and [writeAtomic], [delete] and [ensureDirectory]
 * refuse one with a [FileSystemException], so no call reads, writes or deletes anything outside
 * the project.
 *
 * **Write lease.** An OS lock (`FileChannel.tryLock()`) on `build/.session-lock`, so a second
 * Typewright, in this process or another, opens the project read-only; the OS frees the lock
 * if the process dies. Releasing unlocks the file but never deletes it: every claimant then
 * locks the same file, whereas deleting it would let one claimant lock the old file while
 * another creates and locks a new one. It lives in `build/`, which git ignores.
 */
class FileSystemProjectStore(
    root: Path,
) : ProjectStore {
    private val root: Path = root.toAbsolutePath().normalize()

    override val location: ProjectLocation = ProjectLocation.FileSystem(this.root.toString())
    override val displayName: String = this.root.fileName?.toString() ?: this.root.toString()
    override val durability: Durability = Durability.PERSISTENT

    override suspend fun list(): List<String> {
        val base = existingRealRoot() ?: return emptyList()
        if (!Files.isDirectory(base)) return emptyList()
        val paths = ArrayList<String>()
        // No FOLLOW_LINKS: a link inside the project is visited as a non-regular file and left out.
        Files.walkFileTree(
            base,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(
                    dir: Path,
                    attrs: BasicFileAttributes,
                ): FileVisitResult =
                    if (dir != base &&
                        ProjectPath.isInGitDirectory(relative(base, dir))
                    ) {
                        FileVisitResult.SKIP_SUBTREE
                    } else {
                        FileVisitResult.CONTINUE
                    }

                override fun visitFile(
                    file: Path,
                    attrs: BasicFileAttributes,
                ): FileVisitResult {
                    if (attrs.isRegularFile) {
                        val path = relative(base, file)
                        if (ProjectPath.isValid(path)) paths += path
                    }
                    return FileVisitResult.CONTINUE
                }

                // A file removed while the walk runs is simply not listed.
                override fun visitFileFailed(
                    file: Path,
                    exc: IOException,
                ): FileVisitResult = if (exc is NoSuchFileException) FileVisitResult.CONTINUE else throw exc
            },
        )
        return paths.sorted()
    }

    override suspend fun read(path: String): ByteArray? {
        val target = locate(path)
        if (target.throughLink) return null
        return try {
            if (Files.isRegularFile(target.file, LinkOption.NOFOLLOW_LINKS)) Files.readAllBytes(target.file) else null
        } catch (_: NoSuchFileException) {
            null
        }
    }

    override suspend fun writeAtomic(
        path: String,
        bytes: ByteArray,
    ) {
        ProjectPath.validateWritable(path)
        AtomicFiles.write(writable(path), bytes)
    }

    override suspend fun delete(path: String) {
        val file = writable(path)
        if (!Files.deleteIfExists(file)) return
        AtomicFiles.syncDirectory(file.parent)
        val base = realRoot()
        var directory = file.parent
        while (directory != base && isUnderLocks(relative(base, directory)) && AtomicFiles.isEmptyDirectory(directory)) {
            Files.deleteIfExists(directory)
            directory = directory.parent
            AtomicFiles.syncDirectory(directory)
        }
    }

    override suspend fun ensureDirectory(path: String) {
        Files.createDirectories(writable(path))
    }

    override suspend fun recover(): RecoveryReport {
        val plan = RecoveryPlan.of(list())
        for (temp in plan.discard) {
            val file = writable(temp)
            Files.deleteIfExists(file)
            AtomicFiles.syncDirectory(file.parent)
        }
        for (newPath in plan.rollForward) AtomicFiles.moveOver(writable(newPath), writable(RecoveryPlan.targetOf(newPath)))
        return RecoveryReport(rolledForward = plan.rollForward.map(RecoveryPlan::targetOf), discarded = plan.discard)
    }

    override suspend fun acquireWriteLease(): WriteLease? {
        val lockFile = writable(SESSION_LOCK)
        Files.createDirectories(lockFile.parent)
        // One channel per process on the lock file: closing any channel on a file drops every
        // lock this process holds on it (POSIX), so a second in-process claim never opens one.
        val processLease = ProcessWriteLeases.tryAcquire(lockFile.parent.toRealPath().resolve(lockFile.fileName)) ?: return null
        try {
            val channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)
            val lock: FileLock? =
                try {
                    channel.tryLock()
                } catch (_: OverlappingFileLockException) {
                    null
                } catch (e: IOException) {
                    channel.close()
                    throw e
                }
            if (lock == null) {
                channel.close()
                processLease.release()
                return null
            }
            return OnceLease {
                runCatching { lock.release() }
                runCatching { channel.close() }
                processLease.release()
            }
        } catch (e: Throwable) {
            processLease.release()
            throw e
        }
    }

    // The project's real directory, or null while it doesn't exist.
    private fun existingRealRoot(): Path? =
        try {
            root.toRealPath()
        } catch (_: NoSuchFileException) {
            null
        }

    private fun realRoot(): Path = existingRealRoot() ?: root

    private class Target(
        val file: Path,
        val throughLink: Boolean,
    )

    // The file [path] names under the real root, and whether any existing part of it, the last
    // included, is a symbolic link.
    private fun locate(path: String): Target {
        ProjectPath.validate(path)
        var file = realRoot()
        var throughLink = false
        for (segment in path.split('/')) {
            file = file.resolve(segment)
            if (!throughLink && Files.isSymbolicLink(file)) throughLink = true
        }
        return Target(file, throughLink)
    }

    // The file [path] names, for a call that changes it; refused when it goes through a link.
    private fun writable(path: String): Path {
        val target = locate(path)
        if (target.throughLink) {
            throw FileSystemException(
                target.file.toString(),
                null,
                "\"$path\" goes through a symbolic link, which a project doesn't follow",
            )
        }
        return target.file
    }

    private fun relative(
        base: Path,
        file: Path,
    ): String = base.relativize(file).joinToString("/")

    private companion object {
        const val SESSION_LOCK = "build/.session-lock"
    }
}
