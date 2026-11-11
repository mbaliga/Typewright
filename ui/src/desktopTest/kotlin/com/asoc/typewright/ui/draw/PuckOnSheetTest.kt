// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.Constraints
import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.ContourPoint
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.ui.puck.PuckEdge
import com.asoc.typewright.ui.puck.PuckGestureDefaults
import com.asoc.typewright.ui.puck.PuckGestureState
import com.asoc.typewright.ui.puck.Tool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The puck on the real sheet, driven by touch pointers: where a finger lands is where the puck is, the
 * grip carries it to the nearer edge, a tap unfolds the tools, a swipe changes the tool, and a held
 * finger opens the tool ring on the puck's own centre. Touch on a real screen is owner-verified on a
 * device only.
 */
class PuckOnSheetTest {
    private fun on(
        x: Int,
        y: Int,
    ) = ContourPoint(Point(x, y), onCurve = true)

    private fun slab(): Glyph =
        Glyph(
            "I",
            400,
            listOf(
                Contour(
                    listOf(
                        on(50, 0),
                        ContourPoint(Point(50, 0), false),
                        ContourPoint(Point(350, 0), false),
                        on(350, 0),
                        ContourPoint(Point(350, 0), false),
                        ContourPoint(Point(350, 400), false),
                        on(350, 400),
                        ContourPoint(Point(350, 400), false),
                        ContourPoint(Point(50, 400), false),
                        on(50, 400),
                        ContourPoint(Point(50, 400), false),
                        ContourPoint(Point(50, 0), false),
                    ),
                    CurveFormat.CUBIC,
                ),
            ),
        )

    private fun withSlab(block: DrawSheetRig.() -> Unit) = withDrawSheet(DrawTestProjects.small(slab()), "I", 720, 1560, 2f, block = block)

    private val radius = PuckGestureDefaults.PUCK_DIAMETER_DP / 2.0

    @Test
    fun theGripCarriesThePuckAcrossTheScreenAndItPinsToTheNearerEdgeWhenLetGo() =
        withSlab {
            val start = pin.center
            val grip = Vec2(start.x, start.y - 25.0)
            press(grip, type = PointerType.Touch)
            // 148 dp to the right and 90 down, in steps: the finger and the puck stay together all the way.
            for (i in 1..10) {
                val at = Vec2(grip.x + 14.8 * i, grip.y + 9.0 * i)
                move(at, type = PointerType.Touch)
                assertEquals(start.x + 14.8 * i, pin.center.x, 0.05, "the puck is under the finger at step $i")
                assertEquals(start.y + 9.0 * i, pin.center.y, 0.05)
            }
            release(Vec2(grip.x + 148.0, grip.y + 90.0), type = PointerType.Touch)
            frames(700L)
            assertEquals(PuckEdge.RIGHT, pin.edge, "past the middle it goes to the right edge")
            assertEquals(360.0 - 18.0 - radius, pin.center.x, 0.05)
            assertEquals(start.y + 90.0, pin.center.y, 0.05, "at the height it was let go")
            assertEquals(PuckGestureState.Idle, puck.gestureState)
        }

    @Test
    fun aTapOnThePuckUnfoldsTheToolListAndAnotherTapFoldsItAgain() =
        withSlab {
            val body = Vec2(pin.center.x, pin.center.y + 10.0)
            press(body, type = PointerType.Touch)
            release(body, type = PointerType.Touch)
            assertTrue(puck.toolListUnfolded)
            press(body, type = PointerType.Touch)
            release(body, type = PointerType.Touch)
            assertEquals(false, puck.toolListUnfolded)
        }

    @Test
    fun sweepingAFingerDownThePuckCyclesTheTool() =
        withSlab {
            val body = Vec2(pin.center.x, pin.center.y + 10.0)
            assertEquals(Tool.SELECT, puck.currentTool)
            press(body, type = PointerType.Touch)
            move(Vec2(body.x, body.y + 40.0), type = PointerType.Touch)
            move(Vec2(body.x, body.y + 80.0), type = PointerType.Touch)
            release(Vec2(body.x, body.y + 80.0), type = PointerType.Touch)
            assertNotEquals(Tool.SELECT, puck.currentTool, "34 dp of travel cycles a tool, and 80 dp cycled it")
        }

    @Test
    fun holdingThePuckOpensTheToolRingOnThePucksOwnCentre() =
        withSlab {
            val body = Vec2(pin.center.x, pin.center.y + 10.0)
            press(body, type = PointerType.Touch)
            Thread.sleep(520)
            frames(2)
            val ring = puck.gestureState as PuckGestureState.RadialOpen
            assertEquals(pin.center, ring.hubCenter, "the hub is the puck's centre in the sheet's own dp")
            release(body, type = PointerType.Touch)
            assertEquals(PuckGestureState.Idle, puck.gestureState)
        }

    @Test
    fun resizingTheWindowRePinsThePuckOnItsEdgeInsteadOfLeavingItBehind() =
        withSlab {
            val before = pin.center
            scene.constraints = Constraints.fixed(1000, 1800)
            frames(6)
            assertEquals(PuckEdge.LEFT, pin.edge)
            assertEquals(before.x, pin.center.x, 1e-6, "still 18 dp from the left edge")
            assertEquals(900.0 * 0.4, pin.center.y, 1e-6, "and still 40% of the way down the taller screen")
        }
}
