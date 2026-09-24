package dev.aarso.typewright.engine.trace

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HoleFillTest {
    @Test
    fun fillsASmallEnclosedHole() {
        val raster =
            rasterizeRectangle(20, 20, x0 = 3, y0 = 3, rectWidth = 12, rectHeight = 12)
                .withHoleAt(listOf(8 to 8, 9 to 8, 8 to 9, 9 to 9)) // a 2x2 hole, fully inside
        val filled = fillHoles(raster, maxArea = DEFAULT_HOLE_FILL_MIN_AREA)

        assertTrue(filled[8, 8])
        assertTrue(filled[9, 9])
        for (y in 3 until 15) for (x in 3 until 15) assertTrue(filled[x, y], "the whole rectangle should now read as solid ink")
    }

    @Test
    fun neverFillsTheUnboundedBackgroundEvenOnATinyRaster() {
        // A raster with no ink at all: the entire grid is one background component that touches
        // the border, so it must never be "filled" regardless of maxArea.
        val raster = BinaryRaster.filled(3, 3)
        val filled = fillHoles(raster, maxArea = 1000)
        assertEquals(0, filled.ink.count { it })
    }

    @Test
    fun doesNotFillAHoleLargerThanMaxArea() {
        val raster =
            rasterizeRectangle(20, 20, x0 = 2, y0 = 2, rectWidth = 16, rectHeight = 16)
                .let { rectangle ->
                    val ink = rectangle.ink.copyOf()
                    for (y in 6 until 14) for (x in 6 until 14) ink[y * 20 + x] = false // an 8x8 = 64-pixel hole
                    BinaryRaster(20, 20, ink)
                }
        val filled = fillHoles(raster, maxArea = 10)
        assertFalse(filled[9, 9], "a hole bigger than maxArea should be left alone")
    }

    @Test
    fun doesNotFillABackgroundRegionThatTouchesTheBorderEvenIfSmall() {
        // A "C" shape: background pokes in from the right edge, so that notch's background
        // component touches the border and must never be filled, however small it is.
        val raster =
            rasterizeRectangle(10, 10, x0 = 1, y0 = 1, rectWidth = 8, rectHeight = 8)
                .withHoleAt(listOf(8 to 4, 9 to 4)) // reaches the raster's right edge (x = 9)
        val filled = fillHoles(raster, maxArea = 1000)
        assertFalse(filled[8, 4])
        assertFalse(filled[9, 4])
    }

    @Test
    fun maxAreaZeroFillsNothing() {
        val raster =
            rasterizeRectangle(20, 20, x0 = 3, y0 = 3, rectWidth = 12, rectHeight = 12)
                .withHoleAt(listOf(8 to 8))
        val filled = fillHoles(raster, maxArea = 0)
        assertFalse(filled[8, 8])
    }
}
