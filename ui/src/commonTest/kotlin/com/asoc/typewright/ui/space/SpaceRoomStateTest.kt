// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.space

import com.asoc.typewright.ui.deck.DeckLeft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [SpaceRoomState]: what the deck's circles and seats do on the Space page, readable without a window. */
class SpaceRoomStateTest {
    private fun labels(state: SpaceRoomState) = state.toggles().map { it.label }

    private fun pressed(state: SpaceRoomState) = state.toggles().filter { it.on }.map { it.label }

    @Test
    fun spacingShowsTwoSeatsAndKerningAddsBeforeAndAfterAsASecondGroup() {
        val state = SpaceRoomState()
        assertEquals(listOf("Spacing", "Kerning"), labels(state))
        assertEquals(listOf("Spacing"), pressed(state))
        state.onToggle(SpaceRoomState.SEAT_KERNING)
        assertEquals(listOf("Spacing", "Kerning", "Before", "After"), labels(state))
        assertEquals(listOf("Kerning", "After"), pressed(state))
        assertEquals(listOf(false, false, true, false), state.toggles().map { it.groupStart }, "Before starts the second group")
    }

    @Test
    fun beforeShowsOnlyInKerningAndLeavingKerningShowsTheStringAsItIs() {
        val state = SpaceRoomState()
        state.onToggle(SpaceRoomState.SEAT_KERNING)
        state.onToggle(SpaceRoomState.SEAT_BEFORE)
        assertFalse(state.showAfter)
        assertFalse(state.editable, "nothing is edited while the Before is shown")
        assertEquals(listOf("Kerning", "Before"), pressed(state))
        state.onToggle(SpaceRoomState.SEAT_SPACING)
        assertTrue(state.showAfter, "back in Spacing the string is the current one, so edits land on what is seen")
        assertTrue(state.editable)
        state.showAfter(false)
        assertTrue(state.showAfter, "Before cannot be chosen outside Kerning")
    }

    @Test
    fun theArrowsChangeTheKerningInKerningAndAMetricInSpacing() {
        val state = SpaceRoomState()
        assertEquals(SpaceField.RIGHT, state.field)
        state.showMode(SpaceMode.KERNING)
        assertEquals(SpaceField.KERN, state.field)
        state.showMode(SpaceMode.SPACING)
        assertEquals(SpaceField.RIGHT, state.field)
        state.field = SpaceField.LEFT
        state.showMode(SpaceMode.KERNING)
        state.showMode(SpaceMode.SPACING)
        assertEquals(SpaceField.RIGHT, state.field, "kern is not a spacing field, so it falls back to right")
    }

    @Test
    fun theLeftCircleLeavesWhenNothingIsOpenAndOtherwiseClosesWhatIs() {
        val state = SpaceRoomState()
        var left = 0
        assertEquals(DeckLeft.BACK, state.left())
        state.menuOpen = true
        state.onLeft { left++ }
        assertFalse(state.menuOpen)
        assertEquals(0, left, "a menu closes before the page is left")
        state.open(SpacePanel.GROUPS)
        assertEquals(DeckLeft.BACK, state.left(), "a sub-view is left, not discarded")
        state.onLeft { left++ }
        assertEquals(null, state.panel)
        assertEquals(0, left)
        state.onLeft { left++ }
        assertEquals(1, left)
    }

    @Test
    fun aValueBeingTypedMakesTheLeftCircleCloseAndEscPutsItAway() {
        val state = SpaceRoomState()
        state.editingField = true
        assertEquals(DeckLeft.CLOSE, state.left())
        val tick = state.cancelEditTick
        var left = 0
        state.onLeft { left++ }
        assertEquals(tick + 1, state.cancelEditTick)
        assertEquals(0, left)
    }

    @Test
    fun unsavedFeaTextMakesTheLeftCircleCloseAndAsksBeforeItIsLost() {
        val state = SpaceRoomState()
        state.open(SpacePanel.FEATURES)
        assertEquals(DeckLeft.BACK, state.left(), "clean text can simply be left")
        state.featuresDirty = true
        assertEquals(DeckLeft.CLOSE, state.left())
        state.onLeft { error("must not leave the page") }
        assertTrue(state.confirmingDiscard)
        assertEquals(SpacePanel.FEATURES, state.panel, "still open until the person agrees")
        state.onLeft { error("must not leave the page") }
        assertFalse(state.confirmingDiscard, "a second Close puts the question away")
        state.confirmingDiscard = true
        state.closePanel()
        assertEquals(null, state.panel)
        assertFalse(state.featuresDirty)
        assertFalse(state.confirmingDiscard)
        assertEquals(DeckLeft.BACK, state.left())
    }

    @Test
    fun aSeatDoesNotSilentlyThrowAwayUnsavedFeaText() {
        val state = SpaceRoomState()
        state.open(SpacePanel.FEATURES)
        state.featuresDirty = true
        state.onToggle(SpaceRoomState.SEAT_KERNING)
        assertEquals(SpaceMode.SPACING, state.mode)
        assertTrue(state.confirmingDiscard)
        state.closePanel()
        state.onToggle(SpaceRoomState.SEAT_KERNING)
        assertEquals(SpaceMode.KERNING, state.mode)
        assertEquals(null, state.panel)
    }

    private fun dirtyFea(state: SpaceRoomState = SpaceRoomState()): SpaceRoomState {
        state.open(SpacePanel.FEATURES)
        state.featuresDraft = "feature kern {"
        state.featuresDirty = true
        return state
    }

    @Test
    fun everyWayOutOfAnUnsavedFeaEditorAsksFirstAndGoesOnlyIfThePersonAgrees() {
        // The ⋯ menu's other sub-views, a section on the arc and the Map all leave through guardExit; so do the seats and the left circle.
        val exits: List<Pair<String, (SpaceRoomState, () -> Unit) -> Unit>> =
            listOf(
                "menu: Groups" to { s, _ -> s.requestPanel(SpacePanel.GROUPS) },
                "menu: Control strings" to { s, _ -> s.requestPanel(SpacePanel.STRINGS) },
                "section or Map" to { s, go -> s.guardExit(go) },
                "a seat" to { s, _ -> s.onToggle(SpaceRoomState.SEAT_KERNING) },
                "the left circle" to { s, go -> s.onLeft(go) },
            )
        for ((way, leave) in exits) {
            val state = dirtyFea()
            var gone = 0
            leave(state) { gone++ }
            assertTrue(state.confirmingDiscard, "$way asks")
            assertEquals(SpacePanel.FEATURES, state.panel, "$way: still in the editor while it asks")
            assertEquals("feature kern {", state.featuresDraft, "$way: the text is untouched while it asks")
            assertEquals(0, gone, "$way: nothing happened yet")

            state.cancelDiscard()
            assertFalse(state.confirmingDiscard)
            assertEquals("feature kern {", state.featuresDraft, "$way: keep editing keeps the text")
            assertEquals(0, gone)

            leave(state) { gone++ }
            state.confirmDiscard()
            assertEquals(null, state.featuresDraft, "$way: the text goes when the person says so")
            assertFalse(state.confirmingDiscard)
            assertFalse(state.featuresDirty)
        }
    }

    @Test
    fun agreeingToThrowTheTextAwayDoesWhatWasAskedFor() {
        val toGroups = dirtyFea()
        toGroups.requestPanel(SpacePanel.GROUPS)
        toGroups.confirmDiscard()
        assertEquals(SpacePanel.GROUPS, toGroups.panel, "the menu entry that was chosen opens")

        val toKerning = dirtyFea()
        toKerning.onToggle(SpaceRoomState.SEAT_KERNING)
        toKerning.confirmDiscard()
        assertEquals(SpaceMode.KERNING, toKerning.mode, "the seat that was chosen applies, without a second tap")
        assertEquals(null, toKerning.panel)

        val toSection = dirtyFea()
        var went = 0
        toSection.guardExit { went++ }
        assertEquals(0, went)
        toSection.confirmDiscard()
        assertEquals(1, went, "the section is gone to once, after the text is thrown away")
        toSection.confirmDiscard()
        assertEquals(1, went, "and not again")

        val leftCircle = dirtyFea()
        leftCircle.onLeft { error("the left circle closes the editor, it does not leave the page") }
        leftCircle.confirmDiscard()
        assertEquals(null, leftCircle.panel)
    }

    @Test
    fun withNothingUnsavedEveryWayOutGoesAtOnce() {
        val state = SpaceRoomState()
        var went = 0
        state.guardExit { went++ }
        assertEquals(1, went)
        state.requestPanel(SpacePanel.GROUPS)
        assertEquals(SpacePanel.GROUPS, state.panel)
        state.open(SpacePanel.FEATURES)
        state.featuresDraft = "same"
        state.featuresDirty = false
        state.requestPanel(SpacePanel.STRINGS)
        assertEquals(SpacePanel.STRINGS, state.panel, "text the person put back is not unsaved")
        assertFalse(state.confirmingDiscard)
        assertEquals(
            null,
            state.featuresDraft,
            "and it is not kept as a draft either, so it cannot outlive a change to what the project holds",
        )
    }

    @Test
    fun askingForTheEditorAlreadyOpenIsNotAQuestion() {
        val state = dirtyFea()
        state.menuOpen = true
        state.requestPanel(SpacePanel.FEATURES)
        assertFalse(state.confirmingDiscard)
        assertFalse(state.menuOpen)
        assertEquals("feature kern {", state.featuresDraft)
    }

    @Test
    fun theDraftOutlivesTheEditorAndIsSaidToHaveBeenKeptWhenThePageIsOpenedAgain() {
        val state = dirtyFea()
        assertFalse(state.draftKept, "while the person is in the editor it is just their text")
        // Left by a way out the page cannot stop (the palette, a swipe to Draw): nothing runs, and the state is what it was.
        state.reopened()
        assertTrue(state.draftKept)
        assertEquals("feature kern {", state.featuresDraft)
        assertEquals(SpacePanel.FEATURES, state.panel, "Space opens on the editor it was left in")
        state.discardDraft()
        assertFalse(state.draftKept)
        assertEquals(null, state.featuresDraft)

        val clean = SpaceRoomState()
        clean.reopened()
        assertFalse(clean.draftKept, "nothing was kept, so nothing is said")
    }

    @Test
    fun leavingTheEditorForgetsItsNoticeAndItsDraft() {
        val state = dirtyFea()
        state.notice = SpaceNotice("saved")
        state.closePanel()
        assertEquals(null, state.notice)
        assertEquals(null, state.featuresDraft)
        assertFalse(state.draftKept)
    }

    @Test
    fun openingASubViewClosesTheMenuAndClearsTheLastNotice() {
        val state = SpaceRoomState()
        state.menuOpen = true
        state.notice = SpaceNotice("done")
        state.open(SpacePanel.STRINGS)
        assertFalse(state.menuOpen)
        assertEquals(null, state.notice)
        assertEquals(SpacePanel.STRINGS, state.panel)
    }
}
