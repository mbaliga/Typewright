// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.workbook

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** `#s-workbook`'s own fixed task count (`.t2`: "12 tasks -- a submission-ready font at the end"). */
const val WORKBOOK_TASK_COUNT: Int = 12

/**
 * [WorkbookScreen]'s own state: which one of the twelve tasks is showing, plus the real
 * [WorkbookCampaignSnapshot] it renders ([loadWorkbookCampaignSnapshot] by default -- a plain
 * function call, not `@Composable`, evaluated once per instance so a caller building one inside
 * `remember` only pays for the real font parse and corpus load once per composition, matching
 * [WorkbookCampaignSnapshot]'s own KDoc). [taskIndex] is a real Compose state field, the same
 * "GESTURE HONESTY" shape (CLAUDE.md law 4) [com.asoc.typewright.ui.learn.LearnScreenUiState]/
 * [com.asoc.typewright.ui.TypewrightAppNavState] already use, even though nothing in
 * [WorkbookScreen]'s own explorer reference (`#s-workbook`) has an on-screen affordance that ever
 * changes it -- the explorer shows exactly one task at a time with no in-screen task switcher, so
 * this class exists to be *constructed* at a caller-chosen task (the entry point opens it at the
 * real current/next task; a test or screenshot opens it at any task it wants to show), not to be
 * mutated from within [WorkbookScreen] itself.
 */
class WorkbookScreenUiState(
    val snapshot: WorkbookCampaignSnapshot = loadWorkbookCampaignSnapshot(),
    initialTaskIndex: Int? = null,
) {
    var taskIndex: Int by mutableStateOf((initialTaskIndex ?: defaultTaskIndexFor(snapshot)).coerceIn(1, WORKBOOK_TASK_COUNT))
        internal set
}

/** [WorkbookCampaignSnapshot.Loaded.currentTaskIndex] when real data loaded; task 1 (the workbook's own first task) when it did not -- there is no real "current task" fact left to show, so this falls back to where the workbook itself starts rather than a task chosen for looks. */
private fun defaultTaskIndexFor(snapshot: WorkbookCampaignSnapshot): Int =
    when (snapshot) {
        is WorkbookCampaignSnapshot.Loaded -> snapshot.currentTaskIndex
        is WorkbookCampaignSnapshot.Unavailable -> 1
    }

/** Remembers a [WorkbookScreenUiState] for the composition's lifetime -- the real campaign snapshot loads once, here, not on every recomposition. */
@Composable
fun rememberWorkbookScreenUiState(initialTaskIndex: Int? = null): WorkbookScreenUiState =
    remember { WorkbookScreenUiState(initialTaskIndex = initialTaskIndex) }
