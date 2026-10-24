// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.compile

/**
 * Android backend. Compiling on a phone is not built: whether the route is Chaquopy with fontmake or
 * fontc over JNI is an owner decision that rests on a measurement only a device can give, so this
 * says so in words, compiles nothing and fakes nothing.
 */
class ChaquopyBackend : CompileBackend {
    override val name: String = "fontmake (Chaquopy)"

    override val location: CompileLocation =
        CompileLocation.OnThisDevice(runtime = "fontmake and fontTools in the app's bundled Python")

    override suspend fun availability(): BackendAvailability = BackendAvailability.Unavailable(NOT_BUILT)

    override suspend fun compile(
        request: CompileRequest,
        onLog: (CompileLogLine) -> Unit,
    ): CompileResult = notImplemented(name, NOT_BUILT).also { result -> result.log.forEach(onLog) }

    companion object {
        /** What the UI shows wherever it would offer to build on this device. */
        const val NOT_BUILT =
            "Compiling on this phone is not built in this version (the Android route is an owner decision, D5); " +
                "build in the desktop app."
    }
}

actual fun platformCompileBackend(): CompileBackend = ChaquopyBackend()
