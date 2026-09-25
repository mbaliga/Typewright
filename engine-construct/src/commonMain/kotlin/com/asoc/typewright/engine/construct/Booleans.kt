// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.construct

import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.ContourPoint
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.CurveSegment
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.core.geometry.segments
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Curve-restoring booleans on closed [CurveFormat.CUBIC] contours (task P5a-hard item 1;
 * docs/ARCHITECTURE_REVIEW.md section 3 `:engine-construct` risk 2, section 5 item 49; brief
 * section 2 principle 2; docs/KNOWLEDGE.md A1). Public entry points: [booleanOp] (general,
 * multi-contour, any [BooleanOp]) and the four single-contour convenience wrappers [union],
 * [subtract], [intersect], [exclude].
 *
 * **Method, in order** (this file's own four sections below):
 * 1. Flatten every contour to a dense polyline, same as [offsetContour]'s own [CurveSegment.Cubic.flatten]
 *    reused verbatim, but *with* a breadcrumb per vertex (which original segment, and at what
 *    parameter `t` — [flattenWithParameter], [flattenRingWithProvenance]) so the boundary can be
 *    traced back to its source after clipping.
 * 2. Find every crossing between a subject edge and a clip edge (transversal, or collinear
 *    overlap — [intersectSegments]) and split both edges there ([splitEdgesAtIntersections]),
 *    building a planar arrangement of the two flattened polygons together.
 * 3. Classify every arrangement edge by sampling a point just off each side of it and asking
 *    which side is "inside the requested op's result" (the standard nonzero-winding-number test,
 *    [windingNumber]/[totalWinding]) — an edge whose two sides disagree is a boundary edge of the
 *    result and is kept, oriented so the result's interior is on its left (this app's own
 *    CCW-outward/CW-inward convention); an edge whose two sides agree is interior or exterior to
 *    the result and is dropped ([classifyEdges]). The kept, directed edges are then traced into
 *    closed loops by simple vertex-to-vertex adjacency ([traceLoops]).
 * 4. **Curve restoration**, the step that actually prevents a boolean from reintroducing the
 *    1,763-point-T problem this whole build exists to fix: each traced loop is walked and
 *    consecutive edges that came from the *same original cubic segment* are merged back into one
 *    run ([mergeIntoRuns]); a run covering a whole original segment is emitted **verbatim** (the
 *    exact original control points, not refit); a run covering only part of one is extracted as
 *    an **exact** de Casteljau sub-arc ([subArc], built only from the existing, already-tested
 *    [CurveSegment.Cubic.subdivide]) — so away from where the two shapes actually cross, the
 *    output boundary is byte-identical to the input geometry, and only the handful of segments
 *    actually touching a crossing point are ever newly computed.
 *
 * **On "Vatti's algorithm or an equivalent scan-line/sweep approach" (this task's own
 * instruction).** This is deliberately *not* a literal implementation of Vatti's own sweep-line
 * active-edge-table bookkeeping (Vatti 1992) — that data structure is large and easy to get
 * subtly wrong from a description alone with no primary source to check against (the risk this
 * task's own instructions raise about a from-scratch reconstruction — see
 * `HobbySpline.kt`'s own precedent for the same honesty call on `mp_make_choices`). What is
 * implemented instead is a **general, correct, planar-arrangement-and-winding-number** boolean
 * (build the full overlay of both polygons' edges, classify every resulting edge by the standard
 * nonzero-winding-number rule for the requested combination, trace the kept edges into closed
 * loops) — the same *mathematical* operation every scan-line boolean (Vatti's, Greiner–Hormann's,
 * Martinez–Rueda's) computes, described in exactly this form in the computational-geometry
 * literature on Boolean operations via map overlay (e.g. de Berg et al., *Computational
 * Geometry*, the "overlay of two subdivisions" chapter). The trade-off, honestly: this is
 * `O(n*m)` in the two inputs' edge counts (a brute-force pairwise intersection search, not a
 * sweep line's `O((n+m) log(n+m) + k)`), which is a deliberate simplicity-over-asymptotic-
 * performance choice given a flattened letterform contour is realistically tens to a few hundred
 * points at this module's own flatten tolerances, not thousands. It is **not** a port of
 * Clipper2 or any other existing implementation (architecture review item 49's own warning) —
 * every function below is this module's own code against the general algorithm structure above.
 *
 * **Scope, stated plainly (the honesty rule this task names).** [union] is the operation this
 * task asks to be made solid first, and is the one exercised by every fixture in
 * `BooleansTest.kt` with a genuine, non-trivial intersection (two overlapping circles) plus the
 * disjoint/nested/self-union degenerate cases. [subtract], [intersect] and [exclude] share
 * *exactly* the same machinery (only [insideResult]'s per-op rule differs) and are verified
 * against the same two-overlapping-circles fixture, checked against the closed-form circle-circle
 * intersection-area formula — see `BooleansTest.kt`'s own KDoc for the actual measured numbers,
 * reported honestly rather than asserted. Two structural assumptions, stated once rather than
 * re-derived per function, matching every general polygon-clipping algorithm's own standard input
 * contract (Vatti 1992; Greiner–Hormann 1998): each side ([subject], [clip]) is a set of *simple*
 * (non-self-intersecting) contours, and this file does not resolve intersections *within* one
 * side (a subject with two of its own rings overlapping each other is not this file's job to
 * flatten first). A configuration this file's own tracer cannot close into simple loops (an
 * exact tangency, or any other degenerate touching this task's own effort did not reach) fails
 * loudly with [IllegalStateException] rather than returning silently-wrong geometry — the
 * "honesty rule" applied to failure mode, not only to the final report.
 */
enum class BooleanOp { UNION, SUBTRACT, INTERSECT, EXCLUDE }

/**
 * Tunable parameters for [booleanOp], in the same fixed-global-default spirit as every other
 * parameter type in this module. There is no fit-tolerance parameter here (unlike [OffsetParameters]
 * or [StrokeParameters]): section 4's curve restoration is exact (verbatim originals or an exact
 * de Casteljau sub-arc), never a refit, so there is nothing for one to tune.
 */
data class BooleanParameters(
    /**
     * Passed to [flattenWithParameter]; see [DEFAULT_FLATTEN_TOLERANCE]'s own KDoc (reused
     * unchanged here — flattening for a boolean has no different accuracy need than flattening
     * for an offset).
     */
    val flattenTolerance: Double = DEFAULT_FLATTEN_TOLERANCE,
)

/** [union] of [a] and [b] — see [booleanOp]. */
fun union(
    a: Contour,
    b: Contour,
    params: BooleanParameters = BooleanParameters(),
): List<Contour> = booleanOp(listOf(a), listOf(b), BooleanOp.UNION, params)

/** [subtract] [b] from [a] (`a - b`) — see [booleanOp]. */
fun subtract(
    a: Contour,
    b: Contour,
    params: BooleanParameters = BooleanParameters(),
): List<Contour> = booleanOp(listOf(a), listOf(b), BooleanOp.SUBTRACT, params)

/** [intersect] of [a] and [b] — see [booleanOp]. */
fun intersect(
    a: Contour,
    b: Contour,
    params: BooleanParameters = BooleanParameters(),
): List<Contour> = booleanOp(listOf(a), listOf(b), BooleanOp.INTERSECT, params)

/** [exclude] (symmetric difference / XOR) of [a] and [b] — see [booleanOp]. */
fun exclude(
    a: Contour,
    b: Contour,
    params: BooleanParameters = BooleanParameters(),
): List<Contour> = booleanOp(listOf(a), listOf(b), BooleanOp.EXCLUDE, params)

/**
 * The general entry point: [op] of every contour in [subject] against every contour in [clip],
 * each side read with the nonzero-winding fill rule (so a subject with its own outer-plus-inner
 * rings, like `o`'s counter, already means what it looks like). See this file's own module KDoc
 * for the full method and its stated scope. Returns the result's contours in no particular order,
 * each already correctly wound (CCW outer, CW inner, CLAUDE.md) by construction of step 3 — never
 * post-corrected.
 */
fun booleanOp(
    subject: List<Contour>,
    clip: List<Contour>,
    op: BooleanOp,
    params: BooleanParameters = BooleanParameters(),
): List<Contour> {
    require(subject.isNotEmpty()) { "booleanOp's subject must have at least one contour" }
    require(clip.isNotEmpty()) { "booleanOp's clip must have at least one contour" }
    for (c in subject + clip) {
        require(c.format == CurveFormat.CUBIC) { "Booleans.kt only supports CurveFormat.CUBIC contours, got ${c.format}" }
    }

    val subjectVerts = subject.mapIndexed { i, c -> flattenRingWithProvenance(c, Side.SUBJECT, i, params.flattenTolerance) }
    val clipVerts = clip.mapIndexed { i, c -> flattenRingWithProvenance(c, Side.CLIP, i, params.flattenTolerance) }

    val subjectEdgesRaw = subjectVerts.flatMap { ringEdges(it) }
    val clipEdgesRaw = clipVerts.flatMap { ringEdges(it) }
    val (subjectEdges, clipEdges) = splitEdgesAtIntersections(subjectEdgesRaw, clipEdgesRaw)

    val subjectPolys = subjectVerts.map { ring -> ring.map { it.point } }
    val clipPolys = clipVerts.map { ring -> ring.map { it.point } }

    val kept = classifyEdges(subjectEdges + clipEdges, subjectPolys, clipPolys, op)
    val loops = traceLoops(kept)

    return loops.map { loopEdges ->
        val runs = mergeIntoRuns(loopEdges)
        val segments = runs.map { runToCubic(it, subject, clip) }
        buildResultContour(segments)
    }
}

// =================================================================================================
// 1. Flattening with provenance: which original cubic segment (and parameter t) each vertex is on.
// =================================================================================================

internal enum class Side { SUBJECT, CLIP }

/**
 * Which original cubic segment (by index into that contour's own [Contour.segments]) and
 * parameter `t` in `[0,1]` a boolean-arrangement vertex sits on, plus which side and which ring
 * (index into [subject] or [clip]) it came from — the breadcrumb curve restoration (section 4)
 * reads back.
 */
internal data class Provenance(
    val side: Side,
    val ringIndex: Int,
    val segmentIndex: Int,
    val t: Double,
)

internal data class PVertex(
    val point: Vec2,
    val provenance: Provenance,
)

/**
 * [CurveSegment.Cubic.flatten]'s own recursive-bisection flattener, but also returning each
 * emitted point's exact parameter `t` along *this* segment (`t=0` at [CurveSegment.Cubic.start],
 * `t=1` at [CurveSegment.Cubic.end]) — reusing the same flatness test ([isFlatEnough]) and the
 * same exact [CurveSegment.Cubic.subdivide] this module already has, tracking the `[tStart, tEnd]`
 * range each half of a split covers.
 */
internal fun CurveSegment.Cubic.flattenWithParameter(
    tolerance: Double,
    tStart: Double = 0.0,
    tEnd: Double = 1.0,
    depth: Int = 0,
): List<Pair<Vec2, Double>> {
    if (depth >= MAX_FLATTEN_DEPTH || isFlatEnough(tolerance)) return listOf(start to tStart, end to tEnd)
    val mid = (tStart + tEnd) / 2.0
    val (left, right) = subdivide(0.5)
    return left.flattenWithParameter(tolerance, tStart, mid, depth + 1) +
        right.flattenWithParameter(tolerance, mid, tEnd, depth + 1).drop(1)
}

/**
 * [contour] flattened to a provenance-tagged, cyclic vertex list: every segment contributes its
 * own flattened points *except its own trailing (`t=1`) point* (the next segment — or, for the
 * last segment, the first segment, wrapping — supplies that exact same coordinate as *its own*
 * `t=0` point instead), so every vertex has one unambiguous `(segmentIndex, t)` breadcrumb and no
 * coordinate is emitted twice. [ringIndex] is this contour's own index within [subject]/[clip].
 */
internal fun flattenRingWithProvenance(
    contour: Contour,
    side: Side,
    ringIndex: Int,
    tolerance: Double,
): List<PVertex> {
    val segs = contour.segments()
    val result = mutableListOf<PVertex>()
    for ((segmentIndex, seg) in segs.withIndex()) {
        val cubic = seg as CurveSegment.Cubic
        val flat = cubic.flattenWithParameter(tolerance)
        for ((point, t) in flat.dropLast(1)) result += PVertex(point, Provenance(side, ringIndex, segmentIndex, t))
    }
    return result
}

// =================================================================================================
// 2. The arrangement: primitive edges, segment-segment intersection, splitting.
// =================================================================================================

/**
 * One edge of a flattened, provenance-tagged ring, between two cyclically-consecutive [PVertex]s. [tA]/[tB] are the original segment's own
 * parameter at [a]/[b] (see [ringEdges]'s KDoc for why a segment-boundary edge is always attributed to the *earlier* segment, `tEnd =
 * 1.0`).
 */
internal data class PEdge(
    val a: Vec2,
    val b: Vec2,
    val side: Side,
    val ringIndex: Int,
    val segmentIndex: Int,
    val tA: Double,
    val tB: Double,
)

/**
 * [vertices] (one ring's flattened, provenance-tagged points, in order) turned into cyclic edges.
 * Consecutive vertices sharing a `segmentIndex` are an ordinary interior sub-edge of that
 * segment (`tA`/`tB` straight from their own breadcrumbs). Consecutive vertices with *different*
 * `segmentIndex` (happens exactly once per original segment — its own final sub-edge, whose
 * second endpoint's breadcrumb was attributed to the *next* segment's `t=0` by
 * [flattenRingWithProvenance]) are attributed to the *first* vertex's segment with `tB = 1.0`:
 * that edge is geometrically that segment's own tail, whichever segment index the shared
 * coordinate happened to be tagged with at the far end.
 */
internal fun ringEdges(vertices: List<PVertex>): List<PEdge> {
    val n = vertices.size
    return (0 until n).map { i ->
        val va = vertices[i]
        val vb = vertices[(i + 1) % n]
        val prov = va.provenance
        val tB = if (vb.provenance.segmentIndex == prov.segmentIndex) vb.provenance.t else 1.0
        PEdge(va.point, vb.point, prov.side, prov.ringIndex, prov.segmentIndex, prov.t, tB)
    }
}

internal const val BOOLEAN_EPSILON = 1e-9

/**
 * A found crossing (or collinear-overlap boundary) between two segments: the shared [point] plus where it sits, as a fraction in `[0,1]`,
 * along each of the two input segments.
 */
internal data class Crossing(
    val point: Vec2,
    val fracA: Double,
    val fracB: Double,
)

/**
 * Every point where segment `a1->a2` meets segment `b1->b2`. The ordinary case (the two segments'
 * directions are not parallel) is the standard `t`/`u` parametric line-intersection solve (Cormen
 * / de Berg's textbook formula, `t = cross(b1-a1, s) / cross(r, s)`, re-derived here rather than
 * quoted, `r`/`s` the two segments' own direction vectors): a single [Crossing] when both `t` and
 * `u` land in `[0,1]`, none otherwise. When the two directions *are* parallel, this checks for
 * true collinearity (not just parallel-and-offset) and, if collinear, returns the *interval*
 * where the two segments overlap as up to two [Crossing]s (its start and its end) — the general
 * fix for exactly-coincident or partially-overlapping input (task's own "a shape union with
 * itself should equal itself": subject and clip are then the same polygon edge-for-edge, entirely
 * collinear-overlapping with no transversal crossing at all).
 */
internal fun intersectSegments(
    a1: Vec2,
    a2: Vec2,
    b1: Vec2,
    b2: Vec2,
): List<Crossing> {
    val r = a2 - a1
    val s = b2 - b1
    val rxs = r.cross(s)
    val qp = b1 - a1
    if (abs(rxs) > BOOLEAN_EPSILON) {
        val t = qp.cross(s) / rxs
        val u = qp.cross(r) / rxs
        if (t in -FRAC_TOLERANCE..(1.0 + FRAC_TOLERANCE) && u in -FRAC_TOLERANCE..(1.0 + FRAC_TOLERANCE)) {
            val tc = t.coerceIn(0.0, 1.0)
            val uc = u.coerceIn(0.0, 1.0)
            return listOf(Crossing(a1 + r * tc, tc, uc))
        }
        return emptyList()
    }
    // Parallel. Collinear only if b1 also lies on a's own infinite line.
    if (abs(qp.cross(r)) > BOOLEAN_EPSILON) return emptyList()
    val rr = r.dot(r)
    if (rr <= BOOLEAN_EPSILON) return emptyList()
    val ss = s.dot(s)
    if (ss <= BOOLEAN_EPSILON) return emptyList()
    val t0 = qp.dot(r) / rr
    val t1 = t0 + s.dot(r) / rr
    val lo = min(t0, t1).coerceIn(0.0, 1.0)
    val hi = max(t0, t1).coerceIn(0.0, 1.0)
    if (hi - lo <= FRAC_TOLERANCE) return emptyList()
    return listOf(lo, hi).map { fracA ->
        val point = a1 + r * fracA
        val fracB = ((point - b1).dot(s) / ss).coerceIn(0.0, 1.0)
        Crossing(point, fracA, fracB)
    }
}

/**
 * How close a crossing's fraction must be to `0` or `1` to count as "already at that edge's own endpoint" (no new split needed there)
 * rather
 * than a genuine interior split point.
 */
private const val FRAC_TOLERANCE = 1e-7

private fun bboxOverlap(
    e1: PEdge,
    e2: PEdge,
    slack: Double,
): Boolean {
    val minX1 = min(e1.a.x, e1.b.x) - slack
    val maxX1 = max(e1.a.x, e1.b.x) + slack
    val minY1 = min(e1.a.y, e1.b.y) - slack
    val maxY1 = max(e1.a.y, e1.b.y) + slack
    val minX2 = min(e2.a.x, e2.b.x)
    val maxX2 = max(e2.a.x, e2.b.x)
    val minY2 = min(e2.a.y, e2.b.y)
    val maxY2 = max(e2.a.y, e2.b.y)
    return maxX1 >= minX2 && minX1 <= maxX2 && maxY1 >= minY2 && minY1 <= maxY2
}

/**
 * Splits every edge of [subjectEdges] and [clipEdges] at every crossing found between a subject
 * edge and a clip edge ([intersectSegments], pairwise — see the module KDoc for why this is
 * `O(n*m)` rather than a sweep line's near-linear cost). Both sides of a shared crossing point
 * reuse the *exact same* [Crossing.point] (never independently re-interpolated per side), so a
 * subject sub-edge and a clip sub-edge that meet there share a bit-identical coordinate, which
 * [traceLoops]' vertex-key adjacency depends on.
 */
internal fun splitEdgesAtIntersections(
    subjectEdges: List<PEdge>,
    clipEdges: List<PEdge>,
): Pair<List<PEdge>, List<PEdge>> {
    val subjectSplits = Array(subjectEdges.size) { mutableListOf<Pair<Double, Vec2>>() }
    val clipSplits = Array(clipEdges.size) { mutableListOf<Pair<Double, Vec2>>() }
    for ((i, e1) in subjectEdges.withIndex()) {
        for ((j, e2) in clipEdges.withIndex()) {
            if (!bboxOverlap(e1, e2, BOOLEAN_EPSILON)) continue
            for (crossing in intersectSegments(e1.a, e1.b, e2.a, e2.b)) {
                if (crossing.fracA > FRAC_TOLERANCE && crossing.fracA < 1.0 - FRAC_TOLERANCE) {
                    subjectSplits[i] += crossing.fracA to crossing.point
                }
                if (crossing.fracB > FRAC_TOLERANCE && crossing.fracB < 1.0 - FRAC_TOLERANCE) {
                    clipSplits[j] += crossing.fracB to crossing.point
                }
            }
        }
    }
    val newSubject = subjectEdges.indices.flatMap { i -> splitEdge(subjectEdges[i], subjectSplits[i]) }
    val newClip = clipEdges.indices.flatMap { j -> splitEdge(clipEdges[j], clipSplits[j]) }
    return newSubject to newClip
}

private fun splitEdge(
    edge: PEdge,
    splits: List<Pair<Double, Vec2>>,
): List<PEdge> {
    if (splits.isEmpty()) return listOf(edge)
    val deduped = splits.sortedBy { it.first }.distinctBy { (it.first / FRAC_TOLERANCE).roundToLong() }
    val stops = listOf(0.0 to edge.a) + deduped + listOf(1.0 to edge.b)
    return (0 until stops.size - 1).map { k ->
        val (f0, p0) = stops[k]
        val (f1, p1) = stops[k + 1]
        val t0 = edge.tA + (edge.tB - edge.tA) * f0
        val t1 = edge.tA + (edge.tB - edge.tA) * f1
        PEdge(p0, p1, edge.side, edge.ringIndex, edge.segmentIndex, t0, t1)
    }
}

// =================================================================================================
// 3. Classification (nonzero winding number, per op) and tracing kept edges into closed loops.
// =================================================================================================

/**
 * The integer winding number of [point] about the closed polygon [ring] (Dan Sunday's standard crossing-number formulation, re-derived
 * here,
 * not quoted): counts each edge that crosses a horizontal ray from [point] to `+x`, `+1` for an upward crossing with [point] to its left,
 * `-1` for a downward crossing with [point] to its right.
 */
internal fun windingNumber(
    point: Vec2,
    ring: List<Vec2>,
): Int {
    var wn = 0
    val n = ring.size
    for (i in 0 until n) {
        val a = ring[i]
        val b = ring[(i + 1) % n]
        if (a.y <= point.y) {
            if (b.y > point.y && isLeftOfLine(a, b, point) > 0.0) wn++
        } else {
            if (b.y <= point.y && isLeftOfLine(a, b, point) < 0.0) wn--
        }
    }
    return wn
}

private fun isLeftOfLine(
    a: Vec2,
    b: Vec2,
    p: Vec2,
): Double = (b.x - a.x) * (p.y - a.y) - (p.x - a.x) * (b.y - a.y)

/**
 * [windingNumber], summed across every ring of [rings] — the nonzero fill rule over a whole multi-contour side (so a subject with its own
 * outer-plus-inner rings, like `o`, already reads correctly: a point in the counter gets `+1` from the outer ring and `-1` from the inner
 * one, net `0`, "outside").
 */
internal fun totalWinding(
    point: Vec2,
    rings: List<List<Vec2>>,
): Int = rings.sumOf { windingNumber(point, it) }

/**
 * Whether a point with the given subject/clip winding numbers is inside the result of [op] — the
 * nonzero rule applied to each op's own set definition.
 */
internal fun insideResult(
    op: BooleanOp,
    subjectWinding: Int,
    clipWinding: Int,
): Boolean {
    val inSubject = subjectWinding != 0
    val inClip = clipWinding != 0
    return when (op) {
        BooleanOp.UNION -> inSubject || inClip
        BooleanOp.INTERSECT -> inSubject && inClip
        BooleanOp.SUBTRACT -> inSubject && !inClip
        BooleanOp.EXCLUDE -> inSubject != inClip
    }
}

/**
 * One kept, directed, restoration-ready edge of the result boundary: [start]->[end] with the result's interior on its left, plus [t0]/[t1]
 * (that original segment's own parameter at [start]/[end] — possibly `t0 > t1`, meaning this fragment reads its source segment backward).
 */
internal data class ResultEdge(
    val start: Vec2,
    val end: Vec2,
    val side: Side,
    val ringIndex: Int,
    val segmentIndex: Int,
    val t0: Double,
    val t1: Double,
)

/**
 * A coordinate snapped to a fine, fixed grid so nearly-identical floating-point points (the same intersection, computed once, reused
 * verbatim per [splitEdgesAtIntersections]'s own contract — so in practice this is exact, not
 * approximate) compare equal for adjacency and de-duplication.
 */
private const val SNAP = 1.0e-4

private fun keyOf(v: Vec2): Pair<Long, Long> = Pair((v.x / SNAP).roundToLong(), (v.y / SNAP).roundToLong())

/**
 * Classifies every edge in [edges] (subject and clip edges together, already split at every
 * crossing) by [insideResult] under [op], sampling a small perpendicular offset to each side of
 * every edge's midpoint. An edge whose two sides agree is not on the result's boundary and is
 * dropped; one whose two sides disagree is kept, oriented (reversed if needed) so the result's
 * interior sits on its left — this app's own CCW-outward/CW-inward convention (CLAUDE.md), which
 * therefore needs no separate fix-up pass afterwards. Exact duplicate directed edges (can arise
 * when subject and clip fully coincide over some stretch — the self-union case) are merged to one.
 */
internal fun classifyEdges(
    edges: List<PEdge>,
    subjectRings: List<List<Vec2>>,
    clipRings: List<List<Vec2>>,
    op: BooleanOp,
): List<ResultEdge> {
    val seen = HashSet<Pair<Pair<Long, Long>, Pair<Long, Long>>>()
    val result = mutableListOf<ResultEdge>()
    for (edge in edges) {
        val delta = edge.b - edge.a
        val length = delta.length()
        if (length <= BOOLEAN_EPSILON) continue
        val dir = Vec2(delta.x / length, delta.y / length)
        val leftNormal = Vec2(-dir.y, dir.x)
        val offset = (length * 1.0e-3).coerceIn(1.0e-6, 0.05)
        val mid = (edge.a + edge.b) * 0.5
        val sampleLeft = mid + leftNormal * offset
        val sampleRight = mid - leftNormal * offset
        val insideLeft = insideResult(op, totalWinding(sampleLeft, subjectRings), totalWinding(sampleLeft, clipRings))
        val insideRight = insideResult(op, totalWinding(sampleRight, subjectRings), totalWinding(sampleRight, clipRings))
        if (insideLeft == insideRight) continue

        val orientation =
            if (insideLeft) {
                ResultOrientation(edge.a, edge.b, edge.tA, edge.tB)
            } else {
                ResultOrientation(edge.b, edge.a, edge.tB, edge.tA)
            }

        val key = keyOf(orientation.start) to keyOf(orientation.end)
        if (seen.add(key)) {
            result +=
                ResultEdge(
                    orientation.start,
                    orientation.end,
                    edge.side,
                    edge.ringIndex,
                    edge.segmentIndex,
                    orientation.t0,
                    orientation.t1,
                )
        }
    }
    return result
}

private data class ResultOrientation(
    val start: Vec2,
    val end: Vec2,
    val t0: Double,
    val t1: Double,
)

/**
 * Traces [edges] (already kept and oriented by [classifyEdges]) into closed loops by simple
 * vertex-to-vertex adjacency: from each edge's end vertex, follow the one remaining unvisited
 * edge starting there (or, if more than one remains — a vertex where the result boundary touches
 * itself, not exercised by this task's own fixtures but handled generally rather than left
 * undefined — the one making the sharpest clockwise turn from the incoming direction, the
 * standard rule for tracing one simple face out of a planar arrangement). A dangling edge (no
 * unvisited edge continues the loop, and the loop has not returned to its own start) means this
 * arrangement is not the simple union-of-disjoint-cycles a correctly classified boolean result
 * always is — a genuinely degenerate input this task's effort did not reach — and this throws
 * rather than emit silently-wrong geometry (the honesty rule applied to a failure mode).
 */
internal fun traceLoops(edges: List<ResultEdge>): List<List<ResultEdge>> {
    val byStart = HashMap<Pair<Long, Long>, MutableList<Int>>()
    for ((i, e) in edges.withIndex()) byStart.getOrPut(keyOf(e.start)) { mutableListOf() }.add(i)
    val visited = BooleanArray(edges.size)
    val loops = mutableListOf<List<ResultEdge>>()

    for (startIndex in edges.indices) {
        if (visited[startIndex]) continue
        val loopStartKey = keyOf(edges[startIndex].start)
        val loop = mutableListOf<ResultEdge>()
        var current = startIndex
        while (true) {
            visited[current] = true
            loop += edges[current]
            val endKey = keyOf(edges[current].end)
            if (endKey == loopStartKey) break
            val candidates = byStart[endKey]?.filter { !visited[it] } ?: emptyList()
            if (candidates.isEmpty()) {
                error(
                    "Booleans.kt: could not close a traced loop (dangling at $endKey after ${loop.size} edges) -- " +
                        "a degenerate arrangement (exact tangency or similar) this implementation does not resolve.",
                )
            }
            current = if (candidates.size == 1) candidates[0] else chooseSharpestRightTurn(edges, current, candidates)
        }
        if (loop.size >= 3) loops += loop
    }
    return loops
}

private fun chooseSharpestRightTurn(
    edges: List<ResultEdge>,
    incomingIndex: Int,
    candidates: List<Int>,
): Int {
    val incomingDir = (edges[incomingIndex].end - edges[incomingIndex].start).let { it * (1.0 / it.length()) }
    // The most-clockwise turn from "continuing straight" (i.e. from -incomingDir reversed back to
    // incomingDir) has the largest *negative* signed angle; sort ascending on that and take the first.
    return candidates.minByOrNull { idx ->
        val outDir = (edges[idx].end - edges[idx].start).let { it * (1.0 / it.length()) }
        val angle = signedAngleBetween(incomingDir, outDir)
        // Map (-PI, PI] to [0, 2*PI) so "smallest" means "first when sweeping clockwise from straight-on".
        if (angle <= 0.0) -angle else (2.0 * kotlin.math.PI - angle)
    }!!
}

// =================================================================================================
// 4. Curve restoration: traced loops of flattened edges back into exact CurveFormat.CUBIC contours.
// =================================================================================================

/**
 * A maximal run of consecutive [ResultEdge]s from the same original `(side, ring, segment)`, merged into one `[tStart, tEnd]` range
 * (possibly `tStart > tEnd`, a backward-read run). `internal`, not `private`, so [mergeIntoRuns] (itself `internal` for its own direct unit
 * test) can return it.
 */
internal data class Run(
    val side: Side,
    val ringIndex: Int,
    val segmentIndex: Int,
    val tStart: Double,
    val tEnd: Double,
)

/**
 * How close two `t` values must be to count as "the same point" when chaining consecutive edges of the same source segment into one
 * [Run] (a
 * looser tolerance than [FRAC_TOLERANCE], since `t` here is an absolute segment parameter, not an edge-local fraction).
 */
private const val CHAIN_T_TOLERANCE = 1.0e-4

internal fun mergeIntoRuns(loopEdges: List<ResultEdge>): List<Run> {
    val runs = mutableListOf<Run>()
    for (e in loopEdges) {
        val last = runs.lastOrNull()
        if (last != null &&
            last.side == e.side &&
            last.ringIndex == e.ringIndex &&
            last.segmentIndex == e.segmentIndex &&
            abs(last.tEnd - e.t0) < CHAIN_T_TOLERANCE
        ) {
            runs[runs.lastIndex] = last.copy(tEnd = e.t1)
        } else {
            runs += Run(e.side, e.ringIndex, e.segmentIndex, e.t0, e.t1)
        }
    }
    if (runs.size >= 2) {
        val first = runs.first()
        val last = runs.last()
        if (first.side == last.side &&
            first.ringIndex == last.ringIndex &&
            first.segmentIndex == last.segmentIndex &&
            abs(last.tEnd - first.tStart) < CHAIN_T_TOLERANCE
        ) {
            runs[0] = first.copy(tStart = last.tStart)
            runs.removeAt(runs.lastIndex)
        }
    }
    return runs
}

/**
 * How close a run's `[tStart, tEnd]` must be to the full `[0, 1]` to be emitted as the original segment verbatim rather than an extracted
 * sub-arc.
 */
private const val WHOLE_SEGMENT_TOLERANCE = 1.0e-6

private fun subArc(
    cubic: CurveSegment.Cubic,
    t0: Double,
    t1: Double,
): CurveSegment.Cubic {
    val afterT0 = if (t0 <= WHOLE_SEGMENT_TOLERANCE) cubic else cubic.subdivide(t0).second
    if (t1 >= 1.0 - WHOLE_SEGMENT_TOLERANCE) return afterT0
    val remapped = ((t1 - t0) / (1.0 - t0)).coerceIn(0.0, 1.0)
    return afterT0.subdivide(remapped).first
}

private fun CurveSegment.Cubic.reversed(): CurveSegment.Cubic = CurveSegment.Cubic(end, control2, control1, start)

private fun runToCubic(
    run: Run,
    subject: List<Contour>,
    clip: List<Contour>,
): CurveSegment.Cubic {
    val contour = if (run.side == Side.SUBJECT) subject[run.ringIndex] else clip[run.ringIndex]
    val original = contour.segments()[run.segmentIndex] as CurveSegment.Cubic
    val forward = run.tStart <= run.tEnd
    val lo = if (forward) run.tStart else run.tEnd
    val hi = if (forward) run.tEnd else run.tStart
    val piece = if (lo <= WHOLE_SEGMENT_TOLERANCE && hi >= 1.0 - WHOLE_SEGMENT_TOLERANCE) original else subArc(original, lo, hi)
    return if (forward) piece else piece.reversed()
}

/**
 * Builds a [CurveFormat.CUBIC] [Contour] from an ordered, closed loop of cubic segments, exactly as `core-geometry`'s own
 * (module-`internal`, so not directly reusable here) `buildCubicContour` does: each segment contributes its own start (rounded to the
 * nearest integer font unit) plus its two control points.
 */
private fun buildResultContour(segments: List<CurveSegment.Cubic>): Contour {
    require(segments.isNotEmpty()) { "a traced boolean loop produced no segments" }
    val points =
        segments.flatMap { segment ->
            listOf(
                ContourPoint(segment.start.roundToPointLocal(), onCurve = true),
                ContourPoint(segment.control1.roundToPointLocal(), onCurve = false),
                ContourPoint(segment.control2.roundToPointLocal(), onCurve = false),
            )
        }
    return Contour(points, CurveFormat.CUBIC)
}

private fun Vec2.roundToPointLocal(): Point = Point(x.roundToInt(), y.roundToInt())
