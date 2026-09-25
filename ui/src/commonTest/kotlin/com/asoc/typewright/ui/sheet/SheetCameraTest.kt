// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.sheet

import com.asoc.typewright.core.geometry.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SheetCameraTest {
    @Test
    fun identityCameraMapsWorldToScreenUnchanged() {
        val camera = SheetCamera.IDENTITY
        assertEquals(Vec2(10.0, -4.0), camera.worldToScreen(Vec2(10.0, -4.0)))
        assertEquals(Vec2(10.0, -4.0), camera.screenToFont(Vec2(10.0, -4.0)))
    }

    @Test
    fun worldToScreenAppliesOffsetThenZoom() {
        val camera = SheetCamera(offset = Vec2(100.0, 50.0), zoom = 2.0)
        // (300 - 100) * 2 = 400, (50 - 50) * 2 = 0
        assertEquals(Vec2(400.0, 0.0), camera.worldToScreen(Vec2(300.0, 50.0)))
    }

    @Test
    fun worldToScreenAndScreenToFontAreExactInverses() {
        val camera = SheetCamera(offset = Vec2(123.0, -45.0), zoom = 3.25)
        val points =
            listOf(
                Vec2(0.0, 0.0),
                Vec2(500.0, -200.0),
                Vec2(-17.5, 900.25),
            )
        for (world in points) {
            val screen = camera.worldToScreen(world)
            val roundTripped = camera.screenToFont(screen)
            assertNear(world, roundTripped)
        }
        for (screen in points) {
            val world = camera.screenToFont(screen)
            val roundTripped = camera.worldToScreen(world)
            assertNear(screen, roundTripped)
        }
    }

    @Test
    fun zeroOrNegativeZoomIsRejected() {
        assertFailsWith<IllegalArgumentException> { SheetCamera(Vec2(0.0, 0.0), zoom = 0.0) }
        assertFailsWith<IllegalArgumentException> { SheetCamera(Vec2(0.0, 0.0), zoom = -1.0) }
    }

    @Test
    fun pannedByMovesTheWorldPointUnderAFixedScreenPositionByTheScreenDeltaOverZoom() {
        val camera = SheetCamera(offset = Vec2(0.0, 0.0), zoom = 2.0)
        val panned = camera.pannedBy(Vec2(20.0, 10.0))
        // A screen-space drag of (20, 10) at zoom 2 is (10, 5) of world movement.
        assertEquals(Vec2(-10.0, -5.0), panned.offset)
    }

    @Test
    fun pannedToReplacesTheOffsetOutright() {
        val camera = SheetCamera(offset = Vec2(5.0, 5.0), zoom = 1.5)
        val panned = camera.pannedTo(Vec2(999.0, -3.0))
        assertEquals(Vec2(999.0, -3.0), panned.offset)
        assertEquals(1.5, panned.zoom)
    }

    @Test
    fun zoomedToKeepsTheWorldPointUnderTheFocusFixedOnScreen() {
        val camera = SheetCamera(offset = Vec2(50.0, 20.0), zoom = 1.0)
        val focusScreen = Vec2(200.0, 80.0)
        val worldUnderFocusBefore = camera.screenToFont(focusScreen)

        val zoomed = camera.zoomedTo(newZoom = 4.0, focusScreen = focusScreen)

        assertEquals(4.0, zoomed.zoom)
        assertNear(focusScreen, zoomed.worldToScreen(worldUnderFocusBefore))
    }

    @Test
    fun zoomedByMultipliesTheCurrentZoom() {
        val camera = SheetCamera(offset = Vec2(0.0, 0.0), zoom = 2.0)
        val zoomed = camera.zoomedBy(factor = 1.5, focusScreen = Vec2(0.0, 0.0))
        assertDoubleNear(3.0, zoomed.zoom)
    }

    @Test
    fun depthReadsGlyphAboveWordMaxZoom() {
        assertEquals(SheetDepth.GLYPH, SheetCamera(Vec2(0.0, 0.0), zoom = SheetDepth.WORD_MAX_ZOOM + 0.01).depth)
    }

    @Test
    fun depthReadsWordBetweenSpecimenAndWordMaxZoom() {
        assertEquals(SheetDepth.WORD, SheetCamera(Vec2(0.0, 0.0), zoom = SheetDepth.WORD_MAX_ZOOM).depth)
        assertEquals(SheetDepth.WORD, SheetCamera(Vec2(0.0, 0.0), zoom = SheetDepth.SPECIMEN_MAX_ZOOM + 0.01).depth)
    }

    @Test
    fun depthReadsSpecimenBetweenMapAndSpecimenMaxZoom() {
        assertEquals(SheetDepth.SPECIMEN, SheetCamera(Vec2(0.0, 0.0), zoom = SheetDepth.SPECIMEN_MAX_ZOOM).depth)
        assertEquals(SheetDepth.SPECIMEN, SheetCamera(Vec2(0.0, 0.0), zoom = SheetDepth.MAP_MAX_ZOOM + 0.001).depth)
    }

    @Test
    fun depthReadsMapAtOrBelowMapMaxZoom() {
        assertEquals(SheetDepth.MAP, SheetCamera(Vec2(0.0, 0.0), zoom = SheetDepth.MAP_MAX_ZOOM).depth)
        assertEquals(SheetDepth.MAP, SheetCamera(Vec2(0.0, 0.0), zoom = 0.001).depth)
    }

    @Test
    fun depthLevelsAreOrderedZoomedInToZoomedOut() {
        assertTrue(SheetDepth.MAP_MAX_ZOOM < SheetDepth.SPECIMEN_MAX_ZOOM)
        assertTrue(SheetDepth.SPECIMEN_MAX_ZOOM < SheetDepth.WORD_MAX_ZOOM)
    }

    private fun assertNear(
        expected: Vec2,
        actual: Vec2,
        tolerance: Double = 1e-9,
    ) {
        assertTrue(
            kotlin.math.abs(expected.x - actual.x) < tolerance && kotlin.math.abs(expected.y - actual.y) < tolerance,
            "expected $expected to be near $actual",
        )
    }

    private fun assertDoubleNear(
        expected: Double,
        actual: Double,
        tolerance: Double = 1e-9,
    ) {
        assertTrue(kotlin.math.abs(expected - actual) < tolerance, "expected $expected, was $actual")
    }
}
