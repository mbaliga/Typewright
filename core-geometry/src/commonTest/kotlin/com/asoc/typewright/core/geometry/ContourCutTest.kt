// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.geometry

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** [cutBetween]: the contour is split along a straight line between two places, shape kept, or refused with a reason. */
class ContourCutTest {
    private fun rectangle(): Contour = polygonContour(listOf(Point(0, 0), Point(200, 0), Point(200, 100), Point(0, 100)), CurveFormat.CUBIC)

    private fun cut(result: ContourCutResult): ContourCutResult.Cut = assertIs<ContourCutResult.Cut>(result)

    @Test
    fun cuttingBetweenTwoOppositeNodesGivesTwoTrianglesThatConserveArea() {
        val rect = rectangle()
        val result = cut(rect.cutBetween(CutPlace.AtNode(0), CutPlace.AtNode(6)))
        assertEquals(9, result.first.points.size)
        assertEquals(9, result.second.points.size)
        assertEquals(rect.signedArea(), result.first.signedArea() + result.second.signedArea(), 1e-6)
        assertTrue(result.first.signedArea() > 0 && result.second.signedArea() > 0, "both pieces keep the original winding")
    }

    @Test
    fun cuttingAcrossTwoSidesInsertsTheNodesWithoutChangingTheShape() {
        val rect = rectangle()
        val result = cut(rect.cutBetween(CutPlace.OnSegment(0, 0.5), CutPlace.OnSegment(2, 0.5)))
        assertEquals(rect.signedArea(), result.first.signedArea() + result.second.signedArea(), 1e-6)
        val all = (result.first.points + result.second.points).filter { it.onCurve }.map { it.point }
        assertTrue(Point(100, 0) in all && Point(100, 100) in all, "the cut ends are real nodes on the sides")
        assertEquals(100.0 * 100.0, abs(result.first.signedArea()), 1e-6)
    }

    @Test
    fun twoSpotsOnOneStraightSideLeaveNoAreaAndASpotPlusANodeCuts() {
        val rect = rectangle()
        assertIs<ContourCutResult.Refused>(rect.cutBetween(CutPlace.OnSegment(0, 0.25), CutPlace.OnSegment(0, 0.75)))
        val mixed = cut(rect.cutBetween(CutPlace.AtNode(9), CutPlace.OnSegment(1, 0.5)))
        assertEquals(rect.signedArea(), mixed.first.signedArea() + mixed.second.signedArea(), 1e-6)
    }

    @Test
    fun twoSpotsOnTheSameBulgedSideEachLandWhereTheyWerePicked() {
        val bulged =
            Contour(
                listOf(
                    ContourPoint(Point(0, 0), true),
                    ContourPoint(Point(60, -80), false),
                    ContourPoint(Point(140, -80), false),
                    ContourPoint(Point(200, 0), true),
                    ContourPoint(Point(200, 0), false),
                    ContourPoint(Point(200, 100), false),
                    ContourPoint(Point(200, 100), true),
                    ContourPoint(Point(200, 100), false),
                    ContourPoint(Point(0, 100), false),
                    ContourPoint(Point(0, 100), true),
                    ContourPoint(Point(0, 100), false),
                    ContourPoint(Point(0, 0), false),
                ),
                CurveFormat.CUBIC,
            )
        val result = cut(bulged.cutBetween(CutPlace.OnSegment(0, 0.2), CutPlace.OnSegment(0, 0.8)))
        val total = result.first.signedArea() + result.second.signedArea()
        // Inserting a node rounds it to a whole unit, which moves the curve by at most half a unit along its length.
        assertEquals(bulged.signedArea(), total, 150.0)
        val expected = bulged.segments()[0].pointAt(0.2)
        val nodes = (result.first.points + result.second.points).filter { it.onCurve }.map { it.point.toVec2() }
        assertTrue(nodes.any { (it - expected).length() <= 1.0 }, "a node sits where t=0.2 was, not where the rescaled t would put it")
    }

    @Test
    fun smoothFlagsOfUntouchedNodesSurviveAndTheCutEndsBecomeCorners() {
        val rect = rectangle()
        val smoothed = rect.copy(points = rect.points.mapIndexed { i, p -> if (i == 3) p.copy(smooth = true) else p })
        val result = cut(smoothed.cutBetween(CutPlace.AtNode(0), CutPlace.AtNode(6)))
        val flags = (result.first.points + result.second.points).filter { it.onCurve && it.smooth }
        assertEquals(1, flags.size, "the smooth flag of node 1 is still there; the cut nodes are corners")
        assertEquals(Point(200, 0), flags.single().point)
    }

    @Test
    fun refusalsAreSpecificAndLeaveNothingHalfDone() {
        val rect = rectangle()
        assertTrue(
            assertIs<ContourCutResult.Refused>(rect.cutBetween(CutPlace.AtNode(3), CutPlace.AtNode(3))).reason.contains("same place"),
        )
        val quad =
            Contour(
                listOf(ContourPoint(Point(0, 0), true), ContourPoint(Point(9, 0), true), ContourPoint(Point(9, 9), true)),
                CurveFormat.QUADRATIC,
            )
        assertTrue(assertIs<ContourCutResult.Refused>(rect.cutBetween(CutPlace.AtNode(1), CutPlace.AtNode(6))).reason.contains("not on"))
        assertTrue(assertIs<ContourCutResult.Refused>(quad.cutBetween(CutPlace.AtNode(0), CutPlace.AtNode(1))).reason.contains("quadratic"))
        assertIs<ContourCutResult.Refused>(rect.cutBetween(CutPlace.OnSegment(9, 0.5), CutPlace.AtNode(0)))
    }

    @Test
    fun aCutBetweenAdjacentCornersOfAStraightSideLeavesNoAreaAndIsRefused() {
        val refused = assertIs<ContourCutResult.Refused>(rectangle().cutBetween(CutPlace.AtNode(0), CutPlace.AtNode(3)))
        assertTrue(refused.reason.contains("no area"), refused.reason)
    }

    @Test
    fun aCutThatWouldLeaveAConcaveShapeIsRefusedAndOneInsideIsAllowed() {
        // An L: the cut from the far corner (0,200) to (200,100) leaves the shape.
        val ell =
            polygonContour(
                listOf(Point(0, 0), Point(200, 0), Point(200, 100), Point(100, 100), Point(100, 200), Point(0, 200)),
                CurveFormat.CUBIC,
            )
        val outside = assertIs<ContourCutResult.Refused>(ell.cutBetween(CutPlace.AtNode(6), CutPlace.AtNode(15)))
        assertTrue(outside.reason.contains("cross") || outside.reason.contains("leave"), outside.reason)
        assertIs<ContourCutResult.Cut>(ell.cutBetween(CutPlace.AtNode(0), CutPlace.AtNode(9)))
    }

    @Test
    fun aCutCannotRunThroughACounterOfTheGlyph() {
        val outer = polygonContour(listOf(Point(0, 0), Point(300, 0), Point(300, 300), Point(0, 300)), CurveFormat.CUBIC)
        val hole = polygonContour(listOf(Point(100, 100), Point(100, 200), Point(200, 200), Point(200, 100)), CurveFormat.CUBIC)
        val refused = assertIs<ContourCutResult.Refused>(outer.cutBetween(CutPlace.AtNode(0), CutPlace.AtNode(6), others = listOf(hole)))
        assertTrue(refused.reason.contains("cross"))
        assertIs<ContourCutResult.Cut>(outer.cutBetween(CutPlace.AtNode(0), CutPlace.AtNode(6)))
    }

    @Test
    fun aCutThatTouchesAReflexCornerOfItsOwnContourIsRefused() {
        // A notch cut down from the top whose tip sits exactly at y = 100: the straight cut across at y = 100 grazes the tip.
        val notched =
            polygonContour(
                listOf(Point(0, 0), Point(300, 0), Point(300, 200), Point(120, 200), Point(100, 100), Point(80, 200), Point(0, 200)),
                CurveFormat.CUBIC,
            )
        val refused = assertIs<ContourCutResult.Refused>(notched.cutBetween(CutPlace.OnSegment(1, 0.5), CutPlace.OnSegment(6, 0.5)))
        assertTrue(refused.reason.contains("node"), refused.reason)
        // Moved off the tip, the same kind of cut is clear and is made.
        assertIs<ContourCutResult.Cut>(notched.cutBetween(CutPlace.OnSegment(1, 0.25), CutPlace.OnSegment(6, 0.75)))
    }

    @Test
    fun aCutThatTouchesTheCornerOfACounterIsRefused() {
        val outer = polygonContour(listOf(Point(0, 0), Point(300, 0), Point(300, 300), Point(0, 300)), CurveFormat.CUBIC)
        // The counter's corner (100, 100) lies exactly on the diagonal; the counter lies wholly on one side of it.
        val hole = polygonContour(listOf(Point(100, 100), Point(100, 150), Point(150, 150)), CurveFormat.CUBIC)
        val refused = assertIs<ContourCutResult.Refused>(outer.cutBetween(CutPlace.AtNode(0), CutPlace.AtNode(6), others = listOf(hole)))
        assertTrue(refused.reason.contains("node"), refused.reason)
    }
}
