// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.asoc.typewright.ui.deck.ControlDeck
import com.asoc.typewright.ui.deck.DeckKeyCommand
import com.asoc.typewright.ui.deck.DeckKeys
import com.asoc.typewright.ui.deck.DeckLeft
import com.asoc.typewright.ui.deck.DeckRight
import com.asoc.typewright.ui.deck.DeckSection
import com.asoc.typewright.ui.deck.DeckToggle
import com.asoc.typewright.ui.deck.SystemBackHandler
import com.asoc.typewright.ui.deck.deckKeys
import com.asoc.typewright.ui.glass.CommandPalette
import com.asoc.typewright.ui.glass.commandPaletteShortcut
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.Typography
import com.asoc.typewright.ui.tokens.toColor
import kotlinx.coroutines.delay

/** One line of a page's ⋯ menu: [title], a mono [note] under it, and what it does. */
class PageMenuItem(
    val title: String,
    val note: String,
    val run: () -> Unit,
)

/**
 * The page frame every screen but the Brief and the sheet shares: the explorer's header (a 14 sp
 * title and a mono line under it), the page, and the control deck at the foot, with the section arc
 * live. [toggles] are this page's views (SCREENS_V1 §2.4); the left circle is Back, which goes up
 * one level ([onBack]), or hidden where there is nowhere up ([DeckLeft.NONE]); the right is ⋯, whose
 * menu starts with Command… and then [menuItems]. Esc and the system back do what the left circle
 * does. Keys, taps and swipes on the section arc go through [LocalSectionNav].
 */
@Composable
fun SectionScaffold(
    texture: CanvasTexture,
    section: DeckSection,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    toggles: List<DeckToggle> = emptyList(),
    onToggle: (Int) -> Unit = {},
    left: DeckLeft = DeckLeft.BACK,
    onBack: () -> Unit = {},
    menuItems: List<PageMenuItem> = emptyList(),
    /** The right circle: ⋯ (the page menu) by default, or a primary action ([onRight] runs it; [rightDimmed] says it cannot yet). */
    right: DeckRight = DeckRight.MENU,
    onRight: (() -> Unit)? = null,
    rightDimmed: Boolean = false,
    rightLabel: String? = null,
    /** What a tap on a dimmed right circle says. */
    rightExplanation: String? = null,
    /** Dims the section arc and makes it inert, saying [dimReason] on a tap: for a state that sits under a section. */
    dimSections: Boolean = false,
    dimReason: String = "",
    content: @Composable BoxScope.() -> Unit,
) {
    val nav = LocalSectionNav.current
    var menuOpen by remember { mutableStateOf(false) }
    var paletteOpen by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(TOAST_MILLIS)
            toast = null
        }
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    fun leave() {
        if (left != DeckLeft.NONE) onBack()
    }

    fun goTo(target: DeckSection) {
        when {
            dimSections -> toast = dimReason
            target != section -> nav?.goTo?.invoke(target)
        }
    }
    SystemBackHandler(enabled = left != DeckLeft.NONE) { leave() }
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(texture.canvas.toColor())
                .commandPaletteShortcut { paletteOpen = !paletteOpen }
                .deckKeys { command ->
                    when (command) {
                        is DeckKeyCommand.StepSection -> DeckKeys.step(section, command.delta)?.let(::goTo) != null
                        is DeckKeyCommand.GoToSection -> true.also { goTo(command.section) }
                        DeckKeyCommand.OpenMap -> nav?.openMap?.invoke() != null
                        DeckKeyCommand.Leave -> true.also { leave() }
                    }
                }.focusRequester(focusRequester)
                .focusable(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = title, subtitle = subtitle, texture = texture)
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), content = content)
        }
        ControlDeck(
            texture = texture,
            modifier = Modifier.align(Alignment.BottomCenter),
            section = section,
            left = left,
            right = right,
            rightDimmed = rightDimmed,
            rightLabel = rightLabel,
            dimSections = dimSections,
            toggles = toggles,
            sectionStatus = nav?.statusOf ?: { null },
            onLeft = ::leave,
            onRight = {
                when {
                    onRight == null -> menuOpen = !menuOpen
                    rightDimmed -> toast = rightExplanation ?: "Not available yet"
                    else -> onRight()
                }
            },
            onToggle = { index -> toggles.getOrNull(index)?.let { if (it.enabled) onToggle(index) else toast = it.why } },
            onSection = if (nav == null) null else ::goTo,
            onMap = nav?.openMap,
        )
        if (menuOpen) {
            PageMenu(
                texture = texture,
                items = listOf(PageMenuItem("Command…", "Ctrl or ⌘ K") { paletteOpen = true }) + menuItems,
                onDismiss = { menuOpen = false },
            )
        }
        toast?.let { SectionToast(text = it, texture = texture, modifier = Modifier.align(Alignment.BottomCenter)) }
        CommandPalette(
            visible = paletteOpen,
            onDismiss = { paletteOpen = false },
            texture = texture,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

private const val TOAST_MILLIS = 2600L

/**
 * The explorer's `.bar` without its buttons (the deck has them): the title (14, semibold) and under
 * it where in the page the person is (mono, 11, sentence case, muted), 12 below the status bar and
 * 16 above the page.
 */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String,
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BasicText(
            text = title,
            style =
                TextStyle(
                    fontFamily = FontFamily.Default,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = texture.fg.toColor(),
                ),
        )
        if (subtitle.isNotEmpty()) {
            BasicText(text = subtitle, style = Typography.mono(sizeSp = 11.0).copy(color = texture.muted.toColor(), letterSpacing = 0.sp))
        }
    }
}

/** The ⋯ menu, rising from the deck's right circle: ink blocks with paper text and a mono second line. A tap elsewhere closes it. */
@Composable
fun PageMenu(
    texture: CanvasTexture,
    items: List<PageMenuItem>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onInk = texture.canvas.toColor()
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = MENU_BOTTOM.dp).width(MENU_WIDTH.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            for (item in items) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(texture.ink.toColor())
                            .clickable {
                                onDismiss()
                                item.run()
                            }.padding(horizontal = 13.dp, vertical = 11.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    BasicText(text = item.title, style = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, color = onInk))
                    BasicText(
                        text = item.note.uppercase(),
                        style = Typography.mono(sizeSp = 9.0).copy(color = onInk.copy(alpha = 0.72f), letterSpacing = 0.12.em),
                    )
                }
            }
        }
    }
}

private const val MENU_WIDTH = 230
private const val MENU_BOTTOM = 136

/** One line on the glass, in an ink block above the deck: why a control did not go anywhere. */
@Composable
fun SectionToast(
    text: String,
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = text,
        style = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.5.sp, color = texture.canvas.toColor()),
        modifier =
            modifier
                .padding(start = 18.dp, end = 18.dp, bottom = 184.dp)
                .background(texture.ink.toColor())
                .padding(horizontal = 13.dp, vertical = 10.dp),
    )
}

/** An ink block on the glass that asks before something is lost (build brief §4.5): the message, then two text buttons. */
@Composable
fun ConfirmBlock(
    texture: CanvasTexture,
    message: String,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val onInk = texture.canvas.toColor()
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onCancel),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .padding(horizontal = 18.dp)
                    .widthIn(max = 340.dp)
                    .background(texture.ink.toColor())
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BasicText(
                text = message,
                style = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, lineHeight = 18.sp, color = onInk),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                BasicText(
                    text = confirmLabel,
                    style = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = onInk),
                    modifier = Modifier.clickable(onClick = onConfirm).padding(vertical = 8.dp),
                )
                BasicText(
                    text = cancelLabel,
                    style = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.5.sp, color = onInk.copy(alpha = 0.72f)),
                    modifier = Modifier.clickable(onClick = onCancel).padding(vertical = 8.dp),
                )
            }
        }
    }
}
