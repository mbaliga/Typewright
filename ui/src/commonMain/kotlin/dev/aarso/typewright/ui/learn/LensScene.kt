// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.ui.learn

import dev.aarso.typewright.core.geometry.Glyph
import dev.aarso.typewright.core.geometry.Vec2
import dev.aarso.typewright.qa.corpus.style.Storeys
import dev.aarso.typewright.qa.corpus.style.TerminalStyle
import dev.aarso.typewright.qa.corpus.style.inkBounds
import dev.aarso.typewright.ui.tokens.toFixedString

// The Anatomy Lens tab's pure scene-selection logic (`ui/typewright-explorer.html`'s `#ln-lens`;
// TYPEWRIGHT_BUILD_BRIEF.md section 9 strand 2; `docs/LESSONS_SCAFFOLD.md` section 3), kept
// Compose-light (only [Vec2]/[Glyph], no `@Composable`) so [lensSceneFor] and its helpers are
// directly unit-testable without a Compose test rule -- the same split [OverlayRenderPlan.kt]
// already uses for the sibling Overlay tab (pure plan, separate `@Composable` file).
//
// **Which real letter is on stage, and why it can change.** The explorer's own static `#ln-lens`
// demo draws its four terms (bowl, counter, stem, terminal) as simultaneous leader lines on one
// fixed, hand-drawn "a" -- a stylised illustration, not a real measurement. This build wires real
// [dev.aarso.typewright.ui.learn.AnatomyLensData] measurements instead, and
// [dev.aarso.typewright.ui.learn.AnatomyLensData]'s own KDoc ("Which qa/corpus function backs
// which lens term") is explicit that different terms are measured on different real glyphs
// (contrast/stress/roundness on `o`, serif on `T`, terminal/aperture on `c`/`e`/`s`, storeys on
// `a`/`g`) -- so a lens that always drew one glyph regardless of term would be pointing a leader
// line at ink that was never actually the subject of the measurement being shown, which is exactly
// what CLAUDE.md law 1 and law 5 rule out. [HERO_CHAR_CANDIDATES] instead names, per term, the
// same real glyph(s) [dev.aarso.typewright.ui.learn.anatomyLensEntry] itself measures; the
// composable switches which glyph is on stage only when the tapped term needs a different one, and
// shows every term that shares the *current* glyph as its own simultaneous leader line
// ([termsSharingHeroChar]) -- reproducing the explorer's own "several labels on one glyph" look
// (law 6) for the group of terms (bowl / counter / contrast / roundness, all real `o` measurements)
// where that is honest, without faking it for the rest.
//
// **Leader-line target points are a bounding-box approximation, not the probe's own point.**
// [AnatomyLensValue] carries only the real aggregate number or enum each `qa/corpus` function
// actually returns (a ratio, a degree, a style) -- never a location -- for every measured term this
// file places a dot for: [dev.aarso.typewright.qa.corpus.style.contrastRatio],
// [dev.aarso.typewright.qa.corpus.style.stressAngleDegrees],
// [dev.aarso.typewright.qa.corpus.style.superellipseExponent],
// [dev.aarso.typewright.qa.corpus.style.apertureOpenness] and
// [dev.aarso.typewright.qa.corpus.style.terminalStyle]'s own signatures were all checked before
// writing this file; none of them hands back the sampled point their own probe actually touched.
// The one place that point exists internally, `qa/corpus`'s own `narrowestThroat`
// (`Geometry2D.kt`), is `internal` -- unreachable from `ui` without a second AnatomyLensData-style
// visibility widening this task was not asked to make. [LEADER_ANCHOR] is therefore a fixed
// fraction of the hero glyph's own real [Glyph.inkBounds] (0,0 bottom-left corner, 1,1 top-right),
// chosen by eye per term to land on the anatomical part being named (checked against a real
// rendered screenshot, per this task's own instructions) -- an honest, disclosed approximation, not
// the measurement's own location. Logged as an open question: a future task could widen
// `narrowestThroat`'s own result type to expose its point, if pointing exactly at the sampled
// location is wanted over this approximation.

/**
 * The Anatomy Lens tab's curated term set: the explorer's own `#defs` worked example -- bowl,
 * counter, stem, terminal, in that exact order -- plus five more real, `qa/corpus`-measured terms:
 * aperture, serif, contrast and roundness from `docs/LESSONS_SCAFFOLD.md` section 3's own lens
 * list, and storeys (named explicitly by this task's own instructions, though not in section 3's
 * own list -- already logged, `docs/OPEN_QUESTIONS.md` item 52, [AnatomyLensData]'s own "Terms
 * beyond..." KDoc). Every term here has a real [HERO_CHAR_CANDIDATES] entry, so every one of them
 * draws a real leader line, not just a definitions-list row.
 */
val LENS_TAB_TERMS: List<AnatomyTerm> =
    listOf(
        AnatomyTerm.BOWL,
        AnatomyTerm.COUNTER,
        AnatomyTerm.STEM,
        AnatomyTerm.TERMINAL,
        AnatomyTerm.APERTURE,
        AnatomyTerm.SERIF,
        AnatomyTerm.STOREYS,
        AnatomyTerm.CONTRAST,
        AnatomyTerm.ROUNDNESS,
    )

/**
 * Per [AnatomyTerm], the real glyph character(s) [dev.aarso.typewright.ui.learn.anatomyLensEntry]
 * itself measures that term on, in the same preference order `AnatomyLensData.kt`'s own dispatcher
 * uses (e.g. terminal/aperture prefer `c`, then `e`, then `s`; storeys prefers `g`'s topological
 * answer, but `a` is a fine stand-in glyph to draw when only its own heuristic answer is available)
 * -- [heroGlyphFor] resolves this against whichever glyphs the font this build reads actually has.
 */
private val HERO_CHAR_CANDIDATES: Map<AnatomyTerm, List<Char>> =
    mapOf(
        AnatomyTerm.BOWL to listOf('o'),
        AnatomyTerm.COUNTER to listOf('o'),
        AnatomyTerm.STEM to listOf('n'),
        AnatomyTerm.TERMINAL to listOf('c', 'e', 's'),
        AnatomyTerm.APERTURE to listOf('c', 'e', 's'),
        AnatomyTerm.SERIF to listOf('T'),
        AnatomyTerm.STOREYS to listOf('a', 'g'),
        AnatomyTerm.CONTRAST to listOf('o'),
        AnatomyTerm.ROUNDNESS to listOf('o'),
    )

/**
 * [term]'s real hero character and [Glyph] in [glyphSet]: the first of [HERO_CHAR_CANDIDATES] that
 * [glyphSet] actually has (an [AnatomyLensGlyphSet] built from a font with an incomplete `cmap`
 * simply has fewer candidates to try), or `null` if none of them are present -- the caller then
 * shows [term]'s definition with no diagram, never a wrong or invented glyph.
 */
fun heroGlyphFor(
    term: AnatomyTerm,
    glyphSet: AnatomyLensGlyphSet,
): Pair<Char, Glyph>? {
    val candidates = HERO_CHAR_CANDIDATES[term] ?: return null
    for (ch in candidates) {
        val glyph = glyphSet.glyphs[ch]
        if (glyph != null) return ch to glyph
    }
    return null
}

/** Every [LENS_TAB_TERMS] entry whose own [heroGlyphFor] resolves to [heroChar] in [glyphSet], in [LENS_TAB_TERMS]'s own order -- the group of terms the lens diagram shows as simultaneous leader lines while [heroChar]'s glyph is on stage. */
fun termsSharingHeroChar(
    glyphSet: AnatomyLensGlyphSet,
    heroChar: Char,
): List<AnatomyTerm> = LENS_TAB_TERMS.filter { heroGlyphFor(it, glyphSet)?.first == heroChar }

/** A leader-line target as a fraction of the hero glyph's own real ink bounds; see this file's own top-level KDoc, "Leader-line target points...". */
private data class LeaderAnchor(
    val fx: Double,
    val fy: Double,
)

private val LEADER_ANCHOR: Map<AnatomyTerm, LeaderAnchor> =
    mapOf(
        AnatomyTerm.BOWL to LeaderAnchor(0.03, 0.50),
        AnatomyTerm.COUNTER to LeaderAnchor(0.50, 0.50),
        AnatomyTerm.STEM to LeaderAnchor(0.14, 0.32),
        AnatomyTerm.TERMINAL to LeaderAnchor(0.92, 0.82),
        AnatomyTerm.APERTURE to LeaderAnchor(0.96, 0.46),
        AnatomyTerm.SERIF to LeaderAnchor(0.50, 0.02),
        AnatomyTerm.STOREYS to LeaderAnchor(0.60, 0.88),
        AnatomyTerm.CONTRAST to LeaderAnchor(0.50, 0.97),
        AnatomyTerm.ROUNDNESS to LeaderAnchor(0.97, 0.50),
    )

/**
 * One term's full lens scene: its real [AnatomyLensEntry] (via
 * [dev.aarso.typewright.ui.learn.anatomyLensEntry] -- a real definition always, plus a real
 * measured value when `qa/corpus` has one), which real glyph the leader line is drawn on
 * ([heroGlyphFor]), and that leader line's target point in font units (`null` exactly when
 * [heroGlyph] is `null`, or is present but has no ink -- an empty glyph has no [Glyph.inkBounds] to
 * place a fraction of).
 */
data class LensScene(
    val term: AnatomyTerm,
    val entry: AnatomyLensEntry,
    val heroChar: Char?,
    val heroGlyph: Glyph?,
    val leaderTargetFontUnits: Vec2?,
)

/** [term]'s real [LensScene] against [glyphSet]. */
fun lensSceneFor(
    term: AnatomyTerm,
    glyphSet: AnatomyLensGlyphSet,
): LensScene {
    val entry = anatomyLensEntry(term, glyphSet)
    val hero = heroGlyphFor(term, glyphSet)
    val anchor = LEADER_ANCHOR[term]
    val bounds = hero?.second?.inkBounds()
    val target =
        if (anchor != null && bounds != null) {
            Vec2(bounds.minX + anchor.fx * bounds.width, bounds.minY + anchor.fy * bounds.height)
        } else {
            null
        }
    return LensScene(term, entry, hero?.first, hero?.second, target)
}

/**
 * [value]'s real number or enum, formatted for the definitions list -- every branch reads straight
 * off the real value `qa/corpus`/`core-font` returned (CLAUDE.md law 5: never an invented display
 * string). Decimal formatting is [dev.aarso.typewright.ui.tokens.toFixedString], the same
 * `String.format`-free helper the inspector already uses (that file's own KDoc: `String.format` is
 * JVM-only, unavailable on this module's `wasmJs` target).
 */
fun formatAnatomyLensValue(value: AnatomyLensValue): String =
    when (value) {
        is AnatomyLensValue.Ratio -> {
            value.value.toFixedString(2)
        }

        is AnatomyLensValue.DegreesFromVertical -> {
            "${value.value.toFixedString(1)}° from vertical"
        }

        is AnatomyLensValue.FontUnits -> {
            "${value.value} units"
        }

        is AnatomyLensValue.StoreyCount -> {
            when (value.storeys) {
                Storeys.SINGLE -> "single storey"
                Storeys.DOUBLE -> "double storey"
                Storeys.UNKNOWN -> "storey count unknown"
            }
        }

        is AnatomyLensValue.TerminalShape -> {
            when (value.style) {
                TerminalStyle.FLAT -> "flat terminal"
                TerminalStyle.ROUND -> "round terminal"
                TerminalStyle.ANGLED -> "angled terminal"
                TerminalStyle.UNKNOWN -> "terminal style unknown"
            }
        }

        is AnatomyLensValue.SerifShape -> {
            when (value.kind) {
                SerifKind.NONE -> "no serif"
                SerifKind.BRACKETED -> "bracketed serif"
                SerifKind.UNBRACKETED -> "unbracketed serif"
                SerifKind.UNKNOWN -> "serif kind unknown"
            }
        }
    }

/** `"our heuristic"` (CLAUDE.md law 5's own phrase) when [entry] is a measured, heuristic entry that actually produced a value; `null` otherwise -- a caller shows this next to the value, never in place of a missing one (see [unavailableReasonOf] for that case). */
fun heuristicCaveat(entry: AnatomyLensEntry): String? {
    val measured = entry as? AnatomyLensEntry.Measured ?: return null
    return "our heuristic".takeIf { measured.isHeuristic && measured.value != null }
}

/** [entry]'s own real "why not" when a measured term's value could not be produced; `null` when [entry] has a value, or is [AnatomyLensEntry.DefinitionOnly]. */
fun unavailableReasonOf(entry: AnatomyLensEntry): String? = (entry as? AnatomyLensEntry.Measured)?.unavailableReason

/**
 * [entry]'s one-line value readout for the definitions list: the real formatted value plus, when
 * present, [heuristicCaveat] (` · our heuristic`); the real [unavailableReasonOf] prefixed
 * `not measured:` when a measured term genuinely has no value; `null` for
 * [AnatomyLensEntry.DefinitionOnly] (no number to show at all, so no line -- CLAUDE.md law 5's
 * "our heuristic" caveat is for a number the app judges the user by, and a purely structural term
 * has none).
 */
fun lensValueLine(entry: AnatomyLensEntry): String? {
    if (entry !is AnatomyLensEntry.Measured) return null
    val value = entry.value
    if (value == null) {
        return entry.unavailableReason?.let { "not measured: $it" } ?: "not measured"
    }
    val caveat = heuristicCaveat(entry)
    val formatted = formatAnatomyLensValue(value)
    return if (caveat != null) "$formatted · $caveat" else formatted
}
