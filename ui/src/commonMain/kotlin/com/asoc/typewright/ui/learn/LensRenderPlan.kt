// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.learn

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.qa.corpus.style.inkBounds
import com.asoc.typewright.ui.toComposePath
import kotlin.math.ceil

// The Anatomy Lens tab's pure render plan: [LensScene.kt]'s real scene data turned into pixel
// positions and a Compose [Path], the same "pure plan, separate `@Composable`" split
// `OverlayRenderPlan.kt` already established for the sibling Overlay tab -- testable directly
// (`LensRenderPlanTest`/`LensRenderPlanHyleDecoTest`), no Compose test rule needed.
//
// [com.asoc.typewright.ui.toComposePath] (`GeometryInterop.kt`, already built and already used by
// the Overlay tab's own project layer) is the one outline-to-Compose-path bridge this file reuses
// rather than reinventing (CLAUDE.md: "reuse these, do not invent a second... outline-to-Compose
// bridge").

/** One term's own leader line + dot + label, already placed in pixel space for the glyph currently on stage. */
data class LensLabelPlan(
    val term: AnatomyTerm,
    val dotPx: Offset,
    val labelAnchorPx: Offset,
    val selected: Boolean,
)

/** Everything [LensTab]'s `Canvas` needs to draw one frame: the hero glyph's real outline, the baseline/x-height guide lines, and every term sharing that glyph as its own [LensLabelPlan]. `null` fields mean the selected term's hero glyph is missing from this font (an incomplete `cmap`) -- the composable then shows the definitions list with no diagram, never a blank or wrong glyph. */
data class LensRenderPlan(
    val heroChar: Char?,
    val glyphPath: Path?,
    val baselineYPx: Float?,
    val xHeightYPx: Float?,
    val labels: List<LensLabelPlan>,
)

/**
 * Per-term label placement: an offset, as a *fraction of the diagram's own [widthPx]/[heightPx]*
 * (never a fixed dp/px amount), from the leader line's dot to where its label sits -- chosen by eye
 * per term so the several simultaneous labels sharing one hero glyph (up to four, on `o`) fan out
 * around the glyph without overlapping it or each other, checked against this task's own real
 * rendered screenshot the same way `ui/typewright-explorer.html`'s own hand-placed `L` array was
 * checked against its one static scene. A fraction of the diagram's own size, rather than a fixed
 * pixel amount, is what keeps a label on-canvas at any real container width (a phone's narrow
 * column and a wider desktop pane both fit the same fractions) -- a first version of this file used
 * a fixed dp offset instead, and a real screenshot at this tab's own actual (narrower than assumed)
 * width showed `bowl`'s and `roundness`'s own labels clipped off the left and right edges; fixed
 * here, not just guarded against, by [clampToCanvas] as a second, independent safety net.
 */
private val LABEL_OFFSET_FRACTION: Map<AnatomyTerm, Offset> =
    mapOf(
        AnatomyTerm.BOWL to Offset(-0.30f, 0f),
        AnatomyTerm.COUNTER to Offset(0f, 0.15f),
        AnatomyTerm.CONTRAST to Offset(0f, -0.14f),
        AnatomyTerm.ROUNDNESS to Offset(0.22f, 0f),
        AnatomyTerm.STEM to Offset(-0.30f, -0.02f),
        AnatomyTerm.TERMINAL to Offset(0.18f, -0.12f),
        AnatomyTerm.APERTURE to Offset(0.22f, 0.08f),
        AnatomyTerm.SERIF to Offset(0f, 0.11f),
        AnatomyTerm.STOREYS to Offset(0f, -0.13f),
    )

/** [point], moved (if necessary) to stay at least [marginPx] inside a `0..widthPx` x `0..heightPx` canvas -- a label anchor's own last-resort guard against sitting off-canvas (see [LABEL_OFFSET_FRACTION]'s own KDoc). */
private fun clampToCanvas(
    point: Offset,
    widthPx: Float,
    heightPx: Float,
    marginPx: Float,
): Offset =
    Offset(
        x = point.x.coerceIn(marginPx, (widthPx - marginPx).coerceAtLeast(marginPx)),
        y = point.y.coerceIn(marginPx, (heightPx - marginPx).coerceAtLeast(marginPx)),
    )

/** The minimum distance a label anchor is kept from the diagram's own edge -- a small, fixed fraction of the diagram's own [widthPx]/[heightPx] rather than a pixel amount, for the same reason [LABEL_OFFSET_FRACTION] is fraction-based. */
private const val LABEL_EDGE_MARGIN_FRACTION = 0.02f

/**
 * [selectedTerm]'s real [LensRenderPlan] against [glyphSet], fit into a [widthPx] x [heightPx]
 * pixel area: the hero glyph's own ascender-to-descender span (real `hhea` metrics,
 * [com.asoc.typewright.ui.learn.AnatomyLensGlyphSet.ascender]/`descender`) scaled to fill the
 * area minus [marginFraction] on top and bottom, centred horizontally on the glyph's own advance
 * width -- the same framing idea `core-font`'s own glyphs are drawn at everywhere else in `ui`
 * (advance-width centring, not ink-bounds centring, so an asymmetric glyph like `T` still sits
 * where a reader expects it).
 */
fun buildLensRenderPlan(
    glyphSet: AnatomyLensGlyphSet,
    selectedTerm: AnatomyTerm,
    widthPx: Float,
    heightPx: Float,
    marginFraction: Float = 0.16f,
): LensRenderPlan {
    val scene = lensSceneFor(selectedTerm, glyphSet)
    val heroChar = scene.heroChar
    val heroGlyph = scene.heroGlyph
    if (heroChar == null || heroGlyph == null) {
        return LensRenderPlan(heroChar = null, glyphPath = null, baselineYPx = null, xHeightYPx = null, labels = emptyList())
    }

    val ascender = glyphSet.ascender ?: heroGlyph.inkBounds()?.maxY?.let { ceil(it).toInt() } ?: DEFAULT_ASCENDER_UNITS
    val descender = glyphSet.descender ?: 0
    val spanUnits = (ascender - descender).takeIf { it > 0 }?.toDouble() ?: DEFAULT_ASCENDER_UNITS.toDouble()
    val usableHeightPx = heightPx * (1f - 2f * marginFraction)
    val scale = usableHeightPx / spanUnits
    val marginPx = heightPx * marginFraction
    val centerX = heroGlyph.advanceWidth / 2.0

    fun toPx(v: Vec2): Offset =
        Offset(
            x = (widthPx / 2.0 + (v.x - centerX) * scale).toFloat(),
            y = (marginPx + (ascender - v.y) * scale).toFloat(),
        )

    val glyphPath = heroGlyph.toComposePath(::toPx)
    val baselineYPx = toPx(Vec2(0.0, 0.0)).y
    val xHeightUnits =
        (
            (
                anatomyLensEntry(
                    AnatomyTerm.X_HEIGHT,
                    glyphSet,
                ) as? AnatomyLensEntry.Measured
            )?.value as? AnatomyLensValue.FontUnits
        )?.value
    val xHeightYPx = xHeightUnits?.let { toPx(Vec2(0.0, it.toDouble())).y }

    val labelMarginPx = LABEL_EDGE_MARGIN_FRACTION * minOf(widthPx, heightPx)
    val labels =
        termsSharingHeroChar(glyphSet, heroChar).mapNotNull { term ->
            val target = lensSceneFor(term, glyphSet).leaderTargetFontUnits ?: return@mapNotNull null
            val dotPx = toPx(target)
            val fraction = LABEL_OFFSET_FRACTION[term] ?: Offset.Zero
            val anchorPx = dotPx + Offset(fraction.x * widthPx, fraction.y * heightPx)
            LensLabelPlan(
                term = term,
                dotPx = dotPx,
                labelAnchorPx = clampToCanvas(anchorPx, widthPx, heightPx, labelMarginPx),
                selected = term == selectedTerm,
            )
        }

    return LensRenderPlan(heroChar, glyphPath, baselineYPx, xHeightYPx, labels)
}

/** Fallback vertical span (font units) when a font has neither real `hhea` metrics nor a usable hero-glyph ink height -- never actually hit for a font `readSfntFont` accepted (`hhea` is required), kept only so [buildLensRenderPlan] is total. */
private const val DEFAULT_ASCENDER_UNITS = 1000
