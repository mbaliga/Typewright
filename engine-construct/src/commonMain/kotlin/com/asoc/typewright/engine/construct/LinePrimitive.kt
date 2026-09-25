// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.construct

import com.asoc.typewright.core.geometry.CurveSegment
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.core.geometry.pointAt
import kotlin.math.cos
import kotlin.math.sin

/**
 * The construction grammar's Line primitive (brief section 10 M2;
 * `docs/TYPEWRIGHT_HANDOFF.md`'s own M2 list: "Line: two points; point, angle, length; point,
 * tangent to a curve"). Every entry method reduces to a straight [CurveSegment.Line] — `core-geometry`
 * has no open-contour type (see [com.asoc.typewright.engine.construct.hobbySplineOpen]'s own KDoc),
 * so, like an [ArcPrimitive], a bare [LinePrimitive] is not itself a `Contour`: a caller that wants a
 * filled shape from it strokes the result ([strokeOpenPolyline]) or uses it as one edge of a
 * [RectanglePrimitive]. Stays parametric until [realize] is called (this module's own "recompute on
 * demand" model — see [ArcPrimitive]'s own KDoc for the full statement, shared by every primitive in
 * this file set).
 */
sealed class LinePrimitive {
    /** This primitive's current parameters, realized as a straight segment. */
    abstract fun realize(): CurveSegment.Line

    /** Entry method 1: the straight segment from [start] to [end]. */
    data class TwoPoints(
        val start: Point,
        val end: Point,
    ) : LinePrimitive() {
        init {
            require(start != end) { "a line needs two distinct points, both were $start" }
        }

        override fun realize(): CurveSegment.Line = CurveSegment.Line(start.toVec2(), end.toVec2())
    }

    /** Entry method 2: the segment starting at [start], running [length] font units at [angleRadians] (measured the usual way, counter-clockwise from the positive x-axis). */
    data class PointAngleLength(
        val start: Point,
        val angleRadians: Double,
        val length: Double,
    ) : LinePrimitive() {
        init {
            require(length > 0.0) { "line length must be positive, was $length" }
        }

        override fun realize(): CurveSegment.Line {
            val startVec = start.toVec2()
            val end = startVec + Vec2(cos(angleRadians), sin(angleRadians)) * length
            return CurveSegment.Line(startVec, end)
        }
    }

    /**
     * Entry method 3: a line tangent to [curve] at the point on it at parameter [t] (`0` its start,
     * `1` its end — the direct-manipulation reading of "point, tangent to a curve": the user's point
     * snaps onto the curve, giving a parameter there, and the tool draws the tangent through it), of
     * [length] font units, centred on that point (`length / 2` each way along the tangent direction —
     * an unambiguous, general placement, since a tangent line itself has no other natural start/end).
     * Direction comes from [tangentAt], the exact derivative of [curve]'s own parametrization, so this
     * is exact for a [CurveSegment.Line], [CurveSegment.Quadratic] or [CurveSegment.Cubic] alike —
     * never an approximation specific to one curve kind.
     */
    data class TangentToCurve(
        val curve: CurveSegment,
        val t: Double,
        val length: Double,
    ) : LinePrimitive() {
        init {
            require(t in 0.0..1.0) { "t must be in [0, 1], was $t" }
            require(length > 0.0) { "line length must be positive, was $length" }
        }

        override fun realize(): CurveSegment.Line {
            val point = curve.pointAt(t)
            val direction = curve.tangentAt(t).normalizedOrNull() ?: error("curve $curve is degenerate at t=$t (zero-length tangent)")
            val half = direction * (length / 2.0)
            return CurveSegment.Line(point - half, point + half)
        }
    }
}
