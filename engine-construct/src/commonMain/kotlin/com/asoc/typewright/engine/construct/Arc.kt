// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.construct

import com.asoc.typewright.core.geometry.CurveSegment
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.core.geometry.midpoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * The construction grammar's Arc primitive (brief section 10 M2;
 * `docs/TYPEWRIGHT_HANDOFF.md`'s own M2 list: "Arc: three points; start, centre, end; start, end,
 * radius; start, end, bulge; tangent, tangent, radius (the fillet); centre, radius, start angle,
 * sweep"). **All six entry methods are implemented** (task's own "as many... as you can build
 * correctly and generally" — arc geometry is this task's most failure-prone corner, so every entry
 * method below reduces to the one canonical, independently-derivable representation, [CircularArc],
 * and every entry method's own tests check a closed-form fact about the *result* — the swept angle,
 * the radius, that the arc actually passes through its own named points — never merely "it
 * compiled").
 *
 * Every construction stays parametric until baked (brief section 10): an [ArcPrimitive] holds only
 * the entry method's own original parameters; [realize] recomputes [CircularArc] fresh from them on
 * every call, with no caching anywhere in this file, so changing one field (mutate a `data class`
 * with `.copy(...)`) and calling [realize] again is the entire "recompute on demand" model this
 * whole primitive grammar uses (see this module's own README). "Baked" is simply the caller keeping
 * one [realize] result (or its [toCubicSegments]) as plain geometry and discarding the
 * [ArcPrimitive] that produced it — [CircularArc] and [CurveSegment.Cubic] are already "final, plain
 * geometry" (`core-geometry`'s own types), so no separate "baked" wrapper type is needed.
 */
sealed class ArcPrimitive {
    /** This primitive's current parameters, reduced to the one canonical circular-arc representation. */
    abstract fun realize(): CircularArc

    /**
     * Which way an arc's angle is read as increasing, in this module's own y-up, counter-clockwise-
     * positive convention (matching [com.asoc.typewright.engine.construct.rotate]).
     */
    enum class Direction { CLOCKWISE, COUNTER_CLOCKWISE }

    /**
     * Entry method 1: the arc through [start], [through] and [end], in that travel order (the
     * classic "3-point arc" of any CAD or vector tool: draw the circle through all three points —
     * [circumcenter] — then sweep from [start] whichever way actually visits [through] before
     * reaching [end]).
     */
    data class ThreePoints(
        val start: Point,
        val through: Point,
        val end: Point,
    ) : ArcPrimitive() {
        override fun realize(): CircularArc {
            val s = start.toVec2()
            val m = through.toVec2()
            val e = end.toVec2()
            val center = circumcenter(s, m, e)
            val radius = (s - center).length()
            val startAngle = atan2(s.y - center.y, s.x - center.x)
            val throughOffset = normalizeAngleToPositiveTwoPi(atan2(m.y - center.y, m.x - center.x) - startAngle)
            val endOffset = normalizeAngleToPositiveTwoPi(atan2(e.y - center.y, e.x - center.x) - startAngle)
            // Travelling counter-clockwise from `start`, does `through` come before `end`? If so the
            // arc sweeps counter-clockwise by endOffset; otherwise `through` only lies on the *other*
            // (clockwise) way around, so the arc sweeps clockwise instead.
            val sweep = if (throughOffset < endOffset) endOffset else endOffset - TWO_PI
            return CircularArc(center, radius, startAngle, sweep)
        }
    }

    /**
     * Entry method 2: the arc on the circle centred at [center] through [start] (which fixes the
     * radius), swept [direction] until it reaches the ray towards [end] ([end] itself need not sit
     * exactly on that circle — only its angle from [center] is used, the standard "start, centre,
     * end" CAD arc convention).
     */
    data class StartCenterEnd(
        val start: Point,
        val center: Point,
        val end: Point,
        val direction: Direction = Direction.COUNTER_CLOCKWISE,
    ) : ArcPrimitive() {
        override fun realize(): CircularArc {
            val c = center.toVec2()
            val s = start.toVec2()
            val e = end.toVec2()
            val radius = (s - c).length()
            require(radius > EPSILON) { "start $start coincides with centre $center; an arc needs a positive radius" }
            val startAngle = atan2(s.y - c.y, s.x - c.x)
            val endAngle = atan2(e.y - c.y, e.x - c.x)
            val ccwSweep = normalizeAngleToPositiveTwoPi(endAngle - startAngle)
            val sweep = if (direction == Direction.COUNTER_CLOCKWISE) ccwSweep else ccwSweep - TWO_PI
            return CircularArc(c, radius, startAngle, sweep)
        }
    }

    /**
     * Entry method 3: the arc from [start] to [end] on a circle of [radius] — under-determined by
     * itself (any radius at least half the chord length admits two circle centres, and each centre
     * admits two arcs between the same two points), so [largeArc] and [counterClockwise] pick one of
     * the (at most) four solutions, in exactly the spirit of the W3C SVG path spec's own circular-arc
     * endpoint parameterization (`large-arc-flag`/`sweep-flag`) — reimplemented here directly over
     * this module's own y-up convention (see [arcThroughChordAndRadius]'s KDoc for why it is built by
     * direct construction-and-selection rather than by transcribing that spec's own sign algebra).
     */
    data class StartEndRadius(
        val start: Point,
        val end: Point,
        val radius: Double,
        val largeArc: Boolean = false,
        val counterClockwise: Boolean = true,
    ) : ArcPrimitive() {
        override fun realize(): CircularArc = arcThroughChordAndRadius(start.toVec2(), end.toVec2(), radius, largeArc, counterClockwise)
    }

    /**
     * Entry method 4: the arc from [start] to [end] with [bulge] encoding both curvature and
     * direction in one number, the DXF `LWPOLYLINE`/`POLYLINE` vertex "bulge" convention: `bulge =
     * tan(includedAngle / 4)`, positive for a counter-clockwise arc, negative for clockwise (Autodesk
     * DXF Reference, "Bulge"; this module's own counter-clockwise-positive convention matches it
     * exactly, needing no sign flip). See [arcFromBulge]'s KDoc for the closed-form conversion to
     * [radius]/`largeArc`/`counterClockwise`, which then shares [StartEndRadius]'s own solver.
     */
    data class StartEndBulge(
        val start: Point,
        val end: Point,
        val bulge: Double,
    ) : ArcPrimitive() {
        override fun realize(): CircularArc = arcFromBulge(start.toVec2(), end.toVec2(), bulge)
    }

    /**
     * Entry method 5: the fillet — the arc of [radius] tangent to both [line1] and [line2]
     * ([filletCenterAndTangents]), always the *minor* (at most a half turn) arc between the two
     * tangent points: a fillet rounds a corner, so it is by definition the short, convex way around,
     * never the reflex way.
     */
    data class TangentTangentRadius(
        val line1: TwoPointLine,
        val line2: TwoPointLine,
        val radius: Double,
        val centerSide1: FilletSide,
        val centerSide2: FilletSide,
    ) : ArcPrimitive() {
        override fun realize(): CircularArc {
            val (center, tangent1, tangent2) =
                filletCenterAndTangents(line1, line2, radius, centerSide1, centerSide2)
                    ?: error("$line1 and $line2 are parallel; no fillet of radius $radius exists between them")
            val startAngle = atan2(tangent1.y - center.y, tangent1.x - center.x)
            val endAngle = atan2(tangent2.y - center.y, tangent2.x - center.x)
            val ccwSweep = normalizeAngleToPositiveTwoPi(endAngle - startAngle)
            // The minor arc: counter-clockwise if that is the short way (<= a half turn), clockwise otherwise.
            val sweep = if (ccwSweep <= PI) ccwSweep else ccwSweep - TWO_PI
            return CircularArc(center, radius, startAngle, sweep)
        }
    }

    /** Entry method 6: the canonical representation itself, taken directly (angles in radians, [sweepRadians] signed, positive counter-clockwise). */
    data class CenterRadiusStartSweep(
        val center: Point,
        val radius: Double,
        val startAngleRadians: Double,
        val sweepRadians: Double,
    ) : ArcPrimitive() {
        override fun realize(): CircularArc = CircularArc(center.toVec2(), radius, startAngleRadians, sweepRadians)
    }
}

/**
 * The canonical realized form every [ArcPrimitive] entry method reduces to: a circular arc centred
 * at [center], of [radius], starting at [startAngleRadians] and sweeping [sweepRadians] (signed:
 * positive counter-clockwise, this module's own convention — [com.asoc.typewright.engine.construct.rotate]'s
 * KDoc). This is deliberately not itself a `Contour` — like [com.asoc.typewright.engine.construct.hobbySplineOpen],
 * an arc is generally an *open* run of geometry (`core-geometry` has no open-contour type — see that
 * function's own KDoc); [toCubicSegments] is how a caller gets concrete curve geometry from it, and
 * [CirclePrimitive] is the case where a caller wants a *closed* full-turn arc as a [com.asoc.typewright.core.geometry.Contour].
 */
data class CircularArc(
    val center: Vec2,
    val radius: Double,
    val startAngleRadians: Double,
    val sweepRadians: Double,
) {
    init {
        require(radius > 0.0) { "an arc's radius must be positive, was $radius" }
        require(sweepRadians != 0.0) { "an arc's sweep must be non-zero" }
    }

    /** This arc's endpoint angle, [startAngleRadians] plus [sweepRadians] — not normalized into any particular range. */
    val endAngleRadians: Double get() = startAngleRadians + sweepRadians

    /** The exact point on this arc at parameter [t] (`0` is the start, `1` is the end). */
    fun pointAt(t: Double): Vec2 {
        val angle = startAngleRadians + sweepRadians * t
        return center + Vec2(cos(angle), sin(angle)) * radius
    }

    /**
     * This arc realized as one or more [CurveSegment.Cubic]s, splitting so that no single piece
     * sweeps more than [maxSegmentSweepRadians] (default a quarter turn, matching the classic 4-arc
     * full-circle construction this module's own `TestShapes.circleContour` fixture uses
     * independently). **Method** (Riškus, A. (2006), "Approximation of a Cubic Bezier Curve by
     * Circular Arcs and Vice Versa", Information Technology and Control, 35(4) — the standard
     * closed-form circular-arc-to-cubic-Bezier approximation; equivalently, for a quarter-turn piece,
     * the well-known kappa = `4/3 * tan(pi/8)` ~= `0.5523` constant): each piece's own two control
     * points sit at distance `radius * 4/3 * tan(pieceSweep / 4)` from its own endpoint, along that
     * endpoint's own counter-clockwise tangent direction `(-sin(angle), cos(angle))` — this single
     * formula handles a clockwise (`sweepRadians < 0`) piece correctly too, with no separate case,
     * because `tan` of a negative angle is itself negative, which flips the control points' offset to
     * the opposite side automatically (verified directly in this file's own tests, both directions).
     */
    fun toCubicSegments(maxSegmentSweepRadians: Double = PI / 2.0): List<CurveSegment.Cubic> {
        require(maxSegmentSweepRadians > 0.0) { "maxSegmentSweepRadians must be positive, was $maxSegmentSweepRadians" }
        val segmentCount = ceil(abs(sweepRadians) / maxSegmentSweepRadians).toInt().coerceAtLeast(1)
        val step = sweepRadians / segmentCount
        return (0 until segmentCount).map { i -> singleCubicArcSegment(center, radius, startAngleRadians + step * i, step) }
    }
}

/** One cubic Bezier approximating the piece of a circle centred at [center], radius [radius], from angle [startAngle] sweeping [pieceSweep] (see [CircularArc.toCubicSegments]'s KDoc for the formula and citation). */
private fun singleCubicArcSegment(
    center: Vec2,
    radius: Double,
    startAngle: Double,
    pieceSweep: Double,
): CurveSegment.Cubic {
    val endAngle = startAngle + pieceSweep
    val p0 = center + Vec2(cos(startAngle), sin(startAngle)) * radius
    val p1 = center + Vec2(cos(endAngle), sin(endAngle)) * radius
    val handleLength = radius * (4.0 / 3.0) * tan(pieceSweep / 4.0)
    val tangent0 = Vec2(-sin(startAngle), cos(startAngle))
    val tangent1 = Vec2(-sin(endAngle), cos(endAngle))
    val c1 = p0 + tangent0 * handleLength
    val c2 = p1 - tangent1 * handleLength
    return CurveSegment.Cubic(p0, c1, c2, p1)
}

/**
 * [ArcPrimitive.StartEndRadius]'s own solver, shared with [arcFromBulge]. **Why this is built by
 * direct construction-and-selection, not by transcribing the SVG spec's endpoint-to-centre algebra
 * (`F.6.5`) line for line:** that derivation's sign choices are tied to SVG's own y-down coordinate
 * convention, and reproducing it blind, in this module's y-up convention, from a general description
 * risks exactly the kind of silent sign error `HobbySpline.kt`'s own honesty note warns about. This
 * function instead **enumerates the actual, finite solution set directly**: a chord of [radius] has
 * at most two centres (the two points at distance [radius] from both [start] and [end], found the
 * standard way — offset the chord's midpoint along its own perpendicular by
 * `sqrt(radius^2 - (chordLength/2)^2)`, both signs), and each centre admits exactly two arcs between
 * [start] and [end] (the counter-clockwise-swept one and the clockwise-swept one, computed directly
 * from that centre's own real angles to [start] and [end] — no algebraic sign derivation to get
 * wrong). [counterClockwise] then keeps only the one candidate per centre whose own computed sweep
 * sign actually matches, and [largeArc] picks the larger- or smaller-magnitude of the (exactly) two
 * survivors. Every step is a direct, checkable geometric fact, not an inherited formula.
 */
internal fun arcThroughChordAndRadius(
    start: Vec2,
    end: Vec2,
    radius: Double,
    largeArc: Boolean,
    counterClockwise: Boolean,
): CircularArc {
    require(radius > 0.0) { "radius must be positive, was $radius" }
    val chord = end - start
    val chordLength = chord.length()
    require(chordLength > EPSILON) { "start $start and end $end must be distinct" }
    val halfChord = chordLength / 2.0
    require(radius >= halfChord - 1e-6) {
        "radius $radius is too small to span a chord of length $chordLength (needs at least $halfChord)"
    }
    val mid = midpoint(start, end)
    val chordDir = chord * (1.0 / chordLength)
    val perpendicular = leftNormal(chordDir)
    val centerOffset = sqrt(max(0.0, radius * radius - halfChord * halfChord))
    val centers = listOf(mid + perpendicular * centerOffset, mid - perpendicular * centerOffset)
    val matchingDirection =
        centers.map { center ->
            val startAngle = atan2(start.y - center.y, start.x - center.x)
            val endAngle = atan2(end.y - center.y, end.x - center.x)
            val ccwSweep = normalizeAngleToPositiveTwoPi(endAngle - startAngle)
            val sweep = if (counterClockwise) ccwSweep else ccwSweep - TWO_PI
            CircularArc(center, radius, startAngle, sweep)
        }
    return if (largeArc) {
        matchingDirection.maxByOrNull {
            abs(
                it.sweepRadians,
            )
        }!!
    } else {
        matchingDirection.minByOrNull { abs(it.sweepRadians) }!!
    }
}

/**
 * [ArcPrimitive.StartEndBulge]'s conversion to [arcThroughChordAndRadius]'s own `radius`/`largeArc`/
 * `counterClockwise` parameterization, from the DXF bulge definition `bulge = tan(theta / 4)` (theta
 * the arc's own signed included angle): `theta = 4 * atan(bulge)` inverts that directly;
 * `counterClockwise = theta > 0` (the DXF sign convention, matching this module's own); `largeArc =
 * |theta| > PI`; and `radius = chordLength / (2 * |sin(theta / 2)|)` from the standard chord-length
 * formula for a circle (`chordLength = 2 * radius * sin(includedAngle / 2)`). The resulting
 * [CircularArc]'s own [CircularArc.sweepRadians] is not read off `theta` directly — it is
 * *recomputed* by [arcThroughChordAndRadius] from the derived centre's own real angles, so a caller
 * gets back exactly the requested `theta`, verified in this file's own tests, never merely assumed
 * equal from the algebra alone.
 */
internal fun arcFromBulge(
    start: Vec2,
    end: Vec2,
    bulge: Double,
): CircularArc {
    require(abs(bulge) > EPSILON) { "bulge must be non-zero (a zero bulge is a straight segment, not an arc)" }
    val theta = 4.0 * atan2(bulge, 1.0)
    val counterClockwise = theta > 0.0
    val largeArc = abs(theta) > PI
    val chordLength = (end - start).length()
    require(chordLength > EPSILON) { "start $start and end $end must be distinct" }
    val radius = chordLength / (2.0 * abs(sin(theta / 2.0)))
    return arcThroughChordAndRadius(start, end, radius, largeArc, counterClockwise)
}
