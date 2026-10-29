// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** [OutlineFitParameters.fitTolerance]'s default, in font units: 2 on a 1000-unit em, a fifth of a percent of the em. */
const val DEFAULT_OUTLINE_FIT_TOLERANCE: Double = 2.0

/** [OutlineFitParameters.cornerTurnDegrees]'s default: a turn of 32 degrees or more over the look distance is a corner. */
const val DEFAULT_OUTLINE_CORNER_TURN_DEGREES: Double = 32.0

/** [OutlineFitParameters.cornerLookUnits]'s default: corners are judged on chords 10 font units long either side. */
const val DEFAULT_OUTLINE_CORNER_LOOK_UNITS: Double = 10.0

/** [OutlineFitParameters.sampleSpacing]'s default: the traced boundary is re-sampled every 2 font units. */
const val DEFAULT_OUTLINE_SAMPLE_SPACING: Double = 2.0

/**
 * The knobs of the outline fitter: the three the Trace screen shows by default (corner detection, fit
 * tolerance, and, in the Snap stage, the snap band) are [cornerTurnDegrees], [fitTolerance] and
 * [TypeConstraintParameters.metricSnapDistance]; the rest are the fitter's own scale choices and are
 * derived from them unless set. One set applies to every glyph; nothing here is chosen per letter.
 */
data class OutlineFitParameters(
    /** The most a fitted outline may stray from the traced boundary, in font units. */
    val fitTolerance: Double = DEFAULT_OUTLINE_FIT_TOLERANCE,
    /** How sharp a turn has to be to count as a corner, in degrees, measured over [cornerLookUnits] either side. */
    val cornerTurnDegrees: Double = DEFAULT_OUTLINE_CORNER_TURN_DEGREES,
    /** How far either side of a point the corner test looks, in font units. A rounded corner whose radius is much larger than this is not a corner. */
    val cornerLookUnits: Double = DEFAULT_OUTLINE_CORNER_LOOK_UNITS,
    /** The even step the traced boundary is re-sampled at before anything is measured, in font units. */
    val sampleSpacing: Double = DEFAULT_OUTLINE_SAMPLE_SPACING,
    /** The shortest stretch, away from a corner, that may be called a straight line; 24 tolerances unless set. */
    val minLineLength: Double = 24.0 * fitTolerance,
) {
    init {
        require(fitTolerance > 0.0) { "fitTolerance must be positive, was $fitTolerance" }
        require(cornerTurnDegrees in 1.0..179.0) { "cornerTurnDegrees must be between 1 and 179, was $cornerTurnDegrees" }
        require(cornerLookUnits > 0.0 && sampleSpacing > 0.0 && minLineLength > 0.0) { "look, spacing and line length must be positive" }
    }

    /** How far a sample may sit from a line and the line still be straight. */
    internal val lineTolerance: Double get() = 0.6 * fitTolerance

    /** The tighter distance a line's two ends are trimmed and regrown against, so they stop where the curve beside them starts. */
    internal val trimTolerance: Double get() = 0.4 * fitTolerance

    /** How much a straight run may bow before it is called a curve: the systematic bulge of its middle over its ends. */
    internal val bowTolerance: Double get() = 0.4 * lineTolerance
}

/** A closed boundary re-sampled at even steps ([samples]) and the indices of its corners ([cornerIndices], ascending). */
class OutlineCorners(
    val samples: List<Vec2>,
    val cornerIndices: List<Int>,
)

/** One kind of piece of a fitted outline. */
enum class OutlinePieceKind { LINE, CURVE }

/**
 * A fitted closed outline: the [contour] (cubic; a straight side is the canonical line triple whose two
 * controls sit on its own ends, so it counts and is written as a plain line), how many pieces of each kind it
 * has, the corners it was cut at, and how far it strays from the boundary it was fitted to.
 */
data class OutlineFit(
    val contour: Contour,
    val lineCount: Int,
    val curveCount: Int,
    val cornerCount: Int,
    /** The largest distance from any sample of the traced boundary to the fitted outline, in font units. */
    val maxDeviation: Double,
)

/**
 * Finds the corners of a closed traced boundary. The boundary is re-sampled every [OutlineFitParameters.sampleSpacing]
 * units and lightly smoothed; at each sample the turn between the chord arriving from
 * [OutlineFitParameters.cornerLookUnits] behind and the chord leaving toward the same distance ahead is measured, and
 * a stretch of samples whose turn reaches [OutlineFitParameters.cornerTurnDegrees] is one corner, placed at its sharpest
 * sample. Judging by arc length rather than by neighbouring vertices is what keeps a raster's pixel staircase, and the
 * chamfer marching squares cuts off every right angle, from reading as several corners or none.
 */
fun detectOutlineCorners(
    polyline: List<Vec2>,
    params: OutlineFitParameters = OutlineFitParameters(),
): OutlineCorners {
    val samples = resampleClosed(polyline, params.sampleSpacing)
    val n = samples.size
    if (n < 8) return OutlineCorners(samples, emptyList())
    val turns = turnProfile(samples, params)
    val threshold = params.cornerTurnDegrees * PI / 180.0
    return OutlineCorners(samples, cornersFromTurns(turns, threshold))
}

private fun turnProfile(
    samples: List<Vec2>,
    params: OutlineFitParameters,
): DoubleArray {
    val n = samples.size
    val smooth = smoothClosed(samples, 2)
    val step = pathLength(samples + samples.first()) / n
    val k = max(1, (params.cornerLookUnits / step).roundToInt()).coerceAtMost(n / 4)
    return DoubleArray(n) { i -> turnAt(smooth[(i - k + n) % n], smooth[i], smooth[(i + k) % n]) }
}

private fun cornersFromTurns(
    turns: DoubleArray,
    threshold: Double,
): List<Int> {
    val n = turns.size
    val over = BooleanArray(n) { abs(turns[it]) >= threshold }
    if (over.none { it }) return emptyList()
    if (over.all { it }) return listOf(turns.indices.maxBy { abs(turns[it]) })
    // Start the scan on a sample that is not over the threshold, so no stretch wraps the start.
    val start = over.indices.first { !over[it] }
    val corners = mutableListOf<Int>()
    var offset = 1
    while (offset <= n) {
        val index = (start + offset) % n
        if (!over[index]) {
            offset++
            continue
        }
        val members = mutableListOf(index)
        var next = offset + 1
        while (next <= n && over[(start + next) % n] && (turns[(start + next) % n] > 0.0) == (turns[index] > 0.0)) {
            members += (start + next) % n
            next++
        }
        val peak = members.maxOf { abs(turns[it]) }
        val plateau = members.filter { abs(turns[it]) >= peak * 0.97 }
        corners += plateau[plateau.size / 2]
        offset = next
    }
    return corners.distinct().sorted()
}

/** The distance, in font units, a stretch may stray from its line and still be looked at when roughness is measured: wide enough to hold a wobbly side. */
private const val ROUGHNESS_SEARCH_TOLERANCE: Double = 4.0

/** The shortest stretch roughness is measured on, in font units. */
private const val ROUGHNESS_RUN_UNITS: Double = 60.0

/**
 * How rough a traced boundary's edge is: the wobble, in font units (the root mean square distance), of its nearly straight
 * stretches about the smooth curve through them. The boundary [samples] (a closed outline re-sampled every [spacing] units) is
 * searched for stretches at least 60 units long that stay within 4 units of one line and bow by under 2; each is fitted with a
 * parabola, which takes out any real curvature a stretch has, and what is left is wobble. The length-weighted median over the
 * stretches is returned, so a few that run into a curve do not move it. A boundary with no such stretch (a circle) reads 0: there
 * is nothing straight to judge the wobble against. A clean render reads a fraction of a unit; a handheld photograph of a pen line
 * reads one or more.
 */
fun measureEdgeRoughness(
    samples: List<Vec2>,
    spacing: Double = DEFAULT_OUTLINE_SAMPLE_SPACING,
): Double {
    val n = samples.size
    if (n < 16) return 0.0
    val doubled = samples + samples
    val runs =
        findLineRuns(
            doubled,
            spacing,
            ROUGHNESS_SEARCH_TOLERANCE,
            0.75 * ROUGHNESS_SEARCH_TOLERANCE,
            ROUGHNESS_RUN_UNITS,
            0.5 * ROUGHNESS_SEARCH_TOLERANCE,
        )
    val readings =
        runs.filter { it.first < n }.map {
            parabolaResidualRms(
                doubled,
                it.first,
                it.last,
            ) to (it.last - it.first + 1).toDouble()
        }
    if (readings.isEmpty()) return 0.0
    val sorted = readings.sortedBy { it.first }
    val half = sorted.sumOf { it.second } / 2.0
    var seen = 0.0
    for ((rms, weight) in sorted) {
        seen += weight
        if (seen >= half) return rms
    }
    return sorted.last().first
}

/** The outline's corners, fitted: [detectOutlineCorners] then [fitOutline]. */
fun fitClosedOutline(
    polyline: List<Vec2>,
    params: OutlineFitParameters = OutlineFitParameters(),
): OutlineFit = fitOutline(detectOutlineCorners(polyline, params), params)

private class Piece(
    val kind: OutlinePieceKind,
    var samples: List<Vec2>,
    var startCorner: Boolean,
    var endCorner: Boolean,
    var line: FittedLine? = null,
)

/**
 * Fits the traced boundary in [detected] to the fewest straight lines and cubic curves that hold it within
 * [OutlineFitParameters.fitTolerance].
 *
 * The boundary is cut at its corners into arcs. Each arc is first tested as a whole: if every sample lies within
 * the line tolerance of one straight line it is a single line, however short. Otherwise the arc is searched for
 * long straight runs ([findLineRuns]); what lies between them is curve, fitted by Schneider's least-squares
 * Bezier fit with the end tangents pinned: along the neighbouring line where a curve leaves a straight side
 * smoothly, exactly horizontal or vertical at every extremum of the curve, and read from the data at a corner.
 * A smooth boundary with no corner is cut cyclically in the same way, and one with no straight run at all (a
 * circle) is cut only at its extrema. Corners between two lines are re-placed at the lines' intersection, which
 * undoes the small chamfer a raster leaves on every right angle.
 *
 * A straight piece comes out as the canonical line triple (both controls on the piece's own ends), the form
 * `GlifCodec` writes as a plain line, so the node counts are the ones the file will hold.
 */
fun fitOutline(
    detected: OutlineCorners,
    params: OutlineFitParameters = OutlineFitParameters(),
): OutlineFit {
    val samples = detected.samples
    val n = samples.size
    require(n >= 3) { "an outline needs at least 3 samples, had $n" }
    val pieces = buildPieces(detected, params)
    val segments = fitPieces(pieces, params)
    val contour = buildCubicContour(segments.map { it.segment })
    val smoothed = markDesignedSmooth(contour, segments.map { it.smoothAtStart })
    return OutlineFit(
        contour = smoothed,
        lineCount = pieces.count { it.kind == OutlinePieceKind.LINE },
        curveCount = segments.count { it.fromCurve },
        cornerCount = detected.cornerIndices.size,
        maxDeviation = maxDeviationFrom(samples, smoothed),
    )
}

private class FittedSegment(
    val segment: CurveSegment.Cubic,
    val fromCurve: Boolean,
    /** Whether the node this segment starts at was joined smoothly by design (so the file may say so). */
    val smoothAtStart: Boolean,
)

// ---- pieces ---------------------------------------------------------------------------------

private fun buildPieces(
    detected: OutlineCorners,
    params: OutlineFitParameters,
): List<Piece> {
    val samples = detected.samples
    val n = samples.size
    val corners = detected.cornerIndices
    if (corners.isNotEmpty()) {
        val pieces = mutableListOf<Piece>()
        for (c in corners.indices) {
            val from = corners[c]
            val to = corners[(c + 1) % corners.size]
            val length = if (corners.size == 1) n else (to - from + n) % n
            val arc = List(length + 1) { samples[(from + it) % n] }
            val withLines = piecesOfArc(arc, params, startCorner = true, endCorner = true)
            pieces += cheaper(withLines, listOf(Piece(OutlinePieceKind.CURVE, arc, true, true)), params)
        }
        return pieces
    }
    val withLines = smoothLoopPieces(samples, params)
    // The loop as one closed curve, cut at its topmost sample where the tangent is horizontal.
    val top = samples.indices.maxBy { samples[it].y }
    val closedCurve = listOf(Piece(OutlinePieceKind.CURVE, List(n + 1) { samples[(top + it) % n] }, false, false))
    return if (withLines.none { it.kind == OutlinePieceKind.LINE }) closedCurve else cheaper(withLines, closedCurve, params)
}

/**
 * The cheaper of two ways to draw the same stretch of boundary, by nodes: a straight piece costs one node, a
 * curve three (its node and two handles). The straight-sides-and-curves reading wins unless the plain curve is
 * cheaper by more than two nodes, since it is the one that says more about the shape. This is what stops a large circle, whose every 50-unit stretch is straight to
 * within tolerance, from being cut into dozens of short lines: four curves are cheaper.
 */
private fun cheaper(
    withLines: List<Piece>,
    pureCurve: List<Piece>,
    params: OutlineFitParameters,
): List<Piece> {
    if (withLines.none { it.kind == OutlinePieceKind.LINE }) return withLines
    return if (nodeCost(withLines, params) <= nodeCost(pureCurve, params) + 2) withLines else pureCurve
}

private fun nodeCost(
    pieces: List<Piece>,
    params: OutlineFitParameters,
): Int = fitPieces(pieces, params).sumOf { if (it.fromCurve) 3 else 1 }

/**
 * A boundary with no corner: cut it cyclically at the middle of its longest stretch of curve, so every straight
 * side keeps the curve on both sides of it for the tangent trim to read. The straight sides are found first on the
 * boundary written twice round, which gives every one of them full context whatever the sample order started at.
 */
private fun smoothLoopPieces(
    samples: List<Vec2>,
    params: OutlineFitParameters,
): List<Piece> {
    val n = samples.size
    val start = cutForSmoothLoop(samples, params)
    val rotated = List(n + 1) { samples[(start + it) % n] }
    val pieces = piecesOfArc(rotated, params, startCorner = false, endCorner = false).toMutableList()
    if (pieces.size == 1 && pieces[0].kind == OutlinePieceKind.CURVE) return pieces
    if (pieces.size > 1) {
        val first = pieces.first()
        val last = pieces.last()
        if (first.kind == last.kind) {
            val merged = last.samples + first.samples.drop(1)
            val mergedPiece =
                if (first.kind == OutlinePieceKind.LINE) {
                    val fit = PointMoments(merged).fit(0, merged.size - 1)
                    Piece(OutlinePieceKind.LINE, merged, false, false, fit)
                } else {
                    Piece(OutlinePieceKind.CURVE, merged, false, false)
                }
            pieces.removeAt(pieces.size - 1)
            pieces[0] = mergedPiece
        }
    }
    return pieces
}

private fun cutForSmoothLoop(
    samples: List<Vec2>,
    params: OutlineFitParameters,
): Int {
    val n = samples.size
    val doubled = samples + samples
    val runs =
        findLineRuns(doubled, params.sampleSpacing, params.lineTolerance, params.trimTolerance, params.minLineLength, params.bowTolerance)
    var bestGap = 0
    var bestMiddle = -1
    for (k in 0 until runs.size - 1) {
        val before = runs[k]
        val after = runs[k + 1]
        if (before.first < 2 || before.last >= n + n / 2 || before.last < n / 4) continue
        val gap = after.first - before.last
        if (gap > bestGap) {
            bestGap = gap
            bestMiddle = (before.last + after.first) / 2
        }
    }
    if (bestMiddle >= 0) return bestMiddle % n
    val turns = turnProfile(samples, params)
    return turns.indices.maxBy { abs(turns[it]) }
}

private fun piecesOfArc(
    arc: List<Vec2>,
    params: OutlineFitParameters,
    startCorner: Boolean,
    endCorner: Boolean,
): List<Piece> {
    val m = arc.size
    if (m <= 2) return listOf(Piece(OutlinePieceKind.LINE, arc, startCorner, endCorner, lineThrough(arc)))
    val moments = PointMoments(arc)
    // A whole arc between two corners is a line if its samples are straight once the few next to each corner are
    // set aside: those are the chamfer a raster cuts off the corner, and the corner is re-placed from the lines.
    val margin = if (startCorner || endCorner) ceil(0.5 * params.cornerLookUnits / params.sampleSpacing).toInt() else 0
    val from = if (startCorner) margin else 0
    val to = if (endCorner) m - 1 - margin else m - 1
    if ((arc.first() - arc.last()).length() > params.sampleSpacing && to - from >= 3) {
        val core = moments.fit(from, to)
        if (moments.maxDistance(core, from, to) <= params.lineTolerance && abs(bowOf(arc, from, to, core)) <= params.bowTolerance) {
            return listOf(Piece(OutlinePieceKind.LINE, arc, startCorner, endCorner, core))
        }
    }
    val runs =
        findLineRuns(arc, params.sampleSpacing, params.lineTolerance, params.trimTolerance, params.minLineLength, params.bowTolerance)
    if (runs.isEmpty()) return listOf(Piece(OutlinePieceKind.CURVE, arc, startCorner, endCorner))

    // A gap this short between a run and the next feature is a rounding of the join, not a curve of its own; beside a
    // corner it is the raster's chamfer, which is wider.
    val absorb = ceil(max(3.0 * params.sampleSpacing, 3.0 * params.fitTolerance) / params.sampleSpacing).toInt()
    val cornerAbsorb = max(absorb, ceil(params.cornerLookUnits / params.sampleSpacing).toInt())
    val pieces = mutableListOf<Piece>()
    var cursor = 0
    var lastLineFrom = -1
    for ((index, run) in runs.withIndex()) {
        var from = run.first
        val to = run.last
        if (from - cursor <= (if (cursor == 0 && startCorner) cornerAbsorb else absorb)) {
            from = cursor
        } else {
            pieces += Piece(OutlinePieceKind.CURVE, arc.subList(cursor, from + 1), cursor == 0 && startCorner, false)
        }
        val previous = pieces.lastOrNull()
        val merged =
            if (from == cursor && previous != null && previous.kind == OutlinePieceKind.LINE && lastLineFrom >= 0) {
                // Two runs with only a rounding of noise between them are one side: join them if one line holds them both.
                val union = moments.fit(lastLineFrom, to)
                if (moments.maxDistance(union, lastLineFrom, to) <= params.lineTolerance * 1.5 &&
                    abs(bowOf(arc, lastLineFrom, to, union)) <= params.bowTolerance * 1.5
                ) {
                    pieces.removeAt(pieces.size - 1)
                    pieces += Piece(OutlinePieceKind.LINE, arc.subList(lastLineFrom, to + 1), previous.startCorner, false, union)
                    true
                } else {
                    false
                }
            } else {
                false
            }
        if (!merged) {
            pieces +=
                Piece(OutlinePieceKind.LINE, arc.subList(from, to + 1), from == 0 && startCorner, false, moments.fit(run.first, run.last))
            lastLineFrom = from
        }
        cursor = to
        if (index == runs.size - 1) {
            if (m - 1 - cursor <= (if (endCorner) cornerAbsorb else absorb)) {
                val last = pieces.removeAt(pieces.size - 1)
                pieces +=
                    Piece(OutlinePieceKind.LINE, arc.subList(if (merged) lastLineFrom else from, m), last.startCorner, false, last.line)
            } else {
                pieces += Piece(OutlinePieceKind.CURVE, arc.subList(cursor, m), false, false)
            }
        }
    }
    pieces.first().startCorner = startCorner
    pieces.last().endCorner = endCorner
    return pieces
}

private fun lineThrough(points: List<Vec2>): FittedLine {
    val d = points.last() - points.first()
    val length = d.length()
    return FittedLine(points.first(), if (length > 1e-9) d * (1.0 / length) else Vec2(1.0, 0.0))
}

// ---- fitting the pieces ---------------------------------------------------------------------

private fun fitPieces(
    pieces: List<Piece>,
    params: OutlineFitParameters,
): List<FittedSegment> {
    val k = pieces.size
    val nodes =
        List(k) { i ->
            val previous = pieces[(i - 1 + k) % k]
            val current = pieces[i]
            junction(previous, current, current.samples.first(), params)
        }
    val out = mutableListOf<FittedSegment>()
    for (i in 0 until k) {
        val piece = pieces[i]
        val start = nodes[i]
        val end = nodes[(i + 1) % k]
        val smoothStart = !piece.startCorner
        when (piece.kind) {
            OutlinePieceKind.LINE -> {
                out += FittedSegment(CurveSegment.Cubic(start, start, end, end), fromCurve = false, smoothAtStart = smoothStart)
            }

            OutlinePieceKind.CURVE -> {
                val previous = pieces[(i - 1 + k) % k]
                val next = pieces[(i + 1) % k]
                out += fitCurvePiece(piece, previous, next, start, end, params, smoothStart)
            }
        }
    }
    return out
}

/** Where the node between [previous] and [current] goes. */
private fun junction(
    previous: Piece,
    current: Piece,
    sample: Vec2,
    params: OutlineFitParameters,
): Vec2 {
    val a = previous.line
    val b = current.line
    if (previous.kind == OutlinePieceKind.LINE && current.kind == OutlinePieceKind.LINE && a != null && b != null) {
        val cross = a.direction.cross(b.direction)
        if (abs(cross) > sin(5.0 * PI / 180.0)) {
            // The two lines' intersection: undoes the chamfer a raster cuts off every sharp corner.
            val t = (b.origin - a.origin).cross(b.direction) / cross
            val hit = a.origin + a.direction * t
            val reach = max(3.0 * params.cornerLookUnits, 4.0 * params.fitTolerance)
            if ((hit - sample).length() <= reach) return hit
        }
        return midpoint(a.project(sample), b.project(sample))
    }
    if (previous.kind == OutlinePieceKind.LINE && a != null) return a.project(sample)
    if (current.kind == OutlinePieceKind.LINE && b != null) return b.project(sample)
    return sample
}

private fun fitCurvePiece(
    piece: Piece,
    previous: Piece,
    next: Piece,
    start: Vec2,
    end: Vec2,
    params: OutlineFitParameters,
    smoothStart: Boolean,
): List<FittedSegment> {
    val raw = piece.samples
    val pts =
        raw.toMutableList().also {
            it[0] = start
            it[it.size - 1] = end
        }
    val length = pathLength(pts)
    val look = (0.1 * length).coerceIn(4.0, 12.0)
    val closedLoop = previous === piece && !piece.startCorner
    val startTangent =
        when {
            closedLoop -> Vec2(if ((pts[1] - pts[0]).x >= 0.0) 1.0 else -1.0, 0.0)
            !piece.startCorner && previous.kind == OutlinePieceKind.LINE && previous.line != null -> previous.line!!.direction
            else -> directionAlong(pts, 0, 1, look)
        }
    val endTangent =
        when {
            closedLoop -> startTangent * -1.0
            !piece.endCorner && next.kind == OutlinePieceKind.LINE && next.line != null -> next.line!!.direction * -1.0
            else -> directionAlong(pts, pts.size - 1, -1, look)
        }
    // Cut at the curve's own extrema so each one becomes a node with an exactly horizontal or vertical tangent.
    val smooth = smoothOpen(pts, 2)
    val prominence = max(0.5 * params.fitTolerance, 0.5)
    val margin = max(2, ceil(2.0 * params.fitTolerance / params.sampleSpacing).toInt())
    val xs = prominentExtrema(DoubleArray(smooth.size) { smooth[it].x }, prominence, margin)
    val ys = prominentExtrema(DoubleArray(smooth.size) { smooth[it].y }, prominence, margin)
    val cuts = (xs.map { it to true } + ys.map { it to false }).sortedBy { it.first }
    val bounds = mutableListOf(0)
    val cutTangents = mutableMapOf<Int, Vec2>()
    for ((index, isXExtremum) in cuts) {
        if (index - bounds.last() < margin || pts.size - 1 - index < margin) continue
        val travel = pts[min(index + 1, pts.size - 1)] - pts[max(index - 1, 0)]
        val tangent =
            if (isXExtremum) Vec2(0.0, if (travel.y >= 0.0) 1.0 else -1.0) else Vec2(if (travel.x >= 0.0) 1.0 else -1.0, 0.0)
        bounds += index
        cutTangents[index] = tangent
    }
    bounds += pts.size - 1
    val result = mutableListOf<FittedSegment>()
    for (b in 0 until bounds.size - 1) {
        val from = bounds[b]
        val to = bounds[b + 1]
        val t1 = cutTangents[from] ?: startTangent
        val t2 = cutTangents[to]?.let { it * -1.0 } ?: endTangent
        val interval = pts.subList(from, to + 1)
        val cubics =
            if (interval.size == 2) {
                listOf(
                    CurveSegment.Cubic(
                        interval[0],
                        interval[0] + t1 * ((interval[1] - interval[0]).length() / 3.0),
                        interval[1] + t2 * ((interval[1] - interval[0]).length() / 3.0),
                        interval[1],
                    ),
                )
            } else {
                fitOpenArc(interval, t1, t2, params.fitTolerance, 12)
            }
        for ((c, cubic) in cubics.withIndex()) {
            result += FittedSegment(cubic, fromCurve = true, smoothAtStart = if (b == 0 && c == 0) smoothStart else true)
        }
    }
    return result
}

/** The contour with [ContourPoint.smooth] set on every node the fitter joined smoothly, wherever the rounded outline still holds that. */
private fun markDesignedSmooth(
    contour: Contour,
    smoothAtStart: List<Boolean>,
): Contour {
    val points = contour.points.toMutableList()
    var changed = false
    for (i in smoothAtStart.indices) {
        if (!smoothAtStart[i]) continue
        if (!contour.smoothHolds(3 * i)) continue
        points[3 * i] = points[3 * i].copy(smooth = true)
        changed = true
    }
    return if (changed) Contour(points, contour.format) else contour
}

private fun maxDeviationFrom(
    samples: List<Vec2>,
    contour: Contour,
): Double {
    val dense = mutableListOf<Vec2>()
    for (segment in contour.segments()) {
        val steps = if (segment is CurveSegment.Line) 1 else 32
        for (s in 0 until steps) dense += segment.pointAt(s.toDouble() / steps)
    }
    var worst = 0.0
    for (p in samples) {
        var best = Double.MAX_VALUE
        for (i in dense.indices) {
            val a = dense[i]
            val b = dense[(i + 1) % dense.size]
            val ab = b - a
            val len2 = ab.dot(ab)
            val t = if (len2 > 1e-12) ((p - a).dot(ab) / len2).coerceIn(0.0, 1.0) else 0.0
            val d = (a + ab * t - p).length()
            if (d < best) best = d
        }
        worst = max(worst, best)
    }
    return worst
}

// ---- snapping straight sides onto the axes --------------------------------------------------

/** [snapLinesToAxes]'s default: a straight side within one and a half degrees of horizontal or vertical is made exactly so. */
const val DEFAULT_LINE_AXIS_SNAP_DEGREES: Double = 1.5

/**
 * Makes every straight side of [contour] that lies within [thresholdDegrees] of horizontal or vertical exactly
 * horizontal or vertical, by moving both of its nodes onto their mean coordinate. A node carries the control
 * points beside it with it, so a curve leaving the side keeps its shape and a neighbouring straight side stays a
 * plain line. Only the contour's straight sides move; a curve is left to [applyTypeConstraints].
 */
fun snapLinesToAxes(
    contour: Contour,
    thresholdDegrees: Double = DEFAULT_LINE_AXIS_SNAP_DEGREES,
): Contour {
    require(contour.format == CurveFormat.CUBIC) { "snapLinesToAxes works on a cubic contour" }
    val n = contour.points.size
    val segments = n / 3
    val xs = IntArray(n) { contour.points[it].point.x }
    val ys = IntArray(n) { contour.points[it].point.y }
    val threshold = thresholdDegrees * PI / 180.0

    fun isLine(i: Int): Boolean {
        val start = contour.points[3 * i].point
        val end = contour.points[(3 * i + 3) % n].point
        return contour.points[3 * i + 1].point == start && contour.points[3 * i + 2].point == end
    }

    fun move(
        node: Int,
        dx: Int,
        dy: Int,
    ) {
        for (index in listOf((node - 1 + n) % n, node, (node + 1) % n)) {
            xs[index] += dx
            ys[index] += dy
        }
    }
    for (i in 0 until segments) {
        if (!isLine(i)) continue
        val a = 3 * i
        val b = (3 * i + 3) % n
        val dx = xs[b] - xs[a]
        val dy = ys[b] - ys[a]
        if (dx == 0 && dy == 0) continue
        val angle = atan2(abs(dy).toDouble(), abs(dx).toDouble())
        when {
            angle <= threshold && dy != 0 -> {
                val target = floorHalf(ys[a], ys[b])
                move(a, 0, target - ys[a])
                move(b, 0, target - ys[b])
            }

            PI / 2.0 - angle <= threshold && dx != 0 -> {
                val target = floorHalf(xs[a], xs[b])
                move(a, target - xs[a], 0)
                move(b, target - xs[b], 0)
            }
        }
    }
    val moved =
        contour.points.indices.map { i ->
            contour.points[i].copy(point = Point(xs[i], ys[i]))
        }
    return Contour(moved, contour.format).withSmoothKept(contour)
}

private fun floorHalf(
    a: Int,
    b: Int,
): Int = kotlin.math.floor((a + b) / 2.0 + 0.5).toInt()

/**
 * One traced and fitted boundary put through the Snap stage: straight sides made axis-aligned ([snapLinesToAxes]), handles within
 * the axis band made exactly horizontal or vertical, extremes given nodes ([insertExtremaOnCurvePoints]), nodes within the metric
 * band moved onto the metric lines ([snapNodesToMetricLines]), and handles snapped again for the nodes just made.
 */
fun snapOutline(
    contour: Contour,
    metricLines: List<Int> = emptyList(),
    params: TypeConstraintParameters = TypeConstraintParameters(),
    axisSnapDegrees: Double = DEFAULT_LINE_AXIS_SNAP_DEGREES,
): Contour {
    val tangentDegrees = max(params.tangentSnapDegrees, axisSnapDegrees)
    val straightened = snapLinesToAxes(contour, axisSnapDegrees)
    // Handles first, so a curve leaving a snapped side starts exactly along it and cannot overshoot into a spurious extreme.
    val aligned = snapNearAxisTangents(straightened, tangentDegrees)
    val withExtrema = insertExtremaOnCurvePoints(aligned, params.minimumExtremumBulgeUnits)
    val onMetrics = snapNodesToMetricLines(withExtrema, metricLines, params.metricSnapDistance)
    return snapNearAxisTangents(onMetrics, tangentDegrees)
}

/**
 * Moves each on-curve node within [snapDistance] units of a line in [metricLines] onto it, carrying the control points
 * beside the node along so a straight side stays a plain line and a curve keeps its shape. The decision is the same as
 * [applyTypeConstraints]' (a node at the top of a round shape keeps its overshoot, a flat run or a sharp corner snaps);
 * only the handling of the handles differs.
 */
internal fun snapNodesToMetricLines(
    contour: Contour,
    metricLines: List<Int>,
    snapDistance: Int,
): Contour {
    if (metricLines.isEmpty() || snapDistance <= 0) return contour
    val points = contour.points
    val n = points.size
    val anchors = (0 until n / 3).map { 3 * it }
    val ys = anchors.map { points[it].point.y }
    val segments = contour.cubicSegments()
    val shifted = IntArray(n)
    for ((segmentIndex, anchorIndex) in anchors.withIndex()) {
        val y = ys[segmentIndex]
        val line = metricLines.minBy { abs(y - it) }
        val distance = abs(y - line)
        if (distance == 0 || distance > snapDistance) continue
        val flat = ys.count { it == y } >= 2
        val previous = segments[(segmentIndex - 1 + segments.size) % segments.size]
        val next = segments[segmentIndex]
        val roundPeak = !flat && (!previous.isEffectivelyStraight() || !next.isEffectivelyStraight())
        if (roundPeak) continue
        for (index in listOf((anchorIndex - 1 + n) % n, anchorIndex, (anchorIndex + 1) % n)) shifted[index] += line - y
    }
    if (shifted.all { it == 0 }) return contour
    val moved = points.indices.map { points[it].copy(point = Point(points[it].point.x, points[it].point.y + shifted[it])) }
    return Contour(moved, contour.format).withSmoothKept(contour)
}

/** The straight sides of [contour]: how many cubic segments have both controls on their own ends. */
fun Contour.lineSegmentCount(): Int {
    if (format != CurveFormat.CUBIC) return 0
    val n = points.size
    var lines = 0
    for (i in 0 until n / 3) {
        if (points[3 * i + 1].point == points[3 * i].point && points[3 * i + 2].point == points[(3 * i + 3) % n].point) lines++
    }
    return lines
}

/** The length of the longest straight side of [contour], in font units; 0 when it has none. */
fun Contour.longestLine(): Double {
    if (format != CurveFormat.CUBIC) return 0.0
    val n = points.size
    var best = 0.0
    for (i in 0 until n / 3) {
        val start = points[3 * i].point
        val end = points[(3 * i + 3) % n].point
        if (points[3 * i + 1].point == start && points[3 * i + 2].point == end) {
            best = max(best, sqrt(((end.x - start.x).toDouble() * (end.x - start.x) + (end.y - start.y).toDouble() * (end.y - start.y))))
        }
    }
    return best
}
