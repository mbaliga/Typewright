// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.font.sfnt

import com.asoc.typewright.core.geometry.Glyph

/**
 * A parsed TrueType-outline (`glyf`) sfnt font: every table [readSfntFont] knows how to read, plus
 * [glyph] and [allGlyphs] to get `core-geometry` [Glyph]s with composite glyphs already decomposed
 * (see [decomposeGlyf]). CFF-outline fonts are out of `core-font`'s v1 scope
 * (docs/ARCHITECTURE_REVIEW.md section 3 `:core-font`: "No CFF in v1: google/fonts ships only
 * TTF."); [readSfntFont] rejects one with a clear message rather than silently returning an
 * outline-free font.
 */
class SfntFont internal constructor(
    val head: HeadTable,
    val hhea: HheaTable,
    val maxp: MaxpTable,
    val hmtx: HmtxTable,
    val cmap: CmapTable,
    val name: NameTable,
    val os2: Os2Table?,
    val post: PostTable?,
    private val glyphOrder: List<String>,
    private val rawGlyphs: List<RawGlyfRecord>,
) {
    /** Number of glyphs in the font, i.e. `maxp.numGlyphs`. */
    val numGlyphs: Int get() = maxp.numGlyphs

    /** The resolved name of glyph [gid]: from `post` when it has names, else `"glyphNNNNN"`. */
    fun glyphName(gid: Int): String = glyphOrder[gid]

    /** The glyph id for Unicode code point [codePoint], via `cmap`'s best subtable, or `null` if unmapped. */
    fun glyphIdForCodePoint(codePoint: Int): Int? = cmap.bestMapping[codePoint]

    /** Glyph [gid] as a `core-geometry` [Glyph], with any composite components already decomposed and transformed. */
    fun glyph(gid: Int): Glyph {
        val contours = decomposeGlyf(rawGlyphs, gid)
        return Glyph(glyphOrder[gid], hmtx[gid].advanceWidth, contours)
    }

    /** The glyph named [name], or `null` if no glyph has that resolved name (see [glyphName]). */
    fun glyph(name: String): Glyph? {
        val gid = glyphOrder.indexOf(name)
        return if (gid < 0) null else glyph(gid)
    }

    /** The glyph mapped from Unicode code point [codePoint] via `cmap`, or `null` if unmapped. */
    fun glyphForCodePoint(codePoint: Int): Glyph? = glyphIdForCodePoint(codePoint)?.let { glyph(it) }

    /** Every glyph in the font, in glyph-id (font) order. */
    fun allGlyphs(): List<Glyph> = List(numGlyphs) { glyph(it) }
}

/**
 * Reads [fontBytes] as a TrueType-outline sfnt font: the table directory, then `head`, `hhea`,
 * `maxp`, `loca`, `glyf` (simple and composite, decoded to `core-geometry` contours), `cmap`
 * (formats 4/12), `hmtx`, `name`, and `OS/2`/`post` when present. `glyf`, `loca`, `cmap` and `hmtx`
 * are required; a font missing any of them cannot have its glyphs or metrics read and is rejected
 * with a clear message, as is one whose `sfntVersion` marks it CFF-outline (`"OTTO"`) rather than
 * TrueType.
 */
fun readSfntFont(fontBytes: ByteArray): SfntFont {
    val directory = readSfntTableDirectory(fontBytes)
    require(directory.sfntVersion != SFNT_VERSION_CFF) {
        "font has CFF outlines (sfntVersion 'OTTO'); core-font reads only TrueType glyf-outline fonts in v1"
    }
    require(directory.sfntVersion == SFNT_VERSION_TRUETYPE || directory.sfntVersion == SFNT_VERSION_TRUE_TAG) {
        "unrecognised sfntVersion 0x${directory.sfntVersion.toString(16)}"
    }

    fun requiredCursor(tag: String): ByteCursor =
        directory.cursor(fontBytes, tag) ?: throw IllegalArgumentException("font has no '$tag' table")

    val head = readHeadTable(requiredCursor("head"))
    val hhea = readHheaTable(requiredCursor("hhea"))
    val maxp = readMaxpTable(requiredCursor("maxp"))

    val loca = readLocaTable(requiredCursor("loca"), maxp.numGlyphs, head.locaFormat)

    val glyfRecord = directory["glyf"] ?: throw IllegalArgumentException("font has no 'glyf' table")
    val rawGlyphs = List(maxp.numGlyphs) { gid -> readGlyfRecord(fontBytes, glyfRecord.offset, loca, gid) }

    val cmap = readCmapTable(requiredCursor("cmap"))
    val hmtx = readHmtxTable(requiredCursor("hmtx"), hhea.numberOfHMetrics, maxp.numGlyphs)
    val name = readNameTable(requiredCursor("name"))
    val os2 = directory.cursor(fontBytes, "OS/2")?.let { readOs2Table(it) }
    val post = directory.cursor(fontBytes, "post")?.let { readPostTable(it, maxp.numGlyphs) }

    val postNames = post?.glyphNames
    val glyphOrder =
        List(maxp.numGlyphs) { gid ->
            postNames?.getOrNull(gid) ?: "glyph$gid"
        }

    return SfntFont(
        head = head,
        hhea = hhea,
        maxp = maxp,
        hmtx = hmtx,
        cmap = cmap,
        name = name,
        os2 = os2,
        post = post,
        glyphOrder = glyphOrder,
        rawGlyphs = rawGlyphs,
    )
}
