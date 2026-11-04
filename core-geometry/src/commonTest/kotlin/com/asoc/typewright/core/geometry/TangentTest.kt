// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.geometry

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** [withHandleTangentToNeighbour] and [withHandleTangentToLine]: a handle turned in line with a side or a chosen line, or refused with a reason. */
class TangentTest {
    /** A 100 square with the outgoing handle of the corner (100, 0) pulled out to (130, 30): node 3, incoming side straight along y = 0. */
    private fun bent(): Contour {
        val square = polygonContour(listOf(Point(0, 0), Point(100, 0), Point(100, 100), Point(0, 100)), CurveFormat.CUBIC)
        return square.copy(points = square.points.mapIndexed { i, p -> if (i == 4) p.copy(point = Point(130, 30)) else p })
    }

    private fun done(result: TangentResult): Contour = assertIs<TangentResult.Done>(result).contour

    private fun length(
        a: Point,
        b: Point,
    ): Double = sqrt(((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y)).toDouble())

    @Test
    fun aHandleTurnsInLineWithTheStraightSideOnTheOtherSideAndKeepsItsLength() {
        val before = bent()
        val after = done(before.withHandleTangentToNeighbour(4))
        val node = after.points[3].point
        val handle = after.points[4].point
        // The side arrives along y = 0 from the left, so the handle leaves along y = 0 to the right.
        assertEquals(0, handle.y)
        assertTrue(handle.x > node.x)
        assertEquals(length(node, before.points[4].point), length(node, handle), 1.0)
        assertTrue(after.points[3].smooth, "a node whose sides are now in line is smooth")
        assertTrue(after.smoothHolds(3))
        // Nothing but the handle (and the flag) changed.
        assertEquals(listOf(4), before.points.indices.filter { before.points[it].point != after.points[it].point })
    }

    @Test
    fun aHandleOpposesTheOtherHandleOfItsNodeWhenThatOneIsPulledOut() {
        val base = bent()
        // Pull the incoming handle (index 2) out along a diagonal: the outgoing one must run opposite to it.
        val pulled = base.copy(points = base.points.mapIndexed { i, p -> if (i == 2) p.copy(point = Point(80, 20)) else p })
        val after = done(pulled.withHandleTangentToNeighbour(4))
        val node = Point(100, 0)
        val incoming = Point(80, 20)
        val outgoing = after.points[4].point
        val cross = (incoming.x - node.x) * (outgoing.y - node.y) - (incoming.y - node.y) * (outgoing.x - node.x)
        assertTrue(abs(cross) <= 40, "cross product $cross")
        assertTrue((incoming.x - node.x) * (outgoing.x - node.x) + (incoming.y - node.y) * (outgoing.y - node.y) < 0)
    }

    @Test
    fun refusalsSayWhy() {
        val c = bent()
        assertTrue(assertIs<TangentResult.Refused>(c.withHandleTangentToNeighbour(3)).reason.contains("handle"))
        val retracted = c.copy(points = c.points.mapIndexed { i, p -> if (i == 4) p.copy(point = Point(100, 0)) else p })
        assertTrue(assertIs<TangentResult.Refused>(retracted.withHandleTangentToNeighbour(4)).reason.contains("pulled in"))
        val quad = polygonContour(listOf(Point(0, 0), Point(100, 0), Point(100, 100)), CurveFormat.QUADRATIC)
        assertTrue(assertIs<TangentResult.Refused>(quad.withHandleTangentToNeighbour(0)).reason.contains("quadratic"))
        assertIs<TangentResult.Refused>(c.withHandleTangentToNeighbour(99))
    }

    @Test
    fun aHandleAlreadyInLineIsRefusedAsAlreadyTangent() {
        val tangent = done(bent().withHandleTangentToNeighbour(4))
        val again = assertIs<TangentResult.Refused>(tangent.withHandleTangentToNeighbour(4))
        assertTrue(again.reason.contains("already tangent"), again.reason)
    }

    @Test
    fun aNodeSittingOnItsNeighbourHasNoDirectionToFollow() {
        val flat = polygonContour(listOf(Point(100, 0), Point(100, 0), Point(100, 100)), CurveFormat.CUBIC)
        val pulled = flat.copy(points = flat.points.mapIndexed { i, p -> if (i == 4) p.copy(point = Point(130, 30)) else p })
        val refused = assertIs<TangentResult.Refused>(pulled.withHandleTangentToNeighbour(4))
        assertTrue(refused.reason.contains("no direction"), refused.reason)
    }

    @Test
    fun aHandleTurnsParallelToAChosenLineChoosingTheNearerWay() {
        val c = bent()
        val horizontal = done(c.withHandleTangentToLine(4, Vec2(0.0, 500.0), Vec2(50.0, 500.0)))
        assertEquals(0, horizontal.points[4].point.y)
        assertTrue(horizontal.points[4].point.x > 100, "it keeps pointing the way it was pointing")
        // The reverse line direction gives the same handle: the way is chosen by where the handle points now.
        val reversed = done(c.withHandleTangentToLine(4, Vec2(50.0, 500.0), Vec2(0.0, 500.0)))
        assertEquals(horizontal.points[4], reversed.points[4])
        val vertical = done(c.withHandleTangentToLine(4, Vec2(0.0, 0.0), Vec2(0.0, 10.0)))
        assertEquals(100, vertical.points[4].point.x)
        assertTrue(vertical.points[4].point.y > 0)
        assertEquals(length(Point(100, 0), c.points[4].point), length(Point(100, 0), vertical.points[4].point), 1.0)
    }

    @Test
    fun aSmoothNodeBesideAStraightSideCannotBeTurnedOffItsLine() {
        val base = done(bent().withHandleTangentToNeighbour(4))
        val refused = assertIs<TangentResult.Refused>(base.withHandleTangentToLine(4, Vec2(0.0, 0.0), Vec2(0.0, 10.0)))
        assertTrue(refused.reason.contains("corner"), refused.reason)
        // A corner node has nothing to keep in line, so the same request is made.
        val corner = base.copy(points = base.points.mapIndexed { i, p -> if (i == 3) p.copy(smooth = false) else p })
        assertIs<TangentResult.Done>(corner.withHandleTangentToLine(4, Vec2(0.0, 0.0), Vec2(0.0, 10.0)))
    }

    @Test
    fun aLineWithNoDirectionIsRefusedAndAResultNeverBreaksTheSmoothFlag() {
        val c = bent()
        assertTrue(
            assertIs<TangentResult.Refused>(c.withHandleTangentToLine(4, Vec2(5.0, 5.0), Vec2(5.0, 5.0))).reason.contains("no direction"),
        )
        assertTrue(done(c.withHandleTangentToNeighbour(4)).brokenSmoothNodes().isEmpty())
    }
}
