// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.scripts.arabic

import dev.aarso.typewright.scripts.ControlCharacter
import dev.aarso.typewright.scripts.ControlCharacterSet
import dev.aarso.typewright.scripts.WritingScript

/**
 * Arabic Naskh's real starting control sequence -- this script's own equivalent of Latin's n/o/H/O
 * ([ControlCharacterSet]'s own KDoc). Both real, sourced references name exactly one concrete
 * pedagogy point and neither gives more: `docs/RESEARCH_font_quality.md`'s Arabic section, quoted
 * in full: "The Glyphs recipe is concrete: start from `behDotless-ar` in all four forms (isolated,
 * `.init`, `.medi`, `.fina`) because ب ت ث پ share a skeleton and differ only by dots";
 * `docs/LESSONS_SCAFFOLD.md` section 5 restates the same single point in fewer words: "four forms
 * from one skeleton (behDotless)". Neither source gives a control-character *progression* the way
 * Design With FontForge's own पाव -> किमीनुफू -> भरसगदह -> ... gave Devanagari
 * ([dev.aarso.typewright.scripts.devanagari.DevanagariControlCharacters]) -- so this set has
 * exactly the four glyphs that one real quoted sentence names, not a larger invented sequence.
 * This is this task's own explicit instruction, applied plainly: "if neither source gives more
 * than that, say so rather than inventing additional structure."
 *
 * `behDotless-ar` is **not** a member of [ArabicGlyphInventory]: the real 49 templates in
 * `scripts/templates/hyle-all-templates.zip`'s own `svg/Naskh/` folder are already-dotted letters
 * (beh, teh, theh, peh -- `beh-ar`, `teh-ar`, `theh-ar`, `peh-ar` in this build's own inventory),
 * not a separate dotless-skeleton template the way the Glyphs recipe's own workflow draws it; a
 * control character naming a glyph outside the base inventory is not new to this build --
 * `dev.aarso.typewright.scripts.devanagari.DevanagariControlCharacters` names `reph-deva` and
 * `dda_dda-deva` for the identical reason, and states so in its own KDoc.
 */
object ArabicControlCharacters {
    private const val RATIONALE_PREFIX =
        "The one real starting sequence docs/RESEARCH_font_quality.md and " +
            "docs/LESSONS_SCAFFOLD.md give for Arabic: behDotless-ar (ب ت ث پ's shared, undotted " +
            "skeleton) in all four forms, because those four real inventory letters " +
            "(beh-ar, teh-ar, theh-ar, peh-ar) differ only by their dots. Not an " +
            "ArabicGlyphInventory member -- see this file's own class KDoc."

    val controlCharacters: List<ControlCharacter> =
        listOf(
            ControlCharacter("behDotless-ar", "$RATIONALE_PREFIX This entry: the isolated form."),
            ControlCharacter("behDotless-ar.init", "$RATIONALE_PREFIX This entry: the initial form."),
            ControlCharacter("behDotless-ar.medi", "$RATIONALE_PREFIX This entry: the medial form."),
            ControlCharacter("behDotless-ar.fina", "$RATIONALE_PREFIX This entry: the final form."),
        )

    fun build(): ControlCharacterSet = ControlCharacterSet(script = WritingScript.ARABIC_NASKH, controlCharacters = controlCharacters)
}
