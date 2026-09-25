// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.core.font.sfnt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NameTableTest {
    private fun nameTableBytes(records: List<Triple<Int, Int, String>>): ByteArray {
        // (platformId, nameId, value); encodingId/languageId fixed per platform for simplicity.
        val header = TestBytes().u16(0).u16(records.size)
        val storageOffsetPos = header.size
        header.u16(0) // storageOffset placeholder, patched in below

        val recordsBytes = TestBytes()
        val storage = TestBytes()
        for ((platformId, nameId, value) in records) {
            val encodingId = if (platformId == NamePlatform.MACINTOSH) 0 else 1
            val languageId = if (platformId == NamePlatform.MACINTOSH) 0 else 1033
            val encoded = if (platformId == NamePlatform.MACINTOSH) macRomanEncode(value) else utf16BeEncode(value)
            recordsBytes
                .u16(platformId)
                .u16(encodingId)
                .u16(languageId)
                .u16(nameId)
                .u16(encoded.size)
                .u16(storage.size)
            storage.bytes(encoded)
        }
        val fullHeader = header.toByteArray()
        val storageOffset = fullHeader.size + recordsBytes.size
        // patch the storageOffset field (bytes 4-5 of the format-0 header) directly
        val patched = fullHeader.copyOf()
        patched[storageOffsetPos] = ((storageOffset ushr 8) and 0xFF).toByte()
        patched[storageOffsetPos + 1] = (storageOffset and 0xFF).toByte()
        return patched + recordsBytes.toByteArray() + storage.toByteArray()
    }

    private fun utf16BeEncode(s: String): ByteArray {
        val out = TestBytes()
        for (c in s) out.u16(c.code)
        return out.toByteArray()
    }

    private fun macRomanEncode(s: String): ByteArray {
        // test fixtures only use plain ASCII, where Mac Roman and ASCII agree byte-for-byte.
        return s.encodeToByteArray()
    }

    @Test
    fun decodesAWindowsUtf16BeRecord() {
        val bytes = nameTableBytes(listOf(Triple(NamePlatform.WINDOWS, NameId.FAMILY, "Hyle Deco")))
        val table = readNameTable(ByteCursor(bytes))
        assertEquals("Hyle Deco", table.get(NameId.FAMILY))
    }

    @Test
    fun decodesAMacintoshAsciiRecord() {
        val bytes = nameTableBytes(listOf(Triple(NamePlatform.MACINTOSH, NameId.FAMILY, "Hyle Deco")))
        val table = readNameTable(ByteCursor(bytes))
        assertEquals("Hyle Deco", table.get(NameId.FAMILY))
    }

    @Test
    fun prefersANonMacintoshRecordWhenBothPlatformsHaveOne() {
        val bytes =
            nameTableBytes(
                listOf(
                    Triple(NamePlatform.MACINTOSH, NameId.FAMILY, "Mac Name"),
                    Triple(NamePlatform.WINDOWS, NameId.FAMILY, "Windows Name"),
                ),
            )
        val table = readNameTable(ByteCursor(bytes))
        assertEquals("Windows Name", table.get(NameId.FAMILY))
    }

    @Test
    fun getReturnsNullWhenTheNameIdIsAbsent() {
        val bytes = nameTableBytes(listOf(Triple(NamePlatform.WINDOWS, NameId.FAMILY, "Hyle Deco")))
        val table = readNameTable(ByteCursor(bytes))
        assertNull(table.get(NameId.POSTSCRIPT_NAME))
    }

    @Test
    fun decodesMacRomanHighBytes() {
        // 0x8E in Mac Roman is U+00E9 (é). Build the record's bytes directly to avoid needing a
        // Kotlin-side Mac Roman encoder for one test.
        val header = TestBytes().u16(0).u16(1).u16(6 + 12)
        val record =
            TestBytes()
                .u16(NamePlatform.MACINTOSH)
                .u16(0)
                .u16(0)
                .u16(NameId.FAMILY)
                .u16(1)
                .u16(0)
        val storage = TestBytes().u8(0x8E)
        val bytes = header.toByteArray() + record.toByteArray() + storage.toByteArray()
        val table = readNameTable(ByteCursor(bytes))
        assertEquals("é", table.get(NameId.FAMILY))
    }
}
