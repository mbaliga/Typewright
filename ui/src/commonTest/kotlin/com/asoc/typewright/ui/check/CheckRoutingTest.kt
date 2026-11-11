// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.check

import com.asoc.typewright.qa.report.CheckRoute
import com.asoc.typewright.qa.report.FontInfoTarget
import com.asoc.typewright.ui.deck.DeckSection
import kotlin.test.Test
import kotlin.test.assertEquals

class CheckRoutingTest {
    @Test
    fun everyRouteLeadsWhereItsActionSays() {
        assertEquals(CheckDestination.InPage(CheckView.ECONOMY), destinationOf(CheckRoute.OpenEconomy))
        // Font info opens on the part the finding is about, so the action lands on the value to change.
        for (target in FontInfoTarget.entries) {
            assertEquals(CheckDestination.FontInfo(target), destinationOf(CheckRoute.OpenFontInfo(target)))
        }
        assertEquals(CheckDestination.Draw("B"), destinationOf(CheckRoute.OpenDraw("B")))
        assertEquals(CheckDestination.Section(DeckSection.TRACE, "o"), destinationOf(CheckRoute.OpenTrace(listOf("o", "e", "s"))))
        assertEquals(CheckDestination.Section(DeckSection.TRACE, null), destinationOf(CheckRoute.OpenTrace(emptyList())))
        assertEquals(CheckDestination.Section(DeckSection.CAPTURE), destinationOf(CheckRoute.OpenCapture))
        assertEquals(CheckDestination.Section(DeckSection.SHIP), destinationOf(CheckRoute.OpenShip))
    }
}
