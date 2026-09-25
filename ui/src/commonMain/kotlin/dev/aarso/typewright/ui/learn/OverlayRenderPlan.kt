// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.ui.learn

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import dev.aarso.typewright.core.geometry.Glyph
import dev.aarso.typewright.core.geometry.Vec2
import dev.aarso.typewright.ui.toComposePath

/** `ui/typewright-explorer.html`'s `#ln-ov` `.ovctl` `.seg`: which real metric [OverlayTab] aligns every visible layer's own cap-height-or-x-height line to. */
enum class OverlayAlignment { CAP_HEIGHT, X_HEIGHT }

/** One [OverlayLayer]'s own real word outline, already built into a Compose [Path] in canvas-pixel space -- [OverlayRenderPlan.buildOverlayRenderPlan]'s own output, ready to draw with no further geometry work. */
data class LayerRenderPlan(
    val layer: OverlayLayer,
    val path: Path,
)

/** [findDivergentPoints]' own real output for the redline check, plus a plain-language summary -- CLAUDE.md law 8's "severity is a shape and a word": [tickPointsPx] is drawn as a shape (a small tick), [maxDivergenceFraction] is the word. */
data class DivergenceSummary(
    val comparisonLayer: OverlayLayer,
    val tickPointsPx: List<Offset>,
    /** The single largest divergence found, as a fraction of [OverlayRenderPlan.targetHeightPx] (the shared aligned cap-height/x-height) -- unit-agnostic on purpose: the two layers being compared can have different `unitsPerEm`, so a raw font-unit number would not mean the same thing for both. */
    val maxDivergenceFraction: Double,
)

/** Everything [OverlayTab] needs to actually draw one frame: the shared metric-line positions, every visible layer's own ready-to-draw [Path], the redline check's own result (`null` when redline mode is off or there is no second visible layer to compare against), and the project layer's real [StrokeProbeReading]. Built once per (layers, alignment, redlineOn, hiddenLayerIds, canvas size) combination -- see [buildOverlayRenderPlan]. */
data class OverlayRenderPlan(
    val baselineYPx: Float,
    val targetHeightPx: Float,
    val visibleLayers: List<LayerRenderPlan>,
    val unavailableLayerIds: Set<String>,
    val divergence: DivergenceSummary?,
    val probe: StrokeProbeReading,
)

/** One glyph of a laid-out word, plus the transform that places it: [glyph] itself (for a probe/divergence check that needs the raw outline) and [transform] (font-units, y-up -> canvas pixels, y-down) together. */
private data class PositionedGlyph(
    val glyph: Glyph,
    val transform: (Vec2) -> Offset,
)

/** [OVERLAY_DEFAULT_WORD]'s own glyphs from [layer], left-to-right, each glyph's own real advance width (scaled by [scale]) carrying the cursor to the next one -- or `null` if [layer] is missing any letter of the word. [layer]'s own outline is drawn at [scale] (canvas px per font unit) with its baseline at [baselineYPx] and its first glyph's left sidebearing origin at [startXPx]. */
private fun layOutWord(
    layer: OverlayLayer,
    scale: Float,
    startXPx: Float,
    baselineYPx: Float,
): List<PositionedGlyph>? {
    val glyphs = layer.glyphsForWord(OVERLAY_DEFAULT_WORD) ?: return null
    var cursor = startXPx
    val result = ArrayList<PositionedGlyph>(glyphs.size)
    for (g in glyphs) {
        val originX = cursor
        val transform: (Vec2) -> Offset = { v -> Offset(originX + (v.x * scale).toFloat(), baselineYPx - (v.y * scale).toFloat()) }
        result += PositionedGlyph(g, transform)
        cursor += g.advanceWidth * scale
    }
    return result
}

/** [OVERLAY_DEFAULT_WORD]'s own total advance width from [layer], in canvas px at [scale] -- the same number [layOutWord] would end its cursor at, computed without building any [PositionedGlyph]/[androidx.compose.ui.graphics.Path] (used only to decide whether [buildOverlayRenderPlan] needs to shrink [targetHeightPx] to keep the word on screen). `null` if [layer] is missing any letter of the word. */
private fun measureWordWidthPx(
    layer: OverlayLayer,
    scale: Float,
): Float? {
    val glyphs = layer.glyphsForWord(OVERLAY_DEFAULT_WORD) ?: return null
    return glyphs.sumOf { it.advanceWidth.toDouble() }.toFloat() * scale
}

/** [layer]'s own real scale factor (canvas px per font unit) for [alignment]: [targetHeightPx] divided by [layer]'s own real cap-height or x-height, in font units. `null` when [layer] has neither its own drawn glyph nor a positive `OS/2` fallback for that metric ([OverlayLayer.capHeightUnits]/[OverlayLayer.xHeightUnits]'s own KDoc) -- never a guessed default (CLAUDE.md law 5). */
private fun scaleFor(
    layer: OverlayLayer,
    alignment: OverlayAlignment,
    targetHeightPx: Float,
): Float? {
    val basisUnits = if (alignment == OverlayAlignment.CAP_HEIGHT) layer.capHeightUnits else layer.xHeightUnits
    if (basisUnits == null || basisUnits <= 0.0) return null
    return (targetHeightPx / basisUnits).toFloat()
}

private fun Offset.toVec2Plain(): Vec2 = Vec2(x.toDouble(), y.toDouble())

/**
 * Builds one real [OverlayRenderPlan] for [layers] at [canvasWidthPx] by [canvasHeightPx]:
 * lays out [OVERLAY_DEFAULT_WORD] for every layer not in [hiddenLayerIds] at [alignment]'s own
 * real scale (each layer independently scaled so its *own* real cap-height/x-height lands on the
 * same shared line -- `ui/typewright-explorer.html`'s own caption, "Aligned at cap height, the
 * invisible differences become obvious"), computes the real redline divergence between the
 * project layer and the first other visible layer when [redlineOn] (via [findDivergentPoints],
 * `OutlineDivergence.kt`), and reads the real [strokeProbe] off the project layer's own `b`/`H`/`o`
 * glyphs. Pure aside from building [Path]s (no [androidx.compose.runtime.Composable] call here,
 * so a caller can build/cache this inside `remember` and draw it in a plain [DrawScope]).
 */
fun buildOverlayRenderPlan(
    layers: List<OverlayLayer>,
    alignment: OverlayAlignment,
    redlineOn: Boolean,
    hiddenLayerIds: Set<String>,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
): OverlayRenderPlan {
    // These two fractions of the canvas height place the shared alignment line and the shared
    // baseline: chosen to leave headroom above the alignment line for a tall ascender/overshoot
    // and room below the baseline for a descender, matching `ui/typewright-explorer.html`'s own
    // `#ovSvg` viewBox proportions (`viewBox="0 0 960 300"`, its own `ovParts()` placing a 200px
    // em-square against a 230px baseline row -- both roughly these same two fractions of 300).
    val nominalTargetHeightPx = canvasHeightPx * 0.62f
    val startXPx = canvasWidthPx * 0.03f
    val visible = layers.filter { it.id !in hiddenLayerIds }

    // Fit-to-width: a real word set at a real per-layer scale can come out wider than the
    // available canvas (a serif comparison face's own real advance widths are not the same as
    // the project font's -- that difference is exactly what this tab exists to show), so a fixed
    // targetHeightPx would silently run "Hamburg" off the right edge on a phone-width canvas.
    // Every visible layer keeps the *same* shared alignment height regardless (CLAUDE.md law 6:
    // "aligned at cap height" must stay true of every layer at once) -- when the *widest* layer's
    // own word would not fit, this shrinks that one shared height uniformly, rather than shrinking
    // one layer's own glyphs relative to the others, which would silently defeat the alignment
    // this whole tab is built to show.
    val availableWidthPx = (canvasWidthPx - 2f * startXPx).coerceAtLeast(1f)
    val nominalMaxWordWidthPx =
        visible
            .mapNotNull { layer -> scaleFor(layer, alignment, nominalTargetHeightPx)?.let { measureWordWidthPx(layer, it) } }
            .maxOrNull()
    val targetHeightPx =
        if (nominalMaxWordWidthPx != null && nominalMaxWordWidthPx > availableWidthPx) {
            nominalTargetHeightPx * (availableWidthPx / nominalMaxWordWidthPx)
        } else {
            nominalTargetHeightPx
        }
    val baselineYPx = canvasHeightPx * 0.80f

    val project = layers.firstOrNull { it.id == PROJECT_LAYER_ID }

    val positionedByLayer = LinkedHashMap<String, List<PositionedGlyph>>()
    val unavailable = mutableSetOf<String>()
    val layerPlans = mutableListOf<LayerRenderPlan>()
    for (layer in visible) {
        val scale = scaleFor(layer, alignment, targetHeightPx)
        val positioned = if (scale == null) null else layOutWord(layer, scale, startXPx, baselineYPx)
        if (scale == null || positioned == null) {
            unavailable += layer.id
            continue
        }
        positionedByLayer[layer.id] = positioned
        // Build the whole-word path by unioning every glyph's own outline (Glyph.toComposePath
        // already does this per glyph; addPath below chains every glyph of the word into one
        // Path this layer draws as a single fill/stroke call).
        val wordPath = Path()
        for (pg in positioned) wordPath.addPath(pg.glyph.toComposePath(pg.transform))
        layerPlans += LayerRenderPlan(layer, wordPath)
    }

    val divergence =
        if (redlineOn && project != null) {
            val projectPositioned = positionedByLayer[project.id]
            val comparisonLayer = visible.firstOrNull { it.id != project.id && positionedByLayer.containsKey(it.id) }
            val comparisonPositioned = comparisonLayer?.let { positionedByLayer[it.id] }
            if (projectPositioned != null && comparisonPositioned != null && comparisonLayer != null) {
                val projectH = projectPositioned.first()
                val comparisonH = comparisonPositioned.first()
                val toleranceCanvasUnits = targetHeightPx * DIVERGENCE_TOLERANCE_FRACTION
                val divergentPoints =
                    findDivergentPoints(
                        a = projectH.glyph,
                        aToCanvas = { v -> projectH.transform(v).toVec2Plain() },
                        b = comparisonH.glyph,
                        bToCanvas = { v -> comparisonH.transform(v).toVec2Plain() },
                        toleranceCanvasUnits = toleranceCanvasUnits.toDouble(),
                    )
                if (divergentPoints.isEmpty()) {
                    DivergenceSummary(comparisonLayer, emptyList(), 0.0)
                } else {
                    val bContours = comparisonH.glyph.flattenContours().map { poly -> poly.map { comparisonH.transform(it).toVec2Plain() } }
                    val maxDistancePx = divergentPoints.maxOf { distanceToNearestOutline(it, bContours) }
                    DivergenceSummary(
                        comparisonLayer = comparisonLayer,
                        tickPointsPx = divergentPoints.map { Offset(it.x.toFloat(), it.y.toFloat()) },
                        maxDivergenceFraction = (maxDistancePx / targetHeightPx),
                    )
                }
            } else {
                null
            }
        } else {
            null
        }

    val probe = strokeProbe(project?.glyphs?.get('b'), project?.glyphs?.get('H'), project?.glyphs?.get('o'))

    return OverlayRenderPlan(
        baselineYPx = baselineYPx,
        targetHeightPx = targetHeightPx,
        visibleLayers = layerPlans,
        unavailableLayerIds = unavailable,
        divergence = divergence,
        probe = probe,
    )
}

/** [OverlayLayer.id] convention this file and [OverlayDemoLayers.kt] both use for the project ("ink") layer. */
const val PROJECT_LAYER_ID = "project"

/** How far (as a fraction of the shared aligned cap-height/x-height) two layers' outlines may sit apart before redline mode marks a point as diverging -- 1.5%, a real, general default (not per-font special-cased): small enough to catch a genuinely different stroke width or curve turn, large enough that ordinary polygon-vs-curve sampling noise from [findDivergentPoints]' own flattening does not itself read as divergence. */
const val DIVERGENCE_TOLERANCE_FRACTION = 0.015
