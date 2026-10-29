// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.asoc.typewright.core.geometry.Guideline
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.ui.sheet.GridFocus
import com.asoc.typewright.ui.sheet.SheetCameraState
import com.asoc.typewright.ui.sheet.WorldGridPass
import com.asoc.typewright.ui.toVec2
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.Typography
import com.asoc.typewright.ui.tokens.toColor
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.exp
import kotlin.math.max
import kotlin.time.TimeSource

/** The grid around the glyph being drawn: only when there is one, densest at the letter, dissolving into paper. */
@Composable
fun DrawGridLayer(
    room: DrawRoom,
    cameraState: SheetCameraState,
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
) {
    val controller = room.controller
    val projectState = room.session?.state?.collectAsState()
    WorldGridPass(
        cameraState = cameraState,
        texture = texture,
        modifier = modifier,
        focus = {
            val glyph = glyphIn(projectState, controller)
            if (glyph == null) {
                null
            } else {
                val space = DrawSpace.of(cameraState.camera)
                val metrics = controller?.metrics() ?: DrawMetrics.NONE
                val (low, high) = metrics.span
                val a = space.toScreen(Vec2(0.0, high.toDouble()))
                val b = space.toScreen(Vec2(glyph.advanceWidth.toDouble(), low.toDouble()))
                val centre = Vec2((a.x + b.x) / 2.0, (a.y + b.y) / 2.0)
                GridFocus(centre, max(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.y - a.y)) * GRID_REACH)
            }
        },
    )
}

private fun glyphIn(
    state: State<com.asoc.typewright.project.ProjectState>?,
    controller: DrawController?,
) = controller?.let { c -> state?.value?.font?.glyph(c.editor.ref) }

/**
 * The Draw room's ink: metric lines, the glyph, its nodes and handles, the selection, the snap
 * targets, the sweep. Above the bloom, so none of it fades under the glass (C4). The camera and the
 * project are read inside the draw lambda, so a pan or a drag redraws without recomposing.
 */
@Composable
fun DrawInkLayer(
    room: DrawRoom,
    cameraState: SheetCameraState,
    texture: CanvasTexture,
    fill: Boolean,
    construction: Boolean,
    modifier: Modifier = Modifier,
    /** The heights, in screen dp, where a metric line's name may be written: not under the header, and not under the inspector. The lines themselves are drawn everywhere. */
    labelBand: ClosedFloatingPointRange<Double>? = null,
    /** How far in from the right edge the names stop, in dp: a column of glass on the right would otherwise cover them. */
    labelEndInsetDp: Double = 0.0,
    /** How strongly the glyph is drawn: 0.3 while a radial ring is open over it (S12). */
    glyphAlpha: Float = 1f,
) {
    val colors = remember(texture) { texture.drawColors() }
    val controller = room.controller
    val projectState = room.session?.state?.collectAsState()
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val state = projectState?.value
            val glyph = controller?.let { c -> state?.font?.glyph(c.editor.ref) }
            val info = state?.font?.let { f -> controller?.editor?.ref?.let { r -> f.master(r.master)?.ufo?.fontInfo } }
            val metrics = info?.let(::drawMetricsFor)
            val guidelines: List<Guideline> = (info?.guidelines.orEmpty()) + (glyph?.guidelines.orEmpty())
            val locked = controller?.editor?.lock?.state == com.asoc.typewright.project.LockState.LOCKED
            val scene =
                DrawScene(
                    glyph = glyph,
                    metrics = metrics,
                    lines = glyph?.let(GlyphLines::of),
                    guidelines = guidelines,
                    fill = fill,
                    locked = locked,
                    construction = construction,
                    selection = controller?.selection ?: DrawSelection.EMPTY,
                    hover = controller?.hover,
                    snaps = controller?.snaps.orEmpty(),
                    sweep = controller?.sweep,
                    pen = controller?.penPoints.orEmpty(),
                    glyphAlpha = glyphAlpha,
                )
            drawDrawRoom(scene, DrawSpace.of(cameraState.camera), colors)
        }
        MetricLabels(room, cameraState, texture, projectState, labelBand, labelEndInsetDp)
    }
}

@Composable
private fun MetricLabels(
    room: DrawRoom,
    cameraState: SheetCameraState,
    texture: CanvasTexture,
    projectState: State<com.asoc.typewright.project.ProjectState>?,
    band: ClosedFloatingPointRange<Double>?,
    endInsetDp: Double,
) {
    val controller = room.controller ?: return
    val state = projectState?.value ?: return
    val glyph = state.font.glyph(controller.editor.ref) ?: return
    val info =
        state.font
            .master(controller.editor.ref.master)
            ?.ufo
            ?.fontInfo ?: return
    val metrics = drawMetricsFor(info)
    val lines = GlyphLines.of(glyph)
    val style = Typography.mono(sizeSp = LABEL_SP).copy(color = texture.muted.toColor())
    for (line in metrics.lines) {
        val below = line.kind == MetricKind.BASELINE || line.kind == MetricKind.DESCENDER
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .offset {
                        val y = DrawSpace.of(cameraState.camera).toScreen(Vec2(0.0, line.y.toDouble())).y
                        // A name that would sit under the header or the inspector is moved out of sight; its line stays.
                        val hidden = band != null && y !in band
                        IntOffset(0, if (hidden) HIDDEN_OFFSET_PX else y.dp.roundToPx() + (if (below) 3.dp else (-15).dp).roundToPx())
                    },
            contentAlignment = Alignment.TopEnd,
        ) {
            BasicText(text = line.text.uppercase(), style = style, modifier = Modifier.padding(end = (LABEL_EDGE_DP + endInsetDp).dp))
        }
    }
    val labelUnitsY = -LABEL_BELOW_BASELINE_UNITS
    lines.lsb?.let { lsb ->
        Box(
            modifier =
                Modifier.offset {
                    val at = DrawSpace.of(cameraState.camera).toScreen(Vec2(0.0, labelUnitsY.toDouble()))
                    val hidden = band != null && at.y !in band
                    IntOffset(at.x.dp.roundToPx() + 4.dp.roundToPx(), if (hidden) HIDDEN_OFFSET_PX else at.y.dp.roundToPx())
                },
        ) {
            BasicText(text = "LSB $lsb", style = style)
        }
    }
    lines.rsb?.let { rsb ->
        Box(
            modifier =
                Modifier
                    .width(RSB_BOX_DP.dp)
                    .offset {
                        val at = DrawSpace.of(cameraState.camera).toScreen(Vec2(lines.advance.toDouble(), labelUnitsY.toDouble()))
                        val hidden = band != null && at.y !in band
                        IntOffset(
                            at.x.dp.roundToPx() - RSB_BOX_DP.dp.roundToPx() - 4.dp.roundToPx(),
                            if (hidden) HIDDEN_OFFSET_PX else at.y.dp.roundToPx(),
                        )
                    },
            contentAlignment = Alignment.TopEnd,
        ) {
            BasicText(text = "RSB $rsb", style = style)
        }
    }
}

// ---- pointer and keys ---------------------------------------------------------------------------

/** What the gesture layer asks of the screen it sits in. */
class DrawGestureEnv(
    val cameraState: SheetCameraState,
    val frame: (pointer: PointerKind, shift: Boolean, alt: Boolean) -> DrawFrame,
    val puckHeld: () -> Boolean,
    val holdPuckOff: () -> Unit,
    val spaceHeld: () -> Boolean,
    val shiftHeld: () -> Boolean,
    val altHeld: () -> Boolean,
    val enabled: () -> Boolean,
)

/**
 * The one-pointer layer over the glyph: a tap selects, a drag moves or bends or sweeps, a held finger
 * opens the radial, a right-click opens the rich menu, the wheel zooms and a hover lights what is
 * under a mouse. Two fingers never reach it; the sheet's pinch-and-pan arbiter owns them and its
 * consumed changes cancel what this layer had begun. Every pointer's run ends in a `finally` that
 * cancels any drag still open, so a cancelled coroutine cannot leave an edit half-made.
 */
fun Modifier.drawPointerInput(
    room: DrawRoom,
    env: DrawGestureEnv,
): Modifier =
    this
        .pointerInput(room.controller) {
            val controller = room.controller ?: return@pointerInput
            gestureLoop(controller, env)
        }.pointerInput(room.controller) {
            val controller = room.controller ?: return@pointerInput
            hoverAndWheelLoop(controller, env)
        }

private suspend fun PointerInputScope.gestureLoop(
    controller: DrawController,
    env: DrawGestureEnv,
) {
    val machine = CanvasGestureMachine()
    val origin = TimeSource.Monotonic.markNow()

    fun now(): Long = origin.elapsedNow().inWholeMilliseconds
    val density: Density = this

    awaitEachGesture {
        val down = awaitAnyDown()
        if (!env.enabled()) return@awaitEachGesture
        val kind =
            when (down.type) {
                PointerType.Mouse -> PointerKind.MOUSE
                PointerType.Stylus, PointerType.Eraser -> PointerKind.STYLUS
                else -> PointerKind.TOUCH
            }
        val startEvent = currentEvent
        val shift = env.shiftHeld()
        val alt = env.altHeld()
        val secondary = kind == PointerKind.MOUSE && startEvent.buttons.isSecondaryPressed
        val middle = kind == PointerKind.MOUSE && startEvent.buttons.isTertiaryPressed
        val position = down.position.toVec2(density)
        if (controller.radial?.state is RadialState.Lifted) {
            down.consume()
            // A tap on the waiting ring is handled on release, below.
            val up = waitForUp(down.id)
            if (up != null) controller.tapRadial(up.position.toVec2(density))
            return@awaitEachGesture
        }
        if (middle || (kind == PointerKind.MOUSE && env.spaceHeld() && startEvent.buttons.isPrimaryPressed)) {
            panLoop(down.id, down.position, env, density)
            return@awaitEachGesture
        }
        val frame = env.frame(kind, shift, alt)
        val glyph = controller.glyph
        val hit =
            if (glyph == null) {
                Hit.Nothing
            } else {
                DrawHit.hit(glyph, frame.space, position, toleranceFor(kind), controller.selectionForMarks())
            }
        down.consume()
        var holdArmed = kind != PointerKind.MOUSE
        val deadline = now() + HOLD_MS
        try {
            handle(controller, machine.onEvent(CanvasEvent.Down(position, now(), hit, kind, secondary, shift, env.puckHeld())), frame)
            if (env.puckHeld()) env.holdPuckOff()
            while (true) {
                val remaining = deadline - now()
                val event: PointerEvent? =
                    when {
                        holdArmed && remaining > 0 -> withTimeoutOrNull(remaining) { awaitPointerEvent() }
                        holdArmed -> null
                        else -> awaitPointerEvent()
                    }
                if (event == null) {
                    holdArmed = false
                    handle(controller, machine.onEvent(CanvasEvent.HoldElapsed(now())), env.frame(kind, shift, alt))
                    continue
                }
                val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                val pressed = event.changes.count { it.pressed }
                if (pressed >= 2 || (change.isConsumed && change.pressed)) {
                    handle(controller, machine.onEvent(CanvasEvent.Cancel(now())), env.frame(kind, shift, alt))
                    break
                }
                val at = change.position.toVec2(density)
                if (!change.pressed) {
                    change.consume()
                    handle(controller, machine.onEvent(CanvasEvent.Up(at, now())), env.frame(kind, env.shiftHeld(), env.altHeld()))
                    break
                }
                change.consume()
                if (machine.isDragging || machine.isHeld) holdArmed = false
                handle(controller, machine.onEvent(CanvasEvent.Move(at, now())), env.frame(kind, env.shiftHeld(), env.altHeld()))
            }
        } finally {
            // Whatever ended this run (an up, a cancel, a second finger, the composable leaving), nothing stays open.
            handle(controller, machine.onEvent(CanvasEvent.Cancel(now())), env.frame(kind, shift, alt))
        }
    }
}

private fun handle(
    controller: DrawController,
    outputs: List<CanvasOutput>,
    frame: DrawFrame,
) {
    for (output in outputs) controller.onOutput(output, frame)
}

private fun toleranceFor(kind: PointerKind): HitTolerance =
    when (kind) {
        PointerKind.TOUCH -> HitTolerance.FINGER
        PointerKind.STYLUS -> HitTolerance.STYLUS
        PointerKind.MOUSE -> HitTolerance.MOUSE
    }

/**
 * The first pointer to go down, whichever button it is: a right-click opens the rich menu and a middle
 * button pans, so unlike the stock first-down wait this one does not look at the button.
 */
private suspend fun AwaitPointerEventScope.awaitAnyDown(): PointerInputChange {
    while (true) {
        val event = awaitPointerEvent()
        event.changes.firstOrNull { it.changedToDownIgnoreConsumed() }?.let { return it }
    }
}

/** Waits for pointer [id] to lift and returns the change that lifted it, or null if it was cancelled or taken. */
private suspend fun AwaitPointerEventScope.waitForUp(id: PointerId): PointerInputChange? {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == id } ?: return null
        if (change.isConsumed && change.pressed) return null
        change.consume()
        if (!change.pressed) return change
    }
}

private suspend fun AwaitPointerEventScope.panLoop(
    id: PointerId,
    start: Offset,
    env: DrawGestureEnv,
    density: Density,
) {
    var last = start
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == id } ?: return
        if (!change.pressed) {
            change.consume()
            return
        }
        env.cameraState.panBy((change.position - last).toVec2(density))
        last = change.position
        change.consume()
    }
}

private suspend fun PointerInputScope.hoverAndWheelLoop(
    controller: DrawController,
    env: DrawGestureEnv,
) {
    val density: Density = this
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull() ?: continue
            if (!env.enabled() || change.type != PointerType.Mouse) continue
            when (event.type) {
                PointerEventType.Scroll -> {
                    val delta = change.scrollDelta.y
                    if (delta != 0f) {
                        env.cameraState.zoomBy(exp(-delta * WHEEL_ZOOM_PER_NOTCH.toFloat()).toDouble(), change.position.toVec2(density))
                        change.consume()
                    }
                }

                PointerEventType.Move -> {
                    if (!change.pressed) {
                        val glyph = controller.glyph
                        val frame = env.frame(PointerKind.MOUSE, false, false)
                        controller.hover =
                            glyph
                                ?.let {
                                    DrawHit.hit(
                                        it,
                                        frame.space,
                                        change.position.toVec2(density),
                                        HitTolerance.MOUSE,
                                        controller.selectionForMarks(),
                                    )
                                }?.takeIf { it !is Hit.Nothing }
                    }
                }

                PointerEventType.Exit -> {
                    controller.hover = null
                }

                else -> {
                    Unit
                }
            }
        }
    }
}

/**
 * The key handler for Draw: turns a Compose key event into a [KeyChord] and asks [DrawKeyMap] what it
 * means. Tool keys are matched on the physical key, so they work whatever character a layout types.
 * Returns true only for a key it used; a Tab with nothing selected, for one, is left for focus to move.
 */
fun Modifier.drawKeys(
    controller: () -> DrawController?,
    frame: () -> DrawFrame,
    enabled: () -> Boolean,
    onSpace: (Boolean) -> Unit,
    onModifiers: (ctrl: Boolean, shift: Boolean, alt: Boolean) -> Unit,
): Modifier =
    this.onKeyEvent { event: KeyEvent ->
        val ctrl = event.isCtrlPressed || event.isMetaPressed
        onModifiers(ctrl, event.isShiftPressed, event.isAltPressed)
        if (!enabled()) return@onKeyEvent false
        val target = controller() ?: return@onKeyEvent false
        if (event.key == Key.Spacebar) {
            onSpace(event.type == KeyEventType.KeyDown)
            return@onKeyEvent false
        }
        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
        val key = drawKeyOf(event.key)
        val action =
            DrawKeyMap.actionFor(
                KeyChord(key, ctrl, event.isShiftPressed, event.isAltPressed),
                hasSelection = !target.selection.isEmpty,
            )
        if (action == null) false else target.onKey(action, frame())
    }

/** Compose's [Key] as the physical [DrawKey]. */
fun drawKeyOf(key: Key): DrawKey =
    when (key) {
        Key.A -> DrawKey.A
        Key.B -> DrawKey.B
        Key.D -> DrawKey.D
        Key.E -> DrawKey.E
        Key.G -> DrawKey.G
        Key.K -> DrawKey.K
        Key.L -> DrawKey.L
        Key.M -> DrawKey.M
        Key.P -> DrawKey.P
        Key.R -> DrawKey.R
        Key.S -> DrawKey.S
        Key.T -> DrawKey.T
        Key.V -> DrawKey.V
        Key.Y -> DrawKey.Y
        Key.Z -> DrawKey.Z
        Key.Zero, Key.NumPad0 -> DrawKey.DIGIT_0
        Key.One, Key.NumPad1 -> DrawKey.DIGIT_1
        Key.Two, Key.NumPad2 -> DrawKey.DIGIT_2
        Key.Three, Key.NumPad3 -> DrawKey.DIGIT_3
        Key.Four, Key.NumPad4 -> DrawKey.DIGIT_4
        Key.Five, Key.NumPad5 -> DrawKey.DIGIT_5
        Key.Six, Key.NumPad6 -> DrawKey.DIGIT_6
        Key.Seven, Key.NumPad7 -> DrawKey.DIGIT_7
        Key.Eight, Key.NumPad8 -> DrawKey.DIGIT_8
        Key.Tab -> DrawKey.TAB
        Key.Enter, Key.NumPadEnter -> DrawKey.ENTER
        Key.Backspace -> DrawKey.BACKSPACE
        Key.Delete -> DrawKey.DELETE
        Key.Escape -> DrawKey.ESCAPE
        Key.DirectionLeft -> DrawKey.LEFT
        Key.DirectionRight -> DrawKey.RIGHT
        Key.DirectionUp -> DrawKey.UP
        Key.DirectionDown -> DrawKey.DOWN
        Key.PageUp -> DrawKey.PAGE_UP
        Key.PageDown -> DrawKey.PAGE_DOWN
        Key.Slash -> DrawKey.QUESTION
        else -> DrawKey.OTHER
    }

private const val HIDDEN_OFFSET_PX = -100_000
private const val LABEL_SP = 8.5
private const val LABEL_EDGE_DP = 14.0
private const val LABEL_BELOW_BASELINE_UNITS = 90
private const val RSB_BOX_DP = 90.0
private const val GRID_REACH = 0.9
private const val HOLD_MS = 380L
private const val WHEEL_ZOOM_PER_NOTCH = 0.1
