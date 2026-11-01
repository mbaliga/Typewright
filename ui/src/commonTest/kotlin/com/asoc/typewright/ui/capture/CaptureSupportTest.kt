// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.capture

import com.asoc.typewright.engine.trace.GrayscaleRaster
import com.asoc.typewright.engine.trace.capture.FoundMark
import com.asoc.typewright.engine.trace.capture.ImageCorner
import com.asoc.typewright.engine.trace.capture.MarkSearch
import com.asoc.typewright.engine.trace.capture.Pt
import com.asoc.typewright.scripts.sheets.PrintScript
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImagePixelsTest {
    @Test
    fun greyIsLumaOverWhitePaperSoATransparentPixelReadsAsPaper() {
        assertEquals(255, ImagePixels.lumaOnWhite(0x00000000))
        assertEquals(0, ImagePixels.lumaOnWhite(0xFF000000.toInt()))
        assertEquals(255, ImagePixels.lumaOnWhite(0xFFFFFFFF.toInt()))
        assertEquals(76, ImagePixels.lumaOnWhite(0xFFFF0000.toInt()), "red is 0.299 of white")
        assertEquals((0 * 128 + 255 * 127) / 255, ImagePixels.lumaOnWhite(0x80000000.toInt()), "half-transparent black is mid grey")
    }

    @Test
    fun aLargePictureIsReducedByAveragingNotBySkippingPixels() {
        // A one-pixel-wide dark line on white, 8 pixels wide: reduced by two it must come out grey, not vanish or stay black.
        val width = 8
        val height = 2
        val argb = IntArray(width * height) { if (it % width == 3) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        val small = ImagePixels.grayFromArgb(width, height, argb, maxSide = 4)
        assertEquals(4, small.width)
        assertEquals(1, small.height)
        assertEquals(listOf(255, 128, 255, 255), small.pixels.toList())
        // A picture already small enough is left alone.
        val same = ImagePixels.grayFromArgb(width, height, argb, maxSide = 100)
        assertEquals(width, same.width)
        assertEquals(0, same.pixels[3])
    }
}

class InkOutlineTest {
    @Test
    fun theDarkPartOfAPictureBecomesShapesAndTheHoleOfARingStaysAHole() {
        val size = 40
        val pixels =
            IntArray(size * size) { i ->
                val x = i % size
                val y = i / size
                val inRing = x in 8..31 && y in 8..31 && !(x in 16..23 && y in 16..23)
                if (inRing) 0 else 255
            }
        val outline = InkOutline.of(GrayscaleRaster(size, size, pixels), maxSide = 40)
        assertEquals(2, outline.loops.size, "the outside of the ring and its hole")
        assertTrue(outline.loops.all { it.size >= 4 })
        assertFalse(outline.isEmpty)
    }

    @Test
    fun aBlankPictureHasNoInk() {
        assertTrue(InkOutline.of(GrayscaleRaster.filled(30, 30), maxSide = 30).isEmpty)
    }
}

class TemplatePackTest {
    @Test
    fun theLatinTemplateBoxIsThePacksOwn() {
        val frame = TemplatePack.frameOf(PrintScript.LATIN)
        assertEquals(SvgFrame(-160.0, -820.0, 970.0, 1150.0), frame)
    }

    @Test
    fun everyLatinGlyphHasAnSvgUnderTheNameThatSaysWhichOneItIs() {
        val files = TemplatePack.packFiles(PrintScript.LATIN)
        val svgs = files.keys.filter { it.endsWith(".svg") }
        assertEquals(120, svgs.size)
        assertTrue("HOW_TO_USE.md" in files)
        val digitOne = svgs.first { it.endsWith("053_DIGIT_ONE.svg") }
        val text = files.getValue(digitOne).decodeToString()
        assertTrue(text.contains("viewBox=\"-160 -820 970 1150\""))
        assertTrue(text.contains("<g id=\"guides\">") && text.contains("<g id=\"DRAW_HERE\"></g>"))
        assertTrue(text.contains("x-height 500") || text.contains("x-height"), "the metric guides are drawn in")
        val (script, glyph) = assertNotNull(TemplatePack.glyphForFileName("053_DIGIT_ONE.svg"))
        assertEquals(PrintScript.LATIN, script)
        assertEquals("one", glyph.glyphName)
        assertEquals(glyph.glyphName, TemplatePack.glyphForFileName("C:\\Users\\me\\053_digit_one.SVG")?.second?.glyphName)
        assertNull(TemplatePack.glyphForFileName("mine.svg"))
    }
}

class CameraLockTrackerTest {
    private fun search(vararg corners: ImageCorner) =
        MarkSearch(corners.map { FoundMark(Pt(10.0, 10.0), 20.0, it) }, ImageCorner.entries - corners.toSet(), corners.size)

    private val three = arrayOf(ImageCorner.TOP_LEFT, ImageCorner.TOP_RIGHT, ImageCorner.BOTTOM_RIGHT)
    private val all = ImageCorner.entries.toTypedArray()

    @Test
    fun aCornerLocksOnlyAfterTwoFramesAndLetsGoOnlyAfterThreeMisses() {
        val tracker = CameraLockTracker()
        tracker.offer(search(*all))
        assertEquals(0, tracker.lockedCount, "one sighting is not a lock")
        tracker.offer(search(*all))
        assertTrue(tracker.armed)
        tracker.offer(search(*three))
        tracker.offer(search(*three))
        assertTrue(tracker.isLocked(ImageCorner.BOTTOM_LEFT), "two misses do not let go")
        assertTrue(tracker.armed)
        tracker.offer(search(*three))
        assertFalse(tracker.isLocked(ImageCorner.BOTTOM_LEFT))
        assertFalse(tracker.armed)
        assertNull(tracker.markAt(ImageCorner.BOTTOM_LEFT))
        assertNotNull(tracker.markAt(ImageCorner.TOP_LEFT))
    }

    @Test
    fun aMarkThatFlickersNeverLocks() {
        val tracker = CameraLockTracker()
        repeat(6) { i -> tracker.offer(if (i % 2 == 0) search(ImageCorner.TOP_LEFT) else search()) }
        assertFalse(tracker.isLocked(ImageCorner.TOP_LEFT))
    }

    @Test
    fun theStatusLineSaysHowManyAndWhichIsMissing() {
        val tracker = CameraLockTracker()
        assertEquals("0 of 4 corners · find the black squares", tracker.status(manual = false))
        repeat(2) { tracker.offer(search(*three)) }
        assertEquals("3 of 4 corners · the bottom left one is missing", tracker.status(manual = false))
        repeat(2) { tracker.offer(search(*all)) }
        assertEquals("4 of 4 corners · hold steady", tracker.status(manual = false))
        assertTrue(tracker.status(manual = true).startsWith("Manual: the limits are off"))
        tracker.reset()
        assertEquals(0, tracker.lockedCount)
    }

    @Test
    fun framesAreAnalysedNoMoreOftenThanTheInterval() {
        val tracker = CameraLockTracker(intervalMillis = 250)
        assertTrue(tracker.dueAt(1_000))
        assertFalse(tracker.dueAt(1_100))
        assertFalse(tracker.dueAt(1_249))
        assertTrue(tracker.dueAt(1_250))
        assertFalse(tracker.dueAt(1_300))
    }
}
