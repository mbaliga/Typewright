// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asoc.typewright.engine.construct.BooleanOp
import com.asoc.typewright.engine.construct.LineJoin
import com.asoc.typewright.ui.puck.Tool
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.toColor

/**
 * The strip above the notice bar while the Boolean, Stroke, Anchors or Metrics tool is in hand: the tool's own buttons (the
 * four operations; the join, a narrower or wider stroke and Stroke itself; Delete anchor) and one sentence on what it does.
 * A chosen button is told by being turned (canvas on ink) and bold, not by a colour. Nothing is shown for any other tool.
 */
@Composable
fun DrawToolStrip(
    tool: Tool,
    controller: DrawController,
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
) {
    val hint = DrawToolPanel.hint(tool, controller) ?: return
    val ink = texture.ink.toColor()
    val onInk = texture.canvas.toColor()
    val locked = controller.editor.isLocked

    @Composable
    fun Chip(
        label: String,
        chosen: Boolean = false,
        enabled: Boolean = true,
        onClick: () -> Unit,
    ) {
        BasicText(
            text = label,
            style =
                TextStyle(
                    fontFamily = FontFamily.Default,
                    fontSize = 12.5.sp,
                    fontWeight = if (chosen) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (chosen) ink else onInk.copy(alpha = if (enabled) 1f else DISABLED_ALPHA),
                ),
            modifier =
                Modifier
                    .background(if (chosen) onInk else ink)
                    .clickable(enabled = enabled, onClick = onClick)
                    .padding(vertical = 8.dp, horizontal = 8.dp),
        )
    }
    Column(
        modifier =
            modifier
                .padding(horizontal = 18.dp)
                .widthIn(max = 420.dp)
                .background(ink)
                .padding(horizontal = 5.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            when (tool) {
                Tool.BOOLEAN -> {
                    val picked = controller.selection.contours.size
                    for ((label, op) in BOOLEAN_CHIPS) Chip(label, enabled = !locked && picked >= 2) { controller.runBoolean(op) }
                }

                Tool.STROKE -> {
                    Chip("Miter", chosen = controller.strokeJoin == LineJoin.MITER) { controller.pickStrokeJoin(LineJoin.MITER) }
                    Chip("Round", chosen = controller.strokeJoin == LineJoin.ROUND) { controller.pickStrokeJoin(LineJoin.ROUND) }
                    Chip("−10") { controller.setStrokeWidth(controller.strokeWidth - WIDTH_STEP) }
                    Chip("+10") { controller.setStrokeWidth(controller.strokeWidth + WIDTH_STEP) }
                    Chip("Stroke", enabled = !locked) { controller.runStroke() }
                }

                Tool.ANCHORS -> {
                    val name = controller.selectedAnchor()
                    if (name != null) Chip("Delete $name", enabled = !locked) { controller.deleteSelectedAnchor() }
                }

                else -> {
                    Unit
                }
            }
        }
        BasicText(
            text = hint,
            style = TextStyle(fontFamily = FontFamily.Default, fontSize = 11.5.sp, lineHeight = 15.sp, color = onInk),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

private val BOOLEAN_CHIPS =
    listOf("Union" to BooleanOp.UNION, "Subtract" to BooleanOp.SUBTRACT, "Intersect" to BooleanOp.INTERSECT, "Exclude" to BooleanOp.EXCLUDE)

private const val WIDTH_STEP = 10.0
private const val DISABLED_ALPHA = 0.45f
