package dev.aarso.typewright.ui.sheet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.aarso.typewright.core.geometry.Vec2
import dev.aarso.typewright.ui.tokens.CanvasTexture
import dev.aarso.typewright.ui.tokens.Typography
import dev.aarso.typewright.ui.tokens.toColor

/**
 * The sheet's world-space "ink" pass (`docs/ARCHITECTURE_REVIEW.md` §4.1 recommendation 2's
 * second `WorldPass`, drawn *above* the bloom so ink "never fades" under the glass -- UI_SPEC §5.3:
 * "letters and nodes never fade"). Real glyph outlines are P5b's job (task P4b's own brief:
 * "real font data wiring is P5b's job"); this pass draws two placeholders per room instead, both
 * anchored in world space so they pan and zoom with the sheet:
 *
 * 1. A room label (`"DRAW"`/`"SPACE"`/`"LEARN"`) -- an ordinary [BasicText], sized in screen space
 *    (its *font size* never scales with zoom, only its position does, via `Modifier.offset { }`
 *    reading [cameraState] at layout time) so it stays legible at every zoom level, matching how
 *    UI_SPEC's own room-name chip behaves.
 * 2. A simple rounded-rect glyph stand-in, drawn as a [Path] built entirely from world
 *    coordinates and projected through [SheetCamera.worldToScreen] every frame -- this one *does*
 *    scale with zoom, on purpose: it stands in for real ink (letterforms), which the review's own
 *    recommendation 3 says must live and scale in world space, unlike the chrome around it.
 *
 * All three rooms are drawn in one `Canvas` (see `GridAndMetrics.kt`'s own note on why that
 * already satisfies "compose only the current room and its neighbours").
 */
@Composable
fun WorldInkPass(
    cameraState: SheetCameraState,
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
) {
    val ink = texture.ink.toColor()

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val camera = cameraState.camera
            for (room in Room.ORDERED) {
                // Anchored near the room's own *left* edge, not its centre: a room is
                // ROOM_WIDTH_DP (1280 dp) wide, far more than a phone viewport, and both
                // TypewrightSheet's initial camera and every RoomFlight land on a room's left
                // edge ([Room.flightTargetOffset]) -- centring this placeholder at
                // `worldBounds().center` would put it off screen on the very frame it is meant
                // to demonstrate.
                val centerX = room.worldBounds().left + 180.0
                // Font-y 0..260, not the full 0..500 up to x-height: with TypewrightSheet's own
                // default camera (baseline placed DEFAULT_BASELINE_SCREEN_Y_DP down the canvas,
                // not at screen y = 0), this range stays fully on screen at zoom 1. Taller would
                // clip at the top, for the same reason a real cap-height line already does.
                val path =
                    Path().apply {
                        val corners =
                            listOf(
                                Vec2(centerX - 140.0, 0.0),
                                Vec2(centerX + 140.0, 0.0),
                                Vec2(centerX + 140.0, 260.0),
                                Vec2(centerX - 140.0, 260.0),
                            ).map { camera.worldToScreenFontUp(it) }
                        moveTo(corners[0].x.dp.toPx(), corners[0].y.dp.toPx())
                        for (corner in corners.drop(1)) lineTo(corner.x.dp.toPx(), corner.y.dp.toPx())
                        close()
                    }
                drawPath(path, color = ink, style = Stroke(width = (2.0 * camera.zoom).dp.toPx().coerceAtLeast(1f)))
            }
        }

        for (room in Room.ORDERED) {
            val centerX = room.worldBounds().left + 180.0
            // Just below the baseline (font-y -40, inside the descender's own headroom, well
            // clear of the shape sitting above the baseline), still comfortably on screen with
            // the default camera.
            Box(
                modifier =
                    Modifier.offset {
                        val screen = cameraState.camera.worldToScreenFontUp(Vec2(centerX, -40.0))
                        IntOffset(screen.x.dp.roundToPx() - 80, screen.y.dp.roundToPx())
                    },
            ) {
                BasicText(text = room.name, style = Typography.roomPlaceholder.copy(color = ink))
            }
        }
    }
}
