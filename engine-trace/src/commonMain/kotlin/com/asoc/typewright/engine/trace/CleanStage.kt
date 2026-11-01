// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.trace

import kotlin.math.max

/** How [cleanCell] decides which darkness is ink. */
enum class ThresholdMode {
    /** Otsu's level, found from the cell itself: right for a cell that holds paper and one kind of pen. */
    OTSU,

    /** The level in [CleanParameters.fixedDarkness], for a cell where the automatic one is wrong. */
    FIXED,
}

/** [CleanParameters.despeckleMinArea]'s default, in pixels: a mark smaller than this is noise, not pen. */
const val DEFAULT_CLEAN_DESPECKLE_AREA: Int = 10

/** [CleanParameters.holeFillMaxArea]'s default, in pixels: a pinhole in the ink smaller than this is filled. */
const val DEFAULT_CLEAN_HOLE_AREA: Int = 10

/** The settings of the Clean stage. */
data class CleanParameters(
    val threshold: ThresholdMode = ThresholdMode.OTSU,
    /** The darkness (0 paper, 1 black) taken as the edge of the ink when [threshold] is [ThresholdMode.FIXED]. */
    val fixedDarkness: Double = 0.5,
    /** The lowest darkness the automatic level may pick: a cell with printed guides on it must not take the guides' pale grey for ink. */
    val minimumLevel: Double = 0.2,
    /** Ink marks smaller than this many pixels are removed. */
    val despeckleMinArea: Int = DEFAULT_CLEAN_DESPECKLE_AREA,
    /** Enclosed pinholes in the ink smaller than this many pixels are filled. */
    val holeFillMaxArea: Int = DEFAULT_CLEAN_HOLE_AREA,
) {
    init {
        require(fixedDarkness in 0.05..0.95) { "fixedDarkness must be between 0.05 and 0.95, was $fixedDarkness" }
        require(minimumLevel in 0.05..0.8) { "minimumLevel must be between 0.05 and 0.8, was $minimumLevel" }
        require(despeckleMinArea >= 0 && holeFillMaxArea >= 0) { "areas must not be negative" }
    }
}

/**
 * What the Clean stage made of a cell: the [darkness] of every pixel measured against the paper around it, the
 * [level] taken as the edge of the ink, the [mask] of ink after despeckling and hole filling, and the [field] the
 * contour is traced on. The field is the darkness scaled so that exactly 0.5 is the edge, forced to agree with the
 * cleaned mask wherever cleaning changed a pixel; tracing it with marching squares puts the outline where the
 * drawn edge really is to a fraction of a pixel instead of snapping it to the pixel grid.
 */
class CleanedCell(
    val width: Int,
    val height: Int,
    val darkness: DoubleArray,
    val level: Double,
    val rawMask: BinaryRaster,
    val mask: BinaryRaster,
    val field: DoubleArray,
    /** False when the cell holds no ink at all (nothing in it is darker than faint paper texture). */
    val inkFound: Boolean,
    /** The stroke's width in pixels (see [estimateStrokeWidthRidge]); 0 without ink. */
    val strokeWidthPx: Double,
    val specksRemoved: Int,
    val pinholesFilled: Int,
)

/**
 * The Clean stage (handoff M1 step 2): measures each pixel against the paper around it so uneven light cancels,
 * takes the edge of the ink at Otsu's level (or [CleanParameters.fixedDarkness]), removes specks and fills
 * pinholes, and estimates the stroke width. A cell with nothing darker than 0.3 on the paper scale has no ink.
 */
fun cleanCell(
    raster: GrayscaleRaster,
    params: CleanParameters = CleanParameters(),
): CleanedCell {
    val w = raster.width
    val h = raster.height
    val darkness = normalisedDarkness(raster)
    val strongest = darkness.maxOrNull() ?: 0.0
    val level =
        when (params.threshold) {
            ThresholdMode.OTSU -> otsuThreshold(darkness).coerceIn(params.minimumLevel, 0.8)
            ThresholdMode.FIXED -> params.fixedDarkness
        }
    val inkFound = strongest >= max(0.3, level)
    val rawMask = BinaryRaster(w, h, BooleanArray(w * h) { inkFound && darkness[it] >= level })
    val despeckled = despeckle(rawMask, params.despeckleMinArea)
    val filled = fillHoles(despeckled, params.holeFillMaxArea)
    var specks = 0
    var pinholes = 0
    val field = DoubleArray(w * h)
    for (i in field.indices) {
        val scaled = (darkness[i] / (2.0 * level)).coerceIn(0.0, 1.5)
        field[i] =
            when {
                filled.ink[i] && !rawMask.ink[i] -> 1.0
                !filled.ink[i] && rawMask.ink[i] -> 0.0
                !inkFound -> 0.0
                else -> scaled
            }
        if (rawMask.ink[i] && !despeckled.ink[i]) specks++
        if (filled.ink[i] && !despeckled.ink[i]) pinholes++
    }
    return CleanedCell(
        width = w,
        height = h,
        darkness = darkness,
        level = level,
        rawMask = rawMask,
        mask = filled,
        field = field,
        inkFound = inkFound && filled.ink.any { it },
        strokeWidthPx = if (inkFound) estimateStrokeWidthRidge(filled) else 0.0,
        specksRemoved = specks,
        pinholesFilled = pinholes,
    )
}
