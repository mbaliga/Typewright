// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa

import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.ContourPoint
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun on(
    x: Int,
    y: Int,
) = ContourPoint(Point(x, y), onCurve = true)

private fun off(
    x: Int,
    y: Int,
) = ContourPoint(Point(x, y), onCurve = false)

private fun quadGlyph(
    name: String,
    points: List<ContourPoint>,
) = Glyph(name, 500, listOf(Contour(points, CurveFormat.QUADRATIC)))

class GeometricChecksTest {
    // --- alignment miss ---

    @Test
    fun aPointCloseToButNotOnAMetricLineIsAnAlignmentMiss() {
        val glyph = quadGlyph("g", listOf(on(0, 2), on(10, 2), on(10, 20), on(0, 20)))
        val findings = checkAlignmentMiss(glyph, mapOf("baseline" to 0))
        assertEquals(2, findings.size) // the two points at y=2
        assertTrue(findings.all { it.checkId == "outline/alignment-miss" })
    }

    @Test
    fun aPointExactlyOnAMetricLineIsNotAMiss() {
        val glyph = quadGlyph("g", listOf(on(0, 0), on(10, 0), on(10, 20), on(0, 20)))
        assertEquals(emptyList(), checkAlignmentMiss(glyph, mapOf("baseline" to 0)))
    }

    @Test
    fun aPointFarFromEveryMetricLineIsNotAMiss() {
        val glyph = quadGlyph("g", listOf(on(0, 50), on(10, 50), on(10, 70), on(0, 70)))
        assertEquals(emptyList(), checkAlignmentMiss(glyph, mapOf("baseline" to 0, "x-height" to 500)))
    }

    @Test
    fun offCurvePointsAreNeverCheckedForAlignment() {
        val glyph = quadGlyph("g", listOf(on(0, 0), off(5, 2), on(10, 0)))
        assertEquals(emptyList(), checkAlignmentMiss(glyph, mapOf("baseline" to 0)))
    }

    // --- collinear / jaggy ---

    @Test
    fun threeCollinearOnCurvePointsFlagTheJointBetweenThem() {
        val glyph = quadGlyph("g", listOf(on(0, 0), on(5, 0), on(10, 0), on(10, 10)))
        val findings = checkCollinearSegments(glyph)
        assertTrue(findings.any { it.location == Point(5, 0) })
    }

    @Test
    fun aSquaresRightAnglesAreNeverCollinearOrJaggy() {
        val glyph = quadGlyph("g", listOf(on(0, 0), on(10, 0), on(10, 10), on(0, 10)))
        assertEquals(emptyList(), checkCollinearSegments(glyph))
        assertEquals(emptyList(), checkJaggyTurns(glyph))
    }

    @Test
    fun aSmallEleventDegreeTurnIsJaggyButNotCollinear() {
        // (0,0)->(100,0)->(100+100*cos(0.2), 100*sin(0.2)) turns by ~0.2 rad at the middle point.
        val glyph = quadGlyph("g", listOf(on(0, 0), on(100, 0), on(198, 20), on(0, 50)))
        val jaggy = checkJaggyTurns(glyph)
        val collinear = checkCollinearSegments(glyph)
        assertTrue(jaggy.any { it.location == Point(100, 0) })
        assertTrue(collinear.none { it.location == Point(100, 0) })
    }

    @Test
    fun aSingleSegmentContourHasNoConsecutivePairToCompare() {
        // Degenerate on purpose: chords.size < 2 must not throw or divide by anything odd.
        val glyph = Glyph("g", 500, listOf(Contour(listOf(on(0, 0)), CurveFormat.QUADRATIC)))
        assertEquals(emptyList(), checkCollinearSegments(glyph))
        assertEquals(emptyList(), checkJaggyTurns(glyph))
    }

    // --- short segments ---

    @Test
    fun aVeryShortSegmentAmongLongOnesIsFlagged() {
        val glyph = quadGlyph("g", listOf(on(0, 0), on(1000, 0), on(1000, 1000), on(999, 1000)))
        val findings = checkShortSegments(glyph)
        assertTrue(findings.any { it.location == Point(1000, 1000) })
    }

    @Test
    fun aRegularSquaresSegmentsAreNotShort() {
        val glyph = quadGlyph("g", listOf(on(0, 0), on(100, 0), on(100, 100), on(0, 100)))
        assertEquals(emptyList(), checkShortSegments(glyph))
    }

    // --- semi-vertical ---

    @Test
    fun aSegmentAQuarterDegreeOffVerticalIsSemiVertical() {
        // tan(0.25 deg) * 10000 ~= 43.6, so (0,0)->(44,10000) is about 0.25 degrees off vertical.
        val glyph = quadGlyph("g", listOf(on(0, 0), on(44, 10000), on(-44, 10000)))
        val findings = checkSemiVertical(glyph)
        assertTrue(findings.any { it.location == Point(0, 0) })
    }

    @Test
    fun anExactlyVerticalSegmentIsNotSemiVertical() {
        val glyph = quadGlyph("g", listOf(on(0, 0), on(0, 100), on(50, 50)))
        assertEquals(emptyList(), checkSemiVertical(glyph))
    }

    @Test
    fun aFortyFiveDegreeSegmentIsNotSemiVertical() {
        val glyph = quadGlyph("g", listOf(on(0, 0), on(100, 100), on(0, 100)))
        assertEquals(emptyList(), checkSemiVertical(glyph))
    }

    // --- extrema on curve ---

    @Test
    fun anInteriorExtremumNotOnAnAnchorIsFlagged() {
        // Same fixture as CurveSegmentTest: a quadratic whose y-extremum sits at t=0.5, off both anchors.
        val glyph = quadGlyph("g", listOf(on(0, 0), off(10, 10), on(20, 0)))
        val findings = checkExtremaOnCurve(glyph)
        assertEquals(1, findings.size)
        assertEquals("outline/extrema-off-curve", findings.single().checkId)
        assertEquals(Point(10, 5), findings.single().location)
    }

    @Test
    fun aPolygonHasNoExtremaToMiss() {
        val glyph = quadGlyph("g", listOf(on(0, 0), on(10, 0), on(10, 10), on(0, 10)))
        assertEquals(emptyList(), checkExtremaOnCurve(glyph))
    }

    // --- off-curve ratio ---

    @Test
    fun offCurveRatioIsOffOverTotal() {
        val points = mutableListOf<ContourPoint>()
        repeat(4) { i ->
            points += on(i, 0)
            points += off(i, 1)
            points += off(i, 2)
        }
        val glyph = Glyph("o", 500, listOf(Contour(points, CurveFormat.CUBIC)))
        val result = offCurveRatio(glyph)
        assertEquals(4, result.onCurve)
        assertEquals(8, result.offCurve)
        assertEquals(8.0 / 12.0, result.ratio)
    }

    @Test
    fun offCurveRatioIsNullForAContourlessGlyph() {
        val glyph = Glyph("space", 300, emptyList())
        assertEquals(null, offCurveRatio(glyph).ratio)
    }
}
