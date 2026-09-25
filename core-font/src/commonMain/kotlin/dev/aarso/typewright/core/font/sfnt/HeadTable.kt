// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.core.font.sfnt

/** Whether `loca` stores its offsets as `uint16` halved (`SHORT`) or `uint32` as-is (`LONG`). */
enum class LocaFormat {
    SHORT,
    LONG,
    ;

    companion object {
        fun fromIndexToLocFormat(value: Int): LocaFormat =
            when (value) {
                0 -> SHORT
                1 -> LONG
                else -> throw IllegalArgumentException("head.indexToLocFormat must be 0 or 1, was $value")
            }
    }
}

/**
 * The `head` table (OpenType spec, "head"): font-wide metadata. `core-font` reads only the fields
 * later tables in this package actually need ([unitsPerEm], [locaFormat]) plus a few more that are
 * cheap to keep and useful to a caller inspecting a font. `created`/`modified` are kept as the raw
 * `LONGDATETIME` count of seconds since 1904-01-01 (see [ByteCursor.longDateTime]); `checkSumAdjustment`
 * and `magicNumber` are skipped, since nothing here re-serializes a `glyf`-table font.
 */
data class HeadTable(
    val majorVersion: Int,
    val minorVersion: Int,
    val fontRevision: Double,
    val flags: Int,
    val unitsPerEm: Int,
    val created: Long,
    val modified: Long,
    val xMin: Int,
    val yMin: Int,
    val xMax: Int,
    val yMax: Int,
    val macStyle: Int,
    val lowestRecPPEM: Int,
    val fontDirectionHint: Int,
    val locaFormat: LocaFormat,
    val glyphDataFormat: Int,
)

/** Parses a `head` table from a cursor positioned at its start. */
fun readHeadTable(cursor: ByteCursor): HeadTable {
    val majorVersion = cursor.u16()
    val minorVersion = cursor.u16()
    val fontRevision = cursor.fixed()
    cursor.skip(4) // checkSumAdjustment
    cursor.skip(4) // magicNumber (0x5F0F3CF5)
    val flags = cursor.u16()
    val unitsPerEm = cursor.u16()
    val created = cursor.longDateTime()
    val modified = cursor.longDateTime()
    val xMin = cursor.i16()
    val yMin = cursor.i16()
    val xMax = cursor.i16()
    val yMax = cursor.i16()
    val macStyle = cursor.u16()
    val lowestRecPPEM = cursor.u16()
    val fontDirectionHint = cursor.i16()
    val indexToLocFormat = cursor.i16()
    val glyphDataFormat = cursor.i16()
    return HeadTable(
        majorVersion = majorVersion,
        minorVersion = minorVersion,
        fontRevision = fontRevision,
        flags = flags,
        unitsPerEm = unitsPerEm,
        created = created,
        modified = modified,
        xMin = xMin,
        yMin = yMin,
        xMax = xMax,
        yMax = yMax,
        macStyle = macStyle,
        lowestRecPPEM = lowestRecPPEM,
        fontDirectionHint = fontDirectionHint,
        locaFormat = LocaFormat.fromIndexToLocFormat(indexToLocFormat),
        glyphDataFormat = glyphDataFormat,
    )
}
