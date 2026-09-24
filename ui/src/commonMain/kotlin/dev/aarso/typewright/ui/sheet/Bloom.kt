package dev.aarso.typewright.ui.sheet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import dev.aarso.typewright.core.geometry.Vec2
import dev.aarso.typewright.ui.toOffset
import dev.aarso.typewright.ui.tokens.BloomToken
import dev.aarso.typewright.ui.tokens.CanvasTexture
import dev.aarso.typewright.ui.tokens.toColor

/** One bloom zone (UI_SPEC §1 layer 4): a quiet paper-coloured radial fade centred on [center] (screen dp, glass space), [radiusDp] wide. */
data class BloomZone(
    val center: Vec2,
    val radiusDp: Double,
)

/**
 * The bloom (`docs/ARCHITECTURE_REVIEW.md` §4.1 recommendation 2/"Gradients"): "a SCREEN-SPACE
 * pass (never transformed with the world), radial gradients from canvas colour to
 * canvas-colour-at-alpha-0 ... positioned under each glass element". Sits between the two
 * `WorldPass`es in [TypewrightSheet]'s `Box`, drawing directly in screen space (no camera read at
 * all -- [zones] are already glass-space coordinates, exactly like the puck's own [dev.aarso.
 * typewright.ui.puck.PuckPinState]), so it can never drift from the glass it fades under, unlike
 * the explorer's own CSS counter-translation hack.
 *
 * **"Record once, redraw only when it changes"** (the review's own performance note): built with
 * `Modifier.drawWithCache`, whose cache-building block reads [zones] and [texture] -- Compose's
 * own mechanism for "rebuild the [Brush]es only when these change, otherwise just replay the
 * cached draw commands every frame" is exactly `drawWithCache`; no bespoke `rememberGraphicsLayer`
 * capture was needed to get that property here, since a `Brush` object is the expensive part to
 * avoid rebuilding, not a whole rasterised layer (there is no cached ink content to rasterise in
 * this task -- `RoomInk.kt`'s placeholder content is cheap to redraw directly).
 */
@Composable
fun BloomLayer(
    zones: List<BloomZone>,
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier.fillMaxSize().drawWithCache {
                val (innerArgb, outerArgb) = BloomToken.stopsFor(texture)
                val inner = innerArgb.toColor()
                // Never `Color.Transparent` -- see BloomToken's own KDoc for the review's finding.
                val outer = outerArgb.toColor()
                val prepared =
                    zones.map { zone ->
                        val centerPx = zone.center.toOffset(this)
                        val radiusPx = zone.radiusDp.dp.toPx()
                        Triple(
                            Brush.radialGradient(colors = listOf(inner, outer), center = centerPx, radius = radiusPx),
                            centerPx,
                            radiusPx,
                        )
                    }
                onDrawBehind {
                    for ((brush, centerPx, radiusPx) in prepared) {
                        drawCircle(brush = brush, radius = radiusPx, center = centerPx)
                    }
                }
            },
    )
}
