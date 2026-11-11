// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GlyphDirectoryTest {
    private val directory =
        GlyphDirectory(
            listOf(
                GlyphEntry("A", listOf(0x41)),
                GlyphEntry("H", listOf(0x48)),
                GlyphEntry("n", listOf(0x6E)),
                GlyphEntry("o", listOf(0x6F)),
                GlyphEntry("O", listOf(0x4F)),
                GlyphEntry("oacute", listOf(0xF3)),
                GlyphEntry("a.alt", emptyList()),
                GlyphEntry("emoji", listOf(0x1F600)),
            ),
        )

    @Test
    fun theDefaultGlyphIsTheFirstOfTheDrawingOrderThatTheProjectHas() {
        // The plan asks for n first and the font has it.
        assertEquals("n", directory.defaultGlyph(listOf('n'.code, 'o'.code, 'H'.code)))
        assertEquals("o", directory.defaultGlyph(listOf('z'.code, 'o'.code, 'n'.code)), "skips code points the font lacks")
    }

    @Test
    fun withNothingFromThePlanTheFirstGlyphOfTheFontIsDefault() {
        assertEquals("A", directory.defaultGlyph(listOf('z'.code, 'y'.code)))
        assertEquals("A", directory.defaultGlyph(emptyList()))
        assertNull(GlyphDirectory(emptyList()).defaultGlyph(listOf('o'.code)))
    }

    @Test
    fun steppingWrapsAtBothEndsAndStartsFromTheFirstWhenNothingIsOpen() {
        assertEquals("H", directory.step("A", 1))
        assertEquals("emoji", directory.step("A", -1))
        assertEquals("A", directory.step("emoji", 1))
        assertEquals("A", directory.step(null, 1))
        assertEquals("A", directory.step("gone", -1))
        assertNull(GlyphDirectory(emptyList()).step("A", 1))
    }

    @Test
    fun aNameFindsItsGlyphFirstAndExactCaseBeatsACaseVariant() {
        val found = directory.search("o").map { it.entry.name }
        assertEquals("o", found.first())
        assertEquals(listOf("o", "O", "oacute"), found.take(3))
    }

    @Test
    fun aSingleCharacterFindsTheGlyphMappedFromIt() {
        val hit = directory.search("H").single()
        assertEquals("H", hit.entry.name)
        assertEquals(MatchKind.EXACT_NAME, hit.kind)
        val accent = directory.search("ó")
        assertEquals("oacute", accent.first().entry.name)
        assertEquals(MatchKind.CHARACTER, accent.first().kind)
    }

    @Test
    fun aSupplementaryCharacterIsOneCharacter() {
        val emojiText = codePointToString(0x1F600)
        assertEquals(2, emojiText.length)
        assertEquals(0x1F600, GlyphDirectory.singleCodePoint(emojiText))
        assertEquals(
            "emoji",
            directory
                .search(emojiText)
                .first()
                .entry.name,
        )
    }

    @Test
    fun aCodePointWrittenAsUPlusOrZeroXFindsItsGlyph() {
        assertEquals(
            "n",
            directory
                .search("U+006E")
                .first()
                .entry.name,
        )
        assertEquals(
            "o",
            directory
                .search("u+6f")
                .first()
                .entry.name,
        )
        assertEquals(
            "H",
            directory
                .search("0x48")
                .first()
                .entry.name,
        )
        assertEquals(emptyList(), directory.search("U+zz"))
    }

    @Test
    fun aPrefixRanksAboveContainsAndAnEmptyQueryFindsNothing() {
        val found = directory.search("acu").map { it.entry.name }
        assertEquals(listOf("oacute"), found, "only a name that contains the text")
        assertEquals(listOf("a.alt"), directory.search("a.").map { it.entry.name })
        assertEquals(emptyList(), directory.search("   "))
    }

    @Test
    fun theLimitCapsTheResults() {
        assertEquals(2, directory.search("o", limit = 2).size)
    }

    @Test
    fun anEntryWritesItsCharacterAndItsUnicodeForTheHeader() {
        assertEquals("o", directory.entry("o")?.character)
        assertEquals("U+006F", directory.entry("o")?.unicodeLabel)
        assertNull(directory.entry("a.alt")?.character)
        assertNull(directory.entry("a.alt")?.unicodeLabel)
        assertEquals("U+1F600", directory.entry("emoji")?.unicodeLabel)
    }
}
