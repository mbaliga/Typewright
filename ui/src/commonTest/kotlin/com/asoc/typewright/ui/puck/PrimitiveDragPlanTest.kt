// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.puck

import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.core.geometry.writtenCount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What a drag with the Primitives tool makes: real contours with the node counts a type designer draws, sized by the drag and snapped. */
class PrimitiveDragPlanTest {
    private fun plan(
        kind: PrimitiveKind,
        from: Vec2,
        to: Vec2,
        snap: (Vec2) -> Vec2 = { it },
    ): PrimitiveShape = assertNotNull(PrimitiveDragPlan.plan(kind, from, to, snap), "a shape for $kind")

    private fun onCurve(shape: PrimitiveShape): Set<Point> =
        shape.contour.points
            .filter { it.onCurve }
            .map { it.point }
            .toSet()

    @Test
    fun aCircleIsFourOnCurvePointsOnItsExtremesAndNoOthers() {
        val circle = plan(PrimitiveKind.CIRCLE, Vec2(100.0, 0.0), Vec2(600.0, 500.0))
        val count = circle.contour.writtenCount()
        assertEquals(4, count.onCurveEquivalent)
        assertEquals(8, count.offCurve)
        assertEquals(setOf(Point(600, 250), Point(350, 500), Point(100, 250), Point(350, 0)), onCurve(circle))
    }

    @Test
    fun aCircleTakesTheLargerSideAndGrowsTowardTheDragEvenBackwards() {
        val wide = plan(PrimitiveKind.CIRCLE, Vec2(0.0, 0.0), Vec2(400.0, 100.0))
        assertEquals(400, wide.maxX - wide.minX)
        assertEquals(400, wide.maxY - wide.minY)
        val backwards = plan(PrimitiveKind.CIRCLE, Vec2(500.0, 500.0), Vec2(300.0, 100.0))
        assertEquals(400, backwards.maxY - backwards.minY)
        assertEquals(100, backwards.minX)
        assertEquals(500, backwards.maxX)
        assertEquals(100, backwards.minY)
        assertEquals(500, backwards.maxY)
    }

    @Test
    fun anEllipseAndARectangleFillTheDraggedBoxInEitherDirection() {
        val ellipse = plan(PrimitiveKind.ELLIPSE, Vec2(400.0, 300.0), Vec2(100.0, 0.0))
        assertEquals(setOf(Point(400, 150), Point(250, 300), Point(100, 150), Point(250, 0)), onCurve(ellipse))
        val rect = plan(PrimitiveKind.RECTANGLE, Vec2(100.0, 500.0), Vec2(300.0, 0.0))
        assertEquals(setOf(Point(100, 0), Point(300, 0), Point(300, 500), Point(100, 500)), onCurve(rect))
        assertEquals(0, rect.contour.writtenCount().offCurve, "straight sides are lines")
    }

    @Test
    fun aRoundedRectangleIsEightNodesWithTheRadiusHeldToTheBox() {
        val rounded = plan(PrimitiveKind.ROUNDED_RECTANGLE, Vec2(0.0, 0.0), Vec2(300.0, 200.0))
        assertEquals(8, onCurve(rounded).size)
        assertTrue(Point(24, 0) in onCurve(rounded) && Point(0, 24) in onCurve(rounded))
        val small = plan(PrimitiveKind.ROUNDED_RECTANGLE, Vec2(0.0, 0.0), Vec2(30.0, 20.0))
        assertTrue(Point(10, 0) in onCurve(small), "a 20 unit tall box can round no more than 10")
    }

    @Test
    fun theSnapPullsBothCornersSoTheShapeLandsOnTheMetricLines() {
        // Snap heights within 8 units onto 0 (baseline) and 500 (x-height).
        val snap: (Vec2) -> Vec2 = { v ->
            val y = listOf(0.0, 500.0).firstOrNull { kotlin.math.abs(it - v.y) <= 8.0 } ?: v.y
            Vec2(v.x, y)
        }
        val rect = plan(PrimitiveKind.RECTANGLE, Vec2(100.0, 5.0), Vec2(300.0, 496.0), snap)
        assertEquals(0, rect.minY)
        assertEquals(500, rect.maxY)
        val circle = plan(PrimitiveKind.CIRCLE, Vec2(100.0, 3.0), Vec2(560.0, 497.0), snap)
        assertEquals(0, circle.minY)
        assertEquals(500, circle.maxY)
        assertEquals(500, circle.maxX - circle.minX, "a snapped height decides a circle's diameter")
    }

    @Test
    fun aDragThatHasNotGoneFarEnoughMakesNothingAndOtherKindsAreNotOnTheSheet() {
        assertNull(PrimitiveDragPlan.plan(PrimitiveKind.RECTANGLE, Vec2(0.0, 0.0), Vec2(1.0, 300.0)))
        assertNull(PrimitiveDragPlan.plan(PrimitiveKind.ELLIPSE, Vec2(0.0, 0.0), Vec2(300.0, 1.0)))
        assertNull(PrimitiveDragPlan.plan(PrimitiveKind.STEM, Vec2(0.0, 0.0), Vec2(300.0, 300.0)))
        assertNull(PrimitiveDragPlan.plan(PrimitiveKind.BOWL, Vec2(0.0, 0.0), Vec2(300.0, 300.0)))
        assertEquals(
            listOf(PrimitiveKind.CIRCLE, PrimitiveKind.ELLIPSE, PrimitiveKind.RECTANGLE, PrimitiveKind.ROUNDED_RECTANGLE),
            PrimitiveKind.ON_SHEET,
        )
    }

    @Test
    fun theReadoutNamesTheShapeAndItsBox() {
        assertEquals("rounded rectangle 300 × 200", plan(PrimitiveKind.ROUNDED_RECTANGLE, Vec2(0.0, 0.0), Vec2(300.0, 200.0)).text)
    }
}
