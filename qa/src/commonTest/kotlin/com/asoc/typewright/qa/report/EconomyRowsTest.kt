// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.report

import com.asoc.typewright.qa.checkNodeEconomy
import com.asoc.typewright.qa.corpus.NodeEconomyVerdict
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EconomyRowsTest {
    private val fx = ReportFixtures

    private fun report(vararg counts: Pair<String, Int>) =
        checkNodeEconomy(
            fx.project(counts.map { (name, n) -> fx.glyph(name, fx.tracedRectangle(n), unicodes = listOf(name.first().code)) }),
            fx.STYLE_KEY,
            fx.corpus,
        )

    @Test
    fun worstFirstIsTheLogDistanceAboveTheTopOfTheBox() {
        // T: 400 / 12.5 = 32x; o: 400 / 24 = 16.7x; H: 400 / 18 = 22x; e: 8 is inside its box.
        val rows = economyRows(report("o" to 400, "T" to 400, "H" to 400, "e" to 8), EconomySort.WORST_FIRST)
        assertEquals(listOf("T", "H", "o", "e"), rows.map { it.glyph })
        assertTrue(rows.zipWithNext().all { (a, b) -> a.logAboveBox >= b.logAboveBox })
    }

    @Test
    fun anInsideCountIsBelowZeroOnTheLogScale() {
        val inside = economyRows(report("T" to 8), EconomySort.WORST_FIRST).single()
        assertTrue(inside.logAboveBox < 0.0)
        assertEquals(NodeEconomyVerdict.IN_RANGE, inside.verdict)
    }

    @Test
    fun alphabeticalPutsACapitalBeforeItsLowerCaseTwinAndIgnoresCountsEntirely() {
        val rows = economyRows(report("o" to 400, "T" to 8, "H" to 400, "e" to 8), EconomySort.ALPHABETICAL)
        assertEquals(listOf("e", "H", "o", "T"), rows.map { it.glyph })
    }

    @Test
    fun equalDistancesFallBackToTheNameSoTheOrderNeverWobbles() {
        val a = economyRows(report("e" to 8, "n" to 8), EconomySort.WORST_FIRST).map { it.glyph }
        val b = economyRows(report("n" to 8, "e" to 8), EconomySort.WORST_FIRST).map { it.glyph }
        assertEquals(a, b)
    }

    @Test
    fun aGlyphTheCorpusHasNoBoxForHasNoRow() {
        val rows = economyRows(report("T" to 8, "Z" to 8), EconomySort.ALPHABETICAL)
        assertEquals(listOf("T"), rows.map { it.glyph })
    }

    @Test
    fun theRowCarriesTheBoxTheVerdictAndTheFenceThatDecidedIt() {
        val row = economyRows(report("T" to 400), EconomySort.WORST_FIRST).single()
        assertEquals(400, row.count)
        assertEquals(12.5, row.box.q3)
        assertEquals(NodeEconomyVerdict.OUTLIER, row.verdict)
        // 12.5 + max(1.5 * (12.5 - 8), 0.25 * 12.5) = 12.5 + 6.75
        assertEquals(19.25, row.fence)
    }

    @Test
    fun theVerdictsHaveAShapeAndTheCorporaWordsNeverFailWarnPass() {
        assertEquals(listOf("●", "◐", "✓"), NodeEconomyVerdict.entries.map { it.shape })
        assertEquals(listOf("outlier", "above", "in range"), NodeEconomyVerdict.entries.map { it.word })
    }

    // ---- the faces behind a box ---------------------------------------------------------------------

    @Test
    fun theFacesNameTheFewestAndTheMostWithHowManyShareTheirCount() {
        val spread = assertNotNull(faceSpread(fx.corpus, fx.STYLE_KEY, "T"))
        assertEquals(listOf(8, 8, 9, 28), spread.faces.map { it.count })
        assertEquals(FaceCount("Alpha", 8), spread.fewest)
        assertEquals(1, spread.fewestAlso, "Beta also has 8")
        assertEquals(FaceCount("Delta", 28), spread.most)
        assertEquals(0, spread.mostAlso)
    }

    @Test
    fun noFacesMeansNoSpread() {
        assertNull(faceSpread(fx.corpus, fx.STYLE_KEY, "Z"))
        assertNull(faceSpread(fx.corpus, "no-such-class", "T"))
    }

    @Test
    fun theLegendAndFenceSentenceAreTheBoardsWords() {
        assertEquals("▮ middle half of the 30 faces · ● your glyph · ─ whiskers: full range · log scale", economyLegend(30))
        assertTrue(
            ECONOMY_FENCE_SENTENCE.startsWith("Outlier: above the top of the box by more than 1.5 box-widths or a quarter of its value"),
        )
        assertTrue("our heuristic" in ECONOMY_FENCE_SENTENCE)
    }
}
