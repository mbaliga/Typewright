package dev.aarso.typewright.core.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sign
import kotlin.math.sqrt

private const val CURVATURE_EPSILON = 1e-9

/**
 * [harmoniseCurvatureAtJoin]'s default: how far apart (in degrees) the incoming and outgoing
 * handle directions at a join may be and still count as "already smooth" (harmonise adjusts
 * handle *length* only — never direction, per its own contract — so it requires an already-smooth
 * join to begin with, rather than silently forcing one). `2.0` degrees is loose enough to absorb
 * the handle-direction noise an integer-grid-snapped smooth point picks up at ordinary stem-scale
 * handle lengths (a handle a few hundred units long, off by a couple of grid units at its tip,
 * reads as a small fraction of a degree — well under this), while still catching a join that is
 * genuinely a corner (handles pointing in unrelated directions, often tens of degrees apart).
 */
const val DEFAULT_HARMONISE_TANGENT_TOLERANCE_DEGREES: Double = 2.0

/**
 * This cubic segment's signed curvature (kappa) at parameter [t] (`0` is [CurveSegment.Cubic.start],
 * `1` is [CurveSegment.Cubic.end]), by the standard plane-curve formula
 * `kappa(t) = cross(B'(t), B''(t)) / |B'(t)|^3`, with `B'`/`B''` the Bezier's own first and second
 * derivatives (`B'(t) = 3(1-t)^2(P1-P0) + 6(1-t)t(P2-P1) + 3t^2(P3-P2)`,
 * `B''(t) = 6(1-t)(P2-2P1+P0) + 6t(P3-2P2+P1)`, the standard cubic-Bezier derivatives). At `t=0`
 * this reduces exactly to the same `kappa(0) = (2/3) * cross(P1-P0, P2-P1) / |P1-P0|^3` closed form
 * `HobbySpline.kt`'s own top KDoc already cites and verifies (`docs/OPEN_QUESTIONS.md` item 23); at
 * `t=1` it reduces, by the same algebra with the segment's roles reversed, to
 * `kappa(1) = (2/3) * cross(P2-P1, P3-P2) / |P3-P2|^3` — both reductions are this file's own unit
 * tests (`CurvatureTest.curvatureAtMatchesClosedFormAtBothEndpoints`), not just asserted here.
 *
 * A zero-speed point (`|B'(t)|` effectively zero — a cusp, or a fully degenerate segment) has no
 * well-defined curvature to measure; this reads as `0.0` there, the same "nothing to measure, so
 * it reads as the least eventful answer" convention [Contour.direction] and
 * [CurveSegment.Cubic.isEffectivelyStraight] already use for their own degenerate cases.
 */
fun CurveSegment.Cubic.curvatureAt(t: Double): Double {
    require(t in 0.0..1.0) { "t must be in [0, 1], was $t" }
    val u = 1.0 - t
    val p01 = control1 - start
    val p12 = control2 - control1
    val p23 = end - control2
    val velocity = p01 * (3.0 * u * u) + p12 * (6.0 * u * t) + p23 * (3.0 * t * t)
    val acceleration = (p12 - p01) * (6.0 * u) + (p23 - p12) * (6.0 * t)
    val speed = velocity.length()
    if (speed <= CURVATURE_EPSILON) return 0.0
    return velocity.cross(acceleration) / (speed * speed * speed)
}

/** Which side of a smooth join [harmoniseCurvatureAtJoin] leaves completely untouched, solving the other side's near-handle length to match its curvature. */
enum class HarmoniseFixedSide {
    /** [harmoniseCurvatureAtJoin]'s incoming segment is kept exactly as given; the outgoing segment's own first control point is the one solved for. */
    INCOMING,

    /** [harmoniseCurvatureAtJoin]'s outgoing segment is kept exactly as given; the incoming segment's own second control point is the one solved for. */
    OUTGOING,
}

/**
 * "Harmonise curvature" (`TYPEWRIGHT_BUILD_BRIEF.md` line 372-373): at a smooth on-curve join —
 * [incoming] ending exactly where [outgoing] starts, with the two segments' near handles already
 * pointing the same direction through the shared anchor within [tangentToleranceDegrees] — adjusts
 * one side's near handle *length* so the segment's curvature (kappa) matches the other side's,
 * **without moving the anchor and without changing the join's tangent direction**: the anchor
 * itself is never touched, and the surviving tangent direction is always exactly [fixedSide]'s own
 * (unchanged) handle direction, never a blend of the two.
 *
 * **Method, derived and verified directly (not eyeballed).** Write `A` for the shared anchor,
 * `T` for [fixedSide]'s own unit handle direction (the join's shared tangent), `Cin` for
 * [incoming]'s *far* control point ([CurveSegment.Cubic.control1], never moved by this function)
 * and `Cout` for [outgoing]'s *far* control point ([CurveSegment.Cubic.control2], likewise never
 * moved). Using [curvatureAt]'s own closed form at each segment's own end (`t=1` for [incoming],
 * `t=0` for [outgoing]) and substituting `control2 = A - h*T` (incoming's *near* handle, length
 * `h` along the fixed tangent) or `control1 = A + h*T` (outgoing's near handle) collapses each
 * side's curvature to a strikingly simple closed form in the *one* remaining unknown, that side's
 * own near-handle length `h`:
 * ```
 * kappa_incoming(h) = (2/3) * cross(A - Cin, T) / h^2
 * kappa_outgoing(h) = (2/3) * cross(T, Cout - A) / h^2
 * ```
 * (the algebra: `cross(u, v)` is bilinear and `cross(x, x) = 0`, so every term but the one linear
 * in `h` cancels, leaving curvature proportional to `1/h^2` with a constant — call it `K` —
 * depending only on the far point, the anchor and the fixed tangent, none of which this function
 * ever moves). Matching the free side's `K_free / h_free^2` to the fixed side's own current
 * `K_fixed / h_fixed^2` (`h_fixed` being that side's own *unchanged* handle length) gives an exact,
 * non-iterative solve: `h_free = h_fixed * sqrt(K_free / K_fixed)` — see [solveFreeHandleLength]
 * for the sign/degenerate cases this needs to rule out or special-case (an inflection, where the
 * two `K`s have opposite sign, has no real solution and is rejected explicitly rather than
 * silently producing `NaN`).
 *
 * **Verified numerically, the same way `HobbySpline.kt`'s own curvature claims are** — see
 * `CurvatureTest.harmoniseMakesCurvatureAtTheJoinConvergeFromBothSides`: curvature is sampled via
 * the independent, general [curvatureAt] formula on both segments approaching the join
 * (`t = 0.999` on the harmonised [incoming], `t = 0.001` on the harmonised [outgoing]) and shown to
 * converge to the same value the exact endpoint evaluation (`t = 1` / `t = 0`) already gives, to
 * several decimal places — not merely asserted equal by construction.
 */
fun harmoniseCurvatureAtJoin(
    incoming: CurveSegment.Cubic,
    outgoing: CurveSegment.Cubic,
    fixedSide: HarmoniseFixedSide = HarmoniseFixedSide.INCOMING,
    tangentToleranceDegrees: Double = DEFAULT_HARMONISE_TANGENT_TOLERANCE_DEGREES,
): Pair<CurveSegment.Cubic, CurveSegment.Cubic> {
    require((incoming.end - outgoing.start).length() <= CURVATURE_EPSILON) {
        "incoming and outgoing must share an anchor (incoming.end == outgoing.start); had ${incoming.end} and ${outgoing.start}"
    }
    val anchor = incoming.end
    val inHandle = anchor - incoming.control2
    val outHandle = outgoing.control1 - anchor
    val inLength = inHandle.length()
    val outLength = outHandle.length()
    require(inLength > CURVATURE_EPSILON && outLength > CURVATURE_EPSILON) {
        "harmonise needs a real (non-zero-length) handle on both sides of the join to have a tangent direction to work along; had incoming=$inLength outgoing=$outLength"
    }
    val inDirection = inHandle * (1.0 / inLength)
    val outDirection = outHandle * (1.0 / outLength)
    val cosAngle = inDirection.dot(outDirection).coerceIn(-1.0, 1.0)
    val angleDegrees = acos(cosAngle) * 180.0 / PI
    require(angleDegrees <= tangentToleranceDegrees) {
        "this join is not smooth within $tangentToleranceDegrees degrees (handles differ by $angleDegrees degrees); " +
            "harmonise adjusts handle length only and never changes tangent direction, so it requires an already-smooth join"
    }

    val tangent = if (fixedSide == HarmoniseFixedSide.INCOMING) inDirection else outDirection
    val kIncoming = (anchor - incoming.control1).cross(tangent)
    val kOutgoing = tangent.cross(outgoing.control2 - anchor)

    return when (fixedSide) {
        HarmoniseFixedSide.INCOMING -> {
            val newOutLength =
                solveFreeHandleLength(
                    fixedNumerator = kIncoming,
                    fixedLength = inLength,
                    freeNumerator = kOutgoing,
                    freeLengthIfUnconstrained = outLength,
                )
            incoming to outgoing.copy(control1 = anchor + tangent * newOutLength)
        }

        HarmoniseFixedSide.OUTGOING -> {
            val newInLength =
                solveFreeHandleLength(
                    fixedNumerator = kOutgoing,
                    fixedLength = outLength,
                    freeNumerator = kIncoming,
                    freeLengthIfUnconstrained = inLength,
                )
            incoming.copy(control2 = anchor - tangent * newInLength) to outgoing
        }
    }
}

/**
 * [contour]-level convenience for [harmoniseCurvatureAtJoin]: [onCurveIndex] must address an
 * on-curve point (`% 3 == 0`, [Contour]'s own CUBIC indexing), and the join harmonised is the one
 * at that anchor — between the segment ending there ("incoming") and the segment starting there
 * ("outgoing").
 */
fun harmoniseCurvatureAtJoin(
    contour: Contour,
    onCurveIndex: Int,
    fixedSide: HarmoniseFixedSide = HarmoniseFixedSide.INCOMING,
    tangentToleranceDegrees: Double = DEFAULT_HARMONISE_TANGENT_TOLERANCE_DEGREES,
): Contour {
    require(contour.format == CurveFormat.CUBIC) {
        "harmoniseCurvatureAtJoin operates on a CUBIC contour, was ${contour.format}"
    }
    require(onCurveIndex in contour.points.indices && onCurveIndex % 3 == 0) {
        "onCurveIndex must address an on-curve point (index % 3 == 0) within ${contour.points.indices}, was $onCurveIndex"
    }
    val segments = contour.cubicSegments()
    val segmentCount = segments.size
    val outgoingIndex = onCurveIndex / 3
    val incomingIndex = (outgoingIndex - 1 + segmentCount) % segmentCount
    val (newIncoming, newOutgoing) =
        harmoniseCurvatureAtJoin(segments[incomingIndex], segments[outgoingIndex], fixedSide, tangentToleranceDegrees)
    val newSegments = segments.toMutableList()
    newSegments[incomingIndex] = newIncoming
    newSegments[outgoingIndex] = newOutgoing
    return buildCubicContour(newSegments)
}

/**
 * The free side's new near-handle length, so its curvature (`(2/3) * freeNumerator / h^2`) matches
 * the fixed side's own current curvature (`(2/3) * fixedNumerator / fixedLength^2`) exactly:
 * `h = fixedLength * sqrt(freeNumerator / fixedNumerator)`. Both `Numerator`s come from
 * [harmoniseCurvatureAtJoin]'s own derivation (its KDoc) and share a sign with their side's own
 * curvature (curvature is `(2/3)/h^2` times the numerator, and `h^2` is always positive), so this
 * rules out, explicitly rather than by producing `NaN`, the cases with no real, exact solution:
 * - **Both numerators effectively zero**: both sides already read as zero curvature *for any*
 *   handle length there (see the KDoc's `1/h^2` form — a zero numerator makes the whole expression
 *   zero regardless of `h`), so nothing needs solving; the free side's own current length is
 *   returned unchanged.
 * - **Only the fixed side's numerator is zero**: the fixed side has zero curvature at the join
 *   (its own far control point already sits exactly on the tangent line) *regardless of its
 *   length*, so matching it would need the free side to also read zero — impossible for a nonzero
 *   free numerator at any finite, nonzero handle length.
 * - **Only the free side's numerator is zero**: symmetric — the free side is pinned to zero
 *   curvature there no matter what length is chosen, so it cannot be made to match a genuinely
 *   nonzero fixed curvature.
 * - **Opposite-sign numerators**: the fixed side curves one way at the join and the free side the
 *   other (an inflection); `freeNumerator / fixedNumerator` is negative, and there is no real
 *   square root — no length on the free side reproduces the fixed side's own sign of curvature.
 */
private fun solveFreeHandleLength(
    fixedNumerator: Double,
    fixedLength: Double,
    freeNumerator: Double,
    freeLengthIfUnconstrained: Double,
): Double {
    val fixedIsZero = abs(fixedNumerator) <= CURVATURE_EPSILON
    val freeIsZero = abs(freeNumerator) <= CURVATURE_EPSILON
    return when {
        fixedIsZero && freeIsZero -> {
            freeLengthIfUnconstrained
        }

        fixedIsZero -> {
            throw IllegalArgumentException(
                "the fixed side already has zero curvature at this join (its far control point sits on the tangent line); " +
                    "matching it exactly would need the free side's curvature to be zero too, which no finite handle length produces there",
            )
        }

        freeIsZero -> {
            throw IllegalArgumentException(
                "the free side's far control point sits exactly on the join's tangent line, so its own curvature there is zero " +
                    "for any handle length -- it cannot be brought to match the fixed side's nonzero curvature by length alone",
            )
        }

        fixedNumerator.sign != freeNumerator.sign -> {
            throw IllegalArgumentException(
                "this join has opposite-sign curvature on each side (an inflection); a handle-length-only adjustment cannot make " +
                    "them equal without moving a far control point or changing the tangent",
            )
        }

        else -> {
            fixedLength * sqrt(freeNumerator / fixedNumerator)
        }
    }
}
