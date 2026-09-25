// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

/** [HistoryStack]: coalescing window, gesture-style single pushes, the 500-entry limit and undo/redo. Runs on the JVM and Wasm. */
class HistoryStackTest {
    private val a = Fixtures.fontState()
    private val b = a.copy(locks = mapOf(GlyphRef("regular", "A") to lockOf(a, "A")))
    private val c = a.copy(locks = mapOf(GlyphRef("regular", "B") to lockOf(a, "B")))

    private fun lockOf(
        font: FontState,
        name: String,
    ) = GlyphLock(LockState.LOCKED, ApprovalOrigin.DRAW, Fixtures.EPOCH, font.glyph(GlyphRef("regular", name))!!, emptyList())

    @Test
    fun startsWithNothingToUndoOrRedo() {
        val stack = HistoryStack(500, 1_000)
        assertEquals(HistoryState.EMPTY, stack.snapshot())
    }

    @Test
    fun pushingANoOpRecordsNothing() {
        val stack = HistoryStack(500, 1_000)
        stack.push("No-op", a, a, coalesceKey = null)
        assertFalse(stack.snapshot().canUndo)
    }

    @Test
    fun undoRestoresBeforeAndRedoRestoresAfter() {
        val stack = HistoryStack(500, 1_000)
        stack.push("Move", a, b, coalesceKey = null)
        assertEquals(a, stack.undo())
        assertEquals(b, stack.redo())
    }

    @Test
    fun coalescesWithinTheWindowUnderTheSameKey() {
        val clock = TestTimeSource()
        val stack = HistoryStack(500, coalesceWindowMs = 1_000, clock = clock)
        stack.push("Move", a, b, coalesceKey = "k")
        clock += 500.milliseconds
        stack.push("Move", b, c, coalesceKey = "k")

        assertEquals(listOf("Move"), stack.snapshot().undoLabels)
        assertEquals(a, stack.undo())
    }

    @Test
    fun doesNotCoalesceOutsideTheWindow() {
        val clock = TestTimeSource()
        val stack = HistoryStack(500, coalesceWindowMs = 1_000, clock = clock)
        stack.push("Move", a, b, coalesceKey = "k")
        clock += 1_500.milliseconds
        stack.push("Move", b, c, coalesceKey = "k")

        assertEquals(2, stack.snapshot().undoLabels.size)
    }

    @Test
    fun doesNotCoalesceDifferentKeys() {
        val stack = HistoryStack(500, coalesceWindowMs = 1_000)
        stack.push("Move", a, b, coalesceKey = "k1")
        stack.push("Move", b, c, coalesceKey = "k2")
        assertEquals(2, stack.snapshot().undoLabels.size)
    }

    @Test
    fun aNullCoalesceKeyNeverMerges() {
        val stack = HistoryStack(500, coalesceWindowMs = 1_000)
        stack.push("Approve", a, b, coalesceKey = null)
        stack.push("Approve", b, c, coalesceKey = null)
        assertEquals(2, stack.snapshot().undoLabels.size)
    }

    @Test
    fun pushClearsTheRedoStack() {
        val stack = HistoryStack(500, 1_000)
        stack.push("Move", a, b, coalesceKey = null)
        stack.undo()
        assertTrue(stack.snapshot().canRedo)
        stack.push("Other", a, c, coalesceKey = null)
        assertFalse(stack.snapshot().canRedo)
    }

    @Test
    fun dropsTheOldestEntryPastTheLimit() {
        val stack = HistoryStack(3, 1_000)
        var state = a
        repeat(5) { index ->
            val next =
                state
                    .copy(locks = mapOf(GlyphRef("regular", "A") to lockOf(a, "A").copy(approvedAt = Fixtures.EPOCH)))
                    .let { it.copy(locks = it.locks + (GlyphRef("regular", "n$index") to lockOf(a, "A"))) }
            stack.push("Step $index", state, next, coalesceKey = null)
            state = next
        }
        assertEquals(3, stack.snapshot().undoLabels.size)
        assertEquals(listOf("Step 2", "Step 3", "Step 4"), stack.snapshot().undoLabels)
    }

    @Test
    fun undoAndRedoReturnNullWhenThereIsNothingToDo() {
        val stack = HistoryStack(500, 1_000)
        assertEquals(null, stack.undo())
        assertEquals(null, stack.redo())
    }
}
