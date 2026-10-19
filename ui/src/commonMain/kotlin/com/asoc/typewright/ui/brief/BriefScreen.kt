// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.brief

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.asoc.typewright.brief.AskPosition
import com.asoc.typewright.brief.CORPUS_GENRE_KEYS
import com.asoc.typewright.brief.Cue
import com.asoc.typewright.brief.Dimension
import com.asoc.typewright.brief.Door
import com.asoc.typewright.brief.FEELINGS
import com.asoc.typewright.brief.FaceMatch
import com.asoc.typewright.brief.GENRES
import com.asoc.typewright.brief.Genre
import com.asoc.typewright.brief.GenreGroup
import com.asoc.typewright.brief.GuideKind
import com.asoc.typewright.brief.OriginKind
import com.asoc.typewright.brief.Reading
import com.asoc.typewright.brief.Source
import com.asoc.typewright.brief.Step
import com.asoc.typewright.brief.Tension
import com.asoc.typewright.brief.USES
import com.asoc.typewright.brief.WrapUp
import com.asoc.typewright.brief.cueFaceOf
import com.asoc.typewright.brief.describeFace
import com.asoc.typewright.brief.facesLike
import com.asoc.typewright.brief.genreByKey
import com.asoc.typewright.brief.guideFor
import com.asoc.typewright.brief.needsConfirm
import com.asoc.typewright.brief.offersNotSure
import com.asoc.typewright.brief.planFor
import com.asoc.typewright.brief.prompt
import com.asoc.typewright.brief.readAs
import com.asoc.typewright.brief.searchFaces
import com.asoc.typewright.brief.why
import com.asoc.typewright.project.MetaChange
import com.asoc.typewright.project.scrapbook.ScrapbookPin
import com.asoc.typewright.project.scrapbook.ScrapbookPinKind
import com.asoc.typewright.project.scrapbook.stablePinRotationDegrees
import com.asoc.typewright.ui.deck.BriefDeckPage
import com.asoc.typewright.ui.deck.ControlDeck
import com.asoc.typewright.ui.deck.DeckAction
import com.asoc.typewright.ui.deck.DeckKeyCommand
import com.asoc.typewright.ui.deck.DeckKeys
import com.asoc.typewright.ui.deck.DeckLayout
import com.asoc.typewright.ui.deck.DeckLeft
import com.asoc.typewright.ui.deck.DeckRight
import com.asoc.typewright.ui.deck.DeckSeats
import com.asoc.typewright.ui.deck.DeckSection
import com.asoc.typewright.ui.deck.DeckToggle
import com.asoc.typewright.ui.deck.NewProjectStage
import com.asoc.typewright.ui.deck.SeatKind
import com.asoc.typewright.ui.deck.StationStatus
import com.asoc.typewright.ui.deck.SystemBackHandler
import com.asoc.typewright.ui.deck.deckKeys
import com.asoc.typewright.ui.deck.ownsDeckKeys
import com.asoc.typewright.ui.glass.CommandPalette
import com.asoc.typewright.ui.glass.commandPaletteShortcut
import com.asoc.typewright.ui.project.LocalProjectWorkspace
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.CanvasTextures
import com.asoc.typewright.ui.tokens.MeaningColors
import com.asoc.typewright.ui.tokens.Typography
import com.asoc.typewright.ui.tokens.toColor
import kotlinx.coroutines.delay

/**
 * The Brief screen: find the visual language a font is aiming for, read what the answers add up
 * to, and get the guide that follows from it. It is laid out as the explorer lays a screen out
 * (`ui/typewright-explorer.html`): a quiet header, a scrolling page with an 18 dp gutter, and the
 * control deck at the foot, with no title bar, back button or tab row of its own. Three views share
 * one page, and the deck's toggle arc switches between them, always, at the doors too:
 *
 * - **Ask** opens on six doors, each set in a real typeface; the chosen door asks its questions
 *   one at a time, every answer shown as a specimen in the face it was measured on. When they are
 *   through, the wrap-up sets the brief in one sentence, puts what is left to settle in front of
 *   the person, names the letters to draw first, and offers more questions for anyone who wants
 *   to refine it.
 * - **Brief** sets the ethos as one large sentence, then the targets (feature, range, where it
 *   came from), the tensions between answers (each kept as a signature or answered again), and
 *   the measured faces that already fit; it ends by pointing at the guide.
 * - **Guide** lists what to do next, grouped by the kind of advice, then the drawing plan: the
 *   brief always leads to drawing.
 *
 * What the deck shows comes from [DeckSeats] alone. Its left circle is Back, which steps out of the
 * brief one place at a time ([BriefScreenUiState.back]) and then leaves through [onBack] to the
 * section the Brief was opened from ([section]); it is Close only on the new-project step, where
 * leaving discards something, and then an ink block asks first. Its right circle is the step's one
 * dominant action, which is removed from the page, or ⋯. The section arc is live: a tap, a swipe,
 * the arrow and digit keys and the Map take the person to another section through [onSection] and
 * [onMap], and they are dimmed, with a reason on tap, only during the new-project step. A chosen
 * answer is an ink block with paper text. There is no red anywhere here but the Close circle.
 * Where the style atlas cannot load, or no project is open, the screen says so in words.
 * [onOpenLearn], when given, opens the lesson a guide item points to.
 */
@Composable
fun BriefScreen(
    modifier: Modifier = Modifier,
    texture: CanvasTexture = CanvasTextures.DEFAULT,
    onBack: () -> Unit = {},
    state: BriefScreenUiState = rememberBriefScreenUiState(),
    onStartDrawing: () -> Unit = onBack,
    onOpenLearn: (() -> Unit)? = null,
    section: DeckSection = DeckSection.HOME,
    onSection: (DeckSection) -> Unit = {},
    onMap: (() -> Unit)? = null,
    sectionStatus: (DeckSection) -> StationStatus? = { null },
) {
    val position =
        remember(state.door, state.ethos, state.model, state.drawing, state.refining, state.focus) { state.askPosition() }
    val onBoard = state.newProject
    val nothingAnswered = state.ethos.isEmpty
    val lettersExist =
        state.drawing.font.glyphs
            .isNotEmpty()
    val session =
        LocalProjectWorkspace.current
            ?.current
            ?.collectAsState()
            ?.value
    val draft = remember(session) { NewProjectDraft(session) }
    var wordmarkDraft by remember(state.ethos.wordmark) { mutableStateOf(state.ethos.wordmark.orEmpty()) }
    var menuOpen by remember { mutableStateOf(false) }
    var paletteOpen by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<PendingConfirm?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(TOAST_MILLIS)
            toast = null
        }
    }

    val page = deckPageOf(state, position)
    val layout =
        if (onBoard) {
            DeckSeats.newProject(NewProjectStage.BRIEF, built = setOf(NewProjectStage.BRIEF), valid = setOf(NewProjectStage.SOURCE))
        } else {
            DeckSeats.brief(
                page,
                canRunAction = page != BriefDeckPage.WORDMARK || wordmarkDraft.isNotBlank(),
                whyNot = "Type the name first",
            )
        }

    fun leave() {
        when {
            onBoard -> confirm = PendingConfirm.CLOSE_NEW_PROJECT
            !state.back() -> onBack()
        }
    }

    fun goTo(target: DeckSection) {
        when {
            onBoard -> toast = FINISH_NEW_PROJECT_FIRST
            target != section -> onSection(target)
        }
    }

    fun perform(action: DeckAction) {
        val step = position?.step
        when (action) {
            DeckAction.DONE -> {
                step?.let(state::confirm)
            }

            DeckAction.SET_WORDMARK -> {
                state.setWordmark(wordmarkDraft)
            }

            DeckAction.USE_ANSWERS -> {
                (step as? Step.ReadDrawing)?.let { state.adoptMeasured(it.levels) }
            }

            DeckAction.START_DRAWING, DeckAction.DRAW_FIRST -> {
                onStartDrawing()
            }

            DeckAction.NEXT, DeckAction.CREATE -> {
                draft.commit()
                state.finishNewProject()
            }
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    SystemBackHandler(enabled = true) { leave() }

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
                        DeckKeyCommand.OpenMap -> onMap?.invoke() != null
                        DeckKeyCommand.Leave -> true.also { leave() }
                    }
                }.focusRequester(focusRequester)
                .focusable(),
    ) {
        Column(modifier = Modifier.align(Alignment.TopCenter).widthIn(max = PAGE_WIDTH.dp).fillMaxSize()) {
            PageHeader(title = if (onBoard) "New project" else "Brief", subtitle = state.subtitle, texture = texture)
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(start = PAGE_SIDE.dp, end = PAGE_SIDE.dp, top = 4.dp, bottom = PAGE_BOTTOM.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (state.tab) {
                    BriefScreenTab.ASK -> {
                        if (onBoard) {
                            NewProjectStep(state = state, draft = draft, texture = texture)
                        } else {
                            AskTab(
                                state = state,
                                position = position,
                                layout = layout,
                                texture = texture,
                                wordmarkDraft = wordmarkDraft,
                                onWordmarkDraft = { wordmarkDraft = it },
                                onStartOver = { confirm = PendingConfirm.START_OVER },
                            )
                        }
                    }

                    BriefScreenTab.BRIEF -> {
                        BriefTab(state = state, texture = texture)
                    }

                    BriefScreenTab.GUIDE -> {
                        GuideTab(
                            state = state,
                            layout = layout,
                            texture = texture,
                            onStartDrawing = onStartDrawing,
                            onOpenLearn = onOpenLearn,
                        )
                    }
                }
            }
        }
        ControlDeck(
            texture = texture,
            modifier = Modifier.align(Alignment.BottomCenter),
            section = section,
            left = layout.left,
            right = layout.right,
            toggles =
                layout.seats.map {
                    DeckToggle(
                        it.label,
                        on = it.id == layout.pressed,
                        enabled = it.enabled,
                        why = it.why,
                        opens = it.kind == SeatKind.OPENS,
                    )
                },
            dimSections = onBoard,
            rightDimmed = layout.actionWhyNot != null,
            rightLabel = layout.action?.let { DeckSeats.actionLabel(it, lettersExist) } ?: "More",
            sectionStatus = sectionStatus,
            onLeft = ::leave,
            onRight = {
                val action = layout.action
                when {
                    action == null -> menuOpen = !menuOpen
                    layout.actionWhyNot != null -> toast = layout.actionWhyNot
                    else -> perform(action)
                }
            },
            onRightLong = if (layout.action != null) ({ menuOpen = true }) else null,
            onToggle = { index ->
                val seat = layout.seats[index]
                when {
                    !seat.enabled -> toast = seat.why
                    !onBoard -> state.selectTab(BriefScreenTab.valueOf(seat.id.uppercase()))
                }
            },
            onSection = ::goTo,
            onMap = onMap,
        )
        if (menuOpen) {
            BriefMenu(
                texture = texture,
                items =
                    briefMenuItems(state, onBoard, nothingAnswered, onCommand = { paletteOpen = true }, onNothing = {
                        toast =
                            NOTHING_TO_CLEAR
                    }) {
                        confirm = PendingConfirm.START_OVER
                    },
                onDismiss = { menuOpen = false },
            )
        }
        confirm?.let { pending ->
            ConfirmBlock(
                texture = texture,
                message = pending.message,
                confirmLabel = pending.confirmLabel,
                cancelLabel = pending.cancelLabel,
                onConfirm = {
                    confirm = null
                    when (pending) {
                        PendingConfirm.START_OVER -> state.startOver()
                        PendingConfirm.CLOSE_NEW_PROJECT -> onBack()
                    }
                },
                onCancel = { confirm = null },
            )
        }
        toast?.let { Toast(text = it, texture = texture, modifier = Modifier.align(Alignment.BottomCenter)) }
        CommandPalette(
            visible = paletteOpen,
            onDismiss = { paletteOpen = false },
            texture = texture,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

private const val FINISH_NEW_PROJECT_FIRST = "Finish or close the new project first"
private const val NOTHING_TO_CLEAR = "Nothing answered yet, so nothing to clear"
private const val TOAST_MILLIS = 2600L

/** Something the person is asked to confirm in an ink block on the glass before it happens. */
private enum class PendingConfirm(
    val message: String,
    val confirmLabel: String,
    val cancelLabel: String,
) {
    START_OVER("Clear every answer in this brief?", "Clear answers", "Keep them"),
    CLOSE_NEW_PROJECT(
        "Close without finishing? The project stays, as Untitled, and what you typed here is not saved.",
        "Close",
        "Keep going",
    ),
}

/** Which deck page the Brief is on: the only thing about the screen's state that [DeckSeats] needs. */
private fun deckPageOf(
    state: BriefScreenUiState,
    position: AskPosition?,
): BriefDeckPage {
    if (state.tab == BriefScreenTab.BRIEF) return BriefDeckPage.BRIEF_TAB
    if (state.tab == BriefScreenTab.GUIDE) return BriefDeckPage.GUIDE_TAB
    if (position == null) return BriefDeckPage.DOORS
    if (position.focused) return BriefDeckPage.FOCUS
    val step = position.step ?: return BriefDeckPage.WRAP_UP
    return when {
        step == Step.EnterWordmark -> BriefDeckPage.WORDMARK

        step is Step.ReadDrawing && state.atlas != null &&
            state.drawing.font.glyphs
                .isEmpty()
        -> BriefDeckPage.MIRROR_EMPTY

        step is Step.ReadDrawing && state.atlas != null && step.levels.isNotEmpty() -> BriefDeckPage.MIRROR_READING

        step.needsConfirm -> BriefDeckPage.MULTI_CHOICE

        else -> BriefDeckPage.SINGLE_CHOICE
    }
}

/** The ⋯ menu's items: Command… first, always, then the Brief's own. */
private fun briefMenuItems(
    state: BriefScreenUiState,
    onBoard: Boolean,
    nothingAnswered: Boolean,
    onCommand: () -> Unit,
    onNothing: () -> Unit,
    onStartOver: () -> Unit,
): List<MenuItem> {
    val command = MenuItem("Command…", "Ctrl or ⌘ K", onCommand)
    if (onBoard) return listOf(command)
    return listOf(
        command,
        MenuItem("Try another door", "Go back to the six doors") {
            state.selectTab(BriefScreenTab.ASK)
            state.chooseDoor(null)
        },
        MenuItem("Start over", if (nothingAnswered) "Nothing answered yet" else "Clear every answer") {
            if (nothingAnswered) onNothing() else onStartOver()
        },
        MenuItem("Read the brief", "What your answers add up to") { state.selectTab(BriefScreenTab.BRIEF) },
        MenuItem("See your guide", "What to draw, and why") { state.selectTab(BriefScreenTab.GUIDE) },
    )
}

/** The page frame: the explorer's 18 dp gutter, room at the foot for the deck, a column no wider than a phone. */
private const val PAGE_WIDTH = 430
private const val PAGE_SIDE = 18
private const val PAGE_BOTTOM = 200

/**
 * The explorer's `.bar` without its buttons (the deck has them): the title (14, semibold) and under
 * it where in the page the person is (mono, 11, sentence case, muted), 12 below the status bar and
 * 16 above the page.
 */
@Composable
private fun PageHeader(
    title: String,
    subtitle: String,
    texture: CanvasTexture,
) {
    Column(
        modifier =
            Modifier
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
        BasicText(text = subtitle, style = monoStyle(texture.muted.toColor(), sizeSp = 11.0).copy(letterSpacing = 0.sp))
    }
}

/** One line of the ⋯ menu: [title], a mono [note] under it, and what it does. */
private class MenuItem(
    val title: String,
    val note: String,
    val run: () -> Unit,
)

/**
 * The ⋯ menu, which rises from the deck's right circle: ink blocks with paper text, a mono second
 * line under each. A tap anywhere else closes it.
 */
@Composable
private fun BriefMenu(
    texture: CanvasTexture,
    items: List<MenuItem>,
    onDismiss: () -> Unit,
) {
    val onInk = texture.canvas.toColor()
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = PAGE_SIDE.dp, bottom = MENU_BOTTOM.dp)
                    .width(MENU_WIDTH.dp),
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
                    BasicText(text = item.title, style = bodyStyle(onInk, sizeSp = 13.0))
                    BasicText(
                        text = item.note.uppercase(),
                        style = monoStyle(onInk.copy(alpha = ON_INK_MUTED_ALPHA), sizeSp = 9.0).copy(letterSpacing = 0.12.em),
                    )
                }
            }
        }
    }
}

private const val MENU_WIDTH = 230
private const val MENU_BOTTOM = 136

/** An ink block on the glass that asks before something is lost (build brief §4.5): the message, then two text buttons. */
@Composable
private fun ConfirmBlock(
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
                    .padding(horizontal = PAGE_SIDE.dp)
                    .widthIn(max = 340.dp)
                    .background(texture.ink.toColor())
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BasicText(text = message, style = bodyStyle(onInk, sizeSp = 13.0))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                BasicText(
                    text = confirmLabel,
                    style = bodyStyle(onInk, sizeSp = 12.5, weight = FontWeight.SemiBold),
                    modifier = Modifier.clickable(onClick = onConfirm).padding(vertical = 8.dp),
                )
                BasicText(
                    text = cancelLabel,
                    style = bodyStyle(onInk.copy(alpha = ON_INK_MUTED_ALPHA), sizeSp = 12.5),
                    modifier = Modifier.clickable(onClick = onCancel).padding(vertical = 8.dp),
                )
            }
        }
    }
}

/** One line on the glass, in an ink block above the deck: why a control did not go anywhere. */
@Composable
private fun Toast(
    text: String,
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = text,
        style = bodyStyle(texture.canvas.toColor(), sizeSp = 12.5),
        modifier =
            modifier
                .padding(
                    start = PAGE_SIDE.dp,
                    end = PAGE_SIDE.dp,
                    bottom = TOAST_BOTTOM.dp,
                ).background(texture.ink.toColor())
                .padding(horizontal = 13.dp, vertical = 10.dp),
    )
}

private const val TOAST_BOTTOM = 184

// ---------------------------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------------------------

internal fun bodyStyle(
    color: Color,
    sizeSp: Double = 12.5,
    weight: FontWeight = FontWeight.Normal,
): TextStyle =
    TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = sizeSp.sp,
        lineHeight = (sizeSp * 1.45).sp,
        fontWeight = weight,
        color = color,
    )

internal fun monoStyle(
    color: Color,
    sizeSp: Double = 9.5,
): TextStyle = Typography.mono(sizeSp = sizeSp).copy(color = color)

/** A small uppercase mono label, with the canvas's room above it (26 between sections). */
@Composable
private fun SectionLabel(
    text: String,
    texture: CanvasTexture,
    first: Boolean = false,
) {
    BasicText(
        text = text.uppercase(),
        style = monoStyle(texture.muted.toColor()),
        modifier = Modifier.padding(top = if (first) 0.dp else 14.dp),
    )
}

/** A line of muted text under a prompt or beside a control (12.5 sp, 330 wide at most). */
@Composable
internal fun Small(
    text: String,
    texture: CanvasTexture,
) {
    BasicText(text = text, style = bodyStyle(texture.muted.toColor()), modifier = Modifier.widthIn(max = READING_WIDTH.dp))
}

/** A small muted mono note, upper case. */
@Composable
private fun NoteMono(
    text: String,
    texture: CanvasTexture,
) {
    BasicText(text = text.uppercase(), style = monoStyle(texture.muted.toColor()))
}

private const val READING_WIDTH = 330

/**
 * The explorer's buttons: [filled] is `.btn.primary`, an ink block, square, sans 12.5 semibold;
 * otherwise `.btn`, violet text, semibold. No outline and no mono or upper case on a button.
 */
@Composable
private fun ActionButton(
    label: String,
    texture: CanvasTexture,
    onClick: () -> Unit,
    filled: Boolean = false,
) {
    val violet = MeaningColors.VIOLET.forTexture(texture.id).toColor()
    BasicText(
        text = label,
        style = bodyStyle(if (filled) texture.canvas.toColor() else violet, weight = FontWeight.SemiBold),
        modifier =
            Modifier
                .let { if (filled) it.background(texture.ink.toColor()) else it }
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

/** `.btn.quiet`: muted text, for the way out of a step that is not the way on. */
@Composable
private fun ActionLink(
    label: String,
    texture: CanvasTexture,
    onClick: () -> Unit,
) {
    BasicText(
        text = label,
        style = bodyStyle(texture.muted.toColor(), weight = FontWeight.SemiBold),
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

/** A row of buttons or links. */
@Composable
private fun ButtonRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(
        modifier = modifier.padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/**
 * The explorer's `.seg` / `.styles` choice: sans 12, muted; the chosen one is an ink block with paper
 * text, semibold. A [count] is a muted small after the label, paper-coloured at 70% when chosen.
 */
@Composable
internal fun Chip(
    label: String,
    on: Boolean,
    texture: CanvasTexture,
    count: String? = null,
    onClick: () -> Unit,
) {
    val paper = texture.canvas.toColor()
    val muted = texture.muted.toColor()
    Row(
        modifier =
            Modifier
                .let { if (on) it.background(texture.ink.toColor()) else it }
                .clickable(onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        BasicText(
            text = label,
            style = bodyStyle(if (on) paper else muted, sizeSp = 12.0, weight = if (on) FontWeight.SemiBold else FontWeight.Normal),
        )
        if (count != null) {
            BasicText(text = count, style = bodyStyle(if (on) paper.copy(alpha = 0.7f) else muted, sizeSp = 11.0))
        }
    }
}

/** Chips that wrap onto as many lines as they need. */
@Composable
internal fun Chips(content: @Composable () -> Unit) {
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

/** The note a chosen chip leaves under the row: its name in bold, then what it means. */
@Composable
private fun ChosenNote(
    title: String,
    detail: String,
    texture: CanvasTexture,
) {
    BasicText(
        text =
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(title) }
                append(" · $detail")
            },
        style = bodyStyle(texture.muted.toColor()),
        modifier = Modifier.widthIn(max = READING_WIDTH.dp),
    )
}

/** True inside an ink block, where text is set in the canvas colour. */
private val LocalOnInk = staticCompositionLocalOf { false }

/**
 * Rows of type with whitespace around them and no box: the canvas's `choices`, 2 apart, bleeding
 * 10 either side so a chosen row's ink block has room around text that stays on the page's edge.
 */
@Composable
private fun Choices(content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.bleed(CHOICE_PADDING.dp), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
}

private const val CHOICE_PADDING = 10

/** Makes the content [amount] wider on each side than the room it is given, without moving its start. */
private fun Modifier.bleed(amount: Dp): Modifier =
    layout { measurable, constraints ->
        val extra = amount.roundToPx()
        val width = constraints.maxWidth + 2 * extra
        val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
        layout(constraints.maxWidth, placeable.height) { placeable.place(-extra, 0) }
    }

/**
 * A choice: a row of type. The chosen one is an ink block with paper text, the same selection as
 * the deck's toggles (SCREENS_V1 §3: an ink block for UI selection, never the accent).
 */
@Composable
private fun OptionCard(
    texture: CanvasTexture,
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .let { if (selected) it.background(texture.ink.toColor()) else it }
                .clickable(onClick = onClick)
                .padding(CHOICE_PADDING.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CompositionLocalProvider(LocalOnInk provides selected) { content() }
    }
}

/** [cue]'s text set in its real typeface; short cues are set large, long ones smaller (canvas: 38, 26, 19). */
@Composable
private fun Specimen(
    cue: Cue,
    color: Color,
    sizeSp: Double? = null,
) {
    val size =
        sizeSp ?: if (cue.text.length <= 2) {
            38.0
        } else if (cue.text.length <= 4) {
            26.0
        } else {
            19.0
        }
    BasicText(
        text = cue.text,
        style =
            TextStyle(
                fontFamily = BriefCueFonts.familyFor(cue.face),
                fontSize = size.sp,
                color = color,
                fontFeatureSettings = if (cue.tabular) "tnum" else null,
            ),
        maxLines = 1,
    )
}

/**
 * A specimen in a 76-wide column on the left, a title and a muted line on the right: the shape of
 * every door, use and answer. With no specimen the text takes the whole row, unless [keepColumn]
 * holds the column so names line up in a mixed list.
 */
@Composable
private fun SpecimenRow(
    cue: Cue?,
    title: String,
    detail: String,
    texture: CanvasTexture,
    extra: String? = null,
    keepColumn: Boolean = false,
    specimenSp: Double? = null,
) {
    val onInk = LocalOnInk.current
    val fg = if (onInk) texture.canvas.toColor() else texture.fg.toColor()
    val muted = if (onInk) texture.canvas.toColor().copy(alpha = ON_INK_MUTED_ALPHA) else texture.muted.toColor()
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        if (cue != null) {
            Box(modifier = Modifier.width(SPECIMEN_COLUMN.dp)) { Specimen(cue = cue, color = fg, sizeSp = specimenSp) }
        } else if (keepColumn) {
            Spacer(modifier = Modifier.width(SPECIMEN_COLUMN.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            BasicText(text = title, style = bodyStyle(fg, sizeSp = 15.0, weight = FontWeight.SemiBold))
            if (detail.isNotEmpty()) BasicText(text = detail, style = bodyStyle(muted))
            if (extra != null) {
                BasicText(
                    text = extra.uppercase(),
                    style = monoStyle(muted, sizeSp = 9.0).copy(letterSpacing = 0.08.em),
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

private const val SPECIMEN_COLUMN = 76

/** How strongly muted text shows on an ink block. */
private const val ON_INK_MUTED_ALPHA = 0.72f

/** The explorer's `.fld`: no box and no rule, and the 2 px violet outline while it has focus. */
internal fun Modifier.fieldFocus(violet: Color): Modifier =
    composed {
        var focused by remember { mutableStateOf(false) }
        onFocusChanged { focused = it.hasFocus }.then(if (focused) Modifier.border(FOCUS_OUTLINE.dp, violet) else Modifier)
    }

private const val FOCUS_OUTLINE = 2

private fun sourceLine(source: Source): String = "${source.kind.label} · ${source.citation}"

/** Where a piece of advice comes from: mono, small, not upper-cased. */
@Composable
private fun SourceLine(
    text: String,
    muted: Color,
) {
    BasicText(text = text, style = monoStyle(muted, sizeSp = 9.0).copy(letterSpacing = 0.06.em))
}

/** The face the brief's own words are set in: the chosen genre's cue face, or the default face before there is one. */
private fun briefFamily(state: BriefScreenUiState): FontFamily =
    state.model.genre
        ?.cue
        ?.face
        ?.let { BriefCueFonts.familyFor(it) } ?: FontFamily.Default

/** The brief's sentence, large, in the genre's own face. */
@Composable
private fun BriefSentence(
    state: BriefScreenUiState,
    texture: CanvasTexture,
) {
    BasicText(
        text = state.model.sentence,
        style = TextStyle(fontFamily = briefFamily(state), fontSize = 22.sp, lineHeight = 27.5.sp, color = texture.fg.toColor()),
    )
}

/**
 * A tension and what can be done about it: keep the departure as a signature, or answer the
 * question again. Both stay on the card, so a tension is never a dead end.
 */
@Composable
private fun TensionCard(
    title: String,
    body: String,
    source: Source?,
    signature: Dimension?,
    dimension: Dimension?,
    state: BriefScreenUiState,
    texture: CanvasTexture,
) {
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    // A Check report item: an 11 dp severity shape (○, info, for a tension), a mono title, one
    // sentence, then violet actions. No rule and no box.
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(text = "○", style = bodyStyle(fg, sizeSp = 11.0), modifier = Modifier.padding(top = 2.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText(text = title, style = monoStyle(fg, sizeSp = 12.0).copy(letterSpacing = 0.sp))
            BasicText(text = body, style = bodyStyle(fg))
            if (source != null) SourceLine(text = sourceLine(source), muted = muted)
            if (signature != null || dimension != null) {
                ButtonRow {
                    if (signature != null) {
                        val kept = signature in state.model.signatures
                        ActionButton(
                            label = if (kept) "Signature kept ›" else "Keep as signature ›",
                            texture = texture,
                            onClick = { state.toggleSignature(signature) },
                        )
                    }
                    if (dimension != null) {
                        ActionButton(
                            label = if (state.model.answer(dimension) == null) "Answer it ›" else "Change answer ›",
                            texture = texture,
                            onClick = { state.askAbout(dimension) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TensionCard(
    tension: Tension,
    state: BriefScreenUiState,
    texture: CanvasTexture,
) = TensionCard(
    title = tension.title,
    body = tension.body,
    source = tension.source,
    signature = tension.signature,
    dimension = tension.dimensions.firstOrNull(),
    state = state,
    texture = texture,
)

/**
 * "This is it: <genre>" under a reading (WP3 "Confirm"): the first corpus class
 * (node-economy's ten, [CORPUS_GENRE_KEYS]) among its votes, since only those have a style class
 * to confirm. Nothing if the reading named none. The style is never confirmed silently -- this is
 * the only place [BriefScreenUiState.confirmStyleClass] is ever called, and only by a tap.
 */
@Composable
private fun ConfirmStyleClassAction(
    reading: Reading?,
    state: BriefScreenUiState,
    texture: CanvasTexture,
) {
    val corpusVote = reading?.genres?.firstOrNull { it.genre.key in CORPUS_GENRE_KEYS } ?: return
    val confirmed = state.confirmedStyleClass == corpusVote.genre.key
    ActionButton(
        label = if (confirmed) "Confirmed: ${corpusVote.genre.name}" else "This is it: ${corpusVote.genre.name}",
        texture = texture,
        onClick = { state.confirmStyleClass(if (confirmed) null else corpusVote.genre.key) },
        filled = confirmed,
    )
}

// ---------------------------------------------------------------------------------------------
// Ask
// ---------------------------------------------------------------------------------------------

@Composable
private fun AskTab(
    state: BriefScreenUiState,
    position: AskPosition?,
    layout: DeckLayout,
    texture: CanvasTexture,
    wordmarkDraft: String,
    onWordmarkDraft: (String) -> Unit,
    onStartOver: () -> Unit,
) {
    if (position == null) {
        DoorList(state = state, texture = texture, onStartOver = onStartOver)
        return
    }
    val step = position.step
    if (step == null) {
        WrapUpView(state = state, texture = texture)
        return
    }
    StepView(
        step = step,
        state = state,
        layout = layout,
        texture = texture,
        wordmarkDraft = wordmarkDraft,
        onWordmarkDraft = onWordmarkDraft,
    )
    Column(modifier = Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (position.focused) {
            ButtonRow {
                ActionButton(label = "Keep my answer", texture = texture, onClick = { state.skip(step) })
            }
            return@Column
        }
        // Done is the deck's check when the question takes several answers: not drawn here as well.
        if (position.answered > 0 || (step.needsConfirm && layout.pageCarries(DeckAction.DONE))) {
            ButtonRow {
                if (position.answered > 0) ActionButton(label = "Back", texture = texture, onClick = state::goBack)
                if (step.needsConfirm && layout.pageCarries(DeckAction.DONE)) {
                    ActionButton(label = "Done", texture = texture, onClick = { state.confirm(step) }, filled = true)
                }
            }
        }
        ButtonRow {
            if (step.offersNotSure) ActionButton(label = "I'm not sure", texture = texture, onClick = { state.skip(step) })
            ActionLink(label = "Skip", texture = texture, onClick = { state.skip(step) })
        }
    }
}

/**
 * The end of a door's questions, which is where the drawing starts: the brief in one sentence,
 * the letters to draw first, the ways on from here, and what is left to settle.
 */
@Composable
private fun WrapUpView(
    state: BriefScreenUiState,
    texture: CanvasTexture,
) {
    val wrap = remember(state.door, state.model, state.drawing) { state.wrapUp() } ?: return
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    SectionLabel(text = "Your brief", texture = texture, first = true)
    BriefSentence(state = state, texture = texture)
    NoteMono(text = wrapUpSummary(wrap), texture = texture)
    Small(text = reviewerLine(state), texture = texture)
    val first = wrap.drawFirst
    if (first != null) {
        SectionLabel(
            text =
                if (state.drawing.font.glyphs
                        .isEmpty()
                ) {
                    "Draw first"
                } else {
                    "Draw next"
                },
            texture = texture,
        )
        Glyphs(text = first.glyphs.joinToString(" "), state = state, texture = texture)
        wrap.thenGenre?.let { Small(text = "then what makes it ${it.genreName}: ${it.letters}", texture = texture) }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText(text = first.title, style = bodyStyle(fg, sizeSp = 14.0, weight = FontWeight.SemiBold))
            BasicText(text = first.why, style = bodyStyle(fg))
        }
    }
    if (wrap.toSettle.isNotEmpty()) {
        SectionLabel(text = "To settle, now or later", texture = texture)
        for (tension in wrap.toSettle.take(MAX_WRAP_UP_TENSIONS)) TensionCard(tension = tension, state = state, texture = texture)
        val more = wrap.toSettle.size - MAX_WRAP_UP_TENSIONS
        if (more > 0) BasicText(text = "$more more in the Brief.", style = bodyStyle(muted))
    }
    ButtonRow(modifier = Modifier.padding(top = 14.dp)) {
        if (wrap.moreQuestions > 0) ActionButton(label = "Ask ${wrap.moreQuestions} more", texture = texture, onClick = state::askMore)
        ActionLink(label = "Try another door", texture = texture, onClick = { state.chooseDoor(null) })
    }
}

/** At most this many tensions in the wrap-up; the rest wait in the Brief. */
private const val MAX_WRAP_UP_TENSIONS = 3

private fun wrapUpSummary(wrap: WrapUp): String {
    val targets = if (wrap.targets == 1) "1 target" else "${wrap.targets} targets"
    val settle =
        when (wrap.toSettle.size) {
            0 -> "nothing to settle"
            1 -> "1 thing to settle"
            else -> "${wrap.toSettle.size} things to settle"
        }
    return "$targets · $settle"
}

@Composable
private fun DoorList(
    state: BriefScreenUiState,
    texture: CanvasTexture,
    onStartOver: () -> Unit,
) {
    Prompt(
        prompt = "Where would you like to start?",
        why = "Start from what you already know. Every door ends at the same brief; they differ in what is asked first.",
        texture = texture,
    )
    if (!state.ethos.isEmpty) {
        Small(text = "Your answers so far carry over: a door skips what is already answered.", texture = texture)
        ButtonRow { ActionLink(label = "Start over", texture = texture, onClick = onStartOver) }
    }
    Spacer(modifier = Modifier.height(10.dp))
    Choices {
        for (door in Door.entries) {
            OptionCard(texture = texture, selected = false, onClick = { state.chooseDoor(door) }) {
                SpecimenRow(cue = door.cue, title = door.label, detail = door.caption, texture = texture)
            }
        }
    }
}

/** A question, 17 sp semibold, and a muted line under it saying why it is asked. */
@Composable
private fun Prompt(
    prompt: String,
    why: String,
    texture: CanvasTexture,
) {
    Column(modifier = Modifier.padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicText(
            text = prompt,
            style = bodyStyle(texture.fg.toColor(), sizeSp = 17.0, weight = FontWeight.SemiBold).copy(lineHeight = (17 * 1.3).sp),
            modifier = Modifier.padding(top = 6.dp),
        )
        Small(text = why, texture = texture)
    }
}

/** The question and why it is asked: the engine's own words, the same the design mockup shows. */
@Composable
private fun StepPrompt(
    step: Step,
    texture: CanvasTexture,
) = Prompt(prompt = step.prompt, why = step.why, texture = texture)

/** [text] in the brief's face, large and spaced out: the letters to draw. */
@Composable
private fun Glyphs(
    text: String,
    state: BriefScreenUiState,
    texture: CanvasTexture,
    sizeSp: Double = 38.0,
) {
    BasicText(
        text = text,
        style =
            TextStyle(
                fontFamily = briefFamily(state),
                fontSize = sizeSp.sp,
                lineHeight = (sizeSp * 1.15).sp,
                letterSpacing = 0.14.em,
                color = texture.fg.toColor(),
            ),
    )
}

@Composable
private fun StepView(
    step: Step,
    state: BriefScreenUiState,
    layout: DeckLayout,
    texture: CanvasTexture,
    wordmarkDraft: String,
    onWordmarkDraft: (String) -> Unit,
) {
    when (step) {
        is Step.PickGenre -> {
            PickGenreView(step = step, state = state, texture = texture)
        }

        Step.PickUses -> {
            PickUsesView(step = step, state = state, texture = texture)
        }

        is Step.AskUse -> {
            AskUseView(step = step, state = state, texture = texture)
        }

        Step.EnterWordmark -> {
            WordmarkView(
                step = step,
                state = state,
                layout = layout,
                texture = texture,
                draft = wordmarkDraft,
                onDraft = onWordmarkDraft,
            )
        }

        is Step.PickFeelings -> {
            PickFeelingsView(step = step, state = state, texture = texture)
        }

        is Step.PickReferences -> {
            PickReferencesView(step = step, state = state, texture = texture)
        }

        is Step.ReadDrawing -> {
            ReadDrawingView(step = step, state = state, layout = layout, texture = texture)
        }

        is Step.AskDimension -> {
            AskDimensionView(step = step, state = state, texture = texture)
        }
    }
}

@Composable
private fun PickGenreView(
    step: Step.PickGenre,
    state: BriefScreenUiState,
    texture: CanvasTexture,
) {
    val chosen = state.model.genre?.key
    StepPrompt(step = step, texture = texture)
    if (step.suggestions.isNotEmpty()) {
        SectionLabel(text = "Suggested", texture = texture)
        Choices {
            for (suggestion in step.suggestions) {
                GenreCard(
                    genre = suggestion.genre,
                    note = suggestion.reason,
                    selected = chosen == suggestion.genre.key,
                    state = state,
                    texture = texture,
                )
            }
        }
    }
    for (group in GenreGroup.entries) {
        val genres = GENRES.filter { it.group == group }
        if (genres.isEmpty()) continue
        SectionLabel(text = group.title, texture = texture)
        Choices {
            for (genre in genres) {
                GenreCard(genre = genre, note = genre.oneLine, selected = chosen == genre.key, state = state, texture = texture)
            }
        }
    }
}

@Composable
private fun GenreCard(
    genre: Genre,
    note: String,
    selected: Boolean,
    state: BriefScreenUiState,
    texture: CanvasTexture,
) {
    OptionCard(texture = texture, selected = selected, onClick = { state.chooseGenre(genre.key) }) {
        SpecimenRow(cue = genre.cue, title = genre.name, detail = note, texture = texture)
    }
}

@Composable
private fun PickUsesView(
    step: Step,
    state: BriefScreenUiState,
    texture: CanvasTexture,
) {
    StepPrompt(step = step, texture = texture)
    Chips {
        for (use in USES) Chip(label = use.name, on = use.id in state.ethos.uses, texture = texture) { state.toggleUse(use.id) }
    }
    for (use in USES.filter { it.id in state.ethos.uses }) ChosenNote(title = use.name, detail = use.oneLine, texture = texture)
}

@Composable
private fun AskUseView(
    step: Step.AskUse,
    state: BriefScreenUiState,
    texture: CanvasTexture,
) {
    val chosen = state.ethos.useOptions["${step.use.id}.${step.question.id}"]
    SectionLabel(text = "For ${step.use.forWhat}", texture = texture, first = true)
    StepPrompt(step = step, texture = texture)
    Chips {
        for (option in step.question.options) {
            Chip(label = option.label, on = chosen == option.id, texture = texture) { state.answerUse(step.use, step.question, option.id) }
        }
    }
    step.question.options
        .firstOrNull { it.id == chosen }
        ?.let { ChosenNote(title = it.label, detail = it.caption, texture = texture) }
}

@Composable
private fun WordmarkView(
    step: Step,
    state: BriefScreenUiState,
    layout: DeckLayout,
    texture: CanvasTexture,
    draft: String,
    onDraft: (String) -> Unit,
) {
    val fg = texture.fg.toColor()
    StepPrompt(step = step, texture = texture)
    Box(modifier = Modifier.fillMaxWidth().fieldFocus(MeaningColors.VIOLET.forTexture(texture.id).toColor()).padding(vertical = 4.dp)) {
        BasicTextField(
            value = draft,
            onValueChange = onDraft,
            textStyle = bodyStyle(fg, sizeSp = 14.0),
            singleLine = true,
            cursorBrush = SolidColor(fg),
            modifier = Modifier.fillMaxWidth().ownsDeckKeys(),
            decorationBox = { inner ->
                Box {
                    if (draft.isEmpty()) {
                        BasicText(
                            text = "The name, as it will be set",
                            style = bodyStyle(texture.muted.toColor(), sizeSp = 14.0),
                        )
                    }
                    inner()
                }
            },
        )
    }
    // Set is the deck's check here; the button only shows where the deck is not carrying it.
    if (layout.pageCarries(DeckAction.SET_WORDMARK)) {
        ButtonRow(modifier = Modifier.padding(top = 4.dp)) {
            ActionButton(label = "Set wordmark", texture = texture, onClick = { state.setWordmark(draft) }, filled = true)
        }
    }
}

@Composable
private fun PickFeelingsView(
    step: Step,
    state: BriefScreenUiState,
    texture: CanvasTexture,
) {
    StepPrompt(step = step, texture = texture)
    SectionLabel(text = "${state.ethos.feelings.size} of 3 chosen", texture = texture)
    Chips {
        for (feeling in FEELINGS) {
            Chip(label = feeling.word, on = feeling.key in state.ethos.feelings, texture = texture) { state.toggleFeeling(feeling.key) }
        }
    }
    for (feeling in FEELINGS.filter {
        it.key in state.ethos.feelings
    }) {
        ChosenNote(title = feeling.word, detail = feeling.gloss, texture = texture)
    }
}

@Composable
private fun PickReferencesView(
    step: Step.PickReferences,
    state: BriefScreenUiState,
    texture: CanvasTexture,
) {
    val atlas = state.atlas
    val muted = texture.muted.toColor()
    var query by remember { mutableStateOf("") }
    StepPrompt(step = step, texture = texture)
    if (atlas == null) {
        BasicText(text = BRIEF_ATLAS_UNAVAILABLE_NOTE, style = bodyStyle(muted))
    }
    SectionLabel(text = "${state.ethos.references.size} of 3 chosen", texture = texture)
    if (state.ethos.references.isNotEmpty()) {
        Choices { for (family in state.ethos.references) ReferenceCard(family = family, state = state, texture = texture) }
    }
    if (atlas == null) return
    SearchField(
        value = query,
        onValueChange = { query = it },
        placeholder = "Search ${atlas.pack.faces.size} measured faces by name",
        texture = texture,
    )
    val found = remember(query, atlas) { searchFaces(atlas, query, limit = MAX_REFERENCE_RESULTS) }
    val searching = query.isNotBlank()
    val label =
        when {
            searching && found.isEmpty() -> "No measured face by that name"
            searching -> "Found · best name match first"
            step.suggestions.isNotEmpty() -> "Fit your brief so far · closest to your targets first"
            else -> "Well-known faces to start from · listed, not ranked"
        }
    val shown =
        when {
            searching -> found
            step.suggestions.isNotEmpty() -> step.suggestions.map { it.family }
            else -> step.starters
        }.filter { it !in state.ethos.references }
    SectionLabel(text = label, texture = texture)
    Choices { for (family in shown) ReferenceCard(family = family, state = state, texture = texture) }
}

/** At most this many search results at once. */
private const val MAX_REFERENCE_RESULTS = 8

/** A measured face to choose as a reference, set in its own typeface when the app carries it. */
@Composable
private fun ReferenceCard(
    family: String,
    state: BriefScreenUiState,
    texture: CanvasTexture,
) {
    val atlas = state.atlas
    val detail =
        if (atlas == null) {
            ""
        } else {
            atlas.pack.faces[family]
                ?.let { describeFace(it, atlas) }
                .orEmpty()
        }
    val cue = cueFaceOf(family)?.let { Cue(it, "Aa") }
    val chosen = family in state.ethos.references
    OptionCard(texture = texture, selected = chosen, onClick = { state.toggleReference(family) }) {
        SpecimenRow(cue = cue, title = family, detail = detail, texture = texture, keepColumn = true)
        if (chosen) AddReferenceToScrapbookAction(family = family, detail = detail, texture = texture)
    }
}

/**
 * "Add to scrapbook" under a chosen reference (WP5 "Task 1 meets the References door"): one tap
 * pins a note naming the face and its own [describeFace] line, through the same
 * [MetaChange.AddPin] path [com.asoc.typewright.ui.learn.ScrapbookTab]'s "+ note" uses. Nothing
 * with no project open: a pin needs somewhere real to live, so this reads [LocalProjectWorkspace]
 * directly rather than going through [BriefScreenUiState], which only ever persists the ethos.
 */
@Composable
private fun AddReferenceToScrapbookAction(
    family: String,
    detail: String,
    texture: CanvasTexture,
) {
    val session =
        LocalProjectWorkspace.current
            ?.current
            ?.collectAsState()
            ?.value ?: return
    var added by remember(family, session) { mutableStateOf(false) }
    // Always on ink: this action only ever renders under a chosen reference, which OptionCard
    // already shows as an ink block with paper text (CLAUDE.md law 8).
    val colour = texture.canvas.toColor().copy(alpha = ON_INK_MUTED_ALPHA)
    BasicText(
        text = if (added) "Added to scrapbook" else "Add to scrapbook",
        style = monoStyle(colour, sizeSp = 9.5),
        modifier =
            Modifier.padding(top = 4.dp).clickable(enabled = !added) {
                val id =
                    session.state.value.meta.scrapbook
                        .nextPinId()
                session.update(
                    MetaChange.AddPin(
                        referenceScrapbookPin(family = family, detail = detail, id = id),
                        image = null,
                        imageExtension = null,
                    ),
                )
                added = true
            },
    )
}

/** The pin [AddReferenceToScrapbookAction] writes: a note naming [family] and its own [describeFace] line ([detail]), or just its name when there is none (no atlas, or nothing measured). */
internal fun referenceScrapbookPin(
    family: String,
    detail: String,
    id: String,
): ScrapbookPin =
    ScrapbookPin(
        id = id,
        kind = ScrapbookPinKind.NOTE,
        captionTitle = family,
        captionSource = "reference",
        noteText = if (detail.isEmpty()) family else "$family: $detail",
        rotationDegrees = stablePinRotationDegrees(id),
    )

/** A flat, square text field with a muted placeholder. */
@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    texture: CanvasTexture,
) {
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .fieldFocus(
                    MeaningColors.VIOLET.forTexture(texture.id).toColor(),
                ).padding(top = 18.dp, bottom = 4.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = bodyStyle(fg, sizeSp = 14.0),
            singleLine = true,
            cursorBrush = SolidColor(fg),
            modifier = Modifier.fillMaxWidth().ownsDeckKeys(),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) BasicText(text = placeholder, style = bodyStyle(muted, sizeSp = 14.0))
                    inner()
                }
            },
        )
    }
}

@Composable
private fun ReadDrawingView(
    step: Step.ReadDrawing,
    state: BriefScreenUiState,
    layout: DeckLayout,
    texture: CanvasTexture,
) {
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    StepPrompt(step = step, texture = texture)
    if (state.atlas == null) {
        BasicText(text = BRIEF_ATLAS_UNAVAILABLE_NOTE, style = bodyStyle(muted))
        return
    }
    if (step.levels.isEmpty()) {
        BasicText(text = BRIEF_NO_DRAWING_NOTE, style = bodyStyle(fg))
    }
    if (step.toDraw.isNotEmpty()) {
        SectionLabel(text = "Still to draw for a surer reading", texture = texture)
        Glyphs(text = step.toDraw.toList().joinToString(" "), state = state, texture = texture)
    }
    val letters = state.drawing.font
    if (letters.glyphs.isEmpty()) {
        Small(text = "Draw them, then come back: this question waits. Or skip it and answer by eye.", texture = texture)
    }
    val reading = step.reading
    if (reading != null) {
        ReadsAs(reading = reading, measured = step.levels.values.map { it.phrase }, texture = texture)
        ConfirmStyleClassAction(reading = reading, state = state, texture = texture)
    }
    if (step.levels.isNotEmpty()) {
        SectionLabel(text = "What each measures as", texture = texture)
        for ((dimension, level) in step.levels) {
            SpecimenRow(cue = level.cue, title = dimension.title, detail = level.label, texture = texture, specimenSp = 34.0)
        }
        if (layout.pageCarries(DeckAction.USE_ANSWERS)) {
            ButtonRow {
                ActionButton(
                    label = "Use these answers",
                    texture = texture,
                    onClick = { state.adoptMeasured(step.levels) },
                    filled = true,
                )
            }
        }
    }
}

@Composable
private fun AskDimensionView(
    step: Step.AskDimension,
    state: BriefScreenUiState,
    texture: CanvasTexture,
) {
    val chosen = state.model.answer(step.dimension)
    val genreName = state.model.genre?.name
    StepPrompt(step = step, texture = texture)
    Choices {
        for (level in step.options) {
            val count = step.genreCounts[level] ?: 0
            val notes =
                listOfNotNull(
                    if (level == step.suggested) suggestedNote(step) else null,
                    if (step.genreTotal > 0 && genreName != null) "$count of ${step.genreTotal} $genreName faces measured" else null,
                ).joinToString(" · ")
            OptionCard(
                texture = texture,
                selected = chosen == level,
                onClick = { state.answerLevel(step.dimension, level) },
            ) {
                SpecimenRow(
                    cue = level.cue,
                    title = level.label,
                    detail = level.phrase,
                    texture = texture,
                    extra = notes.takeIf { it.isNotEmpty() },
                )
            }
        }
    }
}

private fun suggestedNote(step: Step.AskDimension): String =
    step.suggestedBy?.let { "suggested by ${it.kind.word}: ${it.label}" } ?: "suggested"

// ---------------------------------------------------------------------------------------------
// Brief
// ---------------------------------------------------------------------------------------------

@Composable
private fun BriefTab(
    state: BriefScreenUiState,
    texture: CanvasTexture,
) {
    val model = state.model
    BriefSentence(state = state, texture = texture)
    if (model.isEmpty) {
        Small(text = "Answer a few questions in Ask and the brief fills in here.", texture = texture)
        ButtonRow {
            ActionButton(label = "Choose a door", texture = texture, onClick = { state.selectTab(BriefScreenTab.ASK) }, filled = true)
        }
    }
    NoteMono(text = state.saveNote, texture = texture)
    NoteMono(text = declaredAndConfirmedLine(state), texture = texture)
    Small(text = reviewerLine(state), texture = texture)
    if (state.atlas == null) Small(text = BRIEF_ATLAS_UNAVAILABLE_NOTE, texture = texture)
    TargetsTable(state = state, texture = texture)
    TensionsList(state = state, texture = texture)
    ReferencesList(state = state, texture = texture)
    if (state.drawing.font.glyphs
            .isNotEmpty() && state.atlas != null
    ) {
        ReadingSection(state = state, texture = texture)
    }
    if (!model.isEmpty) {
        SectionLabel(text = "Next", texture = texture)
        Small(text = "The guide turns this brief into what to draw, in what order, and why.", texture = texture)
        ButtonRow {
            ActionButton(label = "See your guide", texture = texture, onClick = { state.selectTab(BriefScreenTab.GUIDE) }, filled = true)
        }
    }
}

/**
 * What a reviewer will check for this class, in one line: its node-economy box, with how many
 * measured families it rests on, and Google Fonts' packaging rules. Check holds the detail; it is
 * not built yet, so this line has nothing to link to.
 */
private fun reviewerLine(state: BriefScreenUiState): String {
    val genre = state.model.genre?.takeIf { it.corpus }
    val count =
        genre?.let {
            state.atlas
                ?.pack
                ?.classes
                ?.get(it.key)
                ?.n
        }
    return when {
        genre != null && count != null -> {
            "A reviewer will check ${genre.name.lowercase()}'s node-economy box (n $count) and Google Fonts' packaging rules."
        }

        else -> {
            "A reviewer will check Google Fonts' packaging rules, and a node-economy box once there is a class."
        }
    }
}

/** "declared: <genre> · confirmed: <genre or "not yet">" (WP3 "Confirm"), the Brief tab's own one-line summary of [StyleClass]. */
private fun declaredAndConfirmedLine(state: BriefScreenUiState): String {
    val declared =
        state.model.genre
            ?.takeIf { it.corpus }
            ?.name ?: "none"
    val confirmed = state.confirmedStyleClass?.let { genreByKey(it)?.name } ?: "not yet"
    return "declared: $declared · confirmed: $confirmed"
}

/** The Brief tab's own reading, once letters exist: the same [readAs] vote and confirm action the Mirror door offers, so confirming does not require a trip through Ask. */
@Composable
private fun ReadingSection(
    state: BriefScreenUiState,
    texture: CanvasTexture,
) {
    val atlas = state.atlas ?: return
    val reading = remember(state.drawing, atlas) { readAs(state.drawing.measurement, atlas) } ?: return
    val fg = texture.fg.toColor()
    SectionLabel(text = "Your letters", texture = texture)
    ReadsAs(reading = reading, measured = emptyList(), texture = texture)
    ButtonRow { ConfirmStyleClassAction(reading = reading, state = state, texture = texture) }
}

/**
 * The explorer's Economy `.reads`: "Reads as <genre>" as an ink block, how sure it is in muted
 * 11.5 (the votes among the nearest measured faces, with the runners-up), then what was measured.
 */
@Composable
private fun ReadsAs(
    reading: Reading,
    measured: List<String>,
    texture: CanvasTexture,
) {
    val top = reading.genres.firstOrNull() ?: return
    val muted = texture.muted.toColor()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicText(
            text = "Reads as ${top.genre.name}",
            style = bodyStyle(texture.canvas.toColor(), sizeSp = 12.5, weight = FontWeight.SemiBold),
            modifier = Modifier.background(texture.ink.toColor()).padding(horizontal = 6.dp, vertical = 2.dp),
        )
        val others = reading.genres.drop(1).joinToString("") { " · ${it.genre.name.lowercase()} ${it.votes}" }
        BasicText(text = "${top.votes} of the ${reading.k} nearest faces$others", style = bodyStyle(muted, sizeSp = 11.5))
        if (measured.isNotEmpty()) BasicText(text = "measured: ${measured.joinToString(" · ")}", style = bodyStyle(muted, sizeSp = 11.5))
    }
}

@Composable
private fun TargetsTable(
    state: BriefScreenUiState,
    texture: CanvasTexture,
) {
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    val order = remember { Dimension.entries.flatMap { it.features } }
    val targets =
        remember(state.model) {
            state.model.targets.values
                .sortedBy { order.indexOf(it.feature) }
        }
    SectionLabel(text = "Targets", texture = texture)
    if (targets.isEmpty()) {
        Small(text = "No targets yet: nothing has been chosen that a letter can be measured against.", texture = texture)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        for (target in targets) {
            val origin = target.origin
            val evidence =
                when {
                    origin.faces > 0 -> "${origin.faces} faces measured"
                    target.range != null -> "our heuristic"
                    else -> null
                }
            val firm = if (origin.kind == OriginKind.ANSWER && target.firm) "firm" else null
            val whereFrom = listOfNotNull("${origin.kind.word} · ${origin.label}", evidence, firm).joinToString(" · ")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BasicText(
                    text = target.feature.label,
                    style = bodyStyle(fg, weight = FontWeight.SemiBold),
                    modifier = Modifier.width(TARGET_FEATURE_COLUMN.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    BasicText(text = target.describe(), style = bodyStyle(fg).copy(fontFeatureSettings = "tnum"))
                    BasicText(text = whereFrom, style = bodyStyle(muted, sizeSp = 12.0))
                }
            }
        }
    }
}

/** 8.5 em of the 12.5 sp text beside it, as the canvas sets it. */
private const val TARGET_FEATURE_COLUMN = 106

@Composable
private fun TensionsList(
    state: BriefScreenUiState,
    texture: CanvasTexture,
) {
    val tensions = state.model.tensions
    SectionLabel(text = "Tensions", texture = texture)
    if (tensions.isEmpty()) {
        Small(text = "None found: nothing you chose pulls against anything else.", texture = texture)
        return
    }
    for (tension in tensions) TensionCard(tension = tension, state = state, texture = texture)
}

@Composable
private fun ReferencesList(
    state: BriefScreenUiState,
    texture: CanvasTexture,
) {
    val atlas = state.atlas
    val matches: List<FaceMatch> = remember(state.model) { facesLike(state.model) }
    SectionLabel(text = "Faces that already fit · closest to your targets first", texture = texture)
    if (atlas == null) {
        Small(text = "Matching real faces needs the style atlas.", texture = texture)
        return
    }
    if (matches.isEmpty()) {
        Small(text = "None yet: they appear once the brief has targets.", texture = texture)
        return
    }
    for (match in matches) {
        val face = atlas.pack.faces[match.family]
        SpecimenRow(
            cue = cueFaceOf(match.family)?.let { Cue(it, "Aa") },
            title = match.family,
            detail = face?.let { describeFace(it, atlas) }.orEmpty(),
            texture = texture,
            extra = if (match.misses.isEmpty()) null else "misses: " + match.misses.joinToString(", ") { it.label },
            specimenSp = 34.0,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Guide
// ---------------------------------------------------------------------------------------------

@Composable
private fun GuideTab(
    state: BriefScreenUiState,
    layout: DeckLayout,
    texture: CanvasTexture,
    onStartDrawing: () -> Unit,
    onOpenLearn: (() -> Unit)?,
) {
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    val model = state.model
    val drawing = state.drawing
    val items = remember(model, drawing) { guideFor(model, drawing) }
    val plan = remember(model) { planFor(model) }

    if (drawing.font.glyphs.isEmpty()) {
        BasicText(text = BRIEF_NO_DRAWING_NOTE, style = bodyStyle(fg))
    }
    for (kind in GuideKind.entries) {
        val ofKind = items.filter { it.kind == kind }
        if (ofKind.isEmpty()) continue
        SectionLabel(text = kind.word, texture = texture)
        for (item in ofKind) {
            if (kind == GuideKind.TENSION) {
                TensionCard(
                    title = item.title,
                    body = item.body,
                    source = item.source,
                    signature = item.signature,
                    dimension = item.dimension,
                    state = state,
                    texture = texture,
                )
            } else {
                Column(modifier = Modifier.padding(top = 4.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BasicText(text = item.title, style = bodyStyle(fg, sizeSp = 14.0, weight = FontWeight.SemiBold))
                    if (item.glyphs.isNotEmpty()) Glyphs(text = item.glyphs, state = state, texture = texture)
                    BasicText(text = item.body, style = bodyStyle(fg))
                    val check = item.check
                    if (check != null) NoteMono(text = check.status.word, texture = texture)
                    val source = item.source
                    if (source != null) SourceLine(text = sourceLine(source), muted = muted)
                    GuideItemAction(
                        kind = kind,
                        dimension = item.dimension,
                        state = state,
                        layout = layout,
                        texture = texture,
                        onStartDrawing = onStartDrawing,
                        onOpenLearn = onOpenLearn,
                    )
                }
            }
        }
    }
    if (items.isEmpty()) {
        Small(text = "Nothing to advise yet: choose a style or a use in Ask.", texture = texture)
        ButtonRow { ActionButton(label = "Choose a door", texture = texture, onClick = { state.selectTab(BriefScreenTab.ASK) }) }
    }
    SectionLabel(text = "Drawing plan", texture = texture)
    if (plan.isEmpty()) {
        Small(text = "No plan yet: it follows from the brief.", texture = texture)
    }
    for (step in plan) {
        val remaining = step.remaining(drawing.font)
        val status =
            when {
                step.glyphs.isEmpty() -> "action"
                remaining.isEmpty() -> "all drawn"
                else -> "${remaining.size} of ${step.glyphs.size} still to draw"
            }
        Column(modifier = Modifier.padding(top = 4.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText(text = step.title, style = bodyStyle(fg, sizeSp = 14.0, weight = FontWeight.SemiBold))
            if (step.glyphs.isNotEmpty()) Glyphs(text = step.glyphs.joinToString(" "), state = state, texture = texture, sizeSp = 30.0)
            NoteMono(text = status, texture = texture)
            BasicText(text = step.why, style = bodyStyle(fg))
            SourceLine(text = sourceLine(step.source), muted = muted)
        }
    }
    Column(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Small(text = "Draw opens on the sheet. Come back any time: the guide measures your letters as you draw.", texture = texture)
        if (layout.pageCarries(DeckAction.START_DRAWING)) {
            ButtonRow { ActionButton(label = "Start drawing", texture = texture, onClick = onStartDrawing, filled = true) }
        }
    }
}

/** What a guide item lets the person do next: draw its letters, change the brief instead of the drawing, or open its lesson. */
@Composable
private fun GuideItemAction(
    kind: GuideKind,
    dimension: Dimension?,
    state: BriefScreenUiState,
    layout: DeckLayout,
    texture: CanvasTexture,
    onStartDrawing: () -> Unit,
    onOpenLearn: (() -> Unit)?,
) {
    when {
        kind == GuideKind.NEXT && layout.pageCarries(DeckAction.START_DRAWING) -> {
            ButtonRow { ActionButton(label = "Start drawing", texture = texture, onClick = onStartDrawing, filled = true) }
        }

        kind == GuideKind.DRIFT && dimension != null -> {
            ButtonRow { ActionButton(label = "Or change the brief", texture = texture, onClick = { state.askAbout(dimension) }) }
        }

        kind == GuideKind.LEARN && onOpenLearn != null -> {
            ButtonRow { ActionButton(label = "Open Learn", texture = texture, onClick = onOpenLearn) }
        }
    }
}
