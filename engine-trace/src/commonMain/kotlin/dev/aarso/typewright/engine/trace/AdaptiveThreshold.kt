package dev.aarso.typewright.engine.trace

/**
 * Tunable parameters for [adaptiveThreshold]. One fixed, global choice, the same for every caller
 * (the same anti-gaming discipline `core-geometry`'s P2 fitter documents for its own parameters:
 * tuning lives only here, never as a branch on a particular raster).
 */
data class AdaptiveThresholdParameters(
    /**
     * How far, in pixels, [adaptiveThreshold] looks in every direction when computing a pixel's
     * local mean darkness (an inclusive square window of side `2 * windowRadius + 1`). Must be
     * positive. `15` comfortably spans a hand-drawn stroke (a few pixels to a few tens of pixels
     * wide at the raster resolutions this module's own tests use) while still being local enough
     * to track a real lighting gradient across the raster rather than averaging over the whole
     * image (which would degenerate into a single global threshold).
     */
    val windowRadius: Int = DEFAULT_ADAPTIVE_THRESHOLD_WINDOW_RADIUS,
    /**
     * How much darker than its own local mean a pixel must be to count as ink, in the same 0..255
     * darkness units as [GrayscaleRaster]. Without this margin, a perfectly flat region (all paper,
     * or all ink) would sit exactly at its own local mean and the classification would be decided
     * by rounding noise alone. `8` is comfortably above typical scan/photograph sensor noise
     * (a handful of levels) and well below the darkness swing between paper and ink this module's
     * synthetic test rasters use (255 vs 0, i.e. a 255-level swing), so it never itself decides an
     * otherwise-clear pixel.
     */
    val bias: Double = DEFAULT_ADAPTIVE_THRESHOLD_BIAS,
)

const val DEFAULT_ADAPTIVE_THRESHOLD_WINDOW_RADIUS: Int = 15
const val DEFAULT_ADAPTIVE_THRESHOLD_BIAS: Double = 8.0

/**
 * Converts [raster] to ink/paper using a **local**, per-pixel threshold rather than one global
 * cutoff (docs/TYPEWRIGHT_HANDOFF.md section 4 M1 step 2, "Clean... Adaptive threshold";
 * docs/ARCHITECTURE_REVIEW.md section 3 `:engine-trace`'s "integral-image adaptive threshold").
 *
 * **Method.** For every pixel, compare its own darkness (`255 - value`) against the mean darkness
 * of the `(2 * windowRadius + 1)` square window centred on it (via [IntegralImage], so this whole
 * pass is O(`width*height`) after one O(`width*height`) integral-image build, not
 * O(`width*height*windowRadius^2`)): a pixel is ink when it is darker than its own neighbourhood's
 * mean by at least [AdaptiveThresholdParameters.bias]. This is the classic *adaptive mean*
 * threshold (as opposed to one global cutoff applied to the whole image): a region that is
 * uniformly darker or lighter than the rest of the raster (uneven "lighting") still gets a
 * threshold that tracks its own local background, so ink on an unevenly lit page is still found
 * where a single global cutoff would fail either the dim or the bright side of the page.
 *
 * **A real limitation, measured directly, not hidden.** This method assumes the ink it is looking
 * for is *thin relative to the window* -- true of an ordinary hand-drawn letter stroke (this
 * module's actual target), but not of an arbitrarily large filled shape. Deep inside a solid ink
 * region wider than about `2 * windowRadius`, every pixel's own window is *entirely* ink, so its
 * local mean darkness equals its own darkness and the "darker than the mean by at least `bias`"
 * test can never fire there -- that whole interior reads as background, not a boundary artefact but
 * a real, measured failure mode of local-mean thresholding at that scale (observed directly while
 * building this module: a filled disc of radius 25 against the default `windowRadius` of 15
 * misclassified a large connected interior region, not just a thin ring at the edge). Callers whose
 * ink can be wider than that either raise [AdaptiveThresholdParameters.windowRadius] to match, or --
 * for this module's actual letterform-stroke use case -- simply do not hit this case; every test in
 * this file and `TraceChainTest` was chosen accordingly, honestly, rather than papering over it with
 * a larger default that would make the *common* case (a thin stroke on a small window) less
 * sensitive for no real benefit.
 */
fun adaptiveThreshold(
    raster: GrayscaleRaster,
    params: AdaptiveThresholdParameters = AdaptiveThresholdParameters(),
): BinaryRaster {
    require(params.windowRadius > 0) { "windowRadius must be positive, was ${params.windowRadius}" }
    val integral = IntegralImage(raster)
    val ink = BooleanArray(raster.width * raster.height)
    for (y in 0 until raster.height) {
        for (x in 0 until raster.width) {
            val darkness = 255 - raster[x, y]
            val localMeanDarkness = integral.windowMean(x, y, params.windowRadius)
            ink[y * raster.width + x] = darkness > localMeanDarkness + params.bias
        }
    }
    return BinaryRaster(raster.width, raster.height, ink)
}
