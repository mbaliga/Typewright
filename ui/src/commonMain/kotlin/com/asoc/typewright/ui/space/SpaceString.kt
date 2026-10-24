// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.space

import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.sidebearings
import com.asoc.typewright.project.KernResolution
import com.asoc.typewright.ui.project.ProjectFont

/** One glyph of a control string as it is set: where its origin falls, and what kerning it took from the glyph before it. */
data class SpaceCell(
    /** Position among the glyphs that were set, from 0; spaces and left-out characters are not counted. */
    val index: Int,
    /** The character in the string this glyph stands for. */
    val char: Char,
    val glyph: Glyph,
    /** The glyph's origin in font units, kerning already applied. */
    val x: Double,
    /** The kerning against the glyph before it, or null for the first glyph and for one that follows a space. */
    val kern: KernResolution?,
) {
    /** The glyph's left sidebearing, or null for a glyph with no ink. */
    val lsb: Int? get() = glyph.sidebearings()?.left

    /** The glyph's right sidebearing, or null for a glyph with no ink. */
    val rsb: Int? get() = glyph.sidebearings()?.right

    /** Where the glyph's advance ends. */
    val right: Double get() = x + glyph.advanceWidth
}

/** Why a character of a control string was left out. */
enum class LeftOut {
    /** The font has no glyph for the character. */
    NO_GLYPH,

    /** The font has a glyph for it, but nothing is drawn in it yet. */
    NOT_DRAWN,
}

/** A character that could not be set, and why. */
data class LeftOutChar(
    val char: Char,
    val why: LeftOut,
)

/** A gap in the string: the project's space, [width] font units wide, starting at [x]. */
data class SpaceGap(
    val x: Double,
    val width: Double,
)

/**
 * A control string set in the project's own outlines: [cells] in order with their advances and
 * kerning, the [gaps] a space makes, the string's [width] in font units, and the characters that were
 * [leftOut] because the font does not have a drawn glyph for them. Nothing is ever substituted for a
 * character that is left out; the screen names them instead.
 */
data class SpaceString(
    val cells: List<SpaceCell>,
    val gaps: List<SpaceGap>,
    val width: Double,
    val leftOut: List<LeftOutChar>,
) {
    /** The index of the first cell that is the glyph called [name], or null. */
    fun indexOf(name: String): Int? = cells.firstOrNull { it.glyph.name == name }?.index

    /**
     * The cell on the left of the junction a kerning selection at [selected] points at: the pair
     * ([selected], [selected] + 1) when those two sit side by side, else ([selected] - 1, [selected]),
     * else null for a string with no two glyphs together.
     */
    fun junctionAt(selected: Int): Int? =
        when {
            hasJunction(selected) -> selected
            hasJunction(selected - 1) -> selected - 1
            else -> cells.indices.firstOrNull { hasJunction(it) }
        }

    /** Whether cell [left] and the one after it are set side by side, with no space between: the pair kerning applies to. */
    fun hasJunction(left: Int): Boolean = left in 0 until cells.lastIndex && cells[left + 1].kern != null

    /** The junction nearest the font-unit position [x] (measured to the middle of the gap between its two letters), or null when the string has none. */
    fun nearestJunction(x: Double): Int? =
        cells.indices
            .filter { hasJunction(it) }
            .minByOrNull { kotlin.math.abs((cells[it].right + cells[it + 1].x) / 2.0 - x) }

    /** The cell whose advance holds [x] (font units), or the nearest one at either end; null for an empty string. */
    fun cellAt(x: Double): Int? {
        if (cells.isEmpty()) return null
        cells.firstOrNull { x >= it.x && x < it.right }?.let { return it.index }
        return if (x < cells.first().x) 0 else cells.lastIndex
    }

    /** The distinct characters that were left out, in order, as a sentence fragment ("a, b"), or null when none were. */
    fun leftOutText(): String? =
        leftOut
            .map { it.char }
            .distinct()
            .takeIf { it.isNotEmpty() }
            ?.joinToString(", ")

    companion object {
        /** The empty string: nothing set, nothing left out. */
        val EMPTY = SpaceString(emptyList(), emptyList(), 0.0, emptyList())

        /**
         * Sets [text] in [font]: each character with a drawn glyph becomes a cell, a space becomes a gap
         * as wide as the project's space, and every other character is [leftOut]. Kerning applies between
         * glyphs set side by side, by the font's own [ProjectFont.kerningTable].
         */
        fun set(
            font: ProjectFont,
            text: String,
        ): SpaceString {
            val cells = ArrayList<SpaceCell>(text.length)
            val gaps = ArrayList<SpaceGap>()
            val out = ArrayList<LeftOutChar>()
            var pen = 0.0
            var previous: SpaceCell? = null
            for (char in text) {
                if (char.isWhitespace()) {
                    val width = font.spaceAdvance()
                    gaps += SpaceGap(pen, width)
                    pen += width
                    previous = null
                    continue
                }
                val glyph = font.glyphFor(char)
                if (glyph == null) {
                    out += LeftOutChar(char, LeftOut.NO_GLYPH)
                    continue
                }
                if (glyph.contours.isEmpty()) {
                    out += LeftOutChar(char, LeftOut.NOT_DRAWN)
                    continue
                }
                val kern = previous?.let { font.kerningTable.resolve(it.glyph.name, glyph.name) }
                if (kern != null) pen += kern.value
                val cell = SpaceCell(cells.size, char, glyph, pen, kern)
                cells += cell
                pen += glyph.advanceWidth
                previous = cell
            }
            return SpaceString(cells, gaps, pen, out)
        }
    }
}
