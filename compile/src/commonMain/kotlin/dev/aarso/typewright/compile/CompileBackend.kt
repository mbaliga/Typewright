package dev.aarso.typewright.compile

/**
 * Turns a Typewright project (UFO 3 masters, optionally a designspace) into font binaries.
 *
 * v1 backends are fontmake + fontTools in a Python runtime (system Python on Linux, Chaquopy
 * on Android) and a hosted build endpoint on the web; the planned v2 backend is fontc as a
 * native library (brief §3). Nothing outside a backend may assume Python exists: callers
 * see only this interface, ask [availability] first, and show [location] to the user.
 */
interface CompileBackend {
    /** Short name for the UI and logs, for example "fontmake (system Python)". */
    val name: String

    /** Where the compilation runs. The UI names it every time it is used (CLAUDE.md law 3). */
    val location: CompileLocation

    /** Whether this backend can compile here, now. Never throws and never installs anything. */
    suspend fun availability(): BackendAvailability

    /** Compiles [request]. A stub returns [CompileResult.NotImplemented]; no backend fakes output. */
    suspend fun compile(request: CompileRequest): CompileResult
}

/** Where a [CompileBackend] does its work, in words the UI can show as they are. */
sealed interface CompileLocation {
    /** One plain sentence for the UI. */
    val description: String

    /** On the phone or tablet itself, with no network use. */
    data class OnThisDevice(
        val runtime: String,
    ) : CompileLocation {
        override val description: String
            get() = "Compiled on this device by $runtime. Nothing leaves the device."
    }

    /** On the user's own computer, with no network use. */
    data class OnThisComputer(
        val runtime: String,
    ) : CompileLocation {
        override val description: String
            get() = "Compiled on this computer by $runtime. Nothing leaves the computer."
    }

    /**
     * On a hosted build endpoint. The project is sent to [endpoint] only when the user starts a
     * build. [endpoint] is null while the endpoint's home is undecided (docs/DECISIONS.md).
     */
    data class HostedEndpoint(
        val endpoint: String?,
    ) : CompileLocation {
        override val description: String
            get() =
                if (endpoint == null) {
                    "Compiled by a hosted build endpoint that has not been chosen yet. Nothing is sent."
                } else {
                    "Compiled by the hosted build endpoint at $endpoint. Your project is sent there when you build."
                }
    }
}

/** Whether a backend can run here. */
sealed interface BackendAvailability {
    data object Available : BackendAvailability

    data class Unavailable(
        val reason: String,
    ) : BackendAvailability
}
