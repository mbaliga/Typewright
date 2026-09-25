// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.scripts.kana

/**
 * Which kana script a Unicode character name belongs to, and the glyph-name suffix this task's
 * own naming convention assigns it ("kana glyphs end `-hira`/`-kata`" -- the same dash-suffix
 * pattern [com.asoc.typewright.scripts.GlyphSpec]'s own KDoc already names for Devanagari
 * (`-deva`) and Arabic (`-ar`)).
 *
 * [unicodeNamePrefixes] is ordered most-specific-first because Katakana's own Unicode names are
 * not all `KATAKANA LETTER ...`: the middle dot is `KATAKANA MIDDLE DOT` and the long-vowel mark
 * is `KATAKANA-HIRAGANA PROLONGED SOUND MARK` (shared by both kana scripts in Unicode, but
 * templated only once, in `svg/Katakana`, in `scripts/templates/hyle-all-templates.zip`, so it is
 * modelled here as a Katakana glyph only -- see `KatakanaGlyphs.kt`).
 */
enum class KanaScriptKind(
    val unicodeNamePrefixes: List<String>,
    val glyphNameSuffix: String,
) {
    HIRAGANA(unicodeNamePrefixes = listOf("HIRAGANA LETTER "), glyphNameSuffix = "-hira"),
    KATAKANA(
        unicodeNamePrefixes = listOf("KATAKANA LETTER ", "KATAKANA-HIRAGANA ", "KATAKANA "),
        glyphNameSuffix = "-kata",
    ),
}

/**
 * Derives a kana glyph's name from its real Unicode character name (e.g. `"HIRAGANA LETTER KA"`),
 * per this task's own naming convention (see [KanaScriptKind]).
 *
 * The scheme is mechanical, not a romanisation choice: strip [kind]'s own Unicode-name prefix,
 * lowercase what remains, replace spaces with `-`, then append [KanaScriptKind.glyphNameSuffix].
 * Using the real Unicode name rather than a Hepburn (shi, chi, tsu, fu) or nihon-shiki (si, ti,
 * tu, hu) romanisation avoids inventing a romanisation scheme neither
 * `docs/RESEARCH_font_quality.md` nor `docs/LESSONS_SCAFFOLD.md` documents for kana glyph naming;
 * Unicode's own character names already use the nihon-shiki forms (SI, TI, TU, HU, not SHI, CHI,
 * TSU, FU), so that is what these derived names carry through, not a choice made here. For
 * example `"HIRAGANA LETTER SI"` (U+3057, し) derives to `"si-hira"`, not `"shi-hira"`.
 *
 * @throws IllegalArgumentException if [unicodeName] does not start with any of [kind]'s known
 *   Unicode-name prefixes -- this function refuses to guess a name for a character it does not
 *   recognise rather than silently producing a wrong one (CLAUDE.md law 5, "measured, not
 *   invented").
 */
fun deriveKanaGlyphName(
    unicodeName: String,
    kind: KanaScriptKind,
): String {
    val prefix =
        kind.unicodeNamePrefixes.firstOrNull { unicodeName.startsWith(it) }
            ?: throw IllegalArgumentException(
                "'$unicodeName' does not start with any of $kind's known Unicode-name prefixes " +
                    "${kind.unicodeNamePrefixes}",
            )
    val remainder = unicodeName.removePrefix(prefix)
    val slug = remainder.lowercase().replace(" ", "-")
    return "$slug${kind.glyphNameSuffix}"
}
