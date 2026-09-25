// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.scripts.kana

import dev.aarso.typewright.scripts.GlyphInventory
import dev.aarso.typewright.scripts.TemplateSheet
import dev.aarso.typewright.scripts.WritingScript

/** `scripts/templates/hyle-all-templates.zip`'s own `svg/Hiragana` folder path. */
const val HIRAGANA_TEMPLATE_FOLDER: String = "svg/Hiragana"

/**
 * Hiragana's real 55-glyph inventory, one entry per real template in
 * `scripts/templates/hyle-all-templates.zip`'s `svg/Hiragana` folder (confirmed by
 * `scripts/templates/generate_kana_manifest.py` against the zip's own `HOW_TO_USE.md`, which
 * states the same count: "Hiragana svg/Hiragana 55"). This is the 46 base gojuon syllables
 * (あ--ん, including the syllabic ん) plus the 9 small kana (ぁぃぅぇぉっゃゅょ) used for
 * combination sounds and gemination -- no voiced (が) or semi-voiced (ぱ) forms, because the
 * template pack does not draw them separately: the research and the pack's own `HOW_TO_USE.md`
 * agree those compose from a base kana plus the combining dakuten/handakuten mark
 * (`KanaFeaturePlan.kt`), not as their own drawn outlines. This is real coverage, not the
 * research's own "closer to ~200 glyphs than 92" full-kana-with-variants estimate -- see
 * [KANA_INVENTORY_SCOPE_NOTE].
 */
internal val HIRAGANA_TEMPLATES: List<KanaTemplate> =
    listOf(
        KanaTemplate(
            name = "a-hira",
            unicodeName = "HIRAGANA LETTER A",
            codepoint = 0x3042,
            templateFileName = "000_HIRAGANA_LETTER_A.svg",
        ),
        KanaTemplate(
            name = "i-hira",
            unicodeName = "HIRAGANA LETTER I",
            codepoint = 0x3044,
            templateFileName = "001_HIRAGANA_LETTER_I.svg",
        ),
        KanaTemplate(
            name = "u-hira",
            unicodeName = "HIRAGANA LETTER U",
            codepoint = 0x3046,
            templateFileName = "002_HIRAGANA_LETTER_U.svg",
        ),
        KanaTemplate(
            name = "e-hira",
            unicodeName = "HIRAGANA LETTER E",
            codepoint = 0x3048,
            templateFileName = "003_HIRAGANA_LETTER_E.svg",
        ),
        KanaTemplate(
            name = "o-hira",
            unicodeName = "HIRAGANA LETTER O",
            codepoint = 0x304A,
            templateFileName = "004_HIRAGANA_LETTER_O.svg",
        ),
        KanaTemplate(
            name = "ka-hira",
            unicodeName = "HIRAGANA LETTER KA",
            codepoint = 0x304B,
            templateFileName = "005_HIRAGANA_LETTER_KA.svg",
        ),
        KanaTemplate(
            name = "ki-hira",
            unicodeName = "HIRAGANA LETTER KI",
            codepoint = 0x304D,
            templateFileName = "006_HIRAGANA_LETTER_KI.svg",
        ),
        KanaTemplate(
            name = "ku-hira",
            unicodeName = "HIRAGANA LETTER KU",
            codepoint = 0x304F,
            templateFileName = "007_HIRAGANA_LETTER_KU.svg",
        ),
        KanaTemplate(
            name = "ke-hira",
            unicodeName = "HIRAGANA LETTER KE",
            codepoint = 0x3051,
            templateFileName = "008_HIRAGANA_LETTER_KE.svg",
        ),
        KanaTemplate(
            name = "ko-hira",
            unicodeName = "HIRAGANA LETTER KO",
            codepoint = 0x3053,
            templateFileName = "009_HIRAGANA_LETTER_KO.svg",
        ),
        KanaTemplate(
            name = "sa-hira",
            unicodeName = "HIRAGANA LETTER SA",
            codepoint = 0x3055,
            templateFileName = "010_HIRAGANA_LETTER_SA.svg",
        ),
        KanaTemplate(
            name = "si-hira",
            unicodeName = "HIRAGANA LETTER SI",
            codepoint = 0x3057,
            templateFileName = "011_HIRAGANA_LETTER_SI.svg",
        ),
        KanaTemplate(
            name = "su-hira",
            unicodeName = "HIRAGANA LETTER SU",
            codepoint = 0x3059,
            templateFileName = "012_HIRAGANA_LETTER_SU.svg",
        ),
        KanaTemplate(
            name = "se-hira",
            unicodeName = "HIRAGANA LETTER SE",
            codepoint = 0x305B,
            templateFileName = "013_HIRAGANA_LETTER_SE.svg",
        ),
        KanaTemplate(
            name = "so-hira",
            unicodeName = "HIRAGANA LETTER SO",
            codepoint = 0x305D,
            templateFileName = "014_HIRAGANA_LETTER_SO.svg",
        ),
        KanaTemplate(
            name = "ta-hira",
            unicodeName = "HIRAGANA LETTER TA",
            codepoint = 0x305F,
            templateFileName = "015_HIRAGANA_LETTER_TA.svg",
        ),
        KanaTemplate(
            name = "ti-hira",
            unicodeName = "HIRAGANA LETTER TI",
            codepoint = 0x3061,
            templateFileName = "016_HIRAGANA_LETTER_TI.svg",
        ),
        KanaTemplate(
            name = "tu-hira",
            unicodeName = "HIRAGANA LETTER TU",
            codepoint = 0x3064,
            templateFileName = "017_HIRAGANA_LETTER_TU.svg",
        ),
        KanaTemplate(
            name = "te-hira",
            unicodeName = "HIRAGANA LETTER TE",
            codepoint = 0x3066,
            templateFileName = "018_HIRAGANA_LETTER_TE.svg",
        ),
        KanaTemplate(
            name = "to-hira",
            unicodeName = "HIRAGANA LETTER TO",
            codepoint = 0x3068,
            templateFileName = "019_HIRAGANA_LETTER_TO.svg",
        ),
        KanaTemplate(
            name = "na-hira",
            unicodeName = "HIRAGANA LETTER NA",
            codepoint = 0x306A,
            templateFileName = "020_HIRAGANA_LETTER_NA.svg",
        ),
        KanaTemplate(
            name = "ni-hira",
            unicodeName = "HIRAGANA LETTER NI",
            codepoint = 0x306B,
            templateFileName = "021_HIRAGANA_LETTER_NI.svg",
        ),
        KanaTemplate(
            name = "nu-hira",
            unicodeName = "HIRAGANA LETTER NU",
            codepoint = 0x306C,
            templateFileName = "022_HIRAGANA_LETTER_NU.svg",
        ),
        KanaTemplate(
            name = "ne-hira",
            unicodeName = "HIRAGANA LETTER NE",
            codepoint = 0x306D,
            templateFileName = "023_HIRAGANA_LETTER_NE.svg",
        ),
        KanaTemplate(
            name = "no-hira",
            unicodeName = "HIRAGANA LETTER NO",
            codepoint = 0x306E,
            templateFileName = "024_HIRAGANA_LETTER_NO.svg",
        ),
        KanaTemplate(
            name = "ha-hira",
            unicodeName = "HIRAGANA LETTER HA",
            codepoint = 0x306F,
            templateFileName = "025_HIRAGANA_LETTER_HA.svg",
        ),
        KanaTemplate(
            name = "hi-hira",
            unicodeName = "HIRAGANA LETTER HI",
            codepoint = 0x3072,
            templateFileName = "026_HIRAGANA_LETTER_HI.svg",
        ),
        KanaTemplate(
            name = "hu-hira",
            unicodeName = "HIRAGANA LETTER HU",
            codepoint = 0x3075,
            templateFileName = "027_HIRAGANA_LETTER_HU.svg",
        ),
        KanaTemplate(
            name = "he-hira",
            unicodeName = "HIRAGANA LETTER HE",
            codepoint = 0x3078,
            templateFileName = "028_HIRAGANA_LETTER_HE.svg",
        ),
        KanaTemplate(
            name = "ho-hira",
            unicodeName = "HIRAGANA LETTER HO",
            codepoint = 0x307B,
            templateFileName = "029_HIRAGANA_LETTER_HO.svg",
        ),
        KanaTemplate(
            name = "ma-hira",
            unicodeName = "HIRAGANA LETTER MA",
            codepoint = 0x307E,
            templateFileName = "030_HIRAGANA_LETTER_MA.svg",
        ),
        KanaTemplate(
            name = "mi-hira",
            unicodeName = "HIRAGANA LETTER MI",
            codepoint = 0x307F,
            templateFileName = "031_HIRAGANA_LETTER_MI.svg",
        ),
        KanaTemplate(
            name = "mu-hira",
            unicodeName = "HIRAGANA LETTER MU",
            codepoint = 0x3080,
            templateFileName = "032_HIRAGANA_LETTER_MU.svg",
        ),
        KanaTemplate(
            name = "me-hira",
            unicodeName = "HIRAGANA LETTER ME",
            codepoint = 0x3081,
            templateFileName = "033_HIRAGANA_LETTER_ME.svg",
        ),
        KanaTemplate(
            name = "mo-hira",
            unicodeName = "HIRAGANA LETTER MO",
            codepoint = 0x3082,
            templateFileName = "034_HIRAGANA_LETTER_MO.svg",
        ),
        KanaTemplate(
            name = "ya-hira",
            unicodeName = "HIRAGANA LETTER YA",
            codepoint = 0x3084,
            templateFileName = "035_HIRAGANA_LETTER_YA.svg",
        ),
        KanaTemplate(
            name = "yu-hira",
            unicodeName = "HIRAGANA LETTER YU",
            codepoint = 0x3086,
            templateFileName = "036_HIRAGANA_LETTER_YU.svg",
        ),
        KanaTemplate(
            name = "yo-hira",
            unicodeName = "HIRAGANA LETTER YO",
            codepoint = 0x3088,
            templateFileName = "037_HIRAGANA_LETTER_YO.svg",
        ),
        KanaTemplate(
            name = "ra-hira",
            unicodeName = "HIRAGANA LETTER RA",
            codepoint = 0x3089,
            templateFileName = "038_HIRAGANA_LETTER_RA.svg",
        ),
        KanaTemplate(
            name = "ri-hira",
            unicodeName = "HIRAGANA LETTER RI",
            codepoint = 0x308A,
            templateFileName = "039_HIRAGANA_LETTER_RI.svg",
        ),
        KanaTemplate(
            name = "ru-hira",
            unicodeName = "HIRAGANA LETTER RU",
            codepoint = 0x308B,
            templateFileName = "040_HIRAGANA_LETTER_RU.svg",
        ),
        KanaTemplate(
            name = "re-hira",
            unicodeName = "HIRAGANA LETTER RE",
            codepoint = 0x308C,
            templateFileName = "041_HIRAGANA_LETTER_RE.svg",
        ),
        KanaTemplate(
            name = "ro-hira",
            unicodeName = "HIRAGANA LETTER RO",
            codepoint = 0x308D,
            templateFileName = "042_HIRAGANA_LETTER_RO.svg",
        ),
        KanaTemplate(
            name = "wa-hira",
            unicodeName = "HIRAGANA LETTER WA",
            codepoint = 0x308F,
            templateFileName = "043_HIRAGANA_LETTER_WA.svg",
        ),
        KanaTemplate(
            name = "wo-hira",
            unicodeName = "HIRAGANA LETTER WO",
            codepoint = 0x3092,
            templateFileName = "044_HIRAGANA_LETTER_WO.svg",
        ),
        KanaTemplate(
            name = "n-hira",
            unicodeName = "HIRAGANA LETTER N",
            codepoint = 0x3093,
            templateFileName = "045_HIRAGANA_LETTER_N.svg",
        ),
        KanaTemplate(
            name = "small-a-hira",
            unicodeName = "HIRAGANA LETTER SMALL A",
            codepoint = 0x3041,
            templateFileName = "046_HIRAGANA_LETTER_SMALL_A.svg",
        ),
        KanaTemplate(
            name = "small-i-hira",
            unicodeName = "HIRAGANA LETTER SMALL I",
            codepoint = 0x3043,
            templateFileName = "047_HIRAGANA_LETTER_SMALL_I.svg",
        ),
        KanaTemplate(
            name = "small-u-hira",
            unicodeName = "HIRAGANA LETTER SMALL U",
            codepoint = 0x3045,
            templateFileName = "048_HIRAGANA_LETTER_SMALL_U.svg",
        ),
        KanaTemplate(
            name = "small-e-hira",
            unicodeName = "HIRAGANA LETTER SMALL E",
            codepoint = 0x3047,
            templateFileName = "049_HIRAGANA_LETTER_SMALL_E.svg",
        ),
        KanaTemplate(
            name = "small-o-hira",
            unicodeName = "HIRAGANA LETTER SMALL O",
            codepoint = 0x3049,
            templateFileName = "050_HIRAGANA_LETTER_SMALL_O.svg",
        ),
        KanaTemplate(
            name = "small-tu-hira",
            unicodeName = "HIRAGANA LETTER SMALL TU",
            codepoint = 0x3063,
            templateFileName = "051_HIRAGANA_LETTER_SMALL_TU.svg",
        ),
        KanaTemplate(
            name = "small-ya-hira",
            unicodeName = "HIRAGANA LETTER SMALL YA",
            codepoint = 0x3083,
            templateFileName = "052_HIRAGANA_LETTER_SMALL_YA.svg",
        ),
        KanaTemplate(
            name = "small-yu-hira",
            unicodeName = "HIRAGANA LETTER SMALL YU",
            codepoint = 0x3085,
            templateFileName = "053_HIRAGANA_LETTER_SMALL_YU.svg",
        ),
        KanaTemplate(
            name = "small-yo-hira",
            unicodeName = "HIRAGANA LETTER SMALL YO",
            codepoint = 0x3087,
            templateFileName = "054_HIRAGANA_LETTER_SMALL_YO.svg",
        ),
    )

val HIRAGANA_GLYPH_INVENTORY: GlyphInventory = HIRAGANA_TEMPLATES.toGlyphInventory(WritingScript.HIRAGANA)

val HIRAGANA_TEMPLATE_SHEET: TemplateSheet =
    TemplateSheet(
        script = WritingScript.HIRAGANA,
        entries = HIRAGANA_TEMPLATES.toTemplateSheetEntries(HIRAGANA_TEMPLATE_FOLDER),
    )
