// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [ProjectWorkspace]: create/open become [ProjectWorkspace.current] and are remembered in recents; [ProjectWorkspace.reopenLast], [ProjectWorkspace.forget]. Runs on the JVM and Wasm. */
class ProjectWorkspaceTest {
    private val root = ProjectLocation.InMemory("root")

    private fun spec(name: String = "Fixture Project") =
        NewProjectSpec(name, Fixtures.manifest().brief, listOf("Latn"), listOf(NewMaster(ufo = Fixtures.ufoProject())))

    private fun workspace(scope: kotlinx.coroutines.CoroutineScope): Pair<ProjectWorkspace, FakeStorageProvider> {
        val storage = FakeStorageProvider()
        val workspace = ProjectWorkspace(storage, InMemoryAppConfigStore()) { Fixtures.env(scope) }
        return workspace to storage
    }

    @Test
    fun createOpensAndRemembersTheProject() =
        runTest {
            val (workspace, _) = workspace(backgroundScope)
            val result = workspace.create(root, spec())
            assertIs<CreateResult.Created>(result)
            assertEquals(result.session, workspace.current.value)
            assertEquals(1, workspace.recents.value.size)
            assertEquals(
                "Fixture Project",
                workspace.recents.value
                    .single()
                    .name,
            )
        }

    @Test
    fun openAnExistingLocationBecomesCurrentAndUpdatesTheSameRecentsEntry() =
        runTest {
            val (workspace, storage) = workspace(backgroundScope)
            val created = workspace.create(root, spec()) as CreateResult.Created
            val location = created.session.store.location
            workspace.closeCurrent()

            val reopened = workspace.open(location)
            assertIs<OpenResult.Opened>(reopened)
            assertEquals(reopened.session, workspace.current.value)
            assertEquals(1, workspace.recents.value.size)
        }

    @Test
    fun openOfAMissingLocationGivesDamaged() =
        runTest {
            val (workspace, storage) = workspace(backgroundScope)
            storage.markMissing(ProjectLocation.InMemory("ghost"))
            val result = workspace.open(ProjectLocation.InMemory("ghost"))
            assertIs<OpenResult.Damaged>(result)
        }

    @Test
    fun reopenLastReopensTheMostRecentlyOpenedProject() =
        runTest {
            val (workspace, _) = workspace(backgroundScope)
            workspace.create(root, spec("First"))
            workspace.closeCurrent()
            workspace.create(root, spec("Second"))
            workspace.closeCurrent()

            val result = workspace.reopenLast()
            assertIs<OpenResult.Opened>(result)
            assertEquals("Second", result.session.state.value.meta.manifest.name)
        }

    @Test
    fun reopenLastWithNoRecentsReturnsNull() =
        runTest {
            val (workspace, _) = workspace(backgroundScope)
            assertNull(workspace.reopenLast())
        }

    @Test
    fun closeCurrentClosesAndClearsCurrent() =
        runTest {
            val (workspace, _) = workspace(backgroundScope)
            workspace.create(root, spec())
            assertNotNull(workspace.current.value)
            workspace.closeCurrent()
            assertNull(workspace.current.value)
        }

    @Test
    fun rememberResumeUpdatesTheCurrentProjectsRecentsEntry() =
        runTest {
            val (workspace, _) = workspace(backgroundScope)
            workspace.create(root, spec())
            workspace.rememberResume(ResumePoint("draw", "regular", "A"))
            assertEquals(
                ResumePoint("draw", "regular", "A"),
                workspace.recents.value
                    .single()
                    .resume,
            )
        }

    @Test
    fun forgetRemovesTheEntryFromRecents() =
        runTest {
            val (workspace, _) = workspace(backgroundScope)
            val created = workspace.create(root, spec()) as CreateResult.Created
            val location = created.session.store.location
            workspace.closeCurrent()
            assertEquals(1, workspace.recents.value.size)
            workspace.forget(location)
            assertTrue(workspace.recents.value.isEmpty())
        }

    @Test
    fun openInMemoryNeverEntersRecents() =
        runTest {
            val (workspace, _) = workspace(backgroundScope)
            val zip = InMemoryProjectStore("Zip Project")
            // Build a minimal on-disk project to unzip semantics: create one for real, then read its files.
            val created = ProjectSession.create(zip, spec("Zip Project"), Fixtures.env(this)) as CreateResult.Created
            created.session.close()
            val files = zip.snapshot().files

            val result = workspace.openInMemory(files, "Zip Project")
            assertIs<OpenResult.Opened>(result)
            assertEquals(result.session, workspace.current.value)
            assertTrue(workspace.recents.value.isEmpty())
        }

    @Test
    fun flushDelegatesToTheCurrentSession() =
        runTest {
            val (workspace, _) = workspace(backgroundScope)
            val created = workspace.create(root, spec()) as CreateResult.Created
            created.session.execute(SetAdvance(GlyphRef("regular", "A"), 900))
            workspace.flush()
            val status = created.session.saveStatus.value
            assertTrue(status is SaveStatus.Saved || status is SaveStatus.TabOnly)
        }
}
