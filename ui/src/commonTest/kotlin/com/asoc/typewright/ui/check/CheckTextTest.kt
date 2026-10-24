// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.check

import com.asoc.typewright.qa.CheckOutcome
import com.asoc.typewright.qa.CheckStatus
import com.asoc.typewright.qa.LayerOneReport
import com.asoc.typewright.qa.LayerOneRuntime
import com.asoc.typewright.qa.report.EconomySort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun aFailureHeadingReadsAsASentenceForEachStage() {
        assertEquals("Layer one stopped while compiling the project with fontmake", CheckText.failureHeading(LayerOneStage.COMPILE))
        assertEquals("Layer one stopped while checking the compiled binary", CheckText.failureHeading(LayerOneStage.CHECK))
        // The old heading put a command after "was": "while it was compile the project with fontmake".
        for (stage in LayerOneStage.entries) assertFalse("while it was" in CheckText.failureHeading(stage))
    }

    @Test
    fun aLayerTwoStopNamesTheCauseAndEndsInOnePeriod() {
        assertEquals("Check stopped: the contour of T has no points.", CheckText.layerTwoStopped("the contour of T has no points"))
        assertEquals("Check stopped: boom.", CheckText.layerTwoStopped("boom."))
        assertTrue("Nothing in your project was changed" in CheckText.LAYER_TWO_STOPPED_ADVICE)
    }

    @Test
    fun theRuntimeLineNamesTheToolThatMadeTheReportNotTheOneLookedForFirst() {
        val spector = LayerOneRuntime("Fontspector", "1.0.0", "on this computer")
        val report = LayerOneReport(listOf(CheckOutcome("a", CheckStatus.PASS, "ok")), tool = fontbakery)
        // Fontspector was found first, its report could not be read, and Fontbakery's was used: the page says Fontbakery.
        val state = CheckRunState(layerOne = LayerOneState.Done(report, Any()), runtime = spector, probed = true)
        assertEquals("Layer one: Fontbakery 0.12.10 on this computer · layer two here", CheckText.runtimeLine(state))
    }
}
