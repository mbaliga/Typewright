package dev.aarso.typewright.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.aarso.typewright.ui.learn.LearnScreen
import dev.aarso.typewright.ui.sheet.TypewrightSheet
import dev.aarso.typewright.ui.tokens.CanvasTextures
import dev.aarso.typewright.ui.tokens.Typography
import dev.aarso.typewright.ui.tokens.toColor

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
 * (`dev.aarso.typewright.ui.learn.LearnScreen`, matching `ui/typewright-explorer.html`'s own
 * standalone `#s-learn` station) is real, complete and tested on its own, but nothing in this app
 * could open it until this change -- a real, disclosed gap (`docs/OPEN_QUESTIONS.md` item 56).
 * This task's own instructions are explicit that the *full* cross-station navigation shell
 * (Home/Capture/Trace/Economy/Draw-Space-Learn/Check/Ship/Workbook/Desktop) is separate, later
 * work the explorer itself does not yet design either, and stay out of scope here -- what this
 * function adds is only a minimal, honest entry point: [TypewrightAppNavState.showLearn] (a
 * plain, testable state class, the same shape [dev.aarso.typewright.ui.puck.PuckUiState] already
 * uses -- see [TypewrightAppNavState]'s own KDoc), a small always-visible "Learn" tab pinned to
 * the bottom-right corner (an ink block, this app's established selection/affordance language,
 * CLAUDE.md law 8) that opens [LearnScreen] full-screen over [TypewrightSheet], and [LearnScreen]'s
 * own real back button (`onBack = navState::closeLearn`) to return. Chosen over, say, a debug-only
 * flag or a no-op placeholder button because the task's own instructions rule both out by name;
 * chosen over trying to bend [TypewrightSheet]'s own [dev.aarso.typewright.ui.sheet.Room.LEARN]
 * placeholder room into a real entry point because the human product owner's decision, restated
 * in this task's own instructions, is that the real Learn experience is [LearnScreen] itself, not
 * that room's content. The corner was picked, not guessed: [TypewrightSheet]'s own glass already
 * claims every other corner/edge of the phone (`TypewrightSheet.kt`: [dev.aarso.typewright.ui.
 * glass.Header] top-start, `LayersPanel` top-end, `SpaceRoomGlass`/`CommandPalette` top-centre,
 * `InspectorRow` bottom-start, [dev.aarso.typewright.ui.glass.EdgeMarks] centre-start/-end) --
 * bottom-end is the one corner nothing else in the sheet draws into.
 */
@Composable
fun TypewrightApp(
    modifier: Modifier = Modifier,
    navState: TypewrightAppNavState = rememberTypewrightAppNavState(),
) {
    Box(modifier = modifier.fillMaxSize()) {
        TypewrightSheet(modifier = Modifier.fillMaxSize())
        if (navState.showLearn) {
            // Opaque: LearnScreen paints its own texture-coloured background across the whole
            // box, the same "occluding background" pattern SpaceRoomGlass already established
            // for a glass element that needs to fully cover what is behind it.
            LearnScreen(modifier = Modifier.fillMaxSize(), onBack = navState::closeLearn)
        } else {
            LearnEntryButton(onClick = navState::openLearn, modifier = Modifier.align(Alignment.BottomEnd))
        }
    }
}

/**
 * [TypewrightApp]'s own navigation state: today, only whether [LearnScreen] is open over
 * [TypewrightSheet]. A plain class, not a `@Composable` function, holding one Compose
 * `mutableStateOf` field -- mirrors [dev.aarso.typewright.ui.puck.PuckUiState]'s own shape so a
 * test can construct one directly, pre-seed or mutate [showLearn], and render [TypewrightApp] in
 * either state with no simulated pointer sequence needed, the same "GESTURE HONESTY" precedent
 * (CLAUDE.md law 4) [dev.aarso.typewright.ui.SheetScreenshotTest]'s own
 * `puckState.gestureState = ...` already sets for a real, plain click this container can actually
 * simulate.
 */
class TypewrightAppNavState(
    initialShowLearn: Boolean = false,
) {
    var showLearn: Boolean by mutableStateOf(initialShowLearn)
        internal set

    /** What [LearnEntryButton]'s own `onClick` calls. */
    fun openLearn() {
        showLearn = true
    }

    /** What [LearnScreen]'s own back button, via `onBack`, calls. */
    fun closeLearn() {
        showLearn = false
    }
}

/** Remembers a [TypewrightAppNavState] for the composition's lifetime, starting closed unless [initialShowLearn] says otherwise. */
@Composable
fun rememberTypewrightAppNavState(initialShowLearn: Boolean = false): TypewrightAppNavState =
    remember { TypewrightAppNavState(initialShowLearn) }

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
                .padding(16.dp)
                .background(texture.ink.toColor())
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        BasicText(text = "Learn", style = Typography.mono(sizeSp = 11.0).copy(color = texture.canvas.toColor()))
    }
}
