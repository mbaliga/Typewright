// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalWasmJsInterop::class)

package com.asoc.typewright.project.storage

import com.asoc.typewright.project.ProjectLocation
import kotlinx.coroutines.await
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny

/** S02's folder picker on the web (docs/PROJECT_MODEL.md §5): [pick] must be called from inside a user gesture (a click handler), which `showDirectoryPicker` itself requires. */
class WebFolderPicker(
    private val handles: IndexedDbHandleRegistry = IndexedDbHandleRegistry(),
) {
    /** Opens the browser's directory picker; null if the user cancels. The chosen folder's handle is registered under a fresh key, so [PickedFolder.location] alone is enough to reopen it later. */
    suspend fun pick(): PickedFolder? {
        val handle = fsaShowDirectoryPicker().await<JsAny?>() ?: return null
        val key = newBrowserHandleKey()
        handles.put(key, handle)
        return PickedFolder(ProjectLocation.BrowserHandle(key), fsaHandleName(handle).toString())
    }
}

/** What [WebFolderPicker.pick] hands back: where the folder now lives, and its own display name. */
data class PickedFolder(
    val location: ProjectLocation.BrowserHandle,
    val name: String,
)
