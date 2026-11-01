// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.capture

import com.asoc.typewright.project.InMemoryProjectFiles
import com.asoc.typewright.project.ProjectZip
import com.asoc.typewright.scripts.sheets.GuideStyle
import com.asoc.typewright.scripts.sheets.PaperSize
import com.asoc.typewright.scripts.sheets.PdfWriter
import com.asoc.typewright.scripts.sheets.PrintGlyph
import com.asoc.typewright.scripts.sheets.PrintScript
import com.asoc.typewright.scripts.sheets.PrintSheetSpec
import com.asoc.typewright.scripts.sheets.PrintSheets
import com.asoc.typewright.scripts.sheets.SheetLayout
import com.asoc.typewright.scripts.sheets.SheetRenderer

/**
 * What Templates exports: the printable sheets as one PDF, and the per-glyph SVG pack for vector tools. Both are made from the
 * same sheet model the camera reads, so a sheet printed from here is one Capture can read, and an SVG drawn on a pack template
 * comes back under the file name that says which glyph it is.
 */
object TemplatePack {
    /** The PDF of every sheet of [script] on [paper], one page each, with [guides] drawn in the boxes or left out. */
    fun pdf(
        script: PrintScript,
        paper: PaperSize,
        guides: GuideStyle,
    ): ByteArray {
        val pages = SheetRenderer.renderAll(PrintSheets.forScript(script), paper, guides)
        return PdfWriter.write(pages, "Typewright ${script.label} template sheets")
    }

    /** The sheets of [script], in print order. */
    fun sheets(script: PrintScript): List<PrintSheetSpec> = PrintSheets.forScript(script).sheets

    /** The file name a PDF export is offered under. */
    fun pdfName(
        script: PrintScript,
        paper: PaperSize,
    ): String = "typewright-${script.label.lowercase()}-sheets-${paper.label.lowercase()}.pdf"

    /** The file name the SVG pack is offered under. */
    fun packName(script: PrintScript): String = "typewright-${script.label.lowercase()}-svg-templates.zip"

    /**
     * The box one glyph template is drawn in, in font units with y down and the baseline on `y = 0`: the advance guides with 160
     * units of room each side, and 120 above the highest guide and below the lowest. For Latin this is the pack's own
     * `-160 -820 970 1150`.
     */
    fun frameOf(script: PrintScript): SvgFrame {
        val guides = SheetLayout.of(PaperSize.A4, script).guides
        val top = guides.lines.maxOf { it.y }
        val bottom = guides.lines.minOf { it.y }
        return SvgFrame(
            minX = (guides.advanceLeft - SIDE_ROOM).toDouble(),
            minY = (-(top + VERTICAL_ROOM)).toDouble(),
            width = (guides.advanceRight - guides.advanceLeft + 2 * SIDE_ROOM).toDouble(),
            height = (top - bottom + 2 * VERTICAL_ROOM).toDouble(),
        )
    }

    /** One glyph's template as an SVG: the metric guides in a `guides` group, an empty `DRAW_HERE` group, and the glyph's name. */
    fun svgFor(
        script: PrintScript,
        glyph: PrintGlyph,
    ): String {
        val frame = frameOf(script)
        val guides = SheetLayout.of(PaperSize.A4, script).guides
        val left = guides.advanceLeft - GUIDE_OVERHANG
        val right = guides.advanceRight + GUIDE_OVERHANG
        val sb = StringBuilder()
        sb.append(
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"${n(frame.minX)} ${n(frame.minY)} ${n(frame.width)} ${n(frame.height)}\" ",
        )
        sb.append("width=\"${n(frame.width)}\" height=\"${n(frame.height)}\">\n")
        sb.append("<g id=\"guides\">")
        for (line in guides.lines) {
            val y = -line.y
            val dash = if (line.solid) " " else " stroke-dasharray=\"8 8\""
            val weight = if (line.solid) 3 else 2
            sb.append(
                "<line x1=\"$left\" y1=\"$y\" x2=\"$right\" y2=\"$y\" stroke=\"$METRIC_GREY\" stroke-width=\"$weight\"$dash opacity=\"0.5\"/>",
            )
            val labelAttrs = "font-family=\"monospace\" font-size=\"30\" fill=\"$METRIC_GREY\" opacity=\"0.7\""
            sb.append("<text x=\"${left + 6}\" y=\"${y - 8}\" $labelAttrs>${xml(line.label)}</text>")
        }
        val vTop = frame.minY.toInt()
        val vBottom = (frame.minY + frame.height).toInt()
        for (x in listOf(guides.advanceLeft, guides.advanceRight)) {
            sb.append(
                "<line x1=\"$x\" y1=\"$vTop\" x2=\"$x\" y2=\"$vBottom\" stroke=\"$ADVANCE_GREY\" stroke-width=\"2\" opacity=\"0.5\"/>",
            )
        }
        sb.append("</g>\n<g id=\"DRAW_HERE\"></g>\n")
        val code = glyph.codePoint?.let { "U+" + it.toString(16).uppercase().padStart(4, '0') + "  " } ?: ""
        val label = "$code${glyph.character?.let { "$it  " } ?: ""}${glyph.unicodeName ?: glyph.glyphName}"
        sb.append("<text x=\"$left\" y=\"${vTop + 40}\" font-family=\"monospace\" font-size=\"42\" fill=\"#111\">${xml(label)}</text>\n")
        sb.append("</svg>\n")
        return sb.toString()
    }

    /** Every file of [script]'s pack, by path (`<folder>/<file>.svg`), with a short how-to. */
    fun packFiles(script: PrintScript): Map<String, ByteArray> {
        val files = LinkedHashMap<String, ByteArray>()
        for (glyph in PrintSheets.glyphs(script)) {
            files["${glyph.templateFolder}/${glyph.templateFileName}"] = svgFor(script, glyph).encodeToByteArray()
        }
        files["HOW_TO_USE.md"] = howTo(script).encodeToByteArray()
        return files
    }

    /** The pack as one zip, files stored. */
    suspend fun packZip(script: PrintScript): ByteArray =
        ProjectZip.write(InMemoryProjectFiles("svg-templates", packFiles(script)), root = "", deflate = null, directories = emptyList())

    /** The glyph a dropped file is for, from its pack file name, across every script; null when the name matches no template. */
    fun glyphForFileName(fileName: String): Pair<PrintScript, PrintGlyph>? {
        val bare = fileName.substringAfterLast('/').substringAfterLast('\\')
        return byFileName[bare.lowercase()]
    }

    /** The template glyph called [name] in [script], or null. */
    fun printGlyph(
        script: PrintScript,
        name: String,
    ): PrintGlyph? = PrintSheets.glyphs(script).firstOrNull { it.glyphName == name }

    private val byFileName: Map<String, Pair<PrintScript, PrintGlyph>> by lazy {
        val out = LinkedHashMap<String, Pair<PrintScript, PrintGlyph>>()
        for (script in PrintScript.entries) {
            for (glyph in PrintSheets.glyphs(script)) out.getOrPut(glyph.templateFileName.lowercase()) { script to glyph }
        }
        out
    }

    private fun howTo(script: PrintScript): String =
        """
        |# Typewright ${script.label} SVG templates
        |
        |One file per glyph, with the metric guides drawn in a `guides` group. Draw your glyph in the empty `DRAW_HERE` group.
        |
        |1. Filled shapes, not strokes. Run Outline Stroke before exporting.
        |2. One closed shape per glyph is best; counters stay as holes inside it.
        |3. As few points as the shape needs: curves for curves.
        |4. Do not scale after drawing. Draw at final size inside the guides.
        |5. Keep the file names. They say which glyph each drawing is.
        |
        |Bring the drawings back through Capture, then plus, then File. Vector drawings start at the Fit stage of Trace.
        |
        |These templates are CC0. What you draw on them is yours.
        |
        """.trimMargin()

    private fun n(x: Double): String = x.toInt().toString()

    private fun xml(text: String): String =
        text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    /** Guide colours in the exported SVGs: neutral greys, because colour carries meaning in the app and a template is not the app. */
    internal const val METRIC_GREY = "#6b6b6b"
    internal const val ADVANCE_GREY = "#9a9a9a"

    private const val SIDE_ROOM = 160
    private const val VERTICAL_ROOM = 120
    private const val GUIDE_OVERHANG = 140
}
