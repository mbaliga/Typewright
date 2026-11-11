// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.capture

import com.asoc.typewright.engine.trace.GrayscaleRaster
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Turns decoded colour pixels into the grey raster the capture engine reads, shared by every platform's decoder. */
object ImagePixels {
    /**
     * [argb] (row by row, `0xAARRGGBB`) as a grey raster whose long side is at most [maxSide]. Grey is the usual luma
     * (0.299 R + 0.587 G + 0.114 B) of the pixel laid over white paper, so a transparent PNG reads as a drawing on white. A larger
     * picture is reduced by averaging the block of source pixels each output pixel covers, so thin pen lines stay as dark as they were
     * rather than being skipped over.
     */
    fun grayFromArgb(
        width: Int,
        height: Int,
        argb: IntArray,
        maxSide: Int,
    ): GrayscaleRaster {
        require(width > 0 && height > 0 && argb.size == width * height) { "pixels must be width*height" }
        require(maxSide >= 1) { "maxSide must be positive" }
        val longSide = max(width, height)
        val scale = if (longSide > maxSide) maxSide.toDouble() / longSide else 1.0
        val outW = max(1, (width * scale).roundToInt())
        val outH = max(1, (height * scale).roundToInt())
        val out = IntArray(outW * outH)
        for (oy in 0 until outH) {
            val y0 = oy * height / outH
            val y1 = max(y0 + 1, min(height, (oy + 1) * height / outH))
            for (ox in 0 until outW) {
                val x0 = ox * width / outW
                val x1 = max(x0 + 1, min(width, (ox + 1) * width / outW))
                var sum = 0L
                var count = 0
                for (y in y0 until y1) {
                    for (x in x0 until x1) {
                        sum += lumaOnWhite(argb[y * width + x])
                        count++
                    }
                }
                out[oy * outW + ox] = ((sum + count / 2) / count).toInt().coerceIn(0, 255)
            }
        }
        return GrayscaleRaster(outW, outH, out)
    }

    /** One pixel's grey over white paper, 0 to 255. */
    fun lumaOnWhite(argb: Int): Int {
        val a = (argb ushr 24) and 0xFF
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        val luma = (299 * r + 587 * g + 114 * b) / 1000
        return (luma * a + 255 * (255 - a)) / 255
    }
}
