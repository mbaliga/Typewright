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
 * Once the last step has begun it is finished, and **a write that was finished is reported as finished** even when the caller was cancelled
 * meanwhile, so that the caller can say the place was written and not that it was untouched. Files in the place that are not in the tree are
 * left alone, except inside the `ownedDirectories`, which are replaced as a whole so that a file the new tree no longer has (a glyph that was
 * deleted) does not linger in the old copy.
 */
interface TreeWriter {
    /** Where the tree goes, in words the screen shows (`~/Fonts/hyle-deco-gf`). */
    val description: String

    /** Writes [files]; [ownedDirectories] are directories whose old contents are replaced as a whole. Throws [TreeWriteException] in plain words. */
    suspend fun write(
        files: Map<String, ByteArray>,
        ownedDirectories: Collection<String> = emptyList(),
    ): TreeWriteReport

    /**
     * What writing [paths] (with [ownedDirectories] replaced whole) would replace in the place as it is now, without writing anything: each
     * path that already holds a file, and each owned directory that already exists. Empty where there is nothing to replace, and for a place
     * that has no old copy (a zip). A path inside an owned directory is not listed on its own: the directory is.
     */
    suspend fun replacements(
        paths: Collection<String>,
        ownedDirectories: Collection<String> = emptyList(),
    ): List<TreeReplacement> = emptyList()
}

/**
 * Something a write replaces in a place that already had it: the file or, when [directory], the owned folder at [path] (without a trailing
 * slash). [removed] counts the files of an owned folder that the new tree does not have, which go with the old copy; a write counts them, and
 * [TreeWriter.replacements] before a write leaves it 0.
 */
data class TreeReplacement(
    val path: String,
    val directory: Boolean,
    val removed: Int = 0,
)

/**
 * What a write did: how many files it wrote, of which [replaced] took the place of an older file, and how many bytes; and [replacements], what
 * it replaced in the place, folder by folder and file by file, so that a page can say what an existing repository lost.
 */
data class TreeWriteReport(
    val files: Int,
    val replaced: Int,
    val bytes: Long,
    val replacements: List<TreeReplacement> = emptyList(),
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

    /**
     * What [existing] (every file already in a place, by path) holds that a write of [paths] replaces: the paths that are files there, outside
     * the owned directories, and each owned directory that has any file. [removed] counts, for an owned directory, its files that [paths] lacks.
     */
    fun replacementsIn(
        existing: Collection<String>,
        paths: Collection<String>,
        ownedDirectories: Collection<String>,
    ): List<TreeReplacement> {
        val present = existing.toHashSet()
        val wanted = paths.toHashSet()
        val owned = ownedDirectories.toList().sorted()
        val files =
            wanted
                .filter { path -> path in present && owned.none { path.startsWith("$it/") } }
                .sorted()
                .map { TreeReplacement(it, directory = false) }
        val directories =
            owned.mapNotNull { directory ->
                val old = present.filter { it.startsWith("$directory/") }
                if (old.isEmpty()) null else TreeReplacement(directory, directory = true, removed = old.count { it !in wanted })
            }
        return (files + directories).sortedBy { it.path }
    }
}

/**
 * A [TreeWriter] over a [ProjectStore]: Android's document tree, the browser's directory handle, or any other store, through the
 * store's own atomic file writes. Each file is written atomically; the tree as a whole is made all or nothing by remembering what each file
 * it overwrites or deletes held, and putting it back (and removing what was newly created) if a write or a delete fails or the coroutine is
 * cancelled before the deletes begin. The deletes, the old copy's files that the new tree no longer has, come last and are not cancelled
 * half-way; a delete that fails puts the deleted files back too. The caller names where the tree goes by giving the store of that folder.
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
        // What each path held before this write touched it (null: nothing), for the files it overwrites and the old files it deletes.
        val before = LinkedHashMap<String, ByteArray?>()
        var replacements = emptyList<TreeReplacement>()
        try {
            val existing = store.list()
            replacements = TreeLayout.replacementsIn(existing, files.keys, ownedDirectories)
            val stale = existing.filter { path -> ownedDirectories.any { path.startsWith("$it/") } && path !in files }
            for (path in files.keys.sorted()) {
                currentCoroutineContext().ensureActive()
                before[path] = store.read(path)
                store.writeAtomic(path, files.getValue(path))
            }
            currentCoroutineContext().ensureActive()
            // The last step: the old copy's files that the new one no longer has. It is not cancelled half-way, and each file is read before it
            // is deleted, so that a delete that fails can put back every file already gone.
            withContext(NonCancellable) {
                for (path in stale) {
                    before[path] = store.read(path)
                    store.delete(path)
                }
            }
        } catch (e: Throwable) {
            withContext(NonCancellable) { restore(before) }
            if (e is CancellationException) throw e
            throw TreeWriteException(
                "Typewright could not write to ${store.displayName}: ${e.message ?: e::class.simpleName}. Nothing was changed.",
                e,
            )
        }
        val overwritten = files.keys.count { before[it] != null }
        return TreeWriteReport(files.size, overwritten, files.values.sumOf { it.size.toLong() }, replacements)
    }

    override suspend fun replacements(
        paths: Collection<String>,
        ownedDirectories: Collection<String>,
    ): List<TreeReplacement> =
        try {
            TreeLayout.replacementsIn(store.list(), paths, ownedDirectories)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
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
