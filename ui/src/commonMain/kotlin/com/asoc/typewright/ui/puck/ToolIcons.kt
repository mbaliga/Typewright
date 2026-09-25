// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.puck

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * The eight [Tool] icons, traced faithfully (CLAUDE.md law 6: "reproduce it; do not improvise a
 * component's look") from `ui/typewright-explorer.html`'s own `<symbol id="ic-*">` path data
 * (grep `id="ic-select"`, around lines 552-559), each defined on a 20x20 `viewBox`. The
 * explorer's own CSS (`.tool svg{width:20px;height:20px;fill:none;stroke:currentColor;
 * stroke-width:1.6;stroke-linecap:round;stroke-linejoin:round}`, `.puck>svg{width:26px;...}`,
 * `.pmenu button svg{width:18px;...}`) draws every one of them as an **unfilled, round-capped,
 * round-joined stroke**, never a fill -- so this function strokes every path, never fills one,
 * matching that rule exactly (including `ic-select`'s own closed cursor-arrow path, which reads
 * as a thin outline in the explorer, not a solid arrowhead). `strokeWidthDefault` is that shared
 * 1.6-unit stroke width in the icon's own 20-unit design grid; `ic-stroke`'s own path is the one
 * documented exception (`stroke-width="3"` as an inline SVG attribute, overriding the class
 * default) -- see the `Tool.STROKE` branch below.
 *
 * Shared by [Puck]'s own `ToolIcon` composable (the puck's 26 dp icon and the unfolded tool
 * list's 18 dp icon both call it) and [RadialDial]'s per-sector glyph (drawn at the explorer's
 * own native 20 dp, `drawRadial`'s own `width="20" height="20"` -- `tools.js`, around the radial
 * dial's own `<svg class="ic">` markup), so every place a tool icon appears in this app comes
 * from the same one path table, never a second, drifting copy.
 *
 * [topLeft] and [boxSizePx] together define the icon's own on-screen box (its 20x20 viewBox
 * scaled uniformly to [boxSizePx] and translated to [topLeft]); every coordinate in the path
 * data below, and the 1.6/3-unit stroke widths, are scaled by the same factor
 * (`boxSizePx / 20f`) -- exactly how the explorer's own nested SVG viewport scaling behaves when
 * a `<use>` of a 20-unit `<symbol>` is stretched into a larger or smaller box (`.puck>svg`'s own
 * 26 px box, `.pmenu button svg`'s own 18 px box): the *design grid* stays 20 units square, and
 * everything scales with the box, stroke width included.
 */
internal fun DrawScope.drawToolIcon(
    tool: Tool,
    topLeft: Offset,
    boxSizePx: Float,
    color: Color,
) {
    val scale = boxSizePx / 20f

    fun pt(
        x: Float,
        y: Float,
    ): Offset = topLeft + Offset(x * scale, y * scale)

    val strokeWidthDefault = 1.6f * scale
    val strokeCap = StrokeCap.Round
    val strokeJoin = StrokeJoin.Round

    when (tool) {
        Tool.SELECT -> {
            // ic-select: M5 3l11 9-5.2 1L8.4 18z
            val path =
                Path().apply {
                    moveTo(pt(5f, 3f).x, pt(5f, 3f).y)
                    lineTo(pt(16f, 12f).x, pt(16f, 12f).y)
                    lineTo(pt(10.8f, 13f).x, pt(10.8f, 13f).y)
                    lineTo(pt(8.4f, 18f).x, pt(8.4f, 18f).y)
                    close()
                }
            drawPath(path, color = color, style = Stroke(width = strokeWidthDefault, cap = strokeCap, join = strokeJoin))
        }

        Tool.PEN -> {
            // ic-pen: M4 16l1-4 8-8 3 3-8 8zM12 5l3 3
            val nib =
                Path().apply {
                    moveTo(pt(4f, 16f).x, pt(4f, 16f).y)
                    lineTo(pt(5f, 12f).x, pt(5f, 12f).y)
                    lineTo(pt(13f, 4f).x, pt(13f, 4f).y)
                    lineTo(pt(16f, 7f).x, pt(16f, 7f).y)
                    lineTo(pt(8f, 15f).x, pt(8f, 15f).y)
                    close()
                }
            drawPath(nib, color = color, style = Stroke(width = strokeWidthDefault, cap = strokeCap, join = strokeJoin))
            drawLine(color, pt(12f, 5f), pt(15f, 8f), strokeWidth = strokeWidthDefault, cap = strokeCap)
        }

        Tool.PRIMITIVES -> {
            // ic-shape: rect x=3 y=3 w=8 h=8 rx=1 + circle cx=13.5 cy=13.5 r=4
            drawRoundRect(
                color = color,
                topLeft = pt(3f, 3f),
                size = Size(8f * scale, 8f * scale),
                cornerRadius = CornerRadius(1f * scale, 1f * scale),
                style = Stroke(width = strokeWidthDefault, cap = strokeCap, join = strokeJoin),
            )
            drawCircle(color = color, radius = 4f * scale, center = pt(13.5f, 13.5f), style = Stroke(width = strokeWidthDefault))
        }

        Tool.BOOLEAN -> {
            // ic-bool: circle(7.5,10,r5) + circle(12.5,10,r5)
            drawCircle(color = color, radius = 5f * scale, center = pt(7.5f, 10f), style = Stroke(width = strokeWidthDefault))
            drawCircle(color = color, radius = 5f * scale, center = pt(12.5f, 10f), style = Stroke(width = strokeWidthDefault))
        }

        Tool.STROKE -> {
            // ic-stroke: M3 14c4-9 10-9 14 0, stroke-width="3" (the one icon with its own explicit
            // override of the .tool svg default 1.6 -- see this file's own KDoc).
            val path =
                Path().apply {
                    moveTo(pt(3f, 14f).x, pt(3f, 14f).y)
                    cubicTo(
                        pt(7f, 5f).x,
                        pt(7f, 5f).y,
                        pt(13f, 5f).x,
                        pt(13f, 5f).y,
                        pt(17f, 14f).x,
                        pt(17f, 14f).y,
                    )
                }
            drawPath(path, color = color, style = Stroke(width = 3f * scale, cap = strokeCap, join = strokeJoin))
        }

        Tool.MEASURE -> {
            // ic-measure: rect(2,7,16,6,rx1) + three ticks at x=6,10,14 from y=7 to y=9.5
            drawRoundRect(
                color = color,
                topLeft = pt(2f, 7f),
                size = Size(16f * scale, 6f * scale),
                cornerRadius = CornerRadius(1f * scale, 1f * scale),
                style = Stroke(width = strokeWidthDefault, cap = strokeCap, join = strokeJoin),
            )
            for (x in floatArrayOf(6f, 10f, 14f)) {
                drawLine(color, pt(x, 7f), pt(x, 9.5f), strokeWidth = strokeWidthDefault, cap = strokeCap)
            }
        }

        Tool.ANCHORS -> {
            // ic-anchor: circle(10,5,r2) + path M10 7v10M5 12c0 3.3 2.2 5 5 5s5-1.7 5-5
            drawCircle(color = color, radius = 2f * scale, center = pt(10f, 5f), style = Stroke(width = strokeWidthDefault))
            drawLine(color, pt(10f, 7f), pt(10f, 17f), strokeWidth = strokeWidthDefault, cap = strokeCap)
            val curve =
                Path().apply {
                    moveTo(pt(5f, 12f).x, pt(5f, 12f).y)
                    cubicTo(pt(5f, 15.3f).x, pt(5f, 15.3f).y, pt(7.2f, 17f).x, pt(7.2f, 17f).y, pt(10f, 17f).x, pt(10f, 17f).y)
                    cubicTo(pt(12.8f, 17f).x, pt(12.8f, 17f).y, pt(15f, 15.3f).x, pt(15f, 15.3f).y, pt(15f, 12f).x, pt(15f, 12f).y)
                }
            drawPath(curve, color = color, style = Stroke(width = strokeWidthDefault, cap = strokeCap, join = strokeJoin))
        }

        Tool.METRICS -> {
            // ic-metrics: three horizontal lines at y=5,10,15 from x=3 to x=17
            for (y in floatArrayOf(5f, 10f, 15f)) {
                drawLine(color, pt(3f, y), pt(17f, y), strokeWidth = strokeWidthDefault, cap = strokeCap)
            }
        }
    }
}
