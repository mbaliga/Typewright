// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.ContourPoint
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.CurveSegment
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.count
import com.asoc.typewright.core.geometry.segments
import com.asoc.typewright.project.PointRef
import com.asoc.typewright.project.ProjectSession
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** [DrawEditor] against real sessions: the gesture lifecycle, one-undo-step edits, locks, and Hyle Deco's own letters. */
class DrawEditorSessionTest {
    private fun on(
        x: Int,
        y: Int,
        smooth: Boolean = false,
    ) = ContourPoint(Point(x, y), onCurve = true, smooth = smooth)

    private fun off(
        x: Int,
        y: Int,
    ) = ContourPoint(Point(x, y), onCurve = false)

    private fun ProjectSession.glyph(name: String): Glyph =
        state.value.font.glyph(
            com.asoc.typewright.project
                .GlyphRef("regular", name),
        )!!

    private fun ProjectSession.undoSteps(): Int = history.value.undoLabels.size

    /** A leaf: two cubic curves between (0,0) and (300,0), the nodes smooth, handles vertical. */
    private fun leaf(smooth: Boolean = true) =
        Glyph(
            "leaf",
            400,
            listOf(
                Contour(
                    listOf(on(0, 0, smooth), off(0, 200), off(300, 200), on(300, 0, smooth), off(300, -200), off(0, -200)),
                    CurveFormat.CUBIC,
                ),
            ),
        )

    // ---- the real letters ------------------------------------------------------------------

    @Test
    fun theRealLettersAreLoadedAtTheirPinnedCounts() {
        val session = DrawTestProjects.session(DrawTestProjects.hyleDecoQuadratic())
        assertEquals(80, session.glyph("o").count().onCurveEquivalent)
        assertEquals(0, session.glyph("o").count().offCurve)
        assertEquals(1252, session.glyph("H").count().onCurveEquivalent)
        assertEquals(44, session.glyph("n").count().onCurveEquivalent)
    }

    @Test
    fun draggingANodeOfTheRealHIsOneUndoStepAndMovesOnlyThatCorner() {
        val session = DrawTestProjects.session(DrawTestProjects.hyleDecoQuadratic())
        val editor = DrawTestProjects.editor(session, "H")
        val before = session.glyph("H")
        val corner = PointRef(0, 0)

        assertEquals(null, editor.beginDrag(DragOp.MoveSelection(setOf(corner))))
        assertEquals(DragUpdate.FOLLOWED, editor.dragTo(10.0, 5.0))
        assertEquals(DragUpdate.FOLLOWED, editor.dragTo(14.0, 9.0))
        editor.endDrag(commit = true)

        assertEquals(1, session.undoSteps(), "the whole drag is one history entry")
        val after = session.glyph("H")
        val moved = before.contours[0].points[0].point
        assertEquals(Point(moved.x + 14, moved.y + 9), after.contours[0].points[0].point)
        assertEquals(before.contours[0].points.drop(1), after.contours[0].points.drop(1))
        assertTrue(session.undo())
        assertEquals(before, session.glyph("H"))
    }

    @Test
    fun aDragThatEndsWhereItBeganChangesNothing() {
        val session = DrawTestProjects.session(DrawTestProjects.hyleDecoQuadratic())
        val editor = DrawTestProjects.editor(session, "H")
        val before = session.glyph("H")
        editor.beginDrag(DragOp.MoveSelection(setOf(PointRef(0, 0))))
        editor.dragTo(33.0, 12.0)
        editor.dragTo(0.0, 0.0)
        editor.endDrag(commit = true)
        assertEquals(before, session.glyph("H"))
        assertEquals(0, session.undoSteps(), "no change records no step")
    }

    // ---- the gesture lifecycle -------------------------------------------------------------

    @Test
    fun anUndoDuringADragEndsItAndALaterUpdateDoesNotThrow() {
        val session = DrawTestProjects.session(DrawTestProjects.small(leaf()))
        val editor = DrawTestProjects.editor(session, "leaf")
        val before = session.glyph("leaf")
        editor.beginDrag(DragOp.MoveSelection(setOf(PointRef(0, 0))))
        editor.dragTo(20.0, 0.0)
        assertTrue(session.undo(), "undo cancels the open drag")

        assertEquals(DragUpdate.ENDED, editor.dragTo(30.0, 0.0), "the editor asks the gesture before it updates")
        assertFalse(editor.isDragging)
        editor.endDrag(commit = true) // safe to call after the session ended it
        assertEquals(before, session.glyph("leaf"))
        assertEquals(0, session.undoSteps())
    }

    @Test
    fun anEditFromElsewhereDuringADragCommitsItAndTheDragIsOver() {
        val session = DrawTestProjects.session(DrawTestProjects.small(leaf()))
        val editor = DrawTestProjects.editor(session, "leaf")
        editor.beginDrag(DragOp.MoveSelection(setOf(PointRef(0, 0))))
        editor.dragTo(20.0, 0.0)

        editor.toggleSmooth(DrawSelection(setOf(PointRef(0, 3)))) // an execute commits the open gesture first

        assertEquals(DragUpdate.ENDED, editor.dragTo(40.0, 0.0))
        assertEquals(2, session.undoSteps(), "the drag, then the toggle")
    }

    @Test
    fun cancellingADragRestoresTheGlyphAndRecordsNothing() {
        val session = DrawTestProjects.session(DrawTestProjects.small(leaf()))
        val editor = DrawTestProjects.editor(session, "leaf")
        val before = session.glyph("leaf")
        editor.beginDrag(DragOp.MoveSelection(setOf(PointRef(0, 0))))
        editor.dragTo(50.0, 50.0)
        assertNotEquals(before, session.glyph("leaf"))

        editor.endDrag(commit = false)

        assertEquals(before, session.glyph("leaf"))
        assertEquals(0, session.undoSteps())
    }

    @Test
    fun endingADragTwiceOrWithNoneOpenIsHarmless() {
        val session = DrawTestProjects.session(DrawTestProjects.small(leaf()))
        val editor = DrawTestProjects.editor(session, "leaf")
        editor.endDrag(commit = true)
        editor.beginDrag(DragOp.MoveSelection(setOf(PointRef(0, 0))))
        editor.dragTo(5.0, 0.0)
        editor.endDrag(commit = true)
        editor.endDrag(commit = true)
        assertEquals(1, session.undoSteps())
    }

    @Test
    fun aNewDragCommitsTheOneBeforeIt() {
        val session = DrawTestProjects.session(DrawTestProjects.small(leaf()))
        val editor = DrawTestProjects.editor(session, "leaf")
        editor.beginDrag(DragOp.MoveSelection(setOf(PointRef(0, 0))))
        editor.dragTo(5.0, 0.0)
        editor.beginDrag(DragOp.MoveSelection(setOf(PointRef(0, 3))))
        editor.dragTo(0.0, 9.0)
        editor.endDrag(commit = true)
        assertEquals(2, session.undoSteps())
    }

    // ---- locks -----------------------------------------------------------------------------

    @Test
    fun aLockedGlyphRefusesADragAndAnEditWithAPlainReasonAndUnlockingAllowsThem() {
        val session = DrawTestProjects.session(DrawTestProjects.small(leaf()))
        val editor = DrawTestProjects.editor(session, "leaf")
        assertIs<EditOutcome.Done>(editor.approve())
        assertTrue(editor.isLocked)
        val before = session.glyph("leaf")

        val refused = editor.beginDrag(DragOp.MoveSelection(setOf(PointRef(0, 0))))
        assertIs<EditOutcome.Locked>(refused)
        assertTrue(refused.message.contains("approved and locked"))
        assertTrue(refused.message.contains("Unlock"))
        assertFalse(editor.isDragging)
        assertIs<EditOutcome.Locked>(editor.moveBy(DrawSelection(setOf(PointRef(0, 0))), 5, 0))
        assertIs<EditOutcome.Locked>(editor.toggleSmooth(DrawSelection(setOf(PointRef(0, 0)))))
        assertEquals(before, session.glyph("leaf"))

        assertIs<EditOutcome.Done>(editor.unlock())
        assertFalse(editor.isLocked)
        assertIs<EditOutcome.Done>(editor.moveBy(DrawSelection(setOf(PointRef(0, 0))), 5, 0))
        assertIs<EditOutcome.Done>(editor.approve())
        assertTrue(editor.isLocked)
        val episode =
            session.state.value.font.locks.values
                .single()
                .episodes
                .single()
        assertTrue(episode.frozenDiff.orEmpty().contains("@@"), "the relock froze the diff of what changed")
    }

    // ---- moving, typing, nudging -------------------------------------------------------------

    @Test
    fun typingACoordinateMovesThePointExactlyAsADragDoes() {
        val session = DrawTestProjects.session(DrawTestProjects.small(leaf()))
        val editor = DrawTestProjects.editor(session, "leaf")
        val selection = DrawSelection(setOf(PointRef(0, 3)))
        val start = session.glyph("leaf")

        editor.beginDrag(DragOp.MoveSelection(selection.points))
        editor.dragTo(25.0, 40.0)
        editor.endDrag(commit = true)
        val dragged = session.glyph("leaf")
        session.undo()

        editor.placePoint(selection, x = 325, y = 40)
        assertEquals(dragged, session.glyph("leaf"))
        assertEquals(
            start.contours[0]
                .points[3]
                .point.x + 25,
            session
                .glyph("leaf")
                .contours[0]
                .points[3]
                .point.x,
        )
        assertEquals(1, session.undoSteps())
    }

    @Test
    fun typingAHandlePositionSwingsTheOtherHandleOfASmoothNodeExactlyAsADragDoes() {
        val session = DrawTestProjects.session(DrawTestProjects.small(leaf()))
        val editor = DrawTestProjects.editor(session, "leaf")
        val handle = PointRef(0, 1) // the upper handle of the node at (0, 0), which is smooth
        val selection = DrawSelection(setOf(handle))

        editor.beginDrag(editor.dragOpFor(selection))
        editor.dragTo(40.0, 0.0)
        editor.endDrag(commit = true)
        val dragged = session.glyph("leaf")
        session.undo()

        editor.placePoint(selection, x = 40, y = 200)
        assertEquals(dragged, session.glyph("leaf"), "typing x moved the handle the way dragging it does")
        val points = session.glyph("leaf").contours[0].points
        assertEquals(Point(40, 200), points[1].point)
        assertNotEquals(Point(0, -200), points[5].point, "the node's other handle swung to stay in line")
        val pivot = points[0].point
        val a = points[1].point
        val b = points[5].point
        val cross = (a.x - pivot.x).toDouble() * (b.y - pivot.y) - (a.y - pivot.y).toDouble() * (b.x - pivot.x)
        assertEquals(0.0, cross, 250.0, "the two handles are collinear through the node, to the rounding of whole units")
    }

    @Test
    fun anArrowKeyNudgeOfAHandleSwingsItsSmoothPartnerToo() {
        val session = DrawTestProjects.session(DrawTestProjects.small(leaf()))
        val editor = DrawTestProjects.editor(session, "leaf")
        val before = session.glyph("leaf").contours[0].points
        editor.moveBy(DrawSelection(setOf(PointRef(0, 1))), 10, 0, merge = true)
        val after = session.glyph("leaf").contours[0].points
        assertEquals(Point(before[1].point.x + 10, before[1].point.y), after[1].point)
        assertNotEquals(before[5].point, after[5].point, "the partner handle moved with it")
    }

    @Test
    fun movingAHandleWithItsNodeSelectedMovesThemTogetherWithoutSwinging() {
        val session = DrawTestProjects.session(DrawTestProjects.small(leaf()))
        val editor = DrawTestProjects.editor(session, "leaf")
        val before = session.glyph("leaf").contours[0].points
        editor.moveBy(DrawSelection(setOf(PointRef(0, 0), PointRef(0, 1))), 10, 5)
        val after = session.glyph("leaf").contours[0].points
        assertEquals(Point(before[0].point.x + 10, before[0].point.y + 5), after[0].point)
        assertEquals(Point(before[1].point.x + 10, before[1].point.y + 5), after[1].point)
        assertEquals(Point(before[5].point.x + 10, before[5].point.y + 5), after[5].point, "the node carries both of its handles")
    }

    @Test
    fun typingAPositionForARealCubicHCornerKeepsEverySideStraight() {
        val session = DrawTestProjects.session(DrawTestProjects.hyleDecoCubic())
        val editor = DrawTestProjects.editor(session, "H")
        val corner = PointRef(0, 0)
        val at =
            session
                .glyph("H")
                .contours[0]
                .points[0]
                .point

        editor.placePoint(DrawSelection(setOf(corner)), x = at.x + 17, y = at.y + 9)

        for (contour in session.glyph("H").contours) {
            for (segment in contour.segments()) {
                segment as CurveSegment.Cubic
                val chord = segment.end - segment.start
                val len = maxOf(chord.length(), 1.0)
                assertTrue(abs(chord.cross(segment.control1 - segment.start)) / len <= 0.75, "side bent: $segment")
                assertTrue(abs(chord.cross(segment.control2 - segment.start)) / len <= 0.75, "side bent: $segment")
            }
        }
    }

    @Test
    fun aBurstOfNudgesIsOneUndoStep() {
        val session = DrawTestProjects.session(DrawTestProjects.small(leaf()))
        val editor = DrawTestProjects.editor(session, "leaf")
        val selection = DrawSelection(setOf(PointRef(0, 3)))
        repeat(5) { editor.moveBy(selection, 1, 0, label = "Nudge", merge = true) }
        assertEquals(1, session.undoSteps())
        assertEquals(
            305,
            session
                .glyph("leaf")
                .contours[0]
                .points[3]
                .point.x,
        )
        session.undo()
        assertEquals(
            300,
            session
                .glyph("leaf")
                .contours[0]
                .points[3]
                .point.x,
        )
    }

    @Test
    fun movingNothingOrNothingSelectedSaysSo() {
        val session = DrawTestProjects.session(DrawTestProjects.small(leaf()))
        val editor = DrawTestProjects.editor(session, "leaf")
        val none = editor.moveBy(DrawSelection.EMPTY, 1, 0)
        assertIs<EditOutcome.Unchanged>(none)
        assertTrue(none.reason.contains("Select"))
        assertIs<EditOutcome.Unchanged>(editor.moveBy(DrawSelection(setOf(PointRef(0, 0))), 0, 0))
    }

    // ---- smooth and corner, persisted and undoable ------------------------------------------

    @Test
    fun smoothAndCornerAreARealUndoableEditOnTheFlagNotAGuess() {
        val session = DrawTestProjects.session(DrawTestProjects.small(leaf(smooth = false)))
        val editor = DrawTestProjects.editor(session, "leaf")
        val node = DrawSelection(setOf(PointRef(0, 0)))
        assertFalse(
            session
                .glyph("leaf")
                .contours[0]
                .points[0]
                .smooth,
        )

        val toSmooth = editor.toggleSmooth(node)
        assertIs<EditOutcome.Done>(toSmooth)
        assertTrue(
            session
                .glyph("leaf")
                .contours[0]
                .points[0]
                .smooth,
        )
        assertEquals(
            "Make smooth",
            session.history.value.undoLabels
                .last(),
        )

        editor.toggleSmooth(node)
        assertFalse(
            session
                .glyph("leaf")
                .contours[0]
                .points[0]
                .smooth,
        )
        assertEquals(
            "Make corner",
            session.history.value.undoLabels
                .last(),
        )

        assertTrue(session.undo())
        assertTrue(
            session
                .glyph("leaf")
                .contours[0]
                .points[0]
                .smooth,
        )
    }

    @Test
    fun breakingHandlesMakesASmoothNodeACornerAndOnlyThen() {
        val session = DrawTestProjects.session(DrawTestProjects.small(leaf(smooth = true)))
        val editor = DrawTestProjects.editor(session, "leaf")
        val node = DrawSelection(setOf(PointRef(0, 0)))
        assertIs<EditOutcome.Done>(editor.breakHandles(node))
        assertFalse(
            session
                .glyph("leaf")
                .contours[0]
                .points[0]
                .smooth,
        )
        val again = editor.breakHandles(node)
        assertIs<EditOutcome.Unchanged>(again)
        assertTrue(again.reason.contains("independent"))
    }

    // ---- delete, with a plain word for what happened ----------------------------------------

    @Test
    fun deletingOnlyHandlesOfACubicContourRetractsThemAndSaysSo() {
        val session = DrawTestProjects.session(DrawTestProjects.small(leaf()))
        val editor = DrawTestProjects.editor(session, "leaf")
        val outcome = editor.deleteSelection(DrawSelection(setOf(PointRef(0, 1), PointRef(0, 2))))
        assertIs<EditOutcome.Done>(outcome)
        assertTrue(outcome.note.orEmpty().startsWith("Retracted 2 handles"), outcome.note)
        val points = session.glyph("leaf").contours[0].points
        assertEquals(points[0].point, points[1].point)
        assertEquals(points[3].point, points[2].point)
    }

    @Test
    fun deletingOnlyHandlesOfAQuadraticContourRemovesTheControlAndSaysSoInsteadOfDoingNothing() {
        val quad =
            Glyph(
                "q",
                300,
                listOf(Contour(listOf(on(0, 0), off(150, 200), on(300, 0), on(300, -100), on(0, -100)), CurveFormat.QUADRATIC)),
            )
        val session = DrawTestProjects.session(DrawTestProjects.small(quad))
        val editor = DrawTestProjects.editor(session, "q")
        val outcome = editor.deleteSelection(DrawSelection(setOf(PointRef(0, 1))))
        assertIs<EditOutcome.Done>(outcome)
        assertTrue(outcome.note.orEmpty().contains("Removed 1 control point"), outcome.note)
        assertEquals(
            4,
            session
                .glyph("q")
                .contours[0]
                .points.size,
        )
        assertTrue(
            session
                .glyph("q")
                .contours[0]
                .points
                .all { it.onCurve },
        )
    }

    @Test
    fun deletingAnAlreadyRetractedHandleSaysItIsAlreadyGone() {
        val session = DrawTestProjects.session(DrawTestProjects.small(leaf()))
        val editor = DrawTestProjects.editor(session, "leaf")
        editor.deleteSelection(DrawSelection(setOf(PointRef(0, 1))))
        val again = editor.deleteSelection(DrawSelection(setOf(PointRef(0, 1))))
        assertIs<EditOutcome.Unchanged>(again)
        assertTrue(again.reason.contains("already retracted"))
    }

    @Test
    fun deletingARealNodeOfHReportsItsMeasuredPointCountChange() {
        val session = DrawTestProjects.session(DrawTestProjects.hyleDecoQuadratic())
        val editor = DrawTestProjects.editor(session, "H")
        val selection = DrawSelection(setOf(PointRef(0, 1)))
        val plan = editor.planDelete(selection)
        assertEquals("−1 on · 0 off", editor.consequenceOf(plan))
        editor.perform(plan)
        assertEquals(1251, session.glyph("H").count().onCurveEquivalent)
        assertEquals(1, session.undoSteps())
    }

    @Test
    fun deletingEveryNodeOfAContourRemovesTheContourAndSaysWhy() {
        val session = DrawTestProjects.session(DrawTestProjects.hyleDecoQuadratic())
        val editor = DrawTestProjects.editor(session, "H")
        val all = DrawSelection().all(session.glyph("H"))
        val outcome = editor.deleteSelection(all)
        assertIs<EditOutcome.Done>(outcome)
        assertTrue(outcome.note.orEmpty().contains("Removed 1 contour"), outcome.note)
        assertTrue(session.glyph("H").contours.isEmpty())
    }

    // ---- adding a node, contours, the glyph -----------------------------------------------

    @Test
    fun addingANodeToACurveKeepsItsShapeAndSelectsTheNewNode() {
        val session = DrawTestProjects.session(DrawTestProjects.small(leaf()))
        val editor = DrawTestProjects.editor(session, "leaf")
        val before = session.glyph("leaf").contours[0]
        val outcome = editor.addNode(SegmentKey(0, 0), 0.5)
        assertIs<EditOutcome.Done>(outcome)
        val after = session.glyph("leaf").contours[0]
        assertEquals(before.points.size + 3, after.points.size)
        val selected = outcome.selection.points.single()
        assertTrue(after.points[selected.point].onCurve)
        assertEquals(before.segments()[0].start, after.segments()[0].start)
        assertEquals(before.segments()[0].end, after.segments()[1].end)
        assertEquals(after.segments()[0].end, after.segments()[1].start)
    }

    @Test
    fun reversingFlipsTheContourAndOneUndoPutsItBack() {
        val session = DrawTestProjects.session(DrawTestProjects.small(leaf()))
        val editor = DrawTestProjects.editor(session, "leaf")
        val before = session.glyph("leaf")
        assertIs<EditOutcome.Done>(editor.reverseContours(DrawSelection(setOf(PointRef(0, 0)))))
        assertNotEquals(before, session.glyph("leaf"))
        session.undo()
        assertEquals(before, session.glyph("leaf"))
    }

    @Test
    fun addingExtremesToAQuadraticGlyphSaysWhyItCannot() {
        val session = DrawTestProjects.session(DrawTestProjects.hyleDecoQuadratic())
        val editor = DrawTestProjects.editor(session, "o")
        val outcome = editor.addExtremes(DrawSelection.EMPTY)
        assertIs<EditOutcome.Unchanged>(outcome)
        assertTrue(outcome.reason.contains("quadratic"))
    }

    @Test
    fun tidyingTheRealCubicNRemovesNodesAndReportsTheMeasuredCount() {
        val session = DrawTestProjects.session(DrawTestProjects.hyleDecoCubic())
        val editor = DrawTestProjects.editor(session, "n")
        val before = session.glyph("n").count()
        val plan = editor.planTidy(DrawSelection.EMPTY)
        val consequence = editor.consequenceOf(plan)
        assertTrue(consequence.startsWith("−"), consequence)
        assertIs<EditOutcome.Done>(editor.perform(plan))
        val after = session.glyph("n").count()
        assertTrue(after.onCurveEquivalent < before.onCurveEquivalent)
        assertEquals(1, session.undoSteps())
    }

    @Test
    fun aligningMovesANodeOntoTheNearestMetricLineWithinReach() {
        val glyph = Glyph("a", 300, listOf(Contour(listOf(on(0, 8), on(300, 8), on(300, 520), on(0, 520)), CurveFormat.QUADRATIC)))
        val session = DrawTestProjects.session(DrawTestProjects.small(glyph))
        val editor = DrawTestProjects.editor(session, "a")
        val metrics =
            drawMetricsFor(
                session.state.value.font.masters[0]
                    .ufo.fontInfo,
            )
        val outcome = editor.alignToMetrics(DrawSelection(setOf(PointRef(0, 0), PointRef(0, 2))), metrics)
        assertIs<EditOutcome.Done>(outcome)
        val points = session.glyph("a").contours[0].points
        assertEquals(0, points[0].point.y, "8 units above the baseline goes to the baseline")
        assertEquals(500, points[2].point.y, "20 units above the x-height goes to it")
        val far =
            editor.alignToMetrics(
                DrawSelection(setOf(PointRef(0, 3))),
                metrics.copy(
                    lines =
                        metrics.lines.filter {
                            it.kind ==
                                MetricKind.BASELINE
                        },
                ),
            )
        assertIs<EditOutcome.Unchanged>(far)
    }

    @Test
    fun clearingTheGlyphIsOneStepThatUndoRestores() {
        val session = DrawTestProjects.session(DrawTestProjects.hyleDecoQuadratic())
        val editor = DrawTestProjects.editor(session, "H")
        val before = session.glyph("H")
        assertEquals("−1252 on · 0 off", editor.consequenceOf(editor.planClear()))
        assertIs<EditOutcome.Done>(editor.clearGlyph())
        assertTrue(session.glyph("H").contours.isEmpty())
        session.undo()
        assertEquals(before, session.glyph("H"))
    }

    @Test
    fun theRegistryDisablesWhatCannotRunWithAReasonAndMeasuresWhatCan() {
        val session = DrawTestProjects.session(DrawTestProjects.hyleDecoQuadratic())
        val editor = DrawTestProjects.editor(session, "H")
        val metrics =
            drawMetricsFor(
                session.state.value.font.masters[0]
                    .ufo.fontInfo,
            )
        val hooks =
            object : DrawHooks {
                override fun applied(outcome: EditOutcome) = Unit

                override fun fitGlyph() = Unit

                override fun actualSize() = Unit

                override fun selectAll() = Unit

                override fun openPalette(goToGlyph: Boolean) = Unit
            }
        val commands =
            DrawCommands
                .build(
                    DrawCommandContext(editor, DrawSelection(setOf(PointRef(0, 1))), metrics, hooks),
                ).associateBy { it.id }

        assertEquals("−1 on · 0 off", commands.getValue("delete").consequence)
        assertTrue(
            commands
                .getValue("add-extremes")
                .reason
                .orEmpty()
                .contains("quadratic"),
        )
        assertFalse(commands.getValue("add-extremes").enabled)
        assertEquals("nothing to undo", commands.getValue("undo").reason)
        assertTrue(commands.getValue("clear").destructive)
        assertEquals("hold · ↓", commands.getValue("delete").gesture)
        assertEquals("select a segment first", commands.getValue("add-node").reason)

        editor.approve()
        val locked =
            DrawCommands
                .build(
                    DrawCommandContext(editor, DrawSelection(setOf(PointRef(0, 1))), metrics, hooks),
                ).associateBy { it.id }
        assertEquals("locked: unlock to edit", locked.getValue("delete").reason)
        assertEquals("Unlock glyph", locked.getValue("lock").label)
        assertTrue(locked.getValue("lock").enabled)
    }

    @Test
    fun editingAGlyphThatNoLongerExistsRefusesPlainly() {
        val session = DrawTestProjects.session(DrawTestProjects.small(leaf()))
        val editor =
            DrawEditor(
                session,
                com.asoc.typewright.project
                    .GlyphRef("regular", "ghost"),
            )
        assertIs<EditOutcome.Refused>(editor.beginDrag(DragOp.MoveSelection(setOf(PointRef(0, 0)))))
        assertFalse(editor.isDragging)
        assertIs<EditOutcome.Refused>(editor.toggleSmooth(DrawSelection(setOf(PointRef(0, 0)))))
    }
}
