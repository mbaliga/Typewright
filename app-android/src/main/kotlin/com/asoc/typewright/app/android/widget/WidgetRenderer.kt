// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.text.format.DateFormat
import android.util.SizeF
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import com.asoc.typewright.app.android.MainActivity
import com.asoc.typewright.app.android.R
import com.asoc.typewright.app.android.TypewrightApplication
import com.asoc.typewright.learn.scenes.LineagesResources
import com.asoc.typewright.learn.scenes.Scene
import com.asoc.typewright.project.ProjectLocation
import com.asoc.typewright.project.RecentProject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.Date

/**
 * Draws the widgets of docs/SCREENS_V1.md §9.2 as [RemoteViews], one per size, for the launcher
 * to choose between. Reads only what each widget needs: the snapshot, the recent list, today's era.
 */
class WidgetRenderer(
    private val app: TypewrightApplication,
) {
    private val locale = app.resources.configuration.locales[0]

    /** Redraws [ids], all of [kind]. */
    suspend fun render(
        kind: WidgetKind,
        manager: AppWidgetManager,
        ids: IntArray,
    ) {
        val inputs = loadInputs(kind)
        for (id in ids) manager.updateAppWidget(id, sized(kind) { size -> build(kind, id, inputs, size) })
    }

    /** Draws [kind]'s error state on [ids], for when reading its data failed outright. */
    fun renderUnreadable(
        kind: WidgetKind,
        manager: AppWidgetManager,
        ids: IntArray,
    ) {
        val inputs = Inputs(SnapshotRead.Unreadable, recents = null, openLocation = null, lineage = null)
        for (id in ids) manager.updateAppWidget(id, sized(kind) { size -> build(kind, id, inputs, size) })
    }

    // ---- data ------------------------------------------------------------------------------------

    private class Inputs(
        val snapshot: SnapshotRead,
        /** Null when the recent list couldn't be read. */
        val recents: List<RecentProject>?,
        val openLocation: ProjectLocation?,
        val lineage: LineageCard?,
    )

    private suspend fun loadInputs(kind: WidgetKind): Inputs {
        val needsSnapshot = kind != WidgetKind.LINEAGES && kind != WidgetKind.RECENTS
        val snapshot = if (needsSnapshot) WidgetSnapshotStore(app).read() else SnapshotRead.Absent
        val recents =
            if (kind == WidgetKind.RECENTS) {
                try {
                    app.workspace.loadRecentProjects()
                } catch (_: Exception) {
                    null
                }
            } else {
                emptyList()
            }
        val openLocation =
            app.workspace.current.value
                ?.store
                ?.location
        val lineage = if (kind == WidgetKind.LINEAGES || kind == WidgetKind.SHORTCUTS) todaysLineage() else null
        return Inputs(snapshot, recents, openLocation, lineage)
    }

    private fun todaysLineage(): LineageCard? {
        val scenes = eraScenes.getOrNull() ?: return null
        return lineageFor(scenes, localEpochDay(System.currentTimeMillis(), ZoneId.systemDefault()))
    }

    // ---- layouts ---------------------------------------------------------------------------------

    private fun sized(
        kind: WidgetKind,
        build: (WidgetSize) -> RemoteViews,
    ): RemoteViews =
        RemoteViews(
            WidgetSizes.breakpoints(kind).entries.associate { (size, start) -> SizeF(start.width, start.height) to build(size) },
        )

    private fun build(
        kind: WidgetKind,
        id: Int,
        inputs: Inputs,
        size: WidgetSize,
    ): RemoteViews =
        when (kind) {
            WidgetKind.APPROVED -> approved(id, inputs, size)
            WidgetKind.SAVED -> saved(id, inputs, size)
            WidgetKind.RECENTS -> recents(id, inputs, size)
            WidgetKind.WORKBOOK -> workbook(id, inputs, size)
            WidgetKind.LINEAGES -> lineages(id, inputs, size)
            WidgetKind.SHORTCUTS -> shortcuts(id, inputs, size)
        }

    private fun approved(
        id: Int,
        inputs: Inputs,
        size: WidgetSize,
    ): RemoteViews {
        val rv = frame(R.layout.widget_approved, id, WidgetDestination.HOME, size)
        val view = approvedView(inputs.snapshot)
        val ready = view as? OpenProjectView.Ready ?: return openProjectMessage(rv, view, size)
        val counts = ready.value
        if (counts.isEmpty) return message(rv, R.string.widget_approved_empty_title, R.string.widget_approved_empty_body, size)
        rv.setTextViewText(R.id.widget_title, projectTitle(ready))
        rv.show(R.id.widget_title, size != WidgetSize.SMALL)
        rv.setTextViewText(R.id.widget_value, app.getString(R.string.widget_approved_value, number(counts.approved), number(counts.glyphs)))
        rv.setContentDescription(
            R.id.widget_value,
            app.getString(R.string.widget_approved_spoken, number(counts.approved), number(counts.glyphs)),
        )
        rv.setProgressBar(R.id.widget_bar, counts.glyphs, counts.approved, false)
        val shown = counts.rows(size)
        for (row in ApprovalRow.entries) {
            val (container, mark, word, count) = approvalRowIds(row)
            rv.show(container, row in shown)
            rv.setImageViewResource(mark, markDrawable(row.mark))
            rv.setTextViewText(word, app.getString(approvalRowWord(row)))
            rv.setTextViewText(count, number(counts.count(row)))
        }
        asOf(rv, ready.asOfMillis, size != WidgetSize.SMALL)
        return rv
    }

    private fun saved(
        id: Int,
        inputs: Inputs,
        size: WidgetSize,
    ): RemoteViews {
        val rv = frame(R.layout.widget_saved, id, WidgetDestination.HOME, size)
        val view = savedView(inputs.snapshot, WidgetSnapshotStore.PROCESS_TOKEN)
        val ready = view as? OpenProjectView.Ready ?: return openProjectMessage(rv, view, size)
        val value = ready.value
        rv.setTextViewText(R.id.widget_title, projectTitle(ready))
        rv.show(R.id.widget_title, size != WidgetSize.SMALL)
        rv.setImageViewResource(R.id.widget_state_mark, markDrawable(value.kind.mark))
        rv.setTextViewText(R.id.widget_state_word, app.getString(savedWord(value.kind)))
        val savedAt = value.savedAtMillis
        val whenText =
            if (savedAt != null) {
                app.getString(R.string.widget_saved_at, dateTime(savedAt))
            } else {
                app.getString(R.string.widget_as_of, dateTime(ready.asOfMillis))
            }
        rv.setTextViewText(R.id.widget_when, whenText)
        rv.show(R.id.widget_when, size != WidgetSize.SMALL)
        rv.setTextViewText(R.id.widget_sentence, app.getString(savedSentence(value.kind)))
        rv.show(R.id.widget_sentence, size == WidgetSize.LARGE)
        return rv
    }

    private fun recents(
        id: Int,
        inputs: Inputs,
        size: WidgetSize,
    ): RemoteViews {
        val rv = frame(R.layout.widget_recents, id, WidgetDestination.HOME, size)
        val recents = inputs.recents ?: return message(rv, R.string.widget_recents_error_title, R.string.widget_recents_error_body, size)
        val view = recentsView(recents, inputs.openLocation, size)
        if (view.rows.isEmpty()) return message(rv, R.string.widget_recents_empty_title, R.string.widget_recents_empty_body, size)
        RECENT_ROWS.forEachIndexed { index, ids ->
            val row = view.rows.getOrNull(index)
            rv.show(ids.row, row != null)
            if (row == null) return@forEachIndexed
            val opened = app.getString(R.string.widget_recents_opened, dateTime(row.openedAtMillis))
            val glyphs = row.glyphs?.let { app.resources.getQuantityString(R.plurals.widget_glyph_count, it, number(it)) }
            // Withheld names (§9.1 Privacy): the row is its glyph count over the time it was opened.
            val (title, meta) =
                when {
                    row.name != null -> row.name to (glyphs?.let { app.getString(R.string.widget_recents_meta, it, opened) } ?: opened)
                    glyphs != null -> glyphs to opened
                    else -> app.getString(R.string.widget_recents_opened_title, dateTime(row.openedAtMillis)) to null
                }
            rv.setTextViewText(ids.name, title)
            rv.setTextViewText(ids.meta, meta.orEmpty())
            rv.show(ids.meta, size != WidgetSize.SMALL && meta != null)
            rv.show(ids.open, row.isOpen && size != WidgetSize.SMALL)
            if (size == WidgetSize.SMALL) {
                // Small is one tap target: the whole widget opens Typewright (§9.1).
                rv.setOnClickPendingIntent(ids.row, launch(id, SLOT_ROOT, LaunchRequest(WidgetDestination.HOME)))
            } else {
                val details = listOfNotNull(title, meta).joinToString(separator = ". ")
                val spoken =
                    when {
                        row.name != null && row.isOpen -> app.getString(R.string.widget_recents_row_spoken_open, row.name, meta)
                        row.name != null -> app.getString(R.string.widget_recents_row_spoken, row.name, meta)
                        row.isOpen -> app.getString(R.string.widget_recents_row_spoken_withheld_open, details)
                        else -> app.getString(R.string.widget_recents_row_spoken_withheld, details)
                    }
                rv.setContentDescription(ids.row, spoken)
                rv.setOnClickPendingIntent(ids.row, launch(id, SLOT_FIRST + index, LaunchRequest(WidgetDestination.PROJECT, row.key)))
            }
        }
        rv.setTextViewText(R.id.widget_more, app.resources.getQuantityString(R.plurals.widget_recents_more, view.more, number(view.more)))
        rv.show(R.id.widget_more, view.more > 0)
        return rv
    }

    private fun workbook(
        id: Int,
        inputs: Inputs,
        size: WidgetSize,
    ): RemoteViews {
        val rv = frame(R.layout.widget_workbook, id, WidgetDestination.WORKBOOK, size)
        val view = workbookView(inputs.snapshot)
        val ready = view as? OpenProjectView.Ready ?: return openProjectMessage(rv, view, size)
        val summary =
            when (val outcome = ready.value) {
                is WorkbookOutcome.Ready -> outcome.summary

                WorkbookOutcome.Failed -> return message(
                    rv,
                    R.string.widget_workbook_error_title,
                    R.string.widget_workbook_error_body,
                    size,
                )

                WorkbookOutcome.NotRun -> return message(
                    rv,
                    R.string.widget_workbook_not_run_title,
                    R.string.widget_workbook_not_run_body,
                    size,
                )
            }
        if (size == WidgetSize.SMALL) {
            rv.setTextViewText(R.id.widget_label, app.getString(R.string.widget_workbook_task_of, summary.currentIndex, summary.total))
            rv.setTextViewText(R.id.widget_title, summary.currentTitle)
            rv.setInt(R.id.widget_title, "setMaxLines", 1)
        } else {
            rv.setTextViewText(R.id.widget_label, app.getString(R.string.widget_workbook_label))
            rv.setTextViewText(R.id.widget_title, app.getString(R.string.widget_workbook_title, summary.currentIndex, summary.currentTitle))
        }
        rv.show(R.id.widget_strip, size != WidgetSize.SMALL)
        STRIP.forEachIndexed { index, viewId ->
            val state = summary.states.getOrNull(index)
            rv.show(viewId, state != null)
            if (state != null) {
                rv.setImageViewResource(viewId, markDrawable(state.mark()))
                rv.setContentDescription(viewId, app.getString(taskStateSpoken(state), index + 1))
            }
        }
        val done = app.resources.getQuantityString(R.plurals.widget_workbook_done, summary.done, summary.done, summary.total)
        rv.setTextViewText(R.id.widget_done, done)
        rv.show(R.id.widget_done, size != WidgetSize.SMALL)
        rv.setTextViewText(R.id.widget_gate, app.getString(R.string.widget_workbook_gate, summary.currentGateSummary))
        rv.show(R.id.widget_gate, size == WidgetSize.LARGE)
        val checks = summary.checkCounts()
        rv.show(R.id.widget_checks, size == WidgetSize.LARGE && checks.isNotEmpty())
        CHECK_SLOTS.forEachIndexed { index, (line, mark, word) ->
            val check = checks.getOrNull(index)
            rv.show(line, check != null)
            if (check != null) {
                rv.setImageViewResource(mark, markDrawable(check.first.mark()))
                rv.setTextViewText(word, app.resources.getQuantityString(checkWord(check.first), check.second, check.second))
            }
        }
        rv.show(R.id.widget_scaffold, size == WidgetSize.LARGE && summary.currentScaffold)
        asOf(rv, ready.asOfMillis, size != WidgetSize.SMALL)
        return rv
    }

    private fun lineages(
        id: Int,
        inputs: Inputs,
        size: WidgetSize,
    ): RemoteViews {
        val rv = frame(R.layout.widget_lineages, id, WidgetDestination.LEARN, size)
        val card = inputs.lineage ?: return message(rv, R.string.widget_lineages_error_title, R.string.widget_lineages_error_body, size)
        rv.setTextViewText(R.id.widget_label, app.getString(R.string.widget_lineages_label, card.position, card.count))
        rv.show(R.id.widget_label, size != WidgetSize.SMALL)
        rv.setTextViewText(R.id.widget_title, card.title)
        rv.setTextViewText(R.id.widget_era, card.era.orEmpty())
        rv.show(R.id.widget_era, card.era != null)
        rv.setTextViewText(R.id.widget_caption, card.caption)
        rv.setInt(R.id.widget_caption, "setMaxLines", if (size == WidgetSize.LARGE) LARGE_CAPTION_LINES else MEDIUM_CAPTION_LINES)
        rv.show(R.id.widget_caption, size != WidgetSize.SMALL)
        rv.setTextViewText(R.id.widget_tags, card.tags.joinToString(separator = " · "))
        rv.show(R.id.widget_tags, size == WidgetSize.LARGE && card.tags.isNotEmpty())
        rv.setTextViewText(R.id.widget_tool, app.getString(R.string.widget_lineages_made_with, card.tool))
        rv.show(R.id.widget_tool, size == WidgetSize.LARGE && card.tool.isNotBlank())
        rv.show(R.id.widget_scaffold, size == WidgetSize.LARGE && card.scaffold)
        return rv
    }

    private fun shortcuts(
        id: Int,
        inputs: Inputs,
        size: WidgetSize,
    ): RemoteViews {
        val rv = RemoteViews(app.packageName, R.layout.widget_shortcuts)
        rv.setOnClickPendingIntent(android.R.id.background, launch(id, SLOT_ROOT, LaunchRequest(WidgetDestination.HOME)))
        val view = shortcutsView(inputs.snapshot, inputs.lineage)
        val workbookSub =
            if (view.taskIndex != null && view.taskTitle != null) {
                app.getString(R.string.widget_workbook_title, view.taskIndex, view.taskTitle)
            } else {
                app.getString(R.string.widget_pad_workbook_default)
            }
        val learnSub =
            view.eraTitle?.let { app.getString(R.string.widget_pad_learn_era, it) } ?: app.getString(R.string.widget_pad_learn_default)
        val resumeSub =
            when {
                view.projectName != null -> view.projectName
                view.projectOpen -> app.getString(R.string.widget_your_font)
                else -> app.getString(R.string.widget_pad_resume_default)
            }
        val pads =
            listOf(
                Triple(WidgetDestination.HOME, R.string.widget_pad_resume, resumeSub),
                Triple(WidgetDestination.WORKBOOK, R.string.widget_pad_workbook, workbookSub),
                Triple(WidgetDestination.LEARN, R.string.widget_pad_learn, learnSub),
            )
        PADS.forEachIndexed { index, ids ->
            val (destination, label, sub) = pads[index]
            rv.show(ids.pad, index == 0 || size != WidgetSize.SMALL)
            rv.setTextViewText(ids.label, app.getString(label))
            rv.setTextViewText(ids.sub, sub)
            rv.show(ids.sub, size == WidgetSize.LARGE)
            rv.setOnClickPendingIntent(ids.pad, launch(id, SLOT_FIRST + index, LaunchRequest(destination)))
        }
        return rv
    }

    // ---- helpers ---------------------------------------------------------------------------------

    /** [layout] with its whole surface opening [destination]; small layouts centre their content. */
    private fun frame(
        layout: Int,
        id: Int,
        destination: WidgetDestination,
        size: WidgetSize,
    ): RemoteViews {
        val rv = RemoteViews(app.packageName, layout)
        rv.setOnClickPendingIntent(android.R.id.background, launch(id, SLOT_ROOT, LaunchRequest(destination)))
        val gravity = if (size == WidgetSize.SMALL) Gravity.CENTER_VERTICAL else Gravity.TOP
        rv.setInt(android.R.id.background, "setGravity", gravity)
        rv.setInt(R.id.widget_content, "setGravity", gravity)
        return rv
    }

    /** The open project's title: its name, or "Your font" while names are withheld (§9.1 Privacy). */
    private fun projectTitle(ready: OpenProjectView.Ready<*>): String = ready.name ?: app.getString(R.string.widget_your_font)

    private fun openProjectMessage(
        rv: RemoteViews,
        view: OpenProjectView<*>,
        size: WidgetSize,
    ): RemoteViews =
        if (view is OpenProjectView.NoProject) {
            message(rv, R.string.widget_no_project_title, R.string.widget_no_project_body, size)
        } else {
            message(rv, R.string.widget_unreadable_title, R.string.widget_unreadable_body, size)
        }

    /** Replaces [rv]'s content with a state's title and, above small, its line beneath. */
    private fun message(
        rv: RemoteViews,
        title: Int,
        body: Int,
        size: WidgetSize,
    ): RemoteViews {
        rv.show(R.id.widget_content, false)
        rv.show(R.id.widget_message, true)
        rv.setTextViewText(R.id.widget_message_title, app.getString(title))
        rv.setTextViewText(R.id.widget_message_body, app.getString(body))
        rv.show(R.id.widget_message_body, size != WidgetSize.SMALL)
        rv.setInt(R.id.widget_message_title, "setMaxLines", if (size == WidgetSize.SMALL) 1 else 2)
        return rv
    }

    private fun asOf(
        rv: RemoteViews,
        millis: Long,
        visible: Boolean,
    ) {
        rv.setTextViewText(R.id.widget_as_of, app.getString(R.string.widget_as_of, dateTime(millis)))
        rv.show(R.id.widget_as_of, visible)
    }

    private fun launch(
        id: Int,
        slot: Int,
        request: LaunchRequest,
    ): PendingIntent {
        val intent =
            Intent(app, MainActivity::class.java)
                .setAction(WidgetLaunch.ACTION_OPEN_FROM_WIDGET)
                .putExtra(WidgetLaunch.EXTRA_DESTINATION, request.destination.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        request.projectKey?.let { intent.putExtra(WidgetLaunch.EXTRA_PROJECT_KEY, it) }
        return PendingIntent.getActivity(
            app,
            id * SLOTS_PER_WIDGET + slot,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** A date and time that stays true without a redraw: day, month and time, in the phone's 12/24-hour setting. */
    private fun dateTime(millis: Long): String {
        val skeleton = if (DateFormat.is24HourFormat(app)) "dMMMHHmm" else "dMMMhmma"
        return SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(Date(millis))
    }

    private fun number(value: Int): String = NumberFormat.getIntegerInstance(locale).format(value)

    private fun RemoteViews.show(
        viewId: Int,
        visible: Boolean,
    ) = setViewVisibility(viewId, if (visible) View.VISIBLE else View.GONE)

    private data class RecentRowIds(
        val row: Int,
        val name: Int,
        val meta: Int,
        val open: Int,
    )

    private data class PadIds(
        val pad: Int,
        val label: Int,
        val sub: Int,
    )

    private data class RowIds(
        val container: Int,
        val mark: Int,
        val word: Int,
        val count: Int,
    )

    private fun approvalRowIds(row: ApprovalRow): RowIds =
        when (row) {
            ApprovalRow.APPROVED -> {
                RowIds(
                    R.id.widget_row_approved,
                    R.id.widget_row_approved_mark,
                    R.id.widget_row_approved_word,
                    R.id.widget_row_approved_count,
                )
            }

            ApprovalRow.UNLOCKED -> {
                RowIds(
                    R.id.widget_row_unlocked,
                    R.id.widget_row_unlocked_mark,
                    R.id.widget_row_unlocked_word,
                    R.id.widget_row_unlocked_count,
                )
            }

            ApprovalRow.NOT_YET -> {
                RowIds(R.id.widget_row_not_yet, R.id.widget_row_not_yet_mark, R.id.widget_row_not_yet_word, R.id.widget_row_not_yet_count)
            }
        }

    private companion object {
        const val SLOTS_PER_WIDGET = 8
        const val SLOT_ROOT = 0
        const val SLOT_FIRST = 1
        const val MEDIUM_CAPTION_LINES = 2
        const val LARGE_CAPTION_LINES = 5

        /** The ten era scenes, parsed once per process; a failure is W5's error state. */
        val eraScenes: Result<List<Scene>> by lazy { runCatching { LineagesResources.loadEraScenes() } }

        val RECENT_ROWS =
            listOf(
                RecentRowIds(R.id.widget_recent_0, R.id.widget_recent_0_name, R.id.widget_recent_0_meta, R.id.widget_recent_0_open),
                RecentRowIds(R.id.widget_recent_1, R.id.widget_recent_1_name, R.id.widget_recent_1_meta, R.id.widget_recent_1_open),
                RecentRowIds(R.id.widget_recent_2, R.id.widget_recent_2_name, R.id.widget_recent_2_meta, R.id.widget_recent_2_open),
                RecentRowIds(R.id.widget_recent_3, R.id.widget_recent_3_name, R.id.widget_recent_3_meta, R.id.widget_recent_3_open),
                RecentRowIds(R.id.widget_recent_4, R.id.widget_recent_4_name, R.id.widget_recent_4_meta, R.id.widget_recent_4_open),
            )

        val PADS =
            listOf(
                PadIds(R.id.widget_pad_0, R.id.widget_pad_0_label, R.id.widget_pad_0_sub),
                PadIds(R.id.widget_pad_1, R.id.widget_pad_1_label, R.id.widget_pad_1_sub),
                PadIds(R.id.widget_pad_2, R.id.widget_pad_2_label, R.id.widget_pad_2_sub),
            )

        val STRIP =
            listOf(
                R.id.widget_strip_0,
                R.id.widget_strip_1,
                R.id.widget_strip_2,
                R.id.widget_strip_3,
                R.id.widget_strip_4,
                R.id.widget_strip_5,
                R.id.widget_strip_6,
                R.id.widget_strip_7,
                R.id.widget_strip_8,
                R.id.widget_strip_9,
                R.id.widget_strip_10,
                R.id.widget_strip_11,
            )

        val CHECK_SLOTS =
            listOf(
                Triple(R.id.widget_check_0, R.id.widget_check_0_mark, R.id.widget_check_0_word),
                Triple(R.id.widget_check_1, R.id.widget_check_1_mark, R.id.widget_check_1_word),
                Triple(R.id.widget_check_2, R.id.widget_check_2_mark, R.id.widget_check_2_word),
                Triple(R.id.widget_check_3, R.id.widget_check_3_mark, R.id.widget_check_3_word),
                Triple(R.id.widget_check_4, R.id.widget_check_4_mark, R.id.widget_check_4_word),
            )

        fun markDrawable(mark: Mark): Int =
            when (mark) {
                Mark.PASS -> R.drawable.widget_mark_pass
                Mark.WARN -> R.drawable.widget_mark_warn
                Mark.FAIL -> R.drawable.widget_mark_fail
                Mark.INFO -> R.drawable.widget_mark_info
                Mark.TODO -> R.drawable.widget_mark_todo
                Mark.BLOCK -> R.drawable.widget_mark_block
            }

        fun approvalRowWord(row: ApprovalRow): Int =
            when (row) {
                ApprovalRow.APPROVED -> R.string.widget_approved_row_approved
                ApprovalRow.UNLOCKED -> R.string.widget_approved_row_unlocked
                ApprovalRow.NOT_YET -> R.string.widget_approved_row_not_yet
            }

        fun savedWord(kind: SavedKind): Int =
            when (kind) {
                SavedKind.SAVED -> R.string.widget_saved_saved
                SavedKind.SAVING -> R.string.widget_saved_saving
                SavedKind.FAILED -> R.string.widget_saved_failed
                SavedKind.CONFLICT -> R.string.widget_saved_conflict
                SavedKind.READ_ONLY -> R.string.widget_saved_read_only
                SavedKind.TAB_ONLY -> R.string.widget_saved_tab_only
                SavedKind.NOT_CONFIRMED -> R.string.widget_saved_not_confirmed
            }

        fun savedSentence(kind: SavedKind): Int =
            when (kind) {
                SavedKind.SAVED -> R.string.widget_saved_sentence_saved
                SavedKind.SAVING -> R.string.widget_saved_sentence_saving
                SavedKind.FAILED -> R.string.widget_saved_sentence_failed
                SavedKind.CONFLICT -> R.string.widget_saved_sentence_conflict
                SavedKind.READ_ONLY -> R.string.widget_saved_sentence_read_only
                SavedKind.TAB_ONLY -> R.string.widget_saved_sentence_tab_only
                SavedKind.NOT_CONFIRMED -> R.string.widget_saved_sentence_not_confirmed
            }

        fun taskStateSpoken(state: TaskState): Int =
            when (state) {
                TaskState.DONE -> R.string.widget_workbook_task_done
                TaskState.CURRENT -> R.string.widget_workbook_task_current
                TaskState.TODO -> R.string.widget_workbook_task_todo
            }

        fun checkWord(state: CheckState): Int =
            when (state) {
                CheckState.PASS -> R.plurals.widget_workbook_check_pass
                CheckState.WARN -> R.plurals.widget_workbook_check_warn
                CheckState.FAIL -> R.plurals.widget_workbook_check_fail
                CheckState.INFO -> R.plurals.widget_workbook_check_info
                CheckState.NOT_MEASURED -> R.plurals.widget_workbook_check_not_measured
            }
    }
}
