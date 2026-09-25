// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.trace

/** [despeckle]'s default: a foreground component smaller than 6 pixels is noise, not a drawn mark, at the raster resolutions this module's own tests use. */
const val DEFAULT_DESPECKLE_MIN_AREA: Int = 6

/**
 * Removes small foreground (ink) components from [raster] (docs/TYPEWRIGHT_HANDOFF.md section 4
 * M1 step 2, "Clean... Despeckle"): any connected ink component ([labelComponents], 8-connected --
 * see [Connectivity]'s KDoc) whose pixel count is below [minArea] is turned to background. A real
 * stroke's own component is, by construction, unaffected as long as it is at least [minArea]
 * pixels -- this never special-cases which component is "the real letter"; it only removes what
 * is small everywhere, uniformly.
 */
fun despeckle(
    raster: BinaryRaster,
    minArea: Int = DEFAULT_DESPECKLE_MIN_AREA,
): BinaryRaster {
    require(minArea >= 0) { "minArea must not be negative, was $minArea" }
    val labeling = labelComponents(raster.width, raster.height, Connectivity.EIGHT) { x, y -> raster[x, y] }
    val ink =
        BooleanArray(raster.width * raster.height) { index ->
            val label = labeling.labels[index]
            label >= 0 && labeling.componentSizes[label] >= minArea
        }
    return BinaryRaster(raster.width, raster.height, ink)
}
