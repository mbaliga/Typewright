package dev.aarso.typewright.core.font.sfnt

/**
 * A `post` table (OpenType spec, "post"): PostScript-adjacent metadata, of which `core-font` reads
 * only the two things a caller actually needs — [italicAngle] and, when the font's [version] holds
 * one, [glyphNames] (one name per glyph id).
 *
 * - **Version 1.0** glyphs are named directly from [STANDARD_MAC_GLYPH_NAMES], in glyph-id order;
 *   the table carries no extra bytes beyond the fixed header, so this is only valid for a font
 *   whose first 258 glyphs are exactly that standard set in that order.
 * - **Version 2.0** gives an explicit `glyphNameIndex` per glyph (< 258 names a
 *   [STANDARD_MAC_GLYPH_NAMES] entry; >= 258 indexes, from 258, into this table's own list of
 *   Pascal (length-prefixed) strings) — the common case for a font with glyphs the standard 258
 *   names cannot cover (ligatures, accented composites, `.tf`-suffixed alternates, ...), which is
 *   why `fonts/HyleDeco-Regular.ttf` uses it.
 * - **Version 3.0** (and any other version) declares no glyph names at all — common in a
 *   size-conscious web font, since nothing needs them at render time — so [glyphNames] is `null`
 *   and a caller falls back to synthesizing names (`core-font`'s [dev.aarso.typewright.core.font.sfnt.SfntFont]
 *   does this with `"glyphNNNNN"`, matching the pattern fontTools itself falls back to).
 */
data class PostTable(
    val version: Double,
    val italicAngle: Double,
    val underlinePosition: Int,
    val underlineThickness: Int,
    val isFixedPitch: Boolean,
    val glyphNames: List<String>?,
)

/** Parses a `post` table from a cursor positioned at its start, for a font with [numGlyphs] glyphs. */
fun readPostTable(
    cursor: ByteCursor,
    numGlyphs: Int,
): PostTable {
    val version = cursor.fixed()
    val italicAngle = cursor.fixed()
    val underlinePosition = cursor.i16()
    val underlineThickness = cursor.i16()
    val isFixedPitch = cursor.u32() != 0L
    cursor.skip(4 * 4) // minMemType42, maxMemType42, minMemType1, maxMemType1

    val glyphNames: List<String>? =
        when {
            version == 1.0 -> STANDARD_MAC_GLYPH_NAMES
            version == 2.0 -> readPostFormat2GlyphNames(cursor, numGlyphs)
            else -> null
        }

    return PostTable(
        version = version,
        italicAngle = italicAngle,
        underlinePosition = underlinePosition,
        underlineThickness = underlineThickness,
        isFixedPitch = isFixedPitch,
        glyphNames = glyphNames,
    )
}

private fun readPostFormat2GlyphNames(
    cursor: ByteCursor,
    numGlyphs: Int,
): List<String> {
    val tableNumGlyphs = cursor.u16()
    require(tableNumGlyphs == numGlyphs) {
        "post format 2.0's numberOfGlyphs ($tableNumGlyphs) does not match maxp.numGlyphs ($numGlyphs)"
    }
    val glyphNameIndex = IntArray(tableNumGlyphs) { cursor.u16() }
    // The table has no explicit count of Pascal strings: per the spec (and fontTools'
    // `_p_o_s_t.decode_format_2_0`, which this mirrors), they are read in a single fixed sequence
    // starting at logical index 258, so the highest glyphNameIndex value actually used tells us
    // how many to read. A font whose indices never reach 258 has none.
    val maxCustomIndex = glyphNameIndex.maxOrNull() ?: 0
    val namesToRead = if (maxCustomIndex < 258) 0 else maxCustomIndex - 257
    val customNames = ArrayList<String>(namesToRead)
    repeat(namesToRead) {
        val length = cursor.u8()
        customNames += decodeAscii(cursor.bytes(length))
    }
    return glyphNameIndex.map { index ->
        if (index < 258) STANDARD_MAC_GLYPH_NAMES[index] else customNames[index - 258]
    }
}

private fun decodeAscii(bytes: ByteArray): String {
    val sb = StringBuilder(bytes.size)
    for (b in bytes) sb.append((b.toInt() and 0xFF).toChar())
    return sb.toString()
}
