// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.scripts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScriptProfileTest {
    @Test
    fun everyWritingScriptHasARealFourLetterIsoCode() {
        for (script in WritingScript.entries) {
            assertEquals(4, script.isoCode.length, "${script.name}'s ISO 15924 code should be 4 letters")
        }
    }

    @Test
    fun onlyArabicNaskhIsRightToLeft() {
        assertTrue(WritingScript.ARABIC_NASKH.rightToLeft)
        for (script in WritingScript.entries - WritingScript.ARABIC_NASKH) {
            assertFalse(script.rightToLeft, "${script.name} should not be right-to-left")
        }
    }

    @Test
    fun scriptMetricSystemsOwnRightToLeftMatchesItsScripts() {
        val devanagari = ScriptMetricSystem(WritingScript.DEVANAGARI, unitsPerEm = 1000, lines = emptyList())
        val arabic = ScriptMetricSystem(WritingScript.ARABIC_NASKH, unitsPerEm = 1000, lines = emptyList())
        assertFalse(devanagari.rightToLeft)
        assertTrue(arabic.rightToLeft)
    }
}
