// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.trace.capture

import com.asoc.typewright.engine.trace.GrayscaleRaster
import com.asoc.typewright.engine.trace.estimatePaperLevel
import com.asoc.typewright.scripts.sheets.MmRect
import com.asoc.typewright.scripts.sheets.SheetLayout
import com.asoc.typewright.scripts.sheets.SheetRenderer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Where the printed guides lie over a warped cell, so they can be divided out of the photograph instead of merely being left
 * under the ink threshold.
 *
 * A guide is a pale line printed on the paper, and a good drawing sits right on it: the flat top of a T is on the cap line, the
 * bottom of an o on the baseline. Where an ink edge crosses the pale line the pixel is part ink and part pale line, and a
 * threshold that does not know about the line puts the edge half a pixel out, which is enough to turn a straight side into a
 * wobble the fitter then has to spend nodes on. The sheet's geometry says exactly where each guide is, how wide and whether it
 * is dashed, so [coverage] renders that, softened by the photograph's own blur, and [correct] divides the paper-and-guide
 * transmittance `1 - strength * coverage` out of the warped pixels before anything is thresholded.
 *
 * How strongly the guides printed ([estimateStrength]) is measured from the photograph itself, pooled over every cell of the
 * sheet, because a printer's pale grey is not the nominal one and a sheet printed without guides must not get phantom ones.
 */
internal object PrintedGuides {
    /** A strength under this is no guides at all (our heuristic: the pooled estimate on a sheet printed without guides reads about 0.01). */
    const val MIN_STRENGTH: Double = 0.04

    /** The share of full strength beyond which a pixel is taken as lying on a guide when the strength is estimated. */
    private const val ON_GUIDE: Double = 0.5

    /** The darkness above which a pixel is ink or text, not a guide, when the strength is estimated. */
    private const val NOT_A_GUIDE_DARKNESS: Double = 0.35

    /**
     * For each pixel of a [width] by [height] warp of [area] at [pxPerMm], the share (0 to 1) of a full-strength guide that lies
     * over it, blurred by a Gaussian of [blurSigmaMm]; zero everywhere on a sheet printed without guides.
     */
    fun coverage(
        layout: SheetLayout,
        box: MmRect,
        area: MmRect,
        width: Int,
        height: Int,
        pxPerMm: Double,
        blurSigmaMm: Double,
    ): DoubleArray {
        val sigma = sqrt(blurSigmaMm * blurSigmaMm + 0.05 * 0.05).coerceAtLeast(0.03)
        val frame = layout.frame
        val out = DoubleArray(width * height)
        val columnsU = DoubleArray(width) { area.left + (it + 0.5) / pxPerMm }
        val rowsV = DoubleArray(height) { area.top + (it + 0.5) / pxPerMm }
        for (line in layout.guides.lines) {
            val y = box.top + frame.boxMm(0.0, line.y.toDouble()).y
            val lineWidth = if (line.solid) SheetRenderer.GUIDE_SOLID_MM else SheetRenderer.GUIDE_DASHED_MM
            val dash = if (line.solid) null else SheetRenderer.GUIDE_DASH_MM
            val along = DoubleArray(width) { alongProfile(columnsU[it], box.left, box.right, dash, sigma) }
            for (j in 0 until height) {
                val across = acrossProfile(rowsV[j] - y, lineWidth, sigma)
                if (across < 1e-4) continue
                for (i in 0 until width) out[j * width + i] = combine(out[j * width + i], across * along[i])
            }
        }
        for (x in listOf(layout.guides.advanceLeft, layout.guides.advanceRight)) {
            val u = box.left + frame.boxMm(x.toDouble(), 0.0).x
            val along = DoubleArray(height) { alongProfile(rowsV[it], box.top, box.bottom, SheetRenderer.GUIDE_DASH_MM, sigma) }
            for (i in 0 until width) {
                val across = acrossProfile(columnsU[i] - u, SheetRenderer.GUIDE_DASHED_MM, sigma)
                if (across < 1e-4) continue
                for (j in 0 until height) out[j * width + i] = combine(out[j * width + i], across * along[j])
            }
        }
        return out
    }

    /**
     * The strength, 0 to about 0.5, with which the guides printed on this sheet: the pooled median, over every pixel the model
     * puts on a guide and that carries no ink or text, of its darkness against the paper around it divided by the model's
     * coverage there. [cells] are the warped cells and [coverages] their [coverage] maps, in the same order.
     */
    fun estimateStrength(
        cells: List<GrayscaleRaster>,
        coverages: List<DoubleArray>,
    ): Double {
        val samples = ArrayList<Double>()
        for ((index, raster) in cells.withIndex()) {
            val coverage = coverages[index]
            if (coverage.none { it >= ON_GUIDE }) continue
            val paper = estimatePaperLevel(raster)
            for (i in coverage.indices) {
                if (coverage[i] < ON_GUIDE) continue
                val darkness = 1.0 - raster.pixels[i] / paper[i].coerceAtLeast(1.0)
                if (darkness > NOT_A_GUIDE_DARKNESS) continue
                samples += darkness / coverage[i]
            }
        }
        if (samples.size < MIN_SAMPLES) return 0.0
        samples.sort()
        val strength = samples[samples.size / 2]
        return if (strength < MIN_STRENGTH) 0.0 else strength.coerceAtMost(0.6)
    }

    /** [raster] with the guides of [coverage] at [strength] divided out: a pixel under a guide is brightened to the paper it was printed on. */
    fun correct(
        raster: GrayscaleRaster,
        coverage: DoubleArray,
        strength: Double,
    ): GrayscaleRaster {
        if (strength <= 0.0) return raster
        val pixels =
            IntArray(raster.pixels.size) {
                val transmittance = 1.0 - strength * coverage[it]
                (raster.pixels[it] / max(transmittance, 0.3) + 0.5).toInt().coerceIn(0, 255)
            }
        return GrayscaleRaster(raster.width, raster.height, pixels)
    }

    private fun combine(
        a: Double,
        b: Double,
    ): Double = 1.0 - (1.0 - a) * (1.0 - b)

    /** The share of a line of [lineWidth] that a Gaussian of [sigma] leaves at [distance] from its centre. */
    private fun acrossProfile(
        distance: Double,
        lineWidth: Double,
        sigma: Double,
    ): Double {
        val d = abs(distance)
        if (d > lineWidth / 2.0 + 4.0 * sigma) return 0.0
        return normalCdf((d + lineWidth / 2.0) / sigma) - normalCdf((d - lineWidth / 2.0) / sigma)
    }

    /** How much of the line is "on" at position [at] along it: 1 for a solid line, the blurred on-off pattern for a dashed one, 0 beyond the ends. */
    private fun alongProfile(
        at: Double,
        start: Double,
        end: Double,
        dash: Double?,
        sigma: Double,
    ): Double {
        if (dash == null) return normalCdf((at - start) / sigma) - normalCdf((at - end) / sigma)
        val period = 2.0 * dash
        val centre = floor((at - start) / period).toInt()
        var sum = 0.0
        for (k in centre - 2..centre + 2) {
            val on = start + k * period
            val off = on + dash
            if (off < start || on > end) continue
            sum += normalCdf((at - max(on, start)) / sigma) - normalCdf((at - minOf(off, end)) / sigma)
        }
        return sum.coerceIn(0.0, 1.0)
    }

    /** The standard normal distribution function, by Abramowitz and Stegun 26.2.17 (error under 1e-7). */
    internal fun normalCdf(x: Double): Double {
        val t = 1.0 / (1.0 + 0.2316419 * abs(x))
        val density = exp(-x * x / 2.0) / sqrt(2.0 * PI)
        val tail = density * t * (0.319381530 + t * (-0.356563782 + t * (1.781477937 + t * (-1.821255978 + t * 1.330274429))))
        return if (x >= 0.0) 1.0 - tail else tail
    }

    private const val MIN_SAMPLES = 200
}
