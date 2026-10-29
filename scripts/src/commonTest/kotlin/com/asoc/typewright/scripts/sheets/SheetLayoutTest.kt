// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.scripts.sheets

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SheetLayoutTest {
    private val all = PaperSize.entries.flatMap { paper -> PrintScript.entries.map { SheetLayout.of(paper, it) } }

    @Test
    fun theFourMarksSitInTheCornersAndTheCodeBetweenTheUpperTwo() {
        for (layout in all) {
            val (tl, tr, br, bl) = layout.markCentres
            assertEquals(tl.y, tr.y, 1e-9)
            assertEquals(bl.y, br.y, 1e-9)
            assertEquals(tl.x, bl.x, 1e-9)
            assertEquals(tr.x, br.x, 1e-9)
            assertEquals(layout.pageWidthMm - SheetLayout.MARK_INSET_MM, tr.x, 1e-9)
            assertEquals(SheetIdentity.BIT_COUNT, layout.codeCentres.size)
            val half = SheetLayout.MARK_SIDE_MM / 2.0
            assertTrue(
                layout.codeCentres.all { it.x - SheetLayout.CODE_MODULE_MM / 2.0 > tl.x + half + 2.0 },
                "the code clears the left mark",
            )
            assertTrue(
                layout.codeCentres.all { it.x + SheetLayout.CODE_MODULE_MM / 2.0 < tr.x - half - 2.0 },
                "the code clears the right mark",
            )
            assertEquals(layout.pageWidthMm / 2.0, (layout.codeCentres.first().x + layout.codeCentres.last().x) / 2.0, 1e-9)
        }
    }

    @Test
    fun twentyFourBoxesFillTheGridInsideThePageWithoutOverlap() {
        for (layout in all) {
            assertEquals(24, layout.cellBoxes.size)
            for (box in layout.cellBoxes) {
                assertTrue(
                    box.left > 0 && box.right < layout.pageWidthMm && box.top > SheetLayout.GRID_TOP_MM - 1 &&
                        box.bottom < layout.pageHeightMm,
                )
                assertTrue(
                    layout.inkArea(layout.cellBoxes.indexOf(box)).let {
                        box.contains(MmPoint(it.left, it.top)) &&
                            box.contains(MmPoint(it.right, it.bottom))
                    },
                )
            }
            for (i in layout.cellBoxes.indices) {
                for (j in i + 1 until layout.cellBoxes.size) {
                    val a = layout.cellBoxes[i]
                    val b = layout.cellBoxes[j]
                    val overlap = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom
                    assertTrue(!overlap, "boxes $i and $j overlap")
                }
            }
            val widths = layout.cellBoxes.map { it.width }.toSet()
            assertEquals(1, widths.size, "every box is the same size")
            assertTrue(layout.cellBoxes.none { box -> layout.markCentres.any { box.contains(it) } }, "no mark sits in a box")
        }
    }

    @Test
    fun theMarksAreOutsideTheGridAndTheLabelBandFitsUnderEachBox() {
        for (layout in all) {
            val half = SheetLayout.MARK_SIDE_MM / 2.0
            val gridTop = layout.cellBoxes.minOf { it.top }
            val gridBottom = layout.cellBoxes.maxOf { it.bottom } + SheetLayout.LABEL_BAND_MM
            assertTrue(layout.markCentres[0].y + half < gridTop, "upper marks clear the grid")
            assertTrue(layout.markCentres[3].y - half > gridBottom, "lower marks clear the labels")
            for (i in 0 until 20) assertTrue(layout.cellBoxes[i].bottom + SheetLayout.LABEL_BAND_MM <= layout.cellBoxes[i + 4].top + 1e-9)
        }
    }

    @Test
    fun theFrameMapsTheScriptsGuidesIntoTheBoxAndBackAgain() {
        for (layout in all) {
            val box = layout.cellBoxes.first()
            val frame = layout.frame
            for (line in layout.guides.lines) {
                val v = frame.boxMm(0.0, line.y.toDouble()).y
                assertTrue(
                    v > 0.0 && v < box.height,
                    "${layout.script.label} guide ${line.label} falls inside the box: $v of ${box.height}",
                )
            }
            val left = frame.boxMm(layout.guides.advanceLeft.toDouble(), 0.0).x
            val right = frame.boxMm(layout.guides.advanceRight.toDouble(), 0.0).x
            assertTrue(left > 0.0 && right < box.width && right > left)
            assertEquals(box.width / 2.0, (left + right) / 2.0, 1e-9, "the advance is centred in the box")
            val (x, y) = frame.unitsAt(frame.boxMm(123.0, -77.0).x, frame.boxMm(123.0, -77.0).y)
            assertEquals(123.0, x, 1e-9)
            assertEquals(-77.0, y, 1e-9)
            assertEquals(1.0, frame.mmPerUnit * frame.unitsPerMm, 1e-12)
        }
    }

    @Test
    fun latinOnA4HasTheScaleTheSheetsAreDrawnAt() {
        val layout = SheetLayout.of(PaperSize.A4, PrintScript.LATIN)
        // Cap height 700 units: about 24 mm tall, a size one draws by hand.
        assertEquals(24.0, 700.0 * layout.frame.mmPerUnit, 1.5)
        assertEquals(36.0, layout.cellBoxes.first().height, 0.01)
    }

    @Test
    fun theFrameShowsTheWholeOfAnyGlyphThatStaysInsideTheGuidesWithRoomToSpare() {
        // The window runs from 6% of the guide span below the lowest guide to 10% above the highest.
        val latin = SheetLayout.of(PaperSize.A4, PrintScript.LATIN)
        val box = latin.cellBoxes.first()
        val top = latin.frame.unitsAt(0.0, 0.0).second
        val bottom = latin.frame.unitsAt(0.0, box.height).second
        assertEquals(700.0 + 0.10 * 910.0, top, 1e-6)
        assertEquals(-210.0 - 0.06 * 910.0, bottom, 1e-6)
    }

    @Test
    fun aMmRectKnowsItsEdgesCentreAndWhatItContains() {
        val r = MmRect(10.0, 20.0, 30.0, 40.0)
        assertEquals(40.0, r.right)
        assertEquals(60.0, r.bottom)
        assertEquals(MmPoint(25.0, 40.0), r.centre)
        assertTrue(r.contains(MmPoint(10.0, 20.0)) && !r.contains(MmPoint(9.9, 20.0)) && !abs(1.0).isNaN())
    }
}
