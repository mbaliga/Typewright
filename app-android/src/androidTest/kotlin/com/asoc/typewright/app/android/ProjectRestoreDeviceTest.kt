// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.android

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.asoc.typewright.project.ProjectLocation
import com.asoc.typewright.project.ProjectSession
import com.asoc.typewright.project.ProjectWorkspace
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * docs/PROJECT_MODEL.md §13's restore half, run in a fresh process after
 * `tools/device/p11-save-kill-restore.sh`'s own `adb shell am force-stop` (owner-run, CLAUDE.md
 * law 4): the SAF grant [ProjectSaveDeviceTest] took must still be persisted; launching
 * `MainActivity` must reopen that same project on its own ("Launch: reopen the last project",
 * §13's UI-half stopgap); its state and file hashes must match [ProjectSaveDeviceTest]'s record;
 * no `.tmp`/`.new` file may remain (recovery ran on open); and undo history must be empty (§6:
 * not kept across reopen). It then deletes the whole test folder.
 *
 * Depends on the same not-yet-wired `MainActivity.workspace` and the app auto-reopening its last
 * project on launch (WP5) that [ProjectSaveDeviceTest]'s own KDoc names.
 */
@RunWith(AndroidJUnit4::class)
class ProjectRestoreDeviceTest {
    @Test
    fun reopensTheSavedProjectWithMatchingStateAndNoLeftoverTempFiles() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val record = DeviceTestSupport.readRecord(context)

        val treeUri = Uri.parse(record.location.treeUri)
        val grantStillPersisted =
            context.contentResolver.persistedUriPermissions.any {
                it.uri == treeUri && it.isReadPermission && it.isWritePermission
            }
        assertTrue(grantStillPersisted, "The SAF grant from ProjectSaveDeviceTest must survive a force-stop")

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var session: ProjectSession? = null
            scenario.onActivity { activity -> session = activity.workspace.current.value }
            val reopened = assertNotNull(session, "MainActivity did not reopen the last project on launch")

            assertEquals(
                record.location,
                reopened.store.location,
                "The reopened project's location must match what ProjectSaveDeviceTest recorded",
            )

            val paths = runBlocking { reopened.store.list() }
            val tempLeftovers = paths.filter { it.endsWith(".tmp") || it.endsWith(".new") }
            assertTrue(tempLeftovers.isEmpty(), "recover() must leave no .tmp/.new files, found: $tempLeftovers")

            val hashes = runBlocking { DeviceTestSupport.hashFiles(paths, reopened.store::read) }
            assertEquals(record.fileHashes, hashes, "File hashes must match what ProjectSaveDeviceTest recorded")

            assertFalse(reopened.history.value.canUndo, "History is not kept across reopen (docs/PROJECT_MODEL.md §6)")

            var workspace: ProjectWorkspace? = null
            scenario.onActivity { activity -> workspace = activity.workspace }
            runBlocking { checkNotNull(workspace).closeCurrent() }

            deleteTestFolder(context, record.location)
        }
    }

    // Cleans up TypewrightDeviceTest-<ts>/ itself, straight through SAF (there is no
    // ProjectStore.deleteSelf -- a store only knows its own files, not its own containing
    // directory), and releases the persisted grant the same way "Remove from list" would (S01).
    private fun deleteTestFolder(
        context: android.content.Context,
        location: ProjectLocation.SafTree,
    ) {
        val treeUri = Uri.parse(location.treeUri)
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, location.documentId)
        DocumentsContract.deleteDocument(context.contentResolver, documentUri)
        context.contentResolver.releasePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }
}
