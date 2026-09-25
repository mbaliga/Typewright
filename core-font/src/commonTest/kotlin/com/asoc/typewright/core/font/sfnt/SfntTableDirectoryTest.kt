// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.font.sfnt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SfntTableDirectoryTest {
    private fun buildFont(tables: List<Triple<String, Long, ByteArray>>): ByteArray {
        val header = TestBytes()
        header.u32(SFNT_VERSION_TRUETYPE)
        header.u16(tables.size)
        header.u16(0).u16(0).u16(0) // searchRange, entrySelector, rangeShift (unused by the reader)

        var offset = 12 + tables.size * 16
        val recordBytes = TestBytes()
        val dataBytes = TestBytes()
        for ((tag, checksum, data) in tables) {
            recordBytes
                .tag(tag)
                .u32(checksum)
                .u32(offset)
                .u32(data.size)
            dataBytes.bytes(data)
            offset += data.size
        }
        return header.toByteArray() + recordBytes.toByteArray() + dataBytes.toByteArray()
    }

    @Test
    fun readsTheTableDirectory() {
        val fontBytes =
            buildFont(
                listOf(
                    Triple("head", 111L, byteArrayOf(1, 2, 3, 4)),
                    Triple("glyf", 222L, byteArrayOf(5, 6)),
                ),
            )
        val directory = readSfntTableDirectory(fontBytes)
        assertEquals(SFNT_VERSION_TRUETYPE, directory.sfntVersion)
        assertEquals(2, directory.tables.size)

        val head = directory["head"]
        assertEquals("head", head?.tag)
        assertEquals(111L, head?.checksum)
        assertEquals(4, head?.length)

        assertNull(directory["cmap"])
    }

    @Test
    fun cursorPointsAtTheStartOfATablesBytes() {
        val fontBytes = buildFont(listOf(Triple("glyf", 0L, byteArrayOf(9, 8, 7))))
        val directory = readSfntTableDirectory(fontBytes)
        val cursor = directory.cursor(fontBytes, "glyf")
        assertEquals(9, cursor?.u8())
    }

    @Test
    fun cursorIsNullForAMissingTable() {
        val fontBytes = buildFont(emptyList())
        val directory = readSfntTableDirectory(fontBytes)
        assertNull(directory.cursor(fontBytes, "glyf"))
    }
}
