// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.geometry

import kotlin.math.sqrt

/**
 * What tidying some contours of a glyph would do, worked out on a copy before anything is changed
 * (the "n → m" a person sees before they apply): the tidied [contours] of the whole glyph, the nodes
 * that would go ([removed], in the old outline's coordinates), the point counts before and after over
 * the contours it looked at, and how far the shape moved, measured not assumed: [maxShift] is the
 * largest distance between the old and new outlines (sampled, both directions) and [areaChange] the
 * change in signed area, both in font units. [toleranceUnits] is the bound the tidy was asked to keep,
 * which is our heuristic (a fixed one-and-a-half units, not a measured distribution).
 */
data class TidyReport(
    val contours: List<Contour>,
    val removed: List<Point>,
    val before: ContourCount,
    val after: ContourCount,
    val maxShift: Double,
    val areaChange: Double,
    val toleranceUnits: Double,
) {
    /** True when tidying would change nothing. */
    val isNoop: Boolean get() = removed.isEmpty()
}

/**
 * The tidy of the cubic contours [which] of this glyph (all of them when [which] is empty), with the
 * measurements in [TidyReport]. Quadratic contours and contours not named are left as they are.
 * Each contour is tidied by [tidyKeeping], so the whole result, not just each removal, stays within
 * [toleranceUnits] of the original (a contour that cannot be tidied inside the bound is left alone).
 */
fun Glyph.tidyReport(
    which: Set<Int> = emptySet(),
    toleranceUnits: Double = DEFAULT_SIMPLIFY_TOLERANCE_UNITS,
): TidyReport {
    val chosen = if (which.isEmpty()) contours.indices.toSet() else which
    val tidiedAll =
        contours.mapIndexed { i, c ->
            if (i in chosen && c.format == CurveFormat.CUBIC) tidyKeeping(c, toleranceUnits) else c
        }
    var before = ContourCount(0, 0)
    var after = ContourCount(0, 0)
    var shift = 0.0
    var area = 0.0
    val removed = mutableListOf<Point>()
    for (i in contours.indices) {
        if (i !in chosen || contours[i].format != CurveFormat.CUBIC) continue
        val old = contours[i]
        val new = tidiedAll[i]
        before = before + old.count()
        after = after + new.count()
        shift = maxOf(shift, outlineDistance(old, new))
        area += new.signedArea() - old.signedArea()
        val kept =
            new.points
                .filter { it.onCurve }
                .map { it.point }
                .toSet()
        removed += old.points.filter { it.onCurve && it.point !in kept }.map { it.point }
    }
    return TidyReport(tidiedAll, removed, before, after, shift, area, toleranceUnits)
}

/**
 * [Contour.tidied] with the bound enforced on the whole result, not just on each removal: the tidy removes
 * nodes one at a time, each checked against the shape as it stood after the last removal, so a long run of
 * small removals can add up to more than the bound. The result is measured against the original with
 * [outlineDistance]; if it drifted past [toleranceUnits] the tidy is retried with a stricter per-removal
 * tolerance (three quarters of the last), and if no attempt keeps within the bound the contour is left as it was.
 */
internal fun tidyKeeping(
    contour: Contour,
    toleranceUnits: Double,
): Contour {
    var attempt = toleranceUnits
    repeat(RETRIES) {
        val tidied = contour.tidied(attempt).contour
        if (tidied == contour || outlineDistance(contour, tidied) <= toleranceUnits) return tidied
        attempt *= RETRY_FACTOR
    }
    return contour
}

private const val RETRIES = 8
private const val RETRY_FACTOR = 0.75

private operator fun ContourCount.plus(other: ContourCount): ContourCount =
    ContourCount(onCurveEquivalent + other.onCurveEquivalent, offCurve + other.offCurve)

/**
 * The largest distance from any sample of either outline to the other, in font units: a symmetric
 * Hausdorff distance over [SAMPLES_PER_SEGMENT] samples per segment, so it reads the shape's drift, not the nodes'.
 */
fun outlineDistance(
    a: Contour,
    b: Contour,
): Double = maxOf(oneWay(a, b), oneWay(b, a))

private const val SAMPLES_PER_SEGMENT = 24
private const val CELL_UNITS = 16.0

/** The edges of a polyline, bucketed in a grid so the nearest one to a point is found without scanning them all. */
private class EdgeGrid(
    edges: List<Pair<Vec2, Vec2>>,
) {
    private val cells = HashMap<Long, MutableList<Pair<Vec2, Vec2>>>()

    init {
        for (edge in edges) {
            val x0 = cell(minOf(edge.first.x, edge.second.x))
            val x1 = cell(maxOf(edge.first.x, edge.second.x))
            val y0 = cell(minOf(edge.first.y, edge.second.y))
            val y1 = cell(maxOf(edge.first.y, edge.second.y))
            for (cx in x0..x1) for (cy in y0..y1) cells.getOrPut(key(cx, cy)) { mutableListOf() }.add(edge)
        }
    }

    private fun cell(v: Double): Long = kotlin.math.floor(v / CELL_UNITS).toLong()

    private fun key(
        cx: Long,
        cy: Long,
    ): Long = cx * GRID_STRIDE + cy

    /** The exact distance from [p] to the nearest edge, searching outward ring by ring until nothing nearer can exist. */
    fun nearest(p: Vec2): Double {
        val cx = cell(p.x)
        val cy = cell(p.y)
        var best = Double.MAX_VALUE
        var ring = 0L
        while (true) {
            if (best <= (ring - 1) * CELL_UNITS || ring > MAX_RINGS) return best
            for (dx in -ring..ring) {
                for (dy in -ring..ring) {
                    if (maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) != ring) continue
                    cells[key(cx + dx, cy + dy)]?.forEach { (u, v) -> best = minOf(best, distanceToEdge(p, u, v)) }
                }
            }
            ring++
        }
    }

    private companion object {
        const val GRID_STRIDE = 1_000_003L
        const val MAX_RINGS = 400L
    }
}

private fun oneWay(
    from: Contour,
    to: Contour,
): Double {
    val target = to.segments().flatMap { s -> (0..SAMPLES_PER_SEGMENT).map { s.pointAt(it.toDouble() / SAMPLES_PER_SEGMENT) } }
    val grid = EdgeGrid(target.zipWithNext() + (target.last() to target.first()))
    var worst = 0.0
    for (s in from.segments()) {
        for (k in 0..SAMPLES_PER_SEGMENT) {
            worst = maxOf(worst, grid.nearest(s.pointAt(k.toDouble() / SAMPLES_PER_SEGMENT)))
        }
    }
    return worst
}

private fun distanceToEdge(
    p: Vec2,
    u: Vec2,
    v: Vec2,
): Double {
    val d = v - u
    val len2 = d.dot(d)
    val t = if (len2 < 1e-12) 0.0 else ((p - u).dot(d) / len2).coerceIn(0.0, 1.0)
    val e = p - (u + d * t)
    return sqrt(e.x * e.x + e.y * e.y)
}
