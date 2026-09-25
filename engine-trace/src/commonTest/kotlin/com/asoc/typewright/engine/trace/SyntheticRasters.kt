// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.trace

import kotlin.math.hypot

/**
 * A binary raster with a filled disc of [radius] centred at ([centerX], [centerY]), rasterised by
 * point-sampling each pixel's own grid coordinate `(x, y)` against the mathematically exact circle
 * -- exactly the "synthetic rasterised circle" this task's own instructions ask for, at whatever
 * resolution the caller's [width]/[height]/[radius] choose. Deliberately *not* offset to a pixel's
 * centre (`x + 0.5, y + 0.5`): [traceContours] treats a raster value at `(x, y)` as sampled *at*
 * that integer grid point (its crossings are computed directly in that coordinate space), so this
 * function's samples and a caller measuring a traced contour's accuracy against the same
 * `(centerX, centerY)` must agree on that one convention, or an accuracy measurement would be
 * comparing two coordinate spaces half a pixel apart without knowing it.
 */
internal fun rasterizeDisc(
    width: Int,
    height: Int,
    centerX: Double,
    centerY: Double,
    radius: Double,
): BinaryRaster {
    val ink = BooleanArray(width * height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val dx = x - centerX
            val dy = y - centerY
            ink[y * width + x] = hypot(dx, dy) <= radius
        }
    }
    return BinaryRaster(width, height, ink)
}

/** A binary raster with a filled axis-aligned rectangle `[x0, x0 + rectWidth) x [y0, y0 + rectHeight)`, everything else background. */
internal fun rasterizeRectangle(
    width: Int,
    height: Int,
    x0: Int,
    y0: Int,
    rectWidth: Int,
    rectHeight: Int,
): BinaryRaster {
    val ink = BooleanArray(width * height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            ink[y * width + x] = x in x0 until x0 + rectWidth && y in y0 until y0 + rectHeight
        }
    }
    return BinaryRaster(width, height, ink)
}

/** [raster], with every one of [points] flipped to ink -- for scattering speckle noise onto an otherwise clean raster. */
internal fun BinaryRaster.withInkAt(points: List<Pair<Int, Int>>): BinaryRaster {
    val ink = ink.copyOf()
    for ((x, y) in points) ink[y * width + x] = true
    return BinaryRaster(width, height, ink)
}

/** [raster], with every one of [points] flipped to background -- for punching a hole into an otherwise solid raster. */
internal fun BinaryRaster.withHoleAt(points: List<Pair<Int, Int>>): BinaryRaster {
    val ink = ink.copyOf()
    for ((x, y) in points) ink[y * width + x] = false
    return BinaryRaster(width, height, ink)
}

/** [raster] read as a [GrayscaleRaster]: ink pixels at [inkValue] (default pure black), background at [paperValue] (default pure white). */
internal fun BinaryRaster.toGrayscale(
    inkValue: Int = 0,
    paperValue: Int = 255,
): GrayscaleRaster {
    val pixels = IntArray(width * height) { if (ink[it]) inkValue else paperValue }
    return GrayscaleRaster(width, height, pixels)
}
