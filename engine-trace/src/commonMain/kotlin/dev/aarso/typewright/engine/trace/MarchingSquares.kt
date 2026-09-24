package dev.aarso.typewright.engine.trace

import dev.aarso.typewright.core.geometry.Vec2

/**
 * A scalar sampled at every integer grid coordinate `(x, y)` for `x in 0 until width`, `y in 0
 * until height` -- what [traceContours] extracts an isocontour from. [BinaryRaster.toScalarField]
 * is the adapter the real chain ([traceBinaryToContours]) uses (`1.0` for ink, `0.0` for paper,
 * traced at [DEFAULT_MARCHING_SQUARES_ISOVALUE]); a caller with genuine grayscale or anti-aliased
 * coverage information can supply a field with intermediate values instead, and [traceContours]'s
 * linear interpolation will place a crossing exactly where that richer signal says the boundary
 * truly is, sub-pixel, rather than always exactly halfway between two pixel centres -- see that
 * function's KDoc for why the pipeline still chooses to trace the *binary* mask (post despeckle/
 * hole-fill), and [MarchingSquaresSubpixelAccuracyTest] for a direct demonstration, against a
 * synthetic anti-aliased circle, of how much sharper the same general algorithm gets when it is
 * handed that extra information.
 */
fun interface ScalarField {
    fun valueAt(
        x: Int,
        y: Int,
    ): Double
}

/** [raster] read as a [ScalarField]: `1.0` at ink, `0.0` at paper -- the natural field for [DEFAULT_MARCHING_SQUARES_ISOVALUE]. */
fun BinaryRaster.toScalarField(): ScalarField = ScalarField { x, y -> if (this[x, y]) 1.0 else 0.0 }

/** [traceContours]'s default isovalue: the midpoint between [BinaryRaster.toScalarField]'s `0.0`/`1.0`. */
const val DEFAULT_MARCHING_SQUARES_ISOVALUE: Double = 0.5

/** One of the four edges of a marching-squares grid cell: identifies a crossing exactly, so loop-stitching never needs a floating-point key. */
private sealed class GridEdge {
    /** The horizontal edge between grid vertices `(x, y)` and `(x + 1, y)`. */
    data class Horizontal(
        val x: Int,
        val y: Int,
    ) : GridEdge()

    /** The vertical edge between grid vertices `(x, y)` and `(x, y + 1)`. */
    data class Vertical(
        val x: Int,
        val y: Int,
    ) : GridEdge()
}

/**
 * Sub-pixel boundary extraction by marching squares (docs/TYPEWRIGHT_HANDOFF.md section 4 M1
 * step 3, "Contour... Sub pixel boundary extraction. Still a polygon at this point."), general
 * over any [ScalarField] -- see that type's KDoc for why this is not hardwired to [BinaryRaster].
 *
 * Returns one dense, ordered, **closed and cyclic** polyline per isocontour loop found (the
 * caller's own convention going forward, and exactly what `core-geometry`'s
 * `fitClosedContourToCubics` expects -- the last point is not the first point repeated). A trivial
 * loop of fewer than 3 points (degenerate: every crossing found happened to coincide) is dropped.
 *
 * **Method.** A grid vertex `(x, y)` is "inside" when `field.valueAt(x, y) >= isovalue`. For every
 * grid edge whose two endpoints disagree, this locates the crossing by plain linear interpolation
 * along that edge (`t = (isovalue - v0) / (v1 - v0)`) -- the sub-pixel accuracy this function's
 * name promises, and the only place this file assumes anything about [field]: that its values vary
 * roughly linearly between adjacent samples, the same assumption any marching-squares
 * implementation makes. Every edge's crossing is computed exactly once (not once per adjacent
 * cell), so the two cells sharing an edge always agree on that crossing bit-for-bit -- no
 * near-miss floating-point stitching failure is possible.
 *
 * Each `(width - 1) x (height - 1)` grid cell then reads its own four corners' inside/outside
 * pattern and emits zero, one, or two directed segments between its crossed edges, oriented so
 * that -- pieced together over the whole grid -- consecutive segments' endpoints always coincide
 * and each loop closes on itself (derived from first principles for every one of the 16 corner
 * patterns by requiring the interior region's own boundary to be traced in one consistent rotational
 * sense; the two diagonal ("saddle") patterns, where all four edges are crossed, are disambiguated
 * by the cell's average corner value against [isovalue] -- the standard resolution for marching
 * squares' one genuine ambiguity, choosing whether the two inside corners are connected through the
 * cell's centre or are two separate blobs). Loops are then traced by following each segment's own
 * end edge to the segment that starts there, using [GridEdge] identity (never point coordinates) as
 * the join key.
 *
 * **A genuine 90-degree corner is very slightly chamfered, honestly, not reproduced exactly.** A
 * cell where only one of the four corner samples sits on the minority side of [isovalue] (the
 * ordinary case at any sharp axis-aligned corner) has exactly two crossed edges, and this function
 * connects their two crossing points directly -- a straight cut across that one cell -- rather than
 * a genuine right angle, because linear interpolation over only four samples has no way to know the
 * true boundary turns sharply *inside* that cell rather than running straight through it. For a
 * binary field this cut is always the hypotenuse between the midpoints of the cell's two crossed
 * edges: a right triangle of legs `0.5`, area `0.125`, trimmed off that one corner. This is a
 * well-known, inherent property of marching squares (every implementation of it does this), not a
 * bug in this one; `MarchingSquaresTest`'s rectangle tests measure and account for it explicitly
 * rather than asserting a right angle this method structurally cannot produce.
 */
fun traceContours(
    width: Int,
    height: Int,
    field: ScalarField,
    isovalue: Double = DEFAULT_MARCHING_SQUARES_ISOVALUE,
): List<List<Vec2>> {
    require(width >= 2 && height >= 2) { "a grid must be at least 2x2 to have any cells, was ${width}x$height" }

    fun inside(
        x: Int,
        y: Int,
    ): Boolean = field.valueAt(x, y) >= isovalue

    // Every horizontal/vertical grid edge's crossing point, computed exactly once.
    val horizontalCrossing = Array(height) { arrayOfNulls<Vec2>(width - 1) }
    for (y in 0 until height) {
        for (x in 0 until width - 1) {
            val v0 = field.valueAt(x, y)
            val v1 = field.valueAt(x + 1, y)
            if ((v0 >= isovalue) != (v1 >= isovalue)) {
                val t = (isovalue - v0) / (v1 - v0)
                horizontalCrossing[y][x] = Vec2(x + t, y.toDouble())
            }
        }
    }
    val verticalCrossing = Array(height - 1) { arrayOfNulls<Vec2>(width) }
    for (y in 0 until height - 1) {
        for (x in 0 until width) {
            val v0 = field.valueAt(x, y)
            val v1 = field.valueAt(x, y + 1)
            if ((v0 >= isovalue) != (v1 >= isovalue)) {
                val t = (isovalue - v0) / (v1 - v0)
                verticalCrossing[y][x] = Vec2(x.toDouble(), y + t)
            }
        }
    }

    fun pointAt(edge: GridEdge): Vec2 =
        when (edge) {
            is GridEdge.Horizontal -> horizontalCrossing[edge.y][edge.x]!!
            is GridEdge.Vertical -> verticalCrossing[edge.y][edge.x]!!
        }

    val segmentStartPoint = mutableMapOf<GridEdge, Vec2>()
    val segmentEndEdge = mutableMapOf<GridEdge, GridEdge>()

    fun emit(
        from: GridEdge,
        to: GridEdge,
    ) {
        segmentStartPoint[from] = pointAt(from)
        segmentEndEdge[from] = to
    }

    for (y in 0 until height - 1) {
        for (x in 0 until width - 1) {
            val b00 = inside(x, y)
            val b10 = inside(x + 1, y)
            val b11 = inside(x + 1, y + 1)
            val b01 = inside(x, y + 1)
            val caseIndex = (if (b00) 1 else 0) or (if (b10) 2 else 0) or (if (b11) 4 else 0) or (if (b01) 8 else 0)
            if (caseIndex == 0 || caseIndex == 15) continue

            val bottom = GridEdge.Horizontal(x, y)
            val top = GridEdge.Horizontal(x, y + 1)
            val left = GridEdge.Vertical(x, y)
            val right = GridEdge.Vertical(x + 1, y)

            when (caseIndex) {
                1 -> {
                    emit(bottom, left)
                }

                2 -> {
                    emit(right, bottom)
                }

                3 -> {
                    emit(right, left)
                }

                4 -> {
                    emit(top, right)
                }

                6 -> {
                    emit(top, bottom)
                }

                7 -> {
                    emit(top, left)
                }

                8 -> {
                    emit(left, top)
                }

                9 -> {
                    emit(bottom, top)
                }

                11 -> {
                    emit(right, top)
                }

                12 -> {
                    emit(left, right)
                }

                13 -> {
                    emit(bottom, right)
                }

                14 -> {
                    emit(left, bottom)
                }

                5 -> {
                    val center =
                        (field.valueAt(x, y) + field.valueAt(x + 1, y) + field.valueAt(x + 1, y + 1) + field.valueAt(x, y + 1)) / 4.0
                    if (center >= isovalue) {
                        emit(bottom, right)
                        emit(top, left)
                    } else {
                        emit(bottom, left)
                        emit(top, right)
                    }
                }

                10 -> {
                    val center =
                        (field.valueAt(x, y) + field.valueAt(x + 1, y) + field.valueAt(x + 1, y + 1) + field.valueAt(x, y + 1)) / 4.0
                    if (center >= isovalue) {
                        emit(left, bottom)
                        emit(right, top)
                    } else {
                        emit(right, bottom)
                        emit(left, top)
                    }
                }
            }
        }
    }

    val visited = mutableSetOf<GridEdge>()
    val loops = mutableListOf<List<Vec2>>()
    val maxSteps = segmentStartPoint.size
    for (startEdge in segmentStartPoint.keys) {
        if (startEdge in visited) continue
        val loop = mutableListOf<Vec2>()
        var current = startEdge
        var steps = 0
        while (current !in visited && steps <= maxSteps) {
            visited += current
            loop += segmentStartPoint.getValue(current)
            val next = segmentEndEdge[current] ?: break
            steps++
            if (next == startEdge) break
            current = next
        }
        if (loop.size >= 3) loops += loop
    }
    return loops
}
