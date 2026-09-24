package dev.aarso.typewright.core.geometry

/**
 * Whether a [Contour]'s off-curve points are TrueType-style quadratic control points (one per
 * segment, with runs of two or more consecutive off-curve points meaning implied on-curve
 * points) or PostScript/UFO-style cubic control points (always exactly two per segment, never
 * implying anything). See the module overview for the full rationale.
 */
enum class CurveFormat {
    QUADRATIC,
    CUBIC,
}

/** One point of a [Contour]: a coordinate plus whether the curve actually passes through it. */
data class ContourPoint(
    val point: Point,
    val onCurve: Boolean,
)

/**
 * A closed, cyclic sequence of tagged points, read according to [format]. See the module overview
 * for what "cyclic" and "closed" mean for each format, and why this one type serves both.
 *
 * [points] is never empty: a glyph whose contour has no coordinates at all is represented by
 * leaving it out of [Glyph.contours], not by an empty [Contour] (see the KDoc on
 * [onCurveEquivalentCount] for why that distinction matters to a caller counting nodes).
 *
 * A [CurveFormat.CUBIC] contour additionally must read as whole (on, off, off) triples starting
 * at an on-curve point: `points.size % 3 == 0` and `points[3 * k].onCurve` for every `k`. This is
 * `core-geometry`'s normalized form. UFO's `.glif` format allows a closed contour's point list to
 * start at an off-curve point (the first segment then wraps around to the last on-curve point);
 * a reader that encounters that is responsible for rotating the list to start on-curve before
 * constructing a [Contour], so that every function in this module can rely on the invariant
 * instead of re-deriving it. [CurveFormat.QUADRATIC] has no such requirement: a TrueType contour
 * may start anywhere, including on an off-curve point, or have no on-curve point at all.
 */
data class Contour(
    val points: List<ContourPoint>,
    val format: CurveFormat,
) {
    init {
        require(points.isNotEmpty()) {
            "a Contour must have at least one point; represent a glyph with no coordinates by omitting the contour, " +
                "not by Contour(emptyList(), ...)"
        }
        if (format == CurveFormat.CUBIC) {
            require(points.size % 3 == 0) {
                "a CUBIC contour's point count must be a multiple of 3 (on, off, off triples), was ${points.size}"
            }
            for (i in points.indices step 3) {
                require(points[i].onCurve && !points[i + 1].onCurve && !points[i + 2].onCurve) {
                    "a CUBIC contour must read as (on, off, off) triples starting on-curve; point $i breaks the pattern"
                }
            }
        }
    }
}

/** A contour's winding, by the sign of its [Contour.signedArea]. */
enum class Direction {
    COUNTER_CLOCKWISE,
    CLOCKWISE,
}

/**
 * This contour's signed area in font units², positive for counter-clockwise (CLAUDE.md: outer
 * contours counter-clockwise, inner clockwise, y up). Computed by Green's theorem
 * (`Area = 1/2 ∮ (x dy − y dx)`) over the actual decoded curve segments ([Contour.segments]), not
 * over the polygon of anchor points, so a contour's curved bulge is counted exactly rather than
 * approximated. Each segment kind's exact contribution is a closed form derived directly from
 * integrating its own parametrization (verified against `fontTools.pens.areaPen.AreaPen`, the
 * MIT-licensed reference this module's design follows for curve-aware area — see
 * docs/ARCHITECTURE_REVIEW.md section 3 `:core-geometry` — and cross-checked against dense
 * numerical integration; both checks are reproduced as this file's unit tests).
 */
fun Contour.signedArea(): Double = segments().sumOf { it.signedAreaContribution() }

/** This contour's winding direction; a degenerate (zero-area) contour reads as counter-clockwise. */
fun Contour.direction(): Direction = if (signedArea() >= 0.0) Direction.COUNTER_CLOCKWISE else Direction.CLOCKWISE

/**
 * The same contour traced in the opposite direction: [Contour.signedArea] negates exactly.
 *
 * For [CurveFormat.QUADRATIC], reversing the point order is enough: a quadratic contour has no
 * "starts on-curve" contract to preserve, so [points] simply runs backwards.
 *
 * For [CurveFormat.CUBIC], reversing a single segment swaps its two control points
 * (`start -[c1, c2]-> end` becomes `end -[c2, c1]-> start`, the standard rule for reversing a
 * cubic Bezier's parametrization), and reversing the whole closed contour also reverses the
 * order the segments are visited in. Applied to the flat, triple-grouped point list, both of
 * those together are exactly a full reversal of [points] followed by rotating the last point
 * (the original start) back to the front — which is also exactly what keeps the result satisfying
 * [Contour]'s "starts on-curve, (on, off, off) triples" contract.
 */
fun Contour.reverse(): Contour =
    when (format) {
        CurveFormat.QUADRATIC -> {
            Contour(points.reversed(), format)
        }

        CurveFormat.CUBIC -> {
            val reversed = points.reversed()
            Contour(listOf(reversed.last()) + reversed.dropLast(1), format)
        }
    }
