// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ProjectZipTest {
    private val project =
        InMemoryProjectFiles(
            "Hyle Deco",
            mapOf(
                "typewright.json" to "{}\n".encodeToByteArray(),
                "HyleDeco-Regular.ufo/metainfo.plist" to "<plist/>\n".encodeToByteArray(),
                "HyleDeco-Regular.ufo/glyphs/T_.glif" to ("<glyph name=\"T\">\n" + " ".repeat(600) + "</glyph>\n").encodeToByteArray(),
                "locks/regular/T_.glif" to "<glyph name=\"T\"/>\n".encodeToByteArray(),
                "scrapbook/manifest.json" to "{ \"pins\": [] } · ".encodeToByteArray(),
                "build/.gitignore" to "*\n!.gitignore\n".encodeToByteArray(),
            ),
        )

    private fun Map<String, ByteArray>.text(): Map<String, String> = mapValues { it.value.decodeToString() }

    @Test
    fun aStoredZipRoundTripsUnderItsTopFolder() =
        runTest {
            val zip = ProjectZip.write(project, "Hyle Deco", deflate = null)
            val read = ProjectZip.read(zip, inflate = { error("nothing is deflated") })
            assertEquals(project.files.text(), read.text())
            assertEquals(project.listFiles(), read.keys.toList())
        }

    @Test
    fun entriesAreSortedWithDirectoryEntriesForParentsAndTheEmptyFolders() =
        runTest {
            val names = centralDirectory(ProjectZip.write(project, "Hyle Deco", deflate = null)).map { it.name }
            assertEquals(names.sorted(), names)
            assertEquals(
                listOf(
                    "Hyle Deco/",
                    "Hyle Deco/HyleDeco-Regular.ufo/",
                    "Hyle Deco/HyleDeco-Regular.ufo/glyphs/",
                    "Hyle Deco/HyleDeco-Regular.ufo/glyphs/T_.glif",
                    "Hyle Deco/HyleDeco-Regular.ufo/metainfo.plist",
                    "Hyle Deco/build/",
                    "Hyle Deco/build/.gitignore",
                    "Hyle Deco/comparisons/",
                    "Hyle Deco/lessons/",
                    "Hyle Deco/locks/",
                    "Hyle Deco/locks/regular/",
                    "Hyle Deco/locks/regular/T_.glif",
                    "Hyle Deco/scrapbook/",
                    "Hyle Deco/scrapbook/manifest.json",
                    "Hyle Deco/typewright.json",
                ),
                names,
            )
        }

    @Test
    fun theSameProjectGivesTheSameBytesWithFixedTimestamps() =
        runTest {
            val reordered =
                InMemoryProjectFiles(
                    "Hyle Deco",
                    project.files.entries
                        .reversed()
                        .associate { it.key to it.value },
                )
            val first = ProjectZip.write(project, "Hyle Deco", ::toyDeflate)
            assertContentEquals(first, ProjectZip.write(reordered, "Hyle Deco", ::toyDeflate))
            for (entry in centralDirectory(first)) {
                assertEquals(0, entry.dosTime, entry.name)
                assertEquals(0x21, entry.dosDate, "${entry.name}: 1980-01-01")
                assertEquals(0x0800, entry.flags, "${entry.name}: UTF-8 name")
            }
        }

    @Test
    fun deflatesOnlyWhenItHelps() =
        runTest {
            val zip = ProjectZip.write(project, "", ::toyDeflate)
            val methods = centralDirectory(zip).associate { it.name to it.method }
            assertEquals(8, methods["HyleDeco-Regular.ufo/glyphs/T_.glif"], "a long run compresses")
            assertEquals(0, methods["typewright.json"], "a tiny file is stored")
            assertEquals(0, methods["comparisons/"], "directories are stored")
            assertEquals(project.files.text(), ProjectZip.read(zip, ::toyInflate).text())
        }

    @Test
    fun withoutARootEntriesSitAtTheTopAndReadBackUnchanged() =
        runTest {
            val single = InMemoryProjectFiles("p", mapOf("scrapbook/manifest.json" to "1".encodeToByteArray()))
            val zip = ProjectZip.write(single, "", deflate = null, directories = emptyList())
            assertEquals(listOf("scrapbook/", "scrapbook/manifest.json"), centralDirectory(zip).map { it.name })
            assertEquals(mapOf("scrapbook/manifest.json" to "1"), ProjectZip.read(zip, ::toyInflate).text())
            val two = InMemoryProjectFiles("p", mapOf("a/one.txt" to "1".encodeToByteArray(), "b.txt" to "2".encodeToByteArray()))
            assertEquals(mapOf("a/one.txt" to "1", "b.txt" to "2"), ProjectZip.read(ProjectZip.write(two, "", null), ::toyInflate).text())
            assertEquals(project.files.text(), ProjectZip.read(ProjectZip.write(project, "", ::toyDeflate), ::toyInflate).text())
        }

    @Test
    fun aZippedUfoKeepsItsOwnFolder() =
        runTest {
            val ufo =
                InMemoryProjectFiles(
                    "HyleDeco-Regular.ufo",
                    mapOf(
                        "metainfo.plist" to "<plist/>".encodeToByteArray(),
                        "glyphs/contents.plist" to "<plist/>".encodeToByteArray(),
                        "glyphs/A_.glif" to "<glyph/>".encodeToByteArray(),
                    ),
                )
            val zip = ProjectZip.write(ufo, "HyleDeco-Regular.ufo", deflate = null, directories = emptyList())
            assertEquals(
                listOf(
                    "HyleDeco-Regular.ufo/glyphs/A_.glif",
                    "HyleDeco-Regular.ufo/glyphs/contents.plist",
                    "HyleDeco-Regular.ufo/metainfo.plist",
                ),
                ProjectZip.read(zip, ::toyInflate).keys.toList(),
            )
            // Upper-case .UFO too, as some archivers and older tools write it.
            val upper = rawStoredZip(listOf("Font.UFO/metainfo.plist" to "<plist/>"))
            assertEquals(listOf("Font.UFO/metainfo.plist"), ProjectZip.read(upper, ::toyInflate).keys.toList())
        }

    @Test
    fun aSharedTopFolderIsStrippedOnlyWhenItHoldsAProjectOrUfos() =
        runTest {
            val sources =
                rawStoredZip(
                    listOf(
                        "My Font/MyFont-Regular.ufo/metainfo.plist" to "<plist/>",
                        "My Font/MyFont-Bold.ufo/metainfo.plist" to "<plist/>",
                        "My Font/OFL.txt" to "licence",
                    ),
                )
            assertEquals(
                listOf("MyFont-Bold.ufo/metainfo.plist", "MyFont-Regular.ufo/metainfo.plist", "OFL.txt"),
                ProjectZip.read(sources, ::toyInflate).keys.toList(),
            )
            val project = rawStoredZip(listOf("Hyle Deco/typewright.json" to "{}", "Hyle Deco/notes.txt" to "n"))
            assertEquals(listOf("notes.txt", "typewright.json"), ProjectZip.read(project, ::toyInflate).keys.toList())
            val other = rawStoredZip(listOf("photos/a.jpg" to "a", "photos/b.jpg" to "b"))
            assertEquals(listOf("photos/a.jpg", "photos/b.jpg"), ProjectZip.read(other, ::toyInflate).keys.toList())
        }

    @Test
    fun refusesEntriesThatWouldEscapeTheProject() =
        runTest {
            for (evil in listOf(
                "../evil.txt",
                "Hyle Deco/../../evil.txt",
                "/etc/passwd",
                "Hyle Deco\\..\\evil.txt",
                "a/./b",
                "a//b",
                "nul\u0000.txt",
            )) {
                val zip = rawStoredZip(listOf("Hyle Deco/typewright.json" to "{}", evil to "x"))
                val error = assertFailsWith<IllegalArgumentException>(evil) { ProjectZip.read(zip, ::toyInflate) }
                assertTrue(error.message!!.contains("unsafe"), error.message)
            }
        }

    @Test
    fun ignoresMacOsResourceForksAndDirectoryEntries() =
        runTest {
            val zip =
                rawStoredZip(
                    listOf(
                        "Hyle Deco/" to "",
                        "Hyle Deco/typewright.json" to "{}",
                        "__MACOSX/Hyle Deco/._typewright.json" to "junk",
                        "Hyle Deco/scrapbook/" to "",
                    ),
                )
            assertEquals(mapOf("typewright.json" to "{}"), ProjectZip.read(zip, ::toyInflate).text())
        }

    @Test
    fun refusesDamagedArchives() =
        runTest {
            assertFailsWith<IllegalArgumentException> { ProjectZip.read("not a zip at all, just text".encodeToByteArray(), ::toyInflate) }
            assertFailsWith<IllegalArgumentException> { ProjectZip.read(ByteArray(3), ::toyInflate) }
            val zip = rawStoredZip(listOf("a.txt" to "hello"))
            val corrupted = zip.copyOf().also { it[30 + "a.txt".length] = 'j'.code.toByte() }
            assertFailsWith<IllegalArgumentException> { ProjectZip.read(corrupted, ::toyInflate) }
            assertFailsWith<IllegalArgumentException> {
                ProjectZip.read(
                    rawStoredZip(listOf("a.txt" to "1", "a.txt" to "2")),
                    ::toyInflate,
                )
            }
            assertFailsWith<IllegalArgumentException> { ProjectZip.read(zip.copyOf(zip.size - 5), ::toyInflate) }
        }

    @Test
    fun refusesAnInvalidRootOrPath() =
        runTest {
            assertFailsWith<IllegalArgumentException> { ProjectZip.write(project, "../up", null) }
            assertFailsWith<IllegalArgumentException> { ProjectZip.write(project, "p", null, directories = listOf("a/../b")) }
        }

    // ---- Helpers ----------------------------------------------------------------------------

    private class CentralEntry(
        val name: String,
        val flags: Int,
        val method: Int,
        val dosTime: Int,
        val dosDate: Int,
    )

    private fun u16(
        bytes: ByteArray,
        at: Int,
    ): Int = (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8)

    private fun u32(
        bytes: ByteArray,
        at: Int,
    ): Int = u16(bytes, at) or (u16(bytes, at + 2) shl 16)

    private fun centralDirectory(zip: ByteArray): List<CentralEntry> {
        val end = zip.size - 22
        assertEquals(0x06054b50, u32(zip, end))
        var at = u32(zip, end + 16)
        return List(u16(zip, end + 10)) {
            assertEquals(0x02014b50, u32(zip, at))
            val nameLength = u16(zip, at + 28)
            val entry =
                CentralEntry(
                    name = zip.copyOfRange(at + 46, at + 46 + nameLength).decodeToString(),
                    flags = u16(zip, at + 8),
                    method = u16(zip, at + 10),
                    dosTime = u16(zip, at + 12),
                    dosDate = u16(zip, at + 14),
                )
            at += 46 + nameLength + u16(zip, at + 30) + u16(zip, at + 32)
            entry
        }
    }

    // A zip of stored entries with any names, including ones ProjectZip.write would refuse.
    private fun rawStoredZip(entries: List<Pair<String, String>>): ByteArray {
        val out = ArrayList<Byte>()
        val central = ArrayList<Byte>()

        fun MutableList<Byte>.u16(value: Int) {
            add(value.toByte())
            add((value ushr 8).toByte())
        }

        fun MutableList<Byte>.u32(value: Int) {
            u16(value and 0xFFFF)
            u16(value ushr 16)
        }
        for ((name, text) in entries) {
            val nameBytes = name.encodeToByteArray()
            val data = text.encodeToByteArray()
            val crc = Crc32.of(data)
            val offset = out.size
            out.u32(0x04034b50)
            out.u16(20)
            out.u16(0x0800)
            out.u16(0)
            out.u16(0)
            out.u16(0x21)
            out.u32(crc)
            out.u32(data.size)
            out.u32(data.size)
            out.u16(nameBytes.size)
            out.u16(0)
            out.addAll(nameBytes.toList())
            out.addAll(data.toList())
            central.u32(0x02014b50)
            central.u16(20)
            central.u16(20)
            central.u16(0x0800)
            central.u16(0)
            central.u16(0)
            central.u16(0x21)
            central.u32(crc)
            central.u32(data.size)
            central.u32(data.size)
            central.u16(nameBytes.size)
            repeat(4) { central.u16(0) }
            central.u32(0)
            central.u32(offset)
            central.addAll(nameBytes.toList())
        }
        val centralOffset = out.size
        out.addAll(central)
        out.u32(0x06054b50)
        out.u16(0)
        out.u16(0)
        out.u16(entries.size)
        out.u16(entries.size)
        out.u32(central.size)
        out.u32(centralOffset)
        out.u16(0)
        return out.toByteArray()
    }
}

/** A toy run-length codec standing in for raw deflate in common tests: (count, byte) pairs. */
@Suppress("RedundantSuspendModifier")
suspend fun toyDeflate(data: ByteArray): ByteArray {
    val out = ArrayList<Byte>()
    var i = 0
    while (i < data.size) {
        var run = 1
        while (i + run < data.size && run < 255 && data[i + run] == data[i]) run++
        out += run.toByte()
        out += data[i]
        i += run
    }
    return out.toByteArray()
}

@Suppress("RedundantSuspendModifier")
suspend fun toyInflate(data: ByteArray): ByteArray {
    val out = ArrayList<Byte>()
    for (pair in 0 until data.size / 2) repeat(data[2 * pair].toInt() and 0xFF) { out += data[2 * pair + 1] }
    return out.toByteArray()
}
