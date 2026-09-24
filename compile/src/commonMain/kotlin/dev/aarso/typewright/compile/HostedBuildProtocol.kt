package dev.aarso.typewright.compile

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * The hosted build endpoint's wire contract (docs/HOSTED_BUILD_ENDPOINT.md), as pure Kotlin: no
 * wasmJs, no network, so it is provable on every target this module builds for -- including
 * desktop/JVM, where `fetch()` itself cannot run. [HostedEndpointBackend] (wasmJsMain) is the
 * only caller: it builds the request body with [encodeRequest], sends it to
 * `endpoint + `[COMPILE_PATH] itself, and hands whatever the transport layer got back to
 * [interpretTransportOutcome].
 *
 * [HostedBuildProtocolTest] (commonTest) is this file's own test -- it runs on desktop/JVM,
 * Android host tests and `wasmJsNodeTest`, proving the encode/decode logic on every target
 * regardless of whether that target can also prove the HTTP call itself (only
 * `HostedEndpointBackendRealHttpTest`, wasmJsTest under Node, does that, against a real local
 * server -- docs/HOSTED_BUILD_ENDPOINT.md's own "Tested" section).
 */
internal object HostedBuildProtocol {
    /** Appended to the configured endpoint for the one call this backend makes. */
    const val COMPILE_PATH: String = "/v1/compile"

    private const val WIRE_VERSION = 1

    private val json = Json { ignoreUnknownKeys = true }

    /** Builds the JSON body for one build: every file in [CompileRequest.project], base64-encoded, plus [CompileSource] and [CompileOptions]. */
    @OptIn(ExperimentalEncodingApi::class)
    fun encodeRequest(request: CompileRequest): String {
        val payload =
            RequestPayload(
                requestVersion = WIRE_VERSION,
                source = SourcePayload(kind = request.source.wireKind(), path = request.source.path),
                options =
                    OptionsPayload(
                        formats = request.options.formats.map { it.name },
                        removeOverlaps = request.options.removeOverlaps,
                        autohint = request.options.autohint,
                    ),
                files =
                    request.project.listFiles().map { path ->
                        FilePayload(path = path, contentBase64 = Base64.encode(request.project.readBytes(path)))
                    },
            )
        return json.encodeToString(RequestPayload.serializer(), payload)
    }

    /**
     * Turns the transport layer's own outcome JSON (`{"ok":...}`, mirroring [TransportOutcome])
     * into a [CompileResult]. A transport-level problem -- `fetch()` itself rejected, or the
     * endpoint answered with a non-2xx status -- becomes a [CompileResult.Failure] naming
     * [endpoint], the same as a build that ran and failed; [HostedEndpointBackend]'s own KDoc
     * explains why [CompileResult] has no separate case for it.
     */
    fun interpretTransportOutcome(
        rawOutcomeJson: String,
        endpoint: String,
    ): CompileResult {
        val outcome =
            try {
                json.decodeFromString(TransportOutcome.serializer(), rawOutcomeJson)
            } catch (malformed: Exception) {
                return CompileResult.Failure(
                    reason = "The hosted build endpoint's transport layer returned something this client could not read.",
                    log = listOf(errorLine("malformed transport outcome: ${malformed.message}")),
                )
            }
        if (!outcome.ok) {
            return CompileResult.Failure(
                reason = "Could not reach the hosted build endpoint at $endpoint: ${outcome.error}",
                log = listOf(errorLine(outcome.error ?: "unknown network error")),
            )
        }
        if (outcome.status !in 200..299) {
            return CompileResult.Failure(
                reason = "The hosted build endpoint at $endpoint returned HTTP ${outcome.status}.",
                log = listOf(errorLine(outcome.bodyText.orEmpty().take(MAX_ECHOED_BODY_CHARS))),
            )
        }
        return decodeResponseBody(outcome.bodyText.orEmpty(), endpoint)
    }

    /** Parses a `2xx` response body -- exposed separately from [interpretTransportOutcome] so a test can feed it the doc's own example bodies directly. */
    @OptIn(ExperimentalEncodingApi::class)
    internal fun decodeResponseBody(
        body: String,
        endpoint: String,
    ): CompileResult {
        val response =
            try {
                json.decodeFromString(ResponsePayload.serializer(), body)
            } catch (malformed: Exception) {
                return CompileResult.Failure(
                    reason = "The hosted build endpoint at $endpoint returned a response this client could not read.",
                    log = listOf(errorLine("malformed response body: ${malformed.message}")),
                )
            }
        val log = response.log.map { CompileLogLine(level = it.level.toLevelOrInfo(), message = it.message) }
        return when (response.status) {
            "success" -> {
                val warnings = mutableListOf<CompileLogLine>()
                val binaries =
                    response.binaries.mapNotNull { binary ->
                        binary.toFontBinaryOrNull { warning -> warnings += warning }
                    }
                CompileResult.Success(binaries = binaries, log = log + warnings)
            }

            "failure" -> {
                CompileResult.Failure(
                    reason = response.reason ?: "The hosted build endpoint reported a failure with no reason.",
                    log = log,
                )
            }

            else -> {
                CompileResult.Failure(
                    reason = "The hosted build endpoint at $endpoint returned an unrecognised status \"${response.status}\".",
                    log = log,
                )
            }
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun BinaryPayload.toFontBinaryOrNull(onWarning: (CompileLogLine) -> Unit): FontBinary? {
        val format = format.toOutputFormatOrNull()
        if (format == null) {
            onWarning(
                CompileLogLine(
                    CompileLogLine.Level.WARNING,
                    "hosted build endpoint returned a binary with an unrecognised format \"${this.format}\" ($fileName); dropped.",
                ),
            )
            return null
        }
        val bytes =
            try {
                Base64.decode(contentBase64)
            } catch (malformed: IllegalArgumentException) {
                onWarning(
                    CompileLogLine(
                        CompileLogLine.Level.WARNING,
                        "hosted build endpoint returned a binary ($fileName) whose contentBase64 would not decode: ${malformed.message}; dropped.",
                    ),
                )
                return null
            }
        return FontBinary(fileName = fileName, format = format, bytes = bytes)
    }

    private fun errorLine(message: String) = CompileLogLine(CompileLogLine.Level.ERROR, message)

    private fun CompileSource.wireKind(): String =
        when (this) {
            is CompileSource.Ufo -> "ufo"
            is CompileSource.Designspace -> "designspace"
        }

    private fun String.toLevelOrInfo(): CompileLogLine.Level =
        CompileLogLine.Level.entries.firstOrNull { it.name == this } ?: CompileLogLine.Level.INFO

    private fun String.toOutputFormatOrNull(): OutputFormat? = OutputFormat.entries.firstOrNull { it.name == this }

    private const val MAX_ECHOED_BODY_CHARS = 2_000
}

/** The transport layer's own outcome for one HTTP call, before the response body inside it is even looked at -- see [HostedEndpointBackend]'s wasmJsMain half, which is the only thing that produces this JSON. */
@Serializable
internal data class TransportOutcome(
    /** False only when `fetch()` itself rejected (DNS, refused connection, CORS, ...); [error] then names why. */
    val ok: Boolean,
    /** The HTTP status `fetch()` got back; meaningless when [ok] is false. */
    val status: Int = 0,
    /** The response body as text; meaningless when [ok] is false. */
    val bodyText: String? = null,
    /** Why `fetch()` rejected; null when [ok] is true. */
    val error: String? = null,
)

@Serializable
private data class RequestPayload(
    val requestVersion: Int,
    val source: SourcePayload,
    val options: OptionsPayload,
    val files: List<FilePayload>,
)

@Serializable
private data class SourcePayload(
    val kind: String,
    val path: String,
)

@Serializable
private data class OptionsPayload(
    val formats: List<String>,
    val removeOverlaps: Boolean,
    val autohint: Boolean,
)

@Serializable
private data class FilePayload(
    val path: String,
    val contentBase64: String,
)

@Serializable
private data class ResponsePayload(
    val responseVersion: Int = 1,
    val status: String,
    val reason: String? = null,
    val binaries: List<BinaryPayload> = emptyList(),
    val log: List<LogPayload> = emptyList(),
)

@Serializable
private data class BinaryPayload(
    val fileName: String,
    val format: String,
    val contentBase64: String,
)

@Serializable
private data class LogPayload(
    val level: String,
    val message: String,
)
