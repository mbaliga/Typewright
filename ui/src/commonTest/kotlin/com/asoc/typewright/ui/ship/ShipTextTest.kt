// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.ship

import com.asoc.typewright.brief.GENRES
import com.asoc.typewright.brief.GenreGroup
import com.asoc.typewright.compile.CompileLocation
import com.asoc.typewright.qa.ship.GfCategory
import com.asoc.typewright.qa.ship.ShipFontInfoField
import com.asoc.typewright.qa.ship.ShipGate
import com.asoc.typewright.qa.ship.ShipMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShipTextTest {
    @Test
    fun theTitleAndSubtitleSayWhatTheFolderIsAndHowItLeaves() {
        assertEquals("Ship · Hyle Deco", ShipText.title("Hyle Deco"))
        assertEquals("A Google Fonts repository, written to a folder", ShipText.subtitle(ShipMode.GOOGLE_FONTS, ShipExit.FOLDER))
        assertEquals("A Google Fonts repository, exported as a zip", ShipText.subtitle(ShipMode.GOOGLE_FONTS, ShipExit.ZIP))
        assertEquals("A repository under your own licence, written to a folder", ShipText.subtitle(ShipMode.OWN_LICENCE, ShipExit.FOLDER))
        assertEquals(
            "A Google Fonts repository, written to a folder on this device",
            ShipText.subtitle(ShipMode.GOOGLE_FONTS, ShipExit.DOCUMENT_TREE),
        )
    }

    @Test
    fun theBuildingSubtitleSaysWhereTheWorkHappensAndThatNothingLeaves() {
        assertEquals(
            "On this computer · nothing leaves it",
            ShipText.buildingSubtitle(CompileLocation.OnThisComputer("fontmake"), ShipExit.FOLDER),
        )
        assertEquals(
            "On this device · nothing leaves it",
            ShipText.buildingSubtitle(CompileLocation.OnThisDevice("fontmake"), ShipExit.DOCUMENT_TREE),
        )
        assertEquals("In this browser · nothing is sent", ShipText.buildingSubtitle(null, ShipExit.ZIP))
        assertTrue(
            "sent there" in ShipText.buildingSubtitle(CompileLocation.HostedEndpoint("https://x"), ShipExit.FOLDER),
            "a hosted build says the project is sent",
        )
    }

    @Test
    fun theActionLabelNamesWhatATapDoesHere() {
        assertEquals("Build and write the repository", ShipText.actionLabel(ShipExit.FOLDER, compiles = true))
        assertEquals("Write the folder without a font", ShipText.actionLabel(ShipExit.DOCUMENT_TREE, compiles = false))
        assertEquals("Export a zip", ShipText.actionLabel(ShipExit.ZIP, compiles = false))
    }

    @Test
    fun theTargetRowSaysWhatIsMissing() {
        assertEquals("No folder is chosen yet", ShipText.notChosen(ShipExit.FOLDER))
        assertEquals("A zip your browser downloads: hyle-deco-gf.zip", ShipText.notChosen(ShipExit.ZIP, "hyle-deco-gf"))
        assertEquals("A zip your browser downloads", ShipText.notChosen(ShipExit.ZIP))
        assertEquals("Choose a folder", ShipText.chooseLink(ShipExit.FOLDER, chosen = false))
        assertEquals("Choose another folder", ShipText.chooseLink(ShipExit.FOLDER, chosen = true))
        assertNull(ShipText.chooseLink(ShipExit.ZIP, chosen = false), "a zip needs no folder")
    }

    @Test
    fun elapsedTimeIsMinutesAndSeconds() {
        assertEquals("00:08", ShipText.elapsed(8))
        assertEquals("01:05", ShipText.elapsed(65))
        assertEquals("00:00", ShipText.elapsed(-3))
    }

    @Test
    fun durationsAreTenthsOfASecondThenMinutes() {
        assertEquals("0.4 s", ShipText.duration(400))
        assertEquals("6.2 s", ShipText.duration(6_200))
        assertEquals("0.0 s", ShipText.duration(10))
        assertEquals("59.9 s", ShipText.duration(59_900))
        assertEquals("1 min 12 s", ShipText.duration(72_000))
        assertEquals("", ShipText.duration(-1))
    }

    @Test
    fun sizesAreFilesAndTheLargestUnitThatFits() {
        assertEquals("1 file · 12 bytes", ShipText.size(1, 12))
        assertEquals("19 files · 3 KB", ShipText.size(19, 2_600))
        assertEquals("19 files · 3.2 MB", ShipText.size(19, 3_400_000))
    }

    @Test
    fun aPathUnderTheHomeFolderIsWrittenWithATilde() {
        assertEquals("~/Fonts/hyle-deco-gf", ShipText.shortPath("/home/me/Fonts/hyle-deco-gf", "/home/me"))
        assertEquals("~/Fonts", ShipText.shortPath("/home/me/Fonts", "/home/me/"))
        assertEquals("~", ShipText.shortPath("/home/me", "/home/me"))
        assertEquals("/srv/fonts", ShipText.shortPath("/srv/fonts", "/home/me"))
        assertEquals(
            "/home/mean/x",
            ShipText.shortPath("/home/mean/x", "/home/me"),
            "a folder that merely starts with the same letters is not under home",
        )
        assertEquals("/a/b", ShipText.shortPath("/a/b", null))
    }

    @Test
    fun theBlockedBlockCountsTheFails() {
        val gate = ShipGate(listOf(blocker("a"), blocker("b")))
        assertEquals("Build is blocked while Check has fails. 2 to go.", ShipText.blockedBlock(gate))
    }

    private fun blocker(id: String) =
        com.asoc.typewright.qa.ship
            .ShipBlocker(com.asoc.typewright.qa.ship.BlockerSource.LAYER_TWO, id, id, id)

    @Test
    fun everyShipFontInfoFieldNamesAFontInfoFieldOfTheSameName() {
        for (field in ShipFontInfoField.entries) {
            val named =
                com.asoc.typewright.ui.shell.FontInfoField.entries
                    .firstOrNull { it.name == field.name }
            assertTrue(named != null, "${field.name} must be a Font info field so a link from Ship never lands on a dead end")
            assertEquals(named, fontInfoFieldOf(field))
        }
    }

    @Test
    fun theCategoryReadFromAStyleClassAgreesWithTheBriefsOwnGenreGroups() {
        val byGroup =
            mapOf(
                GenreGroup.SANS to GfCategory.SANS_SERIF,
                GenreGroup.SERIF to GfCategory.SERIF,
                GenreGroup.DISPLAY to GfCategory.DISPLAY,
                GenreGroup.SCRIPT to GfCategory.HANDWRITING,
                GenreGroup.MONO to GfCategory.MONOSPACE,
            )
        assertTrue(GENRES.size >= 20, "the brief lists the genres: ${GENRES.size}")
        for (genre in GENRES) {
            assertEquals(byGroup.getValue(genre.group), GfCategory.fromStyleKey(genre.key), "${genre.key} is in the ${genre.group} group")
        }
    }
}
