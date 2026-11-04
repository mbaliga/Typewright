// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.font.sfnt

import com.asoc.typewright.core.font.ufo.PlistValue
import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.core.font.ufo.UfoLib
import com.asoc.typewright.core.font.ufo.UfoProject
import com.asoc.typewright.core.font.ufo.withoutXmlIllegalCharacters
import com.asoc.typewright.core.geometry.CurveFormat
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * [this] as a [UfoProject]: every glyph, in font order, from [SfntFont.allGlyphs] (so glyph order,
 * contours -- quadratic, exactly as [SfntFont] already holds them, never re-fit -- and advance
 * widths are all the sfnt's own), plus a [UfoFontInfo] built from [head]/[hhea]/`OS/2`/[name]/[post].
 *
 * **Font info.** What a compiler needs to emit the same tables again, and what the licence audit
 * reads:
 * - names: [UfoFontInfo.familyName] and [UfoFontInfo.styleName] from the typographic records
 *   (`nameID` 16/17) when the font has them, else from 1/2, unless [familyName] overrides the family;
 *   `styleMapFamilyName` and `styleMapStyleName` (in [UfoFontInfo.other]) when the legacy records
 *   (1/2) say something the typographic ones do not; the PostScript name (6);
 * - rights and people: copyright (0), trademark (7), manufacturer (8) and its URL (11), designer (9)
 *   and its URL (12), licence description (13) and URL (14), description (10, as
 *   `openTypeNameDescription`), and the `OS/2` vendor id;
 * - numbers: [UfoFontInfo.unitsPerEm] from `head`; the version from `head.fontRevision`
 *   (`1.024` is major 1, minor 24); [UfoFontInfo.ascender]/[UfoFontInfo.descender] and the three
 *   `openTypeHhea*` values from `hhea`; the `openTypeOS2Typo*` and `openTypeOS2Win*` values,
 *   [UfoFontInfo.xHeight] and [UfoFontInfo.capHeight] from `OS/2` (the last two `null` on a
 *   version-0/1 table, or when the font has none); the weight and width class, the embedding bits
 *   (`openTypeOS2Type`, when any are set) and `italicAngle`.
 *
 * Not carried: the unique id (3) and full name (4), which belong to the font as it was released and
 * would be stale on a modified one; a compiler builds fresh ones from the names above.
 *
 * **Clean strings.** Every string is stripped of characters XML cannot carry (the `OS/2` vendor id
 * of Work Sans ends in a NUL) and its line breaks are made `\n`, so the font info is already what
 * the `fontinfo.plist` will say.
 *
 * **Unicodes.** Each glyph's `Glyph.unicodes` are the code points [cmap]'s best subtable maps to
 * it, in ascending order, so the lowest is the primary one (`space` gets U+0020, then U+00A0).
 * A mapping to glyph 0 is skipped, since glyph 0 is the missing glyph and a mapping to it means
 * "not covered" (fontTools' `getBestCmap` skips it too), as is one to a glyph id past the font's
 * glyph count or from a value outside Unicode's code space, which only a malformed font has.
 *
 * **Quadratic.** The project's [UfoLib.lineContourFormat] is [CurveFormat.QUADRATIC]: its glyphs
 * are TrueType outlines, so a glyph made only of straight segments must read back as quadratic
 * when the UFO is opened again, keeping its point counts (see [UfoLib]).
 */
fun SfntFont.toUfoProject(familyName: String? = null): UfoProject {
    val fontInfo = ufoFontInfoOf(head, hhea, name, os2, post, familyName)
    val codePointsByGlyphId = HashMap<Int, MutableList<Int>>()
    for ((codePoint, glyphId) in cmap.bestMapping) {
        if (glyphId in 1 until numGlyphs && codePoint in 0..MAX_CODE_POINT) {
            codePointsByGlyphId.getOrPut(glyphId) { mutableListOf() } += codePoint
        }
    }
    val glyphs =
        allGlyphs().mapIndexed { glyphId, glyph ->
            glyph.copy(unicodes = codePointsByGlyphId[glyphId]?.sorted().orEmpty())
        }
    return UfoProject(fontInfo, glyphs, lib = UfoLib(lineContourFormat = CurveFormat.QUADRATIC))
}

/**
 * The [UfoFontInfo] [toUfoProject] would give the font in [fontBytes] (see there for what it holds),
 * read from the `head`, `hhea`, `maxp`, `name`, `OS/2` and `post` tables alone: no outline is
 * decoded, so a caller that only needs the font's names and licence (the import audit, which may
 * refuse the font) does not pay for every glyph. Throws [IllegalArgumentException] for bytes that
 * are not a TrueType-outline font, as [readSfntFont] does.
 */
fun readSfntFontInfo(
    fontBytes: ByteArray,
    familyName: String? = null,
): UfoFontInfo {
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
    val name = readNameTable(requiredCursor("name"))
    val os2 = directory.cursor(fontBytes, "OS/2")?.let { readOs2Table(it) }
    val post = directory.cursor(fontBytes, "post")?.let { readPostTable(it, maxp.numGlyphs) }
    return ufoFontInfoOf(head, hhea, name, os2, post, familyName)
}

private fun ufoFontInfoOf(
    head: HeadTable,
    hhea: HheaTable,
    name: NameTable,
    os2: Os2Table?,
    post: PostTable?,
    familyName: String?,
): UfoFontInfo {
    val typographicFamily = name.cleanText(NameId.TYPOGRAPHIC_FAMILY)
    val legacyFamily = name.cleanText(NameId.FAMILY)
    val resolvedFamily = familyName ?: typographicFamily ?: legacyFamily
    val resolvedStyle = name.cleanText(NameId.TYPOGRAPHIC_SUBFAMILY) ?: name.cleanText(NameId.SUBFAMILY)

    val other = mutableListOf<Pair<String, PlistValue>>()

    fun addText(
        key: String,
        value: String?,
    ) {
        if (value != null) other += key to PlistValue.PString(value)
    }

    fun addInt(
        key: String,
        value: Int?,
    ) {
        if (value != null) other += key to PlistValue.PInteger(value.toLong())
    }

    if (familyName == null) {
        // The legacy (RIBBI) names matter only when they differ from the typographic ones.
        if (legacyFamily != null && legacyFamily != resolvedFamily) addText("styleMapFamilyName", legacyFamily)
        val legacyStyle = name.cleanText(NameId.SUBFAMILY)?.lowercase()
        if (legacyStyle in RIBBI_STYLES && legacyStyle != resolvedStyle?.lowercase()) addText("styleMapStyleName", legacyStyle)
    }
    addText("openTypeNameDescription", name.cleanText(NameId.DESCRIPTION))
    os2?.let { table ->
        addInt("openTypeOS2WeightClass", table.usWeightClass)
        addInt("openTypeOS2WidthClass", table.usWidthClass)
        val embeddingBits = FS_TYPE_BITS.filter { table.fsType and (1 shl it) != 0 }
        if (embeddingBits.isNotEmpty()) {
            other += "openTypeOS2Type" to PlistValue.PArray(embeddingBits.map { PlistValue.PInteger(it.toLong()) })
        }
    }
    post?.takeIf { it.italicAngle != 0.0 }?.let { other += "italicAngle" to PlistValue.PReal(it.italicAngle) }

    val revision = head.fontRevision
    val major = floor(revision).toInt()
    val minor = ((revision - major) * VERSION_MINOR_SCALE).roundToInt()

    val fontInfo =
        UfoFontInfo(
            familyName = resolvedFamily,
            styleName = resolvedStyle,
            unitsPerEm = head.unitsPerEm,
            ascender = hhea.ascender,
            descender = hhea.descender,
            xHeight = os2?.sxHeight,
            capHeight = os2?.sCapHeight,
            versionMajor = major,
            versionMinor = minor,
            copyright = name.cleanText(NameId.COPYRIGHT),
            trademark = name.cleanText(NameId.TRADEMARK),
            openTypeNameDesigner = name.cleanText(NameId.DESIGNER),
            openTypeNameDesignerURL = name.cleanText(NameId.DESIGNER_URL),
            openTypeNameManufacturer = name.cleanText(NameId.MANUFACTURER),
            openTypeNameManufacturerURL = name.cleanText(NameId.VENDOR_URL),
            openTypeNameLicense = name.cleanText(NameId.LICENSE_DESCRIPTION),
            openTypeNameLicenseURL = name.cleanText(NameId.LICENSE_URL),
            postscriptFontName = if (familyName == null) name.cleanText(NameId.POSTSCRIPT_NAME) else null,
            openTypeOS2VendorID = os2?.achVendId?.let(::cleanVendorId),
            openTypeHheaAscender = hhea.ascender,
            openTypeHheaDescender = hhea.descender,
            openTypeHheaLineGap = hhea.lineGap,
            openTypeOS2TypoAscender = os2?.sTypoAscender,
            openTypeOS2TypoDescender = os2?.sTypoDescender,
            openTypeOS2TypoLineGap = os2?.sTypoLineGap,
            openTypeOS2WinAscent = os2?.usWinAscent,
            openTypeOS2WinDescent = os2?.usWinDescent,
            other = other,
        )
    return fontInfo
}

/**
 * [NameTable.get] for [nameId], made safe to write into a `.plist`: characters XML cannot carry
 * are removed, every line break becomes `\n`, and the ends are trimmed. `null` when the record is
 * absent or nothing is left.
 */
internal fun NameTable.cleanText(nameId: Int): String? =
    get(nameId)
        ?.let { withoutXmlIllegalCharacters(it).replace("\r\n", "\n").replace('\r', '\n').trim() }
        ?.takeIf { it.isNotEmpty() }

/**
 * The `OS/2` vendor id, with the padding some fonts leave in it removed (Work Sans, Jost and
 * Playfair Display end theirs in a NUL). `null` when nothing is left.
 */
private fun cleanVendorId(raw: String): String? = withoutXmlIllegalCharacters(raw).takeIf { it.isNotEmpty() }

/** The values `OS/2` style bits map to in a UFO's `styleMapStyleName`. */
private val RIBBI_STYLES = setOf("regular", "italic", "bold", "bold italic")

/** The `fsType` bits UFO's `openTypeOS2Type` can name: 1 restricted, 2 preview and print, 3 editable, 8 no subsetting, 9 bitmap only. */
private val FS_TYPE_BITS = listOf(1, 2, 3, 8, 9)

/** UFO's `versionMinor` counts thousandths of the revision, so `1.024` is major 1, minor 24. */
private const val VERSION_MINOR_SCALE = 1000.0

private const val MAX_CODE_POINT = 0x10FFFF
