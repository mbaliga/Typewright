// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.trace

import kotlin.math.sqrt

/** The steepest slope of a unit step edge blurred by a Gaussian of deviation s is `1 / (s * sqrt(2 pi))` per pixel; this is `sqrt(2 pi)`. */
private const val SQRT_TWO_PI = 2.5066282746310002

/**
 * How soft the edges of the ink in [cleaned] are, as the standard deviation of the blur that would produce them, in pixels; 0
 * when there is no ink or too few edge pixels to tell. At every pixel on the border of the ink mask the darkness changes by
 * some amount per pixel; a step edge blurred by a Gaussian of deviation s, between paper and ink of darkness d, is steepest
 * at its middle, where the darkness changes by `d / (s sqrt(2 pi))` per pixel, and the mask's border lies at that middle. So the
 * median steepness over the whole border and the ink's own darkness give s. Noise and a slanted edge make the steepest slope
 * read steeper than it is, so the figure is a lower bound on the softness. This is the photograph's softness as the trace sees it (lens, focus, resampling), measured from the
 * ink and not from anything printed on the sheet, so it holds for a scan or a file as well.
 */
fun estimateEdgeSoftnessPx(cleaned: CleanedCell): Double {
    val w = cleaned.width
    val h = cleaned.height
    val ink = cleaned.mask.ink
    val rates = ArrayList<Double>()
    val plateau = ArrayList<Double>()
    for (y in 1 until h - 1) {
        for (x in 1 until w - 1) {
            val i = y * w + x
            val onInk = ink[i]
            val border = ink[i - 1] != onInk || ink[i + 1] != onInk || ink[i - w] != onInk || ink[i + w] != onInk
            if (border) {
                val gx = (cleaned.darkness[i + 1] - cleaned.darkness[i - 1]) / 2.0
                val gy = (cleaned.darkness[i + w] - cleaned.darkness[i - w]) / 2.0
                rates += sqrt(gx * gx + gy * gy)
            } else if (onInk && ink[i - w - 1] && ink[i - w + 1] && ink[i + w - 1] && ink[i + w + 1]) {
                plateau += cleaned.darkness[i]
            }
        }
    }
    if (rates.size < 30 || plateau.size < 10) return 0.0
    rates.sort()
    plateau.sort()
    val rate = rates[rates.size / 2]
    val darkness = plateau[plateau.size / 2]
    if (rate <= 1e-6) return 0.0
    return (darkness / (rate * SQRT_TWO_PI)).coerceIn(0.0, MAX_SOFTNESS_PX)
}

/** The softest edge, in pixels, that is believed: a larger reading is a smear, not a lens. */
private const val MAX_SOFTNESS_PX = 6.0
