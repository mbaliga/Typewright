// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.polygonContour
import com.asoc.typewright.qa.corpus.FamilyEntry
import com.asoc.typewright.qa.corpus.GlyphDist
import com.asoc.typewright.qa.corpus.NodeEconomyCorpus
import com.asoc.typewright.qa.corpus.NodeEconomyPack
import com.asoc.typewright.qa.corpus.NodeEconomyVerdict
import com.asoc.typewright.qa.corpus.Quartiles
import com.asoc.typewright.qa.corpus.StyleClass
import com.asoc.typewright.ui.project.ProjectStyle
import com.asoc.typewright.ui.project.StyleOrigin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The economy strip's judgment of the open glyph, against a box chosen for the test, not the real corpus. */
class EconomyStripTest {
    private val corpus =
        NodeEconomyCorpus(
            NodeEconomyPack(
                source = "test fixture",
                glyphs = "o",
                styles =
                    mapOf(
                        "test-style" to
                            StyleClass(
                                tags = listOf("/Test"),
                                families =
                                    listOf(
                                        FamilyEntry("Alpha", "alpha.ttf", "quadratic", 90.0, mapOf("o" to listOf(12, 4, 1))),
                                        FamilyEntry("Beta", "beta.ttf", "quadratic", null, mapOf("o" to listOf(16, 8, 1))),
                                        FamilyEntry("Gamma", "gamma.ttf", "quadratic", 70.0, mapOf("o" to listOf(14, 6, 1))),
                                    ),
                                dist =
                                    mapOf(
                                        "o" to
                                            GlyphDist(
                                                on = Quartiles(min = 12.0, q1 = 12.0, med = 14.0, q3 = 16.0, max = 16.0, n = 3),
                                                off = Quartiles(min = 4.0, q1 = 4.0, med = 6.0, q3 = 8.0, max = 8.0, n = 3),
                                            ),
                                    ),
                            ),
                    ),
            ),
        )

    private fun glyphWithNodes(
        name: String,
        nodes: Int,
    ): Glyph {
        val ring = (0 until nodes).map { Point(it * 10, if (it % 2 == 0) 0 else 40) }
        return Glyph(name, 500, listOf(polygonContour(ring, CurveFormat.CUBIC)))
    }

    private val declared = ProjectStyle("test-style", StyleOrigin.DECLARED)

    @Test
    fun aCountInsideTheBoxIsInRangeAndTheStripNamesTheClassAndItsFaces() {
        val strip = assertIs<EconomyStrip.Judged>(EconomyStrip.of(glyphWithNodes("o", 14), declared, corpus))
        assertEquals(NodeEconomyVerdict.IN_RANGE, strip.verdict)
        assertEquals("o · 14 on-curve · ✓ in range", strip.line)
        assertTrue(strip.classLine.startsWith("test-style, n = 3 faces"), strip.classLine)
        assertEquals(14, strip.spec.value)
        assertEquals(12.0, strip.spec.q1)
        assertEquals(16.0, strip.spec.q3)
    }

    @Test
    fun theCountFollowsTheOutlineSoAnEditMovesThePoint() {
        val before = assertIs<EconomyStrip.Judged>(EconomyStrip.of(glyphWithNodes("o", 14), declared, corpus))
        val after = assertIs<EconomyStrip.Judged>(EconomyStrip.of(glyphWithNodes("o", 30), declared, corpus))
        assertEquals(14, before.spec.value)
        assertEquals(30, after.spec.value)
        // The fence is 16 + max(1.5 * 4, 0.25 * 16) = 22, so 30 is an outlier and 18 is above the box but inside the fence.
        assertEquals(NodeEconomyVerdict.OUTLIER, after.verdict)
        assertTrue(after.line.contains("● outlier"), after.line)
        val above = assertIs<EconomyStrip.Judged>(EconomyStrip.of(glyphWithNodes("o", 18), declared, corpus))
        assertEquals(NodeEconomyVerdict.ABOVE, above.verdict)
        assertTrue(above.line.contains("◐ above the middle half"), above.line)
    }

    @Test
    fun aClassTheAppReadOffTheLettersIsCalledOurHeuristicAndAStatedOneIsNot() {
        val read = ProjectStyle("test-style", StyleOrigin.DETECTED)
        val heuristic = assertIs<EconomyStrip.Judged>(EconomyStrip.of(glyphWithNodes("o", 14), read, corpus))
        assertTrue(heuristic.classLine.contains("our heuristic"), heuristic.classLine)
        val stated = assertIs<EconomyStrip.Judged>(EconomyStrip.of(glyphWithNodes("o", 14), declared, corpus))
        assertFalse(stated.classLine.contains("our heuristic"), stated.classLine)
        assertTrue(stated.classLine.contains("declared by you"), stated.classLine)
        val confirmed =
            assertIs<EconomyStrip.Judged>(
                EconomyStrip.of(glyphWithNodes("o", 14), ProjectStyle("test-style", StyleOrigin.CONFIRMED), corpus),
            )
        assertTrue(confirmed.classLine.contains("confirmed by you"))
    }

    @Test
    fun whenThereIsNothingToJudgeTheStripSaysWhyAndShowsNoNumber() {
        assertTrue(
            assertIs<EconomyStrip.Unjudged>(EconomyStrip.of(Glyph("o", 500, emptyList()), declared, corpus)).line.contains("nothing drawn"),
        )
        assertTrue(assertIs<EconomyStrip.Unjudged>(EconomyStrip.of(glyphWithNodes("o", 14), declared, null)).line.contains("cannot read"))
        assertTrue(assertIs<EconomyStrip.Unjudged>(EconomyStrip.of(glyphWithNodes("o", 14), null, corpus)).line.contains("style class"))
        val noBox = assertIs<EconomyStrip.Unjudged>(EconomyStrip.of(glyphWithNodes("q", 14), declared, corpus))
        assertTrue(noBox.line.contains("No measured box for q"), noBox.line)
        val unknownClass =
            assertIs<EconomyStrip.Unjudged>(EconomyStrip.of(glyphWithNodes("o", 14), ProjectStyle("nope", StyleOrigin.DECLARED), corpus))
        assertTrue(unknownClass.line.contains("No measured box"))
    }
}
