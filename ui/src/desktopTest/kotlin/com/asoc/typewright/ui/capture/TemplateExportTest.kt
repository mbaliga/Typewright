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
        assertTrue(letter.contains("612") || letter.contains("2.8"), "Letter is a different page size from A4")
        assertTrue(a4 != letter)
        assertEquals("typewright-latin-sheets-a4.pdf", TemplatePack.pdfName(PrintScript.LATIN, PaperSize.A4))
        assertEquals(5, TemplatePack.sheets(PrintScript.LATIN).size)
        assertEquals(3, TemplatePack.sheets(PrintScript.NASKH).size)
    }
}
