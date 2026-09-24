package dev.aarso.typewright.scripts.devanagari

import dev.aarso.typewright.scripts.WritingScript
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DevanagariGlyphInventoryTest {
    @Test
    fun hasSixtySixDistinctGlyphsMatchingTheZipsRealFolderMinusItsTwoDuplicateFiles() {
        assertEquals(66, DevanagariGlyphInventory.build().glyphs.size)
    }

    @Test
    fun everyGlyphNameEndsInDeva() {
        for (glyph in DevanagariGlyphInventory.build().glyphs) {
            assertTrue(glyph.name.endsWith("-deva"), "${glyph.name} should end -deva")
        }
    }

    @Test
    fun everyGlyphNameIsUnique() {
        val names = DevanagariGlyphInventory.build().glyphs.map { it.name }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun everyGlyphHasARealUnicodeNameAndCodepoint() {
        for (glyph in DevanagariGlyphInventory.build().glyphs) {
            val unicodeName = assertNotNull(glyph.unicodeName, "${glyph.name} should have a Unicode name")
            assertTrue(unicodeName.startsWith("DEVANAGARI "), "${glyph.name}: $unicodeName")
            val codepoint = assertNotNull(glyph.codepoint, "${glyph.name} should have a codepoint")
            assertTrue(
                codepoint in 0x0900..0x097F,
                "${glyph.name}: codepoint $codepoint should be in the Devanagari Unicode block",
            )
        }
    }

    @Test
    fun vowelSignsAreNamedWithTheMatraSuffixEvidencedByResearch() {
        val byName = DevanagariGlyphInventory.build().glyphs.associateBy { it.name }
        assertEquals("DEVANAGARI VOWEL SIGN AA", byName.getValue("aaMatra-deva").unicodeName)
        assertEquals("DEVANAGARI VOWEL SIGN I", byName.getValue("iMatra-deva").unicodeName)
    }

    @Test
    fun independentVowelsAndConsonantsUsePlainDevaSuffixNotMatra() {
        val byName = DevanagariGlyphInventory.build().glyphs.associateBy { it.name }
        assertEquals("DEVANAGARI LETTER A", byName.getValue("a-deva").unicodeName)
        assertEquals("DEVANAGARI LETTER KA", byName.getValue("ka-deva").unicodeName)
        assertTrue("aMatra-deva" !in byName)
    }

    @Test
    fun aKnownCodepointIsCorrect() {
        val byName = DevanagariGlyphInventory.build().glyphs.associateBy { it.name }
        assertEquals(0x0915, byName.getValue("ka-deva").codepoint)
        assertEquals(0x093E, byName.getValue("aaMatra-deva").codepoint)
    }

    @Test
    fun coversAllTenDigits() {
        val byName = DevanagariGlyphInventory.build().glyphs.associateBy { it.name }
        val digitNames = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine")
        for (digit in digitNames) {
            assertTrue("$digit-deva" in byName, "$digit-deva should be in the inventory")
        }
    }

    @Test
    fun includesAnusvaraAndVisargaOnceEachDespiteAnusvarasDuplicateTemplateFile() {
        val names = DevanagariGlyphInventory.build().glyphs.map { it.name }
        assertEquals(1, names.count { it == "anusvara-deva" })
        assertEquals(1, names.count { it == "visarga-deva" })
    }

    @Test
    fun scriptIsDevanagari() {
        assertEquals(WritingScript.DEVANAGARI, DevanagariGlyphInventory.build().script)
    }

    @Test
    fun buildIsPureAndDeterministic() {
        assertEquals(DevanagariGlyphInventory.build(), DevanagariGlyphInventory.build())
    }
}
