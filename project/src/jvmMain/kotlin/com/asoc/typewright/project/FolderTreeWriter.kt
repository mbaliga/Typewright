// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.random.Random

/**
 * A [TreeWriter] for a folder on a desktop filesystem. The whole tree is first written to a hidden sibling of [target] (same filesystem, so a
 * move is a rename), and only when every file is there is it moved into place. If [target] does not exist, the sibling is renamed to it in one
 * step. If it does (the person chose their repository), each file is moved over its old one after the old one is set aside, and an
 * [ownedDirectories][TreeWriter.write] folder is replaced as a whole; any failure puts every set-aside file back and removes what was created, so
 * [target] holds what it held. Cancelling before the move leaves [target] untouched and removes the sibling. Files in [target] that the tree
 * does not name (a `.git` folder, a README) are never touched.
 */
class FolderTreeWriter internal constructor(
    private val target: Path,
    override val description: String,
    private val beforeCommit: () -> Unit,
    private val beforeMove: (String) -> Unit,
) : TreeWriter {
    constructor(target: Path, description: String = target.toString()) : this(target, description, {}, {})

    override suspend fun write(
        files: Map<String, ByteArray>,
        ownedDirectories: Collection<String>,
    ): TreeWriteReport {
        TreeLayout.check(files, ownedDirectories)
        return withContext(Dispatchers.IO) {
            val absolute = target.toAbsolutePath().normalize()
            val parent = absolute.parent ?: throw TreeWriteException("“$description” has no folder to be made in.")
            if (!Files.isDirectory(parent)) {
                throw TreeWriteException("The folder “$parent” does not exist, so “${absolute.fileName}” cannot be made in it.")
            }
            if (Files.exists(absolute) && !Files.isDirectory(absolute)) throw TreeWriteException("“$description” is a file, not a folder.")
            val replaced = files.keys.count { Files.isRegularFile(absolute.resolve(it)) }
            var staging: Path? = null
            try {
                staging = uniqueSibling(parent, ".${absolute.fileName}.ship-")
                for ((path, bytes) in files) {
                    currentCoroutineContext().ensureActive()
                    val file = staging.resolve(path)
                    Files.createDirectories(file.parent)
                    Files.write(file, bytes)
                }
                currentCoroutineContext().ensureActive()
                beforeCommit()
                currentCoroutineContext().ensureActive()
                // From here on the move is not cancelled half-way: it completes, or it puts everything back.
                val owned = ownedDirectories.toSet().filter { Files.isDirectory(staging.resolve(it)) }
                withContext(NonCancellable) { commit(staging, absolute, files.keys, owned) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: TreeWriteException) {
                throw e
            } catch (e: IOException) {
                throw TreeWriteException("Typewright could not write to $description: ${explain(e)} Nothing was changed.", e)
            } catch (e: SecurityException) {
                throw TreeWriteException("Typewright was not allowed to write to $description. Nothing was changed.", e)
            } finally {
                staging?.let { deleteTree(it) }
            }
            TreeWriteReport(files.size, replaced, files.values.sumOf { it.size.toLong() })
        }
    }

    private fun commit(
        staging: Path,
        absolute: Path,
        paths: Set<String>,
        owned: List<String>,
    ) {
        if (!Files.exists(absolute)) {
            beforeMove("")
            moveInto(staging, absolute)
            return
        }
        val aside = uniqueSibling(absolute.parent, ".${absolute.fileName}.old-")
        val moved = mutableListOf<Moved>()
        val madeDirectories = mutableListOf<Path>()
        try {
            // An owned directory goes whole; every other file goes by itself. In path order, so a failure is the same failure each time.
            val units = (owned + paths.filter { path -> owned.none { path.startsWith("$it/") } }).sorted()
            for (unit in units) {
                beforeMove(unit)
                val inPlace = absolute.resolve(unit)
                val setAside = aside.resolve(unit)
                val hadOld = Files.exists(inPlace, LinkOption.NOFOLLOW_LINKS)
                if (hadOld) {
                    Files.createDirectories(setAside.parent)
                    moveInto(inPlace, setAside)
                }
                moved += Moved(unit, hadOld)
                madeDirectories += makeParents(inPlace.parent, absolute)
                moveInto(staging.resolve(unit), inPlace)
            }
        } catch (e: Throwable) {
            rollBack(absolute, aside, moved, madeDirectories)
            throw e
        } finally {
            deleteTree(aside)
        }
    }

    private class Moved(
        val unit: String,
        val hadOld: Boolean,
    )

    /** Removes what the failed commit put in place, puts back what it set aside, and removes the directories it made. Best effort: a step that fails does not stop the others. */
    private fun rollBack(
        absolute: Path,
        aside: Path,
        moved: List<Moved>,
        madeDirectories: List<Path>,
    ) {
        for (entry in moved.asReversed()) {
            val inPlace = absolute.resolve(entry.unit)
            runCatching { deleteTree(inPlace) }
            if (entry.hadOld) runCatching { moveInto(aside.resolve(entry.unit), inPlace) }
        }
        for (directory in madeDirectories.asReversed()) runCatching { Files.deleteIfExists(directory) }
    }

    /** Creates the missing directories down to [directory] (inside [root]) and returns the ones it made, outermost first. */
    private fun makeParents(
        directory: Path,
        root: Path,
    ): List<Path> {
        val missing = ArrayDeque<Path>()
        var next = directory
        while (next != root && !Files.exists(next)) {
            missing.addFirst(next)
            next = next.parent
        }
        for (path in missing) Files.createDirectory(path)
        return missing.toList()
    }

    private fun moveInto(
        from: Path,
        to: Path,
    ) {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(from, to)
        }
    }

    /**
     * A new, empty directory in [parent] named [prefix] and some digits. It is made with the folder's usual permissions, not the owner-only
     * ones a temporary directory gets, because when the tree is renamed into place that directory becomes the repository.
     */
    private fun uniqueSibling(
        parent: Path,
        prefix: String,
    ): Path {
        repeat(UNIQUE_TRIES) {
            val candidate = parent.resolve(prefix + Random.nextLong(0, Long.MAX_VALUE))
            try {
                return Files.createDirectory(candidate)
            } catch (_: FileAlreadyExistsException) {
                // Another name, then.
            }
        }
        throw TreeWriteException("Typewright could not make a working folder next to “$description”.")
    }

    private fun explain(e: IOException): String {
        val detail = e.message?.takeIf { it.isNotBlank() } ?: e::class.simpleName.orEmpty()
        return "$detail."
    }

    private fun deleteTree(path: Path) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return
        Files.walk(path).use { walk -> walk.sorted(Comparator.reverseOrder()).forEach { runCatching { Files.deleteIfExists(it) } } }
    }

    private companion object {
        const val UNIQUE_TRIES = 20
    }
}
