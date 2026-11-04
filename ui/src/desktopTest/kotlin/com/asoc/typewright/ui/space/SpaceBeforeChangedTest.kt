// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.space

import androidx.compose.ui.input.key.Key
import com.asoc.typewright.core.font.ufo.UfoKerning
import com.asoc.typewright.project.GlyphRef
import com.asoc.typewright.project.ReplaceOutline
import com.asoc.typewright.project.SetKerning
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Before is the string as it stood. When the letters drawn at the Before are not the letters drawn now - the usual way a font is made: draw
 * some letters, space them, draw more - the Before picture is still the string as it was, the letters it cannot compare are named, and
 * it is never the After picture under another name.
 */
class SpaceBeforeChangedTest {
    private val full = SpaceTestProjects.hyleDeco(UfoKerning(groups = SpaceTestProjects.GROUPS))
    private val capitals = "HHOHOHOH"

    /** The project as it was opened: [undrawn] letters are in the font but nothing is drawn in them yet. */
    private fun opened(vararg undrawn: String) =
        full.copy(glyphs = full.glyphs.map { if (it.name in undrawn) it.copy(contours = emptyList()) else it })

    private fun SpacePageRig.draw(name: String) {
        val contours = full.glyphs.first { it.name == name }.contours
        session.execute(ReplaceOutline(GlyphRef(master, name), contours, "Draw $name"))
        rig.frames(8)
    }

    /** Where the row is, found while it is the string as it stands (with its selected pair an ink block): its top and the height of letters and numbers. */
    private class RowBox(
        val top: Double,
        val height: Double,
    )

    private fun SpacePageRig.locateRow(text: String): RowBox {
        val first = string(text).cells[0]
        val top = rowTop(xAt(first.x + first.glyph.advanceWidth / 2.0, text))
        return RowBox(top, row(text).dpPerUnit * font().let { it.ascent + it.descent } + LABELS)
    }

    /** The pixels of the row's letters and numbers, across the whole width. */
    private fun SpacePageRig.pixels(box: RowBox): List<Int> = region(box.top, box.top + box.height)

    private fun SpacePageRig.description(): String = descriptions().first { "Control string" in it }

    private fun SpacePageRig.inKerning() =
        change {
            activeString = capitals
            showMode(SpaceMode.KERNING)
        }

    @Test
    fun aLetterDrawnAfterTheBeforeIsNotInTheBeforePictureAndTheBeforeIsNotTheAfterPicture() =
        withSpacePage(opened("O")) {
            inKerning()
            // O is drawn only now, after the Before was made.
            draw("O")
            rig.frames(40)
            val box = locateRow(capitals)
            val afterPixels = pixels(box)
            assertTrue(description().startsWith("Control string HHOHOHOH"), description())

            val deck = DeckPoints(widthDp, heightDp)
            rig.click(deck.seat("Before", KERNING_SEATS))
            rig.frames(60)
            val beforePixels = pixels(box)

            assertTrue(
                description().startsWith("Before. Control string HHHHH"),
                "the Before picture holds the five H and no O: ${description()}",
            )
            assertNotEquals(afterPixels, beforePixels, "Before is not the picture of After")
            assertNotNull(
                visibleText("O was not drawn when Before was made, so Before leaves it out."),
                "the letter that cannot be compared is named",
            )
            assertEquals(false, state.editable)

            rig.click(deck.seat("After", KERNING_SEATS))
            rig.frames(60)
            assertTrue(description().startsWith("Control string HHOHOHOH"), description())
            assertEquals(afterPixels, pixels(box), "After is the string as it is, pixel for pixel, as it was before Before was looked at")
            assertNull(visibleText("was not drawn when Before was made"), "nothing is said in After")
        }

    @Test
    fun theLettersTheBeforeHasSlideToWhereTheyStandNow() =
        withSpacePage(opened("O")) {
            inKerning()
            draw("O")
            // Kern the second H-H pair now; the Before had no kerning there.
            session.execute(SetKerning(master, "H", "H", -40.0))
            rig.frames(40)
            val box = locateRow(capitals)
            val deck = DeckPoints(widthDp, heightDp)
            val afterPixels = pixels(box)
            assertNotNull(visibleText("-40"), "After says the pair is kerned -40, in the row and in the inspector")
            rig.click(deck.seat("Before", KERNING_SEATS))
            rig.frames(60)
            val beforePixels = pixels(box)
            assertNotEquals(afterPixels, beforePixels)
            assertNull(
                visibleText("-40"),
                "Before says nothing of the kerning the pair has now, anywhere on the page, the inspector included",
            )
            // Part of the way the picture is neither one: a letter in both is part of the way.
            rig.click(deck.seat("After", KERNING_SEATS))
            rig.frames(4)
            val between = pixels(box)
            assertNotEquals(afterPixels, between, "a few frames into the slide it has not arrived")
            assertNotEquals(beforePixels, between)
            rig.frames(60)
            assertEquals(afterPixels, pixels(box))
        }

    @Test
    fun whenNothingWasDrawnAtTheBeforeTheBeforePictureIsEmptyAndSaysWhy() =
        withSpacePage(opened("H", "O")) {
            inKerning()
            draw("H")
            draw("O")
            rig.frames(40)
            assertTrue(description().startsWith("Control string HHOHOHOH"), description())
            rig.click(DeckPoints(widthDp, heightDp).seat("Before", KERNING_SEATS))
            rig.frames(60)
            assertTrue(description().startsWith("Before. Control string ,"), "Before holds no letter at all: ${description()}")
            assertNotNull(visibleText("H and O were not drawn when Before was made, so Before leaves them out."))
        }

    @Test
    fun aStringWhoseLettersAreAllThereAtBothTimesSaysNothingIsLeftOut() =
        withSpacePage(opened()) {
            inKerning()
            rig.key(Key.DirectionRight)
            rig.click(DeckPoints(widthDp, heightDp).seat("Before", KERNING_SEATS))
            rig.frames(60)
            assertTrue(description().startsWith("Before. Control string HHOHOHOH"), description())
            assertNull(visibleText("was not drawn when Before was made"))
            assertNull(visibleText("is not drawn now"))
            assertFalse(state.editable)
        }

    private companion object {
        const val LABELS = 34.0
    }
}
