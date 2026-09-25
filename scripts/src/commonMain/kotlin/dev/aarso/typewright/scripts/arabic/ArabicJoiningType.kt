// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.scripts.arabic

/**
 * An Arabic letter's real Unicode `Joining_Type` (UAX #53's `ArabicShaping.txt`;
 * https://www.unicode.org/Public/UCD/latest/ucd/ArabicShaping.txt), the property that decides how
 * many cursive-joining positional forms a letter actually has. Only the three values this
 * script's own 39 real letters use are modelled here -- Unicode also defines `C` (join-causing:
 * tatweel, ZWJ) and `T` (transparent: combining marks), neither of which applies to any of the 39
 * real base letters `scripts/templates/hyle-all-templates.zip`'s own `svg/Naskh/` folder covers.
 *
 * [DUAL_JOINING] letters connect to a neighbour on both sides and get all four positional forms
 * (isolated, `.init`, `.medi`, `.fina`) -- this task's own instruction: "for each dual-joining
 * letter, also generate its three positional variants (.init, .medi, .fina suffixes on the same
 * -ar base name)". [RIGHT_JOINING] letters (e.g. alef, dal, reh, waw, the small closed set every
 * Naskh learner meets early because they break the cursive line) connect only to a preceding
 * letter and never to a following one, so a shaping engine only ever needs an isolated and a
 * final form for them, never initial or medial; this build does not generate `.init`/`.medi`/
 * `.fina` glyphs for them at all -- see [ArabicGlyphInventory]'s own KDoc for why it does not even
 * add a bare `.fina`, staying inside this task's literal instruction rather than extending it.
 * [NON_JOINING] has exactly one real member here, hamza (`hamza-ar`), which never connects to a
 * neighbour on either side and so has only its isolated form; the ten Arabic-Indic digits are
 * outside the cursive-joining system entirely (not classified by `Joining_Type` at all, unlike
 * hamza) and are modelled separately in [ArabicLetters] for that reason -- both end up with an
 * isolated-only glyph, but for two genuinely different real reasons, not the same one.
 *
 * This classification was cross-checked against the real Unicode `ArabicShaping.txt` (18.0.0) for
 * all 39 real letters `scripts/templates/hyle-all-templates.zip`'s own `svg/Naskh/` folder
 * contains -- not typed from memory alone. No copy of that Unicode Character Database file lives
 * in this repository to check it against later, so this cross-check, and the full R/D/U table it
 * produced, are written up in `docs/OPEN_QUESTIONS.md`.
 */
enum class ArabicJoiningType {
    DUAL_JOINING,
    RIGHT_JOINING,
    NON_JOINING,
}
