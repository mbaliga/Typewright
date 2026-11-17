// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.compile

/**
 * iOS backend: an honest stub (CLAUDE.md law 4). fontmake and fontTools need a Python runtime and
 * fontc needs to be linked in; an iOS app can spawn no processes (docs/PORTING_PLAN.md IOS-6).
 * Reports itself unavailable and compiles nothing, so the UI says so rather than faking a build.
 * The real route is fontc as a Rust static library behind a C ABI, gated on decision V1-D5.
 */
class IosUnavailableBackend : CompileBackend {
    override val name: String = "fontc (native library, not linked yet)"

    override val location: CompileLocation =
        CompileLocation.OnThisDevice(runtime = "fontc, linked into the app as a native library")

    override suspend fun availability(): BackendAvailability = BackendAvailability.Unavailable(PLANNED)

    override suspend fun compile(request: CompileRequest): CompileResult = notImplemented(name, PLANNED)

    private companion object {
        const val PLANNED =
            "Not built yet on iOS. An iPad app cannot run fontmake (no Python, no child processes). " +
                "Planned: fontc linked in as a native library, so a build runs on the device with " +
                "no network use. Until then this device cannot compile a font."
    }
}

actual fun platformCompileBackend(): CompileBackend = IosUnavailableBackend()
