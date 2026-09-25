// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.engine.trace

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class IntegralImageTest {
    @Test
    fun windowSumMatchesABruteForceSumOverTheWholeRaster() {
        val random = Random(42)
        val width = 12
        val height = 9
        val raster = GrayscaleRaster(width, height, IntArray(width * height) { random.nextInt(0, 256) })
        val integral = IntegralImage(raster)

        val expected = (0 until width * height).sumOf { 255 - raster.pixels[it] }.toLong()
        assertEquals(expected, integral.windowSum(0, 0, width - 1, height - 1))
    }

    @Test
    fun windowSumMatchesABruteForceSumOverAnInteriorRectangle() {
        val random = Random(7)
        val width = 20
        val height = 15
        val raster = GrayscaleRaster(width, height, IntArray(width * height) { random.nextInt(0, 256) })
        val integral = IntegralImage(raster)

        val x0 = 3
        val y0 = 2
        val x1 = 11
        val y1 = 9
        var expected = 0L
        for (y in y0..y1) for (x in x0..x1) expected += 255 - raster[x, y]

        assertEquals(expected, integral.windowSum(x0, y0, x1, y1))
    }

    @Test
    fun windowSumClampsARectangleThatOverhangsTheRasterEdge() {
        val raster = GrayscaleRaster.filled(5, 5, value = 255 - 10) // darkness 10 everywhere
        val integral = IntegralImage(raster)

        // Requested window sticks out by 2 on every side; only the 5x5 raster itself should count.
        val sum = integral.windowSum(-2, -2, 6, 6)
        assertEquals(5L * 5L * 10L, sum)
    }

    @Test
    fun windowSumOfAWindowEntirelyOutsideTheRasterIsZero() {
        val raster = GrayscaleRaster.filled(4, 4, value = 0) // maximum darkness
        val integral = IntegralImage(raster)
        assertEquals(0L, integral.windowSum(10, 10, 12, 12))
    }

    @Test
    fun windowMeanOfAUniformRasterIsThatRastersOwnDarkness() {
        val raster = GrayscaleRaster.filled(21, 21, value = 255 - 40) // darkness 40 everywhere
        val integral = IntegralImage(raster)
        assertApproxEquals(40.0, integral.windowMean(10, 10, radius = 5), tolerance = 1e-9)
    }

    private fun assertApproxEquals(
        expected: Double,
        actual: Double,
        tolerance: Double,
    ) {
        kotlin.test.assertTrue(
            kotlin.math.abs(expected - actual) <= tolerance,
            "expected $expected, got $actual (tolerance $tolerance)",
        )
    }
}
