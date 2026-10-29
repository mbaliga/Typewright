// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.scripts.sheets

/**
 * Draws printed template sheets. A sheet is four solid corner marks, the identity code ([SheetIdentity]), a header and a
 * footer, and a 4 by 6 grid of boxes, each with the script's metric guides in a pale grey ([GuideStyle.LIGHT]), the
 * glyph's name and code point under it, and nothing else inside. Everything is positioned from [SheetLayout], which the
 * photograph reader uses too.
 *
 * **Colour.** The marks and the code are black. Guides, box borders and the guide names are [GUIDE_GRAY] (darkness about
 * 20%), a level a pencil or pen line is well past, so the reader can tell a guide from a stroke by darkness alone and a
 * guide never becomes ink. Labels outside the boxes are darker; the reader never looks there.
 */
object SheetRenderer {
    /** The grey of everything printed inside a box. */
    const val GUIDE_GRAY: Double = 0.80

    /** The stroke width of the solid (baseline) guide, in millimetres. */
    const val GUIDE_SOLID_MM: Double = 0.3

    /** The stroke width of the dashed guides, in millimetres. */
    const val GUIDE_DASHED_MM: Double = 0.2

    /** The length of a dash, and of the gap after it, in the dashed guides, in millimetres. */
    const val GUIDE_DASH_MM: Double = 1.2

    private const val LABEL_GRAY = 0.25
    private const val NOTE_GRAY = 0.45

    /** One sheet. */
    fun render(
        spec: PrintSheetSpec,
        layout: SheetLayout,
        guides: GuideStyle,
    ): SheetPage {
        require(spec.script == layout.script) { "a ${spec.script.label} sheet cannot use the ${layout.script.label} layout" }
        require(
            spec.glyphs.size <= layout.cellBoxes.size,
        ) { "a sheet holds ${layout.cellBoxes.size} cells, this one has ${spec.glyphs.size}" }
        val ops = mutableListOf<DrawOp>()
        val half = SheetLayout.MARK_SIDE_MM / 2.0
        for (centre in layout.markCentres) {
            ops += DrawOp.Rect(centre.x - half, centre.y - half, SheetLayout.MARK_SIDE_MM, SheetLayout.MARK_SIDE_MM, fillGray = 0.0)
        }
        val identity = SheetIdentity(spec.script, spec.index, spec.count, layout.paper)
        for ((bit, centre) in identity.encode().zip(layout.codeCentres)) {
            if (!bit) continue
            val side = SheetLayout.CODE_MODULE_MM
            ops += DrawOp.Rect(centre.x - side / 2.0, centre.y - side / 2.0, side, side, fillGray = 0.0)
        }
        ops +=
            DrawOp.Text(
                SheetLayout.GRID_SIDE_MARGIN_MM,
                18.5,
                pdfSafe("${spec.title} - ${printableRange(spec)}"),
                SheetFont.HELVETICA_BOLD,
                9.0,
                LABEL_GRAY,
            )
        ops +=
            DrawOp.Text(
                layout.pageWidthMm - SheetLayout.GRID_SIDE_MARGIN_MM,
                18.5,
                "${layout.paper.label}   ${guides.label.lowercase()}",
                SheetFont.COURIER,
                6.5,
                NOTE_GRAY,
                TextAlign.END,
            )
        for ((i, glyph) in spec.glyphs.withIndex()) cell(ops, layout, i, glyph, guides)
        val footerY = layout.pageHeightMm - 4.5
        ops +=
            DrawOp.Text(
                layout.pageWidthMm / 2.0,
                footerY - 3.2,
                "Draw each glyph in its box in dark pen or pencil, at the size you want it. Keep all four black squares in view.",
                SheetFont.HELVETICA,
                6.5,
                NOTE_GRAY,
                TextAlign.MIDDLE,
            )
        ops +=
            DrawOp.Text(
                layout.pageWidthMm / 2.0,
                footerY,
                "Typewright template - CC0: the template is free to use, and what you draw on it is yours.",
                SheetFont.HELVETICA,
                6.5,
                NOTE_GRAY,
                TextAlign.MIDDLE,
            )
        return SheetPage(layout.pageWidthMm, layout.pageHeightMm, spec.title, ops)
    }

    /** Every sheet of [set] on [paper], in order. */
    fun renderAll(
        set: PrintSheetSet,
        paper: PaperSize,
        guides: GuideStyle,
    ): List<SheetPage> {
        val layout = SheetLayout.of(paper, set.script)
        return set.sheets.map { render(it, layout, guides) }
    }

    /** The sheet's first and last glyph, as characters the base-14 fonts can show and as glyph names where they cannot. */
    private fun printableRange(spec: PrintSheetSpec): String {
        fun mark(g: PrintGlyph): String = g.codePoint?.takeIf { winAnsiByte(it) != null }?.let { Char(it).toString() } ?: g.glyphName
        return "${mark(spec.glyphs.first())}\u2013${mark(spec.glyphs.last())}"
    }

    private fun cell(
        ops: MutableList<DrawOp>,
        layout: SheetLayout,
        index: Int,
        glyph: PrintGlyph,
        guides: GuideStyle,
    ) {
        val box = layout.cellBoxes[index]
        ops += DrawOp.Rect(box.left, box.top, box.width, box.height, fillGray = null, strokeGray = GUIDE_GRAY, strokeMm = 0.25)
        if (guides == GuideStyle.LIGHT) {
            val frame = layout.frame
            for (line in layout.guides.lines) {
                val y = box.top + frame.boxMm(0.0, line.y.toDouble()).y
                ops +=
                    DrawOp.Line(
                        box.left,
                        y,
                        box.right,
                        y,
                        GUIDE_GRAY,
                        if (line.solid) GUIDE_SOLID_MM else GUIDE_DASHED_MM,
                        if (line.solid) null else GUIDE_DASH_MM,
                    )
                ops += DrawOp.Text(box.left + 0.8, y - 0.6, pdfSafe(line.label), SheetFont.HELVETICA, 3.6, GUIDE_GRAY)
            }
            for (x in listOf(layout.guides.advanceLeft, layout.guides.advanceRight)) {
                val px = box.left + frame.boxMm(x.toDouble(), 0.0).x
                ops += DrawOp.Line(px, box.top, px, box.bottom, GUIDE_GRAY, GUIDE_DASHED_MM, GUIDE_DASH_MM)
            }
        }
        val baseline = box.bottom + 3.4
        val character = glyph.codePoint?.let { cp -> if (winAnsiByte(cp) != null) Char(cp).toString() else null }
        if (character != null && character == glyph.glyphName) {
            // "A" is both the character and its name: print it once, large.
            ops += DrawOp.Text(box.left, baseline, character, SheetFont.HELVETICA_BOLD, 9.0, LABEL_GRAY)
        } else {
            var nameX = box.left
            if (character != null) {
                ops += DrawOp.Text(box.left, baseline, character, SheetFont.HELVETICA_BOLD, 9.0, LABEL_GRAY)
                nameX += 5.5
            }
            ops += DrawOp.Text(nameX, baseline, pdfSafe(glyph.glyphName), SheetFont.COURIER, 6.5, LABEL_GRAY)
        }
        glyph.codePoint?.let { cp ->
            ops +=
                DrawOp.Text(
                    box.right,
                    baseline,
                    "U+" + cp.toString(16).uppercase().padStart(4, '0'),
                    SheetFont.COURIER,
                    5.5,
                    NOTE_GRAY,
                    TextAlign.END,
                )
        }
    }
}
