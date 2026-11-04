// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.android.widget

import android.content.Context
import com.asoc.typewright.campaign.WorkbookLatinContent
import com.asoc.typewright.project.ProjectState
import com.asoc.typewright.project.ProjectWorkspace
import com.asoc.typewright.project.SaveStatus
import com.asoc.typewright.project.StyleClass
import com.asoc.typewright.project.WorkbookRecord
import com.asoc.typewright.qa.corpus.NodeEconomyCorpus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Publishes the open project to the widgets (SCREENS_V1 §9.1 "Data" and "Redraws are pushed"): a
 * second after the open project's state or save status last changed, it writes a fresh snapshot
 * and asks the widgets to redraw; when the project is closed, it writes "no project open". A
 * process that starts with nothing open (a widget redraw, a cold launch) publishes nothing, so
 * the last snapshot and its as-of time stay. Recent-list changes redraw W3 directly.
 *
 * With no widget placed that reads the snapshot it writes nothing and removes the old snapshot, so
 * an editing session pays nothing for widgets nobody placed; the Workbook gates run only while a
 * W4 or W6 is placed ([publishPlan]). A newly placed widget asks for a fresh publish ([republish]).
 */
class WidgetPublisher(
    private val context: Context,
    private val workspace: ProjectWorkspace,
    private val scope: CoroutineScope,
    private val store: WidgetSnapshotStore = WidgetSnapshotStore(context),
    private val workbook: WorkbookSummaries = WorkbookSummaries(WORKBOOK_TASKS, WORKBOOK_CORPUS),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var cachedFont: Any? = null
    private var cachedRecords: Map<String, WorkbookRecord>? = null
    private var cachedStyle: StyleClass? = null
    private var cachedSummary: WorkbookOutcome? = null

    private val republishes = MutableStateFlow(0)

    /** Starts publishing for the life of [scope]. */
    fun start() {
        scope.launch { publishOpenProject() }
        scope.launch {
            workspace.recents.drop(1).collect { TypewrightWidgets.refresh(context, listOf(WidgetKind.RECENTS)) }
        }
    }

    /** Publishes the open project again, for a widget that was just placed; no-op with nothing open. */
    fun republish() {
        republishes.update { it + 1 }
    }

    private suspend fun publishOpenProject() {
        var hadSession = false
        workspace.current.collectLatest { session ->
            if (session == null) {
                if (hadSession) publishClosed()
                return@collectLatest
            }
            hadSession = true
            combine(session.state, session.saveStatus, republishes) { state, save, _ -> state to save }.collectLatest { (state, save) ->
                delay(PUBLISH_DELAY_MILLIS)
                publishOpen(state, save)
            }
        }
    }

    private suspend fun publishOpen(
        state: ProjectState,
        save: SaveStatus,
    ) {
        val placed = TypewrightWidgets.placedKinds(context)
        val plan = publishPlan(placed)
        if (!plan.write) {
            withContext(Dispatchers.IO) { store.clear() }
            return
        }
        val outcome = if (plan.runWorkbook) workbookFor(state) else WorkbookOutcome.NotRun
        val snapshot = ProjectWidgetSnapshots.of(state, save, outcome, clock(), WidgetSnapshotStore.PROCESS_TOKEN)
        if (!write(snapshot)) return
        TypewrightWidgets.refresh(context, PUBLISHED_KINDS)
        // A save still waiting: look again shortly, so a Typewright that stops before it finishes leaves ◌ Not confirmed.
        if (placed.any { it in SAVE_CHECK_KINDS }) {
            if (snapshot.project?.save == SaveState.SAVING) WidgetAlarms.armSaveCheck(context) else WidgetAlarms.cancelSaveCheck(context)
        }
    }

    private suspend fun publishClosed() {
        if (!publishPlan(TypewrightWidgets.placedKinds(context)).write) {
            withContext(Dispatchers.IO) { store.clear() }
            return
        }
        if (write(ProjectWidgetSnapshots.closed(clock(), WidgetSnapshotStore.PROCESS_TOKEN))) {
            TypewrightWidgets.refresh(context, PUBLISHED_KINDS)
            WidgetAlarms.cancelSaveCheck(context)
        }
    }

    /** Writes [snapshot]; false when it couldn't, which leaves the previous snapshot, whose as-of time is still true. */
    private suspend fun write(snapshot: ProjectWidgetSnapshot): Boolean =
        withContext(Dispatchers.IO) {
            try {
                store.write(snapshot)
                true
            } catch (_: IOException) {
                false
            } catch (_: IllegalStateException) {
                false
            }
        }

    /** The open project's Workbook outcome, recomputed only when its font, confirmations or style class change. */
    private fun workbookFor(state: ProjectState): WorkbookOutcome {
        val records = state.meta.manifest.workbook
        val style = state.meta.manifest.brief.styleClass
        cachedSummary?.let { cached ->
            if (state.font === cachedFont && records == cachedRecords && style == cachedStyle) return cached
        }
        val outcome =
            try {
                WorkbookOutcome.Ready(workbook.summarise(state.font, state.meta.manifest))
            } catch (_: Exception) {
                WorkbookOutcome.Failed
            }
        cachedFont = state.font
        cachedRecords = records
        cachedStyle = style
        cachedSummary = outcome
        return outcome
    }

    private companion object {
        /** How long the open project must be still before it is published. */
        const val PUBLISH_DELAY_MILLIS = 1_000L

        /** The widgets a publish can change; Lineages doesn't read the project. */
        val PUBLISHED_KINDS = WidgetKind.entries - WidgetKind.LINEAGES

        private val tasks by lazy { WorkbookLatinContent.load() }
        private val corpus by lazy { NodeEconomyCorpus.load() }
        val WORKBOOK_TASKS = { tasks }
        val WORKBOOK_CORPUS = { corpus }
    }
}
