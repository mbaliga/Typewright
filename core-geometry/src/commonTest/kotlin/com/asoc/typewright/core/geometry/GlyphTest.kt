// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GlyphTest {
    @Test
    fun anchorsDefaultToEmptyForAGlyphBuiltWithoutThem() {
        val glyph = Glyph("A", 500, emptyList())
        assertEquals(emptyList(), glyph.anchors)
    }

    @Test
    fun aGlyphCanCarryItsOwnAnchors() {
        val top = Anchor("top", Point(250, 700))
        val glyph = Glyph("A", 500, emptyList(), anchors = listOf(top))
        assertEquals(listOf(top), glyph.anchors)
    }

    @Test
    fun twoAnchorsAtTheSamePointWithDifferentNamesAreNotEqual() {
        val top = Anchor("top", Point(250, 700))
        val topAlt = Anchor("_top", Point(250, 700))
        assertEquals(false, top == topAlt)
    }

    @Test
    fun guidelinesDefaultToEmptyForAGlyphBuiltWithoutThem() {
        val glyph = Glyph("A", 500, emptyList())
        assertEquals(emptyList(), glyph.guidelines)
    }

    @Test
    fun aGlyphCanCarryItsOwnGuidelines() {
        val overshoot = Guideline(y = -12.0, name = "overshoot")
        val glyph = Glyph("o", 500, emptyList(), guidelines = listOf(overshoot))
        assertEquals(listOf(overshoot), glyph.guidelines)
    }

    @Test
    fun unicodesDefaultToEmptyForAGlyphBuiltWithoutThem() {
        val glyph = Glyph("A", 500, emptyList())
        assertEquals(emptyList(), glyph.unicodes)
    }

    @Test
    fun unicodesKeepTheirOrderSoTheFirstStaysPrimary() {
        val glyph = Glyph("space", 250, emptyList(), unicodes = listOf(0xA0, 0x20))
        assertEquals(listOf(0xA0, 0x20), glyph.unicodes)
        assertEquals(0xA0, glyph.unicodes.first())
    }

    @Test
    fun copyCarriesUnicodesAlong() {
        val glyph = Glyph("A", 500, emptyList(), unicodes = listOf(0x41))
        assertEquals(listOf(0x41), glyph.copy(advanceWidth = 600).unicodes)
    }

    @Test
    fun acceptsTheEdgesOfUnicodesCodeSpace() {
        val glyph = Glyph("edges", 0, emptyList(), unicodes = listOf(0, 0x10FFFF))
        assertEquals(listOf(0, 0x10FFFF), glyph.unicodes)
    }

    @Test
    fun rejectsAUnicodeAboveTheCodeSpace() {
        assertFailsWith<IllegalArgumentException> { Glyph("x", 0, emptyList(), unicodes = listOf(0x110000)) }
    }

    @Test
    fun rejectsANegativeUnicode() {
        assertFailsWith<IllegalArgumentException> { Glyph("x", 0, emptyList(), unicodes = listOf(-1)) }
    }

    @Test
    fun rejectsARepeatedUnicode() {
        assertFailsWith<IllegalArgumentException> { Glyph("A", 500, emptyList(), unicodes = listOf(0x41, 0x61, 0x41)) }
    }
}
