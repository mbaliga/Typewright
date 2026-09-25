// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.font.sfnt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MaxpTableTest {
    @Test
    fun readsVersion0Point5WithNoProfilingFields() {
        val bytes = TestBytes().fixed(0.5).u16(338).toByteArray()
        val maxp = readMaxpTable(ByteCursor(bytes))
        assertEquals(0.5, maxp.version)
        assertEquals(338, maxp.numGlyphs)
        assertNull(maxp.maxPoints)
        assertNull(maxp.maxComponentDepth)
    }

    @Test
    fun readsVersion1WithProfilingFields() {
        val bytes =
            TestBytes()
                .fixed(1.0)
                .u16(338) // numGlyphs
                .u16(1763) // maxPoints
                .u16(2) // maxContours
                .u16(100) // maxCompositePoints
                .u16(3) // maxCompositeContours
                .u16(2) // maxZones
                .u16(0) // maxTwilightPoints
                .u16(1) // maxStorage
                .u16(10) // maxFunctionDefs
                .u16(0) // maxInstructionDefs
                .u16(64) // maxStackElements
                .u16(128) // maxSizeOfInstructions
                .u16(2) // maxComponentElements
                .u16(1) // maxComponentDepth
                .toByteArray()

        val maxp = readMaxpTable(ByteCursor(bytes))
        assertEquals(1.0, maxp.version)
        assertEquals(338, maxp.numGlyphs)
        assertEquals(1763, maxp.maxPoints)
        assertEquals(2, maxp.maxContours)
        assertEquals(1, maxp.maxComponentDepth)
    }
}
