package dev.aarso.typewright.ui.puck

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.aarso.typewright.ui.toOffset
import dev.aarso.typewright.ui.tokens.CanvasTexture
import dev.aarso.typewright.ui.tokens.Typography
import dev.aarso.typewright.ui.tokens.toColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The radial dial (UI_SPEC §3 "Radial dial"): "260 dp square centred on the puck; 8 sectors
 * between r = 50 and r = 120 dp, canvas fill with a 2 dp canvas stroke as the gap; the current
 * sector is an ink block with a canvas icon; a fixed ink triangle marker at 12 o'clock; the
 * current tool's name above the marker in mono."
 *
 * **Rotation direction, derived (no reference screenshot shows the dial open):**
 * [state]'s `accumulatedAngleDegrees` is a raw, unwrapped cumulative angle -- P4a's own KDoc calls
 * it "unwrapped" precisely so the ring can spin more than once. Sector `k`'s *unrotated* home
 * position is centred at screen angle `-90 + k * 45` (`-90` = 12 o'clock in `atan2`'s convention,
 * matching [PuckGeometry]'s own angle math). Rotating the whole ring by
 * `-accumulatedAngleDegrees` puts sector `floor(accumulated / 45)` -- [PuckGestureState.
 * RadialOpen.currentSector] -- under the fixed marker at every exact detent crossing, and mid-way
 * between crossings the same wedge sweeps continuously toward the marker as the pointer turns,
 * which reads as "the ring rotates with the pointer" (UI_SPEC's own words) while the marker itself
 * never moves.
 */
@Composable
fun RadialDial(
    state: PuckGestureState.RadialOpen,
    tools: List<Tool>,
    texture: CanvasTexture,
    density: Density,
) {
    val ink = texture.ink.toColor()
    val canvas = texture.canvas.toColor()
    val hubPx = state.hubCenter.toOffset(density)
    val innerRadiusPx = with(density) { INNER_RADIUS_DP.dp.toPx() }
    val outerRadiusPx = with(density) { OUTER_RADIUS_DP.dp.toPx() }
    val gapPx = with(density) { GAP_DP.dp.toPx() }
    val ringRotationDeg = (-state.accumulatedAngleDegrees).toFloat()
    val sectorSweep = 360f / state.sectorsCount

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            for (k in 0 until state.sectorsCount) {
                val homeCenterDeg = -90f + k * sectorSweep
                val startDeg = homeCenterDeg - sectorSweep / 2f + ringRotationDeg
                val isCurrent = ((k - state.currentSector) % state.sectorsCount + state.sectorsCount) % state.sectorsCount == 0
                val path = wedgePath(hubPx, innerRadiusPx, outerRadiusPx, startDeg, sectorSweep)
                drawPath(path, color = if (isCurrent) ink else canvas)
                // "2 dp canvas stroke as the gap": always canvas-coloured, invisible against a
                // canvas-filled neighbour, visible as a seam against the ink-filled current sector.
                drawPath(path, color = canvas, style = Stroke(width = gapPx))

                val tool = tools.getOrNull(k % tools.size)
                if (tool != null) {
                    val midDeg = (startDeg + sectorSweep / 2f) * (PI.toFloat() / 180f)
                    val midRadius = (innerRadiusPx + outerRadiusPx) / 2f
                    val iconCenter = hubPx + Offset(cos(midDeg), sin(midDeg)) * midRadius
                    // The explorer's own radial draws each sector's icon at its native 20x20
                    // viewBox size (`drawRadial`'s own `width="20" height="20"`, not shrunk to
                    // fit the sector) -- ICON_BOX_DP matches that exactly.
                    val iconBoxPx = with(density) { ICON_BOX_DP.dp.toPx() }
                    drawToolIcon(tool, iconCenter - Offset(iconBoxPx / 2f, iconBoxPx / 2f), iconBoxPx, if (isCurrent) canvas else ink)
                }
            }

            // Fixed ink triangle marker at 12 o'clock, just outside the ring.
            val markerY = hubPx.y - outerRadiusPx - with(density) { 4.dp.toPx() }
            val markerHalfWidth = with(density) { 6.dp.toPx() }
            val markerHeight = with(density) { 8.dp.toPx() }
            val marker =
                Path().apply {
                    moveTo(hubPx.x, markerY)
                    lineTo(hubPx.x - markerHalfWidth, markerY - markerHeight)
                    lineTo(hubPx.x + markerHalfWidth, markerY - markerHeight)
                    close()
                }
            drawPath(marker, color = ink)
        }

        val currentTool = tools.getOrNull(state.currentSector % tools.size)
        if (currentTool != null) {
            Box(
                modifier =
                    Modifier.offset {
                        val labelY = state.hubCenter.y - OUTER_RADIUS_DP - LABEL_GAP_DP
                        IntOffset(
                            (hubPx.x - with(density) { 60.dp.toPx() }).toInt(),
                            with(density) { labelY.dp.toPx() }.toInt(),
                        )
                    },
            ) {
                BasicText(text = currentTool.label.uppercase(), style = Typography.radialLabel.copy(color = ink))
            }
        }
    }
}

/** The explorer's own radial-sector icon size (`drawRadial`'s own `width="20" height="20"`, `ic-*`'s native viewBox -- not shrunk to fit the sector). */
private const val ICON_BOX_DP = 20.0

private fun wedgePath(
    hubCenter: Offset,
    innerRadius: Float,
    outerRadius: Float,
    startAngleDeg: Float,
    sweepDeg: Float,
): Path {
    val outerRect = Rect(hubCenter.x - outerRadius, hubCenter.y - outerRadius, hubCenter.x + outerRadius, hubCenter.y + outerRadius)
    val innerRect = Rect(hubCenter.x - innerRadius, hubCenter.y - innerRadius, hubCenter.x + innerRadius, hubCenter.y + innerRadius)
    return Path().apply {
        arcTo(outerRect, startAngleDeg, sweepDeg, true)
        arcTo(innerRect, startAngleDeg + sweepDeg, -sweepDeg, false)
        close()
    }
}

private const val INNER_RADIUS_DP = 50.0
private const val OUTER_RADIUS_DP = 120.0
private const val GAP_DP = 2.0
private const val LABEL_GAP_DP = 20.0
