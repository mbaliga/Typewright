// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.check

import com.asoc.typewright.qa.report.CheckReport
import com.asoc.typewright.qa.report.EconomySort
import com.asoc.typewright.ui.deck.DeckRight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.TimeSource

class CheckChromeTest {
    private fun chrome(
        name: String? = "Hyle Deco Regular",
        hasGlyphs: Boolean = true,
        run: CheckRunState = CheckRunState(),
        view: CheckView = CheckView.REPORT,
        raw: Boolean = false,
        sort: EconomySort = EconomySort.WORST_FIRST,
        economy: EconomyHeader? = EconomyHeader(30, classIsHeuristic = false),
    ) = checkChrome(name, hasGlyphs, run, view, raw, sort, economy)

    private val settled = CheckRunState(layerTwo = LayerTwoState.Ready(CheckReport(emptyList(), null, null)))

    private val running =
        CheckRunState(layerOne = LayerOneState.Running(LayerOneStage.COMPILE, TimeSource.Monotonic.markNow(), null, emptyList()))

    @Test
    fun theReportHasReportAndEconomyThenPlainAndRawInASecondGroup() {
        val c = chrome()
        assertEquals(listOf("Report", "Economy", "Plain", "Raw"), c.toggles.map { it.label })
        assertEquals(listOf(true, false, true, false), c.toggles.map { it.on })
        assertEquals(listOf(false, false, true, false), c.toggles.map { it.groupStart })
        assertEquals("Check · Hyle Deco Regular", c.title)
    }

    @Test
    fun rawPressesRawAndReleasesPlain() {
        assertEquals(listOf(false, true), chrome(raw = true).toggles.drop(2).map { it.on })
    }

    @Test
    fun economyHidesPlainAndRawAndPressesEconomy() {
        val c = chrome(view = CheckView.ECONOMY)
        assertEquals(listOf("Report", "Economy"), c.toggles.map { it.label })
        assertEquals(listOf(false, true), c.toggles.map { it.on })
        assertEquals("Economy · on-curve points", c.title)
        assertEquals("Your glyph against 30 faces · worst first", c.subtitle)
    }

    @Test
    fun economyWithNoClassSaysSoInsteadOfInventingAFaceCount() {
        assertEquals("No style class to compare against yet", chrome(view = CheckView.ECONOMY, economy = null).subtitle)
    }

    @Test
    fun everySeatFitsTheDecksLabelRule() {
        for (view in listOf(CheckView.REPORT, CheckView.ECONOMY)) {
            assertEquals(emptyList(), chrome(view = view).toggles.filter { it.label.length > 10 })
        }
    }

    @Test
    fun theRightCircleIsRunAgainAndItDimsWhileARunIsGoing() {
        assertEquals(DeckRight.REFRESH, chrome().right)
        assertEquals(false, chrome(run = settled).rightDimmed)
        assertEquals(true, chrome(run = running).rightDimmed, "layer one is running")
        assertEquals(true, chrome(run = CheckRunState()).rightDimmed, "layer two is still running")
    }

    @Test
    fun withNoProjectOrNoGlyphsThereAreNoSeatsAndNoCircle() {
        val none = chrome(name = null, hasGlyphs = false)
        assertEquals("Check", none.title)
        assertEquals(emptyList(), none.toggles)
        assertEquals(DeckRight.NONE, none.right)
        val empty = chrome(hasGlyphs = false)
        assertEquals(emptyList(), empty.toggles)
        assertEquals(DeckRight.NONE, empty.right)
    }
}
