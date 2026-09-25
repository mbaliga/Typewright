// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.scripts.kana

import com.asoc.typewright.scripts.WritingScript
import kotlin.test.Test
import kotlin.test.assertEquals

class KanaTemplateDataTest {
    private val sample =
        listOf(
            KanaTemplate(
                name = "a-hira",
                unicodeName = "HIRAGANA LETTER A",
                codepoint = 0x3042,
                templateFileName = "000_HIRAGANA_LETTER_A.svg",
            ),
            KanaTemplate(
                name = "i-hira",
                unicodeName = "HIRAGANA LETTER I",
                codepoint = 0x3044,
                templateFileName = "001_HIRAGANA_LETTER_I.svg",
            ),
        )

    @Test
    fun toGlyphInventoryCarriesTheGivenScriptAndMapsEachFieldInOrder() {
        val inventory = sample.toGlyphInventory(WritingScript.HIRAGANA)
        assertEquals(WritingScript.HIRAGANA, inventory.script)
        assertEquals(listOf("a-hira", "i-hira"), inventory.glyphs.map { it.name })
        assertEquals(listOf("HIRAGANA LETTER A", "HIRAGANA LETTER I"), inventory.glyphs.map { it.unicodeName })
        assertEquals(listOf(0x3042, 0x3044), inventory.glyphs.map { it.codepoint })
    }

    @Test
    fun toTemplateSheetEntriesCarriesTheGivenFolderAndMapsEachFieldInOrder() {
        val entries = sample.toTemplateSheetEntries("svg/Hiragana")
        assertEquals(listOf("a-hira", "i-hira"), entries.map { it.glyphName })
        assertEquals(listOf("svg/Hiragana", "svg/Hiragana"), entries.map { it.templateFolder })
        assertEquals(
            listOf("000_HIRAGANA_LETTER_A.svg", "001_HIRAGANA_LETTER_I.svg"),
            entries.map { it.templateFileName },
        )
    }

    @Test
    fun emptyInputProducesEmptyOutput() {
        assertEquals(0, emptyList<KanaTemplate>().toGlyphInventory(WritingScript.HIRAGANA).glyphs.size)
        assertEquals(0, emptyList<KanaTemplate>().toTemplateSheetEntries("svg/Hiragana").size)
    }
}
