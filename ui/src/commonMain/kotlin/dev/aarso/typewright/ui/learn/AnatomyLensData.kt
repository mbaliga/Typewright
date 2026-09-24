package dev.aarso.typewright.ui.learn

import dev.aarso.typewright.core.font.sfnt.SfntFont
import dev.aarso.typewright.core.geometry.Glyph
import dev.aarso.typewright.learn.scenes.STYLE_FEATURE_VOCABULARY
import dev.aarso.typewright.qa.corpus.style.Storeys
import dev.aarso.typewright.qa.corpus.style.TerminalStyle
import dev.aarso.typewright.qa.corpus.style.apertureOpenness
import dev.aarso.typewright.qa.corpus.style.contrastRatio
import dev.aarso.typewright.qa.corpus.style.inkBounds
import dev.aarso.typewright.qa.corpus.style.outerContour
import dev.aarso.typewright.qa.corpus.style.serifMetrics
import dev.aarso.typewright.qa.corpus.style.storeysFromA
import dev.aarso.typewright.qa.corpus.style.storeysFromG
import dev.aarso.typewright.qa.corpus.style.stressAngleDegrees
import dev.aarso.typewright.qa.corpus.style.superellipseExponent
import dev.aarso.typewright.qa.corpus.style.terminalStyle
import dev.aarso.typewright.qa.corpus.style.xHeightToCapHeightRatio
import kotlin.math.roundToInt

/**
 * The Learn screen's Anatomy Lens tab (TYPEWRIGHT_BUILD_BRIEF.md section 9, strand 2;
 * `ui/typewright-explorer.html`'s `#ln-lens` tab; docs/LESSONS_SCAFFOLD.md section 3), wired to
 * real measurements rather than invented placeholder numbers (CLAUDE.md law 5): "any number the
 * app judges a user by comes from `qa/corpus`... if a check has no measurement behind it, its UI
 * says 'our heuristic'".
 *
 * **What "the user's own letter" means here.** There is no real "current project" concept wired
 * into `ui` yet (every prior P4/P5b task's own disclosed scope), so this file is built and tested
 * against this build's own real test-fixture font, `fonts/HyleDeco-Regular.ttf`, read through
 * `core-font`'s [SfntFont] -- stated plainly as this task's own necessary substitute for "the
 * user's own letter" until a real project-loading flow exists (docs/OPEN_QUESTIONS.md). Nothing
 * about the API below is Hyle-Deco-specific: [AnatomyLensGlyphSet.fromSfntFont] and every function
 * in this file take any [SfntFont]/[Glyph], real or drawn.
 *
 * **The module-visibility fix this file needed.** `qa:corpus`'s style package (built task P1b:
 * `ContrastStress.kt`, `SerifBracket.kt`, `Storeys.kt`, `Terminal.kt`, `Aperture.kt`,
 * `Roundness.kt`, `Proportions.kt`) was entirely `internal` before this task -- every function,
 * every result type, with zero consumers anywhere outside `:qa:corpus` itself (confirmed by
 * grepping the whole repository before writing this file). Kotlin's `internal` is a *module*
 * boundary, not a file one, so `:ui` could not call any of it no matter what its `build.gradle.kts`
 * dependency graph looked like. This task widened exactly the declarations this file calls (and
 * the result types their signatures return) from `internal` to public, in `:qa:corpus` itself --
 * `contrastRatio`, `stressAngleDegrees`, `serifMetrics`/`SerifMetrics`, `storeysFromA`/
 * `storeysFromG`/`Storeys`, `terminalStyle`/`TerminalStyle`, `apertureOpenness`,
 * `superellipseExponent`, `xHeightToCapHeightRatio`, plus two small navigational helpers
 * (`Glyph.outerContour`, `Glyph.inkBounds`/`Bounds`) their signatures need -- leaving every other
 * internal helper (the probe geometry in `Geometry2D.kt`, `StyleGlyphSet`, `extractFeatures`,
 * `StyleScorer.kt`, `widthClass`) untouched. Every one of those files' own `git diff` is a one- or
 * two-line visibility change plus a KDoc note pointing here; no behaviour changed, confirmed by
 * `./gradlew :qa:corpus:check` staying green before and after (P6, this task).
 *
 * **Which qa/corpus function backs which lens term** (each called with its own real signature,
 * never re-implemented): contrast/stress -- `ContrastStress.kt`'s [contrastRatio]/
 * [stressAngleDegrees] on `o`; serif kind -- `SerifBracket.kt`'s [serifMetrics] on `T`; storey
 * count -- `Storeys.kt`'s [storeysFromA]/[storeysFromG] on `a`/`g`; terminal style --
 * `Terminal.kt`'s [terminalStyle] on `c`/`e`/`s`'s own [Glyph.outerContour]; aperture --
 * `Aperture.kt`'s [apertureOpenness] on `c`/`e`/`s`, divided by an x-height read the same way
 * `Proportions.kt` reads it internally ([Glyph.inkBounds]'s own `maxY`); roundness --
 * `Roundness.kt`'s [superellipseExponent] on `o`; the x-height/cap-height ratio --
 * `Proportions.kt`'s [xHeightToCapHeightRatio] on `x`/`H`. x-height, cap-height, ascender and
 * descender as their own *raw, font-unit* terms (section 3 lists these four separately, and
 * `Proportions.kt` only ever returns their *ratio*) come primarily from the glyph's own drawn ink
 * height ([Glyph.inkBounds]'s `maxY`, the same derivation `Proportions.kt`'s own KDoc documents:
 * "else derived from `x` and `H` ink extents") -- [xHeightEntry]/[capHeightEntry] fall back to
 * `core-font`'s own [SfntFont] `OS/2` fields (`sxHeight`/`sCapHeight`) only when the glyph itself
 * is missing, per CLAUDE.md law 1 ("the user's drawing is the source of truth") -- see
 * [xHeightEntry]'s own KDoc for the real Hyle Deco mismatch (`OS/2 sxHeight = 0`, `sCapHeight =
 * 500`, against drawn ink heights `500`/`700`) that makes this the correct order, not just a
 * cautious one. [ascenderEntry]/[descenderEntry] have no glyph-ink-bounds equivalent (there is no
 * one dedicated "ascender glyph"), so they read `hhea`'s `ascender`/`descender` directly -- there
 * is no real UFO for Hyle Deco to read a `UfoFontInfo` from instead (the task's other named
 * source).
 *
 * **Terms beyond docs/LESSONS_SCAFFOLD.md section 3's own 26-term list.** [AnatomyTerm.STOREYS],
 * [AnatomyTerm.ROUNDNESS] and [AnatomyTerm.X_HEIGHT_TO_CAP_HEIGHT_RATIO] are not in section 3's
 * lens vocabulary, but this task's own instructions name `Storeys.kt`, `Roundness.kt` and
 * `Proportions.kt` explicitly as features to wire, and `qa/corpus` measures all three for real, so
 * they are included as three extra lens terms rather than left unwired. `widthClass` (the ninth
 * `qa/corpus` style feature) is deliberately *not* wired here: it is a Lineages-strand
 * classification feature (`learn:scenes`'s own `STYLE_FEATURE_VOCABULARY` "Width" entry), not
 * anatomy vocabulary, and section 3 never names it. Logged as an open question (product owner:
 * should the three extra terms actually ship in the lens, or stay Lineages/identify-it-only
 * classification features?).
 *
 * **Definitions.** [STYLE_FEATURE_VOCABULARY] (`learn:scenes`, task P1b, marked SCAFFOLD) already
 * gives plain-language definitions for eight of this file's terms -- reused verbatim below via
 * [vocabularyDefinition], never re-typed, so the two never drift apart. The other terms (every
 * purely structural/located part with no measured feature behind it, plus the four raw font-level
 * terms) have no such entry; their definitions are authored here from
 * docs/RESEARCH_font_quality.md's own anatomy section ("Anatomy, critique and sequence"), which
 * names Fonts.com's Fontology and Wikipedia's "Typeface anatomy" as its sources and gives an
 * explicit term-to-glyph graph for `ear`/`link`/`loop` (the two-storey `g` only), `spur` (`G`/`e`),
 * `apex` (`A M N x`), `vertex` (`V W v w`), `tail` (`Q R j y J`) and aperture-as-a-located-part
 * (the open counters `a c e f h m n r s t u`); the research doc itself flags "aperture" and
 * "hairline serif" as undefined in its own fetched glossaries, a gap this file inherits honestly
 * (see [SerifKind]'s KDoc) rather than papering over.
 *
 * [AnatomyTerm] itself is every term the Anatomy Lens can show: docs/LESSONS_SCAFFOLD.md section
 * 3's own list plus the three extras this KDoc's own "Terms beyond..." paragraph above explains
 * ([AnatomyTerm.STOREYS], [AnatomyTerm.ROUNDNESS], [AnatomyTerm.X_HEIGHT_TO_CAP_HEIGHT_RATIO]).
 */
enum class AnatomyTerm {
    STEM,
    BOWL,
    COUNTER,
    APERTURE,
    TERMINAL,
    SPUR,
    EAR,
    LINK,
    LOOP,
    CROSSBAR,
    ARM,
    LEG,
    SHOULDER,
    SPINE,
    APEX,
    VERTEX,
    TAIL,
    TITTLE,
    SERIF,
    STRESS,
    CONTRAST,
    X_HEIGHT,
    CAP_HEIGHT,
    ASCENDER,
    DESCENDER,
    OVERSHOOT,
    STOREYS,
    ROUNDNESS,
    X_HEIGHT_TO_CAP_HEIGHT_RATIO,
}

/**
 * A coarse read of [dev.aarso.typewright.qa.corpus.style.SerifMetrics.bracketScore]: only
 * [BRACKETED] and [UNBRACKETED] are things that score actually distinguishes. Section 3's third
 * named kind, "hairline", has **no dedicated measured signal** in `qa/corpus` -- a hairline serif
 * is a *thin* one, and neither [dev.aarso.typewright.qa.corpus.style.SerifMetrics.hasSerif] nor
 * `bracketScore` measures serif weight, only flare ratio and bracket smoothness.
 * docs/RESEARCH_font_quality.md's own anatomy section already names this exact gap ("'aperture'
 * and 'hairline serif' are not defined in the fetched glossaries"), so [serifEntry] never returns
 * [BRACKETED]/[UNBRACKETED] as a stand-in for "hairline" -- it reports what was actually measured
 * and nothing more (CLAUDE.md law 5: "when unsure, stop and report rather than guess"). Logged as
 * an open question.
 */
enum class SerifKind { NONE, BRACKETED, UNBRACKETED, UNKNOWN }

/** One term's real measured value, in the shape its own `qa/corpus` (or `core-font`) function actually returns -- never collapsed to a display string here, so a caller can format it however the lens screen needs. */
sealed interface AnatomyLensValue {
    /** [contrastRatio] (>= 1.0) or [superellipseExponent] (>= 1.2, a superellipse exponent -- 2.0 is a true ellipse). */
    data class Ratio(
        val value: Double,
    ) : AnatomyLensValue

    /** [stressAngleDegrees]: degrees from vertical, folded into `(-90, 90]`. */
    data class DegreesFromVertical(
        val value: Double,
    ) : AnatomyLensValue

    /** x-height, cap-height, ascender or descender, in font units. */
    data class FontUnits(
        val value: Int,
    ) : AnatomyLensValue

    /** [storeysFromA]/[storeysFromG]'s own real [Storeys] result, carried through unchanged. */
    data class StoreyCount(
        val storeys: Storeys,
    ) : AnatomyLensValue

    /** [terminalStyle]'s own real [TerminalStyle] result, carried through unchanged. */
    data class TerminalShape(
        val style: TerminalStyle,
    ) : AnatomyLensValue

    /** [serifMetrics]'s own real fields, plus [SerifKind]'s own derived (and admittedly incomplete -- see its KDoc) reading of them. */
    data class SerifShape(
        val hasSerif: Boolean,
        val bracketScore: Double?,
        val kind: SerifKind,
    ) : AnatomyLensValue
}

/** One Anatomy Lens scene's worth of data for one [term]: its definition, where it occurs, and -- when `qa/corpus` measures it -- the real value on the glyph(s) this file was given. */
sealed interface AnatomyLensEntry {
    val term: AnatomyTerm
    val definition: String
    val occursOn: List<String>

    /**
     * A term the lens measured for real, via the exact `qa/corpus`/`core-font` function this
     * file's own KDoc names for [term] -- never an invented number (CLAUDE.md law 5). [value] is
     * `null` when the measurement genuinely could not be made ([unavailableReason] says why: a
     * needed glyph was missing from the font, or the probe found no usable geometry -- the
     * underlying function's own "never guessed, return `null`" convention, carried through
     * unchanged rather than papered over with a fallback number). [isHeuristic] is `true` exactly
     * when the `qa/corpus` function backing [term] calls itself "our heuristic" in its own KDoc
     * (law 5's own phrase) rather than a settled measurement; a caller displaying this entry
     * should show that caveat when it is `true`.
     */
    data class Measured(
        override val term: AnatomyTerm,
        override val definition: String,
        override val occursOn: List<String>,
        val value: AnatomyLensValue?,
        val isHeuristic: Boolean,
        val unavailableReason: String? = null,
    ) : AnatomyLensEntry

    /**
     * A term with no measured feature behind it: a purely structural or located anatomical part
     * (a stem, a counter, a spur -- something the lens *labels*, not something it *scores*).
     * CLAUDE.md law 5's "our heuristic" caveat is for a number the app judges the user by; a term
     * with no number here needs no such caveat, so this variant carries none.
     */
    data class DefinitionOnly(
        override val term: AnatomyTerm,
        override val definition: String,
        override val occursOn: List<String>,
    ) : AnatomyLensEntry
}

/**
 * The small set of glyphs (plus font-wide metrics) [anatomyLensEntry] needs to answer any Anatomy
 * Lens term for one font: the same nine glyphs `qa/corpus`'s own (internal) `StyleGlyphSet` looks
 * up by character (brief 8.4's `"onageHTcs"`), plus `x` for the x-height/cap-height terms, plus
 * [SfntFont]'s own `hhea`/`OS/2` metrics. Built once per font via [fromSfntFont]; a caller that
 * already has exactly the one or two glyphs a single term needs can call that term's own function
 * below directly instead ([contrastEntry], [terminalEntry], and so on) without building one of
 * these.
 */
data class AnatomyLensGlyphSet(
    val unitsPerEm: Int,
    val glyphs: Map<Char, Glyph>,
    val ascender: Int?,
    val descender: Int?,
    val osXHeight: Int?,
    val osCapHeight: Int?,
) {
    companion object
}

/** The characters [AnatomyLensGlyphSet.fromSfntFont] looks up: brief 8.4's `"onageHTcs"` plus `x` (the x-height/cap-height terms need it; `H` is already in the nine). */
private const val ANATOMY_LENS_GLYPH_CHARS = "onageHTcsx"

/** Builds an [AnatomyLensGlyphSet] from [font]'s `cmap`/`hhea`/`OS/2`, leaving out any character the font has no mapping for -- the same "skip what's missing, never throw" convention `qa/corpus`'s own `StyleGlyphSet.fromSfntFont` uses. */
fun AnatomyLensGlyphSet.Companion.fromSfntFont(font: SfntFont): AnatomyLensGlyphSet {
    val glyphs =
        ANATOMY_LENS_GLYPH_CHARS
            .mapNotNull { ch -> font.glyphForCodePoint(ch.code)?.let { ch to it } }
            .toMap()
    return AnatomyLensGlyphSet(
        unitsPerEm = font.head.unitsPerEm,
        glyphs = glyphs,
        ascender = font.hhea.ascender,
        descender = font.hhea.descender,
        osXHeight = font.os2?.sxHeight,
        osCapHeight = font.os2?.sCapHeight,
    )
}

/** One term's definition and where it occurs, independent of whether it is measured. */
private data class TermInfo(
    val definition: String,
    val occursOn: List<String>,
)

/** [termName]'s entry in [STYLE_FEATURE_VOCABULARY] (P1b), reused so this file's definitions cannot drift from the Lineages strand's own wording for the same feature. */
private fun vocabularyDefinition(termName: String) = STYLE_FEATURE_VOCABULARY.first { it.termName == termName }

/**
 * Every [AnatomyTerm]'s definition and occurrence list. Eight entries are
 * [vocabularyDefinition]'s own text and glyph list verbatim -- reused exactly as this task's own
 * instructions require, not re-typed. [AnatomyTerm.APERTURE] is the one exception among those
 * eight: its `occursOn` uses docs/RESEARCH_font_quality.md's own broader term-to-glyph answer
 * ("open counters (a c e f h m n r s t u) are where 'aperture' applies") rather than
 * [vocabularyDefinition]'s narrower `c`/`e`/`s` (which is *where `qa/corpus`'s own probe samples
 * it*, still true and still named in the reused definition text itself, just a narrower fact than
 * "where the anatomical part occurs"). Every other entry is authored for this file, from this same
 * research document, per this file's own top-level KDoc.
 */
private val ANATOMY_TERM_INFO: Map<AnatomyTerm, TermInfo> =
    buildMap {
        vocabularyDefinition("Contrast").let { put(AnatomyTerm.CONTRAST, TermInfo(it.plainLanguageDefinition, it.exampleGlyphs)) }
        vocabularyDefinition("Stress").let { put(AnatomyTerm.STRESS, TermInfo(it.plainLanguageDefinition, it.exampleGlyphs)) }
        vocabularyDefinition("Serif and bracket").let { put(AnatomyTerm.SERIF, TermInfo(it.plainLanguageDefinition, it.exampleGlyphs)) }
        vocabularyDefinition("Storeys").let { put(AnatomyTerm.STOREYS, TermInfo(it.plainLanguageDefinition, it.exampleGlyphs)) }
        vocabularyDefinition("Terminal").let { put(AnatomyTerm.TERMINAL, TermInfo(it.plainLanguageDefinition, it.exampleGlyphs)) }
        vocabularyDefinition("Aperture").let {
            put(AnatomyTerm.APERTURE, TermInfo(it.plainLanguageDefinition, listOf("a", "c", "e", "f", "h", "m", "n", "r", "s", "t", "u")))
        }
        vocabularyDefinition("Roundness").let { put(AnatomyTerm.ROUNDNESS, TermInfo(it.plainLanguageDefinition, it.exampleGlyphs)) }
        vocabularyDefinition("x-height ratio").let {
            put(AnatomyTerm.X_HEIGHT_TO_CAP_HEIGHT_RATIO, TermInfo(it.plainLanguageDefinition, it.exampleGlyphs))
        }

        put(
            AnatomyTerm.EAR,
            TermInfo(
                "A small stroke projecting from the top of a two-storey g's upper bowl. Occurs only on the " +
                    "two-storey construction of g (docs/RESEARCH_font_quality.md's term-to-glyph graph).",
                listOf("g"),
            ),
        )
        put(
            AnatomyTerm.LINK,
            TermInfo(
                "The stroke connecting a two-storey g's upper bowl to its lower loop. Occurs only on the " +
                    "two-storey g.",
                listOf("g"),
            ),
        )
        put(
            AnatomyTerm.LOOP,
            TermInfo(
                "The lower, open or closed bowl of a two-storey g, hanging below the baseline. Occurs only " +
                    "on the two-storey g.",
                listOf("g"),
            ),
        )
        put(
            AnatomyTerm.SPUR,
            TermInfo(
                "A small projection off a main stroke, where a curved stroke meets a straight one. Occurs on " +
                    "G and e (docs/RESEARCH_font_quality.md's term-to-glyph graph).",
                listOf("G", "e"),
            ),
        )
        put(
            AnatomyTerm.APEX,
            TermInfo(
                "The point at the top of a letter where two diagonal strokes meet. Occurs on A, M, N and x " +
                    "(docs/RESEARCH_font_quality.md's term-to-glyph graph).",
                listOf("A", "M", "N", "x"),
            ),
        )
        put(
            AnatomyTerm.VERTEX,
            TermInfo(
                "The point at the bottom of a letter where two diagonal strokes meet. Occurs on V, W, v and " +
                    "w (docs/RESEARCH_font_quality.md's term-to-glyph graph).",
                listOf("V", "W", "v", "w"),
            ),
        )
        put(
            AnatomyTerm.TAIL,
            TermInfo(
                "A short stroke, often curved or diagonal, descending from a letter's main body. Occurs on " +
                    "Q, R, j, y and J (docs/RESEARCH_font_quality.md's term-to-glyph graph).",
                listOf("Q", "R", "j", "y", "J"),
            ),
        )
        put(
            AnatomyTerm.STEM,
            TermInfo(
                "A letter's main straight (or near-straight) stroke, usually vertical.",
                listOf("H", "I", "l", "b", "d", "p", "q", "n"),
            ),
        )
        put(
            AnatomyTerm.BOWL,
            TermInfo("The curved stroke that encloses a letter's counter.", listOf("b", "d", "p", "q", "o", "B", "D", "R")),
        )
        put(
            AnatomyTerm.COUNTER,
            TermInfo("The space fully or partly enclosed by a bowl or other curved stroke.", listOf("o", "e", "a", "s", "b", "d", "g")),
        )
        put(
            AnatomyTerm.CROSSBAR,
            TermInfo("The horizontal stroke that connects two strokes, or crosses through a stem.", listOf("A", "H", "e", "f", "t")),
        )
        put(
            AnatomyTerm.ARM,
            TermInfo(
                "A stroke that projects upward or horizontally and is free (unconnected) at one end.",
                listOf("E", "F", "K", "L", "T", "Y", "k"),
            ),
        )
        put(
            AnatomyTerm.LEG,
            TermInfo("A stroke, often diagonal, that projects downward and is free at one end.", listOf("K", "R", "k")),
        )
        put(
            AnatomyTerm.SHOULDER,
            TermInfo("The curved stroke leading off a stem into an arch, as in the top of an n or an r.", listOf("n", "m", "h", "r")),
        )
        put(
            AnatomyTerm.SPINE,
            TermInfo("The main curved stroke running top to bottom through the letter S.", listOf("S", "s")),
        )
        put(
            AnatomyTerm.TITTLE,
            TermInfo("The small dot above a lowercase i or j.", listOf("i", "j")),
        )
        put(
            AnatomyTerm.OVERSHOOT,
            TermInfo(
                "The small amount a round or pointed letter is drawn past a flat letter's own baseline or " +
                    "cap-height line, so the two read as the same size -- a perfectly round o sitting exactly " +
                    "on the baseline looks smaller than a flat-bottomed letter next to it.",
                listOf("o", "e", "c", "s"),
            ),
        )
        put(
            AnatomyTerm.X_HEIGHT,
            TermInfo(
                "The height of lowercase letters with no ascender or descender, from the baseline to the top " +
                    "of a letter like x -- a font-level guide line (docs/RESEARCH_font_quality.md), not a " +
                    "label on one glyph.",
                listOf("x"),
            ),
        )
        put(
            AnatomyTerm.CAP_HEIGHT,
            TermInfo(
                "The height of a capital letter, from the baseline to the top of a letter like H -- a " +
                    "font-level guide line, not a label on one glyph.",
                listOf("H"),
            ),
        )
        put(
            AnatomyTerm.ASCENDER,
            TermInfo(
                "How far a lowercase letter's ascending stroke rises above the x-height line -- a font-level " +
                    "guide line.",
                listOf("b", "d", "f", "h", "k", "l"),
            ),
        )
        put(
            AnatomyTerm.DESCENDER,
            TermInfo(
                "How far a lowercase letter's descending stroke drops below the baseline -- a font-level " +
                    "guide line.",
                listOf("g", "j", "p", "q", "y"),
            ),
        )
    }

private fun termInfo(term: AnatomyTerm): TermInfo =
    ANATOMY_TERM_INFO[term] ?: error("AnatomyTerm.$term has no ANATOMY_TERM_INFO entry -- every enum entry must have one")

private fun unavailable(
    term: AnatomyTerm,
    reason: String,
    isHeuristic: Boolean,
): AnatomyLensEntry.Measured {
    val info = termInfo(term)
    return AnatomyLensEntry.Measured(
        term,
        info.definition,
        info.occursOn,
        value = null,
        isHeuristic = isHeuristic,
        unavailableReason = reason,
    )
}

private fun measured(
    term: AnatomyTerm,
    value: AnatomyLensValue,
    isHeuristic: Boolean,
): AnatomyLensEntry.Measured {
    val info = termInfo(term)
    return AnatomyLensEntry.Measured(term, info.definition, info.occursOn, value = value, isHeuristic = isHeuristic)
}

/** [AnatomyTerm.CONTRAST] on [o], via [contrastRatio]'s own real signature. Not flagged heuristic: [contrastRatio]'s own KDoc never calls itself one -- it is a direct probe average, exact for a true ellipse. */
fun contrastEntry(o: Glyph?): AnatomyLensEntry {
    if (o == null) return unavailable(AnatomyTerm.CONTRAST, "no 'o' glyph in this font", isHeuristic = false)
    val value =
        contrastRatio(o) ?: return unavailable(AnatomyTerm.CONTRAST, "the stroke probe found no usable width on 'o'", isHeuristic = false)
    return measured(AnatomyTerm.CONTRAST, AnatomyLensValue.Ratio(value), isHeuristic = false)
}

/** [AnatomyTerm.STRESS] on [o], via [stressAngleDegrees]'s own real signature. Not flagged heuristic: see [contrastEntry]. */
fun stressEntry(o: Glyph?): AnatomyLensEntry {
    if (o == null) return unavailable(AnatomyTerm.STRESS, "no 'o' glyph in this font", isHeuristic = false)
    val value =
        stressAngleDegrees(o)
            ?: return unavailable(AnatomyTerm.STRESS, "the stroke probe found no usable width on 'o'", isHeuristic = false)
    return measured(AnatomyTerm.STRESS, AnatomyLensValue.DegreesFromVertical(value), isHeuristic = false)
}

/** [AnatomyTerm.ROUNDNESS] on [o], via [superellipseExponent]'s own real signature. Not flagged heuristic: its own KDoc never calls itself one (a coarse-then-refined least-squares fit, not a threshold guess). */
fun roundnessEntry(o: Glyph?): AnatomyLensEntry {
    if (o == null) return unavailable(AnatomyTerm.ROUNDNESS, "no 'o' glyph in this font", isHeuristic = false)
    val value = superellipseExponent(o) ?: return unavailable(AnatomyTerm.ROUNDNESS, "'o' has no usable outer contour", isHeuristic = false)
    return measured(AnatomyTerm.ROUNDNESS, AnatomyLensValue.Ratio(value), isHeuristic = false)
}

/**
 * [AnatomyTerm.SERIF] on [t], via [serifMetrics]'s own real signature -- flagged heuristic: its
 * own KDoc says so explicitly ("This is qa/corpus's own heuristic (law 5), not a published
 * measurement"). [SerifKind.BRACKETED]/[SerifKind.UNBRACKETED] split [bracketScore][dev.aarso.typewright.qa.corpus.style.SerifMetrics.bracketScore]
 * at [SERIF_BRACKET_THRESHOLD], this file's own threshold on top of `qa/corpus`'s own heuristic
 * score -- no published rule sets this cut point either, and [SerifKind.UNKNOWN] is never guessed
 * into one of the other three; see [SerifKind]'s KDoc for why "hairline" is never returned at all.
 */
fun serifEntry(t: Glyph?): AnatomyLensEntry {
    if (t == null) return unavailable(AnatomyTerm.SERIF, "no 'T' glyph in this font", isHeuristic = true)
    val metrics = serifMetrics(t) ?: return unavailable(AnatomyTerm.SERIF, "'${t.name}' has no usable ink bounds", isHeuristic = true)
    val bracketScore = metrics.bracketScore
    val kind =
        when {
            !metrics.hasSerif -> SerifKind.NONE
            bracketScore == null -> SerifKind.UNKNOWN
            bracketScore >= SERIF_BRACKET_THRESHOLD -> SerifKind.BRACKETED
            else -> SerifKind.UNBRACKETED
        }
    return measured(AnatomyTerm.SERIF, AnatomyLensValue.SerifShape(metrics.hasSerif, bracketScore, kind), isHeuristic = true)
}

/** This file's own split point on [dev.aarso.typewright.qa.corpus.style.SerifMetrics.bracketScore] (0 = an abrupt, unbracketed step; up to ~1 = a smooth, fully bracketed curve), the midpoint of that documented range. Not a published rule -- see [serifEntry]'s KDoc. */
private const val SERIF_BRACKET_THRESHOLD = 0.5

/**
 * [AnatomyTerm.STOREYS] on [a]/[g], via [storeysFromA]/[storeysFromG]'s own real signatures,
 * preferring `g`'s answer when it is not [Storeys.UNKNOWN] -- the same preference `qa/corpus`'s
 * own (internal) `combineStoreys` uses, and for the same reason its KDoc gives: `g`'s
 * three-contours-or-two topological signal is reliable, while `a`'s counter-height-ratio signal is
 * this file's own inherited heuristic ([storeysFromA]'s KDoc: "our heuristic, law 5"). Flagged
 * heuristic exactly when the `a`-derived answer is the one actually returned.
 */
fun storeysEntry(
    a: Glyph?,
    g: Glyph?,
): AnatomyLensEntry {
    val fromG = g?.let { storeysFromG(it) }
    if (fromG != null && fromG != Storeys.UNKNOWN) {
        return measured(AnatomyTerm.STOREYS, AnatomyLensValue.StoreyCount(fromG), isHeuristic = false)
    }
    val fromA = a?.let { storeysFromA(it) }
    if (fromA != null && fromA != Storeys.UNKNOWN) {
        return measured(AnatomyTerm.STOREYS, AnatomyLensValue.StoreyCount(fromA), isHeuristic = true)
    }
    return unavailable(AnatomyTerm.STOREYS, "neither 'a' nor 'g' gave a usable storey count", isHeuristic = false)
}

/**
 * [AnatomyTerm.TERMINAL] on [glyph] (a `c`/`e`/`s`/`r`/`f`-shaped glyph, the caller's choice), via
 * [glyph]'s own [Glyph.outerContour] and [terminalStyle]'s own real signature. Flagged heuristic:
 * [terminalStyle]'s own KDoc says so explicitly.
 */
fun terminalEntry(glyph: Glyph?): AnatomyLensEntry {
    if (glyph == null) return unavailable(AnatomyTerm.TERMINAL, "no c/e/s/r/f-shaped glyph given", isHeuristic = true)
    val contour = glyph.outerContour() ?: return unavailable(AnatomyTerm.TERMINAL, "'${glyph.name}' has no contours", isHeuristic = true)
    val style = terminalStyle(glyph, contour)
    if (style == TerminalStyle.UNKNOWN) {
        return unavailable(AnatomyTerm.TERMINAL, "the terminal could not be located on '${glyph.name}'", isHeuristic = true)
    }
    return measured(AnatomyTerm.TERMINAL, AnatomyLensValue.TerminalShape(style), isHeuristic = true)
}

/**
 * [AnatomyTerm.APERTURE] on [glyph] (a `c`/`e`/`s`-shaped glyph) against [xHeight], via
 * [apertureOpenness]'s own real signature. Flagged heuristic: its own KDoc says so explicitly
 * ("Known limitation (law 5, 'our heuristic')"). Unlike `qa/corpus`'s own `extractFeatures`, this
 * does not average over every one of `c`/`e`/`s` present -- the lens shows one real measurement on
 * one of the user's own letters at a time, not a font-wide average for classification.
 */
fun apertureEntry(
    glyph: Glyph?,
    xHeight: Double?,
): AnatomyLensEntry {
    if (glyph == null) return unavailable(AnatomyTerm.APERTURE, "no c/e/s-shaped glyph given", isHeuristic = true)
    if (xHeight == null || xHeight <= 0.0) return unavailable(AnatomyTerm.APERTURE, "no positive x-height to divide by", isHeuristic = true)
    val value =
        apertureOpenness(glyph, xHeight)
            ?: return unavailable(AnatomyTerm.APERTURE, "no throat found on '${glyph.name}'", isHeuristic = true)
    return measured(AnatomyTerm.APERTURE, AnatomyLensValue.Ratio(value), isHeuristic = true)
}

/** [AnatomyTerm.X_HEIGHT_TO_CAP_HEIGHT_RATIO] on [x]/[capH], via [xHeightToCapHeightRatio]'s own real signature. Not flagged heuristic: its own KDoc describes a direct ink-bounds ratio, never calling itself a heuristic. */
fun xHeightToCapHeightRatioEntry(
    x: Glyph?,
    capH: Glyph?,
): AnatomyLensEntry {
    if (x == null || capH == null) {
        return unavailable(AnatomyTerm.X_HEIGHT_TO_CAP_HEIGHT_RATIO, "'x' and/or 'H' missing from this font", isHeuristic = false)
    }
    val value =
        xHeightToCapHeightRatio(x, capH)
            ?: return unavailable(AnatomyTerm.X_HEIGHT_TO_CAP_HEIGHT_RATIO, "'H' has no positive ink height", isHeuristic = false)
    return measured(AnatomyTerm.X_HEIGHT_TO_CAP_HEIGHT_RATIO, AnatomyLensValue.Ratio(value), isHeuristic = false)
}

/**
 * [AnatomyTerm.X_HEIGHT] as a raw font-unit value: [x]'s own ink height ([Glyph.inkBounds]'s
 * `maxY`) when [x] is present, else [os2XHeight] (`OS/2`'s `sxHeight`, `core-font`'s
 * [SfntFont.os2] -- real when the table is version >= 2). The drawn glyph is primary, not `OS/2`'s
 * declared metric, per CLAUDE.md law 1 ("the user's drawing is the source of truth") -- confirmed
 * necessary, not just a cautious default, by this task's own real-font check against
 * `fonts/HyleDeco-Regular.ttf`: its `OS/2` table gives `sxHeight = 0` and `sCapHeight = 500`, while
 * `x`/`H`'s own drawn ink heights are `500`/`700` -- the declared metrics do not match what was
 * actually drawn (docs/OPEN_QUESTIONS.md logs this as a Hyle Deco fixture data-quality finding). A
 * non-positive `OS/2` value is treated the same as a missing one, matching `qa/corpus`'s own
 * "non-positive means unusable" convention (e.g. `xHeightToCapHeightRatio`'s `capHeight <= 0.0`
 * check). Not flagged heuristic: both sources are direct measurements, never a classification
 * guess.
 */
fun xHeightEntry(
    x: Glyph?,
    os2XHeight: Int?,
): AnatomyLensEntry {
    val value = x?.inkBounds()?.maxY?.roundToInt() ?: os2XHeight?.takeIf { it > 0 }
    if (value ==
        null
    ) {
        return unavailable(AnatomyTerm.X_HEIGHT, "no 'x' glyph and no positive OS/2 sxHeight to fall back to", isHeuristic = false)
    }
    return measured(AnatomyTerm.X_HEIGHT, AnatomyLensValue.FontUnits(value), isHeuristic = false)
}

/** [AnatomyTerm.CAP_HEIGHT] as a raw font-unit value: [h]'s own ink height when [h] is present, else [os2CapHeight] (`OS/2`'s `sCapHeight`). Not flagged heuristic: see [xHeightEntry], whose KDoc also has the real Hyle Deco mismatch this priority order is built to handle correctly. */
fun capHeightEntry(
    h: Glyph?,
    os2CapHeight: Int?,
): AnatomyLensEntry {
    val value = h?.inkBounds()?.maxY?.roundToInt() ?: os2CapHeight?.takeIf { it > 0 }
    if (value ==
        null
    ) {
        return unavailable(AnatomyTerm.CAP_HEIGHT, "no 'H' glyph and no positive OS/2 sCapHeight to fall back to", isHeuristic = false)
    }
    return measured(AnatomyTerm.CAP_HEIGHT, AnatomyLensValue.FontUnits(value), isHeuristic = false)
}

/** [AnatomyTerm.ASCENDER] as a raw font-unit value: `core-font`'s [SfntFont.hhea]'s own `ascender` field, real and always present once a font reads at all (`HheaTable.ascender` is non-nullable). Not flagged heuristic: a direct font-table field. */
fun ascenderEntry(ascender: Int?): AnatomyLensEntry {
    if (ascender == null) return unavailable(AnatomyTerm.ASCENDER, "no font metrics given", isHeuristic = false)
    return measured(AnatomyTerm.ASCENDER, AnatomyLensValue.FontUnits(ascender), isHeuristic = false)
}

/** [AnatomyTerm.DESCENDER] as a raw font-unit value: `core-font`'s [SfntFont.hhea]'s own `descender` field (conventionally negative). Not flagged heuristic: see [ascenderEntry]. */
fun descenderEntry(descender: Int?): AnatomyLensEntry {
    if (descender == null) return unavailable(AnatomyTerm.DESCENDER, "no font metrics given", isHeuristic = false)
    return measured(AnatomyTerm.DESCENDER, AnatomyLensValue.FontUnits(descender), isHeuristic = false)
}

/** [term]'s definition and occurrence list with no measurement attached -- for the 17 purely structural/located terms this file's [ANATOMY_TERM_INFO] carries no `qa/corpus` feature for. */
fun definitionOnlyEntry(term: AnatomyTerm): AnatomyLensEntry.DefinitionOnly {
    val info = termInfo(term)
    return AnatomyLensEntry.DefinitionOnly(term, info.definition, info.occursOn)
}

/**
 * The Anatomy Lens's one entry point: given a [term] and the small glyph/metrics set the lens is
 * currently showing (from [AnatomyLensGlyphSet.fromSfntFont] or built by hand), returns that
 * term's real measured value where `qa/corpus`/`core-font` has one, or its definition alone
 * otherwise -- this file's own top-level KDoc has the full mapping from term to source function. A
 * single bare [Glyph] is not enough for most terms (aperture needs an x-height too; the
 * x-height/cap-height terms need font-wide metrics; storeys prefers `g` over `a`), which is
 * exactly why [AnatomyLensGlyphSet] exists rather than this function taking one [Glyph] directly --
 * see that type's own KDoc. A caller that already has exactly the glyph(s) one specific term needs
 * can call that term's own function above directly instead.
 */
fun anatomyLensEntry(
    term: AnatomyTerm,
    glyphSet: AnatomyLensGlyphSet,
): AnatomyLensEntry =
    when (term) {
        AnatomyTerm.CONTRAST -> {
            contrastEntry(glyphSet.glyphs['o'])
        }

        AnatomyTerm.STRESS -> {
            stressEntry(glyphSet.glyphs['o'])
        }

        AnatomyTerm.ROUNDNESS -> {
            roundnessEntry(glyphSet.glyphs['o'])
        }

        AnatomyTerm.SERIF -> {
            serifEntry(glyphSet.glyphs['T'])
        }

        AnatomyTerm.STOREYS -> {
            storeysEntry(glyphSet.glyphs['a'], glyphSet.glyphs['g'])
        }

        AnatomyTerm.TERMINAL -> {
            terminalEntry(glyphSet.glyphs['c'] ?: glyphSet.glyphs['e'] ?: glyphSet.glyphs['s'])
        }

        AnatomyTerm.APERTURE -> {
            val probe = glyphSet.glyphs['c'] ?: glyphSet.glyphs['e'] ?: glyphSet.glyphs['s']
            apertureEntry(probe, glyphSet.glyphs['x']?.inkBounds()?.maxY)
        }

        AnatomyTerm.X_HEIGHT_TO_CAP_HEIGHT_RATIO -> {
            xHeightToCapHeightRatioEntry(glyphSet.glyphs['x'], glyphSet.glyphs['H'])
        }

        AnatomyTerm.X_HEIGHT -> {
            xHeightEntry(glyphSet.glyphs['x'], glyphSet.osXHeight)
        }

        AnatomyTerm.CAP_HEIGHT -> {
            capHeightEntry(glyphSet.glyphs['H'], glyphSet.osCapHeight)
        }

        AnatomyTerm.ASCENDER -> {
            ascenderEntry(glyphSet.ascender)
        }

        AnatomyTerm.DESCENDER -> {
            descenderEntry(glyphSet.descender)
        }

        else -> {
            definitionOnlyEntry(term)
        }
    }
