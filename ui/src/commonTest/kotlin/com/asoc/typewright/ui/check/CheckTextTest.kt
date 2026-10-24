// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.check

import com.asoc.typewright.qa.LayerOneRuntime
import com.asoc.typewright.qa.report.EconomySort
import kotlin.test.Test
import kotlin.test.assertEquals

class CheckTextTest {
    private val fontbakery = LayerOneRuntime("Fontbakery", "0.12.10", "on this computer")

    @Test
    fun theTitleNamesTheFont() {
        assertEquals("Check · Hyle Deco Regular", CheckText.title("Hyle Deco Regular"))
    }

    @Test
    fun beforeAnythingIsKnownOnlyLayerTwoIsPromised() {
        assertEquals("Layer two here", CheckText.runtimeLine(CheckRunState()))
    }

    @Test
    fun aComputerNamesTheToolAndItsVersion() {
        val state = CheckRunState(layerOne = LayerOneState.NotRun, runtime = fontbakery, probed = true)
        assertEquals("Layer one: Fontbakery 0.12.10 on this computer · layer two here", CheckText.runtimeLine(state))
    }

    @Test
    fun aPhoneOrABrowserSaysLayerOneNeedsTheDesktopApp() {
        val reason = "Layer one needs the desktop app: Fontbakery and Fontspector run on a computer. Layer two runs here."
        val state = CheckRunState(layerOne = LayerOneState.Unavailable(reason), probed = true)
        assertEquals("Layer one needs the desktop app · layer two here", CheckText.runtimeLine(state))
    }

    @Test
    fun aComputerWithoutTheToolsSaysSoWithoutClaimingTheyAreThere() {
        val state =
            CheckRunState(layerOne = LayerOneState.Unavailable("Layer one did not run: Fontbakery is not installed."), probed = true)
        assertEquals("Layer one is not available on this computer · layer two here", CheckText.runtimeLine(state))
    }

    @Test
    fun theEconomySubtitleSaysHowManyFacesAndTheOrder() {
        assertEquals("Your glyph against 30 faces · worst first", CheckText.economySubtitle(30, EconomySort.WORST_FIRST, false))
        assertEquals("Your glyph against 8 faces · a–z", CheckText.economySubtitle(8, EconomySort.ALPHABETICAL, false))
        assertEquals(
            "Your glyph against 30 faces · worst first · class read by our heuristic",
            CheckText.economySubtitle(30, EconomySort.WORST_FIRST, true),
        )
    }

    @Test
    fun elapsedTimeIsMinutesAndSeconds() {
        assertEquals("00:08", CheckText.elapsed(8))
        assertEquals("01:05", CheckText.elapsed(65))
        assertEquals("00:00", CheckText.elapsed(-3))
        assertEquals("under 1 s", CheckText.duration(0))
        assertEquals("6 s", CheckText.duration(6))
    }
}
