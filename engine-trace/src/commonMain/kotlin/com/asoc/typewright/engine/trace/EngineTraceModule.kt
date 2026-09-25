// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.trace

/**
 * Pure Kotlin raster-to-vector tracing (P3, docs/TYPEWRIGHT_HANDOFF.md section 4 M1 steps 2-3,
 * "Clean" and "Contour"): [GrayscaleRaster]/[BinaryRaster] as the platform-free raster type
 * (CLAUDE.md law 2 -- no `android.graphics.Bitmap`, no `java.awt.image.BufferedImage`, so this
 * compiles on `wasmJs` too); [adaptiveThreshold] (local, integral-image mean, robust to uneven
 * "lighting"); [despeckle]/[fillHoles] (connected-component labeling, [labelComponents], written
 * here rather than borrowed); [traceContours] (sub-pixel marching squares); [distanceTransform]/
 * [estimateStrokeWidth]; and [traceBinaryToContours]/[traceGrayscaleToContours], which wire the
 * whole chain into `core-geometry`'s P2 `fitClosedContourToCubics`, producing fitted CUBIC
 * [com.asoc.typewright.core.geometry.Contour]s.
 *
 * **Scope.** This module's *pure* part stops at a single, already-isolated glyph raster. Camera
 * capture, perspective correction from template-sheet fiducials, and per-cell detection on a
 * multi-glyph capture sheet are all out of scope here -- docs/ARCHITECTURE_REVIEW.md section 3
 * `:engine-trace` risk 3 and docs/OPEN_QUESTIONS.md item 6 already note that no such template
 * sheets or fiducials exist yet in this repository and that they need real design work first; any
 * platform capture code (the camera, OpenCV) belongs in a separate, Android-only module feeding
 * rasters into this one, never here (CLAUDE.md law 2; OPEN_QUESTIONS item 6).
 *
 * [NAME] just names the module (kept for parity with every other module's placeholder object).
 */
object EngineTraceModule {
    const val NAME: String = "engine-trace"
}
