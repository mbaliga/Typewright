package dev.aarso.typewright.scripts.devanagari

import dev.aarso.typewright.scripts.TemplateSheet
import dev.aarso.typewright.scripts.TemplateSheetEntry
import dev.aarso.typewright.scripts.WritingScript

/**
 * Devanagari's real template sheet: one [TemplateSheetEntry] per file in
 * `scripts/templates/hyle-all-templates.zip`'s own `svg/Devanagari/` folder, read directly from
 * that folder's listing (see `data/scripts/build_devanagari_template_manifest.py` and the
 * checked-in `scripts/templates/devanagari-manifest.json` it generates) -- 68 entries, matching
 * the folder's own file count and `HOW_TO_USE.md`'s stated "68".
 *
 * Two of those 68 files are byte-identical duplicates of an earlier file in the same folder
 * (confirmed by content hash, not filename alone): `011_DEVANAGARI_LETTER_A.svg` duplicates
 * `000_DEVANAGARI_LETTER_A.svg`, and `056_DEVANAGARI_SIGN_ANUSVARA.svg` duplicates
 * `012_DEVANAGARI_SIGN_ANUSVARA.svg`. Rather than silently dropping the duplicate files (which
 * would make this sheet's own count disagree with the real folder listing) or inventing a second
 * glyph to explain them (which [DevanagariGlyphInventory]'s own KDoc explicitly declines to do),
 * both duplicate files get their own [TemplateSheetEntry] pointed at the one real glyph they draw
 * -- `a-deva` twice, `anusvara-deva` twice -- so this sheet says plainly, entry by entry, what
 * the zip actually contains. See `docs/OPEN_QUESTIONS.md` for the write-up.
 */
object DevanagariTemplateSheet {
    private const val FOLDER = "svg/Devanagari"

    val entries: List<TemplateSheetEntry> =
        listOf(
            TemplateSheetEntry("a-deva", FOLDER, "000_DEVANAGARI_LETTER_A.svg"),
            TemplateSheetEntry("aa-deva", FOLDER, "001_DEVANAGARI_LETTER_AA.svg"),
            TemplateSheetEntry("i-deva", FOLDER, "002_DEVANAGARI_LETTER_I.svg"),
            TemplateSheetEntry("ii-deva", FOLDER, "003_DEVANAGARI_LETTER_II.svg"),
            TemplateSheetEntry("u-deva", FOLDER, "004_DEVANAGARI_LETTER_U.svg"),
            TemplateSheetEntry("uu-deva", FOLDER, "005_DEVANAGARI_LETTER_UU.svg"),
            TemplateSheetEntry("vocalicR-deva", FOLDER, "006_DEVANAGARI_LETTER_VOCALIC_R.svg"),
            TemplateSheetEntry("e-deva", FOLDER, "007_DEVANAGARI_LETTER_E.svg"),
            TemplateSheetEntry("ai-deva", FOLDER, "008_DEVANAGARI_LETTER_AI.svg"),
            TemplateSheetEntry("o-deva", FOLDER, "009_DEVANAGARI_LETTER_O.svg"),
            TemplateSheetEntry("au-deva", FOLDER, "010_DEVANAGARI_LETTER_AU.svg"),
            // Duplicate of 000 (byte-identical) -- see class KDoc.
            TemplateSheetEntry("a-deva", FOLDER, "011_DEVANAGARI_LETTER_A.svg"),
            TemplateSheetEntry("anusvara-deva", FOLDER, "012_DEVANAGARI_SIGN_ANUSVARA.svg"),
            TemplateSheetEntry("ka-deva", FOLDER, "013_DEVANAGARI_LETTER_KA.svg"),
            TemplateSheetEntry("kha-deva", FOLDER, "014_DEVANAGARI_LETTER_KHA.svg"),
            TemplateSheetEntry("ga-deva", FOLDER, "015_DEVANAGARI_LETTER_GA.svg"),
            TemplateSheetEntry("gha-deva", FOLDER, "016_DEVANAGARI_LETTER_GHA.svg"),
            TemplateSheetEntry("nga-deva", FOLDER, "017_DEVANAGARI_LETTER_NGA.svg"),
            TemplateSheetEntry("ca-deva", FOLDER, "018_DEVANAGARI_LETTER_CA.svg"),
            TemplateSheetEntry("cha-deva", FOLDER, "019_DEVANAGARI_LETTER_CHA.svg"),
            TemplateSheetEntry("ja-deva", FOLDER, "020_DEVANAGARI_LETTER_JA.svg"),
            TemplateSheetEntry("jha-deva", FOLDER, "021_DEVANAGARI_LETTER_JHA.svg"),
            TemplateSheetEntry("nya-deva", FOLDER, "022_DEVANAGARI_LETTER_NYA.svg"),
            TemplateSheetEntry("tta-deva", FOLDER, "023_DEVANAGARI_LETTER_TTA.svg"),
            TemplateSheetEntry("ttha-deva", FOLDER, "024_DEVANAGARI_LETTER_TTHA.svg"),
            TemplateSheetEntry("dda-deva", FOLDER, "025_DEVANAGARI_LETTER_DDA.svg"),
            TemplateSheetEntry("ddha-deva", FOLDER, "026_DEVANAGARI_LETTER_DDHA.svg"),
            TemplateSheetEntry("nna-deva", FOLDER, "027_DEVANAGARI_LETTER_NNA.svg"),
            TemplateSheetEntry("ta-deva", FOLDER, "028_DEVANAGARI_LETTER_TA.svg"),
            TemplateSheetEntry("tha-deva", FOLDER, "029_DEVANAGARI_LETTER_THA.svg"),
            TemplateSheetEntry("da-deva", FOLDER, "030_DEVANAGARI_LETTER_DA.svg"),
            TemplateSheetEntry("dha-deva", FOLDER, "031_DEVANAGARI_LETTER_DHA.svg"),
            TemplateSheetEntry("na-deva", FOLDER, "032_DEVANAGARI_LETTER_NA.svg"),
            TemplateSheetEntry("pa-deva", FOLDER, "033_DEVANAGARI_LETTER_PA.svg"),
            TemplateSheetEntry("pha-deva", FOLDER, "034_DEVANAGARI_LETTER_PHA.svg"),
            TemplateSheetEntry("ba-deva", FOLDER, "035_DEVANAGARI_LETTER_BA.svg"),
            TemplateSheetEntry("bha-deva", FOLDER, "036_DEVANAGARI_LETTER_BHA.svg"),
            TemplateSheetEntry("ma-deva", FOLDER, "037_DEVANAGARI_LETTER_MA.svg"),
            TemplateSheetEntry("ya-deva", FOLDER, "038_DEVANAGARI_LETTER_YA.svg"),
            TemplateSheetEntry("ra-deva", FOLDER, "039_DEVANAGARI_LETTER_RA.svg"),
            TemplateSheetEntry("la-deva", FOLDER, "040_DEVANAGARI_LETTER_LA.svg"),
            TemplateSheetEntry("va-deva", FOLDER, "041_DEVANAGARI_LETTER_VA.svg"),
            TemplateSheetEntry("sha-deva", FOLDER, "042_DEVANAGARI_LETTER_SHA.svg"),
            TemplateSheetEntry("ssa-deva", FOLDER, "043_DEVANAGARI_LETTER_SSA.svg"),
            TemplateSheetEntry("sa-deva", FOLDER, "044_DEVANAGARI_LETTER_SA.svg"),
            TemplateSheetEntry("ha-deva", FOLDER, "045_DEVANAGARI_LETTER_HA.svg"),
            TemplateSheetEntry("aaMatra-deva", FOLDER, "046_DEVANAGARI_VOWEL_SIGN_AA.svg"),
            TemplateSheetEntry("iMatra-deva", FOLDER, "047_DEVANAGARI_VOWEL_SIGN_I.svg"),
            TemplateSheetEntry("iiMatra-deva", FOLDER, "048_DEVANAGARI_VOWEL_SIGN_II.svg"),
            TemplateSheetEntry("uMatra-deva", FOLDER, "049_DEVANAGARI_VOWEL_SIGN_U.svg"),
            TemplateSheetEntry("uuMatra-deva", FOLDER, "050_DEVANAGARI_VOWEL_SIGN_UU.svg"),
            TemplateSheetEntry("vocalicRMatra-deva", FOLDER, "051_DEVANAGARI_VOWEL_SIGN_VOCALIC_R.svg"),
            TemplateSheetEntry("eMatra-deva", FOLDER, "052_DEVANAGARI_VOWEL_SIGN_E.svg"),
            TemplateSheetEntry("aiMatra-deva", FOLDER, "053_DEVANAGARI_VOWEL_SIGN_AI.svg"),
            TemplateSheetEntry("oMatra-deva", FOLDER, "054_DEVANAGARI_VOWEL_SIGN_O.svg"),
            TemplateSheetEntry("auMatra-deva", FOLDER, "055_DEVANAGARI_VOWEL_SIGN_AU.svg"),
            // Duplicate of 012 (byte-identical) -- see class KDoc.
            TemplateSheetEntry("anusvara-deva", FOLDER, "056_DEVANAGARI_SIGN_ANUSVARA.svg"),
            TemplateSheetEntry("visarga-deva", FOLDER, "057_DEVANAGARI_SIGN_VISARGA.svg"),
            TemplateSheetEntry("zero-deva", FOLDER, "058_DEVANAGARI_DIGIT_ZERO.svg"),
            TemplateSheetEntry("one-deva", FOLDER, "059_DEVANAGARI_DIGIT_ONE.svg"),
            TemplateSheetEntry("two-deva", FOLDER, "060_DEVANAGARI_DIGIT_TWO.svg"),
            TemplateSheetEntry("three-deva", FOLDER, "061_DEVANAGARI_DIGIT_THREE.svg"),
            TemplateSheetEntry("four-deva", FOLDER, "062_DEVANAGARI_DIGIT_FOUR.svg"),
            TemplateSheetEntry("five-deva", FOLDER, "063_DEVANAGARI_DIGIT_FIVE.svg"),
            TemplateSheetEntry("six-deva", FOLDER, "064_DEVANAGARI_DIGIT_SIX.svg"),
            TemplateSheetEntry("seven-deva", FOLDER, "065_DEVANAGARI_DIGIT_SEVEN.svg"),
            TemplateSheetEntry("eight-deva", FOLDER, "066_DEVANAGARI_DIGIT_EIGHT.svg"),
            TemplateSheetEntry("nine-deva", FOLDER, "067_DEVANAGARI_DIGIT_NINE.svg"),
        )

    fun build(): TemplateSheet = TemplateSheet(script = WritingScript.DEVANAGARI, entries = entries)
}
