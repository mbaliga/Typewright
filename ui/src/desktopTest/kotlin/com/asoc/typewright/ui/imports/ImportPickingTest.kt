// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.imports

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.asoc.typewright.ui.ScreenshotHarness
import com.asoc.typewright.ui.deck.DeckSection
import com.asoc.typewright.ui.project.LocalProjectWorkspace
import com.asoc.typewright.ui.project.SampleProject
import com.asoc.typewright.ui.project.createProjectWorkspace
import com.asoc.typewright.ui.shell.HomeScreen
import com.asoc.typewright.ui.shell.HomeView
import com.asoc.typewright.ui.tokens.CanvasTextures
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * While the platform's file picker is open the import draws its own screen with the deck's Close, so a
 * picker that never answers (a browser that sends no cancel) cannot leave the person stuck behind nothing.
 */
class ImportPickingTest {
    private val workspace = SampleProject.workspace()

    private class NeverPicker : FontFilePicker {
        val never = CompletableDeferred<PickResult>()
        override val canPickUfoFolder: Boolean get() = false
        override val ufoFolderUnavailableReason: String get() = "n/a"

        override suspend fun pickFont(): PickResult = never.await()

        override suspend fun pickUfoFolder(): PickResult = never.await()
    }

    private fun picking(): FontImportController {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = FontImportController(createProjectWorkspace(scope), NeverPicker(), InMemoryImportDestination, scope)
        controller.openFont(DeckSection.HOME)
        return controller
    }

    @Test
    fun theWaitForThePickerIsDrawnAndCloseEndsIt() {
        val controller = picking()
        assertIs<ImportState.Picking>(controller.state.value)
        ImageComposeScene(width = 780, height = 1688, density = Density(2f)) {
            CompositionLocalProvider(LocalProjectWorkspace provides workspace) {
                Box(Modifier.fillMaxSize()) {
                    HomeScreen(texture = CanvasTextures.PAPER, initialView = HomeView.SPECIMEN)
                    FontImportHost(controller = controller, texture = CanvasTextures.PAPER)
                }
            }
        }.use { scene ->
            scene.render(0)
            assertIs<ImportState.Picking>(controller.state.value, "the host did not end the wait on its own")
            // The deck's left circle, the Close, at the bottom left of the screen.
            val at = Offset(CLOSE_X, CLOSE_Y)
            val primary = PointerButtons(isPrimaryPressed = true)
            scene.sendPointerEvent(PointerEventType.Move, at)
            scene.render(16_000_000)
            scene.sendPointerEvent(PointerEventType.Press, at, buttons = primary, button = PointerButton.Primary)
            scene.render(32_000_000)
            scene.sendPointerEvent(PointerEventType.Release, at, button = PointerButton.Primary)
            scene.render(48_000_000)
            assertEquals(ImportState.Idle, controller.state.value, "tapping Close on the waiting screen ends the wait")
        }
    }

    @Test
    fun theWaitingScreenIsPictured() {
        val controller = picking()
        val shot =
            ScreenshotHarness.capture(name = "import-audit-picking", width = 780, height = 1688, density = 2f) {
                CompositionLocalProvider(LocalProjectWorkspace provides workspace) {
                    Box(Modifier.fillMaxSize()) {
                        HomeScreen(texture = CanvasTextures.PAPER, initialView = HomeView.SPECIMEN)
                        FontImportHost(controller = controller, texture = CanvasTextures.PAPER)
                    }
                }
            }
        assertEquals(true, shot.file.length() > 0)
        runBlocking { withTimeout(10_000) { controller.state.first { it == ImportState.Idle } } }
    }

    private companion object {
        // Pixels at density 2 on a 780 x 1688 screen; read off import-audit-picking.png.
        const val CLOSE_X = 80f
        const val CLOSE_Y = 1486f
    }
}
