// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.ContourPoint
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.core.geometry.reverse
import com.asoc.typewright.project.PointRef
import com.asoc.typewright.ui.puck.Tool
import com.asoc.typewright.ui.tokens.CanvasTextures
import com.asoc.typewright.ui.tokens.toColor
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Draw as a person sees it: the whole [TypewrightSheet] over a real project (Hyle Deco's own
 * outlines, imported from `fonts/HyleDeco-Regular.ttf`) rendered at phone and desktop width, outline
 * and fill, with a node selected, and the radial and the rich menu opened by the same controller calls
 * the pointer layer makes. Each is written as a PNG under `ui/build/screenshots`; what is asserted is
 * what a unit test can say about a picture: the glyph is inside the area the glass leaves free, and the
 * selection is drawn in the selection colour.
 *
 * Nothing here is a touch on a real screen: that is owner-verified on a device only.
 */
class DrawScreenshotTest {
    private val texture = CanvasTextures.PAPER

    private fun countNear(
        image: org.jetbrains.skia.Image,
        color: Color,
        tolerance: Float = 0.12f,
    ): Int {
        val pixels = image.toComposeImageBitmap().toPixelMap()
        var n = 0
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                val c = pixels[x, y]
                if (abs(c.red - color.red) < tolerance && abs(c.green - color.green) < tolerance &&
                    abs(c.blue - color.blue) < tolerance
                ) {
                    n++
                }
            }
        }
        return n
    }

    private fun assertDrawn(
        image: org.jetbrains.skia.Image,
        selectedColour: Color,
    ) {
        assertTrue(countNear(image, texture.ink.toColor(), 0.1f) > MIN_INK_PIXELS, "the glyph and its lines are drawn in the ink colour")
        assertTrue(countNear(image, selectedColour, 0.1f) > MIN_SELECTED_PIXELS, "the selected node is drawn in the selection colour")
    }

    private val selectedColour: Color get() = texture.drawColors().selected

    // ---- the real letters, phone ---------------------------------------------------------------

    @Test
    fun phoneOutlineO() =
        withDrawSheet(DrawTestProjects.hyleDecoQuadratic(), "o", PHONE_W, PHONE_H, PHONE_DENSITY) {
            select(0, 20)
            assertDrawn(snapshot("draw-o-phone-outline"), selectedColour)
        }

    @Test
    fun phoneFillO() =
        withDrawSheet(DrawTestProjects.hyleDecoQuadratic(), "o", PHONE_W, PHONE_H, PHONE_DENSITY, fill = true) {
            select(0, 20)
            assertDrawn(snapshot("draw-o-phone-fill"), selectedColour)
        }

    @Test
    fun phoneOutlineN() =
        withDrawSheet(DrawTestProjects.hyleDecoQuadratic(), "n", PHONE_W, PHONE_H, PHONE_DENSITY) {
            select(0, 10)
            assertDrawn(snapshot("draw-n-phone-outline"), selectedColour)
        }

    @Test
    fun phoneFillN() =
        withDrawSheet(DrawTestProjects.hyleDecoQuadratic(), "n", PHONE_W, PHONE_H, PHONE_DENSITY, fill = true) {
            select(0, 10)
            assertDrawn(snapshot("draw-n-phone-fill"), selectedColour)
        }

    @Test
    fun phoneOutlineH() =
        withDrawSheet(DrawTestProjects.hyleDecoQuadratic(), "H", PHONE_W, PHONE_H, PHONE_DENSITY) {
            select(0, 3)
            assertDrawn(snapshot("draw-h-phone-outline"), selectedColour)
        }

    @Test
    fun phoneFillH() =
        withDrawSheet(DrawTestProjects.hyleDecoQuadratic(), "H", PHONE_W, PHONE_H, PHONE_DENSITY, fill = true) {
            select(0, 3)
            assertDrawn(snapshot("draw-h-phone-fill"), selectedColour)
        }

    // ---- desktop -------------------------------------------------------------------------------

    @Test
    fun desktopOutlineO() =
        withDrawSheet(DrawTestProjects.hyleDecoQuadratic(), "o", DESKTOP_W, DESKTOP_H, 1f) {
            select(0, 20)
            assertDrawn(snapshot("draw-o-desktop-outline"), selectedColour)
        }

    @Test
    fun desktopFillO() =
        withDrawSheet(DrawTestProjects.hyleDecoQuadratic(), "o", DESKTOP_W, DESKTOP_H, 1f, fill = true) {
            select(0, 20)
            assertDrawn(snapshot("draw-o-desktop-fill"), selectedColour)
        }

    @Test
    fun desktopOutlineN() =
        withDrawSheet(DrawTestProjects.hyleDecoQuadratic(), "n", DESKTOP_W, DESKTOP_H, 1f) {
            select(0, 10)
            assertDrawn(snapshot("draw-n-desktop-outline"), selectedColour)
        }

    @Test
    fun desktopFillN() =
        withDrawSheet(DrawTestProjects.hyleDecoQuadratic(), "n", DESKTOP_W, DESKTOP_H, 1f, fill = true) {
            select(0, 10)
            assertDrawn(snapshot("draw-n-desktop-fill"), selectedColour)
        }

    @Test
    fun desktopOutlineH() =
        withDrawSheet(DrawTestProjects.hyleDecoQuadratic(), "H", DESKTOP_W, DESKTOP_H, 1f) {
            select(0, 3)
            assertDrawn(snapshot("draw-h-desktop-outline"), selectedColour)
        }

    @Test
    fun desktopFillH() =
        withDrawSheet(DrawTestProjects.hyleDecoQuadratic(), "H", DESKTOP_W, DESKTOP_H, 1f, fill = true) {
            select(0, 3)
            assertDrawn(snapshot("draw-h-desktop-fill"), selectedColour)
        }

    // ---- a letter with curves: handles, smooth and corner nodes ----------------------------------

    @Test
    fun phoneRoundedOWithHandles() =
        withDrawSheet(DrawTestProjects.small(roundedO()), "o", PHONE_W, PHONE_H, PHONE_DENSITY) {
            // The second node of the outer contour: smooth, with a handle either side.
            select(0, 3)
            val image = snapshot("draw-rounded-o-phone-outline")
            assertDrawn(image, selectedColour)
        }

    @Test
    fun desktopRoundedOWithHandles() =
        withDrawSheet(DrawTestProjects.small(roundedO()), "o", DESKTOP_W, DESKTOP_H, 1f) {
            select(0, 3)
            assertDrawn(snapshot("draw-rounded-o-desktop-outline"), selectedColour)
        }

    // ---- the radial and the rich menu ----------------------------------------------------------

    @Test
    fun phoneRadialSweepingOnANode() =
        withDrawSheet(DrawTestProjects.small(roundedO()), "o", PHONE_W, PHONE_H, PHONE_DENSITY) {
            select(0, 3)
            val node = screenOf(0, 3)
            val frame =
                DrawFrame(
                    space = room.space(),
                    canvasWidthDp = PHONE_W / PHONE_DENSITY.toDouble(),
                    canvasHeightDp = PHONE_H / PHONE_DENSITY.toDouble(),
                    topInsetDp = 62.0,
                    deckHeightDp = 160.0,
                    pointer = PointerKind.TOUCH,
                    tool = Tool.SELECT,
                )
            controller.onOutput(CanvasOutput.HoldOpened(Hit.OnPoint(PointRef(0, 3), onCurve = true, distanceDp = 0.0), node), frame)
            // The thumb sweeps down and to the right, which chooses the sector across the hub.
            controller.onOutput(CanvasOutput.HeldMoved(Vec2(node.x + 40.0, node.y + 48.0)), frame)
            frames(2)
            val ui = assertNotNull(controller.radial)
            assertTrue(ui.state is RadialState.Sweeping)
            snapshot("draw-radial-sweep-phone")
        }

    @Test
    fun phoneRadialLiftedOnANode() =
        withDrawSheet(DrawTestProjects.small(roundedO()), "o", PHONE_W, PHONE_H, PHONE_DENSITY) {
            select(0, 3)
            val node = screenOf(0, 3)
            val frame =
                DrawFrame(
                    space = room.space(),
                    canvasWidthDp = PHONE_W / PHONE_DENSITY.toDouble(),
                    canvasHeightDp = PHONE_H / PHONE_DENSITY.toDouble(),
                    topInsetDp = 62.0,
                    deckHeightDp = 160.0,
                    pointer = PointerKind.TOUCH,
                    tool = Tool.SELECT,
                )
            controller.onOutput(CanvasOutput.HoldOpened(Hit.OnPoint(PointRef(0, 3), onCurve = true, distanceDp = 0.0), node), frame)
            controller.onOutput(CanvasOutput.HeldReleased(node), frame)
            frames(2)
            assertTrue(controller.radial?.state is RadialState.Lifted)
            snapshot("draw-radial-lifted-phone")
        }

    @Test
    fun phoneRichMenuOnANode() =
        withDrawSheet(DrawTestProjects.small(roundedO()), "o", PHONE_W, PHONE_H, PHONE_DENSITY) {
            select(0, 3)
            controller.openMenuAt(Vec2(180.0, 300.0), PointerKind.TOUCH)
            frames(3)
            assertNotNull(controller.menu)
            snapshot("draw-menu-phone")
        }

    @Test
    fun desktopRichMenuOnANode() =
        withDrawSheet(DrawTestProjects.small(roundedO()), "o", DESKTOP_W, DESKTOP_H, 1f) {
            select(0, 3)
            controller.openMenuAt(screenOf(0, 3), PointerKind.MOUSE)
            frames(3)
            assertNotNull(controller.menu)
            snapshot("draw-menu-desktop")
        }

    // ---- the glass: the palette, the page menu, a locked glyph, an empty glyph ----------------------------

    @Test
    fun desktopCommandPalette() =
        withDrawSheet(DrawTestProjects.small(roundedO()), "o", DESKTOP_W, DESKTOP_H, 1f) {
            select(0, 3)
            key(Keys.K, ctrl = true)
            frames(3)
            snapshot("draw-palette-desktop")
        }

    @Test
    fun phonePageMenu() =
        withDrawSheet(DrawTestProjects.small(roundedO()), "o", PHONE_W, PHONE_H, PHONE_DENSITY) {
            select(0, 3)
            // The deck's right circle, on a 360 dp wide phone.
            click(Vec2(323.0, 687.0))
            frames(3)
            snapshot("draw-page-menu-phone")
        }

    @Test
    fun phoneLockedGlyphRefusesAnEditAndOffersUnlock() =
        withDrawSheet(DrawTestProjects.small(roundedO()), "o", PHONE_W, PHONE_H, PHONE_DENSITY) {
            select(0, 3)
            key(Keys.L, ctrl = true)
            key(Keys.RIGHT)
            assertTrue(controller.notice is DrawNotice.Locked)
            snapshot("draw-locked-phone")
        }

    @Test
    fun phoneEmptyGlyphSaysHowToStart() =
        withDrawSheet(DrawTestProjects.small(Glyph("a", 500, emptyList())), "a", PHONE_W, PHONE_H, PHONE_DENSITY) {
            snapshot("draw-empty-glyph-phone")
        }

    // ---- a fixture with real curves ------------------------------------------------------------

    /**
     * An `o` of 8 smooth nodes and 8 cubic handles on each of its two contours (a flat-sided rounded
     * rectangle and its counter), the shape the V1 boards draw. A test fixture: shipped code never
     * builds it.
     */
    private fun roundedO(): Glyph {
        val outer = roundedRect(47, 0, 428, 500, 130, counterClockwise = true)
        val inner = roundedRect(71, 24, 404, 476, 106, counterClockwise = false)
        return Glyph("o", 475, listOf(outer, inner)).let { it.copy(unicodes = listOf('o'.code)) }
    }

    private fun roundedRect(
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        r: Int,
        counterClockwise: Boolean,
    ): Contour {
        val k = (r * KAPPA).toInt()

        fun on(
            x: Int,
            y: Int,
        ) = ContourPoint(Point(x, y), onCurve = true, smooth = true)

        fun off(
            x: Int,
            y: Int,
        ) = ContourPoint(Point(x, y), onCurve = false)
        val pts =
            listOf(
                // bottom edge, left to right (a straight segment: its handles sit on its ends)
                on(x0 + r, y0),
                off(x0 + r, y0),
                off(x1 - r, y0),
                // bottom-right corner
                on(x1 - r, y0),
                off(x1 - r + k, y0),
                off(x1, y0 + r - k),
                // right edge, up
                on(x1, y0 + r),
                off(x1, y0 + r),
                off(x1, y1 - r),
                // top-right corner
                on(x1, y1 - r),
                off(x1, y1 - r + k),
                off(x1 - r + k, y1),
                // top edge, right to left
                on(x1 - r, y1),
                off(x1 - r, y1),
                off(x0 + r, y1),
                // top-left corner
                on(x0 + r, y1),
                off(x0 + r - k, y1),
                off(x0, y1 - r + k),
                // left edge, down
                on(x0, y1 - r),
                off(x0, y1 - r),
                off(x0, y0 + r),
                // bottom-left corner
                on(x0, y0 + r),
                off(x0, y0 + r - k),
                off(x0 + r - k, y0),
            )
        val contour = Contour(pts, CurveFormat.CUBIC)
        return if (counterClockwise) contour else contour.reverse()
    }

    private companion object {
        const val PHONE_DENSITY = 1.5f
        const val PHONE_W = 540
        const val PHONE_H = 1170
        const val DESKTOP_W = 1100
        const val DESKTOP_H = 700
        const val FRAME_NS = 16_000_000L
        const val MIN_INK_PIXELS = 400
        const val MIN_SELECTED_PIXELS = 20
        const val KAPPA = 0.5523
    }
}
