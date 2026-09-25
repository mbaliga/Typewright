// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.scripts.kana

import dev.aarso.typewright.scripts.ControlCharacterSet
import dev.aarso.typewright.scripts.WritingScript

/**
 * Hiragana's and Katakana's control-character set -- this script's own equivalent of Latin's
 * n/o/H/O (`dev.aarso.typewright.scripts.ControlCharacterSet`'s own KDoc).
 *
 * **Both are deliberately empty**, and this is an honest finding, not an oversight or a
 * placeholder waiting to be filled in later. Two sources were read in full for this task, as
 * instructed:
 * - `docs/RESEARCH_font_quality.md`'s "Kana are centred in a virtual body, not seated on a
 *   baseline" subsection ends its own "Template implications" sentence with "No 'draw these kana
 *   first' list was found" -- stated as plainly as this file states it now.
 * - `docs/LESSONS_SCAFFOLD.md` section 5's kana bullet ("the virtual body, the letter face,
 *   centred not seated; the dakuten corner; ~200 glyphs, not 92") names none either.
 *
 * By contrast, the same research is explicit and repeated about Latin's own control set (n and o,
 * then H and O -- "Every source agrees on the sequence and the control set"), and gives Devanagari
 * a real, sourced first-glyph progression from Design With FontForge (पाव then किमीनुफू then
 * भरसगदह). No comparable source exists for kana. Per this task's own instruction --
 * "if neither source gives a real starting sequence for kana, say so honestly in the data rather
 * than inventing one to match Latin's shape" -- and per CLAUDE.md law 5 ("measured, not
 * invented"), [HIRAGANA_CONTROL_CHARACTERS] and [KATAKANA_CONTROL_CHARACTERS] both carry an empty
 * [ControlCharacterSet.controlCharacters] rather than a guessed sequence (an obvious temptation
 * would have been "draw あ/ア and か/カ first, the kana equivalent of Latin's round/straight
 * pair" -- plausible-sounding, and invented, so not done here). [KanaControlCharactersTest]
 * pins both lists at empty specifically so a future edit that quietly adds an invented sequence
 * fails a test rather than shipping unnoticed.
 */
val HIRAGANA_CONTROL_CHARACTERS: ControlCharacterSet =
    ControlCharacterSet(
        script = WritingScript.HIRAGANA,
        controlCharacters = emptyList(),
    )

/** See [HIRAGANA_CONTROL_CHARACTERS] -- the identical honest-absence finding applies to Katakana. */
val KATAKANA_CONTROL_CHARACTERS: ControlCharacterSet =
    ControlCharacterSet(
        script = WritingScript.KATAKANA,
        controlCharacters = emptyList(),
    )
