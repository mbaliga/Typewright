// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.trace.capture

import kotlin.math.abs
import kotlin.math.sqrt

/** A point in a plane. */
data class Pt(
    val x: Double,
    val y: Double,
)

/**
 * A plane projective transform (a homography): the 3 by 3 matrix [m] (row-major, `h00 h01 h02 / h10 h11 h12 / h20 h21 h22`)
 * that carries a point of one plane to where a camera sees it in another. It is what turns a photograph of a flat sheet
 * taken at an angle back into the flat sheet: solved from the four corner marks, then applied to every point wanted.
 *
 * [fromCorrespondences] is the direct linear transform (Hartley and Zisserman, *Multiple View Geometry*, 4.1, with
 * the normalisation of 4.4): both point sets are first moved to their centroid and scaled to a mean distance of the
 * square root of two, which is what keeps the least-squares system well conditioned however far from the origin the
 * pixel coordinates are; the homography is solved in those coordinates (fixing `h22 = 1`, by the normal equations,
 * so more than four points are fitted in the least-squares sense) and the normalisation is undone afterwards.
 */
class Homography(
    val m: DoubleArray,
) {
    init {
        require(m.size == 9) { "a homography has 9 entries, had ${m.size}" }
    }

    /** Where [p] goes. A point mapped to infinity (the horizon) returns non-finite coordinates. */
    fun apply(p: Pt): Pt = apply(p.x, p.y)

    fun apply(
        x: Double,
        y: Double,
    ): Pt {
        val w = m[6] * x + m[7] * y + m[8]
        return Pt((m[0] * x + m[1] * y + m[2]) / w, (m[3] * x + m[4] * y + m[5]) / w)
    }

    /** The transform that undoes this one, or null when this one is singular. */
    fun inverse(): Homography? {
        val a = m
        val c00 = a[4] * a[8] - a[5] * a[7]
        val c01 = a[5] * a[6] - a[3] * a[8]
        val c02 = a[3] * a[7] - a[4] * a[6]
        val det = a[0] * c00 + a[1] * c01 + a[2] * c02
        if (abs(det) < 1e-300 || !det.isFinite()) return null
        val inv =
            doubleArrayOf(
                c00,
                a[2] * a[7] - a[1] * a[8],
                a[1] * a[5] - a[2] * a[4],
                c01,
                a[0] * a[8] - a[2] * a[6],
                a[2] * a[3] - a[0] * a[5],
                c02,
                a[1] * a[6] - a[0] * a[7],
                a[0] * a[4] - a[1] * a[3],
            )
        return Homography(DoubleArray(9) { inv[it] / det })
    }

    /** The transform that applies [first] and then this one. */
    fun after(first: Homography): Homography {
        val r = DoubleArray(9)
        for (i in 0 until 3) for (j in 0 until 3) r[i * 3 + j] = (0 until 3).sumOf { k -> m[i * 3 + k] * first.m[k * 3 + j] }
        return Homography(r)
    }

    companion object {
        val IDENTITY: Homography = Homography(doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0))

        /**
         * The homography carrying each of [from] to the matching point of [to], by the normalised direct linear transform;
         * null when fewer than four pairs are given, the points are degenerate (three or more collinear in a way that
         * leaves the system singular) or the answer is not finite.
         */
        fun fromCorrespondences(
            from: List<Pt>,
            to: List<Pt>,
        ): Homography? {
            require(from.size == to.size) { "need as many source points as target points" }
            if (from.size < 4) return null
            val tFrom = normalisation(from)
            val tTo = normalisation(to)
            val src = from.map { tFrom.apply(it) }
            val dst = to.map { tTo.apply(it) }
            // Normal equations of the 2N x 8 system A h = b with h22 fixed at 1.
            val ata = Array(8) { DoubleArray(8) }
            val atb = DoubleArray(8)

            fun accumulate(
                row: DoubleArray,
                rhs: Double,
            ) {
                for (i in 0 until 8) {
                    atb[i] += row[i] * rhs
                    for (j in 0 until 8) ata[i][j] += row[i] * row[j]
                }
            }
            for (k in src.indices) {
                val (x, y) = src[k]
                val (u, v) = dst[k]
                accumulate(doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y), u)
                accumulate(doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y), v)
            }
            val h = solve(ata, atb) ?: return null
            val normalised = Homography(doubleArrayOf(h[0], h[1], h[2], h[3], h[4], h[5], h[6], h[7], 1.0))
            val back = tTo.inverse() ?: return null
            val result = back.after(normalised).after(tFrom)
            if (result.m.any { !it.isFinite() }) return null
            val scale = result.m[8]
            if (abs(scale) < 1e-12) return null
            return Homography(DoubleArray(9) { result.m[it] / scale })
        }

        /** The similarity that moves the points' centroid to the origin and scales their mean distance from it to the square root of two. */
        private fun normalisation(points: List<Pt>): Homography {
            val cx = points.sumOf { it.x } / points.size
            val cy = points.sumOf { it.y } / points.size
            val mean = points.sumOf { sqrt((it.x - cx) * (it.x - cx) + (it.y - cy) * (it.y - cy)) } / points.size
            val s = if (mean > 1e-12) sqrt(2.0) / mean else 1.0
            return Homography(doubleArrayOf(s, 0.0, -s * cx, 0.0, s, -s * cy, 0.0, 0.0, 1.0))
        }

        /** Solves the square system `a x = b` by Gaussian elimination with partial pivoting; null if it is singular. */
        private fun solve(
            a: Array<DoubleArray>,
            b: DoubleArray,
        ): DoubleArray? {
            val n = b.size
            val m = Array(n) { i -> DoubleArray(n + 1) { j -> if (j < n) a[i][j] else b[i] } }
            for (col in 0 until n) {
                var pivot = col
                for (r in col + 1 until n) if (abs(m[r][col]) > abs(m[pivot][col])) pivot = r
                if (abs(m[pivot][col]) < 1e-12) return null
                val swap = m[col]
                m[col] = m[pivot]
                m[pivot] = swap
                for (r in col + 1 until n) {
                    val f = m[r][col] / m[col][col]
                    for (c in col until n + 1) m[r][c] -= f * m[col][c]
                }
            }
            val x = DoubleArray(n)
            for (r in n - 1 downTo 0) {
                var sum = m[r][n]
                for (c in r + 1 until n) sum -= m[r][c] * x[c]
                x[r] = sum / m[r][r]
            }
            return x
        }
    }
}
