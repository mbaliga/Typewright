// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.sheet

import com.asoc.typewright.core.geometry.Vec2
import kotlin.math.abs

/**
 * Tells a tap with [fingers] fingers (two is undo, three is redo; INTERACTION_V1 §4.1) from the start
 * of a pinch, a pan or a tap with another number of fingers. It is fed the state of the pointers on the
 * screen at each event, and says so, on the event that lifts the last one, when what happened was
 * [fingers] fingers down together and up again quickly, none having travelled and the spread between
 * them not having changed by more than [slopDp]. More fingers than that, a long press, a drag and a
 * pinch all make it a different gesture, so none of them is ever read as a tap.
 */
class FingerTap(
    private val fingers: Int,
    private val maxMs: Long = DEFAULT_MAX_MS,
    private val slopDp: Double = DEFAULT_SLOP_DP,
) {
    init {
        require(fingers >= 2) { "a finger tap is made with two fingers or more, not $fingers" }
    }

    private var startMs = 0L
    private var startCentroid = Vec2(0.0, 0.0)
    private var startSpan = 0.0
    private var active = false
    private var spoiled = false

    /**
     * The pointers now: how many are down ([count]), where the middle of them is ([centroid], dp) and the
     * widest gap between any two ([span], dp), at [timeMs]. True exactly when this event is the lift of
     * the last finger of a tap.
     */
    fun onPointers(
        count: Int,
        centroid: Vec2,
        span: Double,
        timeMs: Long,
    ): Boolean {
        if (count == 0) {
            val tapped = active && !spoiled && timeMs - startMs <= maxMs
            reset()
            return tapped
        }
        if (count >= fingers && !active) {
            active = true
            spoiled = false
            startMs = timeMs
            startCentroid = centroid
            startSpan = span
        }
        if (active) {
            if (count > fingers || timeMs - startMs > maxMs) spoiled = true
            if (count == fingers && ((centroid - startCentroid).length() > slopDp || abs(span - startSpan) > slopDp)) spoiled = true
        }
        return false
    }

    /** Forgets what it has seen, for a gesture that was cancelled from outside. */
    fun reset() {
        active = false
        spoiled = false
    }

    companion object {
        const val DEFAULT_MAX_MS: Long = 280
        const val DEFAULT_SLOP_DP: Double = 12.0
    }
}
