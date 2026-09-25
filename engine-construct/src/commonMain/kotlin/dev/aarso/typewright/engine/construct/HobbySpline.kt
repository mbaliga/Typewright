// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.engine.construct

import dev.aarso.typewright.core.geometry.Contour
import dev.aarso.typewright.core.geometry.ContourPoint
import dev.aarso.typewright.core.geometry.CurveFormat
import dev.aarso.typewright.core.geometry.CurveSegment
import dev.aarso.typewright.core.geometry.Point
import dev.aarso.typewright.core.geometry.Vec2
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Hobby's method (Hobby 1986, "Smooth, easy to compute interpolating splines"; Jackowski,
 * "Typography and Hobby's curves", TUGboat 34:2 — `docs/ARCHITECTURE_REVIEW.md` section 3
 * `:engine-construct`'s own pick, "Hobby first"): a sequence of knots with **no explicit
 * handles** becomes exactly one cubic Bezier per pair of consecutive knots
 * ([hobbySplineOpen]/[hobbySplineClosed]), by solving for a smooth tangent direction at every
 * knot and then placing that segment's two control points along those tangents with Hobby's own
 * published control-point-distance ("velocity") formula ([hobbyVelocity]) — this is what gives a
 * Hobby curve its distinctive shape (and, for points on a circle, the well-known kappa ~= 0.5523
 * control-point ratio — see `HobbySplineTest.circleKnotsMatchKnownKappaRatio` and this file's own
 * derivation notes below).
 *
 * **How this file's tangent solve relates to Hobby's own paper, told honestly (this build's own
 * convention — see `CubicFitting.kt`'s "Implementation note on licensing" for the precedent).**
 * [hobbyVelocity] is Hobby's own published formula, reproduced here from the general description
 * (`docs/RESEARCH_font_quality.md`, published descriptions of Hobby's algorithm and METAFONT's
 * `mp_make_choices`) and independently re-derived and verified against the standard cubic-Bezier
 * curvature formula (`kappa(0) = (2/3) * cross(P1-P0, P2-P1) / |P1-P0|^3`) to machine precision —
 * it is not a guess. Hobby's own paper additionally derives a specific *linearized* tridiagonal
 * system for the tangent **angles**, obtained by linearizing his own mock-curvature-continuity
 * condition; reproducing Knuth's exact `mp_make_choices` coefficients from a general description
 * alone (rather than the primary source) turned out, on direct testing, to be too easy to get
 * subtly wrong to trust blindly. This file solves the closely related, independently-verifiable
 * **chord-length-weighted tangent-vector system** instead (the standard parametric-cubic-spline
 * "Bessel tangent" equations — `d_i*m_(i-1) + 2*(d_(i-1)+d_i)*m_i + d_(i-1)*m_(i+1) = 3*[d_i*(z_i -
 * z_(i-1))/d_(i-1) + d_(i-1)*(z_(i+1) - z_i)/d_i]`, one tridiagonal — cyclic-tridiagonal when
 * closed — linear solve for the tangent *direction* at every knot, in exactly the spirit of "one
 * tridiagonal solve... no explicit handles" this task asks for), combined with [hobbyVelocity] for
 * the actual handle lengths. **Why this is a sound substitute, not a guess dressed up as one:** for
 * a fully symmetric configuration (points evenly spaced on a circle) the two approaches are
 * provably forced to agree — matching mock curvature on both sides of a knot that looks identical
 * from either side is satisfied by *any* uniform tangent choice there, so the real content of
 * "smooth" in that case comes from direction continuity plus the chosen tangent system, exactly
 * what this file solves — and the resulting curve reproduces the textbook kappa ~= 0.5523 circle
 * constant exactly (`4/3 * tan(pi/8)`, verified both as a closed-form ratio and as end-to-end
 * Bezier-to-circle deviation of about 0.027%, the same figure the classic 4-arc cubic circle
 * approximation is known for) and reduces to Hobby's own straight-line "1/3-chord" degenerate
 * placement for a 2-knot open path. It is reported this way, plainly, rather than claimed as a
 * literal reproduction of `mp_make_choices` it is not (this build's Honesty rule).
 *
 * **Curl and tension** (task's own "default curl 1.0 and tension 1.0... enough for v1", accepted
 * as [HobbyKnotStyle] per knot for forward compatibility). Tension follows Hobby's own formula
 * directly: it divides a segment's own handle length ([hobbyVelocity]'s result), so higher tension
 * makes a shorter, tighter handle — exactly Hobby's/METAFONT's documented effect, fully verified
 * (tension enters the same, already-verified [hobbyVelocity] formula, as `alpha`/`beta`). Curl —
 * only meaningful at an *open* path's two ends — is implemented as this file's own **documented,
 * simplified reading of curl's qualitative published role** ("the additional curling at the
 * endpoint relative to a straight continuation"): `curl = 0` places the boundary tangent exactly
 * along the endpoint's own chord (a straight run at that end); `curl = 1` (the default) uses the
 * standard one-sided (PCHIP-style, three-point) boundary tangent estimate this file computes and
 * fully tests; values scale linearly between (and beyond) those two by scaling the *angle* between
 * the chord and the natural estimate — see [boundaryTangentDirection]'s KDoc. This is not Knuth's
 * own curl formula (same honesty note as above), but it is directionally correct, trivially exact
 * at the tested default, and documented rather than silently approximated.
 */
data class HobbyKnotStyle(
    /** Only meaningful at an open path's two endpoints; see this file's own KDoc for exactly what this implementation does with it. */
    val curl: Double = 1.0,
    /** Divides the handle length of the segment *leaving* this knot (Hobby's own tension convention: higher tension, shorter handle). */
    val tensionOut: Double = 1.0,
    /** Divides the handle length of the segment *arriving at* this knot. */
    val tensionIn: Double = 1.0,
)

private fun defaultStyles(count: Int): List<HobbyKnotStyle> = List(count) { HobbyKnotStyle() }

private const val HOBBY_EPSILON = 1e-9

/**
 * Hobby's own published control-point-distance formula (the "velocity" function — see this file's
 * top KDoc for how it was verified): the ratio of a segment's own control-point distance to its
 * chord length, for a departure angle [theta] and arrival angle [phi] (both radians, relative to
 * the segment's own chord — see [buildHobbySegment]'s KDoc for the exact sign convention), before
 * dividing by tension.
 */
internal fun hobbyVelocity(
    theta: Double,
    phi: Double,
): Double {
    val sqrt2 = sqrt(2.0)
    val sqrt5 = sqrt(5.0)
    val sinTheta = sin(theta)
    val sinPhi = sin(phi)
    val num = 2.0 + sqrt2 * (sinTheta - sinPhi / 16.0) * (sinPhi - sinTheta / 16.0) * (cos(theta) - cos(phi))
    val den = 3.0 * (1.0 + 0.5 * (sqrt5 - 1.0) * cos(theta) + 0.5 * (3.0 - sqrt5) * cos(phi))
    return num / den
}

/**
 * The one cubic segment between knots at [p0] and [p1], given each end's already-solved tangent
 * *direction* [m0]/[m1] (forward direction of travel, not required to be unit — this function
 * normalizes) and this segment's own [tensionOut]/[tensionIn] (see [HobbyKnotStyle]).
 *
 * **Angle convention** (matches [hobbyVelocity]'s own derivation exactly — see this file's top
 * KDoc): writing `chord` for the unit direction from [p0] to [p1], `theta` is the signed angle
 * *from* `chord` *to* `m0` (how far the departure tangent has already turned away from the chord),
 * and `phi` is the signed angle *from* `m1` *to* `chord` (how far the arrival tangent still has to
 * turn to reach the chord) — deliberately *not* symmetric in `m0`/`m1`, reproducing exactly the
 * convention this file's own end-to-end circle verification used.
 */
internal fun buildHobbySegment(
    p0: Vec2,
    p1: Vec2,
    m0: Vec2,
    m1: Vec2,
    tensionOut: Double,
    tensionIn: Double,
): CurveSegment.Cubic {
    val chordVector = p1 - p0
    val chordLength = chordVector.length()
    val chordDir = chordVector.normalizedOrNull() ?: Vec2(1.0, 0.0)
    val m0Dir = m0.normalizedOrNull() ?: chordDir
    val m1Dir = m1.normalizedOrNull() ?: chordDir

    val theta = signedAngleBetween(chordDir, m0Dir)
    val phi = signedAngleBetween(m1Dir, chordDir)

    val l1 = hobbyVelocity(theta, phi) * chordLength / tensionOut
    val l2 = hobbyVelocity(phi, theta) * chordLength / tensionIn

    val control1 = p0 + m0Dir * l1
    val control2 = p1 - m1Dir * l2
    return CurveSegment.Cubic(p0, control1, control2, p1)
}

// -------------------------------------------------------------------------------------------
// Chord-length-weighted tangent-direction solve (see this file's top KDoc).
// -------------------------------------------------------------------------------------------

/** Standard Thomas algorithm for a (non-cyclic) tridiagonal system: `sub[i]*x[i-1] + diag[i]*x[i] + sup[i]*x[i+1] = rhs[i]` (`sub[0]` and `sup[last]` are ignored). */
private fun solveTridiagonal(
    sub: DoubleArray,
    diag: DoubleArray,
    sup: DoubleArray,
    rhs: DoubleArray,
): DoubleArray {
    val n = diag.size
    val cPrime = DoubleArray(n)
    val dPrime = DoubleArray(n)
    cPrime[0] = sup[0] / diag[0]
    dPrime[0] = rhs[0] / diag[0]
    for (i in 1 until n) {
        val m = diag[i] - sub[i] * cPrime[i - 1]
        cPrime[i] = sup[i] / m
        dPrime[i] = (rhs[i] - sub[i] * dPrime[i - 1]) / m
    }
    val x = DoubleArray(n)
    x[n - 1] = dPrime[n - 1]
    for (i in n - 2 downTo 0) {
        x[i] = dPrime[i] - cPrime[i] * x[i + 1]
    }
    return x
}

/**
 * Solves an `n`-by-`n` dense linear system by Gaussian elimination with partial pivoting. Used
 * only for the *cyclic* tangent system ([solveCyclicTangents]): a proper cyclic-tridiagonal solver
 * (Sherman-Morrison, the standard technique) is asymptotically faster, but Hobby knots are always
 * a handful of user-placed points (never a dense per-pixel trace — this task's own item 4 is
 * explicit that the input points *are* the on-curve anchors), so a plain dense solve is simple,
 * easy to verify correct, and fast enough; this is a deliberate, documented simplicity-over-micro-
 * optimization choice, not an oversight.
 */
private fun solveDense(
    matrix: Array<DoubleArray>,
    rhs: DoubleArray,
): DoubleArray {
    val n = rhs.size
    val a = Array(n) { i -> matrix[i].copyOf() }
    val b = rhs.copyOf()
    for (col in 0 until n) {
        var pivotRow = col
        var maxAbs = abs(a[col][col])
        for (row in col + 1 until n) {
            val v = abs(a[row][col])
            if (v > maxAbs) {
                maxAbs = v
                pivotRow = row
            }
        }
        if (pivotRow != col) {
            val tmpRow = a[col]
            a[col] = a[pivotRow]
            a[pivotRow] = tmpRow
            val tmpB = b[col]
            b[col] = b[pivotRow]
            b[pivotRow] = tmpB
        }
        val pivot = a[col][col]
        require(
            abs(pivot) > 1e-12,
        ) { "singular system while solving Hobby's cyclic tangent system (duplicate or collinear-degenerate knots?)" }
        for (row in 0 until n) {
            if (row == col) continue
            val factor = a[row][col] / pivot
            if (factor == 0.0) continue
            for (c in col until n) a[row][c] -= factor * a[col][c]
            b[row] -= factor * b[col]
        }
    }
    return DoubleArray(n) { i -> b[i] / a[i][i] }
}

/** Chord lengths `d[i] = |pts[(i + 1) % n] - pts[i]|`; every consecutive pair must be distinct. */
private fun chordLengths(
    pts: List<Vec2>,
    cyclic: Boolean,
): DoubleArray {
    val count = if (cyclic) pts.size else pts.size - 1
    return DoubleArray(count) { i ->
        val d = (pts[(i + 1) % pts.size] - pts[i]).length()
        require(d > HOBBY_EPSILON) { "Hobby knots $i and ${(i + 1) % pts.size} coincide; every consecutive pair of knots must be distinct" }
        d
    }
}

/**
 * Solves for a smooth tangent *direction* at every knot of a **closed**, cyclic sequence, via the
 * cyclic chord-length-weighted tangent system (this file's top KDoc). Only directions are ever
 * used downstream ([buildHobbySegment] normalizes), so the solved vectors' own magnitude is not
 * meaningful and is not reported.
 */
internal fun solveCyclicTangents(pts: List<Vec2>): List<Vec2> {
    val n = pts.size
    val d = chordLengths(pts, cyclic = true)
    val matrix = Array(n) { DoubleArray(n) }
    val rhsX = DoubleArray(n)
    val rhsY = DoubleArray(n)
    for (i in 0 until n) {
        val im1 = (i - 1 + n) % n
        val ip1 = (i + 1) % n
        matrix[i][im1] += d[i]
        matrix[i][i] += 2.0 * (d[im1] + d[i])
        matrix[i][ip1] += d[im1]
        val dzIn = pts[i] - pts[im1]
        val dzOut = pts[ip1] - pts[i]
        rhsX[i] = 3.0 * (d[i] * dzIn.x / d[im1] + d[im1] * dzOut.x / d[i])
        rhsY[i] = 3.0 * (d[i] * dzIn.y / d[im1] + d[im1] * dzOut.y / d[i])
    }
    val mx = solveDense(matrix, rhsX)
    val my = solveDense(matrix, rhsY)
    return (0 until n).map { Vec2(mx[it], my[it]) }
}

/**
 * The standard one-sided ("PCHIP", Fritsch-Carlson) three-point boundary tangent estimate at [p0]
 * (an open path's very first — or, called with its points reversed, very last — knot), using its
 * own next two knots [p1]/[p2] and the two chord lengths [d0]=|p1-p0|, [d1]=|p2-p1|:
 * `m = [(2*d0 + d1)*(p1 - p0)/d0 - d0*(p2 - p1)/d1] / (d0 + d1)`.
 *
 * **[curl]'s documented, simplified role** (this file's top KDoc): this function measures the
 * signed angle between the plain chord direction (`p1 - p0`) and this natural estimate's own
 * direction, scales that angle by [curl], and returns the chord direction rotated by the scaled
 * angle — so `curl = 0` returns the chord direction exactly (a straight run into the boundary) and
 * `curl = 1` returns the natural estimate's own direction exactly.
 */
internal fun boundaryTangentDirection(
    p0: Vec2,
    p1: Vec2,
    p2: Vec2,
    d0: Double,
    d1: Double,
    curl: Double,
): Vec2 {
    val naturalVector = (p1 - p0) * ((2.0 * d0 + d1) / d0) - (p2 - p1) * (d0 / d1)
    val chordDir = (p1 - p0).normalizedOrNull() ?: return Vec2(1.0, 0.0)
    val naturalDir = naturalVector.normalizedOrNull() ?: chordDir
    val naturalAngle = signedAngleBetween(chordDir, naturalDir)
    val baseAngle = atan2(chordDir.y, chordDir.x)
    val finalAngle = baseAngle + curl * naturalAngle
    return Vec2(cos(finalAngle), sin(finalAngle))
}

/**
 * Solves for a smooth tangent direction at every knot of an **open** sequence: the two endpoints
 * from [boundaryTangentDirection] (mirrored at the far end — see the inline comment), every
 * interior knot from the non-cyclic chord-length-weighted tangent system ([solveTridiagonal]) with
 * the two boundary directions folded into its first/last row's own right-hand side. A 2-knot path
 * (no interior knots, no boundary formula possible — it needs a third point) is the trivial special
 * case: both ends simply face the one chord directly, which combined with [hobbyVelocity] below
 * reproduces the conventional "handles at one third of the chord" straight-line placement.
 */
private fun solveOpenTangents(
    pts: List<Vec2>,
    styles: List<HobbyKnotStyle>,
): List<Vec2> {
    val n = pts.size
    if (n == 2) {
        val chordDir = (pts[1] - pts[0]).normalizedOrNull() ?: Vec2(1.0, 0.0)
        return listOf(chordDir, chordDir)
    }
    val d = chordLengths(pts, cyclic = false)
    val startDir = boundaryTangentDirection(pts[0], pts[1], pts[2], d[0], d[1], styles[0].curl)
    val endDirReversed = boundaryTangentDirection(pts[n - 1], pts[n - 2], pts[n - 3], d[n - 2], d[n - 3], styles[n - 1].curl)
    val endDir = endDirReversed * -1.0 // reverse-walked estimate faces backward; the forward tangent at the end is its negation.

    val interiorCount = n - 2
    val subX = DoubleArray(interiorCount)
    val diagX = DoubleArray(interiorCount)
    val supX = DoubleArray(interiorCount)
    val rhsX = DoubleArray(interiorCount)
    val rhsY = DoubleArray(interiorCount)
    for (k in 0 until interiorCount) {
        val i = k + 1 // knot index, 1..n-2
        subX[k] = d[i]
        diagX[k] = 2.0 * (d[i - 1] + d[i])
        supX[k] = d[i - 1]
        val dzIn = pts[i] - pts[i - 1]
        val dzOut = pts[i + 1] - pts[i]
        rhsX[k] = 3.0 * (d[i] * dzIn.x / d[i - 1] + d[i - 1] * dzOut.x / d[i])
        rhsY[k] = 3.0 * (d[i] * dzIn.y / d[i - 1] + d[i - 1] * dzOut.y / d[i])
    }
    // Fold the known boundary tangents (used only as directions, so an arbitrary consistent
    // magnitude is fine -- unit length keeps the folded contribution on the same scale as the
    // solved-for interior vectors) into the first/last interior row's own right-hand side.
    rhsX[0] -= subX[0] * startDir.x
    rhsY[0] -= subX[0] * startDir.y
    rhsX[interiorCount - 1] -= supX[interiorCount - 1] * endDir.x
    rhsY[interiorCount - 1] -= supX[interiorCount - 1] * endDir.y

    val mx = solveTridiagonal(subX, diagX, supX, rhsX)
    val my = solveTridiagonal(subX, diagX, supX, rhsY)

    val result = mutableListOf(startDir)
    for (k in 0 until interiorCount) result += Vec2(mx[k], my[k])
    result += endDir
    return result
}

// -------------------------------------------------------------------------------------------
// Public entry points (task item 4).
// -------------------------------------------------------------------------------------------

/**
 * Hobby's method over an **open** sequence of [knots] (at least 2, all distinct from their own
 * neighbours): returns exactly one [CurveSegment.Cubic] per consecutive pair, with no fitting or
 * splitting (the knots themselves are the on-curve anchors — task item 4's own requirement).
 * `core-geometry` has no "open `Contour`" type, so the result is the segment list directly; a
 * caller building a [Contour] from it (for a stroke centreline, say) does that itself.
 */
fun hobbySplineOpen(
    knots: List<Point>,
    styles: List<HobbyKnotStyle> = defaultStyles(knots.size),
): List<CurveSegment.Cubic> {
    require(knots.size >= 2) { "an open Hobby spline needs at least 2 knots, had ${knots.size}" }
    require(styles.size == knots.size) { "styles must have one entry per knot: ${knots.size} knots, ${styles.size} styles" }
    val pts = knots.map { it.toVec2() }
    val tangents = solveOpenTangents(pts, styles)
    return (0 until pts.size - 1).map { i ->
        buildHobbySegment(pts[i], pts[i + 1], tangents[i], tangents[i + 1], styles[i].tensionOut, styles[i + 1].tensionIn)
    }
}

/**
 * Hobby's method over a **closed**, cyclic sequence of [knots] (at least 3, all distinct from
 * their own neighbours): returns exactly one cubic segment per consecutive pair (wrapping the last
 * back to the first), built into a [CurveFormat.CUBIC] [Contour].
 */
fun hobbySplineClosed(
    knots: List<Point>,
    styles: List<HobbyKnotStyle> = defaultStyles(knots.size),
): Contour {
    require(knots.size >= 3) { "a closed Hobby spline needs at least 3 knots, had ${knots.size}" }
    require(styles.size == knots.size) { "styles must have one entry per knot: ${knots.size} knots, ${styles.size} styles" }
    val pts = knots.map { it.toVec2() }
    val tangents = solveCyclicTangents(pts)
    val n = pts.size
    val segments =
        (0 until n).map { i ->
            val ip1 = (i + 1) % n
            buildHobbySegment(pts[i], pts[ip1], tangents[i], tangents[ip1], styles[i].tensionOut, styles[ip1].tensionIn)
        }
    return buildCubicContourFromSegments(segments)
}

/** Local equivalent of `core-geometry`'s own `buildCubicContour` (internal there, not visible across the module boundary): every segment contributes its own (rounded) start plus its two control points as one (on, off, off) triple. */
private fun buildCubicContourFromSegments(segments: List<CurveSegment.Cubic>): Contour {
    val points =
        segments.flatMap { segment ->
            listOf(
                ContourPoint(segment.start.roundToPointLocal(), onCurve = true),
                ContourPoint(segment.control1.roundToPointLocal(), onCurve = false),
                ContourPoint(segment.control2.roundToPointLocal(), onCurve = false),
            )
        }
    return Contour(points, CurveFormat.CUBIC)
}

private fun Vec2.roundToPointLocal(): Point = Point(x.roundToInt(), y.roundToInt())
