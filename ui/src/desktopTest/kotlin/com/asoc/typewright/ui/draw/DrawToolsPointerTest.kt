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
import com.asoc.typewright.ui.puck.Tool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Pen, the Knife and Tidy driven by the pointer and keys of the real sheet, one undo step per gesture. */
class DrawToolsPointerTest {
    private fun slab(): Glyph =
        Glyph("I", 400, listOf(polygonContour(listOf(Point(50, 0), Point(350, 0), Point(350, 400), Point(50, 400)), CurveFormat.CUBIC)))

    private fun DrawSheetRig.glyph(name: String): Glyph =
        session.state.value.font
            .glyph(GlyphRef("regular", name))!!

    private fun DrawSheetRig.undoSteps(): Int = session.history.value.undoLabels.size

    private fun withSlab(block: DrawSheetRig.() -> Unit) = withDrawSheet(DrawTestProjects.small(slab()), "I", 720, 1560, 2f, block = block)

    private fun withEmpty(block: DrawSheetRig.() -> Unit) =
        withDrawSheet(DrawTestProjects.small(Glyph("a", 500, emptyList())), "a", 720, 1560, 2f, block = block)

    private fun midpoint(
        a: Vec2,
        b: Vec2,
    ) = Vec2((a.x + b.x) / 2.0, (a.y + b.y) / 2.0)

    // ---- the pen ---------------------------------------------------------------------------------

    @Test
    fun draggingWithThePenPullsHandlesOutOfASmoothPointAndTheShapeIsOneUndoStep() =
        withEmpty {
            key(Keys.P)
            assertEquals(Tool.PEN, puck.currentTool)
            click(Vec2(150.0, 250.0))
            val corner = Vec2(300.0, 250.0)
            drag(corner, Vec2(corner.x + 40.0, corner.y - 30.0))
            val pulled = controller.penPath.anchors.last()
            assertTrue(pulled.hasHandles, "a drag from empty canvas with the pen is a pulled point, not a selection sweep")
            assertTrue(checkNotNull(pulled.pull).x > 0 && pulled.pull!!.y > 0, "the handle follows the pointer (y is up in font units)")
            assertNull(controller.sweep, "no marquee was drawn")
            click(Vec2(300.0, 400.0))
            click(Vec2(150.0, 400.0))
            assertEquals(4, controller.penPoints.size)
            assertEquals(0, glyph("a").contours.size, "nothing is in the glyph until the path is closed")
            click(Vec2(150.0, 250.0))
            val made = glyph("a").contours.single()
            assertEquals(4, made.points.count { it.onCurve })
            assertEquals(1, made.points.count { it.smooth })
            assertTrue(made.brokenSmoothNodes().isEmpty())
            assertEquals(1, undoSteps())
            assertTrue(controller.penPath.isEmpty)
            assertTrue(session.undo())
            assertEquals(0, glyph("a").contours.size)
        }

    @Test
    fun undoWhileThePenIsDrawingStepsBackOnePointWithoutTouchingTheHistory() =
        withEmpty {
            key(Keys.P)
            click(Vec2(150.0, 250.0))
            click(Vec2(300.0, 250.0))
            click(Vec2(300.0, 400.0))
            key(Keys.Z, ctrl = true)
            assertEquals(2, controller.penPoints.size)
            key(Keys.Z, ctrl = true)
            key(Keys.Z, ctrl = true)
            assertTrue(controller.penPath.isEmpty)
            assertEquals(0, undoSteps())
            assertEquals(0, glyph("a").contours.size)
        }

    @Test
    fun closingAPathWithNoAreaKeepsThePathAndSaysWhy() =
        withEmpty {
            key(Keys.P)
            click(Vec2(150.0, 300.0))
            click(Vec2(250.0, 300.0))
            click(Vec2(350.0, 300.0))
            click(Vec2(150.0, 300.0))
            assertEquals(3, controller.penPoints.size, "the path is still there to be fixed")
            val notice = assertIs<DrawNotice.Toast>(controller.notice)
            assertTrue(notice.text.contains("no area"), notice.text)
            assertEquals(0, undoSteps())
        }

    // ---- the knife -------------------------------------------------------------------------------

    @Test
    fun theSplitChordWithTwoNodesSelectedCutsTheContourInTwoAsOneStep() =
        withSlab {
            val before = glyph("I")
            controller.selection = DrawSelection(setOf(PointRef(0, 0), PointRef(0, 6)))
            frames(2)
            key(Keys.K, ctrl = true, shift = true)
            assertEquals(2, glyph("I").contours.size)
            assertEquals(1, undoSteps())
            assertNull(controller.knife)
            key(Keys.Z, ctrl = true)
            assertEquals(before, glyph("I"))
        }

    @Test
    fun theKnifeCutsBetweenTwoTapsOnTheOutlineAndOneTapTwiceOnlyAddsANode() =
        withSlab {
            key(Keys.K, ctrl = true, shift = true)
            assertNotNull(controller.knife, "with nothing selected the chord arms the knife")
            val bottom = midpoint(screenOf(0, 0), screenOf(0, 3))
            val top = midpoint(screenOf(0, 6), screenOf(0, 9))
            click(bottom)
            assertNotNull(controller.knife?.first, "the first tap puts the first end down and changes nothing")
            assertEquals(1, glyph("I").contours.size)
            assertEquals(0, undoSteps())
            click(top)
            assertEquals(2, glyph("I").contours.size, "the second tap cuts")
            assertEquals(1, undoSteps(), "the whole cut is one undo step, the two inserted nodes included")
            assertNull(controller.knife)
            key(Keys.Z, ctrl = true)
            assertEquals(slab(), glyph("I"))
            // The same place twice is a node and no cut.
            key(Keys.K, ctrl = true, shift = true)
            click(bottom)
            click(bottom)
            assertEquals(1, glyph("I").contours.size)
            assertEquals(5, glyph("I").contours[0].points.count { it.onCurve })
            assertNull(controller.knife)
        }

    @Test
    fun theKnifeRefusesEmptyCanvasAndAnotherEscapePutsItDown() =
        withSlab {
            key(Keys.K, ctrl = true, shift = true)
            val nothing = midpoint(screenOf(0, 0), screenOf(0, 6))
            click(nothing)
            assertNotNull(controller.knife)
            assertTrue((controller.notice as DrawNotice.Toast).text.contains("outline itself"), "${controller.notice}")
            key(Keys.ESCAPE)
            assertNull(controller.knife)
            assertEquals(0, undoSteps())
        }

    @Test
    fun theKnifeRefusesALockedGlyphWithTheUnlockPrompt() =
        withSlab {
            controller.editor.approve()
            controller.selection = DrawSelection(setOf(PointRef(0, 0), PointRef(0, 6)))
            frames(2)
            key(Keys.K, ctrl = true, shift = true)
            assertIs<DrawNotice.Locked>(controller.notice)
            assertEquals(1, glyph("I").contours.size)
        }

    // ---- tidy ------------------------------------------------------------------------------------

    @Test
    fun theTidyChordShowsTheCountAndMarksFirstAndApplyMakesOneStep() {
        val session = DrawTestProjects.hyleDecoCubic()
        withDrawSheet(session, "H", 720, 1560, 2f) {
            val before = glyph("H")
            key(Keys.Y, ctrl = true, shift = true)
            val proposal = assertIs<DrawNotice.Proposal>(controller.notice)
            assertTrue(proposal.text.startsWith("Tidy · ") && proposal.text.contains(" → "), proposal.text)
            assertTrue(controller.tidyRemoved.isNotEmpty())
            assertEquals(before, glyph("H"))
            assertEquals(0, undoSteps())
            key(Keys.ESCAPE)
            assertNull(controller.notice)
            assertTrue(controller.tidyRemoved.isEmpty())
            key(Keys.Y, ctrl = true, shift = true)
            controller.applyProposal()
            assertEquals(1, undoSteps())
            key(Keys.Z, ctrl = true)
            assertEquals(before, glyph("H"))
        }
    }
}
