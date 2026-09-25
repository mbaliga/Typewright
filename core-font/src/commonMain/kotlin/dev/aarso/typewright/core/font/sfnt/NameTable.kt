// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.core.font.sfnt

/**
 * Codepoints for Mac Roman bytes 0x80-0xFF (0x00-0x7F is plain ASCII); Mac Roman is the encoding
 * platform-1 (`Macintosh`) `name` records use (OpenType spec, "name", "Macintosh platform (Platform
 * ID = 1)": "This corresponds to the encoding used for Mac OS 'plain text'... typically the Roman
 * script system, but does depend on the 'language' field"; `core-font` decodes only the common
 * Roman variant, which is what every font it has read so far declares).
 */
private val MAC_ROMAN_HIGH_BYTES =
    intArrayOf(
        196,
        197,
        199,
        201,
        209,
        214,
        220,
        225,
        224,
        226,
        228,
        227,
        229,
        231,
        233,
        232,
        234,
        235,
        237,
        236,
        238,
        239,
        241,
        243,
        242,
        244,
        246,
        245,
        250,
        249,
        251,
        252,
        8224,
        176,
        162,
        163,
        167,
        8226,
        182,
        223,
        174,
        169,
        8482,
        180,
        168,
        8800,
        198,
        216,
        8734,
        177,
        8804,
        8805,
        165,
        181,
        8706,
        8721,
        8719,
        960,
        8747,
        170,
        186,
        937,
        230,
        248,
        191,
        161,
        172,
        8730,
        402,
        8776,
        8710,
        171,
        187,
        8230,
        160,
        192,
        195,
        213,
        338,
        339,
        8211,
        8212,
        8220,
        8221,
        8216,
        8217,
        247,
        9674,
        255,
        376,
        8260,
        8364,
        8249,
        8250,
        64257,
        64258,
        8225,
        183,
        8218,
        8222,
        8240,
        194,
        202,
        193,
        203,
        200,
        205,
        206,
        207,
        204,
        211,
        212,
        63743,
        210,
        218,
        219,
        217,
        305,
        710,
        732,
        175,
        728,
        729,
        730,
        184,
        733,
        731,
        711,
    )

/** Platform IDs a `name` record can declare (OpenType spec, "name"). */
object NamePlatform {
    const val UNICODE = 0
    const val MACINTOSH = 1
    const val WINDOWS = 3
}

/** Well-known `nameID` values (OpenType spec, "name", "Name IDs") that a caller commonly wants. */
object NameId {
    const val FAMILY = 1
    const val SUBFAMILY = 2
    const val UNIQUE_ID = 3
    const val FULL_NAME = 4
    const val VERSION = 5
    const val POSTSCRIPT_NAME = 6
}

/** One decoded `name` table record. */
data class NameRecord(
    val platformId: Int,
    val encodingId: Int,
    val languageId: Int,
    val nameId: Int,
    val value: String,
)

/** A `name` table (OpenType spec, "name"): every [NameRecord], in file order. Format 1's language-tag records are not read. */
data class NameTable(
    val records: List<NameRecord>,
) {
    /**
     * The first record for [nameId], preferring Windows/Unicode BMP platforms (which cover the
     * full repertoire a name can use) over Macintosh, then the lowest language id, or `null` if
     * [nameId] has no record at all.
     */
    fun get(nameId: Int): String? =
        records
            .filter { it.nameId == nameId }
            .minByOrNull { record ->
                val platformRank = if (record.platformId == NamePlatform.MACINTOSH) 1 else 0
                platformRank * 1_000_000 + record.languageId
            }?.value
}

/** Parses a `name` table (format 0 or 1; format 1's extra language-tag records are skipped) from a cursor at its start. */
fun readNameTable(cursor: ByteCursor): NameTable {
    val tableStart = cursor.position
    val format = cursor.u16()
    require(format == 0 || format == 1) { "name table format must be 0 or 1, was $format" }
    val count = cursor.u16()
    val stringOffset = cursor.u16()
    val records = ArrayList<NameRecord>(count)
    repeat(count) {
        val platformId = cursor.u16()
        val encodingId = cursor.u16()
        val languageId = cursor.u16()
        val nameId = cursor.u16()
        val length = cursor.u16()
        val offset = cursor.u16()
        val bytes = cursor.at(tableStart + stringOffset + offset).bytes(length)
        val value = decodeNameBytes(platformId, bytes)
        records += NameRecord(platformId, encodingId, languageId, nameId, value)
    }
    return NameTable(records)
}

private fun decodeNameBytes(
    platformId: Int,
    bytes: ByteArray,
): String =
    when (platformId) {
        NamePlatform.MACINTOSH -> decodeMacRoman(bytes)
        else -> decodeUtf16Be(bytes) // Windows and Unicode platforms both use UTF-16BE in practice
    }

private fun decodeMacRoman(bytes: ByteArray): String {
    val sb = StringBuilder(bytes.size)
    for (b in bytes) {
        val u = b.toInt() and 0xFF
        sb.append(if (u < 0x80) u.toChar() else MAC_ROMAN_HIGH_BYTES[u - 0x80].toChar())
    }
    return sb.toString()
}

private fun decodeUtf16Be(bytes: ByteArray): String {
    require(bytes.size % 2 == 0) { "UTF-16BE name bytes must have an even length, had ${bytes.size}" }
    val sb = StringBuilder(bytes.size / 2)
    var i = 0
    while (i < bytes.size) {
        val unit = ((bytes[i].toInt() and 0xFF) shl 8) or (bytes[i + 1].toInt() and 0xFF)
        sb.append(unit.toChar())
        i += 2
    }
    return sb.toString()
}
