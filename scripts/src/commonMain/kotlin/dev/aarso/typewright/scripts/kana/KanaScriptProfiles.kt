// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.scripts.kana

import dev.aarso.typewright.scripts.ScriptProfile
import dev.aarso.typewright.scripts.WritingScript

/**
 * Hiragana's and Katakana's real [ScriptProfile]s: this package's own metric system
 * ([HIRAGANA_METRICS]/[KATAKANA_METRICS]), glyph inventory ([HIRAGANA_GLYPH_INVENTORY]/
 * [KATAKANA_GLYPH_INVENTORY]), control-character set ([HIRAGANA_CONTROL_CHARACTERS]/
 * [KATAKANA_CONTROL_CHARACTERS], both real and honestly empty -- see that file's own KDoc),
 * template sheet ([HIRAGANA_TEMPLATE_SHEET]/[KATAKANA_TEMPLATE_SHEET]) and feature-generation
 * plan ([HIRAGANA_FEATURE_PLAN]/[KATAKANA_FEATURE_PLAN]), bundled per
 * [dev.aarso.typewright.scripts.ScriptProfile]'s own KDoc ("Everything this module has for one
 * script, bundled for a caller that wants it all at once").
 *
 * `scaffold = true` on both: this is new, unshipped data (handoff M8's ship order names
 * Hiragana and Katakana next after Latin, not yet shipped), matching
 * [ScriptProfile.scaffold]'s own KDoc ("never true for a shipped script").
 */
val HIRAGANA_SCRIPT_PROFILE: ScriptProfile =
    ScriptProfile(
        script = WritingScript.HIRAGANA,
        metrics = HIRAGANA_METRICS,
        glyphInventory = HIRAGANA_GLYPH_INVENTORY,
        controlCharacters = HIRAGANA_CONTROL_CHARACTERS,
        templateSheet = HIRAGANA_TEMPLATE_SHEET,
        featurePlan = HIRAGANA_FEATURE_PLAN,
        scaffold = true,
    )

val KATAKANA_SCRIPT_PROFILE: ScriptProfile =
    ScriptProfile(
        script = WritingScript.KATAKANA,
        metrics = KATAKANA_METRICS,
        glyphInventory = KATAKANA_GLYPH_INVENTORY,
        controlCharacters = KATAKANA_CONTROL_CHARACTERS,
        templateSheet = KATAKANA_TEMPLATE_SHEET,
        featurePlan = KATAKANA_FEATURE_PLAN,
        scaffold = true,
    )
