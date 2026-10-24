// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa

/**
 * Web actual: layer one needs the desktop app, and this says so in words and checks nothing (CLAUDE.md
 * law 4). Fontspector does ship a Wasm build (`fontspector-web`), but loading it means a browser-side
 * `.wasm` module behind its own JS interop, which belongs to a platform module with a browser target
 * (`app-web`); `qa` is a pure module (`jvm()` and `wasmJs { nodejs() }` only) and cannot hold that bridge.
 */
private class UnavailableOnWebLayerOneChecker : LayerOneChecker {
    override val name: String = "Layer one (desktop app only)"

    override suspend fun availability(): LayerOneAvailability = LayerOneAvailability.Unavailable(REASON)

    override suspend fun check(input: LayerOneInput): LayerOneReport = unavailableReport(REASON)

    companion object {
        const val REASON =
            "Layer one needs the desktop app: Fontbakery and Fontspector run on a computer, not in the browser. " +
                "Layer two, Typewright's own checks, runs here."
    }
}

actual fun platformLayerOneChecker(): LayerOneChecker = UnavailableOnWebLayerOneChecker()
