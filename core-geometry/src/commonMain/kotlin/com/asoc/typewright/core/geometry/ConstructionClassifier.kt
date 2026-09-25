// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.geometry

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * A fitted contour's construction (`TYPEWRIGHT_BUILD_BRIEF.md` §8.5: "any per-glyph target the
 * workbook quotes must be classified by construction ... before it judges count"). The four
 * classes §8.5 names:
 * - [POLYGONAL]: every segment is effectively straight — no meaningful curvature anywhere (`T`,
 *   `H`).
 * - [ELLIPTICAL]: a good fit to a true ellipse (superellipse exponent close to 2).
 * - [SUPERELLIPTICAL]: a good fit to a superellipse whose exponent is meaningfully different from
 *   2, continuously-varying curvature throughout (no long straight run).
 * - [ROUNDED_RECTANGLE]: straight sides meeting sharply-curved short corners — Hyle Deco's `o`.
 */
enum class ConstructionKind {
    POLYGONAL,
    ELLIPTICAL,
    SUPERELLIPTICAL,
    ROUNDED_RECTANGLE,
}

/**
 * [classifyConstruction]'s result: the decided [kind], plus the measurements it was decided from,
 * so a caller (or a person reading a report) can see the evidence rather than trust a bare label —
 * CLAUDE.md law 5's "our heuristic" spirit, applied to a classifier rather than a numeric check.
 */
data class ConstructionClassification(
    val kind: ConstructionKind,
    /** The fraction (`0.0`-`1.0`) of this contour's own arc length covered by effectively-straight segments. */
    val straightPerimeterFraction: Double,
    /**
     * The best-fit superellipse exponent ([fitSuperellipseExponent]), or `null` when [kind] is
     * [ConstructionKind.POLYGONAL] (never computed for a shape with no curvature to fit).
     */
    val superellipseExponent: Double?,
    /** A one-line, plain-language reason, for a report or a UI (`TYPEWRIGHT_BUILD_BRIEF.md` law 5: never an unexplained number). */
    val evidence: String,
)

/**
 * [ConstructionClassifierParameters.straightnessToleranceUnits]'s default; see
 * [DEFAULT_STRAIGHTNESS_TOLERANCE_UNITS] (`SegmentStraightness.kt`) for the shared rationale this
 * classifier and the type-constraints stage both use the same value for.
 */
const val DEFAULT_CONSTRUCTION_STRAIGHTNESS_TOLERANCE: Double = DEFAULT_STRAIGHTNESS_TOLERANCE_UNITS

/**
 * [ConstructionClassifierParameters.roundedRectangleStraightFraction]'s default: 5% of a
 * contour's own perimeter. Tuned as one global choice: the Schneider fitter's own error tolerance
 * ([DEFAULT_FIT_ERROR_TOLERANCE]) only lets a genuinely smooth, continuously-curving shape (a
 * true ellipse or a higher-exponent superellipse alike) deviate a couple of font units from its
 * fitted curve over a short span — nowhere near enough slack to fake a long *flat* run without
 * the underlying geometry actually having one — so any straight run clearing even a modest
 * fraction of the perimeter is a reliable, non-coincidental sign of an actual flat side, not
 * curvature-fitting noise.
 */
const val DEFAULT_ROUNDED_RECTANGLE_STRAIGHT_FRACTION: Double = 0.05

/**
 * [ConstructionClassifierParameters.ellipticalExponentTolerance]'s default: a fitted superellipse
 * exponent within 0.3 of 2.0 reads as [ConstructionKind.ELLIPTICAL] rather than
 * [ConstructionKind.SUPERELLIPTICAL].
 */
const val DEFAULT_ELLIPTICAL_EXPONENT_TOLERANCE: Double = 0.3

/**
 * Tuning for [classifyConstruction]. One fixed, global set of numbers, never adjusted per glyph —
 * the same anti-gaming rule P2a's [CubicFitParameters] and P2b's [TypeConstraintParameters]
 * document.
 */
data class ConstructionClassifierParameters(
    val straightnessToleranceUnits: Double = DEFAULT_CONSTRUCTION_STRAIGHTNESS_TOLERANCE,
    val roundedRectangleStraightFraction: Double = DEFAULT_ROUNDED_RECTANGLE_STRAIGHT_FRACTION,
    val ellipticalExponentTolerance: Double = DEFAULT_ELLIPTICAL_EXPONENT_TOLERANCE,
)

/**
 * Classifies [contour]'s construction (`TYPEWRIGHT_BUILD_BRIEF.md` §8.5), from its own segment
 * geometry alone: no name, point count or any other glyph-specific signal is read (P2a's
 * anti-gaming rule, carried over to this classifier).
 *
 * **Method, in order:**
 * 1. Measure each segment's arc length (a dense sample of [CurveSegment.pointAt], not the chord —
 *    a real curved corner's chord underestimates the path length it actually sweeps) and whether
 *    it [is effectively straight][CurveSegment.Cubic.isEffectivelyStraight].
 * 2. **Every segment straight** → [ConstructionKind.POLYGONAL]: there is no curvature anywhere to
 *    fit a superellipse to in the first place.
 * 3. **A straight run covering at least [ConstructionClassifierParameters.roundedRectangleStraightFraction]
 *    of the perimeter, but not all of it** → [ConstructionKind.ROUNDED_RECTANGLE]: exactly the
 *    "long straight run meeting short curved corners" signal brief §8.5 asks for, distinct from a
 *    superellipse's continuously-varying curvature.
 * 4. **Otherwise** (some curvature everywhere, no long flat run) → [fitSuperellipseExponent]; a
 *    result close to 2 is [ConstructionKind.ELLIPTICAL], anything meaningfully different is
 *    [ConstructionKind.SUPERELLIPTICAL].
 *
 * [contour] must be [CurveFormat.CUBIC] (the P2 fitter's own output format, typically after
 * [applyTypeConstraints] as well).
 */
fun classifyConstruction(
    contour: Contour,
    params: ConstructionClassifierParameters = ConstructionClassifierParameters(),
): ConstructionClassification {
    require(contour.format == CurveFormat.CUBIC) {
        "classifyConstruction operates on a fitted CUBIC contour, was ${contour.format}"
    }
    val segments = contour.cubicSegments()
    if (segments.isEmpty()) {
        return ConstructionClassification(ConstructionKind.POLYGONAL, 1.0, null, "empty contour: no segments to classify")
    }

    val arcLengths = segments.map { it.approximateArcLength() }
    val totalLength = arcLengths.sum()
    val straightFlags = segments.map { it.isEffectivelyStraight(params.straightnessToleranceUnits) }
    val straightLength = arcLengths.filterIndexed { index, _ -> straightFlags[index] }.sum()
    val straightFraction = if (totalLength > 0.0) straightLength / totalLength else 1.0

    if (straightFlags.all { it }) {
        return ConstructionClassification(
            kind = ConstructionKind.POLYGONAL,
            straightPerimeterFraction = straightFraction,
            superellipseExponent = null,
            evidence =
                "every one of ${segments.size} segments is effectively straight " +
                    "(within ${params.straightnessToleranceUnits} units of its own chord): no meaningful curvature anywhere",
        )
    }

    if (straightFraction >= params.roundedRectangleStraightFraction) {
        // Kotlin common has no locale-aware float formatter (this module stays JVM/Wasm-common,
        // CLAUDE.md law 2), so the evidence string rounds to a whole percentage point itself
        // rather than reach for a java.util.Formatter-backed "%.1f".
        val percent = (straightFraction * 100).roundToTenths()
        return ConstructionClassification(
            kind = ConstructionKind.ROUNDED_RECTANGLE,
            straightPerimeterFraction = straightFraction,
            superellipseExponent = null,
            evidence =
                "straight sides ($percent% of perimeter) meeting sharply-curved short corners, " +
                    "distinct from a superellipse's continuously-varying curvature",
        )
    }

    val exponent = fitSuperellipseExponent(contour)
    return if (exponent != null && abs(exponent - 2.0) <= params.ellipticalExponentTolerance) {
        ConstructionClassification(
            kind = ConstructionKind.ELLIPTICAL,
            straightPerimeterFraction = straightFraction,
            superellipseExponent = exponent,
            evidence =
                "best-fit superellipse exponent $exponent is within ${params.ellipticalExponentTolerance} of 2.0 " +
                    "(a true ellipse), with curvature spread continuously across the whole outline",
        )
    } else {
        ConstructionClassification(
            kind = ConstructionKind.SUPERELLIPTICAL,
            straightPerimeterFraction = straightFraction,
            superellipseExponent = exponent,
            evidence =
                if (exponent != null) {
                    "best-fit superellipse exponent $exponent differs meaningfully from 2.0 (a true ellipse), " +
                        "with curvature spread continuously across the whole outline"
                } else {
                    "curvature is spread continuously across the whole outline (no long straight run), " +
                        "but no stable superellipse exponent fit this shape well"
                },
        )
    }
}

/**
 * This non-negative percentage, rounded to one decimal place and rendered without a locale-aware
 * formatter (Kotlin common has none — CLAUDE.md law 2 keeps this module off `java.util.Formatter`)
 * — used only to build [classifyConstruction]'s human-readable evidence string.
 */
private fun Double.roundToTenths(): String {
    val tenths = kotlin.math.round(this * 10.0).toInt()
    return "${tenths / 10}.${tenths % 10}"
}

/**
 * This segment's arc length, approximated by summing the chord lengths of [samples] evenly
 * `t`-spaced sub-points — a cheap, general stand-in for true arc-length integration, adequate for
 * this classifier's coarse straight/curved-fraction ratio (the same approximation
 * `qa/corpus`'s [com.asoc.typewright.qa.corpus.style.sampleContourPoints]-based measurements
 * already make elsewhere in this codebase for comparable coarse ratios).
 */
private fun CurveSegment.approximateArcLength(samples: Int = 8): Double {
    var total = 0.0
    var previous = pointAt(0.0)
    for (step in 1..samples) {
        val t = step.toDouble() / samples
        val current = pointAt(t)
        total += (current - previous).length()
        previous = current
    }
    return total
}

// -------------------------------------------------------------------------------------------
// Superellipse exponent fit.
//
// Ported from qa/corpus/.../style/Roundness.kt's superellipseExponent (read per this task's
// instructions): REUSED as-is are the grid-search idea (a coarse sweep over the exponent range,
// then one round of finer local refinement around the coarse winner) and the sum-of-squared-error
// objective `sum (|x/a|^n + |y/b|^n - 1)^2` itself, both copied with the same MIN_EXPONENT (1.2),
// MAX_EXPONENT (8.0) and COARSE_STEP (0.1) constants as that file. WRITTEN FRESH: the bounds and
// sample-point computation. Roundness.kt is Glyph-shaped (it calls a `Glyph.outerContour()` named
// 'o' and depends on qa/corpus's own internal `Bounds`/`tightBounds`, both wrong-direction for
// core-geometry to depend on — see this task's shared instructions), so this version works
// directly off one core-geometry [Contour]'s own [CurveSegment.pointAt] samples and
// [Contour.extrema] (P1a) for its bounds, with no Glyph, no qa/corpus type, and no assumption
// about which glyph the contour came from.
// -------------------------------------------------------------------------------------------

private const val SUPERELLIPSE_MIN_EXPONENT = 1.2
private const val SUPERELLIPSE_MAX_EXPONENT = 8.0
private const val SUPERELLIPSE_COARSE_STEP = 0.1

/**
 * The best-fit superellipse exponent `n` in `|x/a|^n + |y/b|^n = 1` for [contour], where `a`/`b`
 * are half this contour's own tight bounding box width/height (via [Contour.segments]' endpoints
 * plus [Contour.extrema] — P1a's own extrema detector, so a curve that bulges past its anchor
 * points is measured exactly rather than approximated by the control-point polygon) and `x`/`y`
 * are dense sample points relative to that box's centre. `null` for a degenerate contour with no
 * positive-area bounds. See this section's header comment for exactly what was ported from
 * `qa/corpus`'s `Roundness.kt` versus written fresh for this module.
 */
internal fun fitSuperellipseExponent(contour: Contour): Double? {
    val bounds = contour.tightBoundsForSuperellipseFit() ?: return null
    val (minX, minY, maxX, maxY) = bounds
    val a = (maxX - minX) / 2.0
    val b = (maxY - minY) / 2.0
    if (a <= 0.0 || b <= 0.0) return null
    val centerX = (minX + maxX) / 2.0
    val centerY = (minY + maxY) / 2.0

    val samples = contour.segments().flatMap { segment -> (0 until 8).map { segment.pointAt(it / 8.0) } }
    if (samples.isEmpty()) return null

    fun sumSquaredError(n: Double): Double =
        samples.sumOf { point ->
            val nx = abs((point.x - centerX) / a).pow(n)
            val ny = abs((point.y - centerY) / b).pow(n)
            val residual = nx + ny - 1.0
            residual * residual
        }

    var bestExponent = SUPERELLIPSE_MIN_EXPONENT
    var bestError = Double.POSITIVE_INFINITY
    var n = SUPERELLIPSE_MIN_EXPONENT
    while (n <= SUPERELLIPSE_MAX_EXPONENT) {
        val error = sumSquaredError(n)
        if (error < bestError) {
            bestError = error
            bestExponent = n
        }
        n += SUPERELLIPSE_COARSE_STEP
    }

    val fineStep = SUPERELLIPSE_COARSE_STEP / 10.0
    var refined = (bestExponent - SUPERELLIPSE_COARSE_STEP).coerceAtLeast(SUPERELLIPSE_MIN_EXPONENT)
    val high = (bestExponent + SUPERELLIPSE_COARSE_STEP).coerceAtMost(SUPERELLIPSE_MAX_EXPONENT)
    while (refined <= high) {
        val error = sumSquaredError(refined)
        if (error < bestError) {
            bestError = error
            bestExponent = refined
        }
        refined += fineStep
    }
    return bestExponent
}

/**
 * `(minX, minY, maxX, maxY)`, as a plain 4-tuple — this file's own minimal stand-in for
 * `qa/corpus`'s internal `Bounds`, which core-geometry cannot depend on.
 */
private data class TightBounds(
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double,
)

/**
 * This contour's exact bounds: every segment endpoint plus every [Contour.extrema] location, so a
 * curve's own bulge (never just its control-point polygon) is measured. `null` for an empty contour.
 */
private fun Contour.tightBoundsForSuperellipseFit(): TightBounds? {
    var minX = Double.POSITIVE_INFINITY
    var minY = Double.POSITIVE_INFINITY
    var maxX = Double.NEGATIVE_INFINITY
    var maxY = Double.NEGATIVE_INFINITY
    var any = false

    fun include(p: Vec2) {
        any = true
        minX = min(minX, p.x)
        minY = min(minY, p.y)
        maxX = max(maxX, p.x)
        maxY = max(maxY, p.y)
    }
    for (segment in segments()) {
        include(segment.start)
        include(segment.end)
    }
    for (extremum in extrema()) include(extremum.segment.pointAt(extremum.t))
    return if (!any) null else TightBounds(minX, minY, maxX, maxY)
}
