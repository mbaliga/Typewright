// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.scripts.sheets

import kotlin.math.roundToLong

/**
 * One printed sheet as a standalone SVG, 1 user unit to the millimetre, so a vector tool shows it at paper size. It draws
 * the same [DrawOp]s [PdfWriter] does, so the two exports cannot differ. Grays are written as `rgb()` percentages and text
 * as ordinary SVG text in a font family that falls back to the same Helvetica/Courier the PDF names.
 */
object SvgWriter {
    fun write(page: SheetPage): String {
        val sb = StringBuilder()
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ${num(page.widthMm)} ${num(page.heightMm)}\" ")
        sb.append("width=\"${num(page.widthMm)}mm\" height=\"${num(page.heightMm)}mm\">\n")
        sb.append("<title>${xml(page.title)}</title>\n")
        sb.append("<rect width=\"${num(page.widthMm)}\" height=\"${num(page.heightMm)}\" fill=\"#fff\"/>\n")
        for (op in page.ops) {
            when (op) {
                is DrawOp.Rect -> {
                    sb.append("<rect x=\"${num(op.x)}\" y=\"${num(op.y)}\" width=\"${num(op.width)}\" height=\"${num(op.height)}\" ")
                    sb.append("fill=\"${op.fillGray?.let { gray(it) } ?: "none"}\"")
                    op.strokeGray?.let { sb.append(" stroke=\"${gray(it)}\" stroke-width=\"${num(op.strokeMm)}\"") }
                    sb.append("/>\n")
                }

                is DrawOp.Line -> {
                    sb.append("<line x1=\"${num(op.x1)}\" y1=\"${num(op.y1)}\" x2=\"${num(op.x2)}\" y2=\"${num(op.y2)}\" ")
                    sb.append("stroke=\"${gray(op.gray)}\" stroke-width=\"${num(op.widthMm)}\"")
                    op.dashMm?.let { sb.append(" stroke-dasharray=\"${num(it)} ${num(it)}\"") }
                    sb.append("/>\n")
                }

                is DrawOp.Text -> {
                    val anchor =
                        when (op.align) {
                            TextAlign.START -> "start"
                            TextAlign.MIDDLE -> "middle"
                            TextAlign.END -> "end"
                        }
                    // Font sizes are points; the drawing is in millimetres.
                    val size = op.sizePt * 25.4 / 72.0
                    sb.append(
                        "<text x=\"${num(op.x)}\" y=\"${num(op.y)}\" font-family=\"${op.font.svgFamily}\" font-size=\"${num(size)}\" ",
                    )
                    sb.append("fill=\"${gray(op.gray)}\" text-anchor=\"$anchor\"")
                    if (op.font.bold) sb.append(" font-weight=\"bold\"")
                    sb.append(">${xml(op.text)}</text>\n")
                }
            }
        }
        sb.append("</svg>\n")
        return sb.toString()
    }

    private fun gray(g: Double): String {
        val level = (g.coerceIn(0.0, 1.0) * 255.0).roundToLong().toInt()
        val hex = level.toString(16).padStart(2, '0')
        return "#$hex$hex$hex"
    }

    private fun num(value: Double): String {
        val scaled = (value * 1000.0).roundToLong()
        val negative = scaled < 0
        val magnitude = if (negative) -scaled else scaled
        val fraction = (magnitude % 1000).toString().padStart(3, '0').trimEnd('0')
        return (if (negative) "-" else "") + magnitude / 1000 + (if (fraction.isEmpty()) "" else ".$fraction")
    }

    private fun xml(text: String): String =
        text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
