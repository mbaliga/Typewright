// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.capture

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.Typography
import com.asoc.typewright.ui.tokens.accent
import com.asoc.typewright.ui.tokens.toColor

/** A sentence in the app's sans at [size], in [color]. */
internal fun sentence(
    color: Color,
    size: Double = 12.5,
    weight: FontWeight = FontWeight.Normal,
): TextStyle =
    TextStyle(fontFamily = FontFamily.Default, fontSize = size.sp, lineHeight = (size * 1.45).sp, fontWeight = weight, color = color)

/** The small mono line (a count, a source, a legend) in [color]; callers upper-case the text where the board does. */
internal fun mono(
    color: Color,
    size: Double = 9.5,
    tracking: Double = 0.14,
): TextStyle = Typography.mono(sizeSp = size).copy(color = color, letterSpacing = tracking.em)

/** An action link in the accent: `label ›`. */
@Composable
internal fun LinkText(
    label: String,
    texture: CanvasTexture,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = "$label ›",
        style = sentence(texture.accent.toColor(), size = 13.0, weight = FontWeight.SemiBold),
        modifier = modifier.semantics { role = Role.Button }.clickable(onClick = onClick).padding(vertical = 6.dp),
    )
}

/** A small mono label that is an ink block with paper text, as the board draws "2 need you". */
@Composable
internal fun InkTag(
    text: String,
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
    size: Double = 9.5,
) {
    BasicText(
        text = text.uppercase(),
        style = mono(texture.canvas.toColor(), size),
        modifier = modifier.background(texture.ink.toColor()).padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** A choice in a small group: mono capitals, an ink block with paper text when chosen, and a tap area taller than the block. */
@Composable
internal fun ChoiceChip(
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
                    role = Role.RadioButton
                    this.selected = selected
                }.clickable(onClick = onClick)
                .padding(vertical = 8.dp),
    ) {
        BasicText(
            text = text.uppercase(),
            style = mono(if (selected) paper else ink, size = 9.5, tracking = 0.12),
            modifier = Modifier.background(if (selected) ink else Color.Transparent).padding(horizontal = 8.dp, vertical = 5.dp),
        )
    }
}

/** Fills [outline] (a small picture's ink as polylines) scaled to fit the box [width] by [height] at ([left], [top]), centred, in [color]. */
internal fun DrawScope.drawInk(
    outline: InkOutline,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    color: Color,
) {
    if (outline.isEmpty) return
    val scale = minOf(width / outline.width, height / outline.height)
    val path =
        Path().apply {
            fillType = PathFillType.EvenOdd
            for (loop in outline.loops) {
                loop.forEachIndexed { i, p -> if (i == 0) moveTo(p.x.toFloat(), p.y.toFloat()) else lineTo(p.x.toFloat(), p.y.toFloat()) }
                close()
            }
        }
    withTransform({
        translate(left + (width - outline.width * scale) / 2f, top + (height - outline.height * scale) / 2f)
        scale(scale, scale, pivot = Offset.Zero)
    }) {
        drawPath(path, color)
    }
}

/** A box that draws [outline] in the current ink, for a cell or a sheet thumbnail. */
@Composable
internal fun InkPicture(
    outline: InkOutline,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) { drawInk(outline, 0f, 0f, size.width, size.height, color) }
}
