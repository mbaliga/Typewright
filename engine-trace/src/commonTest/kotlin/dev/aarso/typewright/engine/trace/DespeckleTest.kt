package dev.aarso.typewright.engine.trace

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DespeckleTest {
    @Test
    fun removesAComponentSmallerThanMinArea() {
        // A single isolated pixel (area 1) next to a real 5x5 mark (area 25).
        val raster =
            rasterizeRectangle(20, 20, x0 = 5, y0 = 5, rectWidth = 5, rectHeight = 5)
                .withInkAt(listOf(1 to 1))
        val cleaned = despeckle(raster, minArea = DEFAULT_DESPECKLE_MIN_AREA)

        assertFalse(cleaned[1, 1], "the isolated speckle pixel should be removed")
        for (y in 5 until 10) for (x in 5 until 10) assertTrue(cleaned[x, y], "the real mark must survive untouched")
    }

    @Test
    fun keepsAComponentAtOrAboveMinArea() {
        val raster = rasterizeRectangle(10, 10, x0 = 2, y0 = 2, rectWidth = 3, rectHeight = 2) // area 6
        val cleaned = despeckle(raster, minArea = 6)
        for (y in 2 until 4) for (x in 2 until 5) assertTrue(cleaned[x, y])
    }

    @Test
    fun removesAComponentOneBelowMinArea() {
        val raster = rasterizeRectangle(10, 10, x0 = 2, y0 = 2, rectWidth = 3, rectHeight = 2) // area 6
        val cleaned = despeckle(raster, minArea = 7)
        for (y in 2 until 4) for (x in 2 until 5) assertFalse(cleaned[x, y])
    }

    @Test
    fun minAreaZeroRemovesNothing() {
        val raster =
            rasterizeRectangle(20, 20, x0 = 5, y0 = 5, rectWidth = 5, rectHeight = 5)
                .withInkAt(listOf(1 to 1, 18 to 18))
        val cleaned = despeckle(raster, minArea = 0)
        assertEquals(raster.width, cleaned.width)
        for (i in raster.ink.indices) assertEquals(raster.ink[i], cleaned.ink[i])
    }

    @Test
    fun anAllBackgroundRasterStaysAllBackground() {
        val raster = BinaryRaster.filled(8, 8)
        val cleaned = despeckle(raster)
        assertEquals(0, cleaned.ink.count { it })
    }
}
