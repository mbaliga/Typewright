// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.space

import androidx.compose.ui.input.key.Key
import com.asoc.typewright.core.font.ufo.UfoKerning
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.ui.tokens.CanvasTextures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Space page as a person sees it, rendered over a real project (Hyle Deco's own outlines, imported from
 * `fonts/HyleDeco-Regular.ttf`) at phone and desktop width: Spacing, Kerning with a selected pair, the groups list and
 * the `.fea` editor. Each is written as a PNG under `ui/build/screenshots`; what is asserted is what a unit test can
 * say about a picture: the selection is an ink block on the right letters, and a sub-view takes the string's place.
 *
 * Nothing here is a touch on a real screen: that is owner-verified on a device only.
 */
class SpaceScreenshotTest {
    private val kerning =
        UfoKerning(
            groups = SpaceTestProjects.GROUPS,
            kerning = mapOf("public.kern1.round" to mapOf("public.kern2.straight" to -24.0), "n" to mapOf("o" to -12.0)),
            // A kern feature of its own, with a block left open: what the page tells the person about each.
            features = "# kerning, set by hand\nfeature kern {\n    pos n o -12;\n} kern;\n\nfeature calt {\n    sub n o by n.alt;\n",
        )
    private val ufo get() = SpaceTestProjects.hyleDeco(kerning)

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

    private fun panel(
        name: String,
        which: SpacePanel,
        width: Int,
        height: Int,
        density: Float,
    ) = withSpacePage(ufo, "n", width, height, density) {
        val top = top()
        val first = string().cells[0]
        val left = xAt(first.x)
        val right = xAt(first.right)
        assertTrue(inkShare(left, top, right, top + 8.0) > SOLID, "the string's ink block is there before the sub-view opens")
        change { open(which) }
        assertTrue(
            inkShare(left, top, right, top + 8.0) < TEXT_ONLY,
            "and the sub-view takes its place: only a line of text is where it was",
        )
        if (which == SpacePanel.GROUPS) {
            rig.click(Vec2(widthDp / 2.0, if (widthDp >= WIDE_DP) GROUP_ROW_Y_WIDE else GROUP_ROW_Y))
            rig.frames(6)
        }
        rig.snapshot(name)
    }

    @Test
    fun phoneSpacing() = spacing("space-phone-spacing", PHONE_W, PHONE_H, PHONE_DENSITY)

    @Test
    fun phoneKerning() = kerning("space-phone-kerning", PHONE_W, PHONE_H, PHONE_DENSITY)

    @Test
    fun phoneGroups() = panel("space-phone-groups", SpacePanel.GROUPS, PHONE_W, PHONE_H, PHONE_DENSITY)

    @Test
    fun phoneFea() = panel("space-phone-fea", SpacePanel.FEATURES, PHONE_W, PHONE_H, PHONE_DENSITY)

    @Test
    fun desktopSpacing() = spacing("space-desktop-spacing", DESKTOP_W, DESKTOP_H, 1f)

    @Test
    fun desktopKerning() = kerning("space-desktop-kerning", DESKTOP_W, DESKTOP_H, 1f)

    @Test
    fun desktopGroups() = panel("space-desktop-groups", SpacePanel.GROUPS, DESKTOP_W, DESKTOP_H, 1f)

    @Test
    fun desktopFea() = panel("space-desktop-fea", SpacePanel.FEATURES, DESKTOP_W, DESKTOP_H, 1f)

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
        /** The first group row of the list at phone width, in dp: under the intro and the first side's label. */
        const val SOLID = 0.8
        const val TEXT_ONLY = 0.4
        const val GROUP_ROW_Y = 215.0
        const val GROUP_ROW_Y_WIDE = 170.0
        const val WIDE_DP = 900.0
    }
}
