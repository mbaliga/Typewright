// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.workbook

import com.asoc.typewright.campaign.WorkbookLatinContent
import com.asoc.typewright.campaign.WorkbookTask
import com.asoc.typewright.campaign.WorkbookTaskProgress
import com.asoc.typewright.campaign.campaignProgress
import com.asoc.typewright.campaign.currentWorkbookTaskIndex
import com.asoc.typewright.campaign.runAllWorkbookGates
import com.asoc.typewright.qa.NodeEconomyReport
import com.asoc.typewright.qa.checkNodeEconomy
import com.asoc.typewright.qa.corpus.NodeEconomyCorpus
import com.asoc.typewright.qa.corpus.StyleAtlas
import com.asoc.typewright.ui.project.OpenProject
import com.asoc.typewright.ui.project.ProjectDataPacks
import com.asoc.typewright.ui.project.ProjectFont
import com.asoc.typewright.ui.project.ProjectStyle
import com.asoc.typewright.ui.project.projectStyle

/** The workbook whose reflections and confirmed tasks `typewright.json` keeps: the Latin one. */
const val LATIN_WORKBOOK_KEY = "latn"

/**
 * The real campaign data [WorkbookScreen] renders: the twelve real [WorkbookTask]s
 * ([WorkbookLatinContent]), each one's real gate run against the open project's own glyphs
 * ([campaignProgress]), and the real current/next task ([currentWorkbookTaskIndex]). Without a
 * project there is nothing to measure, so the snapshot is [NoProject]; where this target cannot
 * read the data it needs it is an honest [Unavailable]. Never a stand-in project.
 */
sealed interface WorkbookCampaignSnapshot {
    /**
     * The real, loaded campaign data. Task 4 compares the project's control characters with a style
     * class's box: [task4Style] is that class and where it came from (the person's, or our
     * heuristic's reading, which the gate then says), null when there is none to judge by, and
     * [task4Report] is [checkNodeEconomy] run once against it, kept because it carries the raw
     * per-glyph on-curve count and box that the gate grid shows beside each glyph.
     */
    data class Loaded(
        val tasks: List<WorkbookTask>,
        val progress: List<WorkbookTaskProgress>,
        val currentTaskIndex: Int,
        val font: ProjectFont,
        val task4Style: ProjectStyle?,
        val task4Report: NodeEconomyReport?,
    ) : WorkbookCampaignSnapshot

    /** No project is open. */
    data object NoProject : WorkbookCampaignSnapshot

    /** The gates are being run on the open project, off the UI thread; nothing is shown as measured until they have. */
    data object Loading : WorkbookCampaignSnapshot

    /** The real, honest reason loading failed on this target: never a fabricated task list. */
    data class Unavailable(
        val reason: String,
    ) : WorkbookCampaignSnapshot
}

/**
 * Loads [WorkbookCampaignSnapshot] for [project], which is the open project or null. Not
 * `@Composable`; a caller wraps it in `remember` so the gates run once per composition, not on every
 * recomposition.
 */
fun loadWorkbookCampaignSnapshot(
    project: OpenProject?,
    corpus: NodeEconomyCorpus? = ProjectDataPacks.corpus,
    atlas: StyleAtlas? = ProjectDataPacks.atlas,
): WorkbookCampaignSnapshot {
    val font = project?.font ?: return WorkbookCampaignSnapshot.NoProject
    if (corpus == null) {
        return WorkbookCampaignSnapshot.Unavailable(
            "The node-economy data (data/node-economy-latin.json) can't be read on this target yet, so the gates have nothing to measure against.",
        )
    }
    return runCatching {
        val tasks = WorkbookLatinContent.load()
        val style = projectStyle(project.manifest, font, atlas)?.takeIf { it.key in corpus.styleKeys }
        val gates = runAllWorkbookGates(tasks, font.ufo, corpus, style?.key)
        val confirmed =
            project.manifest.workbook[LATIN_WORKBOOK_KEY]
                ?.confirmedTasks
                .orEmpty()
        val progress = campaignProgress(tasks, gates, confirmed)
        WorkbookCampaignSnapshot.Loaded(
            tasks = tasks,
            progress = progress,
            currentTaskIndex = currentWorkbookTaskIndex(progress),
            font = font,
            task4Style = style,
            task4Report = style?.let { checkNodeEconomy(font.ufo, it.key, corpus) },
        )
    }.getOrElse { error ->
        WorkbookCampaignSnapshot.Unavailable(error.message ?: error::class.simpleName ?: "unknown error loading the campaign data")
    }
}

/**
 * `#s-home`'s own `.taskcard` copy shape (`ui/typewright-explorer.html` line 607: `"Task 4 ·
 * Control characters"`), for whichever task [WorkbookCampaignSnapshot.Loaded.currentTaskIndex]
 * reports: real data, not the mockup's own hardcoded "Task 4". Falls back to the plain word
 * "Workbook" when there is no loaded snapshot: there is no real next-task fact left to show, so this
 * says the honest, generic thing rather than inventing one.
 */
fun nextTaskLabel(snapshot: WorkbookCampaignSnapshot): String =
    when (snapshot) {
        is WorkbookCampaignSnapshot.Loaded -> {
            val task = snapshot.tasks.first { it.index == snapshot.currentTaskIndex }
            "Task ${task.index} · ${task.title}"
        }

        is WorkbookCampaignSnapshot.NoProject, is WorkbookCampaignSnapshot.Loading, is WorkbookCampaignSnapshot.Unavailable -> {
            "Workbook"
        }
    }
