package dev.aarso.typewright.engine.trace

import dev.aarso.typewright.core.geometry.Contour
import dev.aarso.typewright.core.geometry.CubicFitParameters
import dev.aarso.typewright.core.geometry.Point
import dev.aarso.typewright.core.geometry.Vec2
import dev.aarso.typewright.core.geometry.fitClosedContourToCubics
import kotlin.math.roundToInt

/**
 * How many integer units [traceBinaryToContours]/[traceGrayscaleToContours] use per raster pixel
 * when building the [Point] polyline `core-geometry`'s `fitClosedContourToCubics` fits
 * (`core-geometry`'s [Point] is integer, "at rest" -- CLAUDE.md conventions -- but marching squares'
 * whole reason to exist is the *sub-pixel* crossing positions [traceContours] computes). Scaling
 * every coordinate by this factor before rounding to the nearest integer keeps `1 / scale` of a
 * pixel's worth of that sub-pixel precision instead of discarding it at the first pixel-grid
 * rounding. `10` keeps a tenth of a pixel, comfortably finer than [DEFAULT_MARCHING_SQUARES_ISOVALUE]
 * linear interpolation's own accuracy at the raster resolutions this module's tests use (see
 * [MarchingSquaresSubpixelAccuracyTest]).
 *
 * The resulting contour's coordinates are in this scaled *raster pixel* space, not font units --
 * placing a traced glyph onto the em square (choosing a scale and origin from the drawn shape's
 * own metrics) is a later, product-level step this task does not attempt; see [TraceChainResult]'s
 * KDoc.
 */
const val DEFAULT_SUBPIXEL_SCALE: Int = 10

/** How much fit error, in *real* (unscaled) pixels, [traceBinaryToContours]/[traceGrayscaleToContours] allow by default: about one pixel. */
const val DEFAULT_TRACE_FIT_ERROR_TOLERANCE_PIXELS: Double = 1.0

/** The [CubicFitParameters] a scale of [subpixelScale] implies for [DEFAULT_TRACE_FIT_ERROR_TOLERANCE_PIXELS] of real error tolerance. */
fun defaultTraceFitParameters(subpixelScale: Int): CubicFitParameters =
    CubicFitParameters(errorTolerance = DEFAULT_TRACE_FIT_ERROR_TOLERANCE_PIXELS * subpixelScale)

/**
 * Everything one call through the trace chain produced, at every stage, so a caller (or a test)
 * can inspect what cleanup actually changed rather than only the final answer --
 * docs/TYPEWRIGHT_HANDOFF.md section 4 M1 step 2's "the mask is shown, not hidden" in spirit, even
 * though the actual showing is `ui`'s job, not this pure module's.
 */
data class TraceChainResult(
    /** [raster] (or its thresholded binary form) after [despeckle]. */
    val despeckled: BinaryRaster,
    /** [despeckled] after [fillHoles]: what [traceContours] actually ran on. */
    val holeFilled: BinaryRaster,
    /** One dense, sub-pixel [Vec2] polyline per contour loop [traceContours] found, in raw (unscaled) raster-pixel coordinates. */
    val densePolylines: List<List<Vec2>>,
    /** [densePolylines], scaled by [DEFAULT_SUBPIXEL_SCALE] (or the caller's own `subpixelScale`) and rounded to integer [Point]s: what was actually fitted. */
    val contours: List<List<Point>>,
    /** [contours], each fitted to a [dev.aarso.typewright.core.geometry.CurveFormat.CUBIC] [Contour] by `core-geometry`'s `fitClosedContourToCubics`. */
    val fittedContours: List<Contour>,
)

/**
 * The pure raster-to-vector chain's core (P3, docs/TYPEWRIGHT_HANDOFF.md section 4 M1 steps 2-3),
 * from an already-binary mask through to fitted cubic contours: [despeckle] -> [fillHoles] ->
 * [traceContours] (marching squares, sub-pixel) -> `core-geometry`'s `fitClosedContourToCubics`
 * (P2), one call per contour loop found.
 *
 * Passing `despeckleMinArea = 0` and `holeFillMaxArea = 0` is a genuine, general way to run the
 * *same* function with cleanup effectively skipped (every component clears a threshold of `0`),
 * rather than a separate code path -- exactly what this task's own ablation test
 * ([TraceChainAblationTest]) uses to measure what despeckle and hole-fill actually bought.
 */
fun traceBinaryToContours(
    raster: BinaryRaster,
    despeckleMinArea: Int = DEFAULT_DESPECKLE_MIN_AREA,
    holeFillMaxArea: Int = DEFAULT_HOLE_FILL_MIN_AREA,
    subpixelScale: Int = DEFAULT_SUBPIXEL_SCALE,
    fitParams: CubicFitParameters = defaultTraceFitParameters(subpixelScale),
): TraceChainResult {
    require(subpixelScale >= 1) { "subpixelScale must be at least 1, was $subpixelScale" }
    val despeckled = despeckle(raster, despeckleMinArea)
    val holeFilled = fillHoles(despeckled, holeFillMaxArea)
    val densePolylines = traceContours(holeFilled.width, holeFilled.height, holeFilled.toScalarField())
    val scaledPolylines = densePolylines.map { polyline -> polyline.map { it.toScaledPoint(subpixelScale) } }
    val fitted = scaledPolylines.map { fitClosedContourToCubics(it, fitParams) }
    return TraceChainResult(despeckled, holeFilled, densePolylines, scaledPolylines, fitted)
}

/**
 * [traceBinaryToContours], starting one stage earlier from a [GrayscaleRaster] via
 * [adaptiveThreshold] (docs/TYPEWRIGHT_HANDOFF.md section 4 M1 step 2's full "Clean" stage: adaptive
 * threshold, despeckle, hole fill, then step 3's "Contour").
 */
fun traceGrayscaleToContours(
    raster: GrayscaleRaster,
    thresholdParams: AdaptiveThresholdParameters = AdaptiveThresholdParameters(),
    despeckleMinArea: Int = DEFAULT_DESPECKLE_MIN_AREA,
    holeFillMaxArea: Int = DEFAULT_HOLE_FILL_MIN_AREA,
    subpixelScale: Int = DEFAULT_SUBPIXEL_SCALE,
    fitParams: CubicFitParameters = defaultTraceFitParameters(subpixelScale),
): TraceChainResult {
    val binary = adaptiveThreshold(raster, thresholdParams)
    return traceBinaryToContours(binary, despeckleMinArea, holeFillMaxArea, subpixelScale, fitParams)
}

private fun Vec2.toScaledPoint(scale: Int): Point = Point((x * scale).roundToInt(), (y * scale).roundToInt())
