// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.space

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.Typography
import com.asoc.typewright.ui.tokens.accent
import com.asoc.typewright.ui.tokens.toColor

/** The mono caps label of a section of the page (the board's `.lab` / `.m`). */
@Composable
internal fun SpaceLabel(
    text: String,
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = text.uppercase(),
        style = Typography.mono(sizeSp = 9.0).copy(color = texture.muted.toColor()),
        modifier = modifier,
    )
}

/** A sentence in the page's system sans (12.5 sp), muted or not. */
@Composable
internal fun SpaceSentence(
    text: String,
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    bold: Boolean = false,
) {
    BasicText(
        text = text,
        style =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
                color = (if (muted) texture.muted else texture.fg).toColor(),
            ),
        modifier = modifier,
    )
}

/** An action in the accent colour, a 44 dp target (the board's `Try 46 · 44 ›` link); dimmed and inert when not [enabled]. */
@Composable
internal fun SpaceAction(
    text: String,
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val color = texture.accent.toColor()
    BasicText(
        text = text,
        style =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) color else color.copy(alpha = DISABLED),
            ),
        modifier =
            modifier
                .defaultMinSize(minHeight = 44.dp)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(vertical = 12.dp),
    )
}

/**
 * A chip: mono caps, an ink block with paper text when [on] (the board's `chip`), plain when not. [caps] is false where
 * the letters' case is the point (a control string), so the chip shows them as they are.
 */
@Composable
internal fun SpaceChip(
    text: String,
    on: Boolean,
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
    caps: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .defaultMinSize(minHeight = 44.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier =
                Modifier
                    .then(
                        if (on) Modifier.background(texture.ink.toColor()) else Modifier,
                    ).padding(horizontal = 9.dp, vertical = 6.dp),
        ) {
            BasicText(
                text = if (caps) text.uppercase() else text,
                style =
                    Typography
                        .mono(
                            sizeSp = 10.0,
                        ).copy(color = (if (on) texture.canvas else texture.fg).toColor(), letterSpacing = 0.12.em),
            )
        }
    }
}

/**
 * A one-line text field with the board's underline input: [value] and [onValueChange] as for any field,
 * [onSubmit] on Enter. Esc is not handled here (the page's left circle handles it).
 */
@Composable
internal fun SpaceLineField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
    onSubmit: () -> Unit = {},
) {
    val line = texture.ink.toColor().copy(alpha = INPUT_LINE_ALPHA)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 40.dp)
                .drawBehind {
                    val y = size.height - 1.dp.toPx()
                    drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                },
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            BasicText(
                text = placeholder,
                style = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, color = texture.muted.toColor()),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, color = texture.fg.toColor()),
            cursorBrush = SolidColor(texture.fg.toColor()),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                            onSubmit()
                            true
                        } else {
                            false
                        }
                    },
        )
    }
}

private const val DISABLED = 0.35f
private const val INPUT_LINE_ALPHA = 0.35f
