package dev.aarso.typewright.scripts.arabic

import dev.aarso.typewright.scripts.ScriptProfile
import dev.aarso.typewright.scripts.WritingScript

/**
 * Arabic Naskh's [ScriptProfile], bundling [ArabicMetrics], [ArabicGlyphInventory],
 * [ArabicControlCharacters], [ArabicTemplateSheet] and [ArabicFeaturePlan] for a caller that wants
 * all of it at once ([ScriptProfile]'s own KDoc).
 *
 * `scaffold = true`: handoff M8's own ship order ([WritingScript]'s own KDoc) ships Latin, then
 * Hiragana and Katakana, then Devanagari, then Arabic Naskh, with Nastaliq explicitly out of scope
 * for v1 (see [NastaliqOutOfScope]). This profile is real, sourced data -- not a placeholder -- but
 * it has not shipped ([ScriptProfile.scaffold]'s own KDoc: "never true for a shipped script"), so
 * `scaffold` stays `true` until handoff M8 says otherwise.
 */
object ArabicScriptProfile {
    fun build(): ScriptProfile =
        ScriptProfile(
            script = WritingScript.ARABIC_NASKH,
            metrics = ArabicMetrics.build(),
            glyphInventory = ArabicGlyphInventory.build(),
            controlCharacters = ArabicControlCharacters.build(),
            templateSheet = ArabicTemplateSheet.build(),
            featurePlan = ArabicFeaturePlan.build(),
            scaffold = true,
        )
}
