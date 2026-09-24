package dev.aarso.typewright.core.geometry

import kotlin.math.sqrt

/**
 * An integer point in font units, y up (CLAUDE.md: "integers at rest"). This is the coordinate
 * type a [Contour] stores: what a `glyf` table or a `.glif` file actually has on disk.
 *
 * See the module overview (`package-info.kt`) for why algorithm results use [Vec2] instead.
 */
data class Point(
    val x: Int,
    val y: Int,
) {
    operator fun plus(other: Point): Point = Point(x + other.x, y + other.y)

    operator fun minus(other: Point): Point = Point(x - other.x, y - other.y)

    /** This point's exact value as a double-precision [Vec2], for use inside an algorithm. */
    fun toVec2(): Vec2 = Vec2(x.toDouble(), y.toDouble())
}

/**
 * A double-precision 2D vector, used only inside algorithms (CLAUDE.md: "floats only inside
 * algorithms"): curve evaluation, implied on-curve midpoints, extrema and signed area all produce
 * values that are not generally integers even though the [Point]s that define the curve are.
 */
data class Vec2(
    val x: Double,
    val y: Double,
) {
    operator fun plus(other: Vec2): Vec2 = Vec2(x + other.x, y + other.y)

    operator fun minus(other: Vec2): Vec2 = Vec2(x - other.x, y - other.y)

    operator fun times(scalar: Double): Vec2 = Vec2(x * scalar, y * scalar)

    /** The dot product of this vector with [other]. */
    fun dot(other: Vec2): Double = x * other.x + y * other.y

    /** The 2D "cross product" (the z component of the 3D cross product) with [other]. */
    fun cross(other: Vec2): Double = x * other.y - y * other.x

    /** This vector's Euclidean length. */
    fun length(): Double = sqrt(x * x + y * y)
}

/** The exact midpoint of [a] and [b]. */
fun midpoint(
    a: Vec2,
    b: Vec2,
): Vec2 = Vec2((a.x + b.x) / 2.0, (a.y + b.y) / 2.0)
