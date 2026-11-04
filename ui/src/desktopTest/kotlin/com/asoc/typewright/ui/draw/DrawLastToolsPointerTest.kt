// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import androidx.compose.ui.input.key.Key
import com.asoc.typewright.core.geometry.Anchor
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.core.geometry.polygonContour
import com.asoc.typewright.engine.construct.BooleanOp
import com.asoc.typewright.project.GlyphRef
import com.asoc.typewright.ui.puck.Tool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Boolean, Stroke, Anchors and Metrics driven by the pointer and keys of the real sheet: one undo step per gesture, a locked
 * glyph refuses and offers Unlock, a refusal says why, and every one is in the command palette or the inspector.
 */
class DrawLastToolsPointerTest {
    private fun square(
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
    ) = polygonContour(listOf(Point(x0, y0), Point(x1, y0), Point(x1, y1), Point(x0, y1)), CurveFormat.CUBIC)

    private fun twoSquares() = Glyph("u", 500, listOf(square(50, 0, 250, 200), square(150, 100, 350, 300)))

    private fun DrawSheetRig.glyph(name: String): Glyph =
        session.state.value.font
            .glyph(GlyphRef("regular", name))!!

    private fun DrawSheetRig.undoSteps(): Int = session.history.value.undoLabels.size

    private fun DrawSheetRig.at(
        x: Int,
        y: Int,
    ): Vec2 = room.space().toScreen(Vec2(x.toDouble(), y.toDouble()))

    private fun DrawSheetRig.info() =
        session.state.value.font
            .master("regular")!!
            .ufo.fontInfo

    private fun withGlyph(
        glyph: Glyph,
        block: DrawSheetRig.() -> Unit,
    ) = withDrawSheet(DrawTestProjects.small(glyph), glyph.name, 720, 1560, 2f, block = block)

    private fun DrawSheetRig.command(id: String): DrawCommand =
        DrawCommands
            .build(DrawCommandContext(controller.editor, controller.selection, controller.metrics(), controller))
            .first { it.id == id }

    // ---- boolean --------------------------------------------------------------------------------

    @Test
    fun tappingTwoContoursAndRunningUnionIsOneUndoStepAndUndoGivesBackBoth() =
        withGlyph(twoSquares()) {
            val before = glyph("u")
            key(Key.B)
            assertEquals(Tool.BOOLEAN, puck.currentTool)
            frames(2)
            click(at(150, 0))
            click(at(250, 300))
            assertEquals(setOf(0, 1), controller.selection.contours)
            assertEquals(listOf(0, 1), controller.booleanOrder())
            assertTrue(controller.modeLine.orEmpty().contains("2 contours picked"), controller.modeLine)
            val union = command("bool-union")
            assertTrue(union.enabled, union.reason)
            assertTrue(union.consequenceText.orEmpty().isNotEmpty())
            controller.run(union)
            assertEquals(1, glyph("u").contours.size)
            assertEquals(1, undoSteps())
            key(Keys.Z, ctrl = true)
            assertEquals(before, glyph("u"))
        }

    @Test
    fun subtractKeepsTheContourTappedFirst() =
        withGlyph(twoSquares()) {
            key(Key.B)
            frames(2)
            click(at(250, 300))
            click(at(150, 0))
            controller.run(command("bool-subtract"))
            val kept = glyph("u").contours.single()
            assertEquals(350, kept.points.maxOf { it.point.x }, "the right-hand square was tapped first, so it stays")
        }

    @Test
    fun aBooleanWithOneContourPickedIsDisabledWithItsReasonAndTheStripSaysWhatToDo() =
        withGlyph(twoSquares()) {
            key(Key.B)
            frames(2)
            click(at(150, 0))
            val union = command("bool-union")
            assertEquals("select two or more contours", union.reason)
            controller.runBoolean(BooleanOp.UNION)
            assertTrue(assertIs<DrawNotice.Toast>(controller.notice).text.contains("two or more"))
            assertEquals(0, undoSteps())
        }

    @Test
    fun aBooleanOnALockedGlyphOffersUnlockAndChangesNothing() =
        withGlyph(twoSquares()) {
            key(Key.B)
            frames(2)
            click(at(150, 0))
            click(at(250, 300))
            controller.editor.approve()
            val before = glyph("u")
            assertEquals("locked: unlock to edit", command("bool-union").reason)
            controller.runBoolean(BooleanOp.UNION)
            assertIs<DrawNotice.Locked>(controller.notice)
            assertEquals(before, glyph("u"))
        }

    @Test
    fun theBooleanOperationsAreInTheCommandPalette() =
        withGlyph(twoSquares()) {
            val ids = DrawPalette.entries(room).map { it.id }
            for (op in listOf("union", "subtract", "intersect", "exclude")) assertTrue("draw-bool-$op" in ids, "no palette row for $op")
            assertTrue("draw-stroke" in ids)
        }

    // ---- stroke ---------------------------------------------------------------------------------

    @Test
    fun tappingACentrelineAndPressingEnterStrokesItAsOneUndoStep() =
        withGlyph(Glyph("l", 600, emptyList())) {
            key(Key.K)
            assertEquals(Tool.STROKE, puck.currentTool)
            frames(2)
            click(at(200, 100))
            click(at(200, 500))
            assertEquals(2, controller.centreline.size)
            assertTrue(controller.strokePreview.isNotEmpty(), "the outline it would make shows before it is made")
            assertEquals(0, glyph("l").contours.size, "and nothing is in the glyph yet")
            key(Keys.ENTER)
            val made = glyph("l").contours.single()
            assertTrue(made.points.count { it.onCurve } == 4, "a butt stroke of a straight line is a rectangle")
            assertTrue(controller.centreline.isEmpty())
            assertEquals(1, undoSteps())
            key(Keys.Z, ctrl = true)
            assertEquals(0, glyph("l").contours.size)
        }

    @Test
    fun undoWhileLayingACentrelineStepsBackOnePointAndEscapeDropsIt() =
        withGlyph(Glyph("l", 600, emptyList())) {
            key(Key.K)
            frames(2)
            click(at(100, 100))
            click(at(300, 100))
            click(at(300, 400))
            key(Keys.Z, ctrl = true)
            assertEquals(2, controller.centreline.size)
            assertEquals(0, undoSteps(), "a point is not a history step")
            key(Keys.ESCAPE)
            assertTrue(controller.centreline.isEmpty())
        }

    @Test
    fun strokeOnALockedGlyphOffersUnlockAndMakesNothing() =
        withGlyph(Glyph("l", 600, emptyList())) {
            key(Key.K)
            frames(2)
            click(at(200, 100))
            click(at(200, 500))
            controller.editor.approve()
            key(Keys.ENTER)
            assertIs<DrawNotice.Locked>(controller.notice)
            assertEquals(0, glyph("l").contours.size)
            assertEquals(2, controller.centreline.size, "the line stays, so Unlock then Enter finishes it")
        }

    @Test
    fun strokeWidthIsATypedFieldAndAnUnusableOneStaysOutAndSaysWhy() =
        withGlyph(Glyph("l", 600, emptyList())) {
            key(Key.K)
            frames(2)
            assertEquals(DrawController.DEFAULT_STROKE_WIDTH, controller.strokeWidth)
            assertNull(controller.commitField(FieldKeys.STROKE_WIDTH, "120"))
            assertEquals(120.0, controller.strokeWidth)
            assertTrue(assertNotNull(controller.commitField(FieldKeys.STROKE_WIDTH, "1")).contains("at least 2"))
            assertTrue(assertNotNull(controller.commitField(FieldKeys.STROKE_WIDTH, "=xheight*10")).contains("2000"))
            assertEquals(120.0, controller.strokeWidth)
            val fields = DrawToolPanel.fields(Tool.STROKE, controller, glyph("l"))!!
            assertEquals(FieldKeys.STROKE_WIDTH, fields.first().key)
            assertTrue(DrawToolPanel.hint(Tool.STROKE, controller)!!.contains("our heuristic"))
        }

    // ---- anchors --------------------------------------------------------------------------------

    @Test
    fun tappingEmptyCanvasAddsAnAnchorDraggingMovesItAndDeleteTakesItAway() =
        withGlyph(Glyph("a", 600, emptyList())) {
            key(Key.A)
            assertEquals(Tool.ANCHORS, puck.currentTool)
            frames(2)
            click(at(200, 250))
            assertEquals(listOf(Anchor("top", Point(200, 250))), glyph("a").anchors)
            assertEquals("top", controller.selectedAnchor())
            assertEquals(1, undoSteps())
            drag(at(200, 250), at(300, 330))
            val moved = glyph("a").anchors.single().point
            assertTrue(moved.x > 200 && moved.y > 250, "the anchor followed the pointer: $moved")
            assertEquals(2, undoSteps(), "a drag is one step however long it is")
            key(Keys.Z, ctrl = true)
            assertEquals(Point(200, 250), glyph("a").anchors.single().point)
            key(Keys.DELETE)
            assertTrue(glyph("a").anchors.isEmpty())
            key(Keys.Z, ctrl = true)
            assertEquals(1, glyph("a").anchors.size)
        }

    @Test
    fun theInspectorRenamesAndPlacesTheSelectedAnchor() =
        withGlyph(Glyph("a", 600, emptyList(), anchors = listOf(Anchor("top", Point(200, 250)), Anchor("bottom", Point(200, -10))))) {
            key(Key.A)
            frames(2)
            click(at(200, 250))
            assertEquals("top", controller.selectedAnchor())
            val keys = DrawToolPanel.fields(Tool.ANCHORS, controller, glyph("a"))!!.mapNotNull { it.key }
            assertEquals(listOf(FieldKeys.ANCHOR_NAME, FieldKeys.ANCHOR_X, FieldKeys.ANCHOR_Y), keys)
            assertNull(controller.commitField(FieldKeys.ANCHOR_NAME, "topright"))
            assertEquals(listOf("topright", "bottom"), glyph("a").anchors.map { it.name })
            assertTrue(assertNotNull(controller.commitField(FieldKeys.ANCHOR_NAME, "bottom")).contains("already an anchor"))
            assertNull(controller.commitField(FieldKeys.ANCHOR_X, "321"))
            assertEquals(Point(321, 250), glyph("a").anchors.first().point)
            val listed = DrawToolPanel.groups(Tool.ANCHORS, controller, glyph("a"), null)!!.first { it.title == "Anchors" }
            assertEquals(listOf("topright", "bottom"), listed.fields.map { it.label })
        }

    @Test
    fun anAnchorOnALockedGlyphCannotBeAddedOrMovedAndUnlockIsOffered() =
        withGlyph(Glyph("a", 600, emptyList(), anchors = listOf(Anchor("top", Point(200, 250))))) {
            controller.editor.approve()
            val steps = undoSteps()
            key(Key.A)
            frames(2)
            click(at(400, 100))
            assertIs<DrawNotice.Locked>(controller.notice)
            drag(at(200, 250), at(300, 330))
            assertEquals(listOf(Anchor("top", Point(200, 250))), glyph("a").anchors)
            assertEquals(steps, undoSteps(), "locking is the only step")
        }

    // ---- metrics --------------------------------------------------------------------------------

    @Test
    fun draggingTheXHeightLineIsOneUndoStepWrittenToTheFontInfoAndSaysItTouchesEveryGlyph() =
        withGlyph(Glyph("a", 600, emptyList())) {
            key(Key.T)
            assertEquals(Tool.METRICS, puck.currentTool)
            frames(2)
            assertEquals(500, info().xHeight)
            drag(at(300, 500), Vec2(at(300, 500).x, at(300, 500).y - 30.0))
            val moved = assertNotNull(info().xHeight)
            assertTrue(moved > 500, "dragging up raises the line: $moved")
            assertEquals(700, info().capHeight)
            assertEquals(1, undoSteps())
            assertTrue(assertIs<DrawNotice.Toast>(controller.notice).text.contains("every glyph's alignment"))
            key(Keys.Z, ctrl = true)
            assertEquals(500, info().xHeight)
        }

    @Test
    fun theBaselineCannotBeDraggedAndSaysWhy() =
        withGlyph(Glyph("a", 600, emptyList())) {
            key(Key.T)
            frames(2)
            drag(at(300, 0), Vec2(at(300, 0).x, at(300, 0).y - 40.0))
            assertTrue(assertIs<DrawNotice.Toast>(controller.notice).text.contains("baseline"))
            assertEquals(0, undoSteps())
        }

    @Test
    fun theInspectorTypesAMetricAndListsTheBaselineAsFixed() =
        withGlyph(Glyph("a", 600, emptyList())) {
            key(Key.T)
            frames(2)
            val fields = DrawToolPanel.fields(Tool.METRICS, controller, glyph("a"))!!
            assertEquals(5, fields.size)
            assertEquals("0 · fixed", fields.first { it.label == "baseline" }.value)
            assertNull(fields.first { it.label == "baseline" }.key, "the baseline has no field to type into")
            assertEquals("—", fields.first { it.label == "ascender" }.value, "a line the font does not give is not invented")
            assertNull(controller.commitField(FieldKeys.keyOf(MetricKind.ASCENDER)!!, "=xheight+250"))
            assertEquals(750, info().ascender)
            assertEquals(1, undoSteps())
            assertTrue(assertNotNull(controller.commitField(FieldKeys.keyOf(MetricKind.DESCENDER)!!, "200")).contains("negative"))
            assertEquals(1, undoSteps())
        }

    @Test
    fun aMetricsDragOnALockedGlyphStillMovesTheFontsLine() =
        withGlyph(Glyph("a", 600, listOf(square(50, 0, 450, 400)))) {
            controller.editor.approve()
            key(Key.T)
            frames(2)
            val before = glyph("a")
            drag(at(550, 500), Vec2(at(550, 500).x, at(550, 500).y - 20.0))
            assertTrue(assertNotNull(info().xHeight) > 500)
            assertEquals(before, glyph("a"), "no outline moved")
        }

    @Test
    fun everyToolPanelFitsTheInspectorRow() =
        withGlyph(twoSquares()) {
            for (tool in listOf(Tool.BOOLEAN, Tool.STROKE, Tool.ANCHORS, Tool.METRICS)) {
                val fields = assertNotNull(DrawToolPanel.fields(tool, controller, glyph("u")), "$tool has a panel")
                assertTrue(fields.size <= 6, "$tool: UI_SPEC §3 allows six pairs, got ${fields.size}")
            }
            assertNull(DrawToolPanel.fields(Tool.SELECT, controller, glyph("u")))
        }
}
