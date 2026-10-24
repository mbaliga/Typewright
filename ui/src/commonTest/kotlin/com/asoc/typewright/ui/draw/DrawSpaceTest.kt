// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.ui.sheet.MAX_ZOOM
import com.asoc.typewright.ui.sheet.MIN_ZOOM
import com.asoc.typewright.ui.sheet.SheetCamera
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DrawSpaceTest {
    private val area = FitArea(widthDp = 360.0, heightDp = 780.0, topDp = 110.0, bottomDp = 200.0, leftDp = 96.0, rightDp = 24.0)

    @Test
    fun screenAndFontAreExactInverses() {
        val space = DrawSpace(Vec2(40.0, 520.0), dpPerUnit = 0.54)
        val font = Vec2(123.0, 456.0)
        val back = space.toFont(space.toScreen(font))
        assertEquals(font.x, back.x, 1e-9)
        assertEquals(font.y, back.y, 1e-9)
    }

    @Test
    fun fontYGrowsUpTheScreen() {
        val space = DrawSpace(Vec2(0.0, 500.0), dpPerUnit = 1.0)
        assertTrue(space.toScreen(Vec2(0.0, 100.0)).y < space.toScreen(Vec2(0.0, 0.0)).y)
        assertEquals(100.0, space.unitsFor(100.0), 1e-9)
    }

    @Test
    fun theCameraDecidesWhereTheGlyphOriginLandsAndHowBigAUnitIs() {
        val camera = SheetCamera(Vec2(DrawWorld.originX - 50.0, -300.0), zoom = 2.0)
        val space = DrawSpace.of(camera)
        // World x of the origin minus the camera offset, times zoom.
        assertEquals(100.0, space.originScreen.x, 1e-9)
        assertEquals(600.0, space.originScreen.y, 1e-9)
        assertEquals(DrawWorld.UNIT_DP * 2.0, space.dpPerUnit, 1e-9)
    }

    @Test
    fun theSpaceAgreesWithTheSheetsOwnFontUpProjection() {
        val camera = SheetCamera(Vec2(DrawWorld.originX - 20.0, -250.0), zoom = 1.5)
        val space = DrawSpace.of(camera)
        // A font point (x, y) is at world (originX + x*U, y*U) font-up; the camera maps world (x, -y).
        val world = Vec2(DrawWorld.originX + 300 * DrawWorld.UNIT_DP, -(400 * DrawWorld.UNIT_DP))
        val expected = camera.worldToScreen(world)
        val actual = space.toScreen(Vec2(300.0, 400.0))
        assertEquals(expected.x, actual.x, 1e-9)
        assertEquals(expected.y, actual.y, 1e-9)
    }

    @Test
    fun fitPutsTheGlyphBoxInsideTheUsableAreaAndCentresIt() {
        val camera = fitCamera(advanceWidth = 475, lowest = -200, highest = 700, area = area)
        val space = DrawSpace.of(camera)
        val topLeft = space.toScreen(Vec2(0.0, 700.0))
        val bottomRight = space.toScreen(Vec2(475.0, -200.0))
        assertTrue(
            topLeft.x >= area.leftDp - 1e-6 && bottomRight.x <= area.widthDp - area.rightDp + 1e-6,
            "inside the side gutters: $topLeft $bottomRight",
        )
        assertTrue(
            topLeft.y >= area.topDp - 1e-6 && bottomRight.y <= area.heightDp - area.bottomDp + 1e-6,
            "between the header and the deck: $topLeft $bottomRight",
        )
        val centreX = (topLeft.x + bottomRight.x) / 2.0
        val centreY = (topLeft.y + bottomRight.y) / 2.0
        assertEquals(area.leftDp + (area.widthDp - area.leftDp - area.rightDp) / 2.0, centreX, 1e-6)
        assertEquals(area.topDp + (area.heightDp - area.topDp - area.bottomDp) / 2.0, centreY, 1e-6)
    }

    @Test
    fun fitUsesTheTighterOfTheTwoAxes() {
        val wide = fitCamera(advanceWidth = 2000, lowest = 0, highest = 700, area = area)
        val tall = fitCamera(advanceWidth = 300, lowest = -400, highest = 1200, area = area)
        assertTrue(DrawSpace.of(wide).dpPerUnit < DrawSpace.of(tall).dpPerUnit * 2)
        val wideBox = DrawSpace.of(wide).toScreen(Vec2(2000.0, 0.0)).x - DrawSpace.of(wide).toScreen(Vec2(0.0, 0.0)).x
        assertTrue(wideBox <= area.widthDp - area.leftDp - area.rightDp + 1e-6, "a wide glyph is limited by the width: $wideBox")
        val tallBox = DrawSpace.of(tall).toScreen(Vec2(0.0, -400.0)).y - DrawSpace.of(tall).toScreen(Vec2(0.0, 1200.0)).y
        assertTrue(tallBox <= area.heightDp - area.topDp - area.bottomDp + 1e-6, "a tall glyph is limited by the height: $tallBox")
    }

    @Test
    fun fitStaysInsideTheZoomRangeTheSheetAllows() {
        val tiny = fitCamera(advanceWidth = 1, lowest = 0, highest = 1, area = area)
        assertEquals(
            DrawWorld.MAX_FIT_DP_PER_UNIT / DrawWorld.UNIT_DP,
            tiny.zoom,
            1e-9,
            "a one-unit glyph is shown at the largest size a fit allows",
        )
        assertTrue(tiny.zoom <= MAX_ZOOM)
        val huge = fitCamera(advanceWidth = 5_000_000, lowest = 0, highest = 5_000_000, area = area)
        assertEquals(MIN_ZOOM, huge.zoom, "a vast glyph would want less than the smallest zoom")
    }

    @Test
    fun aFullStopIsNotBlownUpToFillTheScreen() {
        val camera = fitCamera(advanceWidth = 100, lowest = 0, highest = 100, area = area)
        assertEquals(DrawWorld.MAX_FIT_DP_PER_UNIT, DrawSpace.of(camera).dpPerUnit, 1e-9)
    }

    private val lowercaseMetrics =
        DrawMetrics(
            unitsPerEm = 1000,
            lines =
                listOf(
                    MetricLine(MetricKind.ASCENDER, 984),
                    MetricLine(MetricKind.CAP_HEIGHT, 700),
                    MetricLine(MetricKind.X_HEIGHT, 500),
                    MetricLine(MetricKind.BASELINE, 0),
                    MetricLine(MetricKind.DESCENDER, -292),
                ),
            zones = emptyList(),
        )

    @Test
    fun aRoundLetterFitsFromTheBaselineToItsOvershootAndNotToTheAscender() {
        assertEquals(
            0 to 510,
            DrawFit.heights(inkBottom = -10, inkTop = 510, metrics = lowercaseMetrics).let { (low, high) ->
                maxOf(low, 0) to
                    high
            },
        )
        assertEquals(
            -10,
            DrawFit.heights(inkBottom = -10, inkTop = 510, metrics = lowercaseMetrics).first,
            "an overshoot below the baseline is kept in view",
        )
    }

    @Test
    fun aCapitalFitsToItsHeightAndADescenderToItsDepth() {
        assertEquals(0 to 700, DrawFit.heights(inkBottom = 0, inkTop = 700, metrics = lowercaseMetrics))
        assertEquals(
            -200 to 500,
            DrawFit.heights(inkBottom = -200, inkTop = 480, metrics = lowercaseMetrics),
            "the x-height stays in view above a descender glyph",
        )
    }

    @Test
    fun aSmallGlyphStillShowsTheXHeightLineAndAnEmptyOneFitsTheReference() {
        assertEquals(0 to 500, DrawFit.heights(inkBottom = 0, inkTop = 100, metrics = lowercaseMetrics))
        assertEquals(0 to 500, DrawFit.heights(inkBottom = null, inkTop = null, metrics = lowercaseMetrics))
        val capsOnly = lowercaseMetrics.copy(lines = lowercaseMetrics.lines.filter { it.kind != MetricKind.X_HEIGHT })
        assertEquals(0 to 700, DrawFit.heights(null, null, capsOnly), "with no x-height the cap height is the reference")
        assertEquals(0 to 700, DrawFit.heights(null, null, DrawMetrics.NONE), "with neither, 70% of the em")
    }
}
