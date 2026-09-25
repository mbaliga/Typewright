// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.CanvasTextures
import com.asoc.typewright.ui.tokens.Typography
import com.asoc.typewright.ui.tokens.toColor

/**
 * The Learn screen (`ui/typewright-explorer.html`'s `#s-learn`, CLAUDE.md law 6): its own
 * standalone station, not content inside the one-sheet's placeholder [com.asoc.typewright.ui.
 * sheet.Room.LEARN] room (that placeholder is left untouched -- see this file's own KDoc on why
 * the two are deliberately separate, and `docs/OPEN_QUESTIONS.md` item 56 for where this
 * boundary was first disclosed). Reproduces `#s-learn`'s own header bar and `#lnTabs` tab row
 * exactly (grep targets: `id="s-learn"` around line 749, `id="lnTabs"` around line 755), then
 * shows exactly one of the four sibling tab composables underneath, per [LearnScreenTab] --
 * [LineagesTab], [OverlayTab], [LensTab], [ScrapbookTab] -- each already a complete, real,
 * self-contained `(texture: CanvasTexture, modifier: Modifier)` composable built by this same
 * task's own Tabs stage; this file only adds the chrome around them and the state that switches
 * between them.
 *
 * **Header, not [com.asoc.typewright.ui.glass.Header].** That composable is UI_SPEC §3's
 * *one-sheet* header: a room-name ink block plus a MAP toggle, built around horizontal-swipe
 * room navigation (`onSwipePrevious`/`onSwipeNext`) that has no equivalent meaning here. `#s-learn`'s
 * own bar is a different, simpler shape -- back button, title block, commands button -- with
 * nothing in it [Header] already has a token for beyond [Typography]/[com.asoc.typewright.ui.
 * tokens.SpacingTokens]/[CanvasTexture] themselves, which this file reuses directly. Forcing
 * `#s-learn`'s bar through [Header]'s own room/swipe-shaped API would mean adding parameters
 * [Header] does not need for its one real caller just to bend a different design into it, so this
 * file builds [LearnHeaderBar] as its own small, purpose-built composable instead -- reusing
 * tokens, not a second token system, per this task's own instructions.
 *
 * **The commands button (`›_`, `.ib.pal`, aria-label "Commands") is visually present and
 * deliberately unwired.** It reuses the exact glyph
 * [com.asoc.typewright.ui.glass.CommandPalette]'s own input row already prints for its prompt
 * (`"›_"`, `CommandPalette.kt` line 158) -- the established visual language for that affordance in
 * this codebase -- but tapping it does nothing: wiring a real command palette onto the Learn
 * screen is out of this task's own scope (its instructions are explicit that an unwired button
 * here is fine, disclosed, rather than either inventing a new affordance or silently pretending
 * one exists). No `clickable` modifier is attached to it, so it reads, correctly, as inert rather
 * than as a button that silently swallows a tap (CLAUDE.md law 4's stub-not-fake spirit, applied
 * to a piece of chrome rather than an on-device feature).
 */
@Composable
public fun LearnScreen(
    modifier: Modifier = Modifier,
    texture: CanvasTexture = CanvasTextures.DEFAULT,
    onBack: () -> Unit = {},
    /**
     * Exposed (rather than created unconditionally inside this function) so a test can pre-seed
     * or mutate the selected tab directly and see the right sibling composable rendered with no
     * simulated pointer sequence needed -- the same reason [com.asoc.typewright.ui.sheet.
     * TypewrightSheet] exposes its own `puckState`. Ordinary callers just take the default.
     */
    uiState: LearnScreenUiState = rememberLearnScreenUiState(),
) {
    Column(
        modifier = modifier.fillMaxSize().background(texture.canvas.toColor()),
    ) {
        LearnHeaderBar(texture = texture, onBack = onBack)
        LearnTabsRow(texture = texture, selected = uiState.selectedTab, onSelect = uiState::select)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Each sibling tab already fills its own box and paints its own texture background
            // (LineagesTab.kt/OverlayTab.kt/LensTab.kt/ScrapbookTab.kt all do
            // `.fillMaxSize().background(texture.canvas.toColor())` themselves), so this Box only
            // needs to pick which one tab is composed -- never more than one at a time, per this
            // task's own instructions.
            when (uiState.selectedTab) {
                LearnScreenTab.LINEAGES -> LineagesTab(texture = texture)
                LearnScreenTab.OVERLAY -> OverlayTab(texture = texture)
                LearnScreenTab.LENS -> LensTab(texture = texture)
                LearnScreenTab.SCRAPBOOK -> ScrapbookTab(texture = texture)
            }
        }
    }
}

/** `#s-learn .bar`: back button, title block ("Learn" + [LEARN_SCREEN_SUBTITLE]), commands button. */
@Composable
private fun LearnHeaderBar(
    texture: CanvasTexture,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()

    Row(
        modifier = modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.size(34.dp).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(text = "‹", style = TextStyle(fontFamily = FontFamily.Default, fontSize = 16.sp, color = fg))
        }
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = "Learn",
                style = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = fg),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                text = LEARN_SCREEN_SUBTITLE,
                style = Typography.mono(sizeSp = 11.0).copy(color = muted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            // Deliberately no `clickable` -- see this file's own top KDoc ("commands button").
            BasicText(
                text = "›_",
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, letterSpacing = (-0.02).em, color = muted),
            )
        }
    }
}

/** `#lnTabs`: the four tab buttons, the current one an ink block per this app's own established selection language (CLAUDE.md law 8). */
@Composable
private fun LearnTabsRow(
    texture: CanvasTexture,
    selected: LearnScreenTab,
    onSelect: (LearnScreenTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        for (tab in LearnScreenTab.ORDERED) {
            LearnTabButton(
                label = tab.label,
                selected = tab == selected,
                texture = texture,
                onClick = { onSelect(tab) },
            )
        }
    }
}

@Composable
private fun LearnTabButton(
    label: String,
    selected: Boolean,
    texture: CanvasTexture,
    onClick: () -> Unit,
) {
    val ink = texture.ink.toColor()
    val canvasColor = texture.canvas.toColor()
    val muted = texture.muted.toColor()

    // [LearnTabsRow]'s own Row is horizontally scrollable (four labels do not always fit this
    // codebase's own 210dp screenshot "phone" width -- see that function's own KDoc). A selected
    // tab set from outside a drag (the initial tab, or a caller-supplied [LearnScreenUiState])
    // otherwise rendered its ink-block selection off-screen at scroll offset 0, invisible until
    // the person scrolled the row themselves -- a real bug this verification pass found by
    // screenshot on the Scrapbook tab (`ui/build/screenshots/learn-screen-scrap.png`): the ink
    // block was a one-pixel sliver at the row's clipped right edge, the label itself entirely
    // off-canvas. [BringIntoViewRequester] is the standard Compose fix -- ask the nearest
    // scrollable ancestor to scroll this button into view whenever it becomes selected, without
    // owning or fighting the user's own drag position. Disclosed, not silently assumed correct:
    // this project's own `ScreenshotHarness`/`ImageComposeScene` renders one synchronous frame
    // with no running coroutine dispatcher behind it, so a `LaunchedEffect` here never actually
    // gets to run in that harness (confirmed directly: pumping 90 synthetic `render()` frames,
    // 1.44s of simulated time, through a raw `ImageComposeScene` left the scroll offset at 0 the
    // entire time) -- this fix cannot be screenshot-verified by this codebase's own tooling, only
    // reasoned about as the correct, standard API for the problem (see `docs/OPEN_QUESTIONS.md`).
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(selected) {
        if (selected) bringIntoViewRequester.bringIntoView()
    }

    Box(
        modifier =
            Modifier
                .bringIntoViewRequester(bringIntoViewRequester)
                .let { if (selected) it.background(ink) else it }
                .clickable(onClick = onClick)
                .padding(horizontal = if (selected) 6.dp else 0.dp, vertical = if (selected) 2.dp else 4.dp),
    ) {
        BasicText(
            text = label,
            style =
                TextStyle(
                    fontFamily = FontFamily.Default,
                    fontSize = 12.5.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) canvasColor else muted,
                ),
        )
    }
}
