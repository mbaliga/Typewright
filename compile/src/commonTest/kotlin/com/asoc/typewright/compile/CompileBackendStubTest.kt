// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.compile

import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * What the platform's backend answers on a phone and in a browser, where it is a stub: nothing was compiled, it
 * says so and why, it produces no font and it reports itself unavailable. On a computer the backend is the real
 * compiler, which suspends on real processes and so cannot be driven from common code; its answers are tested in
 * `desktopTest` ([SystemPythonFontmakeBackendTest]). Each platform's exact message is pinned where it can run:
 * `ChaquopyBackendTest` on Android and `HostedEndpointStubTest` in the browser.
 */
class CompileBackendStubTest {
    private val emptyProject =
        object : ProjectDirectory {
            override val displayName = "Empty"

            override fun listFiles() = emptyList<String>()

            override fun readBytes(path: String) = error("no files in $path")
        }

    @Test
    fun stubSaysNotImplementedAndProducesNothing() {
        val backend = platformCompileBackend()
        assertTrue(backend.name.isNotBlank())
        assertTrue(backend.location.description.isNotBlank())
        // A computer compiles for real; that is not a stub, and its tests are the desktop ones.
        if (backend.location is CompileLocation.OnThisComputer) return

        val request = CompileRequest(emptyProject, CompileSource.Ufo("Font-Regular.ufo"))
        val result = runSuspend { backend.compile(request) }

        assertIs<CompileResult.NotImplemented>(result)
        assertTrue(result.log.isNotEmpty())
        assertIs<BackendAvailability.Unavailable>(runSuspend { backend.availability() })
    }
}
