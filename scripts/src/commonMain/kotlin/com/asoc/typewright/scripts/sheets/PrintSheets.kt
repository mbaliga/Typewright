// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.scripts.sheets

import com.asoc.typewright.scripts.GlyphSpec
import com.asoc.typewright.scripts.TemplateSheetEntry
import com.asoc.typewright.scripts.arabic.ArabicGlyphInventory
import com.asoc.typewright.scripts.arabic.ArabicTemplateSheet
import com.asoc.typewright.scripts.devanagari.DevanagariGlyphInventory
import com.asoc.typewright.scripts.devanagari.DevanagariTemplateSheet
import com.asoc.typewright.scripts.kana.HIRAGANA_GLYPH_INVENTORY
import com.asoc.typewright.scripts.kana.HIRAGANA_TEMPLATE_SHEET
import com.asoc.typewright.scripts.kana.KATAKANA_GLYPH_INVENTORY
import com.asoc.typewright.scripts.kana.KATAKANA_TEMPLATE_SHEET
import com.asoc.typewright.scripts.latin.LatinTemplateSheet

/**
 * The content of the printable template sheets: which glyph goes in which cell of which sheet, for each
 * [PrintScript]. Every glyph is a real template of the pack (`scripts/templates/hyle-all-templates.zip`), named with
 * the glyph name a UFO uses, so a drawing captured in cell *k* of sheet *n* is already the glyph it is.
 *
 * Latin is 120 glyphs on five sheets of 24, in the pack's order (capitals, small letters, digits, punctuation, then
 * the extra Latin letters) with one deliberate change: sheet 3 carries `w x y z`, the digits, then `& @ ' "` and
 * `. , : ; ! ?`, so it ends on `?` as the Capture board's "Sheet 3 · w–?" has it, and the `#` the pack lists among
 * those marks moves to the start of sheet 4. Kana is Hiragana's 55 then Katakana's 57; Devanagari is the pack's 68
 * templates with the two byte-identical duplicates (a second `a-deva` and `anusvara-deva`) printed once; Naskh is
 * the 49 isolated forms. A script's last sheet may hold fewer than 24.
 */
object PrintSheets {
    const val CELLS_PER_SHEET: Int = 24

    private val latinSheetThree =
        listOf(
            "w",
            "x",
            "y",
            "z",
            "zero",
            "one",
            "two",
            "three",
            "four",
            "five",
            "six",
            "seven",
            "eight",
            "nine",
            "ampersand",
            "at",
            "quotesingle",
            "quotedbl",
            "period",
            "comma",
            "colon",
            "semicolon",
            "exclam",
            "question",
        )

    /** The glyphs of [script] in print order. */
    fun glyphs(script: PrintScript): List<PrintGlyph> =
        when (script) {
            PrintScript.LATIN -> {
                latinInOrder()
            }

            PrintScript.KANA -> {
                join(HIRAGANA_TEMPLATE_SHEET.entries, HIRAGANA_GLYPH_INVENTORY.glyphs) +
                    join(KATAKANA_TEMPLATE_SHEET.entries, KATAKANA_GLYPH_INVENTORY.glyphs)
            }

            PrintScript.DEVANAGARI -> {
                join(DevanagariTemplateSheet.entries.distinctBy { it.glyphName }, DevanagariGlyphInventory.glyphs)
            }

            PrintScript.NASKH -> {
                join(ArabicTemplateSheet.entries, ArabicGlyphInventory.glyphs)
            }
        }

    /** Every sheet of [script], 24 glyphs to a sheet. */
    fun forScript(script: PrintScript): PrintSheetSet {
        val chunks = glyphs(script).chunked(CELLS_PER_SHEET)
        return PrintSheetSet(script, chunks.mapIndexed { index, glyphs -> PrintSheetSpec(script, index, chunks.size, glyphs) })
    }

    private fun latinInOrder(): List<PrintGlyph> {
        val inPackOrder = join(LatinTemplateSheet.entries, LatinTemplateSheet.inventory.glyphs)
        val byName = inPackOrder.associateBy { it.glyphName }
        val sheetThree = latinSheetThree.map { byName.getValue(it) }
        val first = inPackOrder.take(CELLS_PER_SHEET * 2)
        val placed = (first + sheetThree).map { it.glyphName }.toSet()
        return first + sheetThree + inPackOrder.filter { it.glyphName !in placed }
    }

    private fun join(
        entries: List<TemplateSheetEntry>,
        specs: List<GlyphSpec>,
    ): List<PrintGlyph> {
        val byName = specs.associateBy { it.name }
        return entries.map { entry ->
            val spec = byName[entry.glyphName]
            PrintGlyph(entry.glyphName, spec?.codepoint, spec?.unicodeName, entry.templateFolder, entry.templateFileName)
        }
    }
}
