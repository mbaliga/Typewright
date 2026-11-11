// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import androidx.compose.ui.graphics.Color
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.MeaningColors
import com.asoc.typewright.ui.tokens.ThemeRole
import com.asoc.typewright.ui.tokens.accent
import com.asoc.typewright.ui.tokens.role
import com.asoc.typewright.ui.tokens.toColor

/**
 * Every colour the glyph is drawn in, taken from the texture's theme roles and nothing else
 * (SCREENS_V1 §3): selection is the accent (violet by default), the thing snapped to is the snap role
 * (cyan by default), nodes and lines are ink unless the theme sets them. Colour is never the only
 * signal: a selected node also has a ring, a snap target a crosshair, a smooth node is round and a
 * corner square.
 */
class DrawColors(
    val ink: Color,
    val canvas: Color,
    val muted: Color,
    val cornerNode: Color,
    val smoothNode: Color,
    val offCurve: Color,
    val handleLine: Color,
    val selected: Color,
    val snap: Color,
    val metricLine: Color,
    val guide: Color,
    val construction: Color,
    val zone: Color,
)

fun CanvasTexture.drawColors(): DrawColors {
    val inkColor = ink.toColor()
    return DrawColors(
        ink = inkColor,
        canvas = canvas.toColor(),
        muted = muted.toColor(),
        cornerNode = role(ThemeRole.CORNER_NODE)?.toColor() ?: inkColor,
        smoothNode = role(ThemeRole.SMOOTH_NODE)?.toColor() ?: inkColor,
        offCurve = role(ThemeRole.OFF_CURVE)?.toColor() ?: inkColor,
        handleLine = role(ThemeRole.HANDLE_LINE)?.toColor() ?: inkColor.copy(alpha = HANDLE_LINE_ALPHA),
        selected = (role(ThemeRole.SELECTED_NODE) ?: accent).toColor(),
        snap = MeaningColors.CYAN.forTexture(this).toColor(),
        metricLine = role(ThemeRole.METRIC_LINES)?.toColor() ?: line.toColor(),
        guide = role(ThemeRole.GUIDES)?.toColor() ?: inkColor.copy(alpha = GUIDE_ALPHA),
        construction = role(ThemeRole.CONSTRUCTION_LINES)?.toColor() ?: inkColor.copy(alpha = CONSTRUCTION_ALPHA),
        zone = inkColor.copy(alpha = ZONE_ALPHA),
    )
}

private const val HANDLE_LINE_ALPHA = 0.5f
private const val GUIDE_ALPHA = 0.4f
private const val CONSTRUCTION_ALPHA = 0.62f
private const val ZONE_ALPHA = 0.06f
