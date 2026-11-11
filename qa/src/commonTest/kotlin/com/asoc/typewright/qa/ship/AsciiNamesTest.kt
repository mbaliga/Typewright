// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.ship

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AsciiNamesTest {
    @Test
    fun printableAsciiIsKeptAsItIs() {
        val all = (0x20..0x7E).map { it.toChar() }.joinToString("")
        assertEquals(all, asciiFolded(all))
        assertEquals("", asciiFolded(""))
    }

    @Test
    fun aLetterOfLatinOneOrExtendedAIsWrittenWithTheAsciiLettersItIsMadeOf() {
        assertEquals("Hyle Deco", asciiFolded("Hylé Deco"))
        assertEquals("Julian", asciiFolded("Julián"))
        assertEquals("Odegard", asciiFolded("Ødegård"))
        assertEquals("Strasse", asciiFolded("Straße"))
        assertEquals("AEther oeuvre", asciiFolded("Æther œuvre"))
        assertEquals("Lodz Zolc", asciiFolded("Łódź Żółć"))
        assertEquals("Dvorak Cesky", asciiFolded("Dvořák Český"))
        assertEquals("Thorn d", asciiFolded("Þorn ð"))
    }

    @Test
    fun everyLetterInTheTableFoldsToAsciiLettersOnly() {
        val letters = (0xC0..0x17F).map { it.toChar() }.filter { it.isLetter() && it != '×' && it != '÷' }
        for (letter in letters) {
            val folded = asciiFolded(letter.toString())
            // A few letters of Latin Extended-A have no ASCII spelling (the eng, the long s); they are left out and never turn into a symbol.
            assertTrue(folded.all { it in 'A'..'Z' || it in 'a'..'z' }, "$letter folded to \"$folded\"")
        }
        assertEquals("ss", asciiFolded("ß"))
        assertEquals("Th", asciiFolded("Þ"))
        assertEquals("th", asciiFolded("þ"))
    }

    @Test
    fun whatHasNoAsciiSpellingIsLeftOutAndNeverReplacedByAQuestionMark() {
        assertEquals("", asciiFolded("あいう"))
        assertEquals("ab", asciiFolded("aあb"))
        assertEquals("Sans", asciiFolded("Sanś"), "a combining accent has nothing of its own to write")
        assertEquals("ab", asciiFolded("a\tb"), "a control character is not a file-name character")
        assertEquals("", asciiFolded("\u0001\u007F"), "control characters and DEL are left out")
        assertEquals("", asciiFolded("😀"), "a character outside the basic plane has no ASCII spelling")
    }

    @Test
    fun theFileNameOfAFamilyIsTheFoldedNameWithoutSpacesOrUnsafeCharacters() {
        assertEquals("HyleDeco", ShipFacts.pathSafe("Hylé Deco"))
        assertEquals("HyleDeco", ShipFacts.pathSafe("Hyle Deco"))
        assertEquals("AB", ShipFacts.pathSafe("A/B"))
        assertEquals("", ShipFacts.pathSafe("あ"))
    }
}
