// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.geometry

import kotlin.math.floor

/**
 * The extent of a glyph's ink in font units: the box that just holds the drawn outline, rounded to
 * the nearest unit (integers at rest). It is measured on the curves themselves, not on their
 * control points, so an off-curve handle that sticks out past a curve's real extreme (an overshoot
 * drawn with long handles, say) does not widen it: sidebearings are the distance to the ink, not to
 * the handles.
 */
data class OutlineBounds(
    val xMin: Int,
    val yMin: Int,
    val xMax: Int,
    val yMax: Int,
)

/** The left and right sidebearings of a glyph, measured from [OutlineBounds] and the advance width. */
data class Sidebearings(
    val left: Int,
    val right: Int,
)

/**
 * The bounds of every contour's curves, or `null` for a glyph with no contours. Each segment
 * contributes its two anchors and any interior extreme in x or y ([extremaT]), so the answer is
 * exact for lines, quadratics and cubics alike before it is rounded once, at the end.
 */
fun Glyph.outlineBounds(): OutlineBounds? {
    var xMin = Double.POSITIVE_INFINITY
    var yMin = Double.POSITIVE_INFINITY
    var xMax = Double.NEGATIVE_INFINITY
    var yMax = Double.NEGATIVE_INFINITY

    fun include(v: Vec2) {
        if (v.x < xMin) xMin = v.x
        if (v.x > xMax) xMax = v.x
        if (v.y < yMin) yMin = v.y
        if (v.y > yMax) yMax = v.y
    }
    var any = false
    for (contour in contours) {
        for (segment in contour.segments()) {
            any = true
            include(segment.start)
            include(segment.end)
            for (axis in Axis.entries) {
                for (t in segment.extremaT(axis)) include(segment.pointAt(t))
            }
        }
    }
    if (!any) return null
    return OutlineBounds(roundHalfUp(xMin), roundHalfUp(yMin), roundHalfUp(xMax), roundHalfUp(yMax))
}

/**
 * This glyph's sidebearings: `left = xMin` and `right = advanceWidth - xMax` of [outlineBounds], or
 * `null` when there is no ink to measure from. This is the one definition: the Space and Draw
 * readouts and the command that sets sidebearings all call it, so a number shown is the number set.
 */
fun Glyph.sidebearings(): Sidebearings? {
    val bounds = outlineBounds() ?: return null
    return Sidebearings(left = bounds.xMin, right = advanceWidth - bounds.xMax)
}

// Half-up, not banker's: a whole-unit shift of the outline then shifts the rounded result by the
// same whole number, which is what lets a set sidebearing read back as exactly the value set.
private fun roundHalfUp(value: Double): Int = floor(value + 0.5).toInt()
