// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.qa.corpus.style

import dev.aarso.typewright.core.geometry.Glyph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ProportionsTest {
    @Test
    fun xHeightToCapHeightRatioIsExactForRectangles() {
        val (x, h) = proportionGlyphs(xHeight = 500, capHeightValue = 700)
        val ratio = assertNotNull(xHeightToCapHeightRatio(x, h))
        assertEquals(500.0 / 700.0, ratio, 1e-9)
    }

    @Test
    fun xHeightToCapHeightRatioIsNullWhenAGlyphIsMissing() {
        val (x, _) = proportionGlyphs(500, 700)
        assertNull(xHeightToCapHeightRatio(x, null))
        assertNull(xHeightToCapHeightRatio(null, x))
    }

    @Test
    fun widthClassIsTheMeanAdvanceOverUnitsPerEm() {
        val glyphs = listOf(Glyph("a", 400, emptyList()), Glyph("b", 600, emptyList()))
        val w = assertNotNull(widthClass(glyphs, unitsPerEm = 1000))
        assertEquals(0.5, w, 1e-9) // mean(400,600)/1000
    }

    @Test
    fun widthClassIsNullForAnEmptySetOrNonPositiveUpm() {
        assertNull(widthClass(emptyList(), 1000))
        assertNull(widthClass(listOf(Glyph("a", 400, emptyList())), 0))
    }
}
