// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.font.sfnt

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [toUfoProject] against the same embedded `fonts/HyleDeco-Regular.ttf` bytes
 * [HyleDecoCrossCheckTest] uses (see [HyleDecoRegularTtfBase64]'s own KDoc for why the font is
 * embedded rather than read off disk here): real glyph count, one real glyph's contours and
 * advance, and the real `head`/`hhea`/`OS/2`/`name` metrics, cross-checked against fontTools
 * 4.66.0 reading the same file directly.
 */
class SfntFontUfoBridgeTest {
    private val font by lazy { readSfntFont(HyleDecoRegularTtfBase64.bytes) }

    @Test
    fun carriesEveryGlyphInFontOrder() {
        val project = font.toUfoProject()
        assertEquals(font.numGlyphs, project.glyphs.size)
        assertEquals((0 until font.numGlyphs).map { font.glyphName(it) }, project.glyphs.map { it.name })
    }

    @Test
    fun oneGlyphsContoursAndAdvanceMatchTheSfnt() {
        val project = font.toUfoProject()
        val n = project.glyphs.single { it.name == "n" }
        assertEquals(1, n.contours.size, "n contours")
        assertEquals(
            44,
            n.contours
                .single()
                .points
                .count { it.onCurve },
            "n on-curve points",
        )
        assertEquals(
            0,
            n.contours
                .single()
                .points
                .count { !it.onCurve },
            "n off-curve points",
        )
        assertEquals(469, n.advanceWidth, "n advance width")
    }

    @Test
    fun fontInfoComesFromHeadHheaOs2AndName() {
        val info = font.toUfoProject().fontInfo
        assertEquals("Hyle Deco", info.familyName)
        assertEquals("Regular", info.styleName)
        assertEquals(1000, info.unitsPerEm)
        assertEquals(984, info.ascender)
        assertEquals(-292, info.descender)
        assertEquals(500, info.xHeight)
        assertEquals(700, info.capHeight)
    }

    @Test
    fun anExplicitFamilyNameOverridesTheNameTable() {
        val info = font.toUfoProject(familyName = "Golden Path Seed").fontInfo
        assertEquals("Golden Path Seed", info.familyName)
        assertEquals("Regular", info.styleName)
    }
}
