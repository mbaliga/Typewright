// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project.storage

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import com.asoc.typewright.project.DocumentOps
import com.asoc.typewright.project.ProjectPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.io.IOException

/**
 * [DocumentOps] over the Android Storage Access Framework, inside the tree granted by
 * [treeUri] and rooted at [rootDocumentId] (docs/PROJECT_MODEL.md §5). [SwapProtocolStore]
 * (`:project`) builds the swap protocol on top of this; this class only does what SAF itself
 * needs: one child-cursor query per directory, cached in [DirectoryCache] and invalidated by
 * every write in that directory (SAF has no "list changed" notification we can await
 * synchronously); documents are created with MIME `application/octet-stream` so the provider
 * doesn't invent an extension, and the resulting display name is always checked against what was
 * asked for, because a provider is free to sanitise or dedupe it otherwise (§5's note).
 *
 * Every method blocks on `ContentResolver`/cursor I/O and runs on [Dispatchers.IO].
 */
class SafDocumentOps(
    private val resolver: ContentResolver,
    private val treeUri: Uri,
    private val rootDocumentId: String,
) : DocumentOps {
    private val cache = DirectoryCache()

    override suspend fun list(): List<String> =
        withContext(Dispatchers.IO) {
            val out = mutableListOf<String>()

            suspend fun walk(
                dirDocumentId: String,
                dirPath: String,
            ) {
                for (child in childrenOf(dirDocumentId, dirPath)) {
                    val path = joinPath(dirPath, child.name)
                    if (ProjectPath.isInGitDirectory(path)) continue
                    if (child.isDirectory) {
                        walk(child.documentId, path)
                    } else if (ProjectPath.isValid(path)) {
                        out += path
                    }
                }
            }
            walk(rootDocumentId, "")
            out.sorted()
        }

    override suspend fun read(path: String): ByteArray? {
        ProjectPath.validate(path)
        return withContext(Dispatchers.IO) {
            val resolved = resolve(path) ?: return@withContext null
            if (resolved.isDirectory) return@withContext null
            resolver.openInputStream(documentUri(resolved.documentId))?.use { it.readBytes() }
        }
    }

    override suspend fun writeTruncate(
        path: String,
        bytes: ByteArray,
    ) = withContext(Dispatchers.IO) {
        val parent = ProjectPath.parent(path)
        val name = ProjectPath.name(path)
        val parentDocumentId = resolveDirectoryCreating(parent)
        val existing = childrenOf(parentDocumentId, parent).firstOrNull { it.name == name && !it.isDirectory }
        val documentId =
            existing?.documentId
                ?: createDocumentChecked(parentDocumentId, parent, MIME_OCTET_STREAM, name)
        val descriptor =
            resolver.openFileDescriptor(documentUri(documentId), "rwt")
                ?: throw IOException("Could not open \"$path\" for writing")
        descriptor.use { pfd ->
            FileOutputStream(pfd.fileDescriptor).use { out ->
                out.write(bytes)
                out.fd.sync()
            }
        }
        cache.invalidate(parent)
        Unit
    }

    override suspend fun rename(
        path: String,
        newName: String,
    ) = withContext(Dispatchers.IO) {
        val parent = ProjectPath.parent(path)
        val resolved = resolve(path) ?: throw IOException("\"$path\" does not exist")
        val clash = childrenOf(resolveDirectoryId(parent) ?: rootDocumentId, parent).any { it.name == newName }
        if (clash) throw IOException("\"$newName\" already exists in \"$parent\"")
        DocumentsContract.renameDocument(resolver, documentUri(resolved.documentId), newName)
            ?: throw IOException("Could not rename \"$path\" to \"$newName\"")
        cache.invalidate(parent)
        Unit
    }

    override suspend fun delete(path: String) =
        withContext(Dispatchers.IO) {
            val resolved = resolve(path) ?: return@withContext
            DocumentsContract.deleteDocument(resolver, documentUri(resolved.documentId))
            cache.invalidate(ProjectPath.parent(path))
        }

    override suspend fun ensureDirectory(path: String) {
        withContext(Dispatchers.IO) { resolveDirectoryCreating(path) }
    }

    /**
     * Whether the tree's root supports create, rename and delete: creates a hidden probe
     * document, renames it and deletes it, undoing itself either way. A root a storage app
     * exposes as create-only (some read-mostly providers) fails here rather than during a later
     * autosave; callers map a `false` result to a refusal instead of writing unsafely (D14).
     */
    suspend fun probeWritable(): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val createdUri =
                    DocumentsContract.createDocument(resolver, documentUri(rootDocumentId), MIME_OCTET_STREAM, PROBE_NAME)
                        ?: return@withContext false
                val renamedUri = DocumentsContract.renameDocument(resolver, createdUri, PROBE_NAME_RENAMED)
                val toDelete = renamedUri ?: createdUri
                val deleted = DocumentsContract.deleteDocument(resolver, toDelete)
                renamedUri != null && deleted
            } catch (_: Exception) {
                false
            } finally {
                cache.invalidate("")
            }
        }

    /** Creates the directory [name] directly inside this tree's own root and returns its document id. */
    suspend fun createChildDirectory(name: String): String =
        withContext(Dispatchers.IO) { createDocumentChecked(rootDocumentId, "", Document.MIME_TYPE_DIR, name) }

    /** This tree's own root display name, or null if it can no longer be queried (moved, revoked, unmounted). */
    suspend fun rootDisplayName(): String? =
        withContext(Dispatchers.IO) {
            try {
                documentDisplayName(rootDocumentId)
            } catch (_: Exception) {
                null
            }
        }

    // ---- internals -------------------------------------------------------------------------------

    private fun documentUri(documentId: String): Uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)

    private fun childUri(dirDocumentId: String): Uri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirDocumentId)

    private fun documentDisplayName(documentId: String): String? =
        resolver.query(documentUri(documentId), arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    // Creates [name] inside [parentDocumentId] (cached under [parentPath]), verifies the
    // provider kept the exact name asked for, and invalidates that directory's cache entry.
    private fun createDocumentChecked(
        parentDocumentId: String,
        parentPath: String,
        mimeType: String,
        name: String,
    ): String {
        val createdUri =
            DocumentsContract.createDocument(resolver, documentUri(parentDocumentId), mimeType, name)
                ?: throw IOException("The SAF tree refused to create \"$name\"")
        val documentId = DocumentsContract.getDocumentId(createdUri)
        val actualName = documentDisplayName(documentId)
        if (actualName != null && actualName != name) {
            throw IOException("SAF created \"$name\" as \"$actualName\"; Typewright needs the exact name it asked for")
        }
        cache.invalidate(parentPath)
        return documentId
    }

    // The cached children of the directory [dirPath] names, querying once and caching if absent.
    private fun childrenOf(
        dirDocumentId: String,
        dirPath: String,
    ): List<ChildDoc> {
        cache.get(dirPath)?.let { return it }
        val entries = mutableListOf<ChildDoc>()
        resolver
            .query(
                childUri(dirDocumentId),
                arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE),
                null,
                null,
                null,
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    entries +=
                        ChildDoc(
                            cursor.getString(nameIndex),
                            cursor.getString(idIndex),
                            cursor.getString(mimeIndex) == Document.MIME_TYPE_DIR,
                        )
                }
            }
        cache.put(dirPath, entries)
        return entries
    }

    // Walks [path] from the root, resolving each segment through the (cached) directory
    // listings; null if any segment is missing.
    private fun resolve(path: String): ResolvedDoc? {
        var dirDocumentId = rootDocumentId
        var dirPath = ""
        val segments = path.split('/')
        for ((index, segment) in segments.withIndex()) {
            val match = childrenOf(dirDocumentId, dirPath).firstOrNull { it.name == segment } ?: return null
            if (index == segments.lastIndex) return ResolvedDoc(match.documentId, match.isDirectory)
            if (!match.isDirectory) return null
            dirDocumentId = match.documentId
            dirPath = joinPath(dirPath, segment)
        }
        return null
    }

    // [path]'s document id if it names an existing directory (root included as "").
    private fun resolveDirectoryId(path: String): String? =
        if (path.isEmpty()) rootDocumentId else resolve(path)?.takeIf { it.isDirectory }?.documentId

    // Walks [path] from the root, creating any missing directory segment with MIME_TYPE_DIR.
    private fun resolveDirectoryCreating(path: String): String {
        if (path.isEmpty()) return rootDocumentId
        var dirDocumentId = rootDocumentId
        var dirPath = ""
        for (segment in path.split('/')) {
            val existing = childrenOf(dirDocumentId, dirPath).firstOrNull { it.name == segment && it.isDirectory }
            dirDocumentId = existing?.documentId ?: createDocumentChecked(dirDocumentId, dirPath, Document.MIME_TYPE_DIR, segment)
            dirPath = joinPath(dirPath, segment)
        }
        return dirDocumentId
    }

    private fun joinPath(
        dirPath: String,
        name: String,
    ): String = if (dirPath.isEmpty()) name else "$dirPath/$name"

    private companion object {
        const val MIME_OCTET_STREAM = "application/octet-stream"
        const val PROBE_NAME = ".typewright-probe"
        const val PROBE_NAME_RENAMED = ".typewright-probe~"
    }
}

/** One entry from a SAF child-documents cursor: [name] as the provider reports it, its own [documentId], and whether it is a directory. */
internal data class ChildDoc(
    val name: String,
    val documentId: String,
    val isDirectory: Boolean,
)

// A resolved path: the document it names and whether that document is a directory.
private data class ResolvedDoc(
    val documentId: String,
    val isDirectory: Boolean,
)

/**
 * [SafDocumentOps]'s per-directory listing cache, keyed by the project-relative directory path
 * (`""` for the tree's own root). A directory is queried at most once until something inside it
 * (a create, rename or delete) invalidates that one entry (docs/PROJECT_MODEL.md §5's "one
 * child-cursor query per directory cached and invalidated on write").
 */
internal class DirectoryCache {
    private val children = mutableMapOf<String, List<ChildDoc>>()

    fun get(dirPath: String): List<ChildDoc>? = children[dirPath]

    fun put(
        dirPath: String,
        entries: List<ChildDoc>,
    ) {
        children[dirPath] = entries
    }

    fun invalidate(dirPath: String) {
        children.remove(dirPath)
    }

    /** The directories currently cached, for tests. */
    fun cachedPaths(): Set<String> = children.keys
}
