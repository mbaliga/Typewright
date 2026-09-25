// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

/**
 * [TypeConstraintParameters.tangentSnapDegrees]'s default: within half a degree of horizontal or
 * vertical gets snapped exactly onto it (`TYPEWRIGHT_BUILD_BRIEF.md` §7 stage 6 "Snap").
 */
const val DEFAULT_TANGENT_SNAP_DEGREES: Double = 0.5

/**
 * [TypeConstraintParameters.metricSnapDistance]'s default, in font units: a point within 2 units
 * of a supplied metric line snaps exactly onto it, unless overshoot is preserved (see
 * [snapPointsToMetricLines]'s KDoc).
 */
const val DEFAULT_METRIC_SNAP_DISTANCE: Int = 2

/**
 * [TypeConstraintParameters.minimumExtremumBulgeUnits]'s default, in font units: an interior
 * extremum whose segment bulges less than this far from its own straight chord, at that
 * extremum's own point, is not inserted as a new on-curve anchor. See
 * [insertExtremaOnCurvePoints]'s KDoc for why this exists at all — found and tuned honestly
 * against the real `fonts/HyleDeco-Regular.ttf` fixtures (`FitPipelineHyleDecoValidationTest`'s
 * KDoc has the before/after numbers), not against any synthetic test alone. `0.5` sits comfortably
 * below the smallest bulge a real, designed round feature in this app's fixtures produces (a
 * rounded corner's own radius, tens of units at minimum) and above the sub-unit wobble a
 * least-squares fit of real, integer-quantised, near-straight trace data leaves behind on a
 * segment that is otherwise already [CurveSegment.Cubic.isEffectivelyStraight].
 *
 * **Honestly, this does not fully close the gap for `H`.** Raising this value further (`1.0`,
 * `1.5`) was tried and is reported here rather than chosen silently: it does reduce `H`'s point
 * count a little more (18, then 16, matching the raw P2a fit exactly with zero insertions), but
 * at `1.5` it also starts making [classifyConstruction] misclassify `H` as
 * [ConstructionKind.ROUNDED_RECTANGLE] instead of [ConstructionKind.POLYGONAL] — `H`'s own raw
 * Schneider fit (P2a, `errorTolerance = 2.0`) leaves at least one segment with genuine,
 * non-numerical-noise curvature up to nearly 2 units (plausibly an imperfectly-merged corner
 * "staircase", `DEFAULT_CORNER_MERGE_DISTANCE`'s own KDoc already names this exact corner), which
 * no amount of *this* stage's own tuning can distinguish from a small designed feature without
 * risking under-fitting real curvature elsewhere. `0.5` is kept as the more conservative,
 * defensible choice — a stage that trims sub-visual-significance noise without reaching past its
 * own scope to compensate for an upstream (P2a) fitter's own residual error. See
 * `FitPipelineHyleDecoValidationTest`'s printed report for the exact honest numbers this produces
 * on all four real letters.
 */
const val DEFAULT_MINIMUM_EXTREMUM_BULGE_UNITS: Double = 0.5

/**
 * Tuning for [applyTypeConstraints]/[applyTypeConstraintsToContours] — P2b's "Snap" stage
 * (`TYPEWRIGHT_BUILD_BRIEF.md` §7 stage 6), applied after [fitClosedContourToCubics]. One fixed,
 * global set of numbers, never adjusted per glyph (the same anti-gaming rule P2a's
 * [CubicFitParameters] documents).
 */
data class TypeConstraintParameters(
    val tangentSnapDegrees: Double = DEFAULT_TANGENT_SNAP_DEGREES,
    val metricSnapDistance: Int = DEFAULT_METRIC_SNAP_DISTANCE,
    val minimumExtremumBulgeUnits: Double = DEFAULT_MINIMUM_EXTREMUM_BULGE_UNITS,
)

/**
 * P2b's "Snap" stage entry point for one contour: [insertExtremaOnCurvePoints], then
 * [snapNearAxisTangents], then [snapPointsToMetricLines against][snapPointsToMetricLines]
 * [metricLines]. Rounding to integer font units ("integers at rest", CLAUDE.md) is not a separate
 * step here: every one of those three passes rebuilds its contour through
 * [buildCubicContour]/[Vec2.roundToPoint], so a coordinate is never left as an unrounded
 * intermediate value between stages — see those functions' own KDoc.
 *
 * Direction ([Contour.direction]) is deliberately **not** enforced here: a single contour has no
 * way to know whether it is an outer or inner contour of its glyph — that needs every contour of
 * the glyph together, which is what [applyTypeConstraintsToContours] is for. A caller with only
 * one, definitely-outer contour (this function's own unit tests; a glyph like `T`, `H` or `n`
 * that never has a counter) can call [Contour.direction]/[Contour.reverse] directly afterwards if
 * it needs to.
 *
 * [contour] must already be [CurveFormat.CUBIC] (the P2 fitter's own output format); this stage
 * never runs on a raw dense polyline or a [CurveFormat.QUADRATIC] source contour.
 */
fun applyTypeConstraints(
    contour: Contour,
    metricLines: List<Int> = emptyList(),
    params: TypeConstraintParameters = TypeConstraintParameters(),
): Contour {
    require(contour.format == CurveFormat.CUBIC) {
        "applyTypeConstraints operates on a fitted CUBIC contour (fitClosedContourToCubics' own output), was ${contour.format}"
    }
    val withExtrema = insertExtremaOnCurvePoints(contour, params.minimumExtremumBulgeUnits)
    val tangentsSnapped = snapNearAxisTangents(withExtrema, params.tangentSnapDegrees)
    return snapPointsToMetricLines(tangentsSnapped, metricLines, params.metricSnapDistance)
}

/**
 * [applyTypeConstraints], applied to every contour of a fitted glyph (`o`'s outer and inner ring,
 * for example), followed by [enforceContourDirections] across the whole set — the one place in
 * this stage that genuinely needs every contour together, since "is this contour an outer or an
 * inner (counter) contour" (CLAUDE.md: "outer contours counter-clockwise, inner clockwise") is a
 * question about the *glyph*, not about any one contour alone.
 */
fun applyTypeConstraintsToContours(
    contours: List<Contour>,
    metricLines: List<Int> = emptyList(),
    params: TypeConstraintParameters = TypeConstraintParameters(),
): List<Contour> {
    val perContour = contours.map { applyTypeConstraints(it, metricLines, params) }
    return enforceContourDirections(perContour)
}

// -------------------------------------------------------------------------------------------
// Step 1: insert on-curve points at extrema.
// -------------------------------------------------------------------------------------------

/**
 * Splits every cubic segment of [contour] at its own interior x/y extrema (reusing
 * `core-geometry`'s existing extrema primitive, [CurveSegment.extremaT], built in P1a), inserting
 * a real on-curve anchor there — `TYPEWRIGHT_BUILD_BRIEF.md` §7 stage 6's "insert on-curve points
 * at extrema".
 *
 * [CurveSegment.extremaT]'s own contract already does the "not already landing on an on-curve
 * point" filtering this brief line asks for: it only ever reports interior parameter values
 * (`0 < t < 1`), never a segment's own endpoints — which are exactly [contour]'s existing
 * on-curve anchors (see `Contour.kt`'s CUBIC invariant). An extremum sitting exactly at a
 * transition between two segments (an anchor that already happens to be a local extremum, for
 * example the very top of an already-corner-detected arc) is therefore never reported here in the
 * first place, and this function correctly leaves it alone.
 *
 * A segment with two extrema close together (rare — a tight double-bend within one arc) is split
 * at both, in order, via repeated de Casteljau subdivision ([splitCubicAt]), each subsequent split
 * point's parameter re-mapped onto the *remaining* sub-segment (see [splitCubicAtParameters]).
 *
 * **A magnitude floor, found honestly against real data, not only synthetic fixtures.**
 * [CurveSegment.extremaT]'s own contract is purely algebraic: any segment whose curve is not
 * *exactly* linear along an axis reports a root there, with no notion of how visually or
 * practically significant that root's bulge actually is. Against a hand-built, perfectly on-line
 * synthetic polygon (this file's own and `CubicFittingTest`'s fixtures) that is a non-issue — such
 * a segment's coefficients are exactly zero and it reports no extrema at all. Against the real
 * `fonts/HyleDeco-Regular.ttf` `T`/`H`/`n`/`o` (`FitPipelineHyleDecoValidationTest`), it was not:
 * the Schneider fitter's own least-squares solve, fitting real, slightly noisy trace data, produces
 * plenty of segments that are [CurveSegment.Cubic.isEffectivelyStraight] (within its own 1.5-unit
 * control-point tolerance) yet still carry a tiny, non-zero, purely numerical wobble — enough for
 * [CurveSegment.extremaT] to report a technically-real interior root, with a bulge nowhere near a
 * font's real design intent (a fraction of a unit, not the tens-of-units bulge a designed round
 * corner or bowl actually has). Splitting at every one of those inflated `H` from 16 to 21 on-curve
 * points and `n` from 17 to 24, each strictly *worse* than P2a's own already-honest gap against
 * `TYPEWRIGHT_BUILD_BRIEF.md` §7's targets, before [minimumBulgeUnits] existed at all — this file's
 * git history has the exact before/after numbers. [minimumBulgeUnits] ([DEFAULT_MINIMUM_EXTREMUM_BULGE_UNITS])
 * requires an extremum's own bulge — the perpendicular distance from its segment's straight chord
 * to `segment.pointAt(t)`, the same point-to-line measure [CurveSegment.Cubic.isEffectivelyStraight]
 * uses, evaluated at the extremum's own `t` rather than at its control points — to clear this floor
 * before it is inserted at all. This is one more global, glyph-agnostic tuning knob (P2a's
 * anti-gaming rule), not a special case for any of these four letters.
 *
 * `public`, not `internal` (P5b): this is also the Palette's own "add extremes" command
 * (`TYPEWRIGHT_BUILD_BRIEF.md` line 372-373) — used directly by a later UI task rather than
 * reimplemented, in the same pure-visibility-widening spirit as `Offset.kt`'s own `MAX_FLATTEN_DEPTH`/
 * `isFlatEnough`/`distanceToLine` (P4b: `private` to `internal`). No behaviour change here either.
 */
fun insertExtremaOnCurvePoints(
    contour: Contour,
    minimumBulgeUnits: Double = DEFAULT_MINIMUM_EXTREMUM_BULGE_UNITS,
): Contour {
    val segments = contour.cubicSegments()
    val split =
        segments.flatMap { segment ->
            val extremaTs =
                (segment.extremaT(Axis.X) + segment.extremaT(Axis.Y))
                    .distinct()
                    .filter { t -> bulgeAt(segment, t) >= minimumBulgeUnits }
                    .sorted()
            splitCubicAtParameters(segment, extremaTs)
        }
    return buildCubicContour(split)
}

/**
 * The perpendicular distance from [segment]'s straight start-to-end chord to
 * `segment.pointAt(t)` — the same point-to-line measure [CurveSegment.Cubic.isEffectivelyStraight]
 * uses, evaluated at one specific parameter rather than at the segment's control points. `0.0` for
 * a zero-length chord (nothing to measure a bulge away from).
 */
private fun bulgeAt(
    segment: CurveSegment.Cubic,
    t: Double,
): Double {
    val chord = segment.end - segment.start
    val chordLength = chord.length()
    if (chordLength <= 1e-9) return 0.0
    val point = segment.pointAt(t)
    return abs(chord.cross(point - segment.start)) / chordLength
}

/**
 * De Casteljau subdivision of [segment] at parameter [t] (`0 < t < 1`): the standard construction
 * (repeated linear interpolation at `t` across the control polygon) that splits one cubic Bezier
 * into two cubics which together trace exactly the same curve, sharing the point `segment.pointAt(t)`
 * with matching (not just G1, but exactly equal) tangent direction there.
 */
internal fun splitCubicAt(
    segment: CurveSegment.Cubic,
    t: Double,
): Pair<CurveSegment.Cubic, CurveSegment.Cubic> {
    val p0 = segment.start
    val p1 = segment.control1
    val p2 = segment.control2
    val p3 = segment.end
    val p01 = lerpVec2(p0, p1, t)
    val p12 = lerpVec2(p1, p2, t)
    val p23 = lerpVec2(p2, p3, t)
    val p012 = lerpVec2(p01, p12, t)
    val p123 = lerpVec2(p12, p23, t)
    val split = lerpVec2(p012, p123, t)
    return CurveSegment.Cubic(p0, p01, p012, split) to CurveSegment.Cubic(split, p123, p23, p3)
}

private fun lerpVec2(
    a: Vec2,
    b: Vec2,
    t: Double,
): Vec2 = a + (b - a) * t

/**
 * Splits [segment] at every parameter in [sortedInteriorTs] (ascending, each strictly inside
 * `(0, 1)`), via repeated [splitCubicAt]. Each split consumes the segment's own *remaining* tail,
 * so every parameter after the first must be re-expressed relative to what is left: after
 * splitting off `[0, t1]`, the remaining segment spans the original curve's `[t1, 1]`, so an
 * original parameter `t2 > t1` lands at local parameter `(t2 - t1) / (1 - t1)` on it — the
 * standard Bezier re-parameterisation for a sub-range. Returns `[segment]` unchanged when
 * [sortedInteriorTs] is empty, and silently skips a parameter that (after this re-mapping) no
 * longer falls strictly inside `(0, 1)` — this can only happen for two extrema numerically
 * coincident to floating-point precision, never for two genuinely distinct extrema, and treating
 * them as one point is the only sensible outcome either way.
 */
internal fun splitCubicAtParameters(
    segment: CurveSegment.Cubic,
    sortedInteriorTs: List<Double>,
): List<CurveSegment.Cubic> {
    if (sortedInteriorTs.isEmpty()) return listOf(segment)
    val result = mutableListOf<CurveSegment.Cubic>()
    var remaining = segment
    var consumed = 0.0
    for (t in sortedInteriorTs) {
        val localT = (t - consumed) / (1.0 - consumed)
        if (localT <= 0.0 || localT >= 1.0) continue
        val (left, right) = splitCubicAt(remaining, localT)
        result += left
        remaining = right
        consumed = t
    }
    result += remaining
    return result
}

// -------------------------------------------------------------------------------------------
// Step 2: snap near-horizontal/near-vertical tangents exact.
// -------------------------------------------------------------------------------------------

/**
 * Snaps every cubic segment's two tangent directions (`control1 - start`, `control2 - end`) to
 * exactly horizontal or exactly vertical wherever they already sit within [thresholdDegrees] of
 * one — `TYPEWRIGHT_BUILD_BRIEF.md` §7 stage 6's "snap tangents within 0.5° of horizontal or
 * vertical". Applies uniformly to every segment, curved or straight: an already-straight,
 * on-line-degenerate segment (see `SegmentStraightness.kt`) whose chord happens to be exactly
 * horizontal or vertical is already snapped by construction and this is a no-op on it; one whose
 * chord is close to, but not exactly, axis-aligned gets pulled onto the axis exactly like a real
 * curve's tangent would.
 */
internal fun snapNearAxisTangents(
    contour: Contour,
    thresholdDegrees: Double,
): Contour {
    val thresholdRadians = thresholdDegrees * PI / 180.0
    val snapped =
        contour.cubicSegments().map { segment ->
            val newControl1 = snapControlTangent(anchor = segment.start, control = segment.control1, thresholdRadians)
            val newControl2 = snapControlTangent(anchor = segment.end, control = segment.control2, thresholdRadians)
            CurveSegment.Cubic(segment.start, newControl1, newControl2, segment.end)
        }
    return buildCubicContour(snapped)
}

/**
 * [control]'s new position: unchanged unless the direction from [anchor] to [control] sits within
 * [thresholdRadians] of horizontal or vertical, in which case the off-axis coordinate is set to
 * exactly match [anchor]'s own coordinate on that axis (moving [control] onto the axis through
 * [anchor] without changing its distance from [anchor] along the axis it is snapping *to*).
 *
 * A direction and its own reverse describe the same line (a tangent has no "which way" for this
 * purpose), so the angle is measured modulo PI: `atan2(dy, dx)` normalized into `[0, PI)`, then
 * compared to both `0`/`PI` (horizontal) and `PI/2` (vertical) by the shorter distance in each
 * case. A zero-length direction (the control point sits exactly on its anchor) has no direction
 * to measure and is left alone.
 */
private fun snapControlTangent(
    anchor: Vec2,
    control: Vec2,
    thresholdRadians: Double,
): Vec2 {
    val dx = control.x - anchor.x
    val dy = control.y - anchor.y
    if (dx == 0.0 && dy == 0.0) return control
    val angle = atan2(dy, dx)
    var normalized = angle % PI
    if (normalized < 0.0) normalized += PI
    val distanceToHorizontal = minOf(normalized, PI - normalized)
    val distanceToVertical = abs(normalized - PI / 2.0)
    return when {
        distanceToHorizontal <= thresholdRadians -> Vec2(control.x, anchor.y)
        distanceToVertical <= thresholdRadians -> Vec2(anchor.x, control.y)
        else -> control
    }
}

// -------------------------------------------------------------------------------------------
// Step 3: snap points near a metric line, except a genuinely curved (non-flat) approach.
// -------------------------------------------------------------------------------------------

/**
 * Snaps each on-curve anchor within [snapDistance] font units of its nearest entry in
 * [metricLines] to exactly that line's y value — `TYPEWRIGHT_BUILD_BRIEF.md` §7 stage 6's "snap
 * points within 2 units of a supplied metric line ... except where overshoot is detected on a
 * curved approach ... in which case preserve the original position".
 *
 * **Flat versus curved, adapted from `qa`'s [com.asoc.typewright.qa.checkOvershootPresence]
 * heuristic** (this task's shared instructions point at that file's `isFlat` idea specifically).
 * For each candidate anchor at extreme y-value `extremeY`:
 * - **flat**: two or more of this contour's on-curve anchors already share `extremeY` (a run —
 *   Hyle Deco's rounded-rectangle `o`'s flat top and bottom, or two straight sides meeting at the
 *   same height by coincidence). A flat approach has no real curvature to preserve an overshoot
 *   *of*, so it always snaps.
 * - **curved**: exactly one anchor sits at `extremeY`, and at least one of its two neighbouring
 *   segments is *not* [CurveSegment.Cubic.isEffectivelyStraight] — the anchor is genuinely the
 *   peak of a round bulge, not a plain polygon vertex. This is preserved, unsnapped, since a
 *   round top or bottom sitting deliberately a little off the line is exactly what "overshoot" is
 *   (CLAUDE.md law 5's "our heuristic", `com.asoc.typewright.qa.OvershootFinding`'s own note that
 *   a designed overshoot is typically several units, well inside this 2-unit band at the small
 *   end of that range).
 * - **sharp corner** (not flat, but every neighbouring segment *is* effectively straight — CLAUDE.md
 *   fixture "T stem foot y=1 -> 0"): there is no curvature anywhere nearby to be an overshoot, so
 *   this snaps like the flat case.
 *
 * Ties among [metricLines] resolve to the numerically nearest one; a point already sitting exactly
 * on a line is left untouched (there is nothing to snap).
 */
internal fun snapPointsToMetricLines(
    contour: Contour,
    metricLines: List<Int>,
    snapDistance: Int,
): Contour {
    if (metricLines.isEmpty() || snapDistance <= 0) return contour
    val points = contour.points
    val onCurveIndices = points.indices.filter { it % 3 == 0 }
    if (onCurveIndices.isEmpty()) return contour
    val onCurveYs = onCurveIndices.map { points[it].point.y }
    val segments = contour.cubicSegments()

    val newPoints = points.toMutableList()
    for ((segmentIndex, anchorIndex) in onCurveIndices.withIndex()) {
        val anchor = points[anchorIndex].point
        val nearestLine = metricLines.minBy { abs(anchor.y - it) }
        val distance = abs(anchor.y - nearestLine)
        if (distance == 0 || distance > snapDistance) continue

        val isFlatRun = onCurveYs.count { it == anchor.y } >= 2
        val previousSegment = segments[(segmentIndex - 1 + segments.size) % segments.size]
        val nextSegment = segments[segmentIndex]
        val hasCurvedNeighbor = !previousSegment.isEffectivelyStraight() || !nextSegment.isEffectivelyStraight()
        val preserveAsOvershoot = !isFlatRun && hasCurvedNeighbor
        if (!preserveAsOvershoot) {
            newPoints[anchorIndex] = ContourPoint(Point(anchor.x, nearestLine), onCurve = true)
        }
    }
    return Contour(newPoints, contour.format)
}

// -------------------------------------------------------------------------------------------
// Step (glyph-level): enforce path direction across every contour of a glyph.
// -------------------------------------------------------------------------------------------

/**
 * Sets every contour's winding direction by CLAUDE.md's convention — outer contours
 * counter-clockwise, inner (counter) contours clockwise — using [Contour.direction]/
 * [Contour.reverse] (P1a) to detect and, if needed, fix each one.
 *
 * **"Outer" vs. "inner" (nesting), determined generally, not by contour order or by which is
 * larger.** For each contour, this counts how many of the glyph's *other* contours contain a
 * point known to be just inside it ([representativeInteriorPoint]): an even count (0, 2, ...)
 * means top-level/outer, so the contour should read counter-clockwise; an odd count (1, 3, ...)
 * means it is nested one level inside another contour — a counter — so it should read clockwise.
 * This is the standard even-odd nesting rule for winding assignment, and it is correct for any
 * number of contours and any nesting depth: it does not assume a glyph has exactly one outer and
 * one inner contour (true for `o`, but not for, say, a glyph with two separate, unnested shapes —
 * both would correctly come out counter-clockwise — or a doubly-nested "hole inside a hole inside
 * a hole").
 *
 * Containment is tested with a standard even-odd ray-casting point-in-polygon test
 * ([pointInPolygon]) against each contour flattened to a dense sample ([flattenForContainment]),
 * general-purpose and independent of which glyph or shape produced the contours.
 *
 * `public`, not `internal` (P5b): this is also the Palette's own "correct direction" command
 * (`TYPEWRIGHT_BUILD_BRIEF.md` line 372-373) — used directly, not reimplemented as a second
 * point-in-polygon containment check (see `Palette.kt`'s module KDoc for the full Palette registry
 * this belongs to). A pure visibility widening, no behaviour change, matching `Offset.kt`'s own
 * P4b precedent.
 */
fun enforceContourDirections(contours: List<Contour>): List<Contour> {
    if (contours.isEmpty()) return contours
    val flattened = contours.map { it.flattenForContainment() }
    val interiorPoints = contours.map { it.representativeInteriorPoint() }
    return contours.mapIndexed { index, contour ->
        val containingCount =
            contours.indices.count { other ->
                other != index && pointInPolygon(flattened[other], interiorPoints[index])
            }
        val shouldBeCounterClockwise = containingCount % 2 == 0
        val isCounterClockwise = contour.direction() == Direction.COUNTER_CLOCKWISE
        if (shouldBeCounterClockwise == isCounterClockwise) contour else contour.reverse()
    }
}

/** [contour] flattened to a dense [Vec2] polygon (12 samples per segment), for [pointInPolygon]. */
private fun Contour.flattenForContainment(samplesPerSegment: Int = 12): List<Vec2> =
    segments().flatMap { segment -> (0 until samplesPerSegment).map { segment.pointAt(it.toDouble() / samplesPerSegment) } }

/**
 * A point guaranteed to sit just inside this specific contour's own loop (never mind any *other*
 * contour): the midpoint of the contour's first segment, nudged [epsilon] font units along that
 * segment's own inward normal. "Inward" is derived from the contour's *current* winding
 * ([Contour.direction]) — for a curve traversed counter-clockwise the interior is to the left of
 * the direction of travel (`(-dy, dx)`); for clockwise it is to the right (`(dy, -dx)`) — which is
 * a property of the polygon's own shape, not of which direction we might reverse it to afterwards
 * ([enforceContourDirections] only ever reads this before it decides whether to reverse anything).
 *
 * A single contour's own geometric centroid is deliberately not used here: for an annular contour
 * (a ring's outer or inner boundary) the centroid usually sits *outside* that specific contour
 * (inside the counter it and its sibling contour together enclose), which would make this
 * function's whole point — "a point that is inside this loop and only this loop, usable to test
 * against every other contour" — false exactly where it matters most (`o`'s outer/inner rings).
 */
private fun Contour.representativeInteriorPoint(epsilon: Double = 1.0): Vec2 {
    val firstSegment = segments().first()
    val midpoint = firstSegment.pointAt(0.5)
    val tangent = firstSegment.tangentAt(0.5)
    val unitTangent = tangent.normalizedOrDefault(Vec2(1.0, 0.0))
    val inwardNormal =
        if (direction() == Direction.COUNTER_CLOCKWISE) {
            Vec2(-unitTangent.y, unitTangent.x)
        } else {
            Vec2(unitTangent.y, -unitTangent.x)
        }
    return midpoint + inwardNormal * epsilon
}

/**
 * This segment's direction of travel at parameter [t], by a small central finite difference —
 * general across [CurveSegment.Line]/[CurveSegment.Quadratic]/[CurveSegment.Cubic] alike, with no
 * per-kind derivative formula to keep in sync with [CurveSegment.pointAt].
 */
private fun CurveSegment.tangentAt(
    t: Double,
    h: Double = 1e-3,
): Vec2 {
    val lo = (t - h).coerceIn(0.0, 1.0)
    val hi = (t + h).coerceIn(0.0, 1.0)
    return pointAt(hi) - pointAt(lo)
}

private fun Vec2.normalizedOrDefault(default: Vec2): Vec2 {
    val len = length()
    return if (len > 1e-9) Vec2(x / len, y / len) else default
}

/**
 * The standard even-odd ray-casting point-in-polygon test (cast a ray from [point] in the `+x`
 * direction and count how many of [polygon]'s cyclic edges it crosses; an odd count means inside)
 * against [polygon], read as closed and cyclic.
 */
private fun pointInPolygon(
    polygon: List<Vec2>,
    point: Vec2,
): Boolean {
    var inside = false
    var previous = polygon.size - 1
    for (current in polygon.indices) {
        val a = polygon[current]
        val b = polygon[previous]
        if ((a.y > point.y) != (b.y > point.y)) {
            val xAtPointY = a.x + (point.y - a.y) / (b.y - a.y) * (b.x - a.x)
            if (point.x < xAtPointY) inside = !inside
        }
        previous = current
    }
    return inside
}

// -------------------------------------------------------------------------------------------
// Shared: a CUBIC contour's segments, known to always decode as CurveSegment.Cubic.
// -------------------------------------------------------------------------------------------

/**
 * This CUBIC contour's segments, already known (by [Contour.segments]' own decoding rule for
 * [CurveFormat.CUBIC]: one [CurveSegment.Cubic] per (on, off, off) triple) to always be
 * [CurveSegment.Cubic] — used throughout this file and `ConstructionClassifier.kt` wherever a
 * caller has already established [Contour.format] is CUBIC and wants that narrower type back
 * instead of the general [CurveSegment] sealed class.
 */
internal fun Contour.cubicSegments(): List<CurveSegment.Cubic> {
    require(format == CurveFormat.CUBIC) { "cubicSegments() requires a CUBIC contour, was $format" }
    return segments().map { it as CurveSegment.Cubic }
}
