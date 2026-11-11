// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.geometry

import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
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

private const val KAPPA = 0.5523

/**
 * A rounded rectangle in cubic form: straight sides (handles on their own ends) joined by quarter
 * curves, every one of its eight nodes smooth. Every node is a line-to-curve junction, the case the
 * smooth flag most often has to keep true. Node 0 is the start of the bottom side, node 3 the start of
 * the bottom-right corner, and so on round counter-clockwise.
 */
private fun roundedRectangle(
    x0: Int = 0,
    y0: Int = 0,
    x1: Int = 300,
    y1: Int = 200,
    r: Int = 60,
): Contour {
    val k = (r * KAPPA).toInt()
    return Contour(
        listOf(
            on(x0 + r, y0, true),
            off(x0 + r, y0),
            off(x1 - r, y0),
            on(x1 - r, y0, true),
            off(x1 - r + k, y0),
            off(x1, y0 + r - k),
            on(x1, y0 + r, true),
            off(x1, y0 + r),
            off(x1, y1 - r),
            on(x1, y1 - r, true),
            off(x1, y1 - r + k),
            off(x1 - r + k, y1),
            on(x1 - r, y1, true),
            off(x1 - r, y1),
            off(x0 + r, y1),
            on(x0 + r, y1, true),
            off(x0 + r - k, y1),
            off(x0, y1 - r + k),
            on(x0, y1 - r, true),
            off(x0, y1 - r),
            off(x0, y0 + r),
            on(x0, y0 + r, true),
            off(x0, y0 + r - k),
            off(x0 + r - k, y0),
        ),
        CurveFormat.CUBIC,
    )
}

/** A circle-like cubic with four smooth nodes, radius 200 about the origin, counter-clockwise from the east. */
private fun roundCircle(): Contour {
    val r = 200
    val k = 110
    return Contour(
        listOf(
            on(r, 0, true),
            off(r, k),
            off(k, r),
            on(0, r, true),
            off(-k, r),
            off(-r, k),
            on(-r, 0, true),
            off(-r, -k),
            off(-k, -r),
            on(0, -r, true),
            off(k, -r),
            off(r, -k),
        ),
        CurveFormat.CUBIC,
    )
}

/** A TrueType-style rounded square whose four nodes each sit between two lone controls in line with them, every node smooth. */
private fun quadraticSquare(): Contour =
    Contour(
        listOf(
            on(50, 0, true),
            off(100, 0),
            on(100, 50, true),
            off(100, 100),
            on(50, 100, true),
            off(0, 100),
            on(0, 50, true),
            off(0, 0),
        ),
        CurveFormat.QUADRATIC,
    )

private fun Contour.point(index: Int): Vec2 = points[index].point.toVec2()

/** The vector from node [node] to the point [other]. */
private fun Contour.from(
    node: Int,
    other: Int,
): Vec2 = point(other) - point(node)

private fun Vec2.unit(): Vec2 = this * (1.0 / length())

/**
 * The independent check the property test runs after every edit: the way the outline arrives at each
 * smooth node and the way it leaves are one line. Written against the decoded segments, not against
 * the library's own [smoothHolds], so a mistake in the library's reading cannot excuse itself.
 */
private fun directionalBreaks(contour: Contour): List<String> {
    val problems = mutableListOf<String>()
    val layout = contour.segmentLayout()
    for (index in contour.points.indices) {
        val point = contour.points[index]
        if (!point.onCurve || !point.smooth) continue
        val arriving = layout.filter { it.endPoint == index }
        val leaving = layout.filter { it.startPoint == index }
        if (arriving.size != 1 || leaving.size != 1) continue
        val back =
            when (val s = arriving.single().segment) {
                is CurveSegment.Line -> listOf(s.start - s.end)
                is CurveSegment.Quadratic -> listOf(s.control - s.end, s.start - s.end)
                is CurveSegment.Cubic -> listOf(s.control2 - s.end, s.control1 - s.end, s.start - s.end)
            }.firstOrNull { it.length() > 1e-9 }
        val forward =
            when (val s = leaving.single().segment) {
                is CurveSegment.Line -> listOf(s.end - s.start)
                is CurveSegment.Quadratic -> listOf(s.control - s.start, s.end - s.start)
                is CurveSegment.Cubic -> listOf(s.control1 - s.start, s.control2 - s.start, s.end - s.start)
            }.firstOrNull { it.length() > 1e-9 }
        if (back == null || forward == null) continue
        val shorterTipOffLine = abs(back.cross(forward)) / max(back.length(), forward.length())
        if (shorterTipOffLine >
            SMOOTH_TOLERANCE_UNITS
        ) {
            problems += "node $index: the shorter side is $shorterTipOffLine units off the line"
        }
        if (back.dot(forward) >= 0.0) problems += "node $index: the curve turns back on itself"
    }
    return problems
}

class ContourSmoothTest {
    // ---- the definition -------------------------------------------------------------------------------

    @Test
    fun everyNodeOfARoundedRectangleAndACircleIsSmoothAndTheDefinitionSaysSo() {
        for (contour in listOf(roundedRectangle(), roundCircle(), quadraticSquare())) {
            assertEquals(emptyList(), contour.brokenSmoothNodes())
            assertEquals(emptyList(), directionalBreaks(contour))
            for (i in contour.points.indices.filter { contour.points[it].onCurve }) assertTrue(contour.smoothHolds(i), "node $i")
        }
    }

    @Test
    fun aCornerNodeHoldsOnlyWhenItsSidesRunOnAsOneLine() {
        // The right-angle corner of a plain rectangle does not hold; a node in the middle of a side does.
        val corners =
            Contour(
                listOf(
                    on(0, 0),
                    off(0, 0),
                    off(200, 0),
                    on(200, 0),
                    off(200, 0),
                    off(200, 100),
                    on(200, 100),
                    off(200, 100),
                    off(0, 100),
                    on(0, 100),
                    off(0, 100),
                    off(0, 0),
                ),
                CurveFormat.CUBIC,
            )
        assertTrue(!corners.smoothHolds(0), "two straight sides at a right angle")
        val withMiddle = corners.withNodeInserted(0, 0.5).contour
        assertTrue(withMiddle.smoothHolds(3), "a node in the middle of a straight side")
    }

    @Test
    fun aCuspIsNotSmoothEvenWhenTheTwoSidesAreOnOneLine() {
        // Handles on the same side of the node, both pointing the same way: collinear, but the curve turns back.
        val cusp = Contour(listOf(on(0, 0, true), off(50, 0), off(150, 0), on(200, 0), off(250, 0), off(300, 0)), CurveFormat.CUBIC)
        // Node 0 leaves towards +x and arrives (from the last handle at (300, 0)) from +x too; node 3 has one handle each side.
        assertTrue(!cusp.smoothHolds(0))
        assertTrue(cusp.smoothHolds(3))
    }

    @Test
    fun aHandlePulledInToNothingLeavesTheDirectionToTheFarControl() {
        // Node 0's out handle sits on it; the curve still leaves along its other control, which is in line with the in handle.
        val c =
            Contour(
                listOf(
                    on(0, 0, true),
                    off(0, 0),
                    off(100, 100),
                    on(200, 100),
                    off(300, 100),
                    off(300, 0),
                    on(100, -100),
                    off(-50, -50),
                    off(-100, -100),
                ),
                CurveFormat.CUBIC,
            )
        val tangent = (c.point(2) - c.point(0)).unit()
        val inHandle = c.point(8) - c.point(0)
        assertEquals(0.0, abs(tangent.cross(inHandle.unit())), 1e-9, "the fixture's in handle is in line with the far control")
        assertTrue(c.smoothHolds(0))
    }

    @Test
    fun smoothHoldsRejectsAnOffCurvePoint() {
        assertFailsWith<IllegalArgumentException> { roundCircle().smoothHolds(1) }
    }

    @Test
    fun brokenSmoothNodesNamesTheNodesWhoseFlagTheOutlineContradicts() {
        val lie = roundCircle().copy(points = roundCircle().points.mapIndexed { i, p -> if (i == 1) p.copy(point = Point(260, 90)) else p })
        assertEquals(listOf(0), lie.brokenSmoothNodes())
        assertTrue(roundCircle().brokenSmoothNodes().isEmpty())
    }

    // ---- moving: the line-to-curve junction -------------------------------------------------------------

    @Test
    fun movingTheBottomRightNodeOfARoundedRectangleUpTurnsItsCornerHandleOntoTheSlantedSide() {
        val before = roundedRectangle()
        // Node 3 is (240, 0); its corner handle is point 4. Drag the node up 30 units.
        val after = before.withSelectionMoved(setOf(3), 0, 30)
        assertEquals(Point(240, 30), after.points[3].point)
        // The bottom side now runs from node 0 (60, 0) to (240, 30): its direction is (180, 30).
        val side = Vec2(180.0, 30.0).unit()
        val handle = after.from(3, 4)
        assertTrue(
            abs(side.cross(handle)) <= SMOOTH_TOLERANCE_UNITS,
            "the corner handle's tip is ${abs(side.cross(handle))} units off the slanted side",
        )
        assertTrue(side.dot(handle) > 0.0, "and points on past the node, not back along the side")
        assertEquals(before.from(3, 4).length(), handle.length(), 1.0, "it keeps its length")
        assertTrue(after.points[3].smooth)
        assertEquals(emptyList(), after.brokenSmoothNodes())
        assertEquals(emptyList(), directionalBreaks(after))
    }

    @Test
    fun theNodeAtTheOtherEndOfATiltedSideTurnsItsHandleToo() {
        val after = roundedRectangle().withSelectionMoved(setOf(3), 0, 30)
        // Node 0 (60, 0) is the other end of the slanted side; its handle (the last curve handle, point 23) must follow the side.
        val side = Vec2(180.0, 30.0).unit()
        val handle = after.from(0, 23)
        assertTrue(
            abs(side.cross(handle)) <= SMOOTH_TOLERANCE_UNITS,
            "the in handle's tip is ${abs(side.cross(handle))} units off the side",
        )
        assertTrue(side.dot(handle) < 0.0, "the in handle points away from the side")
        assertEquals(emptyList(), after.brokenSmoothNodes())
    }

    @Test
    fun nudgingAndTypingAPositionAreTheSameMoveSoTheyKeepTheSameFlag() {
        // An arrow-key nudge of one unit and a typed position are both withSelectionMoved; neither may leave a lie behind.
        var c = roundedRectangle()
        repeat(7) { c = c.withSelectionMoved(setOf(3), 1, 1) }
        assertEquals(emptyList(), c.brokenSmoothNodes())
        assertEquals(emptyList(), directionalBreaks(c))
        val typed = roundedRectangle().withSelectionMoved(setOf(6), -9, 0)
        assertEquals(emptyList(), directionalBreaks(typed))
    }

    @Test
    fun movingASmoothNodeOfACircleCarriesBothHandlesAndStaysSmoothWithoutTurningAnything() {
        val moved = roundCircle().withSelectionMoved(setOf(3), 17, -23)
        assertEquals(roundCircle().from(3, 4), moved.from(3, 4))
        assertEquals(roundCircle().from(3, 2), moved.from(3, 2))
        assertTrue(moved.points[3].smooth)
    }

    @Test
    fun movingAHandleAndNotItsNodeTurnsThePartnerHandleNotTheHandleTheEditMoved() {
        // Point 1 and the circle's other points move together as a loose selection: the node's partner handle swings to stay in line.
        val moved = roundCircle().withSelectionMoved(setOf(1), 40, 0)
        assertEquals(Point(240, 110), moved.points[1].point)
        assertEquals(emptyList(), moved.brokenSmoothNodes())
        assertEquals(roundCircle().from(0, 11).length(), moved.from(0, 11).length(), 1.0)
    }

    @Test
    fun aMoveThatNothingCanKeepSmoothMakesTheNodeACorner() {
        // The middle node of a three-node run of straight sides cannot be moved off the line and stay smooth: it has no handle to turn.
        val run = rectangleOfStraightSides().withNodeInserted(0, 0.5).contour.withSmooth(3, true)
        assertTrue(run.points[3].smooth)
        val moved = run.withSelectionMoved(setOf(3), 0, 40)
        assertTrue(!moved.points[3].smooth, "no handle of its own can follow, so the flag goes")
        assertEquals(Point(100, 40), moved.points[3].point)
        assertEquals(emptyList(), moved.brokenSmoothNodes())
    }

    private fun rectangleOfStraightSides(): Contour {
        val corners = listOf(Point(0, 0), Point(200, 0), Point(200, 100), Point(0, 100))
        val points = mutableListOf<ContourPoint>()
        for (i in corners.indices) {
            points += ContourPoint(corners[i], onCurve = true)
            points += ContourPoint(corners[i], onCurve = false)
            points += ContourPoint(corners[(i + 1) % 4], onCurve = false)
        }
        return Contour(points, CurveFormat.CUBIC)
    }

    // ---- moving a handle ------------------------------------------------------------------------------------

    @Test
    fun aHandleAtALineToCurveJunctionSlidesAlongTheLineAndNeverLeavesIt() {
        val c = roundedRectangle()
        // Handle 4 of node 3 leaves along the bottom side's line (y = 0). Drag it right and well up: it may only go right.
        val moved = c.withHandleMoved(4, 25, 80)
        assertEquals(Point(240 + 33 + 25, 0), moved.points[4].point)
        assertEquals(emptyList(), moved.brokenSmoothNodes())
        assertTrue(moved.points[3].smooth)
    }

    @Test
    fun aHandleDraggedBackAcrossItsNodeStopsAtTheNodeInsteadOfCrossingTheLine() {
        val moved = roundedRectangle().withHandleMoved(4, -200, 0)
        assertEquals(Point(240, 0), moved.points[4].point, "retracted onto the node, on the right side of it")
        assertEquals(emptyList(), moved.brokenSmoothNodes())
    }

    @Test
    fun holdingAltWhileDraggingAHandleAtAJunctionLetsItGoAndMakesTheNodeACorner() {
        val moved = roundedRectangle().withHandleMoved(4, 25, 80, keepSmooth = false)
        assertEquals(Point(240 + 33 + 25, 80), moved.points[4].point)
        assertTrue(!moved.points[3].smooth)
        assertEquals(emptyList(), moved.brokenSmoothNodes())
    }

    @Test
    fun draggingAHandleOfACircleNodeStillSwingsItsPartner() {
        val moved = roundCircle().withHandleMoved(1, 60, 0)
        assertEquals(emptyList(), moved.brokenSmoothNodes())
        assertEquals(emptyList(), directionalBreaks(moved))
    }

    // ---- making smooth ------------------------------------------------------------------------------------------

    @Test
    fun makingALineToCurveCornerSmoothTurnsTheCurveHandleOntoTheLine() {
        val tilted = roundedRectangle().withSmooth(3, false).withHandleMoved(4, 0, 20, keepSmooth = false)
        assertTrue(!tilted.smoothHolds(3))
        val smooth = tilted.withSmooth(3, true)
        assertTrue(smooth.points[3].smooth)
        assertEquals(0, smooth.points[4].point.y, "the handle is back on the bottom side's line")
        assertEquals(tilted.from(3, 4).length(), smooth.from(3, 4).length(), 1.0, "and the same length")
        assertEquals(emptyList(), smooth.brokenSmoothNodes())
    }

    @Test
    fun aNodeBetweenTwoLoneQuadraticControlsThatAreNotInLineCannotBeMadeSmooth() {
        val bent =
            quadraticSquare().withSmooth(2, false).let {
                it.copy(
                    points =
                        it.points.mapIndexed { i, p ->
                            if (i ==
                                3
                            ) {
                                p.copy(point = Point(160, 100))
                            } else {
                                p
                            }
                        },
                )
            }
        assertEquals(bent, bent.withSmooth(2, true), "the controls belong to the segments on both sides, so nothing could be turned")
    }

    // ---- deleting, adding, bending, reversing -------------------------------------------------------------------

    @Test
    fun deletingTheNodeBesideAJunctionKeepsTheJunctionSmooth() {
        val c = roundedRectangle()
        // Node 6 (300, 60) is between the corner curve and the right side. Delete node 9 (300, 140): the right side then ends at the top-right curve.
        val deleted = assertNotNull(c.withNodeDeleted(9))
        assertEquals(emptyList(), deleted.brokenSmoothNodes())
        assertEquals(emptyList(), directionalBreaks(deleted))
    }

    @Test
    fun deletingABendBetweenTwoStraightSidesTurnsTheSmoothNeighboursHandlesOntoTheNewLine() {
        // Roughen the rounded rectangle so a bend sits in its bottom side: node X between two straight sides, then delete it.
        val bent = roundedRectangle().withNodeInserted(0, 0.5).contour // a corner node in the middle of the bottom side
        val bump = bent.withSelectionMoved(setOf(3), 0, 25) // lifted: the side is now two slanted lines
        assertEquals(emptyList(), bump.brokenSmoothNodes())
        val straightened = assertNotNull(bump.withNodeDeleted(3))
        assertEquals(emptyList(), straightened.brokenSmoothNodes())
        assertEquals(emptyList(), directionalBreaks(straightened))
    }

    @Test
    fun addingANodeToACurveMakesASmoothNodeThatIsInLineToTheUnitWhereverItIsAdded() {
        for (segment in 0 until 4) {
            for (step in 1..19) {
                val t = step / 20.0
                val inserted = roundCircle().withNodeInserted(segment, t)
                assertTrue(inserted.contour.points[inserted.nodeIndex].smooth)
                assertEquals(emptyList(), directionalBreaks(inserted.contour), "segment $segment at $t")
            }
        }
    }

    @Test
    fun addingANodeToAQuadraticCurveMakesASmoothNodeThatIsInLineToTheUnit() {
        val c = Contour(listOf(on(0, 0), off(53, 87), on(160, 40), on(80, -90)), CurveFormat.QUADRATIC)
        for (step in 1..19) {
            val inserted = c.withNodeInserted(0, step / 20.0)
            assertEquals(emptyList(), directionalBreaks(inserted.contour), "at ${step / 20.0}")
        }
    }

    @Test
    fun bendingASegmentNextToAJunctionKeepsEveryNodeSmooth() {
        val bent = roundedRectangle().withSegmentBent(1, 0.5, 14.0, -18.0) // the bottom-right corner
        assertEquals(emptyList(), bent.brokenSmoothNodes())
        assertEquals(emptyList(), directionalBreaks(bent))
        val line = roundedRectangle().withSegmentBent(0, 0.5, 0.0, 20.0) // bending a straight side moves its two end nodes
        assertEquals(emptyList(), directionalBreaks(line))
    }

    @Test
    fun bendingAQuadraticSegmentThatMovesASharedControlMakesTheNodesItUpsetCorners() {
        val bent = quadraticSquare().withSegmentBent(1, 0.5, 30.0, 0.0)
        assertEquals(emptyList(), bent.brokenSmoothNodes())
        assertTrue(!bent.points[2].smooth, "the node whose control moved off the line lost its flag")
        assertTrue(bent.points[4].smooth, "the node whose controls are still in line kept it")
        assertTrue(bent.points[0].smooth && bent.points[6].smooth, "and the others kept theirs")
    }

    @Test
    fun deletingAHandleOfASmoothNodeMakesItACornerAndMovesNothing() {
        val c = roundCircle().withHandlesRemoved(setOf(1))
        assertTrue(!c.points[0].smooth, "the node was in line with the handle that is now gone")
        assertEquals(emptyList(), c.brokenSmoothNodes())
        assertEquals(roundCircle().points[11].point, c.points[11].point)
        assertTrue(c.points[3].smooth, "a node the deletion does not touch keeps its flag")
    }

    @Test
    fun deletingTheHandlesOfAStraightenedSideKeepsTheNodesThatStillHold() {
        // Both handles of the circle's first segment go; node 0 and node 3 each lose a side and are corners, the others are untouched.
        val c = roundCircle().withHandlesRemoved(setOf(1, 2))
        assertEquals(emptyList(), c.brokenSmoothNodes())
        assertTrue(c.points[6].smooth && c.points[9].smooth)
        val quad = quadraticSquare().withHandlesRemoved(setOf(1))
        assertEquals(emptyList(), quad.brokenSmoothNodes())
    }

    @Test
    fun reversingAContourKeepsEverySmoothNodeInLine() {
        for (contour in listOf(roundedRectangle(), roundCircle(), quadraticSquare())) {
            val reversed = contour.reverse()
            assertEquals(contour.points.count { it.smooth }, reversed.points.count { it.smooth })
            assertEquals(emptyList(), reversed.brokenSmoothNodes())
            assertEquals(emptyList(), directionalBreaks(reversed))
        }
    }

    @Test
    fun addingExtremesAndTidyingKeepTheFlagsTrue() {
        val tilted = roundCircle().withSelectionMoved(setOf(3), 30, 12)
        assertEquals(emptyList(), directionalBreaks(tilted.withExtremesAdded()))
        assertEquals(emptyList(), directionalBreaks(tilted.tidied().contour))
        assertEquals(emptyList(), directionalBreaks(roundedRectangle().withExtremesAdded()))
    }

    @Test
    fun elevatingAQuadraticContourKeepsItsSmoothNodesInLine() {
        assertEquals(emptyList(), directionalBreaks(quadraticSquare().degreeElevatedToCubic()))
    }

    // ---- withSmoothKept ------------------------------------------------------------------------------------------

    @Test
    fun aWholeContourTransformThatRoundsEveryPointIsMendedBySmoothKept() {
        val before = roundedRectangle(0, 0, 301, 203, 61)
        // A shear and a 0.77 scale, rounded to whole units point by point, as a transform would write it.
        val after =
            before.copy(
                points =
                    before.points.map { p ->
                        val x = p.point.x * 0.77 + p.point.y * 0.31
                        val y = p.point.y * 0.77
                        p.copy(point = Point(kotlin.math.round(x).toInt(), kotlin.math.round(y).toInt()))
                    },
            )
        val kept = after.withSmoothKept(before)
        assertEquals(emptyList(), directionalBreaks(kept))
        assertEquals(emptyList(), kept.brokenSmoothNodes())
    }

    @Test
    fun smoothKeptLeavesANodeAloneThatWasAlreadyOutOfLineBeforeTheEdit() {
        val lying =
            roundCircle().copy(
                points =
                    roundCircle().points.mapIndexed { i, p ->
                        if (i ==
                            1
                        ) {
                            p.copy(point = Point(260, 90))
                        } else {
                            p
                        }
                    },
            )
        assertEquals(listOf(0), lying.brokenSmoothNodes())
        val moved = lying.withSelectionMoved(setOf(6), 5, 5)
        assertEquals(lying.points[1], moved.points[1], "the lie already in the file is not this edit's to mend")
        assertTrue(moved.points[0].smooth)
    }

    @Test
    fun smoothKeptRequiresTheSameContourBeforeAndAfter() {
        assertFailsWith<IllegalArgumentException> { roundCircle().withSmoothKept(roundedRectangle()) }
    }

    // ---- the property test --------------------------------------------------------------------------------------

    /** One random edit, described for the failure message; the contour it makes, or null when the edit did not apply. */
    private class Edit(
        val description: String,
        val contour: Contour?,
    )

    private fun randomEdit(
        contour: Contour,
        random: Random,
    ): Edit {
        val nodes = contour.points.indices.filter { contour.points[it].onCurve }
        val handles = contour.points.indices.filter { !contour.points[it].onCurve }
        val segments = contour.segmentLayout().size

        fun delta() = random.nextInt(-45, 46)
        return when (random.nextInt(13)) {
            0, 1 -> {
                val node = nodes.random(random)
                val dx = delta()
                val dy = delta()
                Edit("move node $node by $dx,$dy", contour.withSelectionMoved(setOf(node), dx, dy))
            }

            2 -> {
                val picked = (1..random.nextInt(1, 5)).map { contour.points.indices.random(random) }.toSet()
                val dx = delta()
                val dy = delta()
                Edit("move points $picked by $dx,$dy", contour.withSelectionMoved(picked, dx, dy))
            }

            3, 4 -> {
                if (handles.isEmpty()) return Edit("no handle", null)
                val handle = handles.random(random)
                val dx = delta()
                val dy = delta()
                val keep = random.nextInt(5) != 0
                Edit("move handle $handle by $dx,$dy keepSmooth=$keep", contour.withHandleMoved(handle, dx, dy, keep))
            }

            5 -> {
                val node = nodes.random(random)
                val smooth = random.nextBoolean()
                Edit("smooth node $node = $smooth", contour.withSmooth(node, smooth))
            }

            6 -> {
                val segment = random.nextInt(segments)
                val t = random.nextInt(8, 93) / 100.0
                Edit("insert into segment $segment at $t", contour.withNodeInserted(segment, t).contour)
            }

            7 -> {
                val node = nodes.random(random)
                Edit("delete node $node", contour.withNodeDeleted(node))
            }

            8 -> {
                if (handles.isEmpty()) return Edit("no handle", null)
                val picked = (1..random.nextInt(1, 4)).map { handles.random(random) }.toSet()
                Edit("remove handles $picked", contour.withHandlesRemoved(picked))
            }

            9 -> {
                val segment = random.nextInt(segments)
                val t = random.nextInt(15, 86) / 100.0
                val dx = delta().toDouble()
                val dy = delta().toDouble()
                Edit("bend segment $segment at $t by $dx,$dy", contour.withSegmentBent(segment, t, dx, dy))
            }

            10 -> {
                Edit("reverse", contour.reverse())
            }

            11 -> {
                if (contour.format != CurveFormat.CUBIC) return Edit("not cubic", null)
                Edit("add extremes", contour.withExtremesAdded())
            }

            else -> {
                if (contour.format != CurveFormat.CUBIC) return Edit("not cubic", null)
                Edit("tidy", contour.tidied().contour)
            }
        }
    }

    @Test
    fun afterEveryOneOfSeveralHundredRandomEditsEverySmoothNodeIsStillInLine() {
        val starts = listOf("rounded rectangle" to roundedRectangle(), "circle" to roundCircle(), "quadratic square" to quadraticSquare())
        var applied = 0
        for ((name, start) in starts) {
            for (seed in 1..60) {
                val random = Random(seed * 7919 + name.length)
                var contour = start
                val log = mutableListOf<String>()
                repeat(60) {
                    val edit = randomEdit(contour, random)
                    val next = edit.contour
                    if (next != null) {
                        log += edit.description
                        applied++
                        val problems = directionalBreaks(next)
                        assertEquals(
                            emptyList(),
                            problems,
                            "$name, seed $seed, after ${log.size} edits (${edit.description}); history: ${log.takeLast(6)}; " +
                                "before: ${contour.points.map {
                                    "${it.point.x},${it.point.y}${if (it.smooth) {
                                        "s"
                                    } else if (it.onCurve) {
                                        "c"
                                    } else {
                                        "o"
                                    }}"
                                }}",
                        )
                        assertEquals(emptyList(), next.brokenSmoothNodes(), "$name, seed $seed, after ${edit.description}")
                        contour = if (next.points.count { it.onCurve } < 3) start else next
                    }
                }
            }
        }
        assertTrue(applied > 400, "the run applied only $applied edits")
    }
}
