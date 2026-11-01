// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.geometry

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A straight line through [origin] along unit vector [direction]: the thing a run of nearly collinear
 * samples is fitted to, and what a corner is re-computed from.
 */
internal class FittedLine(
    val origin: Vec2,
    val direction: Vec2,
) {
    /** Unsigned perpendicular distance from [p] to the line. */
    fun distance(p: Vec2): Double = abs(direction.cross(p - origin))

    /** The point of the line closest to [p]. */
    fun project(p: Vec2): Vec2 = origin + direction * direction.dot(p - origin)
}

/**
 * Prefix sums of a point list's first and second moments, so the total-least-squares line through any
 * contiguous index range comes back in constant time (the run search asks for thousands).
 */
internal class PointMoments(
    private val points: List<Vec2>,
) {
    private val sumX = DoubleArray(points.size + 1)
    private val sumY = DoubleArray(points.size + 1)
    private val sumXX = DoubleArray(points.size + 1)
    private val sumXY = DoubleArray(points.size + 1)
    private val sumYY = DoubleArray(points.size + 1)

    init {
        for (i in points.indices) {
            val p = points[i]
            sumX[i + 1] = sumX[i] + p.x
            sumY[i + 1] = sumY[i] + p.y
            sumXX[i + 1] = sumXX[i] + p.x * p.x
            sumXY[i + 1] = sumXY[i] + p.x * p.y
            sumYY[i + 1] = sumYY[i] + p.y * p.y
        }
    }

    /**
     * The total-least-squares line through the points from index `from` to index `to` (inclusive): the principal axis of the
     * range's covariance, oriented to run from the range's first point toward its last.
     */
    fun fit(
        from: Int,
        to: Int,
    ): FittedLine {
        val count = (to - from + 1).toDouble()
        val meanX = (sumX[to + 1] - sumX[from]) / count
        val meanY = (sumY[to + 1] - sumY[from]) / count
        val sxx = (sumXX[to + 1] - sumXX[from]) - count * meanX * meanX
        val syy = (sumYY[to + 1] - sumYY[from]) - count * meanY * meanY
        val sxy = (sumXY[to + 1] - sumXY[from]) - count * meanX * meanY
        val theta = 0.5 * atan2(2.0 * sxy, sxx - syy)
        var direction = Vec2(cos(theta), sin(theta))
        if (direction.dot(points[to] - points[from]) < 0.0) direction = direction * -1.0
        return FittedLine(Vec2(meanX, meanY), direction)
    }

    /** The largest distance from any of the points `from` to `to` to [line]. */
    fun maxDistance(
        line: FittedLine,
        from: Int,
        to: Int,
    ): Double {
        var worst = 0.0
        for (i in from..to) worst = max(worst, line.distance(points[i]))
        return worst
    }
}

/**
 * [polyline] (closed, cyclic) re-sampled at equal arc-length steps of about [spacing] font units, so every
 * later stage can measure distances in samples. Marching squares hands back one point per pixel edge it
 * crosses, unevenly spaced; fitting and corner detection both want an even step.
 */
internal fun resampleClosed(
    polyline: List<Vec2>,
    spacing: Double,
): List<Vec2> {
    val cleaned = ArrayList<Vec2>(polyline.size)
    for (p in polyline) if (cleaned.isEmpty() || (p - cleaned.last()).length() > 1e-9) cleaned += p
    if (cleaned.size > 1 && (cleaned.first() - cleaned.last()).length() <= 1e-9) cleaned.removeAt(cleaned.size - 1)
    if (cleaned.size < 3) return cleaned
    val n = cleaned.size
    val cumulative = DoubleArray(n + 1)
    for (i in 0 until n) cumulative[i + 1] = cumulative[i] + (cleaned[(i + 1) % n] - cleaned[i]).length()
    val total = cumulative[n]
    val count = max(8, (total / spacing).roundToInt())
    val step = total / count
    val out = ArrayList<Vec2>(count)
    var segment = 0
    for (k in 0 until count) {
        val target = k * step
        while (segment < n - 1 && cumulative[segment + 1] < target) segment++
        val length = cumulative[segment + 1] - cumulative[segment]
        val t = if (length > 1e-12) (target - cumulative[segment]) / length else 0.0
        val a = cleaned[segment]
        val b = cleaned[(segment + 1) % n]
        out += a + (b - a) * t
    }
    return out
}

/** [points] (closed, cyclic) smoothed by [passes] rounds of the binomial 1-2-1 kernel: noise out, shape kept to within a sample or two. */
internal fun smoothClosed(
    points: List<Vec2>,
    passes: Int,
): List<Vec2> {
    var current = points
    repeat(passes) {
        val n = current.size
        val source = current
        current = List(n) { i -> (source[(i - 1 + n) % n] + source[i] * 2.0 + source[(i + 1) % n]) * 0.25 }
    }
    return current
}

/** [points] (open) smoothed by [passes] rounds of the 1-2-1 kernel with both end points held fixed. */
internal fun smoothOpen(
    points: List<Vec2>,
    passes: Int,
): List<Vec2> {
    var current = points
    repeat(passes) {
        val source = current
        current =
            List(source.size) { i ->
                if (i == 0 || i == source.size - 1) source[i] else (source[i - 1] + source[i] * 2.0 + source[i + 1]) * 0.25
            }
    }
    return current
}

/** The signed turn, in radians, from the direction `a -> b` to the direction `b -> c`. */
internal fun turnAt(
    a: Vec2,
    b: Vec2,
    c: Vec2,
): Double {
    val incoming = b - a
    val outgoing = c - b
    return atan2(incoming.cross(outgoing), incoming.dot(outgoing))
}

/** The total length of the open polyline [points]. */
internal fun pathLength(points: List<Vec2>): Double {
    var total = 0.0
    for (i in 1 until points.size) total += (points[i] - points[i - 1]).length()
    return total
}

/**
 * How far the middle third of the points `from` to `to` bulges from [line] relative to the two outer thirds, signed. A
 * straight run scattered by noise averages out to nearly nothing; a curve, however gentle, leaves a systematic
 * bow: a circular arc of radius R over a run of length L bows by about `L * L / (18 * R)`. It is the test that
 * tells a straight side from a stretch of gentle curve that merely stays inside the line tolerance.
 */
internal fun bowOf(
    points: List<Vec2>,
    from: Int,
    to: Int,
    line: FittedLine,
): Double {
    val count = to - from + 1
    val third = max(1, count / 3)
    var middle = 0.0
    var middleCount = 0
    var outer = 0.0
    var outerCount = 0
    for (i in from..to) {
        val offset = line.direction.cross(points[i] - line.origin)
        if (i - from in third until count - third) {
            middle += offset
            middleCount++
        } else {
            outer += offset
            outerCount++
        }
    }
    if (middleCount == 0 || outerCount == 0) return 0.0
    return middle / middleCount - outer / outerCount
}

/**
 * The root mean square distance of the points `from` to `to` from the best parabola through them, taken in the frame of their
 * total-least-squares line (so the parabola is a curve across the run). A straight run with any real, smooth curvature
 * leaves almost nothing; what is left is wobble.
 */
internal fun parabolaResidualRms(
    points: List<Vec2>,
    from: Int,
    to: Int,
): Double {
    val count = to - from + 1
    if (count < 5) return 0.0
    val line = PointMoments(points).fit(from, to)
    val normal = Vec2(-line.direction.y, line.direction.x)
    // Coordinates along (t) and across (s) the line, t centred and scaled to -1..1 so the normal equations stay well conditioned.
    val ts = DoubleArray(count)
    val ss = DoubleArray(count)
    for (i in 0 until count) {
        val d = points[from + i] - line.origin
        ts[i] = line.direction.dot(d)
        ss[i] = normal.dot(d)
    }
    val tMid = (ts.min() + ts.max()) / 2.0
    val tHalf = max((ts.max() - ts.min()) / 2.0, 1e-9)
    for (i in 0 until count) ts[i] = (ts[i] - tMid) / tHalf
    // Least squares for s = a + b t + c t^2: the 3 by 3 normal equations, solved by Cramer's rule.
    var s0 = 0.0
    var s1 = 0.0
    var s2 = 0.0
    var s3 = 0.0
    var s4 = 0.0
    var r0 = 0.0
    var r1 = 0.0
    var r2 = 0.0
    for (i in 0 until count) {
        val t = ts[i]
        val t2 = t * t
        s0 += 1.0
        s1 += t
        s2 += t2
        s3 += t2 * t
        s4 += t2 * t2
        r0 += ss[i]
        r1 += ss[i] * t
        r2 += ss[i] * t2
    }
    val det = s0 * (s2 * s4 - s3 * s3) - s1 * (s1 * s4 - s3 * s2) + s2 * (s1 * s3 - s2 * s2)
    if (abs(det) < 1e-12) return 0.0
    val a = (r0 * (s2 * s4 - s3 * s3) - s1 * (r1 * s4 - s3 * r2) + s2 * (r1 * s3 - s2 * r2)) / det
    val b = (s0 * (r1 * s4 - s3 * r2) - r0 * (s1 * s4 - s3 * s2) + s2 * (s1 * r2 - r1 * s2)) / det
    val c = (s0 * (s2 * r2 - r1 * s3) - s1 * (s1 * r2 - r1 * s2) + r0 * (s1 * s3 - s2 * s2)) / det
    var sum = 0.0
    for (i in 0 until count) {
        val t = ts[i]
        val e = ss[i] - (a + b * t + c * t * t)
        sum += e * e
    }
    return sqrt(sum / count)
}

/**
 * Index ranges (inclusive) of [points] that are straight lines: contiguous samples at least [minLength] long that
 * lie within [lineTolerance] of one line and do not bow.
 *
 * The search is in two steps. First every window of [minLength] is tested (within tolerance of its own best-fit
 * line, and bowing by no more than [bowTolerance]); a stretch of passing windows is a straight core. Then the
 * core is refined: the line is fitted to the core, each end is trimmed until its sample lies within
 * [trimTolerance] of that line, the line is refitted, and the ends are regrown the same way, so the run stops
 * where the curve beside it actually begins instead of a few units inside it, and a curve's tail never tilts the
 * line it joins.
 *
 * A run has to be long and unbowed, not merely straight within tolerance: a gentle curve of radius 120 deviates
 * from its chord by less than a unit over 25 units, so a short window would call part of every rounded corner a
 * line. Over [minLength] the same curve bows by a full unit and fails the bow test, which is what separates a
 * side from a stretch of curve that merely stays inside the tolerance.
 */
internal fun findLineRuns(
    points: List<Vec2>,
    spacing: Double,
    lineTolerance: Double,
    trimTolerance: Double,
    minLength: Double,
    bowTolerance: Double,
): List<IntRange> {
    val m = points.size
    val window = max(3, ceil(minLength / spacing).toInt())
    if (m <= window) return emptyList()
    val moments = PointMoments(points)

    fun passes(
        from: Int,
        to: Int,
    ): Boolean {
        val line = moments.fit(from, to)
        return moments.maxDistance(line, from, to) <= lineTolerance && abs(bowOf(points, from, to, line)) <= bowTolerance
    }

    val windowOk = BooleanArray(m - window + 1) { passes(it, it + window - 1) }
    val runs = mutableListOf<IntRange>()
    var i = 0
    while (i < windowOk.size) {
        if (!windowOk[i]) {
            i++
            continue
        }
        var last = i
        while (last + 1 < windowOk.size && windowOk[last + 1]) last++
        val floor = (runs.lastOrNull()?.last ?: -1) + 1
        val refined =
            refineRun(points, moments, i, last + window - 1, floor, trimTolerance)?.let {
                tangentTrim(points, moments, it, spacing, floor, trimTolerance)
            }
        if (refined != null &&
            (refined.last - refined.first) * spacing >= minLength * 0.75 &&
            moments.maxDistance(moments.fit(refined.first, refined.last), refined.first, refined.last) <= lineTolerance * 1.5 &&
            abs(bowOf(points, refined.first, refined.last, moments.fit(refined.first, refined.last))) <= bowTolerance
        ) {
            runs += refined
        }
        i = last + 1
    }
    return runs
}

/**
 * Moves each end of [run] back to where the curve beside it truly leaves the line.
 *
 * A curve departs a tangent line parabolically, `d = (s - s0)^2 / (2 R)` at distance `s` along the line past its
 * tangent point `s0`; the run's ends were placed where `d` first passed the trim tolerance, which for a corner of
 * radius 120 is a dozen units too far in. The distances of the next samples past the end are fitted as
 * `sqrt(d)` against `s` (a straight line for a parabola) and the end is moved back to its zero, so the line stops
 * at the tangent point and the curve beside it starts with the right tangent as well as the right position. An
 * end at the edge of [points], next to another run, or with no clear departure beyond it is left where it is.
 */
private fun tangentTrim(
    points: List<Vec2>,
    moments: PointMoments,
    run: IntRange,
    spacing: Double,
    floor: Int,
    trimTolerance: Double,
): IntRange {
    val line = moments.fit(run.first, run.last)
    var from = run.first
    var to = run.last
    val reach = max(6, (60.0 / spacing).toInt())
    // Past the end of the run, going forward, as many samples as there are (up to the reach).
    val after = min(reach, points.size - 1 - to)
    if (after >= 8) {
        val back = departureBackoff(points, line, to, 1, after, trimTolerance)
        if (back > 0) to = max(from + 4, to - ceil(back / spacing).toInt())
    }
    val before = min(reach, from - floor)
    if (before >= 8) {
        val back = departureBackoff(points, line, from, -1, before, trimTolerance)
        if (back > 0) from = min(to - 4, from + ceil(back / spacing).toInt())
    }
    return from..to
}

/** How far back along the line the tangent point lies from [end], or 0 when the samples past it show no clean parabolic departure. */
private fun departureBackoff(
    points: List<Vec2>,
    line: FittedLine,
    end: Int,
    step: Int,
    reach: Int,
    trimTolerance: Double,
): Double {
    val s = mutableListOf<Double>()
    val roots = mutableListOf<Double>()
    val origin = points[end]
    for (k in 1..reach) {
        val p = points[end + step * k]
        val d = line.distance(p)
        if (d < 3.0 * trimTolerance || d > 10.0) continue
        s += abs(line.direction.dot(p - origin))
        roots += sqrt(d)
    }
    if (s.size < 4) return 0.0
    val meanS = s.average()
    val meanR = roots.average()
    var numerator = 0.0
    var denominator = 0.0
    for (i in s.indices) {
        numerator += (s[i] - meanS) * (roots[i] - meanR)
        denominator += (s[i] - meanS) * (s[i] - meanS)
    }
    if (denominator <= 1e-9) return 0.0
    val slope = numerator / denominator
    if (slope <= 1e-3) return 0.0
    // sqrt(d) = slope * (s - s0'): its zero, measured from the end, is where the curve leaves the line.
    val zero = meanS - meanR / slope
    return if (zero < 0.0) min(-zero, 30.0) else 0.0
}

/** Trims [from]..[to] to the samples that follow their own line, then regrows it as far as [floor] and the data allow. */
private fun refineRun(
    points: List<Vec2>,
    moments: PointMoments,
    from: Int,
    to: Int,
    floor: Int,
    trimTolerance: Double,
): IntRange? {
    var a = max(from, floor)
    var b = to
    repeat(3) {
        if (b - a < 4) return null
        val line = moments.fit(a, b)
        while (a < b && line.distance(points[a]) > trimTolerance) a++
        while (b > a && line.distance(points[b]) > trimTolerance) b--
    }
    if (b - a < 4) return null
    val line = moments.fit(a, b)
    while (a - 1 >= floor && line.distance(points[a - 1]) <= trimTolerance) a--
    while (b + 1 < points.size && line.distance(points[b + 1]) <= trimTolerance) b++
    return a..b
}

/** The unit direction of travel from `points[at]` toward the first sample at least [distance] further along, in direction [step] (+1 forward, -1 backward). */
internal fun directionAlong(
    points: List<Vec2>,
    at: Int,
    step: Int,
    distance: Double,
): Vec2 {
    var travelled = 0.0
    var k = at
    while (k + step in points.indices && travelled < distance) {
        travelled += (points[k + step] - points[k]).length()
        k += step
    }
    val d = points[k] - points[at]
    val length = d.length()
    return if (length > 1e-9) d * (1.0 / length) else Vec2(1.0, 0.0)
}

/** Which sample indices of the open sequence [values] are local extrema that stand out by at least [prominence] on both sides. */
internal fun prominentExtrema(
    values: DoubleArray,
    prominence: Double,
    margin: Int,
): List<Int> {
    val n = values.size
    val found = mutableListOf<Int>()
    for (k in margin until n - margin) {
        val v = values[k]
        val isMax = values[k - 1] <= v && values[k + 1] < v
        val isMin = values[k - 1] >= v && values[k + 1] > v
        if (!isMax && !isMin) continue
        // Walk outward until the signal passes this value (a taller peak / deeper trough) or the piece ends.
        var leftExcursion = 0.0
        var a = k - 1
        while (a >= 0) {
            val d = if (isMax) v - values[a] else values[a] - v
            if (d < 0.0) break
            leftExcursion = max(leftExcursion, d)
            a--
        }
        var rightExcursion = 0.0
        var b = k + 1
        while (b < n) {
            val d = if (isMax) v - values[b] else values[b] - v
            if (d < 0.0) break
            rightExcursion = max(rightExcursion, d)
            b++
        }
        if (min(leftExcursion, rightExcursion) >= prominence) found += k
    }
    // A flat top produces a run of equal candidates; keep the middle one of each run.
    val merged = mutableListOf<Int>()
    var runStart = 0
    while (runStart < found.size) {
        var runEnd = runStart
        while (runEnd + 1 < found.size && found[runEnd + 1] == found[runEnd] + 1) runEnd++
        merged += found[(runStart + runEnd) / 2]
        runStart = runEnd + 1
    }
    return merged
}
