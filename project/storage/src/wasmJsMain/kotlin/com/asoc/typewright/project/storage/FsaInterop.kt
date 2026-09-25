// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalWasmJsInterop::class)

package com.asoc.typewright.project.storage

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsString
import kotlin.js.Promise

/**
 * The File System Access API, hand-bound (docs/PROJECT_MODEL.md §5's note: "FSA is hand-bound
 * with `js()` externals"; `kotlinx-browser` 0.5.0 has no binding for it at all, checked directly
 * in its klib the way `shape-preview`'s `BrowserFontFaceVisualPreview.kt` checked `FontFace`).
 * Every `FileSystemDirectoryHandle` crossing this boundary stays an opaque [JsAny]; file content
 * crosses as base64 ([kotlin.io.encoding.Base64] on the Kotlin side), the same bridge this
 * codebase already uses for binary data on wasmJs (`LearnFaceResources.wasmJs.kt`), because
 * Kotlin/Wasm has no typed-array interop in scope without `kotlinx-browser`'s own binding for one,
 * which this module doesn't otherwise need.
 *
 * A project-relative path crosses as one `\u0000`-joined [JsString] ([fsaListFiles]) or as a
 * plain `/`-joined [JsString] naming one file ([fsaReadFile] and friends); [ProjectPath] forbids
 * NUL in a path it considers valid, so the join character can never collide with a real entry.
 */
internal fun fsaShowDirectoryPicker(): Promise<JsAny?> =
    js(
        """
        (async function () {
            try {
                if (typeof window === 'undefined' || !window.showDirectoryPicker) return null;
                return await window.showDirectoryPicker({ mode: 'readwrite' });
            } catch (e) {
                if (e && e.name === 'AbortError') return null;
                throw e;
            }
        })()
        """,
    )

/** Origin-private storage's own root directory handle, with no picker and no permission prompt -- how this module's own browser tests reach a real, writable directory (§5's note). */
internal fun fsaOpfsRoot(): Promise<JsAny> = js("navigator.storage.getDirectory()")

/** [handle]'s own `name` (the picker sets it to the chosen folder's real name). */
internal fun fsaHandleName(handle: JsAny): JsString = js("handle.name")

/** `"granted"`, `"denied"` or `"prompt"` for read+write access to [handle], per the File System Access permission model. */
internal fun fsaQueryReadWritePermission(handle: JsAny): Promise<JsString> = js("handle.queryPermission({ mode: 'readwrite' })")

/** Creates (or reuses) the directory [name] directly inside [parentHandle] and returns its handle. */
internal fun fsaGetOrCreateChildDirectory(
    parentHandle: JsAny,
    name: JsString,
): Promise<JsAny> = js("parentHandle.getDirectoryHandle(name, { create: true })")

/** Every file (not directory) under [rootHandle], as one `\u0000`-joined string of `/`-separated relative paths. */
internal fun fsaListFiles(rootHandle: JsAny): Promise<JsString> =
    js(
        """
        (async function () {
            var out = [];
            async function walk(dirHandle, prefix) {
                for await (const entry of dirHandle.entries()) {
                    var name = entry[0];
                    var handle = entry[1];
                    var path = prefix === '' ? name : prefix + '/' + name;
                    if (handle.kind === 'directory') {
                        await walk(handle, path);
                    } else {
                        out.push(path);
                    }
                }
            }
            await walk(rootHandle, '');
            return out.join('\u0000');
        })()
        """,
    )

/** Creates every missing directory segment of [path] under [rootHandle] (a no-op segment-by-segment where one already exists). */
internal fun fsaEnsureDirectory(
    rootHandle: JsAny,
    path: JsString,
): Promise<JsAny?> =
    js(
        """
        (async function () {
            var text = path;
            if (text === '') return null;
            var segments = text.split('/');
            var dir = rootHandle;
            for (const segment of segments) dir = await dir.getDirectoryHandle(segment, { create: true });
            return null;
        })()
        """,
    )

/**
 * Replaces [path]'s content under [rootHandle] with [base64Bytes], decoded: creates the parent
 * directories and the file if missing, `createWritable({keepExistingData:false})`, writes and
 * closes -- FSA's own atomic replace (docs/PROJECT_MODEL.md §5's table: "by spec the file changes
 * only on close"). On failure the writable stream is aborted before the error is rethrown.
 */
internal fun fsaWriteFile(
    rootHandle: JsAny,
    path: JsString,
    base64Bytes: JsString,
): Promise<JsAny?> =
    js(
        """
        (async function () {
            var segments = path.split('/');
            var name = segments.pop();
            var dir = rootHandle;
            for (const segment of segments) dir = await dir.getDirectoryHandle(segment, { create: true });
            var fileHandle = await dir.getFileHandle(name, { create: true });
            var writable = await fileHandle.createWritable({ keepExistingData: false });
            try {
                var raw = atob(base64Bytes);
                var bytes = new Uint8Array(raw.length);
                for (var i = 0; i < raw.length; i++) bytes[i] = raw.charCodeAt(i);
                await writable.write(bytes);
                await writable.close();
            } catch (e) {
                try {
                    await writable.abort();
                } catch (ignored) {
                    // The abort itself failing doesn't change which error the caller needs to see.
                }
                throw e;
            }
            return null;
        })()
        """,
    )

/** [path]'s content under [rootHandle] as base64, or null if any segment of it (including the file itself) doesn't exist. */
internal fun fsaReadFile(
    rootHandle: JsAny,
    path: JsString,
): Promise<JsString?> =
    js(
        """
        (async function () {
            var segments = path.split('/');
            var name = segments.pop();
            var dir = rootHandle;
            for (const segment of segments) {
                try {
                    dir = await dir.getDirectoryHandle(segment, { create: false });
                } catch (e) {
                    return null;
                }
            }
            var fileHandle;
            try {
                fileHandle = await dir.getFileHandle(name, { create: false });
            } catch (e) {
                return null;
            }
            var file = await fileHandle.getFile();
            var buffer = await file.arrayBuffer();
            var bytes = new Uint8Array(buffer);
            var binary = '';
            var chunkSize = 0x8000;
            for (var i = 0; i < bytes.length; i += chunkSize) {
                binary += String.fromCharCode.apply(null, bytes.subarray(i, i + chunkSize));
            }
            return btoa(binary);
        })()
        """,
    )

/**
 * Removes the file or empty directory at [path] under [rootHandle] (non-recursive); returns
 * `"1"` if it actually removed something, `"0"` if [path]'s parent is missing, [path] itself is
 * already gone, or (for a directory) it isn't empty. [FsaProjectStore.delete] reuses this same
 * primitive to prune `locks/` directories left empty, one level at a time, stopping the first
 * time it reports `"0"`.
 */
internal fun fsaRemoveEntry(
    rootHandle: JsAny,
    path: JsString,
): Promise<JsString> =
    js(
        """
        (async function () {
            var segments = path.split('/');
            var name = segments.pop();
            var dir = rootHandle;
            for (const segment of segments) {
                try {
                    dir = await dir.getDirectoryHandle(segment, { create: false });
                } catch (e) {
                    return '0';
                }
            }
            try {
                await dir.removeEntry(name, { recursive: false });
                return '1';
            } catch (e) {
                return '0';
            }
        })()
        """,
    )
