// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.trace

/**
 * The typical stroke width of [raster], in pixels: twice the median distance-to-paper along the ink's ridge (the
 * pixels at least as far from the paper as all eight neighbours, which is the centreline of every stroke).
 *
 * [estimateStrokeWidth], twice the single largest distance, is exact for one clean bar but reports the biggest
 * blob in the raster: a crossbar's joint with its stem, a dot, a bowl's thick shoulder all read wider than the pen.
 * The median of the ridge is what most of the strokes look like, so a few joints do not move it. A raster with no
 * ink has no stroke and gives `0.0`.
 */
fun estimateStrokeWidthRidge(raster: BinaryRaster): Double {
    val distance = distanceTransform(raster)
    val w = raster.width
    val h = raster.height
    val ridge = ArrayList<Double>()
    for (y in 0 until h) {
        for (x in 0 until w) {
            val d = distance[y * w + x]
            if (d < 1.0) continue
            var isRidge = true
            for (dy in -1..1) {
                for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx
                    val ny = y + dy
                    if (nx in 0 until w && ny in 0 until h && distance[ny * w + nx] > d) isRidge = false
                }
            }
            if (isRidge) ridge += d
        }
    }
    if (ridge.isEmpty()) return 0.0
    ridge.sort()
    return 2.0 * ridge[ridge.size / 2]
}
