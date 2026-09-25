// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project.storage

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts
import com.asoc.typewright.project.ProjectLocation
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * S02's folder picker on Android: `ActivityResultContracts.OpenDocumentTree`, seeded at
 * [pick]'s [initialUri] (`EXTRA_INITIAL_URI`), then a persistable read+write grant on whatever
 * the user chose (docs/PROJECT_MODEL.md §5). [registry] must be one the host activity or
 * fragment registers this with before it reaches `STARTED` -- construct it in `onCreate`, as
 * [ActivityResultRegistry]'s own contract requires; `MainActivity.pickProjectParentFolder()`
 * (WP5) is the intended caller.
 */
class AndroidFolderPicker(
    registry: ActivityResultRegistry,
    private val contentResolver: ContentResolver,
    key: String = "typewright-open-document-tree",
) {
    private var pending: CancellableContinuation<ProjectLocation.SafTree?>? = null

    private val launcher = registry.register(key, ActivityResultContracts.OpenDocumentTree()) { uri -> deliver(uri) }

    /**
     * Opens SAF's folder chooser and suspends until the user picks a folder (returning it as a
     * fresh [ProjectLocation.SafTree], with a persisted read+write permission already taken) or
     * backs out (returning null). Only one [pick] may be in flight at a time.
     */
    suspend fun pick(initialUri: Uri? = null): ProjectLocation.SafTree? =
        suspendCancellableCoroutine { continuation ->
            pending = continuation
            continuation.invokeOnCancellation { pending = null }
            launcher.launch(initialUri)
        }

    private fun deliver(uri: Uri?) {
        val continuation = pending ?: return
        pending = null
        if (uri == null) {
            continuation.resume(null)
            return
        }
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val documentId = DocumentsContract.getTreeDocumentId(uri)
        continuation.resume(ProjectLocation.SafTree(uri.toString(), documentId))
    }
}
