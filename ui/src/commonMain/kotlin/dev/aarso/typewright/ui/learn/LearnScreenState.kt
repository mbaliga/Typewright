// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.ui.learn

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * The Learn screen's four tabs (`ui/typewright-explorer.html`'s `#lnTabs`, id `s-learn`, around
 * line 755: `<button class="on" data-p="lin">Lineages</button><button data-p="ov">Overlay</button>
 * <button data-p="lens">Lens</button><button data-p="scrap">Scrapbook</button>`), in the
 * explorer's own left-to-right order. Kept as a small, `@Composable`-free enum -- rather than
 * inline state in [LearnScreen] -- so tab identity/order/labels are unit-testable with no Compose
 * test rule, the same split every sibling tab in this package already gives its own pure logic
 * ([dev.aarso.typewright.ui.learn.LensScene], [dev.aarso.typewright.ui.learn.OverlayLayer], the
 * `buildQuizItems` family in `LineagesQuizItems.kt`).
 */
enum class LearnScreenTab(
    /** The explorer's own `data-p` attribute value on `#lnTabs`, kept for traceability back to it. */
    val paramValue: String,
    /** The explorer's own visible tab label (`#lnTabs button` text, exactly). */
    val label: String,
) {
    LINEAGES("lin", "Lineages"),
    OVERLAY("ov", "Overlay"),
    LENS("lens", "Lens"),
    SCRAPBOOK("scrap", "Scrapbook"),
    ;

    companion object {
        /** Every tab, left to right, in exactly `#lnTabs`'s own button order. */
        val ORDERED: List<LearnScreenTab> = entries.toList()
    }
}

/**
 * `#s-learn`'s own header subtitle (`.t2`, `ui/typewright-explorer.html` line 752):
 * "lineages · overlay · anatomy lens · scrapbook". Fixed text, independent of which tab is
 * currently selected -- the explorer never changes it when a tab is tapped -- so this is a plain
 * constant rather than something derived per [LearnScreenTab] (whose own `label` for
 * [LearnScreenTab.LENS] is "Lens", not "anatomy lens": the subtitle uses the strand's full name,
 * the tab button uses its short one, and the explorer's own markup keeps the two independent).
 */
const val LEARN_SCREEN_SUBTITLE: String = "lineages · overlay · anatomy lens · scrapbook"

/**
 * [LearnScreen]'s own tab-selection state: which of the four tabs is showing. A plain class, not
 * a `@Composable` function, holding one Compose `mutableStateOf` field -- the same shape
 * [dev.aarso.typewright.ui.puck.PuckUiState] already uses for the one-sheet's own puck state, for
 * the same reason: a test can construct one directly, read or set [selectedTab] with no Compose
 * test rule or simulated pointer event, and get a real answer about the *actual* mechanism
 * [LearnScreen]'s own tab row calls on every tap -- not a stand-in for it. This is the same
 * "GESTURE HONESTY" precedent (CLAUDE.md law 4) `SheetScreenshotTest`'s own
 * `puckState.gestureState = PuckGestureState.RadialOpen(...)` already sets: pre-seeding and
 * reading real state is honest here because a tab tap is a plain click this container can
 * actually simulate and this class actually receives, unlike an on-device gesture.
 */
class LearnScreenUiState(
    initialTab: LearnScreenTab = LearnScreenTab.LINEAGES,
) {
    var selectedTab: LearnScreenTab by mutableStateOf(initialTab)
        internal set

    /** What every tab button's own `onClick` calls -- the one real place [selectedTab] changes. */
    fun select(tab: LearnScreenTab) {
        selectedTab = tab
    }
}

/** Remembers a [LearnScreenUiState] for the composition's lifetime, starting on [initialTab]. */
@Composable
fun rememberLearnScreenUiState(initialTab: LearnScreenTab = LearnScreenTab.LINEAGES): LearnScreenUiState =
    remember { LearnScreenUiState(initialTab) }
