// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import com.asoc.typewright.core.geometry.Glyph

/** A glyph as navigation sees it: its name and the code points it is mapped from (the first is primary). */
data class GlyphEntry(
    val name: String,
    val unicodes: List<Int>,
) {
    /** The character this glyph is, as text ("o"), or null for a glyph with no code point. */
    val character: String? get() = unicodes.firstOrNull()?.let(::codePointToString)

    /** "U+006F", the primary code point as the header writes it, or null. */
    val unicodeLabel: String? get() = unicodes.firstOrNull()?.let { "U+" + it.toString(16).uppercase().padStart(4, '0') }
}

/** Why a glyph matched a search, best first. */
enum class MatchKind { EXACT_NAME, CHARACTER, CODE_POINT, NAME_PREFIX, NAME_CONTAINS }

/** One search result. */
data class GlyphMatch(
    val entry: GlyphEntry,
    val kind: MatchKind,
)

/**
 * The glyphs of the open project in the order the font keeps them, and the three questions Draw asks
 * of that list: which glyph to open when none is chosen, which comes next or before, and which match
 * what a person typed (a name, a character, or a code point). Pure, so every rule is a test.
 */
class GlyphDirectory(
    val glyphs: List<GlyphEntry>,
) {
    fun entry(name: String?): GlyphEntry? = glyphs.firstOrNull { it.name == name }

    /**
     * The glyph to open when none is chosen: the first of [drawingOrder] (code points, in the order the
     * brief's plan asks the person to draw them) that the project has, otherwise the first glyph in
     * the font, otherwise null for a project with no glyphs.
     */
    fun defaultGlyph(drawingOrder: List<Int>): String? {
        for (codePoint in drawingOrder) glyphs.firstOrNull { codePoint in it.unicodes }?.let { return it.name }
        return glyphs.firstOrNull()?.name
    }

    /**
     * The glyph [delta] places after [current] in font order, wrapping at the ends; the first glyph
     * when [current] is null or not in the font, and null for an empty project.
     */
    fun step(
        current: String?,
        delta: Int,
    ): String? {
        if (glyphs.isEmpty()) return null
        val at = glyphs.indexOfFirst { it.name == current }
        if (at < 0) return glyphs.first().name
        return glyphs[((at + delta) % glyphs.size + glyphs.size) % glyphs.size].name
    }

    /**
     * Glyphs matching [query], best match first, at most [limit]. A name matches whole, by prefix or by
     * containing the text (case does not matter, except that an exact-case name ranks above a case
     * variant); one character matches the glyph mapped from it; `U+006F`, `u+6f` or `0x6F` match by code
     * point. An empty query matches nothing: the caller shows the whole list itself.
     */
    fun search(
        query: String,
        limit: Int = DEFAULT_LIMIT,
    ): List<GlyphMatch> {
        val text = query.trim()
        if (text.isEmpty()) return emptyList()
        val codePoint = singleCodePoint(text)
        val hex = parseCodePoint(text)
        val lower = text.lowercase()
        val matches = mutableListOf<Pair<Int, GlyphMatch>>()
        for ((order, entry) in glyphs.withIndex()) {
            val name = entry.name
            val lowerName = name.lowercase()
            val kind =
                when {
                    name == text -> MatchKind.EXACT_NAME
                    codePoint != null && codePoint in entry.unicodes -> MatchKind.CHARACTER
                    hex != null && hex in entry.unicodes -> MatchKind.CODE_POINT
                    lowerName == lower -> MatchKind.EXACT_NAME
                    lowerName.startsWith(lower) -> MatchKind.NAME_PREFIX
                    lowerName.contains(lower) -> MatchKind.NAME_CONTAINS
                    else -> null
                }
            if (kind != null) matches += order to GlyphMatch(entry, kind)
        }
        return matches
            .sortedWith(compareBy({ it.second.kind.ordinal }, { it.second.entry.name != text }, { it.first }))
            .map { it.second }
            .take(limit)
    }

    companion object {
        const val DEFAULT_LIMIT = 40

        /** The directory of [glyphs], in the order given (the font's own glyph order). */
        fun of(glyphs: List<Glyph>): GlyphDirectory = GlyphDirectory(glyphs.map { GlyphEntry(it.name, it.unicodes) })

        /** The code point [text] is, when it is exactly one character (a surrogate pair counts as one), else null. */
        fun singleCodePoint(text: String): Int? =
            when {
                text.length == 1 && !text[0].isSurrogate() -> {
                    text[0].code
                }

                text.length == 2 && text[0].isHighSurrogate() && text[1].isLowSurrogate() -> {
                    ((text[0].code - HIGH_SURROGATE_START) shl 10) + (text[1].code - LOW_SURROGATE_START) + SUPPLEMENTARY_START
                }

                else -> {
                    null
                }
            }

        /** The code point written as `U+0041`, `u+41` or `0x41`, else null. */
        fun parseCodePoint(text: String): Int? {
            val lower = text.lowercase()
            val digits =
                when {
                    lower.startsWith("u+") -> lower.drop(2)
                    lower.startsWith("0x") -> lower.drop(2)
                    else -> return null
                }
            if (digits.isEmpty() || digits.length > MAX_HEX_DIGITS) return null
            val value = digits.toIntOrNull(HEX_RADIX) ?: return null
            return value.takeIf { it in 0..MAX_CODE_POINT }
        }

        private const val HEX_RADIX = 16
        private const val MAX_HEX_DIGITS = 6
        private const val MAX_CODE_POINT = 0x10FFFF
        private const val HIGH_SURROGATE_START = 0xD800
        private const val LOW_SURROGATE_START = 0xDC00
        private const val SUPPLEMENTARY_START = 0x10000
    }
}

/** [codePoint] as text, building the surrogate pair by hand because common Kotlin has no `appendCodePoint`. */
fun codePointToString(codePoint: Int): String =
    if (codePoint < 0x10000) {
        codePoint.toChar().toString()
    } else {
        val offset = codePoint - 0x10000
        val high = (0xD800 + (offset shr 10)).toChar()
        val low = (0xDC00 + (offset and 0x3FF)).toChar()
        "$high$low"
    }
