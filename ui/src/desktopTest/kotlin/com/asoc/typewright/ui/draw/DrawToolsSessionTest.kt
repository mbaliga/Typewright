// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.CutPlace
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.PenPath
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.brokenSmoothNodes
import com.asoc.typewright.core.geometry.count
import com.asoc.typewright.core.geometry.outlineBounds
import com.asoc.typewright.core.geometry.polygonContour
import com.asoc.typewright.core.geometry.signedArea
import com.asoc.typewright.project.GlyphRef
import com.asoc.typewright.project.PointRef
import com.asoc.typewright.project.ProjectSession
import com.asoc.typewright.ui.puck.Tool
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Pen, the Knife and Tidy against real sessions: each is one undo step that undoes to exactly the outline it started
 * from, a locked glyph refuses all three, and Tidy never moves Hyle Deco's own letters past the bound it states.
 */
class DrawToolsSessionTest {
    private fun ProjectSession.glyph(name: String): Glyph = state.value.font.glyph(GlyphRef("regular", name))!!

    private fun ProjectSession.undoSteps(): Int = history.value.undoLabels.size

    private class QuietHost : DrawHost {
        override fun pickTool(tool: Tool) = Unit

        override fun fitGlyph() = Unit

        override fun actualSize() = Unit

        override fun stepGlyph(delta: Int) = Unit

        override fun openPalette(goToGlyph: Boolean) = Unit

        override fun openMap() = Unit

        override fun showShortcuts() = Unit
    }

    private fun slabGlyph(name: String = "I") =
        Glyph(name, 400, listOf(polygonContour(listOf(Point(50, 0), Point(350, 0), Point(350, 400), Point(50, 400)), CurveFormat.CUBIC)))

    private fun sessionOf(vararg glyphs: Glyph): ProjectSession = DrawTestProjects.session(DrawTestProjects.small(*glyphs))

    // ---- the pen ---------------------------------------------------------------------------------

    @Test
    fun aPenPathIsOneUndoStepAndUndoGivesBackTheEmptyGlyph() {
        val session = sessionOf(Glyph("a", 500, emptyList()))
        val editor = DrawTestProjects.editor(session, "a")
        val before = session.glyph("a")
        val path =
            PenPath()
                .plus(Point(100, 0))
                .plus(Point(300, 0), pull = Point(40, 60))
                .plus(Point(300, 400))
                .plus(Point(100, 400))
        assertIs<EditOutcome.Done>(editor.perform(editor.planPen(path, null, CurveFormat.CUBIC)))
        val made = session.glyph("a").contours.single()
        assertEquals(4, made.points.count { it.onCurve })
        assertTrue(made.points.any { it.smooth }, "the dragged point is smooth")
        assertTrue(made.brokenSmoothNodes().isEmpty(), "and the flag is true to the outline")
        assertTrue(made.signedArea() > 0, "a UFO outer contour winds anticlockwise")
        assertEquals(1, session.undoSteps())
        assertTrue(session.undo())
        assertEquals(before, session.glyph("a"))
    }

    @Test
    fun aPenContourInsideAnotherWindsTheOtherWayAsACounter() {
        val session = sessionOf(slabGlyph())
        val editor = DrawTestProjects.editor(session, "I")
        val hole =
            PenPath()
                .plus(Point(100, 100))
                .plus(Point(300, 100))
                .plus(Point(300, 300))
                .plus(Point(100, 300))
        assertIs<EditOutcome.Done>(editor.perform(editor.planPen(hole, null, CurveFormat.CUBIC)))
        val contours = session.glyph("I").contours
        assertEquals(2, contours.size)
        assertTrue(contours[0].signedArea() > 0 && contours[1].signedArea() < 0, "the outer winds anticlockwise, the counter clockwise")
    }

    @Test
    fun anUnclosablePenPathIsRefusedWithItsReasonAndChangesNothing() {
        val session = sessionOf(Glyph("a", 500, emptyList()))
        val editor = DrawTestProjects.editor(session, "a")
        val flat = PenPath().plus(Point(0, 0)).plus(Point(100, 100)).plus(Point(200, 200))
        val outcome = editor.perform(editor.planPen(flat, null, CurveFormat.CUBIC))
        assertTrue(assertIs<EditOutcome.Unchanged>(outcome).reason.contains("no area"))
        val curved = PenPath().plus(Point(0, 0)).plus(Point(100, 0), pull = Point(9, 9)).plus(Point(50, 80))
        val quadratic = editor.perform(editor.planPen(curved, null, CurveFormat.QUADRATIC))
        assertTrue(assertIs<EditOutcome.Unchanged>(quadratic).reason.contains("quadratic"))
        assertEquals(0, session.undoSteps())
    }

    @Test
    fun aLockedGlyphRefusesThePen() {
        val session = sessionOf(Glyph("a", 500, emptyList()))
        val editor = DrawTestProjects.editor(session, "a")
        assertIs<EditOutcome.Done>(editor.approve())
        val path = PenPath().plus(Point(0, 0)).plus(Point(100, 0)).plus(Point(100, 100))
        assertIs<EditOutcome.Locked>(editor.perform(editor.planPen(path, null, CurveFormat.CUBIC)))
        assertTrue(session.glyph("a").contours.isEmpty())
    }

    // ---- the knife -------------------------------------------------------------------------------

    @Test
    fun aCutIsOneUndoStepAndUndoGivesBackTheSameBytes() {
        val session = sessionOf(slabGlyph())
        val editor = DrawTestProjects.editor(session, "I")
        val before = session.glyph("I")
        val outcome = editor.cutContour(0, CutPlace.AtNode(0), CutPlace.AtNode(6))
        assertIs<EditOutcome.Done>(outcome)
        val after = session.glyph("I")
        assertEquals(2, after.contours.size)
        assertEquals(before.contours[0].signedArea(), after.contours.sumOf { it.signedArea() }, 1e-6)
        assertEquals(1, session.undoSteps())
        assertTrue(session.undo())
        assertEquals(before, session.glyph("I"))
        assertTrue(session.redo())
        assertEquals(after, session.glyph("I"))
    }

    @Test
    fun aCutBetweenTwoSelectedNodesIsTheSplitCommandAndOneSelectedNodeArmsTheKnife() {
        val session = sessionOf(slabGlyph())
        val editor = DrawTestProjects.editor(session, "I")
        val two = DrawSelection(setOf(PointRef(0, 0), PointRef(0, 6)))
        assertIs<EditPlan.Change>(editor.planSplit(two))
        assertNull(editor.planSplit(DrawSelection(setOf(PointRef(0, 0)))), "one place is a knife to arm, not a cut to make")
        assertNull(editor.planSplit(DrawSelection.EMPTY))
        val three = DrawSelection(setOf(PointRef(0, 0), PointRef(0, 3), PointRef(0, 6)))
        assertIs<EditPlan.Blocked>(editor.planSplit(three))
        val handleOnly = DrawSelection(setOf(PointRef(0, 1), PointRef(0, 2)))
        assertNull(editor.planSplit(handleOnly), "handles are not places a cut can end at")
    }

    @Test
    fun aRefusedCutSaysWhyAndLeavesTheGlyphAlone() {
        val session = sessionOf(slabGlyph())
        val editor = DrawTestProjects.editor(session, "I")
        val adjacent = editor.cutContour(0, CutPlace.AtNode(0), CutPlace.AtNode(3))
        assertTrue(assertIs<EditOutcome.Unchanged>(adjacent).reason.contains("no area"))
        val same = editor.cutContour(0, CutPlace.AtNode(3), CutPlace.AtNode(3))
        assertTrue(assertIs<EditOutcome.Unchanged>(same).reason.contains("same place"))
        assertEquals(0, session.undoSteps())
    }

    @Test
    fun aLockedGlyphRefusesTheKnife() {
        val session = sessionOf(slabGlyph())
        val editor = DrawTestProjects.editor(session, "I")
        editor.approve()
        assertIs<EditOutcome.Locked>(editor.cutContour(0, CutPlace.AtNode(0), CutPlace.AtNode(6)))
        assertEquals(1, session.glyph("I").contours.size)
    }

    // ---- tidy ------------------------------------------------------------------------------------

    @Test
    fun theTidyPreviewCountsThePolygonsOfHyleDecoBeforeAnythingChangesAndTheOutlineStaysWithinTheBound() {
        val session = DrawTestProjects.session(DrawTestProjects.hyleDecoCubic())
        for (name in listOf("T", "H", "n", "o")) {
            val editor = DrawTestProjects.editor(session, name)
            val before = session.glyph(name)
            val preview = assertNotNull(editor.tidyPreview(DrawSelection.EMPTY), "$name has nodes to tidy")
            assertEquals(before, session.glyph(name), "$name: a preview changes nothing")
            assertEquals(before.count().onCurveEquivalent, preview.report.before.onCurveEquivalent)
            assertTrue(preview.report.after.onCurveEquivalent < preview.report.before.onCurveEquivalent, "$name loses nodes")
            assertTrue(preview.text.contains("→") && preview.text.contains("our heuristic"), preview.text)
            assertTrue(preview.report.maxShift <= DrawEditor.TIDY_TOLERANCE_UNITS, "$name moved ${preview.report.maxShift}")
            assertIs<EditOutcome.Done>(editor.perform(preview.plan))
            val after = session.glyph(name)
            assertEquals(
                preview.report.after.onCurveEquivalent,
                after.count().onCurveEquivalent,
                "$name: the count shown is the count made",
            )
            val boundsBefore = checkNotNull(before.outlineBounds())
            val boundsAfter = checkNotNull(after.outlineBounds())
            assertTrue(abs(boundsBefore.xMin - boundsAfter.xMin) <= DrawEditor.TIDY_TOLERANCE_UNITS)
            assertTrue(abs(boundsBefore.yMax - boundsAfter.yMax) <= DrawEditor.TIDY_TOLERANCE_UNITS)
            val areaMoved = abs(before.contours.sumOf { it.signedArea() } - after.contours.sumOf { it.signedArea() })
            assertTrue(areaMoved <= DrawEditor.TIDY_TOLERANCE_UNITS * 4000, "$name: area moved by $areaMoved")
            assertEquals(1, session.undoSteps(), "$name: one undo step")
            assertTrue(session.undo())
            assertEquals(before, session.glyph(name), "$name: undo restores the same outline")
        }
    }

    @Test
    fun tidyOnAGlyphOfNeededNodesSaysSoAndMakesNoStep() {
        val session = sessionOf(slabGlyph())
        val editor = DrawTestProjects.editor(session, "I")
        assertNull(editor.tidyPreview(DrawSelection.EMPTY))
        val outcome = editor.tidy(DrawSelection.EMPTY)
        assertTrue(assertIs<EditOutcome.Unchanged>(outcome).reason.contains("every node is needed"))
        assertEquals(0, session.undoSteps())
    }

    @Test
    fun tidyWaitsForApplyAndApplyingAfterTheOutlineChangedIsRefused() {
        val session = DrawTestProjects.session(DrawTestProjects.hyleDecoCubic())
        val editor = DrawTestProjects.editor(session, "H")
        val controller = DrawController(session, editor, QuietHost())
        val before = session.glyph("H")
        controller.proposeTidy()
        val proposal = assertIs<DrawNotice.Proposal>(controller.notice)
        assertTrue(proposal.text.contains("→"), proposal.text)
        assertTrue(controller.tidyRemoved.isNotEmpty(), "the nodes that would go are marked")
        assertEquals(before, session.glyph("H"), "nothing is changed before Apply")
        assertEquals(0, session.undoSteps())
        // The person changes the outline before applying: the shown count no longer holds.
        assertIs<EditOutcome.Done>(editor.moveBy(DrawSelection(setOf(PointRef(0, 0))), 5, 0, label = "Nudge", merge = false))
        controller.applyProposal()
        assertTrue((controller.notice as DrawNotice.Toast).text.contains("changed since"))
        assertEquals(1, session.undoSteps(), "only the nudge is in the history")
        assertTrue(controller.tidyRemoved.isEmpty())
        // Asking again and applying makes exactly one more step.
        controller.proposeTidy()
        controller.applyProposal()
        assertEquals(2, session.undoSteps())
        assertTrue(session.glyph("H").count().onCurveEquivalent < before.count().onCurveEquivalent)
    }

    @Test
    fun cancellingATidyProposalChangesNothingAndALockedGlyphNeverGetsOne() {
        val session = DrawTestProjects.session(DrawTestProjects.hyleDecoCubic())
        val editor = DrawTestProjects.editor(session, "H")
        val controller = DrawController(session, editor, QuietHost())
        val before = session.glyph("H")
        controller.proposeTidy()
        controller.dismissProposal()
        assertNull(controller.notice)
        assertTrue(controller.tidyRemoved.isEmpty())
        controller.applyProposal()
        assertEquals(before, session.glyph("H"))
        editor.approve()
        controller.proposeTidy()
        assertIs<DrawNotice.Locked>(controller.notice)
        assertTrue(controller.tidyRemoved.isEmpty(), "a locked glyph shows no removal marks")
    }

    @Test
    fun theSplitAndTidyCommandsAreInTheRegistryWithTheirConsequences() {
        val session = DrawTestProjects.session(DrawTestProjects.hyleDecoCubic())
        val editor = DrawTestProjects.editor(session, "H")
        val controller = DrawController(session, editor, QuietHost())
        val commands =
            DrawCommands
                .build(
                    DrawCommandContext(editor, DrawSelection.EMPTY, controller.metrics(), controller),
                ).associateBy { it.id }
        val tidy = assertNotNull(commands["tidy"])
        assertTrue(tidy.consequenceText.orEmpty().contains("→"), "the menu row shows the live count: ${tidy.consequenceText}")
        val split = assertNotNull(commands["split"])
        assertTrue(split.enabled)
        assertEquals("Ctrl ⇧ K", split.keys)
        split.run()
        assertNotNull(controller.knife, "with no places named the command arms the knife")
    }
}
