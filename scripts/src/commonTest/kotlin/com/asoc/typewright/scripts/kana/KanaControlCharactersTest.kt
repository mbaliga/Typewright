// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.scripts.kana

import com.asoc.typewright.scripts.WritingScript
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KanaControlCharactersTest {
    @Test
    fun bothControlCharacterSetsCarryTheirOwnScript() {
        assertEquals(WritingScript.HIRAGANA, HIRAGANA_CONTROL_CHARACTERS.script)
        assertEquals(WritingScript.KATAKANA, KATAKANA_CONTROL_CHARACTERS.script)
    }

    @Test
    fun bothControlCharacterSetsAreHonestlyEmptyBecauseNoRealSourceGivesAKanaStartingSequence() {
        // Pinned deliberately: docs/RESEARCH_font_quality.md's kana subsection states plainly
        // "No 'draw these kana first' list was found", and docs/LESSONS_SCAFFOLD.md section 5's
        // kana bullet names none either. If a future edit adds entries here without a real,
        // cited source, this test should be the thing that makes that reviewable, not silent.
        assertTrue(HIRAGANA_CONTROL_CHARACTERS.controlCharacters.isEmpty())
        assertTrue(KATAKANA_CONTROL_CHARACTERS.controlCharacters.isEmpty())
    }
}
