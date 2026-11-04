// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.capture

import com.asoc.typewright.engine.trace.capture.FoundMark
import com.asoc.typewright.engine.trace.capture.ImageCorner
import com.asoc.typewright.engine.trace.capture.MarkSearch

/**
 * Which of the four corner marks the camera holds a lock on, from the stream of preview frames. A mark locks when it has been
 * found in [framesToLock] frames in a row and lets go after [framesToRelease] frames without it, so a corner does not flicker
 * between locked and open as the hand shakes. The shutter arms when all four are locked.
 *
 * Time only enters through [dueAt]: the preview is analysed at most every [intervalMillis] (the detector is not free), whatever
 * rate the camera delivers frames at. Plain state with no Compose, so the behaviour is tested without a camera.
 */
class CameraLockTracker(
    private val framesToLock: Int = 2,
    private val framesToRelease: Int = 3,
    val intervalMillis: Long = 250L,
) {
    private val seen = IntArray(CORNERS)
    private val missed = IntArray(CORNERS)
    private val locked = BooleanArray(CORNERS)
    private val positions = arrayOfNulls<FoundMark>(CORNERS)
    private var lastAnalysed: Long? = null

    /** True when a frame arriving at [nowMillis] should be analysed, and records that it will be. */
    fun dueAt(nowMillis: Long): Boolean {
        val last = lastAnalysed
        if (last != null && nowMillis - last < intervalMillis) return false
        lastAnalysed = nowMillis
        return true
    }

    /** Takes one frame's search. */
    fun offer(search: MarkSearch) {
        val found = search.marks.associateBy { it.corner }
        for (corner in ImageCorner.entries) {
            val i = corner.ordinal
            val mark = found[corner]
            if (mark != null) {
                seen[i]++
                missed[i] = 0
                positions[i] = mark
                if (seen[i] >= framesToLock) locked[i] = true
            } else {
                seen[i] = 0
                missed[i]++
                if (missed[i] >= framesToRelease) {
                    locked[i] = false
                    positions[i] = null
                }
            }
        }
    }

    /** Forgets everything, for a camera that was just reopened. */
    fun reset() {
        seen.fill(0)
        missed.fill(0)
        locked.fill(false)
        positions.fill(null)
        lastAnalysed = null
    }

    fun isLocked(corner: ImageCorner): Boolean = locked[corner.ordinal]

    /** Where the mark for [corner] was last seen, if it is locked. */
    fun markAt(corner: ImageCorner): FoundMark? = if (locked[corner.ordinal]) positions[corner.ordinal] else null

    val lockedCount: Int get() = locked.count { it }

    /** True when all four corners are locked: the shutter arms. */
    val armed: Boolean get() = lockedCount == CORNERS

    /** The mono line under the title: how many corners, and what to do next. [manual] is the long-press override. */
    fun status(manual: Boolean): String =
        when {
            manual -> "Manual: the limits are off · $lockedCount of 4 corners"
            armed -> "4 of 4 corners · hold steady"
            lockedCount == 0 -> "0 of 4 corners · find the black squares"
            else -> "$lockedCount of 4 corners · ${missingHint()}"
        }

    private fun missingHint(): String {
        val open = ImageCorner.entries.filter { !locked[it.ordinal] }
        return if (open.size == 1) "the ${open.first().label} one is missing" else "hold steady"
    }

    private companion object {
        const val CORNERS = 4
    }
}
