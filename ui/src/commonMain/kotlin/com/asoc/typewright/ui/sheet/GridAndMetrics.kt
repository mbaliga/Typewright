// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.sheet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.GridToken
import com.asoc.typewright.ui.tokens.toColor
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * `SheetCamera`'s own `worldToScreen` is a plain, unflipped scale+translate (see its own KDoc:
 * "Nothing here is a Compose type", no axis convention asserted). Font-convention values (y up) are
 * drawn into that world through this one helper, which negates y before handing a value to the
 * camera; everything else in `sheet/` (room layout, grid) treats world y exactly like screen y.
 */
internal fun SheetCamera.worldToScreenFontUp(worldFontUp: Vec2): Vec2 = worldToScreen(Vec2(worldFontUp.x, -worldFontUp.y))

/** How the grid spaces itself so it is never a smear of lines: a rule per zoom, with no Compose in it. */
object GridSteps {
    /** The least gap, in screen dp, between two grid lines that is still worth drawing. */
    const val MIN_GAP_DP: Double = 8.0

    /** The most lines the grid will ever draw along one axis, whatever the zoom and the window. */
    const val MAX_LINES: Int = 200

    /**
     * The world spacing of the grid at [zoom]: [base] while its lines are at least [MIN_GAP_DP] apart
     * on screen, doubled until they are. So zooming out thins the grid by whole steps instead of
     * packing hundreds of hairlines into a few pixels.
     */
    fun stepFor(
        zoom: Double,
        base: Double,
    ): Double {
        var step = base
        while (step * zoom < MIN_GAP_DP && step < MAX_STEP) step *= 2.0
        return step
    }

    /** The grid-line coordinates (multiples of [step]) that fall in the visible range `[from, to]`, at most [MAX_LINES] of them. */
    fun linesIn(
        from: Double,
        to: Double,
        step: Double,
    ): List<Double> {
        if (to < from || step <= 0.0) return emptyList()
        val first = ceil(from / step)
        val last = floor(to / step)
        if (last < first) return emptyList()
        val count = min((last - first).toLong() + 1L, MAX_LINES.toLong()).toInt()
        return List(count) { (first + it) * step }
    }

    private const val MAX_STEP = 1.0e9
}

/**
 * The grid around the glyph being edited (INTERACTION_V1 S2): the design space's own grid, densest at
 * the letter and dissolving radially into paper, drawn only when [focus] says where the letter is.
 * With no glyph under edit there is no grid. It is the sheet's bottom layer, under the bloom, so it is
 * the one thing that dissolves near the glass (C4); the metric lines and the ink are drawn above.
 *
 * The camera is read inside the draw lambda, never in composition, and only the lines in view are
 * generated ([GridSteps.linesIn]), however far the sheet is zoomed out.
 */
@Composable
fun WorldGridPass(
    cameraState: SheetCameraState,
    texture: CanvasTexture,
    focus: () -> GridFocus?,
    modifier: Modifier = Modifier,
) {
    // CanvasTokens.kt: blueprint draws its own FineGrid instead of the standard grid.
    val fineGrid = texture.fineGrid
    val gridColor = (fineGrid?.color ?: GridToken.colorFor(texture)).toColor()
    val baseStep = fineGrid?.sizeDp ?: GridToken.SIZE_DP
    val ink = texture.ink.toColor()
    Box(modifier = modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
            val where = focus() ?: return@Canvas
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
            val topLeft = camera.screenToFont(Vec2(0.0, 0.0))
            val bottomRight = camera.screenToFont(Vec2(widthDp, heightDp))
            val step = GridSteps.stepFor(camera.zoom, baseStep)
            val strokeWidthPx = 1.dp.toPx()
            for (x in GridSteps.linesIn(topLeft.x, bottomRight.x, step)) {
                val sx =
                    camera
                        .worldToScreen(Vec2(x, 0.0))
                        .x.dp
                        .toPx()
                drawLine(gridColor, Offset(sx, 0f), Offset(sx, size.height), strokeWidth = strokeWidthPx)
            }
            for (y in GridSteps.linesIn(topLeft.y, bottomRight.y, step)) {
                val sy =
                    camera
                        .worldToScreen(Vec2(0.0, y))
                        .y.dp
                        .toPx()
                drawLine(gridColor, Offset(0f, sy), Offset(size.width, sy), strokeWidth = strokeWidthPx)
            }
            // The dissolve: keep the grid where the letter is and fade it to nothing by the time it is one and a half letters away.
            val centre =
                Offset(
                    where.centreDp.x.dp
                        .toPx(),
                    where.centreDp.y.dp
                        .toPx(),
                )
            val radius = max(where.radiusDp.dp.toPx(), 1f)
            drawRect(
                brush =
                    Brush.radialGradient(
                        0f to ink,
                        GRID_SOLID_FRACTION to ink,
                        1f to ink.copy(alpha = 0f),
                        center = centre,
                        radius = radius,
                    ),
                blendMode = BlendMode.DstIn,
            )
        }
    }
}

/** Where the letter is on screen and how far the grid reaches from it, in screen dp. */
data class GridFocus(
    val centreDp: Vec2,
    val radiusDp: Double,
)

private const val GRID_SOLID_FRACTION = 0.3f
