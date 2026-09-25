// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.scripts.kana

import com.asoc.typewright.scripts.ScriptMetricLine
import com.asoc.typewright.scripts.ScriptMetricSystem
import com.asoc.typewright.scripts.WritingScript

/*
 * Hiragana and Katakana's real metric system: the "virtual body" / optical-centre model
 * `docs/RESEARCH_font_quality.md`'s "Kana are centred in a virtual body, not seated on a
 * baseline" subsection and `docs/LESSONS_SCAFFOLD.md` section 5's kana bullet both name, not a
 * Latin-style baseline+x-height system. Both scripts share one [ScriptMetricSystem] (this task's
 * own instruction -- "Hiragana and Katakana ... share one metric system per the research" --
 * matches the research itself, which never gives Hiragana and Katakana separate metrics).
 *
 * Grounded in the real asset, not invented: `scripts/templates/hyle-all-templates.zip`'s own
 * `svg/Hiragana` and `svg/Katakana` templates (all 55 + 57 of them, confirmed by
 * `scripts/templates/generate_kana_manifest.py`, which refuses to write a manifest at all if a
 * single template's guides disagree) draw four identical horizontal guide lines and two identical
 * vertical advance-width guides in *every* file -- body top 880, body bottom -120, virtual-body
 * centre 380, baseline 0, and a uniform advance width of 720 -- matching the pack's own
 * `HOW_TO_USE.md` prose: "Hiragana and Katakana: design to an optical centre, not a baseline. The
 * glyph fills a near-square body from about -120 to 880."
 */

/**
 * No source read for this task states a units-per-em value for kana. 1000 is used here because
 * the virtual body's own span (880 - (-120) = 1000) matches it exactly, and because CLAUDE.md's
 * own convention states "1000 UPM default" for this build's geometry generally -- an inference
 * stated honestly, not a value `HOW_TO_USE.md` or the research names as "units per em".
 */
const val KANA_UNITS_PER_EM: Int = 1000

/**
 * Every one of the 112 real templates (55 Hiragana + 57 Katakana) draws its two blue vertical
 * guide lines at x=0 and x=720: every kana glyph in this template pack advances a uniform 720
 * units. This is a real, measured fact of the asset (confirmed by
 * `scripts/templates/generate_kana_manifest.py`), not a script-wide constant any source read for
 * this task publishes -- kana are commonly, but not universally, drawn as fixed-width in real
 * type design, and no source read here states a number either way. There is no field on
 * [ScriptMetricLine]/[ScriptMetricSystem] for a horizontal advance (that type models named
 * *vertical* reference lines only, per its own KDoc), so this is a plain constant beside it rather
 * than forced into the vertical-line model.
 */
const val KANA_TEMPLATE_ADVANCE_WIDTH: Int = 720

private val KANA_METRIC_LINES: List<ScriptMetricLine> =
    listOf(
        ScriptMetricLine(
            name = "baseline",
            value = 0,
            fixed = true,
            note =
                "The universal glyph-origin / advance-width reference every OpenType font measures " +
                    "from, kept here for cross-script consistency with Latin, Devanagari and Arabic -- not " +
                    "where a kana glyph is visually seated. The research's own subsection title states this " +
                    "plainly: kana are 'centred in a virtual body, not seated on a baseline'.",
        ),
        ScriptMetricLine(
            name = "virtual body bottom",
            value = -120,
            fixed = false,
            note =
                "This template pack's own concrete choice (HOW_TO_USE.md: 'a near-square body from " +
                    "about -120 to 880'), confirmed identical across all 112 real templates by " +
                    "generate_kana_manifest.py. Marked NOT fixed: the research states plainly that how much " +
                    "smaller a kana letter face sits inside its virtual body 'varies by design and for which " +
                    "no percentage is published', so this is this asset's own design decision, not a " +
                    "structural constant of the script the way Devanagari's headline and baseline are fixed.",
        ),
        ScriptMetricLine(
            name = "virtual body top",
            value = 880,
            fixed = false,
            note =
                "See 'virtual body bottom' -- same source (this template pack's HOW_TO_USE.md and its " +
                    "112 templates' own guides), same caveat (varies by design; no published percentage). " +
                    "Body height 880 - (-120) = 1000, a full em at this asset's own inferred KANA_UNITS_PER_EM.",
        ),
        ScriptMetricLine(
            name = "virtual body centre",
            value = 380,
            fixed = false,
            note =
                "The optical-centre line the research names (kana are 'centred', not seated) -- the " +
                    "exact midpoint of this asset's own body top/bottom, (880 + -120) / 2 = 380, not the " +
                    "em's own midpoint (500), because the virtual body itself sits asymmetrically around the " +
                    "baseline. This is this design's own visual-centre line, standing in for the x-height / " +
                    "cap-height pair Latin has and kana structurally does not.",
        ),
    )

/** Hiragana's real metric system. Identical to [KATAKANA_METRICS] except for [WritingScript]. */
val HIRAGANA_METRICS: ScriptMetricSystem =
    ScriptMetricSystem(
        script = WritingScript.HIRAGANA,
        unitsPerEm = KANA_UNITS_PER_EM,
        lines = KANA_METRIC_LINES,
    )

/** Katakana's real metric system. Identical to [HIRAGANA_METRICS] except for [WritingScript]. */
val KATAKANA_METRICS: ScriptMetricSystem =
    ScriptMetricSystem(
        script = WritingScript.KATAKANA,
        unitsPerEm = KANA_UNITS_PER_EM,
        lines = KANA_METRIC_LINES,
    )
