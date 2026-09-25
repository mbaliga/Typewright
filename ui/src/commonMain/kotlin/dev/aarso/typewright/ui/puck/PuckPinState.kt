// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.ui.puck

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.aarso.typewright.core.geometry.Vec2
import dev.aarso.typewright.ui.sheet.CubicBezierEasing
import dev.aarso.typewright.ui.tokens.MotionTokens

/** The two edges [PuckPinState] re-pins to (UI_SPEC §3 Puck: "re-pins to nearest edge on release"). Only left/right, matching the explorer's own puck placement (`draw-wide.png`: pinned to the canvas's right edge) -- top/bottom re-pinning is not modelled, a documented simplification. */
enum class PuckEdge { LEFT, RIGHT }

/**
 * The puck's own glass-space position (screen dp, independent of [dev.aarso.typewright.ui.sheet.
 * SheetCamera] -- the glass never moves with the sheet). [center] updates live, once per pointer
 * move, while the grip is being dragged ([dev.aarso.typewright.ui.puck.PuckOutputEvent.
 * GripDragged]); [animateReleaseTo] is the one animated motion here, the 350 ms puck-pin
 * (UI_SPEC §5 "Puck pin: 350 ms") that runs after [dev.aarso.typewright.ui.puck.PuckOutputEvent.
 * GripReleased].
 */
class PuckPinState(
    initial: Vec2,
    var edge: PuckEdge = PuckEdge.RIGHT,
) {
    var center: Vec2 by mutableStateOf(initial)
        internal set

    /** Sets [center] immediately, with no animation -- a live drag follows the finger 1:1. */
    fun dragTo(newCenter: Vec2) {
        center = newCenter
    }

    /**
     * Animates [center] from wherever the drag left it to [target] over [MotionTokens.
     * PUCK_PIN_MS], eased by [CubicBezierEasing.ROOM_PAN] (UI_SPEC §5 names one shared easing
     * curve for every motion but the two explicitly "linear" ones; the puck pin is not one of
     * those two).
     */
    suspend fun animateReleaseTo(target: Vec2) {
        val from = center
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(durationMillis = MotionTokens.PUCK_PIN_MS, easing = LinearEasing),
        ) { value, _ ->
            val eased = CubicBezierEasing.ROOM_PAN.transform(value.toDouble())
            center = from + (target - from) * eased
        }
        center = target
    }

    companion object {
        /** UI_SPEC §3: "Pinned position: 18 dp from an edge, 40% down the canvas by default." Right edge, per the explorer's own screenshots. */
        fun defaultFor(canvasSizeDp: Vec2): Vec2 {
            val radius = PuckGestureDefaults.PUCK_DIAMETER_DP / 2.0
            return Vec2(canvasSizeDp.x - PIN_INSET_DP - radius, canvasSizeDp.y * 0.40)
        }

        private const val PIN_INSET_DP = 18.0
    }
}

/** Remembers a [PuckPinState] pinned at its UI_SPEC §3 default position for a [canvasSizeDp] canvas. */
@Composable
fun rememberPuckPinState(canvasSizeDp: Vec2): PuckPinState = remember { PuckPinState(PuckPinState.defaultFor(canvasSizeDp)) }

/** [center] re-pinned to [edge] (18 dp inset), keeping the dragged y (clamped into the canvas). */
fun PuckPinState.nearestEdgeTarget(canvasSizeDp: Vec2): Vec2 {
    val radius = PuckGestureDefaults.PUCK_DIAMETER_DP / 2.0
    val nearestEdge = if (center.x < canvasSizeDp.x / 2.0) PuckEdge.LEFT else PuckEdge.RIGHT
    edge = nearestEdge
    val x =
        when (nearestEdge) {
            PuckEdge.LEFT -> 18.0 + radius
            PuckEdge.RIGHT -> canvasSizeDp.x - 18.0 - radius
        }
    val y = center.y.coerceIn(radius, (canvasSizeDp.y - radius).coerceAtLeast(radius))
    return Vec2(x, y)
}
