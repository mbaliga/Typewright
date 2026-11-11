// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.construct

import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.ContourPoint
import com.asoc.typewright.core.geometry.Vec2
import kotlin.math.abs

/**
 * The ellipse that exactly fills the box from ([minX], [minY]) to ([maxX], [maxY]), as a closed counter-clockwise
 * contour of four on-curve points, one on each extreme (right, top, left, bottom), each with its two handles and no
 * other node: what a type designer draws. A square box gives a circle. The extremes are the box's edges to within
 * the rounding of whole font units.
 */
fun ellipseInBox(
    minX: Double,
    minY: Double,
    maxX: Double,
    maxY: Double,
): Contour {
    require(maxX > minX && maxY > minY) { "the box must have area, was ($minX, $minY) to ($maxX, $maxY)" }
    return ellipseContour(Vec2((minX + maxX) / 2.0, (minY + maxY) / 2.0), (maxX - minX) / 2.0, (maxY - minY) / 2.0, 0.0)
}

/** The rectangle filling the box, as a closed counter-clockwise contour of four on-curve corners with straight sides. */
fun rectangleInBox(
    minX: Double,
    minY: Double,
    maxX: Double,
    maxY: Double,
): Contour {
    require(maxX > minX && maxY > minY) { "the box must have area, was ($minX, $minY) to ($maxX, $maxY)" }
    return axisAlignedRectangleContour(minX, minY, maxX, maxY).withStraightSidesAsLines()
}

/**
 * The rectangle filling the box with all four corners rounded by [radius] (held to what the box allows: never more
 * than half its shorter side), as a closed counter-clockwise contour of eight on-curve points, two on every corner,
 * with straight sides between the arcs. A radius that rounds to nothing gives the plain rectangle.
 */
fun roundedRectangleInBox(
    minX: Double,
    minY: Double,
    maxX: Double,
    maxY: Double,
    radius: Double,
): Contour {
    require(maxX > minX && maxY > minY) { "the box must have area, was ($minX, $minY) to ($maxX, $maxY)" }
    require(radius >= 0.0) { "the corner radius must not be negative, was $radius" }
    return roundedRectangleContour(Box(minX, minY, maxX, maxY), radius, radius, radius, radius).withStraightSidesAsLines()
}

/**
 * Every straight side of this cubic contour written the way a `.glif` stores a line: both controls on the side's own
 * ends, so it counts as a line and adds no off-curve points. A side counts as straight when both its controls lie on
 * the line between its ends to within the rounding of whole font units.
 */
private fun Contour.withStraightSidesAsLines(): Contour {
    val updated = points.toMutableList()
    for (start in points.indices step 3) {
        val end = (start + 3) % points.size
        val a = points[start].point
        val b = points[end].point
        val length = Vec2((b.x - a.x).toDouble(), (b.y - a.y).toDouble()).length()
        if (length == 0.0) continue

        fun offLine(i: Int): Double {
            val p = points[i].point
            return abs(((b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)).toDouble()) / length
        }
        if (offLine(start + 1) <= STRAIGHT_UNITS && offLine(start + 2) <= STRAIGHT_UNITS) {
            updated[start + 1] = ContourPoint(a, onCurve = false)
            updated[start + 2] = ContourPoint(b, onCurve = false)
        }
    }
    return copy(points = updated)
}

private const val STRAIGHT_UNITS = 0.51
