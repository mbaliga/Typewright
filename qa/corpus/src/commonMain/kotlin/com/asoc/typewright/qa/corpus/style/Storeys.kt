// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.corpus.style

import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.core.geometry.signedArea
import kotlin.math.abs

/**
 * Single- versus double-storey `a`/`g` (brief 8.4: "detect by contour count and topology"). The
 * two letters need different rules because contour count alone only separates them cleanly for
 * `g`:
 *
 * - [storeysFromG]: a double-storey `g` (Garamond, Times) encloses two counters (the upper bowl
 *   and the lower loop) for 3 contours total (outline + 2 counters); a single-storey `g` (Futura)
 *   encloses one, for 2. This is a reliable topological signal, so it is this package's primary
 *   one.
 * - [storeysFromA]: both constructions usually have exactly 2 contours (outline + one counter),
 *   so contour count cannot tell them apart. Instead this reads the ink above the main counter on
 *   a vertical line through it: a two-storey `a` crosses the top of its lower bowl and then the
 *   arm (two runs, with the open aperture between them); a one-storey `a` crosses only the top of
 *   its round bowl. [A_COUNTER_HEIGHT_RATIO_THRESHOLD] and [SINGLE_TOP_STROKE_MAX] (our heuristic,
 *   law 5) only break ties when the line meets no ink, or one solid run, above the counter.
 *
 * [combineStoreys] prefers `g`'s answer when available.
 */
private const val A_COUNTER_HEIGHT_RATIO_THRESHOLD = 0.80

// Public (P6, `ui`'s Anatomy Lens): com.asoc.typewright.ui.learn.AnatomyLensData wires these two
// functions to the "storeys" lens term on the user's own `a`/`g`, so they have to cross the
// `:qa:corpus` module boundary -- see that file's KDoc for the rest of the wiring.
fun storeysFromG(g: Glyph): Storeys =
    when {
        g.contours.isEmpty() -> Storeys.UNKNOWN
        g.contours.size >= 3 -> Storeys.DOUBLE
        else -> Storeys.SINGLE
    }

fun storeysFromA(a: Glyph): Storeys {
    if (a.contours.size < 2) return Storeys.UNKNOWN
    val outer = a.outerContour() ?: return Storeys.UNKNOWN
    val counter = a.contours.filter { it !== outer }.maxByOrNull { abs(it.signedArea()) } ?: return Storeys.UNKNOWN
    val glyphBounds = a.inkBounds() ?: return Storeys.UNKNOWN
    val counterBounds = counter.tightBounds() ?: return Storeys.UNKNOWN
    if (glyphBounds.height <= 0.0) return Storeys.UNKNOWN
    // Read the ink above the counter on a vertical line through it. A two-storey a has two runs
    // there: the top of the bowl, then the arm across the open space above it. A one-storey a
    // has one: the top of its single round bowl. The counter-height ratio alone (the earlier rule)
    // read most regular-weight one-storey a's as two-storey, because a heavier stroke shortens
    // the counter; it stays as the tie-break for a single solid run.
    val runsAbove =
        listOf(0.5, 0.35).maxOf { fraction ->
            val x = counterBounds.minX + counterBounds.width * fraction
            inkIntervals(a.lineCrossings(Vec2(x, glyphBounds.minY - 10.0), Vec2(0.0, 1.0)))
                .count { run -> glyphBounds.minY - 10.0 + run.start >= counterBounds.maxY - 1.0 }
        }
    return when {
        runsAbove >= 2 -> {
            Storeys.DOUBLE
        }

        runsAbove == 0 -> {
            if (counterBounds.height / glyphBounds.height >=
                A_COUNTER_HEIGHT_RATIO_THRESHOLD
            ) {
                Storeys.SINGLE
            } else {
                Storeys.DOUBLE
            }
        }

        else -> {
            val aboveCounter = (glyphBounds.maxY - counterBounds.maxY) / glyphBounds.height
            if (aboveCounter <= SINGLE_TOP_STROKE_MAX) Storeys.SINGLE else Storeys.DOUBLE
        }
    }
}

/** The most of an `a`'s height one top stroke above a one-storey bowl takes; more than this is a solid upper storey. */
private const val SINGLE_TOP_STROKE_MAX = 0.3

/** `g`'s answer when known (the more reliable, topological signal per this file's KDoc); `a`'s otherwise. [Storeys.UNKNOWN] if both are. */
internal fun combineStoreys(
    fromA: Storeys,
    fromG: Storeys,
): Storeys =
    when {
        fromG != Storeys.UNKNOWN -> fromG
        fromA != Storeys.UNKNOWN -> fromA
        else -> Storeys.UNKNOWN
    }
