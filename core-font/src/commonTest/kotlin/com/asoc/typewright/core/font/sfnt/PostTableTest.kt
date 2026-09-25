// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.font.sfnt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PostTableTest {
    private fun header(version: Double): TestBytes =
        TestBytes()
            .fixed(version)
            .fixed(-2.5) // italicAngle
            .i16(-100) // underlinePosition
            .i16(50) // underlineThickness
            .u32(1L) // isFixedPitch
            .pad(4 * 4) // minMemType42, maxMemType42, minMemType1, maxMemType1

    @Test
    fun readsVersion1UsingTheStandardMacGlyphOrder() {
        val bytes = header(1.0).toByteArray()
        val post = readPostTable(ByteCursor(bytes), numGlyphs = STANDARD_MAC_GLYPH_NAMES.size)
        assertEquals(1.0, post.version)
        assertEquals(-2.5, post.italicAngle)
        assertEquals(true, post.isFixedPitch)
        assertEquals(STANDARD_MAC_GLYPH_NAMES, post.glyphNames)
    }

    @Test
    fun readsVersion2WithStandardAndCustomNames() {
        // 3 glyphs: index 0 (".notdef", standard), then two custom names past 258.
        val bytes =
            header(2.0)
                .u16(3) // numberOfGlyphs
                .u16(0) // glyphNameIndex[0] -> ".notdef"
                .u16(258) // glyphNameIndex[1] -> custom name 0
                .u16(259) // glyphNameIndex[2] -> custom name 1
                .u8(5)
                .ascii("myst0")
                .u8(7)
                .ascii("zero.tf")
                .toByteArray()
        val post = readPostTable(ByteCursor(bytes), numGlyphs = 3)
        assertEquals(2.0, post.version)
        assertEquals(listOf(".notdef", "myst0", "zero.tf"), post.glyphNames)
    }

    @Test
    fun readsVersion2WithNoCustomNames() {
        val bytes =
            header(2.0)
                .u16(2)
                .u16(0)
                .u16(3) // both glyphs use standard names
                .toByteArray()
        val post = readPostTable(ByteCursor(bytes), numGlyphs = 2)
        assertEquals(listOf(".notdef", "space"), post.glyphNames)
    }

    @Test
    fun version3HasNoGlyphNames() {
        val bytes = header(3.0).toByteArray()
        val post = readPostTable(ByteCursor(bytes), numGlyphs = 10)
        assertNull(post.glyphNames)
    }

    @Test
    fun rejectsAMismatchedGlyphCountInFormat2() {
        val bytes =
            header(2.0)
                .u16(5)
                .u16(0)
                .u16(0)
                .u16(0)
                .u16(0)
                .u16(0)
                .toByteArray()
        assertFailsWith<IllegalArgumentException> { readPostTable(ByteCursor(bytes), numGlyphs = 2) }
    }
}
