// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import com.asoc.typewright.core.geometry.Vec2

/** Which kind of pointer is acting; it decides the slop, the hit tolerance and whether a hold opens the radial (INTERACTION_V1 T6). */
enum class PointerKind { TOUCH, STYLUS, MOUSE }

/** The tunables of one-pointer gestures on the glyph. */
data class CanvasGestureConfig(
    val touchSlopDp: Double = 8.0,
    val fineSlopDp: Double = 4.0,
    val holdMs: Long = 380,
    val doubleTapMs: Long = 300,
    val doubleTapRadiusDp: Double = 24.0,
) {
    fun slopFor(kind: PointerKind): Double = if (kind == PointerKind.TOUCH) touchSlopDp else fineSlopDp
}

/** What the pointer did, abstracted from Compose so the sequence is a plain list in a test. */
sealed interface CanvasEvent {
    val timestampMs: Long

    /** A pointer went down at [position] (screen dp) over [hit]; [secondary] is the right button, [additive] is Shift held. */
    data class Down(
        val position: Vec2,
        override val timestampMs: Long,
        val hit: Hit,
        val pointer: PointerKind,
        val secondary: Boolean = false,
        val additive: Boolean = false,
        /** The puck is held under a thumb: a drag on empty canvas moves the selection precisely instead of sweeping (INTERACTION_V1 §4.3). */
        val chord: Boolean = false,
    ) : CanvasEvent

    data class Move(
        val position: Vec2,
        override val timestampMs: Long,
    ) : CanvasEvent

    data class Up(
        val position: Vec2,
        override val timestampMs: Long,
    ) : CanvasEvent

    /** The platform took the pointer away, or a second finger arrived and the sheet's pinch took over. */
    data class Cancel(
        override val timestampMs: Long,
    ) : CanvasEvent

    /** The hold timer ran out with no movement and no release. */
    data class HoldElapsed(
        override val timestampMs: Long,
    ) : CanvasEvent
}

/** What the screen should do about a [CanvasEvent]. */
sealed interface CanvasOutput {
    data class Tap(
        val position: Vec2,
        val hit: Hit,
        val additive: Boolean,
    ) : CanvasOutput

    data class DoubleTap(
        val position: Vec2,
        val hit: Hit,
    ) : CanvasOutput

    /** A drag began on [hit] (a point, handle or segment): the screen opens a session drag. [from] is where the pointer went down. */
    data class BeginObjectDrag(
        val hit: Hit,
        val from: Vec2,
        val additive: Boolean,
    ) : CanvasOutput

    /** The object drag is now [dx], [dy] screen dp from where it began. */
    data class ObjectDragged(
        val dx: Double,
        val dy: Double,
        val at: Vec2,
    ) : CanvasOutput

    /** The object drag ended; [committed] false means it was cancelled and must be undone. */
    data class EndObjectDrag(
        val committed: Boolean,
    ) : CanvasOutput

    /** A drag began on empty canvas: a rectangle for a mouse, a lasso for a finger or stylus. */
    data class BeginSweep(
        val from: Vec2,
        val lasso: Boolean,
        val additive: Boolean,
    ) : CanvasOutput

    data class SweepMoved(
        val to: Vec2,
    ) : CanvasOutput

    data class EndSweep(
        val to: Vec2,
        val committed: Boolean,
    ) : CanvasOutput

    /** The hold timer ran out over [hit] at [position] (a finger or stylus): the radial opens. */
    data class HoldOpened(
        val hit: Hit,
        val position: Vec2,
    ) : CanvasOutput

    /** The pointer moved while the radial is open. */
    data class HeldMoved(
        val position: Vec2,
    ) : CanvasOutput

    /** The pointer lifted while the radial is open: [position] is where, and the radial decides whether that was a sweep or a lift. */
    data class HeldReleased(
        val position: Vec2,
    ) : CanvasOutput

    /** The radial's pointer was cancelled. */
    data object HeldCancelled : CanvasOutput

    /** A right-click: the rich menu opens for [hit]. */
    data class ContextMenu(
        val position: Vec2,
        val hit: Hit,
    ) : CanvasOutput
}

/**
 * One pointer's gesture on the glyph, as a state machine with no Compose in it (INTERACTION_V1
 * §4.1). A press can become: a tap (selects; a second tap soon after is a double-tap), a drag that
 * starts on a point or curve (moves or bends it), a drag that starts on empty canvas (a rectangle for
 * a mouse, a lasso for a finger), or, for a finger or stylus held still for the hold time, the
 * radial. A mouse's right button is the rich menu. Two-finger pan and zoom never reach this machine:
 * the sheet's arbiter owns them and sends [CanvasEvent.Cancel] if a second finger lands.
 *
 * Any drag it starts it also ends: [CanvasEvent.Cancel] while dragging produces an
 * [CanvasOutput.EndObjectDrag] or [CanvasOutput.EndSweep] with `committed = false`, so a caller that
 * feeds [CanvasEvent.Cancel] from a `finally` can never leave an edit open.
 */
class CanvasGestureMachine(
    private val config: CanvasGestureConfig = CanvasGestureConfig(),
) {
    private sealed interface Phase {
        data object Idle : Phase

        data class Pressed(
            val down: CanvasEvent.Down,
            val isDouble: Boolean,
        ) : Phase

        data class ObjectDrag(
            val from: Vec2,
        ) : Phase

        data class Sweep(
            val from: Vec2,
        ) : Phase

        data object Held : Phase

        /** A right-click or other press whose outcome is already sent; the rest of it is ignored. */
        data object Spent : Phase
    }

    private data class LastTap(
        val position: Vec2,
        val timestampMs: Long,
    )

    private var phase: Phase = Phase.Idle
    private var lastTap: LastTap? = null

    /** True while a drag or a sweep is open. */
    val isDragging: Boolean get() = phase is Phase.ObjectDrag || phase is Phase.Sweep

    /** True while the radial is open. */
    val isHeld: Boolean get() = phase is Phase.Held

    fun onEvent(event: CanvasEvent): List<CanvasOutput> =
        when (event) {
            is CanvasEvent.Down -> down(event)
            is CanvasEvent.Move -> move(event)
            is CanvasEvent.Up -> up(event)
            is CanvasEvent.Cancel -> cancel()
            is CanvasEvent.HoldElapsed -> holdElapsed(event)
        }

    private fun down(event: CanvasEvent.Down): List<CanvasOutput> {
        val ended = if (phase != Phase.Idle) cancel() else emptyList()
        if (event.secondary) {
            phase = Phase.Spent
            return ended + CanvasOutput.ContextMenu(event.position, event.hit)
        }
        val previous = lastTap
        val double =
            previous != null &&
                event.timestampMs - previous.timestampMs <= config.doubleTapMs &&
                (event.position - previous.position).length() <= config.doubleTapRadiusDp
        phase = Phase.Pressed(event, double)
        return ended
    }

    private fun move(event: CanvasEvent.Move): List<CanvasOutput> {
        return when (val current = phase) {
            is Phase.Pressed -> {
                val travelled = (event.position - current.down.position).length()
                if (travelled <= config.slopFor(current.down.pointer)) return emptyList()
                lastTap = null
                if (current.down.hit is Hit.Nothing && current.down.chord) {
                    phase = Phase.ObjectDrag(current.down.position)
                    val delta = event.position - current.down.position
                    listOf(
                        CanvasOutput.BeginObjectDrag(Hit.Nothing, current.down.position, current.down.additive),
                        CanvasOutput.ObjectDragged(delta.x, delta.y, event.position),
                    )
                } else if (current.down.hit is Hit.Nothing) {
                    phase = Phase.Sweep(current.down.position)
                    val lasso = current.down.pointer != PointerKind.MOUSE
                    listOf(
                        CanvasOutput.BeginSweep(current.down.position, lasso, current.down.additive),
                        CanvasOutput.SweepMoved(event.position),
                    )
                } else {
                    phase = Phase.ObjectDrag(current.down.position)
                    val delta = event.position - current.down.position
                    listOf(
                        CanvasOutput.BeginObjectDrag(current.down.hit, current.down.position, current.down.additive),
                        CanvasOutput.ObjectDragged(delta.x, delta.y, event.position),
                    )
                }
            }

            is Phase.ObjectDrag -> {
                val delta = event.position - current.from
                listOf(CanvasOutput.ObjectDragged(delta.x, delta.y, event.position))
            }

            is Phase.Sweep -> {
                listOf(CanvasOutput.SweepMoved(event.position))
            }

            is Phase.Held -> {
                listOf(CanvasOutput.HeldMoved(event.position))
            }

            Phase.Idle, Phase.Spent -> {
                emptyList()
            }
        }
    }

    private fun up(event: CanvasEvent.Up): List<CanvasOutput> {
        val current = phase
        phase = Phase.Idle
        return when (current) {
            is Phase.Pressed -> {
                if (current.isDouble) {
                    lastTap = null
                    listOf(CanvasOutput.DoubleTap(current.down.position, current.down.hit))
                } else {
                    lastTap = LastTap(current.down.position, event.timestampMs)
                    listOf(CanvasOutput.Tap(current.down.position, current.down.hit, current.down.additive))
                }
            }

            is Phase.ObjectDrag -> {
                listOf(CanvasOutput.EndObjectDrag(committed = true))
            }

            is Phase.Sweep -> {
                listOf(CanvasOutput.EndSweep(event.position, committed = true))
            }

            is Phase.Held -> {
                listOf(CanvasOutput.HeldReleased(event.position))
            }

            Phase.Idle, Phase.Spent -> {
                emptyList()
            }
        }
    }

    private fun cancel(): List<CanvasOutput> {
        val current = phase
        phase = Phase.Idle
        return when (current) {
            is Phase.ObjectDrag -> listOf(CanvasOutput.EndObjectDrag(committed = false))
            is Phase.Sweep -> listOf(CanvasOutput.EndSweep(current.from, committed = false))
            is Phase.Held -> listOf(CanvasOutput.HeldCancelled)
            else -> emptyList()
        }
    }

    private fun holdElapsed(event: CanvasEvent.HoldElapsed): List<CanvasOutput> {
        val current = phase
        if (current !is Phase.Pressed) return emptyList()
        if (current.down.pointer == PointerKind.MOUSE) return emptyList()
        if (event.timestampMs - current.down.timestampMs < config.holdMs) return emptyList()
        phase = Phase.Held
        lastTap = null
        return listOf(CanvasOutput.HoldOpened(current.down.hit, current.down.position))
    }
}
