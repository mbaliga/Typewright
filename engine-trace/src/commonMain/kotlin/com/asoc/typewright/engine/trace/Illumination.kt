// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.trace

import kotlin.math.max
import kotlin.math.min

/**
 * An estimate, per pixel, of how bright the paper is under [raster]'s ink: what a pixel would read if nothing
 * were drawn on it. Uneven light, a shadow across a corner or a tinted sheet all show up as a smooth surface in
 * this estimate, and dividing the raster by it ([normalisedDarkness]) leaves only the drawing.
 *
 * The estimate is a windowed mean of the paper-like pixels only. It starts from the 90th-percentile brightness
 * of the whole raster, keeps the pixels within [paperFraction] of the current estimate, takes their mean over a
 * window of [radius] pixels, and repeats [passes] times, so ink and printed guides, which are darker than paper,
 * never pull the estimate down under themselves. Where a window holds no paper at all the previous estimate stands.
 * Pixels at or above [saturation] are glare, not paper: they take no part in the starting level or in the means, so a
 * highlight covering a tenth of the sheet cannot lift the estimate of the paper beside it.
 *
 * With [followShadows] the starting level is the local brightness (the brightest of what lies within [radius]) instead
 * of one level for the whole raster, and the first pass accepts pixels down to 80% of it. Without it, a shadow wide
 * enough to leave a window with no pixel near the global level is never reached and reads as one big dark patch; with
 * it the estimate follows the shadow, at the cost of trusting the brightest nearby pixels, so it is for a whole sheet
 * (where paper is everywhere) and not for a single cell.
 */
fun estimatePaperLevel(
    raster: GrayscaleRaster,
    radius: Int = max(8, max(raster.width, raster.height) / 6),
    paperFraction: Double = 0.93,
    passes: Int = 3,
    saturation: Int = 256,
    followShadows: Boolean = false,
): DoubleArray {
    val w = raster.width
    val h = raster.height
    val start = brightnessPercentile(raster.pixels, 0.9, saturation).toDouble().coerceAtLeast(1.0)
    var level = if (followShadows) localBrightness(raster, radius, saturation) else DoubleArray(w * h) { start }
    val stride = w + 1
    repeat(passes) { pass ->
        val fraction = if (followShadows && pass == 0) FIRST_PASS_FRACTION else paperFraction
        val sumValue = LongArray(stride * (h + 1))
        val sumCount = IntArray(stride * (h + 1))
        for (y in 0 until h) {
            var rowValue = 0L
            var rowCount = 0
            for (x in 0 until w) {
                val v = raster.pixels[y * w + x]
                if (v < saturation && v >= fraction * level[y * w + x]) {
                    rowValue += v
                    rowCount++
                }
                sumValue[(y + 1) * stride + x + 1] = sumValue[y * stride + x + 1] + rowValue
                sumCount[(y + 1) * stride + x + 1] = sumCount[y * stride + x + 1] + rowCount
            }
        }
        val next = DoubleArray(w * h)
        for (y in 0 until h) {
            val y0 = max(0, y - radius)
            val y1 = min(h - 1, y + radius)
            for (x in 0 until w) {
                val x0 = max(0, x - radius)
                val x1 = min(w - 1, x + radius)
                val value =
                    sumValue[(y1 + 1) * stride + x1 + 1] - sumValue[y0 * stride + x1 + 1] -
                        sumValue[(y1 + 1) * stride + x0] + sumValue[y0 * stride + x0]
                val count =
                    sumCount[(y1 + 1) * stride + x1 + 1] - sumCount[y0 * stride + x1 + 1] -
                        sumCount[(y1 + 1) * stride + x0] + sumCount[y0 * stride + x0]
                next[y * w + x] = if (count > 0) value.toDouble() / count else level[y * w + x]
            }
        }
        level = next
    }
    return level
}

/** The share of the local brightness a pixel needs, in the first pass of a shadow-following estimate, to count as paper. */
private const val FIRST_PASS_FRACTION = 0.8

/**
 * A smooth surface of how bright the raster is, nearby: the 80th-percentile brightness of each 6 by 6 block, the largest of
 * those within [radius] pixels, and the result smoothed and enlarged back to the raster's size. Pixels at or above [saturation] are left out.
 */
private fun localBrightness(
    raster: GrayscaleRaster,
    radius: Int,
    saturation: Int,
): DoubleArray {
    val w = raster.width
    val h = raster.height
    val block = 6
    val bw = (w + block - 1) / block
    val bh = (h + block - 1) / block
    val coarse = DoubleArray(bw * bh)
    val values = IntArray(block * block)
    for (by in 0 until bh) {
        for (bx in 0 until bw) {
            var n = 0
            for (y in by * block until min(h, by * block + block)) {
                for (x in bx * block until min(w, bx * block + block)) {
                    val v = raster.pixels[y * w + x]
                    if (v < saturation) values[n++] = v
                }
            }
            if (n == 0) {
                coarse[by * bw + bx] = 255.0
            } else {
                values.sort(0, n)
                coarse[by * bw + bx] = values[(n * 4) / 5].toDouble()
            }
        }
    }
    val reach = max(1, radius / block)
    val rowMax = DoubleArray(bw * bh)
    for (by in 0 until bh) {
        for (bx in 0 until bw) {
            var best = 0.0
            for (k in max(0, bx - reach)..min(bw - 1, bx + reach)) best = max(best, coarse[by * bw + k])
            rowMax[by * bw + bx] = best
        }
    }
    val colMax = DoubleArray(bw * bh)
    for (by in 0 until bh) {
        for (bx in 0 until bw) {
            var best = 0.0
            for (k in max(0, by - reach)..min(bh - 1, by + reach)) best = max(best, rowMax[k * bw + bx])
            colMax[by * bw + bx] = best
        }
    }
    // Smooth, so a single bright block does not leave a plateau edge in the estimate.
    var smooth = colMax
    repeat(2) {
        val source = smooth
        smooth =
            DoubleArray(bw * bh) { i ->
                val bx = i % bw
                val by = i / bw
                var sum = 0.0
                var count = 0
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val x = bx + dx
                        val y = by + dy
                        if (x in 0 until bw && y in 0 until bh) {
                            sum += source[y * bw + x]
                            count++
                        }
                    }
                }
                sum / count
            }
    }
    return DoubleArray(w * h) { i ->
        val fx = ((i % w) + 0.5) / block - 0.5
        val fy = ((i / w) + 0.5) / block - 0.5
        val x0 = fx.toInt().coerceIn(0, bw - 1)
        val y0 = fy.toInt().coerceIn(0, bh - 1)
        val x1 = min(bw - 1, x0 + 1)
        val y1 = min(bh - 1, y0 + 1)
        val tx = (fx - x0).coerceIn(0.0, 1.0)
        val ty = (fy - y0).coerceIn(0.0, 1.0)
        val top = smooth[y0 * bw + x0] * (1 - tx) + smooth[y0 * bw + x1] * tx
        val bottom = smooth[y1 * bw + x0] * (1 - tx) + smooth[y1 * bw + x1] * tx
        (top * (1 - ty) + bottom * ty).coerceAtLeast(1.0)
    }
}

/** The brightness below which [fraction] of the [pixels] under [ceiling] lie (all of them when none is under it), by a 256-bin histogram. */
private fun brightnessPercentile(
    pixels: IntArray,
    fraction: Double,
    ceiling: Int,
): Int {
    val histogram = IntArray(256)
    var counted = 0
    for (v in pixels) {
        if (v < ceiling) {
            histogram[v.coerceIn(0, 255)]++
            counted++
        }
    }
    if (counted == 0) {
        for (v in pixels) histogram[v.coerceIn(0, 255)]++
        counted = pixels.size
    }
    val target = (counted * fraction).toInt().coerceAtMost(counted - 1)
    var seen = 0
    for (b in 0 until 256) {
        seen += histogram[b]
        if (seen > target) return b
    }
    return 255
}

/**
 * Each pixel of [raster] as a darkness between 0 (as bright as the paper around it) and 1 (black), judged against
 * [paperLevel] so that a shadow does not read as ink and glare does not hide it.
 */
fun normalisedDarkness(
    raster: GrayscaleRaster,
    paperLevel: DoubleArray = estimatePaperLevel(raster),
): DoubleArray = DoubleArray(raster.pixels.size) { (1.0 - raster.pixels[it] / paperLevel[it].coerceAtLeast(1.0)).coerceIn(0.0, 1.0) }

/**
 * Otsu's threshold (1979) for [values] in 0..1: the level that best splits them into two groups, found by
 * maximising the variance between the groups over a 256-bin histogram. Returns the middle of the range when the
 * values are all alike.
 */
fun otsuThreshold(values: DoubleArray): Double {
    val bins = 256
    val histogram = IntArray(bins)
    for (v in values) histogram[(v * (bins - 1)).toInt().coerceIn(0, bins - 1)]++
    val total = values.size.toDouble()
    var sumAll = 0.0
    for (b in 0 until bins) sumAll += b * histogram[b].toDouble()
    var weightBackground = 0.0
    var sumBackground = 0.0
    var bestVariance = -1.0
    var bestBin = bins / 2
    for (b in 0 until bins) {
        weightBackground += histogram[b]
        if (weightBackground == 0.0) continue
        val weightForeground = total - weightBackground
        if (weightForeground == 0.0) break
        sumBackground += b * histogram[b].toDouble()
        val meanBackground = sumBackground / weightBackground
        val meanForeground = (sumAll - sumBackground) / weightForeground
        val variance = weightBackground * weightForeground * (meanBackground - meanForeground) * (meanBackground - meanForeground)
        if (variance > bestVariance) {
            bestVariance = variance
            bestBin = b
        }
    }
    return (bestBin + 0.5) / bins
}
