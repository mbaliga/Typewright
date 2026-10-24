// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.ContourPoint
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.project.PointRef
import com.asoc.typewright.ui.puck.Tool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [DrawController] against a real session and a host that counts what it is asked, with no window in the way. */
class DrawControllerTest {
    private class CountingHost : DrawHost {
        var ticks = 0
        var thumps = 0
        var fitted = 0
        val stepped = mutableListOf<Int>()

        override fun pickTool(tool: Tool) = Unit

        override fun fitGlyph() {
            fitted++
        }

        override fun actualSize() = Unit

        override fun stepGlyph(delta: Int) {
            stepped += delta
        }

        override fun openPalette(goToGlyph: Boolean) = Unit

        override fun openMap() = Unit

        override fun showShortcuts() = Unit

        override fun tick() {
            ticks++
        }

        override fun thump() {
            thumps++
        }
    }

    private fun on(
        x: Int,
        y: Int,
    ) = ContourPoint(Point(x, y), onCurve = true)

    private fun slab() =
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

    /** One font unit is one dp, the origin of the font at (0, 500) on the screen. */
    private val space = DrawSpace(Vec2(0.0, 500.0), 1.0)

    private fun frame(pointer: PointerKind = PointerKind.MOUSE) =
        DrawFrame(
            space,
            canvasWidthDp = 500.0,
            canvasHeightDp = 700.0,
            topInsetDp = 0.0,
            deckHeightDp = 100.0,
            pointer = pointer,
            tool = Tool.SELECT,
        )

    private fun controller(host: CountingHost): DrawController {
        val session = DrawTestProjects.session(DrawTestProjects.small(slab()))
        return DrawController(session, DrawTestProjects.editor(session, "I"), host)
    }

    // ---- ticks ------------------------------------------------------------------------------------

    @Test
    fun aSnapThatTakesHoldTicksOnceAndStayingOnItDoesNot() {
        val host = CountingHost()
        val controller = controller(host)
        val node = PointRef(0, 6)
        val from = space.toScreen(Vec2(350.0, 400.0))
        controller.onOutput(CanvasOutput.BeginObjectDrag(Hit.OnPoint(node, true, 0.0), from, false), frame())

        fun to(dx: Double) = controller.onOutput(CanvasOutput.ObjectDragged(dx, 0.0, Vec2(from.x + dx, from.y)), frame())
        // The corner starts level with the other top corner, so that alignment takes hold with the first move.
        to(20.0)
        assertEquals(1, host.ticks, "the height it already shares with the other top corner")
        to(46.0) // x 396: within 6 of the advance at 400
        assertEquals(2, host.ticks, "the advance line took hold")
        to(47.0)
        assertEquals(2, host.ticks, "still on the same targets")
        to(90.0)
        assertEquals(2, host.ticks, "letting go of the advance line is not a tick")
        assertEquals(1, controller.snaps.size, "only the shared height is left")
        to(48.0)
        assertEquals(3, host.ticks, "taking hold of the advance line again ticks again")
        controller.onOutput(CanvasOutput.EndObjectDrag(committed = true), frame())
        assertEquals(
            400,
            checkNotNull(controller.glyph)
                .contours[0]
                .points[6]
                .point.x,
            "the corner ended on the advance line",
        )
    }

    @Test
    fun theRadialTicksWhenTheChoiceChangesAndThumpsWhenItRuns() {
        val host = CountingHost()
        val controller = controller(host)
        val hit = Hit.OnPoint(PointRef(0, 6), true, 0.0)
        val touch = space.toScreen(Vec2(350.0, 400.0))
        controller.onOutput(CanvasOutput.HoldOpened(hit, touch), frame(PointerKind.TOUCH))
        assertNotNull(controller.radial)
        val away = Vec2(touch.x + 45.0, touch.y + 55.0)
        controller.onOutput(CanvasOutput.HeldMoved(away), frame(PointerKind.TOUCH))
        assertEquals(1, host.ticks, "the first sector chosen ticks")
        controller.onOutput(CanvasOutput.HeldMoved(Vec2(away.x + 2.0, away.y + 2.0)), frame(PointerKind.TOUCH))
        assertEquals(1, host.ticks, "moving within the same sector does not")
        // Straight down from where it touched: the sector across the hub is the one at the top, Delete.
        controller.onOutput(CanvasOutput.HeldMoved(Vec2(touch.x, touch.y + 60.0)), frame(PointerKind.TOUCH))
        assertEquals(2, host.ticks, "a different sector ticks")
        controller.onOutput(CanvasOutput.HeldReleased(Vec2(touch.x, touch.y + 60.0)), frame(PointerKind.TOUCH))
        assertEquals(1, host.thumps, "running a sector is one thump")
        assertNull(controller.radial)
        assertEquals(3, checkNotNull(controller.glyph).contours[0].points.count { it.onCurve }, "Delete took the corner away")
    }

    @Test
    fun releasingOnASectorThatHoldsNothingDoesNotThump() {
        val host = CountingHost()
        val controller = controller(host)
        val touch = space.toScreen(Vec2(350.0, 400.0))
        controller.onOutput(CanvasOutput.HoldOpened(Hit.OnPoint(PointRef(0, 6), true, 0.0), touch), frame(PointerKind.TOUCH))
        // Left and a little down: the sector across the hub is on the right, where the node's ring has no command.
        val away = Vec2(touch.x - 50.0, touch.y + 5.0)
        controller.onOutput(CanvasOutput.HeldMoved(away), frame(PointerKind.TOUCH))
        controller.onOutput(CanvasOutput.HeldReleased(away), frame(PointerKind.TOUCH))
        assertEquals(0, host.thumps, "nothing ran, so nothing thumps")
        assertEquals(4, checkNotNull(controller.glyph).contours[0].points.count { it.onCurve })
    }

    @Test
    fun cancellingTheRadialInTheDeadZoneDoesNotThump() {
        val host = CountingHost()
        val controller = controller(host)
        val touch = space.toScreen(Vec2(350.0, 400.0))
        controller.onOutput(CanvasOutput.HoldOpened(Hit.OnPoint(PointRef(0, 6), true, 0.0), touch), frame(PointerKind.TOUCH))
        controller.onOutput(CanvasOutput.HeldMoved(Vec2(touch.x + 45.0, touch.y + 55.0)), frame(PointerKind.TOUCH))
        controller.onOutput(CanvasOutput.HeldMoved(touch), frame(PointerKind.TOUCH))
        controller.onOutput(CanvasOutput.HeldReleased(touch), frame(PointerKind.TOUCH))
        assertEquals(0, host.thumps)
        assertNull(controller.radial)
    }

    // ---- the ring and the finger -------------------------------------------------------------------------

    @Test
    fun aRingShiftedAwayFromAnEdgeStillWaitsForASweepInsteadOfChoosingAtOnce() {
        val host = CountingHost()
        val controller = controller(host)
        // A touch 20 dp from the left edge: the ring must be moved right to fit, so the hub is not under the finger.
        val touch = Vec2(20.0, 300.0)
        controller.onOutput(CanvasOutput.HoldOpened(Hit.Nothing, touch), frame(PointerKind.TOUCH))
        val ring = assertNotNull(controller.radial)
        assertTrue(ring.center.x > touch.x + 50.0, "the ring was shifted inward")
        assertEquals(touch, ring.anchor, "and remembers where the finger actually touched")
        assertNull((ring.state as RadialState.Sweeping).selected, "no sector is chosen before the finger moves")
        controller.onOutput(CanvasOutput.HeldReleased(touch), frame(PointerKind.TOUCH))
        assertTrue(controller.radial?.state is RadialState.Lifted, "lifting without moving leaves the ring waiting, wherever it was drawn")
    }

    @Test
    fun theRingIsMeasuredFromTheTouchSoTheSameSweepPicksTheSameSectorAnywhere() {
        val host = CountingHost()
        val chosen = mutableListOf<Int?>()
        for (touch in listOf(Vec2(20.0, 300.0), Vec2(250.0, 300.0))) {
            val controller = controller(host)
            controller.onOutput(CanvasOutput.HoldOpened(Hit.Nothing, touch), frame(PointerKind.TOUCH))
            controller.onOutput(CanvasOutput.HeldMoved(Vec2(touch.x + 40.0, touch.y + 50.0)), frame(PointerKind.TOUCH))
            chosen += (controller.radial?.state as RadialState.Sweeping).selected
        }
        assertEquals(chosen[0], chosen[1])
    }
}
