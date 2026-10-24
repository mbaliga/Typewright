// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.ship

import kotlinx.coroutines.runBlocking
import java.awt.HeadlessException
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopShipDestinationTest {
    private val home = "/home/me"

    @Test
    fun aChosenFolderIsNamedWithATildeAndWrittenByTheFolderWriter() =
        runBlocking {
            val folder = Files.createTempDirectory("typewright-dest-").toFile()
            val destination = DesktopShipDestination(chooser = { _, _ -> folder }, home = folder.parent)
            val chosen = assertIs<ShipChoice.Chosen>(destination.choose("hyle-deco-gf", null))
            assertEquals("~/${folder.name}", chosen.target.label)
            assertFalse(chosen.target.withoutFontOk, "a computer's folder wants a font; a missing compiler is something to fix")
            chosen.target.writer.write(
                mapOf("OFL.txt" to "licence".encodeToByteArray(), "fonts/ttf/F.ttf" to byteArrayOf(1, 2)),
                emptyList(),
            )
            assertEquals("licence", File(folder, "OFL.txt").readText())
            assertContentEquals(byteArrayOf(1, 2), File(folder, "fonts/ttf/F.ttf").readBytes())
        }

    @Test
    fun theChooserIsToldWhatItIsChoosingForAndStartsNearTheLastPlace() =
        runBlocking {
            val parent = Files.createTempDirectory("typewright-dest-").toFile()
            val last = File(parent, "hyle-deco-gf").apply { mkdir() }
            var title = ""
            var start: File? = null
            val destination =
                DesktopShipDestination(chooser = { t, s ->
                    title = t
                    start = s
                    null
                }, home = "/nowhere")
            destination.choose("hyle-deco-gf", last.absolutePath)
            assertEquals("Choose the folder to write hyle-deco-gf to", title)
            assertEquals(parent, start, "the chooser opens in the folder that holds the last place")
            destination.choose("hyle-deco-gf", "/does/not/exist")
            assertNull(start, "a last place that is gone is not a starting point")
        }

    @Test
    fun aLastPlaceWrittenWithATildeIsExpandedForTheChooser() =
        runBlocking {
            val home = Files.createTempDirectory("typewright-home-").toFile()
            val last = File(home, "Fonts/hyle-deco-gf").apply { mkdirs() }
            var start: File? = null
            val destination =
                DesktopShipDestination(chooser = { _, s ->
                    start = s
                    null
                }, home = home.absolutePath)
            destination.choose("hyle-deco-gf", "~/Fonts/hyle-deco-gf")
            assertEquals(last.parentFile, start)
        }

    @Test
    fun cancellingTheChooserIsCancelledNotAnError() =
        runBlocking {
            assertEquals(ShipChoice.Cancelled, DesktopShipDestination(chooser = { _, _ -> null }, home = home).choose("x", null))
        }

    @Test
    fun aComputerWithNoScreenSaysSoInWordsAndNeverThrows() =
        runBlocking {
            val destination = DesktopShipDestination(chooser = { _, _ -> throw HeadlessException() }, home = home)
            val failed = assertIs<ShipChoice.Failed>(destination.choose("x", null))
            assertTrue("no screen" in failed.message, failed.message)
        }

    @Test
    fun anyOtherFailureOfTheChooserIsSaidInWords() =
        runBlocking {
            val failed =
                assertIs<ShipChoice.Failed>(
                    DesktopShipDestination(chooser = {
                        _,
                        _,
                        ->
                        error("the portal is gone")
                    }, home = home).choose("x", null),
                )
            assertTrue("the portal is gone" in failed.message, failed.message)
        }

    @Test
    fun theDestinationIsAFolderChooser() {
        assertEquals(ShipExit.FOLDER, DesktopShipDestination(chooser = { _, _ -> null }).exit)
        assertEquals(ShipExit.FOLDER, platformShipDestination().exit)
    }

    @Test
    fun theTargetMemoryKeepsTheLastPlacePerProject() {
        val memory = ShipTargetMemory()
        assertNull(memory.placeFor("Hyle Deco"))
        memory.remember("Hyle Deco", "~/a")
        memory.remember("Other", "~/b")
        memory.remember("Hyle Deco", "~/c")
        assertEquals("~/c", memory.placeFor("Hyle Deco"))
        assertEquals("~/b", memory.placeFor("Other"))
    }

    @Test
    fun anUnavailableDestinationFailsInTheWordsItWasGiven() =
        runBlocking {
            val destination = UnavailableShipDestination("nowhere to write")
            assertEquals(ShipExit.NOWHERE, destination.exit)
            assertEquals("nowhere to write", assertIs<ShipChoice.Failed>(destination.choose("x", null)).message)
        }
}
