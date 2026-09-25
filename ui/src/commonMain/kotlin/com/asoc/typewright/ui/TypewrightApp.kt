// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asoc.typewright.ui.learn.LearnScreen
import com.asoc.typewright.ui.sheet.TypewrightSheet
import com.asoc.typewright.ui.tokens.CanvasTextures
import com.asoc.typewright.ui.tokens.Typography
import com.asoc.typewright.ui.tokens.toColor
import com.asoc.typewright.ui.workbook.WorkbookScreen
import com.asoc.typewright.ui.workbook.loadWorkbookCampaignSnapshot
import com.asoc.typewright.ui.workbook.nextTaskLabel
import com.asoc.typewright.ui.workbook.rememberWorkbookScreenUiState

/**
 * The app's entry point on every platform (`app-android`/`app-desktop`/`app-web` each call only
 * this). From P0 through P4a this was a paper-canvas placeholder with the app's name in mono ink;
 * task P4b replaces that placeholder with the real one-sheet UI ([TypewrightSheet]), opened on
 * the Draw room with the paper texture, per UI_SPEC §2 ("Default: paper [CONFIRM]" -- still the
 * default pending that confirmation) and UI_SPEC §9's own screen list (Draw is the sheet's first
 * room). [PaperTokens] (the old placeholder's own two colours) is unused by this function now but
 * left in place: `PlaceholderScreenshotTest`'s pixel-level assertions still reference it directly
 * against `CanvasTextures.PAPER`'s own values, which it also is a subset of.
 *
 * **Task P6 ("Shell" phase): the minimal, honest way to reach [LearnScreen].** The Learn screen
 * (`com.asoc.typewright.ui.learn.LearnScreen`, matching `ui/typewright-explorer.html`'s own
 * standalone `#s-learn` station) is real, complete and tested on its own, but nothing in this app
 * could open it until this change -- a real, disclosed gap (`docs/OPEN_QUESTIONS.md` item 56).
 * This task's own instructions are explicit that the *full* cross-station navigation shell
 * (Home/Capture/Trace/Economy/Draw-Space-Learn/Check/Ship/Workbook/Desktop) is separate, later
 * work the explorer itself does not yet design either, and stay out of scope here -- what this
 * function adds is only a minimal, honest entry point: [TypewrightAppNavState.showLearn] (a
 * plain, testable state class, the same shape [com.asoc.typewright.ui.puck.PuckUiState] already
 * uses -- see [TypewrightAppNavState]'s own KDoc), a small always-visible "Learn" tab pinned to
 * the bottom-right corner (an ink block, this app's established selection/affordance language,
 * CLAUDE.md law 8) that opens [LearnScreen] full-screen over [TypewrightSheet], and [LearnScreen]'s
 * own real back button (`onBack = navState::closeLearn`) to return. Chosen over, say, a debug-only
 * flag or a no-op placeholder button because the task's own instructions rule both out by name;
 * chosen over trying to bend [TypewrightSheet]'s own [com.asoc.typewright.ui.sheet.Room.LEARN]
 * placeholder room into a real entry point because the human product owner's decision, restated
 * in this task's own instructions, is that the real Learn experience is [LearnScreen] itself, not
 * that room's content. The corner was picked, not guessed: [TypewrightSheet]'s own glass already
 * claims every other corner/edge of the phone (`TypewrightSheet.kt`: [com.asoc.typewright.ui.
 * glass.Header] top-start, `LayersPanel` top-end, `SpaceRoomGlass`/`CommandPalette` top-centre,
 * `InspectorRow` bottom-start, [com.asoc.typewright.ui.glass.EdgeMarks] centre-start/-end) --
 * bottom-end is the one corner nothing else in the sheet draws into.
 *
 * **Task P7: a second corner affordance for [com.asoc.typewright.ui.workbook.WorkbookScreen],
 * stacked in the same bottom-end corner as [LearnEntryButton] -- there is no free corner left.**
 * By this task's own start, every corner/edge [TypewrightSheet] itself claims is exactly the list
 * above (unchanged since P6), and P6's own [LearnEntryButton] now additionally occupies the one
 * corner that used to be free. P7's own brief asks for the workbook to appear "as a one-line
 * next-task on the specimen" (TYPEWRIGHT_BUILD_BRIEF.md section 9/`PROMPTS_CLAUDE_CODE.md` P7) --
 * but this app has no specimen/home screen yet, a real, already-disclosed gap
 * (`docs/OPEN_QUESTIONS.md` item 82, and item 56 it points back to: "the *full* cross-station
 * navigation shell... remains separate, later, undesigned-by-the-explorer work"). Given that gap,
 * stacking a second small ink-block affordance directly above [LearnEntryButton] in the same
 * corner -- showing the real one-line next-task label ([nextTaskLabel], `#s-home`'s own `.taskcard`
 * copy shape, e.g. "Task 1 · Choose your reference", never a generic "Workbook" label except when
 * [com.asoc.typewright.ui.workbook.WorkbookCampaignSnapshot] itself could not load) -- is this
 * build's own honest substitute for that missing specimen taskcard, not an attempt to build the
 * specimen screen itself (`docs/OPEN_QUESTIONS.md` records this substitution explicitly). Opening
 * [com.asoc.typewright.ui.workbook.WorkbookScreen] closes [LearnScreen] if it was open, and vice
 * versa ([TypewrightAppNavState.openWorkbook]/[TypewrightAppNavState.openLearn]) -- both are
 * full-screen, texture-occluding overlays over the same [TypewrightSheet], so showing both at once
 * would mean one silently painting over the other.
 */
@Composable
fun TypewrightApp(
    modifier: Modifier = Modifier,
    navState: TypewrightAppNavState = rememberTypewrightAppNavState(),
) {
    Box(modifier = modifier.fillMaxSize()) {
        TypewrightSheet(modifier = Modifier.fillMaxSize())
        when {
            navState.showLearn -> {
                // Opaque: LearnScreen paints its own texture-coloured background across the
                // whole box, the same "occluding background" pattern SpaceRoomGlass already
                // established for a glass element that needs to fully cover what is behind it.
                LearnScreen(modifier = Modifier.fillMaxSize(), onBack = navState::closeLearn)
            }

            navState.showWorkbook -> {
                WorkbookScreen(
                    modifier = Modifier.fillMaxSize(),
                    onBack = navState::closeWorkbook,
                    uiState = rememberWorkbookScreenUiState(),
                )
            }

            else -> {
                Column(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    WorkbookEntryButton(onClick = navState::openWorkbook)
                    LearnEntryButton(onClick = navState::openLearn)
                }
            }
        }
    }
}

/**
 * [TypewrightApp]'s own navigation state: which of [showLearn]/[showWorkbook] (mutually
 * exclusive -- see [openLearn]/[openWorkbook]) is open over [TypewrightSheet], if either. A plain
 * class, not a `@Composable` function, holding two Compose `mutableStateOf` fields -- mirrors
 * [com.asoc.typewright.ui.puck.PuckUiState]'s own shape so a test can construct one directly,
 * pre-seed or mutate either flag, and render [TypewrightApp] in any state with no simulated
 * pointer sequence needed, the same "GESTURE HONESTY" precedent (CLAUDE.md law 4) [com.asoc.
 * typewright.ui.SheetScreenshotTest]'s own `puckState.gestureState = ...` already sets for a real,
 * plain click this container can actually simulate.
 */
class TypewrightAppNavState(
    initialShowLearn: Boolean = false,
    initialShowWorkbook: Boolean = false,
) {
    var showLearn: Boolean by mutableStateOf(initialShowLearn)
        internal set
    var showWorkbook: Boolean by mutableStateOf(initialShowWorkbook)
        internal set

    /** What [LearnEntryButton]'s own `onClick` calls. Closes [showWorkbook] -- the two screens are mutually exclusive full-screen overlays. */
    fun openLearn() {
        showLearn = true
        showWorkbook = false
    }

    /** What [LearnScreen]'s own back button, via `onBack`, calls. */
    fun closeLearn() {
        showLearn = false
    }

    /** What [WorkbookEntryButton]'s own `onClick` calls. Closes [showLearn] -- the two screens are mutually exclusive full-screen overlays. */
    fun openWorkbook() {
        showWorkbook = true
        showLearn = false
    }

    /** What [com.asoc.typewright.ui.workbook.WorkbookScreen]'s own back button, via `onBack`, calls. */
    fun closeWorkbook() {
        showWorkbook = false
    }
}

/** Remembers a [TypewrightAppNavState] for the composition's lifetime, starting closed unless [initialShowLearn]/[initialShowWorkbook] say otherwise. */
@Composable
fun rememberTypewrightAppNavState(
    initialShowLearn: Boolean = false,
    initialShowWorkbook: Boolean = false,
): TypewrightAppNavState = remember { TypewrightAppNavState(initialShowLearn, initialShowWorkbook) }

/** The small, always-visible, real affordance that opens [LearnScreen] -- see [TypewrightApp]'s own KDoc for why it exists and why this corner. */
@Composable
private fun LearnEntryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val texture = CanvasTextures.DEFAULT
    Box(
        modifier =
            modifier
                .background(texture.ink.toColor())
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        BasicText(text = "Learn", style = Typography.mono(sizeSp = 11.0).copy(color = texture.canvas.toColor()))
    }
}

/**
 * The small, always-visible, real affordance that opens [com.asoc.typewright.ui.workbook.
 * WorkbookScreen] at the campaign module's own real current/next task -- see [TypewrightApp]'s own
 * KDoc ("Task P7") for why it stacks above [LearnEntryButton] in the same corner rather than
 * inventing a new one, and why its label is real data ([nextTaskLabel]), not a generic "Workbook"
 * word.
 *
 * [loadWorkbookCampaignSnapshot] runs the real font parse and campaign gate computation once per
 * composition ([remember]) -- a second, independent load from
 * [com.asoc.typewright.ui.workbook.WorkbookScreen]'s own [com.asoc.typewright.ui.workbook.
 * rememberWorkbookScreenUiState] when it opens, not a shared instance threaded through
 * [TypewrightApp]'s own state: [com.asoc.typewright.ui.workbook.WorkbookScreen]'s own signature
 * is fixed to `(modifier, texture, onBack, uiState)` (this task's own instruction, matching
 * [LearnScreen]'s established shape) with no fifth "pre-loaded snapshot" parameter to widen it
 * for. Both loads are deterministic and read the same real data, so they always agree; the
 * duplicated font-parse-plus-corpus-load cost is a real, small inefficiency, not a correctness
 * gap, and disclosed as a known simplification (`docs/OPEN_QUESTIONS.md`) rather than left silent.
 */
@Composable
private fun WorkbookEntryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val texture = CanvasTextures.DEFAULT
    val snapshot = remember { loadWorkbookCampaignSnapshot() }
    Box(
        modifier =
            modifier
                .widthIn(max = WORKBOOK_ENTRY_BUTTON_MAX_WIDTH_DP.dp)
                .background(texture.ink.toColor())
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        BasicText(
            text = nextTaskLabel(snapshot),
            style = Typography.mono(sizeSp = 9.5, textAlign = TextAlign.End).copy(color = texture.canvas.toColor()),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A real, longer task title ("Task 10 · Extend a script (optional)") reads as a small corner tab
 * only with a width cap -- confirmed by screenshot, not by eye alone (this task's own instruction):
 * the first unconstrained render spanned nearly the full phone width, reading as a banner rather
 * than a stacked corner affordance next to [LearnEntryButton]. Ellipsis, not wrapping, keeps
 * [WorkbookEntryButton]'s own real label a genuine one line ("a one-line next-task", this task's
 * own brief) rather than growing the corner stack's own height unpredictably.
 */
private const val WORKBOOK_ENTRY_BUTTON_MAX_WIDTH_DP = 190.0
