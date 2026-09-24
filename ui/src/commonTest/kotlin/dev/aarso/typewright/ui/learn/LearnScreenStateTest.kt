package dev.aarso.typewright.ui.learn

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Portable (desktop + wasmJs/browser) unit tests for [LearnScreen]'s own Compose-free tab state:
 * [LearnScreenTab], [LEARN_SCREEN_SUBTITLE], [LearnScreenUiState]. No Compose test rule and no
 * simulated pointer event is needed for any of this -- [LearnScreenUiState] is a plain class
 * holding a Compose `mutableStateOf` field, constructible and readable directly, the same shape
 * [dev.aarso.typewright.ui.puck.PuckUiState] already uses (see [LearnScreenUiState]'s own KDoc).
 * Asserting on [LearnScreenUiState.select] here is a real test of the exact mechanism
 * [LearnScreen]'s own tab row calls on every tap, not a stand-in for it.
 */
class LearnScreenStateTest {
    @Test
    fun tabsAreOrderedExactlyAsHashLnTabsInTheExplorer() {
        // `#lnTabs`: Lineages, Overlay, Lens, Scrapbook, left to right (ui/typewright-explorer.html line 755).
        assertEquals(
            listOf(LearnScreenTab.LINEAGES, LearnScreenTab.OVERLAY, LearnScreenTab.LENS, LearnScreenTab.SCRAPBOOK),
            LearnScreenTab.ORDERED,
        )
    }

    @Test
    fun tabLabelsMatchTheExplorersOwnButtonText() {
        assertEquals("Lineages", LearnScreenTab.LINEAGES.label)
        assertEquals("Overlay", LearnScreenTab.OVERLAY.label)
        assertEquals("Lens", LearnScreenTab.LENS.label)
        assertEquals("Scrapbook", LearnScreenTab.SCRAPBOOK.label)
    }

    @Test
    fun tabParamValuesMatchTheExplorersOwnDataPAttribute() {
        assertEquals("lin", LearnScreenTab.LINEAGES.paramValue)
        assertEquals("ov", LearnScreenTab.OVERLAY.paramValue)
        assertEquals("lens", LearnScreenTab.LENS.paramValue)
        assertEquals("scrap", LearnScreenTab.SCRAPBOOK.paramValue)
    }

    @Test
    fun subtitleMatchesTheExplorersOwnFixedT2Text() {
        assertEquals("lineages · overlay · anatomy lens · scrapbook", LEARN_SCREEN_SUBTITLE)
    }

    @Test
    fun uiStateStartsOnLineagesByDefault() {
        val state = LearnScreenUiState()
        assertSame(LearnScreenTab.LINEAGES, state.selectedTab)
    }

    @Test
    fun uiStateCanStartOnAnyTab() {
        for (tab in LearnScreenTab.ORDERED) {
            assertSame(tab, LearnScreenUiState(initialTab = tab).selectedTab)
        }
    }

    @Test
    fun selectChangesSelectedTab() {
        val state = LearnScreenUiState()
        state.select(LearnScreenTab.OVERLAY)
        assertSame(LearnScreenTab.OVERLAY, state.selectedTab)
    }

    @Test
    fun selectCanMoveThroughEveryTabInOrder() {
        val state = LearnScreenUiState()
        for (tab in LearnScreenTab.ORDERED) {
            state.select(tab)
            assertSame(tab, state.selectedTab)
        }
    }

    @Test
    fun selectingTheAlreadyCurrentTabIsANoOpNotAnError() {
        val state = LearnScreenUiState(initialTab = LearnScreenTab.LENS)
        state.select(LearnScreenTab.LENS)
        assertSame(LearnScreenTab.LENS, state.selectedTab)
    }
}
