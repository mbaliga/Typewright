// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.learn.scenes

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.ExperimentalWasmJsInterop

/**
 * Reads [resourcePath] with Node's built-in `fs` module, the same way
 * [readSceneResourceText]'s own wasmJs actual does (see its KDoc for the full explanation this
 * mirrors: `process.getBuiltinModule`, resolved relative to `import.meta.url`, Node >= 22.3.0,
 * `wasmJs { nodejs() }` only). The one difference: a `.ttf` is binary, and Kotlin/Wasm's stdlib
 * has no `Uint8Array`/`ArrayBuffer` interop in scope here (no `kotlinx-browser` dependency this
 * module would otherwise need to add just for this one conversion), so this reads the file as a
 * base64 string on the JS side (`Buffer.toString('base64')`) and decodes it with
 * [kotlin.io.encoding.Base64] — pure Kotlin stdlib, identical on every target, no typed-array
 * interop required at all.
 */
@OptIn(ExperimentalWasmJsInterop::class)
private fun readNodeFileBase64NextToThisModule(resourcePath: String): String =
    js(
        """
        (function () {
            var fs = process.getBuiltinModule('fs');
            var url = new URL(resourcePath, import.meta.url);
            return fs.readFileSync(url).toString('base64');
        })()
        """,
    )

@OptIn(ExperimentalEncodingApi::class)
internal actual fun readLearnFaceResourceBytes(resourcePath: String): ByteArray =
    Base64.decode(readNodeFileBase64NextToThisModule(resourcePath))
