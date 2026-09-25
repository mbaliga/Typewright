// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalWasmJsInterop::class)

package com.asoc.typewright.compile

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsString
import kotlin.js.Promise
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Proves [HostedEndpointBackend.compile] over a real socket: a real local HTTP server (Node's
 * own `http` module, [MockHttpServer.kt]) receiving a real `fetch()` POST from real production
 * code, not just Kotlin values asserted in memory. `:compile:wasmJsNodeTest` runs this (this
 * module's own `wasmJs { nodejs() }`); Node's global `fetch()` (Node >= 18) is the same Fetch API
 * a browser exposes, so this is the same call path a real browser runs in the shipped app.
 *
 * [HostedBuildProtocolTest] (commonTest) is this file's complement: the request/response
 * encoding proven on every target, including where `fetch()` cannot run at all (desktop/JVM,
 * Android host tests). Each test returns [runSuspendTest]'s [Promise] (`SuspendTest.kt`) rather
 * than being `suspend` itself -- the Kotlin 2.4.20 compiler refuses a `suspend fun` annotated
 * `@Test` on this target.
 */
class HostedEndpointBackendRealHttpTest {
    private val project =
        object : ProjectDirectory {
            override val displayName = "Real HTTP test project"

            override fun listFiles() = listOf("Font-Regular.ufo/metainfo.plist")

            override fun readBytes(path: String) = "<?xml version=\"1.0\"?><metainfo/>".encodeToByteArray()
        }

    private val request = CompileRequest(project, CompileSource.Ufo("Font-Regular.ufo"))

    @AfterTest
    fun tearDown() {
        stopMockHttpServer()
    }

    @Test
    fun successResponseFromARealServerBecomesARealCompileResultSuccess(): Promise<JsString> =
        runSuspendTest {
            val port =
                startMockHttpServer(
                    responseStatus = 200,
                    responseBodyJson =
                        """
                        {
                          "status": "success",
                          "binaries": [{"fileName": "Font-Regular.ttf", "format": "TTF", "contentBase64": "AQIDBA=="}],
                          "log": [{"level": "INFO", "message": "fontmake: done"}]
                        }
                        """.trimIndent(),
                )

            val result = HostedEndpointBackend("http://127.0.0.1:$port").compile(request)

            val success = assertIs<CompileResult.Success>(result)
            assertEquals(1, success.binaries.size)
            assertEquals("Font-Regular.ttf", success.binaries[0].fileName)
            assertEquals(OutputFormat.TTF, success.binaries[0].format)
            assertTrue(success.binaries[0].bytes.contentEquals(byteArrayOf(1, 2, 3, 4)))
            assertEquals(listOf(CompileLogLine(CompileLogLine.Level.INFO, "fontmake: done")), success.log)
        }

    @Test
    fun failureResponseFromARealServerBecomesARealCompileResultFailure(): Promise<JsString> =
        runSuspendTest {
            val port =
                startMockHttpServer(
                    responseStatus = 200,
                    responseBodyJson = """{"status": "failure", "reason": "glyph 'A' has an open contour"}""",
                )

            val result = HostedEndpointBackend("http://127.0.0.1:$port").compile(request)

            val failure = assertIs<CompileResult.Failure>(result)
            assertEquals("glyph 'A' has an open contour", failure.reason)
        }

    @Test
    fun aRealNonTwoHundredsResponseBecomesAFailureNamingTheStatus(): Promise<JsString> =
        runSuspendTest {
            val port = startMockHttpServer(responseStatus = 503, responseBodyJson = "service unavailable")

            val result = HostedEndpointBackend("http://127.0.0.1:$port").compile(request)

            val failure = assertIs<CompileResult.Failure>(result)
            assertTrue(failure.reason.contains("503"))
        }

    @Test
    fun theRealRequestThatReachedTheServerIsAPostWithTheProjectFileAndOptions(): Promise<JsString> =
        runSuspendTest {
            val port =
                startMockHttpServer(
                    responseStatus = 200,
                    responseBodyJson = """{"status": "failure", "reason": "irrelevant to this test"}""",
                )

            HostedEndpointBackend("http://127.0.0.1:$port").compile(request)

            val received = lastMockRequest()
            assertEquals("POST", received.getValue("method").jsonPrimitive.content)
            assertEquals(HostedBuildProtocol.COMPILE_PATH, received.getValue("url").jsonPrimitive.content)
            assertTrue(
                received
                    .getValue("contentType")
                    .jsonPrimitive.content
                    .startsWith("application/json"),
            )

            val sentBody = Json.parseToJsonElement(received.getValue("bodyText").jsonPrimitive.content).jsonObject
            assertEquals(
                "ufo",
                sentBody
                    .getValue("source")
                    .jsonObject
                    .getValue("kind")
                    .jsonPrimitive.content,
            )
            assertEquals(
                "Font-Regular.ufo",
                sentBody
                    .getValue("source")
                    .jsonObject
                    .getValue("path")
                    .jsonPrimitive.content,
            )
            val sentPaths =
                sentBody.getValue("files").jsonArray.map {
                    it.jsonObject
                        .getValue("path")
                        .jsonPrimitive.content
                }
            assertEquals(listOf("Font-Regular.ufo/metainfo.plist"), sentPaths)
            assertTrue(
                sentBody
                    .getValue("options")
                    .jsonObject
                    .getValue("autohint")
                    .jsonPrimitive.content == "true",
            )
        }

    /** Connecting to a real closed port on loopback is itself a real network failure, not a Kotlin fake of one. */
    @Test
    fun aRealUnreachableEndpointBecomesAFailureRatherThanThrowing(): Promise<JsString> =
        runSuspendTest {
            val result = HostedEndpointBackend("http://127.0.0.1:1").compile(request)

            val failure = assertIs<CompileResult.Failure>(result)
            assertTrue(failure.reason.contains("127.0.0.1"))
        }

    private fun lastMockRequest(): Map<String, JsonElement> {
        val raw = lastMockRequestJson()?.toString() ?: error("mock server received no request")
        return Json.parseToJsonElement(raw).jsonObject
    }
}
