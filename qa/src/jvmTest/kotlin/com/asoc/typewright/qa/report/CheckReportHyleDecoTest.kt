// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.report

import com.asoc.typewright.core.font.sfnt.readSfntFont
import com.asoc.typewright.core.font.sfnt.toUfoProject
import com.asoc.typewright.core.font.ufo.UfoProject
import com.asoc.typewright.qa.checkAlignmentMiss
import com.asoc.typewright.qa.checkNodeEconomy
import com.asoc.typewright.qa.corpus.NodeEconomyCorpus
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The report over the repository's real dogfood font (`fonts/HyleDeco-Regular.ttf`, opened the way an import opens
 * it) and the real node-economy corpus. The numbers pinned here are the ones CLAUDE.md states for the font (a T of
 * 1,763 points against a box of 8 to 12.5) and the ones Home shows (38 of 52 letters beyond the fence); the rest are
 * this build's own reading of the font, pinned so a change to a check shows up as a change here.
 */
class CheckReportHyleDecoTest {
    private val ufo: UfoProject by lazy {
        val root = checkNotNull(System.getProperty("typewright.repoRoot")) { "typewright.repoRoot is not set" }
        readSfntFont(File(root, "fonts/HyleDeco-Regular.ttf").readBytes()).toUfoProject()
    }
    private val corpus: NodeEconomyCorpus by lazy { NodeEconomyCorpus.load() }
    private val outlineGlyphs: Int get() = ufo.glyphs.count { it.contours.isNotEmpty() }

    private val inputs: CheckInputs
        get() =
            CheckInputs(
                project = ufo,
                style = CheckStyle("sans-geometric", "geometric sans", heuristic = false),
                corpus = corpus,
                provenance = ImportProvenance(true, outlineGlyphs, outlineGlyphs, "HyleDeco-Regular.ttf"),
            )

    private val report: CheckReport by lazy { buildCheckReport(inputs) }

    @Test
    fun theNodeEconomyItemQuotesTheBoardsSentenceFromTheRealCorpus() {
        val economy = assertNotNull(report.item(ReviewOrder.NODE_ECONOMY))
        assertEquals(Severity.FAIL, economy.severity)
        assertTrue(
            economy.summary.startsWith("Your T has 1,763 points. In 30 geometric sans faces a T has 8 to 12.5, at most 28."),
            economy.summary,
        )
        assertEquals("38 of 52 letters are outliers", economy.detail)
    }

    @Test
    fun theReportsEconomyIsCheckNodeEconomyOnTheSameProject() {
        assertEquals(checkNodeEconomy(ufo, "sans-geometric", corpus), report.economy)
    }

    @Test
    fun theFontHasNoCurvesAnywhereAndSaysSo() {
        val polygons = assertNotNull(report.item(ReviewOrder.CURVES_ARE_POLYGONS))
        assertEquals(Severity.FAIL, polygons.severity)
        assertEquals("0 off-curve points in $outlineGlyphs glyphs", polygons.detail)
        assertTrue(polygons.summary.endsWith("this font has none."), polygons.summary)
        // The letters that most need curves come first in the refit list, and it is the corpus that says which they are.
        val route = polygons.action?.route
        assertTrue(route is CheckRoute.OpenTrace && route.glyphs.take(3).all { it in setOf("S", "g", "s", "O", "Q", "a") }, "$route")
    }

    @Test
    fun theTStemFootAtOneUnitIsAnAlignmentMiss() {
        val t = ufo.glyphs.first { it.name == "T" }
        val finding = checkAlignmentMiss(t, metricLines(ufo.fontInfo)).firstOrNull { it.location.y == 1 }
        assertNotNull(finding, "CLAUDE.md: the T's stem foot is at y = 1")
        assertEquals(Severity.WARN, report.item(ReviewOrder.ALIGNMENT_MISS)?.severity)
    }

    @Test
    fun theImportedSourceIsAFossilAndNoAnchorIsOnAnyBaseLetter() {
        assertEquals(Severity.FAIL, report.item(ReviewOrder.SOURCE_FOSSIL)?.severity)
        assertEquals(Severity.FAIL, report.item(ReviewOrder.ANCHORS)?.severity)
    }

    @Test
    fun theCountsOfThisBuildsReadingAreFourFailSixWarnFourPass() {
        assertEquals(CheckCounts(fail = 4, warn = 6, pass = 4, notMeasured = 0), report.counts)
        assertEquals("4 fail · 6 warn · 4 pass", report.counts.line())
    }

    @Test
    fun theReportIsTheSameEveryTimeItIsBuilt() {
        assertEquals(report, buildCheckReport(inputs))
    }
}
