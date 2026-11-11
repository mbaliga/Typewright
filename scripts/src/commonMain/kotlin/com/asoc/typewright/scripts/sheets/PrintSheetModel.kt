// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.scripts.sheets

/**
 * The scripts that have printable template sheets, as the Templates screen's toggles list them: Latin, Kana (Hiragana
 * then Katakana on one run of sheets), Devanagari and Naskh. [id] is what the sheet's identity code stores (three bits).
 */
enum class PrintScript(
    val id: Int,
    val label: String,
) {
    LATIN(0, "Latin"),
    KANA(1, "Kana"),
    DEVANAGARI(2, "Devanagari"),
    NASKH(3, "Naskh"),
    ;

    companion object {
        /** The script whose identity-code id is [id], or null for an id no script uses. */
        fun fromId(id: Int): PrintScript? = entries.firstOrNull { it.id == id }
    }
}

/** The paper a sheet is printed on, in millimetres. */
enum class PaperSize(
    val label: String,
    val widthMm: Double,
    val heightMm: Double,
    /** The paper bit of the identity code. */
    val code: Int,
) {
    A4("A4", 210.0, 297.0, 0),
    LETTER("Letter", 215.9, 279.4, 1),
    ;

    companion object {
        fun fromCode(code: Int): PaperSize? = entries.firstOrNull { it.code == code }
    }
}

/** Whether a cell prints its metric guides: [LIGHT] draws them in a pale grey a pen will not be mistaken for, [NONE] leaves the box empty. */
enum class GuideStyle(
    val label: String,
) {
    LIGHT("Guides light"),
    NONE("No guides"),
}

/** One metric guide of a script's templates: the font-unit height [y], the name it is printed with, and whether it is the solid baseline. */
data class PackGuideLine(
    val y: Int,
    val label: String,
    val solid: Boolean,
)

/** The guides a script's templates carry: [lines] across the cell, and the two vertical advance guides at [advanceLeft] and [advanceRight]. */
data class PackGuides(
    val lines: List<PackGuideLine>,
    val advanceLeft: Int,
    val advanceRight: Int,
)

/** One glyph a sheet asks for: its UFO [glyphName], the character it is when it has a single code point, and the pack template it matches. */
data class PrintGlyph(
    val glyphName: String,
    val codePoint: Int?,
    val unicodeName: String?,
    val templateFolder: String,
    val templateFileName: String,
) {
    /** The character, or null for a glyph with no single code point. */
    val character: String? get() = codePoint?.let { Char(it).toString() }
}

/** One printed sheet: which of the script's [count] sheets it is ([index], from 0), its [glyphs] in cell order (row by row), and its range label. */
data class PrintSheetSpec(
    val script: PrintScript,
    val index: Int,
    val count: Int,
    val glyphs: List<PrintGlyph>,
) {
    /** "Sheet 1 of 5". */
    val title: String get() = "${script.label} sheet ${index + 1} of $count"

    /** The first and last glyph, "A–X": characters where the sheet's glyphs have them, names otherwise. */
    val range: String
        get() {
            fun mark(g: PrintGlyph) = g.character ?: g.glyphName
            return "${mark(glyphs.first())}–${mark(glyphs.last())}"
        }
}

/** Every sheet of one script. */
data class PrintSheetSet(
    val script: PrintScript,
    val sheets: List<PrintSheetSpec>,
) {
    val glyphCount: Int get() = sheets.sumOf { it.glyphs.size }
}
