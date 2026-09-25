// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.scripts.devanagari

import dev.aarso.typewright.scripts.WritingScript
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DevanagariTemplateSheetTest {
    @Test
    fun hasSixtyEightEntriesMatchingTheZipsRealFileCount() {
        assertEquals(68, DevanagariTemplateSheet.build().entries.size)
    }

    @Test
    fun everyEntryPointsAtTheDevanagariFolder() {
        for (entry in DevanagariTemplateSheet.build().entries) {
            assertEquals("svg/Devanagari", entry.templateFolder)
        }
    }

    @Test
    fun everyEntrysFilenameIsUniqueAndStartsWithItsThreeDigitZipIndex() {
        val entries = DevanagariTemplateSheet.build().entries
        assertEquals(entries.size, entries.map { it.templateFileName }.toSet().size)
        for ((i, entry) in entries.withIndex()) {
            val expectedIndex = i.toString().padStart(3, '0')
            assertTrue(
                entry.templateFileName.startsWith("${expectedIndex}_"),
                "entry $i (${entry.templateFileName}) should start with $expectedIndex\\_",
            )
        }
    }

    @Test
    fun everyEntrysGlyphNameIsInTheGlyphInventory() {
        val inventoryNames =
            DevanagariGlyphInventory
                .build()
                .glyphs
                .map { it.name }
                .toSet()
        for (entry in DevanagariTemplateSheet.build().entries) {
            assertTrue(entry.glyphName in inventoryNames, "${entry.glyphName} should be a real inventory glyph")
        }
    }

    @Test
    fun theTwoRealDuplicateFilesBothMapToTheGlyphTheyActuallyDraw() {
        val byFilename = DevanagariTemplateSheet.build().entries.associateBy { it.templateFileName }
        assertEquals("a-deva", byFilename.getValue("000_DEVANAGARI_LETTER_A.svg").glyphName)
        assertEquals("a-deva", byFilename.getValue("011_DEVANAGARI_LETTER_A.svg").glyphName)
        assertEquals("anusvara-deva", byFilename.getValue("012_DEVANAGARI_SIGN_ANUSVARA.svg").glyphName)
        assertEquals("anusvara-deva", byFilename.getValue("056_DEVANAGARI_SIGN_ANUSVARA.svg").glyphName)
    }

    @Test
    fun aDevaAndAnusvaraDevaEachAppearTwiceAcrossTheSheetsEntries() {
        val names = DevanagariTemplateSheet.build().entries.map { it.glyphName }
        assertEquals(2, names.count { it == "a-deva" })
        assertEquals(2, names.count { it == "anusvara-deva" })
    }

    @Test
    fun everyOtherGlyphAppearsExactlyOnce() {
        val names = DevanagariTemplateSheet.build().entries.map { it.glyphName }
        val counts = names.groupingBy { it }.eachCount()
        for ((name, count) in counts) {
            if (name == "a-deva" || name == "anusvara-deva") continue
            assertEquals(1, count, "$name should appear exactly once")
        }
    }

    @Test
    fun scriptIsDevanagari() {
        assertEquals(WritingScript.DEVANAGARI, DevanagariTemplateSheet.build().script)
    }

    @Test
    fun buildIsPureAndDeterministic() {
        assertEquals(DevanagariTemplateSheet.build(), DevanagariTemplateSheet.build())
    }
}
