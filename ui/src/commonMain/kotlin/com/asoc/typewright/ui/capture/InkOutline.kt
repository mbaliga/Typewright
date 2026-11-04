// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.capture

import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.engine.trace.GrayscaleRaster
import com.asoc.typewright.engine.trace.ScalarField
import com.asoc.typewright.engine.trace.traceContours
import kotlin.math.max

/**
 * The dark parts of a small picture as filled shapes, so a thumbnail of a photograph or a captured cell is drawn as a path
 * (crisp at any size, one fill) and not as thousands of squares. [loops] are closed polylines in the picture's own pixels
 * ([width] by [height]); filled with the even-odd rule, a ring of ink keeps its hole.
 */
class InkOutline(
    val width: Int,
    val height: Int,
    val loops: List<List<Vec2>>,
) {
    val isEmpty: Boolean get() = loops.isEmpty()

    companion object {
        val EMPTY = InkOutline(1, 1, emptyList())

        /**
         * The ink of [raster] reduced to at most [maxSide] pixels on its long side. Darkness is measured against the raster's own
         * paper (its 90th-percentile brightness), so an unevenly lit photograph and a clean white cell both read, and the edge is
         * taken where a pixel is [edge] of the way from paper to black. With [thin] each output pixel takes the darkest of the
         * pixels it covers, so a pen line far thinner than a thumbnail pixel still shows.
         */
        fun of(
            raster: GrayscaleRaster,
            maxSide: Int,
            edge: Double = 0.5,
            thin: Boolean = false,
        ): InkOutline {
            val scale = max(raster.width, raster.height).toDouble() / maxSide
            val w = max(2, if (scale > 1.0) (raster.width / scale).toInt() else raster.width)
            val h = max(2, if (scale > 1.0) (raster.height / scale).toInt() else raster.height)
            val sums = DoubleArray(w * h)
            val darkest = DoubleArray(w * h) { 255.0 }
            val counts = IntArray(w * h)
            for (y in 0 until raster.height) {
                val oy = minOf(h - 1, y * h / raster.height)
                for (x in 0 until raster.width) {
                    val ox = minOf(w - 1, x * w / raster.width)
                    val v = raster.pixels[y * raster.width + x]
                    sums[oy * w + ox] += v
                    if (v < darkest[oy * w + ox]) darkest[oy * w + ox] = v.toDouble()
                    counts[oy * w + ox]++
                }
            }
            val small =
                DoubleArray(w * h) {
                    if (counts[it] == 0) {
                        255.0
                    } else if (thin) {
                        darkest[it]
                    } else {
                        sums[it] / counts[it]
                    }
                }
            val sorted = small.sorted()
            val paper = max(1.0, sorted[(sorted.size * 9) / 10])
            // Padded by one paper-coloured pixel so ink that touches the edge still closes into a loop.
            val field =
                ScalarField { x, y ->
                    if (x in 1..w && y in 1..h) ((paper - small[(y - 1) * w + (x - 1)]) / paper).coerceIn(0.0, 1.0) else 0.0
                }
            val loops = traceContours(w + 2, h + 2, field, isovalue = edge).map { loop -> loop.map { Vec2(it.x - 1.0, it.y - 1.0) } }
            return InkOutline(w, h, loops.filter { it.size >= 3 })
        }
    }
}
