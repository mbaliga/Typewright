// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.qa.corpus.style

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApertureTest {
    @Test
    fun narrowGapReadsSmallerThanAWideGap() {
        val narrow = straightCutCGlyph(gapHalfAngleDegrees = 6.0)
        val wide = straightCutCGlyph(gapHalfAngleDegrees = 24.0)
        val xHeight = 500.0
        val narrowOpenness = assertNotNull(apertureOpenness(narrow, xHeight))
        val wideOpenness = assertNotNull(apertureOpenness(wide, xHeight))
        assertTrue(narrowOpenness < wideOpenness, "narrow gap ($narrowOpenness) should read smaller than wide gap ($wideOpenness)")
    }

    @Test
    fun opennessScalesWithXHeight() {
        val c = straightCutCGlyph()
        val small = assertNotNull(apertureOpenness(c, 250.0))
        val large = assertNotNull(apertureOpenness(c, 1000.0))
        assertTrue(small > large, "the same physical gap should read as more open relative to a smaller x-height")
    }

    @Test
    fun nonPositiveXHeightIsNull() {
        assertNull(apertureOpenness(straightCutCGlyph(), 0.0))
    }

    @Test
    fun contourlessGlyphIsNull() {
        val empty =
            dev.aarso.typewright.core.geometry
                .Glyph("c", 500, emptyList())
        assertNull(apertureOpenness(empty, 500.0))
    }
}
