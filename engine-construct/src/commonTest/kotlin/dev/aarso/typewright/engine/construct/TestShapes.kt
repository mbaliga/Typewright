package dev.aarso.typewright.engine.construct

import dev.aarso.typewright.core.geometry.Contour
import dev.aarso.typewright.core.geometry.ContourPoint
import dev.aarso.typewright.core.geometry.CurveFormat
import dev.aarso.typewright.core.geometry.Point
import dev.aarso.typewright.core.geometry.Vec2
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.assertTrue

// Shared constructed-shape helpers for this module's tests (CLAUDE.md: "constructed shapes where
// the correct offset/stroke/etc. is computable analytically"). Every helper below is a plain,
// general-purpose builder -- nothing here branches on which test calls it.

/** A CCW rectangle as a [CurveFormat.CUBIC] [Contour] with straight (on-line, degenerate) edges — see `CubicFitting.kt`'s own KDoc on why a straight cubic side has no shorter representation in this type. */
fun rectangleContour(
    x0: Int,
    y0: Int,
    x1: Int,
    y1: Int,
): Contour {
    val corners = listOf(Point(x0, y0), Point(x1, y0), Point(x1, y1), Point(x0, y1))
    val points =
        corners.indices.flatMap { i ->
            val start = corners[i]
            val end = corners[(i + 1) % corners.size]
            val c1 = Point(start.x + (end.x - start.x) / 3, start.y + (end.y - start.y) / 3)
            val c2 = Point(start.x + 2 * (end.x - start.x) / 3, start.y + 2 * (end.y - start.y) / 3)
            listOf(ContourPoint(start, onCurve = true), ContourPoint(c1, onCurve = false), ContourPoint(c2, onCurve = false))
        }
    return Contour(points, CurveFormat.CUBIC)
}

/** The well-known 4-arc kappa (~0.55228) cubic Bezier approximation of a circle, CCW, centred at ([cx], [cy]), radius [radius] — an independent fixture (hand-derived from the standard closed-form construction, not from this module's own [hobbyVelocity]/[offsetContour] code under test) accurate to about 0.027% of the radius. */
fun circleContour(
    cx: Double,
    cy: Double,
    radius: Double,
): Contour {
    val k = 0.5522847498307936
    // Explicit 4-arc construction (0, 90, 180, 270 degrees), standard and well known.
    val anchors =
        listOf(
            Pair(radius, 0.0),
            Pair(0.0, radius),
            Pair(-radius, 0.0),
            Pair(0.0, -radius),
        )
    val tangentOffsets =
        listOf(
            Pair(0.0, k * radius), // at (r,0): tangent direction is +y
            Pair(-k * radius, 0.0), // at (0,r): tangent direction is -x
            Pair(0.0, -k * radius), // at (-r,0): tangent direction is -y
            Pair(k * radius, 0.0), // at (0,-r): tangent direction is +x
        )
    val points = mutableListOf<ContourPoint>()
    for (i in 0 until 4) {
        val anchor = anchors[i]
        val tangent = tangentOffsets[i]
        val c1 = Pair(anchor.first + tangent.first, anchor.second + tangent.second)
        val c2Anchor = anchors[(i + 1) % 4]
        val c2Tangent = tangentOffsets[(i + 1) % 4]
        val c2 = Pair(c2Anchor.first - c2Tangent.first, c2Anchor.second - c2Tangent.second)
        points += ContourPoint(Point((cx + anchor.first).roundToInt(), (cy + anchor.second).roundToInt()), onCurve = true)
        points += ContourPoint(Point((cx + c1.first).roundToInt(), (cy + c1.second).roundToInt()), onCurve = false)
        points += ContourPoint(Point((cx + c2.first).roundToInt(), (cy + c2.second).roundToInt()), onCurve = false)
    }
    return Contour(points, CurveFormat.CUBIC)
}

/** A dense CCW polygon approximating a circle, useful as an exact-radius reference to measure an offset/stroke result against (every vertex sits exactly `radius` from centre, unlike [circleContour]'s cubic approximation, which only *reads* like a circle). */
fun denseCirclePoints(
    cx: Double,
    cy: Double,
    radius: Double,
    count: Int = 720,
): List<Point> =
    (0 until count).map { i ->
        val angle = 2.0 * kotlin.math.PI * i / count
        Point((cx + radius * cos(angle)).roundToInt(), (cy + radius * sin(angle)).roundToInt())
    }

/**
 * The local "ink width" at each of [samplePoints], measured as the sum of that point's own
 * nearest distance to [outer] and to [inner] (each flattened to a dense polyline first) — a
 * general proxy for "how thick is the stroke here" that needs no per-point correspondence between
 * a sample point and either boundary (task P5a-hard item 2's own "measure the resulting stroke
 * width at many points"). Test-only measurement tooling, not production API — see `ObliqueTest.kt`.
 */
fun measureRingWidths(
    samplePoints: List<Vec2>,
    outer: Contour,
    inner: Contour,
    tolerance: Double = DEFAULT_FLATTEN_TOLERANCE,
): List<Double> {
    val outerPoly = outer.flattenToPolyline(tolerance)
    val innerPoly = inner.flattenToPolyline(tolerance)
    return samplePoints.map { p -> distanceToPolyline(p, outerPoly) + distanceToPolyline(p, innerPoly) }
}

private fun distanceToPolyline(
    p: Vec2,
    polyline: List<Vec2>,
): Double {
    val n = polyline.size
    var best = Double.MAX_VALUE
    for (i in 0 until n) {
        val d = distanceToSegment(p, polyline[i], polyline[(i + 1) % n])
        if (d < best) best = d
    }
    return best
}

private fun distanceToSegment(
    p: Vec2,
    a: Vec2,
    b: Vec2,
): Double {
    val ab = b - a
    val len2 = ab.dot(ab)
    if (len2 <= EPSILON) return (p - a).length()
    val t = ((p - a).dot(ab) / len2).coerceIn(0.0, 1.0)
    val proj = a + ab * t
    return (p - proj).length()
}

// -------------------------------------------------------------------------------------------
// Shared tolerance-based assertions (task P5b-construction-grammar's own primitive tests all use
// these, rather than each redefining its own copy).
// -------------------------------------------------------------------------------------------

/** Asserts [actual] is within [tolerance] of [expected]. */
fun assertApprox(
    expected: Double,
    actual: Double,
    tolerance: Double = 1e-6,
    message: String = "",
) {
    assertTrue(abs(expected - actual) <= tolerance, "$message expected $expected, got $actual (tolerance $tolerance)")
}

/** Asserts [actual] is within [tolerance] of [expected] on both axes. */
fun assertVec2Approx(
    expected: Vec2,
    actual: Vec2,
    tolerance: Double = 1e-6,
    message: String = "",
) {
    assertTrue(
        abs(expected.x - actual.x) <= tolerance && abs(expected.y - actual.y) <= tolerance,
        "$message expected $expected, got $actual (tolerance $tolerance)",
    )
}
