// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.compile

/**
 * Linux desktop backend, planned: fontmake and fontTools run by the system's `python3`.
 * Stub until built: it reports itself unavailable and compiles nothing.
 */
class SystemPythonFontmakeBackend : CompileBackend {
    override val name: String = "fontmake (system Python)"

    override val location: CompileLocation =
        CompileLocation.OnThisComputer(runtime = "fontmake and fontTools in this computer's Python 3")

    override suspend fun availability(): BackendAvailability = BackendAvailability.Unavailable(PLANNED)

    override suspend fun compile(request: CompileRequest): CompileResult = notImplemented(name, PLANNED)

    private companion object {
        const val PLANNED =
            "Not built yet. Planned: find python3, check that fontmake and fontTools import, name " +
                "whichever is missing, then run fontmake on the project in a temporary directory."
    }
}

actual fun platformCompileBackend(): CompileBackend = SystemPythonFontmakeBackend()
