// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.construct

import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.signedArea
import com.asoc.typewright.core.geometry.writtenCount
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** [strokeCentreline] and [strokeRing]: strokes whose outlines and areas are computable by hand, and every refusal with its reason. */
class StrokeOutlineTest {
    private fun done(result: StrokeResult): List<Contour> = assertIs<StrokeResult.Done>(result).contours

    private fun refused(result: StrokeResult): String = assertIs<StrokeResult.Refused>(result).reason

    @Test
    fun aStraightCentrelineWithButtCapsIsAnExactRectangleWoundAnticlockwise() {
        val outline = done(strokeCentreline(listOf(Point(0, 0), Point(100, 0)), StrokeParameters(width = 20.0))).single()
        assertEquals(CurveFormat.CUBIC, outline.format)
        assertEquals(
            setOf(Point(0, -10), Point(100, -10), Point(100, 10), Point(0, 10)),
            outline.points
                .filter {
                    it.onCurve
                }.map { it.point }
                .toSet(),
        )
        assertTrue(outline.signedArea() > 0, "an outer contour winds anticlockwise")
        assertEquals(2_000.0, outline.signedArea(), 1.0)
    }

    @Test
    fun aStrokeCostsOnlyTheNodesItsShapeNeeds() {
        val line = done(strokeCentreline(listOf(Point(0, 0), Point(100, 0)), StrokeParameters(width = 20.0))).single().writtenCount()
        assertEquals(4, line.onCurveEquivalent, "a butt stroke is a rectangle")
        assertEquals(0, line.offCurve, "and its sides are lines, with no handles written")
        val elbow = listOf(Point(0, 0), Point(100, 0), Point(100, 100))
        val miter = done(strokeCentreline(elbow, StrokeParameters(width = 20.0, join = LineJoin.MITER))).single().writtenCount()
        assertEquals(6, miter.onCurveEquivalent, "an elbow with a miter is its six corners")
        assertEquals(0, miter.offCurve)
        val straightOn = done(strokeCentreline(listOf(Point(0, 0), Point(50, 0), Point(100, 0)), StrokeParameters(width = 20.0)))
        assertEquals(4, straightOn.single().writtenCount().onCurveEquivalent, "a point on a straight run adds no node")
        val round = done(strokeCentreline(listOf(Point(0, 0), Point(100, 0)), StrokeParameters(width = 20.0, cap = LineCap.ROUND)))
        assertEquals(6, round.single().writtenCount().onCurveEquivalent, "two round caps are two nodes each, plus the sides' ends")
    }

    @Test
    fun aCentrelineDrawnRightToLeftStillWindsAnticlockwise() {
        val outline = done(strokeCentreline(listOf(Point(100, 0), Point(0, 0)), StrokeParameters(width = 20.0))).single()
        assertTrue(outline.signedArea() > 0)
    }

    @Test
    fun aMiterJoinKeepsTheOuterCornerSharpAndARoundJoinDoesNot() {
        val elbow = listOf(Point(0, 0), Point(100, 0), Point(100, 100))
        val miter = done(strokeCentreline(elbow, StrokeParameters(width = 20.0, join = LineJoin.MITER))).single()
        val round = done(strokeCentreline(elbow, StrokeParameters(width = 20.0, join = LineJoin.ROUND))).single()
        assertTrue(Point(110, -10) in miter.points.filter { it.onCurve }.map { it.point }, "the miter reaches the corner (110, -10)")
        // How far each outline reaches toward the outer corner, along the diagonal (1, -1): 120 for the miter, 100 + 10 * sqrt(2) for the arc.
        val reach = { c: Contour -> c.flattenToPolyline(0.25).maxOf { it.x - it.y } }
        assertTrue(reach(miter) > 119.0, "the miter reaches the corner: ${reach(miter)}")
        assertTrue(reach(round) < 116.0, "a round join stays inside the miter: ${reach(round)}")
        assertTrue(
            abs(round.signedArea()) < abs(miter.signedArea()),
            "the round corner takes less ink than the miter: ${round.signedArea()} against ${miter.signedArea()}",
        )
    }

    @Test
    fun theAreaOfAnElbowStrokeIsTheTwoLegsAndTheCornerSquare() {
        val elbow = listOf(Point(0, 0), Point(100, 0), Point(100, 100))
        val outline = done(strokeCentreline(elbow, StrokeParameters(width = 20.0, join = LineJoin.MITER))).single()
        // A 110 x 20 bar along the baseline and a 20 x 90 bar above it: 2,200 + 1,800.
        assertEquals(4_000.0, outline.signedArea(), 5.0)
    }

    @Test
    fun aCentrelineThatCrossesItselfIsRefusedWithAReason() {
        val bow = listOf(Point(0, 0), Point(100, 100), Point(100, 0), Point(0, 100))
        assertTrue(refused(strokeCentreline(bow, StrokeParameters(width = 10.0))).contains("crosses itself"))
    }

    @Test
    fun aSinglePointOrRepeatedPointIsRefused() {
        assertTrue(refused(strokeCentreline(listOf(Point(5, 5)), StrokeParameters(width = 10.0))).contains("two different points"))
        assertTrue(
            refused(strokeCentreline(listOf(Point(5, 5), Point(5, 5)), StrokeParameters(width = 10.0))).contains("two different points"),
        )
    }

    @Test
    fun widthsOutsideTheUsableRangeAreRefusedWithTheirLimit() {
        assertTrue(strokeWidthProblem(1.0)!!.contains("at least 2"))
        assertTrue(strokeWidthProblem(5_000.0)!!.contains("2000"))
        assertEquals(null, strokeWidthProblem(40.0))
        assertTrue(refused(strokeCentreline(listOf(Point(0, 0), Point(100, 0)), StrokeParameters(width = 5_000.0))).contains("2000"))
    }

    @Test
    fun aClosedSquareCentrelineStrokesToARingOfTheRightAreaWithTheCounterClockwise() {
        val ring = done(strokeRing(rectangleContour(0, 0, 200, 200), StrokeParameters(width = 20.0, join = LineJoin.MITER)))
        assertEquals(2, ring.size)
        assertTrue(ring[0].signedArea() > 0 && ring[1].signedArea() < 0, "outer anticlockwise, inner clockwise")
        // Outer 220 x 220 minus inner 180 x 180.
        assertEquals(220.0 * 220.0 - 180.0 * 180.0, ring.sumOf { it.signedArea() }, 20.0)
    }

    @Test
    fun aRingTooWideForItsCentrelineIsRefused() {
        assertTrue(refused(strokeRing(rectangleContour(0, 0, 40, 40), StrokeParameters(width = 120.0))).contains("folds over itself"))
    }

    @Test
    fun aQuadraticCentrelineRingIsRefused() {
        val quadratic = Contour(rectangleContour(0, 0, 10, 10).points, CurveFormat.QUADRATIC)
        assertTrue(refused(strokeRing(quadratic, StrokeParameters(width = 4.0))).contains("quadratic"))
    }

    @Test
    fun roundCapsBulgeBeyondTheEndsOfTheCentreline() {
        val outline =
            done(strokeCentreline(listOf(Point(0, 0), Point(100, 0)), StrokeParameters(width = 20.0, cap = LineCap.ROUND))).single()
        val xs = outline.flattenToPolyline(0.25).map { it.x }
        assertTrue(xs.max() > 108.0 && xs.min() < -8.0)
    }
}
