// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.scripts.sheets

import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The exported template PDFs opened by a real PDF library, pypdf, from the Python `TYPEWRIGHT_PYTHON` names (otherwise
 * `python3`): page count and page size must be what the sheets were drawn at, and the text of page one must hold the
 * sheet's header. Runs in the `pythonIntegrationTest` task, not in `jvmTest`. When pypdf is missing the test fails and says
 * how to install it; it never passes without having opened the file.
 */
class SheetPdfPythonIntegrationTest {
    private val python: String = System.getenv("TYPEWRIGHT_PYTHON") ?: "python3"

    private fun run(vararg args: String): String {
        val process = ProcessBuilder(listOf(python) + args).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(60, TimeUnit.SECONDS)) fail("$python timed out")
        if (process.exitValue() != 0) {
            fail("$python ${args.joinToString(" ")} failed:\n$output\nInstall pypdf into that Python: $python -m pip install pypdf")
        }
        return output.trim()
    }

    private fun exportTo(
        paper: PaperSize,
        script: PrintScript,
    ): File {
        val pages = SheetRenderer.renderAll(PrintSheets.forScript(script), paper, GuideStyle.LIGHT)
        val file = File.createTempFile("typewright-${script.label}-${paper.label}-", ".pdf")
        file.deleteOnExit()
        file.writeBytes(PdfWriter.write(pages))
        return file
    }

    @Test
    fun pypdfReadsTheLatinA4SheetsAsFivePagesOfA4() {
        val file = exportTo(PaperSize.A4, PrintScript.LATIN)
        val output =
            run(
                "-c",
                "import pypdf,sys; r=pypdf.PdfReader(sys.argv[1]); " +
                    "print(len(r.pages)); b=r.pages[0].mediabox; print(round(float(b.width),2), round(float(b.height),2)); " +
                    "print(r.pages[2].extract_text())",
                file.absolutePath,
            ).lines()
        assertEquals("5", output[0])
        assertEquals("595.28 841.89", output[1])
        val text = output.drop(2).joinToString("\n")
        assertTrue("Latin sheet 3 of 5" in text, text)
        assertTrue("ampersand" in text && "U+0026" in text, "the glyph labels are in the page text")
    }

    @Test
    fun pypdfReadsTheKanaLetterSheetsAsFivePagesOfLetter() {
        val file = exportTo(PaperSize.LETTER, PrintScript.KANA)
        val output =
            run(
                "-c",
                "import pypdf,sys; r=pypdf.PdfReader(sys.argv[1]); print(len(r.pages)); " +
                    "print({(round(float(p.mediabox.width),1), round(float(p.mediabox.height),1)) for p in r.pages})",
                file.absolutePath,
            ).lines()
        assertEquals("5", output[0])
        assertEquals("{(612.0, 792.0)}", output[1])
    }

    @Test
    fun pdfinfoAgreesWhenItIsInstalled() {
        val file = exportTo(PaperSize.A4, PrintScript.DEVANAGARI)
        val process = runCatching { ProcessBuilder("pdfinfo", file.absolutePath).redirectErrorStream(true).start() }.getOrNull()
        if (process == null) {
            // pdfinfo is poppler's; pypdf above is the check that must run. Say so rather than pass silently.
            println("pdfinfo is not installed here; the pypdf tests above are the check")
            return
        }
        val info = process.inputStream.bufferedReader().readText()
        process.waitFor(30, TimeUnit.SECONDS)
        assertTrue(Regex("Pages:\\s+3").containsMatchIn(info), info)
        assertTrue("A4" in info || "595.276 x 841.89" in info, info)
    }
}
