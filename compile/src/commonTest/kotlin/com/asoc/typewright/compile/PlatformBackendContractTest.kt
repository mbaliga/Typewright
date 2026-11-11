// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.compile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * What every platform's backend promises without being run: it names itself, says in a sentence where
 * it compiles, and the shared stub result says plainly that nothing was compiled. The real work of each
 * backend is tested where it can run: the desktop backend in `desktopTest`, the Android and web stubs in
 * their own source sets.
 */
class PlatformBackendContractTest {
    @Test
    fun theBackendNamesItselfAndSaysWhereItCompiles() {
        val backend = platformCompileBackend()
        assertTrue(backend.name.isNotBlank())
        assertTrue(backend.location.description.isNotBlank())
        assertTrue(backend.location.description.endsWith("."), "a sentence: ${backend.location.description}")
    }

    @Test
    fun aStubResultSaysNothingWasCompiledAndCarriesNoBinaries() {
        val result = notImplemented("a backend", "why it cannot")
        assertIs<CompileResult.NotImplemented>(result)
        assertEquals("a backend", result.backend)
        assertEquals("why it cannot", result.planned)
        assertEquals(CompileLogLine.Level.ERROR, result.log.single().level)
        assertTrue("Nothing was compiled" in result.log.single().message)
    }
}
