// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.compile

/**
 * Android backend, planned: fontmake and fontTools bundled into the app through Chaquopy.
 * Chaquopy is not added yet (its licence text is still to confirm, brief §15 item 7).
 * Stub until built: it reports itself unavailable and compiles nothing.
 */
class ChaquopyBackend : CompileBackend {
    override val name: String = "fontmake (Chaquopy)"

    override val location: CompileLocation =
        CompileLocation.OnThisDevice(runtime = "fontmake and fontTools in the app's bundled Python")

    override suspend fun availability(): BackendAvailability = BackendAvailability.Unavailable(PLANNED)

    override suspend fun compile(request: CompileRequest): CompileResult = notImplemented(name, PLANNED)

    private companion object {
        const val PLANNED =
            "Not built yet. Planned: Chaquopy bundles Python with fontmake and fontTools into the " +
                "app, and the build runs on the device with no network use."
    }
}

actual fun platformCompileBackend(): CompileBackend = ChaquopyBackend()
