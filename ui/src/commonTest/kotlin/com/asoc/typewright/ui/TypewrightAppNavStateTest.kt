// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui

import kotlin.test.Test
import kotlin.test.assertFalse
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
    fun closingOneScreenNeverAffectsTheOther() {
        val state = TypewrightAppNavState(initialShowLearn = true)
        state.closeWorkbook()
        assertTrue(state.showLearn, "closing the workbook (never open) must not close Learn")
    }
}
