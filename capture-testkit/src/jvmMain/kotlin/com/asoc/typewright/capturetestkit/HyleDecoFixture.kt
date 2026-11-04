// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.capturetestkit

import com.asoc.typewright.core.font.sfnt.SfntFont
import com.asoc.typewright.core.font.sfnt.readSfntFont
import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.scripts.sheets.PrintGlyph

/**
 * The repository's Hyle Deco Regular, read once: the source outlines the synthetic photographs and the trace-quality tests are drawn
 * from. The font file is found by [RepoFiles], so a test may run from any module.
 */
object HyleDecoFixture {
    val font: SfntFont by lazy { readSfntFont(RepoFiles.file("fonts/HyleDeco-Regular.ttf").readBytes()) }

    fun glyphForCodePoint(codePoint: Int): Glyph? = font.glyphForCodePoint(codePoint)

    fun glyph(char: Char): Glyph = glyphForCodePoint(char.code) ?: error("Hyle Deco has no glyph for '$char'")
}

/** What to draw in a cell: the Hyle Deco glyph for the template glyph's code point; nothing for a glyph the font has none for. */
object HyleArt : CellArt {
    override fun contours(
        glyph: PrintGlyph,
        index: Int,
    ): List<Contour> = glyph.codePoint?.let { HyleDecoFixture.glyphForCodePoint(it)?.contours } ?: emptyList()
}
