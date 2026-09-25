// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.font.sfnt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HmtxTableTest {
    @Test
    fun readsExplicitMetricsForEveryGlyphWhenCountsMatch() {
        val bytes =
            TestBytes()
                .u16(500)
                .i16(10)
                .u16(600)
                .i16(-5)
                .toByteArray()
        val hmtx = readHmtxTable(ByteCursor(bytes), numberOfHMetrics = 2, numGlyphs = 2)
        assertEquals(2, hmtx.size)
        assertEquals(HMetric(500, 10), hmtx[0])
        assertEquals(HMetric(600, -5), hmtx[1])
    }

    @Test
    fun trailingGlyphsRepeatTheLastAdvanceWidth() {
        val bytes =
            TestBytes()
                .u16(500)
                .i16(10) // glyph 0: explicit
                .i16(2)
                .i16(4) // glyphs 1, 2: lsb only, share glyph 0's advance width
                .toByteArray()
        val hmtx = readHmtxTable(ByteCursor(bytes), numberOfHMetrics = 1, numGlyphs = 3)
        assertEquals(HMetric(500, 10), hmtx[0])
        assertEquals(HMetric(500, 2), hmtx[1])
        assertEquals(HMetric(500, 4), hmtx[2])
    }

    @Test
    fun rejectsMoreHMetricsThanGlyphs() {
        assertFailsWith<IllegalArgumentException> {
            readHmtxTable(ByteCursor(ByteArray(0)), numberOfHMetrics = 5, numGlyphs = 2)
        }
    }
}
