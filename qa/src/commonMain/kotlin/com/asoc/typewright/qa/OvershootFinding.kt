// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa

import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.core.geometry.Axis
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.extrema
import com.asoc.typewright.core.geometry.pointAt
import kotlin.math.roundToInt

/**
 * Overshoot presence (this task's item 6; brief §7: "a flat-topped round glyph gets no overshoot
 * and the app says so"). Informational only, per that same brief line: this never fails or warns
 * by itself, it names what it found.
 *
 * **"Round" heuristic (documented, not measured -- law 5).** A glyph counts as round enough to
 * check when either:
 * - it has two or more contours (a counter implies a bowl -- o, e, a, ...), or
 * - one of its contours has a non-empty [com.asoc.typewright.core.geometry.Contour.extrema]:
 *   by that function's own contract only a curved ([com.asoc.typewright.core.geometry.CurveSegment.Quadratic]/
 *   [com.asoc.typewright.core.geometry.CurveSegment.Cubic]) segment ever contributes an extremum
 *   (a straight [com.asoc.typewright.core.geometry.CurveSegment.Line] contributes none), so this
 *   is really asking "does this contour curve anywhere near an extreme" (c, s, ...).
 *
 * This will also call a few genuinely angular shapes "round" (for example a diamond drawn with
 * curved corners at its points) and miss some genuinely round ones with no interior extremum at
 * all (a perfect square-ish superellipse whose curvature is spread too evenly to register as one);
 * it is a heuristic, stated as one.
 *
 * **Flat-top/flat-bottom detection (documented, not measured -- law 5).** A contour's extreme at a
 * metric line is called *flat*, and exempted from the "no overshoot" flag, when two or more of that
 * contour's own **on-curve** points share that exact extreme y -- a single peak point cannot be
 * flat, but a run of points at the same height (Hyle Deco's rounded-rectangle `o`, flat top and
 * bottom by design) can.
 */
data class OvershootFinding(
    val glyphName: String,
    val contourIndex: Int,
    val metricLine: String,
    val metricValue: Int,
    val isFlat: Boolean,
    val message: String,
)

/**
 * Checks [glyph] for the "no overshoot" case against [metricLines] (a name-to-value map, typically
 * built by a caller from [fontInfo]'s baseline/x-height/cap-height -- see [checkAlignmentMiss]'s
 * KDoc for the same pattern). Returns one [OvershootFinding] per contour whose vertical extreme
 * sits exactly on a metric line; a flat one is named as such (informational, not a problem) and a
 * non-flat one is the "gets no overshoot" case brief §7 asks the app to say out loud. Glyphs judged
 * not round by the heuristic above, or with no contours, produce no findings at all.
 */
fun checkOvershootPresence(
    glyph: Glyph,
    fontInfo: UfoFontInfo,
): List<OvershootFinding> {
    if (glyph.contours.isEmpty()) return emptyList()
    val isRound = glyph.contours.size >= 2 || glyph.contours.any { it.extrema().isNotEmpty() }
    if (!isRound) return emptyList()

    // Baseline (y=0) is a font-wide convention (CLAUDE.md: "integers at rest ... y up"), not a
    // UfoFontInfo field, so it is always checked; x-height/cap-height are checked only when the
    // project's fontinfo actually states them.
    val metricLines =
        buildMap {
            put("baseline", 0)
            fontInfo.xHeight?.let { put("x-height", it) }
            fontInfo.capHeight?.let { put("cap-height", it) }
        }

    val findings = mutableListOf<OvershootFinding>()
    glyph.contours.forEachIndexed { contourIndex, contour ->
        val onCurveYs = contour.points.filter { it.onCurve }.map { it.point.y }
        val extremaYs =
            contour.extrema().filter { it.axis == Axis.Y }.map {
                it.segment
                    .pointAt(it.t)
                    .y
                    .roundToInt()
            }
        val allYs = onCurveYs + extremaYs
        if (allYs.isEmpty()) return@forEachIndexed
        val top = allYs.max()
        val bottom = allYs.min()
        for ((lineName, lineValue) in metricLines) {
            if (top == lineValue) findings += overshootFinding(glyph.name, contourIndex, lineName, lineValue, onCurveYs, top, "top")
            if (bottom == lineValue && bottom != top) {
                findings += overshootFinding(glyph.name, contourIndex, lineName, lineValue, onCurveYs, bottom, "bottom")
            }
        }
    }
    return findings
}

private fun overshootFinding(
    glyphName: String,
    contourIndex: Int,
    lineName: String,
    lineValue: Int,
    onCurveYs: List<Int>,
    extremeY: Int,
    side: String,
): OvershootFinding {
    val isFlat = onCurveYs.count { it == extremeY } >= 2
    val message =
        if (isFlat) {
            "'$glyphName' is flat-topped/flat-bottomed at $side ($lineName, $lineValue) by design: overshoot does not apply here."
        } else {
            "'$glyphName' has no overshoot: its $side sits exactly on $lineName ($lineValue). A round $side usually " +
                "reads better a few units beyond the line (about 6-20 units at 1000 UPM is typical -- our heuristic, " +
                "docs/RESEARCH_font_quality.md)."
        }
    return OvershootFinding(glyphName, contourIndex, lineName, lineValue, isFlat, message)
}
