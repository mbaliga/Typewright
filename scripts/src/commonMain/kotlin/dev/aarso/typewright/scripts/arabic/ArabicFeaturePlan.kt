// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.scripts.arabic

import dev.aarso.typewright.scripts.FeatureGenerationPlan
import dev.aarso.typewright.scripts.WritingScript

/**
 * Arabic Naskh's real, documented joining-feature model.
 *
 * `docs/RESEARCH_font_quality.md`'s Arabic section states it plainly, citing Microsoft's Arabic
 * spec and n8willis's opentype-shaping-documents: "The feature model is fixed: GSUB `ccmp, isol,
 * fina, medi, init, rlig, rclt, calt` always applied, `liga` on by default, GPOS `curs, kern,
 * mark, mkmk`". [gsubStagesInOrder] carries the eight *always-applied* tags in that exact order.
 *
 * `liga` is deliberately **not** folded into [gsubStagesInOrder]: the research's own sentence
 * draws a real distinction between the eight tags that are "always applied" and `liga`, which is
 * merely "on by default" (an optional feature a user or app could still turn off) -- a different
 * status, not a rounding error. Rather than silently blurring that distinction the way it would if
 * `liga` were appended to the same flat "always applied" list, this plan states it separately, in
 * [notes], mirroring how
 * `dev.aarso.typewright.scripts.devanagari.DevanagariFeaturePlan` discloses its own real,
 * documented ambiguity (`pstf`) in notes rather than silently resolving it inside the ordered
 * list.
 *
 * Two real constraints from the same research section are carried in [notes] rather than modelled
 * structurally, since [FeatureGenerationPlan] has no field for either: `rlig` "must contain
 * lam-alef" (the mandatory lam+alef ligature every Arabic font needs, regardless of what other
 * ligatures `rlig` carries), and mark classes are validated so that "above-marks (DIAC1) and
 * below-marks (DIAC2) of the same class are never doubled on a base" -- i.e. a base glyph never
 * carries two above-marks or two below-marks from the same `mark`/`mkmk` class at once.
 */
object ArabicFeaturePlan {
    /** The eight real, always-applied GSUB tags, in the documented order. `liga` is not here -- see class KDoc. */
    val gsubStagesInOrder: List<String> =
        listOf("ccmp", "isol", "fina", "medi", "init", "rlig", "rclt", "calt")

    val gposFeatures: List<String> = listOf("curs", "kern", "mark", "mkmk")

    val notes: String =
        "GSUB/GPOS from docs/RESEARCH_font_quality.md's Arabic section (citing Microsoft's " +
            "Arabic spec and n8willis's opentype-shaping-documents): \"GSUB ccmp, isol, fina, " +
            "medi, init, rlig, rclt, calt always applied, liga on by default, GPOS curs, kern, " +
            "mark, mkmk\". liga is deliberately left out of gsubStagesInOrder: it is \"on by " +
            "default\", a different (overridable) status from the other eight tags' \"always " +
            "applied\" -- see this file's own class KDoc. Two real constraints this plan's own " +
            "fields have no place for: rlig must contain the mandatory lam-alef ligature; mark " +
            "classes are validated so that above-marks (DIAC1) and below-marks (DIAC2) of the " +
            "same class are never doubled on one base. Anchors: exit/entry sit at y=0 on the " +
            "sidebearings (docs/RESEARCH_font_quality.md, citing Glyphs: Creating an Arabic " +
            "font) -- see ArabicMetrics's own baseline note. Every base glyph needs top/bottom " +
            "mark-attachment anchors for its own diacritics, and connector stroke ends should " +
            "click into each other with zero sidebearing rather than merely overlapping, per the " +
            "same Glyphs recipe."

    fun build(): FeatureGenerationPlan =
        FeatureGenerationPlan(
            script = WritingScript.ARABIC_NASKH,
            gsubStagesInOrder = gsubStagesInOrder,
            gposFeatures = gposFeatures,
            notes = notes,
        )
}
