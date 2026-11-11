// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.scripts.sheets

/** A printed sheet as vector drawing operations, in millimetres from the page's top-left, y down. The PDF and SVG writers both draw from this. */
data class SheetPage(
    val widthMm: Double,
    val heightMm: Double,
    val title: String,
    val ops: List<DrawOp>,
)

/** The three fonts a sheet prints text in: the PDF base-14 Helvetica, Helvetica Bold and Courier, which every PDF reader has. */
enum class SheetFont(
    val pdfName: String,
    val svgFamily: String,
    val bold: Boolean,
) {
    HELVETICA("Helvetica", "Helvetica, Arial, sans-serif", false),
    HELVETICA_BOLD("Helvetica-Bold", "Helvetica, Arial, sans-serif", true),
    COURIER("Courier", "Courier, 'Courier New', monospace", false),
}

/** Where a text's [DrawOp.Text.x] sits along the text. */
enum class TextAlign { START, MIDDLE, END }

/** One drawing operation. Grays run from 0 (black) to 1 (white). */
sealed interface DrawOp {
    /** A rectangle, filled with [fillGray] and/or stroked with [strokeGray] at [strokeMm]. */
    data class Rect(
        val x: Double,
        val y: Double,
        val width: Double,
        val height: Double,
        val fillGray: Double?,
        val strokeGray: Double? = null,
        val strokeMm: Double = 0.0,
    ) : DrawOp

    /** A straight line, optionally dashed (equal [dashMm] on and off). */
    data class Line(
        val x1: Double,
        val y1: Double,
        val x2: Double,
        val y2: Double,
        val gray: Double,
        val widthMm: Double,
        val dashMm: Double? = null,
    ) : DrawOp

    /** One line of [text] with its baseline at [y]. The text is limited to what WinAnsi encodes, which [pdfSafe] enforces. */
    data class Text(
        val x: Double,
        val y: Double,
        val text: String,
        val font: SheetFont,
        val sizePt: Double,
        val gray: Double,
        val align: TextAlign = TextAlign.START,
    ) : DrawOp
}

/** The WinAnsi byte for [codePoint] in the PDF base-14 fonts, or null when those fonts cannot show it. */
fun winAnsiByte(codePoint: Int): Int? =
    when (codePoint) {
        in 0x20..0x7E -> codePoint
        in 0xA0..0xFF -> codePoint
        0x20AC -> 0x80
        0x2026 -> 0x85
        0x2013 -> 0x96
        0x2014 -> 0x97
        0x0152 -> 0x8C
        0x0153 -> 0x9C
        else -> null
    }

/** [text] with every character the base-14 fonts cannot show replaced by `?`, so a sheet never prints a wrong glyph silently as a missing one. */
fun pdfSafe(text: String): String = text.map { c -> if (winAnsiByte(c.code) != null) c else '?' }.joinToString("")
