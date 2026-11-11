// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.capture

import com.asoc.typewright.engine.trace.GrayscaleRaster
import kotlin.math.max

/**
 * Turns a camera's brightness plane (the Y of a YUV frame, as an Android camera hands it over) into the grey raster the corner
 * finder reads: reduced to at most [maxSide] on the long side by taking the middle sample of each block, and turned by the frame's
 * [rotationDegrees] (0, 90, 180 or 270, clockwise) so the picture is the way up the person sees it. Plain arithmetic over a byte
 * array, so it is tested off the device.
 */
object LumaFrames {
    fun toRaster(
        luma: ByteArray,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int,
        rotationDegrees: Int,
        maxSide: Int,
    ): GrayscaleRaster {
        require(
            width > 0 && height > 0 && rowStride >= width * pixelStride - (pixelStride - 1),
        ) { "the plane is smaller than its size says" }
        val turned = rotationDegrees % FULL_TURN
        require(turned % QUARTER == 0) { "rotation must be a multiple of 90 degrees, was $rotationDegrees" }
        val outWidthUnrotated = max(1, if (max(width, height) > maxSide) width * maxSide / max(width, height) else width)
        val outHeightUnrotated = max(1, if (max(width, height) > maxSide) height * maxSide / max(width, height) else height)
        val swap = turned == QUARTER || turned == QUARTER * 3
        val outW = if (swap) outHeightUnrotated else outWidthUnrotated
        val outH = if (swap) outWidthUnrotated else outHeightUnrotated
        val out = IntArray(outW * outH)
        for (y in 0 until outH) {
            for (x in 0 until outW) {
                // The position in the unrotated, reduced frame that this output pixel shows.
                val (ux, uy) =
                    when (turned) {
                        0 -> x to y
                        QUARTER -> y to (outW - 1 - x)
                        QUARTER * 2 -> (outW - 1 - x) to (outH - 1 - y)
                        else -> (outH - 1 - y) to x
                    }
                val sx = (ux * width + width / 2) / outWidthUnrotated.coerceAtLeast(1)
                val sy = (uy * height + height / 2) / outHeightUnrotated.coerceAtLeast(1)
                val cx = sx.coerceIn(0, width - 1)
                val cy = sy.coerceIn(0, height - 1)
                out[y * outW + x] = luma[cy * rowStride + cx * pixelStride].toInt() and 0xFF
            }
        }
        return GrayscaleRaster(outW, outH, out)
    }

    private const val FULL_TURN = 360
    private const val QUARTER = 90
}
