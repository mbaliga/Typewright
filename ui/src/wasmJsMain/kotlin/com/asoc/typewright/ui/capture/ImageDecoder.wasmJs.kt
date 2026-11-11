// SPDX-License-Identifier: FSL-1.1-ALv2

@file:OptIn(ExperimentalWasmJsInterop::class, ExperimentalEncodingApi::class)

package com.asoc.typewright.ui.capture

import com.asoc.typewright.engine.trace.GrayscaleRaster
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.await
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsString
import kotlin.js.Promise
import kotlin.js.toJsString

/**
 * The browser reads pictures with its own decoder: `createImageBitmap` on a Blob made from the bytes, drawn onto a canvas of at most
 * [ImageDecoder.DEFAULT_MAX_SIDE] pixels (the browser does the reduction), and turned to grey there. The grey bytes cross to Kotlin as
 * base64, the bridge this codebase already uses for binary data on wasm. Compile-verified only: no browser runs here (CLAUDE.md law 4).
 */
actual fun platformImageDecoder(): ImageDecoder = WebImageDecoder

private const val WEB_MAX_SIDE = 2400

internal object WebImageDecoder : ImageDecoder {
    override suspend fun decodeGray(
        bytes: ByteArray,
        maxSide: Int,
    ): GrayscaleRaster? {
        val answer: JsString? =
            try {
                decodeInBrowser(Base64.encode(bytes).toJsString(), minOf(maxSide, WEB_MAX_SIDE)).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                return null
            }
        // "<width>x<height>:<base64 grey bytes>"
        val text = answer?.toString() ?: return null
        val colon = text.indexOf(':')
        val x = text.indexOf('x')
        if (colon < 0 || x < 0 || x > colon) return null
        val w = text.substring(0, x).toIntOrNull() ?: return null
        val h = text.substring(x + 1, colon).toIntOrNull() ?: return null
        val grey =
            try {
                Base64.decode(text.substring(colon + 1))
            } catch (e: IllegalArgumentException) {
                return null
            }
        if (w <= 0 || h <= 0 || grey.size != w * h) return null
        return GrayscaleRaster(w, h, IntArray(grey.size) { grey[it].toInt() and 0xFF })
    }
}

private fun decodeInBrowser(
    base64: JsString,
    maxSide: Int,
): Promise<JsString?> =
    js(
        """
        (async function () {
            try {
                var binary = atob(base64);
                var array = new Uint8Array(binary.length);
                for (var i = 0; i < binary.length; i++) array[i] = binary.charCodeAt(i);
                var bitmap = await createImageBitmap(new Blob([array]));
                var scale = Math.min(1, maxSide / Math.max(bitmap.width, bitmap.height));
                var w = Math.max(1, Math.round(bitmap.width * scale));
                var h = Math.max(1, Math.round(bitmap.height * scale));
                var canvas = document.createElement('canvas');
                canvas.width = w;
                canvas.height = h;
                var context = canvas.getContext('2d');
                context.fillStyle = '#ffffff';
                context.fillRect(0, 0, w, h);
                context.imageSmoothingQuality = 'high';
                context.drawImage(bitmap, 0, 0, w, h);
                var rgba = context.getImageData(0, 0, w, h).data;
                var grey = '';
                var chunk = [];
                for (var p = 0; p < w * h; p++) {
                    chunk.push(Math.round(0.299 * rgba[4 * p] + 0.587 * rgba[4 * p + 1] + 0.114 * rgba[4 * p + 2]));
                    if (chunk.length === 8192) { grey += String.fromCharCode.apply(null, chunk); chunk = []; }
                }
                if (chunk.length) grey += String.fromCharCode.apply(null, chunk);
                return w + 'x' + h + ':' + btoa(grey);
            } catch (e) {
                return null;
            }
        })()
        """,
    )
