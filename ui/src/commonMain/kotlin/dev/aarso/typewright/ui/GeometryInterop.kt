package dev.aarso.typewright.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Density
import dev.aarso.typewright.core.geometry.Contour
import dev.aarso.typewright.core.geometry.CurveSegment
import dev.aarso.typewright.core.geometry.Glyph
import dev.aarso.typewright.core.geometry.Vec2
import dev.aarso.typewright.core.geometry.segments
import kotlin.math.roundToInt

// The one place raw Compose pixels convert to and from `core-geometry`'s Vec2 as screen dp. Every
// P4a file (`ui.sheet` / `.puck` / `.tokens`) is deliberately Compose-free and works only in
// screen dp (see `PuckGestureEvents.kt`'s own KDoc: "already density-converted by the caller");
// this file is that caller's conversion, and the only place in `ui` that mixes raw pixels with
// Vec2.
//
// [Contour.addToComposePath]/[Glyph.toComposePath] below are the outline-to-Compose-path bridge
// `ui.learn.LearnFaceFonts.kt`'s own KDoc already names and describes ("that bridge draws this
// app's own traced/constructed geometry -- the Draw sheet's ink, and the Overlay tab's colour +
// line-pattern comparison layers -- where the caller needs the raw contour"), built here for real
// by task P6 (Overlay tab) rather than assumed to already exist: `RoomInk.kt`'s `WorldInkPass`
// (the Draw sheet's own ink pass, checked before writing this) draws a placeholder rounded-rect
// stand-in, not real letterforms yet ("Real glyph outlines are P5b's job" -- P5b never actually
// wired one either), so this is the *first* real glyph-outline renderer in `ui`, not a reuse of
// an existing one; it lives in this file because this file's own charter is exactly "the one
// place raw Compose pixels convert to and from core-geometry", and a second such bridge
// elsewhere would be exactly the duplication CLAUDE.md's "reuse, do not reinvent" warns against.
// General over [dev.aarso.typewright.core.geometry.CurveFormat.QUADRATIC] (`core-font`'s sfnt
// reader) and [dev.aarso.typewright.core.geometry.CurveFormat.CUBIC] (a UFO source) alike, since
// both go through [Contour.segments] -- the same primitive every `qa/corpus` style probe already
// builds on -- rather than assuming one format.

/**
 * Appends [contour]'s outline to this [Path], projecting every anchor and control point through
 * [transform] (a caller-supplied font-units-to-Compose-space map, e.g. "scale to this alignment
 * height, flip y, offset by this word cursor position"). A [CurveSegment.Line] becomes
 * [Path.lineTo]; Compose's own [Path.quadraticTo]/[Path.cubicTo] take a quadratic or cubic
 * segment directly, so [dev.aarso.typewright.core.geometry.CurveFormat.QUADRATIC] contours (every
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
 * shape (even-odd winding handles inner counters correctly, the same way a rasterizer already
 * does) or strokes it as one dashed outline (CLAUDE.md law 8's "colour plus a line pattern").
 */
fun Glyph.toComposePath(transform: (Vec2) -> Offset): Path {
    val path = Path()
    for (contour in contours) path.addContour(contour, transform)
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
