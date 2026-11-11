// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.core.geometry.Anchor
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.polygonContour
import com.asoc.typewright.core.geometry.signedArea
import com.asoc.typewright.engine.construct.BooleanOp
import com.asoc.typewright.engine.construct.LineJoin
import com.asoc.typewright.project.AutosavePolicy
import com.asoc.typewright.project.GlyphRef
import com.asoc.typewright.project.OpenResult
import com.asoc.typewright.project.PointRef
import com.asoc.typewright.project.ProjectSession
import com.asoc.typewright.project.SessionEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Boolean, Stroke, Anchors and Metrics against real sessions: each is one undo step that undoes to exactly what it started
 * from, a locked glyph refuses the three that change a glyph (and says why), a refusal says why and makes no step, and what
 * is written to the project's files comes back when it is opened again.
 */
class DrawLastToolsSessionTest {
    private fun ProjectSession.glyph(name: String): Glyph = state.value.font.glyph(GlyphRef("regular", name))!!

    private fun ProjectSession.undoSteps(): Int = history.value.undoLabels.size

    private fun ProjectSession.info(): UfoFontInfo =
        state.value.font
            .master("regular")!!
            .ufo.fontInfo

    private fun square(
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
    ) = polygonContour(listOf(Point(x0, y0), Point(x1, y0), Point(x1, y1), Point(x0, y1)), CurveFormat.CUBIC)

    /** Two 200 squares overlapping in a 100 square: contour 0 is the left one. */
    private fun twoSquares() = Glyph("u", 500, listOf(square(50, 0, 250, 200), square(150, 100, 350, 300)))

    private fun sessionOf(vararg glyphs: Glyph): ProjectSession = DrawTestProjects.session(DrawTestProjects.small(*glyphs))

    private fun both(glyph: Glyph) =
        DrawSelection(
            glyph.contours.indices
                .flatMap { c ->
                    glyph.contours[c]
                        .points.indices
                        .map { PointRef(c, it) }
                }.toSet(),
        )

    // ---- booleans --------------------------------------------------------------------------------

    @Test
    fun aUnionIsOneUndoStepAndUndoGivesBackBothContours() {
        val session = sessionOf(twoSquares())
        val editor = DrawTestProjects.editor(session, "u")
        val before = session.glyph("u")
        val outcome = editor.boolean(BooleanOp.UNION, both(before))
        val done = assertIs<EditOutcome.Done>(outcome)
        assertTrue(done.note.orEmpty().contains("2 contours → 1"), done.note)
        val after = session.glyph("u")
        assertEquals(1, after.contours.size)
        assertEquals(200 * 200 * 2 - 100 * 100.0, after.contours.single().signedArea(), 1.0)
        assertEquals(
            done.selection.points.size,
            after.contours
                .single()
                .points.size,
            "the result is what is selected",
        )
        assertEquals(1, session.undoSteps())
        assertTrue(session.undo())
        assertEquals(before, session.glyph("u"))
        assertTrue(session.redo())
        assertEquals(after, session.glyph("u"))
    }

    @Test
    fun subtractKeepsTheFirstPickedAndTheOrderIsTheCallers() {
        val session = sessionOf(twoSquares())
        val editor = DrawTestProjects.editor(session, "u")
        val glyph = session.glyph("u")
        assertIs<EditOutcome.Done>(editor.boolean(BooleanOp.SUBTRACT, both(glyph), order = listOf(1, 0)))
        val kept = session.glyph("u").contours.single()
        assertEquals(350, kept.points.maxOf { it.point.x }, "contour 1 was picked first, so it is the one that stays")
        assertEquals(200 * 200 - 100 * 100.0, kept.signedArea(), 1.0)
    }

    @Test
    fun anIntersectionLeavesOnlyTheOverlapAndAnExcludeEverythingElse() {
        val session = sessionOf(twoSquares())
        val editor = DrawTestProjects.editor(session, "u")
        assertIs<EditOutcome.Done>(editor.boolean(BooleanOp.INTERSECT, both(session.glyph("u"))))
        assertEquals(
            100 * 100.0,
            session
                .glyph("u")
                .contours
                .single()
                .signedArea(),
            1.0,
        )
        assertTrue(session.undo())
        assertIs<EditOutcome.Done>(editor.boolean(BooleanOp.EXCLUDE, both(session.glyph("u"))))
        assertEquals(2 * (200 * 200 - 100 * 100.0), session.glyph("u").contours.sumOf { it.signedArea() }, 1.0)
    }

    @Test
    fun aBooleanOnFewerThanTwoContoursSaysSoAndMakesNoStep() {
        val session = sessionOf(twoSquares())
        val editor = DrawTestProjects.editor(session, "u")
        val one = DrawSelection(setOf(PointRef(0, 0)))
        val outcome = editor.boolean(BooleanOp.UNION, one)
        assertTrue(assertIs<EditOutcome.Unchanged>(outcome).reason.contains("two or more"))
        assertEquals(0, session.undoSteps())
    }

    @Test
    fun intersectingContoursThatDoNotOverlapIsRefusedWithItsReason() {
        val session = sessionOf(Glyph("u", 500, listOf(square(0, 0, 100, 100), square(300, 300, 400, 400))))
        val editor = DrawTestProjects.editor(session, "u")
        val outcome = editor.boolean(BooleanOp.INTERSECT, both(session.glyph("u")))
        assertTrue(assertIs<EditOutcome.Unchanged>(outcome).reason.contains("do not all overlap"))
        assertEquals(0, session.undoSteps())
    }

    @Test
    fun aLockedGlyphRefusesABooleanAndKeepsBothContours() {
        val session = sessionOf(twoSquares())
        val editor = DrawTestProjects.editor(session, "u")
        assertIs<EditOutcome.Done>(editor.approve())
        val before = session.glyph("u")
        assertIs<EditOutcome.Locked>(editor.boolean(BooleanOp.UNION, both(before)))
        assertEquals(before, session.glyph("u"))
    }

    @Test
    fun aBooleanOnAQuadraticOutlineSaysTheyAreCubicOnly() {
        val triangle = polygonContour(listOf(Point(0, 0), Point(100, 0), Point(100, 100)), CurveFormat.QUADRATIC)
        val quadratic = Glyph("q", 500, listOf(triangle, triangle))
        val session = sessionOf(quadratic)
        val editor = DrawTestProjects.editor(session, "q")
        val outcome = editor.boolean(BooleanOp.UNION, both(session.glyph("q")))
        assertTrue(assertIs<EditOutcome.Unchanged>(outcome).reason.contains("quadratic"))
    }

    // ---- stroke ----------------------------------------------------------------------------------

    @Test
    fun aCentrelineStrokesToOneClosedOutlineAsOneUndoStep() {
        val session = sessionOf(Glyph("l", 400, emptyList()))
        val editor = DrawTestProjects.editor(session, "l")
        val line = listOf(Point(100, 0), Point(100, 600))
        val plan = editor.planStroke(line, DrawSelection.EMPTY, 80.0, LineJoin.MITER, CurveFormat.CUBIC)
        val done = assertIs<EditOutcome.Done>(editor.perform(plan))
        assertTrue(done.note.orEmpty().contains("80 units wide"), done.note)
        val made = session.glyph("l").contours.single()
        assertEquals(80 * 600.0, made.signedArea(), 1.0)
        assertTrue(made.signedArea() > 0, "an outer contour winds anticlockwise")
        assertEquals(1, session.undoSteps())
        assertTrue(session.undo())
        assertTrue(session.glyph("l").contours.isEmpty())
    }

    @Test
    fun aSelectedContourBecomesARingAndUndoGivesItBack() {
        val session = sessionOf(Glyph("o", 600, listOf(square(100, 100, 500, 500))))
        val editor = DrawTestProjects.editor(session, "o")
        val before = session.glyph("o")
        val plan = editor.planStroke(emptyList(), both(before), 60.0, LineJoin.MITER, CurveFormat.CUBIC)
        assertIs<EditOutcome.Done>(editor.perform(plan))
        val rings = session.glyph("o").contours
        assertEquals(2, rings.size)
        assertTrue(rings[0].signedArea() > 0 && rings[1].signedArea() < 0, "an outer and a counter")
        assertEquals(460.0 * 460.0 - 340.0 * 340.0, rings.sumOf { it.signedArea() }, 40.0)
        assertTrue(session.undo())
        assertEquals(before, session.glyph("o"))
    }

    @Test
    fun aStrokeWithNothingToStrokeOrAnUnusableWidthSaysWhy() {
        val session = sessionOf(Glyph("l", 400, emptyList()))
        val editor = DrawTestProjects.editor(session, "l")
        val none = editor.perform(editor.planStroke(emptyList(), DrawSelection.EMPTY, 60.0, LineJoin.MITER, CurveFormat.CUBIC))
        assertTrue(assertIs<EditOutcome.Unchanged>(none).reason.contains("Lay a centreline"))
        val tooWide = editor.planStroke(listOf(Point(0, 0), Point(10, 0)), DrawSelection.EMPTY, 5000.0, LineJoin.MITER, CurveFormat.CUBIC)
        val wide = editor.perform(tooWide)
        assertTrue(assertIs<EditOutcome.Unchanged>(wide).reason.contains("2000"))
        val bow = listOf(Point(0, 0), Point(100, 100), Point(100, 0), Point(0, 100))
        val crossing = editor.perform(editor.planStroke(bow, DrawSelection.EMPTY, 10.0, LineJoin.MITER, CurveFormat.CUBIC))
        assertTrue(assertIs<EditOutcome.Unchanged>(crossing).reason.contains("crosses itself"))
        val quadratic = editor.perform(editor.planStroke(bow.take(2), DrawSelection.EMPTY, 10.0, LineJoin.MITER, CurveFormat.QUADRATIC))
        assertTrue(assertIs<EditOutcome.Unchanged>(quadratic).reason.contains("quadratic"))
        assertEquals(0, session.undoSteps())
    }

    @Test
    fun aLockedGlyphRefusesAStroke() {
        val session = sessionOf(Glyph("l", 400, emptyList()))
        val editor = DrawTestProjects.editor(session, "l")
        assertIs<EditOutcome.Done>(editor.approve())
        val plan = editor.planStroke(listOf(Point(0, 0), Point(0, 300)), DrawSelection.EMPTY, 40.0, LineJoin.MITER, CurveFormat.CUBIC)
        assertIs<EditOutcome.Locked>(editor.perform(plan))
        assertTrue(session.glyph("l").contours.isEmpty())
    }

    // ---- anchors ---------------------------------------------------------------------------------

    @Test
    fun addMoveRenameAndDeleteAreOneUndoStepEachAndUndoGivesBackTheSameAnchors() {
        val session = sessionOf(Glyph("a", 500, listOf(square(50, 0, 450, 400))))
        val editor = DrawTestProjects.editor(session, "a")
        val start = session.glyph("a")
        assertIs<EditOutcome.Done>(editor.addAnchor("top", Point(250, 420)))
        assertEquals(listOf(Anchor("top", Point(250, 420))), session.glyph("a").anchors)
        assertIs<EditOutcome.Done>(editor.moveAnchor("top", Point(260, 430)))
        assertEquals(
            Point(260, 430),
            session
                .glyph("a")
                .anchors
                .single()
                .point,
        )
        assertIs<EditOutcome.Done>(editor.renameAnchor("top", "bottom"))
        assertEquals(
            "bottom",
            session
                .glyph("a")
                .anchors
                .single()
                .name,
        )
        assertIs<EditOutcome.Done>(editor.deleteAnchor("bottom"))
        assertTrue(session.glyph("a").anchors.isEmpty())
        assertEquals(4, session.undoSteps())
        while (session.undo()) Unit
        assertEquals(start, session.glyph("a"))
    }

    @Test
    fun aBlankOrRepeatedAnchorNameIsRefusedWithItsReason() {
        val session = sessionOf(Glyph("a", 500, emptyList(), anchors = listOf(Anchor("top", Point(1, 2)))))
        val editor = DrawTestProjects.editor(session, "a")
        assertTrue(assertIs<EditOutcome.Unchanged>(editor.addAnchor("  ", Point(0, 0))).reason.contains("needs a name"))
        assertTrue(assertIs<EditOutcome.Unchanged>(editor.addAnchor("top", Point(0, 0))).reason.contains("already an anchor"))
        assertTrue(assertIs<EditOutcome.Unchanged>(editor.renameAnchor("top", "top")).reason.contains("name already"))
        assertTrue(assertIs<EditOutcome.Unchanged>(editor.moveAnchor("nope", Point(0, 0))).reason.contains("gone"))
        assertEquals(0, session.undoSteps())
    }

    @Test
    fun aLockedGlyphRefusesEveryAnchorEdit() {
        val session = sessionOf(Glyph("a", 500, listOf(square(50, 0, 450, 400)), anchors = listOf(Anchor("top", Point(250, 420)))))
        val editor = DrawTestProjects.editor(session, "a")
        assertIs<EditOutcome.Done>(editor.approve())
        val before = session.glyph("a")
        assertIs<EditOutcome.Locked>(editor.addAnchor("bottom", Point(0, 0)))
        assertIs<EditOutcome.Locked>(editor.moveAnchor("top", Point(1, 1)))
        assertIs<EditOutcome.Locked>(editor.renameAnchor("top", "x"))
        assertIs<EditOutcome.Locked>(editor.deleteAnchor("top"))
        assertIs<EditOutcome.Locked>(editor.beginAnchorDrag("top"))
        assertEquals(before, session.glyph("a"))
    }

    @Test
    fun anAnchorDragIsOneUndoStepHoweverLongItIs() {
        val session = sessionOf(Glyph("a", 500, emptyList(), anchors = listOf(Anchor("top", Point(250, 420)))))
        val editor = DrawTestProjects.editor(session, "a")
        assertNull(editor.beginAnchorDrag("top"))
        for (i in 1..20) assertEquals(DragUpdate.FOLLOWED, editor.dragAnchorTo("top", Point(250 + i, 420 - i)))
        editor.endDrag(commit = true)
        assertEquals(
            Point(270, 400),
            session
                .glyph("a")
                .anchors
                .single()
                .point,
        )
        assertEquals(1, session.undoSteps())
        assertTrue(session.undo())
        assertEquals(
            Point(250, 420),
            session
                .glyph("a")
                .anchors
                .single()
                .point,
        )
    }

    @Test
    fun anchorsComeBackAfterTheProjectIsSavedAndOpenedAgain() {
        val session = sessionOf(Glyph("a", 500, listOf(square(50, 0, 450, 400))))
        val editor = DrawTestProjects.editor(session, "a")
        editor.addAnchor("top", Point(250, 420))
        editor.addAnchor("_bottom", Point(250, -10))
        val expected = session.glyph("a").anchors
        runBlocking { session.flush() }
        val store = session.store
        runBlocking { session.close() }
        val env =
            SessionEnvironment(
                scope = CoroutineScope(Dispatchers.Unconfined),
                io = Dispatchers.Unconfined,
                autosave = AutosavePolicy(enabled = false),
            )
        val reopened = (runBlocking { ProjectSession.open(store, env) } as OpenResult.Opened).session
        assertEquals(expected, reopened.glyph("a").anchors)
    }

    // ---- metrics ---------------------------------------------------------------------------------

    @Test
    fun aTypedXHeightIsWrittenToTheFontInfoAsOneUndoStepAndSaysItChangesEveryGlyph() {
        val session = sessionOf(Glyph("a", 500, emptyList()))
        val editor = DrawTestProjects.editor(session, "a")
        val outcome = editor.setMetric(MetricKind.X_HEIGHT, 520)
        val note = assertIs<EditOutcome.Done>(outcome).note.orEmpty()
        assertTrue(note.contains("every glyph's alignment"), note)
        assertEquals(520, session.info().xHeight)
        assertEquals(700, session.info().capHeight, "the other lines are untouched")
        assertEquals(1, session.undoSteps())
        assertTrue(session.undo())
        assertEquals(500, session.info().xHeight)
    }

    @Test
    fun theBaselineIsFixedAndOnlySensibleValuesAreTaken() {
        val session = sessionOf(Glyph("a", 500, emptyList()))
        val editor = DrawTestProjects.editor(session, "a")
        assertTrue(assertIs<EditOutcome.Unchanged>(editor.setMetric(MetricKind.BASELINE, 10)).reason.contains("baseline"))
        assertTrue(assertIs<EditOutcome.Unchanged>(editor.setMetric(MetricKind.DESCENDER, 200)).reason.contains("negative"))
        assertTrue(assertIs<EditOutcome.Unchanged>(editor.setMetric(MetricKind.X_HEIGHT, -5)).reason.contains("positive"))
        assertTrue(assertIs<EditOutcome.Unchanged>(editor.setMetric(MetricKind.ASCENDER, 9000)).reason.contains("ems"))
        assertEquals(0, session.undoSteps())
    }

    @Test
    fun anXHeightAboveTheCapHeightIsNotedAsOurHeuristicButAllowed() {
        val session = sessionOf(Glyph("a", 500, emptyList()))
        val editor = DrawTestProjects.editor(session, "a")
        val note = assertIs<EditOutcome.Done>(editor.setMetric(MetricKind.X_HEIGHT, 800)).note.orEmpty()
        assertTrue(note.contains("our heuristic"), note)
        assertEquals(800, session.info().xHeight)
    }

    @Test
    fun aMetricsDragIsOneUndoStepAndALineTheFontLacksCannotBeDragged() {
        val session = sessionOf(Glyph("a", 500, emptyList()))
        val editor = DrawTestProjects.editor(session, "a")
        assertNull(editor.beginMetricDrag(MetricKind.CAP_HEIGHT))
        for (i in 1..15) assertEquals(DragUpdate.FOLLOWED, editor.dragMetricTo(MetricKind.CAP_HEIGHT, 700 + i))
        editor.endDrag(commit = true)
        assertEquals(715, session.info().capHeight)
        assertEquals(1, session.undoSteps())
        assertTrue(session.undo())
        assertEquals(700, session.info().capHeight)
        assertTrue(assertIs<EditOutcome.Unchanged>(editor.beginMetricDrag(MetricKind.ASCENDER)).reason.contains("does not give"))
    }

    @Test
    fun aMetricEditIsAllowedOnALockedGlyphBecauseItMovesNoOutline() {
        val session = sessionOf(Glyph("a", 500, listOf(square(50, 0, 450, 400))))
        val editor = DrawTestProjects.editor(session, "a")
        assertIs<EditOutcome.Done>(editor.approve())
        val before = session.glyph("a")
        assertIs<EditOutcome.Done>(editor.setMetric(MetricKind.X_HEIGHT, 510))
        assertEquals(before, session.glyph("a"))
        assertEquals(510, session.info().xHeight)
    }

    @Test
    fun aMetricWrittenToTheFontInfoComesBackAfterTheProjectIsOpenedAgain() {
        val session = sessionOf(Glyph("a", 500, emptyList()))
        val editor = DrawTestProjects.editor(session, "a")
        editor.setMetric(MetricKind.ASCENDER, 760)
        editor.setMetric(MetricKind.DESCENDER, -240)
        runBlocking { session.flush() }
        val store = session.store
        runBlocking { session.close() }
        val env =
            SessionEnvironment(
                scope = CoroutineScope(Dispatchers.Unconfined),
                io = Dispatchers.Unconfined,
                autosave = AutosavePolicy(enabled = false),
            )
        val reopened = (runBlocking { ProjectSession.open(store, env) } as OpenResult.Opened).session
        assertEquals(760, reopened.info().ascender)
        assertEquals(-240, reopened.info().descender)
    }
}
