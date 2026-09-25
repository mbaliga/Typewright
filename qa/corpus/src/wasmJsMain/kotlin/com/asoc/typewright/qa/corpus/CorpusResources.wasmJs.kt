// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.corpus

import kotlin.js.ExperimentalWasmJsInterop

/**
 * Reads [resourcePath] with Node's built-in `fs` module, resolved relative to this compiled
 * module's own `import.meta.url` rather than the process's working directory (see
 * [readCorpusResourceText]'s doc comment in commonMain for why). `process.getBuiltinModule` (not
 * `require`, which Kotlin/Wasm's ESM output forbids at top level, and not a static `import`,
 * which cannot take a dynamic specifier) needs Node >= 22.3.0; this pure module's Wasm target is
 * `wasmJs { nodejs() }` only (`KmpPureConventionPlugin`), never a browser, so `process` is always
 * defined here.
 */
@OptIn(ExperimentalWasmJsInterop::class)
private fun readNodeFileNextToThisModule(resourcePath: String): String =
    js(
        """
        (function () {
            var fs = process.getBuiltinModule('fs');
            var url = new URL(resourcePath, import.meta.url);
            return fs.readFileSync(url, 'utf8');
        })()
        """,
    )

internal actual fun readCorpusResourceText(resourcePath: String): String = readNodeFileNextToThisModule(resourcePath)
