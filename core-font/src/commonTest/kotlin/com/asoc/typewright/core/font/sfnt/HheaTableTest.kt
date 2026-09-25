// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.font.sfnt

import kotlin.test.Test
import kotlin.test.assertEquals

class HheaTableTest {
    @Test
    fun readsHorizontalHeaderMetrics() {
        val bytes =
            TestBytes()
                .u16(1)
                .u16(0) // majorVersion, minorVersion
                .i16(984)
                .i16(-292)
                .i16(0) // ascender, descender, lineGap
                .u16(1800) // advanceWidthMax
                .i16(-50)
                .i16(-60)
                .i16(1200) // minLeftSideBearing, minRightSideBearing, xMaxExtent
                .i16(1)
                .i16(0)
                .i16(0) // caretSlopeRise, caretSlopeRun, caretOffset
                .pad(8) // reserved x4 int16
                .i16(0) // metricDataFormat
                .u16(325) // numberOfHMetrics
                .toByteArray()

        val hhea = readHheaTable(ByteCursor(bytes))
        assertEquals(984, hhea.ascender)
        assertEquals(-292, hhea.descender)
        assertEquals(1800, hhea.advanceWidthMax)
        assertEquals(325, hhea.numberOfHMetrics)
        assertEquals(1200, hhea.xMaxExtent)
    }
}
