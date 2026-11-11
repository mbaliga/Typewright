// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.puck

import com.asoc.typewright.core.geometry.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The puck's pointer handling in the frames it really runs in: a pointer reports positions inside the
 * puck's own box, the gesture machine reasons in the parent's dp, and the box follows a finger that
 * drags the grip, one frame behind. These drive [PuckGestureMachine] with positions converted by
 * [PuckPlacement] exactly as the arbiter converts them, including the lag, and compare the result with
 * what the finger actually did.
 */
class PuckPlacementTest {
    private val config = PuckGestureConfig()
    private val radius = PuckGestureDefaults.PUCK_DIAMETER_DP / 2.0

    private fun topLeftFor(centre: Vec2) = Vec2(centre.x - radius, centre.y - radius)

    @Test
    fun aPositionInsideTheBoxIsTheSamePositionInTheParentOnceTheBoxOriginIsAdded() {
        val placement = PuckPlacement(Vec2(286.0, 278.0))
        assertEquals(Vec2(320.0, 312.0), placement.toParent(Vec2(34.0, 34.0)))
    }

    @Test
    fun aFingerOnTheGripStartsADragWhereverThePuckSitsOnTheScreen() {
        val centre = Vec2(320.0, 312.0)
        val machine = PuckGestureMachine(config)
        val placement = PuckPlacement(topLeftFor(centre))
        val grip = PuckGeometry(centre).gripVisualCenter
        val local = grip - placement.topLeftDp
        machine.onEvent(PuckInputEvent.PointerDown(placement.toParent(local), 0), PuckGeometry(centre))
        assertIs<PuckGestureState.GripDragging>(machine.state)
    }

    @Test
    fun theRawBoxPositionOfTheSameTouchMissesTheGripBecauseItIsInTheWrongFrame() {
        // What the puck did before the conversion: the box-local position fed to a machine that thinks in parent dp.
        val centre = Vec2(320.0, 312.0)
        val machine = PuckGestureMachine(config)
        val placement = PuckPlacement(topLeftFor(centre))
        val grip = PuckGeometry(centre).gripVisualCenter
        machine.onEvent(PuckInputEvent.PointerDown(grip - placement.topLeftDp, 0), PuckGeometry(centre))
        assertIs<PuckGestureState.HoldTimerRunning>(machine.state, "an unconverted position is nowhere near the grip")
    }

    /**
     * The puck's box as Compose has it: where it was last laid out, which is what a pointer is reported
     * against, and which follows the machine's state one frame late. [placement] is told about it only by
     * [layOut], as `onGloballyPositioned` does, so the test's idea of where the finger is never comes
     * from the object under test.
     */
    private class Box(
        var origin: Vec2,
        val placement: PuckPlacement,
    ) {
        fun reported(finger: Vec2): Vec2 = finger - origin

        fun layOut(newOrigin: Vec2) {
            origin = newOrigin
            placement.topLeftDp = newOrigin
        }
    }

    @Test
    fun draggingTheGripMovesThePuckExactlyAsFarAsTheFingerEvenThoughTheBoxFollowsOneFrameLate() {
        var centre = Vec2(100.0, 300.0)
        val box = Box(topLeftFor(centre), PuckPlacement(topLeftFor(centre)))
        val machine = PuckGestureMachine(config)
        var finger = PuckGeometry(centre).gripVisualCenter
        val start = finger

        fun deliver(event: (Vec2) -> PuckInputEvent) {
            // Compose reports the finger inside the box as it stands; the arbiter adds the placement back.
            val outputs = machine.onEvent(event(box.placement.toParent(box.reported(finger))), PuckGeometry(centre))
            for (out in outputs) if (out is PuckOutputEvent.GripDragged) centre += out.delta
        }
        deliver { PuckInputEvent.PointerDown(it, 0) }
        repeat(8) { step ->
            finger += Vec2(7.0, 5.0)
            deliver { PuckInputEvent.PointerMove(it, step.toLong()) }
            assertTrue(box.origin != topLeftFor(centre), "the box is still where it was: it lags the puck by this move")
            // The box catches up with the state only now, after the event that moved it.
            box.layOut(topLeftFor(centre))
        }
        assertEquals(Vec2(100.0 + 56.0, 300.0 + 40.0), centre, "the puck is where the finger took it")
        assertEquals(finger - start, centre - Vec2(100.0, 300.0))
    }

    @Test
    fun aPlacementThatIsNeverUpdatedLosesTheFingerAfterTheFirstMove() {
        // The failure the placement exists to prevent: converting every move with the origin the gesture began at.
        var centre = Vec2(100.0, 300.0)
        val box = Box(topLeftFor(centre), PuckPlacement(topLeftFor(centre)))
        val machine = PuckGestureMachine(config)
        var finger = PuckGeometry(centre).gripVisualCenter
        machine.onEvent(PuckInputEvent.PointerDown(box.placement.toParent(box.reported(finger)), 0), PuckGeometry(centre))
        repeat(8) { step ->
            finger += Vec2(7.0, 5.0)
            for (out in machine.onEvent(
                PuckInputEvent.PointerMove(box.placement.toParent(box.reported(finger)), step.toLong()),
                PuckGeometry(centre),
            )) {
                if (out is PuckOutputEvent.GripDragged) centre += out.delta
            }
            box.origin = topLeftFor(centre) // the box moves, but the placement is not told
        }
        assertNotEquals(Vec2(100.0 + 56.0, 300.0 + 40.0), centre, "a stale origin does not follow the finger")
    }

    @Test
    fun twoMovesReportedBeforeTheBoxIsLaidOutAgainBothConvertAgainstTheSameOriginExactly() {
        var centre = Vec2(100.0, 300.0)
        val box = Box(topLeftFor(centre), PuckPlacement(topLeftFor(centre)))
        val machine = PuckGestureMachine(config)
        var finger = PuckGeometry(centre).gripVisualCenter
        machine.onEvent(PuckInputEvent.PointerDown(box.placement.toParent(box.reported(finger)), 0), PuckGeometry(centre))
        // The puck has moved on, but the box has not been laid out again: both moves are reported against the old box.
        repeat(2) {
            finger += Vec2(10.0, 0.0)
            for (out in machine.onEvent(
                PuckInputEvent.PointerMove(box.placement.toParent(box.reported(finger)), 1),
                PuckGeometry(centre),
            )) {
                if (out is PuckOutputEvent.GripDragged) centre += out.delta
            }
        }
        assertEquals(Vec2(120.0, 300.0), centre, "20 dp of finger travel is 20 dp of puck travel")
        assertEquals(topLeftFor(Vec2(100.0, 300.0)), box.placement.topLeftDp, "and the placement is still the old box's")
    }

    @Test
    fun theRadialOpensOnThePucksOwnCentreAndAMoveOutPastTheHubIsMeasuredFromIt() {
        val centre = Vec2(320.0, 312.0)
        val placement = PuckPlacement(topLeftFor(centre))
        val machine = PuckGestureMachine(config)
        val down = Vec2(centre.x, centre.y + 10.0)
        machine.onEvent(PuckInputEvent.PointerDown(placement.toParent(down - placement.topLeftDp), 0), PuckGeometry(centre))
        machine.onEvent(PuckInputEvent.HoldTimeout(400), PuckGeometry(centre))
        val open = assertIs<PuckGestureState.RadialOpen>(machine.state)
        assertEquals(centre, open.hubCenter)
        val out = Vec2(centre.x + 60.0, centre.y)
        machine.onEvent(PuckInputEvent.PointerMove(placement.toParent(out - placement.topLeftDp), 500), PuckGeometry(centre))
        val moved = assertIs<PuckGestureState.RadialOpen>(machine.state)
        assertNotEquals(null, moved.referenceAngleDegrees, "60 dp from the hub is outside its 24 dp dead zone")
        assertEquals(out, moved.lastPosition)
    }

    // ---- the held puck as a modifier -------------------------------------------------------------

    @Test
    fun aFingerWaitingOnThePuckIsAModifierButOneThatHasBecomeASwipeIsNot() {
        val state = PuckUiState()
        assertFalse(state.modifierHeld, "nothing is touching the puck")
        state.fingerOnPuck = true
        state.gestureState = PuckGestureState.HoldTimerRunning(Vec2(0.0, 0.0), 0, Vec2(0.0, 0.0))
        assertTrue(state.modifierHeld)
        state.gestureState = PuckGestureState.Swiping(Vec2(0.0, 0.0), 0)
        assertFalse(state.modifierHeld, "a swipe is the puck doing its own work")
    }

    @Test
    fun takingTheHoldKeepsItAModifierUntilTheFingerLifts() {
        val state = PuckUiState()
        state.fingerOnPuck = true
        state.gestureState = PuckGestureState.HoldTimerRunning(Vec2(0.0, 0.0), 0, Vec2(0.0, 0.0))
        state.takeHoldAsModifier()
        state.gestureState = PuckGestureState.Idle // the arbiter cancelled the puck's own gesture
        assertTrue(state.modifierHeld, "the finger is still down, and the canvas has its modifier")
        state.fingerOnPuck = false
        assertFalse(state.modifierHeld)
    }

    @Test
    fun takingTheHoldDoesNothingWhenThePuckIsNotHeld() {
        val state = PuckUiState()
        state.takeHoldAsModifier()
        assertFalse(state.usedAsModifier)
    }

    @Test
    fun aCancelledHoldCannotOpenTheRadialWhenItsTimerFiresAnyway() {
        val machine = PuckGestureMachine(config)
        val geometry = PuckGeometry(Vec2(100.0, 300.0))
        machine.onEvent(PuckInputEvent.PointerDown(Vec2(100.0, 320.0), 0), geometry)
        machine.onEvent(PuckInputEvent.PointerCancel(100), geometry)
        machine.onEvent(PuckInputEvent.HoldTimeout(400), geometry)
        assertEquals(PuckGestureState.Idle, machine.state)
    }

    // ---- where the puck is pinned ----------------------------------------------------------------

    @Test
    fun theDefaultPinIsOnTheLeftEdgeFortyPercentDown() {
        val canvas = Vec2(360.0, 780.0)
        val at = PuckPinState.defaultFor(canvas)
        assertEquals(18.0 + radius, at.x)
        assertEquals(312.0, at.y)
        assertEquals(canvas.x - 18.0 - radius, PuckPinState.defaultFor(canvas, PuckEdge.RIGHT).x)
    }

    @Test
    fun aResizedCanvasRePinsThePuckOnItsOwnEdgeAtTheSameHeightFraction() {
        val pin = PuckPinState(PuckPinState.defaultFor(Vec2(360.0, 780.0)), PuckEdge.LEFT)
        pin.fitTo(Vec2(1280.0, 800.0))
        assertEquals(18.0 + radius, pin.center.x)
        assertEquals(320.0, pin.center.y)
        val right = PuckPinState(PuckPinState.defaultFor(Vec2(360.0, 780.0), PuckEdge.RIGHT), PuckEdge.RIGHT)
        right.fitTo(Vec2(1280.0, 800.0))
        assertEquals(1280.0 - 18.0 - radius, right.center.x)
    }

    @Test
    fun releasingTheGripPinsToTheNearerEdgeAndRemembersTheHeight() {
        val canvas = Vec2(360.0, 780.0)
        val pin = PuckPinState(Vec2(250.0, 500.0), PuckEdge.LEFT)
        val target = pin.nearestEdgeTarget(canvas)
        assertEquals(PuckEdge.RIGHT, pin.edge)
        assertEquals(canvas.x - 18.0 - radius, target.x)
        assertEquals(500.0, target.y)
        pin.fitTo(Vec2(360.0, 1000.0))
        assertEquals(500.0 / 780.0 * 1000.0, pin.center.y, 1e-9, "the height it was dropped at is kept as a fraction")
    }

    @Test
    fun aPuckDroppedAboveOrBelowTheCanvasIsPulledBackInside() {
        val canvas = Vec2(360.0, 780.0)
        val low = PuckPinState(Vec2(40.0, 2000.0), PuckEdge.LEFT).nearestEdgeTarget(canvas)
        assertEquals(780.0 - radius, low.y)
        val high = PuckPinState(Vec2(40.0, -50.0), PuckEdge.LEFT).nearestEdgeTarget(canvas)
        assertEquals(radius, high.y)
    }
}
