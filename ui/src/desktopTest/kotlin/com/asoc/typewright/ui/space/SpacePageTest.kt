// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.space

import androidx.compose.ui.input.key.Key
import com.asoc.typewright.core.font.ufo.UfoKerning
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.core.geometry.sidebearings
import com.asoc.typewright.project.GlyphRef
import com.asoc.typewright.project.KernScope
import com.asoc.typewright.project.KerningTable
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The Space page as a person uses it: the real page over a real project (Hyle Deco's outlines), pressed and dragged
 * with a mouse and typed at, with every result read back from the project. Nothing here is a touch on a screen; that
 * is owner-verified on a device only.
 */
class SpacePageTest {
    private val kerning =
        UfoKerning(
            groups = SpaceTestProjects.GROUPS,
            kerning = mapOf("public.kern1.round" to mapOf("public.kern2.straight" to -24.0), "n" to mapOf("o" to -12.0)),
        )
    private val ufo get() = SpaceTestProjects.hyleDeco(kerning)

    private fun SpacePageRig.glyph(name: String) =
        session.state.value.font
            .glyph(GlyphRef(master, name))!!

    private fun SpacePageRig.kerningNow() =
        session.state.value.font
            .master(master)!!
            .ufo.kerningInfo

    private fun SpacePageRig.steps() = session.history.value.undoLabels.size

    /** The y (dp) just inside the top of the row's ink block, above any letter, found against the first letter. */
    private fun SpacePageRig.blockTop(): Double {
        val s = string()
        val c0 = s.cells[0]
        return rowTop(xAt(c0.x + c0.glyph.advanceWidth / 2.0)) + 2.0
    }

    private fun SpacePageRig.centre(i: Int): Double {
        val c = string().cells[i]
        return xAt(c.x + c.glyph.advanceWidth / 2.0)
    }

    // ---- the string and its selection ---------------------------------------------------------------

    @Test
    fun theSelectedLetterIsAnInkBlockAndPressingAnotherLetterMovesIt() =
        withSpacePage(ufo) {
            val top = blockTop()
            assertTrue(isInk(colourAt(centre(0), top)), "the router's n, the first in the string, is the ink block")
            assertTrue(isCanvas(colourAt(centre(2), top)))
            rig.click(Vec2(centre(2), top + 20.0))
            rig.frames(4)
            assertTrue(isInk(colourAt(centre(2), top)), "the pressed o is the ink block now")
            assertTrue(isCanvas(colourAt(centre(0), top)), "and the n is not")
            assertEquals(listOf("o"), selections, "the router is told which glyph is in hand")
        }

    @Test
    fun inKerningTheSelectedPairIsOneInkBlockAcrossBothLetters() =
        withSpacePage(ufo) {
            val deck = DeckPoints(widthDp, heightDp)
            rig.click(deck.seat("Kerning", SPACING_SEATS))
            rig.frames(6)
            val top = blockTop()
            assertTrue(isInk(colourAt(centre(0), top)))
            assertTrue(isInk(colourAt(centre(1), top)), "both letters of the pair")
            assertTrue(isCanvas(colourAt(centre(2), top)))
            val s = string()
            val gap = xAt((s.cells[1].right + s.cells[2].x) / 2.0)
            rig.click(Vec2(gap, top + 20.0))
            rig.frames(6)
            assertTrue(isInk(colourAt(centre(1), top)) && isInk(colourAt(centre(2), top)), "the pair o-after-n moved on")
            assertTrue(isCanvas(colourAt(centre(0), top)))
        }

    // ---- dragging a number --------------------------------------------------------------------------

    @Test
    fun draggingTheSelectedLettersRightLineChangesItsRightSidebearingInOneUndoStep() =
        withSpacePage(ufo) {
            val top = blockTop()
            val dpu = row().dpPerUnit
            val n = glyph("n")
            val before = n.sidebearings()!!
            val line = xAt(string().cells[0].right)
            rig.drag(Vec2(line, top + 20.0), Vec2(line + 12.0, top + 20.0))
            rig.frames(4)
            val expected = before.right + (12.0 / dpu).roundToInt()
            assertEquals(expected, glyph("n").sidebearings()!!.right)
            assertEquals(before.left, glyph("n").sidebearings()!!.left, "only the right side moved")
            assertEquals(1, steps(), "the whole drag is one undo step")
            assertTrue(session.undo())
            assertEquals(before.right, glyph("n").sidebearings()!!.right)
        }

    @Test
    fun draggingALettersBodyMovesTheDrawingWithinItsSpaceAndSelectsIt() =
        withSpacePage(ufo) {
            val top = blockTop()
            val dpu = row().dpPerUnit
            val before = glyph("o").sidebearings()!!
            rig.drag(Vec2(centre(2), top + 20.0), Vec2(centre(2) + 10.0, top + 20.0))
            rig.frames(4)
            assertEquals(before.left + (10.0 / dpu).roundToInt(), glyph("o").sidebearings()!!.left)
            assertEquals(
                before.right,
                glyph("o").sidebearings()!!.right,
                "the space after it is unchanged, so the advance grew by the same",
            )
            assertEquals(listOf("o"), selections)
            assertEquals(1, steps())
        }

    @Test
    fun inKerningDraggingBetweenTwoLettersChangesThatPairAtTheGroupsLevel() =
        withSpacePage(ufo) {
            val deck = DeckPoints(widthDp, heightDp)
            rig.click(deck.seat("Kerning", SPACING_SEATS))
            rig.frames(6)
            val top = blockTop()
            val dpu = row().dpPerUnit
            val s = string()
            // The first junction is n-n, both in the straight groups: nothing kerns it yet, so the value goes to the groups.
            val gap = xAt((s.cells[0].right + s.cells[1].x) / 2.0)
            rig.drag(Vec2(gap, top + 20.0), Vec2(gap + 12.0, top + 20.0))
            rig.frames(4)
            val value = (12.0 / dpu).roundToInt().toDouble()
            assertEquals(value, kerningNow().kerning.getValue("public.kern1.straight").getValue("public.kern2.straight"))
            assertEquals(1, steps())
            assertEquals(value, KerningTable(kerningNow()).value("H", "n"), "H before n and the rest of the groups follow")
        }

    // ---- the keyboard -----------------------------------------------------------------------------

    @Test
    fun theArrowsNudgeTheSelectedNumberByOneAndShiftByTenAndUndoTakesThemBack() =
        withSpacePage(ufo) {
            val start = glyph("n").sidebearings()!!.right
            rig.key(Key.DirectionRight)
            assertEquals(start + 1, glyph("n").sidebearings()!!.right)
            rig.key(Key.DirectionRight, shift = true)
            assertEquals(start + 11, glyph("n").sidebearings()!!.right)
            rig.key(Key.DirectionLeft)
            assertEquals(start + 10, glyph("n").sidebearings()!!.right)
            // A burst of nudges on one number is one undo step, as in Draw.
            assertEquals(1, steps())
            rig.key(Key.Z, ctrl = true)
            assertEquals(start, glyph("n").sidebearings()!!.right, "Ctrl Z takes the burst back")
            rig.key(Key.Z, ctrl = true, shift = true)
            assertEquals(start + 10, glyph("n").sidebearings()!!.right, "Ctrl Shift Z does it again")
            rig.key(Key.Z, ctrl = true)
            rig.key(Key.Y, ctrl = true)
            assertEquals(start + 10, glyph("n").sidebearings()!!.right, "and so does Ctrl Y")
        }

    @Test
    fun theUpAndDownArrowsChooseWhichNumberTheLeftAndRightArrowsChange() =
        withSpacePage(ufo) {
            val left = glyph("n").sidebearings()!!.left
            rig.key(Key.DirectionUp) // right -> left
            assertEquals(SpaceField.LEFT, state.field)
            rig.key(Key.DirectionRight)
            assertEquals(left + 1, glyph("n").sidebearings()!!.left)
            rig.key(Key.DirectionDown)
            rig.key(Key.DirectionDown)
            assertEquals(SpaceField.ADVANCE, state.field)
        }

    // ---- Before and After ---------------------------------------------------------------------------

    @Test
    fun beforeShowsTheRowAsItWasAndCannotBeEdited() =
        withSpacePage(ufo) {
            val deck = DeckPoints(widthDp, heightDp)
            rig.click(deck.seat("Kerning", SPACING_SEATS))
            rig.frames(40)
            val top = blockTop() - 2.0
            val box = row().dpPerUnit * font().let { it.ascent + it.descent }
            val original = region(top, top + box - HANDLE_CLEAR) + region(top + box + HANDLE_CLEAR, top + box + LABELS)
            // Kern the first pair with the arrow keys, as After.
            repeat(7) { rig.key(Key.DirectionRight) }
            rig.frames(40)
            val edited = region(top, top + box - HANDLE_CLEAR) + region(top + box + HANDLE_CLEAR, top + box + LABELS)
            assertNotEquals(original, edited, "the row changed when the kerning did")
            rig.click(deck.seat("Before", KERNING_SEATS))
            rig.frames(60)
            assertEquals(
                original,
                region(top, top + box - HANDLE_CLEAR) + region(top + box + HANDLE_CLEAR, top + box + LABELS),
                "Before is the row as it stood, pixel for pixel",
            )
            assertEquals(false, state.editable)
            val kerned = kerningNow()
            rig.key(Key.DirectionRight)
            val s = string()
            val gap = xAt((s.cells[0].right + s.cells[1].x) / 2.0)
            rig.drag(Vec2(gap, top + 30.0), Vec2(gap + 15.0, top + 30.0))
            assertEquals(kerned, kerningNow(), "nothing is edited while Before is shown")
            rig.click(deck.seat("After", KERNING_SEATS))
            rig.frames(60)
            assertEquals(
                edited,
                region(top, top + box - HANDLE_CLEAR) + region(top + box + HANDLE_CLEAR, top + box + LABELS),
                "After is the row as it is",
            )
        }

    @Test
    fun makingAnotherBeforeComparesFromNowOn() =
        withSpacePage(ufo) {
            val deck = DeckPoints(widthDp, heightDp)
            rig.click(deck.seat("Kerning", SPACING_SEATS))
            rig.frames(40)
            val top = blockTop() - 2.0
            val box = row().dpPerUnit * font().let { it.ascent + it.descent }
            repeat(5) { rig.key(Key.DirectionRight) }
            rig.frames(40)
            val edited = region(top, top + box - HANDLE_CLEAR) + region(top + box + HANDLE_CLEAR, top + box + LABELS)
            // Menu: ⋯ then the last item, Make this the Before.
            rig.click(deck.right)
            rig.frames(4)
            rig.click(menuItem(index = MENU_BEFORE))
            rig.frames(4)
            rig.click(deck.seat("Before", KERNING_SEATS))
            rig.frames(60)
            assertEquals(
                edited,
                region(top, top + box - HANDLE_CLEAR) + region(top + box + HANDLE_CLEAR, top + box + LABELS),
                "Before is now the row as it stood when it was made",
            )
        }

    // ---- the menu and its sub-views ---------------------------------------------------------------

    private fun SpacePageRig.menuItem(index: Int): Vec2 =
        Vec2(widthDp - MENU_RIGHT - MENU_WIDTH / 2.0, MENU_BOTTOM_Y - (MENU_ITEMS - 1 - index) * MENU_PITCH - MENU_PITCH / 2.0)

    @Test
    fun theMenuOpensTheCommandPaletteAndTheThreeSubViewsAndBackClosesEach() =
        withSpacePage(ufo) {
            val deck = DeckPoints(widthDp, heightDp)
            rig.click(deck.right)
            rig.frames(4)
            assertTrue(state.menuOpen)
            rig.click(menuItem(MENU_COMMAND))
            rig.frames(4)
            assertEquals(1, palette[0], "Command… is the first item and opens the palette")
            assertTrue(!state.menuOpen)
            for ((index, panel) in listOf(
                MENU_GROUPS to SpacePanel.GROUPS,
                MENU_FEA to SpacePanel.FEATURES,
                MENU_STRINGS to SpacePanel.STRINGS,
            )) {
                rig.click(deck.right)
                rig.frames(4)
                rig.click(menuItem(index))
                rig.frames(6)
                assertEquals(panel, state.panel)
                rig.click(deck.left)
                rig.frames(4)
                assertEquals(null, state.panel, "Back leaves the sub-view for the string")
            }
        }

    @Test
    fun theMenuUndoesAndRedoesWhatTheArrowsDid() =
        withSpacePage(ufo) {
            val deck = DeckPoints(widthDp, heightDp)
            val start = glyph("n").sidebearings()!!.right
            rig.key(Key.DirectionRight)
            assertEquals(start + 1, glyph("n").sidebearings()!!.right)
            rig.click(deck.right)
            rig.frames(4)
            rig.click(menuItem(MENU_UNDO))
            rig.frames(4)
            assertEquals(start, glyph("n").sidebearings()!!.right)
            rig.click(deck.right)
            rig.frames(4)
            rig.click(menuItem(MENU_REDO))
            rig.frames(4)
            assertEquals(start + 1, glyph("n").sidebearings()!!.right)
        }

    @Test
    fun theControlStringChosenIsTheOneTheRowSets() =
        withSpacePage(ufo) {
            val capitals = "HHOHOHOH"
            change { activeString = capitals }
            val s = string(capitals)
            assertEquals(8, s.cells.size)
            val top = rowTop(xAt(s.cells[0].x + s.cells[0].glyph.advanceWidth / 2.0, capitals)) + 2.0
            assertTrue(
                isInk(colourAt(xAt(s.cells[0].x + s.cells[0].glyph.advanceWidth / 2.0, capitals), top)),
                "the first H is the ink block",
            )
            assertTrue(isCanvas(colourAt(xAt(s.cells[4].x + s.cells[4].glyph.advanceWidth / 2.0, capitals), top)))
            rig.click(Vec2(xAt(s.cells[4].x + s.cells[4].glyph.advanceWidth / 2.0, capitals), top + 20.0))
            rig.frames(4)
            assertEquals(listOf(s.cells[4].glyph.name), selections)
            assertTrue(isInk(colourAt(xAt(s.cells[4].x + s.cells[4].glyph.advanceWidth / 2.0, capitals), top)))
        }

    @Test
    fun escapeDoesWhatTheLeftCircleDoes() =
        withSpacePage(ufo) {
            val deck = DeckPoints(widthDp, heightDp)
            rig.click(deck.right)
            rig.frames(4)
            assertTrue(state.menuOpen)
            rig.key(Key.Escape)
            assertTrue(!state.menuOpen, "Esc closes the menu as Back does")
            change { open(SpacePanel.GROUPS) }
            rig.key(Key.Escape)
            assertEquals(null, state.panel)
        }

    @Test
    fun theKerningScopeChipsChooseWhereAValueIsWritten() =
        withSpacePage(ufo) {
            val deck = DeckPoints(widthDp, heightDp)
            rig.click(deck.seat("Kerning", SPACING_SEATS))
            rig.frames(6)
            assertEquals(KernScope.GROUPS, state.scope)
            change { scope = KernScope.PAIR_ONLY }
            rig.key(Key.DirectionRight)
            assertEquals(1.0, kerningNow().kerning.getValue("n").getValue("n"), "this pair only writes the two glyphs, and no group pair")
            assertTrue(kerningNow().kerning["public.kern1.straight"] == null)
        }

    private companion object {
        // The grab handle sits across the foot of the ink block and is drawn only where the row can be edited, so the row is compared without it.
        const val HANDLE_CLEAR = 6.0
        const val LABELS = 34.0
        const val MENU_ITEMS = 7
        const val MENU_COMMAND = 0
        const val MENU_UNDO = 1
        const val MENU_REDO = 2
        const val MENU_GROUPS = 3
        const val MENU_FEA = 4
        const val MENU_STRINGS = 5
        const val MENU_BEFORE = 6
        const val MENU_RIGHT = 18.0
        const val MENU_WIDTH = 230.0
        const val MENU_BOTTOM_Y = 707.5
        const val MENU_PITCH = 51.5
    }
}
