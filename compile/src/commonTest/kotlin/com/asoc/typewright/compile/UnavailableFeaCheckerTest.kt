// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.compile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** The checker for a platform with no feature-file parser: it says why, to every text, and never claims anything was checked. */
class UnavailableFeaCheckerTest {
    @Test
    fun itSaysUpFrontThatItNeverReadsSoTheEditorDoesNotWaitForIt() {
        assertEquals("No parser here.", UnavailableFeaChecker("No parser here.").neverReads)
        val reading =
            object : FeaChecker {
                override suspend fun check(
                    text: String,
                    glyphNames: List<String>,
                ): FeaCheckResult = FeaCheckResult.Checked("t", "w", built = true, problems = emptyList())
            }
        assertEquals(null, reading.neverReads, "a checker that may read says nothing of the kind")
    }

    @Test
    fun everyTextGetsTheReasonAndNeverAClaimOfHavingBeenChecked() =
        runSuspend {
            val checker = UnavailableFeaChecker("No parser here.")
            for (text in listOf("", "feature kern { pos a b -1; } kern;", "this is not even a feature file {{{")) {
                val result = checker.check(text, listOf("a", "b"))
                assertEquals("No parser here.", assertIs<FeaCheckResult.Unavailable>(result).reason)
            }
        }
}
