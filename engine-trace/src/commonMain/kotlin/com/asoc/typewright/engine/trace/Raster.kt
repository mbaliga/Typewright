// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.trace

/**
 * A fixed-size grid of 8-bit grayscale pixel values, row-major, top-left origin: [pixels]`[y *
 * width + x]` is the pixel at column [x], row [y]. This is the raster type the whole trace chain
 * (docs/TYPEWRIGHT_HANDOFF.md section 4 M1 steps 2-3, "Clean" and "Contour") operates on, in
 * place of a platform bitmap type -- no `android.graphics.Bitmap`, no `java.awt.image.BufferedImage`
 * -- so this module compiles on `wasmJs` as well as `jvm` (CLAUDE.md law 2).
 *
 * **Value convention.** `0` is black ink, `255` is white paper -- the usual scanned-page sense
 * ("low value = dark"), not an alpha or coverage channel. A pixel's *darkness* is therefore `255 -
 * value`; [AdaptiveThresholdParameters] and [adaptiveThreshold] read values this way.
 *
 * **Coordinate convention.** `x` runs right, `y` runs *down* (row index increasing), matching how
 * a raster is actually stored and how every raster-processing algorithm in this file's siblings
 * reasons about it. This is deliberately different from `core-geometry`'s font-unit convention (y
 * up, CLAUDE.md conventions) -- a raster is not yet a glyph. Placing a traced contour into font
 * space (translating, scaling onto the em square, flipping y) is a later, product-level decision
 * this task does not make; see [TraceChainResult]'s KDoc. Pixel `(x, y)` is a point sample *at*
 * that integer grid coordinate, not the centre of a unit cell `x + 0.5` away from it -- the same
 * convention [traceContours] uses for its own grid, so a contour it extracts and the raster it was
 * extracted from always agree on where a given coordinate sits.
 */
class GrayscaleRaster(
    val width: Int,
    val height: Int,
    val pixels: IntArray,
) {
    init {
        require(width > 0 && height > 0) { "a raster must be at least 1x1, was ${width}x$height" }
        require(pixels.size == width * height) {
            "pixels.size must be width*height (${width * height}), was ${pixels.size}"
        }
        require(pixels.all { it in 0..255 }) { "every grayscale pixel must be in 0..255" }
    }

    /** The grayscale value (0..255) at column [x], row [y]. */
    operator fun get(
        x: Int,
        y: Int,
    ): Int {
        require(x in 0 until width && y in 0 until height) { "($x, $y) is outside the ${width}x$height raster" }
        return pixels[y * width + x]
    }

    companion object {
        /** A raster filled entirely with [value] (paper, 255, by default). */
        fun filled(
            width: Int,
            height: Int,
            value: Int = 255,
        ): GrayscaleRaster = GrayscaleRaster(width, height, IntArray(width * height) { value })
    }
}

/**
 * A fixed-size binary grid: `true` at [ink]`[y * width + x]` means foreground (ink), `false` means
 * background (paper). This is what [adaptiveThreshold] produces from a [GrayscaleRaster], and what
 * [despeckle], [fillHoles] and the marching-squares contour extraction ([traceContours]) all
 * operate on. Same row-major, top-left-origin, y-down convention as [GrayscaleRaster] -- see that
 * class's KDoc.
 */
class BinaryRaster(
    val width: Int,
    val height: Int,
    val ink: BooleanArray,
) {
    init {
        require(width > 0 && height > 0) { "a raster must be at least 1x1, was ${width}x$height" }
        require(ink.size == width * height) { "ink.size must be width*height (${width * height}), was ${ink.size}" }
    }

    /** Whether column [x], row [y] is foreground (ink). Out-of-bounds reads as background, so callers never need an edge check. */
    operator fun get(
        x: Int,
        y: Int,
    ): Boolean {
        if (x !in 0 until width || y !in 0 until height) return false
        return ink[y * width + x]
    }

    companion object {
        /** A raster filled entirely with [value] (background, `false`, by default). */
        fun filled(
            width: Int,
            height: Int,
            value: Boolean = false,
        ): BinaryRaster = BinaryRaster(width, height, BooleanArray(width * height) { value })
    }
}
