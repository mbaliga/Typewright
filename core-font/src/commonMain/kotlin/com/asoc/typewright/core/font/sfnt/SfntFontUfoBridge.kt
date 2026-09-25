// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.font.sfnt

import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.core.font.ufo.UfoProject

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
 * **Carries no unicodes.** [SfntFont]'s glyphs (from [SfntFont.glyph]/[SfntFont.allGlyphs]) have no
 * Unicode code point of their own -- only [SfntFont.glyphIdForCodePoint] maps a code point to a
 * glyph id, the other direction from what a [UfoProject] glyph would need to carry its own
 * `unicode` -- so this bridge cannot round-trip `cmap` into the UFO glyphs it produces
 * (docs/OPEN_QUESTIONS.md item 124). A caller that needs unicodes on the result has to add them
 * itself, from the same `cmap` this function does not consult.
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
    return UfoProject(fontInfo, allGlyphs())
}
