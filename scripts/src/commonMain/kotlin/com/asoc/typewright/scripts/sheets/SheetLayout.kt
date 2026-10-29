// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.scripts.sheets

/** A point on the page, in millimetres from its top-left corner, y down. */
data class MmPoint(
    val x: Double,
    val y: Double,
)

/** A rectangle on the page, in millimetres. */
data class MmRect(
    val left: Double,
    val top: Double,
    val width: Double,
    val height: Double,
) {
    val right: Double get() = left + width
    val bottom: Double get() = top + height
    val centre: MmPoint get() = MmPoint(left + width / 2.0, top + height / 2.0)

    fun contains(p: MmPoint): Boolean = p.x >= left && p.x <= right && p.y >= top && p.y <= bottom
}

/**
 * How a cell box maps to font units: [mmPerUnit] millimetres of paper per font unit, with the box's left edge at font
 * x = [leftUnits] and its top edge at font y = [topUnits]. A point `(u, v)` millimetres from the box's top-left corner
 * is font `(leftUnits + u / mmPerUnit, topUnits - v / mmPerUnit)`. The frame is the same for every cell of a sheet and
 * is what lets a drawing be read off the paper at the size and place it was drawn: the baseline is at a known height,
 * the left advance guide at a known x.
 */
data class CellFrame(
    val mmPerUnit: Double,
    val leftUnits: Double,
    val topUnits: Double,
) {
    /** Font units per millimetre. */
    val unitsPerMm: Double get() = 1.0 / mmPerUnit

    /** The position in the cell box, in millimetres from its top-left, of the font-unit point ([x], [y]). */
    fun boxMm(
        x: Double,
        y: Double,
    ): MmPoint = MmPoint((x - leftUnits) * mmPerUnit, (topUnits - y) * mmPerUnit)

    /** The font-unit position `(x, y)` of the point [u], [v] millimetres from the box's top-left. */
    fun unitsAt(
        u: Double,
        v: Double,
    ): Pair<Double, Double> = (leftUnits + u / mmPerUnit) to (topUnits - v / mmPerUnit)
}

/**
 * The geometry of a printed sheet for one [paper] and [script], in millimetres: the four corner marks, the identity code,
 * the 4 by 6 grid of cell boxes and the [frame] that maps each box to font units. The renderer draws from it and the
 * photograph reader (`engine-trace`'s capture) locates things from it, so the two cannot disagree about where anything is.
 *
 * - **Corner marks.** Four solid black squares of [MARK_SIDE_MM] with their centres [MARK_INSET_MM] in from the page edges,
 *   listed top-left, top-right, bottom-right, bottom-left. Their centres are the four points a homography is solved from.
 * - **Identity code.** [SheetIdentity.BIT_COUNT] square modules of [CODE_MODULE_MM] in a row centred between the two upper marks.
 * - **Cells.** Box *i* is in column `i % 4`, row `i / 4`; each is a rectangle to draw in, with its label printed under it.
 * - **Frame.** The vertical window is the script's lowest and highest guide plus a margin, so a letter with a descender or
 *   an ascender fits; the horizontal window is centred on the middle of the advance guides.
 */
class SheetLayout private constructor(
    val paper: PaperSize,
    val script: PrintScript,
) {
    val pageWidthMm: Double get() = paper.widthMm
    val pageHeightMm: Double get() = paper.heightMm

    /** Centres of the corner marks: top-left, top-right, bottom-right, bottom-left. */
    val markCentres: List<MmPoint> =
        listOf(
            MmPoint(MARK_INSET_MM, MARK_INSET_MM),
            MmPoint(paper.widthMm - MARK_INSET_MM, MARK_INSET_MM),
            MmPoint(paper.widthMm - MARK_INSET_MM, paper.heightMm - MARK_INSET_MM),
            MmPoint(MARK_INSET_MM, paper.heightMm - MARK_INSET_MM),
        )

    /** Centres of the identity code's modules, left to right. */
    val codeCentres: List<MmPoint> =
        List(SheetIdentity.BIT_COUNT) {
            MmPoint(paper.widthMm / 2.0 - SheetIdentity.BIT_COUNT * CODE_MODULE_MM / 2.0 + (it + 0.5) * CODE_MODULE_MM, MARK_INSET_MM)
        }

    private val pitchX = (paper.widthMm - 2.0 * GRID_SIDE_MARGIN_MM) / COLUMNS
    private val pitchY = (paper.heightMm - GRID_TOP_MM - GRID_BOTTOM_MARGIN_MM) / ROWS

    /** The cell boxes, row by row. */
    val cellBoxes: List<MmRect> =
        List(COLUMNS * ROWS) { i ->
            MmRect(
                left = GRID_SIDE_MARGIN_MM + (i % COLUMNS) * pitchX + COLUMN_GUTTER_MM / 2.0,
                top = GRID_TOP_MM + (i / COLUMNS) * pitchY + ROW_GUTTER_MM / 2.0,
                width = pitchX - COLUMN_GUTTER_MM,
                height = pitchY - LABEL_BAND_MM - ROW_GUTTER_MM,
            )
        }

    /** The guides the script's templates carry, which the cells draw. */
    val guides: PackGuides = PACK_GUIDES.getValue(script)

    /** The map from a cell box to font units. */
    val frame: CellFrame

    init {
        val low = guides.lines.minOf { it.y }.toDouble()
        val high = guides.lines.maxOf { it.y }.toDouble()
        val span = high - low
        val bottom = low - BOTTOM_MARGIN_FRACTION * span
        val top = high + TOP_MARGIN_FRACTION * span
        val box = cellBoxes.first()
        val mmPerUnit = box.height / (top - bottom)
        val centre = (guides.advanceLeft + guides.advanceRight) / 2.0
        frame = CellFrame(mmPerUnit, centre - box.width / mmPerUnit / 2.0, top)
    }

    /** The paper area, in the cell box's own frame, that the reader treats as drawing: the box less [INK_INSET_MM] all round. */
    fun inkArea(cell: Int): MmRect {
        val box = cellBoxes[cell]
        return MmRect(box.left + INK_INSET_MM, box.top + INK_INSET_MM, box.width - 2.0 * INK_INSET_MM, box.height - 2.0 * INK_INSET_MM)
    }

    companion object {
        /** Columns of cells. */
        const val COLUMNS: Int = 4

        /** Rows of cells. */
        const val ROWS: Int = 6

        /** The side of a corner mark. */
        const val MARK_SIDE_MM: Double = 8.0

        /** Distance from the page edge to the centre of a corner mark. */
        const val MARK_INSET_MM: Double = 10.0

        /** The side of one module of the identity code. */
        const val CODE_MODULE_MM: Double = 3.0

        /** Margin left and right of the cell grid. */
        const val GRID_SIDE_MARGIN_MM: Double = 14.0

        /** Where the cell grid starts, from the top of the page. */
        const val GRID_TOP_MM: Double = 24.0

        /** Room kept under the grid for the footer and the lower marks. */
        const val GRID_BOTTOM_MARGIN_MM: Double = 18.0

        /** Gap between neighbouring boxes across a row. */
        const val COLUMN_GUTTER_MM: Double = 2.5

        /** Gap between a label and the box below it. */
        const val ROW_GUTTER_MM: Double = 1.5

        /** Height of the label printed under each box. */
        const val LABEL_BAND_MM: Double = 5.0

        /** The reader ignores this much inside the box edge, where the printed border and any pen starting at the edge sit. */
        const val INK_INSET_MM: Double = 1.0

        /** Room above the highest guide, as a fraction of the guides' span. */
        const val TOP_MARGIN_FRACTION: Double = 0.10

        /** Room below the lowest guide, as a fraction of the guides' span. */
        const val BOTTOM_MARGIN_FRACTION: Double = 0.06

        /** The layout for [script] on [paper]. */
        fun of(
            paper: PaperSize,
            script: PrintScript,
        ): SheetLayout = SheetLayout(paper, script)
    }
}
