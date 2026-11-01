// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.capture

import com.asoc.typewright.project.ProjectZip
import com.asoc.typewright.scripts.sheets.GuideStyle
import com.asoc.typewright.scripts.sheets.PaperSize
import com.asoc.typewright.scripts.sheets.PrintScript
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TemplateExportTest {
    @Test
    fun theSvgPackIsAValidZipOfEveryTemplate() =
        runBlocking<Unit> {
            val zip = TemplatePack.packZip(PrintScript.LATIN)
            assertEquals('P'.code.toByte(), zip[0])
            assertEquals('K'.code.toByte(), zip[1])
            val read = ProjectZip.read(zip) { it }
            assertEquals(121, read.size, "120 templates and the how-to")
            assertTrue(read.keys.all { it == "HOW_TO_USE.md" || it.endsWith(".svg") })
            // Every file in the pack is one a dropped drawing can be matched to.
            for (name in read.keys.filter { it.endsWith(".svg") }) assertTrue(TemplatePack.glyphForFileName(name) != null, name)
        }

    @Test
    fun thePdfCarriesOnePagePerSheetOnTheChosenPaper() {
        val a4 = TemplatePack.pdf(PrintScript.LATIN, PaperSize.A4, GuideStyle.LIGHT).decodeToString()
        assertTrue(a4.startsWith("%PDF-"))
        assertEquals(5, Regex("/Type /Page\\b").findAll(a4).count(), "five Latin sheets, five pages")
        val letter = TemplatePack.pdf(PrintScript.LATIN, PaperSize.LETTER, GuideStyle.NONE).decodeToString()
        val box = Regex("/MediaBox \\[0 0 ([0-9.]+) ([0-9.]+)\\]")
        val letterBox =
            box
                .find(letter)!!
                .groupValues
                .drop(1)
                .map { it.toDouble() }
        val a4Box =
            box
                .find(a4)!!
                .groupValues
                .drop(1)
                .map { it.toDouble() }
        assertEquals(612.0, letterBox[0], 1.0, "Letter is 8.5 in wide, 612 points")
        assertEquals(792.0, letterBox[1], 1.0, "Letter is 11 in tall, 792 points")
        assertEquals(595.0, a4Box[0], 1.0, "A4 is 210 mm wide, 595 points")
        assertEquals(842.0, a4Box[1], 1.0, "A4 is 297 mm tall, 842 points")
        assertEquals("typewright-latin-sheets-a4.pdf", TemplatePack.pdfName(PrintScript.LATIN, PaperSize.A4))
        assertEquals(5, TemplatePack.sheets(PrintScript.LATIN).size)
        assertEquals(3, TemplatePack.sheets(PrintScript.NASKH).size)
    }
}
