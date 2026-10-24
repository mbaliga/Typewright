// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.Constraints
import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.ContourPoint
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Direction
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.core.geometry.direction
import com.asoc.typewright.project.GlyphRef
import com.asoc.typewright.project.PointRef
import com.asoc.typewright.ui.deck.DeckSection
import com.asoc.typewright.ui.puck.PuckGestureState
import com.asoc.typewright.ui.puck.Tool
import com.asoc.typewright.ui.sheet.MAX_ZOOM
import com.asoc.typewright.ui.sheet.MIN_ZOOM
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Draw sheet driven by pointer and key events sent through the scene, the same way a window sends
 * them: what a mouse and a keyboard do to a real project's real glyph, and what the history holds
 * afterwards. A touch screen is not simulated beyond the pointer type; touch is owner-verified on a
 * device only.
 */
class DrawPointerTest {
    private fun on(
        x: Int,
        y: Int,
    ) = ContourPoint(Point(x, y), onCurve = true)

    /** A plain 300 by 400 rectangle in cubic form: four corners, each side a straight line. */
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

    private companion object {
        const val HOLD_WAIT_MS = 520L
    }

    private fun DrawSheetRig.glyph(name: String): Glyph =
        session.state.value.font
            .glyph(GlyphRef("regular", name))!!

    private fun DrawSheetRig.undoSteps(): Int = session.history.value.undoLabels.size

    private fun withSlab(block: DrawSheetRig.() -> Unit) = withDrawSheet(DrawTestProjects.small(slab()), "I", 720, 1560, 2f, block = block)

    // ---- selecting -----------------------------------------------------------------------------

    @Test
    fun clickingANodeSelectsItAndClickingNothingClearsIt() =
        withSlab {
            val corner = screenOf(0, 3)
            click(corner)
            assertEquals(setOf(PointRef(0, 3)), controller.selection.points)
            // The middle of the slab is far from every line of the outline.
            click(Vec2(corner.x - 100.0, corner.y - 120.0))
            assertTrue(controller.selection.isEmpty, "a click on nothing clears the selection")
        }

    @Test
    fun aSheetThatAnotherScreenCoversTakesNoPointerAndNoKey() =
        withDrawSheet(DrawTestProjects.small(slab()), "I", 720, 1560, 2f, deckVisible = false) {
            val before = glyph("I")
            val corner = screenOf(0, 3)
            click(corner)
            assertTrue(controller.selection.isEmpty, "a tap meant for the screen on top selects nothing underneath")
            drag(corner, Vec2(corner.x + 40.0, corner.y - 40.0))
            key(Keys.RIGHT)
            key(Key.Delete)
            assertEquals(before, glyph("I"))
            assertEquals(0, undoSteps())
        }

    @Test
    fun clickingASideSelectsTheSegmentWithTheSpotItWasPickedAt() =
        withSlab {
            val a = screenOf(0, 0)
            val b = screenOf(0, 3)
            click(Vec2((a.x + b.x) / 2.0, (a.y + b.y) / 2.0))
            val segment = assertNotNull(controller.selection.segment)
            assertEquals(0, segment.contour)
            assertEquals(0.5, controller.selection.segmentT, 0.1)
        }

    // ---- moving --------------------------------------------------------------------------------

    @Test
    fun draggingANodeMovesItAndTheWholeDragIsOneUndoStep() =
        withSlab {
            val before = glyph("I")
            val from = screenOf(0, 3)
            drag(from, Vec2(from.x + 30.0, from.y - 20.0), steps = 8)
            val after = glyph("I")
            assertNotEquals(before, after, "the corner moved")
            assertEquals(1, undoSteps(), "the whole drag is one history entry")
            // The two straight sides that meet at the corner still end on it.
            val moved = after.contours[0].points[3].point
            assertEquals(moved, after.contours[0].points[2].point, "the side before the corner still ends on it")
            assertEquals(moved, after.contours[0].points[4].point, "the side after the corner still starts on it")
            assertTrue(session.undo())
            assertEquals(before, glyph("I"), "one undo puts the corner back")
        }

    @Test
    fun aDragThatStartsOnNothingWithNothingSelectedMovesNothingAndSweepsAMarquee() =
        withSlab {
            val before = glyph("I")
            val origin = screenOf(0, 0)
            // From outside the slab's lower left, across the whole of it.
            drag(Vec2(origin.x - 40.0, origin.y + 40.0), Vec2(origin.x + 400.0, origin.y - 520.0), steps = 6)
            assertEquals(before, glyph("I"), "a marquee edits nothing")
            assertEquals(0, undoSteps())
            assertEquals(4, controller.selection.nodes(glyph("I")).size, "the marquee took in every corner")
        }

    // ---- the keyboard --------------------------------------------------------------------------

    @Test
    fun twoFingersTappedTogetherUndoTheLastEdit() =
        withSlab {
            select(0, 3)
            val before = glyph("I")
            key(Keys.RIGHT)
            assertNotEquals(before, glyph("I"))
            twoFingerTap(Vec2(120.0, 200.0), Vec2(220.0, 240.0))
            assertEquals(before, glyph("I"), "the nudge was undone")
        }

    @Test
    fun threeFingersTappedTogetherRedoWhatTwoUndid() =
        withSlab {
            select(0, 3)
            val before = glyph("I")
            key(Keys.RIGHT)
            val nudged = glyph("I")
            twoFingerTap(Vec2(120.0, 200.0), Vec2(220.0, 240.0))
            assertEquals(before, glyph("I"), "two fingers undid it")
            val a = Vec2(120.0, 200.0)
            val b = Vec2(200.0, 220.0)
            val c = Vec2(280.0, 200.0)
            touches(PointerEventType.Press, Triple(1, a, true))
            touches(PointerEventType.Press, Triple(1, a, true), Triple(2, b, true))
            touches(PointerEventType.Press, Triple(1, a, true), Triple(2, b, true), Triple(3, c, true))
            touches(PointerEventType.Release, Triple(1, a, true), Triple(2, b, true), Triple(3, c, false))
            touches(PointerEventType.Release, Triple(1, a, true), Triple(2, b, false), Triple(3, c, false))
            touches(PointerEventType.Release, Triple(1, a, false), Triple(2, b, false), Triple(3, c, false))
            assertEquals(nudged, glyph("I"), "three fingers redid it")
        }

    @Test
    fun twoFingersThatSpreadAreAPinchAndUndoNothing() =
        withSlab {
            select(0, 3)
            key(Keys.RIGHT)
            val nudged = glyph("I")
            val zoomBefore = zoom
            val a = Vec2(150.0, 300.0)
            val b = Vec2(210.0, 300.0)
            touches(PointerEventType.Press, Triple(1, a, true))
            touches(PointerEventType.Press, Triple(1, a, true), Triple(2, b, true))
            touches(PointerEventType.Move, Triple(1, Vec2(100.0, 300.0), true), Triple(2, Vec2(260.0, 300.0), true))
            touches(PointerEventType.Release, Triple(1, Vec2(100.0, 300.0), true), Triple(2, Vec2(260.0, 300.0), false))
            touches(PointerEventType.Release, Triple(1, Vec2(100.0, 300.0), false), Triple(2, Vec2(260.0, 300.0), false))
            assertEquals(nudged, glyph("I"), "a pinch is not an undo")
            assertTrue(zoom > zoomBefore, "the fingers spreading zoomed the sheet in")
            assertTrue(controller.selection.points.isNotEmpty() && glyph("I") == nudged, "and the selection and the glyph were left alone")
        }

    @Test
    fun aFingerHeldOnThePuckKeepsTheOtherHandsDragOnOneAxisAndTheSheetStill() =
        withSlab {
            val puckBody = Vec2(52.0, 335.0)
            val corner = screenOf(0, 3)
            val before = glyph("I").contours[0].points[3].point
            val zoomBefore = zoom
            touches(PointerEventType.Press, Triple(1, puckBody, true))
            assertTrue(puck.modifierHeld, "a finger resting on the puck is a modifier")
            touches(PointerEventType.Press, Triple(1, puckBody, true), Triple(2, corner, true))
            for (i in 1..8) {
                val at = Vec2(corner.x + 4.0 * i, corner.y - 1.5 * i)
                touches(PointerEventType.Move, Triple(1, puckBody, true), Triple(2, at, true))
            }
            val end = Vec2(corner.x + 32.0, corner.y - 12.0)
            touches(PointerEventType.Release, Triple(1, puckBody, true), Triple(2, end, false))
            touches(PointerEventType.Release, Triple(1, puckBody, false), Triple(2, end, false))
            val after = glyph("I").contours[0].points[3].point
            assertTrue(after.x > before.x, "the corner followed the finger to the right")
            assertEquals(before.y, after.y, "and with the puck held it stayed on one axis")
            assertEquals(1, undoSteps())
            assertEquals(zoomBefore, zoom, 1e-9, "the two fingers were not read as a pinch")
            assertEquals(false, puck.toolListUnfolded, "and lifting the puck finger did not count as a tap on the puck")
            assertEquals(PuckGestureState.Idle, puck.gestureState)
        }

    @Test
    fun theArrowKeysNudgeTheSelectionAndUndoTakesTheNudgeBack() =
        withSlab {
            select(0, 3)
            val before = glyph("I").contours[0].points[3].point
            key(Keys.RIGHT)
            assertEquals(Point(before.x + 1, before.y), glyph("I").contours[0].points[3].point)
            key(Keys.Z, ctrl = true)
            assertEquals(before, glyph("I").contours[0].points[3].point)
        }

    @Test
    fun controlShiftYIsTidyAndNotRedo() =
        withSlab {
            select(0, 3)
            key(Keys.RIGHT)
            key(Keys.Z, ctrl = true)
            assertTrue(session.history.value.canRedo, "there is something to redo")
            key(Keys.Y, ctrl = true, shift = true)
            assertTrue(session.history.value.canRedo, "Ctrl Shift Y did not redo")
            key(Keys.Y, ctrl = true)
            assertEquals(false, session.history.value.canRedo, "Ctrl Y did")
        }

    @Test
    fun enterMakesTheSelectedCornerSmoothAndOneUndoMakesItACornerAgain() =
        withSlab {
            select(0, 3)
            key(Keys.ENTER)
            assertTrue(glyph("I").contours[0].points[3].smooth)
            assertEquals(1, undoSteps())
            key(Keys.Z, ctrl = true)
            assertEquals(false, glyph("I").contours[0].points[3].smooth)
        }

    @Test
    fun controlASelectsEveryPointAndEscapeClearsTheSelection() =
        withSlab {
            key(Key.A, ctrl = true)
            assertEquals(glyph("I").contours[0].points.size, controller.selection.points.size)
            key(Keys.ESCAPE)
            assertTrue(controller.selection.isEmpty)
        }

    @Test
    fun controlZeroFitsTheGlyphAndControlOneShowsItAtOneUnitToTheDp() =
        withSlab {
            val fitted = zoom
            repeat(2) { wheel(Vec2(180.0, 300.0), -3f) }
            assertNotEquals(fitted, zoom, 1e-6)
            key(Key.Zero, ctrl = true)
            assertEquals(fitted, zoom, 1e-9, "Ctrl 0 fits the glyph again")
            key(Key.One, ctrl = true)
            assertEquals(1.0, room.space().dpPerUnit, 1e-9, "Ctrl 1 is one font unit to one dp")
        }

    @Test
    fun thePageKeysStepThroughTheFontsGlyphsAndWrapAtTheEnds() =
        withDrawSheet(DrawTestProjects.small(slab(), slab().copy(name = "J")), "I", 720, 1560, 2f) {
            key(Key.PageDown)
            key(Key.PageUp)
            assertEquals(
                listOf("J", "J"),
                selections,
                "past the last glyph the next is the first, and the one before the first is the last",
            )
        }

    @Test
    fun toolKeysSwitchTheToolByThePhysicalKey() =
        withSlab {
            key(Keys.P)
            assertEquals(Tool.PEN, puck.currentTool)
            key(Keys.V)
            assertEquals(Tool.SELECT, puck.currentTool)
        }

    @Test
    fun tabStepsToTheNextNodeWhileSomethingIsSelected() =
        withSlab {
            select(0, 0)
            key(Keys.TAB)
            assertEquals(setOf(PointRef(0, 3)), controller.selection.points, "Tab goes to the next node")
        }

    @Test
    fun tabWithNothingSelectedSelectsNothingAndIsLeftToMoveFocus() =
        withSlab {
            key(Keys.TAB)
            assertTrue(controller.selection.isEmpty, "Tab on an empty selection does not start selecting nodes")
        }

    @Test
    fun deletingTheSelectedCornerLeavesAThreeSidedContourAndOneUndoPutsItBack() =
        withSlab {
            val before = glyph("I")
            select(0, 3)
            key(Keys.DELETE)
            assertEquals(3, glyph("I").contours[0].points.count { it.onCurve })
            assertEquals(1, undoSteps())
            session.undo()
            assertEquals(before, glyph("I"))
        }

    // ---- the inspector row ---------------------------------------------------------------------------

    /** Where the X value of the phone's inspector row is, in dp, on a 360 by 780 dp screen. */
    private val xValue = Vec2(28.0, 585.0)

    @Test
    fun scrubbingTheXValueMovesTheSelectedNodeAndTheWholeScrubIsOneUndoStep() =
        withSlab {
            select(0, 3)
            val before = glyph("I").contours[0].points[3].point
            // 8 dp of drag is one unit, after the platform's own slop: 40 dp is a handful.
            drag(xValue, Vec2(xValue.x + 40.0, xValue.y), steps = 10)
            val after = glyph("I").contours[0].points[3].point
            assertTrue(after.x - before.x in 3..5, "the node moved right by a few units: ${after.x - before.x}")
            assertEquals(before.y, after.y)
            assertEquals(1, undoSteps(), "a scrub is one history entry however long it ran")
            session.undo()
            assertEquals(before, glyph("I").contours[0].points[3].point)
        }

    @Test
    fun scrubbingBackToWhereItStartedLeavesTheGlyphAsItWas() =
        withSlab {
            select(0, 3)
            val before = glyph("I")
            press(xValue)
            for (i in 1..5) move(Vec2(xValue.x + 8.0 * i, xValue.y))
            for (i in 4 downTo 0) move(Vec2(xValue.x + 8.0 * i, xValue.y))
            release(xValue)
            assertEquals(before, glyph("I"))
        }

    @Test
    fun aTypedNumberPlacesTheSelectedNodeAndAnExpressionReadsTheFontsMetrics() =
        withSlab {
            select(0, 3)
            assertNull(controller.commitField(FieldKeys.X, "320"), "a number goes in")
            assertEquals(
                320,
                glyph("I")
                    .contours[0]
                    .points[3]
                    .point.x,
            )
            assertNull(controller.commitField(FieldKeys.Y, "=xheight"))
            assertEquals(
                500,
                glyph("I")
                    .contours[0]
                    .points[3]
                    .point.y,
                "the slab's font says its x-height is 500",
            )
            assertEquals(2, undoSteps(), "each committed value is one undo step")
        }

    @Test
    fun aMistypedValueComesBackAsAMessageAndChangesNothing() =
        withSlab {
            select(0, 3)
            val before = glyph("I")
            val message = assertNotNull(controller.commitField(FieldKeys.X, "=nonsense+"))
            assertTrue(message.isNotBlank())
            assertEquals(before, glyph("I"))
            assertEquals(0, undoSteps())
        }

    @Test
    fun theAdvanceAndTheSidebearingsAreTypedAboutTheGlyphWhenNothingIsSelected() =
        withSlab {
            assertNull(controller.commitField(FieldKeys.ADVANCE, "450"))
            assertEquals(450, glyph("I").advanceWidth)
            val rsbBefore = GlyphLines.of(glyph("I")).rsb
            assertNull(controller.commitField(FieldKeys.LSB, "60"))
            assertEquals(60, GlyphLines.of(glyph("I")).lsb)
            assertEquals(rsbBefore, GlyphLines.of(glyph("I")).rsb, "setting the left sidebearing leaves the right one as it was")
        }

    @Test
    fun aTypedValueOnALockedGlyphIsRefusedWithTheLockedNotice() =
        withSlab {
            select(0, 3)
            key(Keys.L, ctrl = true)
            val before = glyph("I")
            controller.commitField(FieldKeys.X, "320")
            assertEquals(before, glyph("I"))
            assertTrue(controller.notice is DrawNotice.Locked)
        }

    // ---- an unbuilt tool --------------------------------------------------------------------------

    @Test
    fun aToolThatIsNotOnTheSheetYetSaysSoOnceAndTouchesNothing() =
        withSlab {
            val before = glyph("I")
            key(Key.B) // Boolean
            click(screenOf(0, 3))
            val notice = controller.notice as DrawNotice.Toast
            assertTrue(notice.text.contains("not connected"), notice.text)
            assertEquals(before, glyph("I"))
            assertTrue(controller.selection.isEmpty, "a click with an unbuilt tool selects nothing either")
            val first = notice.serial
            click(screenOf(0, 0))
            assertEquals(first, (controller.notice as? DrawNotice.Toast)?.serial, "it does not say it again at every click")
        }

    // ---- the pen ---------------------------------------------------------------------------------

    @Test
    fun penClicksMakeAClosedContourWoundTheWayItsFormatWantsAndOneUndoRemovesIt() =
        withDrawSheet(DrawTestProjects.small(Glyph("a", 500, emptyList())), "a", 720, 1560, 2f) {
            key(Keys.P)
            assertEquals(Tool.PEN, puck.currentTool)
            val corners = listOf(Vec2(150.0, 250.0), Vec2(260.0, 250.0), Vec2(260.0, 400.0), Vec2(150.0, 400.0))
            for (corner in corners) click(corner)
            assertEquals(4, controller.penPoints.size, "four points are waiting; nothing is in the glyph yet")
            assertEquals(0, glyph("a").contours.size)
            // A click back on the first point closes the loop.
            click(corners[0])
            val made = glyph("a")
            assertEquals(1, made.contours.size)
            assertEquals(4, made.contours[0].points.count { it.onCurve })
            val wantsCounterClockwise = made.contours[0].format == CurveFormat.CUBIC
            assertEquals(
                if (wantsCounterClockwise) Direction.COUNTER_CLOCKWISE else Direction.CLOCKWISE,
                made.contours[0].direction(),
                "an outer contour winds the way its format's files wind it",
            )
            assertEquals(1, undoSteps())
            assertTrue(controller.penPoints.isEmpty())
            assertTrue(session.undo())
            assertEquals(0, glyph("a").contours.size)
        }

    @Test
    fun escapeThrowsAwayAPenContourInProgress() =
        withDrawSheet(DrawTestProjects.small(Glyph("a", 500, emptyList())), "a", 720, 1560, 2f) {
            key(Keys.P)
            click(Vec2(150.0, 250.0))
            click(Vec2(260.0, 250.0))
            assertEquals(2, controller.penPoints.size)
            key(Keys.ESCAPE)
            assertTrue(controller.penPoints.isEmpty())
            assertEquals(0, undoSteps())
        }

    // ---- the mouse beyond a click ------------------------------------------------------------------

    @Test
    fun aRightClickOnANodeOpensTheRichMenuWithoutMovingAnything() =
        withSlab {
            val before = glyph("I")
            val corner = screenOf(0, 3)
            click(corner, PointerButton.Secondary)
            val menu = assertNotNull(controller.menu, "right-click opens the rich menu, not the radial")
            assertNull(controller.radial)
            assertEquals(PointerKind.MOUSE, menu.pointer)
            assertEquals(setOf(PointRef(0, 3)), controller.selection.points, "the node under the pointer is what the menu is about")
            assertEquals(before, glyph("I"))
            key(Keys.ESCAPE)
            assertNull(controller.menu, "Esc closes it")
        }

    @Test
    fun aRightClickOnASideSelectsThatSegmentAndTheMenuIsAboutIt() =
        withSlab {
            val a = screenOf(0, 0)
            val b = screenOf(0, 3)
            click(Vec2((a.x + b.x) / 2.0, (a.y + b.y) / 2.0), PointerButton.Secondary)
            val menu = assertNotNull(controller.menu)
            assertNotNull(controller.selection.segment, "the side under the pointer is selected")
            assertTrue(menu.subject.startsWith("1 segment"), menu.subject)
        }

    @Test
    fun aDragThatIsUndoneWhileItRunsEndsCleanlyAndLeavesNothingBehind() =
        withSlab {
            val before = glyph("I")
            val from = screenOf(0, 3)
            press(from)
            move(Vec2(from.x + 20.0, from.y - 10.0))
            move(Vec2(from.x + 30.0, from.y - 15.0))
            assertNotEquals(before, glyph("I"), "the corner follows the pointer")
            key(Keys.Z, ctrl = true)
            move(Vec2(from.x + 60.0, from.y - 30.0))
            release(Vec2(from.x + 60.0, from.y - 30.0))
            assertEquals(before, glyph("I"), "the undo took the drag back and the rest of the pointer's run changed nothing")
            assertEquals(0, undoSteps())
            assertEquals(false, controller.editor.isDragging)
        }

    @Test
    fun holdingControlMakesTheSelectToolEvenWhilePenIsChosen() =
        withSlab {
            key(Keys.P)
            assertEquals(Tool.PEN, puck.currentTool)
            holdModifier(Key.CtrlLeft, down = true)
            click(screenOf(0, 3))
            holdModifier(Key.CtrlLeft, down = false)
            assertEquals(setOf(PointRef(0, 3)), controller.selection.points, "with Ctrl held the click selected the corner")
            assertTrue(controller.penPoints.isEmpty(), "and did not start a pen contour")
            assertEquals(Tool.PEN, puck.currentTool, "the chosen tool is still the pen")
        }

    @Test
    fun resizingTheWindowDoesNotThrowAwayTheViewThePersonSet() =
        withSlab {
            repeat(2) { wheel(Vec2(180.0, 300.0), -3f) }
            val zoomed = zoom
            val centre = room.space().toScreen(Vec2(200.0, 200.0))
            scene.constraints = Constraints.fixed(720, 1400)
            frames(6)
            assertEquals(zoomed, zoom, 1e-9, "the zoom is the same after a resize")
            assertEquals(centre, room.space().toScreen(Vec2(200.0, 200.0)), "and so is where the glyph sits")
        }

    @Test
    fun leavingDrawForSpaceAndComingBackLandsOnTheViewThePersonHad() {
        val section = mutableStateOf(DeckSection.DRAW)
        withDrawSheet(DrawTestProjects.small(slab()), "I", 720, 1560, 2f, section = section) {
            repeat(2) { wheel(Vec2(180.0, 300.0), -3f) }
            val view = zoom
            val corner = screenOf(0, 3)
            section.value = DeckSection.SPACE
            frames(1000L)
            assertEquals(1.0, zoom, 1e-6, "Space is laid out for a camera at rest")
            section.value = DeckSection.DRAW
            frames(1000L)
            assertEquals(view, zoom, 1e-6, "back in Draw at the zoom it was left at")
            assertEquals(corner.x, screenOf(0, 3).x, 1e-6, "and the glyph is where it was")
            assertEquals(corner.y, screenOf(0, 3).y, 1e-6)
        }
    }

    @Test
    fun theWheelZoomsAndNeverPastTheRangeTheSheetAllows() =
        withSlab {
            val at = Vec2(180.0, 400.0)
            repeat(60) { wheel(at, -3f) }
            assertTrue(zoom <= MAX_ZOOM + 1e-9, "zoomed in to $zoom")
            assertTrue(zoom > MAX_ZOOM * 0.9, "and it did get there")
            repeat(120) { wheel(at, 3f) }
            assertTrue(zoom >= MIN_ZOOM - 1e-9, "zoomed out to $zoom")
        }

    @Test
    fun dragWithSpaceHeldPansTheSheetAndEditsNothing() =
        withSlab {
            val before = glyph("I")
            val corner = screenOf(0, 3)
            holdModifier(Key.Spacebar, down = true)
            drag(corner, Vec2(corner.x - 30.0, corner.y + 20.0), steps = 5)
            holdModifier(Key.Spacebar, down = false)
            val moved = screenOf(0, 3)
            assertEquals(corner.x - 30.0, moved.x, 0.5, "the sheet followed the pointer left")
            assertEquals(corner.y + 20.0, moved.y, 0.5, "and down")
            assertEquals(before, glyph("I"))
            assertEquals(0, undoSteps())
        }

    @Test
    fun theMiddleButtonPansToo() =
        withSlab {
            val corner = screenOf(0, 3)
            press(corner, PointerButton.Tertiary)
            move(Vec2(corner.x + 25.0, corner.y - 15.0), PointerButton.Tertiary)
            move(Vec2(corner.x + 50.0, corner.y - 30.0), PointerButton.Tertiary)
            release(Vec2(corner.x + 50.0, corner.y - 30.0), PointerButton.Tertiary)
            val moved = screenOf(0, 3)
            assertEquals(corner.x + 50.0, moved.x, 0.5)
            assertEquals(corner.y - 30.0, moved.y, 0.5)
            assertEquals(0, undoSteps())
        }

    @Test
    fun aDoubleClickOnEmptyCanvasFitsTheGlyphAgain() =
        withSlab {
            val fitted = zoom
            repeat(2) { wheel(Vec2(180.0, 400.0), 3f) }
            assertTrue(zoom < fitted - 1e-6, "zoomed out from the fitted size")
            val empty = Vec2(12.0, 150.0)
            click(empty)
            click(empty)
            assertEquals(fitted, zoom, 1e-6, "back at the fitted size")
        }

    // ---- touch: hold for the radial ----------------------------------------------------------------

    /** Real time: the hold is timed on the clock, so the test waits it out. */
    private fun waitForHold() = Thread.sleep(HOLD_WAIT_MS)

    @Test
    fun holdingAFingerOnANodeOpensTheRadialAndLiftingWithoutSweepingLeavesItWaiting() =
        withSlab {
            val corner = screenOf(0, 3)
            press(corner, type = PointerType.Touch)
            waitForHold()
            frames(2)
            val open = assertNotNull(controller.radial, "a finger held 380 ms on a node opens the ring")
            assertTrue(open.state is RadialState.Sweeping)
            assertEquals(setOf(PointRef(0, 3)), controller.selection.points, "the node under the finger is what the ring is about")
            release(corner, type = PointerType.Touch)
            assertTrue(controller.radial?.state is RadialState.Lifted, "lifting inside the dead zone without sweeping leaves the ring open")
        }

    @Test
    fun sweepingAwayFromTheHubAndReleasingRunsTheSectorAcrossFromTheFinger() =
        withSlab {
            val corner = screenOf(0, 3)
            press(corner, type = PointerType.Touch)
            waitForHold()
            frames(2)
            // The thumb goes down and to the right; the sector across the hub is up and to the left: Make smooth.
            val thumb = Vec2(corner.x + 45.0, corner.y + 55.0)
            move(thumb, type = PointerType.Touch)
            assertEquals(7, (controller.radial?.state as RadialState.Sweeping).selected, "the sector at the top left is chosen")
            release(thumb, type = PointerType.Touch)
            assertNull(controller.radial, "releasing commits and closes the ring")
            assertTrue(glyph("I").contours[0].points[3].smooth, "the corner became smooth")
            assertEquals(1, undoSteps())
        }

    @Test
    fun aDigitPicksASectorWhileTheRingIsOpenAndMeansNothingWhenItIsNot() =
        withSlab {
            val corner = screenOf(0, 3)
            key(Key.One)
            assertEquals(0, undoSteps(), "with no ring a 1 is just a 1")
            press(corner, type = PointerType.Touch)
            waitForHold()
            frames(2)
            release(corner, type = PointerType.Touch)
            assertTrue(controller.radial?.state is RadialState.Lifted)
            key(Key.One) // the top sector of a node's ring is Delete
            assertNull(controller.radial)
            assertEquals(3, glyph("I").contours[0].points.count { it.onCurve }, "Delete ran")
            assertEquals(1, undoSteps())
        }

    @Test
    fun movingBackIntoTheDeadZoneBeforeReleasingCancels() =
        withSlab {
            val corner = screenOf(0, 3)
            val before = glyph("I")
            press(corner, type = PointerType.Touch)
            waitForHold()
            frames(2)
            move(Vec2(corner.x + 45.0, corner.y + 55.0), type = PointerType.Touch)
            move(corner, type = PointerType.Touch)
            release(corner, type = PointerType.Touch)
            assertEquals(before, glyph("I"), "nothing was run")
            assertEquals(0, undoSteps())
        }

    @Test
    fun aTapOnALiftedRingSectorRunsIt() =
        withSlab {
            val corner = screenOf(0, 3)
            press(corner, type = PointerType.Touch)
            waitForHold()
            frames(2)
            release(corner, type = PointerType.Touch)
            val ring = assertNotNull(controller.radial)
            // The top left sector of the lifted ring is Make smooth; tap it where the ring was drawn.
            val inRing = Vec2(ring.center.x - 60.0, ring.center.y - 60.0)
            press(inRing, type = PointerType.Touch)
            release(inRing, type = PointerType.Touch)
            assertNull(controller.radial)
            assertTrue(glyph("I").contours[0].points[3].smooth)
        }

    // ---- going to a glyph ------------------------------------------------------------------------

    @Test
    fun controlGOpensTheGoToListAndEnterOnItsFirstRowGoesThere() =
        withDrawSheet(DrawTestProjects.small(slab(), slab().copy(name = "J")), "I", 720, 1560, 2f) {
            key(Keys.G, ctrl = true)
            frames(3)
            key(Keys.ENTER)
            assertEquals(listOf("I"), selections, "the first glyph row was chosen and the router was asked to open it")
        }

    @Test
    fun theLockKeyLocksAndAnEditThenRefusesAndOffersUnlock() =
        withSlab {
            select(0, 3)
            key(Keys.L, ctrl = true)
            assertTrue(controller.editor.isLocked)
            val before = glyph("I")
            key(Keys.RIGHT)
            assertEquals(before, glyph("I"), "a locked glyph refuses the nudge")
            assertTrue(controller.notice is DrawNotice.Locked, "and the screen offers Unlock")
            controller.unlockFromNotice()
            key(Keys.RIGHT)
            assertNotEquals(before, glyph("I"), "after Unlock the edit goes in")
        }
}
