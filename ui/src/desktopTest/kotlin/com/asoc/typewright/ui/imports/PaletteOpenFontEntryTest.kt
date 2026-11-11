// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.imports

import androidx.compose.runtime.CompositionLocalProvider
import com.asoc.typewright.project.ProjectLocation
import com.asoc.typewright.ui.ScreenshotHarness
import com.asoc.typewright.ui.deck.DeckSection
import com.asoc.typewright.ui.glass.PaletteEntry
import com.asoc.typewright.ui.project.IN_MEMORY_WORKSPACE_ROOT
import com.asoc.typewright.ui.project.createProjectWorkspace
import com.asoc.typewright.ui.shell.LocalSectionNav
import com.asoc.typewright.ui.shell.SectionNav
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The command palette's "Open a font…" row: it starts the flow over the section the palette was opened on, and says so where it cannot. */
class PaletteOpenFontEntryTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private object Destination : ImportDestination {
        override val persists: Boolean get() = false

        override suspend fun parentFolder(): ProjectLocation = IN_MEMORY_WORKSPACE_ROOT

        override fun describe(
            parent: ProjectLocation?,
            folderName: String,
        ): String = "test"
    }

    /** The palette's font-import entries as composed under [controller] and a router on [section]. */
    private fun entriesUnder(
        controller: FontImportController?,
        section: DeckSection,
        dismiss: () -> Unit,
    ): List<PaletteEntry> {
        var entries: List<PaletteEntry> = emptyList()
        ScreenshotHarness.capture(name = "palette-entry-probe", width = 100, height = 100) {
            CompositionLocalProvider(
                LocalFontImport provides controller,
                LocalSectionNav provides SectionNav(current = section, goTo = {}),
            ) {
                entries = rememberFontImportPaletteEntries(dismiss)
            }
        }
        return entries
    }

    @Test
    fun theEntryStartsTheFlowOverTheSectionThePaletteWasOpenedOnAfterDismissingThePalette() {
        val never = CompletableDeferred<PickResult>()
        val picker =
            object : FontFilePicker {
                override val canPickUfoFolder: Boolean get() = false
                override val ufoFolderUnavailableReason: String get() = "n/a"

                override suspend fun pickFont(): PickResult = never.await()

                override suspend fun pickUfoFolder(): PickResult = never.await()
            }
        val controller = FontImportController(createProjectWorkspace(scope), picker, Destination, scope)
        var dismissed = 0
        val entries = entriesUnder(controller, DeckSection.DRAW) { dismissed++ }

        val entry = entries.single()
        assertEquals("Open a font…", entry.label)
        val result = checkNotNull(entry.run).invoke()
        assertEquals(1, dismissed, "the palette closes first, so S03 is not drawn under it")
        assertTrue(result.startsWith("Open a font…"), result)
        val state = controller.state.value
        assertIs<ImportState.Picking>(state)
        assertEquals(DeckSection.DRAW, state.from, "Close on S03 returns to the section the palette was opened on")
        controller.cancel()
    }

    @Test
    fun whereNothingProvidedTheFlowTheEntryIsStillThereAndSaysSoInsteadOfDoingNothing() {
        var dismissed = 0
        val entry = entriesUnder(null, DeckSection.HOME) { dismissed++ }.single()
        val result = checkNotNull(entry.run).invoke()
        assertEquals(0, dismissed)
        assertTrue("not available on this screen" in result, result)
    }
}
