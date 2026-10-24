// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.desktop

import com.asoc.typewright.project.ProjectLocation
import com.asoc.typewright.project.StoreLookup
import com.asoc.typewright.ui.imports.PickResult
import kotlinx.coroutines.runBlocking
import java.awt.HeadlessException
import java.io.File
import java.nio.file.Files
import java.nio.file.InvalidPathException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** What the desktop picker does after its dialog has answered; the dialogs themselves are owner-verified (no display here). */
class DesktopFontFilePickerTest {
    private fun tempDir(): File = Files.createTempDirectory("typewright-desktop-picker-").toFile()

    @Test
    fun nothingChosenIsACancellation() {
        assertEquals(PickResult.Cancelled, readPickedFont(directory = "/tmp", fileName = null))
        assertEquals(PickResult.Cancelled, readPickedUfo(null))
    }

    @Test
    fun aChosenFileIsReadWholeUnderItsOwnName() {
        val dir = tempDir()
        val bytes = byteArrayOf(0, 1, 0, 0, 7, 8, 9)
        File(dir, "Some Font.ttf").writeBytes(bytes)
        val result = readPickedFont(dir.path, "Some Font.ttf")
        assertIs<PickResult.Font>(result)
        assertEquals("Some Font.ttf", result.name)
        assertContentEquals(bytes, result.bytes)
    }

    @Test
    fun aFolderOrAMissingFileOrAnOversizedFileIsRefusedInPlainWords() {
        val dir = tempDir()
        File(dir, "big.ttf").writeBytes(ByteArray(2048))
        val folder = readPickedFont(dir.parent, dir.name)
        assertIs<PickResult.Failed>(folder)
        assertTrue("isn't a file" in folder.message, folder.message)
        val missing = readPickedFont(dir.path, "nothing.ttf")
        assertIs<PickResult.Failed>(missing)
        val large = readPickedFont(dir.path, "big.ttf", maxBytes = 1024)
        assertIs<PickResult.Failed>(large)
        assertTrue("too large" in large.message, large.message)
    }

    @Test
    fun aNameThisComputerCannotMakeIntoAPathDoesNotCrash() {
        // The JDK itself rejects a NUL in a path; the picker turns that into a message.
        assertTrue(
            runCatching {
                java.nio.file.Path
                    .of("/tmp", "bad\u0000name")
            }.exceptionOrNull() is InvalidPathException,
        )
        val result = readPickedFont("/tmp", "bad\u0000name.ttf")
        assertIs<PickResult.Failed>(result)
        assertTrue("isn't one this computer can open" in result.message, result.message)
    }

    @Test
    fun aUfoFolderIsReadWithItsFilesAndItsNeighbours() {
        val parent = tempDir()
        val ufo = File(parent, "Mine-Regular.ufo").apply { mkdirs() }
        File(ufo, "metainfo.plist").writeText(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<plist version=\"1.0\"><dict><key>formatVersion</key><integer>3</integer></dict></plist>\n",
        )
        File(parent, "Other-Bold.ufo").mkdirs()
        val result = readPickedUfo(ufo)
        assertIs<PickResult.Ufo>(result)
        assertEquals("Mine-Regular.ufo", result.folder.name)
        assertEquals(parent.absolutePath, result.folder.parentPath)
        assertEquals(listOf("Other-Bold.ufo"), result.folder.otherUfos)
    }

    @Test
    fun aFolderThatIsNotAUfoSaysSo() {
        val result = readPickedUfo(tempDir())
        assertIs<PickResult.Failed>(result)
        assertTrue("isn't a UFO" in result.message, result.message)
    }

    @Test
    fun aDialogThatCouldNotBeShownGetsAPlainSentence() {
        assertEquals(NO_DISPLAY_MESSAGE, dialogFailure(HeadlessException()))
        assertTrue("could not be shown" in dialogFailure(IllegalStateException("boom")))
    }

    @Test
    fun diskLocationsAreRealFoldersAndTheWorkspaceRootStaysInMemory() {
        runBlocking {
            val storage = DesktopImportStorage()
            val parent = tempDir()
            val child = storage.createChild(ProjectLocation.FileSystem(parent.path), "My Font")
            assertEquals(File(parent, "My Font").path, (child as ProjectLocation.FileSystem).path)
            assertTrue(File(parent, "My Font").isDirectory)
            assertIs<StoreLookup.Available>(storage.storeFor(child))
            assertEquals(StoreLookup.Missing, storage.storeFor(ProjectLocation.FileSystem(File(parent, "absent").path)))
            val memory = storage.createChild(ProjectLocation.InMemory("workspace"), "Untitled")
            assertIs<ProjectLocation.InMemory>(memory)
            assertIs<StoreLookup.Available>(storage.storeFor(memory))
        }
    }

    @Test
    fun theAuditSaysWhereTheProjectWillBeWrittenBeforeAndAfterAFolderIsKnown() {
        val destination = DesktopImportDestination()
        assertTrue(destination.persists)
        val asked = destination.describe(null, "Lumen")
        assertTrue("asks which folder" in asked && "Lumen" in asked, asked)
        val known = destination.describe(ProjectLocation.FileSystem("/home/me/Fonts"), "Lumen")
        assertTrue("/home/me/Fonts" in known && "Lumen" in known, known)
    }
}
