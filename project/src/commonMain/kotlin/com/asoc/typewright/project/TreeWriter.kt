// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Writes a tree of files, as a path to its bytes, into one place the person chose: the Ship room's repository folder. It is all or nothing
 * as far as storage allows: when it throws, or the calling coroutine is cancelled before the last step, the place holds what it held before.
 * Files in the place that are not in the tree are left alone, except inside the `ownedDirectories`, which are replaced as a whole so that a
 * file the new tree no longer has (a glyph that was deleted) does not linger in the old copy.
 */
interface TreeWriter {
    /** Where the tree goes, in words the screen shows (`~/Fonts/hyle-deco-gf`). */
    val description: String

    /** Writes [files]; [ownedDirectories] are directories whose old contents are replaced as a whole. Throws [TreeWriteException] in plain words. */
    suspend fun write(
        files: Map<String, ByteArray>,
        ownedDirectories: Collection<String> = emptyList(),
    ): TreeWriteReport
}

/** What a write did: how many files it wrote, of which [replaced] took the place of an older file, and how many bytes. */
data class TreeWriteReport(
    val files: Int,
    val replaced: Int,
    val bytes: Long,
)

/** A write that did not happen, with [message] a sentence a person can read. The place holds what it held before. */
class TreeWriteException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** The rules every [TreeWriter] applies before it touches anything. */
object TreeLayout {
    /**
     * Throws [TreeWriteException] unless every path of [files] and every owned directory is a valid project path (relative, inside the place,
     * not a temp-file name), no path is also a directory holding another, and every owned directory is a directory.
     */
    fun check(
        files: Map<String, ByteArray>,
        ownedDirectories: Collection<String>,
    ) {
        for (path in files.keys) {
            try {
                ProjectPath.validateWritable(path)
            } catch (e: IllegalArgumentException) {
                throw TreeWriteException("“$path” is not a path Typewright can write: ${e.message}", e)
            }
        }
        for (directory in ownedDirectories) {
            try {
                ProjectPath.validate(directory)
            } catch (e: IllegalArgumentException) {
                throw TreeWriteException("“$directory” is not a folder Typewright can write: ${e.message}", e)
            }
            if (directory in files) throw TreeWriteException("“$directory” is both a file and a folder in what was to be written.")
        }
        val directories = files.keys.flatMapTo(HashSet()) { ancestorsOf(it) }
        val clash = files.keys.firstOrNull { it in directories }
        if (clash != null) throw TreeWriteException("“$clash” is both a file and a folder in what was to be written.")
    }

    /** The directories above [path], outermost first: `a/b/c.txt` is in `a` and `a/b`. */
    fun ancestorsOf(path: String): List<String> {
        val segments = path.split('/')
        return (1 until segments.size).map { segments.take(it).joinToString("/") }
    }
}

/**
 * A [TreeWriter] over a [ProjectStore]: Android's document tree, the browser's directory handle, or any other store, through the
 * store's own atomic file writes. Each file is written atomically; the tree as a whole is made all or nothing by remembering what each
 * replaced file held and putting it back (and removing what was newly created) if a write fails or the coroutine is cancelled part-way.
 * The caller names where the tree goes by giving the store of that folder.
 */
class StoreTreeWriter(
    private val store: ProjectStore,
    override val description: String = store.displayName,
) : TreeWriter {
    override suspend fun write(
        files: Map<String, ByteArray>,
        ownedDirectories: Collection<String>,
    ): TreeWriteReport {
        TreeLayout.check(files, ownedDirectories)
        val before = LinkedHashMap<String, ByteArray?>()
        val stale = mutableListOf<String>()
        try {
            val existing = store.list()
            for (directory in ownedDirectories) stale += existing.filter { it.startsWith("$directory/") && it !in files }
            for (path in files.keys.sorted()) {
                currentCoroutineContext().ensureActive()
                before[path] = store.read(path)
                store.writeAtomic(path, files.getValue(path))
            }
            currentCoroutineContext().ensureActive()
            // The last step: the old copy's files that the new one no longer has. It is not cancelled half-way.
            withContext(NonCancellable) { stale.forEach { store.delete(it) } }
        } catch (e: Throwable) {
            withContext(NonCancellable) { restore(before) }
            if (e is CancellationException) throw e
            throw TreeWriteException(
                "Typewright could not write to ${store.displayName}: ${e.message ?: e::class.simpleName}. Nothing was changed.",
                e,
            )
        }
        return TreeWriteReport(files.size, before.values.count { it != null }, files.values.sumOf { it.size.toLong() })
    }

    /** Puts every file back as it was: the old bytes where there were some, otherwise gone. */
    private suspend fun restore(before: Map<String, ByteArray?>) {
        for ((path, old) in before.entries.reversed()) {
            runCatching { if (old != null) store.writeAtomic(path, old) else store.delete(path) }
        }
    }
}

/**
 * A [TreeWriter] that makes the tree a zip under the folder [rootFolder] and hands its bytes to [save] (the browser's download, where a page
 * cannot write a folder). [fileName] is what the download is called. The zip is stored uncompressed unless [deflate] is given, and is the
 * same bytes for the same tree. [ownedDirectories] mean nothing here: a zip has no old copy to replace.
 */
class ZipTreeWriter(
    private val fileName: String,
    private val rootFolder: String,
    private val save: suspend (fileName: String, bytes: ByteArray) -> Unit,
    private val deflate: (suspend (ByteArray) -> ByteArray)? = null,
) : TreeWriter {
    override val description: String = fileName

    override suspend fun write(
        files: Map<String, ByteArray>,
        ownedDirectories: Collection<String>,
    ): TreeWriteReport {
        TreeLayout.check(files, ownedDirectories)
        val bytes =
            try {
                ProjectZip.write(InMemoryProjectFiles(rootFolder, files), root = rootFolder, deflate = deflate, directories = emptyList())
            } catch (e: IllegalArgumentException) {
                throw TreeWriteException("Typewright could not make the zip: ${e.message}", e)
            }
        try {
            save(fileName, bytes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw TreeWriteException("Typewright could not hand “$fileName” to be saved: ${e.message ?: e::class.simpleName}.", e)
        }
        return TreeWriteReport(files.size, 0, bytes.size.toLong())
    }
}
