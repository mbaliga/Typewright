// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.project

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import com.asoc.typewright.ui.deck.DeckSection
import com.asoc.typewright.ui.deck.StationStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The function the router hands the deck, composed over a real open project. */
class StationStatusComposeTest {
    @Test
    fun theDeckGetsDrawNowAndTraceOnceTheProjectHasSettled() {
        val workspace = SampleProject.workspace()
        var statusOf: (DeckSection) -> StationStatus? = { null }
        ImageComposeScene(width = 100, height = 100) {
            CompositionLocalProvider(LocalProjectWorkspace provides workspace) {
                statusOf = rememberProjectStationStatus()
            }
        }.use { scene ->
            val deadline = System.nanoTime() + 20_000_000_000L
            var frame = 0L
            while (statusOf(DeckSection.TRACE) == null) {
                check(System.nanoTime() < deadline) { "Trace's status never arrived" }
                scene.render(frame * 16_000_000L)
                frame++
                Thread.sleep(5)
            }
            // An imported font has outliers against its declared class, and nothing in it has been looked at by the person.
            assertEquals(StationStatus.TO_REFIT, statusOf(DeckSection.TRACE))
            assertEquals(StationStatus.NOT_STARTED, statusOf(DeckSection.DRAW))
            for (section in listOf(DeckSection.CAPTURE, DeckSection.CHECK, DeckSection.SHIP, DeckSection.SPACE, DeckSection.HOME)) {
                assertNull(statusOf(section), "$section")
            }
        }
    }

    @Test
    fun withNoProjectOpenEveryStationHasNoShape() {
        var statusOf: (DeckSection) -> StationStatus? = { StationStatus.DONE }
        ImageComposeScene(width = 100, height = 100) {
            statusOf = rememberProjectStationStatus()
        }.use { scene ->
            scene.render(0)
            for (section in DeckSection.entries) assertNull(statusOf(section), "$section")
        }
    }
}
