// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.space

import kotlin.math.abs

/** How a control string sits in the width it has: [dpPerUnit] screen dp per font unit, and how many of the string's glyphs fit ([shown]). */
data class SpaceRowFit(
    val dpPerUnit: Double,
    val shown: Int,
    /** The width of the shown glyphs in dp. */
    val widthDp: Double,
)

/** Where the pieces of the row go, in dp: pure arithmetic on a [SpaceString], so a test can read it without a window. */
object SpaceRowLayout {
    /** The biggest the string is set, in dp per em: the board's 72. */
    const val MAX_EM_DP = 72.0

    /** The smallest, below which letters are left off rather than made smaller. */
    const val MIN_EM_DP = 34.0

    /** How wide one character of a number label is, in dp: the 9.5 sp mono the numbers are set in, with its tracking. */
    const val LABEL_CHAR_DP = 6.2

    /** The room a label needs beyond its characters, so two neighbours never touch. */
    const val LABEL_PAD_DP = 4.0

    /** How wide the hit zone is on each side of a line a drag can take hold of. */
    const val HANDLE_ZONE_DP = 22.0

    /**
     * Sets [string] as large as [availableDp] allows between [MIN_EM_DP] and [maxEmDp] per em; at the
     * smallest size, glyphs that would run past the width are not shown (the proof still holds them).
     */
    fun fit(
        string: SpaceString,
        unitsPerEm: Int,
        availableDp: Double,
        maxEmDp: Double = MAX_EM_DP,
    ): SpaceRowFit {
        if (string.cells.isEmpty() || string.width <= 0.0 || availableDp <= 0.0) return SpaceRowFit(maxEmDp / unitsPerEm, 0, 0.0)
        val ems = string.width / unitsPerEm
        val emDp = (availableDp / ems).coerceIn(MIN_EM_DP, maxEmDp)
        return at(string, emDp / unitsPerEm, availableDp)
    }

    /** [string] at the scale [dpPerUnit] already chosen (a drag keeps its scale), showing the glyphs that fit in [availableDp]. */
    fun at(
        string: SpaceString,
        dpPerUnit: Double,
        availableDp: Double,
    ): SpaceRowFit {
        if (string.cells.isEmpty()) return SpaceRowFit(dpPerUnit, 0, 0.0)
        val shown = string.cells.count { it.right * dpPerUnit <= availableDp + EPSILON }.coerceAtLeast(1)
        return SpaceRowFit(dpPerUnit, shown, string.cells[shown - 1].right * dpPerUnit)
    }

    /** The font-unit x for a pointer [xDp] from the row's left edge. */
    fun unitsAt(
        xDp: Double,
        dpPerUnit: Double,
    ): Double = xDp / dpPerUnit

    /** Whether a press at [xDp] is within the grab zone of the line at [lineDp]. */
    fun nearLine(
        xDp: Double,
        lineDp: Double,
    ): Boolean = abs(xDp - lineDp) <= HANDLE_ZONE_DP

    /** The two sidebearings as a cell of [cellDp] wide writes them: "46 · 51", or on two lines when that would run into the next cell's; "—" for a side with no drawing. */
    fun sidebearingLabel(
        lsb: Int?,
        rsb: Int?,
        cellDp: Double,
    ): String {
        val left = lsb?.toString() ?: "—"
        val right = rsb?.toString() ?: "—"
        val oneLine = "$left · $right"
        return if (cellDp < oneLine.length * LABEL_CHAR_DP + LABEL_PAD_DP) "$left\n$right" else oneLine
    }

    private const val EPSILON = 0.5
}
