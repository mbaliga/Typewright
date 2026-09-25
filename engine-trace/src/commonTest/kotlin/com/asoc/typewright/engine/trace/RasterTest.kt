// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.trace

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RasterTest {
    @Test
    fun grayscaleRasterReadsBackWhatItWasBuiltFrom() {
        val raster = GrayscaleRaster(2, 2, intArrayOf(0, 128, 255, 64))
        assertEquals(0, raster[0, 0])
        assertEquals(128, raster[1, 0])
        assertEquals(255, raster[0, 1])
        assertEquals(64, raster[1, 1])
    }

    @Test
    fun grayscaleRasterFilledFillsEveryPixel() {
        val raster = GrayscaleRaster.filled(3, 2, value = 200)
        for (y in 0 until 2) for (x in 0 until 3) assertEquals(200, raster[x, y])
    }

    @Test
    fun grayscaleRasterRejectsAWrongSizedPixelArray() {
        assertFailsWith<IllegalArgumentException> { GrayscaleRaster(2, 2, intArrayOf(0, 0, 0)) }
    }

    @Test
    fun grayscaleRasterRejectsAnOutOfRangeValue() {
        assertFailsWith<IllegalArgumentException> { GrayscaleRaster(1, 1, intArrayOf(256)) }
        assertFailsWith<IllegalArgumentException> { GrayscaleRaster(1, 1, intArrayOf(-1)) }
    }

    @Test
    fun grayscaleRasterRejectsAnOutOfBoundsRead() {
        val raster = GrayscaleRaster.filled(2, 2)
        assertFailsWith<IllegalArgumentException> { raster[2, 0] }
        assertFailsWith<IllegalArgumentException> { raster[0, -1] }
    }

    @Test
    fun binaryRasterReadsBackWhatItWasBuiltFrom() {
        val raster = BinaryRaster(2, 2, booleanArrayOf(true, false, false, true))
        assertTrue(raster[0, 0])
        assertFalse(raster[1, 0])
        assertFalse(raster[0, 1])
        assertTrue(raster[1, 1])
    }

    @Test
    fun binaryRasterOutOfBoundsReadsAsBackground() {
        val raster = BinaryRaster.filled(2, 2, value = true)
        assertFalse(raster[-1, 0])
        assertFalse(raster[2, 0])
        assertFalse(raster[0, -1])
        assertFalse(raster[0, 2])
    }

    @Test
    fun binaryRasterFilledFillsEveryPixel() {
        val raster = BinaryRaster.filled(3, 2, value = true)
        for (y in 0 until 2) for (x in 0 until 3) assertTrue(raster[x, y])
    }

    @Test
    fun binaryRasterRejectsAWrongSizedInkArray() {
        assertFailsWith<IllegalArgumentException> { BinaryRaster(2, 2, booleanArrayOf(true, false)) }
    }
}
