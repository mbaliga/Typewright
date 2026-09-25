// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.ui.sheet

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.aarso.typewright.core.geometry.Vec2

/**
 * The Compose-side holder for a [SheetCamera]: P4a's camera is plain data, so this is the thin
 * layer that (a) makes it a Compose snapshot state P4b's draw scopes can read live, and (b)
 * drives the one animated transition P4a already modelled as pure math, [RoomFlight]. Nothing
 * else about camera motion lives here -- gesture-driven pan/zoom (`SheetGestures.kt`) reads and
 * writes [camera] directly, synchronously, with no animation, exactly like a drag should track
 * the finger 1:1.
 *
 * [camera] is a plain `mutableStateOf`, not `Animatable`: a `pointerInput` drag calls [snapTo] or
 * [panBy]/[zoomBy] once per pointer move, and only [flyTo] (the 600 ms room pan) is ever
 * interpolated.
 */
class SheetCameraState(
    initial: SheetCamera = SheetCamera.IDENTITY,
) {
    var camera: SheetCamera by mutableStateOf(initial)
        private set

    /** The [Room] [camera] is currently over, per [roomAt] of its offset's world x. */
    val currentRoom: Room get() = roomAt(camera.offset.x)

    /** Replaces the camera outright (a drag's per-move update, or a gesture's final settle). */
    fun snapTo(newCamera: SheetCamera) {
        camera = newCamera
    }

    /** Pans by a raw screen-dp delta (see [SheetCamera.pannedBy]). */
    fun panBy(screenDelta: Vec2) {
        camera = camera.pannedBy(screenDelta)
    }

    /** Zooms by a multiplicative factor around a screen-dp focus point (see [SheetCamera.zoomedBy]). */
    fun zoomBy(
        factor: Double,
        focusScreen: Vec2,
    ) {
        camera = camera.zoomedBy(factor, focusScreen)
    }

    /**
     * Flies the camera to [room]'s own [Room.flightTargetOffset], eased over [RoomFlight]'s
     * 600 ms curve (UI_SPEC §5). `docs/ARCHITECTURE_REVIEW.md` §4.1 recommendation 4 / P4a's own
     * `RoomFlight` KDoc: progress is driven "from a Compose `Animatable`/`withFrameNanos` loop";
     * this uses the top-level `animate()` suspend function (a linear `0f..1f` `tween`, since
     * [RoomFlight.offsetAt] already applies [CubicBezierEasing] itself -- easing it twice would be
     * wrong), which is Compose's own frame-callback-driven interpolator and therefore exactly
     * such a loop. Suspends until the flight completes; callers launch it in a coroutine scope
     * tied to the composition (e.g. `rememberCoroutineScope()`), never awaiting it synchronously
     * inside a gesture handler that must keep processing pointer events.
     */
    suspend fun flyTo(room: Room) {
        val flight = room.flightFrom(camera)
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(durationMillis = RoomFlight.DURATION_MS, easing = LinearEasing),
        ) { value, _ ->
            camera = camera.pannedTo(flight.offsetAt(value.toDouble()))
        }
        // Land exactly on the target regardless of float rounding across the last frame.
        camera = camera.pannedTo(flight.to)
    }
}

/** Remembers a [SheetCameraState] across recomposition, starting at [initial]. */
@Composable
fun rememberSheetCameraState(initial: SheetCamera = SheetCamera.IDENTITY): SheetCameraState = remember { SheetCameraState(initial) }
