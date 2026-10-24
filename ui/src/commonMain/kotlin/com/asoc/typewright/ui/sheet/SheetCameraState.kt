// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.sheet

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.asoc.typewright.core.geometry.Vec2

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

    /**
     * Zooms by a multiplicative factor around a screen-dp focus point (see [SheetCamera.zoomedBy]),
     * held to [MIN_ZOOM]..[MAX_ZOOM]: the one clamp that a pinch, the scroll wheel and a keyboard zoom
     * all go through, so none of them can zoom further than another.
     */
    fun zoomBy(
        factor: Double,
        focusScreen: Vec2,
    ) {
        camera = camera.zoomedTo((camera.zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM), focusScreen)
    }

    /**
     * Flies the camera to [target], offset and zoom together, over the same 600 ms curve as a room
     * flight: how a view the person had (the glyph they were drawing) comes back after a visit to
     * another room.
     */
    suspend fun flyToCamera(target: SheetCamera) {
        val from = camera
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(durationMillis = RoomFlight.DURATION_MS, easing = LinearEasing),
        ) { value, _ ->
            val eased = CubicBezierEasing.ROOM_PAN.transform(value.toDouble())
            camera = SheetCamera(from.offset + (target.offset - from.offset) * eased, from.zoom + (target.zoom - from.zoom) * eased)
        }
        camera = target
    }

    /** The camera each room was last left in, so that coming back to Draw lands on the glyph the person was drawing. */
    private val lastViews = mutableMapOf<Room, SheetCamera>()

    /** True when [room] has been left before, so [flyTo] will take the camera back to where it was and not to a fresh view. */
    fun hasView(room: Room): Boolean = room in lastViews

    /**
     * Flies the camera to [room], over [RoomFlight]'s 600 ms curve (UI_SPEC §5). The room being left is
     * remembered as the camera stands, and the room arrived at gets the view it was left in, or, the
     * first time, the one [RoomViews.firstView] gives it. `docs/ARCHITECTURE_REVIEW.md` §4.1
     * recommendation 4 / P4a's own `RoomFlight` KDoc: progress is driven "from a Compose
     * `Animatable`/`withFrameNanos` loop"; [flyToCamera] uses the top-level `animate()` suspend function,
     * which is exactly that. Suspends until the flight completes; callers launch it in a coroutine scope
     * tied to the composition, never awaiting it synchronously inside a gesture handler that must keep
     * processing pointer events.
     */
    suspend fun flyTo(room: Room) {
        val leaving = currentRoom
        if (leaving != room) lastViews[leaving] = camera
        flyToCamera(lastViews[room] ?: RoomViews.firstView(room, camera))
    }
}

/**
 * Where the camera goes when it flies to a room it has not been in before. Draw keeps the zoom the
 * person has (the glyph comes fitted afterwards, by the Draw room itself); every other room is laid out
 * for a camera at rest, so it gets [REST_ZOOM] and a vertical offset that puts the baseline a sensible
 * way down the screen, however far the Draw view was zoomed or panned.
 */
object RoomViews {
    /** How far down a phone-sized screen the baseline of a room at rest starts, in dp: the camera's starting y offset is its negative. */
    const val REST_BASELINE_DP: Double = 400.0

    const val REST_ZOOM: Double = 1.0

    fun firstView(
        room: Room,
        current: SheetCamera,
    ): SheetCamera =
        when (room) {
            Room.DRAW -> SheetCamera(room.flightTargetOffset(current.offset), current.zoom)
            else -> SheetCamera(Vec2(room.worldBounds().left, -REST_BASELINE_DP), REST_ZOOM)
        }
}

/** Remembers a [SheetCameraState] across recomposition, starting at [initial]. */
@Composable
fun rememberSheetCameraState(initial: SheetCamera = SheetCamera.IDENTITY): SheetCameraState = remember { SheetCameraState(initial) }
