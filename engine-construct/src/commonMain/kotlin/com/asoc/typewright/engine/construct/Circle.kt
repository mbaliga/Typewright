// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.construct

import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.Vec2
import kotlin.math.sqrt

/**
 * The construction grammar's Circle primitive (brief section 10 M2;
 * `docs/TYPEWRIGHT_HANDOFF.md`'s own M2 list: "Circle, ellipse, superellipse: centre radius; three
 * points; tangent tangent radius; fit to points" — all four implemented here for the circle case).
 * Every entry method reduces to a centre and radius, then [CircularArc] (a full `2*PI` turn) and
 * [CircularArc.toCubicSegments] build the closed [Contour] — the same machinery [ArcPrimitive] uses,
 * so a circle and a partial arc on the same centre and radius are, by construction, geometrically
 * consistent with each other. Stays parametric until [realize] is called (see [ArcPrimitive]'s KDoc
 * for the shared model).
 */
sealed class CirclePrimitive {
    /** This primitive's current parameters, realized as a closed, counter-clockwise [Contour]. */
    abstract fun realize(): Contour

    /** Entry method 1: centred at [center], the given [radius]. */
    data class CentreRadius(
        val center: Point,
        val radius: Double,
    ) : CirclePrimitive() {
        override fun realize(): Contour = fullCircleContour(center.toVec2(), radius)
    }

    /** Entry method 2: the circle through [a], [b] and [c] ([circumcenter]). */
    data class ThreePoints(
        val a: Point,
        val b: Point,
        val c: Point,
    ) : CirclePrimitive() {
        override fun realize(): Contour {
            val center = circumcenter(a.toVec2(), b.toVec2(), c.toVec2())
            val radius = (a.toVec2() - center).length()
            return fullCircleContour(center, radius)
        }
    }

    /** Entry method 3: the circle of [radius] tangent to both [line1] and [line2] ([filletCenterAndTangents], the same fillet-centre construction [ArcPrimitive.TangentTangentRadius] uses, taken as a full circle rather than just the minor arc between the tangent points). */
    data class TangentTangentRadius(
        val line1: TwoPointLine,
        val line2: TwoPointLine,
        val radius: Double,
        val centerSide1: FilletSide,
        val centerSide2: FilletSide,
    ) : CirclePrimitive() {
        override fun realize(): Contour {
            val (center, _, _) =
                filletCenterAndTangents(line1, line2, radius, centerSide1, centerSide2)
                    ?: error("$line1 and $line2 are parallel; no circle of radius $radius is tangent to both")
            return fullCircleContour(center, radius)
        }
    }

    /**
     * Entry method 4: the least-squares best-fit circle through [points] (at least 3, not all
     * collinear) — the Kåsa method (I. Kåsa, "A circle fitting procedure and its error analysis",
     * IEEE Transactions on Instrumentation and Measurement, 1976): minimizing the *algebraic*
     * residual of `x^2 + y^2 + D*x + E*y + F = 0` over the points is linear in `(D, E, F)`, so it
     * reduces to one 3x3 normal-equations solve ([solve3x3], closed-form Cramer's rule); the fitted
     * centre is `(-D/2, -E/2)` and radius `sqrt(D^2/4 + E^2/4 - F)`, the standard completion-of-the-
     * square from that circle equation. (Kåsa's algebraic fit is known to bias the radius outward for
     * points sampled from only a small arc of the true circle — a documented limitation of the method
     * itself, not of this implementation — but it is exact for points that lie exactly on a circle,
     * which this file's own tests verify directly.)
     */
    data class FitToPoints(
        val points: List<Point>,
    ) : CirclePrimitive() {
        init {
            require(points.size >= 3) { "a circle fit needs at least 3 points, had ${points.size}" }
        }

        override fun realize(): Contour {
            val (center, radius) = kasaCircleFit(points)
            return fullCircleContour(center, radius)
        }
    }
}

/** A full-turn [CircularArc] at [center]/[radius], realized as a closed, counter-clockwise [Contour] via [CircularArc.toCubicSegments] and [buildClosedCubicContour]. */
internal fun fullCircleContour(
    center: Vec2,
    radius: Double,
): Contour = buildClosedCubicContour(CircularArc(center, radius, 0.0, TWO_PI).toCubicSegments())

/** [CirclePrimitive.FitToPoints]'s own Kåsa least-squares solve; see that type's KDoc for the method and citation. */
internal fun kasaCircleFit(points: List<Point>): Pair<Vec2, Double> {
    var sumXX = 0.0
    var sumYY = 0.0
    var sumXY = 0.0
    var sumX = 0.0
    var sumY = 0.0
    var sumXZ = 0.0
    var sumYZ = 0.0
    var sumZ = 0.0
    for (p in points) {
        val x = p.x.toDouble()
        val y = p.y.toDouble()
        val z = x * x + y * y
        sumXX += x * x
        sumYY += y * y
        sumXY += x * y
        sumX += x
        sumY += y
        sumXZ += x * z
        sumYZ += y * z
        sumZ += z
    }
    val n = points.size.toDouble()
    val (dCoef, eCoef, fCoef) =
        solve3x3(
            sumXX,
            sumXY,
            sumX,
            -sumXZ,
            sumXY,
            sumYY,
            sumY,
            -sumYZ,
            sumX,
            sumY,
            n,
            -sumZ,
        )
    val center = Vec2(-dCoef / 2.0, -eCoef / 2.0)
    val radiusSquared = dCoef * dCoef / 4.0 + eCoef * eCoef / 4.0 - fCoef
    require(radiusSquared > 0.0) { "the fitted circle has a non-positive radius^2 ($radiusSquared); are the points collinear?" }
    return center to sqrt(radiusSquared)
}

/**
 * Solves the 3x3 linear system `[[a11,a12,a13],[a21,a22,a23],[a31,a32,a33]] * [x,y,z] =
 * [b1,b2,b3]` by Cramer's rule (closed-form, via 3x3 determinants) — general for any non-singular
 * 3x3 system, not specific to a circle fit; [CirclePrimitive.FitToPoints]'s own linear least-squares
 * normal equations are its only caller.
 */
internal fun solve3x3(
    a11: Double,
    a12: Double,
    a13: Double,
    b1: Double,
    a21: Double,
    a22: Double,
    a23: Double,
    b2: Double,
    a31: Double,
    a32: Double,
    a33: Double,
    b3: Double,
): Triple<Double, Double, Double> {
    fun det3(
        m11: Double,
        m12: Double,
        m13: Double,
        m21: Double,
        m22: Double,
        m23: Double,
        m31: Double,
        m32: Double,
        m33: Double,
    ): Double = m11 * (m22 * m33 - m23 * m32) - m12 * (m21 * m33 - m23 * m31) + m13 * (m21 * m32 - m22 * m31)

    val d = det3(a11, a12, a13, a21, a22, a23, a31, a32, a33)
    require(kotlin.math.abs(d) > EPSILON) { "the 3x3 system is singular (degenerate or duplicate input points?)" }
    val dx = det3(b1, a12, a13, b2, a22, a23, b3, a32, a33)
    val dy = det3(a11, b1, a13, a21, b2, a23, a31, b3, a33)
    val dz = det3(a11, a12, b1, a21, a22, b2, a31, a32, b3)
    return Triple(dx / d, dy / d, dz / d)
}
