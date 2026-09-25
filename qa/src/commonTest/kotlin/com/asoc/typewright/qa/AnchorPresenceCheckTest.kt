// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa

import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.core.font.ufo.UfoProject
import com.asoc.typewright.core.geometry.Anchor
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnchorPresenceCheckTest {
    @Test
    fun aBaseLetterWithATopAnchorPasses() {
        val project =
            UfoProject(UfoFontInfo(), listOf(Glyph("A", 500, emptyList(), anchors = listOf(Anchor("top", Point(250, 700))))))
        val findings = checkAnchorsPresent(project)
        assertEquals(1, findings.size)
        assertTrue(findings.single().hasAppropriateAnchor)
    }

    @Test
    fun aBaseLetterWithNoAnchorIsAGapReportedAsInformational() {
        val project = UfoProject(UfoFontInfo(), listOf(Glyph("A", 500, emptyList())))
        val findings = checkAnchorsPresent(project)
        assertEquals(1, findings.size)
        assertTrue(!findings.single().hasAppropriateAnchor)
        assertTrue(findings.single().message.contains("no mark-attachment anchor"))
    }

    @Test
    fun aBaseLetterWithOnlyAMarkAnchorDoesNotCount() {
        // "_top" is a MARK's own attachment point, not a base letter's -- a base letter that only
        // has one is still a gap.
        val project =
            UfoProject(UfoFontInfo(), listOf(Glyph("A", 500, emptyList(), anchors = listOf(Anchor("_top", Point(250, 0))))))
        val findings = checkAnchorsPresent(project)
        assertTrue(!findings.single().hasAppropriateAnchor)
    }

    @Test
    fun glyphsOutsideTheAllowListAreNotReportedAtAll() {
        val project = UfoProject(UfoFontInfo(), listOf(Glyph("zero", 500, emptyList()), Glyph("space", 500, emptyList())))
        assertEquals(emptyList(), checkAnchorsPresent(project))
    }

    @Test
    fun theAllowListIsExactlyTheFiftyTwoBaseLatinLetters() {
        assertEquals(52, BASE_LATIN_LETTERS_EXPECTING_ANCHORS.size)
        assertTrue("A" in BASE_LATIN_LETTERS_EXPECTING_ANCHORS)
        assertTrue("z" in BASE_LATIN_LETTERS_EXPECTING_ANCHORS)
        assertTrue("zero" !in BASE_LATIN_LETTERS_EXPECTING_ANCHORS)
    }
}
