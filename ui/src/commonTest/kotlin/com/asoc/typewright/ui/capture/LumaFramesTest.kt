// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.capture

import com.asoc.typewright.engine.trace.GrayscaleRaster
import com.asoc.typewright.project.capture.CaptureKind
import com.asoc.typewright.project.capture.CaptureManifest
import com.asoc.typewright.project.capture.CaptureRecord
import com.asoc.typewright.ui.deck.StationStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LumaFramesTest {
    /** A 4 wide, 2 tall frame whose pixels are their own number, with a row stride of 6 (padding) and a pixel stride of 1. */
    private val luma = byteArrayOf(0, 1, 2, 3, 99, 99, 4, 5, 6, 7, 99, 99)

    private fun raster(rotation: Int): GrayscaleRaster =
        LumaFrames.toRaster(luma, 4, 2, rowStride = 6, pixelStride = 1, rotationDegrees = rotation, maxSide = 100)

    @Test
    fun anUnturnedFrameKeepsItsPixelsAndSkipsThePadding() {
        val r = raster(0)
        assertEquals(4, r.width)
        assertEquals(2, r.height)
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7), r.pixels.toList())
    }

    @Test
    fun aQuarterTurnClockwiseMakesTheBottomRowTheLeftColumn() {
        val r = raster(90)
        assertEquals(2, r.width)
        assertEquals(4, r.height)
        assertEquals(listOf(4, 0, 5, 1, 6, 2, 7, 3), r.pixels.toList())
    }

    @Test
    fun aHalfTurnReversesTheFrameAndThreeQuartersIsTheOtherWay() {
        assertEquals(listOf(7, 6, 5, 4, 3, 2, 1, 0), raster(180).pixels.toList())
        val r = raster(270)
        assertEquals(2, r.width)
        assertEquals(listOf(3, 7, 2, 6, 1, 5, 0, 4), r.pixels.toList())
    }

    @Test
    fun aBigFrameIsReducedToTheLongSideAndAPixelStrideOfTwoIsRead() {
        // Interleaved UV after each Y: a pixel stride of 2.
        val width = 8
        val height = 4
        val data = ByteArray(width * 2 * height) { if (it % 2 == 0) (it / 2 % width * 10).toByte() else 77 }
        val r = LumaFrames.toRaster(data, width, height, rowStride = width * 2, pixelStride = 2, rotationDegrees = 0, maxSide = 4)
        assertEquals(4, r.width)
        assertEquals(2, r.height)
        assertEquals(listOf(10, 30, 50, 70), r.pixels.take(4), "every other column of the 0, 10, 20... ramp, never the 77 chroma bytes")
    }

    @Test
    fun aRotationThatIsNotAQuarterTurnIsRefused() {
        assertFailsWith<IllegalArgumentException> { raster(45) }
    }
}

class CaptureStatusTest {
    private fun sheet(
        id: String,
        index: Int,
        count: Int = 5,
    ) = CaptureRecord(id, CaptureKind.FILE, "a.png", "captures/$id.png", script = "LATIN", sheet = index, sheetCount = count)

    @Test
    fun noPicturesIsNotStartedSomeSheetsLeavesWorkAndAllOfThemIsDone() {
        assertEquals(StationStatus.NOT_STARTED, captureStationStatus(CaptureManifest()))
        assertEquals(StationStatus.TO_REFIT, captureStationStatus(CaptureManifest(listOf(sheet("cap-1", 0), sheet("cap-2", 1)))))
        assertEquals(StationStatus.DONE, captureStationStatus(CaptureManifest(List(5) { sheet("cap-$it", it) })))
    }

    @Test
    fun theSameSheetTwiceIsStillOneSheet() {
        assertEquals(StationStatus.TO_REFIT, captureStationStatus(CaptureManifest(List(5) { sheet("cap-$it", 0) })))
    }

    @Test
    fun svgDrawingsAloneNeedNoSheets() {
        val svg = CaptureRecord("cap-1", CaptureKind.SVG, "053_DIGIT_ONE.svg", "captures/cap-1.svg", glyph = "one")
        assertEquals(StationStatus.DONE, captureStationStatus(CaptureManifest(listOf(svg))))
    }
}
