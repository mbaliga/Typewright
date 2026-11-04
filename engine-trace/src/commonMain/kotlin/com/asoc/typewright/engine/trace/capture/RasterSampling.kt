// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.trace.capture

import com.asoc.typewright.engine.trace.GrayscaleRaster
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * A colour photograph as packed ARGB pixels (`0xAARRGGBB`, row-major, top-left origin), the form every platform's image
 * decoder can produce; this module decodes nothing itself. [toGrayscale] is what the capture reads: the luma of each pixel
 * (Rec. 601 weights), so a coloured paper tint dims the whole sheet evenly and leaves ink the darkest thing in it.
 */
class RgbRaster(
    val width: Int,
    val height: Int,
    val argb: IntArray,
) {
    init {
        require(width > 0 && height > 0 && argb.size == width * height) { "argb must hold width*height pixels" }
    }

    fun toGrayscale(): GrayscaleRaster =
        GrayscaleRaster(
            width,
            height,
            IntArray(argb.size) {
                val p = argb[it]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                ((299 * r + 587 * g + 114 * b + 500) / 1000).coerceIn(0, 255)
            },
        )
}

/**
 * The raster's value at ([x], [y]) by bilinear interpolation, in index space (the sample at integer `(i, j)` is pixel
 * `(i, j)`'s own value), clamped to the raster's edge so a point just outside reads the nearest border pixel.
 */
fun GrayscaleRaster.sample(
    x: Double,
    y: Double,
): Double {
    val cx = x.coerceIn(0.0, (width - 1).toDouble())
    val cy = y.coerceIn(0.0, (height - 1).toDouble())
    val x0 = floor(cx).toInt().coerceAtMost(width - 2).coerceAtLeast(0)
    val y0 = floor(cy).toInt().coerceAtMost(height - 2).coerceAtLeast(0)
    val fx = if (width == 1) 0.0 else cx - x0
    val fy = if (height == 1) 0.0 else cy - y0
    val x1 = if (width == 1) 0 else x0 + 1
    val y1 = if (height == 1) 0 else y0 + 1
    val top = pixels[y0 * width + x0] * (1.0 - fx) + pixels[y0 * width + x1] * fx
    val bottom = pixels[y1 * width + x0] * (1.0 - fx) + pixels[y1 * width + x1] * fx
    return top * (1.0 - fy) + bottom * fy
}

/**
 * The raster's value at ([x], [y]) by bicubic (Catmull-Rom) interpolation over the 4 by 4 pixels around it, clamped to 0..255.
 * Bilinear interpolation of a slanted edge leaves a ripple of about a tenth of a pixel in where the edge appears to run;
 * the cubic kernel brings that to a few hundredths, which is what makes a straight pen line come out of a rotated or
 * tilted photograph straight enough to be told from a curve.
 */
fun GrayscaleRaster.sampleCubic(
    x: Double,
    y: Double,
): Double {
    val cx = x.coerceIn(0.0, (width - 1).toDouble())
    val cy = y.coerceIn(0.0, (height - 1).toDouble())
    val x0 = floor(cx).toInt()
    val y0 = floor(cy).toInt()
    val fx = cx - x0
    val fy = cy - y0
    val wx = catmullRom(fx)
    val wy = catmullRom(fy)
    var sum = 0.0
    for (j in 0 until 4) {
        val yy = (y0 - 1 + j).coerceIn(0, height - 1)
        var row = 0.0
        for (i in 0 until 4) row += wx[i] * pixels[yy * width + (x0 - 1 + i).coerceIn(0, width - 1)]
        sum += wy[j] * row
    }
    return sum.coerceIn(0.0, 255.0)
}

private fun catmullRom(t: Double): DoubleArray {
    val t2 = t * t
    val t3 = t2 * t
    return doubleArrayOf(
        -0.5 * t3 + t2 - 0.5 * t,
        1.5 * t3 - 2.5 * t2 + 1.0,
        -1.5 * t3 + 2.0 * t2 + 0.5 * t,
        0.5 * t3 - 0.5 * t2,
    )
}

/** The raster reduced by an integer [factor] in each direction, each new pixel the mean of the block it covers. Index `(i, j)` of the result is the centre of block `(i, j)`. */
fun GrayscaleRaster.downscale(factor: Int): GrayscaleRaster {
    require(factor >= 1) { "factor must be at least 1, was $factor" }
    if (factor == 1) return this
    val w = (width / factor).coerceAtLeast(1)
    val h = (height / factor).coerceAtLeast(1)
    val out = IntArray(w * h)
    for (y in 0 until h) {
        for (x in 0 until w) {
            var sum = 0
            var count = 0
            for (dy in 0 until factor) {
                val sy = y * factor + dy
                if (sy >= height) break
                for (dx in 0 until factor) {
                    val sx = x * factor + dx
                    if (sx >= width) break
                    sum += pixels[sy * width + sx]
                    count++
                }
            }
            out[y * w + x] = (sum.toDouble() / count).roundToInt()
        }
    }
    return GrayscaleRaster(w, h, out)
}
