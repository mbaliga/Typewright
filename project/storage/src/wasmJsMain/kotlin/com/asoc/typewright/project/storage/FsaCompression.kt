// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalWasmJsInterop::class, ExperimentalEncodingApi::class)

package com.asoc.typewright.project.storage

import kotlinx.coroutines.await
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsString
import kotlin.js.Promise
import kotlin.js.toJsString

/**
 * `CompressionStream`/`DecompressionStream("deflate-raw")` as the raw-deflate hooks
 * [com.asoc.typewright.project.ProjectZip.write] and [com.asoc.typewright.project.ProjectZip.read]
 * take (docs/PROJECT_MODEL.md §5's note): the web's only source of deflate, since there is no
 * `java.util.zip` here. Bytes cross as base64, the same bridge [FsaInterop.kt] uses.
 */
object FsaCompression {
    suspend fun deflateRaw(bytes: ByteArray): ByteArray {
        val result = compressStream(FORMAT.toJsString(), Base64.encode(bytes).toJsString()).await<JsString>()
        return Base64.decode(result.toString())
    }

    suspend fun inflateRaw(bytes: ByteArray): ByteArray {
        val result = decompressStream(FORMAT.toJsString(), Base64.encode(bytes).toJsString()).await<JsString>()
        return Base64.decode(result.toString())
    }

    private const val FORMAT = "deflate-raw"
}

private fun compressStream(
    format: JsString,
    base64Bytes: JsString,
): Promise<JsString> =
    js(
        """
        (async function () {
            var raw = atob(base64Bytes);
            var bytes = new Uint8Array(raw.length);
            for (var i = 0; i < raw.length; i++) bytes[i] = raw.charCodeAt(i);
            var stream = new Blob([bytes]).stream().pipeThrough(new CompressionStream(format));
            var buffer = await new Response(stream).arrayBuffer();
            var out = new Uint8Array(buffer);
            var binary = '';
            var chunkSize = 0x8000;
            for (var i = 0; i < out.length; i += chunkSize) binary += String.fromCharCode.apply(null, out.subarray(i, i + chunkSize));
            return btoa(binary);
        })()
        """,
    )

private fun decompressStream(
    format: JsString,
    base64Bytes: JsString,
): Promise<JsString> =
    js(
        """
        (async function () {
            var raw = atob(base64Bytes);
            var bytes = new Uint8Array(raw.length);
            for (var i = 0; i < raw.length; i++) bytes[i] = raw.charCodeAt(i);
            var stream = new Blob([bytes]).stream().pipeThrough(new DecompressionStream(format));
            var buffer = await new Response(stream).arrayBuffer();
            var out = new Uint8Array(buffer);
            var binary = '';
            var chunkSize = 0x8000;
            for (var i = 0; i < out.length; i += chunkSize) binary += String.fromCharCode.apply(null, out.subarray(i, i + chunkSize));
            return btoa(binary);
        })()
        """,
    )
