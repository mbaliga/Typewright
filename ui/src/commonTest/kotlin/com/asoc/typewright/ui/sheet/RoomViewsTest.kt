// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.sheet

import com.asoc.typewright.core.geometry.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RoomViewsTest {
    private val zoomedDrawView = SheetCamera(Vec2(-340.0, -210.0), zoom = 1.8)

    @Test
    fun aRoomAtRestIsLaidOutForTheCameraAtRestWhateverTheDrawViewWas() {
        val space = RoomViews.firstView(Room.SPACE, zoomedDrawView)
        assertEquals(RoomViews.REST_ZOOM, space.zoom)
        assertEquals(Room.SPACE.worldBounds().left, space.offset.x)
        assertEquals(-RoomViews.REST_BASELINE_DP, space.offset.y)
        val learn = RoomViews.firstView(Room.LEARN, zoomedDrawView)
        assertEquals(Room.LEARN.worldBounds().left, learn.offset.x)
        assertEquals(RoomViews.REST_ZOOM, learn.zoom)
    }

    @Test
    fun aFirstArrivalInDrawKeepsTheZoomAndTheHeightSoTheGlyphCanBeFittedFromThere() {
        val from = SheetCamera(Vec2(1500.0, -380.0), zoom = 1.0)
        val draw = RoomViews.firstView(Room.DRAW, from)
        assertEquals(Room.DRAW.worldBounds().left, draw.offset.x)
        assertEquals(-380.0, draw.offset.y)
        assertEquals(1.0, draw.zoom)
    }

    @Test
    fun aCameraStateStartsWithNoRememberedViewAndTheDrawViewIsRememberedOnceLeft() {
        val state = SheetCameraState(zoomedDrawView)
        assertFalse(state.hasView(Room.DRAW))
        assertFalse(state.hasView(Room.SPACE))
    }

    @Test
    fun aFlightToTheRoomTheCameraIsAlreadyInGoesNowhere() {
        val inDraw = SheetCamera(Vec2(Room.DRAW.worldBounds().left + 40.0, -210.0), zoom = 1.8)
        val state = SheetCameraState(inDraw)
        assertEquals(Room.DRAW, state.currentRoom)
        assertEquals(null, state.flightTarget(Room.DRAW), "a flick or an arrow key at the end of the line asks for the room it is in")
        val toSpace = state.flightTarget(Room.SPACE)
        assertEquals(RoomViews.firstView(Room.SPACE, inDraw), toSpace, "any other room is a real flight")
    }

    @Test
    fun zoomIsHeldToTheRangeTheSheetAllows() {
        val state = SheetCameraState(SheetCamera(Vec2(0.0, 0.0), zoom = 1.0))
        repeat(50) { state.zoomBy(2.0, Vec2(100.0, 100.0)) }
        assertEquals(MAX_ZOOM, state.camera.zoom)
        repeat(100) { state.zoomBy(0.5, Vec2(100.0, 100.0)) }
        assertEquals(MIN_ZOOM, state.camera.zoom)
    }

    @Test
    fun zoomingAroundAPointKeepsThatPointStillOnTheScreen() {
        val state = SheetCameraState(SheetCamera(Vec2(300.0, 40.0), zoom = 1.0))
        val focus = Vec2(120.0, 200.0)
        val before = state.camera.screenToFont(focus)
        state.zoomBy(1.7, focus)
        val after = state.camera.screenToFont(focus)
        assertEquals(before.x, after.x, 1e-9)
        assertEquals(before.y, after.y, 1e-9)
    }
}
