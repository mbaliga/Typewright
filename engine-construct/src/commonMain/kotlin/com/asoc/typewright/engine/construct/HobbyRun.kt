// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.construct

import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.withSmoothKept
import kotlin.math.roundToInt

/** What a Hobby curve through a run of nodes did: the new contour and how many segments got new handles, or why it could not. */
sealed interface HobbyRunResult {
    data class Done(
        val contour: Contour,
        val segmentsChanged: Int,
        val runs: Int,
    ) : HobbyRunResult

    data class Refused(
        val reason: String,
    ) : HobbyRunResult
}

/**
 * The handles of every segment between neighbouring [nodes] (indices into [Contour.points], on-curve points of a
 * cubic contour) replaced by Hobby-spline controls, so the curve through each run of three or more neighbouring
 * selected nodes is the one [hobbySplineOpen] makes (or, when every node of the contour is selected,
 * [hobbySplineClosed]). The nodes do not move. A run's ends keep the handles on their outer sides, and a smooth
 * node whose outer side no longer lines up is kept smooth the way every other edit does ([withSmoothKept]).
 *
 * Refused, with the reason, for a quadratic contour, for a selection with no run of three neighbouring nodes
 * (two nodes would only make a straight side), and for two neighbouring selected nodes on the same spot.
 */
fun Contour.withHobbyHandles(nodes: Set<Int>): HobbyRunResult {
    if (format != CurveFormat.CUBIC) return HobbyRunResult.Refused("A Hobby curve needs a cubic outline; this contour is quadratic.")
    val count = points.size / 3
    val selected = nodes.filter { it in points.indices && it % 3 == 0 }.map { it / 3 }.toSet()
    val tooFew = "Select three or more nodes in a row: two nodes make only a straight side."
    if (selected.size < 3) return HobbyRunResult.Refused(tooFew)
    val runs = runsOf(selected, count)
    if (runs.none { it.size >= 3 || (it.size == count && count >= 3) }) return HobbyRunResult.Refused(tooFew)
    val updated = points.toMutableList()
    var changed = 0
    var used = 0
    for (run in runs) {
        if (run.size < 3) continue
        val knots = run.map { points[3 * it].point }
        if (knots.zipWithNext().any { (a, b) -> a == b } || (run.size == count && knots.first() == knots.last())) {
            return HobbyRunResult.Refused(
                "Two neighbouring selected nodes sit on the same spot, so there is no curve to draw between them.",
            )
        }
        used++
        val whole = run.size == count
        val controls: List<Pair<Point, Point>> =
            if (whole) {
                val shaped = hobbySplineClosed(knots)
                (0 until count).map { shaped.points[3 * it + 1].point to shaped.points[3 * it + 2].point }
            } else {
                hobbySplineOpen(knots).map { it.control1.rounded() to it.control2.rounded() }
            }
        val segmentCount = if (whole) count else run.size - 1
        for (k in 0 until segmentCount) {
            val start = run[k]
            val (c1, c2) = controls[k]
            if (updated[3 * start + 1].point != c1 || updated[3 * start + 2].point != c2) changed++
            updated[3 * start + 1] = updated[3 * start + 1].copy(point = c1)
            updated[3 * start + 2] = updated[3 * start + 2].copy(point = c2)
        }
    }
    if (changed == 0) return HobbyRunResult.Refused("Those nodes already have Hobby handles.")
    val touched = updated.indices.filter { !updated[it].onCurve && updated[it] != points[it] }.toSet()
    val result = copy(points = updated).withSmoothKept(this, touched)
    return HobbyRunResult.Done(result, changed, used)
}

/** The maximal runs of neighbouring numbers in [selected] around a ring of [count], each in order; the whole ring is one run starting at its lowest number. */
private fun runsOf(
    selected: Set<Int>,
    count: Int,
): List<List<Int>> {
    if (selected.size == count) return listOf((0 until count).toList())
    val starts = selected.filter { (it - 1 + count) % count !in selected }
    return starts.map { start ->
        val run = mutableListOf(start)
        var next = (start + 1) % count
        while (next in selected) {
            run += next
            next = (next + 1) % count
        }
        run
    }
}

private fun com.asoc.typewright.core.geometry.Vec2.rounded(): Point = Point(x.roundToInt(), y.roundToInt())
