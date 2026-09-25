// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.engine.construct

import dev.aarso.typewright.core.geometry.Contour
import dev.aarso.typewright.core.geometry.Direction
import dev.aarso.typewright.core.geometry.Vec2
import dev.aarso.typewright.core.geometry.count
import dev.aarso.typewright.core.geometry.direction
import dev.aarso.typewright.core.geometry.signedArea
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Task P5a-hard item 1: curve-restoring booleans, on constructed shapes whose correct answer is
 * either exact (a circle-circle lens has a closed-form area) or boundable (disjoint / nested /
 * self cases). Every fixture below prints its actual-vs-expected numbers (CLAUDE.md's "measured,
 * not invented" and this task's own honesty rule) rather than only asserting a pass/fail.
 */
class BooleansTest {
    // -----------------------------------------------------------------------------------------
    // Low-level pieces, tested directly.
    // -----------------------------------------------------------------------------------------

    @Test
    fun intersectSegmentsFindsAnOrdinaryTransversalCrossing() {
        // An X: (0,0)-(10,10) against (0,10)-(10,0), crossing exactly at (5,5).
        val result = intersectSegments(Vec2(0.0, 0.0), Vec2(10.0, 10.0), Vec2(0.0, 10.0), Vec2(10.0, 0.0))
        assertEquals(1, result.size)
        assertTrue(abs(result[0].point.x - 5.0) < 1e-9 && abs(result[0].point.y - 5.0) < 1e-9)
        assertTrue(abs(result[0].fracA - 0.5) < 1e-9)
        assertTrue(abs(result[0].fracB - 0.5) < 1e-9)
    }

    @Test
    fun intersectSegmentsFindsNothingForParallelNonCollinearSegments() {
        val result = intersectSegments(Vec2(0.0, 0.0), Vec2(10.0, 0.0), Vec2(0.0, 5.0), Vec2(10.0, 5.0))
        assertTrue(result.isEmpty())
    }

    @Test
    fun intersectSegmentsFindsNothingWhenSegmentsDoNotReachEachOther() {
        // Same infinite line, but the segments themselves are far apart.
        val result = intersectSegments(Vec2(0.0, 0.0), Vec2(1.0, 0.0), Vec2(10.0, 0.0), Vec2(11.0, 0.0))
        assertTrue(result.isEmpty())
    }

    @Test
    fun intersectSegmentsReturnsTheOverlapIntervalForCollinearOverlappingSegments() {
        // Both on the x-axis: a = [0,10], b = [4,14] -> overlap [4,10].
        val result = intersectSegments(Vec2(0.0, 0.0), Vec2(10.0, 0.0), Vec2(4.0, 0.0), Vec2(14.0, 0.0))
        assertEquals(2, result.size, "a collinear overlap reports its two boundary points")
        val xs = result.map { it.point.x }.sorted()
        assertTrue(abs(xs[0] - 4.0) < 1e-9 && abs(xs[1] - 10.0) < 1e-9)
    }

    @Test
    fun windingNumberIsOneInsideAndZeroOutsideASimpleSquare() {
        val square = listOf(Vec2(0.0, 0.0), Vec2(10.0, 0.0), Vec2(10.0, 10.0), Vec2(0.0, 10.0))
        assertEquals(1, windingNumber(Vec2(5.0, 5.0), square))
        assertEquals(0, windingNumber(Vec2(50.0, 50.0), square))
    }

    @Test
    fun totalWindingIsZeroInsideAHoleOfAnOuterPlusInnerRingPair() {
        val outer = listOf(Vec2(0.0, 0.0), Vec2(20.0, 0.0), Vec2(20.0, 20.0), Vec2(0.0, 20.0))
        // Inner ring reversed (CW) -- CLAUDE.md's own hole convention.
        val inner = listOf(Vec2(5.0, 5.0), Vec2(5.0, 15.0), Vec2(15.0, 15.0), Vec2(15.0, 5.0))
        val rings = listOf(outer, inner)
        assertEquals(0, totalWinding(Vec2(10.0, 10.0), rings), "inside the hole")
        assertEquals(1, totalWinding(Vec2(1.0, 1.0), rings), "inside the material, outside the hole")
        assertEquals(0, totalWinding(Vec2(50.0, 50.0), rings), "outside everything")
    }

    @Test
    fun insideResultMatchesEachOpsOwnSetDefinition() {
        assertTrue(insideResult(BooleanOp.UNION, subjectWinding = 1, clipWinding = 0))
        assertTrue(insideResult(BooleanOp.UNION, subjectWinding = 0, clipWinding = 1))
        assertTrue(!insideResult(BooleanOp.INTERSECT, subjectWinding = 1, clipWinding = 0))
        assertTrue(insideResult(BooleanOp.INTERSECT, subjectWinding = 1, clipWinding = 1))
        assertTrue(insideResult(BooleanOp.SUBTRACT, subjectWinding = 1, clipWinding = 0))
        assertTrue(!insideResult(BooleanOp.SUBTRACT, subjectWinding = 1, clipWinding = 1))
        assertTrue(insideResult(BooleanOp.EXCLUDE, subjectWinding = 1, clipWinding = 0))
        assertTrue(!insideResult(BooleanOp.EXCLUDE, subjectWinding = 1, clipWinding = 1))
    }

    @Test
    fun mergeIntoRunsJoinsConsecutiveSameSegmentEdges() {
        val edges =
            listOf(
                resultEdge(segmentIndex = 0, t0 = 0.0, t1 = 0.3),
                resultEdge(segmentIndex = 0, t0 = 0.3, t1 = 0.7),
                resultEdge(segmentIndex = 1, t0 = 0.0, t1 = 1.0),
            )
        val runs = mergeIntoRuns(edges)
        assertEquals(2, runs.size, "runs: $runs")
        assertEquals(0.0, runs[0].tStart, 1e-9)
        assertEquals(0.7, runs[0].tEnd, 1e-9, "the two adjacent segment-0 pieces merge into one [0.0, 0.7] run")
        assertEquals(1, runs[1].segmentIndex)
    }

    @Test
    fun mergeIntoRunsReassemblesARunThatTheLoopsArbitraryStartingEdgeSplitInTwo() {
        // A real traced loop can start at any edge, including one in the middle of an otherwise
        // untouched original segment's own run of flattening sub-edges: the second half of that
        // run then appears first in the edge list, and its first half appears last (the loop
        // closes there). Segment 0's true, contiguous span here is [0.4, 0.9], split at t=0.6.
        val edges =
            listOf(
                resultEdge(segmentIndex = 0, t0 = 0.6, t1 = 0.9),
                resultEdge(segmentIndex = 1, t0 = 0.0, t1 = 1.0),
                resultEdge(segmentIndex = 0, t0 = 0.4, t1 = 0.6),
            )
        val runs = mergeIntoRuns(edges)
        assertEquals(2, runs.size, "runs: $runs")
        val segment0Run = runs.first { it.segmentIndex == 0 }
        assertEquals(0.4, segment0Run.tStart, 1e-9, "reassembled across the wrap: starts where the last edge started")
        assertEquals(0.9, segment0Run.tEnd, 1e-9, "and ends where the first edge ended")
        val segment1Run = runs.first { it.segmentIndex == 1 }
        assertEquals(0.0, segment1Run.tStart, 1e-9)
        assertEquals(1.0, segment1Run.tEnd, 1e-9)
    }

    private fun resultEdge(
        segmentIndex: Int,
        t0: Double,
        t1: Double,
    ): ResultEdge = ResultEdge(Vec2(t0, 0.0), Vec2(t1, 0.0), Side.SUBJECT, ringIndex = 0, segmentIndex = segmentIndex, t0 = t0, t1 = t1)

    // -----------------------------------------------------------------------------------------
    // The named fixture: two overlapping circles, all four ops checked against the closed-form
    // circle-circle intersection (lens) area.
    // -----------------------------------------------------------------------------------------

    companion object {
        private const val RADIUS = 200.0
        private const val CENTER_DISTANCE = 250.0

        /** The classic closed-form circle-circle intersection ("lens") area, two equal circles of [r], centres [d] apart. */
        private fun lensArea(
            r: Double,
            d: Double,
        ): Double {
            if (d >= 2 * r) return 0.0
            val term = r * r * acos((d * d) / (2.0 * d * r)) * 2.0
            val half = d / 2.0
            val chordHalf = sqrt(r * r - half * half)
            return term - d * chordHalf
        }
    }

    private fun overlappingCircles(): Pair<Contour, Contour> =
        circleContour(cx = 0.0, cy = 0.0, radius = RADIUS) to circleContour(cx = CENTER_DISTANCE, cy = 0.0, radius = RADIUS)

    private fun totalArea(contours: List<Contour>): Double = contours.sumOf { it.signedArea() }

    @Test
    fun unionOfTwoOverlappingCirclesMatchesTheClosedFormAreaAndStaysSmall() {
        val (a, b) = overlappingCircles()
        val result = union(a, b)
        val circleArea = PI * RADIUS * RADIUS
        val lens = lensArea(RADIUS, CENTER_DISTANCE)
        val expected = 2 * circleArea - lens
        val actual = totalArea(result)
        println("union(overlapping circles): expected area=$expected, actual area=$actual, contours=${result.size}")
        assertEquals(1, result.size, "two overlapping (not disjoint, not nested) circles union to one contour")
        assertTrue(abs(actual - expected) / expected < 0.02, "union area $actual too far from expected $expected")
        assertEquals(Direction.COUNTER_CLOCKWISE, result[0].direction())

        val counts = result[0].count()
        println("union(overlapping circles) point counts: on-curve=${counts.onCurveEquivalent}, off-curve=${counts.offCurve}")
        assertTrue(counts.onCurveEquivalent < 30, "curve-restored union ballooned to ${counts.onCurveEquivalent} on-curve points")
    }

    @Test
    fun intersectOfTwoOverlappingCirclesMatchesTheClosedFormLensAreaExactly() {
        val (a, b) = overlappingCircles()
        val result = intersect(a, b)
        val expected = lensArea(RADIUS, CENTER_DISTANCE)
        val actual = totalArea(result)
        println("intersect(overlapping circles): expected lens area=$expected, actual=$actual, contours=${result.size}")
        assertEquals(1, result.size)
        assertTrue(abs(actual - expected) / expected < 0.02, "intersect area $actual too far from expected $expected")
    }

    @Test
    fun subtractOfTwoOverlappingCirclesMatchesCircleAreaMinusTheLens() {
        val (a, b) = overlappingCircles()
        val result = subtract(a, b)
        val circleArea = PI * RADIUS * RADIUS
        val expected = circleArea - lensArea(RADIUS, CENTER_DISTANCE)
        val actual = totalArea(result)
        println("subtract(overlapping circles, A-B): expected area=$expected, actual=$actual, contours=${result.size}")
        assertEquals(1, result.size)
        assertTrue(abs(actual - expected) / expected < 0.02, "subtract area $actual too far from expected $expected")
    }

    @Test
    fun excludeOfTwoOverlappingCirclesMatchesTheSymmetricDifferenceArea() {
        val (a, b) = overlappingCircles()
        val result = exclude(a, b)
        val circleArea = PI * RADIUS * RADIUS
        val expected = 2 * circleArea - 2 * lensArea(RADIUS, CENTER_DISTANCE)
        val actual = totalArea(result)
        println("exclude(overlapping circles): expected area=$expected, actual=$actual, contours=${result.size}")
        assertTrue(abs(actual - expected) / expected < 0.02, "exclude area $actual too far from expected $expected")
    }

    // -----------------------------------------------------------------------------------------
    // Nested, disjoint and self-union -- the degenerate/no-real-crossing cases.
    // -----------------------------------------------------------------------------------------

    @Test
    fun unionOfACircleWithASmallerCircleFullyInsideEqualsTheOuterCircleExactly() {
        val outer = circleContour(cx = 0.0, cy = 0.0, radius = 300.0)
        val inner = circleContour(cx = 0.0, cy = 0.0, radius = 100.0)
        val result = union(outer, inner)
        assertEquals(1, result.size, "the fully-swallowed inner circle contributes no separate contour")
        assertEquals(outer.points, result[0].points, "curve restoration returns the untouched outer circle byte-for-byte")
    }

    @Test
    fun unionOfTwoDisjointCirclesKeepsThemAsTwoSeparateContours() {
        val a = circleContour(cx = 0.0, cy = 0.0, radius = 200.0)
        val b = circleContour(cx = 1000.0, cy = 0.0, radius = 200.0)
        val result = union(a, b)
        println("union(disjoint circles): contours=${result.size}, areas=${result.map { it.signedArea() }}")
        assertEquals(2, result.size, "two disjoint circles union to two separate contours, never one")
        val circleArea = PI * 200.0 * 200.0
        for (c in result) {
            assertTrue(abs(c.signedArea() - circleArea) / circleArea < 0.01)
        }
    }

    @Test
    fun unionOfAShapeWithItselfEqualsItself() {
        val a = circleContour(cx = 0.0, cy = 0.0, radius = 200.0)
        val result = union(a, a)
        println("union(A, A): contours=${result.size}, points=${result.getOrNull(0)?.points?.size}, expected points=${a.points.size}")
        assertEquals(1, result.size, "idempotence: a shape unioned with itself is one contour, not two overlapping copies")
        assertEquals(a.points, result[0].points, "and it is byte-identical to the original, not a re-derived approximation of it")
    }

    // -----------------------------------------------------------------------------------------
    // The named fixture: subtract a circle from a square, known resulting area range.
    // -----------------------------------------------------------------------------------------

    @Test
    fun subtractingACircleFromASquareLeavesAnAnnulusOfTheExpectedArea() {
        val square = rectangleContour(0, 0, 500, 500)
        val circle = circleContour(cx = 250.0, cy = 250.0, radius = 150.0)
        val result = subtract(square, circle)
        val expectedArea = 500.0 * 500.0 - PI * 150.0 * 150.0
        val actualArea = totalArea(result)
        println("subtract(square, inscribed circle): expected area=$expectedArea, actual=$actualArea, contours=${result.size}")
        assertEquals(2, result.size, "an outer square plus an inner hole, exactly like any other counter in this codebase")
        assertTrue(abs(actualArea - expectedArea) / expectedArea < 0.01)
        val outer = result.first { it.direction() == Direction.COUNTER_CLOCKWISE }
        val hole = result.first { it.direction() == Direction.CLOCKWISE }
        assertEquals(square.points, outer.points, "the untouched square boundary is returned byte-for-byte")
        assertTrue(hole.signedArea() < 0.0, "a hole's signed area is negative (clockwise), CLAUDE.md's own inner-ring convention")
    }

    // -----------------------------------------------------------------------------------------
    // booleanOp's own input validation.
    // -----------------------------------------------------------------------------------------

    @Test
    fun booleanOpRejectsAnEmptySubjectOrClipList() {
        val a = circleContour(cx = 0.0, cy = 0.0, radius = 100.0)
        assertTrue(
            runCatching { booleanOp(emptyList(), listOf(a), BooleanOp.UNION) }.isFailure,
            "an empty subject list must be rejected, not silently treated as the empty set",
        )
        assertTrue(runCatching { booleanOp(listOf(a), emptyList(), BooleanOp.UNION) }.isFailure)
    }
}
