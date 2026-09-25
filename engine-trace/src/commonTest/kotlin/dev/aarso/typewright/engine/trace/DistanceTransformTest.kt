// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.engine.trace

import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A brute-force nearest-background distance, for every cell, used only as this test's own reference oracle. */
private fun bruteForceDistanceTransform(raster: BinaryRaster): DoubleArray {
    val backgroundPoints = mutableListOf<Pair<Int, Int>>()
    for (y in 0 until raster.height) for (x in 0 until raster.width) if (!raster[x, y]) backgroundPoints += x to y
    return DoubleArray(raster.width * raster.height) { i ->
        if (!raster.ink[i]) {
            0.0
        } else {
            val x = i % raster.width
            val y = i / raster.width
            backgroundPoints.minOf { (bx, by) -> hypot((x - bx).toDouble(), (y - by).toDouble()) }
        }
    }
}

class DistanceTransformTest {
    @Test
    fun everyBackgroundCellHasDistanceZero() {
        val raster = rasterizeRectangle(10, 10, x0 = 2, y0 = 2, rectWidth = 4, rectHeight = 4)
        val distance = distanceTransform(raster)
        for (y in 0 until 10) {
            for (x in 0 until 10) {
                if (!raster[x, y]) assertEquals(0.0, distance[y * 10 + x])
            }
        }
    }

    @Test
    fun aRasterWithNoBackgroundAtAllLeavesEveryDistanceUnreachable() {
        // No background pixel exists anywhere on the grid, so there is nothing to measure
        // distance to; this documents that honestly (positive infinity) rather than an arbitrary
        // finite placeholder.
        val raster = BinaryRaster.filled(4, 4, value = true)
        val distance = distanceTransform(raster)
        assertTrue(distance.all { it == Double.POSITIVE_INFINITY })
    }

    @Test
    fun theCentreOfALargeSquareIsExactlyItsDistanceToTheNearestBackgroundPixel() {
        // A 41x41 solid square (point-sampled columns/rows 10..50 inclusive, CLAUDE.md/Raster.kt's
        // "pixel (x, y) is a point sample at (x, y)" convention): its centre, column/row 30, is 21
        // *pixels* from the nearest background sample (column/row 9, one past the square's own
        // last ink sample at 10) -- straight along one axis, so even chamfer's orthogonal-step
        // approximation is exact here, not merely close.
        val raster = rasterizeRectangle(80, 80, x0 = 10, y0 = 10, rectWidth = 41, rectHeight = 41)
        val distance = distanceTransform(raster)
        assertApproxEquals(21.0, distance[30 * 80 + 30], tolerance = 1e-9)
    }

    @Test
    fun matchesABruteForceEuclideanDistanceTransformWithinChamferApproximationError() {
        val raster = rasterizeDisc(40, 40, centerX = 20.0, centerY = 20.0, radius = 15.0)
        val chamfer = distanceTransform(raster)
        val exact = bruteForceDistanceTransform(raster)

        var maxAbsoluteError = 0.0
        for (i in chamfer.indices) {
            val error = kotlin.math.abs(chamfer[i] - exact[i])
            if (error > maxAbsoluteError) maxAbsoluteError = error
        }
        println("distanceTransform vs brute-force Euclidean: max absolute error over a 40x40 disc = $maxAbsoluteError")
        // Chamfer (1, sqrt(2)) is a known, bounded approximation of the true Euclidean distance
        // transform (Borgefors 1986); this generous bound only guards against a real algorithmic
        // mistake, not against the approximation's own well-understood, small error.
        assertTrue(maxAbsoluteError < 1.0, "chamfer distance should stay within about a pixel of the true Euclidean distance")
    }

    @Test
    fun anAllBackgroundRasterIsAllZero() {
        val raster = BinaryRaster.filled(6, 6)
        val distance = distanceTransform(raster)
        assertTrue(distance.all { it == 0.0 })
    }

    private fun assertApproxEquals(
        expected: Double,
        actual: Double,
        tolerance: Double,
    ) {
        assertTrue(kotlin.math.abs(expected - actual) <= tolerance, "expected $expected, got $actual (tolerance $tolerance)")
    }
}
