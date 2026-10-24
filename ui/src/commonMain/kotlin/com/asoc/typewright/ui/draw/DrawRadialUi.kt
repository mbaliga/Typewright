// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.Typography
import com.asoc.typewright.ui.tokens.toColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The object radial (S12): eight sectors round the touch, the sweep with its far-side choice at 106%,
 * the sectors under the thumb at 40%, a hairline needle through the hub, and, once lifted, the labels
 * with their trigger chips and a hub that opens everything. Drawn in dp positions the controller holds,
 * so the ring is exactly where [RadialGeometry.ringCenter] put it.
 */
@Composable
fun DrawRadialOverlay(
    controller: DrawController,
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
) {
    val radial = controller.radial ?: return
    val measurer = rememberTextMeasurer()
    val ink = texture.ink.toColor()
    val canvas = texture.canvas.toColor()
    val muted = texture.muted.toColor()
    val field = lerp(canvas, ink, FIELD_TINT)
    val lifted = radial.state is RadialState.Lifted
    BoxWithConstraints(modifier.fillMaxSize()) {
        val screenWidthDp = maxWidth.value
        Canvas(Modifier.fillMaxSize()) {
            val centre =
                Offset(
                    radial.center.x.dp
                        .toPx(),
                    radial.center.y.dp
                        .toPx(),
                )
            val inner = RadialGeometry.INNER_DP.dp.toPx()
            val outer = RadialGeometry.OUTER_DP.dp.toPx()
            val gap = 2.dp.toPx()
            val sweeping = radial.state as? RadialState.Sweeping
            // Where the finger is, as the ring sees it: the same travel from the hub as the finger has made from where it touched.
            val seen = sweeping?.let { radial.center + (it.finger - radial.anchor) }
            val covered = if (seen != null) RadialGeometry.coveredSectors(radial.center, seen) else emptySet()
            for (k in 0 until RadialGeometry.SECTORS) {
                val chosen = sweeping?.selected == k
                val start = -QUARTER_TURN + k * SECTOR_DEGREES - SECTOR_DEGREES / 2f
                val growth = if (chosen) CHOSEN_SCALE else 1f
                val path = wedge(centre, inner * (2f - growth), outer * growth, start, SECTOR_DEGREES)
                val slot = radial.slots.getOrNull(k)
                val usable = slot?.commandId != null && slot.enabled
                val alpha = if (k in covered) COVERED_ALPHA else 1f
                drawPath(path, if (chosen) ink else field.copy(alpha = alpha))
                drawPath(path, canvas, style = Stroke(gap))
                if (slot?.commandId != null) {
                    val mid = (start + SECTOR_DEGREES / 2f) * (PI.toFloat() / 180f)
                    val at = centre + Offset(cos(mid), sin(mid)) * ((inner + outer) / 2f)
                    val strength = (if (usable) 1f else DISABLED_ALPHA) * alpha
                    sectorLabel(
                        measurer = measurer,
                        label = slot.label,
                        chip = if (lifted) slot.chip else null,
                        at = at,
                        labelColor = (if (chosen) canvas else ink).copy(alpha = strength),
                        chipColor = muted.copy(alpha = strength),
                    )
                }
            }
            if (sweeping != null) {
                val dash = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx()))
                drawCircle(
                    ink.copy(alpha = DEAD_ZONE_ALPHA),
                    RadialGeometry.DEAD_ZONE_DP.dp.toPx(),
                    centre,
                    style = Stroke(1.dp.toPx(), pathEffect = dash),
                )
                drawCircle(ink, 3.dp.toPx(), centre)
                val finger =
                    Offset(
                        sweeping.finger.x.dp
                            .toPx(),
                        sweeping.finger.y.dp
                            .toPx(),
                    )
                drawCircle(ink.copy(alpha = HALO_OUTER_ALPHA), HALO_OUTER_DP.dp.toPx(), finger)
                drawCircle(ink.copy(alpha = HALO_INNER_ALPHA), HALO_INNER_DP.dp.toPx(), finger)
                sweeping.selected?.let { sector ->
                    val angle = (-QUARTER_TURN + sector * SECTOR_DEGREES) * (PI.toFloat() / 180f)
                    val end = centre + Offset(cos(angle), sin(angle)) * (outer - NEEDLE_SHORT_DP.dp.toPx())
                    val from = Offset((seen!!.x).dp.toPx(), (seen.y).dp.toPx())
                    drawLine(ink.copy(alpha = NEEDLE_ALPHA), from, end, strokeWidth = 1.dp.toPx(), pathEffect = dash)
                }
            } else {
                drawCircle(ink, HUB_RADIUS_DP.dp.toPx(), centre)
            }
        }
        if (lifted) {
            Box(
                modifier =
                    Modifier
                        .offset {
                            IntOffset(
                                radial.center.x.dp
                                    .roundToPx() - HUB_RADIUS_DP.dp.roundToPx(),
                                radial.center.y.dp
                                    .roundToPx() - HUB_LABEL_RISE_DP.dp.roundToPx(),
                            )
                        }.width((HUB_RADIUS_DP * 2).dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(text = "MORE…", style = Typography.mono(sizeSp = 8.5, weight = FontWeight.SemiBold).copy(color = canvas))
            }
        }
        val sweeping = radial.state as? RadialState.Sweeping
        val chosen = sweeping?.selected?.let { radial.slots.getOrNull(it)?.commandId }?.let { radial.commands[it] }
        if (sweeping != null && chosen != null) RadialTitle(radial, sweeping, chosen, texture, screenWidthDp.toDouble())
    }
}

/** The name of the choice, what it would do and how to trigger it, set on the side of the ring away from the thumb. */
@Composable
private fun RadialTitle(
    radial: RadialUi,
    sweeping: RadialState.Sweeping,
    command: DrawCommand,
    texture: CanvasTexture,
    screenWidthDp: Double,
) {
    val above = sweeping.finger.y >= radial.anchor.y
    val left = (radial.center.x - RadialGeometry.OUTER_DP).coerceAtLeast(TITLE_EDGE_DP)
    val room = (screenWidthDp - left - TITLE_EDGE_DP).coerceAtLeast(TITLE_MIN_WIDTH_DP)
    val top =
        if (above) {
            radial.center.y - RadialGeometry.OUTER_DP - TITLE_BLOCK_HEIGHT_DP
        } else {
            radial.center.y + RadialGeometry.OUTER_DP + TITLE_GAP_DP
        }
    Column(
        modifier = Modifier.offset { IntOffset(left.dp.roundToPx(), top.coerceAtLeast(TITLE_EDGE_DP).dp.roundToPx()) }.width(room.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        BasicText(
            text = RadialLabels.title(command),
            style = Typography.mono(sizeSp = 10.5, weight = FontWeight.SemiBold).copy(color = texture.ink.toColor()),
        )
        val consequence = (command.consequence ?: command.reason ?: "").uppercase()
        BasicText(text = "$consequence · RELEASE TO APPLY", style = Typography.mono(sizeSp = 8.5).copy(color = texture.muted.toColor()))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            command.gesture?.let { chip ->
                BasicText(
                    text = chip.uppercase(),
                    style = Typography.mono(sizeSp = 8.0).copy(color = texture.canvas.toColor()),
                    modifier = Modifier.background(texture.ink.toColor()).padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
            command.keys?.let {
                BasicText(
                    text = it.uppercase(),
                    style = Typography.mono(sizeSp = 8.0).copy(color = texture.muted.toColor()),
                )
            }
        }
    }
}

/** A sector's name, with its trigger under it when the ring is waiting, centred on [at]. */
private fun DrawScope.sectorLabel(
    measurer: TextMeasurer,
    label: String,
    chip: String?,
    at: Offset,
    labelColor: Color,
    chipColor: Color,
) {
    val labelLayout = measurer.measure(label, Typography.mono(sizeSp = 8.0).copy(color = labelColor))
    val chipLayout = chip?.let { measurer.measure(it.uppercase(), Typography.mono(sizeSp = 7.0).copy(color = chipColor)) }
    val lineGap = 3.dp.toPx()
    val total = labelLayout.size.height + (chipLayout?.let { it.size.height + lineGap } ?: 0f)
    val top = at.y - total / 2f
    drawText(labelLayout, topLeft = Offset(at.x - labelLayout.size.width / 2f, top))
    chipLayout?.let { drawText(it, topLeft = Offset(at.x - it.size.width / 2f, top + labelLayout.size.height + lineGap)) }
}

private fun wedge(
    centre: Offset,
    inner: Float,
    outer: Float,
    startDeg: Float,
    sweepDeg: Float,
): Path =
    Path().apply {
        arcTo(Rect(centre.x - outer, centre.y - outer, centre.x + outer, centre.y + outer), startDeg, sweepDeg, true)
        arcTo(Rect(centre.x - inner, centre.y - inner, centre.x + inner, centre.y + inner), startDeg + sweepDeg, -sweepDeg, false)
        close()
    }

private const val QUARTER_TURN = 90f
private const val SECTOR_DEGREES = 45f
private const val FIELD_TINT = 0.06f
private const val CHOSEN_SCALE = 1.06f
private const val COVERED_ALPHA = 0.4f
private const val DISABLED_ALPHA = 0.4f
private const val DEAD_ZONE_ALPHA = 0.25f
private const val HALO_OUTER_ALPHA = 0.10f
private const val HALO_INNER_ALPHA = 0.08f
private const val HALO_OUTER_DP = 27.0
private const val HALO_INNER_DP = 14.0
private const val NEEDLE_ALPHA = 0.5f
private const val NEEDLE_SHORT_DP = 20.0
private const val HUB_RADIUS_DP = 30.0
private const val HUB_LABEL_RISE_DP = 7.0
private const val TITLE_BLOCK_HEIGHT_DP = 60.0
private const val TITLE_GAP_DP = 12.0
private const val TITLE_EDGE_DP = 18.0
private const val TITLE_MIN_WIDTH_DP = 200.0
