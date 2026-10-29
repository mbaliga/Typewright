// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.space

import androidx.compose.ui.input.key.Key
import com.asoc.typewright.core.font.ufo.UfoKerning
import com.asoc.typewright.core.geometry.sidebearings
import com.asoc.typewright.project.GlyphRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The keyboard rule on Space: ← and → change the selected number only while there is one for them to change; otherwise they are left
 * for the sheet, so the keyboard still moves between sections. The test sends the key and reads whether the page took it.
 */
class SpaceKeysTest {
    private val ufo get() = SpaceTestProjects.hyleDeco(UfoKerning(groups = SpaceTestProjects.GROUPS))

    private fun SpacePageRig.right(): Int =
        session.state.value.font
            .glyph(GlyphRef(master, "n"))!!
            .sidebearings()!!
            .right

    @Test
    fun withALetterSelectedTheArrowsAreThePagesAndNudgeIt() =
        withSpacePage(ufo) {
            val start = right()
            assertTrue(pressKey(Key.DirectionRight), "the page took the key")
            assertEquals(start + 1, right())
            assertTrue(pressKey(Key.DirectionLeft))
            assertEquals(start, right())
        }

    @Test
    fun withControlHeldTheArrowsAreLeftForTheSheetToSwitchSectionsAndNothingIsEdited() =
        withSpacePage(ufo) {
            val start = right()
            assertFalse(pressKey(Key.DirectionRight, ctrl = true), "Ctrl or Cmd with an arrow is the sheet's")
            assertFalse(pressKey(Key.DirectionLeft, ctrl = true))
            assertEquals(start, right())
            assertEquals(0, session.history.value.undoLabels.size)
        }

    @Test
    fun whileASubViewIsOpenThereIsNothingToNudgeSoTheArrowsAreLeftForTheSheet() =
        withSpacePage(ufo) {
            val start = right()
            for (panel in SpacePanel.entries) {
                change { open(panel) }
                assertFalse(pressKey(Key.DirectionRight), "$panel: the page has no number to change")
                assertFalse(pressKey(Key.DirectionLeft))
            }
            assertEquals(start, right())
        }

    @Test
    fun inKerningWithNoPairToKernTheArrowsAreLeftForTheSheet() =
        withSpacePage(ufo) {
            // "n o" has a space between its letters, so there is no pair in it.
            val editor = SpaceEditor(session, master)
            editor.setPreferences(ControlStrings.add(editor.preferences(), "n o").let { (it as ControlStrings.Add.Added).preferences })
            change {
                activeString = "n o"
                showMode(SpaceMode.KERNING)
            }
            assertFalse(pressKey(Key.DirectionRight), "no pair is selected, so the key is not the page's")
            assertEquals(0, session.history.value.undoLabels.size)
            // The same keys in Spacing, on the same string, are the page's again.
            change { showMode(SpaceMode.SPACING) }
            assertTrue(pressKey(Key.DirectionRight))
        }

    @Test
    fun inKerningWithAPairSelectedTheArrowsKernIt() =
        withSpacePage(ufo) {
            change { showMode(SpaceMode.KERNING) }
            assertTrue(pressKey(Key.DirectionRight))
            assertEquals(1, session.history.value.undoLabels.size)
        }

    @Test
    fun underBeforeTheArrowsAreStillThePagesAndSayItIsALookBackWithoutEditing() =
        withSpacePage(ufo) {
            change { showMode(SpaceMode.KERNING) }
            change { showAfter(false) }
            assertTrue(pressKey(Key.DirectionRight))
            assertEquals(0, session.history.value.undoLabels.size)
            assertEquals("Before is a look back. Switch to After to change anything.", state.notice?.text)
        }

    @Test
    fun theOtherKeysAreUnchanged() =
        withSpacePage(ufo) {
            assertTrue(pressKey(Key.Tab), "Tab steps along the letters")
            assertFalse(pressKey(Key.A), "a letter is nobody's")
        }
}
