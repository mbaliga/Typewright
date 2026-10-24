// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.compile

/**
 * Web backend: the project is sent to a hosted fontmake endpoint when the user starts a build.
 * Whatever UI calls this **must** name the endpoint to the user every time it is used (brief §3,
 * prompt P9, CLAUDE.md law 3) -- not wired yet: no Ship room or other caller in `ui` invokes
 * [CompileBackend] at all yet, so nothing currently shows that name. The wire contract this class
 * speaks is documented in full at docs/HOSTED_BUILD_ENDPOINT.md;
 * [HostedBuildProtocol] (commonMain) builds the request body and reads the response back, and
 * this class does only the HTTP call itself ([HostedEndpointTransport.kt]'s `postJsonForOutcome`).
 *
 * [endpoint]'s home is undecided (docs/DECISIONS.md), so it is null by default. With no endpoint
 * configured this stays the honest stub it always was: [availability] unavailable, [compile] a
 * [CompileResult.NotImplemented], nothing sent -- that branch is unchanged from before this
 * class had a real implementation to fall back from. With an endpoint configured, [availability]
 * reports [BackendAvailability.Available] (there is now something real to try; per-call failures,
 * including "could not reach it", surface from [compile] as [CompileResult.Failure] rather than
 * from here) and [compile] performs the real upload.
 */
class HostedEndpointBackend(
    val endpoint: String? = null,
) : CompileBackend {
    override val name: String = "fontmake (hosted build endpoint)"

    override val location: CompileLocation = CompileLocation.HostedEndpoint(endpoint)

    override suspend fun availability(): BackendAvailability =
        if (endpoint == null) {
            BackendAvailability.Unavailable(PLANNED)
        } else {
            BackendAvailability.Available
        }

    override suspend fun compile(
        request: CompileRequest,
        onLog: (CompileLogLine) -> Unit,
    ): CompileResult {
        val endpoint = endpoint ?: return notImplemented(name, PLANNED).also { result -> result.log.forEach(onLog) }
        return try {
            val bodyJson = HostedBuildProtocol.encodeRequest(request)
            val outcomeJson = postJsonForOutcome(endpoint, HostedBuildProtocol.COMPILE_PATH, bodyJson)
            HostedBuildProtocol.interpretTransportOutcome(outcomeJson, endpoint)
        } catch (unexpected: Throwable) {
            CompileResult.Failure(
                reason = "Could not build with the hosted build endpoint at $endpoint: ${unexpected.message ?: unexpected.toString()}",
                log = listOf(CompileLogLine(CompileLogLine.Level.ERROR, unexpected.toString())),
            )
        }
    }

    companion object {
        /** What the UI shows wherever it would offer to build in the browser while no endpoint is chosen (decision D4). */
        const val PLANNED =
            "The web preview does not compile (D4); export the UFO and Ship folder and build on the desktop app."
    }
}

actual fun platformCompileBackend(): CompileBackend = HostedEndpointBackend()
