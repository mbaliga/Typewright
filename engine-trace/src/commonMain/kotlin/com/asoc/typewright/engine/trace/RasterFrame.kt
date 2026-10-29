// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.trace

import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.CurveSegment
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.core.geometry.pointAt
import com.asoc.typewright.core.geometry.segments
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Where a raster sits in font space, so a contour traced in pixels lands on the em square without a later
 * guess: font unit `(x, y)` (y up) is at area-space pixel `((x - leftUnits) * pxPerUnit, (topUnits - y) * pxPerUnit)`.
 * A pixel `(i, j)` covers the area `[i, i + 1) x [j, j + 1)`, so the sample the trace chain calls `(i, j)` is the
 * pixel's centre, at `(i + 0.5, j + 0.5)` in area space; [unitsAt] and [indexAt] are that conversion both ways.
 * The frame is what a captured cell carries from its template (the baseline and left guide are known) and what
 * a test render carries from where it drew the glyph.
 */
data class RasterFrame(
    val pxPerUnit: Double,
    val leftUnits: Double,
    val topUnits: Double,
) {
    init {
        require(pxPerUnit > 0.0) { "pxPerUnit must be positive, was $pxPerUnit" }
    }

    /** Font units per pixel. */
    val unitsPerPx: Double get() = 1.0 / pxPerUnit

    /** The font-unit position of the sample at index-space ([ix], [iy]). */
    fun unitsAt(
        ix: Double,
        iy: Double,
    ): Vec2 = Vec2(leftUnits + (ix + 0.5) / pxPerUnit, topUnits - (iy + 0.5) / pxPerUnit)

    /** The index-space position of the font-unit point ([x], [y]). */
    fun indexAt(
        x: Double,
        y: Double,
    ): Vec2 = Vec2((x - leftUnits) * pxPerUnit - 0.5, (topUnits - y) * pxPerUnit - 0.5)
}

/**
 * The fraction of each pixel that [contours] cover, exactly (not sampled), as a `width * height` array in 0..1: a
 * non-zero fill, so a counter wound the other way is a hole. It is the "signed-area accumulation" scheme font
 * rasterisers use: every edge adds the signed trapezoid it sweeps to the pixels it crosses, and a running sum along
 * each row turns that into coverage. The trace uses it to redraw a fitted outline for the Review stage's comparison
 * with the drawing it came from.
 */
fun coverageOf(
    contours: List<Contour>,
    frame: RasterFrame,
    width: Int,
    height: Int,
): DoubleArray {
    val stride = width + 2
    val accumulation = DoubleArray(stride * height)
    for (contour in contours) {
        val points = flattenToArea(contour, frame)
        for (i in points.indices) accumulateEdge(accumulation, stride, width, height, points[i], points[(i + 1) % points.size])
    }
    val coverage = DoubleArray(width * height)
    for (y in 0 until height) {
        var running = 0.0
        for (x in 0 until width) {
            running += accumulation[y * stride + x]
            coverage[y * width + x] = min(abs(running), 1.0)
        }
    }
    return coverage
}

private fun flattenToArea(
    contour: Contour,
    frame: RasterFrame,
): List<Vec2> {
    val out = mutableListOf<Vec2>()
    for (segment in contour.segments()) {
        val steps = if (segment is CurveSegment.Line) 1 else 24
        for (s in 0 until steps) {
            val p = segment.pointAt(s.toDouble() / steps)
            out += Vec2((p.x - frame.leftUnits) * frame.pxPerUnit, (frame.topUnits - p.y) * frame.pxPerUnit)
        }
    }
    return out
}

private fun accumulateEdge(
    buffer: DoubleArray,
    stride: Int,
    width: Int,
    height: Int,
    from: Vec2,
    to: Vec2,
) {
    if (from.y == to.y) return
    val direction = if (from.y < to.y) 1.0 else -1.0
    val top = if (from.y < to.y) from else to
    val bottom = if (from.y < to.y) to else from
    val dxdy = (bottom.x - top.x) / (bottom.y - top.y)
    var x = top.x
    if (top.y < 0.0) x -= top.y * dxdy
    val firstRow = max(top.y, 0.0).toInt()
    val lastRow = min(height.toDouble(), ceil(bottom.y)).toInt()
    for (row in firstRow until lastRow) {
        val dy = min(row + 1.0, bottom.y) - max(row.toDouble(), top.y)
        val xNext = x + dxdy * dy
        val d = dy * direction
        val xLow = min(x, xNext).coerceIn(0.0, width.toDouble())
        val xHigh = max(x, xNext).coerceIn(0.0, width.toDouble())
        val xMid = (xLow + xHigh) / 2.0
        val lowFloor = floor(xLow)
        val lowIndex = lowFloor.toInt()
        val highCeil = ceil(xHigh)
        val highIndex = highCeil.toInt()
        val base = row * stride
        if (highIndex <= lowIndex + 1) {
            val mid = xMid - lowFloor
            buffer[base + lowIndex] += d - d * mid
            buffer[base + lowIndex + 1] += d * mid
        } else {
            val s = 1.0 / (xHigh - xLow)
            val lowFraction = xLow - lowFloor
            val a0 = 0.5 * s * (1.0 - lowFraction) * (1.0 - lowFraction)
            val highFraction = xHigh - highCeil + 1.0
            val am = 0.5 * s * highFraction * highFraction
            buffer[base + lowIndex] += d * a0
            if (highIndex == lowIndex + 2) {
                buffer[base + lowIndex + 1] += d * (1.0 - a0 - am)
            } else {
                val a1 = s * (1.5 - lowFraction)
                buffer[base + lowIndex + 1] += d * (a1 - a0)
                for (xi in lowIndex + 2 until highIndex - 1) buffer[base + xi] += d * s
                val a2 = a1 + (highIndex - lowIndex - 3) * s
                buffer[base + highIndex - 1] += d * (1.0 - a2 - am)
            }
            buffer[base + highIndex] += d * am
        }
        x = xNext
    }
}
