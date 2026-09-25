// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.scripts.arabic

import dev.aarso.typewright.scripts.WritingScript
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArabicTemplateSheetTest {
    @Test
    fun hasFortyNineEntriesMatchingTheZipsRealFileCount() {
        assertEquals(49, ArabicTemplateSheet.build().entries.size)
    }

    @Test
    fun everyEntryPointsAtTheNaskhFolder() {
        for (entry in ArabicTemplateSheet.build().entries) {
            assertEquals("svg/Naskh", entry.templateFolder)
        }
    }

    @Test
    fun everyEntrysFilenameIsUniqueAndStartsWithItsThreeDigitZipIndex() {
        val entries = ArabicTemplateSheet.build().entries
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
    fun everyEntrysGlyphNameIsAnIsolatedFormInTheGlyphInventory() {
        val isolatedNames = ArabicGlyphInventory.isolatedGlyphs.map { it.name }.toSet()
        for (entry in ArabicTemplateSheet.build().entries) {
            assertTrue(entry.glyphName in isolatedNames, "${entry.glyphName} should be a real isolated inventory glyph")
        }
    }

    @Test
    fun noEntryNamesADerivedPositionalGlyph() {
        for (entry in ArabicTemplateSheet.build().entries) {
            assertTrue(
                ".init" !in entry.glyphName && ".medi" !in entry.glyphName && ".fina" !in entry.glyphName,
                "${entry.glyphName} is a template entry and should never name a derived positional form",
            )
        }
    }

    @Test
    fun everyEntrysGlyphNameIsUnique() {
        val names = ArabicTemplateSheet.build().entries.map { it.glyphName }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun scriptIsArabicNaskh() {
        assertEquals(WritingScript.ARABIC_NASKH, ArabicTemplateSheet.build().script)
    }

    @Test
    fun buildIsPureAndDeterministic() {
        assertEquals(ArabicTemplateSheet.build(), ArabicTemplateSheet.build())
    }
}
