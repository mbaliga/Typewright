// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.geometry

import kotlin.math.abs

/** A place on a cubic contour a cut can start or end at: an existing node, or a spot on a side. */
sealed interface CutPlace {
    /** The on-curve node at point index [pointIndex]. */
    data class AtNode(
        val pointIndex: Int,
    ) : CutPlace

    /** The spot at parameter [t] along side [segment] of [Contour.segmentLayout]; near either end it is taken as that end's node. */
    data class OnSegment(
        val segment: Int,
        val t: Double,
    ) : CutPlace
}

/** What a cut came to. */
sealed interface ContourCutResult {
    /** The contour became two closed contours, [first] and [second], split along a straight line between the two places. */
    data class Cut(
        val first: Contour,
        val second: Contour,
    ) : ContourCutResult

    /** The cut was not made, for [reason] in a sentence a person can read. */
    data class Refused(
        val reason: String,
    ) : ContourCutResult
}

/**
 * Cuts this cubic contour in two along the straight line between [a] and [b] ("split the contour
 * here"): each place that is on a side becomes a real node first, by the same de Casteljau split
 * [withNodeInserted] uses, so the shape is unchanged until the cut itself; the two arcs between the nodes
 * are then closed with one straight side each, written the way `.glif` stores a plain `line`. The nodes
 * the cut ends at become corners (their smooth flag cannot survive a new straight side) and every other
 * node keeps its flag and its handles untouched.
 *
 * The cut is refused, never guessed at, when the contour is not cubic, when both places are the same spot,
 * when the straight cut would cross the outline of this contour or of any of [others] (the glyph's other
 * contours, so a cut cannot run through a counter), when it would lie outside the shape, or when either piece
 * would be left with no area.
 */
fun Contour.cutBetween(
    a: CutPlace,
    b: CutPlace,
    others: List<Contour> = emptyList(),
): ContourCutResult {
    if (format != CurveFormat.CUBIC) return ContourCutResult.Refused("Cutting needs a cubic outline; this contour is quadratic.")
    val layout = segmentLayout()

    fun valid(place: CutPlace): Boolean =
        when (place) {
            is CutPlace.AtNode -> place.pointIndex in points.indices && place.pointIndex % 3 == 0
            is CutPlace.OnSegment -> place.segment in layout.indices && place.t in 0.0..1.0
        }
    if (!valid(a) || !valid(b)) return ContourCutResult.Refused("That place is not on the outline any more.")

    val nodeCount = points.size / 3
    // Spots on a side become nodes: insert from the highest side (and highest t) down so earlier numbers stay valid.
    var work = this
    val resolved = arrayOfNulls<Int>(2)
    val places = listOf(a, b)
    val inserts = mutableListOf<Int>()
    for ((slot, place) in places.withIndex()) {
        when (place) {
            is CutPlace.AtNode -> {
                resolved[slot] = place.pointIndex / 3
            }

            is CutPlace.OnSegment -> {
                when {
                    place.t <= END_T -> resolved[slot] = place.segment
                    place.t >= 1.0 - END_T -> resolved[slot] = (place.segment + 1) % nodeCount
                    else -> inserts += slot
                }
            }
        }
    }
    val order =
        inserts.sortedWith(
            compareByDescending<Int> {
                (places[it] as CutPlace.OnSegment).segment
            }.thenByDescending { (places[it] as CutPlace.OnSegment).t },
        )
    var lastInserted: Pair<Int, Double>? = null
    for (slot in order) {
        val place = places[slot] as CutPlace.OnSegment
        // A second spot on the side just split lies in its first part, at a share of the way along it.
        val t = lastInserted?.takeIf { it.first == place.segment }?.let { place.t / it.second } ?: place.t
        val inserted = work.withNodeInserted(place.segment, t.coerceIn(MIN_T, 1.0 - MIN_T))
        work = inserted.contour
        val newNode = inserted.nodeIndex / 3
        for (other in resolved.indices) {
            val value = resolved[other]
            if (value != null && value > place.segment) resolved[other] = value + 1
        }
        resolved[slot] = newNode
        lastInserted = place.segment to place.t
    }
    val na = resolved[0] ?: return ContourCutResult.Refused("That place is not on the outline any more.")
    val nb = resolved[1] ?: return ContourCutResult.Refused("That place is not on the outline any more.")
    if (na == nb) return ContourCutResult.Refused("Both ends of the cut are the same place. Pick two different places on the outline.")
    val lo = minOf(na, nb)
    val hi = maxOf(na, nb)
    val pa = work.points[3 * lo].point
    val pb = work.points[3 * hi].point
    if (!work.chordIsClear(pa, pb, others)) {
        return ContourCutResult.Refused(
            "That cut would cross the outline, run through one of its nodes, or leave the shape. Pick two places that see each other.",
        )
    }
    val corner = { p: ContourPoint -> p.copy(smooth = false) }
    val n = work.points.size
    val closeAtHi = listOf(ContourPoint(pb, true), ContourPoint(pb, false), ContourPoint(pa, false))
    val closeAtLo = listOf(ContourPoint(pa, true), ContourPoint(pa, false), ContourPoint(pb, false))
    val arc1 = work.points.subList(3 * lo, 3 * hi).toMutableList()
    arc1[0] = corner(arc1[0])
    val arc2 = (work.points.subList(3 * hi, n) + work.points.subList(0, 3 * lo)).toMutableList()
    arc2[0] = corner(arc2[0])
    val first = Contour(arc1 + closeAtHi, CurveFormat.CUBIC)
    val second = Contour(arc2 + closeAtLo, CurveFormat.CUBIC)
    if (abs(first.signedArea()) < MIN_PIECE_AREA || abs(second.signedArea()) < MIN_PIECE_AREA) {
        return ContourCutResult.Refused("That cut would leave a piece with no area. Pick places on different sides.")
    }
    return ContourCutResult.Cut(first, second)
}

/** True when the straight line from [from] to [to] stays inside this contour and touches no outline except at its two ends. */
private fun Contour.chordIsClear(
    from: Point,
    to: Point,
    others: List<Contour>,
): Boolean {
    val a = from.toVec2()
    val b = to.toVec2()
    val outlines = listOf(this) + others
    if (passesThroughANode(a, b, outlines)) return false
    for (outline in outlines) {
        val poly = outline.flattened()
        for (i in poly.indices) {
            val p = poly[i]
            val q = poly[(i + 1) % poly.size]
            if (properlyCrosses(a, b, p, q)) return false
        }
    }
    val mid = midpoint(a, b)
    if (!flattened().encloses(mid)) return false
    // A chord that grazes a counter's corner crosses nothing properly yet still runs through it: sample along it too.
    val inside = others.map { it.flattened() }
    return (1 until CHORD_SAMPLES).none { k ->
        val p = a + (b - a) * (k.toDouble() / CHORD_SAMPLES)
        inside.any { it.encloses(p) }
    }
}

/**
 * True when a node of any outline other than the chord's own two ends lies on the chord between them. Such a chord
 * crosses nothing properly and the inside test is ambiguous exactly on the boundary, so it is refused outright: it
 * either touches a counter or runs through a reflex corner of its own contour.
 */
private fun passesThroughANode(
    a: Vec2,
    b: Vec2,
    outlines: List<Contour>,
): Boolean {
    val chord = b - a
    val lengthSquared = chord.dot(chord)
    if (lengthSquared <= 0.0) return false
    for (outline in outlines) {
        for (p in outline.points) {
            if (!p.onCurve) continue
            val v = p.point.toVec2()
            if ((v - a).length() <= NODE_ON_CHORD || (v - b).length() <= NODE_ON_CHORD) continue
            val along = (v - a).dot(chord) / lengthSquared
            if (along <= 0.0 || along >= 1.0) continue
            val foot = a + chord * along
            if ((v - foot).length() <= NODE_ON_CHORD) return true
        }
    }
    return false
}

private fun Contour.flattened(): List<Vec2> =
    segments().flatMap { segment -> (0 until FLATTEN_STEPS).map { segment.pointAt(it.toDouble() / FLATTEN_STEPS) } }

private fun List<Vec2>.encloses(p: Vec2): Boolean {
    var inside = false
    var j = size - 1
    for (i in indices) {
        val pi = this[i]
        val pj = this[j]
        if ((pi.y > p.y) != (pj.y > p.y) && p.x < (pj.x - pi.x) * (p.y - pi.y) / (pj.y - pi.y) + pi.x) inside = !inside
        j = i
    }
    return inside
}

/** True when segment a-b and segment p-q cross at a point interior to both (touching at an end of either does not count). */
private fun properlyCrosses(
    a: Vec2,
    b: Vec2,
    p: Vec2,
    q: Vec2,
): Boolean {
    val d1 = (b - a).cross(p - a)
    val d2 = (b - a).cross(q - a)
    val d3 = (q - p).cross(a - p)
    val d4 = (q - p).cross(b - p)
    val scale = CROSS_EPS * maxOf(1.0, (b - a).length() * (q - p).length())
    return ((d1 > scale && d2 < -scale) || (d1 < -scale && d2 > scale)) && ((d3 > scale && d4 < -scale) || (d3 < -scale && d4 > scale))
}

private const val END_T = 0.02
private const val MIN_T = 0.02
private const val FLATTEN_STEPS = 24
private const val CROSS_EPS = 1e-6
private const val CHORD_SAMPLES = 16
private const val NODE_ON_CHORD = 0.75
private const val MIN_PIECE_AREA = 4.0
