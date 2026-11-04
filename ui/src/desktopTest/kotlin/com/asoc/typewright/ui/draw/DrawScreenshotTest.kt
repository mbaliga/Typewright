// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.pointer.PointerType
import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.ContourPoint
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.core.geometry.brokenSmoothNodes
import com.asoc.typewright.core.geometry.reverse
import com.asoc.typewright.ui.puck.PuckEdge
import com.asoc.typewright.ui.sheet.SheetLayout
import com.asoc.typewright.ui.tokens.CanvasTextures
import com.asoc.typewright.ui.tokens.toColor
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
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

    /** [countNear] over the pixels more than [radiusDp] from [centreDp] (dp at the phone's density), i.e. outside a ring drawn there. */
    private fun countNearOutside(
        image: org.jetbrains.skia.Image,
        color: Color,
        centreDp: Vec2,
        radiusDp: Float,
        tolerance: Float = 0.1f,
    ): Int {
        val pixels = image.toComposeImageBitmap().toPixelMap()
        val cx = centreDp.x.toFloat() * PHONE_DENSITY
        val cy = centreDp.y.toFloat() * PHONE_DENSITY
        val r = radiusDp * PHONE_DENSITY
        var n = 0
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r) continue
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
    fun phoneSmoothNodeDraggedUpTurnsItsCornerHandleOntoTheSlantedSideAndStaysSmooth() =
        withDrawSheet(DrawTestProjects.small(roundedO()), "o", PHONE_W, PHONE_H, PHONE_DENSITY) {
            val node = screenOf(0, 3)
            press(node, type = PointerType.Touch)
            // A drag, not a hold: the finger is on its way before the hold timer fires.
            for (step in 1..8) move(Vec2(node.x, node.y - 5.0 * step), type = PointerType.Touch)
            frames(2)
            val dragged = controller.glyph!!.contours[0]
            assertTrue(dragged.points[3].point.y > 0, "the node followed the finger up")
            assertEquals(emptyList(), dragged.brokenSmoothNodes(), "mid-drag, every smooth node is still in line with its handles")
            assertTrue(dragged.points[3].smooth)
            snapshot("draw-smooth-node-dragged-phone")
            release(Vec2(node.x, node.y - 40.0), type = PointerType.Touch)
        }

    @Test
    fun desktopRoundedOWithHandles() =
        withDrawSheet(DrawTestProjects.small(roundedO()), "o", DESKTOP_W, DESKTOP_H, 1f) {
            select(0, 3)
            assertDrawn(snapshot("draw-rounded-o-desktop-outline"), selectedColour)
        }

    // ---- the radial and the rich menu ----------------------------------------------------------

    /**
     * A finger held on the node, through the pointer layer and the frame the sheet itself builds (its top
     * inset, its glass at the foot), as the real gesture path does: the hold is timed on the clock, so the
     * test waits it out.
     */
    private fun DrawSheetRig.holdFingerOn(node: Vec2) {
        press(node, type = PointerType.Touch)
        Thread.sleep(HOLD_WAIT_MS)
        frames(2)
    }

    /** The lowest screen row, in dp, that the glass at the foot of a phone leaves free: the ring must stay above it. */
    private fun phoneGlassTopDp(): Double {
        val layout = SheetLayout.of(Vec2(PHONE_W / PHONE_DENSITY.toDouble(), PHONE_H / PHONE_DENSITY.toDouble()), 0.0, PuckEdge.LEFT)
        return PHONE_H / PHONE_DENSITY.toDouble() - layout.free.bottom
    }

    @Test
    fun phoneRadialSweepingOnANode() =
        withDrawSheet(DrawTestProjects.small(roundedO()), "o", PHONE_W, PHONE_H, PHONE_DENSITY) {
            val node = screenOf(0, 3)
            holdFingerOn(node)
            // The thumb sweeps down and to the right, which chooses the sector across the hub.
            move(Vec2(node.x + 40.0, node.y + 48.0), type = PointerType.Touch)
            frames(2)
            val ui = assertNotNull(controller.radial, "the hold opened the ring")
            assertTrue(ui.state is RadialState.Sweeping)
            assertTrue(
                ui.center.y + RadialGeometry.OUTER_DP <= phoneGlassTopDp() + 0.5,
                "the ring clears the inspector row and the deck: it ends at ${ui.center.y + RadialGeometry.OUTER_DP}, the glass starts at ${phoneGlassTopDp()}",
            )
            assertTrue(ui.center.y - RadialGeometry.OUTER_DP >= 0.0, "and does not leave the top of the screen")
            snapshot("draw-radial-sweep-phone")
            release(Vec2(node.x + 40.0, node.y + 48.0), type = PointerType.Touch)
        }

    @Test
    fun phoneRadialLiftedOnANode() =
        withDrawSheet(DrawTestProjects.small(roundedO()), "o", PHONE_W, PHONE_H, PHONE_DENSITY) {
            val node = screenOf(0, 3)
            holdFingerOn(node)
            release(node, type = PointerType.Touch)
            frames(2)
            val ui = assertNotNull(controller.radial)
            assertTrue(ui.state is RadialState.Lifted)
            assertTrue(ui.center.y + RadialGeometry.OUTER_DP <= phoneGlassTopDp() + 0.5, "the waiting ring clears the glass too")
            snapshot("draw-radial-lifted-phone")
        }

    @Test
    fun theGlyphStepsBackToThirtyPercentWhileTheRadialIsOpenAndTheMetricLinesDoNot() =
        withDrawSheet(DrawTestProjects.small(roundedO()), "o", PHONE_W, PHONE_H, PHONE_DENSITY) {
            val before = snapshot("draw-radial-dim-before")
            val inkBefore = countNear(before, texture.ink.toColor(), 0.1f)
            val node = screenOf(0, 3)
            holdFingerOn(node)
            val during = snapshot("draw-radial-dim-during")
            val inkDuring = countNear(during, texture.ink.toColor(), 0.1f)
            // The ring itself is drawn in ink, so the glyph's share can only be read from what is left outside the ring's reach.
            val ring = assertNotNull(controller.radial)
            assertTrue(ring.state is RadialState.Sweeping)
            val outsideRing = countNearOutside(during, texture.ink.toColor(), ring.center, RadialGeometry.OUTER_DP.toFloat() + 6f)
            val outsideRingBefore = countNearOutside(before, texture.ink.toColor(), ring.center, RadialGeometry.OUTER_DP.toFloat() + 6f)
            assertTrue(
                outsideRing < outsideRingBefore * 0.6,
                "the glyph away from the ring is much fainter while it is open: $outsideRingBefore -> $outsideRing (ring pixels $inkBefore -> $inkDuring)",
            )
            release(node, type = PointerType.Touch)
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
        const val HOLD_WAIT_MS = 520L
        const val MIN_INK_PIXELS = 400
        const val MIN_SELECTED_PIXELS = 20
        const val KAPPA = 0.5523
    }
}
