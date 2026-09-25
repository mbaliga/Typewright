// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.core.geometry

import kotlin.test.Test
import kotlin.test.assertEquals

private fun on(
    x: Int,
    y: Int,
) = ContourPoint(Point(x, y), onCurve = true)

private fun off(
    x: Int,
    y: Int,
) = ContourPoint(Point(x, y), onCurve = false)

class NodeEconomyTest {
    // The task's own hand-traced fixture: Poppins-Regular's 'o' outer contour, 16 points, the
    // flag pattern [off, off, off, on] repeated four times. Each run of 3 consecutive off-curve
    // points carries (run length - 1) = 2 implied on-curve points, so 4 explicit on-curve points
    // + 4 * 2 = 8 implied = 12 on-curve equivalents; the 12 off-curve points are unchanged. This
    // must match data/scripts/build_node_economy_corpus.py's _count_simple_contours exactly.
    @Test
    fun quadraticRunsOfThreeGiveTwoImpliedPointsEach() {
        val points =
            (0 until 4).flatMap { i ->
                val base = i * 4
                listOf(off(base, 0), off(base + 1, 0), off(base + 2, 0), on(base + 3, 0))
            }
        val contour = Contour(points, CurveFormat.QUADRATIC)
        val count = contour.count()
        assertEquals(12, count.onCurveEquivalent)
        assertEquals(12, count.offCurve)
        assertEquals(12, contour.onCurveEquivalentCount())
        assertEquals(12, contour.offCurveCount())
    }

    // A fully off-curve contour is the general rule's special case: the whole contour is one run,
    // so every cyclically-adjacent pair (including the wrap-around pair) is off/off.
    @Test
    fun aFullyOffCurveContourImpliesOnePointPerCyclicPair() {
        val contour = Contour(listOf(off(2, 0), off(0, 2), off(-2, 0), off(0, -2)), CurveFormat.QUADRATIC)
        val count = contour.count()
        assertEquals(4, count.onCurveEquivalent)
        assertEquals(4, count.offCurve)
    }

    // A run longer than 3 (five consecutive off-curve points between one on-curve point and the
    // wrap back to it): (run length - 1) = 4 implied points.
    @Test
    fun aLongerRunImpliesRunLengthMinusOnePoints() {
        val contour =
            Contour(
                listOf(on(0, 0), off(1, 0), off(2, 0), off(3, 0), off(4, 0), off(5, 0)),
                CurveFormat.QUADRATIC,
            )
        val count = contour.count()
        assertEquals(5, count.onCurveEquivalent) // 1 explicit + 4 implied
        assertEquals(5, count.offCurve)
    }

    // An all-on-curve polygon (no off-curve points at all): no implied points, matching the
    // shipped Hyle Deco T and H fixtures in CLAUDE.md, whose on-curve counts equal their raw
    // point counts exactly because every point in those glyphs is a straight-line corner.
    @Test
    fun aPolygonHasNoImpliedPoints() {
        val contour = Contour(listOf(on(0, 0), on(1, 0), on(1, 1), on(0, 1)), CurveFormat.QUADRATIC)
        val count = contour.count()
        assertEquals(4, count.onCurveEquivalent)
        assertEquals(0, count.offCurve)
    }

    // A cubic contour's off-curve points never imply anything, even though its explicit
    // off-curve points are cyclically adjacent to each other exactly like a quadratic run of 2
    // would be: applying the quadratic rule here would wrongly add implied points.
    @Test
    fun aCubicContourHasNoImpliedPoints() {
        val contour =
            Contour(
                listOf(on(0, 0), off(1, 1), off(2, 1), on(3, 0), off(4, 1), off(5, 1), on(6, 0), off(7, 1), off(8, 1)),
                CurveFormat.CUBIC,
            )
        val count = contour.count()
        assertEquals(3, count.onCurveEquivalent)
        assertEquals(6, count.offCurve)
        assertEquals(3, contour.onCurveEquivalentCount())
        assertEquals(6, contour.offCurveCount())
    }

    @Test
    fun glyphCountSumsAcrossContours() {
        // Two contours shaped like the fully-off-curve diamond above (4 on-curve-equivalent, 4
        // off-curve each), standing in for a two-contour glyph like a Latin 'o'.
        val outer = Contour(listOf(off(4, 0), off(0, 4), off(-4, 0), off(0, -4)), CurveFormat.QUADRATIC)
        val inner = Contour(listOf(off(2, 0), off(0, 2), off(-2, 0), off(0, -2)), CurveFormat.QUADRATIC)
        val glyph = Glyph(name = "o", advanceWidth = 500, contours = listOf(outer, inner))

        val count = glyph.count()
        assertEquals(8, count.onCurveEquivalent)
        assertEquals(8, count.offCurve)
        assertEquals(2, count.contourCount)
        assertEquals(8, glyph.onCurveEquivalentCount())
        assertEquals(8, glyph.offCurveCount())
    }

    @Test
    fun aGlyphWithNoContoursCountsAsZero() {
        val glyph = Glyph(name = "space", advanceWidth = 250, contours = emptyList())
        val count = glyph.count()
        assertEquals(0, count.onCurveEquivalent)
        assertEquals(0, count.offCurve)
        assertEquals(0, count.contourCount)
    }
}
