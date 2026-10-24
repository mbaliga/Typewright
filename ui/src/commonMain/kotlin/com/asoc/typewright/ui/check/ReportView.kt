// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.check

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.asoc.typewright.compile.CompileLogLine
import com.asoc.typewright.qa.CheckOutcome
import com.asoc.typewright.qa.CheckStatus
import com.asoc.typewright.qa.LayerOneReport
import com.asoc.typewright.qa.report.Basis
import com.asoc.typewright.qa.report.CheckItem
import com.asoc.typewright.qa.report.CheckReport
import com.asoc.typewright.qa.report.CheckRoute
import com.asoc.typewright.qa.report.NotMeasured
import com.asoc.typewright.qa.report.Severity
import com.asoc.typewright.qa.report.SeverityBar
import com.asoc.typewright.qa.report.withThousands
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.Typography
import com.asoc.typewright.ui.tokens.toColor
import kotlinx.coroutines.delay

private const val DECK_CLEARANCE_DP = 200
private const val RAW_PANEL_ALPHA = 0.06f
private const val MESSAGE_LIMIT = 400
private const val OUTCOMES_SHOWN = 40
private const val LOG_LINES_SHOWN = 40

/** What a tap on the Report page can ask for. */
internal class ReportActions(
    val onRoute: (CheckRoute) -> Unit,
    val onCancel: () -> Unit,
    val onRunAgain: () -> Unit,
    val onToggleOutput: () -> Unit,
)

/** The shape a layer-one status carries: the same ● ◐ ✓ ○ the layer-two items use, so the two layers read alike. */
internal val CheckStatus.shape: String
    get() =
        when (this) {
            CheckStatus.FAIL -> Severity.FAIL.shape
            CheckStatus.WARN -> Severity.WARN.shape
            CheckStatus.PASS -> Severity.PASS.shape
            CheckStatus.SKIP -> Severity.NOT_MEASURED.shape
        }

/**
 * Check, Report (S14): the counts and the severity bar, then the fourteen layer-two checks in groups, each with
 * its shape, its mono title, one plain sentence that quotes the project's own numbers, what a reviewer said when it
 * went wrong, and a link that goes where the fix is. Then layer one, wherever it can run, and last what these
 * checks do not measure. Plain reads the sentences; [raw] shows the same findings as the tool prints them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReportPage(
    texture: CanvasTexture,
    report: CheckReport,
    run: CheckRunState,
    raw: Boolean,
    showOutput: Boolean,
    actions: ReportActions,
    modifier: Modifier = Modifier,
) {
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    val counts = report.counts
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 22.dp, end = 22.dp, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LayerOneNotice(texture, run, showOutput, actions)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CountStat(counts.fail, Severity.FAIL, fg)
            CountStat(counts.warn, Severity.WARN, fg)
            CountStat(counts.pass, Severity.PASS, fg)
            if (counts.notMeasured > 0) CountStat(counts.notMeasured, Severity.NOT_MEASURED, fg)
            BasicText(
                text = "${counts.total} layer-two checks, sorted by what a reviewer attacks first",
                style = sentenceStyle(muted, size = 12.0),
                modifier = Modifier.widthIn(max = 130.dp),
            )
        }
        SeverityBarView(counts.bar(), texture)
        (run.layerOne as? LayerOneState.Done)?.report?.counts?.let { layerOne ->
            BasicText(
                text = "Layer one · ${layerOne.fail} fail · ${layerOne.warn} warn · ${layerOne.pass} pass".uppercase(),
                style = labelStyle(fg, size = 9.5, tracking = 0.06),
            )
        }
        for (group in report.groups) {
            Spacer(Modifier.height(10.dp))
            BasicText(text = group.group.title.uppercase(), style = labelStyle(muted))
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                for (item in group.items) CheckItemView(texture, item, raw, actions.onRoute)
            }
        }
        Spacer(Modifier.height(10.dp))
        LayerOneSection(texture, run, raw, actions)
        Spacer(Modifier.height(10.dp))
        NotMeasuredView(texture)
        Spacer(Modifier.height(DECK_CLEARANCE_DP.dp))
    }
}

@Composable
private fun CountStat(
    count: Int,
    severity: Severity,
    color: Color,
) {
    Column {
        BasicText(text = count.withThousands(), style = sentenceStyle(color, size = 17.0, weight = FontWeight.SemiBold))
        BasicText(text = "${severity.shape} ${severity.word}", style = sentenceStyle(color, size = 12.0))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CheckItemView(
    texture: CanvasTexture,
    item: CheckItem,
    raw: Boolean,
    onRoute: (CheckRoute) -> Unit,
) {
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    val passed = item.severity == Severity.PASS
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicText(
            text = item.severity.shape,
            style = sentenceStyle(fg, size = 12.0),
            modifier = Modifier.widthIn(min = 12.dp).padding(top = 1.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.weight(1f)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(
                    text = item.title,
                    style = Typography.mono(sizeSp = 11.5, weight = FontWeight.SemiBold).copy(color = fg, letterSpacing = 0.02.em),
                    modifier = Modifier.alignByBaseline(),
                )
                item.detail?.let {
                    BasicText(
                        text = it.uppercase(),
                        style = labelStyle(muted, size = 9.0, tracking = 0.06),
                        modifier = Modifier.alignByBaseline(),
                    )
                }
                if (item.basis == Basis.HEURISTIC) {
                    BasicText(
                        text = "· ${item.basis.label.uppercase()}",
                        style = labelStyle(muted, size = 9.0, tracking = 0.06),
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }
            BasicText(text = item.summary, style = sentenceStyle(if (passed) muted else fg))
            if (!passed) {
                item.reviewerQuote?.let {
                    BasicText(
                        text = "Reviewer: “$it”",
                        style = sentenceStyle(muted, size = 12.0).copy(fontStyle = FontStyle.Italic),
                    )
                }
                item.action?.let { action -> ActionLink(action.label, texture, onClick = { onRoute(action.route) }) }
            }
            if (raw) RawPanel(texture, "basis: ${item.basis.label}\n${item.basisNote}\n\n${item.raw}")
        }
    }
}

/** A block of mono text on a faint ink ground: the Raw view of a finding or a log. */
@Composable
internal fun RawPanel(
    texture: CanvasTexture,
    text: String,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = text,
        style = Typography.mono(sizeSp = 10.0).copy(color = texture.fg.toColor(), letterSpacing = 0.sp, fontFamily = FontFamily.Monospace),
        modifier = modifier.fillMaxWidth().background(texture.ink.toColor().copy(alpha = RAW_PANEL_ALPHA)).padding(8.dp),
    )
}

@Composable
private fun NotMeasuredView(texture: CanvasTexture) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicText(
            text = "What these checks do not measure",
            style = sentenceStyle(texture.fg.toColor(), size = 12.5, weight = FontWeight.SemiBold),
        )
        for (paragraph in NotMeasured.PARAGRAPHS) {
            BasicText(text = paragraph, style = sentenceStyle(texture.muted.toColor(), size = 12.0))
        }
    }
}

// ---- layer one ------------------------------------------------------------------------------------------------

/**
 * The states of layer one that need the person's attention sit above the counts, where they are seen without scrolling: a run
 * in progress (with Cancel), a run that stopped with its cause, and a run that was cancelled.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LayerOneNotice(
    texture: CanvasTexture,
    run: CheckRunState,
    showOutput: Boolean,
    actions: ReportActions,
) {
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    when (val state = run.layerOne) {
        is LayerOneState.Running -> {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                BasicText(text = "LAYER ONE · RUNNING", style = labelStyle(muted))
                LayerOneRunning(texture, state, showOutput, actions)
            }
        }

        is LayerOneState.Failed -> {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                BasicText(text = "LAYER ONE · STOPPED", style = labelStyle(muted))
                BasicText(
                    text = "${Severity.FAIL.shape} Layer one stopped while it was ${state.stage.title.lowercase()}",
                    style = sentenceStyle(fg, size = 12.5, weight = FontWeight.SemiBold),
                )
                BasicText(text = state.cause, style = sentenceStyle(fg))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    ActionLink("Run again", texture, onClick = actions.onRunAgain)
                    if (state.log.isNotEmpty()) {
                        ActionLink(if (showOutput) "Hide output" else "Show output", texture, onClick = actions.onToggleOutput)
                    }
                }
                if (showOutput) RawPanel(texture, logText(state.log))
            }
        }

        LayerOneState.Cancelled -> {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                BasicText(text = "LAYER ONE · CANCELLED", style = labelStyle(muted))
                BasicText(text = "Cancelled. Nothing in your project changed.", style = sentenceStyle(fg))
                ActionLink("Run again", texture, onClick = actions.onRunAgain)
            }
        }

        else -> {
            Unit
        }
    }
}

/** Layer one's own section at the foot of the report: what it found, that it has not run yet, or why it cannot run here. */
@Composable
private fun LayerOneSection(
    texture: CanvasTexture,
    run: CheckRunState,
    raw: Boolean,
    actions: ReportActions,
) {
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    val state = run.layerOne
    if (state is LayerOneState.Running || state is LayerOneState.Failed || state == LayerOneState.Cancelled) return
    val heading = "Layer one" + (run.runtime?.let { " · ${it.description}" } ?: "")
    BasicText(text = heading.uppercase(), style = labelStyle(muted))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
        when (state) {
            LayerOneState.NotRun -> {
                BasicText(text = "Layer one has not run on this drawing yet.", style = sentenceStyle(fg))
                ActionLink("Run it now", texture, onClick = actions.onRunAgain)
            }

            is LayerOneState.Unavailable -> {
                BasicText(
                    text = "${Severity.NOT_MEASURED.shape} Not run here",
                    style = sentenceStyle(fg, size = 12.5, weight = FontWeight.SemiBold),
                )
                BasicText(text = state.reason, style = sentenceStyle(muted))
            }

            is LayerOneState.Done -> {
                LayerOneResults(texture, state.report, raw)
            }

            else -> {
                Unit
            }
        }
    }
}

@Composable
private fun LayerOneResults(
    texture: CanvasTexture,
    report: LayerOneReport,
    raw: Boolean,
) {
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    val c = report.counts
    BasicText(
        text =
            "${report.results.size.withThousands()} checks · ${c.fail.withThousands()} fail · ${c.warn.withThousands()} warn · " +
                "${c.pass.withThousands()} pass · ${c.skip.withThousands()} skipped",
        style = labelStyle(fg, size = 9.5, tracking = 0.06),
    )
    if (raw) {
        RawPanel(
            texture,
            report.results.joinToString("\n\n") { outcome ->
                "${outcome.toolStatus ?: outcome.status.name}  ${outcome.id}\n${outcome.title?.let { "$it\n" } ?: ""}${outcome.message}"
            },
        )
        return
    }
    val findings = report.results.filter { it.status == CheckStatus.FAIL || it.status == CheckStatus.WARN }
    val shown = findings.sortedBy { if (it.status == CheckStatus.FAIL) 0 else 1 }.take(OUTCOMES_SHOWN)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
        for (outcome in shown) OutcomeView(texture, outcome)
        if (findings.size > shown.size) {
            BasicText(
                text = "… and ${(findings.size - shown.size).withThousands()} more in Raw.",
                style = sentenceStyle(muted, size = 12.0),
            )
        }
        if (findings.isEmpty()) {
            BasicText(
                text = "No check failed or warned. A green report means “will not break”, not “is good”.",
                style = sentenceStyle(muted),
            )
        }
    }
}

@Composable
private fun OutcomeView(
    texture: CanvasTexture,
    outcome: CheckOutcome,
) {
    val fg = texture.fg.toColor()
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicText(
            text = outcome.status.shape,
            style = sentenceStyle(fg, size = 12.0),
            modifier = Modifier.widthIn(min = 12.dp).padding(top = 1.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
            BasicText(
                text = outcome.id,
                style = Typography.mono(sizeSp = 11.0, weight = FontWeight.SemiBold).copy(color = fg, letterSpacing = 0.02.em),
            )
            val message = outcome.message.trim()
            BasicText(
                text = if (message.length > MESSAGE_LIMIT) message.take(MESSAGE_LIMIT) + "…" else message,
                style = sentenceStyle(fg, size = 12.0),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LayerOneRunning(
    texture: CanvasTexture,
    state: LayerOneState.Running,
    showOutput: Boolean,
    actions: ReportActions,
) {
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    val seconds by produceState(
        initialValue =
            state.startedAt
                .elapsedNow()
                .inWholeSeconds
                .toInt(),
        state.startedAt,
    ) {
        while (true) {
            value =
                state.startedAt
                    .elapsedNow()
                    .inWholeSeconds
                    .toInt()
            delay(ELAPSED_TICK_MILLIS)
        }
    }
    val compileDone = state.stage == LayerOneStage.CHECK
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StageRow(
            texture,
            LayerOneStage.COMPILE.title,
            if (compileDone) "✓" else "◐",
            !compileDone,
            state.compileSeconds?.let(CheckText::duration) ?: "running",
        )
        StageRow(
            texture,
            LayerOneStage.CHECK.title,
            if (compileDone) "◐" else "○",
            compileDone,
            if (compileDone) "running" else "",
            waiting = !compileDone,
        )
    }
    SeverityBarView(
        SeverityBar(solid = if (compileDone) 0.5 else 0.0, hatched = 0.5, plain = 0.5),
        texture,
        height = 3.dp,
        modifier = Modifier.padding(top = 4.dp),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(text = "Elapsed ${CheckText.elapsed(seconds)}".uppercase(), style = labelStyle(fg))
        BasicText(text = "Cancel leaves the project untouched".uppercase(), style = labelStyle(muted, size = 8.5, tracking = 0.06))
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        ActionLink("Cancel", texture, onClick = actions.onCancel)
        ActionLink(if (showOutput) "Hide fontmake output" else "Show fontmake output", texture, onClick = actions.onToggleOutput)
    }
    if (showOutput) RawPanel(texture, logText(state.log))
}

private const val ELAPSED_TICK_MILLIS = 1000L

@Composable
private fun StageRow(
    texture: CanvasTexture,
    title: String,
    shape: String,
    current: Boolean,
    time: String,
    waiting: Boolean = false,
) {
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        BasicText(text = shape, style = sentenceStyle(if (waiting) muted else fg, size = 13.0))
        BasicText(
            text = title,
            style =
                sentenceStyle(
                    if (waiting) muted else fg,
                    size = 13.5,
                    weight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                ),
            modifier = Modifier.weight(1f),
        )
        BasicText(text = time, style = Typography.mono(sizeSp = 10.0).copy(color = muted, letterSpacing = 0.sp))
    }
}

/** The compiler's log as plain lines, the last [LOG_LINES_SHOWN] of it, each marked with its level when it is not routine. */
internal fun logText(log: List<CompileLogLine>): String {
    if (log.isEmpty()) return "The compiler has printed nothing yet."
    val lines =
        log.takeLast(LOG_LINES_SHOWN).map { line ->
            when (line.level) {
                CompileLogLine.Level.INFO -> line.message
                CompileLogLine.Level.WARNING -> "warning: ${line.message}"
                CompileLogLine.Level.ERROR -> "error: ${line.message}"
            }
        }
    val earlier = log.size - lines.size
    return (if (earlier > 0) listOf("… $earlier earlier lines not shown") else emptyList<String>()).plus(lines).joinToString("\n")
}
