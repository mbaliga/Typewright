// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.trace.capture

import com.asoc.typewright.core.geometry.OutlineBounds
import com.asoc.typewright.engine.trace.CleanParameters
import com.asoc.typewright.engine.trace.CleanedCell
import com.asoc.typewright.engine.trace.GrayscaleRaster
import com.asoc.typewright.engine.trace.RasterFrame
import com.asoc.typewright.engine.trace.cleanCell
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
 * A cell is **empty** when its cleaned ink is a few specks (under [MIN_INK_PIXELS]); **uncertain** when the ink touches the edge of
 * the drawing area (cut off, or running in from a neighbour) or is faint; otherwise **read**. Which glyph a cell holds is not
 * judged: it is the template's.
 */
internal object CellReader {
    /** The lowest darkness taken as ink. */
    const val GUIDE_CEILING: Double = 0.33

    /** Darkness below this is paper: guide ghosts and texture are lifted to white. */
    const val PAPER_FLOOR: Double = 0.28

    /** Fewer ink pixels than this is not a drawing. */
    const val MIN_INK_PIXELS: Int = 40

    /** Ink whose strongest darkness is under this is faint. */
    const val FAINT_DARKNESS: Double = 0.5

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
        val reads = spec.glyphs.indices.map { readOne(PrintedGuides.correct(flat[it], coverages[it], strength), layout, spec, it, pxPerMm) }
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
    ): Read {
        val glyph = spec.glyphs[index]
        val box = layout.cellBoxes[index]
        val area = layout.inkArea(index)
        val clean = cleanCell(gray, CleanParameters(minimumLevel = GUIDE_CEILING))
        val inkPixels = clean.mask.ink.count { it }
        val empty = CapturedCell(index, glyph.glyphName, CellStatus.Empty, null, null, null, null, 0.0)
        if (!clean.inkFound || inkPixels < MIN_INK_PIXELS) return Read(empty, null, null)

        val frame = layout.frame
        val cellFrame =
            RasterFrame(
                pxPerUnit = pxPerMm * frame.mmPerUnit,
                leftUnits = frame.leftUnits + (area.left - box.left) / frame.mmPerUnit,
                topUnits = frame.topUnits - (area.top - box.top) / frame.mmPerUnit,
            )
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
            )
        return Read(cell, signature(clean), reason)
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
