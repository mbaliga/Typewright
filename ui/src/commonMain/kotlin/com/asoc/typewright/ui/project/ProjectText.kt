// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.project

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.ui.toComposePath
import kotlin.math.min

/**
 * The outline of [glyph] as a Compose path in font units with y pointing down, so a draw scope only
 * has to scale and translate it. Filled with non-zero winding, which is how a font rasterises, so
 * what the screen shows is what a compiled font would show.
 */
fun glyphPathInFontUnits(
    glyph: Glyph,
    fillType: PathFillType = PathFillType.NonZero,
): Path = glyph.toComposePath({ Offset(it.x.toFloat(), -it.y.toFloat()) }, fillType)

/** Paths for the glyphs of one font, built once each and reused while the glyph is the same one. */
class GlyphPathCache {
    private val paths = HashMap<String, Pair<Glyph, Path>>()

    /** [glyph]'s path, rebuilt only when the glyph with that name has changed. */
    fun pathOf(glyph: Glyph): Path {
        val cached = paths[glyph.name]
        if (cached != null && cached.first === glyph) return cached.second
        return glyphPathInFontUnits(glyph).also { paths[glyph.name] = glyph to it }
    }
}

/** Draws [line] with its baseline at [baselineY] (px) and its left edge at [left] (px), [scale] px per font unit. */
fun DrawScope.drawSetLine(
    line: SetLine,
    paths: GlyphPathCache,
    scale: Float,
    left: Float,
    baselineY: Float,
    color: Color,
) {
    for (placed in line.glyphs) {
        if (placed.glyph.contours.isEmpty()) continue
        withTransform({
            translate(left + placed.x.toFloat() * scale, baselineY)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            drawPath(paths.pathOf(placed.glyph), color)
        }
    }
}

/**
 * [text] set in the open project's own outlines at [sizeSp] (px per em, as a font size would be),
 * on one line, or wrapped to the available width (and broken at each newline) when [wrap] is true,
 * or on one line made smaller until it fits the width when [shrinkToFit] is. Nothing here is a font file: each glyph is drawn
 * from its contours, with the project's advances and kerning, so the specimen is exactly the drawing
 * and a character the project has no glyph for leaves no mark.
 */
@Composable
fun ProjectText(
    font: ProjectFont,
    text: String,
    sizeSp: Float,
    color: Color,
    modifier: Modifier = Modifier,
    wrap: Boolean = false,
    shrinkToFit: Boolean = false,
    lineHeight: Float = DEFAULT_LINE_HEIGHT,
) {
    val density = LocalDensity.current
    val paths = remember(font) { GlyphPathCache() }
    val emPx = with(density) { sizeSp.dp.toPx() }
    val semantic = Modifier.semantics { contentDescription = text }
    if (!wrap && !shrinkToFit) {
        val line = remember(font, text) { font.setLine(text) }
        val scale = emPx / font.unitsPerEm
        val width = with(density) { (line.width.toFloat() * scale).toDp() }
        val height = with(density) { ((font.ascent + font.descent).toFloat() * scale).toDp() }
        Canvas(modifier.size(width.coerceAtLeast(1.dp), height.coerceAtLeast(1.dp)).then(semantic)) {
            drawSetLine(line, paths, scale, 0f, font.ascent * scale, color)
        }
    } else if (shrinkToFit) {
        val line = remember(font, text) { font.setLine(text) }
        BoxWithConstraints(modifier) {
            val maxPx = with(density) { maxWidth.toPx() }
            val fit = if (line.width > 0) maxPx / line.width.toFloat() else emPx / font.unitsPerEm
            val scale = min(emPx / font.unitsPerEm, fit)
            val width = with(density) { (line.width.toFloat() * scale).toDp() }
            val height = with(density) { ((font.ascent + font.descent).toFloat() * scale).toDp() }
            Canvas(Modifier.size(width.coerceAtLeast(1.dp), height.coerceAtLeast(1.dp)).then(semantic)) {
                drawSetLine(line, paths, scale, 0f, font.ascent * scale, color)
            }
        }
    } else {
        val scale = emPx / font.unitsPerEm
        BoxWithConstraints(modifier) {
            val maxWidthUnits = with(density) { maxWidth.toPx() } / scale
            val lines = remember(font, text, maxWidthUnits) { text.split('\n').flatMap { font.setLines(it, maxWidthUnits.toDouble()) } }
            val stepPx = emPx * lineHeight
            val heightDp = with(density) { (stepPx * (lines.size - 1).coerceAtLeast(0) + (font.ascent + font.descent) * scale).toDp() }
            Canvas(Modifier.size(maxWidth, heightDp.coerceAtLeast(1.dp)).then(semantic)) {
                lines.forEachIndexed { index, line ->
                    drawSetLine(line, paths, scale, 0f, font.ascent * scale + index * stepPx, color)
                }
            }
        }
    }
}

/**
 * One [glyph] of the project drawn at [sizeSp], in a box as wide as its advance (at least 0.4 em, so
 * a narrow glyph still has room to be tapped) and as tall as the font's line. Used where a glyph has
 * no character to set it by (an alternate, a ligature, `.notdef`).
 */
@Composable
fun ProjectGlyphView(
    font: ProjectFont,
    glyph: Glyph,
    sizeSp: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val paths = remember(font) { GlyphPathCache() }
    val scale = with(density) { sizeSp.dp.toPx() } / font.unitsPerEm
    val width =
        with(density) {
            maxOf(glyph.advanceWidth.toFloat(), font.unitsPerEm * MIN_GLYPH_BOX_EM) * scale
        }.let { with(density) { it.toDp() } }
    val height = with(density) { ((font.ascent + font.descent).toFloat() * scale).toDp() }
    Canvas(modifier.size(width.coerceAtLeast(1.dp), height.coerceAtLeast(1.dp)).semantics { contentDescription = glyph.name }) {
        val left = (size.width - glyph.advanceWidth * scale) / 2f
        drawSetLine(SetLine(listOf(SetGlyph(glyph, 0.0)), glyph.advanceWidth.toDouble()), paths, scale, left, font.ascent * scale, color)
    }
}

/**
 * One [glyph] drawn at [sizeSp] and centred in whatever box [modifier] gives it, made smaller only
 * when its advance would not fit the width. A grid cell uses this, so a wide glyph (a ligature) stays
 * inside its cell instead of running into the next one.
 */
@Composable
fun ProjectGlyphInBox(
    font: ProjectFont,
    glyph: Glyph,
    sizeSp: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val paths = remember(font) { GlyphPathCache() }
    val density = LocalDensity.current
    val nominal = with(density) { sizeSp.dp.toPx() } / font.unitsPerEm
    Canvas(modifier.semantics { contentDescription = glyph.name }) {
        val fit = if (glyph.advanceWidth > 0) size.width * FIT_FRACTION / glyph.advanceWidth else nominal
        val scale = min(nominal, fit)
        val lineHeight = (font.ascent + font.descent) * scale
        val left = (size.width - glyph.advanceWidth * scale) / 2f
        val baseline = (size.height - lineHeight) / 2f + font.ascent * scale
        drawSetLine(SetLine(listOf(SetGlyph(glyph, 0.0)), glyph.advanceWidth.toDouble()), paths, scale, left, baseline, color)
    }
}

private const val FIT_FRACTION = 0.92f
private const val MIN_GLYPH_BOX_EM = 0.4f

/** A line height of 1.35 em, the board's wrapped specimen. */
const val DEFAULT_LINE_HEIGHT: Float = 1.35f
