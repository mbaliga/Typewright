// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.geometry

import kotlin.math.PI
import kotlin.math.abs

/**
 * The turning-angle threshold [detectCorners] uses when the caller does not pick one: about 32
 * degrees. This sits well below a letterform's usual sharp turns (a right angle, ~90 degrees, or
 * a serif/terminal cusp, typically 45-140 degrees away from straight-through) and well above the
 * per-vertex turning noise a dense raster trace or a pixel-quantised polygon produces along a
 * nominally straight or gently curved run (a few tenths of a degree per step at the point
 * densities this app's fixtures use -- see `CubicFittingHyleDecoValidationTest`'s KDoc for the
 * measured noise on `fonts/HyleDeco-Regular.ttf`). It is one plain global constant, never adjusted
 * per glyph (P2a's anti-gaming rule): tune it here, once, for every caller.
 */
const val DEFAULT_CORNER_TURN_THRESHOLD_RADIANS: Double = 32.0 * PI / 180.0

/**
 * How many neighbours on each side [detectCorners] looks at when estimating the turning angle at
 * a vertex. `1` (the default) compares the immediate previous and next points, the simplest and
 * most sensitive discrete curvature estimate. A larger window trades sensitivity to a single
 * sharp vertex for robustness against per-point noise in a denser or noisier trace, by measuring
 * the turn between the chord *into* the window and the chord *out of* it rather than between two
 * single-point edges.
 */
const val DEFAULT_CORNER_WINDOW: Int = 1

/**
 * How close (in polyline index distance, cyclic) two raw over-threshold vertices must be for
 * [detectCorners] to *consider* treating them as one corner rather than two -- necessary but not
 * sufficient; see [DEFAULT_CORNER_CLUSTER_MAX_TURN_RADIANS]'s KDoc for the other half of the test.
 * A real pixel-quantised corner (a raster trace's "staircase": the true turn split across 2-3
 * adjacent vertices, none of which alone reaches a full right angle but each still over
 * [DEFAULT_CORNER_TURN_THRESHOLD_RADIANS]) would otherwise be reported as several adjacent corners
 * instead of the one it actually is -- measured on the real `fonts/HyleDeco-Regular.ttf` `T` and
 * `H` (see `CubicFittingHyleDecoValidationTest`'s KDoc): `T`'s stem/bar corner near `(258, 1)` and
 * `H`'s near `(105, 193)` both split this way, one point apart. `3` is generous on distance alone
 * because the turn-sum test below is what actually decides it.
 */
const val DEFAULT_CORNER_MERGE_DISTANCE: Int = 3

/**
 * The most one merged cluster of adjacent candidates (see [DEFAULT_CORNER_MERGE_DISTANCE]) is
 * allowed to turn, in total, before [detectCorners] treats it as more than one real corner and
 * stops merging: about 150 degrees. Index distance alone cannot tell a true corner's own
 * pixel-quantised "staircase" (see [DEFAULT_CORNER_MERGE_DISTANCE]'s KDoc) apart from two or more
 * *distinct* real corners that happen to sit only a point or two apart on a dense trace -- both
 * look identical by index spacing alone. They do not look identical by turning angle: a
 * staircase's few adjacent candidates each capture part of the *same* rotation, so they turn the
 * same rotational way and their turns sum to roughly one ordinary corner's own turn (in
 * `fonts/HyleDeco-Regular.ttf`'s `T`, two ~45-degree candidates summing to the one ~90-degree
 * corner they actually are); genuinely separate corners packed close together -- a narrow stem, as
 * in the same font's `n`, where two right-angle corners sit one point apart with no interior point
 * on the short edge between them -- each contribute close to a *full* corner's turn, so summed
 * together they clear this bound (two ~90-degree corners summing to ~180 degrees) and are kept
 * separate. 150 degrees sits above any one ordinary letterform corner (even a sharp serif cusp)
 * and below what two stacked ordinary corners sum to, so it draws that line without knowing
 * anything about which glyph, or which real corner, produced either candidate.
 */
const val DEFAULT_CORNER_CLUSTER_MAX_TURN_RADIANS: Double = 150.0 * PI / 180.0

/**
 * Detects corners on [polyline], a dense polyline this function always treats as **closed and
 * cyclic** -- the segment from the last point back to the first is part of the shape, exactly
 * like every [Contour] in this module (see `CoreGeometryModule`'s KDoc). This is the pre-pass
 * `docs/TYPEWRIGHT_HANDOFF.md` section 4 M1 step 4 ("Corner detect... Corners become on curve
 * points with no smoothing across them") and `docs/RESEARCH_font_quality.md`'s outline section
 * both call for: find true corners by curvature before any curve fitting happens, so a fitted
 * curve never smooths across a real corner.
 *
 * **Method (discrete curvature by turning angle, one tunable threshold, the same for every
 * caller, plus non-maximum suppression).** At each index `i`, this reads the local turning angle
 * as the signed angle ([angleBetween]) from the chord *into* `i`
 * (`polyline[i - window] -> polyline[i]`) to the chord *out of* it
 * (`polyline[i] -> polyline[i + window]`), both cyclic. A vertex whose turning angle's magnitude
 * is at least [turnThresholdRadians] is a *candidate* corner. [window] widens both chords
 * symmetrically to damp per-point noise (see [DEFAULT_CORNER_WINDOW]'s KDoc); it is clamped to at
 * most `polyline.size / 2` so a very small or very short-looped input still produces well-defined,
 * in-range indices instead of overlapping itself more than once around the loop.
 *
 * Because one true corner can turn up as a short run of adjacent candidates rather than a single
 * vertex (see [DEFAULT_CORNER_MERGE_DISTANCE]'s KDoc), candidates within [mergeDistanceIndices] of
 * each other (cyclic) **and** whose combined turn stays within [clusterMaxTurnRadians] (see that
 * parameter's KDoc for why both conditions are needed, not distance alone) are then grouped and
 * each group collapsed to its single strongest turn -- standard non-maximum suppression, the same
 * idea most discrete corner/curvature detectors use, and, like everything else here, one uniform
 * rule with no per-shape branch.
 *
 * This is one general-purpose pass applied uniformly to any closed polyline: it reasons only
 * about turning angle, never about how many points the input has or what glyph (if any) it came
 * from. [Contour.extrema] and the wider curvature-estimation choice this function documents are
 * deliberately not reused here: [Contour.extrema] finds where an *already-curved* [CurveSegment]
 * is locally extreme in one axis, a different question from "does this dense polyline turn
 * sharply at this vertex", which is this pass's actual job (turning P3's raster trace, or a
 * quadratic TrueType contour's own on-curve points, into the open arcs [fitClosedContourToCubics]
 * fits independently).
 *
 * @return the corner indices into [polyline], ascending, each in `0 until polyline.size`. Never
 *   contains a duplicate. Empty when no vertex turns sharply enough (a smooth closed curve, such
 *   as a dense circle polyline, has no true corner at all); [fitClosedContourToCubics] treats
 *   that case as one single closed arc rather than a first special case of its own -- see its
 *   KDoc.
 */
fun detectCorners(
    polyline: List<Point>,
    turnThresholdRadians: Double = DEFAULT_CORNER_TURN_THRESHOLD_RADIANS,
    window: Int = DEFAULT_CORNER_WINDOW,
    mergeDistanceIndices: Int = DEFAULT_CORNER_MERGE_DISTANCE,
    clusterMaxTurnRadians: Double = DEFAULT_CORNER_CLUSTER_MAX_TURN_RADIANS,
): List<Int> {
    val n = polyline.size
    require(n >= 3) { "a closed polyline needs at least 3 points, had $n" }
    require(window >= 1) { "window must be at least 1, was $window" }
    val w = window.coerceAtMost(n / 2).coerceAtLeast(1)

    val turnMagnitude = DoubleArray(n)
    val turnSigned = DoubleArray(n)
    val candidates = mutableListOf<Int>()
    for (i in 0 until n) {
        val prev = polyline[(i - w).mod(n)]
        val curr = polyline[i]
        val next = polyline[(i + w).mod(n)]
        val incoming = Segment(prev, curr)
        val outgoing = Segment(curr, next)
        val turn = angleBetween(incoming, outgoing)
        turnSigned[i] = turn
        turnMagnitude[i] = abs(turn)
        if (abs(turn) >= turnThresholdRadians) candidates += i
    }
    if (candidates.isEmpty()) return emptyList()
    return suppressNonMaximalCorners(candidates, turnMagnitude, turnSigned, n, mergeDistanceIndices, clusterMaxTurnRadians)
}

/**
 * Groups cyclically-adjacent [candidates] (index distance at most [mergeDistanceIndices] apart,
 * *and* combined signed turn at most [clusterMaxTurnRadians] -- see
 * [DEFAULT_CORNER_CLUSTER_MAX_TURN_RADIANS]'s KDoc for why both) and keeps only, from each group,
 * the one index with the largest [turnMagnitude] -- see [DEFAULT_CORNER_MERGE_DISTANCE]'s KDoc for
 * why one true corner can otherwise surface as several adjacent candidates.
 */
private fun suppressNonMaximalCorners(
    candidates: List<Int>,
    turnMagnitude: DoubleArray,
    turnSigned: DoubleArray,
    n: Int,
    mergeDistanceIndices: Int,
    clusterMaxTurnRadians: Double,
): List<Int> {
    val sorted = candidates.sorted()
    if (sorted.size <= 1) return sorted

    // Rotate the cyclic candidate list to start right after its single largest gap, so the one
    // linear pass below never has to treat the wrap-around point specially: whatever real
    // separation exists anywhere around the loop is guaranteed to land exactly at the rotation
    // point (and if every gap is equally small -- candidates continuing all the way around --
    // nothing here depends on where the rotation happens to start; the turn-sum test still splits
    // any genuinely separate corners apart, exactly as it does anywhere else in the loop). Without
    // this, a group that formed for an unrelated reason right at the end of the plain sorted list
    // could wrongly absorb one at the start just because their combined turn happens to also clear
    // [clusterMaxTurnRadians] -- the two were never actually part of the same candidate run.
    var splitAt = 0
    var largestGap = -1
    for (i in sorted.indices) {
        val nextI = (i + 1) % sorted.size
        val gap = if (nextI == 0) (sorted[nextI] + n) - sorted[i] else sorted[nextI] - sorted[i]
        if (gap > largestGap) {
            largestGap = gap
            splitAt = nextI
        }
    }
    // Pairs of (original index, effective index): effective increases strictly monotonically
    // through the rotation (candidates that wrapped past `n` get `n` added), so plain subtraction
    // between consecutive effective indices is always the right cyclic distance; the original
    // index (always `0 until n`) is what actually gets returned and looked up in [turnSigned]/
    // [turnMagnitude].
    val rotated =
        sorted.subList(splitAt, sorted.size).map { original -> original to original } +
            sorted.subList(0, splitAt).map { original -> original to original + n }

    val groups = mutableListOf<MutableList<Int>>()
    var lastEffective = Int.MIN_VALUE
    var clusterTurnSum = 0.0
    for ((original, effective) in rotated) {
        val turn = turnSigned[original]
        val canExtend =
            groups.isNotEmpty() && effective - lastEffective <= mergeDistanceIndices && abs(clusterTurnSum + turn) <= clusterMaxTurnRadians
        if (canExtend) {
            groups.last() += original
            clusterTurnSum += turn
        } else {
            groups += mutableListOf(original)
            clusterTurnSum = turn
        }
        lastEffective = effective
    }
    return groups.map { group -> group.maxBy { turnMagnitude[it] } }.sorted()
}

/**
 * Splits [polyline] (closed and cyclic, exactly as [detectCorners] reads it) into the open arcs
 * running between consecutive entries of [cornerIndices], each inclusive of both corner
 * endpoints, in the polyline's own cyclic order starting from [cornerIndices]'s first entry.
 *
 * [cornerIndices] must be sorted ascending with no duplicate, the contract [detectCorners]
 * already returns; passing anything else is a caller bug this function does not try to recover
 * from. It may be empty, in which case [polyline] has no detected corner: the one general rule
 * this function follows either way is "one arc per pair of cyclically-consecutive corners, or the
 * whole loop as one arc when there is only one corner (real or, with none detected, the
 * synthesised index `0`)" -- this branch does not special-case a shape or a point count, only the
 * *count of corners the previous, shape-agnostic pass found*, so it stays inside P2a's
 * anti-gaming rule (`corner-detection and fitting... applied uniformly to any input contour`).
 * `0` is not an arbitrary glyph-specific choice: it is simply "the polyline's own first point",
 * the same canonical choice any single-arc caller (a closed loop with one real corner too) would
 * already make by starting its own single arc at that corner's index.
 */
fun arcsBetweenCorners(
    polyline: List<Point>,
    cornerIndices: List<Int>,
): List<List<Point>> {
    val n = polyline.size
    require(n >= 3) { "a closed polyline needs at least 3 points, had $n" }
    val corners = cornerIndices.ifEmpty { listOf(0) }
    if (corners.size == 1) {
        val only = corners[0]
        // The whole loop, starting and ending at the single corner: rotate so it starts there,
        // then close it by repeating that first point at the end.
        val rotated = (0 until n).map { polyline[(only + it) % n] }
        return listOf(rotated + rotated[0])
    }
    return corners.indices.map { k ->
        val start = corners[k]
        val end = corners[(k + 1) % corners.size]
        val length = (end - start).mod(n)
        (0..length).map { polyline[(start + it) % n] }
    }
}
