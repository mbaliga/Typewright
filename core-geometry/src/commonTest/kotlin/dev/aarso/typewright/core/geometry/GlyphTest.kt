package dev.aarso.typewright.core.geometry

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
