// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.project

import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.core.font.ufo.UfoKerning
import com.asoc.typewright.core.font.ufo.UfoProject
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.closePolylineToContour
import com.asoc.typewright.project.Master
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [ProjectFont] on a hand-built font, so every advance and kerning value asserted is one the test wrote. */
class ProjectFontTest {
    private fun box(
        name: String,
        char: Char?,
        advance: Int,
    ): Glyph =
        Glyph(
            name = name,
            advanceWidth = advance,
            contours =
                listOf(
                    closePolylineToContour(listOf(Point(0, 0), Point(advance - 100, 0), Point(advance - 100, 500), Point(0, 500))),
                ),
            unicodes = listOfNotNull(char?.code),
        )

    private fun font(kerning: UfoKerning = UfoKerning()): ProjectFont {
        val glyphs =
            listOf(
                box("A", 'A', 600),
                box("V", 'V', 640),
                box("B", 'B', 580),
                box("a", 'a', 500),
                Glyph("space", 250, emptyList(), unicodes = listOf(0x20)),
            )
        val ufo =
            UfoProject(
                UfoFontInfo(familyName = "Test", unitsPerEm = 1000, openTypeHheaAscender = 800, openTypeHheaDescender = -200),
                glyphs,
                kerning,
            )
        return ProjectFont(Master("regular", "Regular.ufo", "Regular", isDefault = true, ufo = ufo))
    }

    @Test
    fun aLineIsSetWithTheProjectsOwnAdvancesAndSpace() {
        val line = font().setLine("AB A")
        assertEquals(listOf("A", "B", "A"), line.glyphs.map { it.glyph.name })
        // A 600, B 580, space 250, A: pens at 0, 600, 1250.
        assertEquals(listOf(0.0, 600.0, 1430.0), line.glyphs.map { it.x })
        assertEquals(2030.0, line.width)
    }

    @Test
    fun kerningFollowsTheUfoLookupOrderPairBeforeGroupBeforeGroupPair() {
        val kerning =
            UfoKerning(
                groups =
                    mapOf(
                        "public.kern1.A" to listOf("A"),
                        "public.kern2.V" to listOf("V"),
                    ),
                kerning =
                    mapOf(
                        "A" to mapOf("V" to -80.0, "public.kern2.V" to -50.0),
                        "public.kern1.A" to mapOf("V" to -30.0, "public.kern2.V" to -20.0, "B" to -10.0),
                    ),
            )
        val f = font(kerning)
        val a = f.glyph("A")!!
        val v = f.glyph("V")!!
        val b = f.glyph("B")!!
        assertEquals(-80.0, f.kerning(a, v), "the glyph pair wins over every group")
        assertEquals(-10.0, f.kerning(a, b), "first-side group with the second glyph")
        assertEquals(0.0, f.kerning(b, a), "no entry, no kerning")
        assertEquals(-80.0 + 600.0, f.setLine("AV").glyphs[1].x, "the second glyph moves by the advance plus the kerning")
    }

    @Test
    fun aGroupPairIsUsedWhenNothingMoreSpecificExists() {
        val kerning =
            UfoKerning(
                groups = mapOf("public.kern1.A" to listOf("A"), "public.kern2.V" to listOf("V")),
                kerning = mapOf("public.kern1.A" to mapOf("public.kern2.V" to -20.0)),
            )
        val f = font(kerning)
        assertEquals(-20.0, f.kerning(f.glyph("A")!!, f.glyph("V")!!))
    }

    @Test
    fun aCharacterTheProjectDoesNotHaveIsSkippedNotReplaced() {
        val f = font()
        assertFalse(f.canSet("AZ"))
        assertTrue(f.canSet("A B"))
        assertEquals(listOf("A"), f.setLine("AZ").glyphs.map { it.glyph.name })
    }

    @Test
    fun theShowWordIsThePreferredOneOnlyIfTheFontCanSetItElseItsOwnFirstLetters() {
        val f = font()
        assertEquals("AB", f.showWord("AB"))
        // 'Hamburg' cannot be set: the font's own letters (by code point, with an outline) stand in.
        assertEquals("ABVa", f.showWord("Hamburg"))
        assertEquals("AB", f.showWord("Hamburg", fallbackLength = 2))
    }

    @Test
    fun wrappingBreaksAtWordsAndKeepsAnOverlongWordWhole() {
        val f = font()
        // "AAA BBB" is 3 x 600 + 250 + 3 x 580 = 3790 wide.
        assertEquals(listOf(3790.0, 1800.0), f.setLines("AAA BBB AAA", maxWidthUnits = 4000.0).map { it.width })
        assertEquals(listOf(1800.0, 1740.0, 1800.0), f.setLines("AAA BBB AAA", maxWidthUnits = 2400.0).map { it.width })
        assertEquals(1, f.setLines("AAAAAAAAAA", maxWidthUnits = 100.0).size, "a word wider than the line is not broken")
    }

    @Test
    fun metricsComeFromTheFontInfoAndFallBackOnlyForLayout() {
        val f = font()
        assertEquals(800, f.ascent)
        assertEquals(200, f.descent)
        assertEquals(1000, f.unitsPerEm)
        val bare =
            ProjectFont(Master("r", "R.ufo", "Regular", true, UfoProject(UfoFontInfo(), emptyList())))
        assertEquals(1000, bare.unitsPerEm)
        assertEquals(800, bare.ascent)
        assertEquals(200, bare.descent)
        assertNull(bare.showWord("Hamburg"), "a font with no letters has no word to show")
    }
}
