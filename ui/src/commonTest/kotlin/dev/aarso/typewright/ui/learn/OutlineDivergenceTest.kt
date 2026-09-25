// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.ui.learn

import dev.aarso.typewright.core.geometry.Contour
import dev.aarso.typewright.core.geometry.ContourPoint
import dev.aarso.typewright.core.geometry.CurveFormat
import dev.aarso.typewright.core.geometry.Glyph
import dev.aarso.typewright.core.geometry.Point
import dev.aarso.typewright.core.geometry.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A straight-sided polygon contour -- the same shape of fixture `qa/corpus`'s own (internal, different module) `SyntheticGlyphs.polygon` builds, reproduced here since it is not visible across the module boundary. */
private fun square(
    x0: Int,
    y0: Int,
    size: Int,
): Contour =
    Contour(
        listOf(
            ContourPoint(Point(x0, y0), onCurve = true),
            ContourPoint(Point(x0 + size, y0), onCurve = true),
            ContourPoint(Point(x0 + size, y0 + size), onCurve = true),
            ContourPoint(Point(x0, y0 + size), onCurve = true),
        ),
        CurveFormat.QUADRATIC,
    )

private fun squareGlyph(
    size: Int = 100,
    advance: Int = size,
): Glyph = Glyph("square", advance, listOf(square(0, 0, size)))

class OutlineDivergenceTest {
    @Test
    fun flattenPolylineOfAFourPointSquareHasFourSamplesPerSegmentEachAtASegmentStart() {
        val poly = square(0, 0, 100).flattenPolyline(samplesPerSegment = 4)
        // 4 segments (one per side) * 4 samples each = 16 points; the first sample of each
        // segment (t=0) is exactly that segment's own start anchor, so all four corners appear.
        assertEquals(16, poly.size)
        assertTrue(Vec2(0.0, 0.0) in poly)
        assertTrue(Vec2(100.0, 0.0) in poly)
        assertTrue(Vec2(100.0, 100.0) in poly)
        assertTrue(Vec2(0.0, 100.0) in poly)
    }

    @Test
    fun distanceToPolylineIsZeroOnAnEdgeAndPositiveOutside() {
        val square = square(0, 0, 100).flattenPolyline(samplesPerSegment = 16)
        assertApproxEquals(0.0, distanceToPolyline(Vec2(50.0, 0.0), square))
        assertApproxEquals(10.0, distanceToPolyline(Vec2(50.0, -10.0), square))
        assertApproxEquals(0.0, distanceToPolyline(Vec2(0.0, 0.0), square))
    }

    @Test
    fun distanceToPolylineIsInfiniteForFewerThanTwoPoints() {
        assertEquals(Double.POSITIVE_INFINITY, distanceToPolyline(Vec2(0.0, 0.0), listOf(Vec2(1.0, 1.0))))
        assertEquals(Double.POSITIVE_INFINITY, distanceToPolyline(Vec2(0.0, 0.0), emptyList()))
    }

    @Test
    fun findDivergentPointsIsEmptyWhenBothOutlinesMapToTheSameShape() {
        val a = squareGlyph()
        val b = squareGlyph()
        val identity: (Vec2) -> Vec2 = { it }
        val divergent = findDivergentPoints(a, identity, b, identity, toleranceCanvasUnits = 0.5)
        assertTrue(divergent.isEmpty(), "an outline compared against an identical outline in the same space must never diverge")
    }

    @Test
    fun findDivergentPointsFindsEveryPointWhenTheSecondOutlineIsFarAway() {
        val a = squareGlyph()
        val b = squareGlyph()
        val identity: (Vec2) -> Vec2 = { it }
        // Move b's whole outline 1000 units away -- every one of a's sampled points is now far
        // from b's nearest edge, well past a tight tolerance.
        val shiftFarAway: (Vec2) -> Vec2 = { v -> Vec2(v.x + 1000.0, v.y) }
        val divergent = findDivergentPoints(a, identity, b, shiftFarAway, toleranceCanvasUnits = 5.0)
        val expectedSampleCount = a.flattenContours().sumOf { it.size }
        assertEquals(expectedSampleCount, divergent.size)
    }

    @Test
    fun findDivergentPointsRespectsToleranceForASmallShift() {
        val a = squareGlyph()
        val b = squareGlyph()
        val identity: (Vec2) -> Vec2 = { it }
        // Shift b by exactly 2 units on x: every point on a's left/right vertical edges is now
        // ~2 units from b's nearest edge; a tolerance of 5 should swallow that, a tolerance of 1
        // should not.
        val shiftSmall: (Vec2) -> Vec2 = { v -> Vec2(v.x + 2.0, v.y) }
        val withGenerousTolerance = findDivergentPoints(a, identity, b, shiftSmall, toleranceCanvasUnits = 5.0)
        val withTightTolerance = findDivergentPoints(a, identity, b, shiftSmall, toleranceCanvasUnits = 1.0)
        assertTrue(withGenerousTolerance.isEmpty())
        assertTrue(withTightTolerance.isNotEmpty())
    }

    @Test
    fun findDivergentPointsIsEmptyWhenTheComparisonGlyphHasNoContours() {
        val a = squareGlyph()
        val empty = Glyph("empty", 100, emptyList())
        val identity: (Vec2) -> Vec2 = { it }
        assertTrue(findDivergentPoints(a, identity, empty, identity, toleranceCanvasUnits = 1.0).isEmpty())
    }
}

private fun assertApproxEquals(
    expected: Double,
    actual: Double,
    absoluteTolerance: Double = 1e-6,
) {
    assertTrue(kotlin.math.abs(expected - actual) <= absoluteTolerance, "expected $expected but was $actual (tolerance $absoluteTolerance)")
}
