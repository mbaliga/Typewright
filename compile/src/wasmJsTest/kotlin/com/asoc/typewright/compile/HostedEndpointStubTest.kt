// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalWasmJsInterop::class)

package com.asoc.typewright.compile

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsString
import kotlin.js.Promise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * With no endpoint chosen (the default: decision D4 makes the web a preview that does not compile),
 * the web backend says so in words, compiles nothing and sends nothing.
 */
class HostedEndpointStubTest {
    private val project =
        object : ProjectDirectory {
            override val displayName = "Empty"

            override fun listFiles() = emptyList<String>()

            override fun readBytes(path: String) = error("no files in $path")
        }

    @Test
    fun availabilitySaysThePreviewDoesNotCompileAndWhatToDoInstead(): Promise<JsString> =
        runSuspendTest {
            val availability = HostedEndpointBackend().availability()
            assertIs<BackendAvailability.Unavailable>(availability)
            assertEquals(
                "The web preview does not compile (D4); export the UFO and Ship folder and build on the desktop app.",
                availability.reason,
            )
        }

    @Test
    fun compileProducesNothingAndStreamsItsLineToTheCaller(): Promise<JsString> =
        runSuspendTest {
            val seen = mutableListOf<CompileLogLine>()
            val result = HostedEndpointBackend().compile(CompileRequest(project, CompileSource.Ufo("Font-Regular.ufo")), seen::add)
            assertIs<CompileResult.NotImplemented>(result)
            assertTrue(result.log.isNotEmpty())
            assertEquals(result.log, seen)
        }
}
