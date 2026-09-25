// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.qa

/**
 * Web actual: always unavailable, honestly labelled (CLAUDE.md law 4). Fontspector does ship an
 * official Wasm build (`fontspector-web`, docs/ARCHITECTURE_REVIEW.md section 3 `:qa`, section 6
 * decision 2), but wiring it in means a browser-loaded `.wasm` module behind its own JS
 * interop, which belongs in a platform module with a `wasmJs { browser {} }` target (`app-web`) —
 * `qa` is `typewright.kmp.pure` (`jvm()` + `wasmJs { nodejs() }` only, no browser target), so it
 * cannot hold that bridge itself. This mirrors `compile`'s `HostedEndpointBackend`: a stub that
 * says so rather than a fake.
 */
private class UnavailableOnWebLayerOneChecker : LayerOneChecker {
    override val name: String = "Fontspector (web)"

    override suspend fun availability(): LayerOneAvailability = LayerOneAvailability.Unavailable(REASON)

    override suspend fun check(input: LayerOneInput): LayerOneReport = unavailableReport(REASON)

    private companion object {
        const val REASON =
            "Unavailable: layer one has no runtime on the web yet. Fontspector ships an official " +
                "Wasm build (fontspector-web), but wiring it in belongs to a browser-targeted platform " +
                "module (app-web), not this pure module -- see docs/ARCHITECTURE_REVIEW.md section 3 " +
                "`:qa` and docs/OPEN_QUESTIONS.md item 7."
    }
}

actual fun platformLayerOneChecker(): LayerOneChecker = UnavailableOnWebLayerOneChecker()
