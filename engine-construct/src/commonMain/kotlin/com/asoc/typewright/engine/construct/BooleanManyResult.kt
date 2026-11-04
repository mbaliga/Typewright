// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.construct

import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.CurveFormat

/** What [booleanMany] made, or why it made nothing. A refusal is a sentence a person can act on, never an exception. */
sealed interface BooleanManyResult {
    /** The outline the operation leaves: every contour wound the right way (outer counter-clockwise, inner clockwise). */
    data class Done(
        val contours: List<Contour>,
    ) : BooleanManyResult

    /** Nothing was made, for [reason]. */
    data class Refused(
        val reason: String,
    ) : BooleanManyResult
}

/**
 * [op] across several [contours] at once, which [booleanOp] alone takes only two sides of.
 *
 * - [BooleanOp.UNION] joins all of them.
 * - [BooleanOp.SUBTRACT] takes the rest from the first: the first contour is the shape that stays, every other one is
 *   a cut taken out of it, so the order of [contours] is the order a person means.
 * - [BooleanOp.INTERSECT] keeps only where every contour overlaps.
 * - [BooleanOp.EXCLUDE] keeps what an odd number of them cover (folded pair by pair, in order).
 *
 * Each contour is read as one filled shape (non-zero winding), so a contour inside another that winds the other way
 * is a counter. Refused, with the reason, when fewer than two contours are given, one is quadratic (these booleans
 * work on cubic outlines), the contours touch in a way the tracer cannot resolve (an exact tangency), or nothing is
 * left. The curves of the inputs are kept exactly wherever they survive ([booleanOp]'s own restoration), so the result
 * carries no nodes the operation did not need.
 */
fun booleanMany(
    contours: List<Contour>,
    op: BooleanOp,
    params: BooleanParameters = BooleanParameters(),
): BooleanManyResult {
    if (contours.size < 2) return BooleanManyResult.Refused("A boolean needs two or more contours; select at least two.")
    if (contours.any { it.format != CurveFormat.CUBIC }) {
        return BooleanManyResult.Refused("Booleans work on cubic curves; this outline is quadratic.")
    }
    // Folded one contour at a time: booleanOp does not resolve a side whose own contours overlap, and each step's result is simple.
    val result =
        try {
            foldPairwise(contours, op, params)
        } catch (_: IllegalStateException) {
            return BooleanManyResult.Refused(TANGENT)
        }
    if (result.isEmpty()) {
        return BooleanManyResult.Refused(
            when (op) {
                BooleanOp.INTERSECT -> "Nothing is left: these shapes do not all overlap."
                BooleanOp.SUBTRACT -> "Nothing is left: the shapes taken away cover the whole of the first."
                else -> "Nothing is left."
            },
        )
    }
    return BooleanManyResult.Done(result)
}

private fun foldPairwise(
    contours: List<Contour>,
    op: BooleanOp,
    params: BooleanParameters,
): List<Contour> {
    var accumulated = listOf(contours.first())
    for (next in contours.drop(1)) {
        accumulated = booleanOp(accumulated, listOf(next), op, params)
        if (accumulated.isEmpty()) return accumulated
    }
    return accumulated
}

private const val TANGENT =
    "These outlines touch along an exact edge or at a single point, which the boolean cannot resolve. Move one a unit and try again."
