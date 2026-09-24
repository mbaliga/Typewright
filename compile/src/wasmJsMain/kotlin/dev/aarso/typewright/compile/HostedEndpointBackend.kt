package dev.aarso.typewright.compile

/**
 * Web backend, planned: the project is sent to a hosted fontmake endpoint when the user starts
 * a build, and the Ship room names the endpoint each time (brief §3, prompt P9). The endpoint's
 * home is undecided, so [endpoint] is null. Stub until built: it sends nothing.
 */
class HostedEndpointBackend(
    val endpoint: String? = null,
) : CompileBackend {
    override val name: String = "fontmake (hosted build endpoint)"

    override val location: CompileLocation = CompileLocation.HostedEndpoint(endpoint)

    override suspend fun availability(): BackendAvailability = BackendAvailability.Unavailable(PLANNED)

    override suspend fun compile(request: CompileRequest): CompileResult = notImplemented(name, PLANNED)

    private companion object {
        const val PLANNED =
            "Not built yet. Planned: on an explicit build, upload the project to the hosted " +
                "endpoint, which runs fontmake in a container, keeps nothing, and returns the binaries."
    }
}

actual fun platformCompileBackend(): CompileBackend = HostedEndpointBackend()
