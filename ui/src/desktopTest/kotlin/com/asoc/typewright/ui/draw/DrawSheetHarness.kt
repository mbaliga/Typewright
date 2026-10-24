// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.scene.ComposeScenePointer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.asoc.typewright.core.font.ufo.UfoProject
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.project.Brief
import com.asoc.typewright.project.BriefSource
import com.asoc.typewright.project.CreateResult
import com.asoc.typewright.project.NewMaster
import com.asoc.typewright.project.NewProjectSpec
import com.asoc.typewright.project.PointRef
import com.asoc.typewright.project.ProjectSession
import com.asoc.typewright.project.ProjectWorkspace
import com.asoc.typewright.ui.ScreenshotHarness
import com.asoc.typewright.ui.deck.DeckSection
import com.asoc.typewright.ui.project.InMemoryProjectFolderPicker
import com.asoc.typewright.ui.project.LocalProjectWorkspace
import com.asoc.typewright.ui.project.createProjectWorkspace
import com.asoc.typewright.ui.puck.PuckPinState
import com.asoc.typewright.ui.puck.PuckUiState
import com.asoc.typewright.ui.sheet.TypewrightSheet
import com.asoc.typewright.ui.shell.GlyphNav
import com.asoc.typewright.ui.shell.LocalGlyphNav
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.CanvasTextures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/** A project in an in-memory workspace, as the app has it open: [ufo] as its one master. */
internal fun workspaceWith(ufo: UfoProject): ProjectWorkspace =
    runBlocking {
        val workspace = createProjectWorkspace(CoroutineScope(Dispatchers.Default))
        val parent = checkNotNull(InMemoryProjectFolderPicker(workspace).pickNewProjectParent())
        val spec =
            NewProjectSpec(
                name = "Hyle Deco Redraw",
                brief = Brief(source = BriefSource.BLANK),
                scripts = listOf("Latn"),
                masters = listOf(NewMaster(id = "regular", styleName = "Regular", ufo = ufo)),
            )
        check(workspace.create(parent, spec) is CreateResult.Created)
        workspace
    }

/**
 * A running [TypewrightSheet] over a real project, driven frame by frame, with the pointer and the
 * keyboard sent through the scene exactly as a window would send them. [selections] records every glyph
 * the sheet asked the router to open.
 */
internal class DrawSheetRig(
    val scene: ImageComposeScene,
    val density: Float,
    val puck: PuckUiState,
    val pin: PuckPinState,
    val selections: MutableList<String>,
    private val roomRef: () -> DrawRoom?,
) {
    private var clockNs = 0L

    val room: DrawRoom get() = checkNotNull(roomRef()) { "the sheet has not reported its Draw room" }
    val controller: DrawController get() = checkNotNull(room.controller) { "no glyph is open" }
    val session: ProjectSession get() = checkNotNull(room.session) { "no project is open" }

    /** Frames for [millis] of animation time, a frame every 16 ms. */
    fun frames(millis: Long) = frames((millis / 16).toInt() + 1)

    fun frames(count: Int = 4) {
        repeat(count) {
            clockNs += FRAME_NS
            scene.render(clockNs)
        }
    }

    /** Dp as the scene's pixels. */
    fun px(dp: Vec2) = Offset((dp.x * density).toFloat(), (dp.y * density).toFloat())

    /** Where point [point] of contour [contour] is on screen, in dp. */
    fun screenOf(
        contour: Int,
        point: Int,
    ): Vec2 {
        val p =
            controller.glyph!!
                .contours[contour]
                .points[point]
                .point
        return room.space().toScreen(Vec2(p.x.toDouble(), p.y.toDouble()))
    }

    fun select(
        contour: Int,
        point: Int,
    ) {
        controller.selection = DrawSelection(setOf(PointRef(contour, point)))
        frames(2)
    }

    /** The zoom of the sheet's camera, as the Draw room's space reports it. */
    val zoom: Double get() = room.space().dpPerUnit / DrawWorld.UNIT_DP

    // ---- the mouse ---------------------------------------------------------------------------

    fun press(
        at: Vec2,
        button: PointerButton = PointerButton.Primary,
        type: PointerType = PointerType.Mouse,
    ) {
        scene.sendPointerEvent(
            eventType = PointerEventType.Press,
            position = px(at),
            type = type,
            buttons = buttonsFor(button, down = true),
            button = button,
        )
        frames(1)
    }

    fun move(
        to: Vec2,
        button: PointerButton? = PointerButton.Primary,
        type: PointerType = PointerType.Mouse,
    ) {
        scene.sendPointerEvent(
            eventType = PointerEventType.Move,
            position = px(to),
            type = type,
            buttons = if (button == null) PointerButtons() else buttonsFor(button, down = true),
        )
        frames(1)
    }

    fun release(
        at: Vec2,
        button: PointerButton = PointerButton.Primary,
        type: PointerType = PointerType.Mouse,
    ) {
        scene.sendPointerEvent(
            eventType = PointerEventType.Release,
            position = px(at),
            type = type,
            buttons = PointerButtons(),
            button = button,
        )
        frames(1)
    }

    fun click(
        at: Vec2,
        button: PointerButton = PointerButton.Primary,
    ) {
        press(at, button)
        release(at, button)
    }

    /** A drag from [from] to [to] in [steps] moves. */
    fun drag(
        from: Vec2,
        to: Vec2,
        steps: Int = 6,
    ) {
        press(from)
        for (i in 1..steps) {
            val t = i.toDouble() / steps
            move(Vec2(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t))
        }
        release(to)
    }

    fun wheel(
        at: Vec2,
        delta: Float,
    ) {
        scene.sendPointerEvent(eventType = PointerEventType.Scroll, position = px(at), scrollDelta = Offset(0f, delta))
        frames(1)
    }

    private fun buttonsFor(
        button: PointerButton,
        down: Boolean,
    ) = PointerButtons(
        isPrimaryPressed = down && button == PointerButton.Primary,
        isSecondaryPressed = down && button == PointerButton.Secondary,
        isTertiaryPressed = down && button == PointerButton.Tertiary,
    )

    // ---- more than one finger ------------------------------------------------------------------

    /** The state of every touch pointer after an event of [type]: each (id, where, down). */
    @OptIn(InternalComposeUiApi::class, ExperimentalComposeUiApi::class)
    fun touches(
        type: PointerEventType,
        vararg pointers: Triple<Int, Vec2, Boolean>,
    ) {
        scene.sendPointerEvent(
            eventType = type,
            pointers = pointers.map { (id, at, down) -> ComposeScenePointer(PointerId(id.toLong()), px(at), down, PointerType.Touch) },
        )
        frames(1)
    }

    /** Two fingers down at [a] and [b] together and up again, as a quick tap does. */
    fun twoFingerTap(
        a: Vec2,
        b: Vec2,
    ) {
        touches(PointerEventType.Press, Triple(1, a, true))
        touches(PointerEventType.Press, Triple(1, a, true), Triple(2, b, true))
        touches(PointerEventType.Release, Triple(1, a, true), Triple(2, b, false))
        touches(PointerEventType.Release, Triple(1, a, false), Triple(2, b, false))
    }

    // ---- the keyboard ------------------------------------------------------------------------

    @OptIn(InternalComposeUiApi::class)
    fun key(
        key: Key,
        ctrl: Boolean = false,
        shift: Boolean = false,
        alt: Boolean = false,
    ) {
        scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown, isCtrlPressed = ctrl, isAltPressed = alt, isShiftPressed = shift))
        frames(1)
        scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp, isCtrlPressed = ctrl, isAltPressed = alt, isShiftPressed = shift))
        frames(1)
    }

    /** A modifier key going down or up on its own, so a test can hold it across other events. */
    @OptIn(InternalComposeUiApi::class)
    fun holdModifier(
        key: Key,
        down: Boolean,
    ) {
        scene.sendKeyEvent(
            KeyEvent(
                key,
                if (down) KeyEventType.KeyDown else KeyEventType.KeyUp,
                isCtrlPressed =
                    down && (key == Key.CtrlLeft || key == Key.CtrlRight),
            ),
        )
        frames(1)
    }

    // ---- pictures ----------------------------------------------------------------------------

    fun snapshot(name: String): org.jetbrains.skia.Image {
        clockNs += FRAME_NS
        val image = scene.render(clockNs)
        File(ScreenshotHarness.outputDir, "$name.png").writeBytes(checkNotNull(image.encodeToData(EncodedImageFormat.PNG)).bytes)
        return image
    }

    private companion object {
        const val FRAME_NS = 16_000_000L
    }
}

/** Runs [block] against a sheet showing [glyph] of [ufo], settled after its first frames. */
internal fun withDrawSheet(
    ufo: UfoProject,
    glyph: String,
    width: Int,
    height: Int,
    density: Float,
    fill: Boolean = false,
    texture: CanvasTexture = CanvasTextures.PAPER,
    deckVisible: Boolean = true,
    section: State<DeckSection>? = null,
    block: DrawSheetRig.() -> Unit,
) {
    val workspace = workspaceWith(ufo)
    val puck = PuckUiState()
    val pin = PuckPinState(PuckPinState.defaultFor(Vec2(width / density.toDouble(), height / density.toDouble())))
    val selections = mutableListOf<String>()
    var room: DrawRoom? = null
    ImageComposeScene(width = width, height = height, density = Density(density)) {
        CompositionLocalProvider(
            LocalProjectWorkspace provides workspace,
            LocalGlyphNav provides GlyphNav(current = glyph, open = {}, select = { selections += it }),
        ) {
            TypewrightSheet(
                initialTexture = texture,
                initialFill = fill,
                puckState = puck,
                puckPin = pin,
                deckVisible = deckVisible,
                section = section?.value ?: DeckSection.DRAW,
                onDrawRoom = { room = it },
            )
        }
    }.use { scene ->
        val rig = DrawSheetRig(scene, density, puck, pin, selections) { room }
        rig.frames(6)
        rig.block()
    }
}

/** The keys the tests send. */
internal object Keys {
    val ENTER = Key.Enter
    val ESCAPE = Key.Escape
    val DELETE = Key.Delete
    val RIGHT = Key.DirectionRight
    val V = Key.V
    val P = Key.P
    val Z = Key.Z
    val Y = Key.Y
    val G = Key.G
    val L = Key.L
    val TAB = Key.Tab
    val K = Key.K
}
