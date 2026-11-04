// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ZipTreeWriterTest {
    private val tree =
        mapOf(
            "OFL.txt" to "licence".encodeToByteArray(),
            "fonts/ttf/F-Regular.ttf" to byteArrayOf(0, 1, 2, -1),
            "sources/F-Regular.ufo/fontinfo.plist" to "info".encodeToByteArray(),
        )

    @Test
    fun handsTheSaverOneZipNamedAsAskedWithEveryFileUnderTheRootFolder() =
        runTest {
            var savedName: String? = null
            var saved: ByteArray? = null
            val writer =
                ZipTreeWriter("f-gf.zip", "f-gf", { name, bytes ->
                    savedName = name
                    saved = bytes
                })
            val report = writer.write(tree)
            assertEquals("f-gf.zip", savedName)
            val read = ProjectZip.read(checkNotNull(saved)) { error("stored entries need no inflater") }
            assertEquals(tree.keys.map { "f-gf/$it" }.sorted(), read.keys.toList())
            for ((path, bytes) in tree) assertContentEquals(bytes, read.getValue("f-gf/$path"), path)
            assertEquals(TreeWriteReport(files = 3, replaced = 0, bytes = saved!!.size.toLong()), report)
            assertEquals("f-gf.zip", writer.description)
        }

    @Test
    fun theSameTreeGivesTheSameBytes() =
        runTest {
            val first = mutableListOf<ByteArray>()
            val writer = ZipTreeWriter("f.zip", "f", { _, bytes -> first += bytes })
            writer.write(tree)
            writer.write(tree.entries.reversed().associate { it.key to it.value })
            assertContentEquals(first[0], first[1])
        }

    @Test
    fun theZipHoldsNoProjectFolderTheTreeDidNotName() =
        runTest {
            var saved = ByteArray(0)
            ZipTreeWriter("f.zip", "f", { _, bytes -> saved = bytes }).write(tree)
            val names = ProjectZip.read(saved) { error("stored") }.keys
            assertTrue(names.none { "comparisons" in it || "lessons" in it || "scrapbook" in it }, names.toString())
        }

    @Test
    fun aRefusedPathMakesNoZip() =
        runTest {
            var calls = 0
            val writer = ZipTreeWriter("f.zip", "f", { _, _ -> calls++ })
            assertFailsWith<TreeWriteException> { writer.write(mapOf("../x" to byteArrayOf(1))) }
            assertEquals(0, calls)
        }

    @Test
    fun aSaverThatFailsIsReportedInWords() =
        runTest {
            val writer = ZipTreeWriter("f.zip", "f", { _, _ -> throw IllegalStateException("the browser said no") })
            val error = assertFailsWith<TreeWriteException> { writer.write(tree) }
            assertTrue("f.zip" in error.message.orEmpty() && "the browser said no" in error.message.orEmpty(), error.message)
        }
}
