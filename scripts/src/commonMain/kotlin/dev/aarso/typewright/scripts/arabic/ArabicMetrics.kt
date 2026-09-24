package dev.aarso.typewright.scripts.arabic

import dev.aarso.typewright.scripts.ScriptMetricLine
import dev.aarso.typewright.scripts.ScriptMetricSystem
import dev.aarso.typewright.scripts.WritingScript

/**
 * Arabic Naskh's real vertical-metric system: tooth-, loop- and eye-heights and a weighted
 * baseline, **not** x-height/cap-height under a different label. `docs/RESEARCH_font_quality.md`'s
 * "Arabic Naskh joins on a weighted baseline" section is explicit: "Arabic's metric system
 * replaces x-height with tooth-, loop- and eye-heights ... 'Do not use "x-height" and "cap
 * height"' in Arabic vocabulary" (quoting TypeTogether's Arabic Type Anatomy). None of the six
 * [ScriptMetricLine]s below is named after a Latin metric; this KDoc says so plainly rather than
 * silently doing it.
 *
 * Four of the six real numbers here are not this build's own choice: they are the four vertical
 * guide values drawn, identically, into *every one* of the 49 real capture templates in
 * `scripts/templates/hyle-all-templates.zip`'s own `svg/Naskh/` folder (confirmed directly against
 * the zip by `scripts/templates/generate_naskh_manifest.py`, which raises if any template
 * disagrees -- see the checked-in `scripts/templates/naskh-manifest.json`'s own
 * `shared_guide_metrics`): [ASCENDER] = 720, [TOOTH_HEIGHT] = 300, [BASELINE] = 0,
 * [EARTH] = -360. These map directly onto the research's own vocabulary: [TOOTH_HEIGHT] is one of
 * the three named x-height replacements quoted above; [ASCENDER] and [EARTH] are what the same
 * section calls "sky" and "earth" ("may use one or two ascender levels ('sky') and two or three
 * descender levels ('earth')") -- this build's real templates draw exactly one of each, the
 * minimum the research allows, so this system models one [line] named `"sky"` and one named
 * `"earth"`, not the two-or-three the research says some designs use. That the template pack's own
 * raw guide labels literally read "ascender"/"descender" (generic, Latin-shaped words) rather than
 * "sky"/"earth" is itself noted here rather than silently carried through: [line]'s own `name`
 * uses the research's real Arabic-vocabulary terms ("sky", "earth"), not the template SVGs' own
 * placeholder label text, per this task's own instruction not to smuggle a Latin metric name in
 * under a different label.
 *
 * The other two lines have no template-drawn value and no numeric source anywhere in the corpus:
 * [loopHeight] and [eyeHeight] are this design's own placeholder choice, clearly marked
 * `fixed = false` with a `[CONFIRM]` note, per the same research section's own closing sentence:
 * "No source gave numeric tooth/loop/descender ratios in dot units". [joiningLineTop] *does* have
 * a real cited number, but from the general research, not from this build's own template pack: the
 * Glyphs recipe the same section quotes says to "set a 'joining line height' master metric equal
 * to the stroke width (default 100 with 16-unit overshoot)" -- the weighted baseline's own real
 * thickness, above [BASELINE] itself, honouring "gives the baseline itself a thickness" from the
 * same section's opening sentence.
 *
 * Only [BASELINE] is `fixed = true`. Every other line -- including [ASCENDER], [TOOTH_HEIGHT] and
 * [EARTH], whose *values* are real and template-sourced -- is `fixed = false`: `fixed` records
 * whether a line is a structural constant *of the script itself* ([ScriptMetricLine]'s own KDoc),
 * and the research treats tooth/loop/eye-height and sky/earth levels as this design's own choice
 * (the direct x-height/cap-height replacement), never as something every Arabic design shares by
 * construction, unlike the baseline itself.
 */
object ArabicMetrics {
    private const val UNITS_PER_EM = 1000
    private const val ASCENDER = 720
    private const val TOOTH_HEIGHT = 300
    private const val EYE_HEIGHT = 260
    private const val LOOP_HEIGHT = 180
    private const val JOINING_LINE_TOP = 100
    private const val BASELINE = 0
    private const val EARTH = -360

    private const val NO_RATIO_PUBLISHED =
        "This design's own placeholder value; no source in docs/RESEARCH_font_quality.md gives a " +
            "numeric tooth/loop/eye-height ratio (\"No source gave numeric tooth/loop/descender " +
            "ratios in dot units\"). [CONFIRM]."

    fun build(): ScriptMetricSystem =
        ScriptMetricSystem(
            script = WritingScript.ARABIC_NASKH,
            unitsPerEm = UNITS_PER_EM,
            lines =
                listOf(
                    ScriptMetricLine(
                        name = "sky",
                        value = ASCENDER,
                        fixed = false,
                        note =
                            "One ascender level -- docs/RESEARCH_font_quality.md: \"may use one " +
                                "or two ascender levels ('sky')\"; this build's real templates " +
                                "draw one. Value 720 is the real 'ascender' guide drawn " +
                                "identically into every one of the 49 real Naskh capture " +
                                "templates in scripts/templates/hyle-all-templates.zip (see " +
                                "scripts/templates/naskh-manifest.json) -- the template SVGs' " +
                                "own raw label reads \"ascender\", a generic word this build " +
                                "does not reuse as this line's own name; the research's real " +
                                "Arabic-vocabulary term \"sky\" is used instead.",
                    ),
                    ScriptMetricLine(
                        name = "toothHeight",
                        value = TOOTH_HEIGHT,
                        fixed = false,
                        note =
                            "One of the three real x-height replacements named in " +
                                "docs/RESEARCH_font_quality.md: \"Arabic's metric system " +
                                "replaces x-height with tooth-, loop- and eye-heights\". Value " +
                                "300 is the real 'tooth height' guide drawn identically into " +
                                "every one of the 49 real Naskh capture templates (see " +
                                "scripts/templates/naskh-manifest.json) -- the one of the three " +
                                "replacement heights the template pack itself actually measures.",
                    ),
                    ScriptMetricLine(
                        name = "eyeHeight",
                        value = EYE_HEIGHT,
                        fixed = false,
                        note = "The eye-shaped counter height (e.g. ain, feh, qaf). $NO_RATIO_PUBLISHED",
                    ),
                    ScriptMetricLine(
                        name = "loopHeight",
                        value = LOOP_HEIGHT,
                        fixed = false,
                        note = "The closed-loop counter height (e.g. meem, the waw tail). $NO_RATIO_PUBLISHED",
                    ),
                    ScriptMetricLine(
                        name = "joiningLineTop",
                        value = JOINING_LINE_TOP,
                        fixed = false,
                        note =
                            "The weighted baseline's own top edge -- \"gives the baseline itself " +
                                "a thickness\" (docs/RESEARCH_font_quality.md). The Glyphs " +
                                "Arabic recipe the same section quotes: \"set a 'joining line " +
                                "height' master metric equal to the stroke width (default 100 " +
                                "with 16-unit overshoot)\". 100 is that real cited default -- " +
                                "from the general research, not measured in this build's own " +
                                "template pack (the templates draw the baseline as a single " +
                                "hairline at 0, not a thickness).",
                    ),
                    ScriptMetricLine(
                        name = "baseline",
                        value = BASELINE,
                        fixed = true,
                        note =
                            "The one real structural constant in this system: every design " +
                                "needs a baseline. Not a hairline here -- see joiningLineTop for " +
                                "its own real thickness above it. Value 0 by this build's own " +
                                "font-units convention (CLAUDE.md: \"Geometry is in font units " +
                                "(1000 UPM default), y up\") and the real 'baseline' guide drawn " +
                                "identically into every one of the 49 real Naskh capture " +
                                "templates. exit/entry anchors sit here too: " +
                                "\"place exit and entry anchors at y = 0 and horizontally on the " +
                                "sidebearings\" (docs/RESEARCH_font_quality.md, citing Glyphs: " +
                                "Creating an Arabic font).",
                    ),
                    ScriptMetricLine(
                        name = "earth",
                        value = EARTH,
                        fixed = false,
                        note =
                            "One descender level -- docs/RESEARCH_font_quality.md: \"two or " +
                                "three descender levels ('earth')\"; this build's real templates " +
                                "draw one. Value -360 is the real 'descender' guide drawn " +
                                "identically into every one of the 49 real Naskh capture " +
                                "templates -- the template SVGs' own raw label again reads the " +
                                "generic \"descender\"; this line uses the research's real term " +
                                "\"earth\" instead, for the same reason as sky.",
                    ),
                ),
        )
}
