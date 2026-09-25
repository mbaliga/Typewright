// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.engine.trace

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConnectedComponentsTest {
    @Test
    fun oneFilledRectangleIsOneComponentOfItsOwnArea() {
        val raster = rasterizeRectangle(10, 10, x0 = 2, y0 = 2, rectWidth = 4, rectHeight = 3)
        val labeling = labelComponents(10, 10, Connectivity.EIGHT) { x, y -> raster[x, y] }
        assertEquals(1, labeling.componentCount)
        assertEquals(12, labeling.componentSizes[0])
        assertFalse(labeling.componentTouchesBorder[0])
    }

    @Test
    fun twoSeparateRectanglesAreTwoComponents() {
        val raster =
            rasterizeRectangle(20, 10, x0 = 1, y0 = 1, rectWidth = 3, rectHeight = 3)
                .withInkAt((5 until 8).flatMap { x -> (1 until 4).map { y -> x to y } })
        val labeling = labelComponents(20, 10, Connectivity.EIGHT) { x, y -> raster[x, y] }
        assertEquals(2, labeling.componentCount)
        assertEquals(9, labeling.componentSizes[0])
        assertEquals(9, labeling.componentSizes[1])
    }

    @Test
    fun eightConnectivityJoinsTwoPixelsThatOnlyTouchAtACorner() {
        val raster = BinaryRaster.filled(5, 5).withInkAt(listOf(1 to 1, 2 to 2))
        val labeling = labelComponents(5, 5, Connectivity.EIGHT) { x, y -> raster[x, y] }
        assertEquals(1, labeling.componentCount)
        assertEquals(2, labeling.componentSizes[0])
    }

    @Test
    fun fourConnectivityDoesNotJoinTwoPixelsThatOnlyTouchAtACorner() {
        val raster = BinaryRaster.filled(5, 5).withInkAt(listOf(1 to 1, 2 to 2))
        val labeling = labelComponents(5, 5, Connectivity.FOUR) { x, y -> raster[x, y] }
        assertEquals(2, labeling.componentCount)
        assertEquals(1, labeling.componentSizes[0])
        assertEquals(1, labeling.componentSizes[1])
    }

    @Test
    fun aComponentTouchingAnyRasterEdgeIsFlaggedAsTouchingTheBorder() {
        val raster = rasterizeRectangle(10, 10, x0 = 0, y0 = 4, rectWidth = 3, rectHeight = 2)
        val labeling = labelComponents(10, 10, Connectivity.EIGHT) { x, y -> raster[x, y] }
        assertEquals(1, labeling.componentCount)
        assertTrue(labeling.componentTouchesBorder[0])
    }

    @Test
    fun getReturnsMinusOneForABackgroundCell() {
        val raster = rasterizeRectangle(6, 6, x0 = 1, y0 = 1, rectWidth = 2, rectHeight = 2)
        val labeling = labelComponents(6, 6, Connectivity.EIGHT) { x, y -> raster[x, y] }
        assertEquals(-1, labeling[0, 0])
        assertEquals(0, labeling[1, 1])
    }

    @Test
    fun anEmptyGridHasNoComponents() {
        val labeling = labelComponents(5, 5, Connectivity.EIGHT) { _, _ -> false }
        assertEquals(0, labeling.componentCount)
    }
}
