// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.time.LocalDateTime
import java.util.zip.Deflater
import java.util.zip.Inflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** ProjectZip with java.util.zip's raw deflate injected, checked against java.util.zip's own reader and writer. */
class ProjectZipJvmTest {
    private val temp = Files.createTempDirectory("project-zip")

    @AfterTest
    fun cleanUp() {
        temp.toFile().deleteRecursively()
    }

    private val project =
        InMemoryProjectFiles(
            "Hyle Deco",
            mapOf(
                "typewright.json" to "{\n  \"format\": \"typewright-project\"\n}\n".encodeToByteArray(),
                "HyleDeco-Regular.ufo/glyphs/T_.glif" to "<point x=\"42\" y=\"0\" type=\"line\"/>\n".repeat(200).encodeToByteArray(),
                "HyleDeco-Regular.ufo/glyphs/contents.plist" to "<plist/>\n".encodeToByteArray(),
                "scrapbook/pin-1.png" to ByteArray(300) { (it * 37).toByte() },
                "lessons/workbook-latn.json" to "{ \"text\": \"Déjà · क\" }\n".encodeToByteArray(),
                "empty.txt" to ByteArray(0),
            ),
        )

    @Suppress("RedundantSuspendModifier")
    private suspend fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        try {
            deflater.setInput(data)
            deflater.finish()
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
            return out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    @Suppress("RedundantSuspendModifier")
    private suspend fun inflate(data: ByteArray): ByteArray {
        val inflater = Inflater(true)
        try {
            // Raw inflate through zlib wants one byte of padding after the stream.
            inflater.setInput(data + 0)
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) error("truncated deflate stream")
                out.write(buffer, 0, count)
            }
            return out.toByteArray()
        } finally {
            inflater.end()
        }
    }

    @Test
    fun javaUtilZipReadsWhatProjectZipWrites(): Unit =
        runBlocking {
            val bytes = ProjectZip.write(project, "Hyle Deco", ::deflate)
            val file = temp.resolve("Hyle Deco.zip").also { Files.write(it, bytes) }
            ZipFile(file.toFile()).use { zip ->
                val entries = zip.entries().toList()
                assertEquals(entries.map { it.name }.sorted(), entries.map { it.name })
                val files = entries.filterNot { it.isDirectory }
                assertEquals(project.listFiles().map { "Hyle Deco/$it" }, files.map { it.name })
                for (entry in files) {
                    val expected = project.readBytes(entry.name.removePrefix("Hyle Deco/"))
                    assertContentEquals(expected, zip.getInputStream(entry).readBytes(), entry.name)
                    assertEquals(expected.size.toLong(), entry.size)
                }
                assertEquals(ZipEntry.DEFLATED, zip.getEntry("Hyle Deco/HyleDeco-Regular.ufo/glyphs/T_.glif").method)
                assertEquals(ZipEntry.STORED, zip.getEntry("Hyle Deco/empty.txt").method)
                val directories = entries.filter { it.isDirectory }.map { it.name }
                assertTrue(
                    directories.containsAll(listOf("Hyle Deco/", "Hyle Deco/comparisons/", "Hyle Deco/lessons/", "Hyle Deco/scrapbook/")),
                    "$directories",
                )
                for (entry in entries) assertEquals(LocalDateTime.of(1980, 1, 1, 0, 0), entry.timeLocal, entry.name)
            }
            assertEquals(project.files.mapValues { it.value.toList() }, ProjectZip.read(bytes, ::inflate).mapValues { it.value.toList() })
            assertContentEquals(bytes, ProjectZip.write(project, "Hyle Deco", ::deflate), "the same project zips to the same bytes")
        }

    @Test
    fun projectZipReadsWhatJavaUtilZipWrites(): Unit =
        runBlocking {
            val out = ByteArrayOutputStream()
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("Hyle Deco/"))
                zip.closeEntry()
                for (path in project.listFiles()) {
                    zip.putNextEntry(ZipEntry("Hyle Deco/$path"))
                    zip.write(project.readBytes(path))
                    zip.closeEntry()
                }
                zip.putNextEntry(ZipEntry("__MACOSX/Hyle Deco/._typewright.json"))
                zip.write(byteArrayOf(0, 5, 22, 7))
                zip.closeEntry()
            }
            val read = ProjectZip.read(out.toByteArray(), ::inflate)
            assertEquals(project.files.mapValues { it.value.toList() }, read.mapValues { it.value.toList() })
        }
}
