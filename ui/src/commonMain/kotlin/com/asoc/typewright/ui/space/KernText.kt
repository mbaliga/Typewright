// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.space

import com.asoc.typewright.project.KernKey
import com.asoc.typewright.project.KernResolution
import com.asoc.typewright.project.KernSide
import com.asoc.typewright.project.KernSource
import com.asoc.typewright.project.KerningGroups
import com.asoc.typewright.project.KerningTable
import kotlin.math.round
import kotlin.math.roundToLong

/** Whether [key] names a kerning group on either side, so one value stands for several glyph pairs. */
fun KerningTable.isGroupLevel(key: KernKey): Boolean =
    KerningGroups.sideOf(key.first) == KernSide.FIRST || KerningGroups.sideOf(key.second) == KernSide.SECOND

/**
 * The entry [key] in words a person can check against the groups list: "group pair O – E (6 glyph pairs)",
 * "T with group o (2 glyph pairs)", "group O with V (3 glyph pairs)", or "this pair" for two glyphs.
 */
fun KerningTable.describe(key: KernKey): String {
    val first = KerningGroups.displayName(key.first)
    val second = KerningGroups.displayName(key.second)
    val firstGroup = KerningGroups.sideOf(key.first) == KernSide.FIRST
    val secondGroup = KerningGroups.sideOf(key.second) == KernSide.SECOND
    if (!firstGroup && !secondGroup) return "this pair"
    val count = coverage(key)
    val pairs = "$count glyph ${if (count == 1) "pair" else "pairs"}"
    return when {
        firstGroup && secondGroup -> "group pair $first – $second ($pairs)"
        secondGroup -> "$first with group $second ($pairs)"
        else -> "group $first with $second ($pairs)"
    }
}

/**
 * A spacing or kerning value as a person reads it: a whole number without a decimal point, anything else with
 * up to two decimals ("-12.5"). Stored kerning may be fractional, and the page must not show it as a different number.
 */
fun formatUnits(value: Double): String {
    val rounded = round(value * HUNDRED) / HUNDRED
    val whole = rounded.roundToLong()
    return if (rounded == whole.toDouble()) whole.toString() else rounded.toString()
}

private const val HUNDRED = 100.0

/** What a junction's kerning reads as in a word: "pair" for an exception, "group" where the groups give the value, "none" where nothing kerns it. */
fun KernResolution.sourceWord(): String =
    when (source) {
        KernSource.PAIR -> "pair"
        KernSource.GLYPH_GROUP, KernSource.GROUP_GLYPH, KernSource.GROUP_GROUP -> "group"
        KernSource.NONE -> "none"
    }
