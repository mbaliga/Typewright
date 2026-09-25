// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.scripts.arabic

/**
 * One real Arabic base letter from `scripts/templates/hyle-all-templates.zip`'s own
 * `svg/Naskh/` folder (indices `000`-`038`). [baseName] is the glyph's own base name *without*
 * either the isolated form's `-ar` suffix or a positional suffix -- e.g. `"alef"`, not
 * `"alef-ar"` -- so [ArabicGlyphInventory] and [ArabicTemplateSheet] can each build their own real
 * glyph name mechanically ([ArabicGlyphInventory.isolatedGlyphName],
 * [ArabicGlyphInventory.positionalGlyphNames]) rather than have it typed twice. [unicodeName] and
 * [codepoint] are this letter's real Unicode character name and scalar value, both cross-checked
 * against Python's own `unicodedata` module by `scripts/templates/generate_naskh_manifest.py`
 * (see the checked-in `scripts/templates/naskh-manifest.json` it produced), not typed from memory
 * alone. [joiningType] is this letter's real Unicode `Joining_Type` (see [ArabicJoiningType]'s own
 * KDoc for how it was sourced) and is what [ArabicGlyphInventory] uses to decide whether this
 * letter gets three extra derived positional glyphs beyond its one real isolated template.
 * [templateFileName] is the exact filename inside `svg/Naskh/`.
 */
internal data class NaskhLetter(
    val baseName: String,
    val unicodeName: String,
    val codepoint: Int,
    val joiningType: ArabicJoiningType,
    val templateFileName: String,
)

/**
 * One real Arabic-Indic digit from `scripts/templates/hyle-all-templates.zip`'s own
 * `svg/Naskh/` folder (indices `039`-`048`). Digits are modelled separately from [NaskhLetter]
 * rather than given a [ArabicJoiningType] of their own: a digit is not assigned a `Joining_Type`
 * by Unicode at all (it is simply outside the cursive-joining system), which is a different real
 * fact from hamza's own explicit `Joining_Type: U` ([ArabicJoiningType.NON_JOINING]) even though
 * both end up isolated-form-only in [ArabicGlyphInventory].
 */
internal data class NaskhDigit(
    val baseName: String,
    val unicodeName: String,
    val codepoint: Int,
    val templateFileName: String,
)

/**
 * The real, complete contents of `scripts/templates/hyle-all-templates.zip`'s own `svg/Naskh/`
 * folder: 49 files total (matching that pack's own `HOW_TO_USE.md`, "Naskh | svg/Naskh | 49 |
 * isolated base letters; positional forms generated at build", and confirmed directly against the
 * real zip listing, not assumed) -- [letters] (39, indices `000`-`038`) plus [digits] (10, indices
 * `039`-`048`). Every field on every entry was produced by
 * `scripts/templates/generate_naskh_manifest.py` reading the real zip and cross-checked against
 * Python's own `unicodedata` module and the real Unicode `ArabicShaping.txt`; this Kotlin table is
 * a direct, hand-transcribed copy of that generator's own checked-in output
 * (`scripts/templates/naskh-manifest.json`), not an independent invention -- both were compared
 * entry by entry before this file was written.
 */
internal object ArabicLetters {
    val letters: List<NaskhLetter> =
        listOf(
            NaskhLetter("alef", "ARABIC LETTER ALEF", 0x0627, ArabicJoiningType.RIGHT_JOINING, "000_ARABIC_LETTER_ALEF.svg"),
            NaskhLetter("beh", "ARABIC LETTER BEH", 0x0628, ArabicJoiningType.DUAL_JOINING, "001_ARABIC_LETTER_BEH.svg"),
            NaskhLetter("peh", "ARABIC LETTER PEH", 0x067E, ArabicJoiningType.DUAL_JOINING, "002_ARABIC_LETTER_PEH.svg"),
            NaskhLetter("teh", "ARABIC LETTER TEH", 0x062A, ArabicJoiningType.DUAL_JOINING, "003_ARABIC_LETTER_TEH.svg"),
            NaskhLetter("tteh", "ARABIC LETTER TTEH", 0x0679, ArabicJoiningType.DUAL_JOINING, "004_ARABIC_LETTER_TTEH.svg"),
            NaskhLetter("theh", "ARABIC LETTER THEH", 0x062B, ArabicJoiningType.DUAL_JOINING, "005_ARABIC_LETTER_THEH.svg"),
            NaskhLetter("jeem", "ARABIC LETTER JEEM", 0x062C, ArabicJoiningType.DUAL_JOINING, "006_ARABIC_LETTER_JEEM.svg"),
            NaskhLetter("tcheh", "ARABIC LETTER TCHEH", 0x0686, ArabicJoiningType.DUAL_JOINING, "007_ARABIC_LETTER_TCHEH.svg"),
            NaskhLetter("hah", "ARABIC LETTER HAH", 0x062D, ArabicJoiningType.DUAL_JOINING, "008_ARABIC_LETTER_HAH.svg"),
            NaskhLetter("khah", "ARABIC LETTER KHAH", 0x062E, ArabicJoiningType.DUAL_JOINING, "009_ARABIC_LETTER_KHAH.svg"),
            NaskhLetter("dal", "ARABIC LETTER DAL", 0x062F, ArabicJoiningType.RIGHT_JOINING, "010_ARABIC_LETTER_DAL.svg"),
            NaskhLetter("ddal", "ARABIC LETTER DDAL", 0x0688, ArabicJoiningType.RIGHT_JOINING, "011_ARABIC_LETTER_DDAL.svg"),
            NaskhLetter("thal", "ARABIC LETTER THAL", 0x0630, ArabicJoiningType.RIGHT_JOINING, "012_ARABIC_LETTER_THAL.svg"),
            NaskhLetter("reh", "ARABIC LETTER REH", 0x0631, ArabicJoiningType.RIGHT_JOINING, "013_ARABIC_LETTER_REH.svg"),
            NaskhLetter("rreh", "ARABIC LETTER RREH", 0x0691, ArabicJoiningType.RIGHT_JOINING, "014_ARABIC_LETTER_RREH.svg"),
            NaskhLetter("zain", "ARABIC LETTER ZAIN", 0x0632, ArabicJoiningType.RIGHT_JOINING, "015_ARABIC_LETTER_ZAIN.svg"),
            NaskhLetter("jeh", "ARABIC LETTER JEH", 0x0698, ArabicJoiningType.RIGHT_JOINING, "016_ARABIC_LETTER_JEH.svg"),
            NaskhLetter("seen", "ARABIC LETTER SEEN", 0x0633, ArabicJoiningType.DUAL_JOINING, "017_ARABIC_LETTER_SEEN.svg"),
            NaskhLetter("sheen", "ARABIC LETTER SHEEN", 0x0634, ArabicJoiningType.DUAL_JOINING, "018_ARABIC_LETTER_SHEEN.svg"),
            NaskhLetter("sad", "ARABIC LETTER SAD", 0x0635, ArabicJoiningType.DUAL_JOINING, "019_ARABIC_LETTER_SAD.svg"),
            NaskhLetter("dad", "ARABIC LETTER DAD", 0x0636, ArabicJoiningType.DUAL_JOINING, "020_ARABIC_LETTER_DAD.svg"),
            NaskhLetter("tah", "ARABIC LETTER TAH", 0x0637, ArabicJoiningType.DUAL_JOINING, "021_ARABIC_LETTER_TAH.svg"),
            NaskhLetter("zah", "ARABIC LETTER ZAH", 0x0638, ArabicJoiningType.DUAL_JOINING, "022_ARABIC_LETTER_ZAH.svg"),
            NaskhLetter("ain", "ARABIC LETTER AIN", 0x0639, ArabicJoiningType.DUAL_JOINING, "023_ARABIC_LETTER_AIN.svg"),
            NaskhLetter("ghain", "ARABIC LETTER GHAIN", 0x063A, ArabicJoiningType.DUAL_JOINING, "024_ARABIC_LETTER_GHAIN.svg"),
            NaskhLetter("feh", "ARABIC LETTER FEH", 0x0641, ArabicJoiningType.DUAL_JOINING, "025_ARABIC_LETTER_FEH.svg"),
            NaskhLetter("qaf", "ARABIC LETTER QAF", 0x0642, ArabicJoiningType.DUAL_JOINING, "026_ARABIC_LETTER_QAF.svg"),
            NaskhLetter("keheh", "ARABIC LETTER KEHEH", 0x06A9, ArabicJoiningType.DUAL_JOINING, "027_ARABIC_LETTER_KEHEH.svg"),
            NaskhLetter("gaf", "ARABIC LETTER GAF", 0x06AF, ArabicJoiningType.DUAL_JOINING, "028_ARABIC_LETTER_GAF.svg"),
            NaskhLetter("lam", "ARABIC LETTER LAM", 0x0644, ArabicJoiningType.DUAL_JOINING, "029_ARABIC_LETTER_LAM.svg"),
            NaskhLetter("meem", "ARABIC LETTER MEEM", 0x0645, ArabicJoiningType.DUAL_JOINING, "030_ARABIC_LETTER_MEEM.svg"),
            NaskhLetter("noon", "ARABIC LETTER NOON", 0x0646, ArabicJoiningType.DUAL_JOINING, "031_ARABIC_LETTER_NOON.svg"),
            NaskhLetter(
                "noonGhunna",
                "ARABIC LETTER NOON GHUNNA",
                0x06BA,
                ArabicJoiningType.DUAL_JOINING,
                "032_ARABIC_LETTER_NOON_GHUNNA.svg",
            ),
            NaskhLetter("waw", "ARABIC LETTER WAW", 0x0648, ArabicJoiningType.RIGHT_JOINING, "033_ARABIC_LETTER_WAW.svg"),
            NaskhLetter(
                "hehGoal",
                "ARABIC LETTER HEH GOAL",
                0x06C1,
                ArabicJoiningType.DUAL_JOINING,
                "034_ARABIC_LETTER_HEH_GOAL.svg",
            ),
            NaskhLetter(
                "hehDoachashmee",
                "ARABIC LETTER HEH DOACHASHMEE",
                0x06BE,
                ArabicJoiningType.DUAL_JOINING,
                "035_ARABIC_LETTER_HEH_DOACHASHMEE.svg",
            ),
            NaskhLetter("hamza", "ARABIC LETTER HAMZA", 0x0621, ArabicJoiningType.NON_JOINING, "036_ARABIC_LETTER_HAMZA.svg"),
            NaskhLetter(
                "farsiYeh",
                "ARABIC LETTER FARSI YEH",
                0x06CC,
                ArabicJoiningType.DUAL_JOINING,
                "037_ARABIC_LETTER_FARSI_YEH.svg",
            ),
            NaskhLetter(
                "yehBarree",
                "ARABIC LETTER YEH BARREE",
                0x06D2,
                ArabicJoiningType.RIGHT_JOINING,
                "038_ARABIC_LETTER_YEH_BARREE.svg",
            ),
        )

    val digits: List<NaskhDigit> =
        listOf(
            NaskhDigit("zero", "ARABIC-INDIC DIGIT ZERO", 0x0660, "039_ARABIC_INDIC_DIGIT_ZERO.svg"),
            NaskhDigit("one", "ARABIC-INDIC DIGIT ONE", 0x0661, "040_ARABIC_INDIC_DIGIT_ONE.svg"),
            NaskhDigit("two", "ARABIC-INDIC DIGIT TWO", 0x0662, "041_ARABIC_INDIC_DIGIT_TWO.svg"),
            NaskhDigit("three", "ARABIC-INDIC DIGIT THREE", 0x0663, "042_ARABIC_INDIC_DIGIT_THREE.svg"),
            NaskhDigit("four", "ARABIC-INDIC DIGIT FOUR", 0x0664, "043_ARABIC_INDIC_DIGIT_FOUR.svg"),
            NaskhDigit("five", "ARABIC-INDIC DIGIT FIVE", 0x0665, "044_ARABIC_INDIC_DIGIT_FIVE.svg"),
            NaskhDigit("six", "ARABIC-INDIC DIGIT SIX", 0x0666, "045_ARABIC_INDIC_DIGIT_SIX.svg"),
            NaskhDigit("seven", "ARABIC-INDIC DIGIT SEVEN", 0x0667, "046_ARABIC_INDIC_DIGIT_SEVEN.svg"),
            NaskhDigit("eight", "ARABIC-INDIC DIGIT EIGHT", 0x0668, "047_ARABIC_INDIC_DIGIT_EIGHT.svg"),
            NaskhDigit("nine", "ARABIC-INDIC DIGIT NINE", 0x0669, "048_ARABIC_INDIC_DIGIT_NINE.svg"),
        )
}
