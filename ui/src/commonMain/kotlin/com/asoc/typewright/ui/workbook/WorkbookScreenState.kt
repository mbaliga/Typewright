// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.workbook

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.asoc.typewright.ui.project.rememberOpenProject

/** `#s-workbook`'s own fixed task count (`.t2`: "12 tasks -- a submission-ready font at the end"). */
const val WORKBOOK_TASK_COUNT: Int = 12

/**
 * [WorkbookScreen]'s own state: which one of the twelve tasks is showing, and whether the deck's
 * "All tasks" view is, plus the [WorkbookCampaignSnapshot] it renders, which is built from the open
 * project ([loadWorkbookCampaignSnapshot]) and given here. The task opens on the real current/next
 * task of the snapshot, or task 1 where there is none.
 */
class WorkbookScreenUiState(
    val snapshot: WorkbookCampaignSnapshot,
    initialTaskIndex: Int? = null,
) {
    var taskIndex: Int by mutableStateOf((initialTaskIndex ?: defaultTaskIndexFor(snapshot)).coerceIn(1, WORKBOOK_TASK_COUNT))
        internal set

    /** True while the deck's "All tasks" view is showing: the twelve tasks as a list, instead of the one task. */
    var allTasks: Boolean by mutableStateOf(false)
}

/** [WorkbookCampaignSnapshot.Loaded.currentTaskIndex] when data loaded; task 1 (the workbook's own first task) when it did not. */
private fun defaultTaskIndexFor(snapshot: WorkbookCampaignSnapshot): Int =
    when (snapshot) {
        is WorkbookCampaignSnapshot.Loaded -> snapshot.currentTaskIndex
        is WorkbookCampaignSnapshot.NoProject, is WorkbookCampaignSnapshot.Unavailable -> 1
    }

/**
 * A [WorkbookScreenUiState] over the open project, rebuilt when the project's letters, its style
 * class or its confirmed tasks change, so the gates always speak about the font as it is now.
 */
@Composable
fun rememberWorkbookScreenUiState(initialTaskIndex: Int? = null): WorkbookScreenUiState {
    val project = rememberOpenProject()
    val manifest = project?.manifest
    return remember(project?.master?.ufo, manifest?.workbook, manifest?.brief?.styleClass) {
        WorkbookScreenUiState(loadWorkbookCampaignSnapshot(project), initialTaskIndex)
    }
}
