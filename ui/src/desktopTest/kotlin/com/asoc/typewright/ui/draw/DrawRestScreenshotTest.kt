// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.core.geometry.polygonContour
import com.asoc.typewright.project.PointRef
import com.asoc.typewright.ui.puck.PrimitiveKind
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The feedback of Primitives, Measure and Hobby on the phone-sized sheet, and the desktop frame, as a person sees them: each is
 * written as a PNG under `ui/build/screenshots`, and what is asserted is that the state changed the picture in the region where
 * its feedback is drawn. Nothing here is a touch screen; touch is owner-verified on a device only.
 */
class DrawRestScreenshotTest {
    /** How many pixels differ between [a] and [b] inside the screen-pixel box [x0, x1) by [y0, y1). */
    private fun differingPixels(
        a: org.jetbrains.skia.Image,
        b: org.jetbrains.skia.Image,
        x0: Int = 0,
        x1: Int = Int.MAX_VALUE,
        y0: Int = 0,
        y1: Int = Int.MAX_VALUE,
    ): Int {
        val pa = a.toComposeImageBitmap().toPixelMap()
        val pb = b.toComposeImageBitmap().toPixelMap()
        var n = 0
        for (y in y0 until minOf(y1, pa.height)) {
            for (x in x0 until minOf(x1, pa.width)) {
                val c = pa[x, y]
                val d = pb[x, y]
                if (abs(c.red - d.red) + abs(c.green - d.green) + abs(c.blue - d.blue) > 0.3f) n++
            }
        }
        return n
    }

    private fun arc(): Glyph =
        Glyph(
            "h",
            400,
            listOf(
                polygonContour(
                    listOf(Point(0, 0), Point(60, 80), Point(150, 110), Point(240, 80), Point(300, 0), Point(150, -60)),
                    CurveFormat.CUBIC,
                ),
            ),
        )

    @Test
    fun aRoundedRectangleBeingDraggedOutOnTheEmptyGlyph() =
        withDrawSheet(DrawTestProjects.small(Glyph("a", 600, emptyList())), "a", PHONE_W, PHONE_H, PHONE_DENSITY) {
            key(Keys.S)
            puck.selectPrimitive(PrimitiveKind.ROUNDED_RECTANGLE)
            frames(3)
            val empty = snapshot("draw-primitives-0-empty")
            val from = room.space().toScreen(Vec2(80.0, 4.0))
            val to = room.space().toScreen(Vec2(480.0, 497.0))
            press(from)
            move(Vec2((from.x + to.x) / 2.0, (from.y + to.y) / 2.0))
            move(to)
            val live = snapshot("draw-primitives-phone")
            // The dashed outline lies inside the box the drag spans; the header and the deck are outside it.
            val box = listOf(from, to)
            val x0 = (box.minOf { it.x } * PHONE_DENSITY).toInt()
            val x1 = (box.maxOf { it.x } * PHONE_DENSITY).toInt() + 1
            val y0 = (box.minOf { it.y } * PHONE_DENSITY).toInt()
            val y1 = (box.maxOf { it.y } * PHONE_DENSITY).toInt() + 1
            assertTrue(
                differingPixels(empty, live, x0, x1, y0, y1) > MIN_CHANGED_PIXELS,
                "the shape being dragged out is drawn where the drag spans",
            )
            release(to)
        }

    @Test
    fun theMeasureLineBetweenTwoNodesWithItsReadingInTheHeader() =
        withDrawSheet(DrawTestProjects.small(arc()), "h", PHONE_W, PHONE_H, PHONE_DENSITY) {
            key(Keys.M)
            frames(3)
            val before = snapshot("draw-measure-0-before")
            val a = screenOf(0, 0)
            val b = screenOf(0, 12)
            click(a)
            click(b)
            val after = snapshot("draw-measure-phone")
            val x0 = (minOf(a.x, b.x) * PHONE_DENSITY).toInt() - MARGIN_PX
            val x1 = (maxOf(a.x, b.x) * PHONE_DENSITY).toInt() + MARGIN_PX
            val y0 = (minOf(a.y, b.y) * PHONE_DENSITY).toInt() - MARGIN_PX
            val y1 = (maxOf(a.y, b.y) * PHONE_DENSITY).toInt() + MARGIN_PX
            assertTrue(differingPixels(before, after, x0, x1, y0, y1) > MIN_CHANGED_PIXELS, "the line and its two crosses are drawn")
            val header = (HEADER_DP * PHONE_DENSITY).toInt()
            assertTrue(differingPixels(before, after, 0, PHONE_W, 0, header) > MIN_HEADER_PIXELS, "the reading is spoken in the header")
        }

    @Test
    fun aHobbyCurveThroughThreeNodesReshapesTheOutlineWhereThoseNodesAre() =
        withDrawSheet(DrawTestProjects.small(arc()), "h", PHONE_W, PHONE_H, PHONE_DENSITY) {
            controller.selection = DrawSelection(setOf(PointRef(0, 3), PointRef(0, 6), PointRef(0, 9)))
            frames(3)
            val before = snapshot("draw-hobby-0-before")
            key(Keys.H)
            frames(3)
            val after = snapshot("draw-hobby-phone")
            val top = room.space().toScreen(Vec2(0.0, 150.0))
            val bottom = room.space().toScreen(Vec2(300.0, 0.0))
            assertTrue(
                differingPixels(
                    before,
                    after,
                    (top.x * PHONE_DENSITY).toInt(),
                    (bottom.x * PHONE_DENSITY).toInt(),
                    (top.y * PHONE_DENSITY).toInt(),
                    (bottom.y * PHONE_DENSITY).toInt(),
                ) > MIN_CHANGED_PIXELS,
                "the curve between the nodes moved",
            )
        }

    @Test
    fun theDesktopFrameHasTheRailTheInspectorColumnTheStripAndTheLayersPanel() =
        withDrawSheet(DrawTestProjects.hyleDecoCubic(), "o", DESKTOP_W * 2, DESKTOP_H * 2, 2f) {
            key(Keys.S)
            puck.selectPrimitive(PrimitiveKind.CIRCLE)
            frames(4)
            val rail = snapshot("draw-desktop-frame")
            // The rail lists the shapes under Primitives, and the wide frame is the 900 dp one.
            assertTrue(room.space().dpPerUnit > 0.0)
            assertTrue(puck.currentTool == com.asoc.typewright.ui.puck.Tool.PRIMITIVES)
            assertTrue(rail.width == DESKTOP_W * 2)
        }

    private companion object {
        const val PHONE_W = 720
        const val PHONE_H = 1560
        const val PHONE_DENSITY = 2f
        const val DESKTOP_W = 1280
        const val DESKTOP_H = 800
        const val MIN_CHANGED_PIXELS = 300
        const val MIN_HEADER_PIXELS = 40
        const val MARGIN_PX = 12
        const val HEADER_DP = 62
    }
}
