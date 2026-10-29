// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.scripts.sheets

import com.asoc.typewright.scripts.devanagari.DevanagariTemplateSheet
import com.asoc.typewright.scripts.latin.LatinTemplateSheet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PrintSheetsTest {
    private val latin = PrintSheets.forScript(PrintScript.LATIN)

    @Test
    fun latinIsFiveSheetsOfTwentyFourWithEveryGlyphOfThePackOnce() {
        assertEquals(5, latin.sheets.size)
        assertTrue(latin.sheets.all { it.glyphs.size == 24 })
        assertEquals(120, latin.glyphCount)
        val names = latin.sheets.flatMap { s -> s.glyphs.map { it.glyphName } }
        assertEquals(120, names.toSet().size, "no glyph twice")
        assertEquals(LatinTemplateSheet.entries.map { it.glyphName }.toSet(), names.toSet(), "exactly the pack's glyphs")
        assertEquals(listOf(0, 1, 2, 3, 4), latin.sheets.map { it.index })
        assertTrue(latin.sheets.all { it.count == 5 })
    }

    @Test
    fun theSheetRangesAreTheOnesTheCaptureBoardNames() {
        assertEquals(listOf("A–X", "Y–v", "w–?"), latin.sheets.take(3).map { it.range })
        assertEquals("Latin sheet 3 of 5", latin.sheets[2].title)
    }

    @Test
    fun sheetThreeCarriesTheLastLettersTheDigitsAndTheMarksInTheBoardsOrder() {
        val sheet = latin.sheets[2].glyphs.map { it.glyphName }
        assertEquals(listOf("w", "x", "y", "z"), sheet.take(4))
        assertEquals(listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"), sheet.subList(4, 14))
        assertEquals(listOf("period", "comma", "colon", "semicolon", "exclam", "question"), sheet.takeLast(6))
        assertEquals(
            "numbersign",
            latin.sheets[3]
                .glyphs
                .first()
                .glyphName,
            "the # the pack lists among those marks starts sheet 4",
        )
    }

    @Test
    fun everyLatinGlyphIsNamedWithItsAdobeGlyphListName() {
        val byChar = latin.sheets.flatMap { it.glyphs }.associateBy { it.codePoint }
        val expected =
            mapOf(
                0x26 to "ampersand",
                0x27 to "quotesingle",
                0x2D to "hyphen",
                0x2013 to "endash",
                0x2014 to "emdash",
                0xB7 to "periodcentered",
                0x2026 to "ellipsis",
                0xA3 to "sterling",
                0x20AC to "Euro",
                0xC6 to "AE",
                0xD0 to "Eth",
                0xDE to "Thorn",
                0xDF to "germandbls",
                0x131 to "dotlessi",
                0x20B9 to "uni20B9",
                0x237 to "uni0237",
                0x41 to "A",
                0x7A to "z",
                0x30 to "zero",
            )
        for ((codePoint, name) in expected) assertEquals(name, byChar.getValue(codePoint).glyphName, "U+${codePoint.toString(16)}")
    }

    @Test
    fun kanaIsHiraganaThenKatakanaOnFiveSheets() {
        val kana = PrintSheets.forScript(PrintScript.KANA)
        assertEquals(112, kana.glyphCount)
        assertEquals(listOf(24, 24, 24, 24, 16), kana.sheets.map { it.glyphs.size })
        val names = kana.sheets.flatMap { s -> s.glyphs.map { it.glyphName } }
        assertEquals(112, names.toSet().size)
        assertTrue(names.take(55).all { it.endsWith("-hira") } && names.drop(55).all { it.endsWith("-kata") }, "55 Hiragana, then Katakana")
    }

    @Test
    fun devanagariPrintsTheTwoByteIdenticalDuplicatesOnce() {
        val deva = PrintSheets.forScript(PrintScript.DEVANAGARI)
        assertEquals(68, DevanagariTemplateSheet.entries.size)
        assertEquals(66, deva.glyphCount)
        assertEquals(listOf(24, 24, 18), deva.sheets.map { it.glyphs.size })
        assertEquals(
            66,
            deva.sheets
                .flatMap { s -> s.glyphs.map { it.glyphName } }
                .toSet()
                .size,
        )
        assertTrue(deva.sheets.flatMap { it.glyphs }.all { it.glyphName.endsWith("-deva") })
    }

    @Test
    fun naskhIsTheFortyNineIsolatedFormsWithTheirCodePoints() {
        val naskh = PrintSheets.forScript(PrintScript.NASKH)
        assertEquals(49, naskh.glyphCount)
        assertEquals(listOf(24, 24, 1), naskh.sheets.map { it.glyphs.size })
        val glyphs = naskh.sheets.flatMap { it.glyphs }
        assertTrue(glyphs.all { it.glyphName.endsWith("-ar") && it.codePoint != null }, "every Naskh glyph knows its character")
        assertTrue(glyphs.all { it.templateFolder == "svg/Naskh" })
    }

    @Test
    fun everyScriptsGlyphKnowsItsCharacterAndItsTemplateFile() {
        for (script in PrintScript.entries) {
            val glyphs = PrintSheets.glyphs(script)
            assertTrue(glyphs.isNotEmpty())
            assertTrue(glyphs.all { it.templateFileName.endsWith(".svg") && it.templateFolder.startsWith("svg/") }, script.label)
            assertTrue(glyphs.all { it.codePoint != null }, "${script.label}: every glyph has a code point")
            assertNotEquals(null, glyphs.first().character)
        }
    }

    @Test
    fun identityIdsAreUniqueAndLookUpBothWays() {
        assertEquals(
            PrintScript.entries.size,
            PrintScript.entries
                .map { it.id }
                .toSet()
                .size,
        )
        for (script in PrintScript.entries) assertEquals(script, PrintScript.fromId(script.id))
        assertEquals(null, PrintScript.fromId(7))
        for (paper in PaperSize.entries) assertEquals(paper, PaperSize.fromCode(paper.code))
        assertEquals(null, PaperSize.fromCode(1 + PaperSize.entries.size))
    }
}
