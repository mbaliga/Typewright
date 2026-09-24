package dev.aarso.typewright.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import dev.aarso.typewright.core.geometry.Vec2
import dev.aarso.typewright.ui.puck.PuckGestureState
import dev.aarso.typewright.ui.puck.PuckPinState
import dev.aarso.typewright.ui.puck.rememberPuckUiState
import dev.aarso.typewright.ui.sheet.Room
import dev.aarso.typewright.ui.sheet.TypewrightSheet
import dev.aarso.typewright.ui.tokens.CanvasTextures
import dev.aarso.typewright.ui.tokens.toColor
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Task P4b item 9: renders [TypewrightSheet] at a phone-ish size (360x780 dp, the explorer's own
 * phone frame -- UI_SPEC §2 "Spacing", `draw-wide.png`) in the four states the task names, via
 * the same [ScreenshotHarness] `PlaceholderScreenshotTest` already used (extended, not replaced).
 *
 * These are visual regression smoke tests (PNG gets written, a handful of gross pixel facts hold:
 * a paper/blueprint corner), not pixel-perfect comparisons against the explorer -- that comparison
 * was done by hand, by eye, against `draw-wide.png`/`space-wide.png`/`texture-blueprint.png`, and
 * is reported in this task's own summary (not encoded as an assertion here, since "does this look
 * like the explorer" is not a thing a unit test checks).
 *
 * GESTURE HONESTY (CLAUDE.md law 4): screenshot (b) renders the radial *open* by pre-seeding
 * [PuckGestureState.RadialOpen] directly onto a [dev.aarso.typewright.ui.puck.PuckUiState] passed
 * into [TypewrightSheet] -- proving the *rendering* of that state, not that a real hold gesture
 * reaches it on a device (that is `PuckGestureMachineTest`'s job, and the device's, per law 4).
 */
class SheetScreenshotTest {
    private val phoneWidthPx = 720
    private val phoneHeightPx = 1560
    private val phoneDensity = 2f
    private val phoneSizeDp = Vec2(360.0, 780.0)

    @Test
    fun drawRoomPaperTexturePuckAtRest() {
        val shot =
            ScreenshotHarness.capture(name = "draw-paper-rest", width = phoneWidthPx, height = phoneHeightPx, density = phoneDensity) {
                TypewrightSheet(initialRoom = Room.DRAW, initialTexture = CanvasTextures.PAPER)
            }
        assertTrue(shot.file.length() > 0, "PNG written to ${shot.file}")
        assertCornerNear(shot, CanvasTextures.PAPER.canvas.toColor())
    }

    @Test
    fun drawRoomRadialDialOpen() {
        val hubCenter = PuckPinState.defaultFor(phoneSizeDp)
        val shot =
            ScreenshotHarness.capture(name = "draw-radial-open", width = phoneWidthPx, height = phoneHeightPx, density = phoneDensity) {
                val puckState = rememberPuckUiState()
                puckState.gestureState =
                    PuckGestureState.RadialOpen(
                        hubCenter = hubCenter,
                        referenceAngleDegrees = -40.0,
                        accumulatedAngleDegrees = 70.0,
                        lastTickedSectorRaw = 1,
                        lastPosition = Vec2(hubCenter.x + 70.0, hubCenter.y - 70.0),
                        sectorsCount = 8,
                    )
                TypewrightSheet(initialRoom = Room.DRAW, initialTexture = CanvasTextures.PAPER, puckState = puckState)
            }
        assertTrue(shot.file.length() > 0, "PNG written to ${shot.file}")
        assertCornerNear(shot, CanvasTextures.PAPER.canvas.toColor())
    }

    @Test
    fun spaceRoomPannedByRoomSwitch() {
        val shot =
            ScreenshotHarness.capture(name = "space-panned", width = phoneWidthPx, height = phoneHeightPx, density = phoneDensity) {
                TypewrightSheet(initialRoom = Room.SPACE, initialTexture = CanvasTextures.PAPER)
            }
        assertTrue(shot.file.length() > 0, "PNG written to ${shot.file}")
        assertCornerNear(shot, CanvasTextures.PAPER.canvas.toColor())
    }

    @Test
    fun blueprintTexture() {
        val shot =
            ScreenshotHarness.capture(name = "draw-blueprint", width = phoneWidthPx, height = phoneHeightPx, density = phoneDensity) {
                TypewrightSheet(initialRoom = Room.DRAW, initialTexture = CanvasTextures.BLUEPRINT)
            }
        assertTrue(shot.file.length() > 0, "PNG written to ${shot.file}")
        assertCornerNear(shot, CanvasTextures.BLUEPRINT.canvas.toColor())
    }

    private fun assertCornerNear(
        shot: Screenshot,
        expected: Color,
    ) {
        val pixels = shot.bitmap.toPixelMap()
        val corner = pixels[4, 4]
        val near =
            abs(corner.red - expected.red) < 0.05f && abs(corner.green - expected.green) < 0.05f && abs(corner.blue - expected.blue) < 0.05f
        assertTrue(near, "corner colour $corner not near expected canvas colour $expected")
    }
}
