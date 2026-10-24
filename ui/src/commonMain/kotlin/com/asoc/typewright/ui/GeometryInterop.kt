// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.unit.Density
import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.CurveSegment
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.core.geometry.segments
import kotlin.math.roundToInt

// The one place raw Compose pixels convert to and from `core-geometry`'s Vec2 as screen dp. The
// sheet, puck and token files are deliberately Compose-free and work only in screen dp (see
// `PuckGestureEvents.kt`: "already density-converted by the caller"); this file is that caller's
// conversion, and the only place in `ui` that mixes raw pixels with Vec2.
//
// [Contour.addToComposePath]/[Glyph.toComposePath] are the outline-to-Compose-path bridge: general
// over [com.asoc.typewright.core.geometry.CurveFormat.QUADRATIC] (a TrueType source) and
// [com.asoc.typewright.core.geometry.CurveFormat.CUBIC] (a UFO source) alike, because both go
// through [Contour.segments].

/**
 * Appends [contour]'s outline to this [Path], projecting every anchor and control point through
 * [transform] (a caller-supplied font-units-to-Compose-space map, e.g. "scale to this alignment
 * height, flip y, offset by this word cursor position"). A [CurveSegment.Line] becomes
 * [Path.lineTo]; Compose's own [Path.quadraticTo]/[Path.cubicTo] take a quadratic or cubic
 * segment directly, so [com.asoc.typewright.core.geometry.CurveFormat.QUADRATIC] contours (every
 * TrueType `glyf` outline) need no cubic elevation. Returns this same [Path] (for chaining
 * several contours, or several glyphs, into one [Path]).
 */
fun Path.addContour(
    contour: Contour,
    transform: (Vec2) -> Offset,
): Path {
    val segs = contour.segments()
    if (segs.isEmpty()) return this
    val start = transform(segs.first().start)
    moveTo(start.x, start.y)
    for (seg in segs) {
        when (seg) {
            is CurveSegment.Line -> {
                val end = transform(seg.end)
                lineTo(end.x, end.y)
            }

            is CurveSegment.Quadratic -> {
                val control = transform(seg.control)
                val end = transform(seg.end)
                quadraticTo(control.x, control.y, end.x, end.y)
            }

            is CurveSegment.Cubic -> {
                val control1 = transform(seg.control1)
                val control2 = transform(seg.control2)
                val end = transform(seg.end)
                cubicTo(control1.x, control1.y, control2.x, control2.y, end.x, end.y)
            }
        }
    }
    close()
    return this
}

/**
 * This glyph's whole outline (every [Glyph.contours] entry) as a new [Path], each point projected
 * through [transform]. One [Path] per glyph is the right grain for a caller that fills it as one
 * shape or strokes it as one dashed outline (CLAUDE.md law 8's "colour plus a line pattern").
 *
 * [fillType] is [PathFillType.NonZero] unless a caller says otherwise, because that is how a font
 * is rasterised: an outer contour and its counter wind in opposite directions, and a glyph whose
 * contours do not is drawn by a compiled font as the solid shape it would show here. A caller that
 * wants to be forgiving of mis-wound sources asks for [PathFillType.EvenOdd] by name.
 */
fun Glyph.toComposePath(
    transform: (Vec2) -> Offset,
    fillType: PathFillType = PathFillType.NonZero,
): Path {
    val path = Path()
    for (contour in contours) path.addContour(contour, transform)
    path.fillType = fillType
    return path
}

/** This raw-pixel [Offset] as screen dp, dividing out [density]. */
fun Offset.toVec2(density: Density): Vec2 = Vec2((x / density.density).toDouble(), (y / density.density).toDouble())

/** This screen-dp [Vec2] as a raw-pixel [Offset], multiplying by [density]. */
fun Vec2.toOffset(density: Density): Offset = Offset((x * density.density).toFloat(), (y * density.density).toFloat())

/** This screen-dp [Vec2] as a raw whole-pixel [androidx.compose.ui.unit.IntOffset], for `Modifier.offset { }`. */
fun Vec2.toIntOffset(density: Density): androidx.compose.ui.unit.IntOffset =
    androidx.compose.ui.unit
        .IntOffset((x * density.density).roundToInt(), (y * density.density).roundToInt())
