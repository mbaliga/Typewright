// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.shell

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.asoc.typewright.project.CreateResult
import com.asoc.typewright.project.ProjectSession
import com.asoc.typewright.project.ProjectWorkspace
import com.asoc.typewright.ui.ScreenshotHarness
import com.asoc.typewright.ui.project.InMemoryProjectFolderPicker
import com.asoc.typewright.ui.project.LocalProjectWorkspace
import com.asoc.typewright.ui.project.OpenProject
import com.asoc.typewright.ui.project.SampleProject
import com.asoc.typewright.ui.project.blankNewProjectSpec
import com.asoc.typewright.ui.project.createProjectWorkspace
import com.asoc.typewright.ui.tokens.CanvasTextures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Font info page drawn over a real project, as PNGs under `ui/build/screenshots/` for a person to set beside
 * ui/v1-screens/FontInfo.dc.html, and driven the way a person drives it: a link that lands on a value opens it for typing,
 * what is typed and kept (Enter) reaches the project as one change, and Esc puts the old value back.
 */
@OptIn(InternalComposeUiApi::class)
class FontInfoScreenTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun blankWorkspace(name: String = "Blank"): ProjectWorkspace =
        runBlocking {
            val workspace = createProjectWorkspace(scope)
            val parent = checkNotNull(InMemoryProjectFolderPicker(workspace).pickNewProjectParent())
            check(workspace.create(parent, blankNewProjectSpec(name)) is CreateResult.Created)
            workspace
        }

    private fun session(workspace: ProjectWorkspace): ProjectSession = checkNotNull(workspace.current.value)

    private fun info(session: ProjectSession) =
        session.state.value.font.masters
            .first()
            .ufo.fontInfo

    /** The page in a scene at 390 x 844 dp (density 2), driven frame by frame. */
    private class Page(
        private val scene: ImageComposeScene,
    ) {
        private var clock = 0L

        fun frames(count: Int) {
            repeat(count) {
                clock += FRAME_NS
                scene.render(clock)
            }
        }

        /** Renders a frame and keeps it as `ui/build/screenshots/<name>.png`. */
        fun shot(name: String): Image {
            clock += FRAME_NS
            val image = scene.render(clock)
            File(ScreenshotHarness.outputDir, "$name.png").writeBytes(checkNotNull(image.encodeToData(EncodedImageFormat.PNG)).bytes)
            return image
        }

        /** Types [text] into the open text field by its own accessibility action, which is how an assistive tool types. */
        fun type(text: String) {
            val field = textFields().firstOrNull() ?: error("no text field is open to type into")
            val action = checkNotNull(field.config[SemanticsActions.SetText].action) { "the field takes no text" }
            check(action(AnnotatedString(text))) { "the field refused the text" }
            frames(3)
        }

        /** How many text fields are on the page. */
        fun openFields(): Int = textFields().size

        fun enter() = key(Key.Enter)

        fun escape() = key(Key.Escape)

        private fun key(key: Key) {
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
            frames(1)
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
            frames(4)
        }

        fun click(
            x: Float,
            y: Float,
        ) {
            val at = Offset(x, y)
            scene.sendPointerEvent(PointerEventType.Move, at)
            frames(1)
            scene.sendPointerEvent(
                PointerEventType.Press,
                at,
                buttons = PointerButtons(isPrimaryPressed = true),
                button = PointerButton.Primary,
            )
            frames(1)
            scene.sendPointerEvent(PointerEventType.Release, at, button = PointerButton.Primary)
            frames(4)
        }

        private fun textFields(): List<SemanticsNode> {
            val all = mutableListOf<SemanticsNode>()

            fun walk(node: SemanticsNode) {
                all += node
                node.children.forEach(::walk)
            }
            scene.semanticsOwners.forEach { walk(it.unmergedRootSemanticsNode) }
            return all.filter { SemanticsActions.SetText in it.config }
        }

        private companion object {
            const val FRAME_NS = 16_000_000L
        }
    }

    /** Runs [block] against the page over [workspace]'s project, with [focus] open for typing. */
    private fun withPage(
        workspace: ProjectWorkspace,
        focus: FontInfoField? = null,
        block: (Page) -> Unit,
    ) {
        ImageComposeScene(width = 780, height = 1688, density = Density(2f)) {
            CompositionLocalProvider(LocalProjectWorkspace provides workspace) {
                val open = checkNotNull(workspace.current.value)
                val state by open.state.collectAsState()
                FontInfoScreen(texture = CanvasTextures.PAPER, project = OpenProject(open, state), onBack = {}, focus = focus)
            }
        }.use { scene ->
            val page = Page(scene)
            page.frames(8)
            block(page)
        }
    }

    private fun inkIn(
        image: Image,
        top: Int,
        bottom: Int,
    ): Int {
        val pixels = image.toComposeImageBitmap().toPixelMap()
        val background = pixels[0, 0]
        var ink = 0
        for (y in top until bottom step 2) for (x in 0 until pixels.width step 2) if (pixels[x, y] != background) ink++
        return ink
    }

    @Test
    fun aBlankFontIsDrawnWithItsMissingValuesNamedAsNotSet() {
        withPage(blankWorkspace()) { page ->
            val image = page.shot("fontinfo-blank")
            assertEquals(780, image.width)
            assertTrue(inkIn(image, 0, image.height) > 2000, "the page should hold its fields, not be blank")
            assertEquals(0, page.openFields(), "nothing is open for typing until a value is tapped")
        }
    }

    @Test
    fun theSampleFontIsDrawnWithItsOwnValues() {
        withPage(SampleProject.workspace()) { page ->
            val image = page.shot("fontinfo-hyle-deco")
            assertTrue(inkIn(image, 0, image.height) > 2000)
        }
    }

    @Test
    fun aLinkThatLandsOnTheCopyrightOpensItForTypingAndEnterKeepsWhatWasTyped() {
        val workspace = blankWorkspace()
        val session = session(workspace)
        withPage(workspace, focus = FontInfoField.COPYRIGHT) { page ->
            assertEquals(1, page.openFields(), "the copyright is open as the page appears")
            assertNull(info(session).copyright)
            page.type("Copyright 2026 X")
            page.shot("fontinfo-landed-on-copyright")
            page.enter()
            assertEquals("Copyright 2026 X", info(session).copyright, "Enter keeps what was typed, as one change to the project")
            assertEquals(1, session.history.value.undoLabels.size)
            assertEquals(0, page.openFields(), "and the field closes")
        }
    }

    @Test
    fun escapePutsTheOldValueBackAndChangesNothing() {
        val workspace = blankWorkspace()
        val session = session(workspace)
        withPage(workspace, focus = FontInfoField.DESIGNER) { page ->
            assertEquals(1, page.openFields())
            page.type("Nobody")
            page.escape()
            assertNull(info(session).openTypeNameDesigner)
            assertEquals(0, session.history.value.undoLabels.size)
            assertEquals(0, page.openFields(), "Esc closes the field without keeping what was typed")
        }
    }

    @Test
    fun aRefusedValueStaysOpenUnderItsReasonAndNothingIsWritten() {
        val workspace = blankWorkspace()
        val session = session(workspace)
        withPage(workspace, focus = FontInfoField.DESIGNER_URL) { page ->
            page.type("example.org")
            page.enter()
            assertNull(info(session).openTypeNameDesignerURL)
            assertEquals(1, page.openFields(), "a value the rules refuse leaves the field open to be corrected")
            page.shot("fontinfo-refused")
            page.type("https://example.org")
            page.enter()
            assertEquals("https://example.org", info(session).openTypeNameDesignerURL)
        }
    }

    @Test
    fun aNumberTakesAnExpressionFromTheMetricsRow() {
        val workspace = blankWorkspace()
        val session = session(workspace)
        withPage(workspace, focus = FontInfoField.ASCENDER) { page ->
            assertEquals(1, page.openFields(), "a link to a metric opens that number")
            page.type("=upm-50")
            page.enter()
            assertEquals(950, info(session).ascender)
        }
    }
}
