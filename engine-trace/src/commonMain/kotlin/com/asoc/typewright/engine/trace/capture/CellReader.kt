// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.trace.capture

import com.asoc.typewright.core.geometry.OutlineBounds
import com.asoc.typewright.engine.trace.CleanParameters
import com.asoc.typewright.engine.trace.CleanedCell
import com.asoc.typewright.engine.trace.GrayscaleRaster
import com.asoc.typewright.engine.trace.RasterFrame
import com.asoc.typewright.engine.trace.cleanCell
import com.asoc.typewright.scripts.sheets.CellFrame
import com.asoc.typewright.scripts.sheets.MmRect
import com.asoc.typewright.scripts.sheets.PrintSheetSpec
import com.asoc.typewright.scripts.sheets.SheetLayout
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Cuts each cell of a sheet out of its photograph and reads what is drawn in it.
 *
 * A cell's drawing area is the box less [SheetLayout.INK_INSET_MM] all round, where the printed border sits. It is sampled flat
 * through the sheet's homography at the requested resolution, the paper's brightness is divided out, and the Clean stage's
 * level is taken from the cell itself but never below [GUIDE_CEILING]: the guides are printed at about 0.2 darkness and a
 * pen or pencil line is well past 0.4, so a guide is never ink and needs no subtraction. What is left of the printed guides
 * after the threshold (the soft edge of a line, a speck of paper texture) is dropped by lifting everything under
 * [PAPER_FLOOR] to white, so the raster handed on is ink on white, as the trace expects.
 *
 * **Every threshold here is our heuristic** ([GUIDE_CEILING], [PAPER_FLOOR], [MIN_INK_PIXELS], [FAINT_DARKNESS] and the faint-pass
 * numbers): they were set by looking at synthetic photographs (ink from 0.25 to 0.65 darkness) and are not measured on real pens,
 * pencils or printers, so a screen that shows one says so. Light pencil is not silently lost: see [readFaint].
 *
 * A cell is **empty** when its cleaned ink is a few specks (under [MIN_INK_PIXELS]); **uncertain** when the ink touches the edge of
 * the drawing area (cut off, or running in from a neighbour) or is faint; otherwise **read**. Which glyph a cell holds is not
 * judged: it is the template's.
 */
internal object CellReader {
    /**
     * How wide, in millimetres, the band round each printed guide is inside which a thin mark is taken for the guide's leftover: the
     * guide's own width, a printer's wider toner and a few tenths of a millimetre out of register (our heuristic). A pen line a
     * millimetre wide or more cannot lie wholly inside it.
     */
    const val GHOST_BAND_MM: Double = 1.8

    /** The lowest darkness taken as ink. */
    const val GUIDE_CEILING: Double = 0.33

    /** Darkness below this is paper: guide ghosts and texture are lifted to white. */
    const val PAPER_FLOOR: Double = 0.28

    /** Fewer ink pixels than this is not a drawing. */
    const val MIN_INK_PIXELS: Int = 40

    /** Ink whose strongest darkness is under this is faint. */
    const val FAINT_DARKNESS: Double = 0.5

    /** The lowest darkness taken as ink when a cell is read again for faint pencil (our heuristic). */
    const val FAINT_LEVEL: Double = 0.12

    /** Fewer ink pixels than this, at the faint level, is residue of a printed guide or the paper and not a drawing (our heuristic). */
    const val FAINT_MIN_INK_PIXELS: Int = 80

    /** Strokes narrower than this many pixels (0.4 mm at the default 10 pixels a millimetre) are taken for a printed guide's leftovers (our heuristic). */
    const val FAINT_MIN_STROKE_PX: Double = 4.0

    /** The darkness a faint drawing's strongest pixel is stretched to, so the trace, which wants to see ink, can. */
    const val FAINT_STRETCH_TO: Double = 0.8

    private class Read(
        val cell: CapturedCell,
        val signature: DoubleArray?,
        val reason: UncertainReason?,
    )

    fun readAll(
        photo: GrayscaleRaster,
        toPhoto: Homography,
        layout: SheetLayout,
        spec: PrintSheetSpec,
        pxPerMm: Double,
        blurSigmaMm: Double,
    ): List<CapturedCell> {
        val warp = SheetWarp(photo, toPhoto)
        // First pass: every cell cut out flat, with the model of the printed guides over it. The guides' strength is pooled over the
        // whole sheet and divided out, so an ink edge that crosses a guide is not pushed a fraction of a pixel by it.
        val areas = spec.glyphs.indices.map { layout.inkArea(it) }
        val flat = areas.map { warp.warp(it, pxPerMm) }
        val coverages =
            spec.glyphs.indices.map {
                PrintedGuides.coverage(layout, layout.cellBoxes[it], areas[it], flat[it].width, flat[it].height, pxPerMm, blurSigmaMm)
            }
        val strength = PrintedGuides.estimateStrength(flat, coverages)
        val reads =
            spec.glyphs.indices.map {
                val band =
                    PrintedGuides.band(
                        layout,
                        layout.cellBoxes[it],
                        areas[it],
                        flat[it].width,
                        flat[it].height,
                        pxPerMm,
                        GHOST_BAND_MM,
                    )
                val corrected = PrintedGuides.correct(flat[it], coverages[it], strength)
                readOne(PrintedGuides.withoutGhosts(corrected, band), layout, spec, it, pxPerMm, blurSigmaMm)
            }
        // Second pass: the relabel list of every uncertain cell is ranked against the drawings of the others.
        return reads.mapIndexed { index, read ->
            val reason = read.reason
            if (reason == null) {
                read.cell
            } else {
                val (candidates, basis) = candidatesFor(index, reads, spec)
                CapturedCell(
                    read.cell.index,
                    read.cell.glyphName,
                    CellStatus.Uncertain(reason, candidates, basis),
                    read.cell.raster,
                    read.cell.frame,
                    read.cell.inkBounds,
                    read.cell.strokeWidthUnits,
                    read.cell.inkFraction,
                    read.cell.edgeSoftnessUnits,
                )
            }
        }
    }

    private fun readOne(
        gray: GrayscaleRaster,
        layout: SheetLayout,
        spec: PrintSheetSpec,
        index: Int,
        pxPerMm: Double,
        blurSigmaMm: Double,
    ): Read {
        val glyph = spec.glyphs[index]
        val box = layout.cellBoxes[index]
        val area = layout.inkArea(index)
        val clean = cleanCell(gray, CleanParameters(minimumLevel = GUIDE_CEILING))
        val inkPixels = clean.mask.ink.count { it }
        val empty = CapturedCell(index, glyph.glyphName, CellStatus.Empty, null, null, null, null, 0.0)
        val frame = layout.frame
        if (!clean.inkFound ||
            inkPixels < MIN_INK_PIXELS
        ) {
            return readFaint(gray, empty, glyph.glyphName, index, area, box, frame, pxPerMm, blurSigmaMm)
        }

        val cellFrame = cellFrameOf(frame, area, box, pxPerMm)
        val raster = inkOnWhite(clean)
        val bounds = inkBounds(clean, cellFrame)
        val touches = touchesEdge(clean)
        val strongest =
            clean.darkness.indices
                .filter { clean.mask.ink[it] }
                .maxOfOrNull { clean.darkness[it] } ?: 0.0
        val reason =
            when {
                touches -> UncertainReason.TOUCHES_EDGE
                strongest < FAINT_DARKNESS -> UncertainReason.FAINT
                else -> null
            }
        val cell =
            CapturedCell(
                index,
                glyph.glyphName,
                CellStatus.Read,
                raster,
                cellFrame,
                bounds,
                clean.strokeWidthPx * cellFrame.unitsPerPx,
                inkPixels.toDouble() / clean.mask.ink.size,
                blurSigmaMm * frame.unitsPerMm,
            )
        return Read(cell, signature(clean), reason)
    }

    private fun cellFrameOf(
        frame: CellFrame,
        area: MmRect,
        box: MmRect,
        pxPerMm: Double,
    ): RasterFrame =
        RasterFrame(
            pxPerUnit = pxPerMm * frame.mmPerUnit,
            leftUnits = frame.leftUnits + (area.left - box.left) / frame.mmPerUnit,
            topUnits = frame.topUnits - (area.top - box.top) / frame.mmPerUnit,
        )

    /**
     * A cell with nothing at the pen level may still hold a light pencil drawing, and silently calling that cell empty would lose
     * the person's work without a word. So the cell is read again at [FAINT_LEVEL], and if what turns up is a drawing and not the
     * residue of a printed guide (enough ink, and strokes at least [FAINT_MIN_STROKE_PX] wide, which a guide is not) it is returned as
     * a faint cell for the person to confirm, its darkness stretched so the trace can see it. Otherwise it is empty.
     */
    private fun readFaint(
        gray: GrayscaleRaster,
        empty: CapturedCell,
        glyphName: String,
        index: Int,
        area: MmRect,
        box: MmRect,
        frame: CellFrame,
        pxPerMm: Double,
        blurSigmaMm: Double,
    ): Read {
        val faint = cleanCell(gray, CleanParameters(minimumLevel = FAINT_LEVEL, minimumInk = FAINT_LEVEL))
        val inkPixels = faint.mask.ink.count { it }
        if (!faint.inkFound || inkPixels < FAINT_MIN_INK_PIXELS || faint.strokeWidthPx < FAINT_MIN_STROKE_PX) return Read(empty, null, null)
        val strongest =
            faint.darkness.indices
                .filter { faint.mask.ink[it] }
                .maxOf { faint.darkness[it] }
        val stretch = FAINT_STRETCH_TO / strongest
        val cellFrame = cellFrameOf(frame, area, box, pxPerMm)
        val raster =
            GrayscaleRaster(
                faint.width,
                faint.height,
                IntArray(faint.darkness.size) {
                    val d = faint.darkness[it]
                    if (d < FAINT_LEVEL) 255 else (255.0 * (1.0 - (d * stretch).coerceAtMost(1.0))).roundToInt().coerceIn(0, 255)
                },
            )
        val cell =
            CapturedCell(
                index,
                glyphName,
                CellStatus.Read,
                raster,
                cellFrame,
                inkBounds(faint, cellFrame),
                faint.strokeWidthPx * cellFrame.unitsPerPx,
                inkPixels.toDouble() / faint.mask.ink.size,
                blurSigmaMm * frame.unitsPerMm,
            )
        return Read(cell, signature(faint), UncertainReason.FAINT)
    }

    /** The cell as ink on white: darkness at or above [PAPER_FLOOR] kept, everything fainter paper. */
    private fun inkOnWhite(clean: CleanedCell): GrayscaleRaster =
        GrayscaleRaster(
            clean.width,
            clean.height,
            IntArray(clean.darkness.size) {
                val d = clean.darkness[it]
                if (d < PAPER_FLOOR) 255 else (255.0 * (1.0 - d)).roundToInt().coerceIn(0, 255)
            },
        )

    private fun inkBounds(
        clean: CleanedCell,
        frame: RasterFrame,
    ): OutlineBounds? {
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = -1
        var maxY = -1
        for (y in 0 until clean.height) {
            for (x in 0 until clean.width) {
                if (!clean.mask.ink[y * clean.width + x]) continue
                minX = min(minX, x)
                maxX = max(maxX, x)
                minY = min(minY, y)
                maxY = max(maxY, y)
            }
        }
        if (maxX < 0) return null
        return OutlineBounds(
            xMin = (frame.leftUnits + minX / frame.pxPerUnit).roundToInt(),
            yMin = (frame.topUnits - (maxY + 1) / frame.pxPerUnit).roundToInt(),
            xMax = (frame.leftUnits + (maxX + 1) / frame.pxPerUnit).roundToInt(),
            yMax = (frame.topUnits - minY / frame.pxPerUnit).roundToInt(),
        )
    }

    /** Whether ink reaches within two pixels of the drawing area's edge. */
    private fun touchesEdge(clean: CleanedCell): Boolean {
        val w = clean.width
        val h = clean.height
        for (y in 0 until h) {
            for (x in 0 until w) {
                if ((x < 2 || y < 2 || x >= w - 2 || y >= h - 2) && clean.mask.ink[y * w + x]) return true
            }
        }
        return false
    }

    /** An 8 by 8 grid of ink density over the drawing area: what two drawings are compared by. */
    private fun signature(clean: CleanedCell): DoubleArray {
        val grid = DoubleArray(SIGNATURE * SIGNATURE)
        val counts = IntArray(SIGNATURE * SIGNATURE)
        for (y in 0 until clean.height) {
            for (x in 0 until clean.width) {
                val gx = x * SIGNATURE / clean.width
                val gy = y * SIGNATURE / clean.height
                counts[gy * SIGNATURE + gx]++
                if (clean.mask.ink[y * clean.width + x]) grid[gy * SIGNATURE + gx] += 1.0
            }
        }
        for (i in grid.indices) grid[i] = if (counts[i] > 0) grid[i] / counts[i] else 0.0
        return grid
    }

    private const val SIGNATURE = 8
    private const val RELABEL_COUNT = 5

    /**
     * The names to offer for the uncertain cell [index]: its own template name first, then the template names of the other
     * cells of the sheet whose drawings look most like this one (ink-density grid distance, smallest first), so "l or I?" puts
     * the cell that was drawn the same way next. A sheet with no other drawing to compare with falls back to the template's
     * own order, nearest cells first.
     */
    private fun candidatesFor(
        index: Int,
        reads: List<Read>,
        spec: PrintSheetSpec,
    ): Pair<List<RelabelCandidate>, RankBasis> {
        val own = reads[index].signature
        val expected = RelabelCandidate(spec.glyphs[index].glyphName, 0.0)
        if (own != null) {
            val others =
                reads.indices
                    .filter { it != index && reads[it].signature != null }
                    .map { it to distance(own, reads[it].signature!!) }
                    .sortedBy { it.second }
                    .take(RELABEL_COUNT - 1)
                    .map { RelabelCandidate(spec.glyphs[it.first].glyphName, it.second) }
            if (others.isNotEmpty()) return listOf(expected) + others to RankBasis.SHAPE_SIMILARITY
        }
        val neighbours =
            spec.glyphs.indices
                .filter {
                    it != index
                }.sortedBy { kotlin.math.abs(it - index) }
                .take(RELABEL_COUNT - 1)
                .map { RelabelCandidate(spec.glyphs[it].glyphName, null) }
        return listOf(expected) + neighbours to RankBasis.MANIFEST_ORDER
    }

    private fun distance(
        a: DoubleArray,
        b: DoubleArray,
    ): Double {
        var sum = 0.0
        for (i in a.indices) sum += (a[i] - b[i]) * (a[i] - b[i])
        return sqrt(sum)
    }
}
