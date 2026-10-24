// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.geometry

import kotlin.math.abs
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun on(
    x: Int,
    y: Int,
    smooth: Boolean = false,
) = ContourPoint(Point(x, y), onCurve = true, smooth = smooth)

private fun off(
    x: Int,
    y: Int,
) = ContourPoint(Point(x, y), onCurve = false)

/** A 4-node circle-like cubic of radius 200 centred on the origin, every node smooth, counter-clockwise from the east. */
private fun circle(smooth: Boolean = true): Contour {
    val r = 200
    val k = 110
    return Contour(
        listOf(
            on(r, 0, smooth),
            off(r, k),
            off(k, r),
            on(0, r, smooth),
            off(-k, r),
            off(-r, k),
            on(-r, 0, smooth),
            off(-r, -k),
            off(-k, -r),
            on(0, -r, smooth),
            off(k, -r),
            off(r, -k),
        ),
        CurveFormat.CUBIC,
    )
}

/** The canonical `line` form of a rectangle: each side's controls sit on its own ends. */
private fun rectangleLines(
    x0: Int = 0,
    y0: Int = 0,
    x1: Int = 200,
    y1: Int = 100,
): Contour {
    val corners = listOf(Point(x0, y0), Point(x1, y0), Point(x1, y1), Point(x0, y1))
    val points = mutableListOf<ContourPoint>()
    for (i in corners.indices) {
        points += ContourPoint(corners[i], onCurve = true)
        points += ContourPoint(corners[i], onCurve = false)
        points += ContourPoint(corners[(i + 1) % 4], onCurve = false)
    }
    return Contour(points, CurveFormat.CUBIC)
}

private fun sample(
    contour: Contour,
    perSegment: Int = 40,
): List<Vec2> = contour.segments().flatMap { s -> (0 until perSegment).map { s.pointAt(it.toDouble() / perSegment) } }

/** The largest distance from any sample of [a] to the nearest sample of [b]: how far one outline strays from the other. */
private fun deviation(
    a: Contour,
    b: Contour,
): Double {
    val bs = sample(b, 200)
    return sample(a).maxOf { p -> bs.minOf { (it - p).length() } }
}

class ContourEditingTest {
    // ---- layout and ownership --------------------------------------------------------------

    @Test
    fun aCubicLayoutNamesEachSegmentsPointIndices() {
        val layout = circle().segmentLayout()
        assertEquals(4, layout.size)
        assertEquals(SegmentInfo(layout[1].segment, startPoint = 3, endPoint = 6, controlPoints = listOf(4, 5)), layout[1])
        assertEquals(0, layout[3].endPoint, "the last segment wraps to the first node")
    }

    @Test
    fun aQuadraticLayoutMarksImpliedEndsAsNull() {
        // on(0,0) off(10,0) off(10,10) on(0,10): the first curve ends at the implied midpoint (10,5).
        val contour = Contour(listOf(on(0, 0), off(10, 0), off(10, 10), on(0, 10)), CurveFormat.QUADRATIC)
        val layout = contour.segmentLayout()
        assertEquals(3, layout.size)
        assertEquals(0, layout[0].startPoint)
        assertNull(layout[0].endPoint)
        assertNull(layout[1].startPoint)
        assertEquals(3, layout[1].endPoint)
        assertEquals(listOf(1), layout[0].controlPoints)
        assertTrue(layout[2].segment is CurveSegment.Line)
        assertEquals(contour.segments(), layout.map { it.segment })
    }

    @Test
    fun cubicHandlesBelongToTheNodeBesideThem() {
        val c = circle()
        assertEquals(0, c.handleOwner(1))
        assertEquals(3, c.handleOwner(2))
        assertNull(c.handleOwner(0))
        assertEquals(listOf(1, 11), c.ownedHandles(0).sorted())
        assertEquals(11, c.oppositeHandle(1))
    }

    @Test
    fun aLoneQuadraticControlBetweenTwoNodesBelongsToNeitherButARunsEndsDo() {
        val shared = Contour(listOf(on(0, 0), off(50, 0), on(100, 0), on(100, 100), on(0, 100)), CurveFormat.QUADRATIC)
        assertNull(shared.handleOwner(1))
        assertEquals(emptyList(), shared.ownedHandles(0))
        val run = Contour(listOf(on(0, 0), off(40, 0), off(60, 0), on(100, 0), on(100, 100)), CurveFormat.QUADRATIC)
        assertEquals(0, run.handleOwner(1))
        assertEquals(3, run.handleOwner(2))
    }

    @Test
    fun ownedHandlesRejectsAnOffCurvePoint() {
        assertFailsWith<IllegalArgumentException> { circle().ownedHandles(1) }
    }

    @Test
    fun aRetractedHandleSitsOnItsNode() {
        val c = rectangleLines()
        assertTrue(c.isRetracted(1))
        assertTrue(!circle().isRetracted(1))
    }

    // ---- moving ----------------------------------------------------------------------------

    @Test
    fun movingANodeCarriesItsOwnHandlesAndNothingElse() {
        val moved = circle().withSelectionMoved(setOf(3), 5, -7)
        val before = circle()
        for (i in before.points.indices) {
            val expected =
                if (i in setOf(3, 4, 2)) Point(before.points[i].point.x + 5, before.points[i].point.y - 7) else before.points[i].point
            assertEquals(expected, moved.points[i].point, "point $i")
        }
    }

    @Test
    fun aHandleSelectedWithItsNodeMovesOnceNotTwice() {
        val moved = circle().withSelectionMoved(setOf(0, 1), 10, 0)
        assertEquals(Point(210, 0), moved.points[0].point)
        assertEquals(Point(210, 110), moved.points[1].point)
    }

    @Test
    fun aLoneQuadraticControlStaysPutWhenEitherNeighbourMoves() {
        val c = Contour(listOf(on(0, 0), off(50, 60), on(100, 0), on(100, -100), on(0, -100)), CurveFormat.QUADRATIC)
        val moved = c.withSelectionMoved(setOf(0), 4, 4)
        assertEquals(Point(4, 4), moved.points[0].point)
        assertEquals(Point(50, 60), moved.points[1].point)
    }

    @Test
    fun movingACornerOfALineFormRectangleKeepsEverySideStraight() {
        val moved = rectangleLines().withSelectionMoved(setOf(3), 17, 9)
        assertStraight(moved)
        assertEquals(Point(217, 9), moved.points[3].point)
    }

    @Test
    fun movingACornerOfAThirdsFormPolygonKeepsEverySideStraight() {
        val polygon = closePolylineToContour(listOf(Point(0, 0), Point(300, 0), Point(300, 200), Point(0, 200)))
        val moved = polygon.withSelectionMoved(setOf(3), 33, -41)
        assertStraight(moved)
        // The two sides at the moved corner kept their one-third / two-thirds controls on the new chord (to a rounded unit).
        val side = moved.segments()[1] as CurveSegment.Cubic
        val third = side.start + (side.end - side.start) * (1.0 / 3.0)
        assertTrue((side.control1 - third).length() < 1.5, "control ${side.control1} is not near the third $third")
    }

    private fun assertStraight(contour: Contour) {
        for (segment in contour.segments()) {
            segment as CurveSegment.Cubic
            val chord = segment.end - segment.start
            for (control in listOf(segment.control1, segment.control2)) {
                val offLine = abs(chord.cross(control - segment.start)) / maxOf(chord.length(), 1.0)
                assertTrue(offLine <= 0.75, "a side bent by $offLine units: $segment")
            }
        }
    }

    @Test
    fun draggingAHandleOfASmoothNodeSwingsTheOppositeHandleKeepingItsLength() {
        val c = circle()
        val moved = c.withHandleMoved(1, 0, 40)
        // Node 0 is at (200, 0); handle 1 went from (200, 110) to (200, 150): the opposite handle stays on the line and keeps length 110.
        assertEquals(Point(200, 150), moved.points[1].point)
        assertEquals(Point(200, -110), moved.points[11].point)
        val tilted = c.withHandleMoved(1, 60, 0)
        val h = tilted.points[1].point.toVec2() - tilted.points[0].point.toVec2()
        val o = tilted.points[11].point.toVec2() - tilted.points[0].point.toVec2()
        assertTrue(abs(h.cross(o)) <= 0.02 * h.length() * o.length(), "handles are collinear")
        assertTrue(h.dot(o) < 0, "and point in opposite directions")
        assertEquals(110.0, o.length(), 1.0)
    }

    @Test
    fun draggingAHandleOfACornerNodeLeavesTheOppositeHandleAlone() {
        val moved = circle(smooth = false).withHandleMoved(1, 60, 0)
        assertEquals(circle().points[11].point, moved.points[11].point)
    }

    @Test
    fun draggingAHandleWithSymmetryBrokenTurnsTheNodeIntoACornerAndMovesOnlyThatHandle() {
        val moved = circle().withHandleMoved(1, 60, 0, keepSmooth = false)
        assertEquals(circle().points[11].point, moved.points[11].point)
        assertTrue(!moved.points[0].smooth)
    }

    @Test
    fun movingAHandleByNothingChangesNothing() {
        assertEquals(circle(), circle().withHandleMoved(1, 0, 0))
        assertEquals(circle(), circle().withSelectionMoved(setOf(3), 0, 0))
    }

    // ---- smooth and corner -----------------------------------------------------------------

    @Test
    fun makingACornerNodeSmoothAlignsTheShorterHandleOnTheLongerOne() {
        val corner = circle(smooth = false).withHandleMoved(1, 40, 0) // handle 1 now (240, 110), no longer in line with handle 11
        val smooth = corner.withSmooth(0, true)
        assertTrue(smooth.points[0].smooth)
        val pivot = smooth.points[0].point.toVec2()
        val a = smooth.points[1].point.toVec2() - pivot
        val b = smooth.points[11].point.toVec2() - pivot
        assertTrue(abs(a.cross(b)) <= 0.02 * a.length() * b.length())
        assertTrue(a.dot(b) < 0)
        // The longer handle was not touched.
        val longIndex =
            if ((corner.points[1].point.toVec2() - pivot).length() >=
                (corner.points[11].point.toVec2() - pivot).length()
            ) {
                1
            } else {
                11
            }
        assertEquals(corner.points[longIndex].point, smooth.points[longIndex].point)
    }

    @Test
    fun makingASmoothNodeACornerChangesNoCoordinate() {
        val corner = circle().withSmooth(0, false)
        assertTrue(!corner.points[0].smooth)
        assertEquals(circle().points.map { it.point }, corner.points.map { it.point })
    }

    @Test
    fun smoothingANodeWhoseHandlesAreAlreadyInLineOnlyTakesTheFlag() {
        val same = circle(smooth = false).withSmooth(0, true)
        assertTrue(same.points[0].smooth)
        assertEquals(circle().points.map { it.point }, same.points.map { it.point })
    }

    @Test
    fun smoothingANodeBetweenTwoStraightSidesThatRunOnAsOneLineTakesTheFlagAndMovesNothing() {
        // A node on the middle of a rectangle's bottom side has no handle and a straight side either way: it is already in line.
        val withMiddleNode = rectangleLines().withNodeInserted(0, 0.5).contour
        val middle = 3
        val c = withMiddleNode.withSmooth(middle, true)
        assertTrue(c.points[middle].smooth)
        assertEquals(withMiddleNode.points.map { it.point }, c.points.map { it.point })
        assertEquals(c, c.withSmooth(middle, true))
    }

    @Test
    fun aCornerBetweenTwoStraightSidesCannotBeMadeSmoothSoTheFlagIsNotSet() {
        // Setting smooth on a right-angle corner of a rectangle would write a flag the outline contradicts.
        val rectangle = rectangleLines()
        assertEquals(rectangle, rectangle.withSmooth(0, true))
        assertTrue(rectangle.withSmooth(0, true).brokenSmoothNodes().isEmpty())
    }

    // ---- inserting -------------------------------------------------------------------------

    @Test
    fun addingANodeToACubicCurveKeepsTheCurveAndAddsOneNodeAndTwoHandles() {
        val c = circle()
        val result = c.withNodeInserted(0, 0.4)
        assertEquals(3, result.nodeIndex)
        assertEquals(15, result.contour.points.size)
        assertTrue(result.contour.points[3].onCurve && result.contour.points[3].smooth)
        assertTrue(deviation(c, result.contour) < 1.0, "the outline stayed put")
        assertTrue(deviation(result.contour, c) < 1.0)
    }

    @Test
    fun addingANodeToAStraightCubicSideKeepsItStraightAndAddsACorner() {
        val rect = rectangleLines()
        val result = rect.withNodeInserted(0, 0.25)
        // The side's cubic parametrisation is not uniform along the line, so t = 0.25 lands at 31, where the pointer was.
        assertEquals(Point(31, 0), result.contour.points[3].point)
        assertTrue(!result.contour.points[3].smooth)
        assertStraight(result.contour)
        assertTrue(deviation(rect, result.contour) < 1.0, "the side is still the same line (sampling spacing aside)")
    }

    @Test
    fun addingANodeToAQuadraticLineInsertsAnOnCurvePointAfterTheStart() {
        val c = Contour(listOf(on(0, 0), on(100, 0), on(100, 100)), CurveFormat.QUADRATIC)
        val last = c.withNodeInserted(2, 0.5) // the closing side from (100,100) back to (0,0)
        assertEquals(4, last.contour.points.size)
        assertEquals(Point(50, 50), last.contour.points[3].point)
        assertEquals(3, last.nodeIndex)
        val first = c.withNodeInserted(0, 0.5)
        assertEquals(Point(50, 0), first.contour.points[1].point)
        assertEquals(1, first.nodeIndex)
    }

    @Test
    fun addingANodeToAQuadraticCurveKeepsTheCurve() {
        val c = Contour(listOf(on(0, 0), off(100, 200), on(200, 0), on(200, -100), on(0, -100)), CurveFormat.QUADRATIC)
        val result = c.withNodeInserted(0, 0.3)
        assertEquals(7, result.contour.points.size)
        val expected = c.segments()[0].pointAt(0.3)
        assertEquals(Point(round(expected.x).toInt(), round(expected.y).toInt()), result.contour.points[result.nodeIndex].point)
        assertTrue(deviation(c, result.contour) < 1.0)
    }

    @Test
    fun addingANodeToAQuadraticCurveWithImpliedEndsWritesThemOutAndKeepsTheShape() {
        // on(0,0) off(0,100) off(100,100) on(100,0): the first curve ends at the implied (50,100).
        val c = Contour(listOf(on(0, 0), off(0, 100), off(100, 100), on(100, 0)), CurveFormat.QUADRATIC)
        val result = c.withNodeInserted(0, 0.5)
        assertTrue(result.contour.points.size > c.points.size)
        assertTrue(deviation(c, result.contour) < 1.0)
        assertTrue(deviation(result.contour, c) < 1.0)
        val second = c.withNodeInserted(1, 0.5)
        assertTrue(deviation(c, second.contour) < 1.0)
        assertTrue(deviation(second.contour, c) < 1.0)
    }

    @Test
    fun addingANodeRejectsAnEndParameterAndAMissingSegment() {
        assertFailsWith<IllegalArgumentException> { circle().withNodeInserted(0, 0.0) }
        assertFailsWith<IllegalArgumentException> { circle().withNodeInserted(0, 1.0) }
        assertFailsWith<IllegalArgumentException> { circle().withNodeInserted(9, 0.5) }
    }

    // ---- deleting --------------------------------------------------------------------------

    @Test
    fun deletingTheMiddleNodeOfAnArcKeepsTheCurveClose() {
        val c = circle().withNodeInserted(0, 0.5).contour // eight points per quarter pair: node 3 is the 45 degree node
        val deleted = assertNotNull(c.withNodeDeleted(3))
        assertEquals(c.points.size - 3, deleted.points.size)
        assertTrue(deviation(c, deleted) < 3.0, "the outline moved ${deviation(c, deleted)} units")
        assertTrue(deviation(deleted, c) < 3.0)
    }

    @Test
    fun deletingACornerBetweenTwoStraightSidesJoinsThemWithAStraightSide() {
        val deleted = assertNotNull(rectangleLines().withNodeDeleted(3))
        assertEquals(9, deleted.points.size)
        assertStraight(deleted)
        // The new side runs from (0,0) straight to (200,100).
        val side = deleted.segments()[0] as CurveSegment.Cubic
        assertEquals(Vec2(0.0, 0.0), side.start)
        assertEquals(Vec2(200.0, 100.0), side.end)
    }

    @Test
    fun deletingANodeOfAContourThatWouldBeLeftWithNoInkGivesNothing() {
        val triangle = Contour(listOf(on(0, 0), on(100, 0), on(0, 100)), CurveFormat.QUADRATIC)
        assertNull(triangle.withNodeDeleted(1), "two nodes joined by lines have no ink")
        val cubicTriangle = closePolylineToContour(listOf(Point(0, 0), Point(100, 0), Point(0, 100)))
        assertNull(cubicTriangle.withNodeDeleted(3))
        val twoNodeLens = Contour(listOf(on(0, 0), off(0, 60), off(100, 60), on(100, 0), off(100, -60), off(0, -60)), CurveFormat.CUBIC)
        assertNull(twoNodeLens.withNodeDeleted(0), "a cubic contour cannot go below two nodes")
    }

    @Test
    fun deletingAQuadraticNodeThatIsTheMidpointOfItsTwoControlsKeepsTheCurveExactly() {
        // on(0,0) off(0,100) on(50,100) off(100,100) on(100,0): the middle node is the midpoint of its controls.
        val c = Contour(listOf(on(0, 0), off(0, 100), on(50, 100), off(100, 100), on(100, 0), on(50, -50)), CurveFormat.QUADRATIC)
        val deleted = assertNotNull(c.withNodeDeleted(2))
        assertEquals(5, deleted.points.size)
        assertEquals(0.0, deviation(c, deleted), 0.01)
    }

    @Test
    fun deletingAQuadraticNodeBetweenTwoCurvesWithOffCentreControlsFitsOneControl() {
        val c = Contour(listOf(on(0, 0), off(10, 90), on(60, 110), off(110, 90), on(120, 0), on(60, -60)), CurveFormat.QUADRATIC)
        val deleted = assertNotNull(c.withNodeDeleted(2))
        assertEquals(4, deleted.points.size, "the node and one of its two controls go; the other is refitted")
        // One quadratic cannot follow two: the least-squares control keeps the result within a fifth of the shape's height.
        assertTrue(deviation(c, deleted) < 20.0, "deviation ${deviation(c, deleted)}")
    }

    @Test
    fun deletingAQuadraticNodeBetweenTwoLinesJustJoinsThem() {
        val c = Contour(listOf(on(0, 0), on(100, 0), on(100, 100), on(0, 100)), CurveFormat.QUADRATIC)
        val deleted = assertNotNull(c.withNodeDeleted(1))
        assertEquals(listOf(Point(0, 0), Point(100, 100), Point(0, 100)), deleted.points.map { it.point })
    }

    // ---- removing handles ------------------------------------------------------------------

    @Test
    fun removingACubicHandleRetractsItOntoItsNodeWithoutChangingThePointCount() {
        val c = circle().withHandlesRemoved(setOf(1, 5))
        assertEquals(12, c.points.size)
        assertEquals(c.points[0].point, c.points[1].point)
        assertEquals(c.points[6].point, c.points[5].point)
        assertTrue(c.isRetracted(1))
    }

    @Test
    fun removingAQuadraticControlTakesThePointOutAndLeavesAStraightSide() {
        val c = Contour(listOf(on(0, 0), off(50, 60), on(100, 0), on(100, -100), on(0, -100)), CurveFormat.QUADRATIC)
        val result = c.withHandlesRemoved(setOf(1, 0)) // an on-curve index in the set is ignored
        assertEquals(4, result.points.size)
        assertTrue(result.points.all { it.onCurve })
        assertEquals(c, c.withHandlesRemoved(setOf(0)))
    }

    // ---- bending ---------------------------------------------------------------------------

    @Test
    fun pullingACubicSegmentBendsItThroughThePulledPointKeepingHandleAngles() {
        val c = circle()
        val before = c.segments()[0] as CurveSegment.Cubic
        val target = before.pointAt(0.5) + Vec2(20.0, 20.0)
        val bent = c.withSegmentBent(0, 0.5, 20.0, 20.0)
        val after = bent.segments()[0] as CurveSegment.Cubic
        assertTrue((after.pointAt(0.5) - target).length() < 1.5, "passes through ${after.pointAt(0.5)} wanted $target")
        val d0 = (before.control1 - before.start)
        val d1 = (after.control1 - after.start)
        assertTrue(abs(d0.cross(d1)) <= 0.02 * d0.length() * d1.length(), "start handle kept its angle")
        val e0 = (before.control2 - before.end)
        val e1 = (after.control2 - after.end)
        assertTrue(abs(e0.cross(e1)) <= 0.02 * e0.length() * e1.length(), "end handle kept its angle")
        assertEquals(c.points[0], bent.points[0])
        assertEquals(c.points[3], bent.points[3])
    }

    @Test
    fun pullingAQuadraticSegmentMovesItsControlSoTheCurveFollows() {
        val c = Contour(listOf(on(0, 0), off(100, 200), on(200, 0), on(200, -100), on(0, -100)), CurveFormat.QUADRATIC)
        val target = c.segments()[0].pointAt(0.5) + Vec2(0.0, 30.0)
        val bent = c.withSegmentBent(0, 0.5, 0.0, 30.0)
        assertTrue((bent.segments()[0].pointAt(0.5) - target).length() < 1.5)
    }

    @Test
    fun pullingAStraightSideMovesBothEndsInsteadOfBendingIt() {
        val moved = rectangleLines().withSegmentBent(1, 0.5, 8.0, 0.0)
        assertEquals(Point(208, 0), moved.points[3].point)
        assertEquals(Point(208, 100), moved.points[6].point)
        assertStraight(moved)
    }

    @Test
    fun aBendThatCannotReachItsPointWithParallelHandlesStillMovesTheCurve() {
        // Both handles lie on one line, so lengths alone cannot lift the middle sideways.
        val c = Contour(listOf(on(0, 0), off(30, 0), off(70, 0), on(100, 0), off(100, 50), off(0, 50)), CurveFormat.CUBIC)
        val bent = c.withSegmentBent(0, 0.5, 0.0, 10.0)
        assertTrue(bent.segments()[0].pointAt(0.5).y > 5.0)
    }

    // ---- closest point ---------------------------------------------------------------------

    @Test
    fun theClosestPointOfALineIsItsProjection() {
        val line = CurveSegment.Line(Vec2(0.0, 0.0), Vec2(100.0, 0.0))
        val hit = line.closestTo(Vec2(30.0, 12.0))
        assertEquals(0.3, hit.t, 1e-6)
        assertEquals(12.0, hit.distance, 1e-6)
    }

    @Test
    fun theClosestPointOfACurveIsFoundBeyondTheSampleSpacing() {
        val seg = circle().segments()[0]
        val query = seg.pointAt(0.37) + Vec2(8.0, 8.0)
        val hit = seg.closestTo(query)
        assertTrue(hit.distance <= (query - seg.pointAt(0.37)).length() + 1e-6)
        assertTrue(abs(hit.t - 0.37) < 0.1)
    }

    // ---- operations that rebuild from segments ----------------------------------------------

    @Test
    fun addingExtremesKeepsSmoothFlagsAndMakesTheNewNodesSmooth() {
        // A cubic with a bulge past its end nodes in y has an interior extreme in y.
        val c =
            Contour(listOf(on(0, 0, smooth = true), off(0, 100), off(100, 100), on(100, 0), off(100, -40), off(0, -40)), CurveFormat.CUBIC)
        val result = c.withExtremesAdded()
        assertTrue(result.points.count { it.onCurve } > 2)
        assertTrue(result.points.first { it.point == Point(0, 0) && it.onCurve }.smooth, "an old node keeps its flag")
        val added = result.points.filter { it.onCurve && it.point != Point(0, 0) && it.point != Point(100, 0) }
        assertTrue(added.isNotEmpty() && added.all { it.smooth })
        assertFailsWith<IllegalArgumentException> {
            Contour(
                listOf(on(0, 0), on(1, 0), on(1, 1)),
                CurveFormat.QUADRATIC,
            ).withExtremesAdded()
        }
    }

    @Test
    fun tidyingKeepsTheSmoothFlagsOfTheNodesItLeaves() {
        val c = closePolylineToContour(listOf(Point(0, 0), Point(100, 0), Point(200, 0), Point(200, 200), Point(0, 200)))
        val flagged = c.copy(points = c.points.map { if (it.onCurve && it.point == Point(200, 200)) it.copy(smooth = true) else it })
        val result = flagged.tidied()
        assertEquals(4, result.after.onCurveEquivalent)
        assertTrue(
            result.contour.points
                .first { it.onCurve && it.point == Point(200, 200) }
                .smooth,
        )
    }

    // ---- degree elevation ------------------------------------------------------------------

    @Test
    fun elevatingAQuadraticContourGivesTheSameOutlineInCubicForm() {
        val quad = Contour(listOf(on(0, 0), off(100, 200), on(200, 0), on(200, -100), on(0, -100)), CurveFormat.QUADRATIC)
        val cubic = quad.degreeElevatedToCubic()
        assertEquals(CurveFormat.CUBIC, cubic.format)
        assertEquals(4, cubic.points.count { it.onCurve })
        assertTrue(deviation(quad, cubic) < 1.0)
        assertTrue(deviation(cubic, quad) < 1.0)
        assertEquals(quad.signedArea(), cubic.signedArea(), 100.0) // control points are rounded to whole units
    }

    @Test
    fun elevatingALineGivesTheDegenerateTripleAndACubicContourIsReturnedAsItIs() {
        val quad = Contour(listOf(on(0, 0), on(100, 0), on(100, 100)), CurveFormat.QUADRATIC)
        val cubic = quad.degreeElevatedToCubic()
        assertEquals(Point(0, 0), cubic.points[1].point)
        assertEquals(Point(100, 0), cubic.points[2].point)
        assertEquals(circle(), circle().degreeElevatedToCubic())
    }

    @Test
    fun elevatingMakesImpliedOnCurvePointsReal() {
        val quad = Contour(listOf(on(0, 0), off(0, 100), off(100, 100), on(100, 0)), CurveFormat.QUADRATIC)
        val cubic = quad.degreeElevatedToCubic()
        assertTrue(cubic.points.any { it.onCurve && it.point == Point(50, 100) })
        assertTrue(deviation(quad, cubic) < 1.0)
    }

    // ---- new contours ----------------------------------------------------------------------

    @Test
    fun aCubicPolygonIsWrittenAsLineTriplesAndAQuadraticOneAsCorners() {
        val corners = listOf(Point(0, 0), Point(100, 0), Point(100, 50))
        val cubic = polygonContour(corners, CurveFormat.CUBIC)
        assertEquals(9, cubic.points.size)
        assertEquals(Point(0, 0), cubic.points[1].point, "the first control sits on its own start")
        assertEquals(Point(100, 0), cubic.points[2].point, "the second on its own end")
        assertTrue(cubic.points.none { it.smooth })
        val quad = polygonContour(corners, CurveFormat.QUADRATIC)
        assertEquals(3, quad.points.size)
        assertTrue(quad.points.all { it.onCurve })
        assertEquals(cubic.signedArea(), quad.signedArea(), 1e-9)
        assertFailsWith<IllegalArgumentException> { polygonContour(emptyList(), CurveFormat.CUBIC) }
    }

    // ---- counting as the file does ---------------------------------------------------------

    @Test
    fun aStraightCubicSideHasNoOffCurvePointsAsTheFileWritesIt() {
        val square = polygonContour(listOf(Point(0, 0), Point(100, 0), Point(100, 100), Point(0, 100)), CurveFormat.CUBIC)
        assertEquals(8, square.count().offCurve, "the in-memory triples carry two controls per side")
        assertEquals(ContourCount(onCurveEquivalent = 4, offCurve = 0), square.writtenCount())
    }

    @Test
    fun aRealCurveKeepsBothOfItsOffCurvePointsEvenWithOneHandleRetracted() {
        val rounded =
            Contour(
                listOf(
                    on(0, 0),
                    off(0, 0),
                    off(100, 0),
                    on(100, 0),
                    off(100, 55),
                    off(55, 100),
                    on(0, 100),
                    off(0, 100),
                    off(0, 0),
                ),
                CurveFormat.CUBIC,
            )
        // Side one is a line; the arc is a curve; the last side's controls are on its own ends too.
        assertEquals(ContourCount(onCurveEquivalent = 3, offCurve = 2), rounded.writtenCount())
        val oneRetracted = Contour(listOf(on(0, 0), off(0, 0), off(60, 40), on(100, 0), off(100, 0), off(0, 0)), CurveFormat.CUBIC)
        assertEquals(2, oneRetracted.writtenCount().offCurve, "a curve with one handle on its node is still a curve")
    }

    @Test
    fun aQuadraticContourCountsTheSameEitherWayAndAGlyphSumsItsContours() {
        val quad = Contour(listOf(on(0, 0), off(0, 100), off(100, 100), on(100, 0)), CurveFormat.QUADRATIC)
        assertEquals(quad.count(), quad.writtenCount())
        val square = polygonContour(listOf(Point(0, 0), Point(10, 0), Point(10, 10)), CurveFormat.CUBIC)
        val glyph = Glyph("x", 500, listOf(square, square))
        assertEquals(GlyphCount(onCurveEquivalent = 6, offCurve = 0, contourCount = 2), glyph.writtenCount())
    }
}
