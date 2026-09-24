package dev.aarso.typewright.engine.trace

/** [fillHoles]'s default: an enclosed background pocket smaller than 6 pixels is filled -- symmetric with [DEFAULT_DESPECKLE_MIN_AREA]. */
const val DEFAULT_HOLE_FILL_MIN_AREA: Int = 6

/**
 * Fills small background pockets fully enclosed inside [raster]'s foreground shape
 * (docs/TYPEWRIGHT_HANDOFF.md section 4 M1 step 2, "Clean... Hole fill"): any connected background
 * component ([labelComponents], 4-connected -- see [Connectivity]'s KDoc for why the complementary
 * colour uses the coarser connectivity) that does **not** touch the raster's outer border and whose
 * pixel count is below [maxArea] is turned to foreground.
 *
 * **Why "does not touch the border" matters.** The unbounded background surrounding every drawn
 * shape is itself one connected component reachable from any border pixel; it is never a "hole",
 * however small a raster happens to be, so it is always left alone regardless of [maxArea]. Only a
 * background component fully surrounded by ink -- one a flood fill from the border can never reach
 * -- counts as an enclosed pocket a real hand-drawn glyph (the counter of an `o`, a mistaken pen
 * skip) can produce.
 */
fun fillHoles(
    raster: BinaryRaster,
    maxArea: Int = DEFAULT_HOLE_FILL_MIN_AREA,
): BinaryRaster {
    require(maxArea >= 0) { "maxArea must not be negative, was $maxArea" }
    val labeling = labelComponents(raster.width, raster.height, Connectivity.FOUR) { x, y -> !raster[x, y] }
    val ink =
        BooleanArray(raster.width * raster.height) { index ->
            if (raster.ink[index]) return@BooleanArray true
            val label = labeling.labels[index]
            // label is always >= 0 here: every background pixel belongs to some background
            // component, by construction of labelComponents' predicate (!raster[x, y]).
            val enclosed = !labeling.componentTouchesBorder[label]
            val small = labeling.componentSizes[label] <= maxArea
            enclosed && small
        }
    return BinaryRaster(raster.width, raster.height, ink)
}
