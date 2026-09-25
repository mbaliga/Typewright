// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.compile

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * [HostedBuildProtocol]'s own test: pure encode/decode logic, so this runs on desktop/JVM and
 * Android host tests as well as `wasmJsNodeTest` -- proof of the wire encoding that does not
 * depend on `fetch()` itself, which only [HostedEndpointBackendRealHttpTest] (wasmJsTest, real
 * local HTTP server) can exercise. docs/HOSTED_BUILD_ENDPOINT.md documents the same contract
 * this file asserts against.
 */
class HostedBuildProtocolTest {
    private val inspectionJson = Json

    private class FakeProject(
        private val files: Map<String, ByteArray>,
    ) : ProjectDirectory {
        override val displayName = "Fake"

        override fun listFiles() = files.keys.toList()

        override fun readBytes(path: String) = files.getValue(path)
    }

    private val metainfo = "<?xml version=\"1.0\"?><metainfo/>".encodeToByteArray()
    private val glyphA = byteArrayOf(0, 1, 2, 3, 250.toByte(), 255.toByte())
    private val project =
        FakeProject(
            mapOf(
                "Font-Regular.ufo/metainfo.plist" to metainfo,
                "Font-Regular.ufo/glyphs/A_.glif" to glyphA,
            ),
        )

    // ---- encodeRequest ----

    @Test
    fun encodeRequestCarriesVersionSourceAndOptions() {
        val request =
            CompileRequest(
                project = project,
                source = CompileSource.Ufo("Font-Regular.ufo"),
                options =
                    CompileOptions(
                        formats = setOf(OutputFormat.TTF, OutputFormat.WOFF2),
                        removeOverlaps = false,
                        autohint = true,
                    ),
            )

        val body = inspectionJson.parseToJsonElement(HostedBuildProtocol.encodeRequest(request)).jsonObject

        assertEquals(
            1,
            body
                .getValue("requestVersion")
                .jsonPrimitive.content
                .toInt(),
        )
        assertEquals(
            "ufo",
            body
                .getValue("source")
                .jsonObject
                .getValue("kind")
                .jsonPrimitive.content,
        )
        assertEquals(
            "Font-Regular.ufo",
            body
                .getValue("source")
                .jsonObject
                .getValue("path")
                .jsonPrimitive.content,
        )
        val options = body.getValue("options").jsonObject
        assertFalse(options.getValue("removeOverlaps").jsonPrimitive.boolean)
        assertTrue(options.getValue("autohint").jsonPrimitive.boolean)
        val formats = options.getValue("formats").jsonArray.map { it.jsonPrimitive.content }
        assertEquals(setOf("TTF", "WOFF2"), formats.toSet())
    }

    @Test
    fun encodeRequestNamesDesignspaceSourceKind() {
        val request = CompileRequest(project, CompileSource.Designspace("Font.designspace"))

        val body = inspectionJson.parseToJsonElement(HostedBuildProtocol.encodeRequest(request)).jsonObject

        assertEquals(
            "designspace",
            body
                .getValue("source")
                .jsonObject
                .getValue("kind")
                .jsonPrimitive.content,
        )
        assertEquals(
            "Font.designspace",
            body
                .getValue("source")
                .jsonObject
                .getValue("path")
                .jsonPrimitive.content,
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun encodeRequestSendsEveryProjectFileBase64EncodedByteForByte() {
        val request = CompileRequest(project, CompileSource.Ufo("Font-Regular.ufo"))

        val body = inspectionJson.parseToJsonElement(HostedBuildProtocol.encodeRequest(request)).jsonObject
        val files =
            body.getValue("files").jsonArray.associate { element ->
                val obj = element.jsonObject
                obj.getValue("path").jsonPrimitive.content to obj.getValue("contentBase64").jsonPrimitive.content
            }

        assertEquals(setOf("Font-Regular.ufo/metainfo.plist", "Font-Regular.ufo/glyphs/A_.glif"), files.keys)
        assertTrue(Base64.decode(files.getValue("Font-Regular.ufo/metainfo.plist")).contentEquals(metainfo))
        assertTrue(Base64.decode(files.getValue("Font-Regular.ufo/glyphs/A_.glif")).contentEquals(glyphA))
    }

    // ---- interpretTransportOutcome: transport-level problems ----

    @Test
    fun networkFailureBecomesAFailureNamingTheEndpointAndTheError() {
        val outcome = """{"ok":false,"error":"getaddrinfo ENOTFOUND example.invalid"}"""

        val result = HostedBuildProtocol.interpretTransportOutcome(outcome, "https://example.invalid")

        val failure = assertIs<CompileResult.Failure>(result)
        assertTrue(failure.reason.contains("https://example.invalid"))
        assertTrue(failure.reason.contains("ENOTFOUND"))
        assertTrue(failure.log.any { it.level == CompileLogLine.Level.ERROR })
    }

    @Test
    fun nonTwoHundredsStatusBecomesAFailureNamingTheStatus() {
        val outcome = """{"ok":true,"status":503,"bodyText":"upstream overloaded"}"""

        val result = HostedBuildProtocol.interpretTransportOutcome(outcome, "https://build.example")

        val failure = assertIs<CompileResult.Failure>(result)
        assertTrue(failure.reason.contains("503"))
        assertTrue(failure.log.any { it.message.contains("upstream overloaded") })
    }

    @Test
    fun malformedTransportOutcomeJsonBecomesAFailureRatherThanThrowing() {
        val result = HostedBuildProtocol.interpretTransportOutcome("not json at all", "https://build.example")

        assertIs<CompileResult.Failure>(result)
    }

    // ---- decodeResponseBody: the response body once a 2xx arrived ----

    @Test
    fun successResponseDecodesBinariesAndLog() {
        val alpha = byteArrayOf(1, 2, 3, 4, 5)
        val body =
            """
            {
              "status": "success",
              "binaries": [{"fileName": "Font-Regular.ttf", "format": "TTF", "contentBase64": "${base64Of(alpha)}"}],
              "log": [{"level": "INFO", "message": "fontmake: done"}]
            }
            """.trimIndent()

        val result = HostedBuildProtocol.decodeResponseBody(body, "https://build.example")

        val success = assertIs<CompileResult.Success>(result)
        assertEquals(1, success.binaries.size)
        assertEquals("Font-Regular.ttf", success.binaries[0].fileName)
        assertEquals(OutputFormat.TTF, success.binaries[0].format)
        assertTrue(success.binaries[0].bytes.contentEquals(alpha))
        assertEquals(listOf(CompileLogLine(CompileLogLine.Level.INFO, "fontmake: done")), success.log)
    }

    @Test
    fun failureResponseCarriesItsReason() {
        val body = """{"status": "failure", "reason": "glyph 'A' has an open contour"}"""

        val result = HostedBuildProtocol.decodeResponseBody(body, "https://build.example")

        val failure = assertIs<CompileResult.Failure>(result)
        assertEquals("glyph 'A' has an open contour", failure.reason)
    }

    @Test
    fun failureResponseWithNoReasonGetsAPlainSentenceAnyway() {
        val body = """{"status": "failure"}"""

        val result = HostedBuildProtocol.decodeResponseBody(body, "https://build.example")

        val failure = assertIs<CompileResult.Failure>(result)
        assertTrue(failure.reason.isNotBlank())
    }

    @Test
    fun unrecognisedStatusBecomesAFailureRatherThanBeingTreatedAsSuccess() {
        val body = """{"status": "pending"}"""

        val result = HostedBuildProtocol.decodeResponseBody(body, "https://build.example")

        val failure = assertIs<CompileResult.Failure>(result)
        assertTrue(failure.reason.contains("pending"))
    }

    @Test
    fun malformedResponseBodyBecomesAFailureRatherThanThrowing() {
        val result = HostedBuildProtocol.decodeResponseBody("{not valid json", "https://build.example")

        assertIs<CompileResult.Failure>(result)
    }

    @Test
    fun unknownLogLevelFallsBackToInfoRatherThanDroppingTheLine() {
        val body = """{"status": "success", "log": [{"level": "TRACE", "message": "chatty"}]}"""

        val result = HostedBuildProtocol.decodeResponseBody(body, "https://build.example")

        val success = assertIs<CompileResult.Success>(result)
        assertEquals(listOf(CompileLogLine(CompileLogLine.Level.INFO, "chatty")), success.log)
    }

    @Test
    fun binaryWithAnUnrecognisedFormatIsDroppedAndWarnedAboutRatherThanMislabelled() {
        val body =
            """
            {
              "status": "success",
              "binaries": [{"fileName": "Font-Regular.eot", "format": "EOT", "contentBase64": "${base64Of(byteArrayOf(1))}"}]
            }
            """.trimIndent()

        val result = HostedBuildProtocol.decodeResponseBody(body, "https://build.example")

        val success = assertIs<CompileResult.Success>(result)
        assertTrue(success.binaries.isEmpty())
        assertTrue(success.log.any { it.level == CompileLogLine.Level.WARNING && it.message.contains("EOT") })
    }

    @Test
    fun binaryWithUndecodableBase64IsDroppedAndWarnedAboutRatherThanThrowing() {
        val body =
            """
            {
              "status": "success",
              "binaries": [{"fileName": "Font-Regular.ttf", "format": "TTF", "contentBase64": "not-base64!!"}]
            }
            """.trimIndent()

        val result = HostedBuildProtocol.decodeResponseBody(body, "https://build.example")

        val success = assertIs<CompileResult.Success>(result)
        assertTrue(success.binaries.isEmpty())
        assertTrue(success.log.any { it.level == CompileLogLine.Level.WARNING })
    }

    @Test
    fun compilePathIsTheDocumentedPath() {
        assertEquals("/v1/compile", HostedBuildProtocol.COMPILE_PATH)
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun base64Of(bytes: ByteArray): String = Base64.encode(bytes)
}
