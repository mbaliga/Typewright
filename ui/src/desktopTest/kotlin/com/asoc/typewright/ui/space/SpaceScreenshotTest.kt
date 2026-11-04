// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.space

import androidx.compose.ui.input.key.Key
import com.asoc.typewright.compile.FeaChecker
import com.asoc.typewright.compile.platformFeaChecker
import com.asoc.typewright.core.font.ufo.UfoKerning
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.project.GlyphRef
import com.asoc.typewright.project.ReplaceOutline
import com.asoc.typewright.project.SetKerning
import com.asoc.typewright.ui.tokens.CanvasTextures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The Space page as a person sees it, rendered over a real project (Hyle Deco's own outlines, imported from
 * `fonts/HyleDeco-Regular.ttf`) at phone and desktop width: Spacing, Kerning with a selected pair, the groups list with a note
 * about a move, the `.fea` editor with the parser's complaint, and Before with a letter that was drawn since. Each is written as a
 * PNG under `ui/build/screenshots`; what is asserted is what a unit test can say about a picture: the selection is an ink block on
 * the right letters, a sub-view takes the string's place, and the words that belong in a picture are in it.
 *
 * The `.fea` picture is drawn with the real feaLib when `TYPEWRIGHT_PYTHON` names a Python that has fontTools, and with the
 * quick shape check (and the page saying so) when it does not.
 *
 * Nothing here is a touch on a real screen: that is owner-verified on a device only.
 */
class SpaceScreenshotTest {
    private val kerning =
        UfoKerning(
            groups = SpaceTestProjects.GROUPS,
            kerning = mapOf("public.kern1.round" to mapOf("public.kern2.straight" to -24.0), "n" to mapOf("o" to -12.0)),
            // A kern feature of its own, then a block that is left open: what the page tells the person about each.
            features = "# kerning, set by hand\nfeature kern {\n    pos n o -12;\n} kern;\n\nfeature calt {\n    sub n o by H;\n",
        )
    private val ufo get() = SpaceTestProjects.hyleDeco(kerning)

    private val python: String? = System.getenv("TYPEWRIGHT_PYTHON")?.takeIf { it.isNotBlank() }
    private val parser: FeaChecker get() = if (python != null) platformFeaChecker() else NO_PARSER

    private fun SpacePageRig.centre(i: Int): Double {
        val c = string().cells[i]
        return xAt(c.x + c.glyph.advanceWidth / 2.0)
    }

    private fun SpacePageRig.top(): Double = rowTop(centre(0)) + 2.0

    /** The first pair that has a value: n then o, in the string `nnonnonon` the second and third letters. */
    private fun SpacePageRig.selectKernedPair(top: Double) {
        val s = string()
        // Junction 1 is n-o, which carries the pair exception.
        val gap = xAt((s.cells[1].right + s.cells[2].x) / 2.0)
        rig.click(Vec2(gap, top + 20.0))
        rig.frames(8)
    }

    private fun spacing(
        name: String,
        width: Int,
        height: Int,
        density: Float,
    ) = withSpacePage(ufo, "n", width, height, density) {
        val top = top()
        assertTrue(isInk(colourAt(centre(0), top)), "the glyph in hand, n, is the ink block")
        assertTrue(isCanvas(colourAt(centre(2), top)), "and nothing else is")
        rig.snapshot(name)
    }

    private fun kerning(
        name: String,
        width: Int,
        height: Int,
        density: Float,
    ) = withSpacePage(ufo, "n", width, height, density) {
        change { showMode(SpaceMode.KERNING) }
        rig.frames(30)
        val top = top()
        selectKernedPair(top)
        assertTrue(isCanvas(colourAt(centre(0), top)), "the first pair is not the selected one")
        assertTrue(isInk(colourAt(centre(1), top)) && isInk(colourAt(centre(2), top)), "the selected pair, n and o, is one ink block")
        assertTrue(isCanvas(colourAt(centre(3), top)))
        rig.snapshot(name)
    }

    /** The groups list with the first side's round group open and n added to it, which takes n out of straight and says so. */
    private fun groups(
        name: String,
        width: Int,
        height: Int,
        density: Float,
    ) = withSpacePage(ufo, "n", width, height, density) {
        val top = top()
        val first = string().cells[0]
        val left = xAt(first.x)
        val right = xAt(first.right)
        assertTrue(inkShare(left, top, right, top + 8.0) > SOLID, "the string's ink block is there before the sub-view opens")
        change { open(SpacePanel.GROUPS) }
        assertTrue(
            inkShare(left, top, right, top + 8.0) < TEXT_ONLY,
            "and the sub-view takes its place: only a line of text is where it was",
        )
        tapStartingWith("round", index = 0)
        typeInto(0, "n")
        tapExactly("Add")
        assertNotNull(visibleText("n left straight"), "the move is said where the groups are")
        rig.snapshot(name)
    }

    private fun fea(
        name: String,
        width: Int,
        height: Int,
        density: Float,
    ) = withSpacePage(ufo, "n", width, height, density, feaChecker = parser) {
        change { open(SpacePanel.FEATURES) }
        awaitVisible(if (python != null) "READ BY FONTTOOLS" else "The language itself is not parsed here")
        assertNotNull(visibleText("fail"), "the problem is listed")
        rig.snapshot(name)
    }

    @Test
    fun phoneSpacing() = spacing("space-phone-spacing", PHONE_W, PHONE_H, PHONE_DENSITY)

    @Test
    fun phoneKerning() = kerning("space-phone-kerning", PHONE_W, PHONE_H, PHONE_DENSITY)

    @Test
    fun phoneGroups() = groups("space-phone-groups", PHONE_W, PHONE_H, PHONE_DENSITY)

    @Test
    fun phoneFea() = fea("space-phone-fea", PHONE_W, PHONE_H, PHONE_DENSITY)

    @Test
    fun phoneBefore() {
        // The project as it was opened had no O drawn; it is drawn now, and the second H pair has been kerned.
        val opened = ufo.copy(glyphs = ufo.glyphs.map { if (it.name == "O") it.copy(contours = emptyList()) else it })
        withSpacePage(opened, "H", PHONE_W, PHONE_H, PHONE_DENSITY) {
            change {
                activeString = "HHOHOHOH"
                showMode(SpaceMode.KERNING)
            }
            session.execute(ReplaceOutline(GlyphRef(master, "O"), ufo.glyphs.first { it.name == "O" }.contours, "Draw O"))
            session.execute(SetKerning(master, "H", "H", -30.0))
            rig.frames(40)
            rig.click(DeckPoints(widthDp, heightDp).seat("Before", KERNING_SEATS))
            rig.frames(60)
            assertTrue(descriptions().any { it.startsWith("Before. Control string HHHHH") }, "Before holds the H that were there, and no O")
            assertNotNull(visibleText("O was not drawn when Before was made, so Before leaves it out."))
            rig.snapshot("space-phone-before")
        }
    }

    @Test
    fun desktopSpacing() = spacing("space-desktop-spacing", DESKTOP_W, DESKTOP_H, 1f)

    @Test
    fun desktopKerning() = kerning("space-desktop-kerning", DESKTOP_W, DESKTOP_H, 1f)

    @Test
    fun desktopGroups() = groups("space-desktop-groups", DESKTOP_W, DESKTOP_H, 1f)

    @Test
    fun desktopFea() = fea("space-desktop-fea", DESKTOP_W, DESKTOP_H, 1f)

    @Test
    fun theBlueprintAndBlackGroundsDrawTheSameSelectionInTheirOwnInk() {
        for (texture in listOf(CanvasTextures.BLACK, CanvasTextures.BLUEPRINT)) {
            withSpacePage(ufo, "n", PHONE_W, PHONE_H, PHONE_DENSITY, texture) {
                val top = top()
                assertTrue(isInk(colourAt(centre(0), top)), "${texture.id}: the selection is an ink block in that ground's ink")
                assertTrue(isCanvas(colourAt(centre(3), top)))
                rig.key(Key.DirectionRight)
                rig.frames(4)
                assertEquals(1, session.history.value.undoLabels.size)
            }
        }
    }

    private companion object {
        const val SOLID = 0.8
        const val TEXT_ONLY = 0.4
    }
}
