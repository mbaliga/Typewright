// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.engine.trace

import dev.aarso.typewright.core.geometry.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The shoelace signed area of a closed, cyclic polyline (last point not repeated -- [traceContours]'s own convention). */
private fun signedArea(polyline: List<Vec2>): Double {
    var sum = 0.0
    for (i in polyline.indices) {
        val a = polyline[i]
        val b = polyline[(i + 1) % polyline.size]
        sum += a.x * b.y - b.x * a.y
    }
    return sum / 2.0
}

/**
 * The exact area marching squares "chamfers" off of every genuine 90-degree corner it traces (a
 * cell with exactly one corner sample on the minority side interpolates a straight cut across that
 * cell rather than reproducing a true right angle -- an inherent, well-known property of linear
 * interpolation between only four samples, not a defect: see
 * [MarchingSquaresTest.aFilledRectangleTracesToExactlyOneLoopOfTheRightAreaAndBounds]'s KDoc). At
 * isovalue 0.5 on a binary field, that cut is always the hypotenuse between the midpoints of the
 * cell's two crossed edges, i.e. a right triangle of legs `0.5`: area `0.5 * 0.5 * 0.5 = 0.125`.
 */
private const val RIGHT_ANGLE_CORNER_CHAMFER_AREA = 0.125

/** The area a point-sampled, binary-traced `w x h` axis-aligned rectangle actually measures at: its 4 corners each lose [RIGHT_ANGLE_CORNER_CHAMFER_AREA]. */
private fun chamferedRectangleArea(
    w: Int,
    h: Int,
): Double = w.toDouble() * h.toDouble() - 4 * RIGHT_ANGLE_CORNER_CHAMFER_AREA

class MarchingSquaresTest {
    @Test
    fun defaultIsovalueIsTheMidpointOfABinaryField() {
        assertApproxEquals(0.5, DEFAULT_MARCHING_SQUARES_ISOVALUE, tolerance = 1e-9)
    }

    @Test
    fun anAllBackgroundGridHasNoContours() {
        val raster = BinaryRaster.filled(10, 10)
        assertTrue(traceContours(10, 10, raster.toScalarField()).isEmpty())
    }

    @Test
    fun anAllForegroundGridHasNoContours() {
        // Solid ink everywhere: there is no boundary inside the grid to find.
        val raster = BinaryRaster.filled(10, 10, value = true)
        assertTrue(traceContours(10, 10, raster.toScalarField()).isEmpty())
    }

    @Test
    fun aFilledRectangleTracesToExactlyOneLoopOfTheRightAreaAndBounds() {
        val x0 = 5
        val y0 = 4
        val w = 8
        val h = 6
        val raster = rasterizeRectangle(20, 20, x0, y0, w, h)
        val loops = traceContours(20, 20, raster.toScalarField())

        assertEquals(1, loops.size)
        val loop = loops.single()

        // Point-sampled at integer pixel centres, isovalue 0.5: the boundary sits exactly half a
        // pixel outside the last true-valued sample on each side.
        assertApproxEquals(x0 - 0.5, loop.minOf { it.x })
        assertApproxEquals(x0 + w - 0.5, loop.maxOf { it.x })
        assertApproxEquals(y0 - 0.5, loop.minOf { it.y })
        assertApproxEquals(y0 + h - 0.5, loop.maxOf { it.y })

        assertApproxEquals(chamferedRectangleArea(w, h), kotlin.math.abs(signedArea(loop)))
    }

    @Test
    fun aRectangularRingTracesToTwoLoopsOfOppositeWinding() {
        val outer = rasterizeRectangle(30, 30, x0 = 5, y0 = 5, rectWidth = 20, rectHeight = 20)
        val ring =
            BinaryRaster(
                30,
                30,
                BooleanArray(30 * 30) { i ->
                    val x = i % 30
                    val y = i / 30
                    outer[x, y] && !(x in 10 until 20 && y in 10 until 20)
                },
            )
        val loops = traceContours(30, 30, ring.toScalarField())

        assertEquals(2, loops.size)
        val areas = loops.map { signedArea(it) }.sortedBy { kotlin.math.abs(it) }
        val innerArea = kotlin.math.abs(areas[0])
        val outerArea = kotlin.math.abs(areas[1])
        assertApproxEquals(chamferedRectangleArea(10, 10), innerArea) // the 10x10 hole, corners chamfered the same way
        assertApproxEquals(chamferedRectangleArea(20, 20), outerArea) // the 20x20 outer square
        // The outer loop encloses ink on its inner side, the inner (hole) loop encloses ink on
        // its outer side: a consistent tracer gives them opposite winding signs.
        assertTrue(areas[0] * areas[1] < 0.0, "the outer boundary and the hole's boundary must wind oppositely")
    }

    @Test
    fun aSingleIsolatedForegroundPixelTracesToASmallDiamond() {
        val raster = BinaryRaster.filled(9, 9).withInkAt(listOf(4 to 4))
        val loops = traceContours(9, 9, raster.toScalarField())

        assertEquals(1, loops.size)
        val loop = loops.single()
        assertEquals(4, loop.size)
        assertApproxEquals(0.5, kotlin.math.abs(signedArea(loop)))
    }

    @Test
    fun distinctContoursStayDistinctAndClosed() {
        val raster =
            rasterizeRectangle(30, 10, x0 = 1, y0 = 1, rectWidth = 4, rectHeight = 4)
                .let { first ->
                    val ink = first.ink.copyOf()
                    for (y in 1 until 5) for (x in 15 until 19) ink[y * 30 + x] = true
                    BinaryRaster(30, 10, ink)
                }
        val loops = traceContours(30, 10, raster.toScalarField())
        assertEquals(2, loops.size)
        for (loop in loops) assertApproxEquals(chamferedRectangleArea(4, 4), kotlin.math.abs(signedArea(loop)))
    }

    @Test
    fun rejectsATooSmallGrid() {
        val raster = BinaryRaster.filled(1, 5)
        kotlin.test.assertFailsWith<IllegalArgumentException> { traceContours(1, 5, raster.toScalarField()) }
    }

    private fun assertApproxEquals(
        expected: Double,
        actual: Double,
        tolerance: Double = 1e-9,
    ) {
        assertTrue(kotlin.math.abs(expected - actual) <= tolerance, "expected $expected, got $actual (tolerance $tolerance)")
    }
}
