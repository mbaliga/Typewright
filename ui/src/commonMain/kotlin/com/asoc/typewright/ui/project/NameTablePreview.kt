// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.project

import com.asoc.typewright.core.font.ufo.PlistValue
import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.project.imports.postScriptName

/** One line of the name table preview: the name ID and the text a compiler would write there. */
data class NameTableRow(
    val id: Int,
    val text: String,
)

/**
 * The names a UFO compiler writes into a font's `name` table from [info], as Font info previews
 * them: 1 family, 2 style, 4 full name, 6 PostScript name, and 16 and 17 when the font has a
 * separate preferred family. A key the font states (`styleMapFamilyName`, `postscriptFontName`, the
 * preferred names) is used as it is; otherwise the name is built from the family and style the way
 * the UFO specification describes. This is a preview of what the keys say, not the output of a build
 * (the app compiles nothing yet). Empty when the font has no family name.
 */
fun nameTablePreview(info: UfoFontInfo): List<NameTableRow> {
    val family = info.familyName?.takeIf { it.isNotBlank() } ?: return emptyList()
    val style = info.styleName?.takeIf { it.isNotBlank() } ?: "Regular"
    val rows = mutableListOf<NameTableRow>()
    val styleMapFamily = info.other.stringOf("styleMapFamilyName") ?: family
    rows += NameTableRow(1, styleMapFamily)
    rows += NameTableRow(2, style)
    rows += NameTableRow(4, info.other.stringOf("postscriptFullName") ?: "$family $style")
    rows += NameTableRow(6, info.postscriptFontName ?: postScriptName(family, style) ?: "—")
    val preferredFamily = info.openTypeNamePreferredFamilyName
    val preferredStyle = info.openTypeNamePreferredSubfamilyName
    if (preferredFamily != null || preferredStyle != null) {
        rows += NameTableRow(16, preferredFamily ?: family)
        rows += NameTableRow(17, preferredStyle ?: style)
    }
    return rows
}

/** [rows] as the preview shows them, two to a line (`1 Hyle Deco · 2 Regular`). */
fun nameTableLines(rows: List<NameTableRow>): List<String> =
    rows.chunked(2).map { pair -> pair.joinToString(" · ") { "${it.id} ${it.text}" } }

private fun List<Pair<String, PlistValue>>.stringOf(key: String): String? =
    (lastOrNull { it.first == key }?.second as? PlistValue.PString)?.value?.takeIf { it.isNotBlank() }
