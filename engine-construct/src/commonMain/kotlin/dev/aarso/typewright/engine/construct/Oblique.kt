// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.engine.construct

import dev.aarso.typewright.core.geometry.Contour
import dev.aarso.typewright.core.geometry.Vec2

// Task P5a-hard item 2: derive an oblique from a monolinear (constant-width) glyph the *correct*
// way -- shear the centreline, then re-stroke it at the same width -- rather than the naive way
// (stroke first, then shear the already-stroked outline directly), which distorts the stroke
// (docs/KNOWLEDGE.md B2: "the 44 unit stroke swung from 40 to 49 around one letter, a 19 percent
// wobble... shearing a finished shape stretches its stroke").
//
// Both functions below are thin, deliberate compositions of item 1's own foundations (shearX,
// strokeClosedContour) -- the point of this file is *which order* those two primitives are
// called in, not any new geometry.

/**
 * **The correct approach.** Shears [centerline] by [shearRadians] first (about [shearOrigin],
 * default the coordinate origin — pass the glyph's own baseline y for a real letterform), then
 * strokes the *sheared* centreline at [strokeParams] — so the stroke offset is computed
 * perpendicular to the sheared path's own local tangent everywhere, which is what keeps the
 * resulting band a constant width (`docs/KNOWLEDGE.md` B2: "shear the centreline, then re-apply
 * the stroke... the weight comes back constant because it is re-created, not stretched").
 */
fun strokeShearedCenterline(
    centerline: Contour,
    shearRadians: Double,
    strokeParams: StrokeParameters,
    shearOrigin: Vec2 = Vec2(0.0, 0.0),
): List<Contour> {
    val sheared = shearX(shearRadians, shearOrigin).apply(centerline)
    return strokeClosedContour(sheared, strokeParams)
}

/**
 * **The naive contrast** this file exists to measure against: strokes [centerline] at its
 * *original*, unsheared position first, then applies the shear directly to the two resulting
 * (outer, inner) stroked outlines. Every point of an already-stroked outline moves by the same
 * affine map regardless of the local direction of travel there, which is exactly the distortion
 * `docs/KNOWLEDGE.md` B2 documents — provided here only so `ObliqueTest.kt` can measure and
 * report the contrast honestly, never as something production code should call.
 */
fun strokeThenShearNaively(
    centerline: Contour,
    shearRadians: Double,
    strokeParams: StrokeParameters,
    shearOrigin: Vec2 = Vec2(0.0, 0.0),
): List<Contour> {
    val stroked = strokeClosedContour(centerline, strokeParams)
    val shear = shearX(shearRadians, shearOrigin)
    return stroked.map { shear.apply(it) }
}
