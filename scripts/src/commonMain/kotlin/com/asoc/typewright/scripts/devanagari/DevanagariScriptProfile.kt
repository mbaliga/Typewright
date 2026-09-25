// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.scripts.devanagari

import com.asoc.typewright.scripts.ScriptProfile
import com.asoc.typewright.scripts.WritingScript

/**
 * Devanagari's [ScriptProfile], bundling [DevanagariMetrics], [DevanagariGlyphInventory],
 * [DevanagariControlCharacters], [DevanagariTemplateSheet] and [DevanagariFeaturePlan] for a
 * caller that wants all of it at once (`ScriptProfile`'s own KDoc).
 *
 * `scaffold = true`: handoff M8's own ship order (`WritingScript`'s own KDoc) ships Latin, then
 * Hiragana and Katakana, then Devanagari, then Arabic Naskh, with Nastaliq explicitly out of
 * scope for v1. This profile is real, sourced data -- not a placeholder -- but it has not shipped
 * (`ScriptProfile.scaffold`'s own KDoc: "never true for a shipped script"), so `scaffold` stays
 * `true` until handoff M8 says otherwise.
 */
object DevanagariScriptProfile {
    fun build(): ScriptProfile =
        ScriptProfile(
            script = WritingScript.DEVANAGARI,
            metrics = DevanagariMetrics.build(),
            glyphInventory = DevanagariGlyphInventory.build(),
            controlCharacters = DevanagariControlCharacters.build(),
            templateSheet = DevanagariTemplateSheet.build(),
            featurePlan = DevanagariFeaturePlan.build(),
            scaffold = true,
        )
}
