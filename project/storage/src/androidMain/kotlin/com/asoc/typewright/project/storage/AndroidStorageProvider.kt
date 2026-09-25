// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project.storage

import android.content.Context
import android.net.Uri
import com.asoc.typewright.project.ProjectLocation
import com.asoc.typewright.project.StorageProvider
import com.asoc.typewright.project.StoreLookup
import com.asoc.typewright.project.SwapProtocolStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [StorageProvider] for Android: every [ProjectLocation] is a [ProjectLocation.SafTree], and its
 * store is a [SwapProtocolStore] over [SafDocumentOps] (docs/PROJECT_MODEL.md §5). Android runs
 * one app instance, so [SwapProtocolStore]'s in-process write lease already gives the "single
 * writer" row of §5's table; there is no separate cross-process lock to take.
 */
class AndroidStorageProvider(
    private val context: Context,
) : StorageProvider {
    override suspend fun storeFor(location: ProjectLocation): StoreLookup {
        val tree = location as? ProjectLocation.SafTree ?: return StoreLookup.Missing
        val treeUri = Uri.parse(tree.treeUri)
        val granted =
            withContext(Dispatchers.IO) {
                context.contentResolver.persistedUriPermissions.any {
                    it.uri == treeUri && it.isReadPermission && it.isWritePermission
                }
            }
        if (!granted) return StoreLookup.PermissionNeeded
        val ops = SafDocumentOps(context.contentResolver, treeUri, tree.documentId)
        val displayName = ops.rootDisplayName() ?: return StoreLookup.Missing
        return StoreLookup.Available(SwapProtocolStore(ops, tree, displayName))
    }

    override suspend fun createChild(
        parent: ProjectLocation,
        name: String,
    ): ProjectLocation {
        val tree =
            parent as? ProjectLocation.SafTree
                ?: throw IllegalArgumentException("AndroidStorageProvider.createChild needs a SafTree location, got $parent")
        val treeUri = Uri.parse(tree.treeUri)
        val ops = SafDocumentOps(context.contentResolver, treeUri, tree.documentId)
        // createChild never throws (docs/PROJECT_MODEL.md §5, D14): a root that fails the
        // writability probe, or a creation that fails after it, still returns a location. The
        // caller's next storeFor() on it finds nothing there (StoreLookup.Missing), which
        // ProjectWorkspace.create turns into CreateResult.NotWritable -- the refusal this method
        // itself has no room to return (its signature is a plain ProjectLocation).
        val childDocumentId =
            if (ops.probeWritable()) {
                runCatching { ops.createChildDirectory(name) }.getOrNull()
            } else {
                null
            }
        return ProjectLocation.SafTree(tree.treeUri, childDocumentId ?: "$UNCREATED_DOCUMENT_ID_PREFIX${tree.documentId}/$name")
    }

    private companion object {
        // A document id that can never resolve to a real document, so a lookup of it reports
        // StoreLookup.Missing rather than throwing.
        const val UNCREATED_DOCUMENT_ID_PREFIX = "typewright-uncreated:"
    }
}
