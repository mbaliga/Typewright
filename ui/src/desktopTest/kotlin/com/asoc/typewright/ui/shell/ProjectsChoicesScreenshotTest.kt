// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.shell

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.asoc.typewright.project.ProjectLocation
import com.asoc.typewright.project.ProjectWorkspace
import com.asoc.typewright.project.imports.ImportOutcome
import com.asoc.typewright.project.imports.ImportReview
import com.asoc.typewright.project.imports.ProjectImporter
import com.asoc.typewright.project.imports.reviewFontFile
import com.asoc.typewright.ui.ScreenshotHarness
import com.asoc.typewright.ui.imports.FontFilePicker
import com.asoc.typewright.ui.imports.FontImportController
import com.asoc.typewright.ui.imports.ImportDestination
import com.asoc.typewright.ui.imports.ImportFixtures
import com.asoc.typewright.ui.imports.ImportState
import com.asoc.typewright.ui.imports.LocalFontImport
import com.asoc.typewright.ui.imports.PickResult
import com.asoc.typewright.ui.project.IN_MEMORY_WORKSPACE_ROOT
import com.asoc.typewright.ui.project.LocalProjectWorkspace
import com.asoc.typewright.ui.project.SampleProject
import com.asoc.typewright.ui.project.createProjectWorkspace
import com.asoc.typewright.ui.tokens.CanvasTextures
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * S01's choices: three ink-text choices on the empty state and none under a list of projects, with a
 * UFO folder opened from under Open a font. What each does is checked by tapping it on the rendered
 * screen; the PNGs under `ui/build/screenshots/` are for looking at.
 */
class ProjectsChoicesScreenshotTest {
    private class RecordingPicker(
        override val canPickUfoFolder: Boolean = true,
        override val ufoFolderUnavailableReason: String? = if (canPickUfoFolder) null else "the folder dialog isn't here",
    ) : FontFilePicker {
        val fonts = AtomicInteger()
        val folders = AtomicInteger()
        private val never = CompletableDeferred<PickResult>()

        override suspend fun pickFont(): PickResult {
            fonts.incrementAndGet()
            return never.await()
        }

        override suspend fun pickUfoFolder(): PickResult {
            folders.incrementAndGet()
            return never.await()
        }
    }

    private object Destination : ImportDestination {
        override val persists: Boolean get() = false

        override suspend fun parentFolder(): ProjectLocation = IN_MEMORY_WORKSPACE_ROOT

        override fun describe(
            parent: ProjectLocation?,
            folderName: String,
        ): String = "in memory"
    }

    private fun controllerOver(
        workspace: ProjectWorkspace,
        picker: FontFilePicker,
    ): FontImportController = FontImportController(workspace, picker, Destination, CoroutineScope(SupervisorJob() + Dispatchers.Default))

    private fun emptyWorkspace(): ProjectWorkspace = createProjectWorkspace(CoroutineScope(SupervisorJob() + Dispatchers.Default))

    private fun render(
        name: String,
        workspace: ProjectWorkspace,
        controller: FontImportController,
    ) = ScreenshotHarness.capture(name = name, width = 780, height = 1688, density = 2f) {
        CompositionLocalProvider(LocalProjectWorkspace provides workspace, LocalFontImport provides controller) {
            ProjectsScreen(texture = CanvasTextures.PAPER, onOpened = {})
        }
    }

    /** Taps ([x], [y]) in pixels on S01 at density 2 and runs [afterTap] with the scene still open. */
    private fun tap(
        workspace: ProjectWorkspace,
        controller: FontImportController,
        x: Float,
        y: Float,
    ) {
        ImageComposeScene(width = 780, height = 1688, density = Density(2f)) {
            CompositionLocalProvider(LocalProjectWorkspace provides workspace, LocalFontImport provides controller) {
                ProjectsScreen(texture = CanvasTextures.PAPER, onOpened = {})
            }
        }.use { scene ->
            scene.render(0)
            val at = Offset(x, y)
            val primary = PointerButtons(isPrimaryPressed = true)
            scene.sendPointerEvent(PointerEventType.Move, at)
            scene.render(16_000_000)
            scene.sendPointerEvent(PointerEventType.Press, at, buttons = primary, button = PointerButton.Primary)
            scene.render(32_000_000)
            scene.sendPointerEvent(PointerEventType.Release, at, button = PointerButton.Primary)
            scene.render(48_000_000)
        }
    }

    private fun awaitState(
        controller: FontImportController,
        predicate: (ImportState) -> Boolean,
    ) {
        val deadline = System.nanoTime() + 20_000_000_000L
        while (!predicate(controller.state.value)) {
            check(System.nanoTime() < deadline) { "the import never reached the state: ${controller.state.value}" }
            Thread.sleep(5)
        }
    }

    @Test
    fun theEmptyStateHasTheThreeChoicesTheBoardNamesAndNoOthers() {
        assertEquals(
            listOf("Start from drawings", "Open a font", "Try the sample (Hyle Deco)"),
            startChoices(hasProjects = false, importAvailable = true).map { it.title },
        )
        assertEquals(emptyList<StartChoice>(), startChoices(hasProjects = true, importAvailable = true), "a list of projects stands alone")
        assertEquals(
            listOf("Start from drawings"),
            startChoices(hasProjects = false, importAvailable = false).map { it.title },
            "without an import flow only the drawings choice is offered",
        )
    }

    @Test
    fun aUfoFolderIsOfferedUnderOpenAFontOrTheReasonItCannotBeIsGiven() {
        val workspace = emptyWorkspace()
        assertEquals(UfoFolderOption.Offered, ufoFolderOption(controllerOver(workspace, RecordingPicker(canPickUfoFolder = true))))
        val none = ufoFolderOption(controllerOver(workspace, RecordingPicker(canPickUfoFolder = false)))
        assertEquals(UfoFolderOption.Unavailable("the folder dialog isn't here"), none)
        val noReason =
            ufoFolderOption(controllerOver(workspace, RecordingPicker(canPickUfoFolder = false, ufoFolderUnavailableReason = null)))
        assertIs<UfoFolderOption.Unavailable>(noReason)
        assertTrue(noReason.reason.isNotBlank())
    }

    @Test
    fun tappingEachChoiceOnTheEmptyScreenDoesWhatItSays() {
        val workspace = emptyWorkspace()
        val picker = RecordingPicker()
        val controller = controllerOver(workspace, picker)
        render("projects-choices-empty", workspace, controller)

        tap(workspace, controller, OPEN_FONT_X, OPEN_FONT_Y)
        awaitState(controller) { it is ImportState.Picking }
        assertEquals(1, picker.fonts.get(), "Open a font asks the font picker")
        assertEquals(0, picker.folders.get())
        controller.cancel()

        tap(workspace, controller, UFO_X, UFO_Y)
        awaitState(controller) { it is ImportState.Picking }
        assertEquals(1, picker.folders.get(), "or a UFO folder asks the folder picker")
        assertEquals(1, picker.fonts.get())
        controller.cancel()

        tap(workspace, controller, SAMPLE_X, SAMPLE_Y)
        awaitState(controller) { it is ImportState.Auditing }
        assertTrue(
            (controller.state.value as ImportState.Auditing).sample,
            "Try the sample audits the bundled font and says it is the sample",
        )
        controller.cancel()
    }

    @Test
    fun withAProjectInTheListNoChoiceIsDrawnOrTappable() {
        val workspace = SampleProject.workspace()
        val picker = RecordingPicker()
        val controller = controllerOver(workspace, picker)
        val shot = render("projects-choices-with-project", workspace, controller)

        // Below the one project row the screen is empty paper down to the deck: not a mark of ink in the band where the choices were.
        assertEquals(
            0,
            inkIn(shot.bitmap, BELOW_ROW_TOP, BELOW_ROW_BOTTOM),
            "no choice (or anything else) is drawn under the list of projects",
        )
        // The same band on the empty screen is where the choices are, so the check above is looking in the right place.
        val empty = render("projects-choices-empty", emptyWorkspace(), controllerOver(emptyWorkspace(), RecordingPicker()))
        assertTrue(inkIn(empty.bitmap, BELOW_ROW_TOP, BELOW_ROW_BOTTOM) > 1_000, "the band holds the choices on the empty screen")

        tap(workspace, controller, OPEN_FONT_X, OPEN_FONT_Y)
        tap(workspace, controller, UFO_X, UFO_Y)
        tap(workspace, controller, SAMPLE_X, SAMPLE_Y)
        Thread.sleep(200)
        assertEquals(ImportState.Idle, controller.state.value, "nothing is there to tap")
        assertEquals(0, picker.fonts.get() + picker.folders.get())
    }

    /** How many sampled pixels between rows [top] and [bottom] are ink (much darker than the paper), not grain or shading. */
    private fun inkIn(
        bitmap: androidx.compose.ui.graphics.ImageBitmap,
        top: Int,
        bottom: Int,
    ): Int {
        val pixels = bitmap.toPixelMap()
        var ink = 0
        for (x in 0 until pixels.width step 2) {
            for (y in top until bottom step 2) {
                val c = pixels[x, y]
                if ((c.red + c.green + c.blue) / 3f < INK_BELOW) ink++
            }
        }
        return ink
    }

    @Test
    fun withTwoProjectsBothRowsAreDrawnAndStillNoChoices() {
        val workspace = SampleProject.workspace()
        val ready =
            assertIs<ImportReview.Ready>(
                reviewFontFile("Poppins-Regular.ttf", ImportFixtures.fontBytes("data/learn-faces/poppins/Poppins-Regular.ttf")),
            )
        val outcome = runBlocking { ProjectImporter(workspace).create(ready.candidate, IN_MEMORY_WORKSPACE_ROOT, "Poppins") }
        assertIs<ImportOutcome.Imported>(outcome)
        val shot = render("projects-choices-two-projects", workspace, controllerOver(workspace, RecordingPicker()))
        val one = render("projects-choices-with-project", SampleProject.workspace(), controllerOver(emptyWorkspace(), RecordingPicker()))
        // Two rows are more ink than one, and the band below both rows is bare paper.
        assertTrue(inkIn(shot.bitmap, 0, 400) > inkIn(one.bitmap, 0, 400), "the second project's row is drawn")
        assertEquals(0, inkIn(shot.bitmap, 400, BELOW_ROW_BOTTOM), "nothing is drawn under the rows, no choices among it")
    }

    private companion object {
        const val INK_BELOW = 0.7f

        // Pixels on a 780 x 1688 screen at density 2, read off projects-choices-empty.png.
        const val OPEN_FONT_X = 120f
        const val OPEN_FONT_Y = 346f
        const val UFO_X = 120f
        const val UFO_Y = 463f
        const val SAMPLE_X = 120f
        const val SAMPLE_Y = 531f
        const val BELOW_ROW_TOP = 260
        const val BELOW_ROW_BOTTOM = 1380
    }
}
