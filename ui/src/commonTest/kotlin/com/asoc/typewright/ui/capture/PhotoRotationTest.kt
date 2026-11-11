// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhotoRotationTest {
    @Test
    fun turnsAreReducedToAQuarterTurnClockwise() {
        assertEquals(0, PhotoRotation.normalised(0))
        assertEquals(90, PhotoRotation.normalised(90))
        assertEquals(270, PhotoRotation.normalised(-90))
        assertEquals(0, PhotoRotation.normalised(360))
        assertEquals(180, PhotoRotation.normalised(540))
        assertFailsWith<IllegalArgumentException> { PhotoRotation.normalised(45) }
    }

    @Test
    fun aQuarterTurnSwapsTheSidesAndAHalfTurnDoesNot() {
        assertEquals(3000 to 4000, PhotoRotation.turnedSize(4000, 3000, 90))
        assertEquals(3000 to 4000, PhotoRotation.turnedSize(4000, 3000, 270))
        assertEquals(4000 to 3000, PhotoRotation.turnedSize(4000, 3000, 180))
        assertEquals(4000 to 3000, PhotoRotation.turnedSize(4000, 3000, 0))
        assertTrue(PhotoRotation.swapsSides(-90))
        assertFalse(PhotoRotation.swapsSides(180))
    }

    @Test
    fun theCornersLandWhereAClockwiseTurnPutsThem() {
        // A 4 wide, 2 tall picture turned a quarter clockwise is 2 wide, 4 tall: its top-left corner goes to the top-right.
        assertEquals(1 to 0, PhotoRotation.turnedPosition(0, 0, 4, 2, 90))
        assertEquals(1 to 3, PhotoRotation.turnedPosition(3, 0, 4, 2, 90))
        assertEquals(0 to 0, PhotoRotation.turnedPosition(0, 1, 4, 2, 90))
        assertEquals(3 to 1, PhotoRotation.turnedPosition(0, 0, 4, 2, 180))
        assertEquals(0 to 3, PhotoRotation.turnedPosition(0, 0, 4, 2, 270))
        assertEquals(2 to 1, PhotoRotation.turnedPosition(2, 1, 4, 2, 0))
    }

    @Test
    fun itAgreesWithHowTheAnalysisPathTurnsTheSameFrame() {
        // The preview detector and the stored picture must be turned the same way, or the lock UI would describe another picture.
        val luma = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7)
        for (degrees in listOf(0, 90, 180, 270)) {
            val turned = LumaFrames.toRaster(luma, 4, 2, rowStride = 4, pixelStride = 1, rotationDegrees = degrees, maxSide = 100)
            val (w, h) = PhotoRotation.turnedSize(4, 2, degrees)
            assertEquals(w, turned.width, "width at $degrees")
            assertEquals(h, turned.height, "height at $degrees")
            for (y in 0 until 2) {
                for (x in 0 until 4) {
                    val (tx, ty) = PhotoRotation.turnedPosition(x, y, 4, 2, degrees)
                    assertEquals(luma[y * 4 + x].toInt(), turned.pixels[ty * w + tx], "pixel ($x,$y) at $degrees")
                }
            }
        }
    }
}
