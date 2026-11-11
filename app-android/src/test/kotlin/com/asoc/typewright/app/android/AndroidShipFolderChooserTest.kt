// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.android

import com.asoc.typewright.project.InMemoryProjectStore
import com.asoc.typewright.project.ProjectLocation
import com.asoc.typewright.project.StorageProvider
import com.asoc.typewright.project.StoreLookup
import com.asoc.typewright.ui.ship.ShipChoice
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** What Ship's Android folder decides without a device: the target it makes, and what it says when the grant or the folder is gone. */
class AndroidShipFolderChooserTest {
    private val tree = ProjectLocation.SafTree("content://tree/1", "primary:Fonts/hyle-deco-gf")

    private class Storage(
        private val lookup: StoreLookup,
    ) : StorageProvider {
        override suspend fun storeFor(location: ProjectLocation) = lookup

        override suspend fun createChild(
            parent: ProjectLocation,
            name: String,
        ): ProjectLocation = error("Ship writes into the chosen folder; it makes no child")
    }

    @Test
    fun aChosenFolderBecomesATargetThatWritesThroughTheStoreAndTakesNoFont() =
        runBlocking {
            val store = InMemoryProjectStore("hyle-deco-gf", mapOf("README.md" to "mine".encodeToByteArray()))
            val chooser = AndroidShipFolderChooser({ tree }, Storage(StoreLookup.Available(store)))

            val chosen = assertIs<ShipChoice.Chosen>(chooser.choose("hyle-deco-gf"))

            assertEquals("hyle-deco-gf", chosen.target.label)
            assertTrue(chosen.target.withoutFontOk, "a phone cannot compile, so the folder is written without a font")
            val report = chosen.target.writer.write(mapOf("OFL.txt" to "licence".encodeToByteArray()), emptyList())
            assertEquals(1, report.files)
            assertContentEquals("licence".encodeToByteArray(), assertNotNull(store.read("OFL.txt")))
            assertContentEquals(
                "mine".encodeToByteArray(),
                assertNotNull(store.read("README.md")),
                "files the tree does not name are left alone",
            )
        }

    @Test
    fun backingOutOfThePickerIsCancelledNotAnError() =
        runBlocking {
            val chooser = AndroidShipFolderChooser({ null }, Storage(StoreLookup.Missing))
            assertEquals(ShipChoice.Cancelled, chooser.choose("x"))
        }

    @Test
    fun aGrantAndroidDidNotKeepAsksForTheFolderAgainInWords() =
        runBlocking {
            val failed = assertIs<ShipChoice.Failed>(AndroidShipFolderChooser({ tree }, Storage(StoreLookup.PermissionNeeded)).choose("x"))
            assertTrue("permission" in failed.message, failed.message)
        }

    @Test
    fun aFolderThatIsGoneSaysSoInWords() =
        runBlocking {
            val failed = assertIs<ShipChoice.Failed>(AndroidShipFolderChooser({ tree }, Storage(StoreLookup.Missing)).choose("x"))
            assertTrue("moved or deleted" in failed.message, failed.message)
        }
}
