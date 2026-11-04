// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.ship

import com.asoc.typewright.compile.CompileLocation
import com.asoc.typewright.qa.ship.BlockerSource
import com.asoc.typewright.qa.ship.ShipBlocker
import com.asoc.typewright.qa.ship.ShipCapability
import com.asoc.typewright.qa.ship.ShipGate
import com.asoc.typewright.qa.ship.ShipMode
import com.asoc.typewright.ui.deck.DeckLeft
import com.asoc.typewright.ui.deck.DeckRight
import com.asoc.typewright.ui.deck.DeckSeats
import com.asoc.typewright.ui.deck.DeckSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShipChromeTest {
    private val compiles = ShipCapability(true, "Compiled on this computer", "fontmake runs here")
    private val cannot = ShipCapability(false, "Not compiled here", "The web preview does not compile (D4)")
    private val desktop = ShipPlatform(ShipExit.FOLDER, compiles, CompileLocation.OnThisComputer("fontmake"))
    private val web = ShipPlatform(ShipExit.ZIP, cannot, null)
    private val phone = ShipPlatform(ShipExit.DOCUMENT_TREE, cannot, null)
    private val nowhere = ShipPlatform(ShipExit.NOWHERE, cannot, null)
    private val bareDesktop = ShipPlatform(ShipExit.FOLDER, cannot, null)
    private val clean = ShipGate(emptyList())
    private val blocked =
        ShipGate(listOf(ShipBlocker(BlockerSource.LAYER_TWO, "outlines/node-economy", "Node economy", "Your T has 1,763 points.")))

    @Test
    fun theReadyPagesDeckIsBackTheTwoModesAndTheBuildCircle() {
        val chrome = readyChrome("Hyle Deco", ShipMode.GOOGLE_FONTS, desktop, shipPrimary(clean, desktop))
        assertEquals("Ship · Hyle Deco", chrome.title)
        assertEquals(listOf("Google Fonts", "Own licence"), chrome.toggles.map { it.label })
        assertEquals(listOf(true, false), chrome.toggles.map { it.on })
        assertEquals(DeckLeft.BACK, chrome.left)
        assertEquals(DeckRight.BUILD, chrome.right)
        assertFalse(chrome.rightDimmed)
        assertEquals("Build and write the repository", chrome.rightLabel)
        assertNull(chrome.rightWhy)
    }

    @Test
    fun theOwnLicenceToggleIsTheOneThatIsOn() {
        val chrome = readyChrome("F", ShipMode.OWN_LICENCE, desktop, shipPrimary(clean, desktop))
        assertEquals(listOf(false, true), chrome.toggles.map { it.on })
        assertEquals("A repository under your own licence, written to a folder", chrome.subtitle)
    }

    @Test
    fun theSeatsAreTheBoardsTwoLabelsInOneGroup() {
        // The board (Ship.dc.html) and SCREENS_V1 §2.4 name these two seats; both are longer than the deck's usual ten characters,
        // and the board wins on how something looks, so they stay as drawn there.
        val chrome = readyChrome("F", ShipMode.GOOGLE_FONTS, desktop, shipPrimary(clean, desktop))
        assertEquals(listOf("Google Fonts", "Own licence"), chrome.toggles.map { it.label })
        assertFalse(chrome.toggles.any { it.groupStart }, "one group of two seats")
        assertTrue(chrome.toggles.size in DeckSeats.MIN_SEATS_PER_GROUP..DeckSeats.MAX_SEATS_PER_GROUP)
    }

    @Test
    fun whileCheckHasFailsTheBuildCircleStaysAndIsDimmedAndASayWhy() {
        val primary = shipPrimary(blocked, desktop)
        assertEquals(DeckRight.BUILD, primary.right)
        assertTrue(primary.dimmed)
        assertTrue("Node economy" in primary.why.orEmpty() && "Fix them in Check" in primary.why.orEmpty(), primary.why)
    }

    @Test
    fun theWebExportsAZipWithTheDownloadCircle() {
        val primary = shipPrimary(clean, web)
        assertEquals(DeckRight.DOWNLOAD, primary.right)
        assertEquals("Export a zip", primary.label)
        assertFalse(primary.dimmed, "a zip needs no compiler")
    }

    @Test
    fun aPhoneWritesAFolderWithoutAFontAndSaysSo() {
        val primary = shipPrimary(clean, phone)
        assertEquals(DeckRight.BUILD, primary.right)
        assertEquals("Write the folder without a font", primary.label)
        assertFalse(primary.dimmed)
    }

    @Test
    fun aComputerWithoutACompilerIsDimmedWithTheBackendsOwnWords() {
        val primary = shipPrimary(clean, bareDesktop)
        assertTrue(primary.dimmed)
        assertEquals("The web preview does not compile (D4)", primary.why)
    }

    @Test
    fun aBuildWithNowhereToWriteIsDimmedAndSaysSo() {
        val primary = shipPrimary(clean, nowhere)
        assertTrue(primary.dimmed)
        assertTrue("cannot write a folder" in primary.why.orEmpty(), primary.why)
    }

    @Test
    fun aBlockOutranksEveryOtherReasonTheCircleIsDimmed() {
        assertTrue("Fix them in Check" in shipPrimary(blocked, nowhere).why.orEmpty())
    }

    @Test
    fun theBuildingPagesDeckIsCloseNoTogglesAndNoRightCircle() {
        val chrome = buildingChrome("Hyle Deco", desktop)
        assertEquals("Building Hyle Deco", chrome.title)
        assertEquals("On this computer · nothing leaves it", chrome.subtitle)
        assertEquals(DeckLeft.CLOSE, chrome.left)
        assertEquals(emptyList(), chrome.toggles)
        assertEquals(DeckRight.NONE, chrome.right)
    }

    @Test
    fun aWebExportsBuildingPageSaysItIsInTheBrowser() {
        assertEquals("In this browser · nothing is sent", buildingChrome("F", web).subtitle)
    }

    @Test
    fun afterAWriteBackReturnsAndTheCircleBuildsAgain() {
        val chrome = afterChrome("Hyle Deco", "Written to ~/x", desktop, shipPrimary(clean, desktop), "Build again")
        assertEquals(DeckLeft.BACK, chrome.left)
        assertEquals(DeckRight.BUILD, chrome.right)
        assertEquals("Build again", chrome.rightLabel)
        assertEquals(emptyList(), chrome.toggles)
    }

    @Test
    fun theShipLabelOnTheSectionArcCarriesTheRightShape() {
        assertEquals(com.asoc.typewright.ui.deck.StationStatus.TO_REFIT, shipStationStatus(3, shippedThisDrawing = false))
        assertEquals(com.asoc.typewright.ui.deck.StationStatus.NOT_STARTED, shipStationStatus(0, shippedThisDrawing = false))
        assertEquals(com.asoc.typewright.ui.deck.StationStatus.DONE, shipStationStatus(0, shippedThisDrawing = true))
        assertEquals(
            com.asoc.typewright.ui.deck.StationStatus.DONE,
            shipStationStatus(3, shippedThisDrawing = true),
            "a shipped drawing is shipped",
        )
        assertNull(shipStationStatus(null, shippedThisDrawing = false), "no report means no shape rather than an invented one")
    }

    @Test
    fun shipIsASectionOfTheDeck() {
        assertEquals("Ship", DeckSection.SHIP.label)
    }
}
