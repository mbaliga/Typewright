// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.qa

import dev.aarso.typewright.core.geometry.Axis
import dev.aarso.typewright.core.geometry.Contour
import dev.aarso.typewright.core.geometry.Glyph
import dev.aarso.typewright.core.geometry.Point
import dev.aarso.typewright.core.geometry.Segment
import dev.aarso.typewright.core.geometry.angleBetween
import dev.aarso.typewright.core.geometry.chords
import dev.aarso.typewright.core.geometry.count
import dev.aarso.typewright.core.geometry.extrema
import dev.aarso.typewright.core.geometry.pointAt
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Fontbakery's six published outline thresholds (docs/RESEARCH_font_quality.md, "Outlines:
 * six published thresholds separate drawn glyphs from pixel traces"; the source numbers are
 * `fontbakery`'s `outline_settings.py`, which this file's constants match exactly) plus extrema-
 * on-curve (item 4 of this task), run over `core-geometry`'s [Contour] -- never a compiled binary,
 * for the same reason [dev.aarso.typewright.qa.checkNodeEconomy] measures the UFO (law 1;
 * docs/ARCHITECTURE_REVIEW.md section 3 `:qa`, risk 2). Fontbakery itself treats all six as
 * heuristics that WARN, never FAIL, and exempts a check that fires more than 100 times in one font
 * as design intent (the research doc's own words) -- that exemption is not implemented here (out
 * of this task's scope: it needs a whole-font view, not a per-glyph one), so a caller aggregating
 * these into a layer-two report should apply it itself before treating a high count as meaningful.
 *
 * **Which segments these checks look at.** Every check here works from [Contour.chords] --
 * `core-geometry`'s straight anchor-to-anchor [Segment] for each curve segment (see that
 * function's own KDoc) -- rather than distinguishing UFO's `line`/`curve` point types. Fontbakery's
 * "collinear segments" and "semi-vertical" checks are usually described over literal straight
 * lines, but a chord is exactly the straight reference a designer's eye judges a curve's
 * *direction* against at each node, curved or not, so treating every chord uniformly is this
 * module's deliberate, documented choice, not an oversight.
 */
private const val ALIGNMENT_MISS_TOLERANCE = 2.0
private const val COLLINEAR_THRESHOLD_RAD = 0.1
private const val JAGGY_THRESHOLD_RAD = 0.25
private const val SHORT_SEGMENT_MIN_UNITS = 3.0
private const val SHORT_SEGMENT_MIN_FRACTION = 0.006
private const val SEMI_VERTICAL_THRESHOLD_RAD = 0.5 * PI / 180.0

/** One geometric-heuristic finding: which [checkId] fired, where in the glyph, and a plain-language [message]. */
data class GeometricFinding(
    val checkId: String,
    val glyphName: String,
    val contourIndex: Int,
    val location: Point,
    val message: String,
)

/**
 * Alignment-miss (docs/RESEARCH_font_quality.md): an on-curve point within [ALIGNMENT_MISS_TOLERANCE]
 * units of one of [metricLines] but not exactly on it. [metricLines] is a name-to-value map, for
 * example `mapOf("baseline" to 0, "x-height" to (fontInfo.xHeight ?: return emptyList()), ...)` --
 * building that map from a [dev.aarso.typewright.core.font.ufo.UfoFontInfo] is the caller's job,
 * since not every glyph should be checked against every line (a lowercase glyph rarely touches
 * cap-height) and this function does not have a glyph-inventory model to know which apply (see
 * item 7's own caveat on that same gap).
 */
fun checkAlignmentMiss(
    glyph: Glyph,
    metricLines: Map<String, Int>,
): List<GeometricFinding> {
    if (metricLines.isEmpty()) return emptyList()
    val findings = mutableListOf<GeometricFinding>()
    glyph.contours.forEachIndexed { contourIndex, contour ->
        for (cp in contour.points) {
            if (!cp.onCurve) continue
            val nearest = metricLines.entries.filter { it.value != cp.point.y }.minByOrNull { abs(it.value - cp.point.y) } ?: continue
            val distance = abs(nearest.value - cp.point.y)
            if (distance <= ALIGNMENT_MISS_TOLERANCE) {
                findings +=
                    GeometricFinding(
                        checkId = "outline/alignment-miss",
                        glyphName = glyph.name,
                        contourIndex = contourIndex,
                        location = cp.point,
                        message =
                            "An on-curve point at y=${cp.point.y} sits $distance unit(s) off ${nearest.key} " +
                                "(${nearest.value}) -- close enough to look like a mistake, not far enough to be overshoot.",
                    )
            }
        }
    }
    return findings
}

/** Collinear segments (docs/RESEARCH_font_quality.md): consecutive chords whose turn is smaller than [COLLINEAR_THRESHOLD_RAD]. */
fun checkCollinearSegments(glyph: Glyph): List<GeometricFinding> =
    checkConsecutiveChordAngles(glyph, "outline/collinear-segments") { angle ->
        if (abs(angle) < COLLINEAR_THRESHOLD_RAD) {
            "Two segments meeting here turn by only ${angle.toDegreesString()} -- practically the same direction; " +
                "the point between them may be redundant."
        } else {
            null
        }
    }

/** Jaggy turns (docs/RESEARCH_font_quality.md): consecutive chords whose turn is [JAGGY_THRESHOLD_RAD] or smaller. */
fun checkJaggyTurns(glyph: Glyph): List<GeometricFinding> =
    checkConsecutiveChordAngles(glyph, "outline/jaggy-segments") { angle ->
        if (abs(angle) <= JAGGY_THRESHOLD_RAD) {
            "Two segments meeting here turn by only ${angle.toDegreesString()} -- a small enough kink to read as " +
                "jaggy rather than a smooth curve or a deliberate corner."
        } else {
            null
        }
    }

private fun checkConsecutiveChordAngles(
    glyph: Glyph,
    checkId: String,
    describe: (angle: Double) -> String?,
): List<GeometricFinding> {
    val findings = mutableListOf<GeometricFinding>()
    glyph.contours.forEachIndexed { contourIndex, contour ->
        val chords = contour.chords()
        if (chords.size < 2) return@forEachIndexed
        for (i in chords.indices) {
            val a = chords[i]
            val b = chords[(i + 1) % chords.size]
            if (a.length() == 0.0 || b.length() == 0.0) continue
            val angle = angleBetween(a, b)
            val message = describe(angle) ?: continue
            findings += GeometricFinding(checkId, glyph.name, contourIndex, a.end, message)
        }
    }
    return findings
}

/**
 * Short segments (docs/RESEARCH_font_quality.md): a chord shorter than [SHORT_SEGMENT_MIN_UNITS]
 * units, or shorter than [SHORT_SEGMENT_MIN_FRACTION] of its own contour's total chord length --
 * whichever is stricter for that contour (a large glyph's "short" is a larger absolute number).
 */
fun checkShortSegments(glyph: Glyph): List<GeometricFinding> {
    val findings = mutableListOf<GeometricFinding>()
    glyph.contours.forEachIndexed { contourIndex, contour ->
        val chords = contour.chords()
        val totalLength = chords.sumOf { it.length() }
        val fractionFloor = totalLength * SHORT_SEGMENT_MIN_FRACTION
        for (chord in chords) {
            val length = chord.length()
            if (length < SHORT_SEGMENT_MIN_UNITS || length < fractionFloor) {
                findings +=
                    GeometricFinding(
                        checkId = "outline/short-segments",
                        glyphName = glyph.name,
                        contourIndex = contourIndex,
                        location = chord.start,
                        message =
                            "A segment from (${chord.start.x}, ${chord.start.y}) to (${chord.end.x}, ${chord.end.y}) " +
                                "is only ${lengthString(length)} units long -- shorter than $SHORT_SEGMENT_MIN_UNITS units " +
                                "and shorter than ${SHORT_SEGMENT_MIN_FRACTION * 100}% of this contour's length.",
                    )
            }
        }
    }
    return findings
}

/**
 * Semi-vertical (docs/RESEARCH_font_quality.md): a chord within [SEMI_VERTICAL_THRESHOLD_RAD]
 * (0.5°) of exactly horizontal or exactly vertical, but not exactly on either.
 */
fun checkSemiVertical(glyph: Glyph): List<GeometricFinding> {
    val findings = mutableListOf<GeometricFinding>()
    glyph.contours.forEachIndexed { contourIndex, contour ->
        for (chord in contour.chords()) {
            val v = chord.unitVector()
            if (v.x == 0.0 && v.y == 0.0) continue
            val theta = atan2(v.y, v.x)
            val quarterTurn = PI / 2.0
            val foldedIntoQuarter = ((theta % quarterTurn) + quarterTurn) % quarterTurn
            val deviationFromAxis = min(foldedIntoQuarter, quarterTurn - foldedIntoQuarter)
            if (deviationFromAxis > 0.0 && deviationFromAxis <= SEMI_VERTICAL_THRESHOLD_RAD) {
                val axisName = if (foldedIntoQuarter < quarterTurn / 2.0 || foldedIntoQuarter > quarterTurn) "horizontal" else "vertical"
                findings +=
                    GeometricFinding(
                        checkId = "outline/semi-vertical",
                        glyphName = glyph.name,
                        contourIndex = contourIndex,
                        location = chord.start,
                        message =
                            "A segment from (${chord.start.x}, ${chord.start.y}) to (${chord.end.x}, ${chord.end.y}) is " +
                                "${(deviationFromAxis * 180.0 / PI).toDegreeRoundedString()}° off $axisName -- close enough " +
                                "to look intended as $axisName, not quite there.",
                    )
            }
        }
    }
    return findings
}

/**
 * Extrema-on-curve (this task's item 4): every local x/y extremum `core-geometry`'s
 * [Contour.extrema] finds is, by that function's own contract, a location strictly *between* two
 * anchors (`0 < t < 1`) -- so every entry it returns already fails this check by construction (an
 * extremum that already sits on an anchor is never reported there at all; see [Contour.extrema]'s
 * KDoc). This function therefore just reports each one, resolved to a real location with
 * [dev.aarso.typewright.core.geometry.pointAt] and rounded to the nearest font unit for display.
 */
fun checkExtremaOnCurve(glyph: Glyph): List<GeometricFinding> {
    val findings = mutableListOf<GeometricFinding>()
    glyph.contours.forEachIndexed { contourIndex, contour ->
        for (extremum in contour.extrema()) {
            val point = extremum.segment.pointAt(extremum.t)
            val axisName = if (extremum.axis == Axis.X) "x" else "y"
            findings +=
                GeometricFinding(
                    checkId = "outline/extrema-off-curve",
                    glyphName = glyph.name,
                    contourIndex = contourIndex,
                    location = Point(point.x.roundToInt(), point.y.roundToInt()),
                    message =
                        "This contour's local $axisName extremum near (${point.x.roundToInt()}, ${point.y.roundToInt()}) " +
                            "does not land on an on-curve point.",
                )
        }
    }
    return findings
}

/** Off-curve ratio (this task's item 5): reported only, no threshold (the brief treats it as informational). */
data class OffCurveRatio(
    val glyphName: String,
    val onCurve: Int,
    val offCurve: Int,
    val ratio: Double?,
)

/** off-curve / (on-curve + off-curve) for [glyph], or `null` for a genuinely contourless glyph (avoids a 0/0 division). */
fun offCurveRatio(glyph: Glyph): OffCurveRatio {
    val counts = glyph.count()
    val total = counts.onCurveEquivalent + counts.offCurve
    return OffCurveRatio(
        glyphName = glyph.name,
        onCurve = counts.onCurveEquivalent,
        offCurve = counts.offCurve,
        ratio = if (total == 0) null else counts.offCurve.toDouble() / total,
    )
}

private fun Double.toDegreesString(): String {
    val degrees = this * 180.0 / PI
    return "${(degrees * 10.0).roundToInt() / 10.0}°"
}

private fun Double.toDegreeRoundedString(): String = ((this * 100.0).roundToInt() / 100.0).toString()

private fun lengthString(length: Double): String = ((length * 100.0).roundToInt() / 100.0).toString()
