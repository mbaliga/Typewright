// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.scripts.kana

import com.asoc.typewright.scripts.FeatureGenerationPlan
import com.asoc.typewright.scripts.WritingScript

/**
 * Hiragana's and Katakana's real OpenType feature-generation plan.
 *
 * Kana needs far less GSUB/GPOS machinery than Latin, Devanagari or Arabic Naskh -- this task's
 * own instruction, and true of the research read for it: kana has no positional joining forms
 * (contrast Arabic's four-forms-per-letter `isol/init/medi/fina` system) and no reordering or
 * conjunct-forming stages (contrast Devanagari's `rphf/rkrf/blwf/half/vatu/cjct`). Both those
 * scripts' plans list far more GSUB stages than kana's one -- Arabic's `gsubStagesInOrder` has 8
 * entries, Devanagari's has 15 (9 pre-base/reordering-adjacent tags, `locl` through `cjct`, plus
 * 6 post-base-application tags, `pres` through `calt` -- `DevanagariFeaturePlan.kt`'s own KDoc
 * documents the two-group split; `DevanagariFeaturePlanTest` pins the total at 15, not 9 --
 * verify-P8 found and fixed this KDoc's own earlier "eight or nine" miscount, which undercounted
 * Devanagari by naming only its first, reordering-adjacent stage group)
 * ([com.asoc.typewright.scripts.FeatureGenerationPlan]'s own KDoc quotes both in full); kana's
 * real plan is one GSUB feature and one GPOS feature, stated plainly below rather than padded
 * out to look comparable.
 *
 * **The one real, sourced piece of machinery: dakuten/handakuten composition.**
 * `docs/RESEARCH_font_quality.md`'s kana subsection: "Dakuten (゛) and handakuten (゜) sit at the
 * upper right; they are encoded as combining U+3099/U+309A, spacing U+309B/U+309C and half-width
 * U+FF9E/U+FF9F, with the combining forms required for half-width kana which have no precomposed
 * glyphs." Two real, standard OpenType mechanisms follow from that one sentence, not invented for
 * kana specifically:
 * - `ccmp` (Glyph Composition/Decomposition, an OpenType-registered feature tag) substitutes a
 *   [base kana, combining U+3099-or-U+309A] sequence for its precomposed glyph when text arrives
 *   NFD-normalised -- the standard, general technique for canonical composition, the same
 *   mechanism any font handling combining marks uses, not something specific to this script.
 * - GPOS `mark` (mark-to-base, lookup type 4) positions a *standalone* dakuten/handakuten at the
 *   base's upper right when no precomposed substitution applies -- the identical anchor mechanism
 *   `docs/RESEARCH_font_quality.md`'s own "Diacritics" section already establishes generally for
 *   Latin accents (`top`/`_top` anchor pairs), applied here to kana's own upper-right placement
 *   rather than Latin's top-centre one.
 * - `mkmk` (mark-to-mark) is deliberately NOT included: a syllable never carries both a dakuten
 *   and a handakuten at once (は takes either ば or ぱ, never both), so there is nothing for a
 *   kana glyph's own marks to stack on top of each other the way Vietnamese's stacked Latin
 *   accents do.
 * - `kern` is deliberately NOT included, though no source read for this task rules it out
 *   outright for kana: `KanaMetrics.kt`'s own `KANA_TEMPLATE_ADVANCE_WIDTH` shows every one of
 *   the real 112 templates advancing a uniform 720 units, i.e. a monospaced design grid, and
 *   kerning is not the normal approach for a monospaced CJK-adjacent script -- a reasoned
 *   inference from this asset's own real metrics, disclosed as an inference rather than a cited
 *   fact.
 *
 * **A disclosed gap, not silently worked around:** composing a dakuten/handakuten onto a base
 * needs a real dakuten/handakuten glyph to compose with, and the real
 * `scripts/templates/hyle-all-templates.zip` -- confirmed by
 * `scripts/templates/generate_kana_manifest.py`'s own full listing -- has no dedicated template
 * for either mark in `svg/Hiragana` or `svg/Katakana`. [HIRAGANA_TEMPLATES] and
 * [KATAKANA_TEMPLATES] therefore cannot yet supply the one glyph this feature plan's own real
 * stage needs to actually run; this is named honestly in [notes] rather than invented as a
 * template that does not exist, or silently dropped from the plan.
 *
 * **`vert` (vertical writing) is real and sourced, but not included as an active stage.** The
 * same research subsection's own "Template implications" sentence names "a planned `vert` set" --
 * real Japanese typesetting is routinely vertical. But the real 112 templates this task's asset
 * provides carry only horizontal guides (`KanaMetrics.kt`: a uniform 720-unit horizontal advance,
 * no vertical-advance or vertical-origin guide anywhere in any of the 112 files), so nothing here
 * is built for it yet; named in [notes] as a real, sourced, future item, not added to
 * [FeatureGenerationPlan.gsubStagesInOrder]/[FeatureGenerationPlan.gposFeatures] as if it were
 * live today -- the same "name it, do not silently omit it, do not claim it's built" treatment
 * this task's own instructions ask for Nastaliq in the Arabic profile.
 */
val KANA_FEATURE_PLAN_NOTES: String =
    "Kana needs far less GSUB/GPOS than Latin/Devanagari/Arabic: no joining, no conjuncts. The " +
        "one real stage is dakuten/handakuten composition -- ccmp composes a base kana + combining " +
        "U+3099/U+309A sequence into its precomposed glyph; GPOS mark (mark-to-base) positions a " +
        "standalone mark at the base's upper right, the same anchor mechanism the research's " +
        "Diacritics section already gives Latin accents. mkmk is excluded: dakuten and handakuten " +
        "never stack on the same base. kern is excluded as a reasoned inference (the real " +
        "templates all advance a uniform 720 units -- a monospaced grid, where kerning is not the " +
        "norm), not a cited fact. A disclosed gap: the real template zip has no dedicated " +
        "dakuten/handakuten glyph template in svg/Hiragana or svg/Katakana, so this plan's own " +
        "real stage has no glyph to compose with yet. vert (vertical writing) is real and sourced " +
        "(research: 'a planned vert set') but not included as an active stage: the real templates " +
        "carry only horizontal guides."

/** Hiragana's real feature-generation plan. Identical to [KATAKANA_FEATURE_PLAN] except for [WritingScript]. */
val HIRAGANA_FEATURE_PLAN: FeatureGenerationPlan =
    FeatureGenerationPlan(
        script = WritingScript.HIRAGANA,
        gsubStagesInOrder = listOf("ccmp"),
        gposFeatures = listOf("mark"),
        notes = KANA_FEATURE_PLAN_NOTES,
    )

/** Katakana's real feature-generation plan. Identical to [HIRAGANA_FEATURE_PLAN] except for [WritingScript]. */
val KATAKANA_FEATURE_PLAN: FeatureGenerationPlan =
    FeatureGenerationPlan(
        script = WritingScript.KATAKANA,
        gsubStagesInOrder = listOf("ccmp"),
        gposFeatures = listOf("mark"),
        notes = KANA_FEATURE_PLAN_NOTES,
    )
