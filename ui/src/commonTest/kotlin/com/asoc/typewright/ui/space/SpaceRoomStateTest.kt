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
