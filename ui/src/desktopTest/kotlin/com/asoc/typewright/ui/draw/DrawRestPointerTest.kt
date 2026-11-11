// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.core.geometry.brokenSmoothNodes
import com.asoc.typewright.core.geometry.polygonContour
import com.asoc.typewright.core.geometry.signedArea
import com.asoc.typewright.project.GlyphRef
import com.asoc.typewright.project.PointRef
import com.asoc.typewright.ui.puck.PrimitiveKind
import com.asoc.typewright.ui.puck.Tool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tangent, Hobby, Primitives and Measure driven by the pointer and keys of the real sheet, and the review fixes to the
 * Pen, the Knife and Tidy: one undo step per gesture, a locked glyph refuses, a refusal says why.
 */
class DrawRestPointerTest {
    private fun slab(): Glyph =
        Glyph("I", 400, listOf(polygonContour(listOf(Point(50, 0), Point(350, 0), Point(350, 400), Point(50, 400)), CurveFormat.CUBIC)))

    /** A 100 square with the outgoing handle of the corner (100, 0) pulled out to (130, 30): handle (0, 4). */
    private fun bent(): Glyph {
        val square = polygonContour(listOf(Point(0, 0), Point(100, 0), Point(100, 100), Point(0, 100)), CurveFormat.CUBIC)
        val moved = square.copy(points = square.points.mapIndexed { i, p -> if (i == 4) p.copy(point = Point(130, 30)) else p })
        return Glyph("t", 200, listOf(moved))
    }

    private fun arc(): Glyph =
        Glyph(
            "h",
            400,
            listOf(
                polygonContour(
                    listOf(Point(0, 0), Point(60, 80), Point(150, 110), Point(240, 80), Point(300, 0), Point(150, -60)),
                    CurveFormat.CUBIC,
                ),
            ),
        )

    private fun DrawSheetRig.glyph(name: String): Glyph =
        session.state.value.font
            .glyph(GlyphRef("regular", name))!!

    private fun DrawSheetRig.undoSteps(): Int = session.history.value.undoLabels.size

    private fun DrawSheetRig.font(
        x: Int,
        y: Int,
    ): Vec2 = room.space().toScreen(Vec2(x.toDouble(), y.toDouble()))

    private fun withGlyph(
        glyph: Glyph,
        block: DrawSheetRig.() -> Unit,
    ) = withDrawSheet(DrawTestProjects.small(glyph), glyph.name, 720, 1560, 2f, block = block)

    private fun withEmpty(block: DrawSheetRig.() -> Unit) = withGlyph(Glyph("a", 600, emptyList()), block)

    // ---- tangent --------------------------------------------------------------------------------

    @Test
    fun makeTangentTurnsTheSelectedHandleInLineAsOneUndoStepAndUndoGivesBackTheOutline() =
        withGlyph(bent()) {
            val before = glyph("t")
            controller.selection = DrawSelection(setOf(PointRef(0, 4)))
            frames(2)
            key(Keys.T, ctrl = true, shift = true)
            val after = glyph("t").contours[0]
            assertEquals(0, after.points[4].point.y, "the handle now runs along the straight side that arrives at its node")
            assertTrue(after.points[3].smooth && after.brokenSmoothNodes().isEmpty())
            assertEquals(1, undoSteps())
            key(Keys.Z, ctrl = true)
            assertEquals(before, glyph("t"))
        }

    @Test
    fun makeTangentOnARetractedHandleSaysWhyAndMakesNoStep() =
        withGlyph(bent()) {
            // Handle (0, 2) is the incoming one of the same corner: retracted onto its node in this outline.
            controller.selection = DrawSelection(setOf(PointRef(0, 2)))
            frames(2)
            key(Keys.T, ctrl = true, shift = true)
            val notice = assertIs<DrawNotice.Toast>(controller.notice)
            assertTrue(notice.text.contains("pulled in"), notice.text)
            assertEquals(0, undoSteps())
        }

    @Test
    fun makeTangentOnALockedGlyphOffersUnlockAndChangesNothing() =
        withGlyph(bent()) {
            controller.editor.approve()
            val before = glyph("t")
            controller.selection = DrawSelection(setOf(PointRef(0, 4)))
            frames(2)
            key(Keys.T, ctrl = true, shift = true)
            assertIs<DrawNotice.Locked>(controller.notice)
            assertEquals(before, glyph("t"))
        }

    @Test
    fun levelTheHandleTurnsItAlongTheMetricLineItsNodeSitsOn() =
        withGlyph(bent()) {
            controller.selection = DrawSelection(setOf(PointRef(0, 4)))
            frames(2)
            val command = DrawCommands.build(DrawCommandContext(controller.editor, controller.selection, controller.metrics(), controller))
            val level = command.first { it.id == "tangent-metric" }
            assertTrue(level.enabled, level.reason)
            controller.run(level)
            assertEquals(
                0,
                glyph("t")
                    .contours[0]
                    .points[4]
                    .point.y,
                "the node is on the baseline, so the handle lies along it",
            )
            assertEquals(1, undoSteps())
        }

    // ---- Hobby ----------------------------------------------------------------------------------

    @Test
    fun hobbyReshapesTheHandlesBetweenThreeNeighbouringNodesAndOnlyThose() =
        withGlyph(arc()) {
            val before = glyph("h").contours[0]
            controller.selection = DrawSelection(setOf(PointRef(0, 3), PointRef(0, 6), PointRef(0, 9)))
            frames(2)
            key(Keys.H)
            val after = glyph("h").contours[0]
            for (i in before.points.indices) {
                if (before.points[i].onCurve) assertEquals(before.points[i].point, after.points[i].point, "node $i stays")
            }
            assertTrue(after.points[4] != before.points[4] || after.points[5] != before.points[5], "a handle between the nodes moved")
            assertEquals(before.points[1], after.points[1], "the segment before the run keeps its handles")
            assertEquals(before.points[10], after.points[10], "and so does the one after")
            assertEquals(1, undoSteps())
            key(Keys.Z, ctrl = true)
            assertEquals(before, glyph("h").contours[0])
        }

    @Test
    fun hobbyWithTooFewNodesSaysSoAndLockedGlyphsRefuse() =
        withGlyph(arc()) {
            controller.selection = DrawSelection(setOf(PointRef(0, 3), PointRef(0, 6)))
            frames(2)
            key(Keys.H)
            assertTrue(assertIs<DrawNotice.Toast>(controller.notice).text.contains("three or more"))
            assertEquals(0, undoSteps())
            controller.editor.approve()
            controller.selection = DrawSelection(setOf(PointRef(0, 3), PointRef(0, 6), PointRef(0, 9)))
            frames(2)
            key(Keys.H)
            assertIs<DrawNotice.Locked>(controller.notice)
        }

    // ---- primitives -----------------------------------------------------------------------------

    @Test
    fun draggingARectangleFromTheBaselineToTheXHeightLandsOnBothLinesAsOneUndoStep() =
        withEmpty {
            key(Keys.S)
            assertEquals(Tool.PRIMITIVES, puck.currentTool)
            puck.selectPrimitive(PrimitiveKind.RECTANGLE)
            frames(2)
            val from = font(100, 4)
            val to = font(300, 497)
            press(from)
            move(to)
            val live = assertNotNull(controller.primitiveLive, "the shape shows while it is dragged")
            assertEquals(0, live.minY)
            assertEquals(500, live.maxY)
            assertEquals(0, glyph("a").contours.size, "and nothing is in the glyph until the pointer lifts")
            release(to)
            val made = glyph("a").contours.single()
            assertEquals(4, made.points.count { it.onCurve })
            assertEquals(0, made.points.minOf { it.point.y })
            assertEquals(500, made.points.maxOf { it.point.y })
            assertNull(controller.primitiveLive)
            assertEquals(1, undoSteps())
            key(Keys.Z, ctrl = true)
            assertEquals(0, glyph("a").contours.size)
        }

    @Test
    fun aCircleIsFourOnCurvePointsAndOneInsideAnotherContourWindsTheOtherWay() =
        withGlyph(slab()) {
            key(Keys.S)
            puck.selectPrimitive(PrimitiveKind.CIRCLE)
            frames(2)
            val from = font(120, 100)
            val to = font(280, 300)
            drag(from, to)
            val g = glyph("I")
            assertEquals(2, g.contours.size)
            val circle = g.contours[1]
            assertEquals(4, circle.points.count { it.onCurve })
            assertEquals(8, circle.points.count { !it.onCurve })
            assertTrue(circle.signedArea() < 0, "a counter inside the slab winds clockwise")
            assertTrue(g.contours[0].signedArea() > 0)
            assertEquals(1, undoSteps())
        }

    @Test
    fun primitivesWithNoShapePickedSayToPickOneAndMakeNothing() =
        withEmpty {
            key(Keys.S)
            assertNull(puck.primitiveKind)
            drag(font(100, 100), font(300, 300))
            assertTrue(assertIs<DrawNotice.Toast>(controller.notice).text.contains("Pick a shape"))
            assertEquals(0, glyph("a").contours.size)
            assertEquals(0, undoSteps())
        }

    @Test
    fun primitivesOnALockedGlyphOfferUnlockAndMakeNothing() =
        withEmpty {
            controller.editor.approve()
            key(Keys.S)
            puck.selectPrimitive(PrimitiveKind.ELLIPSE)
            frames(2)
            drag(font(100, 100), font(300, 300))
            assertIs<DrawNotice.Locked>(controller.notice)
            assertEquals(0, glyph("a").contours.size)
        }

    @Test
    fun aTinyDragMakesNoShapeAndSaysHowFarToGo() =
        withEmpty {
            key(Keys.S)
            puck.selectPrimitive(PrimitiveKind.RECTANGLE)
            frames(2)
            val p = font(100, 100)
            drag(p, Vec2(p.x + 0.3, p.y + 0.3), steps = 2)
            assertEquals(0, glyph("a").contours.size)
            assertEquals(0, undoSteps())
        }

    // ---- measure --------------------------------------------------------------------------------

    @Test
    fun measureReadsTwoPlacesAndLeavesTheGlyphAndTheHistoryAlone() =
        withGlyph(slab()) {
            val before = glyph("I")
            key(Keys.M)
            assertEquals(Tool.MEASURE, puck.currentTool)
            assertEquals("Measure · tap two places", controller.modeLine)
            click(screenOf(0, 0))
            click(screenOf(0, 6))
            val m = assertNotNull(controller.measureState.measurement)
            assertEquals(Vec2(50.0, 0.0), m.a, "a tap on a node measures from the node itself")
            assertEquals(Vec2(350.0, 400.0), m.b)
            assertEquals(500.0, m.distance, 1e-9)
            assertEquals("Δx 300 · Δy 400 · 500 units · 53.1°", controller.modeLine)
            assertEquals(before, glyph("I"))
            assertEquals(0, undoSteps(), "measuring is a reading and makes no undo step")
            key(Keys.ESCAPE)
            assertNull(controller.measureState.first)
        }

    @Test
    fun twoQuickMeasureTapsAreTwoEndsNotAViewRefit() =
        withGlyph(slab()) {
            key(Keys.M)
            val frame =
                DrawFrame(room.space(), 360.0, 780.0, 100.0, 170.0, PointerKind.MOUSE, Tool.MEASURE)
            controller.onOutput(CanvasOutput.Tap(Vec2(60.0, 300.0), Hit.Nothing, false), frame)
            controller.onOutput(CanvasOutput.DoubleTap(Vec2(120.0, 300.0), Hit.Nothing), frame)
            assertNotNull(controller.measureState.measurement)
        }

    // ---- the review fixes -----------------------------------------------------------------------

    @Test
    fun aRefusedSplitOnAnUnlockedGlyphSaysWhyAndOnlyALockedOneOffersUnlock() =
        withGlyph(slab()) {
            controller.selection = DrawSelection(setOf(PointRef(0, 0), PointRef(0, 3)))
            frames(2)
            key(Keys.K, ctrl = true, shift = true)
            val toast = assertIs<DrawNotice.Toast>(controller.notice, "a refused cut is not a lock")
            assertTrue(toast.text.contains("no area"), toast.text)
            assertEquals(0, undoSteps())
            controller.editor.approve()
            key(Keys.K, ctrl = true, shift = true)
            assertIs<DrawNotice.Locked>(controller.notice)
        }

    @Test
    fun aPendingTidyLosesItsCrossesTheMomentTheOutlineChangesAndItsBarOnTheNextInput() {
        withDrawSheet(DrawTestProjects.hyleDecoCubic(), "H", 720, 1560, 2f) {
            key(Keys.Y, ctrl = true, shift = true)
            assertIs<DrawNotice.Proposal>(controller.notice)
            assertTrue(controller.tidyRemoved.isNotEmpty())
            // An edit made some other way (here straight through the editor, as another room would) leaves the crosses on a changed outline.
            controller.editor.moveBy(DrawSelection(setOf(PointRef(0, 0))), 3, 0)
            assertTrue(controller.tidyRemoved.isEmpty(), "the crosses name an outline that is gone")
            click(Vec2(5.0, 400.0))
            assertNull(controller.notice, "and the bar goes with the next touch")
            controller.applyProposal()
            assertEquals(1, undoSteps(), "Apply with nothing pending makes no edit")
        }
    }

    @Test
    fun anyOtherNoticeTakesTheTidyProposalWithIt() {
        withDrawSheet(DrawTestProjects.hyleDecoCubic(), "H", 720, 1560, 2f) {
            key(Keys.Y, ctrl = true, shift = true)
            assertTrue(controller.tidyRemoved.isNotEmpty())
            controller.say("something else")
            assertTrue(controller.tidyRemoved.isEmpty())
            controller.applyProposal()
            assertEquals(0, undoSteps())
        }
    }

    @Test
    fun changingToolByAnyRoutePutsDownThePenPathAndTheArmedKnifeAndTheProposal() =
        withGlyph(slab()) {
            key(Keys.P)
            click(font(-60, 100))
            click(font(-60, 250))
            assertEquals(2, controller.penPoints.size)
            puck.selectTool(Tool.SELECT)
            frames(3)
            assertTrue(controller.penPath.isEmpty, "the puck's tool change drops the pen's path")
            controller.startKnife()
            assertNotNull(controller.knife)
            puck.selectTool(Tool.PEN)
            frames(3)
            assertNull(controller.knife, "and disarms the knife, so a pen tap is a pen tap")
            click(font(-60, 100))
            assertEquals(1, controller.penPoints.size)
            assertEquals(1, glyph("I").contours.size)
        }

    @Test
    fun aPenClickOnTheFirstPointClosesEvenWhereAnotherContoursNodeLiesUnderIt() =
        withGlyph(slab()) {
            key(Keys.P)
            val node = screenOf(0, 6)
            val first = Vec2(node.x + 13.0, node.y + 13.0)
            val closing = Vec2(node.x + 6.0, node.y + 6.0)
            click(first)
            click(Vec2(first.x, first.y + 80.0))
            click(Vec2(first.x - 80.0, first.y + 80.0))
            assertEquals(3, controller.penPoints.size)
            click(closing)
            assertEquals(2, glyph("I").contours.size, "the closing click is on a node of the slab and still closes the path")
            assertEquals(1, undoSteps())
        }

    @Test
    fun twoQuickPenClicksArePlacedAsTwoPointsAndEnterClosesThePath() =
        withEmpty {
            key(Keys.P)
            val frame = DrawFrame(room.space(), 360.0, 780.0, 100.0, 170.0, PointerKind.MOUSE, Tool.PEN)
            controller.onOutput(CanvasOutput.Tap(Vec2(100.0, 300.0), Hit.Nothing, false), frame)
            controller.onOutput(CanvasOutput.DoubleTap(Vec2(200.0, 300.0), Hit.Nothing), frame)
            assertEquals(2, controller.penPoints.size, "the second click of a quick pair is a second point")
            controller.onOutput(CanvasOutput.Tap(Vec2(200.0, 400.0), Hit.Nothing, false), frame)
            assertTrue(controller.modeLine.orEmpty().contains("Enter to close") || controller.modeLine.orEmpty().contains("press Enter"))
            key(Keys.ENTER)
            assertEquals(1, glyph("a").contours.size, "Enter closes the path here")
            assertEquals(1, undoSteps())
            assertTrue(controller.penPath.isEmpty)
        }

    @Test
    fun closePathHereIsInTheMenuOnlyWhileThePenHasAPath() =
        withEmpty {
            val ids = {
                DrawCommands
                    .build(
                        DrawCommandContext(controller.editor, DrawSelection.EMPTY, controller.metrics(), controller),
                    ).map { it.id }
            }
            assertTrue("close-path" !in ids())
            key(Keys.P)
            click(Vec2(100.0, 300.0))
            click(Vec2(200.0, 300.0))
            click(Vec2(200.0, 400.0))
            assertTrue("close-path" in ids())
            val close = DrawCommands.build(DrawCommandContext(controller.editor, DrawSelection.EMPTY, controller.metrics(), controller))
            controller.run(close.first { it.id == "close-path" })
            assertEquals(1, glyph("a").contours.size)
        }
}
