// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.trace.capture

import com.asoc.typewright.engine.trace.BinaryRaster
import com.asoc.typewright.engine.trace.GrayscaleRaster
import com.asoc.typewright.engine.trace.distanceTransform
import com.asoc.typewright.engine.trace.estimatePaperLevel
import com.asoc.typewright.engine.trace.labelComponents
import com.asoc.typewright.scripts.sheets.MmRect
import com.asoc.typewright.scripts.sheets.PdfWriter
import com.asoc.typewright.scripts.sheets.SheetFont
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
 * The model is the template's own guides, so it is exact only for a printer that prints them as drawn. A printer's guides come out darker or
 * lighter, wider and a little out of register, and what the model does not divide out is a thin leftover lying on the guide, which
 * [withoutGhosts] takes out (the tests vary all three against the template). How strongly the guides printed ([estimateStrength]) is measured from the photograph itself, pooled over every cell of the
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
     * Where a printed guide could lie on a warp of [area] however the printer drew it: every pixel within [bandMm] of a guide's
     * position (each horizontal guide across the whole width, each advance guide down the whole height). A mark that is wholly inside this
     * band and thinner than [withoutGhosts]'s limit is the guide's own leftover (heavier, wider or shifted than the model of it), not a
     * drawing.
     */
    fun band(
        layout: SheetLayout,
        box: MmRect,
        area: MmRect,
        width: Int,
        height: Int,
        pxPerMm: Double,
        bandMm: Double,
    ): BooleanArray {
        val frame = layout.frame
        val out = BooleanArray(width * height)
        val half = bandMm / 2.0
        for (line in layout.guides.lines) {
            val y = box.top + frame.boxMm(0.0, line.y.toDouble()).y
            for (j in 0 until height) {
                if (abs(area.top + (j + 0.5) / pxPerMm - y) <= half) for (i in 0 until width) out[j * width + i] = true
            }
        }
        for (x in listOf(layout.guides.advanceLeft, layout.guides.advanceRight)) {
            val u = box.left + frame.boxMm(x.toDouble(), 0.0).x
            for (i in 0 until width) {
                if (abs(area.left + (i + 0.5) / pxPerMm - u) <= half) for (j in 0 until height) out[j * width + i] = true
            }
        }
        // Each guide's printed name, set in small type just above its line at the box's left edge.
        for (line in layout.guides.lines) {
            val y = box.top + frame.boxMm(0.0, line.y.toDouble()).y
            val left = box.left + SheetRenderer.GUIDE_LABEL_INSET_MM - half
            val right = left + half + PdfWriter.textWidthMm(line.label, SheetFont.HELVETICA, SheetRenderer.GUIDE_LABEL_SIZE_PT) + half
            val top = y - SheetRenderer.GUIDE_LABEL_RISE_MM - SheetRenderer.GUIDE_LABEL_SIZE_PT * MM_PER_PT - half
            val bottom = y + half
            for (j in 0 until height) {
                val v = area.top + (j + 0.5) / pxPerMm
                if (v < top || v > bottom) continue
                for (i in 0 until width) {
                    val u = area.left + (i + 0.5) / pxPerMm
                    if (u in left..right) out[j * width + i] = true
                }
            }
        }
        return out
    }

    private const val MM_PER_PT = 25.4 / 72.0

    /**
     * [raster] with every mark that lies wholly inside [band] taken out (painted with the paper around it): the leftover of a guide the
     * model of the guides did not match exactly. A printer's toner is heavier or wider than the template's grey, or a little out of
     * register, and the pooled model cannot divide all of that out; but a leftover is thin and sits on the guide, which a drawing is not,
     * and a mark that is part of a drawing touches something outside the band. The band is as wide as it is to allow for register, so a
     * mark wholly inside it is a leftover only if it is also thinner than [maxThicknessPx]: a pen line a millimetre or more across
     * (an underscore or hyphen on a guide) is a drawing, not a ghost, even where it lies wholly inside the band.
     */
    fun withoutGhosts(
        raster: GrayscaleRaster,
        band: BooleanArray,
        maxThicknessPx: Int = Int.MAX_VALUE,
    ): GrayscaleRaster {
        val paper = estimatePaperLevel(raster)
        val w = raster.width
        val h = raster.height
        val dark =
            BooleanArray(raster.pixels.size) {
                val level = paper[it].coerceAtLeast(1.0)
                1.0 - raster.pixels[it] / level >= GHOST_DARKNESS
            }
        val labels = labelComponents(w, h) { x, y -> dark[y * w + x] }
        if (labels.componentCount == 0) return raster
        val ghost = BooleanArray(labels.componentCount) { true }
        for (i in dark.indices) if (dark[i] && !band[i]) ghost[labels.labels[i]] = false
        // How thick each mark is: twice the deepest distance from its pixels to the paper, less one pixel. A guide's leftover is thin
        // however long it is, and where a horizontal and an advance guide cross their leftovers are one mark with a thin arm each way.
        val depth = distanceTransform(BinaryRaster(w, h, dark))
        val deepest = DoubleArray(labels.componentCount)
        for (i in dark.indices) if (dark[i]) deepest[labels.labels[i]] = max(deepest[labels.labels[i]], depth[i])
        for (c in deepest.indices) if (2.0 * deepest[c] - 1.0 >= maxThicknessPx) ghost[c] = false
        if (ghost.none { it }) return raster
        val pixels = raster.pixels.copyOf()
        for (i in dark.indices) {
            if (!dark[i] || !ghost[labels.labels[i]]) continue
            // The mark and a pixel's worth of its soft edge.
            for (dy in -GHOST_HALO..GHOST_HALO) {
                for (dx in -GHOST_HALO..GHOST_HALO) {
                    val x = i % w + dx
                    val y = i / w + dy
                    if (x in 0 until w && y in 0 until h) pixels[y * w + x] = paper[y * w + x].toInt()
                }
            }
        }
        return GrayscaleRaster(w, h, pixels)
    }

    /** The darkness from which a pixel counts towards a mark when ghosts are looked for: well under any pen, over the paper's own texture (our heuristic). */
    private const val GHOST_DARKNESS = 0.12

    /** How many pixels round a ghost are painted out with it. */
    private const val GHOST_HALO = 1

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
