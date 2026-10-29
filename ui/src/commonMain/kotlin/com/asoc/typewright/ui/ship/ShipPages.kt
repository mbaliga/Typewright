// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.ship

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asoc.typewright.compile.CompileLogLine
import com.asoc.typewright.qa.report.SeverityBar
import com.asoc.typewright.qa.ship.BlockerSource
import com.asoc.typewright.qa.ship.ShipBlocker
import com.asoc.typewright.qa.ship.ShipChoices
import com.asoc.typewright.qa.ship.ShipInput
import com.asoc.typewright.qa.ship.ShipItem
import com.asoc.typewright.qa.ship.ShipItemId
import com.asoc.typewright.qa.ship.ShipMark
import com.asoc.typewright.qa.ship.ShipRoute
import com.asoc.typewright.qa.ship.detailRows
import com.asoc.typewright.qa.ship.mainRows
import com.asoc.typewright.ui.check.ActionLink
import com.asoc.typewright.ui.check.RawPanel
import com.asoc.typewright.ui.check.SegmentChip
import com.asoc.typewright.ui.check.SeverityBarView
import com.asoc.typewright.ui.check.labelStyle
import com.asoc.typewright.ui.check.logText
import com.asoc.typewright.ui.check.sentenceStyle
import com.asoc.typewright.ui.project.ProjectFont
import com.asoc.typewright.ui.project.ProjectText
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.Typography
import com.asoc.typewright.ui.tokens.accent
import com.asoc.typewright.ui.tokens.toColor
import kotlinx.coroutines.delay
import kotlin.time.TimeMark

/** What the pages can do: follow a link, choose a category or a licence, choose where the folder goes, show output or the request text. */
internal class ShipPageActions(
    val onRoute: (ShipRoute) -> Unit,
    val onChoice: (ShipChoices.Kind, String) -> Unit,
    val onChooseTarget: () -> Unit,
    val onToggleOutput: () -> Unit,
    val onToggleRequest: () -> Unit,
    val onCopyRequest: (String) -> Unit,
    val onTryAgain: () -> Unit,
    val onToggleDetails: () -> Unit,
    /** Sets the short text of the line [ShipInput.Kind]; answers a sentence when it is refused, null when it was taken. */
    val onInput: (ShipInput.Kind, String) -> String?,
)

private val MARK_WIDTH = 14.dp
private val BLOCKERS_MAX_HEIGHT = 120.dp

// ---- S16: the checklist -------------------------------------------------------------------------------

/**
 * The Ready page (S16): the board's rows, the folder as the board's five lines, and, while Check has fails, the ink block that says so, which
 * sits above the lines behind Details so it is on screen at phone size. The rows that are not the board's (category, repository address,
 * vendor ID, the branch and the day for METADATA) are behind Details, with a count of how many still need the person.
 */
@Composable
internal fun ChecklistPage(
    texture: CanvasTexture,
    sheet: ShipSheet,
    targetLabel: String?,
    message: String?,
    showDetails: Boolean,
    actions: ShipPageActions,
) {
    val muted = texture.muted.toColor()
    val main = sheet.checklist.mainRows()
    val details = sheet.checklist.detailRows()
    Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
        if (message != null) Notice(texture, message)
        ShipText.withoutFontWhy(sheet.platform.exit, sheet.platform.compiles)?.let { why ->
            BasicText(text = why, style = sentenceStyle(muted, size = 12.0))
        }
        for (item in main) ChecklistRow(texture, item, sheet, actions)
        TargetBlock(texture, sheet, targetLabel, actions)
        ShipText.replacingWarning(sheet.replacements)?.let { Notice(texture, it) }
        if (sheet.gate.blocked) Notice(texture, ShipText.blockedBlock(sheet.gate))
        if (details.isNotEmpty()) {
            val needYou = details.count { it.mark == ShipMark.WARN }
            ActionLink(
                label = (if (showDetails) "Hide details" else "Details") + if (needYou > 0) " · $needYou still need you" else "",
                texture = texture,
                onClick = actions.onToggleDetails,
            )
            if (showDetails) {
                for (item in details) ChecklistRow(texture, item, sheet, actions)
                if (sheet.preview.alsoWritten.isNotEmpty()) {
                    BasicText(
                        text = "Also written: " + sheet.preview.alsoWritten.joinToString(" · "),
                        style = Typography.mono(sizeSp = 10.5).copy(color = muted, letterSpacing = 0.sp),
                    )
                }
            }
        }
        BasicText(text = ShipText.GITHUB_NOTE.uppercase(), style = labelStyle(muted, size = 9.0, tracking = 0.1))
    }
}

/** A line at the head of a page that says what just happened (a cancelled build, a chooser that could not be shown), as an ink block. */
@Composable
internal fun Notice(
    texture: CanvasTexture,
    text: String,
) {
    BasicText(
        text = text,
        style = sentenceStyle(texture.canvas.toColor(), size = 12.5),
        modifier = Modifier.fillMaxWidth().background(texture.ink.toColor()).padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChecklistRow(
    texture: CanvasTexture,
    item: ShipItem,
    sheet: ShipSheet,
    actions: ShipPageActions,
) {
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) { contentDescription = "${item.mark.word}: ${item.title}" },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BasicText(
            text = item.mark.shape,
            style = sentenceStyle(if (item.mark == ShipMark.TODO) muted else fg, size = 12.0),
            modifier = Modifier.widthIn(min = MARK_WIDTH).alignByBaseline(),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f).alignByBaseline()) {
            BasicText(text = item.title, style = sentenceStyle(fg, size = 13.0))
            val action = item.action
            val detail = item.detail
            when {
                detail != null && action != null -> {
                    // The board's own line: the detail, a dot and the link, in one run of text, as `METADATA needs one · Font info ›`.
                    val accent = texture.accent.toColor()
                    BasicText(
                        text =
                            buildAnnotatedString {
                                append("$detail · ")
                                withLink(
                                    LinkAnnotation.Clickable(
                                        tag = action.label,
                                        styles = TextLinkStyles(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)),
                                    ) { actions.onRoute(action.route) },
                                ) { append("${action.label} ›") }
                            },
                        style = sentenceStyle(muted, size = 12.0),
                    )
                }

                detail != null -> {
                    BasicText(text = detail, style = sentenceStyle(muted, size = 12.0))
                }

                action != null -> {
                    ActionLink(action.label, texture, onClick = { actions.onRoute(action.route) })
                }
            }
            item.choices?.let { choices ->
                FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (option in choices.options) {
                        SegmentChip(
                            text = option.label,
                            selected = option.id == choices.chosen,
                            texture = texture,
                            onClick = { actions.onChoice(choices.kind, option.id) },
                        )
                    }
                }
            }
            item.input?.let { input ->
                InlineInput(texture, input.hint, input.value.orEmpty()) { typed -> actions.onInput(input.kind, typed) }
            }
            if (item.id == ShipItemId.CHECK && sheet.gate.blocked) {
                BlockerList(texture, sheet.gate.blockers, actions)
            }
        }
    }
}

/** A short text typed in place on a line: the field, the hint under it, and a Set link; [onSet] answers a sentence when the text is refused. */
@Composable
private fun InlineInput(
    texture: CanvasTexture,
    hint: String,
    initial: String,
    onSet: (String) -> String?,
) {
    val muted = texture.muted.toColor()
    var text by remember(initial) { mutableStateOf(initial) }
    var problem by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(top = 2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = text,
                onValueChange = {
                    text = it
                    problem = null
                },
                singleLine = true,
                textStyle = Typography.mono(sizeSp = 12.0).copy(color = texture.fg.toColor(), letterSpacing = 0.sp),
                cursorBrush = SolidColor(texture.ink.toColor()),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { problem = onSet(text) }),
                modifier =
                    Modifier
                        .widthIn(min = 96.dp, max = 180.dp)
                        .background(texture.canvas.toColor())
                        .border(1.dp, texture.ink.toColor())
                        .padding(horizontal = 6.dp, vertical = 4.dp),
            )
            ActionLink("Set", texture, onClick = { problem = onSet(text) })
        }
        BasicText(text = problem ?: hint, style = sentenceStyle(muted, size = 11.5))
    }
}

/**
 * Every fail that stops the build, each as its mono title, one sentence and a link to where it is put right (Check, or the Font info value
 * it is about). The list scrolls inside its own height, so the lines below it stay on the screen however many fails there are.
 */
@Composable
private fun BlockerList(
    texture: CanvasTexture,
    blockers: List<ShipBlocker>,
    actions: ShipPageActions,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp).heightIn(max = BLOCKERS_MAX_HEIGHT).verticalScroll(rememberScrollState()),
    ) {
        for (blocker in blockers) BlockerRow(texture, blocker, actions)
    }
}

@Composable
private fun BlockerRow(
    texture: CanvasTexture,
    blocker: ShipBlocker,
    actions: ShipPageActions,
) {
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    val accent = texture.accent.toColor()
    val link = if (blocker.route == ShipRoute.Check) "Open it in Check" else "Set it in Font info"
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        BasicText(
            text = "● ${blocker.title}".uppercase() + if (blocker.source == BlockerSource.LAYER_ONE) " · LAYER ONE" else "",
            style = labelStyle(fg, size = 9.5, tracking = 0.08),
        )
        // The sentence and its link in one run, as the board's own rows do.
        BasicText(
            text =
                buildAnnotatedString {
                    append("${blocker.sentence} ")
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = link,
                            styles = TextLinkStyles(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)),
                        ) { actions.onRoute(blocker.route) },
                    ) { append("$link ›") }
                },
            style = sentenceStyle(muted, size = 12.0),
        )
    }
}

/** "Writes to": where the folder goes, the five lines of what it holds as the board draws them, and the link that chooses another place. */
@Composable
private fun TargetBlock(
    texture: CanvasTexture,
    sheet: ShipSheet,
    targetLabel: String?,
    actions: ShipPageActions,
) {
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    val exit = sheet.platform.exit
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BasicText(text = "WRITES TO ·", style = labelStyle(muted, size = 9.0))
            BasicText(
                text = targetLabel ?: ShipText.notChosen(exit, sheet.subject.facts.folderName),
                style = Typography.mono(sizeSp = 10.5).copy(color = if (targetLabel != null) fg else muted, letterSpacing = 0.sp),
            )
        }
        BasicText(
            text = sheet.preview.boardLines.joinToString("\n"),
            style = Typography.mono(sizeSp = 11.0).copy(color = fg, letterSpacing = 0.sp, lineHeight = 19.25.sp),
        )
        ShipText.chooseLink(exit, targetLabel != null)?.let { link ->
            ActionLink(link, texture, onClick = actions.onChooseTarget)
        }
    }
}

/** The order the board lists the folder in: the font, the sources, then the files beside them. */
private fun treeRank(path: String): String =
    when {
        path.startsWith("fonts/") -> "0$path"
        path.startsWith("sources/") -> "1$path"
        path.startsWith("documentation/") -> "3$path"
        else -> "2$path"
    }

// ---- S17: building ------------------------------------------------------------------------------------

/** The building page (S17): the project's own word at the top, the streamed steps, the bar, the elapsed time, and the fontmake output on request. */
@Composable
internal fun BuildingPage(
    texture: CanvasTexture,
    font: ProjectFont?,
    word: String,
    steps: List<ShipStepStatus>,
    startedAt: TimeMark,
    log: List<CompileLogLine>,
    cancelling: Boolean,
    showOutput: Boolean,
    actions: ShipPageActions,
) {
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    val seconds by produceState(initialValue = startedAt.elapsedNow().inWholeSeconds.toInt(), startedAt) {
        while (true) {
            value = startedAt.elapsedNow().inWholeSeconds.toInt()
            delay(ELAPSED_TICK_MILLIS)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (font != null) {
            ProjectText(
                font = font,
                text = if (font.canSet(word)) word else "",
                sizeSp = WORD_SIZE_SP,
                color = fg.copy(alpha = WORD_ALPHA),
                shrinkToFit = true,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 4.dp)) {
            for (status in steps) StepRow(texture, status)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
            val done = steps.count { it.state == StepState.DONE || it.state == StepState.SKIPPED }
            val running = steps.count { it.state == StepState.RUNNING }
            val fraction = ((done + running * 0.5) / steps.size).coerceIn(0.0, 1.0)
            SeverityBarView(SeverityBar(solid = fraction, hatched = 0.0, plain = 1.0 - fraction), texture, height = 3.dp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText(text = "Elapsed ${ShipText.elapsed(seconds)}".uppercase(), style = labelStyle(fg))
                BasicText(
                    text = (if (cancelling) ShipText.CANCELLING else ShipText.CANCEL_NOTE).uppercase(),
                    style = labelStyle(muted, size = 8.5, tracking = 0.06),
                )
            }
            if (log.isNotEmpty()) {
                ActionLink(if (showOutput) "Hide fontmake output" else "Show fontmake output", texture, onClick = actions.onToggleOutput)
            }
            if (showOutput) RawPanel(texture, logText(log))
        }
    }
}

private const val ELAPSED_TICK_MILLIS = 1000L
private const val WORD_SIZE_SP = 54f
private const val WORD_ALPHA = 0.9f

@Composable
private fun StepRow(
    texture: CanvasTexture,
    status: ShipStepStatus,
) {
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    val waiting = status.state == StepState.WAITING
    val skipped = status.state == StepState.SKIPPED
    val color = if (waiting || skipped) muted else fg
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) { contentDescription = "${status.step.title}: ${stateWord(status.state)}" },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        BasicText(text = status.state.shape, style = sentenceStyle(color, size = 13.0))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(
                text = status.step.title,
                style =
                    sentenceStyle(
                        color,
                        size = 13.5,
                        weight =
                            if (status.state ==
                                StepState.RUNNING
                            ) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Normal
                            },
                    ),
            )
            if ((skipped || status.state == StepState.FAILED) && status.note != null) {
                BasicText(text = status.note, style = sentenceStyle(muted, size = 11.5))
            }
        }
        BasicText(
            text =
                when {
                    status.millis != null -> ShipText.duration(status.millis)
                    status.state == StepState.RUNNING -> "running"
                    skipped -> "not here"
                    else -> ""
                },
            style = Typography.mono(sizeSp = 10.0).copy(color = muted, letterSpacing = 0.sp),
        )
    }
}

private fun stateWord(state: StepState): String =
    when (state) {
        StepState.WAITING -> "waiting"
        StepState.RUNNING -> "running"
        StepState.DONE -> "done"
        StepState.SKIPPED -> "not available on this device"
        StepState.FAILED -> "failed"
    }

// ---- after the build ----------------------------------------------------------------------------------

/** The page after a write: where it went, what is in it, how the check on the font went, what still needs the person, and the request text. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DonePage(
    texture: CanvasTexture,
    result: ShipResult,
    stillNeedsYou: List<ShipItem>,
    showRequest: Boolean,
    actions: ShipPageActions,
) {
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BasicText(
            text = "${ShipMark.DONE.shape} ${ShipText.written(result.target, result.withFont)}",
            style = sentenceStyle(fg, size = 14.0, weight = FontWeight.SemiBold),
        )
        val took = ShipText.duration(result.totalMillis)
        BasicText(
            text = ShipText.size(result.files, result.bytes) + " · $took",
            style = Typography.mono(sizeSp = 10.5).copy(color = muted, letterSpacing = 0.sp),
        )
        if (result.closedDuringWrite) Notice(texture, ShipText.CLOSED_DURING_WRITE)
        BasicText(
            text = result.tree.sortedBy(::treeRank).joinToString("\n"),
            style = Typography.mono(sizeSp = 11.0).copy(color = fg, letterSpacing = 0.sp, lineHeight = 19.25.sp),
        )
        if (result.replaced.isNotEmpty()) {
            BasicText(text = "REPLACED IN THE FOLDER", style = labelStyle(muted, size = 9.0), modifier = Modifier.padding(top = 4.dp))
            for (item in result.replaced) {
                BasicText(
                    text = ShipText.replacedLine(item),
                    style = Typography.mono(sizeSp = 10.5).copy(color = fg, letterSpacing = 0.sp),
                )
            }
        }
        when {
            result.checkLine != null -> {
                BasicText(
                    text = "${ShipMark.DONE.shape} The compiled font was checked: ${result.checkLine}.",
                    style = sentenceStyle(fg, size = 12.5),
                )
            }

            result.checkSkipped != null -> {
                BasicText(
                    text = "${ShipMark.WARN.shape} The compiled font was not checked by Google Fonts' own profile: ${result.checkSkipped}",
                    style = sentenceStyle(fg, size = 12.5),
                )
            }

            !result.withFont -> {
                BasicText(
                    text = "${ShipMark.WARN.shape} No font was compiled here, so nothing was checked on a binary.",
                    style = sentenceStyle(fg, size = 12.5),
                )
            }
        }
        if (stillNeedsYou.isNotEmpty()) {
            BasicText(text = "STILL NEEDS YOU", style = labelStyle(muted, size = 9.0), modifier = Modifier.padding(top = 4.dp))
            for (item in stillNeedsYou) ChecklistLine(texture, item, actions)
        }
        if (result.omissions.isNotEmpty()) {
            BasicText(text = "LEFT OUT OF THE FOLDER", style = labelStyle(muted, size = 9.0), modifier = Modifier.padding(top = 4.dp))
            for (omission in result.omissions) {
                BasicText(
                    text = "${omission.path}: ${omission.reason}",
                    style = Typography.mono(sizeSp = 10.5).copy(color = fg, letterSpacing = 0.sp),
                )
            }
        }
        result.requestText?.let { text ->
            BasicText(text = "THE REQUEST TO GOOGLE FONTS", style = labelStyle(muted, size = 9.0), modifier = Modifier.padding(top = 4.dp))
            BasicText(
                text =
                    "It is also in the folder, as documentation/GOOGLE_FONTS_REQUEST.md. " +
                        "Paste it into a new issue on the Google Fonts repository.",
                style = sentenceStyle(muted, size = 12.0),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                ActionLink(if (showRequest) "Hide the text" else "Show the text", texture, onClick = actions.onToggleRequest)
                ActionLink("Copy the text", texture, onClick = { actions.onCopyRequest(text) })
            }
            if (showRequest) RawPanel(texture, text)
        }
        BasicText(text = ShipText.GITHUB_NOTE.uppercase(), style = labelStyle(muted, size = 9.0, tracking = 0.1))
    }
}

/** One checklist line outside the checklist: its shape, its title, and its detail and link. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChecklistLine(
    texture: CanvasTexture,
    item: ShipItem,
    actions: ShipPageActions,
) {
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        BasicText(text = item.mark.shape, style = sentenceStyle(fg, size = 12.0), modifier = Modifier.widthIn(min = MARK_WIDTH))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            BasicText(text = item.title, style = sentenceStyle(fg, size = 13.0))
            item.detail?.let { BasicText(text = it, style = sentenceStyle(muted, size = 12.0)) }
            item.action?.let { action -> ActionLink(action.label, texture, onClick = { actions.onRoute(action.route) }) }
        }
    }
}

/** The page after a build that stopped with an error: where, why in one sentence, that nothing was written, and the compiler's output on request. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FailedPage(
    texture: CanvasTexture,
    step: ShipStep,
    cause: String,
    log: List<CompileLogLine>,
    showOutput: Boolean,
    actions: ShipPageActions,
) {
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(
            text = "${ShipMark.FAIL.shape} ${ShipText.failureHeading(step)}",
            style = sentenceStyle(fg, size = 13.0, weight = FontWeight.SemiBold),
        )
        BasicText(text = cause, style = sentenceStyle(fg))
        BasicText(text = "Nothing was written. Your project and the folder are as they were.", style = sentenceStyle(muted, size = 12.0))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            ActionLink("Try again", texture, onClick = actions.onTryAgain)
            if (log.isNotEmpty()) {
                ActionLink(
                    if (showOutput) "Hide output" else "Show fontmake output",
                    texture,
                    onClick = actions.onToggleOutput,
                )
            }
        }
        if (showOutput) RawPanel(texture, logText(log))
    }
}

/** The page after the check on the compiled font found fails: each one, and that nothing was written. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StoppedPage(
    texture: CanvasTexture,
    blockers: List<ShipBlocker>,
    log: List<CompileLogLine>,
    showOutput: Boolean,
    actions: ShipPageActions,
) {
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicText(
            text =
                "${ShipMark.FAIL.shape} The compiled font has ${blockers.size} " +
                    "${if (blockers.size == 1) "fail" else "fails"} in Google Fonts' own checks",
            style = sentenceStyle(fg, size = 13.0, weight = FontWeight.SemiBold),
        )
        BasicText(
            text = "Nothing was written, because Ship stops while Check has fails. Your project and the folder are as they were.",
            style = sentenceStyle(muted, size = 12.0),
        )
        for (blocker in blockers) BlockerRow(texture, blocker, actions)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            ActionLink("Open Check", texture, onClick = { actions.onRoute(ShipRoute.Check) })
            if (log.isNotEmpty()) {
                ActionLink(
                    if (showOutput) "Hide output" else "Show fontmake output",
                    texture,
                    onClick = actions.onToggleOutput,
                )
            }
        }
        if (showOutput) RawPanel(texture, logText(log))
    }
}
