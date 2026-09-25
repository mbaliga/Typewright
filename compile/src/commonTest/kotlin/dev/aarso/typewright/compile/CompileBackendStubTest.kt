// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.compile

import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

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
        val request = CompileRequest(emptyProject, CompileSource.Ufo("Font-Regular.ufo"))

        val result = runSuspend { backend.compile(request) }

        assertIs<CompileResult.NotImplemented>(result)
        assertTrue(result.log.isNotEmpty())
        assertIs<BackendAvailability.Unavailable>(runSuspend { backend.availability() })
        assertTrue(backend.location.description.isNotBlank())
    }
}
