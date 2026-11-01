// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.trace.capture

import com.asoc.typewright.engine.trace.CleanParameters
import com.asoc.typewright.engine.trace.GrayscaleRaster
import com.asoc.typewright.engine.trace.TraceParameters
import com.asoc.typewright.engine.trace.cleanCell
import com.asoc.typewright.engine.trace.estimateEdgeSoftnessPx
import com.asoc.typewright.scripts.sheets.PaperSize
import com.asoc.typewright.scripts.sheets.PrintScript
import com.asoc.typewright.scripts.sheets.SheetLayout
import com.asoc.typewright.scripts.sheets.SheetRenderer
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The small pieces added for photographs that are not clean: the softness of an edge, the leftovers of a printed guide, and the
 * settings the Trace screen shows for them. Each has an answer worked out from the way the input was made.
 */
class GuideGhostAndSoftnessTest {
    /** A 160 by 100 raster of paper (gray 230) with a bar of ink (gray 40) to the right of x = 80, its edge blurred by a Gaussian of [sigma] pixels. */
    private fun blurredBar(sigma: Double): GrayscaleRaster {
        val w = 160
        val h = 100
        val pixels =
            IntArray(w * h) {
                val x = it % w
                val ink = PrintedGuides.normalCdf((x + 0.5 - 80.0) / sigma)
                (230.0 - 190.0 * ink).toInt()
            }
        return GrayscaleRaster(w, h, pixels)
    }

    @Test
    fun theSoftnessOfABlurredEdgeIsTheBlurThatMadeIt() {
        for (sigma in listOf(2.0, 3.0, 4.0)) {
            val reading = estimateEdgeSoftnessPx(cleanCell(blurredBar(sigma)))
            assertEquals(sigma, reading, 0.2 * sigma, "blur $sigma")
        }
        // Softer reads softer.
        assertTrue(estimateEdgeSoftnessPx(cleanCell(blurredBar(4.0))) > 1.5 * estimateEdgeSoftnessPx(cleanCell(blurredBar(2.0))))
    }

    @Test
    fun aCellWithNoInkHasNoSoftness() {
        assertEquals(0.0, estimateEdgeSoftnessPx(cleanCell(GrayscaleRaster(60, 60, IntArray(3600) { 230 }))))
    }

    @Test
    fun theCleanStageNeedsInkOfTheDarknessItIsToldTo() {
        val faint = GrayscaleRaster(80, 80, IntArray(6400) { if (it % 80 in 30..50 && it / 80 in 20..60) 200 else 230 })
        // 200 on 230 paper is 13% darker: not ink at the usual bar, ink at a lower one.
        assertFalse(cleanCell(faint).inkFound)
        assertTrue(cleanCell(faint, CleanParameters(minimumLevel = 0.05, minimumInk = 0.1)).inkFound)
        assertFailsWith<IllegalArgumentException> { CleanParameters(minimumInk = 0.01) }
    }

    // ---- guide leftovers -------------------------------------------------------------------

    private val layout = SheetLayout.of(PaperSize.A4, PrintScript.LATIN)
    private val box = layout.cellBoxes[0]
    private val area = layout.inkArea(0)
    private val pxPerMm = 10.0
    private val width = (area.width * pxPerMm).toInt()
    private val height = (area.height * pxPerMm).toInt()
    private val band = PrintedGuides.band(layout, box, area, width, height, pxPerMm, 1.8)

    private fun row(guideY: Int): Int = (box.top + layout.frame.boxMm(0.0, guideY.toDouble()).y - area.top).times(pxPerMm).toInt()

    @Test
    fun theBandLiesRoundEveryGuideAndNowhereElse() {
        for (guide in layout.guides.lines) {
            val j = row(guide.y).coerceIn(0, height - 1)
            assertTrue(band[j * width + width / 2], "on the ${guide.label} guide")
            // 0.8 mm (8 pixels) above the line is still in the band, which is 1.8 mm wide.
            if (j - 8 >= 0) assertTrue(band[(j - 8) * width + width / 2], "0.8 mm above the ${guide.label} guide")
        }
        // The middle of the cell, away from every guide line and from the advance guides, is clear.
        val clearRow = (height * 0.62).toInt()
        assertTrue(layout.guides.lines.none { abs(row(it.y) - clearRow) < 12 }, "the test row is between guides")
        assertFalse(band[clearRow * width + width / 2])
    }

    private fun paper(): IntArray = IntArray(width * height) { 230 }

    @Test
    fun aThinMarkOnAGuideIsTakenOutAndADrawingCrossingItIsNot() {
        val j = row(0).coerceIn(40, height - 20)
        val pixels = paper()
        // A leftover of a guide: 3 pixels high (0.3 mm), medium grey, along the baseline over the left third of the cell.
        for (y in j - 1..j + 1) for (x in 0 until width / 3) pixels[y * width + x] = 150
        // A drawing: a block of dark ink 12 pixels wide and 70 high, standing on the baseline, so it crosses the band.
        val left = width / 2
        for (y in j - 60..j + 10) for (x in left until left + 12) pixels[y * width + x] = 60
        val cleaned = PrintedGuides.withoutGhosts(GrayscaleRaster(width, height, pixels), band)
        assertTrue(cleaned.pixels[j * width + 10] >= 225, "the guide's leftover is painted out: ${cleaned.pixels[j * width + 10]}")
        assertEquals(60, cleaned.pixels[(j - 30) * width + left + 5], "the drawing is untouched")
        assertEquals(60, cleaned.pixels[j * width + left + 5], "also where it crosses the guide")
    }

    @Test
    fun aLeftoverThatTouchesADrawingIsPartOfWhatIsKept() {
        // The leftover and the block are one mark; it reaches outside the band, so it is a drawing and is not touched.
        val j = row(0).coerceIn(40, height - 20)
        val pixels = paper()
        for (y in j - 1..j + 1) for (x in 0 until width) pixels[y * width + x] = 150
        for (y in j - 60..j + 10) for (x in width / 2 until width / 2 + 12) pixels[y * width + x] = 60
        val cleaned = PrintedGuides.withoutGhosts(GrayscaleRaster(width, height, pixels), band)
        assertEquals(150, cleaned.pixels[j * width + 10])
    }

    @Test
    fun aMarkThatTouchesNothingButTheGuideIsTakenOutAndOneWiderThanTheBandIsKept() {
        val j = row(0).coerceIn(30, height - 30)
        val thin = paper()
        for (y in j - 1..j + 1) for (x in 40 until 140) thin[y * width + x] = 150
        val cleanedThin = PrintedGuides.withoutGhosts(GrayscaleRaster(width, height, thin), band)
        assertTrue(cleanedThin.pixels.all { it >= 225 }, "a hairline lying on the guide is the guide")
        // A stroke 24 pixels (2.4 mm) wide along the same guide is wider than the band: a pen line, kept.
        val thick = paper()
        for (y in j - 12 until j + 12) for (x in 40 until 140) thick[y * width + x] = 90
        val cleanedThick = PrintedGuides.withoutGhosts(GrayscaleRaster(width, height, thick), band)
        assertEquals(90, cleanedThick.pixels[j * width + 80])
    }

    @Test
    fun aRasterWithNothingDarkIsReturnedAsItWas() {
        val raster = GrayscaleRaster(width, height, paper())
        assertTrue(PrintedGuides.withoutGhosts(raster, band) === raster)
    }

    @Test
    fun aMarkerLineAMillimetreOrMoreAcrossLyingOnAGuideIsADrawingAndAHairlineIsNot() {
        val j = row(0).coerceIn(30, height - 30)
        val limit = ceil(CellReader.GHOST_MAX_THICKNESS_MM * pxPerMm).toInt()
        // A 1.4 mm underscore (14 pixels) centred on the guide lies wholly inside the 1.8 mm band, and is kept.
        val marker = paper()
        for (y in j - 7 until j + 7) for (x in 40 until 140) marker[y * width + x] = 90
        val kept = PrintedGuides.withoutGhosts(GrayscaleRaster(width, height, marker), band, limit)
        assertEquals(90, kept.pixels[j * width + 80], "a 1.4 mm line on a guide is the person's")
        // The same line without the thickness limit would have been painted out: the limit is what keeps it.
        val without = PrintedGuides.withoutGhosts(GrayscaleRaster(width, height, marker), band)
        assertTrue(without.pixels[j * width + 80] >= 225)
        // A 0.5 mm line (5 pixels) is a leftover whatever the limit.
        val hairline = paper()
        for (y in j - 2..j + 2) for (x in 40 until 140) hairline[y * width + x] = 150
        val gone = PrintedGuides.withoutGhosts(GrayscaleRaster(width, height, hairline), band, limit)
        assertTrue(gone.pixels.all { it >= 225 })
        // The widest guide the tests print (three times 0.3 mm) is still a leftover.
        val heavy = paper()
        for (y in j - 4..j + 4) for (x in 40 until 140) heavy[y * width + x] = 150
        assertTrue(PrintedGuides.withoutGhosts(GrayscaleRaster(width, height, heavy), band, limit).pixels.all { it >= 225 })
    }

    @Test
    fun theBandAlsoCoversEachGuidesPrintedName() {
        // The name sits just above its line at the left of the box. Every guide has a band 1.8 mm wide (0.9 mm either side), so the name is
        // probed 1.5 mm above the line (15 pixels), clear of that band, at a column inside the name's own width.
        val first = layout.guides.lines.first()
        val j = row(first.y)
        val probeRow = j - 15
        assertTrue(probeRow in 0 until height, "the probe row $probeRow is in the cell")
        val nameLeft = ((box.left + SheetRenderer.GUIDE_LABEL_INSET_MM - area.left) * pxPerMm).toInt()
        val inName = nameLeft + 3
        assertTrue(band[probeRow * width + inName], "the printed name of the ${first.label} guide is in the band")
        // Away from the name at the same height the band is clear, so the probe really tests the label and not the guide band.
        assertFalse(band[probeRow * width + width / 2])
    }

    // ---- the settings the Trace screen shows ------------------------------------------------

    @Test
    fun theNewSettingsAreOnTheScreenAndChangeTheTrace() {
        val defaults = TraceParameters()
        val ids = defaults.specs().map { it.id }
        assertTrue("fit.denoise" in ids && "fit.square" in ids)
        assertTrue(defaults.denoise && defaults.squareCorners)
        assertFalse(defaults.withParameter("fit.denoise", 0.0).denoise)
        assertFalse(defaults.withParameter("fit.square", 0.2).squareCorners)
        assertTrue(defaults.withParameter("fit.square", 0.7).squareCorners)
        // Neither is among the three shown by default: they are behind "adjust".
        assertFalse(defaults.specs().first { it.id == "fit.denoise" }.shownByDefault)
        assertFalse(defaults.specs().first { it.id == "fit.square" }.shownByDefault)
    }
}
