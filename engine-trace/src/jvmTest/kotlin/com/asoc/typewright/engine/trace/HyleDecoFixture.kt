// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.trace

import com.asoc.typewright.core.font.sfnt.SfntFont
import com.asoc.typewright.core.font.sfnt.readSfntFont
import com.asoc.typewright.core.geometry.Glyph
import java.io.File

/** The repository's Hyle Deco Regular, read once: the source outlines the synthetic photographs and trace-quality tests are drawn from. */
internal object HyleDecoFixture {
    val font: SfntFont by lazy { readSfntFont(File("../fonts/HyleDeco-Regular.ttf").readBytes()) }

    fun glyphForCodePoint(codePoint: Int): Glyph? = font.glyphForCodePoint(codePoint)

    fun glyph(char: Char): Glyph = glyphForCodePoint(char.code) ?: error("Hyle Deco has no glyph for '$char'")
}
