// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.engine.trace

/**
 * A summed-area table (Crow 1984) over a [GrayscaleRaster]'s darkness channel (`255 - value`, see
 * [GrayscaleRaster]'s KDoc), so [adaptiveThreshold] can read any axis-aligned window's mean
 * darkness in O(1) after this one O(`width*height`) build -- the "integral-image-based local mean"
 * docs/ARCHITECTURE_REVIEW.md section 3 `:engine-trace` names as the suggested approach.
 *
 * [sums] is `(width+1) x (height+1)`, one larger than the raster in each direction, so every
 * window query ([windowSum]) stays a plain rectangle-difference with no special case at the raster
 * edge (`sums[0][*]` and `sums[*][0]` are the conventional all-zero border row/column of a summed-
 * area table).
 */
internal class IntegralImage(
    raster: GrayscaleRaster,
) {
    private val width = raster.width
    private val height = raster.height
    private val sums = LongArray((width + 1) * (height + 1))

    init {
        for (y in 0 until height) {
            var rowSum = 0L
            for (x in 0 until width) {
                rowSum += 255 - raster[x, y]
                val above = sums[y * (width + 1) + (x + 1)]
                sums[(y + 1) * (width + 1) + (x + 1)] = above + rowSum
            }
        }
    }

    /**
     * The sum of darkness (`255 - value`) over the inclusive pixel rectangle `[x0, x1] x [y0,
     * y1]`, clamped to the raster's bounds. Returns `0` if the clamped rectangle is empty (the
     * requested window fell entirely outside the raster).
     */
    fun windowSum(
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
    ): Long {
        // An empty request, or one that does not overlap the raster at all, must be caught before
        // clamping: coercing two already-outside coordinates into range would otherwise collapse
        // them onto the same clamped border pixel and report its value as the sum, instead of 0.
        if (x1 < x0 || y1 < y0 || x1 < 0 || y1 < 0 || x0 > width - 1 || y0 > height - 1) return 0L
        val cx0 = x0.coerceIn(0, width - 1)
        val cy0 = y0.coerceIn(0, height - 1)
        val cx1 = x1.coerceIn(0, width - 1)
        val cy1 = y1.coerceIn(0, height - 1)
        val a = sums[cy0 * (width + 1) + cx0]
        val b = sums[cy0 * (width + 1) + (cx1 + 1)]
        val c = sums[(cy1 + 1) * (width + 1) + cx0]
        val d = sums[(cy1 + 1) * (width + 1) + (cx1 + 1)]
        return d - b - c + a
    }

    /** The mean darkness (`255 - value`, as a [Double]) over the inclusive window of [radius] pixels around ([x], [y]). */
    fun windowMean(
        x: Int,
        y: Int,
        radius: Int,
    ): Double {
        val x0 = x - radius
        val y0 = y - radius
        val x1 = x + radius
        val y1 = y + radius
        val cx0 = x0.coerceIn(0, width - 1)
        val cy0 = y0.coerceIn(0, height - 1)
        val cx1 = x1.coerceIn(0, width - 1)
        val cy1 = y1.coerceIn(0, height - 1)
        val area = (cx1 - cx0 + 1) * (cy1 - cy0 + 1)
        if (area <= 0) return 0.0
        return windowSum(x0, y0, x1, y1).toDouble() / area
    }
}
