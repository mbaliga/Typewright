// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.check

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.asoc.typewright.qa.report.SeverityBar
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.Typography
import com.asoc.typewright.ui.tokens.accent
import com.asoc.typewright.ui.tokens.toColor

private const val TRACK_ALPHA = 0.14f
private const val HATCH_SPACING_DP = 7f
private const val HATCH_WIDTH_DP = 2f

/** A sentence in the app's sans at [size], in [color]. */
internal fun sentenceStyle(
    color: Color,
    size: Double = 12.5,
    weight: FontWeight = FontWeight.Normal,
): TextStyle =
    TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = size.sp,
        lineHeight = (size * LINE_HEIGHT_RATIO).sp,
        fontWeight = weight,
        color = color,
    )

private const val LINE_HEIGHT_RATIO = 1.45

/** The small upper-case mono line (a group name, a legend, a tag) in [color]. */
internal fun labelStyle(
    color: Color,
    size: Double = 9.5,
    tracking: Double = 0.14,
): TextStyle = Typography.mono(sizeSp = size).copy(color = color, letterSpacing = tracking.em)

/**
 * A choice in a small group (a style chip, a sort order): mono capitals, an ink block with paper text when it is
 * the one chosen. The tap area is taller than the block so a thumb finds it.
 */
@Composable
internal fun SegmentChip(
    text: String,
    selected: Boolean,
    texture: CanvasTexture,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = texture.ink.toColor()
    val paper = texture.canvas.toColor()
    Box(
        modifier =
            modifier
                .semantics {
                    role = Role.Tab
                    this.selected = selected
                }.clickable(onClick = onClick)
                .padding(vertical = 4.dp),
    ) {
        BasicText(
            text = text.uppercase(),
            style = labelStyle(if (selected) paper else ink, size = 9.0, tracking = 0.12),
            modifier = Modifier.background(if (selected) ink else Color.Transparent).padding(horizontal = 7.dp, vertical = 4.dp),
        )
    }
}

/** An action link in the accent: `label ›`, which the person taps to go where the finding points. */
@Composable
internal fun ActionLink(
    label: String,
    texture: CanvasTexture,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = "$label ›",
        style = sentenceStyle(texture.accent.toColor(), size = 12.5, weight = FontWeight.SemiBold),
        modifier =
            modifier
                .semantics { role = Role.Button }
                .clickable(onClick = onClick)
                .padding(vertical = 4.dp),
    )
}

/**
 * The severity bar: fail as a solid run, warn as a hatched run, everything else as a plain track, in widths that
 * come from [bar]. Never a colour: ink solid, ink hatched, ink faint, so it reads the same on every canvas.
 */
@Composable
internal fun SeverityBarView(
    bar: SeverityBar,
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
    height: Dp = 5.dp,
) {
    val ink = texture.ink.toColor()
    Canvas(modifier.fillMaxWidth().height(height)) {
        val w = size.width
        val h = size.height
        val solid = (bar.solid * w).toFloat()
        val hatched = (bar.hatched * w).toFloat()
        val plainFrom = solid + hatched
        drawRect(ink.copy(alpha = TRACK_ALPHA), topLeft = Offset(plainFrom, 0f), size = Size((w - plainFrom).coerceAtLeast(0f), h))
        drawRect(ink, size = Size(solid, h))
        if (hatched > 0f) {
            clipRect(left = solid, top = 0f, right = solid + hatched, bottom = h) {
                val spacing = HATCH_SPACING_DP.dp.toPx()
                var x = solid - h
                while (x < solid + hatched + h) {
                    drawLine(ink, Offset(x, h), Offset(x + h, 0f), strokeWidth = HATCH_WIDTH_DP.dp.toPx())
                    x += spacing
                }
            }
        }
    }
}
