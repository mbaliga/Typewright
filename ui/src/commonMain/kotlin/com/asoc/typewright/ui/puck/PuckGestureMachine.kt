// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.puck

import com.asoc.typewright.core.geometry.Vec2
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.round

/**
 * The puck's gesture states (`docs/ARCHITECTURE_REVIEW.md` §4.2, task P4a). A pointer's lifecycle
 * moves through these left to right; [reducePuckGesture] is the only thing that transitions one to
 * another. Every field a renderer needs mid-gesture (the ring's current rotation, the grip's
 * current position) is public here, so P4b reads state directly every frame rather than replaying
 * [PuckOutputEvent]s to reconstruct it.
 */
sealed interface PuckGestureState {
    /** No gesture in progress. */
    data object Idle : PuckGestureState

    /**
     * Pointer down, off the grip, waiting to see whether this becomes a tap, a swipe, or the
     * radial: UI_SPEC §3 "Gestures" -- "pointerdown starts a 380 ms hold timer. Movement > 6 dp
     * before the timer cancels it and starts a vertical swipe ... Tap (no movement, released
     * before 380 ms) toggles the unfolded list. Hold opens the radial."
     */
    data class HoldTimerRunning(
        val downPosition: Vec2,
        val downTimestampMs: Long,
        /** The puck's own centre at gesture start, captured once so a later grip re-pin mid-gesture (there is none, today) can never move the radial's hub out from under an open gesture. */
        val hubCenter: Vec2,
    ) : PuckGestureState

    /**
     * Cycling tools by vertical travel (UI_SPEC §3: "every 34 dp of travel cycles one tool").
     * [cumulativeStepsEmitted] is the total step count already reported via [PuckOutputEvent.
     * ToolCycled] since [downPosition]; each [reducePuckGesture] call recomputes the *whole*
     * cumulative count from `dy` since [downPosition] (never resets it to zero, and never
     * discounts the travel spent crossing the slop) and emits only the delta -- see this file's
     * own `handleMove`'s `Swiping` branch for the two explorer bugs this avoids.
     */
    data class Swiping(
        val downPosition: Vec2,
        val cumulativeStepsEmitted: Int,
    ) : PuckGestureState

    /**
     * The radial dial is open (UI_SPEC §3 "Radial dial"). [referenceAngleDegrees] is `null`
     * exactly when the pointer is inside the hub (`radius < hub radius`) or has not yet left it
     * since the radial opened -- re-anchored to the current angle the moment the pointer next
     * crosses back outside, rather than ever being read while undefined at the hub itself. This
     * is the fix for `docs/ARCHITECTURE_REVIEW.md` §4.2's "reference angle at touch-down" bug (a
     * straight outward move spuriously rotating the ring) and its "6 px wobble jumped 4 sectors"
     * finding (a wobble across the hub now always re-anchors instead of ever computing a delta
     * against an undefined angle).
     */
    data class RadialOpen(
        val hubCenter: Vec2,
        val referenceAngleDegrees: Double?,
        val accumulatedAngleDegrees: Double,
        /** The unwrapped detent index (can be negative, or greater than [sectorsCount]); [currentSector] wraps it into `0..sectorsCount-1`. */
        val lastTickedSectorRaw: Int,
        val lastPosition: Vec2,
        val sectorsCount: Int,
    ) : PuckGestureState {
        /** The sector (`0..sectorsCount-1`) currently under the fixed 12 o'clock marker (UI_SPEC §3). */
        val currentSector: Int get() = wrapSector(lastTickedSectorRaw, sectorsCount)
    }

    /**
     * Repositioning the puck from its grip (UI_SPEC §3: "Drag the grip ... to reposition; on
     * release the puck pins to the nearest edge"). Recognised the moment the down lands inside
     * the grip's (>= 44 dp) hit region -- `docs/ARCHITECTURE_REVIEW.md` §4.2's "Grip and ring
     * size" fix -- with no hold timer or slop of its own.
     */
    data class GripDragging(
        val lastPosition: Vec2,
    ) : PuckGestureState
}

/** One [reducePuckGesture] call's result: the machine's new [state], plus zero or more [PuckOutputEvent]s to act on, in order. */
data class PuckTransition(
    val state: PuckGestureState,
    val events: List<PuckOutputEvent> = emptyList(),
)

/**
 * The puck gesture machine's one transition function: pure, total (every `(state, event)` pair
 * produces a [PuckTransition], including ones a correct caller should never send -- see each
 * branch below), and free of any Compose dependency, so a synthetic [PuckInputEvent] sequence
 * with hand-picked timestamps and positions is a complete, deterministic unit test
 * (`PuckGestureMachineTest`) with no `pointerInput`/coroutine scaffolding needed to drive it.
 */
fun reducePuckGesture(
    state: PuckGestureState,
    event: PuckInputEvent,
    config: PuckGestureConfig,
    geometry: PuckGeometry,
): PuckTransition =
    when (event) {
        is PuckInputEvent.PointerDown -> handleDown(event, config, geometry)
        is PuckInputEvent.PointerMove -> handleMove(state, event, config)
        is PuckInputEvent.PointerUp -> handleUp(state, config)
        is PuckInputEvent.PointerCancel -> handleCancel(state)
        is PuckInputEvent.HoldTimeout -> handleHoldTimeout(state, config)
    }

/**
 * A small mutable convenience wrapper around [reducePuckGesture], for a caller (P4b's
 * `pointerInput` handler) that would rather hold one object and call [onEvent] than thread
 * [PuckGestureState] through by hand. This class is itself plain Kotlin -- not a Compose type --
 * so whether P4b holds it inside `remember { }`/`mutableStateOf` or drives [reducePuckGesture]
 * directly is entirely P4b's choice; either is equally valid against this file's tests.
 */
class PuckGestureMachine(
    private val config: PuckGestureConfig = PuckGestureConfig(),
) {
    var state: PuckGestureState = PuckGestureState.Idle
        private set

    /** Feeds [event] to [reducePuckGesture] against the puck's current [geometry], updates [state], and returns the transition's output events. */
    fun onEvent(
        event: PuckInputEvent,
        geometry: PuckGeometry,
    ): List<PuckOutputEvent> {
        val transition = reducePuckGesture(state, event, config, geometry)
        state = transition.state
        return transition.events
    }
}

// -------------------------------------------------------------------------------------------
// PointerDown: always starts a fresh gesture (this module's own input contract -- see
// PuckGestureEvents.kt's KDoc -- is one pointer's lifecycle at a time, so a down always means a
// new gesture is starting, regardless of whatever state a previous, already-finished gesture left
// behind).
// -------------------------------------------------------------------------------------------

private fun handleDown(
    event: PuckInputEvent.PointerDown,
    config: PuckGestureConfig,
    geometry: PuckGeometry,
): PuckTransition =
    if (geometry.isWithinGrip(event.position, config.gripHitSizeDp)) {
        PuckTransition(PuckGestureState.GripDragging(lastPosition = event.position))
    } else {
        PuckTransition(
            PuckGestureState.HoldTimerRunning(
                downPosition = event.position,
                downTimestampMs = event.timestampMs,
                hubCenter = geometry.center,
            ),
        )
    }

// -------------------------------------------------------------------------------------------
// PointerMove
// -------------------------------------------------------------------------------------------

private fun handleMove(
    state: PuckGestureState,
    event: PuckInputEvent.PointerMove,
    config: PuckGestureConfig,
): PuckTransition =
    when (state) {
        PuckGestureState.Idle -> {
            PuckTransition(state)
        }

        is PuckGestureState.HoldTimerRunning -> {
            val travelled = (event.position - state.downPosition).length()
            if (travelled > config.slopDp) {
                // Movement beyond slop before the hold timer fires cancels the hold and starts a
                // swipe (UI_SPEC §3 / review §4.2 recommendation 2). Falling through into the same
                // move's swipe handling, seeded from `downPosition` rather than from this move's
                // own position, means the travel spent crossing the slop still counts towards the
                // first 34 dp step -- the fix for the explorer's own measured bug (§4.2: "the
                // pixels spent crossing the slop are discarded, so tool changes land at 45 and 80
                // px, not at 34 and 68").
                handleMove(PuckGestureState.Swiping(state.downPosition, cumulativeStepsEmitted = 0), event, config)
            } else {
                PuckTransition(state)
            }
        }

        is PuckGestureState.Swiping -> {
            // Recomputed fresh from the *total* signed vertical travel since downPosition every
            // time (never reset to zero after a step), so a single large move correctly emits
            // more than one step and no remainder is ever lost -- the fix for the explorer's own
            // measured bug (§4.2: "A 100 px move in one event cycles one tool, because the
            // accumulator resets to 0").
            val dy = event.position.y - state.downPosition.y
            val newCumulative = (dy / config.swipeStepDp).toInt()
            val delta = newCumulative - state.cumulativeStepsEmitted
            val next = state.copy(cumulativeStepsEmitted = newCumulative)
            if (delta != 0) PuckTransition(next, listOf(PuckOutputEvent.ToolCycled(delta))) else PuckTransition(next)
        }

        is PuckGestureState.RadialOpen -> {
            handleRadialMove(state, event, config)
        }

        is PuckGestureState.GripDragging -> {
            val delta = event.position - state.lastPosition
            val next = state.copy(lastPosition = event.position)
            if (delta.x != 0.0 || delta.y != 0.0) {
                PuckTransition(next, listOf(PuckOutputEvent.GripDragged(delta)))
            } else {
                PuckTransition(next)
            }
        }
    }

private fun handleRadialMove(
    state: PuckGestureState.RadialOpen,
    event: PuckInputEvent.PointerMove,
    config: PuckGestureConfig,
): PuckTransition {
    val radius = (event.position - state.hubCenter).length()
    if (radius < config.radialHubRadiusDp) {
        // Inside the hub: de-anchor and accumulate nothing. The ring visually stays exactly where
        // it is (accumulatedAngleDegrees and lastTickedSectorRaw are untouched) -- only the
        // *reference* angle for the next delta is forgotten, so the next crossing back outside
        // re-anchors instead of resuming from a stale angle across an undefined gap.
        return PuckTransition(state.copy(referenceAngleDegrees = null, lastPosition = event.position))
    }
    val angle = angleDegrees(from = state.hubCenter, to = event.position)
    val reference = state.referenceAngleDegrees
    val newAccumulated =
        if (reference == null) {
            // Just crossed into range >= the hub radius (freshly opened, or re-entering after
            // being inside the hub): anchor here with zero delta. This is the fix for the
            // explorer's own measured bug (§4.2: "the reference angle is taken at touch-down, on
            // the hub, where the angle is undefined. Moving straight out to r = 80 turned the
            // ring -90 deg and changed the tool by two with no rotation") -- a straight outward
            // move applies no rotation, because there is no prior "outside" angle to diff against.
            state.accumulatedAngleDegrees
        } else {
            state.accumulatedAngleDegrees + normalizedDeltaDegrees(angle - reference)
        }
    val newSectorRaw = floor(newAccumulated / config.radialDetentDegrees).toInt()
    val ticks = detentTicks(fromRaw = state.lastTickedSectorRaw, toRaw = newSectorRaw, sectorsCount = state.sectorsCount)
    val next =
        state.copy(
            referenceAngleDegrees = angle,
            accumulatedAngleDegrees = newAccumulated,
            lastTickedSectorRaw = newSectorRaw,
            lastPosition = event.position,
        )
    return PuckTransition(next, ticks)
}

// -------------------------------------------------------------------------------------------
// PointerUp
// -------------------------------------------------------------------------------------------

private fun handleUp(
    state: PuckGestureState,
    config: PuckGestureConfig,
): PuckTransition =
    when (state) {
        PuckGestureState.Idle -> {
            PuckTransition(state)
        }

        // Never moved beyond slop, released before the hold timer fired: a tap (UI_SPEC §3).
        is PuckGestureState.HoldTimerRunning -> {
            PuckTransition(PuckGestureState.Idle, listOf(PuckOutputEvent.ToolListToggled))
        }

        // Already-emitted ToolCycled events along the way stand; ending the swipe itself needs no
        // further output.
        is PuckGestureState.Swiping -> {
            PuckTransition(PuckGestureState.Idle)
        }

        is PuckGestureState.RadialOpen -> {
            val radius = (state.lastPosition - state.hubCenter).length()
            val output =
                if (radius < config.radialHubRadiusDp) {
                    PuckOutputEvent.RadialCancelled
                } else {
                    PuckOutputEvent.RadialCommitted(state.currentSector)
                }
            PuckTransition(PuckGestureState.Idle, listOf(output))
        }

        is PuckGestureState.GripDragging -> {
            PuckTransition(PuckGestureState.Idle, listOf(PuckOutputEvent.GripReleased(state.lastPosition)))
        }
    }

// -------------------------------------------------------------------------------------------
// PointerCancel
// -------------------------------------------------------------------------------------------

private fun handleCancel(state: PuckGestureState): PuckTransition =
    when (state) {
        PuckGestureState.Idle -> {
            PuckTransition(state)
        }

        is PuckGestureState.HoldTimerRunning -> {
            PuckTransition(PuckGestureState.Idle)
        }

        is PuckGestureState.Swiping -> {
            PuckTransition(PuckGestureState.Idle)
        }

        // UI_SPEC §3 / review §4.2 recommendation 2: cancel unconditionally on a platform pointer
        // cancel, regardless of radius -- unlike a PointerUp, which still commits outside the hub.
        is PuckGestureState.RadialOpen -> {
            PuckTransition(PuckGestureState.Idle, listOf(PuckOutputEvent.RadialCancelled))
        }

        is PuckGestureState.GripDragging -> {
            PuckTransition(PuckGestureState.Idle, listOf(PuckOutputEvent.GripReleased(state.lastPosition)))
        }
    }

// -------------------------------------------------------------------------------------------
// HoldTimeout
// -------------------------------------------------------------------------------------------

private fun handleHoldTimeout(
    state: PuckGestureState,
    config: PuckGestureConfig,
): PuckTransition =
    when (state) {
        is PuckGestureState.HoldTimerRunning -> {
            val sectorsCount = round(FULL_TURN_DEGREES / config.radialDetentDegrees).toInt()
            val next =
                PuckGestureState.RadialOpen(
                    hubCenter = state.hubCenter,
                    referenceAngleDegrees = null,
                    accumulatedAngleDegrees = 0.0,
                    lastTickedSectorRaw = 0,
                    lastPosition = state.downPosition,
                    sectorsCount = sectorsCount,
                )
            PuckTransition(next, listOf(PuckOutputEvent.RadialOpened(state.hubCenter)))
        }

        // A correct caller never sends this once the gesture has already resolved (the timer's
        // owning coroutine is cancelled first) -- a defensive no-op rather than an exception, so a
        // stray or duplicate timeout can never corrupt an unrelated gesture already in progress.
        else -> {
            PuckTransition(state)
        }
    }

// -------------------------------------------------------------------------------------------
// Shared math
// -------------------------------------------------------------------------------------------

private const val FULL_TURN_DEGREES = 360.0

/** One crossed-detent event per unwrapped sector between [fromRaw] and [toRaw] (exclusive/inclusive respectively), in travel order. */
private fun detentTicks(
    fromRaw: Int,
    toRaw: Int,
    sectorsCount: Int,
): List<PuckOutputEvent> {
    if (fromRaw == toRaw) return emptyList()
    val step = if (toRaw > fromRaw) 1 else -1
    val ticks = mutableListOf<PuckOutputEvent>()
    var sector = fromRaw
    while (sector != toRaw) {
        sector += step
        ticks += PuckOutputEvent.RadialDetentTicked(wrapSector(sector, sectorsCount))
    }
    return ticks
}

private fun wrapSector(
    raw: Int,
    sectorsCount: Int,
): Int = ((raw % sectorsCount) + sectorsCount) % sectorsCount

/** The angle in degrees from [from] to [to], standard `atan2(dy, dx)` convention. */
private fun angleDegrees(
    from: Vec2,
    to: Vec2,
): Double = atan2(to.y - from.y, to.x - from.x) * 180.0 / kotlin.math.PI

/** [delta] normalised into `(-180, 180]`, so a rotation past the +/-180 deg seam reads as a small step rather than a near-360 deg jump. */
private fun normalizedDeltaDegrees(delta: Double): Double {
    var normalized = delta % FULL_TURN_DEGREES
    if (normalized > 180.0) normalized -= FULL_TURN_DEGREES
    if (normalized <= -180.0) normalized += FULL_TURN_DEGREES
    return normalized
}
