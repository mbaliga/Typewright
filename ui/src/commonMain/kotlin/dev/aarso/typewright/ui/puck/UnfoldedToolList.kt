package dev.aarso.typewright.ui.puck

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.aarso.typewright.core.geometry.Vec2
import dev.aarso.typewright.ui.toIntOffset
import dev.aarso.typewright.ui.tokens.CanvasTexture
import dev.aarso.typewright.ui.tokens.SpacingTokens
import dev.aarso.typewright.ui.tokens.Typography
import dev.aarso.typewright.ui.tokens.toColor

/**
 * The puck's unfolded tool list (UI_SPEC §3 "Unfolded tool list"): "A column beside the puck (on
 * the side with room), canvas background, each row: 18 dp icon, mono uppercase name, shortcut
 * letter right-aligned in muted. Current tool row is an ink block. Tap selects and collapses."
 * A canvas-filled background is this component's own documented exception to "Nothing on the
 * glass has a background" (UI_SPEC §1): the fill is the canvas/ink token pair, not a bordered
 * card, shadow or blur.
 */
@Composable
fun UnfoldedToolList(
    pin: PuckPinState,
    tools: List<PlaceholderTool>,
    currentIndex: Int,
    texture: CanvasTexture,
    canvasSizeDp: Vec2,
    onSelect: (Int) -> Unit,
) {
    val density = LocalDensity.current
    val ink = texture.ink.toColor()
    val canvas = texture.canvas.toColor()
    val muted = texture.muted.toColor()
    val rowWidthDp = 140.0
    // "beside the puck (on the side with room)": the side opposite the edge the puck is pinned to.
    val listLeftDp =
        when (pin.edge) {
            PuckEdge.RIGHT -> pin.center.x - PuckGestureDefaults.PUCK_DIAMETER_DP / 2.0 - SpacingTokens.GUTTER_DP - rowWidthDp
            PuckEdge.LEFT -> pin.center.x + PuckGestureDefaults.PUCK_DIAMETER_DP / 2.0 + SpacingTokens.GUTTER_DP
        }
    val listTopDp = pin.center.y - (tools.size * ROW_HEIGHT_DP) / 2.0

    Box(
        modifier =
            Modifier
                .offset { Vec2(listLeftDp, listTopDp).toIntOffset(density) }
                .width(rowWidthDp.dp)
                .background(canvas),
    ) {
        Column {
            tools.forEachIndexed { index, tool ->
                val current = index == ((currentIndex % tools.size) + tools.size) % tools.size
                Row(
                    modifier =
                        Modifier
                            .height(ROW_HEIGHT_DP.dp)
                            .width(rowWidthDp.dp)
                            .background(if (current) ink else canvas)
                            .clickable { onSelect(index) }
                            .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    ToolIcon(tool = tool, color = if (current) canvas else ink, sizeDp = 18.dp)
                    BasicText(
                        text = tool.label.uppercase(),
                        style = Typography.mono(sizeSp = 9.5).copy(color = if (current) canvas else ink),
                        modifier = Modifier.padding(start = 8.dp).wrapContentWidth(),
                    )
                    Box(modifier = Modifier.wrapContentWidth()) {
                        BasicText(
                            text = tool.shortcut.toString(),
                            style = Typography.mono(sizeSp = 9.5).copy(color = if (current) canvas.copy(alpha = 0.7f) else muted),
                        )
                    }
                }
            }
        }
    }
}

private const val ROW_HEIGHT_DP = 32.0
