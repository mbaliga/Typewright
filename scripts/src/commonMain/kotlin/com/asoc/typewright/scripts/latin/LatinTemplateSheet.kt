// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.scripts.latin

import com.asoc.typewright.scripts.GlyphInventory
import com.asoc.typewright.scripts.GlyphSpec
import com.asoc.typewright.scripts.TemplateSheet
import com.asoc.typewright.scripts.TemplateSheetEntry
import com.asoc.typewright.scripts.WritingScript

/**
 * One Latin template of the pack, read from `svg/Latin`: [name] is the glyph's Adobe Glyph List name (or `uniXXXX`
 * where the list has none), the name a UFO and a compiler expect. The list [LATIN_TEMPLATES] is generated from the
 * pack by `scripts/templates/generate_latin_manifest.py`; it is not edited by hand.
 */
internal data class LatinTemplate(
    val name: String,
    val unicodeName: String,
    val codepoint: Int,
    val templateFileName: String,
)

/** Latin's folder in the template pack. */
const val LATIN_TEMPLATE_FOLDER: String = "svg/Latin"

/**
 * Latin's real inventory and template sheet: the 120 glyphs the pack has a template for, in the pack's own order
 * (capitals, small letters, digits, punctuation and symbols, then the extra Latin letters). It is the inventory the
 * Capture sheets and the Workbook both mean by "the 120 Latin cells".
 */
object LatinTemplateSheet {
    val inventory: GlyphInventory =
        GlyphInventory(
            script = WritingScript.LATIN,
            glyphs = LATIN_TEMPLATES.map { GlyphSpec(it.name, it.unicodeName, it.codepoint) },
        )

    val entries: List<TemplateSheetEntry> =
        LATIN_TEMPLATES.map { TemplateSheetEntry(it.name, LATIN_TEMPLATE_FOLDER, it.templateFileName) }

    fun build(): TemplateSheet = TemplateSheet(script = WritingScript.LATIN, entries = entries)
}
