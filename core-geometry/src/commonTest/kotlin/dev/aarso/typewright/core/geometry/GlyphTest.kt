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
}
