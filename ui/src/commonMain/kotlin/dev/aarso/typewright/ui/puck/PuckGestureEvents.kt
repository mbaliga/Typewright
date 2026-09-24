package dev.aarso.typewright.ui.puck

import dev.aarso.typewright.core.geometry.Vec2

/**
 * The puck gesture machine's own input alphabet (`docs/ARCHITECTURE_REVIEW.md` §4.2, task P4a):
 * abstract pointer-lifecycle events, deliberately not `androidx.compose.ui.input.pointer.
 * PointerInputChange`, so [dev.aarso.typewright.ui.puck.reducePuckGesture] is unit-testable with
 * a synthetic event sequence on jvm and wasmJs, with no Compose runtime involved, and a later
 * Compose `pointerInput` handler (P4b) is the only thing that ever constructs one of these from a
 * real pointer.
 *
 * This alphabet describes **one pointer's own lifecycle already arbitrated to the puck** --
 * `down`, zero or more `move`s, then exactly one of `up`/`cancel` -- never two overlapping
 * pointers. The review's "one sheet-level arbiter" (§4.2 recommendation 1) is a *separate*,
 * higher-level piece that decides which pointer (if any) the puck gets to see at all (multi-touch,
 * pinch-vs-pan, two-finger undo); this module is downstream of that arbiter, not a replacement
 * for it.
 *
 * [HoldTimeout] has no Compose analogue at all: the real 380 ms-or-more hold timer
 * (`docs/ARCHITECTURE_REVIEW.md` §4.2 recommendation 2: `awaitEachGesture` + `awaitFirstDown` +
 * `withTimeoutOrNull(max(380, longPressTimeoutMillis))`) lives entirely in P4b's coroutine code;
 * this event is simply "that timer fired, and nothing cancelled it first", fed in exactly like
 * every other event so the state machine never needs to run its own clock.
 *
 * Every position is screen dp, already density-converted by the caller -- this module has no
 * concept of raw pixels or display density.
 */
sealed interface PuckInputEvent {
    val timestampMs: Long

    data class PointerDown(
        val position: Vec2,
        override val timestampMs: Long,
    ) : PuckInputEvent

    data class PointerMove(
        val position: Vec2,
        override val timestampMs: Long,
    ) : PuckInputEvent

    /** No position: by the time this arrives, every state that needs one tracks its own last [PointerMove] position. */
    data class PointerUp(
        override val timestampMs: Long,
    ) : PuckInputEvent

    /** A platform-level pointer cancel (UI_SPEC §3 radial: "release within r < 24 dp cancels" plus review §4.2: "... and on pointer cancel"). */
    data class PointerCancel(
        override val timestampMs: Long,
    ) : PuckInputEvent

    /** The hold timer (`max(380 ms, platform long-press timeout)`) elapsed without being cancelled by movement or release. */
    data class HoldTimeout(
        override val timestampMs: Long,
    ) : PuckInputEvent
}

/**
 * What the puck gesture machine asks its caller to do, one per [PuckInputEvent] that changes
 * anything. Every ongoing gesture's *live* value (the ring's current rotation, the grip's current
 * drag position) is public on [dev.aarso.typewright.ui.puck.PuckGestureState] itself for P4b to
 * read every frame while rendering; these events are the discrete, one-off things that happen
 * along the way -- a tool change, a haptic-worthy detent, a commit -- not a duplicate render feed.
 */
sealed interface PuckOutputEvent {
    /** Tap (UI_SPEC §3: "no movement, released before 380 ms) toggles the unfolded list"). */
    data object ToolListToggled : PuckOutputEvent

    /**
     * The net number of tools to cycle by since the last [ToolCycled] (positive = forward,
     * negative = back); UI_SPEC §3's "haptic 6 ms" per step is not modelled here. It is a
     * P4b caller concern (`docs/ARCHITECTURE_REVIEW.md` §4.2 recommendation 4) and, as of P4b,
     * an unwired hook point -- `Puck.kt`'s handler for the analogous [RadialDetentTicked] is a
     * no-op with a comment marking where a `LocalHapticFeedback` call would go, and Android is
     * the only target with a vibrator to call it on (UI_SPEC §6, brief §6: "Android only in v1").
     */
    data class ToolCycled(
        val steps: Int,
    ) : PuckOutputEvent

    /** The hold timer fired: P4b starts rendering the radial, centred on [center]. */
    data class RadialOpened(
        val center: Vec2,
    ) : PuckOutputEvent

    /** One 45-degree detent crossed while the ring is live (`radius >= 24 dp` from the hub); [sector] is the newly current one (`0..sectorsCount-1`). */
    data class RadialDetentTicked(
        val sector: Int,
    ) : PuckOutputEvent

    /** Released outside the hub: [sector] is the tool sector under the fixed marker. The caller maps sectors to tools; this module has no `Tool` type of its own. */
    data class RadialCommitted(
        val sector: Int,
    ) : PuckOutputEvent

    /** Released inside the hub (`radius < 24 dp`), or the platform cancelled the pointer. */
    data object RadialCancelled : PuckOutputEvent

    /** The grip moved by [delta] (screen dp) since the last [GripDragged]/the down event. */
    data class GripDragged(
        val delta: Vec2,
    ) : PuckOutputEvent

    /** The grip drag ended at [position]; P4b decides the nearest edge to re-pin to (UI_SPEC §3) and animates the 350 ms puck-pin motion. */
    data class GripReleased(
        val position: Vec2,
    ) : PuckOutputEvent
}
