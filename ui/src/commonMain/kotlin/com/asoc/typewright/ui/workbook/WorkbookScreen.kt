// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.workbook

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.asoc.typewright.campaign.Demonstration
import com.asoc.typewright.campaign.WorkbookTask
import com.asoc.typewright.campaign.WorkbookTaskProgress
import com.asoc.typewright.campaign.WorkbookTaskState
import com.asoc.typewright.project.MetaChange
import com.asoc.typewright.project.ProjectSession
import com.asoc.typewright.project.scrapbook.ScrapbookPin
import com.asoc.typewright.project.scrapbook.ScrapbookPinKind
import com.asoc.typewright.project.scrapbook.stablePinRotationDegrees
import com.asoc.typewright.qa.NodeEconomyReport
import com.asoc.typewright.ui.learn.hyleDecoFontFamily
import com.asoc.typewright.ui.project.LocalProjectWorkspace
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.CanvasTextures
import com.asoc.typewright.ui.tokens.MeaningColors
import com.asoc.typewright.ui.tokens.SpacingTokens
import com.asoc.typewright.ui.tokens.Typography
import com.asoc.typewright.ui.tokens.toColor

/**
 * The Workbook screen (`ui/typewright-explorer.html`'s `#s-workbook`, CLAUDE.md law 6): a real,
 * standalone station -- like [com.asoc.typewright.ui.learn.LearnScreen] before it -- showing
 * exactly one of the twelve real Latin workbook tasks in full ([WorkbookScreenUiState.taskIndex]),
 * wired to the real `:campaign` module ([WorkbookCampaignSnapshot]) rather than any mockup number.
 * Same shell shape as [com.asoc.typewright.ui.learn.LearnScreen]: `(modifier, texture, onBack,
 * uiState)`, its own purpose-built header bar (not [com.asoc.typewright.ui.glass.Header], for the
 * identical reason that file's own KDoc gives), and a commands button that is visually present and
 * deliberately unwired (the same disclosed, established convention).
 *
 * **What "wired to the real campaign module" means here, concretely.** Every number and word this
 * screen shows besides its own chrome comes from [WorkbookCampaignSnapshot]: the task's own real
 * `why`/`task`/`reflection` prose and [Demonstration] ([WorkbookLatinContent]/`workbook-latin.yaml`),
 * the real `SCAFFOLD` flag ([WorkbookTask.scaffold] -- true for all twelve today, not a hardcoded
 * string), the real 12-dot progress strip ([WorkbookTaskProgress.state], `campaignProgress`), and
 * the real Gate section (`campaign`'s own already-run [com.asoc.typewright.campaign.
 * WorkbookGateResult] per task, turned into [WorkbookGateRow]s by [workbookGateRows]) -- including,
 * honestly, a task whose gate `campaign` itself discloses as not implemented (tasks 1, 2, 7, 8, 10,
 * and 11 without a compiled font): [SeverityMark]'s own dashed "not started" ring, never a fake
 * pass.
 *
 * **The "Trace again with Fit" button is task-4-only, not a generic Gate action.** `#s-workbook`'s
 * own markup shows exactly one task (4), and its own Gate section carries exactly one action
 * button, `<button class="btn" data-go="trace">Trace again with Fit ›</button>`. Nothing in the
 * explorer says whether that action is task 4's own (a re-trace makes sense for a node-economy
 * miss specifically) or a generic "Gate" action every task's own section would carry -- and per
 * CLAUDE.md law 6, this file does not guess: the button is reproduced only on task 4's own screen,
 * where the explorer's own content literally is. It is also **unwired**: there is no real Trace
 * screen composable anywhere in `ui` yet to navigate to (confirmed by reading `ui/src/commonMain`
 * in full before writing this file -- the same situation [com.asoc.typewright.ui.learn.
 * LearnScreen]'s own commands button is already in, and the same disclosed convention).
 *
 * **The Reflection section's "+ note"-equivalent text entry (P11 WP5, docs/PROJECT_MODEL.md
 * §10.1).** With a project open ([LocalProjectWorkspace]'s current [ProjectSession], non-null),
 * "Save to scrapbook" calls `session.update(`[MetaChange.AddReflection]`("latn", task.index,
 * text))` -- a real, persisted [com.asoc.typewright.project.Reflection], and one
 * [com.asoc.typewright.ui.learn.ScrapbookTab] *does* then show (`scrapbookBoard` reads every
 * workbook's own reflections): the two screens share the one real project, not two separate
 * `remember` scopes. Saving a reflection also confirms the task ([ProjectSession]'s own
 * `addReflection`, docs/PROJECT_MODEL.md §10.3's D3 trigger) -- but [WorkbookScreenUiState]'s own
 * `campaignProgress` call (`WorkbookScreenState.kt`, outside this work package's touch list) does
 * not yet pass that confirmation back in, so the progress strip above does not yet move because
 * of it; a follow-up wiring gap, not a bug in this section.
 *
 * **With no project open**, this section behaves exactly as it did before P11: reusing
 * [ScrapbookPin]/[ScrapbookPinKind.NOTE]/[stablePinRotationDegrees] -- the real scrapbook data
 * model and its real stable-rotation function, not a second, parallel note type -- but appending
 * to *this composable's own* `remember`ed list, the same "GESTURE HONESTY" shape
 * [com.asoc.typewright.ui.learn.ScrapbookTab] uses for its own sample-data "+ note": real,
 * in-memory-only, lost on recomposition of a fresh instance, neither pretending to be a persisted
 * project file (`docs/OPEN_QUESTIONS.md`).
 */
@Composable
public fun WorkbookScreen(
    modifier: Modifier = Modifier,
    texture: CanvasTexture = CanvasTextures.DEFAULT,
    onBack: () -> Unit = {},
    uiState: WorkbookScreenUiState = rememberWorkbookScreenUiState(),
) {
    Column(modifier = modifier.fillMaxSize().background(texture.canvas.toColor())) {
        WorkbookHeaderBar(texture = texture, onBack = onBack)
        when (val snapshot = uiState.snapshot) {
            is WorkbookCampaignSnapshot.Unavailable -> {
                WorkbookUnavailableBody(texture = texture, reason = snapshot.reason, modifier = Modifier.weight(1f))
            }

            is WorkbookCampaignSnapshot.Loaded -> {
                val progress = snapshot.progress.firstOrNull { it.task.index == uiState.taskIndex } ?: snapshot.progress.first()
                WorkbookBody(texture = texture, snapshot = snapshot, progress = progress, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** `#s-workbook .bar`: back button, "Workbook · Latin" / "12 tasks · a submission-ready font at the end", unwired commands button -- same shape and sizes as [com.asoc.typewright.ui.learn.LearnScreen]'s own `LearnHeaderBar`. */
@Composable
private fun WorkbookHeaderBar(
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
        Box(modifier = Modifier.size(34.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            BasicText(text = "‹", style = TextStyle(fontFamily = FontFamily.Default, fontSize = 16.sp, color = fg))
        }
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = "Workbook · Latin",
                style = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = fg),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                text = "$WORKBOOK_TASK_COUNT tasks · a submission-ready font at the end",
                style = Typography.mono(sizeSp = 11.0).copy(color = muted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            // Deliberately no `clickable` -- see this file's own top KDoc ("commands button"), the
            // exact convention LearnHeaderBar already established.
            BasicText(
                text = "›_",
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, letterSpacing = (-0.02).em, color = muted),
            )
        }
    }
}

@Composable
private fun WorkbookUnavailableBody(
    texture: CanvasTexture,
    reason: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = SpacingTokens.GUTTER_DP.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText(
            text = "Workbook data is not available on this target.",
            style = Typography.sentence.copy(color = texture.fg.toColor(), fontWeight = FontWeight.SemiBold),
        )
        BasicText(text = reason, style = Typography.sentence.copy(color = texture.muted.toColor()))
    }
}

@Composable
private fun WorkbookBody(
    texture: CanvasTexture,
    snapshot: WorkbookCampaignSnapshot.Loaded,
    progress: WorkbookTaskProgress,
    modifier: Modifier = Modifier,
) {
    val task = progress.task
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    val hyleDeco = remember { hyleDecoFontFamily() }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SpacingTokens.GUTTER_DP.dp, vertical = 4.dp),
    ) {
        BasicText(
            text = "TASK ${task.index} OF $WORKBOOK_TASK_COUNT",
            style = Typography.mono(sizeSp = 10.0).copy(color = muted),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)) {
            BasicText(
                text = task.title,
                style =
                    TextStyle(
                        fontFamily = FontFamily.Default,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.01).em,
                        color = fg,
                    ),
            )
            if (task.scaffold) {
                BasicText(
                    text = "SCAFFOLD",
                    style = TextStyle(fontFamily = FontFamily.Default, fontSize = 10.sp, letterSpacing = 0.12.em, color = muted),
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
        WorkbookProgressStrip(progress = snapshot.progress, texture = texture, modifier = Modifier.padding(top = 10.dp, bottom = 18.dp))

        WorkbookProseSection(heading = "Why", body = task.why, texture = texture)
        WorkbookDemonstrationSection(demonstration = task.demonstration, texture = texture, hyleDeco = hyleDeco)
        WorkbookTaskSection(task = task, texture = texture, hyleDeco = hyleDeco)
        WorkbookGateSection(progress = progress, task4Report = snapshot.task4Report, texture = texture, hyleDeco = hyleDeco)
        WorkbookReflectionSection(task = task, texture = texture)

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/** `#s-workbook .prog`: 12 equal segments, done=fg, current=violet, todo=ink at 12% -- real per-task [WorkbookTaskState], never the mockup's own fixed "3 done, 1 current, 8 todo" pattern. */
@Composable
private fun WorkbookProgressStrip(
    progress: List<WorkbookTaskProgress>,
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
) {
    val done = texture.fg.toColor()
    val current = MeaningColors.VIOLET.forTexture(texture.id).toColor()
    val todo = texture.ink.toColor().copy(alpha = 0.12f)

    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (entry in progress.sortedBy { it.task.index }) {
            val color =
                when (entry.state) {
                    WorkbookTaskState.DONE -> done
                    WorkbookTaskState.CURRENT -> current
                    WorkbookTaskState.TODO -> todo
                }
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(2.dp)
                        .background(color = color, shape = RoundedCornerShape(1.dp)),
            )
        }
    }
}

/** `.wsec h5`: mono, uppercase, 10sp, semibold, muted. */
@Composable
private fun WorkbookSectionHeading(
    text: String,
    texture: CanvasTexture,
) {
    BasicText(
        text = text.uppercase(),
        style = Typography.mono(sizeSp = 10.0, weight = FontWeight.SemiBold).copy(color = texture.muted.toColor()),
    )
}

/** One `.wsec`: heading, then [body] split on any embedded newline into separate paragraphs (YAML's own folded-scalar blank-line rule), each `.wsec p` (12.5sp sentence text). */
@Composable
private fun WorkbookProseSection(
    heading: String,
    body: String,
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        WorkbookSectionHeading(text = heading, texture = texture)
        Spacer(modifier = Modifier.height(4.dp))
        for (paragraph in body.split("\n").map { it.trim() }.filter { it.isNotEmpty() }) {
            BasicText(
                text = paragraph,
                style =
                    TextStyle(
                        fontFamily = FontFamily.Default,
                        fontSize = 12.5.sp,
                        lineHeight = 12.5.sp * 1.45,
                        color = texture.fg.toColor(),
                    ),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * `.wsec` "Demonstration · sample font": [Demonstration.StatDemonstration] reproduces `.demo`
 * (`.dl` hero + `.dc` stat lines) when its own [Demonstration.StatDemonstration.label] genuinely
 * reads as a short sample ([isHeroStatLabel] -- task 4's own real `"noHO"`, `#s-workbook`'s only
 * worked example); every other real label in `workbook-latin.yaml` is a longer descriptive phrase
 * (task 6's own real `"on-curve range across 13 open fonts..."`), shown as a plain caption instead
 * -- seeded by a real bug this screen's own screenshot pass found (see [isHeroStatLabel]'s own
 * KDoc). [Demonstration.SceneDemonstration] has no `#s-workbook` worked example at all (only task
 * 4 is ever shown there) -- see this file's own top KDoc for why this screen still needs to handle
 * it, honestly, rather than only the one shape the mockup happens to show.
 */
@Composable
private fun WorkbookDemonstrationSection(
    demonstration: Demonstration,
    texture: CanvasTexture,
    hyleDeco: FontFamily,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        WorkbookSectionHeading(text = "Demonstration · sample font", texture = texture)
        Spacer(modifier = Modifier.height(6.dp))
        when (demonstration) {
            is Demonstration.StatDemonstration -> {
                val heroLabel = isHeroStatLabel(demonstration.label)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (heroLabel) {
                        BasicText(
                            text = demonstration.label,
                            style = TextStyle(fontFamily = hyleDeco, fontSize = 40.sp, color = texture.ink.toColor()),
                        )
                    }
                    // weight(1f): the real fix, not just the heroLabel heuristic above -- this
                    // column always claims the Row's own remaining width explicitly, rather than
                    // relying on the hero's own natural width happening to leave room for it (the
                    // exact assumption that broke on task 6's own real long label).
                    Column(modifier = Modifier.weight(1f)) {
                        if (!heroLabel) {
                            BasicText(
                                text = demonstration.label,
                                style =
                                    TextStyle(
                                        fontFamily = FontFamily.Default,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        lineHeight = 12.5.sp * 1.4,
                                        color = texture.fg.toColor(),
                                    ),
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                        for (stat in demonstration.stats) {
                            BasicText(
                                text = stat,
                                style =
                                    TextStyle(
                                        fontFamily = FontFamily.Default,
                                        fontSize = 11.sp,
                                        lineHeight = 11.sp * 1.5,
                                        color = texture.muted.toColor(),
                                    ),
                            )
                        }
                    }
                }
            }

            is Demonstration.SceneDemonstration -> {
                // No `#s-workbook` worked example exists for a scene demonstration (CLAUDE.md
                // law 6) -- this reuses the same `.demo`-shaped container rather than a second,
                // invented look, and says plainly that the live scene itself lives elsewhere.
                // Stacked, not a `.demo`-style side-by-side row: the note line wraps to several
                // lines at phone width, and bottom-aligning a wrapped multi-line note against a
                // single-line hero (the row shape [Demonstration.StatDemonstration] below uses,
                // for a *big glyph* baseline) reads as visually staggered, not aligned -- a real,
                // screenshot-caught layout problem this task's own instructions asked to check for.
                Column {
                    BasicText(
                        text = demonstration.sceneId,
                        style = Typography.mono(sizeSp = 15.0, weight = FontWeight.SemiBold).copy(color = texture.ink.toColor()),
                    )
                    BasicText(
                        text = "Live scene from Learn — open Learn › Lineages/Craft to play it for real.",
                        style =
                            TextStyle(
                                fontFamily = FontFamily.Default,
                                fontSize = 11.sp,
                                lineHeight = 11.sp * 1.5,
                                color = texture.muted.toColor(),
                            ),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

/** `.wsec` "Task": prose, then [WorkbookTask.controlString] (when present) on its own line in the user's own font, the app's own established control-string display convention (`ui/typewright-explorer.html`'s `.cstr hy`, the Space room). */
@Composable
private fun WorkbookTaskSection(
    task: WorkbookTask,
    texture: CanvasTexture,
    hyleDeco: FontFamily,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        WorkbookSectionHeading(text = "Task", texture = texture)
        Spacer(modifier = Modifier.height(4.dp))
        for (paragraph in task.task
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }) {
            BasicText(
                text = paragraph,
                style =
                    TextStyle(
                        fontFamily = FontFamily.Default,
                        fontSize = 12.5.sp,
                        lineHeight = 12.5.sp * 1.45,
                        color = texture.fg.toColor(),
                    ),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        val controlString = task.controlString
        if (controlString != null) {
            BasicText(
                text = controlString,
                style = TextStyle(fontFamily = hyleDeco, fontSize = 20.sp, color = texture.ink.toColor()),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/**
 * `.wsec` "Gate · {real gateSummary}": [workbookGateRows] decides grid-vs-list per row (see that
 * function's own KDoc); task 4 alone also gets the unwired "Trace again with Fit ›" action --
 * see this file's own top KDoc for why it is task-4-only.
 */
@Composable
private fun WorkbookGateSection(
    progress: WorkbookTaskProgress,
    task4Report: NodeEconomyReport,
    texture: CanvasTexture,
    hyleDeco: FontFamily,
    modifier: Modifier = Modifier,
) {
    val gate = progress.gate
    val task = progress.task
    val rows = remember(gate) { workbookGateRows(task.index, gate, if (task.index == 4) task4Report else null) }
    val useGrid = rows.isNotEmpty() && rows.all { it.isGlyphHero }

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        WorkbookSectionHeading(text = "Gate · ${gate.gateSummary}", texture = texture)
        Spacer(modifier = Modifier.height(8.dp))
        if (useGrid) {
            WorkbookGateGrid(rows = rows, texture = texture, hyleDeco = hyleDeco)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (row in rows) WorkbookGateListRow(row = row, texture = texture)
            }
        }
        if (task.index == 4) {
            BasicText(
                text = "Trace again with Fit ›",
                style =
                    TextStyle(
                        fontFamily = FontFamily.Default,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MeaningColors.VIOLET.forTexture(texture.id).toColor(),
                    ),
                // Deliberately no `clickable` -- see this file's own top KDoc: no real Trace
                // screen composable exists yet to navigate to.
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

/** `.gate{grid-template-columns:repeat(4,1fr)}`: [rows] chunked 4-wide, left-aligned on a short last row. */
@Composable
private fun WorkbookGateGrid(
    rows: List<WorkbookGateRow>,
    texture: CanvasTexture,
    hyleDeco: FontFamily,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (rowChunk in rows.chunked(4)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                for (cell in rowChunk) {
                    WorkbookGateGridCell(row = cell, texture = texture, hyleDeco = hyleDeco, modifier = Modifier.weight(1f))
                }
                repeat(4 - rowChunk.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

/** One `.gate div`: glyph hero (`.g`, 26sp, user's font), mark + value (`b`, sans 11sp semibold fg), optional box range (task 4 only, sans 11sp muted). */
@Composable
private fun WorkbookGateGridCell(
    row: WorkbookGateRow,
    texture: CanvasTexture,
    hyleDeco: FontFamily,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        BasicText(text = row.heroText, style = TextStyle(fontFamily = hyleDeco, fontSize = 26.sp, color = texture.ink.toColor()))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SeverityMark(status = row.status, color = texture.fg.toColor())
            BasicText(
                text = row.valueText,
                style =
                    TextStyle(
                        fontFamily = FontFamily.Default,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = texture.fg.toColor(),
                    ),
            )
        }
        val boxText = row.boxText
        if (boxText != null) {
            BasicText(text = boxText, style = TextStyle(fontFamily = FontFamily.Default, fontSize = 11.sp, color = texture.muted.toColor()))
        }
    }
}

/** A non-glyph gate row (tasks 1, 2, 3's per-glyph-but-INFO-only shape is still glyph-hero, so this only ever renders for 1/2/7/8/10/11/12): mark + mono label + mono status word, then the check's own prose detail -- `UI_SPEC.md`'s own "Report items (Check)" shape (severity + mono title + one sentence), the closest already-established pattern in this codebase for "a check result with no glyph to hero". */
@Composable
private fun WorkbookGateListRow(
    row: WorkbookGateRow,
    texture: CanvasTexture,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SeverityMark(status = row.status, color = texture.fg.toColor(), modifier = Modifier.padding(top = 3.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    text = row.heroText,
                    style = Typography.mono(sizeSp = 12.0, weight = FontWeight.SemiBold).copy(color = texture.fg.toColor()),
                )
                BasicText(
                    text = "  ${gateCheckStatusWord(row.status)}",
                    style = Typography.mono(sizeSp = 11.0).copy(color = texture.muted.toColor()),
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            BasicText(
                text = row.detail,
                style =
                    TextStyle(
                        fontFamily = FontFamily.Default,
                        fontSize = 12.5.sp,
                        lineHeight = 12.5.sp * 1.4,
                        color = texture.fg.toColor(),
                    ),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * `.wsec` "Reflection · saves to the scrapbook": the task's own real reflection prompt, a real
 * text entry (`.refl`'s own italic-muted "Write three sentences…" resting look, as a real
 * placeholder over a real [BasicTextField] rather than the explorer's static, non-interactive
 * div), and a real "save" affordance that appends a real [ScrapbookPin] -- see this file's own top
 * KDoc for exactly what "reuses the scrapbook's own pin machinery" does and does not mean here.
 */
@Composable
private fun WorkbookReflectionSection(
    task: WorkbookTask,
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
) {
    val session =
        LocalProjectWorkspace.current
            ?.current
            ?.collectAsState()
            ?.value
    var draft by remember(task.index, session) { mutableStateOf("") }
    var savedNotes by remember(task.index, session) { mutableStateOf(emptyList<ScrapbookPin>()) }
    var nextSeq by remember(task.index, session) { mutableStateOf(1) }
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    val violet = MeaningColors.VIOLET.forTexture(texture.id).toColor()

    // With a project open, the real, persisted reflections for this task replace the local
    // remember-only list above (see this file's own top KDoc, "The Reflection section...").
    val liveReflections =
        session
            ?.state
            ?.collectAsState()
            ?.value
            ?.meta
            ?.lessons
            ?.get(WORKBOOK_SCRIPT_KEY)
            ?.reflectionsFor(task.index)
    val savedCount = liveReflections?.size ?: savedNotes.size
    val savedTexts = liveReflections?.map { it.text } ?: savedNotes.map { it.noteText.orEmpty() }

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        WorkbookSectionHeading(text = "Reflection · saves to the scrapbook", texture = texture)
        Spacer(modifier = Modifier.height(4.dp))
        BasicText(
            text =
                task.reflection
                    .split("\n")
                    .joinToString(" ") { it.trim() }
                    .trim(),
            style = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.5.sp, lineHeight = 12.5.sp * 1.45, color = fg),
        )
        Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            if (draft.isEmpty()) {
                BasicText(
                    text = "Write three sentences…",
                    style = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.sp, fontStyle = FontStyle.Italic, color = muted),
                )
            }
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                textStyle = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.sp, lineHeight = 12.sp * 1.4, color = fg),
                cursorBrush = SolidColor(fg),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        BasicText(
            text = "Save to scrapbook",
            style = Typography.mono(sizeSp = 10.5, weight = FontWeight.SemiBold).copy(color = violet),
            modifier =
                Modifier.padding(top = 8.dp).clickable(enabled = draft.isNotBlank()) {
                    val trimmed = draft.trim()
                    if (trimmed.isNotEmpty()) {
                        if (session != null) {
                            session.update(MetaChange.AddReflection(WORKBOOK_SCRIPT_KEY, task.index, trimmed))
                        } else {
                            val id = "workbook-reflection-${task.index}-$nextSeq"
                            savedNotes =
                                savedNotes +
                                ScrapbookPin(
                                    id = id,
                                    kind = ScrapbookPinKind.NOTE,
                                    captionTitle = "Reflection · Task ${task.index}",
                                    captionSource = "note",
                                    noteText = trimmed,
                                    rotationDegrees = stablePinRotationDegrees(id),
                                )
                            nextSeq += 1
                        }
                        draft = ""
                    }
                },
        )
        if (savedCount > 0) {
            BasicText(
                text = if (session != null) "$savedCount saved to the project" else "$savedCount saved this session",
                style = Typography.mono(sizeSp = 9.5).copy(color = muted),
                modifier = Modifier.padding(top = 8.dp),
            )
            for (text in savedTexts) {
                BasicText(
                    text = "— $text",
                    style = TextStyle(fontFamily = FontFamily.Default, fontSize = 11.5.sp, fontStyle = FontStyle.Italic, color = muted),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        BasicText(
            text = if (session != null) WORKBOOK_REFLECTION_LIVE_DISCLOSURE else WORKBOOK_REFLECTION_DISCLOSURE,
            style = Typography.mono(sizeSp = 9.0).copy(color = muted),
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/** The workbook key [MetaChange.AddReflection] and [WorkbookLessons][com.asoc.typewright.project.WorkbookLessons] use for the Latin workbook (docs/PROJECT_MODEL.md §12's golden-path fixture: `AddReflection("latn", ...)`). */
private const val WORKBOOK_SCRIPT_KEY = "latn"

private const val WORKBOOK_REFLECTION_DISCLOSURE =
    "Saves to this session's own scrapbook only -- no project is open, so this stays in memory and is lost on " +
        "recomposition or process death, the same limit ScrapbookTab's own \"+ note\" already discloses with no project open."

private const val WORKBOOK_REFLECTION_LIVE_DISCLOSURE =
    "Saves a real reflection to the open project's own lessons file, and confirms this task if it is a judgment " +
        "call the project can't gate by itself (docs/PROJECT_MODEL.md §10.3)."
