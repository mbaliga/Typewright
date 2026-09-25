// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.sheet

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.ui.toVec2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The sheet's root two-finger arbiter (task P4b item 6; `docs/ARCHITECTURE_REVIEW.md` §4.2
 * recommendation 1: a `pointerInput` on the sheet's common parent, in `PointerEventPass.Initial`,
 * that owns multi-touch -- "pinch against pan: whichever passes its slop first wins" and, per
 * recommendation for room switching, "a room change only as an overscroll past the room's edge,
 * or as a fling", **not** a naive "any two-finger horizontal drag" (brief §5.5 / UI_SPEC §5
 * finding 31). Deliberately **not** `detectTransformGestures`, which also recognises a
 * *one*-finger drag as a pan -- that finger is reserved for a future node/letter-editing tool
 * (P5b), not built by this task, so a literal `detectTransformGestures` here would silently start
 * panning the camera under what should eventually be a draw gesture.
 *
 * **This task's own simplification of "overscroll past the edge":** P4a's [SheetCamera]/[Room]
 * model shares one world x-axis between "pan within a room" and "which room the camera is in"
 * ([roomAt]) -- there is no second, room-local coordinate space. So a two-finger drag pans the
 * camera continuously and freely (satisfying brief §5.5's "pan / zoom canvas: two fingers" at
 * all times), and "overscroll past the edge" becomes literal: the room only actually *changes*
 * once the drag has moved the camera's offset past the **whole width** of the room it started in
 * ([roomAt] of the final offset differs from the room the gesture started in), which in practice
 * takes a very large continuous two-finger drag -- room-switching's primary affordances stay the
 * header swipe, the edge marks and the keyboard arrows (all much shorter throws), exactly as
 * UI_SPEC's own component list treats them. **Or a fling**: released with enough velocity,
 * regardless of distance, per the review's own wording -- this part is not simplified.
 *
 * GESTURE HONESTY (CLAUDE.md law 4): implemented per the reviewed spec; not run on a real
 * trackpad, touchscreen or Android's own scale-gesture-as-mouse-scroll path (§4.1 "What will not
 * work, by target": "Trackpad pinch arrives as mouse scale events" on Android, "no multi-touch"
 * on Linux, one open Compose bug on the web for un-consumed pinches) -- owner-verified on device
 * only.
 */
fun Modifier.sheetRootGestures(
    cameraState: SheetCameraState,
    onGestureSettled: (Room) -> Unit,
): Modifier =
    this.pointerInput(cameraState) {
        awaitEachGesturePinchPan(
            currentRoom = { cameraState.currentRoom },
            onTransform = { panDp, zoomFactor, focusDp ->
                val zoomed =
                    cameraState.camera
                        .zoomedBy(zoomFactor, focusDp)
                        .zoom
                        .coerceIn(PINCH_ZOOM_MIN, PINCH_ZOOM_MAX)
                val reZoomed = cameraState.camera.zoomedTo(zoomed, focusDp)
                cameraState.snapTo(reZoomed.pannedBy(panDp))
            },
            onSettled = { startRoom, flingDirection ->
                val target =
                    when {
                        flingDirection > 0 -> startRoom.next()
                        flingDirection < 0 -> startRoom.previous()
                        else -> roomAt(cameraState.camera.offset.x)
                    }
                if (target != startRoom || flingDirection != 0) onGestureSettled(target)
            },
        )
    }

/**
 * The actual multi-touch loop, factored out of [sheetRootGestures] so its transform math is one
 * small, readable piece: waits for a first pointer down, then on every frame with >= 2 pressed
 * pointers computes the centroid delta (pan) and span ratio (zoom) since the previous such frame
 * and reports them via [onTransform]; on gesture end, reports the room the gesture started in and
 * a fling direction (`> 0` = flung toward the next room, `< 0` = previous, `0` = no fling) via
 * [onSettled].
 */
private suspend fun PointerInputScope.awaitEachGesturePinchPan(
    currentRoom: () -> Room,
    onTransform: (panDp: Vec2, zoomFactor: Double, focusDp: Vec2) -> Unit,
    onSettled: (startRoom: Room, flingDirection: Int) -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var startRoom: Room? = null
        var prevCentroid: Offset? = null
        var prevSpan: Float? = null
        val velocityTracker = VelocityTracker()

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.filter { it.pressed }
            if (pressed.size >= 2) {
                if (startRoom == null) startRoom = currentRoom()
                val p0 = pressed[0].position
                val p1 = pressed[1].position
                val centroid = Offset((p0.x + p1.x) / 2f, (p0.y + p1.y) / 2f)
                val span = (p0 - p1).getDistance().coerceAtLeast(1f)
                val lastCentroid = prevCentroid
                val lastSpan = prevSpan
                if (lastCentroid != null && lastSpan != null) {
                    val panDp = (centroid - lastCentroid).toVec2(this)
                    val zoomFactor = (span / lastSpan).toDouble()
                    onTransform(panDp, zoomFactor, centroid.toVec2(this))
                }
                velocityTracker.addPosition(pressed[0].uptimeMillis, centroid)
                pressed.forEach { it.consume() }
                prevCentroid = centroid
                prevSpan = span
            } else {
                prevCentroid = null
                prevSpan = null
            }
            if (event.changes.none { it.pressed }) break
        }

        if (startRoom != null) {
            val velocity = velocityTracker.calculateVelocity()
            val velocityDpPerS = velocity.x / density
            val direction =
                when {
                    velocityDpPerS <= -FLING_VELOCITY_THRESHOLD_DP_PER_S -> 1
                    velocityDpPerS >= FLING_VELOCITY_THRESHOLD_DP_PER_S -> -1
                    else -> 0
                }
            onSettled(startRoom, direction)
        }
    }
}

/**
 * Desktop keyboard room navigation (task P4b item 6: "on desktop, keyboard left/right arrows").
 * `docs/ARCHITECTURE_REVIEW.md` §4.2 recommendation 5: "Put them in a root `onKeyEvent`, not
 * `onPreviewKeyEvent`" -- this app has no text field yet for that rule to matter against, but the
 * recommendation is followed regardless, both as the documented-correct default and so a future
 * `=` expression field (brief §5.2) is not blocked by this handler intercepting its arrow keys
 * first.
 */
fun Modifier.roomKeyboardNavigation(
    cameraState: SheetCameraState,
    scope: CoroutineScope,
): Modifier =
    this.onKeyEvent { event: KeyEvent ->
        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
        when (event.key) {
            Key.DirectionRight -> {
                scope.launch { cameraState.flyTo(cameraState.currentRoom.next()) }
                true
            }

            Key.DirectionLeft -> {
                scope.launch { cameraState.flyTo(cameraState.currentRoom.previous()) }
                true
            }

            else -> {
                false
            }
        }
    }

private const val PINCH_ZOOM_MIN = 0.05
private const val PINCH_ZOOM_MAX = 8.0
private const val FLING_VELOCITY_THRESHOLD_DP_PER_S = 800.0
