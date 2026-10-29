// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.imports

import com.asoc.typewright.project.ProjectLocation
import com.asoc.typewright.ui.deck.DeckSection
import com.asoc.typewright.ui.project.IN_MEMORY_WORKSPACE_ROOT
import com.asoc.typewright.ui.project.createProjectWorkspace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** What the audit tells a person about where their project will live, for the destinations that keep it in memory. */
class ImportDestinationsTest {
    @Test
    fun theBrowserBuildSaysTheProjectLivesInThisTabAndThatItCannotYetSaveItOut() {
        val text = BrowserTabImportDestination.describe(IN_MEMORY_WORKSPACE_ROOT, "Poppins")
        assertTrue("Nothing is written to disk" in text, text)
        assertTrue("this tab's memory" in text, text)
        assertTrue("closed or reloaded" in text, text)
        assertTrue("can't yet save it to your computer" in text, text)
        // The old wording sent people to a palette command that only reports that no download is wired up.
        assertFalse("Download project" in text, text)
        assertFalse("download it first" in text, text)
        assertFalse(BrowserTabImportDestination.persists)
    }

    @Test
    fun theAuditShownOnTheBrowserBuildCarriesThatSentenceAndMakesTheProjectInMemory() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val poppins = ImportFixtures.fontBytes("data/learn-faces/poppins/Poppins-Regular.ttf")
            val picker =
                object : FontFilePicker {
                    override val canPickUfoFolder: Boolean get() = false
                    override val ufoFolderUnavailableReason: String get() = "n/a"

                    override suspend fun pickFont(): PickResult = PickResult.Font("Poppins-Regular.ttf", poppins)

                    override suspend fun pickUfoFolder(): PickResult = pickFont()
                }
            val workspace = createProjectWorkspace(scope)
            val controller = FontImportController(workspace, picker, BrowserTabImportDestination, scope)
            controller.openFont(DeckSection.HOME)
            val auditing = withTimeout(20_000) { controller.state.first { it is ImportState.Auditing } } as ImportState.Auditing
            assertEquals(BrowserTabImportDestination.describe(null, "Poppins"), auditing.where)
            assertEquals(IN_MEMORY_WORKSPACE_ROOT, auditing.parent)

            controller.confirm()
            withTimeout(20_000) { controller.state.first { it == ImportState.Idle } }
            val session = workspace.current.value
            assertIs<ProjectLocation.InMemory>(session?.store?.location, "the project is held in memory, as the audit said")
            Unit
        }
}
