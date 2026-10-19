// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.learn

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.asoc.typewright.ui.deck.DeckSection
import com.asoc.typewright.ui.deck.DeckToggle
import com.asoc.typewright.ui.shell.SectionScaffold
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.CanvasTextures

/**
 * The Learn screen: its own station, showing exactly one of four views at a time, Lineages,
 * Overlay, Lens and Scrapbook ([LearnScreenTab]). The views are the deck's toggle arc, not a tab
 * row of the screen's own, and the header is the explorer's `.bar` without its buttons: back, the
 * page menu and the section arc are the deck's ([SectionScaffold]). Each view paints its own
 * canvas and fills the page.
 *
 * [uiState] is exposed so a test can pre-seed the selected view with no simulated pointer
 * sequence; ordinary callers take the default.
 */
@Composable
fun LearnScreen(
    modifier: Modifier = Modifier,
    texture: CanvasTexture = CanvasTextures.DEFAULT,
    onBack: () -> Unit = {},
    uiState: LearnScreenUiState = rememberLearnScreenUiState(),
) {
    SectionScaffold(
        texture = texture,
        section = DeckSection.LEARN,
        title = "Learn",
        subtitle = LEARN_SCREEN_SUBTITLE,
        modifier = modifier,
        toggles = LearnScreenTab.ORDERED.map { DeckToggle(it.label, on = it == uiState.selectedTab) },
        onToggle = { uiState.select(LearnScreenTab.ORDERED[it]) },
        onBack = onBack,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (uiState.selectedTab) {
                LearnScreenTab.LINEAGES -> LineagesTab(texture = texture)
                LearnScreenTab.OVERLAY -> OverlayTab(texture = texture)
                LearnScreenTab.LENS -> LensTab(texture = texture)
                LearnScreenTab.SCRAPBOOK -> ScrapbookTab(texture = texture)
            }
        }
    }
}
