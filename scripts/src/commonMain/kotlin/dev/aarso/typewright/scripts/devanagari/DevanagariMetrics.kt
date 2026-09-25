// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.scripts.devanagari

import dev.aarso.typewright.scripts.ScriptMetricLine
import dev.aarso.typewright.scripts.ScriptMetricSystem
import dev.aarso.typewright.scripts.WritingScript

/**
 * Devanagari's real seven-level vertical-metric system.
 *
 * The seven names and their top-to-bottom order come straight from
 * `docs/RESEARCH_font_quality.md`'s "Devanagari hangs from the headline" section, which cites
 * TypeTogether's Devanagari Type Anatomy: "Devanagari has seven named vertical levels
 * (urdhvarekha, shirorekha or headline, skandharekha, nabhirekha, zanurekha, padrekha,
 * talrekha), of which only headline and baseline are fixed". `docs/LESSONS_SCAFFOLD.md` section
 * 5 restates the same fact in fewer words: "seven vertical levels, two fixed (headline,
 * baseline); letters hang from the shirorekha; negative sidebearings join the headline". This
 * data treats [shirorekha] as the headline and [talrekha] ("sole line") as the baseline -- the
 * name that reads as the bottommost of the seven and the only other one either source calls
 * fixed; no source in the corpus spells out the headline/baseline correspondence more directly
 * than that, so this mapping is this build's own reading of the ordered list, not a quoted fact.
 *
 * Devanagari has no x-height ("there is no x-height, matras extend above and below and are not
 * called ascenders or descenders" -- same section) and no source gives a numeric value for the
 * five levels between the two fixed ones; harmonising with Latin has "two documented options,
 * Latin cap height comparable to upper-matra height, or Latin x-height comparable to the
 * headline, with no percentage given". The one real number anywhere in this system is
 * [HEADLINE]: `scripts/templates/hyle-all-templates.zip`'s own `HOW_TO_USE.md` draws the capture
 * templates' headline guide at 700 ("the shirorekha, the top headline at 700, connects most
 * letters"). [BASELINE] is 0 by this build's own font-units convention (`CLAUDE.md`
 * "Geometry is in font units (1000 UPM default), y up"). The five levels between them
 * ([skandharekha], [nabhirekha], [zanurekha], [padrekha]) and the one above the headline
 * ([urdhvarekha]) are this design's own even division of the two fixed lines' 700-unit span --
 * every one of them carries `fixed = false` and a note saying so plainly, per
 * `docs/RESEARCH_font_quality.md`'s own instruction: "Typewright should present templates with
 * [CONFIRM] placeholders for ratios rather than invent them."
 */
object DevanagariMetrics {
    private const val UNITS_PER_EM = 1000
    private const val URDHVAREKHA = 900
    private const val HEADLINE = 700
    private const val SKANDHAREKHA = 560
    private const val NABHIREKHA = 420
    private const val ZANUREKHA = 280
    private const val PADREKHA = 140
    private const val BASELINE = 0

    private const val NO_RATIO_PUBLISHED =
        "This design's own even division of the 700-unit span between the two fixed lines " +
            "(shirorekha 700, talrekha 0); no source in docs/RESEARCH_font_quality.md gives a " +
            "numeric value for this level. [CONFIRM]."

    fun build(): ScriptMetricSystem =
        ScriptMetricSystem(
            script = WritingScript.DEVANAGARI,
            unitsPerEm = UNITS_PER_EM,
            lines =
                listOf(
                    ScriptMetricLine(
                        name = "urdhvarekha",
                        value = URDHVAREKHA,
                        fixed = false,
                        note =
                            "Upper level, above the headline, where upper matras " +
                                "(aaMatra-deva and the other upper-matra length variants) and " +
                                "ascending conjunct parts (e.g. reph) reach. Not fixed: " +
                                "docs/RESEARCH_font_quality.md says matras \"extend above and " +
                                "below and are not called ascenders or descenders\", and gives " +
                                "no numeric value for this level. $NO_RATIO_PUBLISHED",
                    ),
                    ScriptMetricLine(
                        name = "shirorekha",
                        value = HEADLINE,
                        fixed = true,
                        note =
                            "The headline. Fixed -- the one line every source in the corpus " +
                                "treats as structural, not chosen: docs/LESSONS_SCAFFOLD.md, " +
                                "\"letters hang from the shirorekha\"; sidebearings go negative " +
                                "so glyphs overlap it (Glyphs: Creating a Devanagari font, cited " +
                                "in docs/RESEARCH_font_quality.md). Value 700 is the real " +
                                "headline guide drawn into every Devanagari capture template in " +
                                "scripts/templates/hyle-all-templates.zip -- its own " +
                                "HOW_TO_USE.md: \"the shirorekha, the top headline at 700, " +
                                "connects most letters\".",
                    ),
                    ScriptMetricLine(
                        name = "skandharekha",
                        value = SKANDHAREKHA,
                        fixed = false,
                        note = "Shoulder line, below the headline. $NO_RATIO_PUBLISHED",
                    ),
                    ScriptMetricLine(
                        name = "nabhirekha",
                        value = NABHIREKHA,
                        fixed = false,
                        note =
                            "Navel line, the middle of the five design-chosen levels. Not an " +
                                "x-height -- Devanagari has none (\"there is no x-height\", " +
                                "docs/RESEARCH_font_quality.md). $NO_RATIO_PUBLISHED",
                    ),
                    ScriptMetricLine(
                        name = "zanurekha",
                        value = ZANUREKHA,
                        fixed = false,
                        note = "Knee line. $NO_RATIO_PUBLISHED",
                    ),
                    ScriptMetricLine(
                        name = "padrekha",
                        value = PADREKHA,
                        fixed = false,
                        note = "Foot line, just above the baseline. $NO_RATIO_PUBLISHED",
                    ),
                    ScriptMetricLine(
                        name = "talrekha",
                        value = BASELINE,
                        fixed = true,
                        note =
                            "The baseline (\"sole line\"). Fixed, alongside shirorekha -- " +
                                "docs/LESSONS_SCAFFOLD.md: \"seven vertical levels, two fixed " +
                                "(headline, baseline)\". TypeTogether's own caveat still " +
                                "applies to every glyph drawn against it: \"there is not and " +
                                "cannot be a prescriptive model for where every part touches a " +
                                "line\" -- not every glyph's foot literally sits here.",
                    ),
                ),
        )
}
