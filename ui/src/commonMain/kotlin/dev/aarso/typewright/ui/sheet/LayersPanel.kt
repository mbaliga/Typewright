// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.aarso.typewright.ui.tokens.CanvasTexture
import dev.aarso.typewright.ui.tokens.SpacingTokens
import dev.aarso.typewright.ui.tokens.Typography
import dev.aarso.typewright.ui.tokens.toColor
import kotlin.math.roundToInt

/**
 * The background/sketch layer toggle (P5b task item 4; see [LayerState]'s own KDoc for why this
 * is a minimal, honestly-scoped affordance rather than a reproduction of any explorer screen).
 * Reuses this module's own established glass vocabulary -- a canvas-filled column, mono uppercase
 * labels, an ink block for the "on" state (UI_SPEC §5.4 "Selected: ink block"), matching
 * [dev.aarso.typewright.ui.puck.UnfoldedToolList]'s row language and [BloomLayer]/
 * [WorldLinesPass]'s own token usage -- rather than inventing new chrome.
 *
 * Each [LayerKind] gets one row: the label toggles [LayerState.visible] (ink block when visible,
 * plain text -- at reduced alpha, so a hidden layer visibly recedes -- when not); a tappable
 * percentage cycles [LayerState.opacity] through [OPACITY_STEPS]; a single-letter "L" toggles
 * [LayerState.locked] (ink block when locked). No slider, no drag -- task's own "simplest-possible
 * toggle affordance".
 */
@Composable
fun LayersPanel(
    state: LayersState,
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
) {
    val ink = texture.ink.toColor()
    val canvas = texture.canvas.toColor()
    val muted = texture.muted.toColor()

    Box(modifier = modifier.width(PANEL_WIDTH_DP.dp).background(canvas)) {
        Column {
            LayerRow(LayerKind.BACKGROUND, state.background, state, ink, canvas, muted)
            LayerRow(LayerKind.SKETCH, state.sketch, state, ink, canvas, muted)
        }
    }
}

@Composable
private fun LayerRow(
    kind: LayerKind,
    layer: LayerState,
    state: LayersState,
    ink: Color,
    canvas: Color,
    muted: Color,
) {
    val onToggleVisible = { state.toggleVisible(kind) }
    val onCycleOpacity = { state.cycleOpacity(kind) }
    val onToggleLocked = { state.toggleLocked(kind) }
    Row(
        modifier = Modifier.height(ROW_HEIGHT_DP.dp).width(PANEL_WIDTH_DP.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier =
                Modifier
                    .let { if (layer.visible) it.background(ink) else it }
                    .clickable(onClick = onToggleVisible)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            BasicText(
                text = kind.label.uppercase(),
                style = Typography.mono(sizeSp = 9.5).copy(color = if (layer.visible) canvas else muted),
            )
        }
        BasicText(
            text = "${(layer.opacity * 100).roundToInt()}%",
            style = Typography.mono(sizeSp = 9.5).copy(color = muted),
            modifier = Modifier.clickable(onClick = onCycleOpacity).padding(horizontal = 4.dp),
        )
        Box(
            modifier =
                Modifier
                    .let { if (layer.locked) it.background(ink) else it }
                    .clickable(onClick = onToggleLocked)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            BasicText(text = "L", style = Typography.mono(sizeSp = 9.5).copy(color = if (layer.locked) canvas else muted))
        }
    }
}

private const val PANEL_WIDTH_DP = 140.0
private const val ROW_HEIGHT_DP = 32.0

/** [LayersPanel]'s own default position: below the header, opposite gutter from the room name (task's own call -- the explorer names no position for this since it shows no panel at all). */
val LayersPanelTopPaddingDp: Double = 54.0 + SpacingTokens.GUTTER_DP
