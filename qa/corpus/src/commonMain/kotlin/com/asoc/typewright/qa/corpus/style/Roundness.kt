// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.corpus.style

import com.asoc.typewright.core.geometry.Glyph
import kotlin.math.abs
import kotlin.math.pow

/**
 * `o`'s roundness as a superellipse exponent (brief 8.4): the best-fit `n` in
 * `|x/a|^n + |y/b|^n = 1`, where `a`/`b` are half the outer contour's ink width/height and `x`/`y`
 * are each sample point relative to the ink-bounds centre. `n = 2` is a true ellipse; larger `n`
 * trends toward a rounded rectangle, and the search is capped at [MAX_EXPONENT] (a very high fit,
 * at the cap, means "not superelliptical at all" -- a blackletter or lombardic `o` built from
 * straight diamond facets fits this family badly at any `n` and pushes the search to its ceiling,
 * which [StyleScorer] reads as its own signal).
 *
 * The search is a coarse grid over `n` (step [coarseStep]) minimising the sum of squared errors
 * `(|x/a|^n + |y/b|^n - 1)^2` over [sampleContourPoints]' points, then one round of local
 * refinement at a tenth of the step -- "a simple search", per the brief, not a general nonlinear
 * solver, since the error surface here is well-behaved (unimodal in practice for every glyph this
 * package tested against) over the sensible exponent range.
 */
private const val MIN_EXPONENT = 1.2
private const val MAX_EXPONENT = 8.0
private const val COARSE_STEP = 0.1

// Public (P6, `ui`'s Anatomy Lens): com.asoc.typewright.ui.learn.AnatomyLensData wires this
// function to the "roundness" lens term on the user's own `o`, so it has to cross the
// `:qa:corpus` module boundary -- see that file's KDoc for the rest of the wiring.
fun superellipseExponent(o: Glyph): Double? {
    val outer = o.outerContour() ?: return null
    val bounds = outer.tightBounds() ?: return null
    val a = bounds.width / 2.0
    val b = bounds.height / 2.0
    if (a <= 0.0 || b <= 0.0) return null
    val cx = bounds.centerX
    val cy = bounds.centerY
    val samples = sampleContourPoints(outer, perSegment = 8)
    if (samples.isEmpty()) return null

    fun sumSquaredError(n: Double): Double =
        samples.sumOf { p ->
            val nx = abs((p.x - cx) / a).pow(n)
            val ny = abs((p.y - cy) / b).pow(n)
            val residual = nx + ny - 1.0
            residual * residual
        }

    var bestN = MIN_EXPONENT
    var bestError = Double.POSITIVE_INFINITY
    var n = MIN_EXPONENT
    while (n <= MAX_EXPONENT) {
        val error = sumSquaredError(n)
        if (error < bestError) {
            bestError = error
            bestN = n
        }
        n += COARSE_STEP
    }

    val fineStep = COARSE_STEP / 10.0
    var refined = (bestN - COARSE_STEP).coerceAtLeast(MIN_EXPONENT)
    val hi = (bestN + COARSE_STEP).coerceAtMost(MAX_EXPONENT)
    while (refined <= hi) {
        val error = sumSquaredError(refined)
        if (error < bestError) {
            bestError = error
            bestN = refined
        }
        refined += fineStep
    }
    return bestN
}
