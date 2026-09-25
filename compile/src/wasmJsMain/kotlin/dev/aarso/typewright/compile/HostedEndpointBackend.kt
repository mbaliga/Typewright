// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.compile

/**
 * Web backend: the project is sent to a hosted fontmake endpoint when the user starts a build,
 * and the Ship room names the endpoint each time (brief §3, prompt P9, CLAUDE.md law 3). The
 * wire contract this class speaks is documented in full at docs/HOSTED_BUILD_ENDPOINT.md;
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

    override suspend fun compile(request: CompileRequest): CompileResult {
        val endpoint = endpoint ?: return notImplemented(name, PLANNED)
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

    private companion object {
        const val PLANNED =
            "Not built yet. Planned: on an explicit build, upload the project to the hosted " +
                "endpoint, which runs fontmake in a container, keeps nothing, and returns the binaries."
    }
}

actual fun platformCompileBackend(): CompileBackend = HostedEndpointBackend()
