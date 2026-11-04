// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.check

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.ui.project.GlyphPathCache
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.accent
import com.asoc.typewright.ui.tokens.toColor

private const val GRID_ALPHA = 0.08f
private const val WHISKER_ALPHA = 0.45f
private const val BOX_ALPHA = 0.16f
private const val FACE_ALPHA = 0.5f
private const val MINIMUM_BOX_DP = 2f
private const val CAP_DP = 3f
private const val POINT_RADIUS_DP = 4.5f
private const val POINT_RING_DP = 2f
private const val FACE_RADIUS_DP = 2.6f

/**
 * A node-economy box plot drawn on the shared log axis (3 to 3,000): the grid, the whiskers' full range, the
 * middle half as a box with its median, and the person's own count as the accent point with a ring of the
 * canvas colour round it. Everything is placed by [spec]'s [marks], so the screen holds no maths of its own.
 * When [faces] are given each reference face is a small dot under the box, at its own count.
 *
 * [centerY] is the height of the box's centre line from the top; [boxHeight] its thickness. The plot is as
 * wide as [modifier] makes it and [height] tall. It reads as a single image to a screen reader, saying the
 * glyph's count, the box and the whiskers in [glyph]'s name.
 */
@Composable
fun BoxPlot(
    spec: BoxPlotSpec,
    glyph: String,
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
    height: Dp = 31.dp,
    centerY: Dp = height / 2,
    boxHeight: Dp = 10.dp,
    faces: List<FaceDot> = emptyList(),
) {
    val marks = remember(spec) { spec.marks() }
    val ink = texture.ink.toColor()
    val accent = texture.accent.toColor()
    val canvasColor = texture.canvas.toColor()
    Canvas(modifier.height(height).semantics { contentDescription = spec.description(glyph) }) {
        val w = size.width.toDouble()
        val cy = centerY.toPx()
        val bh = boxHeight.toPx()

        fun x(fraction: Double) = (fraction * w).toFloat()
        // The grid: one hairline at every tick.
        for (tick in LogAxis.TICKS) {
            val gx = x(LogAxis.position(tick.toDouble()))
            drawLine(ink.copy(alpha = GRID_ALPHA), Offset(gx, 0f), Offset(gx, size.height), strokeWidth = 1f)
        }
        // The whiskers, each with its cap, and the box between the quartiles.
        val whisker = ink.copy(alpha = WHISKER_ALPHA)
        val cap = CAP_DP.dp.toPx()
        drawLine(whisker, Offset(x(marks.whiskerLow), cy), Offset(x(marks.q1), cy), strokeWidth = 1f)
        drawLine(whisker, Offset(x(marks.q3), cy), Offset(x(marks.whiskerHigh), cy), strokeWidth = 1f)
        drawLine(whisker, Offset(x(marks.whiskerLow), cy - cap), Offset(x(marks.whiskerLow), cy + cap), strokeWidth = 1f)
        drawLine(whisker, Offset(x(marks.whiskerHigh), cy - cap), Offset(x(marks.whiskerHigh), cy + cap), strokeWidth = 1f)
        val boxWidth = marks.boxWidth(w, MINIMUM_BOX_DP.dp.toPx().toDouble()).toFloat()
        drawRect(ink.copy(alpha = BOX_ALPHA), Offset(x(marks.q1), cy - bh / 2), Size(boxWidth, bh))
        val mx = x(marks.median)
        drawLine(ink, Offset(mx, cy - bh / 2 - 1.dp.toPx()), Offset(mx, cy + bh / 2 + 1.dp.toPx()), strokeWidth = 1.5.dp.toPx())
        // The reference faces, then the person's own point on top.
        for (dot in faces) {
            drawCircle(ink.copy(alpha = FACE_ALPHA), FACE_RADIUS_DP.dp.toPx(), Offset(x(dot.fraction), dot.y.dp.toPx()))
        }
        val px = x(marks.point)
        drawCircle(canvasColor, (POINT_RADIUS_DP + POINT_RING_DP / 2).dp.toPx(), Offset(px, cy))
        drawCircle(accent, POINT_RADIUS_DP.dp.toPx() - POINT_RING_DP.dp.toPx() / 2, Offset(px, cy))
    }
}

/**
 * The axis that sits above a list of box plots: a label at every tick (`4 8 16 … 512 1k 2k`), in the same
 * horizontal span as the plots below it so each label stands over its grid line.
 */
@Composable
fun BoxPlotAxis(
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val muted = texture.muted.toColor()
    val style = remember(muted) { TextStyle(fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = muted) }
    Canvas(modifier.height(16.dp)) {
        for (tick in LogAxis.TICKS) {
            val label = measurer.measure(LogAxis.label(tick), style)
            val cx = (LogAxis.position(tick.toDouble()) * size.width).toFloat()
            drawText(label, topLeft = Offset(cx - label.size.width / 2f, 2.dp.toPx()))
        }
    }
}

/** Draws [glyph]'s outline from the project's own font, [sizeDp] tall for an em, in [color], its centre over the middle of the box. */
@Composable
fun GlyphMark(
    glyph: Glyph?,
    unitsPerEm: Int,
    paths: GlyphPathCache,
    color: Color,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 19.dp,
    boxWidth: Dp = 28.dp,
    boxHeight: Dp = 26.dp,
) {
    Canvas(modifier.size(boxWidth, boxHeight)) {
        if (glyph == null || glyph.contours.isEmpty()) return@Canvas
        val scale = sizeDp.toPx() / unitsPerEm
        val left = (size.width - glyph.advanceWidth * scale) / 2f
        val baseline = size.height * BASELINE_FRACTION
        withTransform({
            translate(left, baseline)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            drawPath(paths.pathOf(glyph), color)
        }
    }
}

private const val BASELINE_FRACTION = 0.76f
