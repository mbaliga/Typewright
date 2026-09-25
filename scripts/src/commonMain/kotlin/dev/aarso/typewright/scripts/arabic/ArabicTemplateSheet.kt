// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.scripts.arabic

import dev.aarso.typewright.scripts.TemplateSheet
import dev.aarso.typewright.scripts.TemplateSheetEntry
import dev.aarso.typewright.scripts.WritingScript

/**
 * Arabic Naskh's real template sheet: one [TemplateSheetEntry] per file in
 * `scripts/templates/hyle-all-templates.zip`'s own `svg/Naskh/` folder -- 49 entries, matching
 * that folder's own real file count and its pack's own `HOW_TO_USE.md` ("Naskh | svg/Naskh | 49 |
 * isolated base letters; positional forms generated at build"). Isolated forms only, per this
 * task's own instruction (point 4): the zip carries exactly one drawn template per letter, so
 * every entry here names an [ArabicGlyphInventory.isolatedGlyphName] -- never a derived
 * `.init`/`.medi`/`.fina` glyph, none of which has (or should have) a real template file behind
 * it (see [ArabicGlyphInventory]'s own KDoc).
 */
object ArabicTemplateSheet {
    private const val FOLDER = "svg/Naskh"

    val entries: List<TemplateSheetEntry> =
        ArabicLetters.letters.map {
            TemplateSheetEntry(ArabicGlyphInventory.isolatedGlyphName(it.baseName), FOLDER, it.templateFileName)
        } +
            ArabicLetters.digits.map {
                TemplateSheetEntry(ArabicGlyphInventory.isolatedGlyphName(it.baseName), FOLDER, it.templateFileName)
            }

    fun build(): TemplateSheet = TemplateSheet(script = WritingScript.ARABIC_NASKH, entries = entries)
}
