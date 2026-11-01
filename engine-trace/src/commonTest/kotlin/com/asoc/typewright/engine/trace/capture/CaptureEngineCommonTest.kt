// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.trace.capture

import com.asoc.typewright.capturetestkit.CellArt
import com.asoc.typewright.capturetestkit.CellJudge
import com.asoc.typewright.capturetestkit.PhotoSpec
import com.asoc.typewright.capturetestkit.ProxyArt
import com.asoc.typewright.capturetestkit.SyntheticPhotos
import com.asoc.typewright.scripts.sheets.GuideStyle
import com.asoc.typewright.scripts.sheets.PaperSize
import com.asoc.typewright.scripts.sheets.PrintScript
import com.asoc.typewright.scripts.sheets.PrintSheets
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The pure capture maths, in common code so the web target runs it as well as the JVM: the homography, the corner search on a
 * rendered sheet, and the cell reader. The photographs are SYNTHETIC (the testkit's sheets with ring drawings, so no font file is read);
 * each answer is checked against what the generator put there, not against the code under test.
 */
class CaptureEngineCommonTest {
    private val sheet = PrintSheets.forScript(PrintScript.LATIN).sheets[0]
    private val spec = PhotoSpec(seed = 5, tiltDegrees = 12.0, tiltAxisDegrees = 70.0, rollDegrees = 4.0, fill = 0.8, blurSigmaPx = 0.8)

    private fun photo(
        art: CellArt = ProxyArt,
        photoSpec: PhotoSpec = spec,
    ) = SyntheticPhotos.make(sheet, PaperSize.A4, GuideStyle.LIGHT, photoSpec, art)

    // ---- Homography ------------------------------------------------------------------------

    @Test
    fun aHomographyFromFourPointsMapsThemAndItsInverseMapsThemBack() {
        val from = listOf(Pt(0.0, 0.0), Pt(210.0, 0.0), Pt(210.0, 297.0), Pt(0.0, 297.0))
        val to = listOf(Pt(100.0, 80.0), Pt(900.0, 140.0), Pt(820.0, 1500.0), Pt(60.0, 1380.0))
        val h = assertNotNull(Homography.fromCorrespondences(from, to))
        for (i in 0 until 4) {
            val mapped = h.apply(from[i])
            assertEquals(to[i].x, mapped.x, 1e-6)
            assertEquals(to[i].y, mapped.y, 1e-6)
        }
        val back = assertNotNull(h.inverse())
        val centre = Pt(105.0, 148.5)
        val there = h.apply(centre)
        val home = back.apply(there)
        assertEquals(centre.x, home.x, 1e-6)
        assertEquals(centre.y, home.y, 1e-6)
        // A projective map keeps straight lines straight: the page's middle of an edge lands on the line between the edge's corners.
        val edgeMid = h.apply(Pt(105.0, 0.0))
        val a = to[0]
        val b = to[1]
        val cross = (b.x - a.x) * (edgeMid.y - a.y) - (b.y - a.y) * (edgeMid.x - a.x)
        assertEquals(0.0, cross / 800.0, 1e-3)
    }

    @Test
    fun aHomographyFromPointsOnALineIsRefused() {
        val line = listOf(Pt(0.0, 0.0), Pt(1.0, 1.0), Pt(2.0, 2.0), Pt(3.0, 3.0))
        assertNull(Homography.fromCorrespondences(line, line))
    }

    // ---- corner marks ----------------------------------------------------------------------

    @Test
    fun theFourMarksAreFoundWhereTheCameraPutThem() {
        val shot = photo()
        val search = findCornerMarks(shot.gray)
        assertTrue(search.complete, "${search.foundCount} of 4")
        assertTrue(search.missing.isEmpty())
        for (mark in search.marks) {
            val truth = shot.pageToPhoto.apply(shot.layout.markCentres[mark.corner.ordinal].let { Pt(it.x, it.y) })
            // A sheet turned by 4 degrees is still read clockwise from the picture's top left; each mark is within a few pixels of the truth.
            assertTrue(
                abs(truth.x - mark.centre.x) < 4.0 && abs(truth.y - mark.centre.y) < 4.0,
                "${mark.corner}: found ${mark.centre}, drawn at $truth",
            )
        }
    }

    @Test
    fun aThumbOverOneCornerLeavesThreeAndNamesTheMissingOne() {
        val shot = photo(photoSpec = spec.copy(seed = 6, coveredMarks = setOf(2)))
        val search = findCornerMarks(shot.gray)
        assertEquals(3, search.foundCount)
        assertEquals(listOf(ImageCorner.BOTTOM_RIGHT), search.missing)
        val read = SheetCapture.read(shot.gray)
        assertIs<CaptureResult.Failure>(read)
        assertIs<CaptureFailure.CornersMissing>(read.failure)
        assertTrue(
            read.failure.sentence.contains("3 of 4 corners") && read.failure.sentence.contains("bottom right"),
            read.failure.sentence,
        )
    }

    // ---- reading a sheet and its cells -----------------------------------------------------

    @Test
    fun aSheetIsIdentifiedAndEveryCellHoldsTheInkOfItsTemplateGlyph() {
        val shot = photo()
        val read = SheetCapture.read(shot.gray)
        val captured = assertIs<CaptureResult.Success>(read).sheet
        assertEquals(PrintScript.LATIN, captured.identity.script)
        assertEquals(0, captured.identity.index)
        assertEquals(PaperSize.A4, captured.identity.paper)
        assertEquals(24, captured.cells.size)
        val verdicts = captured.cells.map { CellJudge.judge(it, ProxyArt.contours(sheet.glyphs[it.index], it.index)) }
        assertTrue(verdicts.count { it.correct } >= 23, verdicts.filter { !it.correct }.joinToString { "${it.index}: ${it.miss}" })
        // The cell takes its name from the template.
        assertEquals(sheet.glyphs.map { it.glyphName }, captured.cells.map { it.glyphName })
    }

    @Test
    fun aCellNobodyDrewInIsEmptyAndTheOthersAreNot() {
        val withoutSeven = CellArt { glyph, index -> if (index == 7) emptyList() else ProxyArt.contours(glyph, index) }
        val captured = assertIs<CaptureResult.Success>(SheetCapture.read(photo(withoutSeven).gray)).sheet
        assertIs<CellStatus.Empty>(captured.cells[7].status)
        assertNull(captured.cells[7].raster)
        assertEquals(1, captured.emptyCount)
        assertEquals("23 read · 1 empty", captured.summary)
        assertTrue(captured.cells[6].raster != null && captured.cells[6].inkBounds != null)
    }

    @Test
    fun theInkBoxOfACellIsTheBoxOfTheRingDrawnInIt() {
        val captured = assertIs<CaptureResult.Success>(SheetCapture.read(photo().gray)).sheet
        for (cell in captured.cells) {
            val truth = ProxyArt.contours(sheet.glyphs[cell.index], cell.index)
            val ring = truth.first().points
            val xs = ring.map { it.point.x }
            val ys = ring.map { it.point.y }
            val box = assertNotNull(cell.inkBounds, "cell ${cell.index}")
            assertTrue(abs(box.xMin - xs.min()) <= 12 && abs(box.xMax - xs.max()) <= 12, "cell ${cell.index} x: $box")
            assertTrue(abs(box.yMin - ys.min()) <= 12 && abs(box.yMax - ys.max()) <= 12, "cell ${cell.index} y: $box")
        }
    }

    @Test
    fun theEdgeOfAFlatGreyRasterHasNoMeasurableMarksAndIsSaidSo() {
        val flat = flatRaster(1500, 2000, 200)
        val layout =
            com.asoc.typewright.scripts.sheets.SheetLayout
                .of(PaperSize.A4, PrintScript.LATIN)
        val toPhoto =
            assertNotNull(
                Homography.fromCorrespondences(
                    layout.markCentres.map { Pt(it.x, it.y) },
                    layout.markCentres.map {
                        Pt(it.x * 5, it.y * 5)
                    },
                ),
            )
        assertNull(SheetCapture.edgeWidthMm(flat, toPhoto, layout))
        val quality =
            CaptureQuality(
                edgeWidth = null,
                perspective = Measurement(1.0, 1.8, "×"),
                glare = Measurement(0.0, 0.08, "of the sheet"),
                shadow = Measurement(0.0, 0.45, "darker"),
                markContrast = Measurement(0.9, 0.45, "darker than paper"),
            )
        val failure = assertNotNull(SheetCapture.gate(quality, CaptureOptions()))
        assertEquals(CaptureFailure.NoEdgeFound, failure)
        assertTrue(failure.sentence.contains("edges could not be measured"), failure.sentence)
        assertTrue(!failure.sentence.contains("2147"), "no saturated figure is ever printed")
    }
}

private fun flatRaster(
    width: Int,
    height: Int,
    gray: Int,
) = com.asoc.typewright.engine.trace
    .GrayscaleRaster(width, height, IntArray(width * height) { gray })
