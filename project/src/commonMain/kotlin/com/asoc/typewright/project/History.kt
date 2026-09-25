// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import kotlin.time.ComparableTimeMark
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/** What the UI shows for undo/redo (docs/PROJECT_MODEL.md §6): every entry's label, oldest at index 0. */
data class HistoryState(
    val undoLabels: List<String>,
    val redoLabels: List<String>,
) {
    /** Whether [ProjectSession.undo] would do anything. */
    val canUndo: Boolean get() = undoLabels.isNotEmpty()

    /** Whether [ProjectSession.redo] would do anything. */
    val canRedo: Boolean get() = redoLabels.isNotEmpty()

    companion object {
        /** A fresh project's history: nothing to undo or redo. */
        val EMPTY: HistoryState = HistoryState(emptyList(), emptyList())
    }
}

/**
 * The undo/redo stack (docs/PROJECT_MODEL.md §6): snapshots of [FontState], not inverse commands.
 * Every entry is `(label, before, after, coalesceKey)`; undo sets the state to `before`, redo to
 * `after`. Not kept across reopen: a fresh [HistoryStack] is what a session starts with.
 *
 * **Coalescing.** [push] with a non-null [Entry] coalesce key merges into the current top of the
 * undo stack when that entry shares the same key and was last touched within [coalesceWindowMs]
 * (measured on [clock]); merging keeps the original `before` and just replaces `after`. Any push,
 * merged or not, clears the redo stack (the edit diverges from whatever redo would have replayed).
 *
 * **Size.** At most [limit] entries; the oldest is dropped once that is exceeded.
 */
internal class HistoryStack(
    private val limit: Int,
    private val coalesceWindowMs: Long,
    private val clock: TimeSource.WithComparableMarks = TimeSource.Monotonic,
) {
    class Entry(
        val label: String,
        val before: FontState,
        var after: FontState,
        val coalesceKey: Any?,
        var lastTouched: ComparableTimeMark,
    )

    private val undoStack = ArrayDeque<Entry>()
    private val redoStack = ArrayDeque<Entry>()

    /** Every label, oldest first, undo then redo. */
    fun snapshot(): HistoryState = HistoryState(undoStack.map { it.label }, redoStack.map { it.label }.asReversed())

    /**
     * Records that [label] changed the font from [before] to [after], with coalescing key
     * [coalesceKey]. A no-op ([before] == [after]) records nothing. Clears the redo stack.
     */
    fun push(
        label: String,
        before: FontState,
        after: FontState,
        coalesceKey: Any?,
    ) {
        if (before == after) return
        redoStack.clear()
        val now = clock.markNow()
        val top = undoStack.lastOrNull()
        if (coalesceKey != null && top != null && top.coalesceKey == coalesceKey &&
            now - top.lastTouched <= coalesceWindowMs.milliseconds
        ) {
            top.after = after
            top.lastTouched = now
            return
        }
        undoStack.addLast(Entry(label, before, after, coalesceKey, now))
        while (undoStack.size > limit) undoStack.removeFirst()
    }

    /** Always pushes a new entry, never coalescing (a [Gesture]'s single commit, or a law-1 command). */
    fun pushAlways(
        label: String,
        before: FontState,
        after: FontState,
    ) = push(label, before, after, coalesceKey = null)

    /** [after] of the entry undo restores (the new current [FontState]), or null if there is nothing to undo. */
    fun undo(): FontState? {
        val entry = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(entry)
        return entry.before
    }

    /** [after] of the entry redo restores, or null if there is nothing to redo. */
    fun redo(): FontState? {
        val entry = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(entry)
        return entry.after
    }
}
