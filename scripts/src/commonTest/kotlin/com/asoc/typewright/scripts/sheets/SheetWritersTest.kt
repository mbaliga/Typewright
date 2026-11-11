// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.scripts.sheets

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The PDF and SVG writers checked structurally and without a PDF library: the PDF's header, cross-reference table (every
 * offset must land on its own `N 0 obj`), trailer, page count, page sizes and stream lengths are all read back from the
 * bytes; the SVG's elements are counted against the drawing operations that made them. `SheetPdfPythonIntegrationTest`
 * (the `pythonIntegrationTest` task) additionally opens the same file with pypdf and pdfinfo.
 */
class SheetWritersTest {
    private val latinPages = SheetRenderer.renderAll(PrintSheets.forScript(PrintScript.LATIN), PaperSize.A4, GuideStyle.LIGHT)

    private fun text(bytes: ByteArray): String = bytes.decodeToString()

    /** The header, end marker, cross-reference table (every offset lands on its own `N 0 obj`) and trailer of [pdf], read back from its text. */
    private fun assertCrossReference(pdf: String) {
        val startxref = Regex("startxref\\n(\\d+)\\n%%EOF").find(pdf)!!.groupValues[1].toInt()
        assertTrue(pdf.startsWith("xref\n", startxref), "startxref points at the table")
        val header = Regex("xref\\n0 (\\d+)\\n").find(pdf, startxref)!!
        val size = header.groupValues[1].toInt()
        val entries = pdf.substring(header.range.last + 1).lines().take(size)
        assertEquals("0000000000 65535 f ", entries[0])
        for (n in 1 until size) {
            val line = entries[n]
            assertEquals(19, line.length, "entry $n is 20 bytes with its newline")
            assertTrue(line.endsWith(" 00000 n "))
            val offset = line.substring(0, 10).toInt()
            assertTrue(pdf.startsWith("$n 0 obj\n", offset), "object $n is where the table says: offset $offset")
        }
        val trailer = pdf.substring(pdf.indexOf("trailer"))
        assertTrue("/Size $size" in trailer && "/Root 1 0 R" in trailer)
    }

    @Test
    fun everyScriptOnEveryPaperMakesAWellFormedPdfOfThePagesItHas() {
        // Runs in the default jvmTest with no Python and no PDF tool on the machine, so a broken file cannot pass for want of a reader.
        for (script in PrintScript.entries) {
            for (paper in PaperSize.entries) {
                val pages = SheetRenderer.renderAll(PrintSheets.forScript(script), paper, GuideStyle.LIGHT)
                val pdf = text(PdfWriter.write(pages))
                assertCrossReference(pdf)
                assertTrue(pages.isNotEmpty())
                assertTrue("/Count ${pages.size}" in pdf, "${script.label} ${paper.label}: page count")
                assertEquals(pages.size, Regex("/Type /Page /Parent").findAll(pdf).count(), "${script.label} ${paper.label}: page objects")
            }
        }
    }

    @Test
    fun theFiveLatinSheetsMakeAFivePageA4PdfWithAValidCrossReference() {
        val pdf = text(PdfWriter.write(latinPages))
        assertTrue(pdf.startsWith("%PDF-1.4\n"))
        assertTrue(pdf.endsWith("%%EOF\n"))

        assertCrossReference(pdf)
        assertTrue("/Count 5" in pdf)
        assertEquals(5, Regex("/Type /Page /Parent").findAll(pdf).count())
        assertEquals(5, Regex("/MediaBox \\[0 0 595.276 841.89\\]").findAll(pdf).count(), "A4 is 595.276 x 841.89 points")
        assertEquals(3, Regex("/Type /Font /Subtype /Type1").findAll(pdf).count(), "three base-14 fonts, none embedded")
        assertTrue("/FontFile" !in pdf)
    }

    @Test
    fun everyContentStreamsDeclaredLengthIsItsRealLength() {
        val pdf = text(PdfWriter.write(latinPages.take(2)))
        val streams = Regex("<< /Length (\\d+) >>\\nstream\\n").findAll(pdf).toList()
        assertEquals(2, streams.size)
        for (m in streams) {
            val declared = m.groupValues[1].toInt()
            val start = m.range.last + 1
            assertEquals("\nendstream", pdf.substring(start + declared, start + declared + "\nendstream".length))
        }
    }

    @Test
    fun letterPaperIsSixHundredTwelveBySevenHundredNinetyTwoPoints() {
        val pages = SheetRenderer.renderAll(PrintSheets.forScript(PrintScript.NASKH), PaperSize.LETTER, GuideStyle.NONE)
        val pdf = text(PdfWriter.write(pages))
        assertEquals(3, Regex("/MediaBox \\[0 0 612 792\\]").findAll(pdf).count(), pdf.substring(0, 600))
        assertTrue("/Count 3" in pdf)
    }

    @Test
    fun textIsEscapedAndNonAsciiBytesAreOctal() {
        assertEquals("a\\(b\\)c\\\\d", PdfWriter.escape("a(b)c\\d"))
        assertEquals("\\226", PdfWriter.escape("–"), "en dash is WinAnsi 0x96 = octal 226")
        assertEquals("\\200", PdfWriter.escape("€"))
        assertEquals("?", PdfWriter.escape("あ"))
        val page = SheetPage(100.0, 100.0, "t", listOf(DrawOp.Text(10.0, 10.0, "(x)", SheetFont.COURIER, 8.0, 0.0)))
        assertTrue("(\\(x\\)) Tj" in text(PdfWriter.write(listOf(page))))
    }

    @Test
    fun numbersAreWrittenWithoutExponentsOrTrailingZeros() {
        assertEquals("0", PdfWriter.num(0.0))
        assertEquals("12.5", PdfWriter.num(12.5))
        assertEquals("-0.25", PdfWriter.num(-0.25))
        assertEquals("595.276", PdfWriter.num(210.0 * 72.0 / 25.4))
        assertEquals("0.001", PdfWriter.num(0.0004999 + 0.0005))
    }

    @Test
    fun textWidthIsExactForCourierAndFollowsTheAfmForHelvetica() {
        assertEquals(10 * 0.6 * 10.0 / (72.0 / 25.4), PdfWriter.textWidthMm("0123456789", SheetFont.COURIER, 10.0), 1e-9)
        // "Hello": H 722 + e 556 + l 222 + l 222 + o 556 = 2278 per 1000 em.
        assertEquals(2.278 * 10.0 / (72.0 / 25.4), PdfWriter.textWidthMm("Hello", SheetFont.HELVETICA, 10.0), 1e-9)
        assertTrue(
            PdfWriter.textWidthMm("Hello", SheetFont.HELVETICA_BOLD, 10.0) > PdfWriter.textWidthMm("Hello", SheetFont.HELVETICA, 10.0),
        )
    }

    @Test
    fun helveticaBoldFollowsItsOwnPublishedWidthsNotAScaledCopyOfRegular() {
        val perEm = 72.0 / 25.4
        // Adobe's Helvetica-Bold AFM: T 611, y 556, p 611, e 556, w 778, r 389, i 278, g 611, h 611, t 333 = 5334 per 1000 em.
        assertEquals(5.334 * 10.0 / perEm, PdfWriter.textWidthMm("Typewright", SheetFont.HELVETICA_BOLD, 10.0), 1e-9)
        // Regular: T 611, y 500, p 556, e 556, w 722, r 333, i 222, g 556, h 556, t 278 = 4890: the bold word is 9.1% wider, not 6%.
        assertEquals(4.89 * 10.0 / perEm, PdfWriter.textWidthMm("Typewright", SheetFont.HELVETICA, 10.0), 1e-9)
        // Where bold and regular differ by more or less than a flat factor: I is 278 in both, W 944 against 944, but @ is 975 against 1015
        // and the quote mark 238 against 191.
        assertEquals(0.975 * 10.0 / perEm, PdfWriter.textWidthMm("@", SheetFont.HELVETICA_BOLD, 10.0), 1e-9)
        assertEquals(0.238 * 10.0 / perEm, PdfWriter.textWidthMm("\u0027", SheetFont.HELVETICA_BOLD, 10.0), 1e-9)
        assertEquals(0.278 * 10.0 / perEm, PdfWriter.textWidthMm("I", SheetFont.HELVETICA_BOLD, 10.0), 1e-9)
    }

    @Test
    fun aPdfNeedsAtLeastOnePage() {
        assertFailsWith<IllegalArgumentException> { PdfWriter.write(emptyList()) }
    }

    @Test
    fun theSvgDrawsTheSameOperationsAtPaperSize() {
        val page = latinPages.first()
        val svg = SvgWriter.write(page)
        assertTrue(svg.startsWith("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 210 297\" width=\"210mm\" height=\"297mm\">"))
        assertTrue(svg.trimEnd().endsWith("</svg>"))
        assertEquals(
            page.ops.filterIsInstance<DrawOp.Rect>().size + 1,
            Regex("<rect ").findAll(svg).count(),
            "every rect, and the white page",
        )
        assertEquals(page.ops.filterIsInstance<DrawOp.Line>().size, Regex("<line ").findAll(svg).count())
        assertEquals(page.ops.filterIsInstance<DrawOp.Text>().size, Regex("<text ").findAll(svg).count())
        assertEquals(page.ops.filterIsInstance<DrawOp.Text>().size, Regex("</text>").findAll(svg).count())
        assertTrue("stroke-dasharray=\"1.2 1.2\"" in svg)
        assertTrue("fill=\"#000000\"" in svg, "the marks are black")
        assertTrue("stroke=\"#cccccc\"" in svg, "the guides are the light grey")
    }

    @Test
    fun svgTextIsEscaped() {
        val page =
            SheetPage(50.0, 50.0, "a<b", listOf(DrawOp.Text(1.0, 1.0, "x & y < \"z\"", SheetFont.HELVETICA_BOLD, 9.0, 0.5, TextAlign.END)))
        val svg = SvgWriter.write(page)
        assertTrue("<title>a&lt;b</title>" in svg)
        assertTrue(">x &amp; y &lt; &quot;z&quot;</text>" in svg)
        assertTrue("text-anchor=\"end\"" in svg && "font-weight=\"bold\"" in svg)
    }
}
