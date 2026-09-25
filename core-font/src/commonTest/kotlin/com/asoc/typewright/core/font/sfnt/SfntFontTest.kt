// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.font.sfnt

import com.asoc.typewright.core.geometry.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SfntFontTest {
    /** A minimal but complete one-glyph TrueType font: a triangle mapped from codepoint 'A', named "A" by `post` format 2.0. */
    private fun buildOneGlyphFont(): ByteArray {
        val glyfBody =
            TestBytes()
                .i16(1) // numberOfContours
                .i16(0)
                .i16(0)
                .i16(100)
                .i16(100)
                .u16(2) // endPtsOfContours[0]
                .u16(0)
                .u8(0x01)
                .u8(0x01)
                .u8(0x01)
                .i16(0)
                .i16(100)
                .i16(-50)
                .i16(0)
                .i16(0)
                .i16(100)
                .toByteArray()

        val head =
            TestBytes()
                .u16(1)
                .u16(0)
                .fixed(1.0)
                .u32(0)
                .u32(0x5F0F3CF5L)
                .u16(0)
                .u16(1000)
                .pad(8)
                .pad(8)
                .i16(0)
                .i16(0)
                .i16(100)
                .i16(100)
                .u16(0)
                .u16(8)
                .i16(2)
                .i16(1)
                .i16(0) // indexToLocFormat = 1 (LONG), matching loca below
                .toByteArray()

        val hhea =
            TestBytes()
                .u16(1)
                .u16(0)
                .i16(800)
                .i16(-200)
                .i16(0)
                .u16(600)
                .i16(0)
                .i16(0)
                .i16(600)
                .i16(0)
                .i16(0)
                .i16(0)
                .pad(8)
                .i16(0)
                .u16(1)
                .toByteArray()

        val maxp = TestBytes().fixed(0.5).u16(1).toByteArray()
        val loca = TestBytes().u32(0).u32(glyfBody.size).toByteArray() // LONG format
        val hmtx = TestBytes().u16(500).i16(0).toByteArray()

        val cmapSub =
            TestBytes()
                .u16(4)
                .u16(0)
                .u16(0)
                .u16(4) // segCountX2: 1 real segment + terminator
                .u16(0)
                .u16(0)
                .u16(0)
                .u16('A'.code) // endCode[0]
                .u16(0xFFFF)
                .u16(0) // reservedPad
                .u16('A'.code) // startCode[0]
                .u16(0xFFFF)
                .i16(0 - 'A'.code) // idDelta[0]: gid 0 for 'A'
                .i16(1)
                .u16(0)
                .u16(0) // idRangeOffset[0], [1]
                .toByteArray()
        val cmap =
            TestBytes()
                .u16(0)
                .u16(1)
                .u16(3)
                .u16(1)
                .u32(12)
                .bytes(cmapSub)
                .toByteArray()

        val name =
            TestBytes()
                .u16(0)
                .u16(0)
                .u16(6)
                .toByteArray() // format 0, zero records

        val post =
            TestBytes()
                .fixed(2.0)
                .fixed(0.0)
                .i16(0)
                .i16(0)
                .u32(0)
                .pad(16)
                .u16(1)
                .u16(258) // 1 glyph, custom name index 258
                .u8(1)
                .ascii("A")
                .toByteArray()

        val tables =
            listOf(
                "head" to head,
                "hhea" to hhea,
                "maxp" to maxp,
                "loca" to loca,
                "glyf" to glyfBody,
                "cmap" to cmap,
                "hmtx" to hmtx,
                "name" to name,
                "post" to post,
            )
        val header =
            TestBytes()
                .u32(SFNT_VERSION_TRUETYPE)
                .u16(tables.size)
                .u16(0)
                .u16(0)
                .u16(0)
        var offset = 12 + tables.size * 16
        val records = TestBytes()
        val data = TestBytes()
        for ((tag, bytes) in tables) {
            records
                .tag(tag)
                .u32(0L)
                .u32(offset)
                .u32(bytes.size)
            data.bytes(bytes)
            offset += bytes.size
        }
        return header.toByteArray() + records.toByteArray() + data.toByteArray()
    }

    @Test
    fun readsEveryTableAndResolvesAGlyphByCodepointAndByName() {
        val font = readSfntFont(buildOneGlyphFont())
        assertEquals(1, font.numGlyphs)
        assertEquals(1000, font.head.unitsPerEm)
        assertEquals(800, font.hhea.ascender)
        assertEquals("A", font.glyphName(0))
        assertEquals(0, font.glyphIdForCodePoint('A'.code))
        assertNull(font.glyphIdForCodePoint('B'.code))

        val byCodepoint = font.glyphForCodePoint('A'.code)
        val byName = font.glyph("A")
        assertEquals(byCodepoint, byName)
        assertEquals(500, byName?.advanceWidth)
        assertEquals(
            listOf(Point(0, 0), Point(100, 0), Point(50, 100)),
            byName
                ?.contours
                ?.single()
                ?.points
                ?.map { it.point },
        )

        assertEquals(1, font.allGlyphs().size)
        assertNull(font.glyph("nonexistent"))
    }

    @Test
    fun rejectsACffOutlineFont() {
        val bytes =
            TestBytes()
                .u32(SFNT_VERSION_CFF)
                .u16(0)
                .u16(0)
                .u16(0)
                .u16(0)
                .toByteArray()
        assertFailsWith<IllegalArgumentException> { readSfntFont(bytes) }
    }

    @Test
    fun rejectsAFontMissingARequiredTable() {
        // A table directory with only 'head' declared: every other required table is missing.
        val head =
            TestBytes()
                .u16(
                    1,
                ).u16(0)
                .fixed(1.0)
                .u32(0)
                .u32(0x5F0F3CF5L)
                .u16(0)
                .u16(1000)
                .pad(16)
                .pad(8)
                .u16(0)
                .u16(0)
                .i16(2)
                .i16(0)
                .i16(0)
                .toByteArray()
        val header =
            TestBytes()
                .u32(SFNT_VERSION_TRUETYPE)
                .u16(1)
                .u16(0)
                .u16(0)
                .u16(0)
        val record =
            TestBytes()
                .tag("head")
                .u32(0L)
                .u32(28)
                .u32(head.size)
        val bytes = header.toByteArray() + record.toByteArray() + head
        assertFailsWith<IllegalArgumentException> { readSfntFont(bytes) }
    }
}
