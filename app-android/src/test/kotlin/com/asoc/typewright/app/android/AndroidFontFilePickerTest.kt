// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.android

import com.asoc.typewright.project.ProjectLocation
import com.asoc.typewright.ui.imports.PickResult
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The parts of the Android picker that run without a device; the document picker itself is owner-verified. */
class AndroidFontFilePickerTest {
    @Test
    fun aDocumentIsReadWholeUnderItsDisplayName() {
        val bytes = ByteArray(200_000) { (it % 251).toByte() }
        val result = readFontStream("Lumen.ttf", ByteArrayInputStream(bytes), maxBytes = 1_000_000)
        assertIs<PickResult.Font>(result)
        assertEquals("Lumen.ttf", result.name)
        assertContentEquals(bytes, result.bytes)
    }

    @Test
    fun aDocumentOverTheLimitIsRefusedWithoutBeingHeld() {
        val result = readFontStream("movie.mp4", ByteArrayInputStream(ByteArray(5_000)), maxBytes = 4_096)
        assertIs<PickResult.Failed>(result)
        assertTrue("too large to be a font" in result.message, result.message)
    }

    @Test
    fun aDocumentExactlyAtTheLimitIsAccepted() {
        val result = readFontStream("edge.ttf", ByteArrayInputStream(ByteArray(4_096)), maxBytes = 4_096)
        assertIs<PickResult.Font>(result)
        assertEquals(4_096, result.bytes.size)
    }

    // The picker's own refusal of a UFO folder (NO_UFO_ON_ANDROID) needs an ActivityResultRegistry and a ContentResolver, which exist
    // only on a device, and a test that read the constant back would pass whatever the picker did. The reason reaches the person through
    // FontFilePicker.ufoFolderUnavailableReason, and what the Projects screen does with it is tested in :ui (ProjectsChoicesScreenshotTest).

    @Test
    fun theDestinationNeverOpensTheFolderPickerWhileTheAuditIsBeingDrawn() =
        runBlocking {
            var asked = 0
            val chosen = ProjectLocation.SafTree("content://tree/1", "primary:Fonts")
            val destination =
                AndroidImportDestination {
                    asked++
                    chosen
                }
            // The audit reads the destination to say where the project will go; that must not open a system dialog.
            assertEquals(null, destination.parentFolder())
            destination.describe(null, "Lumen")
            destination.describe(chosen, "Lumen")
            assertEquals(0, asked, "only pressing Import may ask for a folder")

            assertEquals(chosen, destination.chooseParentFolder())
            assertEquals(1, asked)
            assertEquals(chosen, destination.chooseParentFolder())
            assertEquals(2, asked, "every Import asks again; nothing is remembered behind the person's back")
        }

    @Test
    fun aDeclinedFolderComesBackAsNothingSoTheImportSaysNoFolderWasChosen() =
        runBlocking {
            assertEquals(null, AndroidImportDestination { null }.chooseParentFolder())
        }

    @Test
    fun theAuditNamesTheFolderItWillMakeAndSaysAndroidWillAsk() {
        val destination = AndroidImportDestination { null }
        for (folder in listOf("Lumen", "Poppins 2", "Josefin Sans")) {
            val text = destination.describe(null, folder)
            assertTrue(folder in text, "the audit names the folder $folder it will make: $text")
            assertTrue("asks which folder" in text, text)
        }
    }
}
