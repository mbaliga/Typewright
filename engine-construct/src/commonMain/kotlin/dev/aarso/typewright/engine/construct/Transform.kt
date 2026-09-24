package dev.aarso.typewright.engine.construct

import dev.aarso.typewright.core.geometry.Anchor
import dev.aarso.typewright.core.geometry.Contour
import dev.aarso.typewright.core.geometry.ContourPoint
import dev.aarso.typewright.core.geometry.Glyph
import dev.aarso.typewright.core.geometry.Point
import dev.aarso.typewright.core.geometry.Vec2
import dev.aarso.typewright.core.geometry.extrema
import dev.aarso.typewright.core.geometry.pointAt
import dev.aarso.typewright.core.geometry.segments
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/**
 * A 2D affine transform in font-unit ([Point]/[Vec2]) space, stored as the six coefficients of
 * `x' = a*x + c*y + e`, `y' = b*x + d*y + f` (the SVG/PostScript `matrix(a b c d e f)`
 * convention). This is `engine-construct`'s one representation for every construction-geometry
 * transform (brief section 10 item 2): [scale], [rotate], [shearX]/[shearY], [mirrorAboutPoint],
 * [mirrorAboutAxis] and their arbitrary-centre variants are all built from this type plus
 * [translate] and [andThen], never as a separate special case each.
 *
 * **Every transform in this file moves both on-curve and off-curve points** (task P5a-foundations'
 * own warning: "a naive implementation that only moves on-curve points corrupts every curve").
 * [apply] operates on a [Contour]'s whole [ContourPoint] list — on-curve and off-curve alike — and
 * every higher-level transform in this file is built only from [apply], never from a shortcut that
 * touches a subset of a contour's points.
 */
data class AffineTransform(
    val a: Double,
    val b: Double,
    val c: Double,
    val d: Double,
    val e: Double,
    val f: Double,
) {
    /** This transform applied to one exact-precision point: `(a*x + c*y + e, b*x + d*y + f)`. */
    fun apply(point: Vec2): Vec2 = Vec2(a * point.x + c * point.y + e, b * point.x + d * point.y + f)

    /** [apply] on a [Point], rounded back to the nearest integer font unit ("integers at rest"). */
    fun apply(point: Point): Point = apply(point.toVec2()).roundToPoint()

    /**
     * This transform's matrix composed with [next]'s, equivalent to applying this transform first
     * and then [next] (`p.transform(this).transform(next) == p.transform(this.andThen(next))`,
     * exactly, to floating-point tolerance — see `TransformTest`'s composition tests). Derived by
     * substituting this transform's `(x', y')` into [next]'s own `x' = a*x + c*y + e` formula and
     * collecting coefficients.
     */
    fun andThen(next: AffineTransform): AffineTransform =
        AffineTransform(
            a = next.a * a + next.c * b,
            b = next.b * a + next.d * b,
            c = next.a * c + next.c * d,
            d = next.b * c + next.d * d,
            e = next.a * e + next.c * f + next.e,
            f = next.b * e + next.d * f + next.f,
        )

    companion object {
        /** The identity transform: every point maps to itself. */
        val IDENTITY = AffineTransform(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
    }
}

private fun Vec2.roundToPoint(): Point = Point(x.roundToInt(), y.roundToInt())

/** [AffineTransform.apply], applied to every [ContourPoint] of [contour] (on-curve and off-curve alike), preserving [Contour.format]. */
fun AffineTransform.apply(contour: Contour): Contour =
    Contour(contour.points.map { cp -> ContourPoint(apply(cp.point), cp.onCurve) }, contour.format)

/** [AffineTransform.apply], applied to every contour and every anchor of [glyph]. [Glyph.advanceWidth] is left unchanged: a scalar advance has no single correct meaning under an arbitrary affine map (rotation, shear), so this function deliberately transforms only geometry — a caller that also wants to rescale the advance (for a pure horizontal [scale], say) does that itself. */
fun AffineTransform.apply(glyph: Glyph): Glyph =
    glyph.copy(
        contours = glyph.contours.map { apply(it) },
        anchors = glyph.anchors.map { anchor -> Anchor(anchor.name, apply(anchor.point)) },
    )

/** A pure translation by `(dx, dy)`. */
fun translate(
    dx: Double,
    dy: Double,
): AffineTransform = AffineTransform(1.0, 0.0, 0.0, 1.0, dx, dy)

/**
 * Wraps [op] (assumed centred on the origin, e.g. [scale] or [rotate] with no explicit centre) so
 * it instead pivots about [center]: translate [center] to the origin, apply [op], translate back.
 * Every centred transform in this file ([scale], [rotate], [shearX], [shearY]) is built this way,
 * so "an arbitrary centre point" is one shared code path rather than six separate ones.
 */
fun aboutCenter(
    center: Vec2,
    op: AffineTransform,
): AffineTransform = translate(-center.x, -center.y).andThen(op).andThen(translate(center.x, center.y))

/** Uniform or non-uniform scale by ([sx], [sy]) about [center] (the origin by default). */
fun scale(
    sx: Double,
    sy: Double = sx,
    center: Vec2 = Vec2(0.0, 0.0),
): AffineTransform = aboutCenter(center, AffineTransform(sx, 0.0, 0.0, sy, 0.0, 0.0))

/**
 * Rotation by [radians] (mathematically positive, i.e. counter-clockwise in this module's y-up
 * convention — CLAUDE.md) about [center] (the origin by default).
 */
fun rotate(
    radians: Double,
    center: Vec2 = Vec2(0.0, 0.0),
): AffineTransform {
    val cosT = cos(radians)
    val sinT = sin(radians)
    return aboutCenter(center, AffineTransform(cosT, sinT, -sinT, cosT, 0.0, 0.0))
}

/**
 * Horizontal shear by [radians] about [origin] (the origin point by default, not to be confused
 * with the coordinate origin): `x' = x + tan(radians) * (y - origin.y)`, `y' = y`. **Convention**
 * (needed unambiguous for the oblique derivation the next task builds on it with — shear the
 * centreline, restroke, per `docs/TYPEWRIGHT_HANDOFF.md` section 4 M2): a **positive** angle shifts
 * a point **rightward in proportion to its height above [origin]**, which is the standard
 * type-oblique convention — an upright glyph drawn with the baseline at y=0 leans forward (top
 * shifted right of the bottom) under a positive [radians], matching how every type designer reads
 * "a 10-degree oblique". [radians] must stay in `(-PI/2, PI/2)`, since `tan` is undefined (and the
 * shear degenerates to a vertical line) at the boundary.
 */
fun shearX(
    radians: Double,
    origin: Vec2 = Vec2(0.0, 0.0),
): AffineTransform {
    require(radians > -HALF_PI && radians < HALF_PI) { "shearX angle must be strictly between -PI/2 and PI/2 radians, was $radians" }
    return aboutCenter(origin, AffineTransform(1.0, 0.0, tan(radians), 1.0, 0.0, 0.0))
}

/**
 * Vertical shear by [radians] about [origin]: `x' = x`, `y' = y + tan(radians) * (x - origin.x)` —
 * the same "positive angle, positive-coordinate-proportional shift" convention as [shearX], with
 * the axes swapped.
 */
fun shearY(
    radians: Double,
    origin: Vec2 = Vec2(0.0, 0.0),
): AffineTransform {
    require(radians > -HALF_PI && radians < HALF_PI) { "shearY angle must be strictly between -PI/2 and PI/2 radians, was $radians" }
    return aboutCenter(origin, AffineTransform(1.0, tan(radians), 0.0, 1.0, 0.0, 0.0))
}

private const val HALF_PI = kotlin.math.PI / 2.0

/** Point reflection (180-degree rotation) about [point]: `p' = 2*point - p`. Applying this twice is the identity (`MirrorTest`). */
fun mirrorAboutPoint(point: Vec2): AffineTransform = aboutCenter(point, AffineTransform(-1.0, 0.0, 0.0, -1.0, 0.0, 0.0))

/**
 * Reflection about the infinite line through [axisPointA] and [axisPointB] (must be two distinct
 * points). Applying this twice is the identity (`MirrorTest`).
 *
 * **Derivation.** A reflection about a line through the origin at angle `phi` to the x-axis is
 * `[[cos(2 phi), sin(2 phi)], [sin(2 phi), -cos(2 phi)]]` (the standard Householder reflection
 * matrix for 2D, derivable by reflecting the two axis unit vectors and reading off where they
 * land). This function computes `phi` from the axis direction, builds that matrix, and pivots it
 * about [axisPointA] (any point on the line) with [aboutCenter], exactly like every other
 * arbitrary-centre transform in this file.
 */
fun mirrorAboutAxis(
    axisPointA: Vec2,
    axisPointB: Vec2,
): AffineTransform {
    val direction = axisPointB - axisPointA
    require(direction.length() > 0.0) { "mirrorAboutAxis needs two distinct points to define a line" }
    val phi = kotlin.math.atan2(direction.y, direction.x)
    val cos2 = cos(2.0 * phi)
    val sin2 = sin(2.0 * phi)
    return aboutCenter(axisPointA, AffineTransform(cos2, sin2, sin2, -cos2, 0.0, 0.0))
}

// -------------------------------------------------------------------------------------------
// Align and distribute (brief section 10 item 2: "align and distribute"). These operate on the
// exact, curve-aware bounding box of a Contour (anchors plus every extremum core-geometry's own
// Extrema.kt already finds), not the coarser control-point box, so a bulging curve's own true
// edge is what gets aligned rather than an inscribed approximation of it.
// -------------------------------------------------------------------------------------------

/** An axis-aligned bounding box in exact (not-yet-rounded) font-unit space. */
data class BoundingBox(
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double,
) {
    val centerX: Double get() = (minX + maxX) / 2.0
    val centerY: Double get() = (minY + maxY) / 2.0
}

/**
 * This contour's exact bounding box: every segment's two anchors plus every point core-geometry's
 * own [dev.aarso.typewright.core.geometry.Contour.extrema] finds (each evaluated exactly with
 * [dev.aarso.typewright.core.geometry.pointAt]) — the curve's own true extent, not the coarser box
 * of its on-curve and off-curve control points, which a bulging curve's control points can sit
 * well outside or well inside of.
 */
fun Contour.boundingBox(): BoundingBox {
    val segments = segments()
    val anchors = segments.flatMap { listOf(it.start, it.end) }
    val extremaPoints = extrema().map { it.segment.pointAt(it.t) }
    val all = anchors + extremaPoints
    require(all.isNotEmpty()) { "a Contour always has at least one segment" }
    return BoundingBox(
        minX = all.minOf { it.x },
        minY = all.minOf { it.y },
        maxX = all.maxOf { it.x },
        maxY = all.maxOf { it.y },
    )
}

/** The union bounding box of every contour in [this]; every list must be non-empty. */
fun List<Contour>.boundingBox(): BoundingBox {
    require(isNotEmpty()) { "boundingBox of an empty contour list is undefined" }
    val boxes = map { it.boundingBox() }
    return BoundingBox(
        minX = boxes.minOf { it.minX },
        minY = boxes.minOf { it.minY },
        maxX = boxes.maxOf { it.maxX },
        maxY = boxes.maxOf { it.maxY },
    )
}

/** Which horizontal edge (or centre) [alignHorizontally] aligns to. */
enum class HorizontalEdge { LEFT, CENTER, RIGHT }

/** Which vertical edge (or centre) [alignVertically] aligns to. */
enum class VerticalEdge { BOTTOM, MIDDLE, TOP }

private fun HorizontalEdge.of(box: BoundingBox): Double =
    when (this) {
        HorizontalEdge.LEFT -> box.minX
        HorizontalEdge.CENTER -> box.centerX
        HorizontalEdge.RIGHT -> box.maxX
    }

private fun VerticalEdge.of(box: BoundingBox): Double =
    when (this) {
        VerticalEdge.BOTTOM -> box.minY
        VerticalEdge.MIDDLE -> box.centerY
        VerticalEdge.TOP -> box.maxY
    }

/**
 * Aligns every contour in [contours] so its own [edge] lands on the *whole group's* own [edge] —
 * the common "align selection" convention (Illustrator, Figma, FontForge's own align dialog): the
 * group's overall bounding box ([List.boundingBox]) is the shared target, not any one member's own
 * box, so aligning `LEFT` moves every contour's left edge to the group's own leftmost edge and
 * aligning `CENTER` moves every contour's own centre onto the group's own horizontal centre. A
 * single contour aligned to itself is therefore always a no-op, by construction.
 */
fun alignHorizontally(
    contours: List<Contour>,
    edge: HorizontalEdge,
): List<Contour> {
    val target = edge.of(contours.boundingBox())
    return contours.map { contour ->
        val dx = target - edge.of(contour.boundingBox())
        translate(dx, 0.0).apply(contour)
    }
}

/** [alignHorizontally]'s vertical counterpart. */
fun alignVertically(
    contours: List<Contour>,
    edge: VerticalEdge,
): List<Contour> {
    val target = edge.of(contours.boundingBox())
    return contours.map { contour ->
        val dy = target - edge.of(contour.boundingBox())
        translate(0.0, dy).apply(contour)
    }
}

/**
 * Distributes [contours] evenly along x: sorted by their own bounding-box centre, the first and
 * last contour keep their own centre exactly where it is, and every contour in between is shifted
 * so consecutive centres are spaced by one even step (`(lastCenter - firstCenter) / (n - 1)`) — the
 * common "distribute centers" convention. Fewer than 3 contours has no meaningful "in between" to
 * redistribute, so [contours] is returned unchanged.
 */
fun distributeHorizontally(contours: List<Contour>): List<Contour> {
    if (contours.size < 3) return contours
    val withCenters = contours.map { it to it.boundingBox().centerX }.sortedBy { it.second }
    val firstCenter = withCenters.first().second
    val lastCenter = withCenters.last().second
    val step = (lastCenter - firstCenter) / (withCenters.size - 1)
    return withCenters.mapIndexed { index, (contour, center) ->
        val targetCenter = firstCenter + step * index
        translate(targetCenter - center, 0.0).apply(contour)
    }
}

/** [distributeHorizontally]'s vertical counterpart, distributing evenly along y. */
fun distributeVertically(contours: List<Contour>): List<Contour> {
    if (contours.size < 3) return contours
    val withCenters = contours.map { it to it.boundingBox().centerY }.sortedBy { it.second }
    val firstCenter = withCenters.first().second
    val lastCenter = withCenters.last().second
    val step = (lastCenter - firstCenter) / (withCenters.size - 1)
    return withCenters.mapIndexed { index, (contour, center) ->
        val targetCenter = firstCenter + step * index
        translate(0.0, targetCenter - center).apply(contour)
    }
}
