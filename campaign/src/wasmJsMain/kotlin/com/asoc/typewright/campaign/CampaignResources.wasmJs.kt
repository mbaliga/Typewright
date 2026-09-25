// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.campaign

import kotlin.js.ExperimentalWasmJsInterop

/**
 * Reads [resourcePath] with Node's built-in `fs` module, resolved relative to this compiled
 * module's own `import.meta.url` rather than the process's working directory -- see
 * [readCampaignResourceText]'s doc comment in commonMain, and `learn:scenes`'
 * `SceneResources.wasmJs.kt`, whose identical approach this mirrors. `process.getBuiltinModule`
 * (not `require`, which Kotlin/Wasm's ESM output forbids at top level, and not a static
 * `import`, which cannot take a dynamic specifier) needs Node >= 22.3.0; this pure module's Wasm
 * target is `wasmJs { nodejs() }` only (`KmpPureConventionPlugin`), never a browser, so `process`
 * is always defined here.
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

internal actual fun readCampaignResourceText(resourcePath: String): String = readNodeFileNextToThisModule(resourcePath)
