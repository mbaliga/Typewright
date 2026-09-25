// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.font.sfnt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CmapTableTest {
    /** A format-4 subtable mapping a handful of BMP code points, built by hand per the OpenType spec. */
    private fun format4Subtable(mapping: List<Pair<Int, Int>>): ByteArray {
        // One segment per (codepoint, gid) pair plus the required 0xFFFF terminator segment;
        // idRangeOffset 0 for every real segment (so gid = code + idDelta, mod 65536).
        val segments = mapping.sortedBy { it.first } + (0xFFFF to 0)
        val segCount = segments.size
        val out = TestBytes()
        out.u16(4) // format
        out.u16(0) // length (unused by the reader)
        out.u16(0) // language
        out.u16(segCount * 2)
        out.u16(0).u16(0).u16(0) // searchRange, entrySelector, rangeShift
        for ((code, _) in segments) out.u16(code) // endCode == startCode (one codepoint per segment)
        out.u16(0) // reservedPad
        for ((code, _) in segments) out.u16(code) // startCode
        for ((code, gid) in segments) {
            val delta = if (code == 0xFFFF && gid == 0) 1 else (gid - code)
            out.i16(delta)
        }
        for (i in segments.indices) out.u16(0) // idRangeOffset: always 0 here
        return out.toByteArray()
    }

    private fun cmapTableBytes(
        subtables: List<Triple<Int, Int, ByteArray>>, // platformId, encodingId, subtable bytes
    ): ByteArray {
        val header = TestBytes().u16(0).u16(subtables.size)
        var offset = 4 + subtables.size * 8
        val records = TestBytes()
        val data = TestBytes()
        for ((platformId, encodingId, bytes) in subtables) {
            records.u16(platformId).u16(encodingId).u32(offset)
            data.bytes(bytes)
            offset += bytes.size
        }
        return header.toByteArray() + records.toByteArray() + data.toByteArray()
    }

    @Test
    fun decodesAFormat4Subtable() {
        val subtable = format4Subtable(listOf('A'.code to 5, 'B'.code to 6, 'z'.code to 200))
        val bytes = cmapTableBytes(listOf(Triple(3, 1, subtable)))
        val cmap = readCmapTable(ByteCursor(bytes))
        assertEquals(5, cmap.bestMapping['A'.code])
        assertEquals(6, cmap.bestMapping['B'.code])
        assertEquals(200, cmap.bestMapping['z'.code])
        assertEquals(1, cmap.subtables.size)
    }

    @Test
    fun decodesAFormat12Subtable() {
        val subtable =
            TestBytes()
                .u16(12)
                .u16(0) // format, reserved
                .u32(0) // length (unused)
                .u32(0) // language
                .u32(2) // numGroups
                .u32(0x41)
                .u32(0x5A)
                .u32(100) // group 1: 'A'..'Z' -> gid 100..125
                .u32(0x1F600)
                .u32(0x1F600)
                .u32(500) // group 2: a single astral code point -> gid 500
                .toByteArray()
        val bytes = cmapTableBytes(listOf(Triple(3, 10, subtable)))
        val cmap = readCmapTable(ByteCursor(bytes))
        assertEquals(100, cmap.bestMapping['A'.code])
        assertEquals(125, cmap.bestMapping['Z'.code])
        assertEquals(500, cmap.bestMapping[0x1F600])
    }

    @Test
    fun prefersFormat12OverFormat4WhenBothArePresent() {
        val f4 = format4Subtable(listOf('A'.code to 1))
        val f12 =
            TestBytes()
                .u16(12)
                .u16(0)
                .u32(0)
                .u32(0)
                .u32(1)
                .u32('A'.code.toLong())
                .u32('A'.code.toLong())
                .u32(999)
                .toByteArray()
        val bytes = cmapTableBytes(listOf(Triple(3, 1, f4), Triple(3, 10, f12)))
        val cmap = readCmapTable(ByteCursor(bytes))
        assertEquals(999, cmap.bestMapping['A'.code])
    }

    @Test
    fun idRangeOffsetIndirectionResolvesThroughTheGlyphIdArray() {
        // One segment covering 'A'..'C' via a non-zero idRangeOffset pointing at an explicit
        // glyphIdArray, exercising the branch format4Subtable()'s helper above does not.
        val out = TestBytes()
        out.u16(4).u16(0).u16(0)
        out.u16(4) // segCountX2: 2 segments (1 real + terminator)
        out.u16(0).u16(0).u16(0)
        out.u16('C'.code) // endCode[0]
        out.u16(0xFFFF) // endCode[1] (terminator)
        out.u16(0) // reservedPad
        out.u16('A'.code) // startCode[0]
        out.u16(0xFFFF) // startCode[1]
        out.i16(0) // idDelta[0] (unused: idRangeOffset != 0)
        out.i16(1) // idDelta[1] (terminator)
        // idRangeOffset[0] points 4 bytes past its own position, i.e. right after idRangeOffset[1]
        // (the start of the glyphIdArray below) -- see readCmapFormat4's KDoc for the addressing rule.
        out.u16(4) // idRangeOffset[0]
        out.u16(0) // idRangeOffset[1] (terminator, unused)
        // glyphIdArray: gid for 'A','B','C' = 10, 11, 12
        out.u16(10).u16(11).u16(12)
        val subtable = out.toByteArray()
        val bytes = cmapTableBytes(listOf(Triple(3, 1, subtable)))
        val cmap = readCmapTable(ByteCursor(bytes))
        assertEquals(10, cmap.bestMapping['A'.code])
        assertEquals(11, cmap.bestMapping['B'.code])
        assertEquals(12, cmap.bestMapping['C'.code])
    }

    @Test
    fun aDirectSegmentWhoseArithmeticLandsOnGidZeroIsStillAMapping() {
        // idDelta arithmetic in the idRangeOffset == 0 branch can legitimately compute gid 0 (for
        // example a minimal test font whose one glyph is at index 0); 0 is only a "not mapped"
        // sentinel in the *indirect* (glyphIdArray) branch, never in this one.
        val subtable = format4Subtable(listOf('A'.code to 0))
        val bytes = cmapTableBytes(listOf(Triple(3, 1, subtable)))
        val cmap = readCmapTable(ByteCursor(bytes))
        assertEquals(0, cmap.bestMapping['A'.code])
    }

    @Test
    fun anIndirectSegmentEntryOfZeroMeansNotCovered() {
        val out = TestBytes()
        out.u16(4).u16(0).u16(0)
        out.u16(4) // segCountX2: 1 real segment + terminator
        out.u16(0).u16(0).u16(0)
        out.u16('B'.code) // endCode[0]
        out.u16(0xFFFF)
        out.u16(0) // reservedPad
        out.u16('A'.code) // startCode[0]: covers 'A'..'B'
        out.u16(0xFFFF)
        out.i16(0) // idDelta[0] (unused: idRangeOffset != 0)
        out.i16(1)
        out.u16(4) // idRangeOffset[0]: points right after idRangeOffset[1], at the glyphIdArray
        out.u16(0) // idRangeOffset[1] (terminator, unused)
        out.u16(0).u16(42) // glyphIdArray: 'A' -> 0 (not covered), 'B' -> 42
        val bytes = cmapTableBytes(listOf(Triple(3, 1, out.toByteArray())))
        val cmap = readCmapTable(ByteCursor(bytes))
        assertEquals(false, cmap.bestMapping.containsKey('A'.code))
        assertEquals(42, cmap.bestMapping['B'.code])
    }

    @Test
    fun rejectsAnUnsupportedTableVersion() {
        val bytes = TestBytes().u16(1).u16(0).toByteArray()
        assertFailsWith<IllegalArgumentException> { readCmapTable(ByteCursor(bytes)) }
    }

    @Test
    fun emptyTableGivesAnEmptyMapping() {
        val bytes = cmapTableBytes(emptyList())
        val cmap = readCmapTable(ByteCursor(bytes))
        assertEquals(emptyMap(), cmap.bestMapping)
    }
}
