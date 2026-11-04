// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.android.widget

import com.asoc.typewright.learn.scenes.Scene
import com.asoc.typewright.project.ProjectLocation
import com.asoc.typewright.project.RecentProject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * What a widget about the open project shows (SCREENS_V1 §9.1 "States"): no project open, the
 * snapshot unreadable, or the project's [name] (null when [ProjectNames.WITHHELD]), its
 * [asOfMillis] and the widget's own [value].
 */
sealed interface OpenProjectView<out T> {
    data object NoProject : OpenProjectView<Nothing>

    data object Unreadable : OpenProjectView<Nothing>

    data class Ready<T>(
        val name: String?,
        val asOfMillis: Long,
        val value: T,
    ) : OpenProjectView<T>
}

private fun <T> openProjectView(
    read: SnapshotRead,
    names: ProjectNames,
    value: (ProjectWidgetSnapshot, OpenProject) -> T,
): OpenProjectView<T> =
    when (read) {
        SnapshotRead.Absent -> {
            OpenProjectView.NoProject
        }

        SnapshotRead.Unreadable -> {
            OpenProjectView.Unreadable
        }

        is SnapshotRead.Present -> {
            val project = read.snapshot.project
            if (project == null) {
                OpenProjectView.NoProject
            } else {
                OpenProjectView.Ready(project.name.shownAs(names), read.snapshot.writtenAtMillis, value(read.snapshot, project))
            }
        }
    }

/** This name if [names] lets it show, else null. */
private fun String.shownAs(names: ProjectNames): String? = takeIf { names == ProjectNames.SHOWN }

// ---- W1 Approved ---------------------------------------------------------------------------------

/** One of W1's large-layout rows. */
enum class ApprovalRow(
    val mark: Mark,
) {
    APPROVED(Mark.PASS),
    UNLOCKED(Mark.WARN),
    NOT_YET(Mark.TODO),
}

/** W1's numbers: [approved] and [unlocked] out of [glyphs]. */
data class ApprovedCounts(
    val glyphs: Int,
    val approved: Int,
    val unlocked: Int,
) {
    val notYetApproved: Int get() = glyphs - approved - unlocked

    /** No glyphs at all: W1's empty state. */
    val isEmpty: Boolean get() = glyphs == 0

    /** [row]'s count. */
    fun count(row: ApprovalRow): Int =
        when (row) {
            ApprovalRow.APPROVED -> approved
            ApprovalRow.UNLOCKED -> unlocked
            ApprovalRow.NOT_YET -> notYetApproved
        }

    /** The rows [size] shows: none when small, the unlocked line on medium (only if any), all three on large. */
    fun rows(size: WidgetSize): List<ApprovalRow> =
        when (size) {
            WidgetSize.SMALL -> emptyList()
            WidgetSize.MEDIUM -> if (unlocked > 0) listOf(ApprovalRow.UNLOCKED) else emptyList()
            WidgetSize.LARGE -> ApprovalRow.entries
        }
}

/** W1's content from the snapshot. */
fun approvedView(
    read: SnapshotRead,
    names: ProjectNames = WidgetPrivacy.projectNames,
): OpenProjectView<ApprovedCounts> =
    openProjectView(read, names) { _, project -> ApprovedCounts(project.glyphs, project.approved, project.unlocked) }

// ---- W2 Saved ------------------------------------------------------------------------------------

/** W2's states, each with its mark (SCREENS_V1 §9.2 W2). */
enum class SavedKind(
    val mark: Mark,
) {
    SAVED(Mark.PASS),
    SAVING(Mark.WARN),
    FAILED(Mark.FAIL),
    CONFLICT(Mark.FAIL),
    READ_ONLY(Mark.INFO),
    TAB_ONLY(Mark.INFO),
    NOT_CONFIRMED(Mark.TODO),
}

/** W2's value: the state and, when saved, when. */
data class SavedValue(
    val kind: SavedKind,
    val savedAtMillis: Long?,
)

/**
 * W2's content. A save still waiting when the snapshot was written becomes
 * [SavedKind.NOT_CONFIRMED] once [currentWriter] (this process) is no longer the process that
 * wrote it: whoever was saving has stopped, so the widget can't say it finished.
 */
fun savedView(
    read: SnapshotRead,
    currentWriter: String,
    names: ProjectNames = WidgetPrivacy.projectNames,
): OpenProjectView<SavedValue> =
    openProjectView(read, names) { snapshot, project ->
        val kind =
            when (project.save) {
                SaveState.SAVED -> SavedKind.SAVED
                SaveState.SAVING -> if (snapshot.writer == currentWriter) SavedKind.SAVING else SavedKind.NOT_CONFIRMED
                SaveState.FAILED -> SavedKind.FAILED
                SaveState.CONFLICT -> SavedKind.CONFLICT
                SaveState.READ_ONLY -> SavedKind.READ_ONLY
                SaveState.TAB_ONLY -> SavedKind.TAB_ONLY
            }
        SavedValue(kind, project.savedAtMillis.takeIf { kind == SavedKind.SAVED })
    }

/**
 * Whether W2 and W3 should look again in a couple of minutes: [read] shows a save still waiting,
 * written by [currentWriter] (this process). If this process then stops, nothing else would redraw
 * them, and W2 would keep saying ◐ Saving where it should say ◌ Not confirmed (§9.2 W2).
 */
fun saveCheckDue(
    read: SnapshotRead,
    currentWriter: String,
): Boolean {
    val snapshot = (read as? SnapshotRead.Present)?.snapshot ?: return false
    return snapshot.project?.save == SaveState.SAVING && snapshot.writer == currentWriter
}

// ---- W3 Recent projects --------------------------------------------------------------------------

/**
 * One W3 row. [name] is null when [ProjectNames.WITHHELD]; [glyphs] is null when the recent list
 * has no summary for it.
 */
data class RecentRow(
    val name: String?,
    val glyphs: Int?,
    val openedAtMillis: Long,
    val isOpen: Boolean,
    val key: String,
)

/** W3's rows for one size, and how many more the app holds. No rows is W3's empty state. */
data class RecentsView(
    val rows: List<RecentRow>,
    val more: Int,
)

/** How many rows W3 shows at [size]: 1, 3 or 5. */
fun recentRowsFor(size: WidgetSize): Int =
    when (size) {
        WidgetSize.SMALL -> 1
        WidgetSize.MEDIUM -> 3
        WidgetSize.LARGE -> 5
    }

/** W3's content: most recently opened first, [openLocation]'s row marked open. No folder ever leaves this function. */
fun recentsView(
    recents: List<RecentProject>,
    openLocation: ProjectLocation?,
    size: WidgetSize,
    names: ProjectNames = WidgetPrivacy.projectNames,
): RecentsView {
    val ordered = recents.sortedByDescending { it.lastOpened }
    val shown = ordered.take(recentRowsFor(size))
    val rows =
        shown.map { recent ->
            RecentRow(
                name = recent.name.shownAs(names),
                glyphs = recent.summary?.glyphs,
                openedAtMillis = recent.lastOpened.toEpochMilliseconds(),
                isOpen = recent.location == openLocation,
                key = WidgetLaunch.recentKey(recent.location),
            )
        }
    val more = if (size == WidgetSize.LARGE) ordered.size - shown.size else 0
    return RecentsView(rows, more)
}

// ---- W4 Workbook ---------------------------------------------------------------------------------

/** A strip square's mark: ✓ done, ■ current, ◌ to do. */
fun TaskState.mark(): Mark =
    when (this) {
        TaskState.DONE -> Mark.PASS
        TaskState.CURRENT -> Mark.BLOCK
        TaskState.TODO -> Mark.TODO
    }

/** A gate check's mark. */
fun CheckState.mark(): Mark =
    when (this) {
        CheckState.PASS -> Mark.PASS
        CheckState.WARN -> Mark.WARN
        CheckState.FAIL -> Mark.FAIL
        CheckState.INFO -> Mark.INFO
        CheckState.NOT_MEASURED -> Mark.TODO
    }

/** W4's large-layout check counts, most urgent first, zero counts left out. */
fun WorkbookSummary.checkCounts(): List<Pair<CheckState, Int>> =
    listOf(CheckState.FAIL, CheckState.WARN, CheckState.PASS, CheckState.INFO, CheckState.NOT_MEASURED)
        .mapNotNull { state -> currentChecks[state]?.takeIf { it > 0 }?.let { state to it } }

/** W4's content: the open project's Workbook outcome. */
fun workbookView(
    read: SnapshotRead,
    names: ProjectNames = WidgetPrivacy.projectNames,
): OpenProjectView<WorkbookOutcome> = openProjectView(read, names) { _, project -> project.workbook }

// ---- W5 Lineages ---------------------------------------------------------------------------------

/** W5's card: one era scene, its place in the ten, and its caption. */
data class LineageCard(
    val position: Int,
    val count: Int,
    val title: String,
    val era: String?,
    val caption: String,
    val tags: List<String>,
    val tool: String,
    val scaffold: Boolean,
)

/** The era for local day [epochDay]: the scenes in play order, one a day, in turn. Null when there are none. */
fun lineageFor(
    scenes: List<Scene>,
    epochDay: Long,
): LineageCard? {
    if (scenes.isEmpty()) return null
    val index = Math.floorMod(epochDay, scenes.size.toLong()).toInt()
    val scene = scenes[index]
    return LineageCard(
        position = index + 1,
        count = scenes.size,
        title = scene.title,
        era = scene.era,
        caption = scene.caption.text.trim(),
        tags = scene.caption.tags,
        tool = scene.caption.tool,
        scaffold = scene.scaffold,
    )
}

/** The local day number of [nowMillis] in [zone]. */
fun localEpochDay(
    nowMillis: Long,
    zone: ZoneId,
): Long =
    Instant
        .ofEpochMilli(nowMillis)
        .atZone(zone)
        .toLocalDate()
        .toEpochDay()

/** The next local midnight after [nowMillis] in [zone], when W5 turns to the next era. */
fun nextLocalMidnightMillis(
    nowMillis: Long,
    zone: ZoneId,
): Long =
    LocalDate
        .ofEpochDay(localEpochDay(nowMillis, zone) + 1)
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()

// ---- W6 Shortcuts --------------------------------------------------------------------------------

/**
 * W6's three sub-lines: whether a project is open and its name (null when withheld or none), the
 * current task, today's era; null where there's none.
 */
data class ShortcutsView(
    val projectOpen: Boolean,
    val projectName: String?,
    val taskIndex: Int?,
    val taskTitle: String?,
    val eraTitle: String?,
)

/** W6's content from the snapshot and today's card. */
fun shortcutsView(
    read: SnapshotRead,
    lineage: LineageCard?,
    names: ProjectNames = WidgetPrivacy.projectNames,
): ShortcutsView {
    val project = (read as? SnapshotRead.Present)?.snapshot?.project
    val workbook = (project?.workbook as? WorkbookOutcome.Ready)?.summary
    return ShortcutsView(
        projectOpen = project != null,
        projectName = project?.name?.shownAs(names),
        taskIndex = workbook?.currentIndex,
        taskTitle = workbook?.currentTitle,
        eraTitle = lineage?.title,
    )
}

// ---- Publishing and timed redraws ----------------------------------------------------------------

/** The widgets that read the open-project snapshot. */
val SNAPSHOT_KINDS: Set<WidgetKind> = setOf(WidgetKind.APPROVED, WidgetKind.SAVED, WidgetKind.WORKBOOK, WidgetKind.SHORTCUTS)

/** The widgets that print today's era, and so redraw just after local midnight. */
val MIDNIGHT_KINDS: Set<WidgetKind> = setOf(WidgetKind.LINEAGES, WidgetKind.SHORTCUTS)

/** The widgets a stopped Typewright leaves out of date: W2's ◐ Saving and W3's OPEN block. */
val SAVE_CHECK_KINDS: Set<WidgetKind> = setOf(WidgetKind.SAVED, WidgetKind.RECENTS)

/**
 * What a publish does, given the widgets placed now: with none that reads the snapshot it writes
 * nothing and removes any old snapshot ([write] false), so the app pays nothing for widgets nobody
 * placed; the Workbook gates run only for a placed W4 or W6 ([runWorkbook]).
 */
data class PublishPlan(
    val write: Boolean,
    val runWorkbook: Boolean,
)

/** The [PublishPlan] for the widget kinds in [placed]. */
fun publishPlan(placed: Set<WidgetKind>): PublishPlan =
    PublishPlan(
        write = placed.any { it in SNAPSHOT_KINDS },
        runWorkbook = WidgetKind.WORKBOOK in placed || WidgetKind.SHORTCUTS in placed,
    )
