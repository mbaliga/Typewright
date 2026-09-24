package dev.aarso.typewright.scripts.devanagari

import dev.aarso.typewright.scripts.FeatureGenerationPlan
import dev.aarso.typewright.scripts.WritingScript

/**
 * Devanagari's real, documented Indic GSUB/GPOS stage order.
 *
 * `docs/RESEARCH_font_quality.md`'s Devanagari section states it plainly, citing Microsoft's
 * Devanagari spec and n8willis's opentype-shaping-documents: "GSUB features apply in the order
 * `locl, nukt, akhn, rphf, rkrf, blwf, half, vatu, cjct` then `pres, abvs, blws, psts, haln,
 * calt`, GPOS `kern/dist, abvm, blwm`". [gsubStagesInOrder] preserves that split as two ordered
 * groups -- the pre-base/reordering group first, the post-base-application group second -- with
 * the same real tag order inside each, and [gposFeatures] carries the three GPOS tags in the
 * same real order.
 *
 * The same research section discloses one real ambiguity rather than picking a side: "Sources
 * differ slightly on whether `pstf` appears in the Devanagari stage list (Microsoft omits it;
 * n8willis includes it); treat it as present but rarely used." This build follows that
 * instruction exactly -- `pstf` (post-base form) is **not** added to [gsubStagesInOrder] (this
 * data keeps to the shorter, Microsoft-agreeing list actually quoted above as "the order"), and
 * [notes] states the ambiguity and the "present but rarely used" reading in full, so a later
 * reader of this plan is not left thinking the omission was silent or the list settled where the
 * sources are not.
 *
 * The font itself must supply reph, rakaar, half forms, below-base Ra, and the two akhand
 * ligatures Ka+Ssa and Ja+Nya (same research section); required control characters are U+25CC
 * (dotted circle), U+200C (ZWNJ), U+200D (ZWJ) and U+200B (ZWSP). And per the Microsoft spec's
 * own warning, quoted directly: "OpenType fonts should not have substitutions that attempt to
 * perform the re-ordering" -- the shaping engine reorders matras and reph before any lookup
 * runs; this plan states feature order only, never a reordering substitution.
 */
object DevanagariFeaturePlan {
    /**
     * Two real, ordered stages from the same quoted sentence: the pre-base/reordering group
     * (`locl` through `cjct`) runs first, then the post-base-application group (`pres` through
     * `calt`). Kept as one flat, already-ordered list -- [FeatureGenerationPlan.gsubStagesInOrder]
     * is `List<String>`, not two lists -- with the stage boundary documented in [notes] rather
     * than invented as a structural split the shared model has no field for.
     */
    val gsubStagesInOrder: List<String> =
        listOf(
            // Stage group 1: pre-base substitution and reordering-adjacent features.
            "locl",
            "nukt",
            "akhn",
            "rphf",
            "rkrf",
            "blwf",
            "half",
            "vatu",
            "cjct",
            // Stage group 2: post-base-application features.
            "pres",
            "abvs",
            "blws",
            "psts",
            "haln",
            "calt",
        )

    val gposFeatures: List<String> = listOf("kern/dist", "abvm", "blwm")

    val notes: String =
        "GSUB order from docs/RESEARCH_font_quality.md's Devanagari section (citing Microsoft's " +
            "Devanagari spec and n8willis's opentype-shaping-documents): \"locl, nukt, akhn, " +
            "rphf, rkrf, blwf, half, vatu, cjct\" (pre-base / reordering-adjacent group) then " +
            "\"pres, abvs, blws, psts, haln, calt\" (post-base-application group). GPOS: " +
            "\"kern/dist, abvm, blwm\". Disclosed ambiguity, not silently resolved: \"Sources " +
            "differ slightly on whether pstf appears in the Devanagari stage list (Microsoft " +
            "omits it; n8willis includes it); treat it as present but rarely used.\" This plan " +
            "does not add pstf to gsubStagesInOrder -- it follows the shorter, Microsoft-side " +
            "list actually quoted as \"the order\" -- and states that choice here rather than " +
            "presenting either source's list as the single settled answer. The engine reorders " +
            "matras and reph before lookups run: \"OpenType fonts should not have substitutions " +
            "that attempt to perform the re-ordering\" (Microsoft spec). Required control " +
            "characters: U+25CC (dotted circle), U+200C (ZWNJ), U+200D (ZWJ), U+200B (ZWSP). " +
            "The font must supply reph, rakaar, half forms, below-base Ra, and the akhand " +
            "ligatures Ka+Ssa (ka_ssa-deva) and Ja+Nya (ja_nya-deva)."

    fun build(): FeatureGenerationPlan =
        FeatureGenerationPlan(
            script = WritingScript.DEVANAGARI,
            gsubStagesInOrder = gsubStagesInOrder,
            gposFeatures = gposFeatures,
            notes = notes,
        )
}
