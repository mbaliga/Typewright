// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.capturetestkit

import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.engine.trace.capture.CapturedCell
import com.asoc.typewright.engine.trace.capture.CellStatus
import com.asoc.typewright.engine.trace.coverageOf

/** What [CellJudge] made of one cell: [iou] is the overlap of the ink read with the ink that should be there, null when there was no ink to compare. */
class CellVerdict(
    val index: Int,
    val glyphName: String,
    val iou: Double?,
    /** Why the cell is a miss, or null when it is right. */
    val miss: String?,
) {
    val correct: Boolean get() = miss == null
}

/**
 * Judges whether the right ink landed in a cell. The capture names a cell from the template (that is not in doubt, and is not what is
 * judged); what can go wrong is geometry, so the ink read from the photograph is compared with the ink of the glyph that belongs in the
 * cell, rendered independently from its outlines in the cell's own frame: intersection over union of the two masks, at least [MIN_IOU].
 * A cell where nothing was meant to be drawn must come back empty, and one where something was must not. The expected outlines come from
 * the template's glyph, never from the photograph's own truth, so a photograph of the wrong sheet, or of drawings one cell off, is a miss.
 */
object CellJudge {
    /** The overlap at which two masks are the same drawing (set before any result was looked at; a drawing one cell off scores far below). */
    const val MIN_IOU: Double = 0.75

    fun judge(
        cell: CapturedCell,
        expected: List<Contour>,
    ): CellVerdict {
        val name = cell.glyphName
        if (expected.isEmpty()) {
            return if (cell.status is CellStatus.Empty) {
                CellVerdict(cell.index, name, null, null)
            } else {
                CellVerdict(cell.index, name, null, "ink found where nothing was drawn")
            }
        }
        val raster = cell.raster
        val frame = cell.frame
        if (cell.status is CellStatus.Empty || raster == null || frame == null) {
            return CellVerdict(cell.index, name, 0.0, "empty, but $name was drawn")
        }
        val darkest = raster.pixels.min()
        val threshold = (255 + darkest) / 2
        val wanted = coverageOf(expected, frame, raster.width, raster.height)
        var both = 0
        var either = 0
        for (i in raster.pixels.indices) {
            val read = raster.pixels[i] < threshold
            val should = wanted[i] >= 0.5
            if (read && should) both++
            if (read || should) either++
        }
        val iou = if (either == 0) 0.0 else both.toDouble() / either
        return if (iou >= MIN_IOU) {
            CellVerdict(cell.index, name, iou, null)
        } else {
            CellVerdict(
                cell.index,
                name,
                iou,
                "the ink read overlaps $name's by only ${(iou * 100).toInt()}% (needs ${(MIN_IOU * 100).toInt()}%)",
            )
        }
    }
}
