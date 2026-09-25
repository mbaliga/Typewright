// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.scripts.arabic

import dev.aarso.typewright.scripts.GlyphInventory
import dev.aarso.typewright.scripts.GlyphSpec
import dev.aarso.typewright.scripts.WritingScript

/** This build's own Arabic positional-form suffixes, in the real, documented shaping order. */
private const val SUFFIX_INIT = ".init"
private const val SUFFIX_MEDI = ".medi"
private const val SUFFIX_FINA = ".fina"

/**
 * Arabic Naskh's real glyph inventory, built from [ArabicLetters] (itself read directly from
 * `scripts/templates/hyle-all-templates.zip`'s own `svg/Naskh/` folder -- not invented
 * independently of it, this task's own instruction).
 *
 * Every glyph name ends `-ar` (this task's own naming instruction). For an isolated form, that
 * suffix is the *whole* suffix -- "the isolated form itself carries no suffix" beyond `-ar`
 * ([isolatedGlyphName]). For each of the 28 real letters whose [ArabicJoiningType] is
 * [ArabicJoiningType.DUAL_JOINING], three more glyphs are generated:
 * `<name>-ar.init`, `<name>-ar.medi`, `<name>-ar.fina` ([positionalGlyphNames]) -- this task's own
 * instruction ("for each dual-joining letter, also generate its three positional variants") and
 * the same pattern `docs/RESEARCH_font_quality.md`'s Arabic section evidences by name: "start from
 * `behDotless-ar` in all four forms (isolated, `.init`, `.medi`, `.fina`)". **These three forms
 * are derived, not independently drawn**: `scripts/templates/hyle-all-templates.zip` itself
 * carries exactly one isolated-form template per letter (its own `HOW_TO_USE.md`, quoted in full:
 * "Naskh: right to left. Draw the isolated form of each letter. The four positional shapes
 * (isolated, initial, medial, final) are generated at build time from your isolated drawing plus
 * join rules; if you want to draw them by hand for control, say so and the pack can be expanded to
 * four cells per letter."). [GlyphSpec.unicodeName] and [GlyphSpec.codepoint] are therefore `null`
 * for every derived positional glyph, exactly as [GlyphSpec]'s own KDoc anticipates ("null for a
 * glyph with no single owning codepoint (e.g. a positional variant...)") -- a positional variant
 * is the same skeleton with different joining behaviour applied by the shaping engine at run time,
 * not a second real Unicode character or a second real drawing.
 *
 * The 10 real [ArabicJoiningType.RIGHT_JOINING] letters (alef, dal, ddal, thal, reh, rreh, zain,
 * jeh, waw, yehBarree) and the 1 real [ArabicJoiningType.NON_JOINING] letter (hamza) get an
 * isolated glyph only, per this task's own literal instruction, which scopes positional-form
 * generation to dual-joining letters alone; the 10 Arabic-Indic digits get an isolated glyph only
 * too, since they are outside the cursive-joining system entirely (see [ArabicJoiningType]'s own
 * KDoc). Total real count: 49 isolated glyphs (one per real template, matching the zip's own 49
 * files) plus 28 * 3 = 84 derived positional glyphs = **133 glyphs** in this inventory.
 */
object ArabicGlyphInventory {
    /** The real, whole-suffix name of a letter's isolated form -- e.g. `"beh-ar"`. */
    internal fun isolatedGlyphName(baseName: String): String = "$baseName-ar"

    /**
     * The three derived positional-form names for a dual-joining letter -- e.g. for `"beh"`:
     * `["beh-ar.init", "beh-ar.medi", "beh-ar.fina"]`, in that shaping order.
     */
    internal fun positionalGlyphNames(baseName: String): List<String> {
        val isolated = isolatedGlyphName(baseName)
        return listOf(isolated + SUFFIX_INIT, isolated + SUFFIX_MEDI, isolated + SUFFIX_FINA)
    }

    private fun isolatedSpecFor(letter: NaskhLetter): GlyphSpec =
        GlyphSpec(
            name = isolatedGlyphName(letter.baseName),
            unicodeName = letter.unicodeName,
            codepoint = letter.codepoint,
        )

    private fun isolatedSpecFor(digit: NaskhDigit): GlyphSpec =
        GlyphSpec(
            name = isolatedGlyphName(digit.baseName),
            unicodeName = digit.unicodeName,
            codepoint = digit.codepoint,
        )

    /** The three derived [GlyphSpec]s for one dual-joining letter; `unicodeName`/`codepoint` are `null` -- see class KDoc. */
    private fun positionalSpecsFor(letter: NaskhLetter): List<GlyphSpec> =
        positionalGlyphNames(letter.baseName).map { GlyphSpec(name = it, unicodeName = null, codepoint = null) }

    /** All 49 real isolated-form glyphs, one per real template file, in the zip's own index order. */
    val isolatedGlyphs: List<GlyphSpec> =
        ArabicLetters.letters.map(::isolatedSpecFor) + ArabicLetters.digits.map(::isolatedSpecFor)

    /** All 84 derived positional-form glyphs (28 dual-joining letters * 3 forms each), no real template behind any of them. */
    val derivedPositionalGlyphs: List<GlyphSpec> =
        ArabicLetters.letters
            .filter { it.joiningType == ArabicJoiningType.DUAL_JOINING }
            .flatMap(::positionalSpecsFor)

    /** All 133 glyphs: [isolatedGlyphs] (49) followed by [derivedPositionalGlyphs] (84). */
    val glyphs: List<GlyphSpec> = isolatedGlyphs + derivedPositionalGlyphs

    fun build(): GlyphInventory = GlyphInventory(script = WritingScript.ARABIC_NASKH, glyphs = glyphs)
}
