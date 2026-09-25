// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalWasmJsInterop::class)

package com.asoc.typewright.project.storage

import com.asoc.typewright.project.ProjectLocation
import kotlinx.coroutines.await
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsString
import kotlin.js.Promise
import kotlin.js.toJsString

/**
 * Persists `FileSystemDirectoryHandle`s in IndexedDB (database `typewright`, object store
 * `handles`), keyed by [ProjectLocation.BrowserHandle.key] (docs/PROJECT_MODEL.md §5's table): a
 * handle itself is a live object, gone when the tab closes, but a browser that supports the File
 * System Access API also lets `structuredClone` (which IndexedDB uses to store a value) clone a
 * handle, so the *capability* survives a reload -- `queryPermission` on the reloaded handle then
 * says whether it is still usable without asking the user again.
 *
 * `kotlinx-browser` 0.5.0 has no IndexedDB binding, so this is hand-bound too, the same as
 * [FsaInterop.kt]'s File System Access surface. Each call opens the database itself, rather than
 * holding a connection open across calls: infrequent (a handful of directory pickers, not a hot
 * path), and it keeps every operation independent of this registry's own lifetime.
 */
class IndexedDbHandleRegistry {
    suspend fun put(
        key: String,
        handle: JsAny,
    ) {
        val db = idbOpen().await<JsAny>()
        idbPut(db, key.toJsString(), handle).await<JsAny?>()
    }

    suspend fun get(key: String): JsAny? {
        val db = idbOpen().await<JsAny>()
        return idbGet(db, key.toJsString()).await<JsAny?>()
    }

    suspend fun remove(key: String) {
        val db = idbOpen().await<JsAny>()
        idbRemove(db, key.toJsString()).await<JsAny?>()
    }
}

private fun idbOpen(): Promise<JsAny> =
    js(
        """
        (function () {
            return new Promise(function (resolve, reject) {
                var request = indexedDB.open('typewright', 1);
                request.onupgradeneeded = function () {
                    request.result.createObjectStore('handles');
                };
                request.onsuccess = function () { resolve(request.result); };
                request.onerror = function () { reject(request.error); };
            });
        })()
        """,
    )

private fun idbPut(
    db: JsAny,
    key: JsString,
    handle: JsAny,
): Promise<JsAny?> =
    js(
        """
        (function () {
            return new Promise(function (resolve, reject) {
                var tx = db.transaction('handles', 'readwrite');
                tx.objectStore('handles').put(handle, key);
                tx.oncomplete = function () { resolve(null); };
                tx.onerror = function () { reject(tx.error); };
            });
        })()
        """,
    )

private fun idbGet(
    db: JsAny,
    key: JsString,
): Promise<JsAny?> =
    js(
        """
        (function () {
            return new Promise(function (resolve, reject) {
                var tx = db.transaction('handles', 'readonly');
                var request = tx.objectStore('handles').get(key);
                request.onsuccess = function () { resolve(request.result === undefined ? null : request.result); };
                request.onerror = function () { reject(request.error); };
            });
        })()
        """,
    )

private fun idbRemove(
    db: JsAny,
    key: JsString,
): Promise<JsAny?> =
    js(
        """
        (function () {
            return new Promise(function (resolve, reject) {
                var tx = db.transaction('handles', 'readwrite');
                tx.objectStore('handles').delete(key);
                tx.oncomplete = function () { resolve(null); };
                tx.onerror = function () { reject(tx.error); };
            });
        })()
        """,
    )
