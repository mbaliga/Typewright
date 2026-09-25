// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.ui.sheet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.aarso.typewright.core.geometry.Vec2
import dev.aarso.typewright.ui.tokens.CanvasTexture
import dev.aarso.typewright.ui.tokens.GridToken
import dev.aarso.typewright.ui.tokens.Typography
import dev.aarso.typewright.ui.tokens.toColor
import kotlin.math.floor

/**
 * Placeholder metric lines (task P4b item 2: "metric lines ... as 1 dp hairlines at ink 20% --
 * real font data wiring is P5b's job"). Values transcribed from the explorer's own Draw-room
 * screenshot (`draw-wide.png`: "descender -210", "baseline 0", "x-height 500", cap-height read
 * from the clipped "...eight 700" label at the canvas edge) -- not measured from any font by this
 * task, and not the fixtures CLAUDE.md's own conventions section pins (those are node counts, not
 * metrics). [aboveBaselineDp] is in the font-unit convention CLAUDE.md's own "Conventions"
 * section states ("y up"): positive values sit visually *above* the baseline.
 */
data class PlaceholderMetrics(
    val descender: Double = -210.0,
    val baseline: Double = 0.0,
    val xHeight: Double = 500.0,
    val capHeight: Double = 700.0,
) {
    val lines: List<Pair<String, Double>>
        get() =
            listOf(
                "descender ${descender.toInt()}" to descender,
                "baseline ${baseline.toInt()}" to baseline,
                "x-height ${xHeight.toInt()}" to xHeight,
                "cap-height ${capHeight.toInt()}" to capHeight,
            )
}

/**
 * `SheetCamera`'s own `worldToScreen` is a plain, unflipped scale+translate (see its own KDoc:
 * "Nothing here is a Compose type", no axis convention asserted). Placeholder metrics and
 * placeholder room ink (`RoomInk.kt`) are the only places in this task that draw *font-convention*
 * y-up values (CLAUDE.md "Conventions": "y up") into that world, so this is the one shared helper
 * that negates y before handing a value to the camera -- everything else in `sheet/` (room
 * layout, grid) treats world y exactly like screen y, matching [Room]'s own x-only layout.
 */
internal fun SheetCamera.worldToScreenFontUp(worldFontUp: Vec2): Vec2 = worldToScreen(Vec2(worldFontUp.x, -worldFontUp.y))

/**
 * The sheet's world-space "lines" pass (`docs/ARCHITECTURE_REVIEW.md` §4.1 recommendation 2:
 * `Box { WorldPass(lines); Bloom(); WorldPass(ink); Glass() }`, first of the two `WorldPass`es).
 * Draws [texture]'s own grid -- the standard 28 dp grid (UI_SPEC §1 layer 2) for every texture
 * except blueprint, which draws its own 24 dp [dev.aarso.typewright.ui.tokens.FineGrid] instead
 * (see [dev.aarso.typewright.ui.tokens.CanvasTexture.fineGrid]'s own KDoc) -- and the placeholder
 * metric hairlines (layer 3) for every room, culled to whatever the camera currently has on
 * screen.
 *
 * **Reads [cameraState] during draw, not composition or layout** (`docs/ARCHITECTURE_REVIEW.md`
 * §4.1: "Read the camera late... A Canvas/drawBehind that reads the camera directly skips both
 * composition and layout"): [cameraState.camera] is read *inside* the `Canvas` draw lambda below,
 * not hoisted into a parameter this composable function recomposes on. Line and grid positions
 * are computed by projecting world coordinates through [SheetCamera.worldToScreen] every draw
 * call rather than via a `graphicsLayer` scale transform, so stroke widths stay a fixed number of
 * pixels regardless of zoom (the review's "Stroke.HairlineWidth is one pixel; the spec's 1 dp
 * line is `1.dp.toPx()`" -- a `graphicsLayer` scale would scale stroke width too, which is wrong
 * for grid/metric lines specifically; it is the right tool for *ink* content that should scale,
 * see `RoomInk.kt`). All three rooms are considered in one pass (no per-room composable), so
 * "compose only the current room and its neighbours" (task P4b item 1) is met trivially: nothing
 * off the visible world rect is ever iterated, let alone composed.
 */
@Composable
fun WorldLinesPass(
    cameraState: SheetCameraState,
    texture: CanvasTexture,
    metrics: PlaceholderMetrics = PlaceholderMetrics(),
    modifier: Modifier = Modifier,
) {
    // CanvasTokens.kt's own KDoc: blueprint draws its *own* FineGrid instead of the standard grid
    // (UI_SPEC §2's grain column for blueprint: "0 (+ 24 dp grid at white 6%)" -- one grid, not
    // two), so this picks whichever one `texture` actually has rather than always drawing the
    // standard 28 dp grid regardless of texture.
    val fineGrid = texture.fineGrid
    val gridColor = (fineGrid?.color ?: GridToken.colorFor(texture)).toColor()
    val gridStep = fineGrid?.sizeDp ?: GridToken.SIZE_DP
    val lineColor = texture.line.toColor()

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val camera = cameraState.camera
            val widthDp =
                size.width
                    .toDp()
                    .value
                    .toDouble()
            val heightDp =
                size.height
                    .toDp()
                    .value
                    .toDouble()
            val topLeftWorld = camera.screenToFont(Vec2(0.0, 0.0))
            val bottomRightWorld = camera.screenToFont(Vec2(widthDp, heightDp))

            val strokeWidthPx = 1.dp.toPx()
            var x = floor(topLeftWorld.x / gridStep) * gridStep
            while (x <= bottomRightWorld.x) {
                val screenX =
                    camera
                        .worldToScreen(Vec2(x, 0.0))
                        .x.dp
                        .toPx()
                drawLine(gridColor, Offset(screenX, 0f), Offset(screenX, size.height), strokeWidth = strokeWidthPx)
                x += gridStep
            }
            var y = floor(topLeftWorld.y / gridStep) * gridStep
            while (y <= bottomRightWorld.y) {
                val screenY =
                    camera
                        .worldToScreen(Vec2(0.0, y))
                        .y.dp
                        .toPx()
                drawLine(gridColor, Offset(0f, screenY), Offset(size.width, screenY), strokeWidth = strokeWidthPx)
                y += gridStep
            }

            // UI_SPEC §1 layer 3: "1 dp hairline at ink 20%", one per placeholder metric.
            for ((_, value) in metrics.lines) {
                val screenY =
                    camera
                        .worldToScreenFontUp(Vec2(0.0, value))
                        .y.dp
                        .toPx()
                if (screenY in -strokeWidthPx..(size.height + strokeWidthPx)) {
                    drawLine(lineColor, Offset(0f, screenY), Offset(size.width, screenY), strokeWidth = strokeWidthPx)
                }
            }
        }

        // Labels: "mono 22 units of the glyph box (~9.5 sp), right-aligned at the canvas edge"
        // (UI_SPEC §1 layer 3) -- ordinary composables, not drawn on the Canvas above, so their
        // *size* never scales with zoom (review recommendation 3: "labels [drawn] in screen
        // space"); only their y-position tracks the camera, via `Modifier.offset { }`'s
        // layout-phase (not composition-phase) read.
        for ((label, value) in metrics.lines) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .offset {
                            // Read inside the layout-phase lambda, not hoisted, so this label's
                            // position updates on every camera change without recomposing this
                            // composable (same rationale as the Canvas above).
                            val screenY = cameraState.camera.worldToScreenFontUp(Vec2(0.0, value)).y
                            IntOffset(0, screenY.dp.roundToPx() - 8)
                        },
                contentAlignment = Alignment.TopEnd,
            ) {
                BasicText(
                    text = label,
                    style = Typography.mono(sizeSp = 9.5).copy(color = texture.muted.toColor()),
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
    }
}
