// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * Atomic file replacement on a desktop filesystem (docs/PROJECT_MODEL.md §5): write `X.tmp` in
 * the same directory and force it to storage, rename it over `X` in one atomic step, then sync
 * the directory so the rename itself is durable. A crash leaves `X` wholly old or wholly new, plus
 * at most a stray `X.tmp` that recovery deletes.
 */
internal object AtomicFiles {
    // NOFOLLOW_LINKS: a stray X.tmp that is a symbolic link fails the write instead of
    // truncating whatever it points to.
    private val TEMP_OPTIONS =
        arrayOf<OpenOption>(
            StandardOpenOption.WRITE,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            LinkOption.NOFOLLOW_LINKS,
        )

    /** Replaces [target] with [bytes], creating its parent directories. On failure the temp file is removed before the exception propagates. */
    fun write(
        target: Path,
        bytes: ByteArray,
    ) {
        val directory = target.parent
        val created = createDirectories(directory)
        val temp = tempOf(target)
        var moved = false
        try {
            FileChannel.open(temp, *TEMP_OPTIONS).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            moved = true
        } finally {
            if (!moved) runCatching { Files.deleteIfExists(temp) }
        }
        syncDirectory(directory)
        for (made in created) made.parent?.let(::syncDirectory)
    }

    /** Renames [source] over [target] atomically, then syncs the directory. */
    fun moveOver(
        source: Path,
        target: Path,
    ) {
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        target.parent?.let(::syncDirectory)
    }

    /** The temp file a write of [target] uses. */
    fun tempOf(target: Path): Path = target.resolveSibling(target.fileName.toString() + ProjectPath.TMP_SUFFIX)

    /**
     * Forces [directory]'s entries to storage, so a rename or delete inside it survives a power
     * cut. Best effort: some filesystems and platforms can't open a directory for syncing, and
     * then the rename's own atomicity is what remains.
     */
    fun syncDirectory(directory: Path) {
        try {
            FileChannel.open(directory, StandardOpenOption.READ).use { it.force(true) }
        } catch (_: IOException) {
            // Not supported here; the rename is still atomic.
        } catch (_: UnsupportedOperationException) {
            // Same.
        }
    }

    /** Whether [directory] exists and has no entries. */
    fun isEmptyDirectory(directory: Path): Boolean {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) return false
        return try {
            Files.newDirectoryStream(directory).use { !it.iterator().hasNext() }
        } catch (_: NoSuchFileException) {
            false
        }
    }

    // Creates [directory] and its missing parents; returns the ones it made, outermost first.
    private fun createDirectories(directory: Path): List<Path> {
        val missing = ArrayList<Path>()
        var current: Path = directory
        while (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
            missing.add(current)
            current = current.parent ?: break
        }
        if (missing.isNotEmpty()) Files.createDirectories(directory)
        return missing.asReversed()
    }
}
