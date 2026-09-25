// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.ui.sheet

import dev.aarso.typewright.core.geometry.Vec2

/**
 * The sheet's rooms. The brief lists seven rooms end to end (§4.1: "Capture · Trace · Draw ·
 * Space · Learn · Check · Ship"), but only three of them are regions of the one scrollable sheet
 * this module lays out: `docs/ARCHITECTURE_REVIEW.md` §4.1 ("What the explorer actually does")
 * and §5 finding 25 both find the explorer's `.world` strip holds exactly Draw, Space and Learn;
 * Capture, Trace, Economy, Check, Ship and Home are separate screens with their own back button,
 * not part of the sheet. Confirmed here against the explorer's own screenshots and notes
 * (`draw-notes.txt`/`space-notes.txt`/`learn-notes.txt`, each captioned "Three rooms on one
 * plane: Draw, Space, Learn") and its nav-chip order, which this enum's declaration order matches
 * left to right.
 */
enum class Room(
    val index: Int,
) {
    DRAW(0),
    SPACE(1),
    LEARN(2),
    ;

    companion object {
        /** Every room, left to right -- `Room.entries` already is, but this reads at call sites. */
        val ORDERED: List<Room> = entries.sortedBy { it.index }
    }
}

/** One room's fixed world-space width (dp): the sheet's rooms tile this wide along x, no gap. */
const val ROOM_WIDTH_DP: Double = 1280.0

/** [left, right) on the shared world x-axis that [Room.worldBounds] occupies. */
data class RoomBounds(
    val left: Double,
    val right: Double,
) {
    val width: Double get() = right - left
    val center: Double get() = (left + right) / 2.0

    /** Whether world-space x-coordinate [x] falls inside this room ([left] inclusive, [right] exclusive). */
    operator fun contains(x: Double): Boolean = x >= left && x < right
}

/** [Room]'s own region of the shared world x-axis: `[index * ROOM_WIDTH_DP, (index + 1) * ROOM_WIDTH_DP)`. */
fun Room.worldBounds(): RoomBounds = RoomBounds(left = index * ROOM_WIDTH_DP, right = (index + 1) * ROOM_WIDTH_DP)

/** Which [Room] world-space x-coordinate [x] falls in, clamping to the first or last room outside `[0, 3 * ROOM_WIDTH_DP)`. */
fun roomAt(x: Double): Room =
    when {
        x < 0.0 -> Room.ORDERED.first()
        else -> Room.ORDERED.firstOrNull { x in it.worldBounds() } ?: Room.ORDERED.last()
    }

/**
 * The camera offset a room switch flies to: [room]'s own left edge on x, keeping [currentOffset]'s
 * y unchanged (this module owns only the rooms' horizontal layout; brief §4.3's proposed vertical
 * margin/proof axis is a separate, [CONFIRM]ed axis this task does not touch, so a room switch
 * must not silently reset whatever vertical position the sheet is already at).
 */
fun Room.flightTargetOffset(currentOffset: Vec2): Vec2 = Vec2(worldBounds().left, currentOffset.y)

/**
 * A single room-to-room camera pan (UI_SPEC §5 / brief §6: "room pan 600 ms,
 * cubic-bezier(.2,.8,.2,1)"), modelled as pure math per `docs/ARCHITECTURE_REVIEW.md` §4.1
 * recommendation 4: [offsetAt] maps an animation progress in `[0, 1]` to a camera offset along
 * [easing]. P4b drives [offsetAt]'s `progress` argument from a Compose `Animatable`/
 * `withFrameNanos` loop over [DURATION_MS]; nothing in this class touches Compose, so the curve
 * and the interpolation are both testable as plain function calls.
 */
data class RoomFlight(
    val from: Vec2,
    val to: Vec2,
    val easing: CubicBezierEasing = CubicBezierEasing.ROOM_PAN,
) {
    /** The camera offset at animation [progress] (`0..1`, clamped): [from] eased towards [to]. */
    fun offsetAt(progress: Double): Vec2 {
        val eased = easing.transform(progress)
        return from + (to - from) * eased
    }

    companion object {
        /** UI_SPEC §5: "Room pan: 600 ms cubic-bezier(.2,.8,.2,1)". */
        const val DURATION_MS: Int = 600
    }
}

/** The [RoomFlight] that pans [camera] from its current offset to [room]'s own [Room.flightTargetOffset]. */
fun Room.flightFrom(camera: SheetCamera): RoomFlight = RoomFlight(from = camera.offset, to = flightTargetOffset(camera.offset))

/**
 * Room-to-room stepping, clamped at the ends (no wraparound past Draw or past Learn) -- brief
 * §4.1's room order ("Capture . Trace . Draw . Space . Learn . Check . Ship") restricted to this
 * sheet's three (§5 finding 25); the header swipe, edge marks and keyboard arrows all step one
 * room this way (task P4b items 6/9).
 */
fun Room.next(): Room = Room.ORDERED.getOrElse(index + 1) { this }

fun Room.previous(): Room = Room.ORDERED.getOrElse(index - 1) { this }
