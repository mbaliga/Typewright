// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.ContourPoint
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.project.GlyphRef
import com.asoc.typewright.project.PointRef
import com.asoc.typewright.ui.deck.DeckSection
import com.asoc.typewright.ui.sheet.Room
import com.asoc.typewright.ui.tokens.CanvasTextures
import com.asoc.typewright.ui.tokens.CloseToken
import com.asoc.typewright.ui.tokens.toColor
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The review's fixes that only show in the whole sheet: the camera, the deck, a field being typed into, the marks. */
class DrawSheetReviewFixesTest {
    private val texture = CanvasTextures.PAPER

    /** How far the handle may end from the pointer, in font units: the platform's own drag slop is swallowed before a drag begins. */
    private val slopUnits = 14.0

    private fun rectangle(
        name: String,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        advance: Int,
    ): Glyph {
        val corners = listOf(Point(x0, y0), Point(x1, y0), Point(x1, y1), Point(x0, y1))
        val points =
            corners.flatMapIndexed { i, p ->
                listOf(ContourPoint(p, true), ContourPoint(p, false), ContourPoint(corners[(i + 1) % 4], false))
            }
        return Glyph(name, advance, listOf(Contour(points, CurveFormat.CUBIC)))
    }

    private fun slab() = rectangle("I", 50, 0, 350, 400, 400)

    private fun DrawSheetRig.glyph(name: String): Glyph =
        session.state.value.font
            .glyph(GlyphRef("regular", name))!!

    private fun DrawSheetRig.undoSteps(): Int = session.history.value.undoLabels.size

    private fun withSlab(block: DrawSheetRig.() -> Unit) = withDrawSheet(DrawTestProjects.small(slab()), "I", 720, 1560, 2f, block = block)

    /** Where the X value of the phone's inspector row is, in dp, on a 360 by 780 dp screen. */
    private val xValue = Vec2(28.0, 585.0)

    // ---- the camera -----------------------------------------------------------------------------------------

    @Test
    fun theLeftArrowWithNothingSelectedLeavesTheCameraWhereItIs() =
        withSlab {
            camera.panBy(Vec2(-31.0, 17.0)) // the person's own view of the glyph
            frames(2)
            val mine = camera.camera
            key(Key.DirectionLeft)
            frames(60)
            assertEquals(mine, camera.camera, "there is no room to the left of Draw: the arrow goes nowhere")
            assertEquals(Room.DRAW, camera.currentRoom)
        }

    @Test
    fun aGlyphChangedWhileInSpaceIsFittedOnComingBackAndNeverBefore() {
        val section = mutableStateOf(DeckSection.DRAW)
        val current = mutableStateOf("I")
        val small = rectangle("J", 0, 0, 120, 120, 150)
        withDrawSheet(DrawTestProjects.small(slab(), small), "I", 720, 1560, 2f, section = section, currentGlyph = current) {
            section.value = DeckSection.SPACE
            frames(80)
            assertEquals(Room.SPACE, camera.currentRoom)
            val inSpace = camera.camera
            current.value = "J"
            frames(10)
            assertEquals(Room.SPACE, camera.currentRoom, "a new glyph does not drag the camera into Draw's world")
            assertEquals(inSpace, camera.camera, "and does not move it at all")
            section.value = DeckSection.DRAW
            frames(80)
            assertEquals(Room.DRAW, camera.currentRoom)
            val arrived = camera.camera
            room.fit()
            frames(2)
            assertEquals(arrived, camera.camera, "the new glyph was fitted on arrival: fitting it again changes nothing")
        }
    }

    @Test
    fun theViewThePersonHadIsWhatDrawComesBackToWhenTheGlyphHasNotChanged() {
        val section = mutableStateOf(DeckSection.DRAW)
        withDrawSheet(DrawTestProjects.small(slab()), "I", 720, 1560, 2f, section = section) {
            camera.panBy(Vec2(-31.0, 17.0))
            camera.zoomBy(1.25, Vec2(180.0, 400.0))
            frames(2)
            val mine = camera.camera
            section.value = DeckSection.SPACE
            frames(80)
            section.value = DeckSection.DRAW
            frames(80)
            assertEquals(mine, camera.camera, "no refit: the glyph is the one that was fitted")
        }
    }

    // ---- a field being typed into ---------------------------------------------------------------------------

    @Test
    fun theArrowKeysStepATypedNumberByOneAndShiftByTenAndEnterCommitsIt() =
        withSlab {
            select(0, 3)
            assertEquals(
                350,
                glyph("I")
                    .contours[0]
                    .points[3]
                    .point.x,
            )
            click(xValue)
            frames(4)
            key(Key.DirectionUp)
            key(Key.DirectionUp)
            key(Key.DirectionUp, shift = true)
            key(Key.DirectionDown)
            assertEquals(
                350,
                glyph("I")
                    .contours[0]
                    .points[3]
                    .point.x,
                "stepping only changes the text; nothing is committed yet",
            )
            key(Key.Enter)
            assertEquals(
                350 + 2 + 10 - 1,
                glyph("I")
                    .contours[0]
                    .points[3]
                    .point.x,
                "the field read 361 when Enter committed it",
            )
            assertEquals(1, undoSteps(), "one committed value is one undo step")
        }

    @Test
    fun escapeAfterSteppingPutsTheOldValueBack() =
        withSlab {
            select(0, 3)
            click(xValue)
            frames(4)
            key(Key.DirectionUp, shift = true)
            key(Key.Escape)
            assertEquals(
                350,
                glyph("I")
                    .contours[0]
                    .points[3]
                    .point.x,
            )
            assertEquals(0, undoSteps())
        }

    // ---- the deck while a field is open ----------------------------------------------------------------------

    private fun closeRedPixels(image: org.jetbrains.skia.Image): List<Pair<Int, Int>> {
        val pixels = image.toComposeImageBitmap().toPixelMap()
        val red: Color = CloseToken.RED_ON_LIGHT.toColor()
        val found = mutableListOf<Pair<Int, Int>>()
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                val c = pixels[x, y]
                if (abs(c.red - red.red) < 0.06f && abs(c.green - red.green) < 0.06f && abs(c.blue - red.blue) < 0.06f) found += x to y
            }
        }
        return found
    }

    @Test
    fun theDecksLeftCircleIsCloseWhileAFieldIsOpenAndPutsTheFieldAwayWhenPressed() =
        withSlab {
            select(0, 3)
            assertEquals(0, closeRedPixels(snapshot("draw-field-closed")).size, "Back, not Close: there is no red on the screen")
            click(xValue)
            frames(4)
            val red = closeRedPixels(snapshot("draw-field-open"))
            assertTrue(red.size > 300, "the left circle is the red Close circle while a number is being typed: ${red.size} red pixels")
            val centre = Vec2(red.map { it.first }.average() / 2.0, red.map { it.second }.average() / 2.0)
            click(centre)
            frames(4)
            assertEquals(0, closeRedPixels(snapshot("draw-field-after-close")).size, "pressing Close put the field away")
            // And nothing was committed: Close is a way out without committing.
            assertEquals(
                350,
                glyph("I")
                    .contours[0]
                    .points[3]
                    .point.x,
            )
            assertEquals(0, undoSteps())
        }

    // ---- the page menu steps between glyphs ------------------------------------------------------------------

    @Test
    fun theDecksPageMenuStepsToTheNextAndThePreviousGlyphAndWrapsAtTheEnds() =
        withDrawSheet(DrawTestProjects.small(slab(), rectangle("J", 0, 0, 120, 120, 150)), "I", 720, 1560, 2f) {
            val items = DrawPageMenuItems.build(room, layersShown = false, onCommand = {}, onGoTo = {}, onLayers = null)
            items.single { it.title == "Next glyph" }.run()
            assertEquals(listOf("J"), selections, "the router was asked to open the glyph after I")
            items.single { it.title == "Previous glyph" }.run()
            assertEquals(listOf("J", "J"), selections, "and, from the first glyph, the previous one wraps to the last")
        }

    // ---- the marks of retracted handles -----------------------------------------------------------------------

    @Test
    fun draggingFromTheMarkWithThePointerPullsTheHandleOutUnderThePointerAndTheSideBecomesACurve() =
        withSlab {
            controller.selection = DrawSelection(setOf(PointRef(0, 0)))
            frames(2)
            val mark = RetractedHandles.marks(controller.glyph!!, room.space(), controller.selection).single { it.ref == PointRef(0, 1) }
            val from = room.space().toScreen(mark.at)
            val to = Vec2(from.x + 20.0, from.y - 50.0)
            drag(from, to)
            val handle = glyph("I").contours[0].points[1].point
            val under = room.space().toFont(to)
            assertTrue(
                abs(handle.x - under.x) <= slopUnits && abs(handle.y - under.y) <= slopUnits,
                "the handle is at $handle, the pointer ended over $under",
            )
            assertTrue(handle.y > 20, "off the bottom side, which it was on: $handle")
            assertEquals(Point(50, 0), glyph("I").contours[0].points[0].point, "the node did not move")
            assertEquals(1, undoSteps(), "one drag, one undo step")
            session.undo()
            assertEquals(Point(50, 0), glyph("I").contours[0].points[1].point, "undo retracts it again")
        }

    private fun pixelAt(
        image: org.jetbrains.skia.Image,
        at: Vec2,
    ): Color {
        val pixels = image.toComposeImageBitmap().toPixelMap()
        return pixels[(at.x * 2).toInt(), (at.y * 2).toInt()]
    }

    private fun distance(
        a: Color,
        b: Color,
    ) = abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)

    @Test
    fun aSelectedNodeShowsAMarkWhereItsRetractedHandleCanBePulledFromAndAnUnselectedOneDoesNot() =
        withSlab {
            val nodeRef = PointRef(0, 0)
            val withoutSelection = snapshot("draw-marks-none")
            controller.selection = DrawSelection(setOf(nodeRef))
            frames(2)
            val mark = RetractedHandles.marks(controller.glyph!!, room.space(), controller.selection).single { it.ref == PointRef(0, 1) }
            val at = room.space().toScreen(mark.at)
            val withSelection = snapshot("draw-marks-shown")
            val canvas = texture.canvas.toColor()
            val ink = texture.ink.toColor()
            // The bottom side runs through the mark: without a selection its centre is the side's ink; with one, the diamond's paper fill covers it.
            assertTrue(distance(pixelAt(withoutSelection, at), ink) < distance(pixelAt(withoutSelection, at), canvas), "ink under the spot")
            assertTrue(
                distance(pixelAt(withSelection, at), canvas) < distance(pixelAt(withSelection, at), ink),
                "a mark's paper fill over it",
            )
            assertNotEquals(pixelAt(withoutSelection, at), pixelAt(withSelection, at))
        }
}
