// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.sheet

import com.asoc.typewright.core.geometry.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoomLayoutTest {
    @Test
    fun roomsAreOrderedDrawSpaceLearnLeftToRight() {
        assertEquals(listOf(Room.DRAW, Room.SPACE, Room.LEARN), Room.ORDERED)
        assertEquals(0, Room.DRAW.index)
        assertEquals(1, Room.SPACE.index)
        assertEquals(2, Room.LEARN.index)
    }

    @Test
    fun roomBoundsTileWithNoGapOrOverlap() {
        val draw = Room.DRAW.worldBounds()
        val space = Room.SPACE.worldBounds()
        val learn = Room.LEARN.worldBounds()

        assertEquals(0.0, draw.left)
        assertEquals(draw.right, space.left)
        assertEquals(space.right, learn.left)
        assertEquals(ROOM_WIDTH_DP, draw.width)
        assertEquals(ROOM_WIDTH_DP, space.width)
        assertEquals(ROOM_WIDTH_DP, learn.width)
    }

    @Test
    fun roomAtFindsTheContainingRoom() {
        assertEquals(Room.DRAW, roomAt(0.0))
        assertEquals(Room.DRAW, roomAt(ROOM_WIDTH_DP - 1.0))
        assertEquals(Room.SPACE, roomAt(ROOM_WIDTH_DP))
        assertEquals(Room.SPACE, roomAt(ROOM_WIDTH_DP * 1.5))
        assertEquals(Room.LEARN, roomAt(ROOM_WIDTH_DP * 2.5))
    }

    @Test
    fun roomAtClampsOutsideTheSheet() {
        assertEquals(Room.DRAW, roomAt(-500.0))
        assertEquals(Room.LEARN, roomAt(ROOM_WIDTH_DP * 10))
    }

    @Test
    fun flightTargetOffsetIsTheRoomsLeftEdgeKeepingCurrentY() {
        val target = Room.SPACE.flightTargetOffset(currentOffset = Vec2(x = 0.0, y = 42.0))
        assertEquals(Vec2(ROOM_WIDTH_DP, 42.0), target)
    }

    @Test
    fun flightFromBuildsARoomFlightFromTheCamerasCurrentOffset() {
        val camera = SheetCamera(offset = Vec2(50.0, 7.0), zoom = 1.0)
        val flight = Room.LEARN.flightFrom(camera)
        assertEquals(Vec2(50.0, 7.0), flight.from)
        assertEquals(Vec2(2 * ROOM_WIDTH_DP, 7.0), flight.to)
    }

    @Test
    fun roomFlightOffsetAtStartAndEndMatchFromAndTo() {
        val flight = RoomFlight(from = Vec2(0.0, 0.0), to = Vec2(1000.0, -200.0))
        assertEquals(Vec2(0.0, 0.0), flight.offsetAt(0.0))
        assertEquals(Vec2(1000.0, -200.0), flight.offsetAt(1.0))
    }

    @Test
    fun roomFlightOffsetAtMidpointFollowsTheEasingCurve() {
        val flight = RoomFlight(from = Vec2(0.0, 0.0), to = Vec2(1000.0, 1000.0))
        val atHalf = flight.offsetAt(0.5)
        val easedHalf = CubicBezierEasing.ROOM_PAN.transform(0.5)
        assertNear(1000.0 * easedHalf, atHalf.x)
        assertNear(1000.0 * easedHalf, atHalf.y)
    }

    @Test
    fun roomFlightDurationMatchesUiSpec() {
        assertEquals(600, RoomFlight.DURATION_MS)
    }

    @Test
    fun nextAndPreviousStepOneRoomClampedAtTheEnds() {
        assertEquals(Room.SPACE, Room.DRAW.next())
        assertEquals(Room.LEARN, Room.SPACE.next())
        assertEquals(Room.LEARN, Room.LEARN.next()) // clamped: no wraparound
        assertEquals(Room.DRAW, Room.SPACE.previous())
        assertEquals(Room.SPACE, Room.LEARN.previous())
        assertEquals(Room.DRAW, Room.DRAW.previous()) // clamped
    }

    private fun assertNear(
        expected: Double,
        actual: Double,
        tolerance: Double = 1e-6,
    ) {
        assertTrue(kotlin.math.abs(expected - actual) < tolerance, "expected $expected, was $actual")
    }
}
