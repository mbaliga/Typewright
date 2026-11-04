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
            assertTrue(report.after.onCurveEquivalent <= report.before.onCurveEquivalent, "${raw.name} grew")
            val perimeter = glyph.contours.sumOf { c -> c.segments().sumOf { s -> (s.end - s.start).length() } }
            worstArea = maxOf(worstArea, abs(report.areaChange) / perimeter)
            assertTrue(abs(report.areaChange) <= report.toleranceUnits * perimeter, "${raw.name}: area moved ${report.areaChange}")
            val before = checkNotNull(glyph.outlineBounds())
            val after = checkNotNull(glyph.copy(contours = report.contours).outlineBounds())
            val pairs = listOf(before.xMin to after.xMin, before.xMax to after.xMax, before.yMin to after.yMin, before.yMax to after.yMax)
            for ((a, b) in pairs) {
                assertTrue(abs(a - b) <= report.toleranceUnits, "${raw.name}: bounds moved $a -> $b")
            }
        }
        println(
            "TidyHyleDeco: $checked glyphs, worst shift $worstShift units, worst area/perimeter $worstArea, $removedTotal nodes removed",
        )
        assertTrue(checked > 20, "the font has glyphs to check")
        assertTrue(removedTotal > 0, "Hyle Deco's polygons have collinear nodes to remove, so tidy must remove some")
    }
}
