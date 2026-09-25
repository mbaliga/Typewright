// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.workbook

import com.asoc.typewright.campaign.Demonstration
import com.asoc.typewright.campaign.GateCheckResult
import com.asoc.typewright.campaign.GateCheckStatus
import com.asoc.typewright.campaign.WorkbookGateResult
import com.asoc.typewright.campaign.WorkbookGateSpec
import com.asoc.typewright.campaign.WorkbookTask
import com.asoc.typewright.campaign.WorkbookTaskProgress
import com.asoc.typewright.campaign.WorkbookTaskState
import com.asoc.typewright.qa.NodeEconomyReport
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A hand-built [WorkbookTask], entirely synthetic -- no [WorkbookLatinContent.load] call, unlike
 * an earlier version of this file's own [fixtureSnapshot] (`docs/OPEN_QUESTIONS.md`, this verify
 * stage's own finding): that real YAML resource read goes through `campaign`'s own
 * `readCampaignResourceText`, whose wasmJs actual is Node-only by its own documented contract
 * (`CampaignResources.kt`'s KDoc: "Written for `wasmJs { nodejs() }` only... not a browser") --
 * `ui`'s own wasmJs target is a real **browser** (`ui/build.gradle.kts`'s own comment, the same
 * gap `docs/OPEN_QUESTIONS.md` item 92 already names for `qa:corpus`'s loader), so calling it from
 * a test that runs under `:ui:wasmJsBrowserTest` throws `ReferenceError: process is not defined`
 * rather than returning data. This fixture is genuinely portable instead: every field is a plain,
 * hand-built value, the same "no real font parse or corpus load" shape this function's own KDoc
 * always intended.
 */
private fun syntheticTask(index: Int): WorkbookTask =
    WorkbookTask(
        index = index,
        title = "Fixture task $index",
        scaffold = true,
        why = "why $index",
        demonstration = Demonstration.StatDemonstration(label = "fx$index", stats = listOf("stat $index")),
        task = "task $index",
        controlString = null,
        gate = WorkbookGateSpec(summary = "gate $index", qaFunction = null, notImplementedReason = "not wired in this fixture"),
        reflection = "reflect $index",
    )

/** A hand-built [WorkbookCampaignSnapshot.Loaded] -- every task TODO except [currentIndex] CURRENT -- portable across every target (no real font parse, corpus load or resource read, unlike [loadWorkbookCampaignSnapshot] itself; see `WorkbookScreenScreenshotTest`, `desktopTest`-only, for that real load, JVM-only for the same reason `HyleDecoTask4GateTest` already is). */
private fun fixtureSnapshot(currentIndex: Int): WorkbookCampaignSnapshot.Loaded {
    val tasks = (1..WORKBOOK_TASK_COUNT).map { syntheticTask(it) }
    val progress =
        tasks.sortedBy { it.index }.map { task ->
            val state = if (task.index == currentIndex) WorkbookTaskState.CURRENT else WorkbookTaskState.TODO
            val gate =
                WorkbookGateResult(task.index, task.gate.summary, listOf(GateCheckResult("x", GateCheckStatus.NOT_IMPLEMENTED, "n/a")))
            WorkbookTaskProgress(task, gate, state)
        }
    return WorkbookCampaignSnapshot.Loaded(
        tasks = tasks,
        progress = progress,
        currentTaskIndex = currentIndex,
        task4StyleKey = "sans-geometric",
        task4Report = NodeEconomyReport("sans-geometric", emptyList()),
    )
}

class WorkbookScreenUiStateTest {
    @Test
    fun defaultsToTheSnapshotsOwnCurrentTaskIndexWhenLoaded() {
        val state = WorkbookScreenUiState(snapshot = fixtureSnapshot(currentIndex = 6))
        assertEquals(6, state.taskIndex)
    }

    @Test
    fun anExplicitInitialTaskIndexOverridesTheSnapshotsCurrentTask() {
        val state = WorkbookScreenUiState(snapshot = fixtureSnapshot(currentIndex = 6), initialTaskIndex = 9)
        assertEquals(9, state.taskIndex)
    }

    @Test
    fun fallsBackToTaskOneWhenTheSnapshotIsUnavailable() {
        val state = WorkbookScreenUiState(snapshot = WorkbookCampaignSnapshot.Unavailable("no corpus on this target"))
        assertEquals(1, state.taskIndex)
    }

    @Test
    fun explicitInitialTaskIndexIsCoercedIntoTheRealTwelveTaskRange() {
        val loaded = fixtureSnapshot(currentIndex = 1)
        assertEquals(1, WorkbookScreenUiState(snapshot = loaded, initialTaskIndex = 0).taskIndex)
        assertEquals(WORKBOOK_TASK_COUNT, WorkbookScreenUiState(snapshot = loaded, initialTaskIndex = 99).taskIndex)
    }
}

class NextTaskLabelTest {
    @Test
    fun namesTheRealCurrentTaskByIndexAndTitle() {
        val snapshot = fixtureSnapshot(currentIndex = 4)
        val task4 = snapshot.tasks.single { it.index == 4 }
        assertEquals("Task 4 · ${task4.title}", nextTaskLabel(snapshot))
    }

    @Test
    fun neverHardcodesTaskFourAsTheMockupDoes() {
        val snapshot = fixtureSnapshot(currentIndex = 1)
        val task1 = snapshot.tasks.single { it.index == 1 }
        assertEquals("Task 1 · ${task1.title}", nextTaskLabel(snapshot))
    }

    @Test
    fun fallsBackToThePlainWordWorkbookWhenUnavailable() {
        assertEquals("Workbook", nextTaskLabel(WorkbookCampaignSnapshot.Unavailable("reason")))
    }
}
