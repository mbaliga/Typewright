// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.workbook

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.asoc.typewright.ui.project.OpenProject
import com.asoc.typewright.ui.project.rememberOpenProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** `#s-workbook`'s own fixed task count (`.t2`: "12 tasks -- a submission-ready font at the end"). */
const val WORKBOOK_TASK_COUNT: Int = 12

/**
 * [WorkbookScreen]'s own state: which one of the twelve tasks is showing, and whether the deck's
 * "All tasks" view is, plus the [WorkbookCampaignSnapshot] it renders, which is built from the open
 * project ([loadWorkbookCampaignSnapshot]) and given here.
 *
 * The task opens on the real current/next task of the snapshot, or task 1 where there is none. The
 * snapshot is replaced ([show]) whenever the project changes, and replacing it never moves the person:
 * the task they are on and the "All tasks" view stay as they are. The one exception is a screen that
 * opened before any gate had run ([WorkbookCampaignSnapshot.Loading]): it has no task of the person's
 * to keep, so the first snapshot that loads puts it on the current task.
 */
class WorkbookScreenUiState(
    snapshot: WorkbookCampaignSnapshot,
    initialTaskIndex: Int? = null,
) {
    /** What the screen renders; replaced by [show] when the project changes. */
    var snapshot: WorkbookCampaignSnapshot by mutableStateOf(snapshot)
        private set

    /** Whether [taskIndex] is already the person's choice or the real current task, so a later snapshot must not move it. */
    private var settled: Boolean = initialTaskIndex != null || snapshot is WorkbookCampaignSnapshot.Loaded

    private var taskIndexState: Int by mutableStateOf((initialTaskIndex ?: defaultTaskIndexFor(snapshot)).coerceIn(1, WORKBOOK_TASK_COUNT))

    /** The task showing, 1 to [WORKBOOK_TASK_COUNT]. Setting it is the person choosing a task. */
    var taskIndex: Int
        get() = taskIndexState
        internal set(value) {
            taskIndexState = value.coerceIn(1, WORKBOOK_TASK_COUNT)
            settled = true
        }

    /** True while the deck's "All tasks" view is showing: the twelve tasks as a list, instead of the one task. */
    var allTasks: Boolean by mutableStateOf(false)

    /** Replaces the snapshot with [next], keeping the task and the view the person is on (see the class comment). */
    fun show(next: WorkbookCampaignSnapshot) {
        snapshot = next
        if (!settled && next is WorkbookCampaignSnapshot.Loaded) {
            taskIndexState = next.currentTaskIndex.coerceIn(1, WORKBOOK_TASK_COUNT)
            settled = true
        }
    }
}

/** [WorkbookCampaignSnapshot.Loaded.currentTaskIndex] when data loaded; task 1 (the workbook's own first task) when it did not. */
private fun defaultTaskIndexFor(snapshot: WorkbookCampaignSnapshot): Int =
    when (snapshot) {
        is WorkbookCampaignSnapshot.Loaded -> snapshot.currentTaskIndex
        is WorkbookCampaignSnapshot.NoProject, is WorkbookCampaignSnapshot.Loading, is WorkbookCampaignSnapshot.Unavailable -> 1
    }

/**
 * A [WorkbookScreenUiState] over the open project. The state belongs to the project's session: opening
 * another project starts a new one. Within a project, the snapshot is measured again whenever its letters, its
 * style class or its confirmed tasks change, so the gates always speak about the font as it is now, and the
 * twelve gates run on a background thread, never inside composition: the screen shows
 * [WorkbookCampaignSnapshot.Loading] until the first run finishes, and keeps showing the last result while the
 * next one runs.
 */
@Composable
fun rememberWorkbookScreenUiState(
    initialTaskIndex: Int? = null,
    /** How a snapshot is made from the open project; run on a background thread. The default runs the real gates. */
    load: (OpenProject) -> WorkbookCampaignSnapshot = { loadWorkbookCampaignSnapshot(it) },
): WorkbookScreenUiState {
    val project = rememberOpenProject()
    val session = project?.session
    val uiState =
        remember(session) {
            WorkbookScreenUiState(
                if (project == null) WorkbookCampaignSnapshot.NoProject else WorkbookCampaignSnapshot.Loading,
                initialTaskIndex,
            )
        }
    val manifest = project?.manifest
    LaunchedEffect(session, project?.master?.ufo, manifest?.workbook, manifest?.brief?.styleClass) {
        uiState.show(
            if (project == null) {
                WorkbookCampaignSnapshot.NoProject
            } else {
                withContext(Dispatchers.Default) { load(project) }
            },
        )
    }
    return uiState
}
