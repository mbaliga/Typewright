// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.scripts.kana

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KanaGlyphNamingTest {
    @Test
    fun derivesTheBaseHiraganaSyllableNames() {
        assertEquals("a-hira", deriveKanaGlyphName("HIRAGANA LETTER A", KanaScriptKind.HIRAGANA))
        assertEquals("ka-hira", deriveKanaGlyphName("HIRAGANA LETTER KA", KanaScriptKind.HIRAGANA))
        assertEquals("n-hira", deriveKanaGlyphName("HIRAGANA LETTER N", KanaScriptKind.HIRAGANA))
    }

    @Test
    fun usesTheRealUnicodeNamesNihonShikiSpellingNotHepburn() {
        // U+3057 し is HIRAGANA LETTER SI in Unicode's own name, not "SHI" -- the derived glyph
        // name should carry that through unchanged, not a Hepburn romanisation invented here.
        assertEquals("si-hira", deriveKanaGlyphName("HIRAGANA LETTER SI", KanaScriptKind.HIRAGANA))
        assertEquals("ti-hira", deriveKanaGlyphName("HIRAGANA LETTER TI", KanaScriptKind.HIRAGANA))
        assertEquals("tu-hira", deriveKanaGlyphName("HIRAGANA LETTER TU", KanaScriptKind.HIRAGANA))
        assertEquals("hu-hira", deriveKanaGlyphName("HIRAGANA LETTER HU", KanaScriptKind.HIRAGANA))
    }

    @Test
    fun derivesSmallKanaNamesWithTheSmallPrefixPreserved() {
        assertEquals("small-a-hira", deriveKanaGlyphName("HIRAGANA LETTER SMALL A", KanaScriptKind.HIRAGANA))
        assertEquals("small-tu-kata", deriveKanaGlyphName("KATAKANA LETTER SMALL TU", KanaScriptKind.KATAKANA))
    }

    @Test
    fun derivesTheBaseKatakanaSyllableNames() {
        assertEquals("a-kata", deriveKanaGlyphName("KATAKANA LETTER A", KanaScriptKind.KATAKANA))
        assertEquals("n-kata", deriveKanaGlyphName("KATAKANA LETTER N", KanaScriptKind.KATAKANA))
    }

    @Test
    fun derivesTheTwoKatakanaGlyphsThatAreNotLetterForms() {
        assertEquals("middle-dot-kata", deriveKanaGlyphName("KATAKANA MIDDLE DOT", KanaScriptKind.KATAKANA))
        assertEquals(
            "prolonged-sound-mark-kata",
            deriveKanaGlyphName("KATAKANA-HIRAGANA PROLONGED SOUND MARK", KanaScriptKind.KATAKANA),
        )
    }

    @Test
    fun refusesAnUnrecognisedUnicodeNameRatherThanGuessing() {
        assertFailsWith<IllegalArgumentException> {
            deriveKanaGlyphName("DEVANAGARI LETTER KA", KanaScriptKind.HIRAGANA)
        }
        assertFailsWith<IllegalArgumentException> {
            deriveKanaGlyphName("HIRAGANA LETTER A", KanaScriptKind.KATAKANA)
        }
    }

    @Test
    fun everyKanaScriptKindSuffixMatchesThisTasksOwnNamingConvention() {
        assertEquals("-hira", KanaScriptKind.HIRAGANA.glyphNameSuffix)
        assertEquals("-kata", KanaScriptKind.KATAKANA.glyphNameSuffix)
    }
}
