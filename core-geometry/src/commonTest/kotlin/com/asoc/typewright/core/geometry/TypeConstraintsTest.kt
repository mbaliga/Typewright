// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.geometry

import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * [applyTypeConstraints]/[applyTypeConstraintsToContours] and their internal building blocks
 * ([insertExtremaOnCurvePoints], [splitCubicAt], [splitCubicAtParameters], [snapNearAxisTangents],
 * [snapPointsToMetricLines], [enforceContourDirections], [cubicSegments]), each against a
 * constructed input whose correct answer is known by construction (CLAUDE.md convention: "every
 * public function in `core-*` has a unit test", applied to every meaningful internal one too, the
 * same pattern P2a's own test files already set).
 */
class TypeConstraintsTest {
    // -------------------------------------------------------------------------------------------
    // insertExtremaOnCurvePoints / splitCubicAt / splitCubicAtParameters
    // -------------------------------------------------------------------------------------------

    @Test
    fun insertsExtremaOnCurvePointsAtASymmetricLensTopAndBottom() {
        // Two mirrored cubic arcs, each symmetric about x=0, so each has exactly one interior
        // y-extremum at t=0.5 (x(t) is strictly monotonic along each, by construction -- see
        // this test's own derivation, reproduced in the KDoc-adjacent comment below) and no
        // x-extremum at all.
        //   top:    (-100,0) -[c(-100,150), c(100,150)]-> (100,0)     -> y(0.5) = 112.5
        //   bottom: (100,0)  -[c(100,-150), c(-100,-150)]-> (-100,0)  -> y(0.5) = -112.5
        val lens =
            Contour(
                listOf(
                    on(-100, 0),
                    off(-100, 150),
                    off(100, 150),
                    on(100, 0),
                    off(100, -150),
                    off(-100, -150),
                ),
                CurveFormat.CUBIC,
            )
        val before = lens.count()
        val after = insertExtremaOnCurvePoints(lens)

        assertEquals(CurveFormat.CUBIC, after.format)
        assertEquals(before.onCurveEquivalent + 2, after.count().onCurveEquivalent, "one new anchor per arc")
        assertEquals(before.offCurve + 4, after.count().offCurve, "each split triple doubles its own 2 off-curve controls")

        val onCurvePoints = after.points.filter { it.onCurve }.map { it.point }
        assertEquals(4, onCurvePoints.size)
        // Kotlin's Double.roundToInt() rounds ties towards positive infinity: 112.5 -> 113, -112.5 -> -112.
        assertEquals(setOf(Point(-100, 0), Point(0, 113), Point(100, 0), Point(0, -112)), onCurvePoints.toSet())
    }

    @Test
    fun straightSegmentsHaveNoExtremaToInsert() {
        // A pure polygon's fitted output (on-line degenerate controls, T/H's own shape) has zero
        // interior curvature anywhere, so this pass must be a complete no-op on it.
        val square = fitClosedContourToCubics(denseSquare(x0 = 0, y0 = 0, side = 400, step = 8))
        assertEquals(square, insertExtremaOnCurvePoints(square))
    }

    @Test
    fun splitCubicAtMidpointProducesTwoSegmentsSharingThePointAndTangent() {
        val segment = CurveSegment.Cubic(Vec2(0.0, 0.0), Vec2(0.0, 100.0), Vec2(100.0, 100.0), Vec2(100.0, 0.0))
        val (left, right) = splitCubicAt(segment, 0.5)
        val expected = segment.pointAt(0.5)
        assertEquals(expected, left.end)
        assertEquals(expected, right.start)
        // Both halves must trace exactly the same points the original curve did.
        for (t in listOf(0.1, 0.3, 0.5, 0.7, 0.9)) {
            val original = segment.pointAt(t)
            val reconstructed = if (t <= 0.5) left.pointAt(t / 0.5) else right.pointAt((t - 0.5) / 0.5)
            assertTrue((original - reconstructed).length() < 1e-9, "t=$t: expected $original, got $reconstructed")
        }
    }

    @Test
    fun splitCubicAtParametersHandlesMultipleSortedTsWithReparameterisation() {
        val segment = CurveSegment.Cubic(Vec2(0.0, 0.0), Vec2(0.0, 90.0), Vec2(90.0, 90.0), Vec2(90.0, 0.0))
        val expectedFirstSplit = segment.pointAt(0.25)
        val expectedSecondSplit = segment.pointAt(0.6)

        val parts = splitCubicAtParameters(segment, listOf(0.25, 0.6))

        assertEquals(3, parts.size)
        assertTrue((parts[0].end - expectedFirstSplit).length() < 1e-9)
        assertTrue((parts[1].start - expectedFirstSplit).length() < 1e-9)
        assertTrue((parts[1].end - expectedSecondSplit).length() < 1e-9)
        assertTrue((parts[2].start - expectedSecondSplit).length() < 1e-9)
    }

    @Test
    fun splitCubicAtParametersWithNoTsReturnsTheSegmentUnchanged() {
        val segment = CurveSegment.Cubic(Vec2(0.0, 0.0), Vec2(10.0, 10.0), Vec2(20.0, 10.0), Vec2(30.0, 0.0))
        assertEquals(listOf(segment), splitCubicAtParameters(segment, emptyList()))
    }

    // -------------------------------------------------------------------------------------------
    // snapNearAxisTangents
    // -------------------------------------------------------------------------------------------

    @Test
    fun snapsATangentJustInsideTheThresholdToExactlyHorizontal() {
        // A long chord (10,000 units) so that rounding the offset to an integer font unit does
        // not itself change which side of the 0.5 degree threshold the angle falls on (at a
        // chord of 100, one whole unit alone is already ~0.57 degrees -- coarser than the
        // threshold being tested).
        val dy = angleOffsetY(chord = 10_000.0, degrees = 0.3)
        val contour = Contour(listOf(on(0, 0), off(10_000, dy), off(5000, 500)), CurveFormat.CUBIC)
        val snapped = snapNearAxisTangents(contour, thresholdDegrees = 0.5)
        assertEquals(0, snapped.points[1].point.y, "0.3 degrees is inside the 0.5 degree threshold")
    }

    @Test
    fun leavesATangentOutsideTheThresholdUnchanged() {
        val dy = angleOffsetY(chord = 10_000.0, degrees = 2.0)
        val contour = Contour(listOf(on(0, 0), off(10_000, dy), off(5000, 500)), CurveFormat.CUBIC)
        val snapped = snapNearAxisTangents(contour, thresholdDegrees = 0.5)
        assertEquals(dy, snapped.points[1].point.y, "2 degrees is well outside the 0.5 degree threshold")
    }

    @Test
    fun snapsANearVerticalTangentToExactlyVertical() {
        val dx = angleOffsetY(chord = 10_000.0, degrees = 0.2)
        val contour = Contour(listOf(on(0, 0), off(dx, 10_000), off(500, 5000)), CurveFormat.CUBIC)
        val snapped = snapNearAxisTangents(contour, thresholdDegrees = 0.5)
        assertEquals(0, snapped.points[1].point.x, "0.2 degrees from vertical is inside the threshold")
    }

    @Test
    fun leavesAZeroLengthTangentUnchanged() {
        val contour = Contour(listOf(on(0, 0), off(0, 0), off(50, 5)), CurveFormat.CUBIC)
        val snapped = snapNearAxisTangents(contour, thresholdDegrees = 0.5)
        assertEquals(Point(0, 0), snapped.points[1].point)
    }

    @Test
    fun anAlreadyOnLineDegenerateSegmentIsUnaffected() {
        // T/H's own straight-side representation is already exactly axis-aligned or diagonal;
        // this pass must not perturb an already-straight chord's controls off the chord.
        val square = fitClosedContourToCubics(denseSquare(x0 = 0, y0 = 0, side = 400, step = 8))
        assertEquals(square, snapNearAxisTangents(square, thresholdDegrees = 0.5))
    }

    // -------------------------------------------------------------------------------------------
    // snapPointsToMetricLines
    // -------------------------------------------------------------------------------------------

    @Test
    fun snapsAFlatRunOfPointsToTheMetricLine() {
        val contour = straightPolygonContour(listOf(Point(0, 2), Point(50, 2), Point(50, -50), Point(0, -50)))
        val snapped = snapPointsToMetricLines(contour, metricLines = listOf(0), snapDistance = 2)
        val onCurvePoints = snapped.points.filter { it.onCurve }.map { it.point }
        assertEquals(listOf(Point(0, 0), Point(50, 0), Point(50, -50), Point(0, -50)), onCurvePoints)
    }

    @Test
    fun snapsASharpPolygonVertexEvenThoughItIsNotFlat() {
        // CLAUDE.md's own fixture: a single sharp vertex near the baseline ("T stem foot y=1 ->
        // 0"), both neighbouring sides straight -- there is no curvature here for an overshoot to
        // preserve, so this snaps even though it is not part of a flat run.
        val contour = straightPolygonContour(listOf(Point(40, 1), Point(60, 60), Point(20, 60)))
        val snapped = snapPointsToMetricLines(contour, metricLines = listOf(0), snapDistance = 2)
        assertEquals(Point(40, 0), snapped.points[0].point)
    }

    @Test
    fun preservesAGenuinelyCurvedPeakNearAMetricLine() {
        // A peak at (0,1) is the single apex of a curved arc on one side and a straight side on
        // the other -- a real round bulge, brief §7's "overshoot detected on a curved approach"
        // exemption -- so it must be left exactly where it was, not pulled onto the line.
        val curvedPoints =
            listOf(
                on(0, 1),
                off(20, 40),
                off(60, 70),
            )
        val straightBack = straightSegmentTriple(Point(80, 80), Point(0, 1))
        val contour = Contour(curvedPoints + straightBack, CurveFormat.CUBIC)

        val snapped = snapPointsToMetricLines(contour, metricLines = listOf(0), snapDistance = 2)

        assertEquals(1, snapped.points[0].point.y, "the curved peak must be preserved, not snapped to the line")
    }

    @Test
    fun aPointAlreadyExactlyOnTheLineIsLeftAlone() {
        val contour = straightPolygonContour(listOf(Point(10, 0), Point(50, 60), Point(-10, 60)))
        assertEquals(contour, snapPointsToMetricLines(contour, metricLines = listOf(0), snapDistance = 2))
    }

    @Test
    fun emptyMetricLinesIsANoOp() {
        val contour = straightPolygonContour(listOf(Point(0, 1), Point(50, 60), Point(-10, 60)))
        assertEquals(contour, snapPointsToMetricLines(contour, metricLines = emptyList(), snapDistance = 2))
    }

    @Test
    fun zeroOrNegativeSnapDistanceIsANoOp() {
        val contour = straightPolygonContour(listOf(Point(0, 1), Point(50, 60), Point(-10, 60)))
        assertEquals(contour, snapPointsToMetricLines(contour, metricLines = listOf(0), snapDistance = 0))
    }

    // -------------------------------------------------------------------------------------------
    // enforceContourDirections
    // -------------------------------------------------------------------------------------------

    @Test
    fun enforceContourDirectionsOnEmptyListReturnsEmpty() {
        assertEquals(emptyList(), enforceContourDirections(emptyList()))
    }

    @Test
    fun enforceContourDirectionsOnASingleContourForcesCounterClockwise() {
        val clockwiseSquare = straightPolygonContour(listOf(Point(0, 0), Point(100, 0), Point(100, 100), Point(0, 100))).reverse()
        assertEquals(Direction.CLOCKWISE, clockwiseSquare.direction())
        val result = enforceContourDirections(listOf(clockwiseSquare))
        assertEquals(Direction.COUNTER_CLOCKWISE, result[0].direction())
    }

    @Test
    fun outerGoesCounterClockwiseAndNestedInnerGoesClockwiseRegardlessOfStartingWinding() {
        val outerCcw = straightPolygonContour(listOf(Point(-100, -100), Point(100, -100), Point(100, 100), Point(-100, 100)))
        val innerCcw = straightPolygonContour(listOf(Point(-50, -50), Point(50, -50), Point(50, 50), Point(-50, 50)))
        assertEquals(Direction.COUNTER_CLOCKWISE, outerCcw.direction())
        assertEquals(Direction.COUNTER_CLOCKWISE, innerCcw.direction())

        val result = enforceContourDirections(listOf(outerCcw, innerCcw))
        assertEquals(Direction.COUNTER_CLOCKWISE, result[0].direction(), "the outer contour stays counter-clockwise")
        assertEquals(Direction.CLOCKWISE, result[1].direction(), "the nested inner contour must become clockwise")

        // Starting the inner contour already clockwise, and the outer already clockwise, must
        // both come out the same way -- this is about nesting, not about which way either one
        // started.
        val result2 = enforceContourDirections(listOf(outerCcw.reverse(), innerCcw.reverse()))
        assertEquals(Direction.COUNTER_CLOCKWISE, result2[0].direction())
        assertEquals(Direction.CLOCKWISE, result2[1].direction())
    }

    @Test
    fun twoDisjointContoursBothEndUpCounterClockwise() {
        // Two separate, unnested shapes (an 'i's dot and stem, or two glyph parts side by side):
        // neither contains the other, so both are "outer" and both must read counter-clockwise --
        // this is what tells enforceContourDirections apart from a naive "first contour outer,
        // rest inner" rule.
        val left = straightPolygonContour(listOf(Point(-200, -50), Point(-100, -50), Point(-100, 50), Point(-200, 50)))
        val right = straightPolygonContour(listOf(Point(100, -50), Point(200, -50), Point(200, 50), Point(100, 50))).reverse()
        assertEquals(Direction.CLOCKWISE, right.direction())

        val result = enforceContourDirections(listOf(left, right))
        assertEquals(Direction.COUNTER_CLOCKWISE, result[0].direction())
        assertEquals(Direction.COUNTER_CLOCKWISE, result[1].direction())
    }

    // -------------------------------------------------------------------------------------------
    // applyTypeConstraints / applyTypeConstraintsToContours (the full "Snap" stage)
    // -------------------------------------------------------------------------------------------

    @Test
    fun applyTypeConstraintsRejectsANonCubicContour() {
        val quadratic =
            Contour(
                listOf(
                    ContourPoint(Point(0, 0), onCurve = true),
                    ContourPoint(Point(10, 10), onCurve = false),
                    ContourPoint(Point(20, 0), onCurve = true),
                ),
                CurveFormat.QUADRATIC,
            )
        assertFailsWith<IllegalArgumentException> { applyTypeConstraints(quadratic) }
    }

    @Test
    fun applyTypeConstraintsSnapsAFlatTopToTheMetricLine() {
        val rectangle = straightPolygonContour(listOf(Point(0, 1), Point(50, 1), Point(50, -49), Point(0, -49)))
        val result = applyTypeConstraints(rectangle, metricLines = listOf(0))
        val onCurveYs = result.points.filter { it.onCurve }.map { it.point.y }
        assertEquals(listOf(0, 0, -49, -49), onCurveYs)
    }

    @Test
    fun applyTypeConstraintsToContoursSnapsMetricsAndFixesDirectionTogether() {
        // The outer contour's top sits 1 unit off the baseline (should snap); the inner contour
        // is a small square deep in the middle, comfortably away from either the metric line or
        // outer's own boundary, so this test isolates "does the pipeline correctly combine both
        // passes" from any edge-proximity questions the containment test itself is answered by
        // (see outerGoesCounterClockwiseAndNestedInnerGoesClockwiseRegardlessOfStartingWinding).
        val outer = straightPolygonContour(listOf(Point(-100, 1), Point(100, 1), Point(100, -99), Point(-100, -99)))
        val innerCcw = straightPolygonContour(listOf(Point(-20, -40), Point(20, -40), Point(20, -60), Point(-20, -60)))

        val result = applyTypeConstraintsToContours(listOf(outer, innerCcw), metricLines = listOf(0))

        assertEquals(Direction.COUNTER_CLOCKWISE, result[0].direction(), "the outer (top-level) contour must read counter-clockwise")
        assertEquals(Direction.CLOCKWISE, result[1].direction(), "the nested inner contour must read clockwise")
        val outerTopYs = result[0].points.filter { it.onCurve && it.point.y > -99 }.map { it.point.y }
        assertEquals(listOf(0, 0), outerTopYs, "the outer contour's near-baseline flat top must snap exactly to 0")
    }

    // -------------------------------------------------------------------------------------------
    // cubicSegments
    // -------------------------------------------------------------------------------------------

    @Test
    fun cubicSegmentsRejectsAQuadraticContour() {
        val quadratic =
            Contour(
                listOf(
                    ContourPoint(Point(0, 0), onCurve = true),
                    ContourPoint(Point(10, 10), onCurve = false),
                    ContourPoint(Point(20, 0), onCurve = true),
                ),
                CurveFormat.QUADRATIC,
            )
        assertFailsWith<IllegalArgumentException> { quadratic.cubicSegments() }
    }

    @Test
    fun cubicSegmentsReturnsOneEntryPerTriple() {
        val square = fitClosedContourToCubics(denseSquare(x0 = 0, y0 = 0, side = 400, step = 8))
        assertEquals(square.points.size / 3, square.cubicSegments().size)
    }
}

// -------------------------------------------------------------------------------------------
// Test fixtures and helpers.
// -------------------------------------------------------------------------------------------

private fun on(
    x: Int,
    y: Int,
) = ContourPoint(Point(x, y), onCurve = true)

private fun off(
    x: Int,
    y: Int,
) = ContourPoint(Point(x, y), onCurve = false)

/**
 * How far, along one axis, a point [chord] units from the origin sits at [degrees] from the other
 * axis -- used to build a tangent that is deliberately just inside or outside
 * [DEFAULT_TANGENT_SNAP_DEGREES].
 */
private fun angleOffsetY(
    chord: Double,
    degrees: Double,
): Int = (chord * tan(degrees * PI / 180.0)).roundToInt()

/**
 * One straight segment's (on, off, off) triple from [a] to [b], with its two control points
 * placed a third and two-thirds of the way along the chord -- the conventional degenerate
 * placement for a straight cubic (also what
 * [com.asoc.typewright.core.geometry.generateBezier]'s own fallback produces).
 */
private fun straightSegmentTriple(
    a: Point,
    b: Point,
): List<ContourPoint> {
    val ax = a.x.toDouble()
    val ay = a.y.toDouble()
    val bx = b.x.toDouble()
    val by = b.y.toDouble()
    val c1 = Point((ax + (bx - ax) / 3.0).roundToInt(), (ay + (by - ay) / 3.0).roundToInt())
    val c2 = Point((ax + (bx - ax) * 2.0 / 3.0).roundToInt(), (ay + (by - ay) * 2.0 / 3.0).roundToInt())
    return listOf(ContourPoint(a, onCurve = true), ContourPoint(c1, onCurve = false), ContourPoint(c2, onCurve = false))
}

/** A closed CUBIC polygon contour through [corners] in order, every side an on-line degenerate straight cubic ([straightSegmentTriple]). */
private fun straightPolygonContour(corners: List<Point>): Contour {
    val points = corners.indices.flatMap { i -> straightSegmentTriple(corners[i], corners[(i + 1) % corners.size]) }
    return Contour(points, CurveFormat.CUBIC)
}
