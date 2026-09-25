// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.compile

/** The v1 backend for the platform this code runs on. */
expect fun platformCompileBackend(): CompileBackend

/** Shared by the stubs so every one says the same thing the same way. */
internal fun notImplemented(
    backend: String,
    planned: String,
): CompileResult.NotImplemented =
    CompileResult.NotImplemented(
        backend = backend,
        planned = planned,
        log = listOf(CompileLogLine(CompileLogLine.Level.ERROR, "$backend is not implemented yet. Nothing was compiled.")),
    )
