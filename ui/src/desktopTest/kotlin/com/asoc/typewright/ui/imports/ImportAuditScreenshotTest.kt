// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.imports

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import com.asoc.typewright.core.font.sfnt.readSfntFont
import com.asoc.typewright.core.font.sfnt.toUfoProject
import com.asoc.typewright.core.font.ufo.writeUfoProject
import com.asoc.typewright.project.ProjectLocation
import com.asoc.typewright.project.imports.UfoFolder
import com.asoc.typewright.ui.Screenshot
import com.asoc.typewright.ui.ScreenshotHarness
import com.asoc.typewright.ui.deck.DeckSection
import com.asoc.typewright.ui.project.IN_MEMORY_WORKSPACE_ROOT
import com.asoc.typewright.ui.project.LocalProjectWorkspace
import com.asoc.typewright.ui.project.SampleProject
import com.asoc.typewright.ui.project.createProjectWorkspace
import com.asoc.typewright.ui.shell.HomeScreen
import com.asoc.typewright.ui.shell.HomeView
import com.asoc.typewright.ui.tokens.CanvasTextures
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S03, the licence check, drawn over the section the import started from, for each verdict: the
 * PNGs land in `ui/build/screenshots/`. The assertions are that the verdict is shown as a word and a
 * shape (the controller's state carries them) and that the audit covers the section underneath,
 * which stays drawn but faded.
 */
class ImportAuditScreenshotTest {
    private class Picker(
        private val name: String,
        private val bytes: ByteArray,
    ) : FontFilePicker {
        override val canPickUfoFolder: Boolean get() = false
        override val ufoFolderUnavailableReason: String get() = "no folder picker in this test"

        override suspend fun pickFont(): PickResult = PickResult.Font(name, bytes)

        override suspend fun pickUfoFolder(): PickResult = PickResult.Failed("no folder picker in this test")
    }

    private object Destination : ImportDestination {
        override val persists: Boolean get() = false

        override suspend fun parentFolder(): ProjectLocation = IN_MEMORY_WORKSPACE_ROOT

        override fun describe(
            parent: ProjectLocation?,
            folderName: String,
        ): String = "Typewright will make the folder $folderName inside ~/Fonts and write the project there."
    }

    private val workspace = SampleProject.workspace()

    private fun auditing(
        name: String,
        bytes: ByteArray,
        from: DeckSection,
        newName: String? = null,
    ): FontImportController {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = FontImportController(createProjectWorkspace(scope), Picker(name, bytes), Destination, scope)
        controller.openFont(from)
        runBlocking { withTimeout(20_000) { controller.state.first { it is ImportState.Auditing } } }
        if (newName != null) controller.setNewName(newName)
        return controller
    }

    private fun shot(
        name: String,
        controller: FontImportController,
    ): Screenshot =
        ScreenshotHarness.capture(name = name, width = 780, height = 1688, density = 2f) {
            CompositionLocalProvider(LocalProjectWorkspace provides workspace) {
                Box(Modifier.fillMaxSize()) {
                    HomeScreen(texture = CanvasTextures.PAPER, initialView = HomeView.SPECIMEN)
                    FontImportHost(controller = controller, texture = CanvasTextures.PAPER)
                }
            }
        }

    private fun bareHome(): Screenshot =
        ScreenshotHarness.capture(name = "import-audit-home-only", width = 780, height = 1688, density = 2f) {
            CompositionLocalProvider(LocalProjectWorkspace provides workspace) {
                HomeScreen(texture = CanvasTextures.PAPER, initialView = HomeView.SPECIMEN)
            }
        }

    private fun differentPixels(
        a: Screenshot,
        b: Screenshot,
    ): Double {
        val pa = a.bitmap.toPixelMap()
        val pb = b.bitmap.toPixelMap()
        var different = 0
        var total = 0
        for (x in 0 until pa.width step 2) {
            for (y in 0 until pa.height step 2) {
                total++
                if (pa[x, y] != pb[x, y]) different++
            }
        }
        return different.toDouble() / total
    }

    @Test
    fun everyVerdictIsShownOverTheSectionItStartedFromAndSaysItInWordsAndShape() {
        val home = bareHome()
        val cases =
            listOf(
                Triple(
                    "import-audit-fine",
                    auditing(
                        "Poppins-Regular.ttf",
                        ImportFixtures.fontBytes("data/learn-faces/poppins/Poppins-Regular.ttf"),
                        DeckSection.HOME,
                    ),
                    "✓",
                ),
                Triple(
                    "import-audit-rename",
                    auditing(
                        "Limelight-Regular.ttf",
                        ImportFixtures.fontBytes("data/learn-faces/limelight/Limelight-Regular.ttf"),
                        DeckSection.HOME,
                    ),
                    "◐",
                ),
                Triple(
                    "import-audit-rename-clash",
                    auditing(
                        "Limelight-Regular.ttf",
                        ImportFixtures.fontBytes("data/learn-faces/limelight/Limelight-Regular.ttf"),
                        DeckSection.HOME,
                        "Limelight Redraw",
                    ),
                    "◐",
                ),
                Triple("import-audit-uncertain", auditing("Unlicensed.ttf", ImportFixtures.unlicensedPoppins(), DeckSection.HOME), "◐"),
                Triple("import-audit-refused", auditing("Proprietary.ttf", ImportFixtures.proprietaryPoppins(), DeckSection.HOME), "●"),
            )
        for ((name, controller, shape) in cases) {
            val state = controller.state.value as ImportState.Auditing
            val review = state.review
            val audit =
                (review as? com.asoc.typewright.project.imports.ImportReview.Ready)?.candidate?.audit
                    ?: (review as com.asoc.typewright.project.imports.ImportReview.Refused).audit
            assertEquals(shape, audit.verdict.shape, "$name: the verdict's shape")
            assertTrue(audit.verdict.title.isNotBlank() && audit.verdictWords.isNotBlank(), "$name: the verdict has its words")
            val s = shot(name, controller)
            assertTrue(s.file.length() > 0)
            assertTrue(differentPixels(s, home) > 0.2, "$name: the audit should cover most of the section underneath")
        }
    }

    /** A picker that hands over a UFO folder (read in memory, as the desktop reader would hand it over) that reserves its name. */
    private class UfoPicker : FontFilePicker {
        override val canPickUfoFolder: Boolean get() = true
        override val ufoFolderUnavailableReason: String? get() = null

        override suspend fun pickFont(): PickResult = pickUfoFolder()

        override suspend fun pickUfoFolder(): PickResult {
            val hyle = readSfntFont(ImportFixtures.fontBytes("fonts/HyleDeco-Regular.ttf")).toUfoProject()
            val info =
                hyle.fontInfo.copy(
                    familyName = "Foo",
                    postscriptFontName = "Foo-Regular",
                    copyright = "Copyright 2012 The Foo Project Authors, with the Reserved Font Name Foo",
                )
            val files = writeUfoProject(hyle.copy(fontInfo = info))
            return PickResult.Ufo(UfoFolder("Foo-Regular.ufo", "/home/me/Fonts", files, emptyList(), listOf("Foo-Bold.ufo"), emptyList()))
        }
    }

    /** A fresh flow on the UFO that reserves its name, with a new name typed and the copy chosen or not (the host ends its flow when a capture ends, so each picture needs its own). */
    private fun ufoAuditing(asCopy: Boolean): FontImportController {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = FontImportController(createProjectWorkspace(scope), UfoPicker(), Destination, scope)
        controller.openUfoFolder(DeckSection.HOME)
        runBlocking { withTimeout(20_000) { controller.state.first { it is ImportState.Auditing } } }
        controller.setNewName("Bar Redraw")
        controller.setAsCopy(asCopy)
        return controller
    }

    @Test
    fun aUfoThatReservesANameOffersToRenameItWhereItLiesOrToImportACopy() {
        val home = bareHome()
        val inPlaceFlow = ufoAuditing(asCopy = false)
        val copyFlow = ufoAuditing(asCopy = true)
        val inPlaceState = inPlaceFlow.state.value as ImportState.Auditing
        val copyState = copyFlow.state.value as ImportState.Auditing
        assertTrue(inPlaceState.offersCopy && copyState.offersCopy)
        assertTrue(!inPlaceState.asCopy && copyState.asCopy)
        val inPlace = shot("import-audit-ufo-rename-in-place", inPlaceFlow)
        val copy = shot("import-audit-ufo-rename-copy", copyFlow)
        // Both pictures are the audit over the section (not the bare section), and they differ from each other in the choice they show.
        assertTrue(differentPixels(inPlace, home) > 0.2, "the in-place audit should cover the section underneath")
        assertTrue(differentPixels(copy, home) > 0.2, "the copy audit should cover the section underneath")
        assertTrue(differentPixels(inPlace, copy) > 0.003, "choosing a copy should change what the audit says")
    }

    @Test
    fun theWaitWhileAFileIsReadIsDrawnAsTheFlowHoldsItsState() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val held =
            object : CoroutineDispatcher() {
                override fun dispatch(
                    context: kotlin.coroutines.CoroutineContext,
                    block: Runnable,
                ) = Unit
            }
        val poppins = ImportFixtures.fontBytes("data/learn-faces/poppins/Poppins-Regular.ttf")
        val controller =
            FontImportController(createProjectWorkspace(scope), Picker("Poppins-Regular.ttf", poppins), Destination, scope, held)
        controller.openFont(DeckSection.HOME)
        runBlocking { withTimeout(20_000) { controller.state.first { it is ImportState.Reading } } }
        val s = shot("import-audit-reading", controller)
        assertTrue(s.file.length() > 0)
    }

    @Test
    fun aMessageIsDrawnOverItsSectionAndLeavingTheScreenEndsTheFlow() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val failing =
            object : FontFilePicker {
                override val canPickUfoFolder: Boolean get() = false
                override val ufoFolderUnavailableReason: String get() = "n/a"

                override suspend fun pickFont(): PickResult =
                    PickResult.Failed("This computer has no screen to show a file dialog on, so a font can't be chosen here.")

                override suspend fun pickUfoFolder(): PickResult = pickFont()
            }
        val controller = FontImportController(createProjectWorkspace(scope), failing, Destination, scope)
        controller.openFont(DeckSection.CHECK)
        runBlocking { withTimeout(20_000) { controller.state.first { it is ImportState.Message } } }
        // The host cancels the flow when it leaves the screen, so what the state says is read before the capture ends.
        assertEquals(DeckSection.CHECK, (controller.state.value as ImportState.Message).from)
        val s = shot("import-audit-message", controller)
        assertTrue(s.file.length() > 0)
        assertEquals(ImportState.Idle, controller.state.value, "leaving the screen ends the flow")
    }
}
