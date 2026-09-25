// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.font.sfnt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class Os2TableTest {
    private fun version0Bytes(version: Int = 0): TestBytes =
        TestBytes()
            .u16(version)
            .i16(430) // xAvgCharWidth -- real spec field, unmodeled; see Os2Table.kt's own KDoc
            .u16(400) // usWeightClass
            .u16(5) // usWidthClass
            .u16(0) // fsType
            .pad(2 * 2) // ySubscriptXSize/YSize
            .pad(2 * 2) // ySubscriptXOffset/YOffset
            .pad(2 * 2) // ySuperscriptXSize/YSize
            .pad(2 * 2) // ySuperscriptXOffset/YOffset
            .pad(2 * 2) // yStrikeoutSize/Position
            .pad(2) // sFamilyClass
            .pad(10) // panose
            .pad(4 * 4) // ulUnicodeRange1-4
            .tag("AARS") // achVendId
            .u16(0x0040) // fsSelection
            .u16(0x0020) // usFirstCharIndex
            .u16(0xFFFF) // usLastCharIndex
            .i16(984) // sTypoAscender
            .i16(-292) // sTypoDescender
            .i16(0) // sTypoLineGap
            .u16(1000) // usWinAscent
            .u16(300) // usWinDescent

    @Test
    fun readsVersion0FieldsAndLeavesLaterOnesNull() {
        val os2 = readOs2Table(ByteCursor(version0Bytes().toByteArray()))
        assertEquals(0, os2.version)
        assertEquals(400, os2.usWeightClass)
        assertEquals("AARS", os2.achVendId)
        assertEquals(984, os2.sTypoAscender)
        assertEquals(-292, os2.sTypoDescender)
        assertEquals(1000, os2.usWinAscent)
        assertNull(os2.ulCodePageRange1)
        assertNull(os2.sxHeight)
        assertNull(os2.usLowerOpticalPointSize)
    }

    @Test
    fun readsVersion2FieldsIncludingCapHeight() {
        val bytes =
            version0Bytes(version = 2)
                .u32(1L)
                .u32(0L) // ulCodePageRange1, ulCodePageRange2
                .i16(500) // sxHeight
                .i16(700) // sCapHeight
                .u16(32) // usDefaultChar
                .u16(32) // usBreakChar
                .u16(3) // usMaxContext
                .toByteArray()
        val os2 = readOs2Table(ByteCursor(bytes))
        assertEquals(2, os2.version)
        assertEquals(1L, os2.ulCodePageRange1)
        assertEquals(500, os2.sxHeight)
        assertEquals(700, os2.sCapHeight)
        assertNull(os2.usLowerOpticalPointSize)
    }

    @Test
    fun readsVersion5OpticalSizeFields() {
        val bytes =
            version0Bytes(version = 5)
                .u32(0L)
                .u32(0L)
                .i16(500)
                .i16(700)
                .u16(32)
                .u16(32)
                .u16(3)
                .u16(8)
                .u16(72)
                .toByteArray()
        val os2 = readOs2Table(ByteCursor(bytes))
        assertEquals(8, os2.usLowerOpticalPointSize)
        assertEquals(72, os2.usUpperOpticalPointSize)
    }
}
