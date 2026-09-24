package dev.aarso.typewright.ui.puck

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.aarso.typewright.core.geometry.Vec2
import dev.aarso.typewright.ui.toIntOffset
import dev.aarso.typewright.ui.toVec2
import dev.aarso.typewright.ui.tokens.CanvasTexture
import dev.aarso.typewright.ui.tokens.MotionTokens
import dev.aarso.typewright.ui.tokens.Typography
import dev.aarso.typewright.ui.tokens.toColor
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.sin
import kotlin.time.TimeSource

/**
 * The puck's live UI state: everything [Puck] needs to render and that other glass elements
 * ([RadialDial], [UnfoldedToolList]) need to read. Deliberately separate from [PuckGestureState]
 * (P4a's own pure state, re-exposed here as a Compose snapshot so reads trigger recomposition)
 * and from [PuckPinState] (position only).
 */
class PuckUiState(
    initialTools: List<PlaceholderTool> = PlaceholderTool.ORDERED,
) {
    val tools: List<PlaceholderTool> = initialTools
    var toolIndex: Int by mutableStateOf(0)
        internal set
    var gestureState: PuckGestureState by mutableStateOf(PuckGestureState.Idle)
        internal set
    var toolListUnfolded: Boolean by mutableStateOf(false)
        internal set

    val currentTool: PlaceholderTool get() = tools[((toolIndex % tools.size) + tools.size) % tools.size]
}

@Composable
fun rememberPuckUiState(tools: List<PlaceholderTool> = PlaceholderTool.ORDERED): PuckUiState = remember { PuckUiState(tools) }

/**
 * The tool puck (UI_SPEC §3 "Puck"), wired to P4a's [reducePuckGesture] through **one**
 * `pointerInput` block built on `awaitEachGesture`/`awaitFirstDown` -- `docs/
 * ARCHITECTURE_REVIEW.md` §4.2's exact recommendation ("Detect with `awaitEachGesture` +
 * `awaitFirstDown` + `withTimeoutOrNull(max(380, longPressTimeoutMillis))`"), not
 * `detectTransformGestures`/`detectTapGestures` run separately, since those each own the whole
 * gesture and cannot arbitrate hold-vs-swipe-vs-tap-vs-grip themselves.
 *
 * GESTURE HONESTY (CLAUDE.md law 4): this wiring is implemented per the reviewed spec and driven
 * by P4a's own unit-tested [reducePuckGesture]; it has not run on a real touchscreen, stylus or
 * trackpad (no device in this container). Only that the *reducer* behaves correctly for a given
 * input sequence is verified (`PuckGestureMachineTest`); that Compose's pointer APIs deliver the
 * sequence this code assumes is owner-verified on device only.
 */
@Composable
fun Puck(
    state: PuckUiState,
    pin: PuckPinState,
    texture: CanvasTexture,
    canvasSizeDp: Vec2,
    config: PuckGestureConfig = PuckGestureConfig(),
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val machine = remember(config) { PuckGestureMachine(config) }
    val rollAnim = remember { Animatable(0f) }

    fun geometryNow() = PuckGeometry(center = pin.center)

    fun handleOutputs(outputs: List<PuckOutputEvent>) {
        for (event in outputs) {
            when (event) {
                PuckOutputEvent.ToolListToggled -> {
                    state.toolListUnfolded = !state.toolListUnfolded
                }

                is PuckOutputEvent.ToolCycled -> {
                    state.toolIndex += event.steps
                    scope.launch {
                        rollAnim.snapTo(0f)
                        rollAnim.animateTo(1f, tween(MotionTokens.PUCK_ICON_ROLL_MS))
                    }
                }

                // state.gestureState (set below) already reflects RadialOpen; nothing further to do.
                is PuckOutputEvent.RadialOpened -> {}

                // Haptic hook point (review §4.2 rec. 4: SegmentFrequentTick); desktop/wasmJs have
                // no vibrator (UI_SPEC §6, brief §6: "Android only in v1").
                is PuckOutputEvent.RadialDetentTicked -> {}

                is PuckOutputEvent.RadialCommitted -> {
                    state.toolIndex = event.sector
                }

                PuckOutputEvent.RadialCancelled -> {}

                is PuckOutputEvent.GripDragged -> {
                    pin.dragTo(pin.center + event.delta)
                }

                is PuckOutputEvent.GripReleased -> {
                    val target = pin.nearestEdgeTarget(canvasSizeDp)
                    scope.launch { pin.animateReleaseTo(target) }
                }
            }
        }
        state.gestureState = machine.state
    }

    val holding = state.gestureState is PuckGestureState.RadialOpen
    val dragging = state.gestureState is PuckGestureState.GripDragging
    val targetScale =
        if (holding) {
            0.94f
        } else if (dragging) {
            1.06f
        } else {
            1.0f
        }
    // UI_SPEC §3: "dragging (no transition, scale 1.06)" -- everything else eases.
    val scale by animateFloatAsState(targetValue = targetScale, animationSpec = if (dragging) snap() else tween(150))

    val diameterDp = PuckGestureDefaults.PUCK_DIAMETER_DP
    Box(
        modifier =
            modifier
                .offset { (pin.center - Vec2(diameterDp / 2.0, diameterDp / 2.0)).toIntOffset(density) }
                .size(diameterDp.dp)
                .scale(scale)
                .pointerInput(config, texture) {
                    puckGestureArbiter(
                        density = density,
                        config = config,
                        geometryProvider = ::geometryNow,
                        machine = machine,
                        onOutputs = ::handleOutputs,
                    )
                },
        contentAlignment = Alignment.Center,
    ) {
        PuckBody(
            texture = texture,
            tool = state.currentTool,
            rollProgress = if (state.gestureState == PuckGestureState.Idle) rollAnim.value else 0f,
            showLabel = !holding,
        )
    }

    val radialState = state.gestureState
    if (radialState is PuckGestureState.RadialOpen) {
        RadialDial(state = radialState, tools = state.tools, texture = texture, density = density)
    }

    if (state.toolListUnfolded) {
        UnfoldedToolList(
            pin = pin,
            tools = state.tools,
            currentIndex = state.toolIndex,
            texture = texture,
            canvasSizeDp = canvasSizeDp,
        ) { index ->
            state.toolIndex = index
            state.toolListUnfolded = false
        }
    }
}

/**
 * The one pointer arbiter for the puck (see [Puck]'s own doc). Runs for the lifetime of the
 * composable ([Modifier.pointerInput]'s own `awaitEachGesture` loop starts a fresh gesture per
 * down), converting raw [androidx.compose.ui.input.pointer.PointerInputChange]s into P4a's
 * Compose-free [PuckInputEvent]s and feeding them to [machine]. [nowMs] is a session-relative
 * monotonic clock ([TimeSource.Monotonic]); [PuckGestureState]'s own timestamps are bookkeeping
 * only (`reducePuckGesture` never gates a transition on an absolute time value), so a clock that
 * is not wall-clock-synchronised across platforms is exactly as correct as one that is.
 */
private suspend fun PointerInputScope.puckGestureArbiter(
    density: Density,
    config: PuckGestureConfig,
    geometryProvider: () -> PuckGeometry,
    machine: PuckGestureMachine,
    onOutputs: (List<PuckOutputEvent>) -> Unit,
) {
    val clockOrigin = TimeSource.Monotonic.markNow()

    fun nowMs(): Long = clockOrigin.elapsedNow().inWholeMilliseconds

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        val downTimeMs = nowMs()
        onOutputs(machine.onEvent(PuckInputEvent.PointerDown(down.position.toVec2(density), downTimeMs), geometryProvider()))

        // review §4.2 recommendation 2: "withTimeoutOrNull(max(380, longPressTimeoutMillis))".
        // PuckGestureConfig.holdTimeoutMs already applies that floor (PuckGestureConfig.kt).
        val deadlineMs = downTimeMs + config.holdTimeoutMs
        var holdArmed = true
        val pointerId = down.id

        while (true) {
            val remaining = deadlineMs - nowMs()
            val event =
                when {
                    holdArmed && remaining > 0 -> withTimeoutOrNull(remaining) { awaitPointerEvent() }
                    holdArmed -> null
                    else -> awaitPointerEvent()
                }

            if (event == null) {
                holdArmed = false
                onOutputs(machine.onEvent(PuckInputEvent.HoldTimeout(nowMs()), geometryProvider()))
                continue
            }

            val change = event.changes.firstOrNull { it.id == pointerId } ?: continue

            if (change.isConsumed && !change.pressed) {
                // Consumed elsewhere (e.g. the root sheet arbiter) and released: treated as a
                // platform cancel (review §4.2 rec. 2: "... and on pointer cancel").
                change.consume()
                onOutputs(machine.onEvent(PuckInputEvent.PointerCancel(nowMs()), geometryProvider()))
                break
            }

            if (!change.pressed) {
                change.consume()
                onOutputs(machine.onEvent(PuckInputEvent.PointerUp(nowMs()), geometryProvider()))
                break
            }

            change.consume()
            onOutputs(machine.onEvent(PuckInputEvent.PointerMove(change.position.toVec2(density), nowMs()), geometryProvider()))
            if (machine.state !is PuckGestureState.HoldTimerRunning) holdArmed = false
        }
    }
}

@Composable
private fun PuckBody(
    texture: CanvasTexture,
    tool: PlaceholderTool,
    rollProgress: Float,
    showLabel: Boolean,
) {
    val ink = texture.ink.toColor()
    val canvas = texture.canvas.toColor()
    val diameterDp = PuckGestureDefaults.PUCK_DIAMETER_DP

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(diameterDp.dp)) {
        Canvas(Modifier.size(diameterDp.dp)) {
            drawCircle(color = ink, radius = size.minDimension / 2f)
            // Grip: "18x3 dp bar at the top inside the circle (canvas at 45%)" (UI_SPEC §3).
            val gripWidth = 18.dp.toPx()
            val gripY = 9.dp.toPx()
            drawLine(
                color = canvas.copy(alpha = 0.45f),
                start = Offset(size.width / 2f - gripWidth / 2f, gripY),
                end = Offset(size.width / 2f + gripWidth / 2f, gripY),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        // "rolling (icon slides up 6 dp and fades to 20% for 110 ms on tool change)" -- a
        // dip-and-return over rollProgress 0..1, not a settle-and-hold (no reference video exists
        // to confirm the exact curve; this is a documented, reasonable reading of the sentence).
        val phase = sin(PI * rollProgress).toFloat().coerceIn(0f, 1f)
        val iconAlpha = 1f - 0.8f * phase
        val iconOffsetDp = -6f * phase
        Box(modifier = Modifier.offset(y = iconOffsetDp.dp)) {
            ToolIcon(tool = tool, color = canvas.copy(alpha = iconAlpha))
        }
        if (showLabel) {
            Box(modifier = Modifier.offset(y = 20.dp)) {
                BasicText(text = tool.label.uppercase(), style = Typography.puckLabel.copy(color = canvas))
            }
        }
    }
}

/**
 * A minimal geometric stand-in for a real tool icon (task P4b: "Render the CURRENT tool's name as
 * placeholder text ... a fixed small list of placeholder tool names ... is enough to prove the
 * mechanism"; icons are a bonus, not the point). UI_SPEC §3: "canvas-coloured icon (26 dp,
 * 1.6 stroke)".
 */
@Composable
fun ToolIcon(
    tool: PlaceholderTool,
    color: Color,
    sizeDp: Dp = 26.dp,
) {
    Canvas(Modifier.size(sizeDp)) {
        val strokeWidth = 1.6.dp.toPx()
        val w = size.width
        val h = size.height
        when (tool) {
            PlaceholderTool.SELECT -> {
                // An arrow/cursor shape, echoing the explorer's own SELECT glyph (draw-wide.png).
                val path =
                    Path().apply {
                        moveTo(w * 0.2f, h * 0.1f)
                        lineTo(w * 0.2f, h * 0.9f)
                        lineTo(w * 0.5f, h * 0.65f)
                        lineTo(w * 0.75f, h * 0.75f)
                        close()
                    }
                drawPath(path, color = color)
            }

            PlaceholderTool.PEN -> {
                drawLine(color, Offset(w * 0.2f, h * 0.85f), Offset(w * 0.8f, h * 0.15f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
                drawLine(
                    color,
                    Offset(w * 0.65f, h * 0.05f),
                    Offset(w * 0.95f, h * 0.35f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }

            PlaceholderTool.SHAPE -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.15f, h * 0.15f),
                    size = Size(w * 0.7f, h * 0.7f),
                    cornerRadius = CornerRadius(w * 0.15f, h * 0.15f),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }
        }
    }
}
