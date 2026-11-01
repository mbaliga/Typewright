// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.scripts.sheets

import kotlin.math.roundToLong

/**
 * A minimal PDF 1.4 writer for printed sheets, with no dependency and no platform code, so every target can export the
 * sheets: one page per [SheetPage], vector rectangles and lines, and text in the three base-14 fonts (Helvetica,
 * Helvetica-Bold, Courier), which every PDF reader has and so need not be embedded. Streams are written uncompressed,
 * which keeps the writer to plain Kotlin; a 24-cell sheet is a few tens of kilobytes.
 *
 * The file is built in memory as ASCII (a byte is a character), so every object's byte offset is simply the length of the
 * text before it; the cross-reference table is exact. Text is encoded as WinAnsi: a character above 0x7E is written as an
 * octal escape, and anything WinAnsi lacks has already been replaced by `?` ([pdfSafe]).
 */
object PdfWriter {
    private const val POINTS_PER_MM = 72.0 / 25.4

    /** The PDF for [pages], titled [title]. The first page is page 1. */
    fun write(
        pages: List<SheetPage>,
        title: String = "Typewright template sheets",
    ): ByteArray {
        require(pages.isNotEmpty()) { "a PDF needs at least one page" }
        val fonts = SheetFont.entries
        // Object numbers: 1 catalog, 2 pages, 3 info, 4.. fonts, then a (page, content) pair per sheet.
        val firstFont = 4
        val firstPage = firstFont + fonts.size
        val out = StringBuilder()
        val offsets = mutableListOf<Int>()

        fun obj(body: String) {
            offsets += out.length
            out
                .append(offsets.size)
                .append(" 0 obj\n")
                .append(body)
                .append("\nendobj\n")
        }
        out.append("%PDF-1.4\n")
        obj("<< /Type /Catalog /Pages 2 0 R >>")
        obj("<< /Type /Pages /Kids [${pages.indices.joinToString(" ") { "${firstPage + 2 * it} 0 R" }}] /Count ${pages.size} >>")
        obj("<< /Title (${escape(pdfSafe(title))}) /Producer (Typewright) >>")
        for (font in fonts) obj("<< /Type /Font /Subtype /Type1 /BaseFont /${font.pdfName} /Encoding /WinAnsiEncoding >>")
        val fontResources = fonts.indices.joinToString(" ") { "/F${it + 1} ${firstFont + it} 0 R" }
        for ((i, page) in pages.withIndex()) {
            val contentNumber = firstPage + 2 * i + 1
            obj(
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${num(page.widthMm * POINTS_PER_MM)} ${num(page.heightMm * POINTS_PER_MM)}] " +
                    "/Resources << /Font << $fontResources >> >> /Contents $contentNumber 0 R >>",
            )
            val content = content(page)
            obj("<< /Length ${content.length} >>\nstream\n$content\nendstream")
        }
        val xrefOffset = out.length
        out.append("xref\n0 ${offsets.size + 1}\n0000000000 65535 f \n")
        for (offset in offsets) out.append(offset.toString().padStart(10, '0')).append(" 00000 n \n")
        out.append("trailer\n<< /Size ${offsets.size + 1} /Root 1 0 R /Info 3 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")
        return out.toString().encodeToByteArray()
    }

    private fun content(page: SheetPage): String {
        val h = page.heightMm
        val sb = StringBuilder()
        for (op in page.ops) {
            when (op) {
                is DrawOp.Rect -> {
                    val stroke = op.strokeGray
                    val fill = op.fillGray
                    if (fill != null) sb.append(num(fill)).append(" g\n")
                    if (stroke != null) {
                        sb
                            .append(num(stroke))
                            .append(" G\n")
                            .append(num(op.strokeMm * POINTS_PER_MM))
                            .append(" w\n")
                    }
                    sb
                        .append(num(op.x * POINTS_PER_MM))
                        .append(' ')
                        .append(num((h - op.y - op.height) * POINTS_PER_MM))
                        .append(' ')
                        .append(num(op.width * POINTS_PER_MM))
                        .append(' ')
                        .append(num(op.height * POINTS_PER_MM))
                        .append(" re ")
                    sb
                        .append(
                            when {
                                fill != null && stroke != null -> "B"
                                fill != null -> "f"
                                stroke != null -> "S"
                                else -> "n"
                            },
                        ).append('\n')
                }

                is DrawOp.Line -> {
                    sb
                        .append(num(op.gray))
                        .append(" G\n")
                        .append(num(op.widthMm * POINTS_PER_MM))
                        .append(" w\n")
                    val dash = op.dashMm
                    if (dash !=
                        null
                    ) {
                        sb
                            .append('[')
                            .append(num(dash * POINTS_PER_MM))
                            .append(' ')
                            .append(num(dash * POINTS_PER_MM))
                            .append("] 0 d\n")
                    }
                    sb
                        .append(num(op.x1 * POINTS_PER_MM))
                        .append(' ')
                        .append(num((h - op.y1) * POINTS_PER_MM))
                        .append(" m ")
                        .append(num(op.x2 * POINTS_PER_MM))
                        .append(' ')
                        .append(num((h - op.y2) * POINTS_PER_MM))
                        .append(" l S\n")
                    if (dash != null) sb.append("[] 0 d\n")
                }

                is DrawOp.Text -> {
                    val width = textWidthMm(op.text, op.font, op.sizePt)
                    val x =
                        when (op.align) {
                            TextAlign.START -> op.x
                            TextAlign.MIDDLE -> op.x - width / 2.0
                            TextAlign.END -> op.x - width
                        }
                    sb
                        .append("BT /F${SheetFont.entries.indexOf(op.font) + 1} ${num(op.sizePt)} Tf ${num(op.gray)} g ")
                        .append(num(x * POINTS_PER_MM))
                        .append(' ')
                        .append(num((h - op.y) * POINTS_PER_MM))
                        .append(" Td (")
                        .append(escape(op.text))
                        .append(") Tj ET\n")
                }
            }
        }
        return sb.toString()
    }

    /** [text] as the inside of a PDF literal string: `\`, `(` and `)` escaped, bytes above 0x7E as octal. */
    internal fun escape(text: String): String {
        val sb = StringBuilder()
        for (c in text) {
            val byte = winAnsiByte(c.code) ?: '?'.code
            when {
                c == '\\' || c == '(' || c == ')' -> sb.append('\\').append(c)
                byte in 0x20..0x7E -> sb.append(byte.toChar())
                else -> sb.append('\\').append(byte.toString(8).padStart(3, '0'))
            }
        }
        return sb.toString()
    }

    /** A number as PDF text: up to three decimals, no exponent. */
    internal fun num(value: Double): String {
        val scaled = (value * 1000.0).roundToLong()
        val negative = scaled < 0
        val magnitude = if (negative) -scaled else scaled
        val whole = magnitude / 1000
        val fraction = (magnitude % 1000).toString().padStart(3, '0').trimEnd('0')
        return (if (negative) "-" else "") + whole + (if (fraction.isEmpty()) "" else ".$fraction")
    }

    /** How wide [text] prints in [font] at [sizePt], in millimetres. Courier is exactly 0.6 em a character; Helvetica uses its published widths. */
    fun textWidthMm(
        text: String,
        font: SheetFont,
        sizePt: Double,
    ): Double {
        val units =
            when (font) {
                SheetFont.COURIER -> text.length * 600.0
                SheetFont.HELVETICA -> text.sumOf { helveticaWidth(it) }.toDouble()
                SheetFont.HELVETICA_BOLD -> text.sumOf { helveticaWidth(it) }.toDouble() * 1.06
            }
        return units / 1000.0 * sizePt / POINTS_PER_MM / 1.0
    }

    // Helvetica's advance widths for 0x20..0x7E, per 1000 em (Adobe's published AFM).
    private val HELVETICA_ASCII =
        intArrayOf(
            278,
            278,
            355,
            556,
            556,
            889,
            667,
            191,
            333,
            333,
            389,
            584,
            278,
            333,
            278,
            278,
            556,
            556,
            556,
            556,
            556,
            556,
            556,
            556,
            556,
            556,
            278,
            278,
            584,
            584,
            584,
            556,
            1015,
            667,
            667,
            722,
            722,
            667,
            611,
            778,
            722,
            278,
            500,
            667,
            556,
            833,
            722,
            778,
            667,
            778,
            722,
            667,
            611,
            722,
            667,
            944,
            667,
            667,
            611,
            278,
            278,
            278,
            469,
            556,
            333,
            556,
            556,
            500,
            556,
            556,
            278,
            556,
            556,
            222,
            222,
            500,
            222,
            833,
            556,
            556,
            556,
            556,
            333,
            500,
            278,
            556,
            500,
            722,
            500,
            500,
            500,
            334,
            260,
            334,
            584,
        )

    private fun helveticaWidth(c: Char): Int = if (c.code in 0x20..0x7E) HELVETICA_ASCII[c.code - 0x20] else 556
}
