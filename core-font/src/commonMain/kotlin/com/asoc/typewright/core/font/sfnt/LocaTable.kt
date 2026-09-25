// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.font.sfnt

/**
 * The `loca` table (OpenType spec, "loca"): `numGlyphs + 1` byte offsets into `glyf`, so glyph
 * `gid`'s bytes run from `offsets[gid]` until `offsets[gid + 1]` (exclusive); equal consecutive
 * offsets mean glyph `gid` has no outline data at all (for example `.notdef` in some fonts, or
 * `space`). [LocaFormat.SHORT] stores each offset as a `uint16` that is half the real byte offset
 * (OpenType spec: "local offset divided by 2"); [LocaFormat.LONG] stores the real byte offset
 * directly as a `uint32`.
 */
data class LocaTable(
    val offsets: IntArray,
) {
    override fun equals(other: Any?): Boolean = other is LocaTable && offsets.contentEquals(other.offsets)

    override fun hashCode(): Int = offsets.contentHashCode()
}

/** Parses a `loca` table of `numGlyphs + 1` offsets from a cursor positioned at its start. */
fun readLocaTable(
    cursor: ByteCursor,
    numGlyphs: Int,
    format: LocaFormat,
): LocaTable {
    val count = numGlyphs + 1
    val offsets = IntArray(count)
    when (format) {
        LocaFormat.SHORT -> for (i in 0 until count) offsets[i] = cursor.u16() * 2
        LocaFormat.LONG -> for (i in 0 until count) offsets[i] = cursor.u32().toInt()
    }
    return LocaTable(offsets)
}
