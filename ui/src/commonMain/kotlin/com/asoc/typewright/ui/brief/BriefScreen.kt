// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.brief

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.CanvasTextures
import com.asoc.typewright.ui.tokens.Typography
import com.asoc.typewright.ui.tokens.toColor

/**
 * The Brief screen: find the visual language a font is aiming for, read what the answers add up
 * to, and get the guide that follows from it. Three toggles share one scrolling page:
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
 * A chosen answer, like the chosen tab, is an ink block with paper text; options are rows of type
 * with no box around them. There is no red anywhere here. Where the style atlas cannot load, or no project is
 * open, the screen says so in words. [onOpenLearn], when given, opens the lesson a guide item
 * points to.
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
    Column(modifier = modifier.fillMaxSize().background(texture.canvas.toColor())) {
        BriefHeaderBar(texture = texture, onBack = onBack)
        BriefTabsRow(texture = texture, selected = state.tab, onSelect = state::selectTab)
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (state.tab) {
                BriefScreenTab.ASK -> {
                    AskTab(state = state, texture = texture, onStartDrawing = onStartDrawing)
                }

                BriefScreenTab.BRIEF -> {
                    BriefTab(state = state, texture = texture)
                }

                BriefScreenTab.GUIDE -> {
                    GuideTab(state = state, texture = texture, onStartDrawing = onStartDrawing, onOpenLearn = onOpenLearn)
                }
            }
        }
    }
}

@Composable
private fun BriefHeaderBar(
    texture: CanvasTexture,
    onBack: () -> Unit,
) {
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 16.dp),
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
                text = "Brief",
                style = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = fg),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                text = BRIEF_SCREEN_SUBTITLE,
                style = Typography.mono(sizeSp = 11.0).copy(color = muted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The three toggles as one segmented row; the chosen one is an ink block with paper text. */
@Composable
private fun BriefTabsRow(
    texture: CanvasTexture,
    selected: BriefScreenTab,
    onSelect: (BriefScreenTab) -> Unit,
) {
    val ink = texture.ink.toColor()
    val canvas = texture.canvas.toColor()
    val muted = texture.muted.toColor()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 10.dp),
    ) {
        for (tab in BriefScreenTab.ORDERED) {
            val isSelected = tab == selected
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .let { if (isSelected) it.background(ink) else it }
                        .clickable { onSelect(tab) }
                        .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = tab.label,
                    style =
                        TextStyle(
                            fontFamily = FontFamily.Default,
                            fontSize = 12.5.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) canvas else muted,
                        ),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------------------------

private fun bodyStyle(
    color: Color,
    sizeSp: Double = 12.5,
    weight: FontWeight = FontWeight.Normal,
): TextStyle =
    TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = sizeSp.sp,
        lineHeight = (sizeSp * 1.4).sp,
        fontWeight = weight,
        color = color,
    )

private fun monoStyle(
    color: Color,
    sizeSp: Double = 9.5,
): TextStyle = Typography.mono(sizeSp = sizeSp).copy(color = color)

/** A small uppercase mono label. */
@Composable
private fun SectionLabel(
    text: String,
    texture: CanvasTexture,
) {
    BasicText(text = text.uppercase(), style = monoStyle(texture.muted.toColor()))
}

@Composable
private fun Rule(texture: CanvasTexture) {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(texture.line.toColor()))
}

/** A square, flat button: outlined, or an ink block when [filled]. */
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
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        BasicText(text = label.uppercase(), style = monoStyle(if (filled) canvas else texture.fg.toColor(), sizeSp = 10.0))
    }
}

/** True inside an ink block, where text is set in the canvas colour. */
private val LocalOnInk = staticCompositionLocalOf { false }

/**
 * A choice: a row of type with whitespace around it, no box. The chosen one is an ink block with
 * paper text, the same selection as the deck's toggles (SCREENS_V1 §3: an ink block for UI
 * selection, never the accent).
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
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CompositionLocalProvider(LocalOnInk provides selected) { content() }
    }
}

/** [cue]'s text set in its real typeface. */
@Composable
private fun Specimen(
    cue: Cue,
    sizeSp: Double,
    color: Color,
) {
    BasicText(
        text = cue.text,
        style =
            TextStyle(
                fontFamily = BriefCueFonts.familyFor(cue.face),
                fontSize = sizeSp.sp,
                color = color,
                fontFeatureSettings = if (cue.tabular) "tnum" else null,
            ),
        maxLines = 1,
    )
}

/** A specimen on the left, a title and a muted line on the right: the shape of every door, use and answer. */
@Composable
private fun SpecimenRow(
    cue: Cue?,
    title: String,
    detail: String,
    texture: CanvasTexture,
    extra: String? = null,
) {
    val onInk = LocalOnInk.current
    val fg = if (onInk) texture.canvas.toColor() else texture.fg.toColor()
    val muted = if (onInk) texture.canvas.toColor().copy(alpha = ON_INK_MUTED_ALPHA) else texture.muted.toColor()
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (cue != null) {
            Box(modifier = Modifier.width(84.dp)) { Specimen(cue = cue, sizeSp = 26.0, color = fg) }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(text = title, style = bodyStyle(fg, sizeSp = 13.0, weight = FontWeight.SemiBold))
            if (detail.isNotEmpty()) BasicText(text = detail, style = bodyStyle(muted, sizeSp = 11.5))
            if (extra != null) BasicText(text = extra, style = monoStyle(muted))
        }
    }
}

/** How strongly muted text shows on an ink block. */
private const val ON_INK_MUTED_ALPHA = 0.72f

/** A rule down the left edge: a tension reads as one thing to decide, without a box around it. */
private fun Modifier.leftRule(color: Color): Modifier =
    drawBehind { drawRect(color = color, size = Size(LEFT_RULE_WIDTH.dp.toPx(), size.height)) }

/** A text field's underline, in place of a box. */
private fun Modifier.underline(color: Color): Modifier =
    drawBehind {
        val stroke = 1.dp.toPx()
        drawRect(color = color, topLeft = Offset(0f, size.height - stroke), size = Size(size.width, stroke))
    }

private const val LEFT_RULE_WIDTH = 2

private fun sourceLine(source: Source): String = "${source.kind.label} · ${source.citation}"

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
        style = TextStyle(fontFamily = briefFamily(state), fontSize = 23.sp, lineHeight = 31.sp, color = texture.fg.toColor()),
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
        modifier = Modifier.fillMaxWidth().leftRule(texture.ink.toColor()).padding(start = 14.dp, top = 2.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BasicText(text = title, style = bodyStyle(fg, sizeSp = 13.0, weight = FontWeight.SemiBold))
        BasicText(text = body, style = bodyStyle(fg, sizeSp = 12.0))
        if (source != null) BasicText(text = sourceLine(source), style = monoStyle(muted, sizeSp = 9.0))
        if (signature != null || dimension != null) {
            Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

/** Clears every answer, after a second tap: the ink-block confirmation SCREENS_V1 asks for before anything is lost. */
@Composable
private fun StartOver(
    state: BriefScreenUiState,
    texture: CanvasTexture,
) {
    var confirming by remember { mutableStateOf(false) }
    if (!confirming) {
        ActionButton(label = "Start over", texture = texture, onClick = { confirming = true })
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(text = "Clear every answer in this brief?", style = bodyStyle(texture.fg.toColor(), sizeSp = 12.0))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(
                label = "Clear answers",
                texture = texture,
                onClick = {
                    confirming = false
                    state.startOver()
                },
                filled = true,
            )
            ActionButton(label = "Keep them", texture = texture, onClick = { confirming = false })
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Ask
// ---------------------------------------------------------------------------------------------

@Composable
private fun AskTab(
    state: BriefScreenUiState,
    texture: CanvasTexture,
    onStartDrawing: () -> Unit,
) {
    val position =
        remember(state.door, state.ethos, state.model, state.drawing, state.refining, state.focus) { state.askPosition() }
    if (position == null) {
        DoorList(state = state, texture = texture)
        return
    }
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    val step = position.step
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (position.focused) {
            ActionButton(label = "Back", texture = texture, onClick = state::leaveFocus)
            Column(modifier = Modifier.weight(1f)) {
                BasicText(text = "Changing one answer", style = bodyStyle(fg, sizeSp = 13.0, weight = FontWeight.SemiBold))
                BasicText(text = "Your other answers stay as they are", style = monoStyle(muted))
            }
        } else {
            ActionButton(label = "Doors", texture = texture, onClick = { state.chooseDoor(null) })
            Column(modifier = Modifier.weight(1f)) {
                BasicText(text = state.door?.label.orEmpty(), style = bodyStyle(fg, sizeSp = 13.0, weight = FontWeight.SemiBold))
                BasicText(text = progressWords(position), style = monoStyle(muted))
            }
        }
    }
    Rule(texture)
    if (step == null) {
        WrapUpView(state = state, texture = texture)
        return
    }
    StepView(step = step, state = state, texture = texture, onStartDrawing = onStartDrawing)
    Rule(texture)
    val canGoBack = !position.focused && position.answered > 0
    if (canGoBack || step.needsConfirm) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canGoBack) ActionButton(label = "Back", texture = texture, onClick = state::goBack)
            if (step.needsConfirm) ActionButton(label = "Done", texture = texture, onClick = { state.confirm(step) }, filled = true)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (position.focused) {
            ActionButton(label = "Keep my answer", texture = texture, onClick = { state.skip(step) })
        } else {
            if (step.offersNotSure) ActionButton(label = "I'm not sure", texture = texture, onClick = { state.skip(step) })
            ActionButton(label = "Skip", texture = texture, onClick = { state.skip(step) })
        }
    }
}

/**
 * The end of a door's questions, which is where the drawing starts: the brief in one sentence,
 * what is left to settle, the letters to draw first, and the ways on from here.
 */
@Composable
private fun WrapUpView(
    state: BriefScreenUiState,
    texture: CanvasTexture,
) {
    val wrap = remember(state.door, state.model, state.drawing) { state.wrapUp() } ?: return
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    SectionLabel(text = "Your brief", texture = texture)
    BriefSentence(state = state, texture = texture)
    BasicText(text = wrapUpSummary(wrap), style = monoStyle(muted))
    val first = wrap.drawFirst
    if (first != null) {
        Rule(texture)
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
        BasicText(
            text = first.glyphs.joinToString(" "),
            style = TextStyle(fontFamily = briefFamily(state), fontSize = 34.sp, lineHeight = 42.sp, color = fg),
        )
        BasicText(text = first.title, style = bodyStyle(fg, sizeSp = 13.0, weight = FontWeight.SemiBold))
        BasicText(text = first.why, style = bodyStyle(fg, sizeSp = 12.0))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionButton(label = "See your guide", texture = texture, onClick = { state.selectTab(BriefScreenTab.GUIDE) }, filled = true)
        ActionButton(label = "Read the brief", texture = texture, onClick = { state.selectTab(BriefScreenTab.BRIEF) })
    }
    if (wrap.toSettle.isNotEmpty()) {
        Rule(texture)
        SectionLabel(text = "To settle, now or later", texture = texture)
        for (tension in wrap.toSettle.take(MAX_WRAP_UP_TENSIONS)) TensionCard(tension = tension, state = state, texture = texture)
        val more = wrap.toSettle.size - MAX_WRAP_UP_TENSIONS
        if (more > 0) BasicText(text = "$more more in the Brief", style = bodyStyle(muted, sizeSp = 11.5))
    }
    Rule(texture)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (wrap.moreQuestions > 0) ActionButton(label = "Ask ${wrap.moreQuestions} more", texture = texture, onClick = state::askMore)
        ActionButton(label = "Try another door", texture = texture, onClick = { state.chooseDoor(null) })
    }
}

/** Where the person is, in words: the questions adapt to the answers, so a running total would mislead. */
private fun progressWords(position: AskPosition): String =
    if (position.step == null) "${position.total} asked" else "question ${position.answered + 1}"

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
) {
    BasicText(text = "Where would you like to start?", style = bodyStyle(texture.fg.toColor(), sizeSp = 16.0, weight = FontWeight.SemiBold))
    BasicText(
        text = "Every door ends at the same brief. They differ in what is asked first.",
        style = bodyStyle(texture.muted.toColor(), sizeSp = 11.5),
    )
    if (!state.ethos.isEmpty) {
        BasicText(
            text = "Your answers so far carry over: a door skips what is already answered.",
            style = bodyStyle(texture.muted.toColor(), sizeSp = 11.5),
        )
        StartOver(state = state, texture = texture)
    }
    for (door in Door.entries) {
        OptionCard(texture = texture, selected = false, onClick = { state.chooseDoor(door) }) {
            SpecimenRow(cue = door.cue, title = door.label, detail = door.caption, texture = texture)
        }
    }
}

/** The question and why it is asked: the engine's own words, the same the design mockup shows. */
@Composable
private fun StepPrompt(
    step: Step,
    texture: CanvasTexture,
) {
    BasicText(text = step.prompt, style = bodyStyle(texture.fg.toColor(), sizeSp = 16.0, weight = FontWeight.SemiBold))
    BasicText(text = step.why, style = bodyStyle(texture.muted.toColor(), sizeSp = 11.5))
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
    for (group in GenreGroup.entries) {
        val genres = GENRES.filter { it.group == group }
        if (genres.isEmpty()) continue
        SectionLabel(text = group.title, texture = texture)
        for (genre in genres) {
            GenreCard(genre = genre, note = genre.oneLine, selected = chosen == genre.key, state = state, texture = texture)
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
    for (use in USES) {
        OptionCard(texture = texture, selected = use.id in state.ethos.uses, onClick = { state.toggleUse(use.id) }) {
            SpecimenRow(cue = use.cue, title = use.name, detail = use.oneLine, texture = texture)
        }
    }
}

@Composable
private fun AskUseView(
    step: Step.AskUse,
    state: BriefScreenUiState,
    texture: CanvasTexture,
) {
    val chosen = state.ethos.useOptions["${step.use.id}.${step.question.id}"]
    SectionLabel(text = "For ${step.use.forWhat}", texture = texture)
    StepPrompt(step = step, texture = texture)
    for (option in step.question.options) {
        OptionCard(
            texture = texture,
            selected = chosen == option.id,
            onClick = { state.answerUse(step.use, step.question, option.id) },
        ) {
            SpecimenRow(cue = null, title = option.label, detail = option.caption, texture = texture)
        }
    }
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
    Box(modifier = Modifier.fillMaxWidth().underline(texture.ink.toColor()).padding(vertical = 10.dp)) {
        BasicTextField(
            value = draft,
            onValueChange = { draft = it },
            textStyle = bodyStyle(fg, sizeSp = 16.0),
            singleLine = true,
            cursorBrush = SolidColor(fg),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    ActionButton(label = "Set wordmark", texture = texture, onClick = { state.setWordmark(draft) }, filled = true)
}

@Composable
private fun PickFeelingsView(
    step: Step,
    state: BriefScreenUiState,
    texture: CanvasTexture,
) {
    StepPrompt(step = step, texture = texture)
    SectionLabel(text = "${state.ethos.feelings.size} of 3 chosen", texture = texture)
    for (feeling in FEELINGS) {
        OptionCard(texture = texture, selected = feeling.key in state.ethos.feelings, onClick = { state.toggleFeeling(feeling.key) }) {
            SpecimenRow(cue = null, title = feeling.word, detail = feeling.gloss, texture = texture)
        }
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
        BasicText(text = BRIEF_ATLAS_UNAVAILABLE_NOTE, style = bodyStyle(muted, sizeSp = 11.5))
    }
    SectionLabel(text = "${state.ethos.references.size} of 3 chosen", texture = texture)
    for (family in state.ethos.references) ReferenceCard(family = family, state = state, texture = texture)
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
    for (family in shown) ReferenceCard(family = family, state = state, texture = texture)
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
    OptionCard(texture = texture, selected = family in state.ethos.references, onClick = { state.toggleReference(family) }) {
        SpecimenRow(cue = cue, title = family, detail = detail, texture = texture)
    }
}

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
    Box(modifier = Modifier.fillMaxWidth().underline(texture.ink.toColor()).padding(vertical = 10.dp)) {
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
        BasicText(text = BRIEF_ATLAS_UNAVAILABLE_NOTE, style = bodyStyle(muted, sizeSp = 11.5))
        return
    }
    if (step.levels.isEmpty()) {
        BasicText(text = BRIEF_NO_DRAWING_NOTE, style = bodyStyle(fg))
    }
    if (step.toDraw.isNotEmpty()) {
        SectionLabel(text = "Still to draw for a surer reading", texture = texture)
        BasicText(text = step.toDraw.toList().joinToString(" "), style = bodyStyle(fg, sizeSp = 16.0))
    }
    val letters = state.drawing.font
    if (letters.glyphs.isEmpty()) {
        BasicText(
            text = "Draw them, then come back: this question waits. Or skip it and answer by eye.",
            style = bodyStyle(muted, sizeSp = 11.5),
        )
        ActionButton(label = "Start drawing", texture = texture, onClick = onStartDrawing, filled = true)
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
            SpecimenRow(cue = level.cue, title = dimension.title, detail = level.label, texture = texture)
        }
        ActionButton(label = "Use these answers", texture = texture, onClick = { state.adoptMeasured(step.levels) }, filled = true)
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
    val muted = texture.muted.toColor()
    BriefSentence(state = state, texture = texture)
    if (model.isEmpty) {
        BasicText(text = "Answer a few questions in Ask and the brief fills in here.", style = bodyStyle(muted, sizeSp = 11.5))
        ActionButton(label = "Choose a door", texture = texture, onClick = { state.selectTab(BriefScreenTab.ASK) }, filled = true)
    }
    BasicText(text = state.saveNote.uppercase(), style = monoStyle(muted))
    BasicText(text = declaredAndConfirmedLine(state), style = monoStyle(muted))
    if (state.atlas == null) {
        BasicText(text = BRIEF_ATLAS_UNAVAILABLE_NOTE, style = bodyStyle(muted, sizeSp = 11.5))
    }
    Rule(texture)
    TargetsTable(state = state, texture = texture)
    TensionsList(state = state, texture = texture)
    ReferencesList(state = state, texture = texture)
    if (state.drawing.font.glyphs
            .isNotEmpty() && state.atlas != null
    ) {
        Rule(texture)
        ReadingSection(state = state, texture = texture)
    }
    if (!model.isEmpty) {
        Rule(texture)
        SectionLabel(text = "Next", texture = texture)
        BasicText(text = "The guide turns this brief into what to draw, in what order, and why.", style = bodyStyle(muted, sizeSp = 11.5))
        ActionButton(label = "See your guide", texture = texture, onClick = { state.selectTab(BriefScreenTab.GUIDE) }, filled = true)
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
    ConfirmStyleClassAction(reading = reading, state = state, texture = texture)
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
        BasicText(
            text = "No targets yet: nothing has been chosen that a letter can be measured against.",
            style = bodyStyle(muted, sizeSp = 11.5),
        )
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.weight(3f)) { BasicText(text = "FEATURE", style = monoStyle(muted, sizeSp = 8.5)) }
        Box(modifier = Modifier.weight(4f)) { BasicText(text = "TARGET", style = monoStyle(muted, sizeSp = 8.5)) }
        Box(modifier = Modifier.weight(4f)) { BasicText(text = "WHERE IT CAME FROM", style = monoStyle(muted, sizeSp = 8.5)) }
    }
    for (target in targets) {
        Rule(texture)
        val origin = target.origin
        val evidence =
            when {
                origin.faces > 0 -> "${origin.faces} faces measured"
                target.range != null -> "our heuristic"
                else -> null
            }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(3f)) {
                BasicText(text = target.feature.label, style = bodyStyle(fg, sizeSp = 12.0, weight = FontWeight.SemiBold))
            }
            Box(modifier = Modifier.weight(4f)) { BasicText(text = target.describe(), style = bodyStyle(fg, sizeSp = 12.0)) }
            Column(modifier = Modifier.weight(4f)) {
                BasicText(text = "${origin.kind.word} · ${origin.label}", style = bodyStyle(muted, sizeSp = 11.0))
                if (evidence != null) BasicText(text = evidence, style = monoStyle(muted, sizeSp = 9.0))
                if (origin.kind == OriginKind.ANSWER && target.firm) BasicText(text = "firm", style = monoStyle(muted, sizeSp = 9.0))
            }
        }
    }
}

@Composable
private fun TensionsList(
    state: BriefScreenUiState,
    texture: CanvasTexture,
) {
    val muted = texture.muted.toColor()
    val tensions = state.model.tensions
    SectionLabel(text = "Tensions", texture = texture)
    if (tensions.isEmpty()) {
        BasicText(text = "None found: nothing you chose pulls against anything else.", style = bodyStyle(muted, sizeSp = 11.5))
        return
    }
    for (tension in tensions) TensionCard(tension = tension, state = state, texture = texture)
}

@Composable
private fun ReferencesList(
    state: BriefScreenUiState,
    texture: CanvasTexture,
) {
    val muted = texture.muted.toColor()
    val atlas = state.atlas
    val matches: List<FaceMatch> = remember(state.model) { facesLike(state.model) }
    SectionLabel(text = "Faces that already fit", texture = texture)
    if (atlas == null) {
        BasicText(text = "Matching real faces needs the style atlas.", style = bodyStyle(muted, sizeSp = 11.5))
        return
    }
    if (matches.isEmpty()) {
        BasicText(text = "None yet: they appear once the brief has targets.", style = bodyStyle(muted, sizeSp = 11.5))
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
    val glyphStyle = TextStyle(fontFamily = briefFamily(state), fontSize = 22.sp, lineHeight = 30.sp, color = fg)

    if (drawing.font.glyphs.isEmpty()) {
        BasicText(text = BRIEF_NO_DRAWING_NOTE, style = bodyStyle(fg))
    }
    for (kind in GuideKind.entries) {
        val ofKind = items.filter { it.kind == kind }
        if (ofKind.isEmpty()) continue
        Rule(texture)
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
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BasicText(text = item.title, style = bodyStyle(fg, sizeSp = 13.0, weight = FontWeight.SemiBold))
                    if (item.glyphs.isNotEmpty()) BasicText(text = item.glyphs, style = glyphStyle)
                    BasicText(text = item.body, style = bodyStyle(fg, sizeSp = 12.0))
                    val check = item.check
                    if (check != null) BasicText(text = check.status.word.uppercase(), style = monoStyle(muted, sizeSp = 9.0))
                    val source = item.source
                    if (source != null) BasicText(text = sourceLine(source), style = monoStyle(muted, sizeSp = 9.0))
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
        BasicText(text = "Nothing to advise yet: choose a style or a use in Ask.", style = bodyStyle(muted, sizeSp = 11.5))
        ActionButton(label = "Choose a door", texture = texture, onClick = { state.selectTab(BriefScreenTab.ASK) })
    }
    Rule(texture)
    SectionLabel(text = "Drawing plan", texture = texture)
    if (plan.isEmpty()) {
        BasicText(text = "No plan yet: it follows from the brief.", style = bodyStyle(muted, sizeSp = 11.5))
    }
    for (step in plan) {
        val remaining = step.remaining(drawing.font)
        val status =
            when {
                step.glyphs.isEmpty() -> "action"
                remaining.isEmpty() -> "all drawn"
                else -> "${remaining.size} of ${step.glyphs.size} still to draw"
            }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText(text = step.title, style = bodyStyle(fg, sizeSp = 13.0, weight = FontWeight.SemiBold))
            if (step.glyphs.isNotEmpty()) BasicText(text = step.glyphs.joinToString(" "), style = glyphStyle)
            BasicText(text = status.uppercase(), style = monoStyle(muted, sizeSp = 9.0))
            BasicText(text = step.why, style = bodyStyle(fg, sizeSp = 12.0))
            BasicText(text = sourceLine(step.source), style = monoStyle(muted, sizeSp = 9.0))
        }
    }
    Rule(texture)
    BasicText(
        text = "Draw opens on the sheet. Come back any time: the guide measures your letters as you draw.",
        style = bodyStyle(muted, sizeSp = 11.5),
    )
    ActionButton(label = "Start drawing", texture = texture, onClick = onStartDrawing, filled = true)
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
            ActionButton(label = "Start drawing", texture = texture, onClick = onStartDrawing, filled = true)
        }

        kind == GuideKind.DRIFT && dimension != null -> {
            ActionButton(label = "Or change the brief", texture = texture, onClick = { state.askAbout(dimension) })
        }

        kind == GuideKind.LEARN && onOpenLearn != null -> {
            ActionButton(label = "Open Learn", texture = texture, onClick = onOpenLearn)
        }
    }
}
