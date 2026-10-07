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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asoc.typewright.brief.Cue
import com.asoc.typewright.brief.CueFace
import com.asoc.typewright.brief.Dimension
import com.asoc.typewright.brief.Door
import com.asoc.typewright.brief.FEELINGS
import com.asoc.typewright.brief.FaceMatch
import com.asoc.typewright.brief.GENRES
import com.asoc.typewright.brief.Genre
import com.asoc.typewright.brief.GenreGroup
import com.asoc.typewright.brief.GuideKind
import com.asoc.typewright.brief.OriginKind
import com.asoc.typewright.brief.Source
import com.asoc.typewright.brief.Step
import com.asoc.typewright.brief.USES
import com.asoc.typewright.brief.describeFace
import com.asoc.typewright.brief.facesLike
import com.asoc.typewright.brief.guideFor
import com.asoc.typewright.brief.planFor
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.CanvasTextures
import com.asoc.typewright.ui.tokens.MeaningColors
import com.asoc.typewright.ui.tokens.Typography
import com.asoc.typewright.ui.tokens.toColor

/**
 * The Brief screen: find the visual language a font is aiming for, read what the answers add up
 * to, and get the guide that follows from it. Three toggles share one scrolling page:
 *
 * - **Ask** opens on six doors, each set in a real typeface; the chosen door asks its questions
 *   one at a time, every answer shown as a specimen in the face it was measured on.
 * - **Brief** sets the ethos as one large sentence, then the targets (feature, range, where it
 *   came from), the tensions between answers, and the measured faces that already fit.
 * - **Guide** lists what to do next, grouped by the kind of advice, then the drawing plan.
 *
 * Selection is violet plus the word "chosen"; the chosen tab is an ink block, like the Learn
 * screen's. There is no red anywhere here. Where the style atlas cannot load, or no project is
 * open, the screen says so in words.
 */
@Composable
fun BriefScreen(
    modifier: Modifier = Modifier,
    texture: CanvasTexture = CanvasTextures.DEFAULT,
    onBack: () -> Unit = {},
    state: BriefScreenUiState = rememberBriefScreenUiState(),
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
                BriefScreenTab.ASK -> AskTab(state = state, texture = texture)
                BriefScreenTab.BRIEF -> BriefTab(state = state, texture = texture)
                BriefScreenTab.GUIDE -> GuideTab(state = state, texture = texture)
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
                .padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 10.dp)
                .border(1.dp, texture.line.toColor()),
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
                .let { if (filled) it.background(ink) else it.border(1.dp, texture.line.toColor()) }
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        BasicText(text = label.uppercase(), style = monoStyle(if (filled) canvas else texture.fg.toColor(), sizeSp = 10.0))
    }
}

/** A choice. Chosen is violet and the word "chosen" (colour is never the only signal). */
@Composable
private fun OptionCard(
    texture: CanvasTexture,
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val violet = MeaningColors.VIOLET.forTexture(texture.id).toColor()
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(if (selected) 2.dp else 1.dp, if (selected) violet else texture.line.toColor())
                .clickable(onClick = onClick)
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        content()
        if (selected) BasicText(text = "CHOSEN", style = monoStyle(violet, sizeSp = 9.0))
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
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
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

private fun sourceLine(source: Source): String = "${source.kind.label} · ${source.citation}"

// ---------------------------------------------------------------------------------------------
// Ask
// ---------------------------------------------------------------------------------------------

@Composable
private fun AskTab(
    state: BriefScreenUiState,
    texture: CanvasTexture,
) {
    val door = state.door
    if (door == null) {
        DoorList(state = state, texture = texture)
        return
    }
    val position = remember(state.door, state.ethos, state.model, state.drawing) { state.askPosition() }
    val step = position?.step
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        ActionButton(label = "Doors", texture = texture, onClick = { state.chooseDoor(null) })
        Column(modifier = Modifier.weight(1f)) {
            BasicText(text = door.label, style = bodyStyle(texture.fg.toColor(), sizeSp = 13.0, weight = FontWeight.SemiBold))
            if (position != null) {
                BasicText(text = "${position.answered} of ${position.total} asked", style = monoStyle(texture.muted.toColor()))
            }
        }
    }
    Rule(texture)
    if (step == null) {
        BasicText(
            text = "You have been through every question this door asks. Your answers are in the Brief.",
            style = bodyStyle(texture.fg.toColor()),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(label = "Back", texture = texture, onClick = state::goBack)
            ActionButton(label = "See the brief", texture = texture, onClick = { state.selectTab(BriefScreenTab.BRIEF) }, filled = true)
        }
    } else {
        StepView(step = step, state = state, texture = texture)
        Rule(texture)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if ((position?.answered ?: 0) > 0) ActionButton(label = "Back", texture = texture, onClick = state::goBack)
            if (step.needsConfirm()) {
                ActionButton(label = "Done", texture = texture, onClick = { state.confirm(step) }, filled = true)
            }
            if (step is Step.AskDimension || step is Step.AskUse) {
                ActionButton(label = "I'm not sure", texture = texture, onClick = { state.skip(step) })
            }
            ActionButton(label = "Skip", texture = texture, onClick = { state.skip(step) })
        }
    }
}

/** The steps whose answer is a set of choices, so they need an explicit "Done". */
private fun Step.needsConfirm(): Boolean = this is Step.PickUses || this is Step.PickFeelings || this is Step.PickReferences

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
    for (door in Door.entries) {
        OptionCard(texture = texture, selected = false, onClick = { state.chooseDoor(door) }) {
            SpecimenRow(cue = door.cue, title = door.label, detail = door.caption, texture = texture)
        }
    }
}

@Composable
private fun StepPrompt(
    prompt: String,
    why: String?,
    texture: CanvasTexture,
) {
    BasicText(text = prompt, style = bodyStyle(texture.fg.toColor(), sizeSp = 16.0, weight = FontWeight.SemiBold))
    if (why != null) BasicText(text = why, style = bodyStyle(texture.muted.toColor(), sizeSp = 11.5))
}

@Composable
private fun StepView(
    step: Step,
    state: BriefScreenUiState,
    texture: CanvasTexture,
) {
    when (step) {
        is Step.PickGenre -> PickGenreView(step = step, state = state, texture = texture)
        Step.PickUses -> PickUsesView(state = state, texture = texture)
        is Step.AskUse -> AskUseView(step = step, state = state, texture = texture)
        Step.EnterWordmark -> WordmarkView(state = state, texture = texture)
        is Step.PickFeelings -> PickFeelingsView(state = state, texture = texture)
        is Step.PickReferences -> PickReferencesView(step = step, state = state, texture = texture)
        is Step.ReadDrawing -> ReadDrawingView(step = step, state = state, texture = texture)
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
    StepPrompt(
        prompt = "What style is it?",
        why = "Pick the shelf it belongs on. The measured faces of that shelf set the first ranges.",
        texture = texture,
    )
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
    state: BriefScreenUiState,
    texture: CanvasTexture,
) {
    StepPrompt(
        prompt = "What is it for?",
        why = "A use changes what to draw first and what the letters must meet. The first you choose is the main one.",
        texture = texture,
    )
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
    StepPrompt(prompt = step.question.prompt, why = step.question.why, texture = texture)
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
    state: BriefScreenUiState,
    texture: CanvasTexture,
) {
    var draft by remember { mutableStateOf(state.ethos.wordmark.orEmpty()) }
    val fg = texture.fg.toColor()
    StepPrompt(prompt = "What does the wordmark say?", why = "A wordmark is only its own letters: they are drawn first.", texture = texture)
    Box(modifier = Modifier.fillMaxWidth().border(1.dp, texture.line.toColor()).padding(12.dp)) {
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
    state: BriefScreenUiState,
    texture: CanvasTexture,
) {
    StepPrompt(
        prompt = "What should it feel like?",
        why = "Up to three. Google Fonts' own feeling tags: the app measures what their top faces have in common.",
        texture = texture,
    )
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
    StepPrompt(
        prompt = "Which fonts do you like?",
        why = "Up to three. What they share becomes a target where nothing else has spoken.",
        texture = texture,
    )
    if (atlas == null) {
        BasicText(text = BRIEF_ATLAS_UNAVAILABLE_NOTE, style = bodyStyle(texture.muted.toColor(), sizeSp = 11.5))
    }
    SectionLabel(text = "${state.ethos.references.size} of 3 chosen", texture = texture)
    val names = (state.ethos.references + step.suggestions.map { it.family }).distinct()
    for (family in names) {
        val detail =
            if (atlas == null) {
                ""
            } else {
                atlas.pack.faces[family]
                    ?.let { describeFace(it, atlas) }
                    .orEmpty()
            }
        OptionCard(texture = texture, selected = family in state.ethos.references, onClick = { state.toggleReference(family) }) {
            SpecimenRow(cue = null, title = family, detail = detail, texture = texture)
        }
    }
}

@Composable
private fun ReadDrawingView(
    step: Step.ReadDrawing,
    state: BriefScreenUiState,
    texture: CanvasTexture,
) {
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    StepPrompt(
        prompt = "What do your letters already say?",
        why = "Your own drawing, measured against real faces. A reading is votes, never a verdict.",
        texture = texture,
    )
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
    val reading = step.reading
    if (reading != null) {
        SectionLabel(text = "Reads as", texture = texture)
        for (vote in reading.genres) {
            BasicText(
                text = "${vote.genre.name}: ${vote.votes} of the ${reading.k} nearest faces",
                style = bodyStyle(fg),
            )
        }
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
    StepPrompt(prompt = step.dimension.prompt, why = step.dimension.why, texture = texture)
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
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    BasicText(
        text = model.sentence,
        style =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontSize = 24.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Medium,
                color = fg,
            ),
    )
    if (model.isEmpty) {
        BasicText(text = "Answer a few questions in Ask and the brief fills in here.", style = bodyStyle(muted, sizeSp = 11.5))
    }
    BasicText(text = state.saveNote.uppercase(), style = monoStyle(muted))
    if (state.atlas == null) {
        BasicText(text = BRIEF_ATLAS_UNAVAILABLE_NOTE, style = bodyStyle(muted, sizeSp = 11.5))
    }
    Rule(texture)
    TargetsTable(state = state, texture = texture)
    TensionsList(state = state, texture = texture)
    ReferencesList(state = state, texture = texture)
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
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    val tensions = state.model.tensions
    SectionLabel(text = "Tensions", texture = texture)
    if (tensions.isEmpty()) {
        BasicText(text = "None found: nothing you chose pulls against anything else.", style = bodyStyle(muted, sizeSp = 11.5))
        return
    }
    for (tension in tensions) {
        Column(
            modifier = Modifier.fillMaxWidth().border(1.dp, texture.line.toColor()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText(text = tension.title, style = bodyStyle(fg, sizeSp = 13.0, weight = FontWeight.SemiBold))
            BasicText(text = tension.body, style = bodyStyle(fg, sizeSp = 12.0))
            BasicText(text = sourceLine(tension.source), style = monoStyle(muted, sizeSp = 9.0))
            val signature = tension.signature
            if (signature != null) {
                val kept = signature in state.model.signatures
                ActionButton(
                    label = if (kept) "Signature kept" else "Keep as signature",
                    texture = texture,
                    onClick = { state.toggleSignature(signature) },
                    filled = kept,
                )
            }
        }
    }
}

@Composable
private fun ReferencesList(
    state: BriefScreenUiState,
    texture: CanvasTexture,
) {
    val fg = texture.fg.toColor()
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
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(text = match.family, style = bodyStyle(fg, sizeSp = 13.0, weight = FontWeight.SemiBold))
            if (face != null) BasicText(text = describeFace(face, atlas), style = bodyStyle(muted, sizeSp = 11.5))
            if (match.misses.isNotEmpty()) {
                BasicText(text = "misses: " + match.misses.joinToString(", ") { it.label }, style = monoStyle(muted, sizeSp = 9.0))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Guide
// ---------------------------------------------------------------------------------------------

@Composable
private fun GuideTab(
    state: BriefScreenUiState,
    texture: CanvasTexture,
) {
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    val model = state.model
    val drawing = state.drawing
    val items = remember(model, drawing) { guideFor(model, drawing) }
    val plan = remember(model) { planFor(model) }
    val glyphFamily = BriefCueFonts.familyFor(model.genre?.cue?.face ?: CueFace.GROTESQUE)
    val glyphStyle = TextStyle(fontFamily = glyphFamily, fontSize = 22.sp, lineHeight = 30.sp, color = fg)

    if (drawing.font.glyphs.isEmpty()) {
        BasicText(text = BRIEF_NO_DRAWING_NOTE, style = bodyStyle(fg))
    }
    for (kind in GuideKind.entries) {
        val ofKind = items.filter { it.kind == kind }
        if (ofKind.isEmpty()) continue
        Rule(texture)
        SectionLabel(text = kind.word, texture = texture)
        for (item in ofKind) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicText(text = item.title, style = bodyStyle(fg, sizeSp = 13.0, weight = FontWeight.SemiBold))
                if (item.glyphs.isNotEmpty()) BasicText(text = item.glyphs, style = glyphStyle)
                BasicText(text = item.body, style = bodyStyle(fg, sizeSp = 12.0))
                val check = item.check
                if (check != null) BasicText(text = check.status.word.uppercase(), style = monoStyle(muted, sizeSp = 9.0))
                val source = item.source
                if (source != null) BasicText(text = sourceLine(source), style = monoStyle(muted, sizeSp = 9.0))
            }
        }
    }
    if (items.isEmpty()) {
        BasicText(text = "Nothing to advise yet: choose a style or a use in Ask.", style = bodyStyle(muted, sizeSp = 11.5))
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
}
