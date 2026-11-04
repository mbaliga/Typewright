// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.geometry

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The scale, in font units, at which [estimateBoundaryNoise] separates wobble from shape: a boundary is compared with itself
 * smoothed by a Gaussian this wide. Wide enough that the staircase and the grain of a photographed pen line are wobble, narrow
 * enough that a gentle curve (radius 100 or more) moves by under a unit.
 */
const val NOISE_SCALE_UNITS: Double = 10.0

/** Below this noise, in font units, a boundary is clean and [detectOutlineCornersDenoised] leaves it exactly as [detectOutlineCorners] does. */
const val NOISE_FLOOR_UNITS: Double = 0.8

/** How many units of smoothing each unit of measured noise buys (our heuristic, set on the synthetic photographs in the capture tests). */
const val SMOOTHING_PER_NOISE: Double = 8.0

/** The widest smoothing [detectOutlineCornersDenoised] will apply, in font units: past this a person should redraw or retake instead. */
const val MAX_SMOOTHING_UNITS: Double = 18.0

/**
 * The width, in font units, of the Gaussian that picks the slow inward pull of smoothing out of the residuals in
 * [estimateBoundaryNoise]: twice [NOISE_SCALE_UNITS], so the pull of a curve is kept as a whole while grain, which changes from one
 * sample to the next, is not (our heuristic: wider takes less of the grain with it, narrower reads small round shapes as clean sooner).
 */
private const val PULL_SCALE_UNITS: Double = 2.0 * NOISE_SCALE_UNITS

/** A boundary with its corners found after the noise was taken out of it. */
class DenoisedCorners(
    /** The samples (smoothed along each side, corners held) and corner indices [fitOutline] takes. */
    val corners: OutlineCorners,
    /** The wobble measured in the raw boundary, in font units ([estimateBoundaryNoise]). */
    val noise: Double,
    /** The Gaussian width, in font units, the sides were smoothed by; 0 when the boundary was clean enough to leave alone. */
    val smoothingUnits: Double,
)

/**
 * How noisy a closed boundary is: the typical distance, in font units, between its [samples] (re-sampled every [spacing] units)
 * and the same boundary smoothed by a Gaussian [NOISE_SCALE_UNITS] wide. The median of the distances is taken and divided by
 * 0.6745, which turns it into the standard deviation of normally scattered wobble; the median is what keeps the few samples at
 * corners, which smoothing rounds off, from reading as noise. The slow inward pull smoothing gives any curve (a clean circle of
 * radius 50 moves by 1 unit) is taken out first, so a clean round shape reads as clean: a circle of radius 30 reads about half the
 * floor. Only a round feature under about 25 units in radius, smaller than the smoothing scale itself, still reads as grain. A boundary of fewer than 16 samples reads 0.
 *
 * This reads the high-frequency part of the edge only: a slow wave (a hand-drawn side that bows over 100 units) is not noise
 * and is left to the fit tolerance. A clean render reads a few tenths of a unit; a handheld photograph of a pen line one to four.
 */
fun estimateBoundaryNoise(
    samples: List<Vec2>,
    spacing: Double = DEFAULT_OUTLINE_SAMPLE_SPACING,
): Double {
    val n = samples.size
    if (n < 16) return 0.0
    val smooth = gaussianClosed(samples, NOISE_SCALE_UNITS, spacing)
    // Smoothing pulls every curved stretch toward its centre of curvature (a circle of radius R in by about sigma^2 / 2R), and that
    // pull is not grain. It varies slowly along the boundary, grain does not, so the pull is taken out by smoothing the residuals
    // once more and keeping what is left.
    val pull = List(n) { samples[it] - smooth[it] }
    val slow = gaussianClosed(pull, PULL_SCALE_UNITS, spacing)
    val residuals = DoubleArray(n) { (pull[it] - slow[it]).length() }
    residuals.sort()
    return residuals[n / 2] / 0.6745
}

/**
 * [detectOutlineCorners] for a boundary that may be rough. A clean boundary (noise under [NOISE_FLOOR_UNITS]) is handled
 * exactly as [detectOutlineCorners] handles it. A rough one is processed in three steps: the corners are found on a copy
 * smoothed by a Gaussian [SMOOTHING_PER_NOISE] times the noise wide (at most [MAX_SMOOTHING_UNITS]), judged over a look
 * distance that grows with the smoothing so a wobble is not a corner; each side between two corners is then smoothed by the
 * same Gaussian on its own, with both ends held where they were, so a straight side is straightened without a corner being
 * rounded; and those smoothed sides, joined, are the samples [fitOutline] fits.
 */
fun detectOutlineCornersDenoised(
    polyline: List<Vec2>,
    params: OutlineFitParameters = OutlineFitParameters(),
): DenoisedCorners {
    val samples = resampleClosed(polyline, params.sampleSpacing)
    val n = samples.size
    val noise = estimateBoundaryNoise(samples, params.sampleSpacing)
    if (n < 16 || noise < NOISE_FLOOR_UNITS) return DenoisedCorners(detectOutlineCorners(polyline, params), noise, 0.0)
    val sigma = min(MAX_SMOOTHING_UNITS, max(SMOOTHING_PER_NOISE * noise, 2.0 * params.sampleSpacing))
    val lookUnits = max(params.cornerLookUnits, 2.0 * sigma)
    val global = gaussianClosed(samples, sigma, params.sampleSpacing)
    val step = pathLength(global + global.first()) / n
    val k = max(1, (lookUnits / step).roundToInt()).coerceAtMost(n / 4)
    val turns = DoubleArray(n) { i -> turnAt(global[(i - k + n) % n], global[i], global[(i + k) % n]) }
    val corners = cornersFromTurns(turns, params.cornerTurnDegrees * PI / 180.0)
    val smoothed =
        if (corners.isEmpty()) {
            global
        } else {
            val out = samples.toMutableList()
            for (c in corners.indices) {
                val from = corners[c]
                val length = if (corners.size == 1) n else (corners[(c + 1) % corners.size] - from + n) % n
                val arc = List(length + 1) { samples[(from + it) % n] }
                val done = gaussianOpen(arc, sigma, params.sampleSpacing)
                for (i in 0 until length) out[(from + i) % n] = done[i]
            }
            out
        }
    return DenoisedCorners(OutlineCorners(smoothed, corners), noise, sigma)
}

/**
 * The most the outline [contours] and the traced [boundary] loops stray from each other, in font units: the larger of the
 * greatest distance from any boundary vertex to the outline and from any vertex of the outline's own flattened curves to the
 * boundary. Both ways, because an outline that leaves out a stroke of the drawing is as far from it as one that adds a stroke.
 * Measured against the boundary as traced, not against anything the fitter smoothed, so the figure is the honest distance
 * between the person's drawing and what will be written. Zero when either side is empty.
 */
fun outlineDeviation(
    boundary: List<List<Vec2>>,
    contours: List<Contour>,
): Double {
    val outline = contours.map { flatten(it) }.filter { it.size >= 2 }
    val traced = boundary.filter { it.size >= 2 }
    if (outline.isEmpty() || traced.isEmpty()) return 0.0
    return max(farthestFrom(traced, outline), farthestFrom(outline, traced))
}

private fun flatten(contour: Contour): List<Vec2> {
    val dense = mutableListOf<Vec2>()
    for (segment in contour.segments()) {
        val steps = if (segment is CurveSegment.Line) 1 else 32
        for (s in 0 until steps) dense += segment.pointAt(s.toDouble() / steps)
    }
    return dense
}

/** The greatest, over every vertex of [from], of its distance to the nearest edge of any closed loop in [to]. */
private fun farthestFrom(
    from: List<List<Vec2>>,
    to: List<List<Vec2>>,
): Double {
    var worst = 0.0
    for (loop in from) {
        for (p in loop) {
            var best = Double.MAX_VALUE
            for (target in to) {
                for (i in target.indices) {
                    val a = target[i]
                    val ab = target[(i + 1) % target.size] - a
                    val len2 = ab.dot(ab)
                    val t = if (len2 > 1e-12) ((p - a).dot(ab) / len2).coerceIn(0.0, 1.0) else 0.0
                    val d = (a + ab * t - p).length()
                    if (d < best) best = d
                }
            }
            worst = max(worst, best)
        }
    }
    return worst
}

private fun kernel(
    sigma: Double,
    spacing: Double,
): DoubleArray {
    val radius = max(1, (3.0 * sigma / spacing).roundToInt())
    val weights = DoubleArray(2 * radius + 1) { exp(-((it - radius) * spacing).let { d -> d * d } / (2.0 * sigma * sigma)) }
    val total = weights.sum()
    for (i in weights.indices) weights[i] /= total
    return weights
}

/** [points] (closed, evenly spaced by [spacing]) smoothed by a Gaussian of standard deviation [sigma] along the boundary. */
internal fun gaussianClosed(
    points: List<Vec2>,
    sigma: Double,
    spacing: Double,
): List<Vec2> {
    val n = points.size
    val weights = kernel(sigma, spacing)
    val radius = weights.size / 2
    return List(n) { i ->
        var x = 0.0
        var y = 0.0
        for (k in -radius..radius) {
            val p = points[((i + k) % n + n) % n]
            x += weights[k + radius] * p.x
            y += weights[k + radius] * p.y
        }
        Vec2(x, y)
    }
}

/**
 * [points] (an open run, evenly spaced) smoothed by a Gaussian of standard deviation [sigma], both ends held where they are.
 * Past an end the run is continued by turning it about the end point (an odd reflection), which keeps the end fixed and the
 * run's direction there, so a straight side stays straight to its last sample instead of curling in.
 */
internal fun gaussianOpen(
    points: List<Vec2>,
    sigma: Double,
    spacing: Double,
): List<Vec2> {
    val m = points.size
    if (m < 3) return points
    val weights = kernel(sigma, spacing)
    val radius = weights.size / 2

    fun at(index: Int): Vec2 =
        when {
            index < 0 -> points[0] * 2.0 - points[min(-index, m - 1)]
            index > m - 1 -> points[m - 1] * 2.0 - points[max(2 * (m - 1) - index, 0)]
            else -> points[index]
        }
    return List(m) { i ->
        if (i == 0 || i == m - 1) {
            points[i]
        } else {
            var x = 0.0
            var y = 0.0
            for (k in -radius..radius) {
                val p = at(i + k)
                x += weights[k + radius] * p.x
                y += weights[k + radius] * p.y
            }
            Vec2(x, y)
        }
    }
}
