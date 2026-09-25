// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.learn

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.SpacingTokens
import com.asoc.typewright.ui.tokens.Typography
import com.asoc.typewright.ui.tokens.toColor
import kotlin.math.roundToInt

/**
 * The Learn screen's Overlay tab, real content wired to real outline geometry
 * (`ui/typewright-explorer.html`'s `#s-learn` station, its own `#ln-ov` markup and `.wordf` /
 * `.ov` / `.ovctl` / `.seg` / `.layers` / `.ly` / `.sw` CSS -- CLAUDE.md law 6, reproduced rather
 * than improvised). Its own standalone tab, matching [com.asoc.typewright.ui.learn.LineagesTab]'s
 * own shape (`texture: CanvasTexture, modifier: Modifier = Modifier`), fully self-contained: it
 * loads its own three real layers ([loadDefaultOverlayLayers]) and keeps its own alignment/redline/
 * layer-visibility state.
 *
 * **Real geometry, not platform text.** [com.asoc.typewright.ui.learn.LearnFaceFonts]'s own KDoc
 * (the sibling Lineages tab's font-loading piece, checked before writing this) already draws the
 * line this file lives on the other side of: platform text rendering is the *finished, opaque*
 * rendering of a face -- the right tool when the caller only wants to *show* a typeface. A redline
 * diff or a stroke probe needs the *raw outline* to measure and dash, which platform text does not
 * expose. Every layer's outline here is real [com.asoc.typewright.core.geometry.Glyph] data, read
 * by `core-font`'s sfnt reader (Hyle Deco via [HyleDecoProjectFontBytes]'s own embedded bytes; EB
 * Garamond/Libre Baskerville via `data/learn-faces`'s own real fetched files,
 * [com.asoc.typewright.learn.scenes.LearnFaceResources]), converted to a Compose [androidx.compose.ui.graphics.Path]
 * through `GeometryInterop.kt`'s own outline bridge ([com.asoc.typewright.ui.toComposePath]) --
 * confirmed general over any of the seventeen real fetched Learn faces, not hardcoded to Hyle
 * Deco: `core-font`'s `glyf`/`loca` reader takes a variable font's *default* master directly, with
 * no `gvar` handling at all (`SfntFont.kt`'s own KDoc, read in full before writing this file; its
 * `readSfntFont` never even opens a `gvar` table), which is exactly the default/Regular instance
 * every one of these files' own filenames names (`EBGaramond[wght].ttf`'s own `fvar` default is
 * `wght=400`, confirmed directly against the real file with fontTools while building this task,
 * alongside Libre Baskerville and Hyle Deco itself -- all three parse and render correctly through
 * this exact path, real screenshot in this task's own report).
 *
 * **Colour + line pattern (CLAUDE.md law 8).** The project layer draws in the texture's own plain
 * ink colour (solid fill normally; stroke-only in redline mode, `ui/typewright-explorer.html`'s
 * own `.ov.redline .lay.ink` rule reproduced exactly). Each comparison layer gets its own
 * [com.asoc.typewright.ui.tokens.MeaningColor] *and* its own dash pattern -- never colour alone
 * -- via [OverlayLayer.meaningColor]/[OverlayLayer.dashPattern]; see [OverlayLayer]'s own KDoc for
 * why this build uses amber/magenta rather than the explorer's own literal violet/cyan swatches.
 * Redline mode's own divergence ticks reuse the diverging layer's own colour (a shape, a tick
 * mark, layered onto an existing meaning rather than inventing a new one) plus a plain-language
 * readout in the probe row -- law 8's "severity is a shape and a word", never a new red/green.
 */
@Composable
public fun OverlayTab(
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
) {
    val layersResult = remember { loadDefaultOverlayLayers() }
    val ink = texture.ink.toColor()
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    val lineColor = texture.line.toColor()
    val canvasColor = texture.canvas.toColor()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(canvasColor)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SpacingTokens.GUTTER_DP.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val layers = layersResult.getOrNull()
        if (layers == null) {
            OverlayUnavailableNote(fg = fg, muted = muted, reason = layersResult.exceptionOrNull()?.message)
            return@Column
        }

        var alignment by remember { mutableStateOf(OverlayAlignment.CAP_HEIGHT) }
        var redlineOn by remember { mutableStateOf(false) }
        var hiddenLayerIds by remember { mutableStateOf(emptySet<String>()) }

        WordFieldRow(fg = fg, muted = muted)

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }
            val heightDp = maxWidth / OVERLAY_ASPECT_RATIO
            val heightPx = with(density) { heightDp.toPx() }

            val plan =
                remember(layers, alignment, redlineOn, hiddenLayerIds, widthPx, heightPx) {
                    buildOverlayRenderPlan(layers, alignment, redlineOn, hiddenLayerIds, widthPx, heightPx)
                }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Canvas(Modifier.fillMaxWidth().height(heightDp)) {
                    val metricStrokePx = 1f
                    drawLine(lineColor, Offset(0f, plan.baselineYPx), Offset(size.width, plan.baselineYPx), strokeWidth = metricStrokePx)
                    val alignY = plan.baselineYPx - plan.targetHeightPx
                    drawLine(lineColor, Offset(0f, alignY), Offset(size.width, alignY), strokeWidth = metricStrokePx)

                    val strokeWidthPx = with(density) { 1.6.dp.toPx() }
                    for (layerPlan in plan.visibleLayers) {
                        if (layerPlan.layer.id == PROJECT_LAYER_ID) {
                            if (redlineOn) {
                                drawPath(layerPlan.path, color = ink, style = Stroke(width = strokeWidthPx))
                            } else {
                                drawPath(layerPlan.path, color = ink, style = Fill)
                            }
                        } else {
                            val color =
                                layerPlan.layer.meaningColor
                                    ?.forTexture(texture.id)
                                    ?.toColor() ?: fg
                            val dashDp = layerPlan.layer.dashPattern
                            val effect =
                                dashDp?.let { intervals ->
                                    PathEffect.dashPathEffect(
                                        FloatArray(intervals.size) { i -> with(density) { intervals[i].dp.toPx() } },
                                        0f,
                                    )
                                }
                            drawPath(layerPlan.path, color = color, style = Stroke(width = strokeWidthPx, pathEffect = effect))
                        }
                    }

                    plan.divergence?.let { divergence ->
                        if (divergence.tickPointsPx.isNotEmpty()) {
                            val tickColor =
                                divergence.comparisonLayer.meaningColor
                                    ?.forTexture(texture.id)
                                    ?.toColor() ?: fg
                            val half = with(density) { 3.dp.toPx() }
                            val tickStrokePx = with(density) { 1.3.dp.toPx() }
                            for (p in divergence.tickPointsPx) {
                                drawLine(
                                    tickColor,
                                    Offset(p.x - half, p.y - half),
                                    Offset(p.x + half, p.y + half),
                                    strokeWidth = tickStrokePx,
                                )
                                drawLine(
                                    tickColor,
                                    Offset(p.x - half, p.y + half),
                                    Offset(p.x + half, p.y - half),
                                    strokeWidth = tickStrokePx,
                                )
                            }
                        }
                    }
                }

                OverlayControlsRow(
                    fg = fg,
                    muted = muted,
                    ink = ink,
                    canvasColor = canvasColor,
                    alignment = alignment,
                    onAlignmentChange = { alignment = it },
                    redlineOn = redlineOn,
                    onRedlineToggle = { redlineOn = !redlineOn },
                    probe = plan.probe,
                )

                if (redlineOn) {
                    RedlineSummaryText(muted = muted, divergence = plan.divergence)
                }

                if (plan.unavailableLayerIds.isNotEmpty()) {
                    val names = layers.filter { it.id in plan.unavailableLayerIds }.joinToString(", ") { it.label }
                    BasicText(
                        text = "no real cap-height/x-height for: $names (font has neither the glyph nor a usable OS/2 fallback)",
                        style = Typography.mono(sizeSp = 9.5).copy(color = muted),
                    )
                }
            }
        }

        LayersList(
            layers = layers,
            hiddenLayerIds = hiddenLayerIds,
            onToggle = { id -> hiddenLayerIds = if (id in hiddenLayerIds) hiddenLayerIds - id else hiddenLayerIds + id },
            fg = fg,
            muted = muted,
            canvasColor = canvasColor,
            texture = texture,
        )

        BasicText(
            text =
                "Each layer has a colour and a line pattern. Aligned at " +
                    (if (alignment == OverlayAlignment.CAP_HEIGHT) "cap height" else "x-height") +
                    ", the invisible differences become obvious: width, stroke, where the curves turn.",
            style = Typography.sentence.copy(color = muted),
        )
    }
}

/** `#ovSvg`'s own `viewBox="0 0 960 300"` aspect ratio, reproduced exactly (960/300). */
private const val OVERLAY_ASPECT_RATIO = 960f / 300f

@Composable
private fun WordFieldRow(
    fg: Color,
    muted: Color,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
        BasicText(text = "WORD", style = Typography.mono(sizeSp = 9.5).copy(color = muted), maxLines = 1)
        // No maxLines/ellipsis here: the word itself is the one thing this row exists to show,
        // so a narrow phone width wraps it (Compose's own default word-wrap; "Hamburg" has no
        // internal space to break on, so a genuinely tight width still splits mid-word) rather
        // than hiding letters behind an ellipsis.
        BasicText(text = OVERLAY_DEFAULT_WORD, style = Typography.sentence.copy(color = fg), modifier = Modifier.weight(1f))
        BasicText(text = "per glyph ›", style = Typography.mono(sizeSp = 9.5).copy(color = muted), maxLines = 1)
    }
}

@Composable
private fun OverlayControlsRow(
    fg: Color,
    muted: Color,
    ink: Color,
    canvasColor: Color,
    alignment: OverlayAlignment,
    onAlignmentChange: (OverlayAlignment) -> Unit,
    redlineOn: Boolean,
    onRedlineToggle: () -> Unit,
    probe: StrokeProbeReading,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            SegmentedButton("cap height", alignment == OverlayAlignment.CAP_HEIGHT, ink, canvasColor, muted) {
                onAlignmentChange(OverlayAlignment.CAP_HEIGHT)
            }
            SegmentedButton("x-height", alignment == OverlayAlignment.X_HEIGHT, ink, canvasColor, muted) {
                onAlignmentChange(OverlayAlignment.X_HEIGHT)
            }
        }
        Box(
            modifier =
                Modifier
                    .let { if (redlineOn) it.background(ink) else it }
                    .clickable(onClick = onRedlineToggle)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
        ) {
            BasicText(text = "REDLINE", style = Typography.mono(sizeSp = 9.5).copy(color = if (redlineOn) canvasColor else fg))
        }
        BasicText(text = "probe: ${probeReadoutText(probe)}", style = Typography.mono(sizeSp = 9.5).copy(color = muted))
    }
}

@Composable
private fun SegmentedButton(
    label: String,
    selected: Boolean,
    ink: Color,
    canvasColor: Color,
    muted: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .let { if (selected) it.background(ink) else it }
                .clickable(onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        BasicText(text = label, style = Typography.mono(sizeSp = 9.5).copy(color = if (selected) canvasColor else muted))
    }
}

/** `stem 44 · bar 43 · contrast 1.25`, or `--` for any field [strokeProbe] could not measure (CLAUDE.md law 5: never a guessed number). */
private fun probeReadoutText(probe: StrokeProbeReading): String {
    fun fmt(v: Double?): String = v?.let { (it * 100.0).roundToInt() / 100.0 }?.toString() ?: "--"
    return "stem ${fmt(probe.stemUnits)} · bar ${fmt(probe.barUnits)} · contrast ${fmt(probe.contrast)}"
}

@Composable
private fun RedlineSummaryText(
    muted: Color,
    divergence: DivergenceSummary?,
) {
    val text =
        when {
            divergence == null -> {
                "redline: no second visible layer to compare against"
            }

            divergence.tickPointsPx.isEmpty() -> {
                "redline: 'H' matches ${divergence.comparisonLayer.label} within tolerance"
            }

            else -> {
                val pct = (divergence.maxDivergenceFraction * 100.0 * 10.0).roundToInt() / 10.0
                "redline: 'H' diverges from ${divergence.comparisonLayer.label} by up to $pct% of cap height at ${divergence.tickPointsPx.size} points"
            }
        }
    BasicText(text = text, style = Typography.mono(sizeSp = 9.5).copy(color = muted))
}

@Composable
private fun LayersList(
    layers: List<OverlayLayer>,
    hiddenLayerIds: Set<String>,
    onToggle: (String) -> Unit,
    fg: Color,
    muted: Color,
    canvasColor: Color,
    texture: CanvasTexture,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        for (layer in layers) {
            val isHidden = layer.id in hiddenLayerIds
            // A face's own real family name (e.g. "Libre Baskerville") and its real source label
            // are both content this tab exists to show, not chrome to truncate -- an earlier
            // version of this row packed swatch/name/"yours"/source into one line with a
            // `weight(1f)` spacer, which on a real phone-width canvas (`OverlayTabScreenshotTest`'s
            // own 210dp-logical viewport, this codebase's own established screenshot width)
            // squeezed the trailing source label to a near-zero width and wrapped it one
            // character per line. Two short lines, name above source, both left-aligned under the
            // swatch, reads correctly at any width no matter how long a real family name or
            // source string is, without hiding a single letter of either behind an ellipsis.
            Column(
                modifier = Modifier.fillMaxWidth().clickable { onToggle(layer.id) }.padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    LayerSwatch(layer, fg, texture)
                    BasicText(text = layer.label, style = Typography.sentence.copy(color = if (isHidden) muted else fg))
                    if (layer.id == PROJECT_LAYER_ID) {
                        BasicText(text = "yours", style = Typography.mono(sizeSp = 9.5).copy(color = muted))
                    }
                }
                BasicText(
                    text = layer.sourceLabel,
                    style = Typography.mono(sizeSp = 9.5).copy(color = muted),
                    modifier = Modifier.padding(start = SWATCH_WIDTH_DP.dp + 12.dp),
                )
            }
        }
    }
}

/** `.ly .sw`: a short swatch line in the layer's own colour and dash pattern -- solid ink for the project layer, dashed/dotted for a comparison layer, matching the canvas exactly. */
@Composable
private fun LayerSwatch(
    layer: OverlayLayer,
    fg: Color,
    texture: CanvasTexture,
) {
    val color = layer.meaningColor?.forTexture(texture.id)?.toColor() ?: fg
    Canvas(Modifier.width(SWATCH_WIDTH_DP.dp).height(12.dp).padding(top = 5.dp)) {
        val y = size.height / 2f
        val strokeWidthPx = 2.dp.toPx()
        val effect =
            layer.dashPattern?.let { intervals ->
                PathEffect.dashPathEffect(FloatArray(intervals.size) { i -> intervals[i].dp.toPx() }, 0f)
            }
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = strokeWidthPx, pathEffect = effect)
    }
}

private const val SWATCH_WIDTH_DP = 34

@Composable
private fun OverlayUnavailableNote(
    fg: Color,
    muted: Color,
    reason: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(text = "Overlay is not available on this target yet.", style = Typography.sentence.copy(color = fg))
        BasicText(
            text = reason ?: "could not read this build's own font resources.",
            style = Typography.mono(sizeSp = 9.5).copy(color = muted),
        )
    }
}
