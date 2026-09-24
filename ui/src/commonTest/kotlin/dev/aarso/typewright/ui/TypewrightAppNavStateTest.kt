package dev.aarso.typewright.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Portable (desktop + wasmJs/browser) unit tests for [TypewrightApp]'s own minimal entry-point
 * state, [TypewrightAppNavState]. A plain class holding a Compose `mutableStateOf` field, exactly
 * like [dev.aarso.typewright.ui.puck.PuckUiState] and [dev.aarso.typewright.ui.learn.
 * LearnScreenUiState] -- constructible and readable directly, no Compose test rule or simulated
 * pointer event needed. Asserting on [TypewrightAppNavState.openLearn]/[TypewrightAppNavState.
 * closeLearn] here is a real test of the exact mechanism the entry button and [dev.aarso.
 * typewright.ui.learn.LearnScreen]'s own back button call, not a stand-in for it.
 */
class TypewrightAppNavStateTest {
    @Test
    fun startsClosedByDefault() {
        assertFalse(TypewrightAppNavState().showLearn)
    }

    @Test
    fun canStartOpenWhenAskedTo() {
        assertTrue(TypewrightAppNavState(initialShowLearn = true).showLearn)
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
}
