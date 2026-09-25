// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.font.sfnt

import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.core.font.ufo.UfoLib
import com.asoc.typewright.core.font.ufo.UfoProject
import com.asoc.typewright.core.geometry.CurveFormat

/**
 * [this] as a [UfoProject]: every glyph, in font order, from [SfntFont.allGlyphs] (so glyph order,
 * contours -- quadratic, exactly as [SfntFont] already holds them, never re-fit -- and advance
 * widths are all the sfnt's own), plus a [UfoFontInfo] built from [head]/[hhea]/`OS/2`/[name]:
 * [UfoFontInfo.unitsPerEm] from `head`, [UfoFontInfo.ascender]/[UfoFontInfo.descender] from
 * `hhea`, [UfoFontInfo.xHeight]/[UfoFontInfo.capHeight] from `OS/2` (`null` on a version-0/1
 * `OS/2` table, or when the font has none), and [UfoFontInfo.familyName]/[UfoFontInfo.styleName]
 * from the `name` table's family/subfamily records (`nameID` 1/2) unless [familyName] overrides
 * the family.
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
    val fontInfo =
        UfoFontInfo(
            familyName = familyName ?: name.get(NameId.FAMILY),
            styleName = name.get(NameId.SUBFAMILY),
            unitsPerEm = head.unitsPerEm,
            ascender = hhea.ascender,
            descender = hhea.descender,
            xHeight = os2?.sxHeight,
            capHeight = os2?.sCapHeight,
        )
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

private const val MAX_CODE_POINT = 0x10FFFF
