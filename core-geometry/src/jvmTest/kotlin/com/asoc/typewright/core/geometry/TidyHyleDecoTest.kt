// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.geometry

import com.asoc.typewright.core.font.sfnt.readSfntFont
import com.asoc.typewright.core.font.sfnt.toUfoProject
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tidy on the real Hyle Deco glyphs (`fonts/HyleDeco-Regular.ttf`): for every glyph the font holds, the tidy
 * keeps the outline within the stated tolerance (measured as [outlineDistance], not assumed), the bounds
 * within the same tolerance, and never adds a node. The tolerance itself is our heuristic
 * ([DEFAULT_SIMPLIFY_TOLERANCE_UNITS]), not a measured distribution.
 */
class TidyHyleDecoTest {
    private val glyphs by lazy { readSfntFont(File("../fonts/HyleDeco-Regular.ttf").readBytes()).toUfoProject().glyphs }

    private fun cubic(glyph: Glyph): Glyph =
        glyph.copy(contours = enforceContourDirections(glyph.contours.map { it.degreeElevatedToCubic() }))

    @Test
    fun everyGlyphStaysWithinTheToleranceInShapeBoundsAndArea() {
        var checked = 0
        var worstShift = 0.0
        var worstArea = 0.0
        var worstIndependent = 0.0
        var removedTotal = 0
        for (raw in glyphs) {
            if (raw.contours.isEmpty()) continue
            val glyph = cubic(raw)
            val report = glyph.tidyReport()
            checked++
            worstShift = maxOf(worstShift, report.maxShift)
            removedTotal += report.removed.size
            assertTrue(
                report.maxShift <= report.toleranceUnits,
                "${raw.name}: moved ${report.maxShift} units, tolerance ${report.toleranceUnits}",
            )
            // The same bound by a measure that shares no code with tidyKeeping's: brute-force distances between flattened outlines.
            val independent = glyph.contours.indices.maxOf { independentShift(glyph.contours[it], report.contours[it]) }
            worstIndependent = maxOf(worstIndependent, independent)
            assertTrue(
                independent <= report.toleranceUnits + SAMPLING_SLACK,
                "${raw.name}: an independent measure finds $independent units of drift, tolerance ${report.toleranceUnits}",
            )
            assertTrue(report.after.onCurveEquivalent <= report.before.onCurveEquivalent, "${raw.name} grew")
            val perimeter = glyph.contours.sumOf { c -> c.segments().sumOf { s -> (s.end - s.start).length() } }
            worstArea = maxOf(worstArea, abs(report.areaChange) / perimeter)
            assertTrue(abs(report.areaChange) <= report.toleranceUnits * perimeter, "${raw.name}: area moved ${report.areaChange}")
            // Two outlines no more than d apart enclose areas that differ by at most d times the outline's length: the
            // distance found above, not a tolerance, must account for the area.
            assertTrue(
                abs(report.areaChange) <= (independent + SAMPLING_SLACK) * perimeter,
                "${raw.name}: area moved ${report.areaChange}, more than $independent units of drift allows",
            )
            val before = checkNotNull(glyph.outlineBounds())
            val after = checkNotNull(glyph.copy(contours = report.contours).outlineBounds())
            val pairs = listOf(before.xMin to after.xMin, before.xMax to after.xMax, before.yMin to after.yMin, before.yMax to after.yMax)
            for ((a, b) in pairs) {
                assertTrue(abs(a - b) <= report.toleranceUnits, "${raw.name}: bounds moved $a -> $b")
            }
        }
        println(
            "TidyHyleDeco: $checked glyphs, worst shift $worstShift units (independent $worstIndependent), " +
                "worst area/perimeter $worstArea, $removedTotal nodes removed",
        )
        assertTrue(checked > 20, "the font has glyphs to check")
        assertTrue(removedTotal > 0, "Hyle Deco's polygons have collinear nodes to remove, so tidy must remove some")
    }

    /** Both outlines flattened (a few samples per segment of the old, more of the new) and compared edge by edge, the slow obvious way. */
    private fun independentShift(
        before: Contour,
        after: Contour,
    ): Double {
        val a = flatten(before, 8)
        val b = flatten(after, 24)
        return maxOf(farthest(a, b), farthest(b, a))
    }

    private fun flatten(
        c: Contour,
        steps: Int,
    ): List<Vec2> = c.segments().flatMap { s -> (0 until steps).map { s.pointAt(it.toDouble() / steps) } }

    /** The largest distance from a vertex of [from] to the closed polyline [to]. */
    private fun farthest(
        from: List<Vec2>,
        to: List<Vec2>,
    ): Double {
        var worst = 0.0
        for (p in from) {
            var nearest = Double.MAX_VALUE
            for (i in to.indices) {
                val u = to[i]
                val v = to[(i + 1) % to.size]
                val d = v - u
                val len2 = d.dot(d)
                val t = if (len2 == 0.0) 0.0 else ((p - u).dot(d) / len2).coerceIn(0.0, 1.0)
                val e = p - (u + d * t)
                nearest = minOf(nearest, kotlin.math.sqrt(e.dot(e)))
            }
            worst = maxOf(worst, nearest)
        }
        return worst
    }

    private companion object {
        /** Flattening a curve into 24 straight pieces leaves it a fraction of a unit from the curve at most. */
        const val SAMPLING_SLACK = 0.1
    }
}
