// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asoc.typewright.ui.brief.bodyStyle
import com.asoc.typewright.ui.brief.monoStyle
import com.asoc.typewright.ui.theme.colorOf
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.ThemeRole
import com.asoc.typewright.ui.tokens.toColor

/**
 * The theme editor's live preview (S22): the `o` with its nodes, handles, a snap target, metric,
 * guide, construction and grid lines; the four comparison layers in their line patterns; a box-plot
 * row with the person's own point; and one check item. It is drawn entirely from [texture], so it
 * shows exactly what the theme will do, and every role is shown with the shape that goes with it
 * (law 8: colour is never the only signal).
 */
@Composable
fun ThemePreview(
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
) {
    val canvas = texture.canvas.toColor()
    Column(
        modifier = modifier.fillMaxWidth().background(canvas).padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(PREVIEW_HEIGHT.dp)) { drawGlyphScene(texture) }
        Canvas(modifier = Modifier.fillMaxWidth().height(COMPARISON_HEIGHT.dp)) { drawComparison(texture) }
        Canvas(modifier = Modifier.fillMaxWidth().height(PLOT_HEIGHT.dp)) { drawBoxPlot(texture) }
        Column(modifier = Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            BasicText(
                text = "○ Contrast is inside what professionals do",
                style = monoStyle(texture.ink.toColor(), sizeSp = 11.0).copy(letterSpacing = 0.sp),
            )
            BasicText(
                text = "A check item: a severity shape, a mono title and one sentence.",
                style = bodyStyle(texture.muted.toColor(), sizeSp = 12.0),
            )
        }
    }
}

private const val PREVIEW_HEIGHT = 170
private const val COMPARISON_HEIGHT = 34
private const val PLOT_HEIGHT = 30

private fun DrawScope.px(dp: Float): Float = dp * density

private fun DrawScope.drawGlyphScene(texture: CanvasTexture) {
    val w = size.width
    val h = size.height
    val ink = texture.ink.toColor()
    val grid = texture.colorOf(ThemeRole.GRID_LINES).toColor().copy(alpha = (texture.gridOpacity ?: 0.085).toFloat().coerceAtLeast(0.2f))
    // Grid: dots.
    var gx = px(14f)
    while (gx < w) {
        var gy = px(14f)
        while (gy < h) {
            drawCircle(grid, px(0.9f), Offset(gx, gy))
            gy += px(14f)
        }
        gx += px(14f)
    }
    val cx = w * 0.42f
    val cy = h * 0.5f
    val rx = px(46f)
    val ry = px(58f)
    // Metric lines (solid), a guide (long dash), construction (short dash with centre marks).
    val metric = texture.colorOf(ThemeRole.METRIC_LINES).toColor()
    drawLine(metric, Offset(0f, cy + ry), Offset(w, cy + ry), px(1f))
    drawLine(metric, Offset(0f, cy - ry), Offset(w, cy - ry), px(1f))
    drawLine(
        texture.colorOf(ThemeRole.GUIDES).toColor(),
        Offset(cx - rx - px(40f), cy),
        Offset(cx + rx + px(60f), cy),
        px(1f),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(px(10f), px(6f))),
    )
    val construction = texture.colorOf(ThemeRole.CONSTRUCTION_LINES).toColor()
    drawLine(
        construction,
        Offset(cx, cy - ry - px(10f)),
        Offset(cx, cy + ry + px(10f)),
        px(1f),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(px(3f), px(3f))),
    )
    drawLine(construction, Offset(cx - px(6f), cy), Offset(cx + px(6f), cy), px(1f))
    // The o.
    drawOval(ink, topLeft = Offset(cx - rx, cy - ry), size = Size(rx * 2, ry * 2), style = Stroke(px(2.4f)))
    val smooth = texture.colorOf(ThemeRole.SMOOTH_NODE).toColor()
    val off = texture.colorOf(ThemeRole.OFF_CURVE).toColor()
    val handle = texture.colorOf(ThemeRole.HANDLE_LINE).toColor()
    val extrema =
        listOf(
            Offset(cx, cy - ry) to Offset(1f, 0f),
            Offset(cx + rx, cy) to Offset(0f, 1f),
            Offset(cx, cy + ry) to Offset(1f, 0f),
            Offset(cx - rx, cy) to Offset(0f, 1f),
        )
    for ((p, dir) in extrema) {
        val reach = px(24f)
        for (sign in listOf(-1f, 1f)) {
            val hp = Offset(p.x + dir.x * reach * sign, p.y + dir.y * reach * sign)
            drawLine(handle, p, hp, px(1f))
            drawCircle(canvasHole(texture), px(3f), hp)
            drawCircle(off, px(3f), hp, style = Stroke(px(1.2f)))
        }
        drawCircle(smooth, px(4.5f), p)
    }
    // A corner node (square), the selected node (ring), a snap target (crosshair).
    val corner = texture.colorOf(ThemeRole.CORNER_NODE).toColor()
    val cp = Offset(cx - rx - px(30f), cy + ry)
    drawRect(corner, Offset(cp.x - px(4.5f), cp.y - px(4.5f)), Size(px(9f), px(9f)))
    val selected = texture.colorOf(ThemeRole.SELECTED_NODE).toColor()
    drawCircle(selected, px(9f), Offset(cx + rx, cy), style = Stroke(px(2f)))
    val snap = texture.colorOf(ThemeRole.SNAP_TARGET).toColor()
    val sp = Offset(cx + rx + px(46f), cy - ry)
    drawLine(snap, Offset(sp.x - px(9f), sp.y), Offset(sp.x + px(9f), sp.y), px(1.5f))
    drawLine(snap, Offset(sp.x, sp.y - px(9f)), Offset(sp.x, sp.y + px(9f)), px(1.5f))
}

private fun canvasHole(texture: CanvasTexture): Color = texture.canvas.toColor()

private fun DrawScope.drawComparison(texture: CanvasTexture) {
    val patterns =
        listOf(
            null,
            floatArrayOf(px(9f), px(5f)),
            floatArrayOf(px(1.5f), px(4f)),
            floatArrayOf(px(10f), px(4f), px(1.5f), px(4f)),
        )
    val roles = listOf(ThemeRole.COMPARISON_1, ThemeRole.COMPARISON_2, ThemeRole.COMPARISON_3, ThemeRole.COMPARISON_4)
    val gap = size.width / 4f
    for (i in 0 until 4) {
        val y = size.height / 2
        val x0 = gap * i + px(14f)
        val effect = patterns[i]?.let { PathEffect.dashPathEffect(it) }
        drawLine(texture.colorOf(roles[i]).toColor(), Offset(x0, y), Offset(x0 + gap - px(26f), y), px(2.2f), pathEffect = effect)
    }
}

private fun DrawScope.drawBoxPlot(texture: CanvasTexture) {
    val ink = texture.ink.toColor()
    val y = size.height / 2
    val x0 = px(14f)
    val x1 = size.width - px(14f)
    val span = x1 - x0
    drawLine(ink.copy(alpha = 0.45f), Offset(x0 + span * 0.08f, y), Offset(x0 + span * 0.92f, y), px(1f))
    drawRect(ink.copy(alpha = 0.16f), Offset(x0 + span * 0.3f, y - px(9f)), Size(span * 0.4f, px(18f)))
    drawLine(ink, Offset(x0 + span * 0.5f, y - px(9f)), Offset(x0 + span * 0.5f, y + px(9f)), px(1.5f))
    val point = Offset(x0 + span * 0.62f, y)
    drawCircle(texture.canvas.toColor(), px(6.5f), point)
    drawCircle(texture.accentFor(), px(4.5f), point)
}

private fun CanvasTexture.accentFor(): Color = colorOf(ThemeRole.ACCENT).toColor()
