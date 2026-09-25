// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.font.sfnt

/** One `cmap` subtable header, as listed in the table's encoding records. */
data class CmapSubtableRecord(
    val platformId: Int,
    val encodingId: Int,
    val offset: Int,
)

/**
 * A `cmap` table (OpenType spec, "cmap"): the Unicode code-point-to-glyph-id mapping used to find
 * a glyph for a character the user typed or pasted. `core-font` reads formats 4 (`Segment mapping
 * to delta values`, the common BMP-only subtable) and 12 (`Segmented coverage`, needed for
 * anything outside the BMP); other formats are skipped, matching this task's brief ("cmap formats
 * 4 and 12 at least").
 */
data class CmapTable(
    val subtables: List<CmapSubtableRecord>,
    /** The single subtable [readCmapTable] picked as "best" (see its own KDoc), decoded to a plain map. */
    val bestMapping: Map<Int, Int>,
)

private data class RankedSubtable(
    val record: CmapSubtableRecord,
    val rank: Int,
    val format: Int,
)

/**
 * Reads every subtable's header, decodes the "best" one into a code-point-to-glyph-id [Map], and
 * returns both. "Best" prefers full Unicode coverage over BMP-only, and prefers Windows- or
 * Unicode-platform subtables (which is what every font `core-font` has seen in the wild declares)
 * over a Macintosh-only one: format 12 (platform 3/encoding 10, or platform 0/encoding 4 or 6)
 * beats format 4 (platform 3/encoding 1, or platform 0/encoding 3 or 4), and if neither preferred
 * combination exists, the first subtable this reader knows how to decode (format 4 or 12, in
 * encoding-record order) is used. `fonts/HyleDeco-Regular.ttf` has only two format-4 subtables
 * (platform 0/encoding 3 and platform 3/encoding 1); both decode to the same mapping, so either
 * being picked gives the same [bestMapping].
 */
fun readCmapTable(cursor: ByteCursor): CmapTable {
    val tableStart = cursor.position
    val version = cursor.u16()
    require(version == 0) { "cmap table version must be 0, was $version" }
    val numTables = cursor.u16()
    val records =
        List(numTables) {
            val platformId = cursor.u16()
            val encodingId = cursor.u16()
            val offset = cursor.u32().toInt()
            CmapSubtableRecord(platformId, encodingId, offset)
        }

    val ranked =
        records.mapNotNull { record ->
            val format = cursor.at(tableStart + record.offset).u16()
            if (format != 4 && format != 12) return@mapNotNull null
            val preferred =
                (format == 12 && record.platformId == 3 && record.encodingId == 10) ||
                    (format == 12 && record.platformId == 0 && (record.encodingId == 4 || record.encodingId == 6)) ||
                    (format == 4 && record.platformId == 3 && record.encodingId == 1) ||
                    (format == 4 && record.platformId == 0 && (record.encodingId == 3 || record.encodingId == 4))
            val rank =
                when {
                    format == 12 && preferred -> 0
                    format == 4 && preferred -> 1
                    format == 12 -> 2
                    else -> 3
                }
            RankedSubtable(record, rank, format)
        }

    val best = ranked.minByOrNull { it.rank }
    val mapping =
        if (best == null) {
            emptyMap()
        } else {
            val subtableCursor = cursor.at(tableStart + best.record.offset)
            when (best.format) {
                4 -> readCmapFormat4(subtableCursor)
                12 -> readCmapFormat12(subtableCursor)
                else -> error("unreachable: only formats 4 and 12 are ranked")
            }
        }
    return CmapTable(records, mapping)
}

/** Decodes a format-4 ("Segment mapping to delta values") subtable, cursor positioned at its `format` field. */
private fun readCmapFormat4(cursor: ByteCursor): Map<Int, Int> {
    val format = cursor.u16()
    require(format == 4) { "expected cmap format 4, found $format" }
    cursor.skip(2) // length
    cursor.skip(2) // language
    val segCountX2 = cursor.u16()
    val segCount = segCountX2 / 2
    cursor.skip(6) // searchRange, entrySelector, rangeShift

    val endCodes = IntArray(segCount) { cursor.u16() }
    cursor.skip(2) // reservedPad
    val startCodes = IntArray(segCount) { cursor.u16() }
    val idDeltas = IntArray(segCount) { cursor.i16() }
    val idRangeOffsetsPosition = cursor.position
    val idRangeOffsets = IntArray(segCount) { cursor.u16() }

    val mapping = LinkedHashMap<Int, Int>()
    for (seg in 0 until segCount) {
        val startCode = startCodes[seg]
        val endCode = endCodes[seg]
        if (startCode == 0xFFFF && endCode == 0xFFFF) continue // the required terminator segment
        val idRangeOffset = idRangeOffsets[seg]
        for (c in startCode..endCode) {
            if (idRangeOffset == 0) {
                // The spec's direct branch: idDelta arithmetic always produces a real mapping,
                // even on the rare font where that arithmetic happens to land on 0 (which is a
                // valid gid, "not mapped" is not a meaning this branch has).
                mapping[c] = (c + idDeltas[seg]) and 0xFFFF
            } else {
                // OpenType spec's pointer arithmetic: the offset is relative to the address of
                // this segment's own idRangeOffset entry, not to the table or the array start.
                val entryAddress = idRangeOffsetsPosition + seg * 2 + idRangeOffset + 2 * (c - startCode)
                val glyphIdArrayValue = cursor.at(entryAddress).u16()
                // Here, unlike the direct branch, 0 is the spec's explicit "not covered" sentinel.
                if (glyphIdArrayValue != 0) mapping[c] = (glyphIdArrayValue + idDeltas[seg]) and 0xFFFF
            }
        }
    }
    return mapping
}

/** Decodes a format-12 ("Segmented coverage") subtable, cursor positioned at its `format` field. */
private fun readCmapFormat12(cursor: ByteCursor): Map<Int, Int> {
    val format = cursor.u16()
    require(format == 12) { "expected cmap format 12, found $format" }
    cursor.skip(2) // reserved
    cursor.skip(4) // length
    cursor.skip(4) // language
    val numGroups = cursor.u32()
    val mapping = LinkedHashMap<Int, Int>()
    for (g in 0 until numGroups) {
        val startCharCode = cursor.u32()
        val endCharCode = cursor.u32()
        val startGlyphId = cursor.u32()
        var c = startCharCode
        while (c <= endCharCode) {
            val gid = startGlyphId + (c - startCharCode)
            mapping[c.toInt()] = gid.toInt()
            c += 1
        }
    }
    return mapping
}
