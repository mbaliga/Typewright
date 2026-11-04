// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.android.widget

import com.asoc.typewright.project.FontState
import com.asoc.typewright.project.GlyphRef
import com.asoc.typewright.project.LockState
import com.asoc.typewright.project.ProjectState
import com.asoc.typewright.project.SaveStatus
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Properties

/**
 * What the widgets know about the open project, as Typewright last published it
 * (SCREENS_V1 §9.1 "Data"). [project] is null when no project is open. [writer] names the
 * process that wrote it, so a save that was still pending can be told apart from one in progress.
 */
data class ProjectWidgetSnapshot(
    val writtenAtMillis: Long,
    val writer: String,
    val project: OpenProject?,
)

/**
 * The open project's widget data: counts, plus its name for the widgets to show or withhold
 * ([ProjectNames]). Never a folder, file, outline or anything else the user wrote (§9.1 "Privacy").
 */
data class OpenProject(
    val name: String,
    val glyphs: Int,
    val approved: Int,
    val unlocked: Int,
    val save: SaveState,
    val savedAtMillis: Long?,
    val workbook: WorkbookOutcome,
)

/** What the snapshot knows of the open project's Workbook (W4, and W6's second line). */
sealed interface WorkbookOutcome {
    /** The gates ran. */
    data class Ready(
        val summary: WorkbookSummary,
    ) : WorkbookOutcome

    /** The gates couldn't run on this project: W4's error state. */
    data object Failed : WorkbookOutcome

    /** Not worked out, because no Workbook or Shortcuts widget was placed when this was published. */
    data object NotRun : WorkbookOutcome
}

/** [SaveStatus] reduced to what W2 shows. */
enum class SaveState {
    SAVED,
    SAVING,
    FAILED,
    CONFLICT,
    READ_ONLY,
    TAB_ONLY,
}

/** The result of reading the snapshot file: nothing written yet, unreadable, or a snapshot. */
sealed interface SnapshotRead {
    /** No snapshot: none written yet, or removed while no widget used it. */
    data object Absent : SnapshotRead

    /** A file exists but can't be read as a snapshot. */
    data object Unreadable : SnapshotRead

    data class Present(
        val snapshot: ProjectWidgetSnapshot,
    ) : SnapshotRead
}

/** Builds snapshots from a live session and reads and writes them as a small properties file. */
object ProjectWidgetSnapshots {
    private const val FORMAT = 1

    /** [state] and [save] as a snapshot written at [nowMillis] by [writer]. */
    fun of(
        state: ProjectState,
        save: SaveStatus,
        workbook: WorkbookOutcome,
        nowMillis: Long,
        writer: String,
    ): ProjectWidgetSnapshot = ProjectWidgetSnapshot(nowMillis, writer, openProject(state.meta.manifest.name, state.font, save, workbook))

    /**
     * The widget data of the project called [name]: its glyphs in every master, those whose lock
     * is `locked` (approved) and those whose lock is `unlocked` (approved, then opened for editing).
     */
    fun openProject(
        name: String,
        font: FontState,
        save: SaveStatus,
        workbook: WorkbookOutcome,
    ): OpenProject {
        val refs = font.masters.flatMap { master -> master.ufo.glyphs.map { GlyphRef(master.id, it.name) } }.toSet()
        return OpenProject(
            name = name,
            glyphs = refs.size,
            approved = refs.count { font.locks[it]?.state == LockState.LOCKED },
            unlocked = refs.count { font.locks[it]?.state == LockState.UNLOCKED },
            save = saveStateOf(save),
            savedAtMillis = (save as? SaveStatus.Saved)?.at?.toEpochMilliseconds(),
            workbook = workbook,
        )
    }

    /** A snapshot saying no project is open. */
    fun closed(
        nowMillis: Long,
        writer: String,
    ): ProjectWidgetSnapshot = ProjectWidgetSnapshot(nowMillis, writer, project = null)

    /** [status] as W2's states; a clean store reads as saved. */
    fun saveStateOf(status: SaveStatus): SaveState =
        when (status) {
            is SaveStatus.Clean, is SaveStatus.Saved -> SaveState.SAVED
            is SaveStatus.Pending, is SaveStatus.Saving -> SaveState.SAVING
            is SaveStatus.Failed -> SaveState.FAILED
            is SaveStatus.Conflict -> SaveState.CONFLICT
            is SaveStatus.ReadOnly -> SaveState.READ_ONLY
            is SaveStatus.TabOnly -> SaveState.TAB_ONLY
        }

    /** [snapshot] as properties-file bytes. */
    fun encode(snapshot: ProjectWidgetSnapshot): ByteArray {
        val props = Properties()
        props["format"] = FORMAT.toString()
        props["written_at"] = snapshot.writtenAtMillis.toString()
        props["writer"] = snapshot.writer
        val project = snapshot.project
        props["open"] = (project != null).toString()
        if (project != null) {
            props["name"] = project.name
            props["glyphs"] = project.glyphs.toString()
            props["approved"] = project.approved.toString()
            props["unlocked"] = project.unlocked.toString()
            props["save"] = project.save.name
            project.savedAtMillis?.let { props["saved_at"] = it.toString() }
            props["workbook"] =
                when (project.workbook) {
                    WorkbookOutcome.Failed -> "error"
                    WorkbookOutcome.NotRun -> "not_run"
                    is WorkbookOutcome.Ready -> "ok"
                }
            val workbook = (project.workbook as? WorkbookOutcome.Ready)?.summary
            if (workbook != null) {
                props["wb.states"] = workbook.states.joinToString(separator = "") { it.code }
                props["wb.current"] = workbook.currentIndex.toString()
                props["wb.title"] = workbook.currentTitle
                props["wb.gate"] = workbook.currentGateSummary
                props["wb.checks"] = workbook.currentChecks.entries.joinToString(separator = ",") { "${it.key.name}:${it.value}" }
                props["wb.scaffold"] = workbook.currentScaffold.toString()
            }
        }
        val out = ByteArrayOutputStream()
        props.store(out, null)
        return out.toByteArray()
    }

    /** [bytes] read back, or [SnapshotRead.Unreadable] for anything [encode] didn't write. */
    fun decode(bytes: ByteArray?): SnapshotRead {
        if (bytes == null) return SnapshotRead.Absent
        return runCatching { SnapshotRead.Present(decodeOrThrow(bytes)) }.getOrElse { SnapshotRead.Unreadable }
    }

    private fun decodeOrThrow(bytes: ByteArray): ProjectWidgetSnapshot {
        val props = Properties().apply { load(ByteArrayInputStream(bytes)) }

        fun text(key: String): String = requireNotNull(props.getProperty(key)) { "missing $key" }

        fun count(key: String): Int = text(key).toInt().also { require(it >= 0) { "negative $key" } }
        require(text("format").toInt() == FORMAT) { "unknown format" }
        val writtenAt = text("written_at").toLong()
        val writer = text("writer")
        if (!text("open").toBooleanStrict()) return ProjectWidgetSnapshot(writtenAt, writer, project = null)
        val workbook =
            when (text("workbook")) {
                "error" -> {
                    WorkbookOutcome.Failed
                }

                "not_run" -> {
                    WorkbookOutcome.NotRun
                }

                "ok" -> {
                    val states = text("wb.states").map { code -> TaskState.entries.single { it.code == code.toString() } }
                    val checks =
                        text("wb.checks")
                            .split(',')
                            .filter { it.isNotEmpty() }
                            .associate { entry ->
                                val (name, value) = entry.split(':')
                                CheckState.valueOf(name) to value.toInt()
                            }
                    WorkbookOutcome.Ready(
                        WorkbookSummary(
                            states = states,
                            currentIndex = text("wb.current").toInt(),
                            currentTitle = text("wb.title"),
                            currentGateSummary = text("wb.gate"),
                            currentChecks = checks,
                            currentScaffold = text("wb.scaffold").toBooleanStrict(),
                        ),
                    )
                }

                else -> {
                    error("unknown workbook state")
                }
            }
        val project =
            OpenProject(
                name = text("name"),
                glyphs = count("glyphs"),
                approved = count("approved"),
                unlocked = count("unlocked"),
                save = SaveState.valueOf(text("save")),
                savedAtMillis = props.getProperty("saved_at")?.toLong(),
                workbook = workbook,
            )
        require(project.approved + project.unlocked <= project.glyphs) { "more locks than glyphs" }
        return ProjectWidgetSnapshot(writtenAt, writer, project)
    }
}
