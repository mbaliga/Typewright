// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import com.asoc.typewright.core.font.ufo.PlistValue
import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.sidebearings

/** Which horizontal metric a [MetricLine] is. */
enum class MetricKind(
    val label: String,
) {
    ASCENDER("ascender"),
    CAP_HEIGHT("cap height"),
    X_HEIGHT("x-height"),
    BASELINE("baseline"),
    DESCENDER("descender"),
}

/** One horizontal metric line: its [kind] and its height in font units. */
data class MetricLine(
    val kind: MetricKind,
    val y: Int,
) {
    /** The line's label as the sheet writes it: the name, then the value ("x-height 500"). */
    val text: String get() = "${kind.label} $y"
}

/** A band of heights the font allows its round letters to overshoot into (PostScript blue zones), in font units. */
data class OvershootZone(
    val bottom: Int,
    val top: Int,
)

/**
 * What the sheet draws behind a glyph from the font's own info: the horizontal metric lines the font
 * names (a font that does not give an x-height gets no x-height line; none is invented) and any
 * overshoot zones it declares. The vertical lines (where the glyph starts and where its advance ends)
 * belong to the glyph and come from [GlyphLines].
 */
data class DrawMetrics(
    val unitsPerEm: Int,
    val lines: List<MetricLine>,
    val zones: List<OvershootZone>,
) {
    /** The lowest and highest heights worth keeping in view: the descender and the taller of the cap height and ascender. */
    val span: Pair<Int, Int>
        get() = (lines.minOfOrNull { it.y } ?: 0) to (lines.maxOfOrNull { it.y } ?: unitsPerEm)

    /** The metric line within [withinUnits] of [y], nearest first, or null. */
    fun nearestLine(
        y: Double,
        withinUnits: Double,
    ): MetricLine? =
        lines
            .map { it to kotlin.math.abs(it.y - y) }
            .filter { it.second <= withinUnits }
            .minByOrNull { it.second }
            ?.first

    companion object {
        /** The metrics a font with no declared info still has: a baseline, on a 1000-unit em. */
        val NONE = DrawMetrics(1000, listOf(MetricLine(MetricKind.BASELINE, 0)), emptyList())
    }
}

/** [info]'s metric lines and overshoot zones. The baseline is always there; every other line only if the font says so. */
fun drawMetricsFor(info: UfoFontInfo): DrawMetrics {
    val lines =
        buildList {
            info.ascender?.let { add(MetricLine(MetricKind.ASCENDER, it)) }
            info.capHeight?.let { add(MetricLine(MetricKind.CAP_HEIGHT, it)) }
            info.xHeight?.let { add(MetricLine(MetricKind.X_HEIGHT, it)) }
            add(MetricLine(MetricKind.BASELINE, 0))
            info.descender?.let { add(MetricLine(MetricKind.DESCENDER, it)) }
        }
    return DrawMetrics(info.unitsPerEm ?: 1000, lines.sortedByDescending { it.y }, overshootZones(info))
}

private val ZONE_KEYS = listOf("postscriptBlueValues", "postscriptOtherBlues")

private fun overshootZones(info: UfoFontInfo): List<OvershootZone> =
    ZONE_KEYS.flatMap { key ->
        val array = info.other.lastOrNull { it.first == key }?.second as? PlistValue.PArray ?: return@flatMap emptyList()
        val numbers = array.items.mapNotNull { (it as? PlistValue.PInteger)?.value?.toInt() ?: (it as? PlistValue.PReal)?.value?.toInt() }
        numbers.chunked(2).filter { it.size == 2 }.map { (a, b) -> OvershootZone(minOf(a, b), maxOf(a, b)) }
    }

/** The glyph's own vertical lines: where its advance ends, and the sidebearings measured by [sidebearings] (null for a glyph with no ink). */
data class GlyphLines(
    val advance: Int,
    val lsb: Int?,
    val rsb: Int?,
) {
    companion object {
        fun of(glyph: Glyph): GlyphLines {
            val sb = glyph.sidebearings()
            return GlyphLines(glyph.advanceWidth, sb?.left, sb?.right)
        }
    }
}
