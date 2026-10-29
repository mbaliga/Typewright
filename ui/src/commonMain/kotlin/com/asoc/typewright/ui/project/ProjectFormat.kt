// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.project

import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.qa.GlyphNodeEconomyResult
import com.asoc.typewright.qa.corpus.NodeEconomyVerdict
import kotlin.math.abs
import kotlin.math.roundToLong

/** [value] with a comma between thousands (`25,991`); common code has no locale formatting to lean on. */
fun groupThousands(value: Int): String {
    val digits = abs(value.toLong()).toString()
    val grouped =
        digits
            .reversed()
            .chunked(THOUSAND_DIGITS)
            .joinToString(",")
            .reversed()
    return if (value < 0) "−$grouped" else grouped
}

/** The font's version as a person reads it (`1.024`): major, a dot, minor to three digits. Null when `fontinfo.plist` states neither. */
fun fontVersionText(info: UfoFontInfo): String? {
    val major = info.versionMajor
    val minor = info.versionMinor
    if (major == null && minor == null) return null
    return "${major ?: 0}.${(minor ?: 0).toString().padStart(VERSION_MINOR_DIGITS, '0')}"
}

/** [value] to three decimal places (`1.276`), rounded half away from zero. */
fun threeDecimals(value: Double): String {
    val scaled = (abs(value) * THOUSAND).roundToLong()
    val whole = scaled / THOUSAND
    val fraction = (scaled % THOUSAND).toString().padStart(VERSION_MINOR_DIGITS, '0')
    return (if (value < 0 && scaled != 0L) "−" else "") + "$whole.$fraction"
}

/** A whole number in font units, with a real minus sign for a negative one (`−292`). */
fun unitsText(value: Int): String = if (value < 0) "−${abs(value)}" else value.toString()

/**
 * What a long-press on a glyph in the Glyphs grid says about its box: how many on-curve points the
 * glyph has and where the style class's box for that glyph sits (its quartiles and range over the
 * corpus faces). A glyph the corpus has no box for says so, and a project judged by no class says
 * that instead. The verdict is a word as well as a shape.
 */
fun glyphBoxSentence(
    name: String,
    onCurve: Int,
    result: GlyphNodeEconomyResult?,
    style: ProjectStyle?,
): String {
    val points = if (onCurve == 1) "1 on-curve point" else "$onCurve on-curve points"
    if (style == null) return "$name · $points. There is no style class to judge it against yet."
    val box = result?.onCurveBox ?: return "$name · $points. The ${style.name} data has no box for this glyph."
    val verdict =
        when (result.onCurveVerdict) {
            NodeEconomyVerdict.OUTLIER -> "● Outlier"
            NodeEconomyVerdict.ABOVE -> "◐ Above the box"
            NodeEconomyVerdict.IN_RANGE -> "✓ In range"
            null -> "Not judged"
        }
    return "$name · $points. $verdict. Box over ${box.n} ${style.name} faces: ${wholeOrDecimal(box.q1)}–${wholeOrDecimal(box.q3)}, " +
        "median ${wholeOrDecimal(box.med)}, range ${wholeOrDecimal(box.min)}–${wholeOrDecimal(box.max)}."
}

private fun wholeOrDecimal(value: Double): String {
    val rounded = (value * 2.0).roundToLong()
    return if (rounded % 2L == 0L) (rounded / 2L).toString() else "${rounded / 2L}.5"
}

private const val THOUSAND = 1000L
private const val THOUSAND_DIGITS = 3
private const val VERSION_MINOR_DIGITS = 3
