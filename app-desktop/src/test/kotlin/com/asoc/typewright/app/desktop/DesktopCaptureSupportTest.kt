// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.desktop

import com.asoc.typewright.ui.capture.ExportOutcome
import com.asoc.typewright.ui.capture.FilePickOutcome
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Everything the desktop's picker and exporter do after the dialog has answered, which is what this container can run. */
class DesktopCaptureSupportTest {
    private val dir: File = Files.createTempDirectory("capture-support").toFile()

    private fun file(
        name: String,
        bytes: ByteArray,
    ): File = File(dir, name).apply { writeBytes(bytes) }

    @Test
    fun chosenFilesAreReadWholeAndInTheOrderChosen() {
        val a = file("a.png", byteArrayOf(1, 2, 3))
        val b = file("b.svg", "<svg/>".encodeToByteArray())
        val outcome = assertIs<FilePickOutcome.Picked>(readPictures(listOf(b, a)))
        assertEquals(listOf("b.svg", "a.png"), outcome.files.map { it.name })
        assertContentEquals(byteArrayOf(1, 2, 3), outcome.files[1].bytes)
    }

    @Test
    fun aFileOverTheLimitIsRefusedByNameWithoutBeingRead() {
        val big = file("huge.png", ByteArray(2048))
        val outcome = assertIs<FilePickOutcome.Failed>(readPictures(listOf(big), maxBytes = 1024))
        assertTrue("huge.png" in outcome.message && "too large" in outcome.message, outcome.message)
    }

    @Test
    fun aFileThatVanishedIsReportedNotThrown() {
        val gone = File(dir, "gone.png")
        assertIs<FilePickOutcome.Failed>(readPictures(listOf(gone)))
    }

    @Test
    fun onlyPicturesAndSvgDrawingsAreTakenFromADrop() {
        val png = file("scan.PNG", byteArrayOf(9))
        val doc = file("notes.txt", byteArrayOf(1))
        val folder = File(dir, "folder.png").apply { mkdirs() }
        assertEquals(listOf("scan.PNG"), readDropped(listOf(png, doc, folder))?.map { it.name })
        assertNull(readDropped(listOf(doc)))
    }

    @Test
    fun anExportIsWrittenWhereChosenAndSaysWhere() {
        val target = File(dir, "sheets.pdf")
        val saved = assertIs<ExportOutcome.Saved>(writeExport(target, byteArrayOf(5, 6)))
        assertTrue(saved.where.startsWith("sheets.pdf in "), saved.where)
        assertContentEquals(byteArrayOf(5, 6), target.readBytes())
        val bad = assertIs<ExportOutcome.Failed>(writeExport(File(dir, "no/such/folder/x.pdf"), byteArrayOf(1)))
        assertTrue("x.pdf" in bad.message)
    }
}
