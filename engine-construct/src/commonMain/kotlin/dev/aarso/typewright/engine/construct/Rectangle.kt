package dev.aarso.typewright.engine.construct

import dev.aarso.typewright.core.geometry.Contour
import dev.aarso.typewright.core.geometry.CurveSegment
import dev.aarso.typewright.core.geometry.Point
import dev.aarso.typewright.core.geometry.Vec2
import kotlin.math.PI
import kotlin.math.min

/**
 * The construction grammar's Rectangle primitive (brief section 10 M2;
 * `docs/TYPEWRIGHT_HANDOFF.md`'s own M2 list: "Rectangle, rounded rectangle: two corners; centre
 * plus size; per corner radius"). Both of this primitive's own entry methods normalize to the same
 * `(minX, minY, maxX, maxY)` axis-aligned box and build the same straight-sided,
 * counter-clockwise [Contour] ([axisAlignedRectangleContour]) — a plain rectangle has no radius
 * concept, so "per corner radius" (the handoff's third listed entry method for this pair) belongs
 * only to [RoundedRectanglePrimitive] below.
 */
sealed class RectanglePrimitive {
    /** This primitive's current parameters, realized as a closed, counter-clockwise [Contour] with straight (on-line, degenerate-control) sides. */
    abstract fun realize(): Contour

    /** Entry method 1: the box with [corner1] and [corner2] as two opposite corners (in either order). */
    data class TwoCorners(
        val corner1: Point,
        val corner2: Point,
    ) : RectanglePrimitive() {
        override fun realize(): Contour {
            val box = normalizedBox(corner1, corner2)
            return axisAlignedRectangleContour(box.minX, box.minY, box.maxX, box.maxY)
        }
    }

    /** Entry method 2: the box centred at [center], [width] wide and [height] tall. */
    data class CentreSize(
        val center: Point,
        val width: Double,
        val height: Double,
    ) : RectanglePrimitive() {
        init {
            require(width > 0.0 && height > 0.0) { "width and height must be positive, were $width, $height" }
        }

        override fun realize(): Contour {
            val box = boxFromCenterSize(center.toVec2(), width, height)
            return axisAlignedRectangleContour(box.minX, box.minY, box.maxX, box.maxY)
        }
    }
}

/**
 * The construction grammar's Rounded Rectangle primitive. [TwoCorners] and [CentreSize] are the
 * same two box constructions as [RectanglePrimitive]'s own, each with one added uniform [radius];
 * [PerCornerRadius] is the handoff's third entry method, an independent radius per corner. **Corner
 * geometry:** each rounded corner is a quarter-turn [CircularArc] ([CircularArc.toCubicSegments]),
 * the same machinery [ArcPrimitive] and [CirclePrimitive] use, so a rounded rectangle's own corners
 * are exactly as accurate as a standalone circular arc of the same radius. **Radius overflow:** when
 * a corner's own requested radius (or radii) would make two adjacent corners overlap (their combined
 * radii exceed the edge length between them), every radius is scaled down by one shared factor so
 * they exactly meet — the CSS Backgrounds and Borders Module Level 3 (section 5.5, "Corner
 * Overflow") algorithm, reused here as a general, well-defined answer to an otherwise ambiguous
 * over-constrained input, rather than an arbitrary per-shape clamp.
 */
sealed class RoundedRectanglePrimitive {
    abstract fun realize(): Contour

    /** Entry method 1: [corner1]/[corner2] as [RectanglePrimitive.TwoCorners], plus a uniform [radius] on all four corners. */
    data class TwoCorners(
        val corner1: Point,
        val corner2: Point,
        val radius: Double,
    ) : RoundedRectanglePrimitive() {
        override fun realize(): Contour {
            val box = normalizedBox(corner1, corner2)
            return roundedRectangleContour(box, radius, radius, radius, radius)
        }
    }

    /** Entry method 2: [center]/[width]/[height] as [RectanglePrimitive.CentreSize], plus a uniform [radius] on all four corners. */
    data class CentreSize(
        val center: Point,
        val width: Double,
        val height: Double,
        val radius: Double,
    ) : RoundedRectanglePrimitive() {
        override fun realize(): Contour {
            val box = boxFromCenterSize(center.toVec2(), width, height)
            return roundedRectangleContour(box, radius, radius, radius, radius)
        }
    }

    /** Entry method 3: [corner1]/[corner2] as the box, with an independent radius per corner. */
    data class PerCornerRadius(
        val corner1: Point,
        val corner2: Point,
        val topLeftRadius: Double,
        val topRightRadius: Double,
        val bottomRightRadius: Double,
        val bottomLeftRadius: Double,
    ) : RoundedRectanglePrimitive() {
        override fun realize(): Contour {
            val box = normalizedBox(corner1, corner2)
            return roundedRectangleContour(box, topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius)
        }
    }
}

/** An axis-aligned box, normalized so `min <= max` on both axes. */
internal data class Box(
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double,
) {
    val width: Double get() = maxX - minX
    val height: Double get() = maxY - minY
}

internal fun normalizedBox(
    corner1: Point,
    corner2: Point,
): Box {
    require(corner1.x != corner2.x && corner1.y != corner2.y) { "corner1 $corner1 and corner2 $corner2 must differ on both axes" }
    return Box(
        minX = min(corner1.x, corner2.x).toDouble(),
        minY = min(corner1.y, corner2.y).toDouble(),
        maxX = kotlin.math.max(corner1.x, corner2.x).toDouble(),
        maxY = kotlin.math.max(corner1.y, corner2.y).toDouble(),
    )
}

internal fun boxFromCenterSize(
    center: Vec2,
    width: Double,
    height: Double,
): Box = Box(center.x - width / 2.0, center.y - height / 2.0, center.x + width / 2.0, center.y + height / 2.0)

/** A straight-sided, counter-clockwise rectangle contour — see [straightCubic]'s own KDoc for why every side is a degenerate cubic. */
internal fun axisAlignedRectangleContour(
    minX: Double,
    minY: Double,
    maxX: Double,
    maxY: Double,
): Contour {
    val corners = listOf(Vec2(minX, minY), Vec2(maxX, minY), Vec2(maxX, maxY), Vec2(minX, maxY))
    val segments = corners.indices.map { i -> straightCubic(corners[i], corners[(i + 1) % corners.size]) }
    return buildClosedCubicContour(segments)
}

/**
 * Scales [topLeft]/[topRight]/[bottomRight]/[bottomLeft] down by one shared factor, if needed, so no
 * two adjacent corners' radii sum past the edge length between them — the CSS corner-overflow
 * algorithm; see [RoundedRectanglePrimitive]'s own KDoc.
 */
internal fun clampCornerRadii(
    width: Double,
    height: Double,
    topLeft: Double,
    topRight: Double,
    bottomRight: Double,
    bottomLeft: Double,
): List<Double> {
    require(topLeft >= 0.0 && topRight >= 0.0 && bottomRight >= 0.0 && bottomLeft >= 0.0) { "corner radii must not be negative" }

    fun edgeFactor(
        a: Double,
        b: Double,
        edgeLength: Double,
    ): Double = if (a + b > edgeLength && a + b > 0.0) edgeLength / (a + b) else 1.0
    val factor =
        listOf(
            edgeFactor(topLeft, topRight, width),
            edgeFactor(bottomLeft, bottomRight, width),
            edgeFactor(topLeft, bottomLeft, height),
            edgeFactor(topRight, bottomRight, height),
        ).min()
    return listOf(topLeft * factor, topRight * factor, bottomRight * factor, bottomLeft * factor)
}

/**
 * The rounded-rectangle contour for [box] with (possibly [clampCornerRadii]-scaled) corner radii
 * [topLeft]/[topRight]/[bottomRight]/[bottomLeft]. **Layout** (counter-clockwise, starting at the
 * bottom-left corner's own arc): bottom edge, bottom-right corner arc, right edge, top-right corner
 * arc, top edge, top-left corner arc, left edge, bottom-left corner arc — each corner arc a
 * quarter-turn (`PI / 2`) counter-clockwise [CircularArc], consistent with the whole contour's own
 * winding; a corner whose radius rounds to zero after clamping is a plain sharp corner (no arc
 * segment emitted there), so a per-corner-radius rectangle with one radius left at `0` is a rectangle
 * with three rounded corners and one sharp one, general and correctly handled, not a special case.
 */
internal fun roundedRectangleContour(
    box: Box,
    topLeft: Double,
    topRight: Double,
    bottomRight: Double,
    bottomLeft: Double,
): Contour {
    val (tl, tr, br, bl) = clampCornerRadii(box.width, box.height, topLeft, topRight, bottomRight, bottomLeft)

    // Each corner: (radius, arc centre, start angle) -- the arc sweeps PI/2 counter-clockwise from
    // "the point where the incoming edge reaches this corner" to "the point where the outgoing edge
    // leaves it" (see this function's own KDoc for why every corner shares the same +PI/2 sweep).
    data class Corner(
        val radius: Double,
        val center: Vec2,
        val startAngleRadians: Double,
    )
    val corners =
        listOf(
            Corner(bl, Vec2(box.minX + bl, box.minY + bl), PI), // bottom-left: arrives via the left edge
            Corner(br, Vec2(box.maxX - br, box.minY + br), -PI / 2.0), // bottom-right: arrives via the bottom edge
            Corner(tr, Vec2(box.maxX - tr, box.maxY - tr), 0.0), // top-right: arrives via the right edge
            Corner(tl, Vec2(box.minX + tl, box.maxY - tl), PI / 2.0), // top-left: arrives via the top edge
        )
    val segments = mutableListOf<CurveSegment.Cubic>()
    for (i in corners.indices) {
        val corner = corners[i]
        val next = corners[(i + 1) % corners.size]
        val cornerSegments =
            if (corner.radius > EPSILON) {
                CircularArc(corner.center, corner.radius, corner.startAngleRadians, PI / 2.0).toCubicSegments()
            } else {
                emptyList()
            }
        segments += cornerSegments
        val edgeStart = cornerSegments.lastOrNull()?.end ?: corner.center
        val edgeEnd =
            if (next.radius > EPSILON) {
                next.center + Vec2(kotlin.math.cos(next.startAngleRadians), kotlin.math.sin(next.startAngleRadians)) * next.radius
            } else {
                next.center
            }
        if ((edgeEnd - edgeStart).length() > EPSILON) segments += straightCubic(edgeStart, edgeEnd)
    }
    return buildClosedCubicContour(segments)
}
