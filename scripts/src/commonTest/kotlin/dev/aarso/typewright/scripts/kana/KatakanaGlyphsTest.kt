package dev.aarso.typewright.scripts.kana

import dev.aarso.typewright.scripts.WritingScript
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KatakanaGlyphsTest {
    @Test
    fun hasExactlyFiftySevenGlyphsMatchingTheRealTemplateZipsSvgKatakanaFolder() {
        // scripts/templates/generate_kana_manifest.py confirms 57 real templates in
        // svg/Katakana, matching the zip's own HOW_TO_USE.md ("Katakana svg/Katakana 57").
        assertEquals(57, KATAKANA_TEMPLATES.size)
        assertEquals(57, KATAKANA_GLYPH_INVENTORY.glyphs.size)
        assertEquals(57, KATAKANA_TEMPLATE_SHEET.entries.size)
    }

    @Test
    fun hasExactlyTwoMoreGlyphsThanHiraganaAndTheyAreTheMiddleDotAndTheProlongedSoundMark() {
        assertEquals(2, KATAKANA_TEMPLATES.size - HIRAGANA_TEMPLATES.size)
        val names = KATAKANA_GLYPH_INVENTORY.glyphs.map { it.name }
        assertTrue("middle-dot-kata" in names)
        assertTrue("prolonged-sound-mark-kata" in names)
    }

    @Test
    fun theInventoryCarriesTheKatakanaScript() {
        assertEquals(WritingScript.KATAKANA, KATAKANA_GLYPH_INVENTORY.script)
        assertEquals(WritingScript.KATAKANA, KATAKANA_TEMPLATE_SHEET.script)
    }

    @Test
    fun everyGlyphNameEndsWithTheKatakanaSuffix() {
        for (glyph in KATAKANA_GLYPH_INVENTORY.glyphs) {
            assertTrue(glyph.name.endsWith("-kata"), "${glyph.name} should end -kata")
        }
    }

    @Test
    fun everyGlyphNameIsMechanicallyDerivedFromItsOwnUnicodeName() {
        for (glyph in KATAKANA_GLYPH_INVENTORY.glyphs) {
            val unicodeName = requireNotNull(glyph.unicodeName) { "${glyph.name} should carry a unicodeName" }
            assertEquals(deriveKanaGlyphName(unicodeName, KanaScriptKind.KATAKANA), glyph.name)
        }
    }

    @Test
    fun everyGlyphHasARealCodepointAndNoTwoGlyphsShareOne() {
        val codepoints = KATAKANA_GLYPH_INVENTORY.glyphs.map { requireNotNull(it.codepoint) }
        assertEquals(codepoints.size, codepoints.toSet().size, "no two Katakana glyphs should share a codepoint")
    }

    @Test
    fun noTwoGlyphNamesCollide() {
        val names = KATAKANA_GLYPH_INVENTORY.glyphs.map { it.name }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun spotChecksWellKnownRealKatakanaCodepoints() {
        val byName = KATAKANA_GLYPH_INVENTORY.glyphs.associateBy { it.name }
        assertEquals(0x30A2, byName.getValue("a-kata").codepoint) // ア
        assertEquals(0x30F3, byName.getValue("n-kata").codepoint) // ン
        assertEquals(0x30FB, byName.getValue("middle-dot-kata").codepoint) // ・
        assertEquals(0x30FC, byName.getValue("prolonged-sound-mark-kata").codepoint) // ー
    }

    @Test
    fun theProlongedSoundMarksRealUnicodeNameKeepsTheInternalHyphen() {
        val glyph = KATAKANA_GLYPH_INVENTORY.glyphs.first { it.name == "prolonged-sound-mark-kata" }
        assertEquals("KATAKANA-HIRAGANA PROLONGED SOUND MARK", glyph.unicodeName)
    }

    @Test
    fun theTemplateSheetsGlyphNamesMatchTheInventoryExactlyAndInTheSameOrder() {
        assertEquals(
            KATAKANA_GLYPH_INVENTORY.glyphs.map { it.name },
            KATAKANA_TEMPLATE_SHEET.entries.map { it.glyphName },
        )
    }

    @Test
    fun everyTemplateSheetEntryPointsAtTheRealKatakanaFolder() {
        for (entry in KATAKANA_TEMPLATE_SHEET.entries) {
            assertEquals(KATAKANA_TEMPLATE_FOLDER, entry.templateFolder)
            assertEquals("svg/Katakana", entry.templateFolder)
        }
    }

    @Test
    fun everyTemplateFileNameIsARealSvgFileFollowingTheZipsOwnNamingPattern() {
        // The real zip names each file "<3-digit index>_<UNICODE_NAME_WITH_UNDERSCORES>.svg", and
        // Katakana's own two non-letter templates keep the same pattern with a different middle
        // segment (e.g. "056_KATAKANA_MIDDLE_DOT.svg") -- confirmed by generate_kana_manifest.py.
        val pattern = Regex("""^\d{3}_KATAKANA(_LETTER|_HIRAGANA)?_[A-Z_]+\.svg$""")
        for (entry in KATAKANA_TEMPLATE_SHEET.entries) {
            assertTrue(entry.templateFileName.endsWith(".svg"))
            assertTrue(
                pattern.matches(entry.templateFileName),
                "${entry.templateFileName} should match the real zip's own <index>_KATAKANA... naming pattern",
            )
        }
    }

    @Test
    fun templateFileNamesAreAllDistinct() {
        val fileNames = KATAKANA_TEMPLATE_SHEET.entries.map { it.templateFileName }
        assertEquals(fileNames.size, fileNames.toSet().size)
    }
}
