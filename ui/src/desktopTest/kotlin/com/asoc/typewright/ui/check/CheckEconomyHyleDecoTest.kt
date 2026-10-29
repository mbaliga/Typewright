// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.check

import com.asoc.typewright.qa.checkNodeEconomy
import com.asoc.typewright.qa.corpus.NodeEconomyVerdict
import com.asoc.typewright.qa.report.EconomySort
import com.asoc.typewright.qa.report.economyRows
import com.asoc.typewright.qa.report.faceSpread
import com.asoc.typewright.qa.report.shape
import com.asoc.typewright.qa.report.word
import com.asoc.typewright.ui.project.ProjectDataPacks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** S15's own done-when, against the real corpus and the real sample font: the T is 1,763 against 8 / 8 / 8 / 12.5 / 28, and an outlier. */
class CheckEconomyHyleDecoTest {
    private val corpus = checkNotNull(ProjectDataPacks.corpus) { "the node-economy pack is on this target" }
    private val font = checkNotNull(CheckTestProject.project.font)
    private val report = checkNodeEconomy(font.ufo, "sans-geometric", corpus)

    @Test
    fun theTIsAnOutlierAgainstTheRealGeometricSansBox() {
        val t = economyRows(report, EconomySort.ALPHABETICAL).first { it.glyph == "T" }
        assertEquals(1763, t.count)
        assertEquals(listOf(8.0, 8.0, 8.0, 12.5, 28.0), listOf(t.box.min, t.box.q1, t.box.med, t.box.q3, t.box.max))
        assertEquals(NodeEconomyVerdict.OUTLIER, t.verdict)
        assertEquals("● outlier", "${t.verdict.shape} ${t.verdict.word}")
        val spec = BoxPlotSpec.of(t.box, t.count)
        assertEquals("T: 1,763 against a box of 8 to 12.5, whiskers 8 to 28", spec.description("T"))
    }

    @Test
    fun worstFirstPutsTheTFirstAndAlphabeticalPutsCapitalsBeforeTheirTwins() {
        assertEquals("T", economyRows(report, EconomySort.WORST_FIRST).first().glyph)
        val az = economyRows(report, EconomySort.ALPHABETICAL).map { it.glyph }
        assertEquals(az.sortedWith(compareBy({ it.lowercase() }, { it })), az)
    }

    @Test
    fun theOpenRowNamesTheFewestAndTheMostFacesFromTheCorpus() {
        val spread = assertNotNull(faceSpread(corpus, "sans-geometric", "T"))
        assertEquals(30, spread.faces.size, "30 geometric sans faces")
        val caption = facesCaption(spread)
        assertTrue(caption.startsWith("FEWEST 8 · "), caption)
        assertTrue(caption.contains(" · MOST 28 · "), caption)
        assertEquals(spread.faces.size, faceDots(spread, 26.0, 56.0).size, "one dot per face")
    }
}
