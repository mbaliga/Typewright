// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa

/**
 * iOS actual: always unavailable, honestly labelled (CLAUDE.md law 4). Fontspector is a command
 * line tool and an iOS app cannot spawn processes; linking it in as a Rust static library is the
 * planned route (docs/PORTING_PLAN.md IOS-6) and is not built. Mirrors the web actual.
 */
private class UnavailableOnIosLayerOneChecker : LayerOneChecker {
    override val name: String = "Fontspector (iOS)"

    override suspend fun availability(): LayerOneAvailability = LayerOneAvailability.Unavailable(REASON)

    override suspend fun check(input: LayerOneInput): LayerOneReport = unavailableReport(REASON)

    private companion object {
        const val REASON =
            "Unavailable: layer one has no runtime on iOS yet. Fontspector runs as a separate " +
                "process, which an iOS app cannot start; linking it in as a native library is " +
                "planned (docs/PORTING_PLAN.md IOS-6) and not built."
    }
}

actual fun platformLayerOneChecker(): LayerOneChecker = UnavailableOnIosLayerOneChecker()
