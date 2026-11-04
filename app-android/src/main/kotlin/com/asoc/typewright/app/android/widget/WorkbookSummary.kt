// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.android.widget

import com.asoc.typewright.campaign.DEFAULT_TASK4_STYLE_KEY
import com.asoc.typewright.campaign.GateCheckStatus
import com.asoc.typewright.campaign.WorkbookTask
import com.asoc.typewright.campaign.WorkbookTaskProgress
import com.asoc.typewright.campaign.WorkbookTaskState
import com.asoc.typewright.campaign.campaignProgress
import com.asoc.typewright.campaign.currentWorkbookTaskIndex
import com.asoc.typewright.campaign.runAllWorkbookGates
import com.asoc.typewright.project.FontState
import com.asoc.typewright.project.ProjectManifest
import com.asoc.typewright.project.StyleClass
import com.asoc.typewright.qa.corpus.NodeEconomyCorpus

/** One Workbook task's place in the strip (W4): done, the one you're on, or still to do. */
enum class TaskState(
    val code: String,
) {
    DONE("D"),
    CURRENT("C"),
    TODO("T"),
}

/** A gate check's outcome as W4 counts it; [NOT_MEASURED] is campaign's "not implemented". */
enum class CheckState {
    PASS,
    WARN,
    FAIL,
    INFO,
    NOT_MEASURED,
}

/**
 * Where the open project stands in the Latin Workbook (SCREENS_V1 §9.2 W4): every task's state in
 * order, and the current task's title, gate sentence, check counts and scaffold flag.
 */
data class WorkbookSummary(
    val states: List<TaskState>,
    val currentIndex: Int,
    val currentTitle: String,
    val currentGateSummary: String,
    val currentChecks: Map<CheckState, Int>,
    val currentScaffold: Boolean,
) {
    val done: Int get() = states.count { it == TaskState.DONE }
    val total: Int get() = states.size
}

/**
 * Runs the Workbook's real gates on the open project, as S19 describes them: each task's gate on
 * the default master, the confirmed tasks from `typewright.json`, tasks done in order.
 */
class WorkbookSummaries(
    private val tasks: () -> List<WorkbookTask>,
    private val corpus: () -> NodeEconomyCorpus,
) {
    /** The summary for the project holding [font] and [manifest]; throws when the gates can't run (W4's error state). */
    fun summarise(
        font: FontState,
        manifest: ProjectManifest,
    ): WorkbookSummary {
        val master = font.masters.firstOrNull { it.isDefault } ?: font.masters.first()
        val loadedCorpus = corpus()
        val loadedTasks = tasks()
        val styleKey = styleKeyFor(manifest.brief.styleClass, loadedCorpus.styleKeys)
        val confirmed = manifest.workbook[LATIN_WORKBOOK]?.confirmedTasks.orEmpty()
        val gates = runAllWorkbookGates(loadedTasks, master.ufo, loadedCorpus, styleKey)
        return summaryOf(campaignProgress(loadedTasks, gates, confirmed))
    }

    companion object {
        /** The Latin workbook's key in `typewright.json` and `lessons/` (docs/PROJECT_MODEL.md §3). */
        const val LATIN_WORKBOOK: String = "latn"

        /**
         * The style class task 4 compares against: the project's confirmed class, else its declared
         * one, when the corpus has it; otherwise campaign's own default.
         */
        fun styleKeyFor(
            styleClass: StyleClass,
            corpusKeys: Set<String>,
        ): String =
            listOfNotNull(styleClass.confirmed, styleClass.declared).firstOrNull { it in corpusKeys }
                ?: DEFAULT_TASK4_STYLE_KEY

        /** [progress] (from `campaignProgress`) reduced to what W4 shows. */
        fun summaryOf(progress: List<WorkbookTaskProgress>): WorkbookSummary {
            require(progress.isNotEmpty()) { "the Workbook has no tasks" }
            val ordered = progress.sortedBy { it.task.index }
            val currentIndex = currentWorkbookTaskIndex(ordered)
            val current = ordered.first { it.task.index == currentIndex }
            return WorkbookSummary(
                states = ordered.map { taskStateOf(it.state) },
                currentIndex = currentIndex,
                currentTitle = current.task.title,
                currentGateSummary = current.gate.gateSummary,
                currentChecks =
                    current.gate.checks
                        .groupingBy { checkStateOf(it.status) }
                        .eachCount(),
                currentScaffold = current.task.scaffold,
            )
        }

        private fun taskStateOf(state: WorkbookTaskState): TaskState =
            when (state) {
                WorkbookTaskState.DONE -> TaskState.DONE
                WorkbookTaskState.CURRENT -> TaskState.CURRENT
                WorkbookTaskState.TODO -> TaskState.TODO
            }

        private fun checkStateOf(status: GateCheckStatus): CheckState =
            when (status) {
                GateCheckStatus.PASS -> CheckState.PASS
                GateCheckStatus.WARN -> CheckState.WARN
                GateCheckStatus.FAIL -> CheckState.FAIL
                GateCheckStatus.INFO -> CheckState.INFO
                GateCheckStatus.NOT_IMPLEMENTED -> CheckState.NOT_MEASURED
            }
    }
}
