package dev.aarso.typewright.scripts

/**
 * The scripts this module has (or will have) real per-script data for, in handoff M8's own ship
 * order (Latin already shipped by earlier tasks; Hiragana and Katakana next, then Devanagari, then
 * Arabic Naskh; Nastaliq is explicitly out of scope for v1 -- M8's own words, "the app says so").
 * A shared, stable enum so every per-script data file (metric system, glyph inventory, control
 * characters, template sheet, feature-generation plan) keys on the same identity rather than each
 * inventing its own.
 */
enum class WritingScript(
    /** ISO 15924 four-letter script code, e.g. for [dev.aarso.typewright.shape.preview.ShapeRequest.script]. */
    val isoCode: String,
    val displayName: String,
    val rightToLeft: Boolean = false,
) {
    LATIN("Latn", "Latin"),
    HIRAGANA("Hira", "Hiragana"),
    KATAKANA("Kana", "Katakana"),
    DEVANAGARI("Deva", "Devanagari"),
    ARABIC_NASKH("Arab", "Arabic (Naskh)", rightToLeft = true),
}

/**
 * One named vertical (or, for a script with no single baseline concept, structurally equivalent)
 * reference line or level for a script, in font units. Scripts differ in how many of these are
 * fixed versus variable per design -- see [fixed] -- so this is deliberately a flat list rather
 * than a fixed set of named fields every script must fill in the same way.
 */
data class ScriptMetricLine(
    val name: String,
    val value: Int,
    /** True when this line's value is a structural constant of the script (e.g. Devanagari's own baseline and headline); false when it is this design's own choice (e.g. its x-height-equivalent, cap-height-equivalent). */
    val fixed: Boolean,
    val note: String? = null,
)

/** A script's own vertical-metric system: its [lines] plus whether it reads right to left. */
data class ScriptMetricSystem(
    val script: WritingScript,
    val unitsPerEm: Int,
    val lines: List<ScriptMetricLine>,
) {
    val rightToLeft: Boolean get() = script.rightToLeft
}

/**
 * One glyph a script's real inventory needs, correctly named per this build's own naming
 * convention (handoff M8 / this task's own instructions): Devanagari glyphs end `-deva`; Arabic
 * glyphs end `-ar` and a positional form adds `.init`/`.medi`/`.fina` (an isolated form carries no
 * suffix); kana glyphs end `-hira`/`-kata`. [unicodeName] is the real Unicode character name (e.g.
 * "DEVANAGARI LETTER KA"), used to tie a glyph back to its real template SVG in
 * `scripts/templates` (see [TemplateSheetEntry]) -- null for a glyph with no single owning
 * codepoint (e.g. a positional variant, a ligature). [codepoint] is that glyph's primary Unicode
 * scalar value, null for the same reason.
 */
data class GlyphSpec(
    val name: String,
    val unicodeName: String?,
    val codepoint: Int?,
)

/** A script's real glyph inventory. */
data class GlyphInventory(
    val script: WritingScript,
    val glyphs: List<GlyphSpec>,
)

/** One control character (or short control string) this script's campaign/proofing content uses, with why. */
data class ControlCharacter(
    val glyphName: String,
    val rationale: String,
)

/** A script's own control-character set for the campaign (handoff M5/M8) -- this script's own equivalent of Latin's n/o/H/O. */
data class ControlCharacterSet(
    val script: WritingScript,
    val controlCharacters: List<ControlCharacter>,
)

/**
 * One entry tying a real glyph to its real capture-template asset in
 * `scripts/templates/hyle-all-templates.zip` (`svg/<ScriptFolder>/<templateFileName>`, e.g.
 * `svg/Devanagari/013_DEVANAGARI_LETTER_KA.svg`). [templateFileName] is the exact filename inside
 * that zip's own per-script folder -- read the zip's own `HOW_TO_USE.md` and folder listing
 * directly, do not guess a name.
 */
data class TemplateSheetEntry(
    val glyphName: String,
    val templateFolder: String,
    val templateFileName: String,
)

/** A script's real template-sheet coverage: which of [GlyphInventory]'s glyphs have a real capture template, and which file. */
data class TemplateSheet(
    val script: WritingScript,
    val entries: List<TemplateSheetEntry>,
)

/**
 * A script's real OpenType feature-generation plan: the GSUB/GPOS feature tags this build's own
 * eventual compiler step must apply, in the documented, shaping-correct order (brief and
 * docs/RESEARCH_font_quality.md's own multi-script section give the real, sourced order per
 * script -- Indic's `locl, nukt, akhn, rphf, rkrf, blwf, half, vatu, cjct` then
 * `pres, abvs, blws, psts, haln, calt`; Arabic's `ccmp, isol, fina, medi, init, rlig, rclt, calt`
 * with `liga` on by default). This module states the plan as data; it does not itself run a
 * compiler or generate real `.fea` text -- that is `core-font`'s / a later task's concern.
 */
data class FeatureGenerationPlan(
    val script: WritingScript,
    val gsubStagesInOrder: List<String>,
    val gposFeatures: List<String>,
    val notes: String,
)

/** Everything this module has for one script, bundled for a caller that wants it all at once. */
data class ScriptProfile(
    val script: WritingScript,
    val metrics: ScriptMetricSystem,
    val glyphInventory: GlyphInventory,
    val controlCharacters: ControlCharacterSet,
    val templateSheet: TemplateSheet,
    val featurePlan: FeatureGenerationPlan,
    /** True while this script's own data is still being built out; never true for a shipped script (handoff M8's ship order). */
    val scaffold: Boolean,
)
