// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.project

import com.asoc.typewright.core.font.ufo.UfoProject
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.project.KerningTable
import com.asoc.typewright.project.Master

/** One glyph placed in a run of text: [x] is its pen position in font units, kerning already applied. */
data class SetGlyph(
    val glyph: Glyph,
    val x: Double,
)

/** A line of [glyphs] and its [width] in font units. */
data class SetLine(
    val glyphs: List<SetGlyph>,
    val width: Double,
)

/**
 * The open project's default master as the screens read it: its glyphs by name and by character,
 * the metrics a line of text needs, and a way to set a string in the project's own outlines (with the
 * project's own kerning, when it has any). Everything the specimen, the Glyphs grid and Font info
 * draw comes from here, never from a bundled font.
 *
 * A character the project has no glyph for is skipped, not replaced: the specimen shows what the
 * font really holds. [canSet] says whether a whole string can be set, so a caller can choose another
 * string rather than show a gap.
 */
class ProjectFont(
    val master: Master,
) {
    val ufo: UfoProject get() = master.ufo
    val styleName: String get() = master.styleName
    val glyphs: List<Glyph> get() = ufo.glyphs

    /** Units per em, from `fontinfo.plist`; 1000 where the font states none (the UFO default). */
    val unitsPerEm: Int = ufo.fontInfo.unitsPerEm?.takeIf { it > 0 } ?: DEFAULT_UNITS_PER_EM

    /** The line's ascent in font units: the hhea ascender, then the UFO ascender, then 80% of the em (layout only; never shown as a metric). */
    val ascent: Int = ufo.fontInfo.openTypeHheaAscender ?: ufo.fontInfo.ascender ?: (unitsPerEm * FALLBACK_ASCENT).toInt()

    /** The line's descent in font units, as a positive number. */
    val descent: Int = -(ufo.fontInfo.openTypeHheaDescender ?: ufo.fontInfo.descender ?: -(unitsPerEm * FALLBACK_DESCENT).toInt())

    private val byName: Map<String, Glyph> = glyphs.associateBy { it.name }
    private val byCodePoint: Map<Int, Glyph> =
        buildMap {
            for (glyph in glyphs) for (codePoint in glyph.unicodes) if (codePoint !in this) put(codePoint, glyph)
        }

    /** The project's kerning read by the UFO lookup order, with where each value comes from; the one reading the specimen and Space share. */
    val kerningTable: KerningTable = KerningTable(ufo.kerningInfo)

    /** The glyph named [name], or null. */
    fun glyph(name: String): Glyph? = byName[name]

    /** The glyph mapped from [codePoint], or null. */
    fun glyphFor(codePoint: Int): Glyph? = byCodePoint[codePoint]

    /** The glyph mapped from [char], or null. */
    fun glyphFor(char: Char): Glyph? = byCodePoint[char.code]

    /** How many distinct characters the project maps to a glyph. */
    val mappedCharacters: Int get() = byCodePoint.size

    /** Whether every non-space character of [text] has a glyph here. */
    fun canSet(text: String): Boolean = text.all { it == ' ' || glyphFor(it) != null }

    /**
     * The kerning between [first] and [second], in font units, by the UFO lookup order: the glyph
     * pair, then the glyph with the second glyph's `public.kern2.*` group, then the first glyph's
     * `public.kern1.*` group with the glyph, then the two groups. Zero where the project has none.
     */
    fun kerning(
        first: Glyph,
        second: Glyph,
    ): Double = kerningTable.value(first.name, second.name)

    /** [text] set on one line in the project's outlines, advances and kerning applied; characters with no glyph are skipped. */
    fun setLine(text: String): SetLine {
        val placed = ArrayList<SetGlyph>(text.length)
        var pen = 0.0
        var previous: Glyph? = null
        for (char in text) {
            if (char == ' ') {
                pen += spaceAdvance()
                previous = null
                continue
            }
            val glyph = glyphFor(char) ?: continue
            if (previous != null) pen += kerning(previous, glyph)
            placed += SetGlyph(glyph, pen)
            pen += glyph.advanceWidth
            previous = glyph
        }
        return SetLine(placed, pen)
    }

    /** [text] broken into lines no wider than [maxWidthUnits] (font units) at word boundaries; a word wider than the line stays whole. */
    fun setLines(
        text: String,
        maxWidthUnits: Double,
    ): List<SetLine> {
        val lines = ArrayList<SetLine>()
        var current = ""
        for (word in text.split(' ').filter { it.isNotEmpty() }) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (current.isNotEmpty() && setLine(candidate).width > maxWidthUnits) {
                lines += setLine(current)
                current = word
            } else {
                current = candidate
            }
        }
        if (current.isNotEmpty()) lines += setLine(current)
        return lines
    }

    /** The width of a space in font units: the project's `space` glyph, else the glyph mapped from U+0020, else a quarter of the em. */
    fun spaceAdvance(): Double = (byName["space"] ?: byCodePoint[SPACE])?.advanceWidth?.toDouble() ?: (unitsPerEm * FALLBACK_SPACE)

    /** A word to show the font off with: [preferred] when the font can set it, else the first [fallbackLength] letters it maps, else null. */
    fun showWord(
        preferred: String,
        fallbackLength: Int = DEFAULT_FALLBACK_WORD,
    ): String? {
        if (canSet(preferred)) return preferred
        val letters = byCodePoint.keys.sorted().filter { it in (SPACE + 1)..0xFFFF && glyphFor(it)?.contours?.isNotEmpty() == true }
        return letters
            .take(fallbackLength)
            .joinToString("") { it.toChar().toString() }
            .takeIf { it.isNotEmpty() }
    }

    companion object {
        private var last: ProjectFont? = null

        /**
         * A [ProjectFont] over [master], reusing the last one built for the same master so a screen that asks
         * on every frame does not rebuild its tables. The font holds its master, so one read of one field is all
         * the cache is, and it can be asked from any thread.
         */
        fun of(master: Master): ProjectFont {
            val cached = last
            if (cached != null && cached.master === master) return cached
            return ProjectFont(master).also { last = it }
        }

        const val DEFAULT_UNITS_PER_EM = 1000
        private const val FALLBACK_ASCENT = 0.8
        private const val FALLBACK_DESCENT = 0.2
        private const val FALLBACK_SPACE = 0.25
        private const val SPACE = 0x20
        private const val DEFAULT_FALLBACK_WORD = 12
    }
}
