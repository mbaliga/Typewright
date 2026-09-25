// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.font.sfnt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HeadTableTest {
    private fun buildHead(
        unitsPerEm: Int,
        indexToLocFormat: Int,
    ): ByteArray =
        TestBytes()
            .u16(1)
            .u16(0) // majorVersion, minorVersion
            .fixed(1.0) // fontRevision
            .u32(0) // checkSumAdjustment
            .u32(0x5F0F3CF5L) // magicNumber
            .u16(0x000B) // flags
            .u16(unitsPerEm)
            .pad(8) // created
            .pad(8) // modified
            .i16(-100)
            .i16(-200)
            .i16(900)
            .i16(1000) // xMin, yMin, xMax, yMax
            .u16(0) // macStyle
            .u16(9) // lowestRecPPEM
            .i16(2) // fontDirectionHint
            .i16(indexToLocFormat)
            .i16(0) // glyphDataFormat
            .toByteArray()

    @Test
    fun readsAShortLocaFormatHead() {
        val head = readHeadTable(ByteCursor(buildHead(unitsPerEm = 1000, indexToLocFormat = 0)))
        assertEquals(1000, head.unitsPerEm)
        assertEquals(LocaFormat.SHORT, head.locaFormat)
        assertEquals(1.0, head.fontRevision)
        assertEquals(-100, head.xMin)
        assertEquals(1000, head.yMax)
        assertEquals(9, head.lowestRecPPEM)
    }

    @Test
    fun readsALongLocaFormatHead() {
        val head = readHeadTable(ByteCursor(buildHead(unitsPerEm = 2048, indexToLocFormat = 1)))
        assertEquals(2048, head.unitsPerEm)
        assertEquals(LocaFormat.LONG, head.locaFormat)
    }

    @Test
    fun rejectsAnInvalidIndexToLocFormat() {
        assertFailsWith<IllegalArgumentException> { LocaFormat.fromIndexToLocFormat(2) }
    }
}
