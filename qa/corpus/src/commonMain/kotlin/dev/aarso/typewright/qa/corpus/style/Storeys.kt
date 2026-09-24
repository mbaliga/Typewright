package dev.aarso.typewright.qa.corpus.style

import dev.aarso.typewright.core.geometry.Glyph
import dev.aarso.typewright.core.geometry.signedArea
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
 *   so contour count cannot tell them apart. Instead this compares the main counter's own ink
 *   height to the whole glyph's: a single-storey `a`'s counter is close to circular and nearly
 *   fills the x-height (Futura's bowl reaches close to both the baseline and the x-height line);
 *   a double-storey `a`'s counter is the lower bowl only, with the arm/link rising above it, so it
 *   covers a visibly smaller fraction. [A_COUNTER_HEIGHT_RATIO_THRESHOLD] (our heuristic, law 5)
 *   was set by inspecting the ten Lineages exemplars' actual `a`s (see this module's validation
 *   notes) rather than derived from a published rule.
 *
 * [combineStoreys] prefers `g`'s answer when available.
 */
private const val A_COUNTER_HEIGHT_RATIO_THRESHOLD = 0.80

internal fun storeysFromG(g: Glyph): Storeys =
    when {
        g.contours.isEmpty() -> Storeys.UNKNOWN
        g.contours.size >= 3 -> Storeys.DOUBLE
        else -> Storeys.SINGLE
    }

internal fun storeysFromA(a: Glyph): Storeys {
    if (a.contours.size < 2) return Storeys.UNKNOWN
    val outer = a.outerContour() ?: return Storeys.UNKNOWN
    val counter = a.contours.filter { it !== outer }.maxByOrNull { abs(it.signedArea()) } ?: return Storeys.UNKNOWN
    val glyphBounds = a.inkBounds() ?: return Storeys.UNKNOWN
    val counterBounds = counter.tightBounds() ?: return Storeys.UNKNOWN
    if (glyphBounds.height <= 0.0) return Storeys.UNKNOWN
    val ratio = counterBounds.height / glyphBounds.height
    return if (ratio >= A_COUNTER_HEIGHT_RATIO_THRESHOLD) Storeys.SINGLE else Storeys.DOUBLE
}

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
