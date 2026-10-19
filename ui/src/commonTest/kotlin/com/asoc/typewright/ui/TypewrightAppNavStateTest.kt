// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui

import com.asoc.typewright.brief.Door
import com.asoc.typewright.ui.deck.DeckSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Portable (desktop + wasmJs/browser) unit tests for [TypewrightApp]'s own minimal entry-point
 * state, [TypewrightAppNavState]. A plain class holding two Compose `mutableStateOf` fields,
 * exactly like [com.asoc.typewright.ui.puck.PuckUiState] and [com.asoc.typewright.ui.learn.
 * LearnScreenUiState] -- constructible and readable directly, no Compose test rule or simulated
 * pointer event needed. Asserting on [TypewrightAppNavState.openLearn]/[TypewrightAppNavState.
 * closeLearn]/[TypewrightAppNavState.openWorkbook]/[TypewrightAppNavState.closeWorkbook] here is a
 * real test of the exact mechanism each entry button and each screen's own back button call, not a
 * stand-in for it.
 */
class TypewrightAppNavStateTest {
    @Test
    fun startsClosedByDefault() {
        val state = TypewrightAppNavState()
        assertFalse(state.showLearn)
        assertFalse(state.showWorkbook)
    }

    @Test
    fun canStartOpenWhenAskedTo() {
        assertTrue(TypewrightAppNavState(initialShowLearn = true).showLearn)
        assertTrue(TypewrightAppNavState(initialShowWorkbook = true).showWorkbook)
    }

    @Test
    fun openLearnSetsShowLearnTrue() {
        val state = TypewrightAppNavState()
        state.openLearn()
        assertTrue(state.showLearn)
    }

    @Test
    fun closeLearnSetsShowLearnFalse() {
        val state = TypewrightAppNavState(initialShowLearn = true)
        state.closeLearn()
        assertFalse(state.showLearn)
    }

    @Test
    fun openThenCloseRoundTrips() {
        val state = TypewrightAppNavState()
        state.openLearn()
        assertTrue(state.showLearn)
        state.closeLearn()
        assertFalse(state.showLearn)
    }

    @Test
    fun openLearnIsIdempotent() {
        val state = TypewrightAppNavState(initialShowLearn = true)
        state.openLearn()
        assertTrue(state.showLearn)
    }

    @Test
    fun openWorkbookSetsShowWorkbookTrue() {
        val state = TypewrightAppNavState()
        state.openWorkbook()
        assertTrue(state.showWorkbook)
    }

    @Test
    fun closeWorkbookSetsShowWorkbookFalse() {
        val state = TypewrightAppNavState(initialShowWorkbook = true)
        state.closeWorkbook()
        assertFalse(state.showWorkbook)
    }

    @Test
    fun openWorkbookClosesLearnAndOpenLearnClosesWorkbook() {
        val state = TypewrightAppNavState(initialShowLearn = true)
        state.openWorkbook()
        assertTrue(state.showWorkbook)
        assertFalse(state.showLearn, "opening the workbook must close Learn -- both are full-screen overlays")

        state.openLearn()
        assertTrue(state.showLearn)
        assertFalse(state.showWorkbook, "opening Learn must close the workbook -- both are full-screen overlays")
    }

    @Test
    fun backFromASectionGoesUpToHome() {
        val state = TypewrightAppNavState(initialShowLearn = true)
        state.closeWorkbook()
        assertEquals(DeckSection.HOME, state.section, "Back goes up one level: a section to Home")
        assertFalse(state.showLearn)
    }

    @Test
    fun theAppStartsOnHome() {
        assertEquals(DeckSection.HOME, TypewrightAppNavState().section)
    }

    @Test
    fun anySectionIsReachableFromAnyOtherAndLeavesTheBriefAndTheMap() {
        val state = TypewrightAppNavState()
        state.openBrief()
        state.mapOpen = true
        for (target in DeckSection.entries) {
            state.goToSection(target)
            assertEquals(target, state.section)
            assertFalse(state.showBrief)
            assertFalse(state.mapOpen)
        }
    }

    @Test
    fun theBriefSitsUnderTheSectionItWasOpenedFrom() {
        val state = TypewrightAppNavState()
        state.openBrief(com.asoc.typewright.brief.Door.REFERENCES, DeckSection.WORKBOOK)
        assertEquals(DeckSection.WORKBOOK, state.section)
        assertEquals(DeckSection.WORKBOOK, state.briefFrom)
        assertTrue(state.showBrief)
        state.closeBrief()
        assertEquals(DeckSection.WORKBOOK, state.section, "closing the Brief returns to the section it was opened from")
    }

    @Test
    fun theRouterSurvivesTheProcessBeingRecreated() {
        val state = TypewrightAppNavState()
        state.openBrief(null, DeckSection.WORKBOOK)
        val restored = TypewrightAppNavState.restore(state.save())
        assertEquals(DeckSection.WORKBOOK, restored?.section)
        assertTrue(restored?.showBrief == true)
        assertEquals(DeckSection.WORKBOOK, restored?.briefFrom)
    }

    @Test
    fun openBriefClosesTheOtherTwoAndTheyCloseIt() {
        val state = TypewrightAppNavState(initialShowLearn = true)
        state.openBrief()
        assertTrue(state.showBrief)
        assertFalse(state.showLearn, "opening the brief must close Learn -- all three are full-screen overlays")

        state.openWorkbook()
        assertFalse(state.showBrief, "opening the workbook must close the brief")
        state.openBrief()
        assertFalse(state.showWorkbook, "opening the brief must close the workbook")

        state.closeBrief()
        assertFalse(state.showBrief)
    }

    @Test
    fun canStartOnTheBrief() {
        assertTrue(TypewrightAppNavState(initialShowBrief = true).showBrief)
        assertFalse(TypewrightAppNavState().showBrief)
    }

    @Test
    fun openBriefWithNoDoorLeavesNothingPending() {
        val state = TypewrightAppNavState()
        state.openBrief()
        assertTrue(state.showBrief)
        assertNull(state.pendingBriefDoor)
    }

    @Test
    fun openBriefWithADoorRemembersItUntilConsumed() {
        val state = TypewrightAppNavState()
        state.openBrief(Door.REFERENCES)
        assertTrue(state.showBrief)
        assertEquals(Door.REFERENCES, state.pendingBriefDoor)
    }

    @Test
    fun projectsIsTheRootAndAnySectionLeavesIt() {
        val state = TypewrightAppNavState()
        state.openProjects()
        assertTrue(state.showProjects)
        state.goToSection(DeckSection.HOME)
        assertFalse(state.showProjects)
    }

    @Test
    fun settingsAboutAndTheEditorStackAndBackUnwindsThem() {
        val state = TypewrightAppNavState()
        state.openSettings()
        state.openThemeEditor(com.asoc.typewright.ui.theme.Themes.PAPER)
        assertEquals(AppOverlay.THEME_EDITOR, state.overlay)
        state.closeOverlay()
        assertEquals(AppOverlay.SETTINGS, state.overlay)
        state.openAbout()
        state.closeOverlay()
        assertEquals(AppOverlay.SETTINGS, state.overlay)
        state.closeOverlay()
        assertEquals(null, state.overlay)
        assertEquals(DeckSection.HOME, state.section, "the section underneath was never left")
    }

    @Test
    fun leavingForASectionDropsAnyOverlay() {
        val state = TypewrightAppNavState()
        state.openSettings()
        state.goToSection(DeckSection.LEARN)
        assertEquals(null, state.overlay)
    }
}
