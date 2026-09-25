// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.core.geometry

import kotlin.math.abs

private const val SIMPLIFY_EPSILON = 1e-9
private const val SIMPLIFY_SAMPLES_PER_SEGMENT = 12

/**
 * [simplifyContour]'s default tolerance, in font units: how far a removed on-curve point's own
 * two original segments may have strayed from the single straight replacement segment and still
 * count as "no visible change". Matches [DEFAULT_STRAIGHTNESS_TOLERANCE_UNITS] — the same order of
 * magnitude this module already treats as "no meaningful curvature" for a single segment
 * ([CurveSegment.Cubic.isEffectivelyStraight]) — since removing a point is exactly the same kind
 * of judgement call one level up: not "is this one segment straight" but "does this whole two-segment
 * run, replaced by one straight segment, still look the same". One fixed, global value, never
 * tuned per glyph (this module's own anti-gaming convention — see `CubicFitParameters`'s KDoc).
 */
const val DEFAULT_SIMPLIFY_TOLERANCE_UNITS: Double = 1.5

/**
 * [simplifyContour]'s result: the simplified [contour] plus its point count before and after —
 * `TYPEWRIGHT_BUILD_BRIEF.md` line 372-373's "tidy/simplify with a live count" — measured with
 * this module's own on-curve-equivalent/off-curve counting ([Contour.count]) rather than raw
 * point-array length, so it reads exactly the way every other count this app reports does
 * (CLAUDE.md's own restated fixtures: "on-curve · off-curve · total").
 */
data class SimplifyResult(
    val contour: Contour,
    val before: ContourCount,
    val after: ContourCount,
)

/**
 * "Tidy/simplify with a live count" (`TYPEWRIGHT_BUILD_BRIEF.md` line 372-373): removes on-curve
 * points that do not change [contour]'s visible shape by more than [toleranceUnits] — both a
 * collinear point in the middle of an otherwise-straight run, and a point sitting so close to its
 * neighbour that the segment between them is visually redundant (a "near-duplicate" point) — using
 * one single, general test for both, rather than two special cases:
 *
 * For each on-curve anchor `B`, with its neighbours `A` (via the segment ending at `B`) and `C`
 * (via the segment starting at `B`): replace both segments with a single straight segment from `A`
 * to `C` ([straightLineCubic] — this module's own "a straight run, represented the only way
 * [CurveFormat.CUBIC] has: a degenerate, on-line cubic" convention, [buildCubicContour]'s KDoc)
 * and measure the largest perpendicular distance from that straight replacement to any point of
 * the two *original* segments (densely sampled, [SIMPLIFY_SAMPLES_PER_SEGMENT] points each). `B`
 * is removed — the two segments become the one replacement — exactly when that largest distance is
 * within [toleranceUnits]. A collinear point on a long straight run and a near-duplicate point are
 * both just instances of this one test: a collinear point's own two segments already sit on the
 * `A`-`C` line by definition, and a near-duplicate point's replacement segment is itself very
 * short, so anything reasonably close to `B` clears the tolerance trivially either way — no special
 * casing needed for either brief-named case.
 *
 * **Greedy and iterative, not a single pass.** One removal changes its neighbours' own segments, so
 * after every successful removal this restarts its scan from the first remaining anchor rather than
 * continuing where it left off — the only way to correctly catch a long collinear run (which needs
 * several sequential single-point removals, each validated against the *current*, already-simplified
 * shape) or a chain of several near-duplicate points in a row. Each successful removal strictly
 * shrinks the segment count by one, so this always terminates, in at most `segmentCount` rounds —
 * more than fast enough for a glyph's actual point counts (tens to low hundreds), even though the
 * scan itself is a plain O(n) pass repeated per removal rather than a smarter single-pass algorithm.
 *
 * [contour] must already be [CurveFormat.CUBIC] — this module's one format with an on-curve point
 * that could ever be redundant in the first place ([CurveFormat.QUADRATIC]'s implied on-curve
 * points are never explicit anchors to begin with).
 */
fun simplifyContour(
    contour: Contour,
    toleranceUnits: Double = DEFAULT_SIMPLIFY_TOLERANCE_UNITS,
): SimplifyResult {
    require(contour.format == CurveFormat.CUBIC) {
        "simplifyContour operates on a CUBIC contour, was ${contour.format}"
    }
    require(toleranceUnits >= 0.0) { "toleranceUnits must not be negative, was $toleranceUnits" }
    val before = contour.count()

    var segments = contour.cubicSegments()
    var mergedSomething = true
    while (mergedSomething && segments.size > 1) {
        mergedSomething = false
        for (i in segments.indices) {
            val prevIndex = (i - 1 + segments.size) % segments.size
            val prev = segments[prevIndex]
            val curr = segments[i]
            val a = prev.start
            val c = curr.end
            val deviation = maxOf(maxDeviationFromLine(prev, a, c), maxDeviationFromLine(curr, a, c))
            if (deviation > toleranceUnits) continue

            val rebuilt = segments.toMutableList()
            rebuilt[prevIndex] = straightLineCubic(a, c)
            rebuilt.removeAt(i)
            segments = rebuilt
            mergedSomething = true
            break
        }
    }

    val simplified = buildCubicContour(segments)
    return SimplifyResult(simplified, before, simplified.count())
}

/** The perpendicular distance from [point] to the infinite line through [lineA] and [lineC], or [point]'s plain distance to [lineA] when they coincide (nothing to measure a perpendicular against). */
private fun pointToLineDistance(
    point: Vec2,
    lineA: Vec2,
    lineC: Vec2,
): Double {
    val direction = lineC - lineA
    val length = direction.length()
    if (length <= SIMPLIFY_EPSILON) return (point - lineA).length()
    return abs(direction.cross(point - lineA)) / length
}

/** The largest [pointToLineDistance] from the [lineA]-[lineC] line over [samples] + 1 evenly spaced points of [segment] (`t = 0, 1/samples, ..., 1`), including both its endpoints. */
private fun maxDeviationFromLine(
    segment: CurveSegment.Cubic,
    lineA: Vec2,
    lineC: Vec2,
    samples: Int = SIMPLIFY_SAMPLES_PER_SEGMENT,
): Double {
    var maxDistance = 0.0
    for (step in 0..samples) {
        val t = step.toDouble() / samples
        val distance = pointToLineDistance(segment.pointAt(t), lineA, lineC)
        if (distance > maxDistance) maxDistance = distance
    }
    return maxDistance
}
