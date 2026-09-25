// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalWasmJsInterop::class)

package com.asoc.typewright.project.storage

import com.asoc.typewright.project.ProjectLocation
import com.asoc.typewright.project.StorageProvider
import com.asoc.typewright.project.StoreLookup
import kotlinx.coroutines.await
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsString
import kotlin.js.toJsString

/**
 * [StorageProvider] for the web, over [handles]' persisted `FileSystemDirectoryHandle`s
 * (docs/PROJECT_MODEL.md §5's table). A location survives a reload only as its
 * [ProjectLocation.BrowserHandle.key]; the live handle it names must be re-fetched from
 * [IndexedDbHandleRegistry] and re-checked with `queryPermission`, since the File System Access
 * API never re-grants access without a fresh user gesture (`PermissionNeeded` when it isn't
 * `"granted"`, matching this table's "Survives process death" row for the web).
 */
class WebStorageProvider(
    private val handles: IndexedDbHandleRegistry = IndexedDbHandleRegistry(),
) : StorageProvider {
    override suspend fun storeFor(location: ProjectLocation): StoreLookup {
        val browserHandle = location as? ProjectLocation.BrowserHandle ?: return StoreLookup.Missing
        val handle = handles.get(browserHandle.key) ?: return StoreLookup.Missing
        val permission = fsaQueryReadWritePermission(handle).await<JsString>().toString()
        if (permission != "granted") return StoreLookup.PermissionNeeded
        val name = fsaHandleName(handle).toString()
        return StoreLookup.Available(FsaProjectStore(handle, browserHandle, name))
    }

    override suspend fun createChild(
        parent: ProjectLocation,
        name: String,
    ): ProjectLocation {
        val parentHandleLocation =
            parent as? ProjectLocation.BrowserHandle
                ?: throw IllegalArgumentException("WebStorageProvider.createChild needs a BrowserHandle location, got $parent")
        val parentHandle = handles.get(parentHandleLocation.key) ?: return ProjectLocation.BrowserHandle(newBrowserHandleKey())
        val childHandle = fsaGetOrCreateChildDirectory(parentHandle, name.toJsString()).await<JsAny>()
        val key = newBrowserHandleKey()
        handles.put(key, childHandle)
        return ProjectLocation.BrowserHandle(key)
    }
}
