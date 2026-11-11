// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.project.PointRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CanvasGestureMachineTest {
    private val node = Hit.OnPoint(PointRef(0, 3), onCurve = true, distanceDp = 2.0)
    private val curve = Hit.OnSegment(SegmentKey(0, 1), 0.4, Vec2(10.0, 10.0), 3.0)
    private val here = Vec2(100.0, 100.0)

    private fun machine() = CanvasGestureMachine()

    private fun down(
        hit: Hit,
        t: Long = 0,
        pointer: PointerKind = PointerKind.TOUCH,
        at: Vec2 = here,
        secondary: Boolean = false,
        additive: Boolean = false,
    ) = CanvasEvent.Down(at, t, hit, pointer, secondary, additive)

    @Test
    fun aQuickPressAndReleaseIsATapOnWhatWasUnderIt() {
        val m = machine()
        m.onEvent(down(node))
        val out = m.onEvent(CanvasEvent.Up(here, 90))
        assertEquals(listOf(CanvasOutput.Tap(here, node, additive = false)), out)
    }

    @Test
    fun aShiftTapIsAdditive() {
        val m = machine()
        m.onEvent(down(node, additive = true))
        assertEquals(true, (m.onEvent(CanvasEvent.Up(here, 40)).single() as CanvasOutput.Tap).additive)
    }

    @Test
    fun aSecondTapSoonAfterAndNearbyIsADoubleTapInsteadOfATap() {
        val m = machine()
        m.onEvent(down(node, t = 0))
        m.onEvent(CanvasEvent.Up(here, 60))
        m.onEvent(down(node, t = 200, at = Vec2(104.0, 98.0)))
        val out = m.onEvent(CanvasEvent.Up(here, 250))
        assertIs<CanvasOutput.DoubleTap>(out.single())
    }

    @Test
    fun aSecondTapTooLateOrTooFarIsJustAnotherTap() {
        val late = machine()
        late.onEvent(down(node, t = 0))
        late.onEvent(CanvasEvent.Up(here, 60))
        late.onEvent(down(node, t = 900))
        assertIs<CanvasOutput.Tap>(late.onEvent(CanvasEvent.Up(here, 950)).single())
        val far = machine()
        far.onEvent(down(node, t = 0))
        far.onEvent(CanvasEvent.Up(here, 60))
        far.onEvent(down(node, t = 150, at = Vec2(200.0, 100.0)))
        assertIs<CanvasOutput.Tap>(far.onEvent(CanvasEvent.Up(here, 200)).single())
    }

    @Test
    fun movingPastTheSlopOnAPointStartsAnObjectDragThatReportsTotalMovement() {
        val m = machine()
        m.onEvent(down(node))
        assertTrue(m.onEvent(CanvasEvent.Move(Vec2(104.0, 100.0), 20)).isEmpty(), "inside the slop nothing happens")
        val begun = m.onEvent(CanvasEvent.Move(Vec2(120.0, 90.0), 40))
        assertIs<CanvasOutput.BeginObjectDrag>(begun[0])
        assertEquals(CanvasOutput.ObjectDragged(20.0, -10.0, Vec2(120.0, 90.0)), begun[1])
        val moved = m.onEvent(CanvasEvent.Move(Vec2(130.0, 80.0), 60)).single()
        assertEquals(CanvasOutput.ObjectDragged(30.0, -20.0, Vec2(130.0, 80.0)), moved)
        assertTrue(m.isDragging)
        assertEquals(CanvasOutput.EndObjectDrag(committed = true), m.onEvent(CanvasEvent.Up(Vec2(130.0, 80.0), 80)).single())
        assertFalse(m.isDragging)
    }

    @Test
    fun aDragOnACurveAlsoBeginsAnObjectDrag() {
        val m = machine()
        m.onEvent(down(curve, pointer = PointerKind.MOUSE))
        val begun = m.onEvent(CanvasEvent.Move(Vec2(110.0, 100.0), 10))
        assertEquals(curve, (begun[0] as CanvasOutput.BeginObjectDrag).hit)
    }

    @Test
    fun cancellingAnObjectDragEndsItUncommittedSoNoEditIsLeftOpen() {
        val m = machine()
        m.onEvent(down(node))
        m.onEvent(CanvasEvent.Move(Vec2(130.0, 100.0), 30))
        assertEquals(CanvasOutput.EndObjectDrag(committed = false), m.onEvent(CanvasEvent.Cancel(50)).single())
        assertFalse(m.isDragging)
        assertTrue(m.onEvent(CanvasEvent.Cancel(60)).isEmpty(), "a second cancel does nothing")
    }

    @Test
    fun aNewPressWhileADragIsOpenEndsTheDragFirst() {
        val m = machine()
        m.onEvent(down(node))
        m.onEvent(CanvasEvent.Move(Vec2(130.0, 100.0), 30))
        val out = m.onEvent(down(curve, t = 100))
        assertEquals(CanvasOutput.EndObjectDrag(committed = false), out.single())
    }

    @Test
    fun aDragOnEmptyCanvasIsALassoForAFingerAndARectangleForAMouse() {
        val finger = machine()
        finger.onEvent(down(Hit.Nothing))
        val lasso = finger.onEvent(CanvasEvent.Move(Vec2(140.0, 100.0), 20))
        assertEquals(CanvasOutput.BeginSweep(here, lasso = true, additive = false), lasso[0])
        val mouse = machine()
        mouse.onEvent(down(Hit.Nothing, pointer = PointerKind.MOUSE))
        val rectangle = mouse.onEvent(CanvasEvent.Move(Vec2(140.0, 100.0), 20))
        assertEquals(CanvasOutput.BeginSweep(here, lasso = false, additive = false), rectangle[0])
        assertEquals(
            CanvasOutput.EndSweep(Vec2(150.0, 110.0), committed = true),
            mouse.onEvent(CanvasEvent.Up(Vec2(150.0, 110.0), 50)).single(),
        )
    }

    @Test
    fun cancellingASweepEndsItUncommitted() {
        val m = machine()
        m.onEvent(down(Hit.Nothing))
        m.onEvent(CanvasEvent.Move(Vec2(140.0, 100.0), 20))
        assertEquals(CanvasOutput.EndSweep(here, committed = false), m.onEvent(CanvasEvent.Cancel(30)).single())
    }

    @Test
    fun holdingAFingerStillOverANodeOpensTheRadial() {
        val m = machine()
        m.onEvent(down(node))
        assertTrue(m.onEvent(CanvasEvent.HoldElapsed(100)).isEmpty(), "too soon")
        assertEquals(CanvasOutput.HoldOpened(node, here), m.onEvent(CanvasEvent.HoldElapsed(380)).single())
        assertTrue(m.isHeld)
        assertEquals(CanvasOutput.HeldMoved(Vec2(130.0, 130.0)), m.onEvent(CanvasEvent.Move(Vec2(130.0, 130.0), 500)).single())
        assertEquals(CanvasOutput.HeldReleased(Vec2(130.0, 130.0)), m.onEvent(CanvasEvent.Up(Vec2(130.0, 130.0), 600)).single())
        assertFalse(m.isHeld)
    }

    @Test
    fun holdingOnEmptyCanvasOpensTheRadialForTheGlyph() {
        val m = machine()
        m.onEvent(down(Hit.Nothing))
        assertEquals(CanvasOutput.HoldOpened(Hit.Nothing, here), m.onEvent(CanvasEvent.HoldElapsed(400)).single())
    }

    @Test
    fun aHoldThatMovedFirstIsADragNotARadial() {
        val m = machine()
        m.onEvent(down(node))
        m.onEvent(CanvasEvent.Move(Vec2(130.0, 100.0), 100))
        assertTrue(m.onEvent(CanvasEvent.HoldElapsed(400)).isEmpty())
    }

    @Test
    fun aMouseHoldNeverOpensTheRadialBecauseItsRightClickIsTheMenu() {
        val m = machine()
        m.onEvent(down(node, pointer = PointerKind.MOUSE))
        assertTrue(m.onEvent(CanvasEvent.HoldElapsed(500)).isEmpty())
    }

    @Test
    fun cancellingTheRadialSaysSoAndASecondaryPressIsTheContextMenu() {
        val m = machine()
        m.onEvent(down(node))
        m.onEvent(CanvasEvent.HoldElapsed(400))
        assertEquals(CanvasOutput.HeldCancelled, m.onEvent(CanvasEvent.Cancel(450)).single())
        val menu = machine()
        val out = menu.onEvent(down(node, pointer = PointerKind.MOUSE, secondary = true))
        assertEquals(CanvasOutput.ContextMenu(here, node), out.single())
        assertTrue(menu.onEvent(CanvasEvent.Up(here, 80)).isEmpty(), "the release of a right-click does nothing more")
    }

    @Test
    fun aDragForgetsThePreviousTapSoItIsNotADoubleTap() {
        val m = machine()
        m.onEvent(down(node, t = 0))
        m.onEvent(CanvasEvent.Up(here, 60))
        m.onEvent(down(node, t = 100))
        m.onEvent(CanvasEvent.Move(Vec2(150.0, 100.0), 120))
        m.onEvent(CanvasEvent.Up(Vec2(150.0, 100.0), 160))
        m.onEvent(down(node, t = 200))
        assertIs<CanvasOutput.Tap>(m.onEvent(CanvasEvent.Up(here, 240)).single())
    }
}
