// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.android

import com.asoc.typewright.project.ProjectLocation
import com.asoc.typewright.ui.imports.PickResult
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

    @Test
    fun aUfoFolderIsNotOfferedAndTheReasonIsPlain() {
        assertTrue("isn't offered on Android" in NO_UFO_ON_ANDROID)
        assertTrue("one file at a time" in NO_UFO_ON_ANDROID)
    }

    @Test
    fun theDestinationAsksForAFolderAndSaysSoBeforeImport() {
        val destination = AndroidImportDestination { null }
        assertTrue(destination.persists)
        val text = destination.describe(null, "Lumen")
        assertTrue("asks which folder" in text && "Lumen" in text, text)
        assertEquals(null, kotlinx.coroutines.runBlocking { destination.parentFolder() })
        assertEquals(null, kotlinx.coroutines.runBlocking { destination.chooseParentFolder() })
        val chosen = ProjectLocation.SafTree("content://tree/1", "primary:Fonts")
        assertEquals(chosen, kotlinx.coroutines.runBlocking { AndroidImportDestination { chosen }.chooseParentFolder() })
    }
}
