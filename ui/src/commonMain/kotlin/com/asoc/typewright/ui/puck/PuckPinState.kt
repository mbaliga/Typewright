// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.puck

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.ui.sheet.CubicBezierEasing
import com.asoc.typewright.ui.tokens.MotionTokens

/** The two edges [PuckPinState] re-pins to (UI_SPEC §3 Puck: "re-pins to nearest edge on release"). Only left/right, matching the explorer's own puck placement (`draw-wide.png`: pinned to the canvas's right edge) -- top/bottom re-pinning is not modelled, a documented simplification. */
enum class PuckEdge { LEFT, RIGHT }

/**
 * The puck's own glass-space position (screen dp, independent of [com.asoc.typewright.ui.sheet.
 * SheetCamera] -- the glass never moves with the sheet). [center] updates live, once per pointer
 * move, while the grip is being dragged ([com.asoc.typewright.ui.puck.PuckOutputEvent.
 * GripDragged]); [animateReleaseTo] is the one animated motion here, the 350 ms puck-pin
 * (UI_SPEC §5 "Puck pin: 350 ms") that runs after [com.asoc.typewright.ui.puck.PuckOutputEvent.
 * GripReleased].
 *
 * The pin remembers which [edge] it is on and how far down the canvas it sits ([heightFraction]), so a
 * canvas that changes size (the first real measurement, a window resize, a rotation) re-pins it with
 * [fitTo] instead of leaving it where the old size put it.
 */
class PuckPinState(
    initial: Vec2,
    edge: PuckEdge = PuckEdge.LEFT,
    var heightFraction: Double = DEFAULT_HEIGHT_FRACTION,
) {
    var center: Vec2 by mutableStateOf(initial)
        internal set

    /** The edge the puck is pinned to; read by the sheet to keep the glyph clear of it. */
    var edge: PuckEdge by mutableStateOf(edge)

    /** Sets [center] immediately, with no animation -- a live drag follows the finger 1:1. */
    fun dragTo(newCenter: Vec2) {
        center = newCenter
    }

    /** Re-pins to [edge] at [heightFraction] of a canvas [canvasSizeDp] big, with no animation (the canvas changed size under it). */
    fun fitTo(canvasSizeDp: Vec2) {
        center = pinnedAt(edge, heightFraction, canvasSizeDp)
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
        /** UI_SPEC §3: "Pinned position: 18 dp from an edge, 40% down the canvas by default." */
        const val DEFAULT_HEIGHT_FRACTION: Double = 0.40

        private const val PIN_INSET_DP = 18.0

        /** Where a puck pinned to [edge] [heightFraction] of the way down a canvas of [canvasSizeDp] sits, kept inside the canvas. */
        fun pinnedAt(
            edge: PuckEdge,
            heightFraction: Double,
            canvasSizeDp: Vec2,
        ): Vec2 {
            val radius = PuckGestureDefaults.PUCK_DIAMETER_DP / 2.0
            val y = (canvasSizeDp.y * heightFraction).coerceIn(radius, (canvasSizeDp.y - radius).coerceAtLeast(radius))
            return Vec2(edgeX(edge, canvasSizeDp), y)
        }

        /** The x of a puck pinned to [edge] of a canvas [canvasSizeDp] wide: 18 dp in from that edge. */
        fun edgeX(
            edge: PuckEdge,
            canvasSizeDp: Vec2,
        ): Double {
            val radius = PuckGestureDefaults.PUCK_DIAMETER_DP / 2.0
            return when (edge) {
                PuckEdge.LEFT -> PIN_INSET_DP + radius
                PuckEdge.RIGHT -> canvasSizeDp.x - PIN_INSET_DP - radius
            }
        }

        /** The default pin for a canvas of [canvasSizeDp]: on [edge] (the left, mid-height, as the Draw board pins it), 18 dp in. */
        fun defaultFor(
            canvasSizeDp: Vec2,
            edge: PuckEdge = PuckEdge.LEFT,
        ): Vec2 = pinnedAt(edge, DEFAULT_HEIGHT_FRACTION, canvasSizeDp)
    }
}

/**
 * Remembers a [PuckPinState] pinned at its default position for a [canvasSizeDp] canvas, and re-pins it
 * whenever the canvas size changes. A drag in progress is never interrupted: the size only changes
 * between gestures.
 */
@Composable
fun rememberPuckPinState(
    canvasSizeDp: Vec2,
    edge: PuckEdge = PuckEdge.LEFT,
): PuckPinState {
    val pin = remember { PuckPinState(PuckPinState.defaultFor(canvasSizeDp, edge), edge) }
    LaunchedEffect(canvasSizeDp) { pin.fitTo(canvasSizeDp) }
    return pin
}

/** [center] re-pinned to the nearest edge (18 dp inset), keeping the dragged y (clamped into the canvas) and remembering it as the new height. */
fun PuckPinState.nearestEdgeTarget(canvasSizeDp: Vec2): Vec2 {
    val nearestEdge = if (center.x < canvasSizeDp.x / 2.0) PuckEdge.LEFT else PuckEdge.RIGHT
    edge = nearestEdge
    val radius = PuckGestureDefaults.PUCK_DIAMETER_DP / 2.0
    val y = center.y.coerceIn(radius, (canvasSizeDp.y - radius).coerceAtLeast(radius))
    if (canvasSizeDp.y > 0.0) heightFraction = y / canvasSizeDp.y
    return Vec2(PuckPinState.edgeX(nearestEdge, canvasSizeDp), y)
}
