// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.project

import com.asoc.typewright.project.ProjectWorkspace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * End-to-end proof that the command palette's project entries (`CommandPalette.kt`'s own
 * `projectPaletteEntries`, docs/PROJECT_MODEL.md §13's palette table) do the real
 * [ProjectWorkspace] call through this worktree's stopgap wiring, not a stub: [createProjectWorkspace]'s
 * own in-memory [InMemoryStorageProvider]/[InMemoryAppConfigStore] (the fallback for WP3's real
 * desktop folder-and-file storage, not yet in this worktree) stand in for "a temp dir" here --
 * every write really happens, is really read back, and a name clash is really retried, exactly as
 * a real filesystem store would behave for the same sequence of calls.
 */
class ProjectPaletteActionsTest {
    private fun workspace(): ProjectWorkspace = createProjectWorkspace(CoroutineScope(Dispatchers.Default))

    @Test
    fun newProjectCreatesABlankUntitledProjectAndItBecomesCurrent() =
        runBlocking {
            val workspace = workspace()
            val picker = InMemoryProjectFolderPicker(workspace)

            val result = createUntitledProject(workspace, picker)

            assertTrue(result.contains("created \"Untitled\""), result)
            val session = workspace.current.value
            requireNotNull(session) { "expected a project to be open after New project…" }
            assertEquals("Untitled", session.state.value.meta.manifest.name)
            assertEquals(
                "Regular",
                session.state.value.font.masters
                    .single()
                    .styleName,
            )
        }

    @Test
    fun aSecondNewProjectRetriesAsUntitled2OnANameClash() =
        runBlocking {
            val workspace = workspace()
            val picker = InMemoryProjectFolderPicker(workspace)

            createUntitledProject(workspace, picker)
            val second = createUntitledProject(workspace, picker)

            assertTrue(second.contains("created \"Untitled 2\""), second)
            assertEquals(
                "Untitled 2",
                workspace.current.value
                    ?.state
                    ?.value
                    ?.meta
                    ?.manifest
                    ?.name,
            )
        }

    @Test
    fun openProjectReopensTheMostRecentlyTouchedProjectAfterItWasClosed() =
        runBlocking {
            val workspace = workspace()
            val picker = InMemoryProjectFolderPicker(workspace)
            createUntitledProject(workspace, picker)
            closeCurrentProject(workspace)
            assertNull(workspace.current.value)

            val result = openPickedProject(workspace, picker)

            assertTrue(result.contains("opened \"Untitled\""), result)
            assertEquals(
                "Untitled",
                workspace.current.value
                    ?.state
                    ?.value
                    ?.meta
                    ?.manifest
                    ?.name,
            )
        }

    @Test
    fun openProjectWithNothingToOpenSaysSoHonestly() =
        runBlocking {
            val workspace = workspace()
            val picker = InMemoryProjectFolderPicker(workspace)

            val result = openPickedProject(workspace, picker)

            assertTrue(result.contains("nothing to open yet"), result)
        }

    @Test
    fun saveNowFlushesTheOpenInMemoryProjectAndReportsItsRealStatus() =
        runBlocking {
            val workspace = workspace()
            val picker = InMemoryProjectFolderPicker(workspace)
            createUntitledProject(workspace, picker)

            // InMemoryProjectStore is Durability.TAB_ONLY -- the honest status word for it is
            // "in this tab only", never "saved" (that would claim persistence this fallback store
            // does not have).
            val result = saveProjectNow(workspace)

            assertEquals("Save now: in this tab only.", result)
        }

    @Test
    fun saveNowWithNoProjectOpenSaysSoHonestly() =
        runBlocking {
            assertEquals("Save now: no project is open.", saveProjectNow(workspace()))
        }

    @Test
    fun closeProjectClosesTheOpenProjectAndClearsCurrent() =
        runBlocking {
            val workspace = workspace()
            val picker = InMemoryProjectFolderPicker(workspace)
            createUntitledProject(workspace, picker)

            val result = closeCurrentProject(workspace)

            assertEquals("Close project: closed.", result)
            assertNull(workspace.current.value)
        }

    @Test
    fun closeProjectWithNoneOpenSaysSoHonestly() =
        runBlocking {
            assertEquals("Close project: no project is open.", closeCurrentProject(workspace()))
        }

    @Test
    fun downloadProjectZipBuildsRealBytesForTheOpenProject() =
        runBlocking {
            val workspace = workspace()
            val picker = InMemoryProjectFolderPicker(workspace)
            createUntitledProject(workspace, picker)

            val result = downloadProjectZip(workspace)

            assertTrue(result.startsWith("Download project (.zip): built "), result)
            assertTrue(result.contains(" bytes"), result)
        }

    @Test
    fun downloadProjectZipWithNoProjectOpenSaysSoHonestly() =
        runBlocking {
            assertEquals("Download project (.zip): no project is open.", downloadProjectZip(workspace()))
        }
}
