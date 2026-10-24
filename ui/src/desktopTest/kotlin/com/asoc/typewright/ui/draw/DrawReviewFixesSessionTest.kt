// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import androidx.compose.runtime.MonotonicFrameClock
import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.ContourPoint
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.core.geometry.brokenSmoothNodes
import com.asoc.typewright.core.geometry.sidebearings
import com.asoc.typewright.core.geometry.withNodeInserted
import com.asoc.typewright.core.geometry.withSmooth
import com.asoc.typewright.project.GlyphRef
import com.asoc.typewright.project.PointRef
import com.asoc.typewright.project.ProjectSession
import com.asoc.typewright.ui.puck.Tool
import com.asoc.typewright.ui.sheet.Room
import com.asoc.typewright.ui.sheet.SheetCamera
import com.asoc.typewright.ui.sheet.SheetCameraState
import com.asoc.typewright.ui.sheet.worldBounds
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A [DrawController] against a real session, with a host that does nothing; these are the review's cases that need a project. */
class DrawReviewFixesSessionTest {
    private class QuietHost : DrawHost {
        override fun pickTool(tool: Tool) = Unit

        override fun fitGlyph() = Unit

        override fun actualSize() = Unit

        override fun stepGlyph(delta: Int) = Unit

        override fun openPalette(goToGlyph: Boolean) = Unit

        override fun openMap() = Unit

        override fun showShortcuts() = Unit
    }

    private fun corner(
        x: Int,
        y: Int,
    ) = ContourPoint(Point(x, y), onCurve = true)

    /** A plain rectangle in cubic form: four corners, each side a straight line, so four segments. */
    private fun slab(name: String = "I"): Glyph {
        val corners = listOf(Point(50, 0), Point(350, 0), Point(350, 400), Point(50, 400))
        val points =
            corners.flatMapIndexed { i, p ->
                listOf(ContourPoint(p, true), ContourPoint(p, false), ContourPoint(corners[(i + 1) % 4], false))
            }
        return Glyph(name, 400, listOf(Contour(points, CurveFormat.CUBIC)))
    }

    private val space = DrawSpace(Vec2(0.0, 500.0), 1.0)

    private fun frame() =
        DrawFrame(
            space,
            canvasWidthDp = 500.0,
            canvasHeightDp = 700.0,
            topInsetDp = 0.0,
            deckHeightDp = 100.0,
            pointer = PointerKind.MOUSE,
            tool = Tool.SELECT,
        )

    /** The controller, the session under it (for history) and the few reads the tests share. */
    private class Rig(
        val session: ProjectSession,
        val controller: DrawController,
    ) {
        val glyph: Glyph get() = checkNotNull(controller.glyph)
        val undoSteps: Int get() = session.history.value.undoLabels.size

        fun commandsFor(selection: DrawSelection) =
            DrawCommands.build(DrawCommandContext(controller.editor, selection, controller.metrics(), controller))
    }

    private fun controllerOver(
        glyph: Glyph = slab(),
        coalesceWindowMillis: Long = 1_000L,
    ): Rig {
        val session = DrawTestProjects.session(DrawTestProjects.small(glyph), coalesceWindowMillis)
        return Rig(session, DrawController(session, DrawTestProjects.editor(session, glyph.name), QuietHost()))
    }

    // ---- 1. a segment picked before an undo ----------------------------------------------------------------

    @Test
    fun aSegmentSelectedBeforeAnUndoIsGoneAfterItAndTheCommandRegistryStillBuilds() {
        val rig = controllerOver()
        val controller = rig.controller
        assertIs<EditOutcome.Done>(controller.editor.addNode(SegmentKey(0, 3), 0.5), "five segments now")
        controller.selection = DrawSelection.EMPTY.onlySegment(SegmentKey(0, 4), 0.5)
        assertEquals(SegmentKey(0, 4), controller.selection.segment, "the last segment of the five is selected")
        controller.onKey(DrawKeyAction.Undo, frame())
        assertNull(controller.selection.segment, "back to four segments: segment 4 is gone")
        // The registry is built by the menu, the palette, the shortcut sheet and the page menu; none may throw.
        val commands = rig.commandsFor(controller.selection)
        val addNode = commands.single { it.id == "add-node" }
        assertFalse(addNode.enabled, "with no segment to add a node to it says so")
    }

    @Test
    fun theRegistryBuildsEvenFromTheStaleSelectionItself() {
        val rig = controllerOver()
        val controller = rig.controller
        controller.editor.addNode(SegmentKey(0, 3), 0.5)
        val stale = DrawSelection.EMPTY.onlySegment(SegmentKey(0, 4), 0.5)
        controller.editor.undo()
        val commands = rig.commandsFor(stale)
        assertFalse(commands.single { it.id == "add-node" }.enabled)
    }

    @Test
    fun aRedoOrAnUndoMadeBehindTheControllersBackLeavesAnUnclippedSelectionThatNothingReadsWrong() {
        val rig = controllerOver()
        val controller = rig.controller
        controller.editor.addNode(SegmentKey(0, 3), 0.5)
        controller.selection = DrawSelection.EMPTY.onlySegment(SegmentKey(0, 4), 0.5)
        controller.editor.undo() // as the Space room or a gesture elsewhere would
        assertNull(controller.selection.segment, "read against the outline as it is now")
        assertEquals(DrawSubject.glyphLine(rig.glyph), DrawSubject.describe(rig.glyph, controller.selection))
        controller.editor.redo()
        assertEquals(
            SegmentKey(0, 4),
            controller.selection.segment,
            "the outline is the one the selection was made against again, so what it names exists again",
        )
    }

    @Test
    fun aPlannerThatCannotPlanSaysItIsNotAvailableInsteadOfThrowing() {
        val rig = controllerOver()
        val controller = rig.controller
        val editor = controller.editor
        for (key in listOf(SegmentKey(0, 4), SegmentKey(3, 0), SegmentKey(0, -1))) {
            assertIs<EditPlan.Blocked>(editor.planAddNode(key, 0.5), "$key")
        }
        // Deleting, reversing and moving a selection that names points that do not exist.
        val ghosts = DrawSelection(setOf(PointRef(0, 99), PointRef(5, 0)))
        assertIs<EditPlan.Blocked>(editor.planDelete(ghosts))
        assertIs<EditPlan.Blocked>(editor.planMove(ghosts, 3, 3))
        assertIs<EditPlan.Blocked>(editor.planToggleSmooth(ghosts))
    }

    @Test
    fun buildingTheRegistryForAnySelectionAtAllNeverThrows() {
        val random = Random(20261024)
        for (glyph in listOf(slab(), DrawTestProjects.hyleDecoQuadratic().glyphs.first { it.name == "o" })) {
            val rig = controllerOver(glyph)
            val controller = rig.controller
            repeat(300) {
                val points =
                    (0 until random.nextInt(0, 5)).map { PointRef(random.nextInt(-1, 4), random.nextInt(-1, 90)) }.toSet()
                val segment = if (random.nextBoolean()) SegmentKey(random.nextInt(-1, 4), random.nextInt(-1, 40)) else null
                val selection = DrawSelection(points, segment, random.nextDouble())
                val commands = rig.commandsFor(selection)
                assertTrue(commands.isNotEmpty())
                DrawSubject.describe(rig.glyph, selection)
                DrawSubject.headerLine(rig.glyph, selection)
                DrawInspectorModel.fields(rig.glyph, selection, GlyphLines.of(rig.glyph))
                DrawInspectorModel.groups(rig.glyph, selection, GlyphLines.of(rig.glyph))
                RadialLayouts.forSelection(selection)
            }
        }
    }

    // ---- 5. scrubbing the glyph's width is one step by construction -----------------------------------------------

    @Test
    fun scrubbingTheAdvanceIsOneUndoStepEvenWithNoCoalescingWindowAtAll() {
        val rig = controllerOver(coalesceWindowMillis = 0L)
        val controller = rig.controller
        controller.scrubStart(FieldKeys.ADVANCE)
        for (units in listOf(3, 10, 25, -2, 12)) controller.scrubTo(FieldKeys.ADVANCE, units)
        assertEquals(412, rig.glyph.advanceWidth, "the scrub follows the finger live")
        controller.scrubEnd(commit = true)
        assertEquals(1, rig.undoSteps, "one gesture, one step, however many frames it ran")
        controller.editor.undo()
        assertEquals(400, rig.glyph.advanceWidth)
    }

    @Test
    fun aCancelledAdvanceScrubPutsTheWidthBack() {
        val rig = controllerOver()
        val controller = rig.controller
        controller.scrubStart(FieldKeys.ADVANCE)
        controller.scrubTo(FieldKeys.ADVANCE, 40)
        assertEquals(440, rig.glyph.advanceWidth)
        controller.scrubEnd(commit = false)
        assertEquals(400, rig.glyph.advanceWidth, "a second finger landing cancels the drag, and the width goes back")
        assertEquals(0, rig.undoSteps)
    }

    @Test
    fun scrubbingTheSidebearingsIsOneStepAndCancellable() {
        val rig = controllerOver(coalesceWindowMillis = 0L)
        val controller = rig.controller
        val before = rig.glyph.sidebearings()
        controller.scrubStart(FieldKeys.LSB)
        for (units in listOf(4, 9, 16)) controller.scrubTo(FieldKeys.LSB, units)
        assertEquals(assertNotNull(before).left + 16, rig.glyph.sidebearings()?.left)
        controller.scrubEnd(commit = true)
        assertEquals(1, rig.undoSteps)
        controller.scrubStart(FieldKeys.RSB)
        controller.scrubTo(FieldKeys.RSB, -30)
        controller.scrubEnd(commit = false)
        assertEquals(1, rig.undoSteps, "the cancelled right-sidebearing scrub left no step")
        assertEquals(assertNotNull(before).right, rig.glyph.sidebearings()?.right)
    }

    @Test
    fun anOutlineEditInTheMiddleOfAWidthScrubEndsTheScrubWithoutAnException() {
        val rig = controllerOver()
        val controller = rig.controller
        controller.scrubStart(FieldKeys.ADVANCE)
        controller.scrubTo(FieldKeys.ADVANCE, 5)
        controller.editor.addNode(SegmentKey(0, 0), 0.5) // the session ends the open gesture
        controller.scrubTo(FieldKeys.ADVANCE, 9) // a late frame of the scrub: ignored
        controller.scrubEnd(commit = true)
        assertEquals(405, rig.glyph.advanceWidth, "the scrub that had run is kept")
    }

    // ---- 5. a nudge merges with the same points only ---------------------------------------------------------------

    @Test
    fun nudgesOfTheSamePointsMergeButNudgesOfDifferentPointsDoNot() {
        val rig = controllerOver()
        val controller = rig.controller
        val editor = controller.editor
        val a = DrawSelection(setOf(PointRef(0, 0)))
        val b = DrawSelection(setOf(PointRef(0, 3)))
        repeat(3) { editor.moveBy(a, 1, 0, label = "Nudge", merge = true) }
        assertEquals(1, rig.undoSteps, "three nudges of one node within the window are one step")
        editor.moveBy(b, 0, 1, label = "Nudge", merge = true)
        assertEquals(2, rig.undoSteps, "a nudge of another node is its own step")
        editor.undo()
        val points = rig.glyph.contours[0].points
        assertEquals(Point(53, 0), points[0].point, "the first node is as the three nudges left it")
        assertEquals(Point(350, 0), points[3].point, "one undo took back only the second node's nudge")
    }

    @Test
    fun aNudgeThatNothingCanKeepSmoothMakesTheNodeACornerAndSaysSo() {
        // A node in the middle of the bottom side, smooth because the two straight sides run on as one line: it has no handle to turn.
        val base = slab()
        val inserted =
            base.contours[0]
                .withNodeInserted(0, 0.5)
                .contour
                .withSmooth(3, true)
        assertTrue(inserted.points[3].smooth)
        val rig = controllerOver(base.copy(contours = listOf(inserted)))
        val outcome = rig.controller.editor.moveBy(DrawSelection(setOf(PointRef(0, 3))), 0, 12, label = "Nudge")
        val done = assertIs<EditOutcome.Done>(outcome)
        assertTrue("corner" in done.note.orEmpty(), "the note says the node became a corner: ${done.note}")
        assertFalse(
            rig.glyph.contours[0]
                .points[3]
                .smooth,
        )
        assertEquals(emptyList(), rig.glyph.contours[0].brokenSmoothNodes())
    }

    @Test
    fun aNudgeThatKeepsEverySmoothNodeSmoothHasNoNote() {
        val rig = controllerOver(slab())
        val done = assertIs<EditOutcome.Done>(rig.controller.editor.moveBy(DrawSelection(setOf(PointRef(0, 3))), 5, 0, label = "Nudge"))
        assertNull(done.note)
    }

    // ---- 4. the note after taking handles off a quadratic outline ---------------------------------------------------

    @Test
    fun deletingOneControlOfAPairOnATrueTypeOutlineDoesNotSayTheSideIsStraight() {
        // on, off, off, on: two controls in a row imply an on-curve point between them.
        val pair =
            Glyph(
                "q",
                300,
                listOf(Contour(listOf(corner(0, 0), off(0, 100), off(100, 100), corner(100, 0), corner(50, -60)), CurveFormat.QUADRATIC)),
            )
        val rig = controllerOver(pair)
        val controller = rig.controller
        controller.selection = DrawSelection(setOf(PointRef(0, 1)))
        val note = assertIs<EditPlan.Change>(controller.editor.planDelete(controller.selection)).note.orEmpty()
        assertTrue("straight" !in note, "the side is still curved: $note")
        assertTrue("implied" in note, note)
        // The same selection on a lone control is straight, so the sentence is not simply always the other one.
        val lone =
            Glyph(
                "r",
                300,
                listOf(
                    Contour(listOf(corner(0, 0), off(50, 60), corner(100, 0), corner(100, -100), corner(0, -100)), CurveFormat.QUADRATIC),
                ),
            )
        val loneRig = controllerOver(lone)
        loneRig.controller.selection = DrawSelection(setOf(PointRef(0, 1)))
        val loneNote = assertIs<EditPlan.Change>(loneRig.controller.editor.planDelete(loneRig.controller.selection)).note.orEmpty()
        assertTrue("straight" in loneNote, loneNote)
    }

    private fun off(
        x: Int,
        y: Int,
    ) = ContourPoint(Point(x, y), onCurve = false)

    // ---- 8. a retracted handle can be pulled out --------------------------------------------------------------------

    @Test
    fun draggingARetractedHandleFromItsMarkPullsItOutUnderThePointerAndTurnsTheSideIntoACurve() {
        val rig = controllerOver()
        val controller = rig.controller
        // Node 0 is the corner at (50, 0); its out handle is point 1, retracted on it, on the bottom side.
        controller.selection = DrawSelection(setOf(PointRef(0, 0)))
        val mark = RetractedHandles.marks(rig.glyph, space, controller.selection).single { it.ref == PointRef(0, 1) }
        val hit = DrawHit.hit(rig.glyph, space, space.toScreen(mark.at), HitTolerance.MOUSE, controller.selection)
        assertIs<Hit.OnPoint>(hit)
        assertEquals(PointRef(0, 1), hit.ref)
        val from = space.toScreen(mark.at)
        controller.onOutput(CanvasOutput.BeginObjectDrag(hit, from, false), frame())
        // Drag 60 up and 30 along: the handle should land at the mark plus that movement.
        controller.onOutput(CanvasOutput.ObjectDragged(30.0, -60.0, Vec2(from.x + 30.0, from.y - 60.0)), frame())
        controller.onOutput(CanvasOutput.EndObjectDrag(committed = true), frame())
        val points = rig.glyph.contours[0].points
        assertEquals(Point((mark.at.x + 30.0).toInt(), 60), points[1].point, "pulled out and under the pointer")
        assertEquals(Point(50, 0), points[0].point, "the node itself did not move")
        assertEquals(Point(350, 0), points[2].point, "the other handle of the side stayed on its node")
        assertEquals(1, rig.undoSteps)
        val side = rig.glyph.contours[0].segmentsSummary()
        assertTrue(side, "the bottom side now curves: its handle is off the line between the nodes")
    }

    @Test
    fun anArrowKeyNudgeOfASelectedRetractedHandleStartsPullingItOut() {
        val rig = controllerOver()
        val controller = rig.controller
        controller.selection = DrawSelection(setOf(PointRef(0, 1)))
        controller.onKey(DrawKeyAction.Nudge(0, 5), frame())
        assertEquals(
            Point(50, 5),
            rig.glyph.contours[0]
                .points[1]
                .point,
        )
    }

    private fun Contour.segmentsSummary(): Boolean {
        val a = points[0].point
        val handle = points[1].point
        return handle.y != a.y
    }

    // ---- 3. the camera ----------------------------------------------------------------------------------------------

    /** A frame clock that gives every frame 16 ms, so a flight runs to its end at once and the same way every time. */
    private class StepClock : MonotonicFrameClock {
        private var now = 0L

        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            now += 16_000_000L
            return onFrame(now)
        }
    }

    @Test
    fun flyingToTheRoomYouAreInChangesNothingEvenWithAStaleViewRemembered() =
        runBlocking(StepClock()) {
            val state = SheetCameraState(SheetCamera(Vec2(Room.DRAW.worldBounds().left + 40.0, -300.0), 1.7))
            state.flyTo(Room.SPACE) // leaves Draw, which remembers the view it was left in
            state.flyTo(Room.DRAW)
            state.panBy(Vec2(-25.0, 10.0))
            state.zoomBy(1.3, Vec2(100.0, 100.0))
            val mine = state.camera
            state.flyTo(Room.DRAW) // a flick, or the Left arrow, at the end of the line
            assertEquals(mine, state.camera, "the person's own pan and zoom are not thrown away")
            state.flyTo(Room.SPACE)
            state.flyTo(Room.DRAW)
            assertEquals(mine, state.camera, "and the view they left Draw in is what they come back to")
        }
}
