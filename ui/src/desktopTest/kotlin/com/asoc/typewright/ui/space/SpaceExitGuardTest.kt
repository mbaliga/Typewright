// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.space

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import com.asoc.typewright.core.font.ufo.UfoKerning
import com.asoc.typewright.ui.deck.DeckSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unsaved `.fea` text is never lost silently. Every way out of the editor that the Space page can see - the ⋯ menu's other sub-views,
 * a section on the arc, the Map, the seats, the left circle, Esc - asks first; the ways it cannot see (the command palette, the page being
 * covered by another screen) leave the text where it was and the page shows it again, marked as kept and not saved.
 */
class SpaceExitGuardTest {
    private val ufo get() = SpaceTestProjects.hyleDeco(UfoKerning(groups = SpaceTestProjects.GROUPS))

    private fun SpacePageRig.typeUnsaved(): String {
        change { open(SpacePanel.FEATURES) }
        val typed = "# not saved yet\n"
        typeInto(0, typed)
        assertTrue(state.featuresDirty, "the editor knows its text is not saved")
        return typed
    }

    private fun SpacePageRig.openMenu() {
        rig.click(DeckPoints(widthDp, heightDp).right)
        rig.frames(4)
        assertTrue(state.menuOpen)
    }

    private fun SpacePageRig.assertAsking(typed: String) {
        assertTrue(state.confirmingDiscard, "the page asks")
        assertEquals(SpacePanel.FEATURES, state.panel, "and is still in the editor")
        assertEquals(typed, state.featuresDraft, "with the text untouched")
        assertNotNull(visibleText("Throw away the changes to features.fea"), "in words the person can read")
    }

    @Test
    fun theMenusOtherSubViewsAskBeforeTheTextIsThrownAwayAndOpenOnlyIfTheyAreTold() {
        for ((entry, panel) in listOf("Groups" to SpacePanel.GROUPS, "Control strings" to SpacePanel.STRINGS)) {
            withSpacePage(ufo) {
                val typed = typeUnsaved()
                openMenu()
                tapStartingWith(entry)
                assertAsking(typed)

                tapExactly("Keep editing")
                assertFalse(state.confirmingDiscard)
                assertEquals(SpacePanel.FEATURES, state.panel, "$entry: keeping on stays in the editor")
                assertEquals(typed, state.featuresDraft, "$entry: with every character")
                assertEquals(typed, fieldValues()[0])

                openMenu()
                tapStartingWith(entry)
                tapExactly("Throw away")
                assertEquals(panel, state.panel, "$entry: the sub-view that was asked for opens")
                assertNull(state.featuresDraft, "$entry: and the text is gone")
                assertFalse(state.confirmingDiscard)
            }
        }
    }

    @Test
    fun aSectionOnTheArcAsksFirstAndIsGoneToOnlyOnceTheTextIsThrownAway() {
        val router = RouterLog()
        withSpacePage(ufo, router = router) {
            val typed = typeUnsaved()
            tapExactly("Draw")
            assertAsking(typed)
            assertEquals(emptyList(), router.sections, "the router has not been asked to go anywhere")

            tapExactly("Keep editing")
            assertEquals(emptyList(), router.sections)
            assertEquals(typed, state.featuresDraft)

            tapExactly("Check")
            tapExactly("Throw away")
            assertEquals(listOf(DeckSection.CHECK), router.sections, "the section that was last asked for, once")
            assertNull(state.featuresDraft)
        }
    }

    @Test
    fun theMapAsksFirstToo() {
        val router = RouterLog()
        withSpacePage(ufo, router = router) {
            val typed = typeUnsaved()
            longPressExactly("Draw")
            assertAsking(typed)
            assertEquals(0, router.maps)
            tapExactly("Throw away")
            assertEquals(1, router.maps)
        }
    }

    @Test
    fun withNothingUnsavedASectionIsGoneToAtOnce() {
        val router = RouterLog()
        withSpacePage(ufo, router = router) {
            change { open(SpacePanel.FEATURES) }
            tapExactly("Draw")
            assertFalse(state.confirmingDiscard)
            assertEquals(listOf(DeckSection.DRAW), router.sections)
        }
    }

    @Test
    fun theLeftCircleEscAndTheSeatsStillAsk() {
        withSpacePage(ufo) {
            val typed = typeUnsaved()
            val deck = DeckPoints(widthDp, heightDp)
            rig.click(deck.left)
            rig.frames(4)
            assertAsking(typed)
            rig.key(Key.Escape)
            assertFalse(state.confirmingDiscard, "Esc puts the question away")
            rig.key(Key.Escape)
            assertAsking(typed)
            tapExactly("Keep editing")
            rig.click(deck.seat("Kerning", SPACING_SEATS))
            rig.frames(4)
            assertAsking(typed)
            assertEquals(SpaceMode.SPACING, state.mode)
            tapExactly("Throw away")
            assertEquals(SpaceMode.KERNING, state.mode, "the seat that was tapped applies once the text is let go")
            assertNull(state.panel)
        }
    }

    @Test
    fun theCommandPaletteCannotBeStoppedSoTheTextIsKeptInsteadAndTheMenuSaysNothingAboutLosingIt() {
        withSpacePage(ufo) {
            val typed = typeUnsaved()
            openMenu()
            tapStartingWith("Command")
            assertEquals(1, palette[0], "the palette was asked for")
            assertFalse(state.confirmingDiscard, "it is not stopped, since what it does next is not Space's to say")
            assertEquals(typed, state.featuresDraft, "and the text is where it was")
            assertEquals(SpacePanel.FEATURES, state.panel)
        }
    }

    @Test
    fun aPageLeftWithUnsavedTextShowsItAgainMarkedAsKeptAndNotSaved() {
        val active = mutableStateOf(true)
        withSpacePage(ufo, active = active) {
            val typed = typeUnsaved()
            assertNull(visibleText("It was kept while this project is open"), "while the person is typing it is just their text")

            active.value = false
            rig.frames(8)
            assertNull(visibleText("features.fea"), "the page is not on screen while another screen covers it")
            assertEquals(typed, state.featuresDraft, "but the text is held")

            active.value = true
            rig.frames(8)
            assertEquals(SpacePanel.FEATURES, state.panel, "Space opens on the editor it was left in")
            assertEquals(typed, fieldValues()[0], "with the text that was typed")
            assertTrue(state.draftKept)
            assertNotNull(visibleText("It was kept while this project is open"), "and says it was kept")
            assertNotNull(visibleText("It is not in the project until you press Save"), "and that it is not saved")
            assertNull(
                session.state.value.font
                    .master(master)!!
                    .ufo.kerningInfo.features,
                "which the project confirms",
            )

            tapExactly("Save")
            assertEquals(
                typed,
                session.state.value.font
                    .master(master)!!
                    .ufo.kerningInfo.features,
            )
            assertNull(visibleText("It was kept while this project is open"), "once saved there is nothing left to say")
        }
    }
}
