// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.trace

import com.asoc.typewright.core.geometry.Contour
import kotlin.math.roundToInt

/**
 * Test-only: [contours] filled into a [width] x [height] [GrayscaleRaster], ink 0 and paper 255, each pixel
 * carrying its exact covered fraction ([coverageOf]) -- an anti-aliased render, the way a scanner or a
 * camera sees a pen line rather than a hard-edged mask.
 */
internal fun rasterizeContours(
    contours: List<Contour>,
    frame: RasterFrame,
    width: Int,
    height: Int,
): GrayscaleRaster {
    val coverage = coverageOf(contours, frame, width, height)
    return GrayscaleRaster(width, height, IntArray(width * height) { 255 - (coverage[it] * 255.0).roundToInt().coerceIn(0, 255) })
}
