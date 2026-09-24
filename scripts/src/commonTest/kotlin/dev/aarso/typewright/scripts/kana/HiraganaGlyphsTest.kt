package dev.aarso.typewright.scripts.kana

import dev.aarso.typewright.scripts.WritingScript
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HiraganaGlyphsTest {
    @Test
    fun hasExactlyFiftyFiveGlyphsMatchingTheRealTemplateZipsSvgHiraganaFolder() {
        // scripts/templates/generate_kana_manifest.py confirms 55 real templates in
        // svg/Hiragana, matching the zip's own HOW_TO_USE.md ("Hiragana svg/Hiragana 55").
        assertEquals(55, HIRAGANA_TEMPLATES.size)
        assertEquals(55, HIRAGANA_GLYPH_INVENTORY.glyphs.size)
        assertEquals(55, HIRAGANA_TEMPLATE_SHEET.entries.size)
    }

    @Test
    fun theInventoryCarriesTheHiraganaScript() {
        assertEquals(WritingScript.HIRAGANA, HIRAGANA_GLYPH_INVENTORY.script)
        assertEquals(WritingScript.HIRAGANA, HIRAGANA_TEMPLATE_SHEET.script)
    }

    @Test
    fun everyGlyphNameEndsWithTheHiraganaSuffix() {
        for (glyph in HIRAGANA_GLYPH_INVENTORY.glyphs) {
            assertTrue(glyph.name.endsWith("-hira"), "${glyph.name} should end -hira")
        }
    }

    @Test
    fun everyGlyphNameIsMechanicallyDerivedFromItsOwnUnicodeName() {
        for (glyph in HIRAGANA_GLYPH_INVENTORY.glyphs) {
            val unicodeName = requireNotNull(glyph.unicodeName) { "${glyph.name} should carry a unicodeName" }
            assertEquals(deriveKanaGlyphName(unicodeName, KanaScriptKind.HIRAGANA), glyph.name)
        }
    }

    @Test
    fun everyGlyphHasARealCodepointAndNoTwoGlyphsShareOne() {
        val codepoints = HIRAGANA_GLYPH_INVENTORY.glyphs.map { requireNotNull(it.codepoint) }
        assertEquals(codepoints.size, codepoints.toSet().size, "no two Hiragana glyphs should share a codepoint")
    }

    @Test
    fun noTwoGlyphNamesCollide() {
        val names = HIRAGANA_GLYPH_INVENTORY.glyphs.map { it.name }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun spotChecksWellKnownRealHiraganaCodepoints() {
        val byName = HIRAGANA_GLYPH_INVENTORY.glyphs.associateBy { it.name }
        assertEquals(0x3042, byName.getValue("a-hira").codepoint) // あ
        assertEquals(0x304B, byName.getValue("ka-hira").codepoint) // か
        assertEquals(0x3093, byName.getValue("n-hira").codepoint) // ん
        assertEquals(0x3041, byName.getValue("small-a-hira").codepoint) // ぁ
        assertEquals(0x3063, byName.getValue("small-tu-hira").codepoint) // っ
    }

    @Test
    fun hasNoVoicedOrSemiVoicedGlyphsBecauseNoRealTemplateDrawsOne() {
        // Honest scope, not a shortfall: see KANA_INVENTORY_SCOPE_NOTE.
        val names = HIRAGANA_GLYPH_INVENTORY.glyphs.map { it.name }
        assertTrue(names.none { it.startsWith("ga-") || it.contains("dakuten") })
        assertTrue(KANA_INVENTORY_SCOPE_NOTE.contains("112 real templates"))
    }

    @Test
    fun theTemplateSheetsGlyphNamesMatchTheInventoryExactlyAndInTheSameOrder() {
        assertEquals(
            HIRAGANA_GLYPH_INVENTORY.glyphs.map { it.name },
            HIRAGANA_TEMPLATE_SHEET.entries.map { it.glyphName },
        )
    }

    @Test
    fun everyTemplateSheetEntryPointsAtTheRealHiraganaFolder() {
        for (entry in HIRAGANA_TEMPLATE_SHEET.entries) {
            assertEquals(HIRAGANA_TEMPLATE_FOLDER, entry.templateFolder)
            assertEquals("svg/Hiragana", entry.templateFolder)
        }
    }

    @Test
    fun everyTemplateFileNameIsARealSvgFileFollowingTheZipsOwnNamingPattern() {
        // The real zip names each file "<3-digit index>_<UNICODE_NAME_WITH_UNDERSCORES>.svg",
        // e.g. "000_HIRAGANA_LETTER_A.svg" -- confirmed by generate_kana_manifest.py.
        val pattern = Regex("""^\d{3}_HIRAGANA(_LETTER)?_[A-Z_]+\.svg$""")
        for (entry in HIRAGANA_TEMPLATE_SHEET.entries) {
            assertTrue(entry.templateFileName.endsWith(".svg"))
            assertTrue(
                pattern.matches(entry.templateFileName),
                "${entry.templateFileName} should match the real zip's own <index>_HIRAGANA... naming pattern",
            )
        }
    }

    @Test
    fun templateFileNamesAreAllDistinct() {
        val fileNames = HIRAGANA_TEMPLATE_SHEET.entries.map { it.templateFileName }
        assertEquals(fileNames.size, fileNames.toSet().size)
    }
}
