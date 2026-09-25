// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.scripts.arabic

/**
 * A real, explicit disclosure that Nastaliq is out of scope for v1 -- a deliberate scope decision
 * this task makes plainly, not an oversight, even though
 * `scripts/templates/hyle-all-templates.zip` carries a real, populated `svg/Nastaliq/` folder.
 *
 * `docs/TYPEWRIGHT_HANDOFF.md` §4 M8, in full: "Ship order: Latin; Hiragana and Katakana;
 * Devanagari; the other Indic scripts on the Devanagari pattern; Arabic Naskh; South East Asian
 * scripts. **Nastaliq is out of scope for v1 and the app says so.**"
 * `com.asoc.typewright.scripts.WritingScript`'s own KDoc restates the same fact for this ship
 * order specifically. There is no `WritingScript.NASTALIQ` entry in that shared, read-only enum --
 * a structural signal, already in place before this task started, that this build has nowhere to
 * hang a Nastaliq [com.asoc.typewright.scripts.ScriptProfile] even if one were built. Consistent
 * with that: this task builds no `NastaliqMetrics`, no `NastaliqGlyphInventory`, no
 * `NastaliqScriptProfile` -- nothing under this `arabic` package models Nastaliq as a script, only
 * this one disclosure of why not.
 *
 * The concrete reason, not just the decision, per `docs/RESEARCH_font_quality.md`'s "Arabic Naskh
 * joins on a weighted baseline; Nastaliq exceeds OpenType" section (its own section title says the
 * finding outright): "Urdu adds a hard limit the app must state honestly: the culturally expected
 * Nastaliq style has a sloping baseline and position-dependent collisions that 'OpenType can't
 * express,' which is why Awami Nastaliq ships only in SIL Graphite and why Simon Cozens'
 * OpenType attempt needed multiple dot-height variants, a collision detector over three-glyph
 * windows and 'half a million cases to check'." OpenType's shaping model -- the same `GSUB`/`GPOS`
 * feature pipeline [ArabicFeaturePlan] documents for Naskh -- has no mechanism for a baseline that
 * slopes glyph-to-glyph or for collision avoidance that depends on which glyphs sit in a
 * three-glyph window; Nastaliq needs both. That is a property of the OpenType format itself, not
 * an implementation gap this build could close with more engineering effort.
 *
 * [realTemplateCountInZip] and [templateFolder] document that the asset exists and is real (so a
 * future reader does not mistake this silence for Typewright's own template pack being
 * Naskh-only); [realTemplateCountInZip] is the same 49 confirmed directly against the zip listing
 * (`unzip -l scripts/templates/hyle-all-templates.zip | grep -c 'svg/Nastaliq/.*\.svg$'`) that
 * `scripts/templates/hyle-all-templates.zip`'s own `HOW_TO_USE.md` states ("Nastaliq | svg/
 * Nastaliq | 49 | same bases; only start this if Nastaliq becomes the whole point").
 */
object NastaliqOutOfScope {
    const val TEMPLATE_FOLDER = "svg/Nastaliq"

    /** Confirmed directly against the real zip listing -- see class KDoc. Matches [ArabicLetters]'s own Naskh count (49) exactly; a coincidence of the source pack, not a shared script. */
    const val REAL_TEMPLATE_COUNT_IN_ZIP = 49

    val disclosure: String =
        "Nastaliq is out of scope for v1 and the app says so (docs/TYPEWRIGHT_HANDOFF.md §4 " +
            "M8) -- a deliberate scope decision, not an oversight: scripts/templates/hyle-all-" +
            "templates.zip's own svg/Nastaliq/ folder is real and populated (49 templates, same " +
            "count as svg/Naskh/), and WritingScript has no NASTALIQ entry to build a " +
            "ScriptProfile against even if this task attempted one. The concrete reason: the " +
            "culturally expected Nastaliq style has a sloping baseline and position-dependent " +
            "letter collisions that OpenType's own shaping model cannot express -- " +
            "docs/RESEARCH_font_quality.md, quoting Simon Cozens' own Nastaliq dot-positioning " +
            "work and the SIL Awami Nastaliq FAQ: \"the culturally expected Nastaliq style has a " +
            "sloping baseline and position-dependent collisions that 'OpenType can't express,' " +
            "which is why Awami Nastaliq ships only in SIL Graphite and why Simon Cozens' " +
            "OpenType attempt needed multiple dot-height variants, a collision detector over " +
            "three-glyph windows and 'half a million cases to check'\"."
}
