// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.scripts.kana

import com.asoc.typewright.scripts.GlyphInventory
import com.asoc.typewright.scripts.TemplateSheet
import com.asoc.typewright.scripts.WritingScript

/** `scripts/templates/hyle-all-templates.zip`'s own `svg/Katakana` folder path. */
const val KATAKANA_TEMPLATE_FOLDER: String = "svg/Katakana"

/**
 * Katakana's real 57-glyph inventory, one entry per real template in
 * `scripts/templates/hyle-all-templates.zip`'s `svg/Katakana` folder (confirmed by
 * `scripts/templates/generate_kana_manifest.py` against the zip's own `HOW_TO_USE.md`, which
 * states the same count: "Katakana svg/Katakana 57"). Structurally the same as
 * [HIRAGANA_TEMPLATES] -- 46 base gojuon syllables (ア--ン) plus 9 small kana -- plus two glyphs
 * Hiragana's own folder has no equivalent template for: the katakana middle dot (・, U+30FB,
 * used to separate loanwords) and the prolonged-sound mark (ー, U+30FC). The prolonged-sound
 * mark's real Unicode name is `KATAKANA-HIRAGANA PROLONGED SOUND MARK` -- it is used in both
 * kana scripts' running text, but the template pack draws it only once, filed under
 * `svg/Katakana`, so it is modelled here as a Katakana glyph only (`prolonged-sound-mark-kata`),
 * not duplicated into [HIRAGANA_TEMPLATES]. No voiced/semi-voiced forms here either, for the
 * identical reason [HIRAGANA_TEMPLATES] states. See [KANA_INVENTORY_SCOPE_NOTE].
 */
internal val KATAKANA_TEMPLATES: List<KanaTemplate> =
    listOf(
        KanaTemplate(
            name = "a-kata",
            unicodeName = "KATAKANA LETTER A",
            codepoint = 0x30A2,
            templateFileName = "000_KATAKANA_LETTER_A.svg",
        ),
        KanaTemplate(
            name = "i-kata",
            unicodeName = "KATAKANA LETTER I",
            codepoint = 0x30A4,
            templateFileName = "001_KATAKANA_LETTER_I.svg",
        ),
        KanaTemplate(
            name = "u-kata",
            unicodeName = "KATAKANA LETTER U",
            codepoint = 0x30A6,
            templateFileName = "002_KATAKANA_LETTER_U.svg",
        ),
        KanaTemplate(
            name = "e-kata",
            unicodeName = "KATAKANA LETTER E",
            codepoint = 0x30A8,
            templateFileName = "003_KATAKANA_LETTER_E.svg",
        ),
        KanaTemplate(
            name = "o-kata",
            unicodeName = "KATAKANA LETTER O",
            codepoint = 0x30AA,
            templateFileName = "004_KATAKANA_LETTER_O.svg",
        ),
        KanaTemplate(
            name = "ka-kata",
            unicodeName = "KATAKANA LETTER KA",
            codepoint = 0x30AB,
            templateFileName = "005_KATAKANA_LETTER_KA.svg",
        ),
        KanaTemplate(
            name = "ki-kata",
            unicodeName = "KATAKANA LETTER KI",
            codepoint = 0x30AD,
            templateFileName = "006_KATAKANA_LETTER_KI.svg",
        ),
        KanaTemplate(
            name = "ku-kata",
            unicodeName = "KATAKANA LETTER KU",
            codepoint = 0x30AF,
            templateFileName = "007_KATAKANA_LETTER_KU.svg",
        ),
        KanaTemplate(
            name = "ke-kata",
            unicodeName = "KATAKANA LETTER KE",
            codepoint = 0x30B1,
            templateFileName = "008_KATAKANA_LETTER_KE.svg",
        ),
        KanaTemplate(
            name = "ko-kata",
            unicodeName = "KATAKANA LETTER KO",
            codepoint = 0x30B3,
            templateFileName = "009_KATAKANA_LETTER_KO.svg",
        ),
        KanaTemplate(
            name = "sa-kata",
            unicodeName = "KATAKANA LETTER SA",
            codepoint = 0x30B5,
            templateFileName = "010_KATAKANA_LETTER_SA.svg",
        ),
        KanaTemplate(
            name = "si-kata",
            unicodeName = "KATAKANA LETTER SI",
            codepoint = 0x30B7,
            templateFileName = "011_KATAKANA_LETTER_SI.svg",
        ),
        KanaTemplate(
            name = "su-kata",
            unicodeName = "KATAKANA LETTER SU",
            codepoint = 0x30B9,
            templateFileName = "012_KATAKANA_LETTER_SU.svg",
        ),
        KanaTemplate(
            name = "se-kata",
            unicodeName = "KATAKANA LETTER SE",
            codepoint = 0x30BB,
            templateFileName = "013_KATAKANA_LETTER_SE.svg",
        ),
        KanaTemplate(
            name = "so-kata",
            unicodeName = "KATAKANA LETTER SO",
            codepoint = 0x30BD,
            templateFileName = "014_KATAKANA_LETTER_SO.svg",
        ),
        KanaTemplate(
            name = "ta-kata",
            unicodeName = "KATAKANA LETTER TA",
            codepoint = 0x30BF,
            templateFileName = "015_KATAKANA_LETTER_TA.svg",
        ),
        KanaTemplate(
            name = "ti-kata",
            unicodeName = "KATAKANA LETTER TI",
            codepoint = 0x30C1,
            templateFileName = "016_KATAKANA_LETTER_TI.svg",
        ),
        KanaTemplate(
            name = "tu-kata",
            unicodeName = "KATAKANA LETTER TU",
            codepoint = 0x30C4,
            templateFileName = "017_KATAKANA_LETTER_TU.svg",
        ),
        KanaTemplate(
            name = "te-kata",
            unicodeName = "KATAKANA LETTER TE",
            codepoint = 0x30C6,
            templateFileName = "018_KATAKANA_LETTER_TE.svg",
        ),
        KanaTemplate(
            name = "to-kata",
            unicodeName = "KATAKANA LETTER TO",
            codepoint = 0x30C8,
            templateFileName = "019_KATAKANA_LETTER_TO.svg",
        ),
        KanaTemplate(
            name = "na-kata",
            unicodeName = "KATAKANA LETTER NA",
            codepoint = 0x30CA,
            templateFileName = "020_KATAKANA_LETTER_NA.svg",
        ),
        KanaTemplate(
            name = "ni-kata",
            unicodeName = "KATAKANA LETTER NI",
            codepoint = 0x30CB,
            templateFileName = "021_KATAKANA_LETTER_NI.svg",
        ),
        KanaTemplate(
            name = "nu-kata",
            unicodeName = "KATAKANA LETTER NU",
            codepoint = 0x30CC,
            templateFileName = "022_KATAKANA_LETTER_NU.svg",
        ),
        KanaTemplate(
            name = "ne-kata",
            unicodeName = "KATAKANA LETTER NE",
            codepoint = 0x30CD,
            templateFileName = "023_KATAKANA_LETTER_NE.svg",
        ),
        KanaTemplate(
            name = "no-kata",
            unicodeName = "KATAKANA LETTER NO",
            codepoint = 0x30CE,
            templateFileName = "024_KATAKANA_LETTER_NO.svg",
        ),
        KanaTemplate(
            name = "ha-kata",
            unicodeName = "KATAKANA LETTER HA",
            codepoint = 0x30CF,
            templateFileName = "025_KATAKANA_LETTER_HA.svg",
        ),
        KanaTemplate(
            name = "hi-kata",
            unicodeName = "KATAKANA LETTER HI",
            codepoint = 0x30D2,
            templateFileName = "026_KATAKANA_LETTER_HI.svg",
        ),
        KanaTemplate(
            name = "hu-kata",
            unicodeName = "KATAKANA LETTER HU",
            codepoint = 0x30D5,
            templateFileName = "027_KATAKANA_LETTER_HU.svg",
        ),
        KanaTemplate(
            name = "he-kata",
            unicodeName = "KATAKANA LETTER HE",
            codepoint = 0x30D8,
            templateFileName = "028_KATAKANA_LETTER_HE.svg",
        ),
        KanaTemplate(
            name = "ho-kata",
            unicodeName = "KATAKANA LETTER HO",
            codepoint = 0x30DB,
            templateFileName = "029_KATAKANA_LETTER_HO.svg",
        ),
        KanaTemplate(
            name = "ma-kata",
            unicodeName = "KATAKANA LETTER MA",
            codepoint = 0x30DE,
            templateFileName = "030_KATAKANA_LETTER_MA.svg",
        ),
        KanaTemplate(
            name = "mi-kata",
            unicodeName = "KATAKANA LETTER MI",
            codepoint = 0x30DF,
            templateFileName = "031_KATAKANA_LETTER_MI.svg",
        ),
        KanaTemplate(
            name = "mu-kata",
            unicodeName = "KATAKANA LETTER MU",
            codepoint = 0x30E0,
            templateFileName = "032_KATAKANA_LETTER_MU.svg",
        ),
        KanaTemplate(
            name = "me-kata",
            unicodeName = "KATAKANA LETTER ME",
            codepoint = 0x30E1,
            templateFileName = "033_KATAKANA_LETTER_ME.svg",
        ),
        KanaTemplate(
            name = "mo-kata",
            unicodeName = "KATAKANA LETTER MO",
            codepoint = 0x30E2,
            templateFileName = "034_KATAKANA_LETTER_MO.svg",
        ),
        KanaTemplate(
            name = "ya-kata",
            unicodeName = "KATAKANA LETTER YA",
            codepoint = 0x30E4,
            templateFileName = "035_KATAKANA_LETTER_YA.svg",
        ),
        KanaTemplate(
            name = "yu-kata",
            unicodeName = "KATAKANA LETTER YU",
            codepoint = 0x30E6,
            templateFileName = "036_KATAKANA_LETTER_YU.svg",
        ),
        KanaTemplate(
            name = "yo-kata",
            unicodeName = "KATAKANA LETTER YO",
            codepoint = 0x30E8,
            templateFileName = "037_KATAKANA_LETTER_YO.svg",
        ),
        KanaTemplate(
            name = "ra-kata",
            unicodeName = "KATAKANA LETTER RA",
            codepoint = 0x30E9,
            templateFileName = "038_KATAKANA_LETTER_RA.svg",
        ),
        KanaTemplate(
            name = "ri-kata",
            unicodeName = "KATAKANA LETTER RI",
            codepoint = 0x30EA,
            templateFileName = "039_KATAKANA_LETTER_RI.svg",
        ),
        KanaTemplate(
            name = "ru-kata",
            unicodeName = "KATAKANA LETTER RU",
            codepoint = 0x30EB,
            templateFileName = "040_KATAKANA_LETTER_RU.svg",
        ),
        KanaTemplate(
            name = "re-kata",
            unicodeName = "KATAKANA LETTER RE",
            codepoint = 0x30EC,
            templateFileName = "041_KATAKANA_LETTER_RE.svg",
        ),
        KanaTemplate(
            name = "ro-kata",
            unicodeName = "KATAKANA LETTER RO",
            codepoint = 0x30ED,
            templateFileName = "042_KATAKANA_LETTER_RO.svg",
        ),
        KanaTemplate(
            name = "wa-kata",
            unicodeName = "KATAKANA LETTER WA",
            codepoint = 0x30EF,
            templateFileName = "043_KATAKANA_LETTER_WA.svg",
        ),
        KanaTemplate(
            name = "wo-kata",
            unicodeName = "KATAKANA LETTER WO",
            codepoint = 0x30F2,
            templateFileName = "044_KATAKANA_LETTER_WO.svg",
        ),
        KanaTemplate(
            name = "n-kata",
            unicodeName = "KATAKANA LETTER N",
            codepoint = 0x30F3,
            templateFileName = "045_KATAKANA_LETTER_N.svg",
        ),
        KanaTemplate(
            name = "small-a-kata",
            unicodeName = "KATAKANA LETTER SMALL A",
            codepoint = 0x30A1,
            templateFileName = "046_KATAKANA_LETTER_SMALL_A.svg",
        ),
        KanaTemplate(
            name = "small-i-kata",
            unicodeName = "KATAKANA LETTER SMALL I",
            codepoint = 0x30A3,
            templateFileName = "047_KATAKANA_LETTER_SMALL_I.svg",
        ),
        KanaTemplate(
            name = "small-u-kata",
            unicodeName = "KATAKANA LETTER SMALL U",
            codepoint = 0x30A5,
            templateFileName = "048_KATAKANA_LETTER_SMALL_U.svg",
        ),
        KanaTemplate(
            name = "small-e-kata",
            unicodeName = "KATAKANA LETTER SMALL E",
            codepoint = 0x30A7,
            templateFileName = "049_KATAKANA_LETTER_SMALL_E.svg",
        ),
        KanaTemplate(
            name = "small-o-kata",
            unicodeName = "KATAKANA LETTER SMALL O",
            codepoint = 0x30A9,
            templateFileName = "050_KATAKANA_LETTER_SMALL_O.svg",
        ),
        KanaTemplate(
            name = "small-tu-kata",
            unicodeName = "KATAKANA LETTER SMALL TU",
            codepoint = 0x30C3,
            templateFileName = "051_KATAKANA_LETTER_SMALL_TU.svg",
        ),
        KanaTemplate(
            name = "small-ya-kata",
            unicodeName = "KATAKANA LETTER SMALL YA",
            codepoint = 0x30E3,
            templateFileName = "052_KATAKANA_LETTER_SMALL_YA.svg",
        ),
        KanaTemplate(
            name = "small-yu-kata",
            unicodeName = "KATAKANA LETTER SMALL YU",
            codepoint = 0x30E5,
            templateFileName = "053_KATAKANA_LETTER_SMALL_YU.svg",
        ),
        KanaTemplate(
            name = "small-yo-kata",
            unicodeName = "KATAKANA LETTER SMALL YO",
            codepoint = 0x30E7,
            templateFileName = "054_KATAKANA_LETTER_SMALL_YO.svg",
        ),
        KanaTemplate(
            name = "prolonged-sound-mark-kata",
            unicodeName = "KATAKANA-HIRAGANA PROLONGED SOUND MARK",
            codepoint = 0x30FC,
            templateFileName = "055_KATAKANA_HIRAGANA_PROLONGED_SOUND_MARK.svg",
        ),
        KanaTemplate(
            name = "middle-dot-kata",
            unicodeName = "KATAKANA MIDDLE DOT",
            codepoint = 0x30FB,
            templateFileName = "056_KATAKANA_MIDDLE_DOT.svg",
        ),
    )

val KATAKANA_GLYPH_INVENTORY: GlyphInventory = KATAKANA_TEMPLATES.toGlyphInventory(WritingScript.KATAKANA)

val KATAKANA_TEMPLATE_SHEET: TemplateSheet =
    TemplateSheet(
        script = WritingScript.KATAKANA,
        entries = KATAKANA_TEMPLATES.toTemplateSheetEntries(KATAKANA_TEMPLATE_FOLDER),
    )
