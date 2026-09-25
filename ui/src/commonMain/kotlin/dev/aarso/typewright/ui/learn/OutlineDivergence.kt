// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.ui.learn

import dev.aarso.typewright.core.geometry.Contour
import dev.aarso.typewright.core.geometry.Glyph
import dev.aarso.typewright.core.geometry.Vec2
import dev.aarso.typewright.core.geometry.pointAt
import dev.aarso.typewright.core.geometry.segments

// The Overlay tab's redline-mode divergence check (`ui/typewright-explorer.html`'s `#ln-ov`,
// TYPEWRIGHT_BUILD_BRIEF.md section 9: "redline mode"). CLAUDE.md law 8 forbids inventing a new
// meaning colour for "these differ", so this file's own output is a *set of points* a caller
// draws as a distinct *shape* (a tick mark), never a colour of its own -- severity here is a
// shape and a short readout number, exactly law 8's own rule ("severity is a shape and a word").
//
// Kept Compose-free on purpose, the same convention `GeometryInterop.kt`'s own top-of-file note
// documents for `ui.sheet`/`.puck`/`.tokens`: every function below works in plain Vec2 space
// (a caller's own "aligned, scaled canvas units", not raw pixels), so it is unit-testable without
// a Compose test harness and reusable by any two outlines in the same normalized space, not just
// an OverlayLayer pair.
//
// `engine-construct`'s `Offset.kt`/`Booleans.kt` already have their own contour-flattening
// helpers (`Contour.flattenToPolyline`, `CurveSegment.Cubic.flatten`), checked before writing
// this file -- both are `internal` to `:engine-construct` and both assume
// CurveFormat.CUBIC input (their own KDoc: "a cubic segment"), which does not fit a Glyph read
// straight off a TrueType `glyf` table (`core-font` decodes those as CurveFormat.QUADRATIC, never
// elevated to cubic). Rather than widen visibility on another module's internal, cubic-only
// helper and then still write a quadratic path around it, this file builds one small, general
// flattener directly on Contour.segments/CurveSegment.pointAt -- both already public
// `core-geometry` primitives, and the exact two every `qa/corpus` style probe (`Geometry2D.kt`'s
// own sampleContourPoints) already builds its own sampling on, format-agnostic by construction.

/**
 * This contour flattened to a dense, closed polyline in font-unit [Vec2] space: [samplesPerSegment]
 * evenly-`t`-spaced points per [Contour.segments] entry (a straight [dev.aarso.typewright.core.geometry.CurveSegment.Line]
 * still contributes its own two endpoints via `t = 0`, so a polygon-only glyph -- Hyle Deco is one,
 * CLAUDE.md's own fixture note -- is not over-sampled into needless duplicate points). The
 * polyline is implicitly closed (its last point connects back to its first); callers walk it with
 * that in mind, matching [distanceToPolyline]'s own convention.
 */
fun Contour.flattenPolyline(samplesPerSegment: Int = 8): List<Vec2> {
    val segs = segments()
    if (segs.isEmpty()) return emptyList()
    val points = mutableListOf<Vec2>()
    for (seg in segs) {
        for (i in 0 until samplesPerSegment) {
            points += seg.pointAt(i.toDouble() / samplesPerSegment)
        }
    }
    return points
}

/** Every contour of this glyph, each flattened via [flattenPolyline]. One polyline per contour, not merged -- a divergence check needs to know which ring a point sits on for [distanceToPolyline] to close it correctly. */
fun Glyph.flattenContours(samplesPerSegment: Int = 8): List<List<Vec2>> =
    contours.mapNotNull { it.flattenPolyline(samplesPerSegment).takeIf { pts -> pts.isNotEmpty() } }

/** The squared distance from [point] to the closed segment [a]-[b] (never negative infinity; used only to compare, so the square root is deferred to the one caller that needs a real distance). */
private fun pointToSegmentDistanceSquared(
    point: Vec2,
    a: Vec2,
    b: Vec2,
): Double {
    val ab = b - a
    val abLenSq = ab.dot(ab)
    if (abLenSq <= 0.0) {
        val d = point - a
        return d.dot(d)
    }
    val t = ((point - a).dot(ab) / abLenSq).coerceIn(0.0, 1.0)
    val closest = a + ab * t
    val d = point - closest
    return d.dot(d)
}

/**
 * The Euclidean distance from [point] to the nearest point on the closed polygon [polygon]
 * (consecutive vertices are edges; the last vertex connects back to the first, per
 * [flattenPolyline]'s own KDoc). `Double.POSITIVE_INFINITY` for a polygon with fewer than 2
 * points (nothing to measure against).
 */
fun distanceToPolyline(
    point: Vec2,
    polygon: List<Vec2>,
): Double {
    if (polygon.size < 2) return Double.POSITIVE_INFINITY
    var best = Double.POSITIVE_INFINITY
    for (i in polygon.indices) {
        val a = polygon[i]
        val b = polygon[(i + 1) % polygon.size]
        val d = pointToSegmentDistanceSquared(point, a, b)
        if (d < best) best = d
    }
    return kotlin.math.sqrt(best)
}

/** [point]'s distance to the nearest of [contours]' own polylines (each one closed, per [flattenPolyline]). `Double.POSITIVE_INFINITY` if [contours] is empty. */
fun distanceToNearestOutline(
    point: Vec2,
    contours: List<List<Vec2>>,
): Double = contours.minOfOrNull { distanceToPolyline(point, it) } ?: Double.POSITIVE_INFINITY

/**
 * Every point on [a]'s own flattened outline (mapped through [aToCanvas], the same
 * font-units-to-aligned-canvas-units transform the caller already draws [a] with) that sits
 * farther than [toleranceCanvasUnits] from [b]'s own flattened outline (mapped through
 * [bToCanvas]) -- a point-to-nearest-segment check, general over any two glyphs and any two
 * transforms, not special-cased to any one font pair. Both glyphs are sampled and compared in the
 * *same* target space (post-transform), never raw font units directly, because two real fonts
 * can (and here, do not have to, but generally might) have different `unitsPerEm` or different
 * cap-heights -- comparing their raw coordinates directly would be meaningless; comparing them
 * once both are aligned to the same on-screen cap-height/x-height (`OverlayTab.kt`'s own job) is
 * exactly what "aligned... the invisible differences become obvious" (`ui/typewright-explorer.html`'s
 * own Overlay caption) means in numbers, not just in eye.
 */
fun findDivergentPoints(
    a: Glyph,
    aToCanvas: (Vec2) -> Vec2,
    b: Glyph,
    bToCanvas: (Vec2) -> Vec2,
    toleranceCanvasUnits: Double,
    samplesPerSegment: Int = 8,
): List<Vec2> {
    val bContoursCanvas = b.flattenContours(samplesPerSegment).map { poly -> poly.map(bToCanvas) }
    if (bContoursCanvas.isEmpty()) return emptyList()
    val result = mutableListOf<Vec2>()
    for (poly in a.flattenContours(samplesPerSegment)) {
        for (fontPoint in poly) {
            val canvasPoint = aToCanvas(fontPoint)
            if (distanceToNearestOutline(canvasPoint, bContoursCanvas) > toleranceCanvasUnits) {
                result += canvasPoint
            }
        }
    }
    return result
}
