// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.qa.corpus.style

import kotlin.math.abs

/**
 * A hand-written, explainable style scorer (brief 8.4: "a small hand-written scorer over the
 * feature vector in v1 (no ML), reviewable and explainable"). [rankStyleClasses] scores
 * [FeatureVector] against the same ten Google Fonts taxonomy keys `qa/corpus`'s node-economy pack
 * uses (`NodeEconomyPack.styles.keys`), one dedicated function per class, and returns them sorted
 * by confidence, each with the plain-language reasons that pushed its score up
 * (`ClassMatch.evidence`) -- what brief 8.4 calls "the app infers a class from measurable features
 * and shows its evidence".
 *
 * Each class function is a weighted average of independent per-feature membership scores in
 * `[0, 1]` (see [near], [atLeast], [atMost], [within] below): a feature that is `null` (missing
 * glyph, degenerate probe) is simply left out of both the weighted sum and the weight total, so a
 * font missing one glyph is not penalised for it, per P1b's "skip... without failing". A class's
 * confidence is therefore always the average match quality of *the evidence it actually has*, not
 * lowered by data it never got a chance to see.
 *
 * The thresholds below are `qa/corpus`'s own heuristic (law 5): grounded in
 * TYPEWRIGHT_BUILD_BRIEF.md 8.4's own description of each class and
 * docs/LESSONS_SCAFFOLD.md's Lineages era table (quoted per class below), then hand-tuned against
 * this package's validation sample (ten Lineages exemplars plus one representative font per
 * corpus style class) rather than fit by any automated search -- see this module's validation
 * notes for the resulting confusion summary and its honest limits, spelled out per class below:
 * [scoreDisplayArtDeco] and [scoreBlackletter] are markedly weaker than the other eight, because
 * none of the nine features names deco's "inline stripes, extreme widths" or directly measures a
 * broken/diamond stroke the way blackletter needs; both functions say so in their own KDoc and
 * [rankStyleClasses] scales their output down accordingly (see [WEAK_CLASS_CONFIDENCE_CAP]).
 */
internal data class ClassMatch(
    val styleKey: String,
    val confidence: Double,
    val evidence: List<String>,
)

/** The ten Google Fonts taxonomy keys, matching `NodeEconomyPack.styles.keys` (TYPEWRIGHT_BUILD_BRIEF.md 8.1). */
internal val STYLE_CLASS_KEYS =
    listOf(
        "sans-geometric",
        "sans-grotesque",
        "sans-neogrotesque",
        "sans-humanist",
        "serif-garalde",
        "serif-transitional",
        "serif-didone",
        "slab",
        "display-artdeco",
        "blackletter",
    )

/** Every class ranked by confidence, most likely first (brief 8.4: "ranked classes with confidence, e.g. 'geometric sans 0.74 · art deco 0.58'"). */
internal fun rankStyleClasses(features: FeatureVector): List<ClassMatch> =
    listOf(
        scoreSansGeometric(features),
        scoreSansGrotesque(features),
        scoreSansNeogrotesque(features),
        scoreSansHumanist(features),
        scoreSerifGaralde(features),
        scoreSerifTransitional(features),
        scoreSerifDidone(features),
        scoreSlab(features),
        scoreDisplayArtDeco(features),
        scoreBlackletter(features),
    ).sortedByDescending { it.confidence }

// --- Membership helpers: each maps a raw measurement to a [0, 1] "how well does this match" score. ---

/** 1.0 at [target], fading linearly to 0.0 at [target] ± [tolerance]. */
private fun near(
    value: Double,
    target: Double,
    tolerance: Double,
): Double = (1.0 - abs(value - target) / tolerance).coerceIn(0.0, 1.0)

/** 0.0 at or below [threshold], ramping linearly up to 1.0 at [full]. */
private fun atLeast(
    value: Double,
    threshold: Double,
    full: Double,
): Double = ((value - threshold) / (full - threshold)).coerceIn(0.0, 1.0)

/** 0.0 at or above [threshold], ramping linearly up to 1.0 at [full] (a lower value). */
private fun atMost(
    value: Double,
    threshold: Double,
    full: Double,
): Double = ((threshold - value) / (threshold - full)).coerceIn(0.0, 1.0)

/** 1.0 anywhere in `[lo, hi]`, fading linearly to 0.0 across [softness] outside either edge. */
private fun within(
    value: Double,
    lo: Double,
    hi: Double,
    softness: Double,
): Double =
    when {
        value < lo -> atLeast(value, lo - softness, lo)
        value > hi -> atMost(value, hi + softness, hi)
        else -> 1.0
    }

/** Accumulates a weighted average of membership scores plus the plain-language reasons behind the strong ones (evidence threshold [EVIDENCE_THRESHOLD]), skipping a `null` measurement entirely rather than counting it as a mismatch. */
private class Evidence {
    private var weightedSum = 0.0
    private var weightTotal = 0.0
    val notes = mutableListOf<String>()

    fun addNullable(
        value: Double?,
        weight: Double,
        note: String,
        score: (Double) -> Double,
    ) {
        if (value == null) return
        add(weight, score(value), note)
    }

    fun add(
        weight: Double,
        score: Double,
        note: String,
    ) {
        val clamped = score.coerceIn(0.0, 1.0)
        weightedSum += weight * clamped
        weightTotal += weight
        if (clamped >= EVIDENCE_THRESHOLD) notes += note
    }

    fun confidence(): Double = if (weightTotal <= 0.0) 0.0 else (weightedSum / weightTotal).coerceIn(0.0, 1.0)

    companion object {
        private const val EVIDENCE_THRESHOLD = 0.6
    }
}

private fun FeatureVector.absStress(): Double? = stressAngleDegrees?.let { abs(it) }

/** Adds [storeys]' match against [want] as evidence, at partial credit (0.1) for the opposite storey count, skipped entirely (never counted as a mismatch) when [storeys] is [Storeys.UNKNOWN] -- the same "missing data is not evidence against you" rule [Evidence.addNullable] follows for numeric features. */
private fun Evidence.addStoreys(
    storeys: Storeys,
    want: Storeys,
    weight: Double,
    note: String,
) {
    if (storeys == Storeys.UNKNOWN) return
    add(weight, if (storeys == want) 1.0 else 0.1, note)
}

/**
 * "The o is a circle, the a is one storey, the stroke is one weight" (LESSONS_SCAFFOLD era 8):
 * monoline contrast, near-zero stress (down-weighted -- a monoline ring's thickest diameter is
 * noisy to measure), no serif, single-storey `a`/`g`, a near-circular-to-mildly-squared `o`.
 */
private fun scoreSansGeometric(f: FeatureVector): ClassMatch {
    val e = Evidence()
    e.addNullable(f.contrastRatio, 2.0, "monoline stroke (contrast near 1.0)") { near(it, 1.05, 0.55) }
    e.addNullable(f.absStress(), 0.5, "little or no stress axis") { atMost(it, 20.0, 55.0) }
    e.addNullable(f.hasSerif?.let { if (it) 0.0 else 1.0 }, 2.0, "no serif") { it }
    e.addStoreys(f.storeys, Storeys.SINGLE, 2.0, "single-storey a/g")
    e.addNullable(f.oRoundnessExponent, 2.0, "o close to a circle or a gently rounded square") { near(it, 2.3, 1.6) }
    e.addNullable(
        f.terminalStyle.takeIf { it != TerminalStyle.UNKNOWN }?.let {
            if (it == TerminalStyle.FLAT ||
                it == TerminalStyle.ROUND
            ) {
                1.0
            } else {
                0.15
            }
        },
        0.75,
        "terminal cut, not diagonal",
    ) { it }
    return ClassMatch("sans-geometric", e.confidence(), e.notes)
}

/**
 * "The first sans. A little contrast left, slightly awkward proportions... narrow apertures"
 * (LESSONS_SCAFFOLD era 6): slight contrast, slight stress, no serif, double-storey, narrow-ish
 * apertures, an angled or flat terminal cut.
 */
private fun scoreSansGrotesque(f: FeatureVector): ClassMatch {
    val e = Evidence()
    e.addNullable(f.contrastRatio, 1.5, "slight contrast") { within(it, 1.15, 2.1, 0.5) }
    e.addNullable(f.absStress(), 0.5, "a slight stress axis") { atMost(it, 16.0, 45.0) }
    e.addNullable(f.hasSerif?.let { if (it) 0.0 else 1.0 }, 1.5, "no serif") { it }
    e.addStoreys(f.storeys, Storeys.DOUBLE, 1.0, "double-storey a/g")
    e.addNullable(f.apertureOpenness, 1.0, "narrow apertures") { atMost(it, 0.55, 0.18) }
    e.addNullable(
        f.terminalStyle.takeIf { it != TerminalStyle.UNKNOWN }?.let {
            if (it == TerminalStyle.ANGLED ||
                it == TerminalStyle.FLAT
            ) {
                1.0
            } else {
                0.2
            }
        },
        0.75,
        "cut or angled terminal",
    ) { it }
    return ClassMatch("sans-grotesque", e.confidence(), e.notes)
}

/**
 * "The grotesque tidied into neutrality: horizontal terminals, even colour, closed apertures"
 * (LESSONS_SCAFFOLD era 10): near-zero contrast and stress, closed apertures, a flat/horizontal
 * terminal cut, double-storey.
 */
private fun scoreSansNeogrotesque(f: FeatureVector): ClassMatch {
    val e = Evidence()
    e.addNullable(f.contrastRatio, 2.0, "even colour (contrast close to 1.0)") { near(it, 1.05, 0.4) }
    e.addNullable(f.absStress(), 1.0, "no stress axis") { atMost(it, 10.0, 40.0) }
    e.addNullable(f.hasSerif?.let { if (it) 0.0 else 1.0 }, 1.5, "no serif") { it }
    e.addStoreys(f.storeys, Storeys.DOUBLE, 1.0, "double-storey a/g")
    e.addNullable(
        f.terminalStyle.takeIf { it != TerminalStyle.UNKNOWN }?.let { if (it == TerminalStyle.FLAT) 1.0 else 0.15 },
        1.5,
        "horizontal terminal cut",
    ) { it }
    e.addNullable(f.apertureOpenness, 1.5, "closed apertures") { atMost(it, 0.45, 0.10) }
    return ClassMatch("sans-neogrotesque", e.confidence(), e.notes)
}

/**
 * "A sans that remembers the pen: open apertures, calligraphic proportions" (LESSONS_SCAFFOLD
 * era 9): moderate contrast, a real but modest stress axis, open apertures, double-storey.
 */
private fun scoreSansHumanist(f: FeatureVector): ClassMatch {
    val e = Evidence()
    e.addNullable(f.contrastRatio, 1.5, "moderate contrast, remembering a pen") { within(it, 1.3, 2.3, 0.5) }
    e.addNullable(f.absStress(), 1.5, "a real stress axis") { within(it, 4.0, 24.0, 8.0) }
    e.addNullable(f.hasSerif?.let { if (it) 0.0 else 1.0 }, 1.0, "no serif") { it }
    e.addStoreys(f.storeys, Storeys.DOUBLE, 1.0, "double-storey a/g")
    e.addNullable(f.apertureOpenness, 1.5, "open apertures") { atLeast(it, 0.35, 0.75) }
    return ClassMatch("sans-humanist", e.confidence(), e.notes)
}

/**
 * "The pen angle is still in the letter: oblique stress, a small x-height, bracketed serifs"
 * (LESSONS_SCAFFOLD era 2): serifs, well-bracketed, an oblique stress axis, moderate contrast, a
 * comparatively low x-height.
 */
private fun scoreSerifGaralde(f: FeatureVector): ClassMatch {
    val e = Evidence()
    e.addNullable(f.hasSerif?.let { if (it) 1.0 else 0.0 }, 2.0, "has serifs") { it }
    e.addNullable(f.bracketScore, 1.5, "well-bracketed serifs") { atLeast(it, 0.30, 0.85) }
    e.addNullable(f.absStress(), 2.0, "oblique stress, from the pen") { within(it, 18.0, 42.0, 10.0) }
    e.addNullable(f.contrastRatio, 1.0, "moderate contrast") { within(it, 1.7, 3.3, 0.6) }
    e.addStoreys(f.storeys, Storeys.DOUBLE, 0.75, "double-storey a/g")
    e.addNullable(f.xHeightToCapHeightRatio, 1.0, "small x-height") { atMost(it, 0.66, 0.45) }
    return ClassMatch("serif-garalde", e.confidence(), e.notes)
}

/**
 * "Sharper, higher contrast, the stress standing up. Printing catches up with engraving"
 * (LESSONS_SCAFFOLD era 3): serifs, still bracketed, stress near vertical, higher contrast than
 * garalde.
 */
private fun scoreSerifTransitional(f: FeatureVector): ClassMatch {
    val e = Evidence()
    e.addNullable(f.hasSerif?.let { if (it) 1.0 else 0.0 }, 2.0, "has serifs") { it }
    e.addNullable(f.bracketScore, 1.0, "still bracketed") { atLeast(it, 0.25, 0.75) }
    e.addNullable(f.absStress(), 2.0, "stress standing up, near vertical") { within(it, 3.0, 18.0, 7.0) }
    e.addNullable(f.contrastRatio, 1.5, "higher contrast than a garalde") { within(it, 2.5, 4.5, 0.7) }
    return ClassMatch("serif-transitional", e.confidence(), e.notes)
}

/**
 * "Vertical stress, hairline serifs, maximum contrast. Type stops imitating the hand"
 * (LESSONS_SCAFFOLD era 4): serifs, minimally bracketed (an abrupt hairline, not a curved
 * transition), vertical stress, the highest contrast of any class, often a round/ball terminal.
 */
private fun scoreSerifDidone(f: FeatureVector): ClassMatch {
    val e = Evidence()
    e.addNullable(f.hasSerif?.let { if (it) 1.0 else 0.0 }, 1.5, "has serifs") { it }
    e.addNullable(f.bracketScore, 1.0, "hairline, barely bracketed serifs") { atMost(it, 0.40, 0.05) }
    e.addNullable(f.absStress(), 1.5, "vertical stress") { atMost(it, 10.0, 45.0) }
    e.addNullable(f.contrastRatio, 2.5, "maximum contrast") { atLeast(it, 3.2, 6.5) }
    e.addNullable(
        f.terminalStyle.takeIf { it != TerminalStyle.UNKNOWN }?.let { if (it == TerminalStyle.ROUND) 1.0 else 0.3 },
        0.5,
        "ball terminal",
    ) { it }
    return ClassMatch("serif-didone", e.confidence(), e.notes)
}

/**
 * "Serifs as thick as stems, made to shout from across a street" (LESSONS_SCAFFOLD era 5): serifs,
 * unbracketed (an abrupt, square transition, not a curve), low contrast, near-zero stress.
 */
private fun scoreSlab(f: FeatureVector): ClassMatch {
    val e = Evidence()
    e.addNullable(f.hasSerif?.let { if (it) 1.0 else 0.0 }, 1.5, "has serifs") { it }
    e.addNullable(f.bracketScore, 1.5, "square, unbracketed serifs") { atMost(it, 0.35, 0.02) }
    e.addNullable(f.contrastRatio, 2.0, "low contrast, even colour") { atMost(it, 1.7, 1.0) }
    e.addNullable(f.absStress(), 1.0, "little or no stress") { atMost(it, 12.0, 45.0) }
    return ClassMatch("slab", e.confidence(), e.notes)
}

/**
 * "Geometric bones with theatrical proportions... extreme widths" (LESSONS_SCAFFOLD era 7). None
 * of the nine features names a crossbar height or an inline stripe, so this function only reaches
 * for the two it can: an unusually wide or narrow [FeatureVector.widthClass], plus a geometric
 * (monoline, single-storey, roundish) profile close to [scoreSansGeometric]'s. Documented as this
 * package's weakest class -- [rankStyleClasses] caps it at [WEAK_CLASS_CONFIDENCE_CAP] -- and the
 * validation notes below say plainly how often it is actually found.
 */
private fun scoreDisplayArtDeco(f: FeatureVector): ClassMatch {
    val e = Evidence()
    e.addNullable(f.widthClass, 1.5, "an unusually wide or narrow proportion") { near(it, 0.55, 0.10).let { s -> 1.0 - s } }
    e.addNullable(f.contrastRatio, 0.75, "a geometric, monoline stroke") { near(it, 1.1, 0.5) }
    e.addStoreys(f.storeys, Storeys.SINGLE, 0.75, "single-storey construction")
    e.addNullable(f.oRoundnessExponent, 0.5, "geometric o") { near(it, 2.5, 2.0) }
    val cap = WEAK_CLASS_CONFIDENCE_CAP
    return ClassMatch("display-artdeco", (e.confidence() * cap), e.notes + WEAK_CLASS_NOTE)
}

/**
 * "The first printed letters imitate the scribe: dense, vertical, compressed, every stroke a pen
 * stroke... no round shapes" (LESSONS_SCAFFOLD era 1): a broad-nib `o` is a diamond or rhomboid,
 * not a superellipse at all, which shows up here as [superellipseExponent] being pushed to its
 * own search ceiling rather than as a genuinely large rounded-rectangle exponent -- a real
 * ambiguity this function cannot resolve from the exponent alone, so it also leans on high
 * contrast and tight apertures. Documented as this package's other weak class (see
 * [scoreDisplayArtDeco]'s KDoc); [rankStyleClasses] caps it the same way.
 */
private fun scoreBlackletter(f: FeatureVector): ClassMatch {
    val e = Evidence()
    e.addNullable(f.oRoundnessExponent, 2.0, "o is not round at all (broken, angular strokes)") { atLeast(it, 5.0, 8.0) }
    e.addNullable(f.contrastRatio, 1.5, "high contrast, a broad nib") { atLeast(it, 2.2, 5.5) }
    e.addNullable(f.apertureOpenness, 1.0, "tight texture, narrow counters") { atMost(it, 0.30, 0.05) }
    val cap = WEAK_CLASS_CONFIDENCE_CAP
    return ClassMatch("blackletter", (e.confidence() * cap), e.notes + WEAK_CLASS_NOTE)
}

private const val WEAK_CLASS_CONFIDENCE_CAP = 0.7
private const val WEAK_CLASS_NOTE = "weak signal: none of these 9 features was designed for this class (our heuristic)"
