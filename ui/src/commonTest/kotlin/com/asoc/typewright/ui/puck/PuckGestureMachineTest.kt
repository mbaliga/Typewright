// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.puck

import com.asoc.typewright.core.geometry.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * `docs/ARCHITECTURE_REVIEW.md` §4.2's recommendation, exercised with synthetic
 * [PuckInputEvent] sequences -- no Compose `pointerInput`/coroutine scaffolding needed. The hub
 * centre used throughout is [HUB]; positions are chosen so each test's arithmetic is checkable by
 * hand (multiples of the config's own thresholds, or right-angle rotations for the radial).
 */
class PuckGestureMachineTest {
    private val config = PuckGestureConfig()
    private val geometry = PuckGeometry(center = HUB)

    // -----------------------------------------------------------------------------------------
    // Tap / hold arbitration
    // -----------------------------------------------------------------------------------------

    @Test
    fun tapWithNoMovementTogglesTheToolList() {
        var state: PuckGestureState = PuckGestureState.Idle
        val (afterDown, downEvents) = step(state, PuckInputEvent.PointerDown(AWAY_FROM_GRIP, 0))
        state = afterDown
        assertTrue(downEvents.isEmpty())
        assertIs<PuckGestureState.HoldTimerRunning>(state)

        val (afterUp, upEvents) = step(state, PuckInputEvent.PointerUp(100))
        assertEquals(listOf(PuckOutputEvent.ToolListToggled), upEvents)
        assertEquals(PuckGestureState.Idle, afterUp)
    }

    @Test
    fun holdTimeoutOpensTheRadialCentredOnThePuck() {
        val (afterDown, _) = step(PuckGestureState.Idle, PuckInputEvent.PointerDown(AWAY_FROM_GRIP, 0))
        val (afterTimeout, events) = step(afterDown, PuckInputEvent.HoldTimeout(400))

        assertEquals(listOf(PuckOutputEvent.RadialOpened(HUB)), events)
        val radial = assertIs<PuckGestureState.RadialOpen>(afterTimeout)
        assertEquals(HUB, radial.hubCenter)
        assertEquals(null, radial.referenceAngleDegrees)
        assertEquals(0.0, radial.accumulatedAngleDegrees)
        assertEquals(0, radial.currentSector)
        assertEquals(8, radial.sectorsCount)
    }

    @Test
    fun holdTimeoutIsIgnoredOutsideHoldTimerRunning() {
        val (state, events) = step(PuckGestureState.Idle, PuckInputEvent.HoldTimeout(0))
        assertEquals(PuckGestureState.Idle, state)
        assertTrue(events.isEmpty())
    }

    // -----------------------------------------------------------------------------------------
    // Swipe: docs/ARCHITECTURE_REVIEW.md §4.2's two explorer bugs, fixed
    // -----------------------------------------------------------------------------------------

    @Test
    fun movementUnderSlopStaysInHoldTimerRunning() {
        val (afterDown, _) = step(PuckGestureState.Idle, PuckInputEvent.PointerDown(AWAY_FROM_GRIP, 0))
        val (afterSmallMove, events) = step(afterDown, PuckInputEvent.PointerMove(AWAY_FROM_GRIP + Vec2(0.0, 3.0), 10))
        assertTrue(events.isEmpty())
        assertIs<PuckGestureState.HoldTimerRunning>(afterSmallMove)
    }

    @Test
    fun swipeStepsLandExactlyAtThirtyFourNotFortyFive() {
        // docs/ARCHITECTURE_REVIEW.md §4.2: the explorer "discards" the pixels spent crossing the
        // slop, so its tool changes land at 45 px, not 34. Here, one move straight from the down
        // position to exactly 34 dp of vertical travel (which also crosses the 6 dp slop) must
        // register exactly one step, not require 34 + 6 = 40 dp.
        var state: PuckGestureState = PuckGestureState.Idle
        state = step(state, PuckInputEvent.PointerDown(AWAY_FROM_GRIP, 0)).first

        val (afterStep, events) = step(state, PuckInputEvent.PointerMove(AWAY_FROM_GRIP + Vec2(0.0, 34.0), 50))
        assertEquals(listOf(PuckOutputEvent.ToolCycled(1)), events)
        val swiping = assertIs<PuckGestureState.Swiping>(afterStep)
        assertEquals(1, swiping.cumulativeStepsEmitted)
    }

    @Test
    fun swipeStepsLandExactlyAtSixtyEightNotEighty() {
        var state: PuckGestureState = PuckGestureState.Idle
        state = step(state, PuckInputEvent.PointerDown(AWAY_FROM_GRIP, 0)).first
        state = step(state, PuckInputEvent.PointerMove(AWAY_FROM_GRIP + Vec2(0.0, 10.0), 20)).first // under one step, past slop
        assertIs<PuckGestureState.Swiping>(state)

        val (afterFirstStep, firstEvents) = step(state, PuckInputEvent.PointerMove(AWAY_FROM_GRIP + Vec2(0.0, 34.0), 50))
        assertEquals(listOf(PuckOutputEvent.ToolCycled(1)), firstEvents)

        val (afterSecondStep, secondEvents) = step(afterFirstStep, PuckInputEvent.PointerMove(AWAY_FROM_GRIP + Vec2(0.0, 68.0), 90))
        assertEquals(listOf(PuckOutputEvent.ToolCycled(1)), secondEvents)
        assertEquals(2, (afterSecondStep as PuckGestureState.Swiping).cumulativeStepsEmitted)
    }

    @Test
    fun aSingleLargeMoveEmitsEveryStepAtOnceKeepingTheRemainder() {
        // docs/ARCHITECTURE_REVIEW.md §4.2: "A 100 px move in one event cycles one tool, because
        // the accumulator resets to 0." floor(100 / 34) = 2: this must cycle two tools in the one
        // event, not one.
        val (afterDown, _) = step(PuckGestureState.Idle, PuckInputEvent.PointerDown(AWAY_FROM_GRIP, 0))
        val (afterJump, events) = step(afterDown, PuckInputEvent.PointerMove(AWAY_FROM_GRIP + Vec2(0.0, 100.0), 50))

        assertEquals(listOf(PuckOutputEvent.ToolCycled(2)), events)
        assertEquals(2, (afterJump as PuckGestureState.Swiping).cumulativeStepsEmitted)
    }

    @Test
    fun swipingUpCyclesBackwards() {
        val (afterDown, _) = step(PuckGestureState.Idle, PuckInputEvent.PointerDown(AWAY_FROM_GRIP, 0))
        val (afterUpSwipe, events) = step(afterDown, PuckInputEvent.PointerMove(AWAY_FROM_GRIP + Vec2(0.0, -40.0), 50))

        assertEquals(listOf(PuckOutputEvent.ToolCycled(-1)), events)
        assertEquals(-1, (afterUpSwipe as PuckGestureState.Swiping).cumulativeStepsEmitted)
    }

    @Test
    fun reversingTheSwipeEmitsACompensatingNegativeStep() {
        var state: PuckGestureState = PuckGestureState.Idle
        state = step(state, PuckInputEvent.PointerDown(AWAY_FROM_GRIP, 0)).first
        val (afterForward, forwardEvents) = step(state, PuckInputEvent.PointerMove(AWAY_FROM_GRIP + Vec2(0.0, 34.0), 20))
        assertEquals(listOf(PuckOutputEvent.ToolCycled(1)), forwardEvents)

        val (afterReturn, returnEvents) = step(afterForward, PuckInputEvent.PointerMove(AWAY_FROM_GRIP, 40))
        assertEquals(listOf(PuckOutputEvent.ToolCycled(-1)), returnEvents)
        assertEquals(0, (afterReturn as PuckGestureState.Swiping).cumulativeStepsEmitted)
    }

    // -----------------------------------------------------------------------------------------
    // Radial: docs/ARCHITECTURE_REVIEW.md §4.2's hub-angle bugs, fixed
    // -----------------------------------------------------------------------------------------

    @Test
    fun aStraightOutwardMoveFromTheHubAppliesNoRotation() {
        // docs/ARCHITECTURE_REVIEW.md §4.2: "The reference angle is taken at touch-down, on the
        // hub, where the angle is undefined. Moving straight out to r = 80 turned the ring -90
        // deg and changed the tool by two with no rotation." Here the equivalent move must apply
        // zero rotation and emit no detent ticks.
        var state: PuckGestureState = PuckGestureState.Idle
        state = step(state, PuckInputEvent.PointerDown(HUB, 0)).first
        state = step(state, PuckInputEvent.HoldTimeout(400)).first
        assertIs<PuckGestureState.RadialOpen>(state)

        val (afterOutwardMove, events) = step(state, PuckInputEvent.PointerMove(HUB + Vec2(0.0, 80.0), 420))

        assertTrue(events.isEmpty(), "a straight outward move must not tick any detent")
        val radial = assertIs<PuckGestureState.RadialOpen>(afterOutwardMove)
        assertEquals(0.0, radial.accumulatedAngleDegrees)
        assertEquals(0, radial.currentSector)
    }

    @Test
    fun wobblingAcrossTheHubNeverAccumulatesRotation() {
        // docs/ARCHITECTURE_REVIEW.md §4.2: "A 6 px wobble across the centre jumped 4 sectors
        // with one 5 ms tick, and releasing committed it." Crossing in and out of the hub here
        // must re-anchor every time, so the ring's accumulated rotation never moves.
        var state: PuckGestureState = PuckGestureState.Idle
        state = step(state, PuckInputEvent.PointerDown(HUB, 0)).first
        state = step(state, PuckInputEvent.HoldTimeout(400)).first

        val (afterOut1, out1Events) = step(state, PuckInputEvent.PointerMove(HUB + Vec2(30.0, 0.0), 410)) // r=30, angle 0
        assertTrue(out1Events.isEmpty())
        val (afterIn, inEvents) = step(afterOut1, PuckInputEvent.PointerMove(HUB + Vec2(5.0, 0.0), 415)) // r=5 < 24: re-enter hub
        assertTrue(inEvents.isEmpty())
        assertEquals(null, (afterIn as PuckGestureState.RadialOpen).referenceAngleDegrees)

        val (afterOut2, out2Events) = step(afterIn, PuckInputEvent.PointerMove(HUB + Vec2(0.0, 30.0), 420)) // r=30, angle 90 -- re-anchors
        assertTrue(out2Events.isEmpty(), "re-entering the hub must re-anchor, not resume the old reference angle")
        assertEquals(0.0, (afterOut2 as PuckGestureState.RadialOpen).accumulatedAngleDegrees)
        assertEquals(0, afterOut2.currentSector)
    }

    @Test
    fun rotatingNinetyDegreesTicksTwoDetentsInOrder() {
        var state: PuckGestureState = PuckGestureState.Idle
        state = step(state, PuckInputEvent.PointerDown(HUB, 0)).first
        state = step(state, PuckInputEvent.HoldTimeout(400)).first
        state = step(state, PuckInputEvent.PointerMove(HUB + Vec2(30.0, 0.0), 410)).first // anchor at angle 0, r=30

        val (afterRotation, events) = step(state, PuckInputEvent.PointerMove(HUB + Vec2(0.0, 30.0), 450)) // angle 90

        assertEquals(listOf(PuckOutputEvent.RadialDetentTicked(1), PuckOutputEvent.RadialDetentTicked(2)), events)
        assertEquals(2, (afterRotation as PuckGestureState.RadialOpen).currentSector)
        assertEquals(90.0, afterRotation.accumulatedAngleDegrees, absoluteTolerance = 1e-9)
    }

    @Test
    fun releaseOutsideTheHubCommitsTheCurrentSector() {
        var state: PuckGestureState = PuckGestureState.Idle
        state = step(state, PuckInputEvent.PointerDown(HUB, 0)).first
        state = step(state, PuckInputEvent.HoldTimeout(400)).first
        state = step(state, PuckInputEvent.PointerMove(HUB + Vec2(30.0, 0.0), 410)).first
        state = step(state, PuckInputEvent.PointerMove(HUB + Vec2(0.0, 30.0), 450)).first // sector 2, still r=30 >= 24

        val (afterRelease, events) = step(state, PuckInputEvent.PointerUp(500))

        assertEquals(listOf(PuckOutputEvent.RadialCommitted(2)), events)
        assertEquals(PuckGestureState.Idle, afterRelease)
    }

    @Test
    fun releaseInsideTheHubCancels() {
        val (afterDown, _) = step(PuckGestureState.Idle, PuckInputEvent.PointerDown(HUB, 0))
        val (afterOpen, _) = step(afterDown, PuckInputEvent.HoldTimeout(400))
        // lastPosition is still the down position, at the hub itself (radius 0).

        val (afterRelease, events) = step(afterOpen, PuckInputEvent.PointerUp(500))

        assertEquals(listOf(PuckOutputEvent.RadialCancelled), events)
        assertEquals(PuckGestureState.Idle, afterRelease)
    }

    @Test
    fun pointerCancelAlwaysCancelsTheRadialEvenOutsideTheHub() {
        var state: PuckGestureState = PuckGestureState.Idle
        state = step(state, PuckInputEvent.PointerDown(HUB, 0)).first
        state = step(state, PuckInputEvent.HoldTimeout(400)).first
        state = step(state, PuckInputEvent.PointerMove(HUB + Vec2(30.0, 0.0), 410)).first // r=30, would commit on PointerUp

        val (afterCancel, events) = step(state, PuckInputEvent.PointerCancel(420))

        assertEquals(listOf(PuckOutputEvent.RadialCancelled), events)
        assertEquals(PuckGestureState.Idle, afterCancel)
    }

    // -----------------------------------------------------------------------------------------
    // Grip drag, and its >= 44 dp hit-area fix
    // -----------------------------------------------------------------------------------------

    @Test
    fun downOnTheGripStartsAGripDrag() {
        val (state, events) = step(PuckGestureState.Idle, PuckInputEvent.PointerDown(GRIP_CENTER, 0))
        assertTrue(events.isEmpty())
        assertEquals(PuckGestureState.GripDragging(GRIP_CENTER), state)
    }

    @Test
    fun gripDragEmitsDeltasAndReleaseEmitsTheFinalPosition() {
        var state: PuckGestureState = PuckGestureState.Idle
        state = step(state, PuckInputEvent.PointerDown(GRIP_CENTER, 0)).first

        val (afterMove, moveEvents) = step(state, PuckInputEvent.PointerMove(GRIP_CENTER + Vec2(5.0, 5.0), 20))
        assertEquals(listOf(PuckOutputEvent.GripDragged(Vec2(5.0, 5.0))), moveEvents)

        val (afterRelease, releaseEvents) = step(afterMove, PuckInputEvent.PointerUp(40))
        assertEquals(listOf(PuckOutputEvent.GripReleased(GRIP_CENTER + Vec2(5.0, 5.0))), releaseEvents)
        assertEquals(PuckGestureState.Idle, afterRelease)
    }

    @Test
    fun gripHitAreaIsAtLeastFortyFourDpNotTheExplorersEighteenBySeventeen() {
        // docs/ARCHITECTURE_REVIEW.md §4.2, "Grip and ring size": "The grip's hit area is 18x12
        // px, against UI_SPEC §6's 44 dp floor." A deliberate fix, not a port: 20 dp from the
        // grip's own centre is well outside an 18x12 px box (half-height 6 px) but inside the
        // enforced 44 dp square (half 22 dp), and must still register as the grip.
        val withinFixedHitArea = GRIP_CENTER + Vec2(0.0, 20.0)
        val (state, _) = step(PuckGestureState.Idle, PuckInputEvent.PointerDown(withinFixedHitArea, 0))
        assertIs<PuckGestureState.GripDragging>(state)
    }

    @Test
    fun beyondTheFortyFourDpGripHitAreaFallsThroughToTheHoldTimer() {
        val outsideFixedHitArea = GRIP_CENTER + Vec2(0.0, 30.0)
        val (state, _) = step(PuckGestureState.Idle, PuckInputEvent.PointerDown(outsideFixedHitArea, 0))
        assertIs<PuckGestureState.HoldTimerRunning>(state)
    }

    // -----------------------------------------------------------------------------------------
    // Cancellation and defensive no-ops
    // -----------------------------------------------------------------------------------------

    @Test
    fun pointerCancelDuringTheHoldTimerAbortsSilently() {
        val (afterDown, _) = step(PuckGestureState.Idle, PuckInputEvent.PointerDown(AWAY_FROM_GRIP, 0))
        val (afterCancel, events) = step(afterDown, PuckInputEvent.PointerCancel(10))
        assertTrue(events.isEmpty())
        assertEquals(PuckGestureState.Idle, afterCancel)
    }

    @Test
    fun pointerCancelDuringASwipeAbortsSilently() {
        var state: PuckGestureState = PuckGestureState.Idle
        state = step(state, PuckInputEvent.PointerDown(AWAY_FROM_GRIP, 0)).first
        state = step(state, PuckInputEvent.PointerMove(AWAY_FROM_GRIP + Vec2(0.0, 34.0), 20)).first
        assertIs<PuckGestureState.Swiping>(state)

        val (afterCancel, events) = step(state, PuckInputEvent.PointerCancel(30))
        assertTrue(events.isEmpty())
        assertEquals(PuckGestureState.Idle, afterCancel)
    }

    @Test
    fun idleIgnoresMoveUpAndCancel() {
        for (event in listOf(PuckInputEvent.PointerMove(AWAY_FROM_GRIP, 0), PuckInputEvent.PointerUp(0), PuckInputEvent.PointerCancel(0))) {
            val (state, events) = step(PuckGestureState.Idle, event)
            assertEquals(PuckGestureState.Idle, state)
            assertTrue(events.isEmpty())
        }
    }

    // -----------------------------------------------------------------------------------------
    // Config floors (docs/ARCHITECTURE_REVIEW.md §4.2 recommendation 2)
    // -----------------------------------------------------------------------------------------

    @Test
    fun holdTimeoutAndSlopAreFlooredAtTheAppMinimum() {
        val shortPlatform = PuckGestureConfig(platformLongPressTimeoutMs = 100, platformTouchSlopDp = 2.0)
        assertEquals(PuckGestureDefaults.MIN_HOLD_TIMEOUT_MS, shortPlatform.holdTimeoutMs)
        assertEquals(PuckGestureDefaults.MIN_SLOP_DP, shortPlatform.slopDp)

        val longPlatform = PuckGestureConfig(platformLongPressTimeoutMs = 500, platformTouchSlopDp = 18.0)
        assertEquals(500L, longPlatform.holdTimeoutMs)
        assertEquals(18.0, longPlatform.slopDp)
    }

    @Test
    fun defaultsMatchUiSpecAndTheReview() {
        assertEquals(380L, PuckGestureDefaults.MIN_HOLD_TIMEOUT_MS)
        assertEquals(6.0, PuckGestureDefaults.MIN_SLOP_DP)
        assertEquals(34.0, PuckGestureDefaults.SWIPE_STEP_DP)
        assertEquals(24.0, PuckGestureDefaults.RADIAL_HUB_RADIUS_DP)
        assertEquals(45.0, PuckGestureDefaults.RADIAL_DETENT_DEGREES)
        assertEquals(44.0, PuckGestureDefaults.GRIP_MIN_HIT_DP)
        assertEquals(68.0, PuckGestureDefaults.PUCK_DIAMETER_DP)
    }

    // -----------------------------------------------------------------------------------------
    // The stateful convenience wrapper
    // -----------------------------------------------------------------------------------------

    @Test
    fun theMachineWrapperDelegatesToTheSameReducer() {
        val machine = PuckGestureMachine(config)
        assertEquals(PuckGestureState.Idle, machine.state)

        val downEvents = machine.onEvent(PuckInputEvent.PointerDown(AWAY_FROM_GRIP, 0), geometry)
        assertTrue(downEvents.isEmpty())
        assertIs<PuckGestureState.HoldTimerRunning>(machine.state)

        val upEvents = machine.onEvent(PuckInputEvent.PointerUp(50), geometry)
        assertEquals(listOf(PuckOutputEvent.ToolListToggled), upEvents)
        assertEquals(PuckGestureState.Idle, machine.state)
    }

    // -----------------------------------------------------------------------------------------

    private fun step(
        state: PuckGestureState,
        event: PuckInputEvent,
    ): Pair<PuckGestureState, List<PuckOutputEvent>> {
        val transition = reducePuckGesture(state, event, config, geometry)
        return transition.state to transition.events
    }

    private fun assertEquals(
        expected: Double,
        actual: Double,
        absoluteTolerance: Double,
    ) {
        assertTrue(kotlin.math.abs(expected - actual) < absoluteTolerance, "expected $expected, was $actual")
    }

    private companion object {
        val HUB = Vec2(100.0, 200.0)
        val AWAY_FROM_GRIP = Vec2(100.0, 300.0) // far below the puck's grip; radial-adjacent tests set their own down position
        val GRIP_CENTER = PuckGeometry(center = HUB).gripVisualCenter
    }
}
