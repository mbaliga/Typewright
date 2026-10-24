// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.space

import com.asoc.typewright.core.font.ufo.UfoKerning
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [SpaceDiff]: what differs between the Before and the font now, so the page can say so in one line. */
class SpaceDiffTest {
    private val glyphs = listOf(SpaceFixtures.glyph("n", 'n', 500, 46, 449), SpaceFixtures.glyph("o", 'o', 520, 47, 472))
    private val before = SpaceFixtures.ufo(glyphs, UfoKerning(kerning = mapOf("n" to mapOf("o" to -12.0))))

    @Test
    fun theSameFontDiffersInNothing() {
        val diff = SpaceDiff.of(before, before)
        assertTrue(diff.isEmpty)
        assertNull(diff.summary())
    }

    @Test
    fun aMovedSidebearingOrAnotherAdvanceMakesAGlyphCountAsSpacedDifferently() {
        val after =
            SpaceFixtures.ufo(
                listOf(SpaceFixtures.glyph("n", 'n', 500, 40, 449), SpaceFixtures.glyph("o", 'o', 540, 47, 472)),
                before.kerningInfo,
            )
        val diff = SpaceDiff.of(before, after)
        assertEquals(listOf("n", "o"), diff.spacingChanged)
        assertEquals("2 glyphs with new sidebearings or advance", diff.summary())
    }

    @Test
    fun kerningEntriesAddedRemovedAndChangedAreCountedOnce() {
        val after =
            SpaceFixtures.ufo(
                glyphs,
                UfoKerning(kerning = mapOf("n" to mapOf("o" to -20.0, "n" to -4.0), "o" to mapOf("n" to 0.0))),
            )
        val diff = SpaceDiff.of(before, after)
        assertEquals(3, diff.kerningEntries, "n o changed, n n added, o n added")
        assertEquals("3 kerning entries", diff.summary())
        assertEquals(1, SpaceDiff.of(before, SpaceFixtures.ufo(glyphs)).kerningEntries, "n o removed")
    }

    @Test
    fun groupsAndFeaturesAreCountedToo() {
        val after =
            SpaceFixtures.ufo(
                glyphs,
                before.kerningInfo.copy(groups = mapOf("public.kern1.round" to listOf("o")), features = "feature kern {\n} kern;\n"),
            )
        val diff = SpaceDiff.of(before, after)
        assertEquals(1, diff.groups)
        assertTrue(diff.featuresChanged)
        assertEquals("1 group, features.fea", diff.summary())
    }
}
