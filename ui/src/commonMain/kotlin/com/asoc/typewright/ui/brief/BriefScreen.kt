// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.brief

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
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
import com.asoc.typewright.ui.deck.ControlDeck
import com.asoc.typewright.ui.deck.DeckLeft
import com.asoc.typewright.ui.deck.DeckRight
import com.asoc.typewright.ui.deck.DeckSection
import com.asoc.typewright.ui.deck.DeckToggle
import com.asoc.typewright.ui.project.LocalProjectWorkspace
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.CanvasTextures
import com.asoc.typewright.ui.tokens.MeaningColors
import com.asoc.typewright.ui.tokens.Typography
import com.asoc.typewright.ui.tokens.toColor

/**
 * The Brief screen: find the visual language a font is aiming for, read what the answers add up
 * to, and get the guide that follows from it. It is laid out as the V1 screens canvas lays it out
 * (`ui/v1-screens/`): the page frame (title at left 22 and top 56, content from about 118), the
 * control deck at the foot, and no title bar, back button or tab row of its own. Three pages share
 * one scrolling column, and the deck's toggle arc switches between them:
 *
 * - **Ask** opens on six doors, each set in a real typeface; the chosen door asks its questions
 *   one at a time, every answer shown as a specimen in the face it was measured on. When they are
 *   through, the wrap-up sets the brief in one sentence, puts what is left to settle in front of
 *   the person, names the letters to draw first, and offers more questions for anyone who wants
 *   to refine it.
 * - **Brief** sets the ethos as one large sentence, then the targets (feature, range, where it
 *   came from), the tensions between answers (each kept as a signature or answered again), and
 *   the measured faces that already fit; it ends by pointing at the guide.
 * - **Guide** lists what to do next, grouped by the kind of advice, then the drawing plan, and
 *   ends in [onStartDrawing]: the brief always leads to drawing.
 *
 * The deck's left circle is Close on the doors and Back everywhere else, where Back steps out of
 * the brief one place at a time ([BriefScreenUiState.back]) and leaves through [onBack] only from
 * the doors' side. The right circle is Next on the doors, which goes on to the guide once there is
 * an answer, and the ⋯ menu elsewhere. A chosen answer is an ink block with paper text; options are
 * rows of type with no box around them. There is no red anywhere here but the Close circle. Where
 * the style atlas cannot load, or no project is open, the screen says so in words. [onOpenLearn],
 * when given, opens the lesson a guide item points to.
 */
@Composable
fun BriefScreen(
    modifier: Modifier = Modifier,
    texture: CanvasTexture = CanvasTextures.DEFAULT,
    onBack: () -> Unit = {},
    state: BriefScreenUiState = rememberBriefScreenUiState(),
    onStartDrawing: () -> Unit = onBack,
    onOpenLearn: (() -> Unit)? = null,
) {
    val position =
        remember(state.door, state.ethos, state.model, state.drawing, state.refining, state.focus) { state.askPosition() }
    val atDoors = state.tab == BriefScreenTab.ASK && position == null
    val onBoard = state.newProject
    val nothingAnswered = state.ethos.isEmpty
    val session =
        LocalProjectWorkspace.current
            ?.current
            ?.collectAsState()
            ?.value
    val draft = remember(session) { NewProjectDraft(session) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmingStartOver by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxSize().background(texture.canvas.toColor())) {
        Column(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = PAGE_WIDTH.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = PAGE_SIDE.dp, end = PAGE_SIDE.dp, top = PAGE_TOP.dp, bottom = PAGE_BOTTOM.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PageHeader(title = if (onBoard) "New project" else "Brief", subtitle = state.subtitle, texture = texture)
            when (state.tab) {
                BriefScreenTab.ASK -> {
                    if (onBoard) {
                        NewProjectStep(state = state, draft = draft, texture = texture)
                    } else {
                        AskTab(
                            state = state,
                            position = position,
                            texture = texture,
                            confirmingStartOver = confirmingStartOver,
                            onConfirmingStartOver = { confirmingStartOver = it },
                            onStartDrawing = onStartDrawing,
                        )
                    }
                }

                BriefScreenTab.BRIEF -> {
                    BriefTab(state = state, texture = texture)
                }

                BriefScreenTab.GUIDE -> {
                    GuideTab(state = state, texture = texture, onStartDrawing = onStartDrawing, onOpenLearn = onOpenLearn)
                }
            }
        }
        ControlDeck(
            texture = texture,
            modifier = Modifier.align(Alignment.BottomCenter),
            section = DeckSection.HOME,
            left = if (atDoors) DeckLeft.CLOSE else DeckLeft.BACK,
            right = if (atDoors) DeckRight.NEXT else DeckRight.MENU,
            toggles = if (atDoors) emptyList() else BriefScreenTab.ORDERED.map { DeckToggle(it.label, on = it == state.tab) },
            dimSections = true,
            rightDimmed = atDoors && nothingAnswered && !onBoard,
            onLeft = { if (atDoors || !state.back()) onBack() },
            onRight = {
                if (onBoard) {
                    draft.commit()
                    state.finishNewProject()
                } else if (atDoors) {
                    if (!nothingAnswered) state.selectTab(BriefScreenTab.GUIDE)
                } else {
                    menuOpen = !menuOpen
                }
            },
            onToggle = { state.selectTab(BriefScreenTab.ORDERED[it]) },
        )
        if (menuOpen) {
            BriefMenu(
                texture = texture,
                nothingAnswered = nothingAnswered,
                onDismiss = { menuOpen = false },
                onStartOver = {
                    menuOpen = false
                    if (!nothingAnswered) {
                        state.selectTab(BriefScreenTab.ASK)
                        state.chooseDoor(null)
                        confirmingStartOver = true
                    }
                },
            )
        }
    }
}

/** The page frame, from the canvas: 390 wide, title at left 22 and top 56, room for the deck below. */
private const val PAGE_WIDTH = 390
private const val PAGE_SIDE = 22
private const val PAGE_TOP = 56
private const val PAGE_BOTTOM = 214

/** The page title (15 sp, semibold) and, under it, where in the page the person is (mono, small, muted). */
@Composable
private fun PageHeader(
    title: String,
    subtitle: String,
    texture: CanvasTexture,
) {
    Column(modifier = Modifier.padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        BasicText(
            text = title,
            style =
                TextStyle(
                    fontFamily = FontFamily.Default,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = texture.fg.toColor(),
                ),
        )
        BasicText(text = subtitle.uppercase(), style = monoStyle(texture.muted.toColor()))
    }
}

/**
 * The ⋯ menu, which rises from the deck's right circle: ink blocks with paper text, a mono second
 * line under each. A tap anywhere else closes it. The command palette is not offered here yet,
 * because this screen is not handed a way to open it.
 */
@Composable
private fun BriefMenu(
    texture: CanvasTexture,
    nothingAnswered: Boolean,
    onDismiss: () -> Unit,
    onStartOver: () -> Unit,
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
                    .width(MENU_WIDTH.dp)
                    .background(texture.ink.toColor())
                    .clickable(onClick = onStartOver)
                    .padding(horizontal = 13.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            BasicText(text = "Start over", style = bodyStyle(onInk, sizeSp = 13.0))
            BasicText(
                text = (if (nothingAnswered) "Nothing answered yet" else "Clear every answer").uppercase(),
                style = monoStyle(onInk.copy(alpha = ON_INK_MUTED_ALPHA), sizeSp = 9.0).copy(letterSpacing = 0.12.em),
            )
        }
    }
}

private const val MENU_WIDTH = 230
private const val MENU_BOTTOM = 136

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
private fun Small(
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

/** A square, flat button: an ink outline, or an ink block with paper text when [filled]. */
@Composable
private fun ActionButton(
    label: String,
    texture: CanvasTexture,
    onClick: () -> Unit,
    filled: Boolean = false,
) {
    val ink = texture.ink.toColor()
    val canvas = texture.canvas.toColor()
    Box(
        modifier =
            Modifier
                .let { if (filled) it.background(ink) else it.border(1.dp, ink) }
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        BasicText(text = label.uppercase(), style = monoStyle(if (filled) canvas else ink, sizeSp = 10.0))
    }
}

/** The tertiary action: the canvas's violet link, sans, semibold, with a trailing ›. */
@Composable
private fun ActionLink(
    label: String,
    texture: CanvasTexture,
    onClick: () -> Unit,
) {
    BasicText(
        text = "$label ›",
        style = bodyStyle(MeaningColors.VIOLET.forTexture(texture.id).toColor(), weight = FontWeight.SemiBold),
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 2.dp, vertical = 4.dp),
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

/** A chip: mono, no border; the chosen one is an ink block with paper text. */
@Composable
internal fun Chip(
    label: String,
    on: Boolean,
    texture: CanvasTexture,
    onClick: () -> Unit,
) {
    val ink = texture.ink.toColor()
    Box(
        modifier =
            Modifier
                .let { if (on) it.background(ink) else it }
                .clickable(onClick = onClick)
                .padding(horizontal = 9.dp, vertical = 6.dp),
    ) {
        BasicText(
            text = label.uppercase(),
            style = monoStyle(if (on) texture.canvas.toColor() else ink, sizeSp = 10.0).copy(letterSpacing = 0.12.em),
        )
    }
}

/** Chips that wrap onto as many lines as they need. */
@Composable
internal fun Chips(content: @Composable () -> Unit) {
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
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

/** A rule down the left edge: a tension reads as one thing to decide, without a box around it. */
private fun Modifier.leftRule(color: Color): Modifier =
    drawBehind { drawRect(color = color, size = Size(LEFT_RULE_WIDTH.dp.toPx(), size.height)) }

/** A text field's underline (ink at 35%), in place of a box. */
internal fun Modifier.underline(color: Color): Modifier =
    drawBehind {
        val stroke = 1.dp.toPx()
        drawRect(color = color.copy(alpha = FIELD_LINE_ALPHA), topLeft = Offset(0f, size.height - stroke), size = Size(size.width, stroke))
    }

private const val FIELD_LINE_ALPHA = 0.35f
private const val LEFT_RULE_WIDTH = 2

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
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .leftRule(texture.ink.toColor())
                .padding(start = 14.dp, top = 2.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BasicText(text = title, style = bodyStyle(fg, sizeSp = 13.0, weight = FontWeight.SemiBold))
        BasicText(text = body, style = bodyStyle(fg))
        if (source != null) SourceLine(text = sourceLine(source), muted = muted)
        if (signature != null || dimension != null) {
            ButtonRow(modifier = Modifier.padding(top = 4.dp)) {
                if (signature != null) {
                    val kept = signature in state.model.signatures
                    ActionButton(
                        label = if (kept) "Signature kept" else "Keep as signature",
                        texture = texture,
                        onClick = { state.toggleSignature(signature) },
                        filled = kept,
                    )
                }
                if (dimension != null) {
                    ActionButton(
                        label = if (state.model.answer(dimension) == null) "Answer it" else "Change answer",
                        texture = texture,
                        onClick = { state.askAbout(dimension) },
                    )
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
    texture: CanvasTexture,
    confirmingStartOver: Boolean,
    onConfirmingStartOver: (Boolean) -> Unit,
    onStartDrawing: () -> Unit,
) {
    if (position == null) {
        DoorList(
            state = state,
            texture = texture,
            confirmingStartOver = confirmingStartOver,
            onConfirmingStartOver = onConfirmingStartOver,
        )
        return
    }
    val step = position.step
    if (step == null) {
        WrapUpView(state = state, texture = texture)
        return
    }
    StepView(step = step, state = state, texture = texture, onStartDrawing = onStartDrawing)
    Column(modifier = Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (position.focused) {
            ButtonRow {
                ActionButton(label = "Back", texture = texture, onClick = state::leaveFocus)
                ActionButton(label = "Keep my answer", texture = texture, onClick = { state.skip(step) })
            }
            return@Column
        }
        val canGoBack = position.answered > 0
        if (canGoBack || step.needsConfirm) {
            ButtonRow {
                if (canGoBack) ActionButton(label = "Back", texture = texture, onClick = state::goBack)
                if (step.needsConfirm) ActionButton(label = "Done", texture = texture, onClick = { state.confirm(step) }, filled = true)
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
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText(text = first.title, style = bodyStyle(fg, sizeSp = 14.0, weight = FontWeight.SemiBold))
            BasicText(text = first.why, style = bodyStyle(fg))
        }
    }
    ButtonRow(modifier = Modifier.padding(top = 4.dp)) {
        ActionButton(label = "See your guide", texture = texture, onClick = { state.selectTab(BriefScreenTab.GUIDE) }, filled = true)
        ActionButton(label = "Read the brief", texture = texture, onClick = { state.selectTab(BriefScreenTab.BRIEF) })
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
    confirmingStartOver: Boolean,
    onConfirmingStartOver: (Boolean) -> Unit,
) {
    Prompt(
        prompt = "Where would you like to start?",
        why = "Start from what you already know. Every door ends at the same brief; they differ in what is asked first.",
        texture = texture,
    )
    if (!state.ethos.isEmpty) {
        Small(text = "Your answers so far carry over: a door skips what is already answered.", texture = texture)
        if (confirmingStartOver) {
            Small(text = "Clear every answer in this brief?", texture = texture)
            ButtonRow {
                ActionButton(
                    label = "Clear answers",
                    texture = texture,
                    onClick = {
                        onConfirmingStartOver(false)
                        state.startOver()
                    },
                    filled = true,
                )
                ActionButton(label = "Keep them", texture = texture, onClick = { onConfirmingStartOver(false) })
            }
        } else {
            ButtonRow { ActionLink(label = "Start over", texture = texture, onClick = { onConfirmingStartOver(true) }) }
        }
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
    texture: CanvasTexture,
    onStartDrawing: () -> Unit,
) {
    when (step) {
        is Step.PickGenre -> PickGenreView(step = step, state = state, texture = texture)
        Step.PickUses -> PickUsesView(step = step, state = state, texture = texture)
        is Step.AskUse -> AskUseView(step = step, state = state, texture = texture)
        Step.EnterWordmark -> WordmarkView(step = step, state = state, texture = texture)
        is Step.PickFeelings -> PickFeelingsView(step = step, state = state, texture = texture)
        is Step.PickReferences -> PickReferencesView(step = step, state = state, texture = texture)
        is Step.ReadDrawing -> ReadDrawingView(step = step, state = state, texture = texture, onStartDrawing = onStartDrawing)
        is Step.AskDimension -> AskDimensionView(step = step, state = state, texture = texture)
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
    texture: CanvasTexture,
) {
    var draft by remember { mutableStateOf(state.ethos.wordmark.orEmpty()) }
    val fg = texture.fg.toColor()
    StepPrompt(step = step, texture = texture)
    Box(modifier = Modifier.fillMaxWidth().underline(texture.ink.toColor()).padding(vertical = 4.dp)) {
        BasicTextField(
            value = draft,
            onValueChange = { draft = it },
            textStyle = bodyStyle(fg, sizeSp = 14.0),
            singleLine = true,
            cursorBrush = SolidColor(fg),
            modifier = Modifier.fillMaxWidth(),
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
    ButtonRow(modifier = Modifier.padding(top = 4.dp)) {
        ActionButton(label = "Set wordmark", texture = texture, onClick = { state.setWordmark(draft) }, filled = true)
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
            searching -> "Found"
            step.suggestions.isNotEmpty() -> "Fit your brief so far"
            else -> "Well-known faces to start from"
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
    Box(modifier = Modifier.fillMaxWidth().underline(texture.ink.toColor()).padding(top = 18.dp, bottom = 4.dp)) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = bodyStyle(fg, sizeSp = 14.0),
            singleLine = true,
            cursorBrush = SolidColor(fg),
            modifier = Modifier.fillMaxWidth(),
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
    texture: CanvasTexture,
    onStartDrawing: () -> Unit,
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
        ButtonRow { ActionButton(label = "Start drawing", texture = texture, onClick = onStartDrawing, filled = true) }
    }
    val reading = step.reading
    if (reading != null) {
        SectionLabel(text = "Reads as", texture = texture)
        for (vote in reading.genres) {
            BasicText(
                text = "${vote.genre.name}: ${vote.votes} of the ${reading.k} nearest faces",
                style = bodyStyle(fg),
            )
        }
        ConfirmStyleClassAction(reading = reading, state = state, texture = texture)
    }
    if (step.levels.isNotEmpty()) {
        SectionLabel(text = "What each measures as", texture = texture)
        for ((dimension, level) in step.levels) {
            SpecimenRow(cue = level.cue, title = dimension.title, detail = level.label, texture = texture, specimenSp = 34.0)
        }
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
    SectionLabel(text = "Your letters read as", texture = texture)
    for (vote in reading.genres) {
        BasicText(text = "${vote.genre.name}: ${vote.votes} of the ${reading.k} nearest faces", style = bodyStyle(fg))
    }
    ButtonRow { ConfirmStyleClassAction(reading = reading, state = state, texture = texture) }
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
    SectionLabel(text = "Faces that already fit", texture = texture)
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
        ButtonRow { ActionButton(label = "Start drawing", texture = texture, onClick = onStartDrawing, filled = true) }
    }
}

/** What a guide item lets the person do next: draw its letters, change the brief instead of the drawing, or open its lesson. */
@Composable
private fun GuideItemAction(
    kind: GuideKind,
    dimension: Dimension?,
    state: BriefScreenUiState,
    texture: CanvasTexture,
    onStartDrawing: () -> Unit,
    onOpenLearn: (() -> Unit)?,
) {
    when {
        kind == GuideKind.NEXT -> {
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
