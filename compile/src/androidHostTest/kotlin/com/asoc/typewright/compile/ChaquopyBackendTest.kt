// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.compile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Compiling on a phone is not built, and the Android backend says so in the words the owner decided
 * on (D5): it reports itself unavailable, hands back [CompileResult.NotImplemented] and produces no
 * font. These run on the JVM; whether anything better works on a device is for the owner to try.
 */
class ChaquopyBackendTest {
    private val project =
        object : ProjectDirectory {
            override val displayName = "Empty"

            override fun listFiles() = emptyList<String>()

            override fun readBytes(path: String) = error("no files in $path")
        }

    @Test
    fun availabilitySaysCompilingOnThisPhoneIsNotBuiltAndPointsToTheDesktopApp() {
        val availability = runSuspend { ChaquopyBackend().availability() }
        assertIs<BackendAvailability.Unavailable>(availability)
        assertEquals(
            "Compiling on this phone is not built in this version (the Android route is an owner decision, D5); build in the desktop app.",
            availability.reason,
        )
    }

    @Test
    fun compileProducesNothingAndStreamsItsOneLineToTheCaller() {
        val seen = mutableListOf<CompileLogLine>()
        val result = runSuspend { ChaquopyBackend().compile(CompileRequest(project, CompileSource.Ufo("Font-Regular.ufo")), seen::add) }
        assertIs<CompileResult.NotImplemented>(result)
        assertTrue(result.log.isNotEmpty())
        assertEquals(result.log, seen)
    }

    @Test
    fun thePlatformBackendOnAndroidIsTheStub() {
        assertIs<ChaquopyBackend>(platformCompileBackend())
    }
}
