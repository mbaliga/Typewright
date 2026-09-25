// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.geometry

/**
 * On-curve-equivalent and off-curve counts for one [Contour]. See the module overview for what
 * "on-curve equivalent" means; see [onCurveEquivalentCount] for the counting rule itself.
 */
data class ContourCount(
    val onCurveEquivalent: Int,
    val offCurve: Int,
)

/** On-curve-equivalent and off-curve counts for a whole [Glyph], summed across its contours. */
data class GlyphCount(
    val onCurveEquivalent: Int,
    val offCurve: Int,
    val contourCount: Int,
)

/**
 * This contour's on-curve-equivalent and off-curve counts in one call; see [onCurveEquivalentCount]
 * and [offCurveCount] for the rule each half follows.
 */
fun Contour.count(): ContourCount =
    when (format) {
        CurveFormat.QUADRATIC -> countQuadratic(points)
        CurveFormat.CUBIC -> countCubic(points)
    }

/**
 * This glyph's on-curve-equivalent and off-curve counts, summed across its contours, plus its
 * contour count. A glyph with no contours (see [Contour]'s KDoc on why an empty outline is
 * represented as an empty contour list, not a zero-point [Contour]) counts as all zero here;
 * whether such a glyph is reported as a zero or skipped outright is a policy decision for the
 * caller building a distribution over many glyphs (`data/scripts/build_node_economy_corpus.py`
 * skips it — see that script's `count_points`), not something this function decides.
 */
fun Glyph.count(): GlyphCount {
    var on = 0
    var off = 0
    for (contour in contours) {
        val c = contour.count()
        on += c.onCurveEquivalent
        off += c.offCurve
    }
    return GlyphCount(onCurveEquivalent = on, offCurve = off, contourCount = contours.size)
}

/**
 * The on-curve-equivalent point count: explicit on-curve points, plus one implied on-curve point
 * for every pair of *cyclically*-consecutive off-curve points (the wrap-around pair — last point,
 * first point — counts too).
 *
 * This is the OpenType `glyf` spec's rule for reconstructing the curve a rasterizer actually
 * draws through: a run of off-curve points is read as sharing an implied on-curve point at the
 * midpoint of every successive pair, so a run of *k* consecutive off-curve points carries *k − 1*
 * implied points. It is a no-op for [CurveFormat.CUBIC], whose off-curve points always come in
 * segment-bound pairs that never imply anything (see [offCurveCount]).
 *
 * This function is `core-geometry`'s side of the counting rule fixed in
 * `data/scripts/build_node_economy_corpus.py`'s `_count_simple_contours` (P0c, 2026-09-24;
 * docs/ARCHITECTURE_REVIEW.md section 5 items 13-15), and must keep matching it exactly so the
 * app's own measurements are comparable to the corpus it judges glyphs against (CLAUDE.md law 5).
 * That function's KDoc hand-traces Poppins-Regular's `o`: a 16-point outer contour that is four
 * repeats of the flag pattern `[off, off, off, on]`. Each group of three consecutive off-curve
 * points decomposes into three quadratic segments with two implied on-curve points between them
 * (`(run length − 1)`, i.e. two per run of three), for 4 explicit on-curve points + 4 × 2 = 8
 * implied = 12 on-curve equivalents total. That exact contour is reproduced as this function's
 * unit test.
 */
fun Contour.onCurveEquivalentCount(): Int = count().onCurveEquivalent

/**
 * The off-curve point count, exactly as flagged — never adjusted for implied points in either
 * format. For [CurveFormat.CUBIC] this is simply the number of explicit control points (always
 * two per segment); for [CurveFormat.QUADRATIC] it is every point not flagged on-curve. Off-curve
 * counts were never the bug the corpus fix corrected (only on-curve-equivalent counting was);
 * this function exists mainly so a caller can report on-curve and off-curve counts symmetrically,
 * per glyph or per contour, the way the fixtures in CLAUDE.md do ("o 16 on · 16 off").
 */
fun Contour.offCurveCount(): Int = count().offCurve

/** This glyph's on-curve-equivalent count, summed across its contours; see [Contour.onCurveEquivalentCount]. */
fun Glyph.onCurveEquivalentCount(): Int = count().onCurveEquivalent

/** This glyph's off-curve count, summed across its contours; see [Contour.offCurveCount]. */
fun Glyph.offCurveCount(): Int = count().offCurve

private fun countQuadratic(points: List<ContourPoint>): ContourCount {
    val n = points.size
    var explicitOn = 0
    for (p in points) if (p.onCurve) explicitOn++
    val off = n - explicitOn
    var implied = 0
    for (i in points.indices) {
        val j = (i + 1) % n
        if (!points[i].onCurve && !points[j].onCurve) implied++
    }
    return ContourCount(onCurveEquivalent = explicitOn + implied, offCurve = off)
}

private fun countCubic(points: List<ContourPoint>): ContourCount {
    var explicitOn = 0
    for (p in points) if (p.onCurve) explicitOn++
    return ContourCount(onCurveEquivalent = explicitOn, offCurve = points.size - explicitOn)
}
