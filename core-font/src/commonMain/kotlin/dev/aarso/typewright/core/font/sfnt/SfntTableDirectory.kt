// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.core.font.sfnt

/** One entry of an sfnt table directory: where a table's bytes live in the font file. */
data class TableRecord(
    val tag: String,
    val checksum: Long,
    val offset: Int,
    val length: Int,
)

/**
 * A parsed sfnt table directory: the font's `sfntVersion` and a lookup from 4-character tag to
 * [TableRecord]. This is the entry point every other `sfnt` reader in this package starts from.
 */
data class SfntTableDirectory(
    val sfntVersion: Long,
    val tables: Map<String, TableRecord>,
) {
    /** The [TableRecord] for [tag], or `null` if the font has no such table. */
    operator fun get(tag: String): TableRecord? = tables[tag]

    /** A [ByteCursor] positioned at the start of [tag]'s bytes, or `null` if the table is absent. */
    fun cursor(
        bytes: ByteArray,
        tag: String,
    ): ByteCursor? = tables[tag]?.let { ByteCursor(bytes, it.offset) }
}

/** `sfntVersion` for TrueType-outline fonts (also written `0x00010000`). */
const val SFNT_VERSION_TRUETYPE: Long = 0x00010000L

/** `sfntVersion` for the alternate "true" tag some TrueType fonts use. */
const val SFNT_VERSION_TRUE_TAG: Long = 0x74727565L // "true"

/** `sfntVersion` for CFF-outline (OpenType/CFF) fonts. `core-font` does not read their outlines. */
const val SFNT_VERSION_CFF: Long = 0x4F54544FL // "OTTO"

/**
 * Reads the sfnt table directory from the start of [bytes]: `sfntVersion`, `numTables` and each
 * table's tag/checksum/offset/length (OpenType spec, "Organization of an OpenType Font").
 * `searchRange`, `entrySelector` and `rangeShift` are skipped — they exist to speed up a binary
 * search a modern reader does not need to perform by hand.
 */
fun readSfntTableDirectory(bytes: ByteArray): SfntTableDirectory {
    val cursor = ByteCursor(bytes)
    val sfntVersion = cursor.u32()
    val numTables = cursor.u16()
    cursor.skip(6) // searchRange, entrySelector, rangeShift
    val tables = LinkedHashMap<String, TableRecord>(numTables)
    repeat(numTables) {
        val tag = cursor.tag()
        val checksum = cursor.u32()
        val offset = cursor.u32()
        val length = cursor.u32()
        require(offset <= bytes.size.toLong() && offset + length <= bytes.size.toLong()) {
            "table '$tag' at offset $offset, length $length overruns a ${bytes.size}-byte font"
        }
        tables[tag] = TableRecord(tag, checksum, offset.toInt(), length.toInt())
    }
    return SfntTableDirectory(sfntVersion, tables)
}
