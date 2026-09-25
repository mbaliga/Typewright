// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.font.sfnt

import kotlin.test.Test
import kotlin.test.assertEquals

class LocaTableTest {
    @Test
    fun readsShortFormatOffsets() {
        // Short format stores each offset halved; readLocaTable must double it back.
        val bytes =
            TestBytes()
                .u16(0)
                .u16(10)
                .u16(10)
                .u16(40)
                .toByteArray()
        val loca = readLocaTable(ByteCursor(bytes), numGlyphs = 3, format = LocaFormat.SHORT)
        assertEquals(listOf(0, 20, 20, 80), loca.offsets.toList())
    }

    @Test
    fun readsLongFormatOffsets() {
        val bytes =
            TestBytes()
                .u32(0)
                .u32(100)
                .u32(100)
                .u32(500)
                .toByteArray()
        val loca = readLocaTable(ByteCursor(bytes), numGlyphs = 3, format = LocaFormat.LONG)
        assertEquals(listOf(0, 100, 100, 500), loca.offsets.toList())
    }

    @Test
    fun equalConsecutiveOffsetsMeanAnEmptyGlyph() {
        val bytes =
            TestBytes()
                .u32(0)
                .u32(0)
                .u32(50)
                .toByteArray()
        val loca = readLocaTable(ByteCursor(bytes), numGlyphs = 2, format = LocaFormat.LONG)
        assertEquals(loca.offsets[0], loca.offsets[1])
    }

    @Test
    fun equalsAndHashCodeCompareContents() {
        val a = LocaTable(intArrayOf(0, 10, 20))
        val b = LocaTable(intArrayOf(0, 10, 20))
        val c = LocaTable(intArrayOf(0, 10, 30))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertEquals(false, a == c)
    }
}
