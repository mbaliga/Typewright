// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/** Whether a store's files outlive the process (a folder on disk) or only the browser tab. */
enum class Durability {
    /** Files on real storage: a desktop folder, an Android document tree, a browser directory handle. */
    PERSISTENT,

    /** Memory only; the project survives only as a downloaded .zip. */
    TAB_ONLY,
}

/**
 * The writable, possibly asynchronous storage behind an open project. Every path is a valid
 * [ProjectPath]; an invalid one throws [IllegalArgumentException] before anything is touched.
 * The caller chooses the dispatcher: implementations over blocking I/O run on whichever
 * dispatcher calls them.
 */
interface ProjectStore {
    /** Where the project lives, as recents remember it. */
    val location: ProjectLocation

    /** The project's name as the UI shows it: the folder's name. */
    val displayName: String

    /** Whether the files survive the process. */
    val durability: Durability

    /** Every file (not directory), relative and `/`-separated, sorted. A root `.git` directory is not listed. */
    suspend fun list(): List<String>

    /** The bytes of [path], or null if there is no such file. */
    suspend fun read(path: String): ByteArray?

    /**
     * Replaces [path] with [bytes], creating parent directories. After this returns, or after a
     * crash at any point inside it followed by [recover], the file is wholly old or wholly new.
     * On failure it removes its own temp file before throwing.
     */
    suspend fun writeAtomic(
        path: String,
        bytes: ByteArray,
    )

    /**
     * Deletes the file at [path]; a no-op if it is absent. Directories under `locks/` left empty
     * are removed too, because `locks/` is absent until the first approval and undoing every
     * approval must give back the tree as it was.
     */
    suspend fun delete(path: String)

    /** Creates the directory [path] and its parents if they don't exist. */
    suspend fun ensureDirectory(path: String)

    /**
     * Finishes or discards what a crash left behind: deletes partial writes (`X.tmp`, `X.crswap`)
     * and rolls completed `X.new` files forward over `X`, only where `X` is a file the project
     * format writes ([RecoveryPlan], [ProjectLayout]); other tools' temp files are left alone. It
     * runs on every open, before the first write.
     */
    suspend fun recover(): RecoveryReport

    /** Claims the right to write, or returns null when the project is open for writing elsewhere (open read-only then). */
    suspend fun acquireWriteLease(): WriteLease?
}

/** What [ProjectStore.recover] did: files completed from their `.new` (by target path) and temp files deleted. */
data class RecoveryReport(
    val rolledForward: List<String>,
    val discarded: List<String>,
) {
    /** Whether recovery found nothing to do. */
    val isEmpty: Boolean get() = rolledForward.isEmpty() && discarded.isEmpty()

    companion object {
        /** A report of no recovery. */
        val NONE: RecoveryReport = RecoveryReport(emptyList(), emptyList())
    }
}

/** A held right to write a project; [release] gives it up and is safe to call more than once. */
fun interface WriteLease {
    suspend fun release()
}

/**
 * Where a project lives, in the form recent-projects remembers (docs/PROJECT_MODEL.md §5.4):
 * `{"kind": "saf", "tree_uri": …, "document_id": …}` and so on.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("kind")
sealed interface ProjectLocation {
    /** A directory on a desktop filesystem, by absolute path. */
    @Serializable
    @SerialName("file")
    data class FileSystem(
        val path: String,
    ) : ProjectLocation

    /** An Android Storage Access Framework document inside a persisted tree grant. */
    @Serializable
    @SerialName("saf")
    data class SafTree(
        @SerialName("tree_uri") val treeUri: String,
        @SerialName("document_id") val documentId: String,
    ) : ProjectLocation

    /** A File System Access directory handle, stored in the browser's IndexedDB under [key]. */
    @Serializable
    @SerialName("browser-handle")
    data class BrowserHandle(
        val key: String,
    ) : ProjectLocation

    /** A project held only in memory; it is never put in recents. */
    @Serializable
    @SerialName("memory")
    data class InMemory(
        val name: String,
    ) : ProjectLocation
}

/**
 * The file operations of a backend with no atomic rename-over (Android's SAF).
 * [SwapProtocolStore] builds the §5 swap protocol on top of these. Paths are valid
 * [ProjectPath]s.
 */
interface DocumentOps {
    /** Every file, relative and `/`-separated, in any order. */
    suspend fun list(): List<String>

    /** The bytes of [path], or null if absent. */
    suspend fun read(path: String): ByteArray?

    /** Creates or truncates [path] (its directory exists), writes [bytes], then syncs them to storage. */
    suspend fun writeTruncate(
        path: String,
        bytes: ByteArray,
    )

    /** Renames [path] to [newName] in the same directory; fails if [newName] exists. */
    suspend fun rename(
        path: String,
        newName: String,
    )

    /** Deletes the file or empty directory at [path]; a no-op if absent. */
    suspend fun delete(path: String)

    /** Creates the directory [path] and its parents if they don't exist. */
    suspend fun ensureDirectory(path: String)
}

/** The app's own small settings files (recent projects), outside any project. */
interface AppConfigStore {
    /** The bytes of the config file [name], or null if it doesn't exist. */
    suspend fun read(name: String): ByteArray?

    /** Replaces the config file [name] atomically: wholly old or wholly new after a crash. */
    suspend fun writeAtomic(
        name: String,
        bytes: ByteArray,
    )
}

/** Turns remembered [ProjectLocation]s into stores, per platform. */
interface StorageProvider {
    /** The store for [location], or why it can't be had right now. */
    suspend fun storeFor(location: ProjectLocation): StoreLookup

    /** Creates the directory [name] inside [parent] (new-project creates `<name>/` in the chosen folder) and returns where it is. */
    suspend fun createChild(
        parent: ProjectLocation,
        name: String,
    ): ProjectLocation
}

/** The result of [StorageProvider.storeFor]. */
sealed interface StoreLookup {
    /** The project is reachable through [store]. */
    data class Available(
        val store: ProjectStore,
    ) : StoreLookup

    /** The folder was moved or deleted; the UI offers Locate…. */
    data object Missing : StoreLookup

    /** The platform needs the user to grant access again (Android's grant, the browser's permission). */
    data object PermissionNeeded : StoreLookup
}
