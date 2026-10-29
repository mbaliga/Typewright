// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.space

import androidx.compose.ui.input.key.Key
import com.asoc.typewright.core.font.ufo.UfoKerning
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A control string longer than the row has room for: the row is shown from the place along it that keeps the selected letter (or pair) in view,
 * so what the inspector says it is editing is always on screen. Tab and the pointer reach every letter.
 */
class SpaceLongStringTest {
    private val ufo = SpaceTestProjects.hyleDeco(UfoKerning(groups = SpaceTestProjects.GROUPS))

    /** Thirty different drawn letters, so the letters the row shows say which part of the string it is showing. */
    private val letters: String =
        ufo.glyphs
            .filter { it.contours.isNotEmpty() && it.unicodes.isNotEmpty() }
            .map { it.unicodes.first().toChar() }
            .filter { it.isLetter() && it.code < ASCII }
            .distinct()
            .take(THIRTY)
            .joinToString("")

    init {
        require(letters.length == THIRTY) { "Hyle Deco has fewer than $THIRTY drawn letters: $letters" }
    }

    private fun SpacePageRig.useLongString() {
        val editor = SpaceEditor(session, master)
        val added = ControlStrings.add(editor.preferences(), letters) as ControlStrings.Add.Added
        editor.setPreferences(added.preferences)
        change { activeString = letters }
    }

    /** The letters the row draws, as a screen reader is told them. */
    private fun SpacePageRig.drawn(): String =
        descriptions()
            .first { it.contains("Control string") }
            .substringAfter("Control string ")
            .substringBefore(",")

    @Test
    fun thePhoneShowsOnlyPartOfAThirtyLetterStringButAlwaysThePartWithTheSelection() =
        withSpacePage(ufo) {
            useLongString()
            change { selected = 0 }
            val head = drawn()
            assertTrue(head.startsWith(letters.take(5)), "the row starts at the first letter: $head")
            assertTrue(head.length in 5 until THIRTY, "the string does not fit the row: ${head.length} of $THIRTY letters are drawn")
            assertTrue(letters.last() !in head)

            change { selected = THIRTY - 1 }
            val tail = drawn()
            assertTrue(letters.last() in tail, "the last letter is selected, so the row has moved along to it: $tail")
            assertTrue(letters.first() !in tail, "and the first has gone off the left: $tail")
            assertTrue(tail.endsWith(letters.takeLast(3)), tail)
        }

    @Test
    fun tabbingAlongTheWholeStringNeverSelectsALetterThatIsNotDrawn() =
        withSpacePage(ufo) {
            useLongString()
            change { selected = 0 }
            repeat(THIRTY - 1) { step ->
                assertTrue(pressKey(Key.Tab))
                rig.frames(2)
                val letter = letters[step + 1]
                assertEquals(step + 1, state.selected)
                assertTrue(letter in drawn(), "after $step Tab, the selected $letter is drawn: ${drawn()}")
            }
            // And back with Shift Tab - the row follows the other way.
            change { selected = THIRTY - 1 }
            val last = drawn()
            assertTrue(letters.last() in last)
            change { selected = 0 }
            assertTrue(letters.first() in drawn())
        }

    @Test
    fun inKerningBothLettersOfTheSelectedPairAreDrawn() =
        withSpacePage(ufo) {
            useLongString()
            change { showMode(SpaceMode.KERNING) }
            for (index in listOf(0, 10, 18, 25, THIRTY - 2)) {
                change { selected = index }
                val shown = drawn()
                assertTrue(
                    letters[index] in shown && letters[index + 1] in shown,
                    "pair ${letters[index]}${letters[index + 1]} is drawn: $shown",
                )
            }
        }

    @Test
    fun theRowStaysWhereItIsWhileTheSelectionMovesAroundInsideIt() =
        withSpacePage(ufo) {
            useLongString()
            change { selected = THIRTY - 1 }
            val tail = drawn()
            change { selected = THIRTY - 3 }
            assertEquals(tail, drawn(), "the selection is still in view, so the row has not moved")
            assertNotNull(tail)
        }

    private companion object {
        const val THIRTY = 30
        const val ASCII = 128
    }
}
