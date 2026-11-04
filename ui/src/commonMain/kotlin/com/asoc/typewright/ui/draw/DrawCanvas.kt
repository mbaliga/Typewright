// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.CurveSegment
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Guideline
import com.asoc.typewright.core.geometry.PenAnchor
import com.asoc.typewright.core.geometry.PenPath
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.core.geometry.handleOwner
import com.asoc.typewright.core.geometry.isRetracted
import com.asoc.typewright.core.geometry.pointAt
import com.asoc.typewright.core.geometry.segmentLayout
import com.asoc.typewright.core.geometry.segments
import com.asoc.typewright.project.PointRef
import com.asoc.typewright.ui.addContour
import com.asoc.typewright.ui.puck.PrimitiveShape
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** A selection shape being swept on empty canvas, in screen dp: a rectangle for a mouse, a lasso loop for a finger. */
data class SweepShape(
    val from: Vec2,
    val to: Vec2,
    val lasso: List<Vec2>?,
)

/** Everything one frame of the Draw room's world draws, gathered so the drawing is a function of it. */
class DrawScene(
    val glyph: Glyph?,
    val metrics: DrawMetrics?,
    val lines: GlyphLines?,
    val guidelines: List<Guideline>,
    val fill: Boolean,
    val locked: Boolean,
    val construction: Boolean,
    val selection: DrawSelection,
    val hover: Hit?,
    val snaps: List<SnapTarget>,
    val sweep: SweepShape?,
    val pen: PenPath,
    /** The point the pen is dragging out right now, so its handles show as they are pulled. */
    val penLive: PenAnchor? = null,
    /** Where the knife's first cut end sits, once placed (font units). */
    val knifeMark: Vec2? = null,
    /** The nodes a tidy waiting for Apply would remove (font units): each shows as a cross on the outline. */
    val tidyRemoved: List<Point> = emptyList(),
    /** The two ends the measure tool has been given, in font units. */
    val measure: MeasureState? = null,
    /** The shape being dragged out with the Primitives tool, drawn as the contour it would become. */
    val primitive: PrimitiveShape? = null,
    /** The glyph's anchors are drawn with the glyph; this one, when set, is the selected one. */
    val anchorSelected: String? = null,
    /** The centreline the Stroke tool has laid so far, and the outline it would make, both in font units. */
    val centreline: List<Point> = emptyList(),
    val strokePreview: List<Contour> = emptyList(),
    /** True while the Metrics tool is in hand: the lines it can move get a grip, and [metricSelected] is the one picked. */
    val metricGrips: Boolean = false,
    val metricSelected: MetricKind? = null,
    /**
     * How strongly the glyph itself (its body, handles and nodes) is drawn: 1 normally, 0.3 while a radial ring
     * is open over it (S12). The metric lines, guidelines, snap targets and the selection stay at full strength (C4).
     */
    val glyphAlpha: Float = 1f,
)

/**
 * Draws the Draw room's world: metric lines and the glyph's own vertical lines (full strength, fading
 * only at their ends, C4), overshoot zones, guidelines and construction geometry (when the switch is
 * on), the glyph filled or outlined (non-zero winding, which is what a font prints with), the handles
 * and nodes, then the selection, the snap targets and any sweep in progress. Nothing here fades under
 * the glass: the bloom is drawn before this pass.
 */
fun DrawScope.drawDrawRoom(
    scene: DrawScene,
    space: DrawSpace,
    colors: DrawColors,
) {
    val widthDp =
        size.width
            .toDp()
            .value
            .toDouble()
    scene.metrics?.let { drawMetricBands(it, scene.lines, space, colors, widthDp) }
    scene.metrics?.let { drawMetricLines(it, space, colors, widthDp) }
    if (scene.metricGrips) scene.metrics?.let { drawMetricGrips(it, scene.metricSelected, space, colors) }
    scene.lines?.let { drawGlyphVerticals(it, scene.metrics, space, colors) }
    if (scene.construction) drawGuidelines(scene.guidelines, space, colors)
    val glyph = scene.glyph
    if (glyph != null) {
        // A locked glyph refuses the edit a mark invites, so it shows none.
        val marks = if (scene.locked) emptyList() else RetractedHandles.marks(glyph, space, scene.selection)
        withGroupAlpha(scene.glyphAlpha) {
            drawGlyphBody(glyph, space, colors, scene.fill, scene.locked)
            drawHandles(glyph, space, colors, scene.selection, scene.fill)
            drawRetractedMarks(marks, space, colors, scene.selection)
            drawNodes(glyph, space, colors, scene.selection)
        }
        drawSelection(glyph, space, colors, scene.selection, marks)
        scene.hover?.let { drawHover(glyph, space, colors, it) }
    }
    glyph?.let { drawAnchors(it.anchors, scene.anchorSelected, space, colors) }
    drawSnapTargets(scene.snaps, space, colors)
    scene.sweep?.let { drawSweep(it, colors) }
    if (!scene.pen.isEmpty || scene.penLive != null) drawPenTrail(scene.pen, scene.penLive, space, colors)
    // A run of a thousand removals would be a smear of crosses: TidyCrosses keeps one per gap, and the proposal says how many that is.
    for (removed in TidyCrosses.shown(scene.tidyRemoved, space)) drawRemovalCross(screenOf(space, removed.toVec2()), colors)
    scene.knifeMark?.let { drawKnifeMark(screenOf(space, it), colors) }
    scene.primitive?.let { drawPrimitivePreview(it, space, colors) }
    if (scene.centreline.isNotEmpty()) drawCentreline(scene.centreline, scene.strokePreview, space, colors)
    scene.measure?.let { drawMeasure(it, space, colors) }
}

private fun DrawScope.px(v: Vec2): Offset = Offset(v.x.dp.toPx(), v.y.dp.toPx())

/** Draws [block] as one layer at [alpha], so overlapping strokes dim together instead of showing through each other. */
private inline fun DrawScope.withGroupAlpha(
    alpha: Float,
    block: () -> Unit,
) {
    if (alpha >= 1f) {
        block()
        return
    }
    val canvas = drawContext.canvas
    canvas.saveLayer(Rect(Offset.Zero, size), Paint().apply { this.alpha = alpha })
    block()
    canvas.restore()
}

private fun DrawScope.screenOf(
    space: DrawSpace,
    font: Vec2,
): Offset = px(space.toScreen(font))

private val LINE_FADE_STOPS = floatArrayOf(0f, 0.14f, 0.86f, 1f)

private fun DrawScope.drawMetricLines(
    metrics: DrawMetrics,
    space: DrawSpace,
    colors: DrawColors,
    widthDp: Double,
) {
    val strokeWidth = 1.dp.toPx()
    for (line in metrics.lines) {
        val y =
            space
                .toScreen(Vec2(0.0, line.y.toDouble()))
                .y.dp
                .toPx()
        if (y < -strokeWidth || y > size.height + strokeWidth) continue
        val brush =
            Brush.horizontalGradient(
                colorStops =
                    arrayOf(
                        LINE_FADE_STOPS[0] to colors.metricLine.copy(alpha = 0f),
                        LINE_FADE_STOPS[1] to colors.metricLine,
                        LINE_FADE_STOPS[2] to colors.metricLine,
                        LINE_FADE_STOPS[3] to colors.metricLine.copy(alpha = 0f),
                    ),
                startX = 0f,
                endX = widthDp.dp.toPx(),
            )
        drawLine(brush, Offset(0f, y), Offset(size.width, y), strokeWidth = strokeWidth)
    }
}

private fun DrawScope.drawMetricBands(
    metrics: DrawMetrics,
    lines: GlyphLines?,
    space: DrawSpace,
    colors: DrawColors,
    widthDp: Double,
) {
    if (metrics.zones.isEmpty()) return
    val left =
        space
            .toScreen(Vec2(0.0, 0.0))
            .x.dp
            .toPx()
    val right = (lines?.let { space.toScreen(Vec2(it.advance.toDouble(), 0.0)).x } ?: widthDp).dp.toPx()
    for (zone in metrics.zones) {
        val top =
            space
                .toScreen(Vec2(0.0, zone.top.toDouble()))
                .y.dp
                .toPx()
        val bottom =
            space
                .toScreen(Vec2(0.0, zone.bottom.toDouble()))
                .y.dp
                .toPx()
        drawRect(colors.zone, topLeft = Offset(left, top), size = Size(max(right - left, 1f), max(bottom - top, 1f)))
    }
}

private fun DrawScope.drawGlyphVerticals(
    lines: GlyphLines,
    metrics: DrawMetrics?,
    space: DrawSpace,
    colors: DrawColors,
) {
    val (low, high) = metrics?.span ?: (0 to 1000)
    val top =
        space
            .toScreen(Vec2(0.0, high.toDouble() + VERTICAL_OVERHANG_UNITS))
            .y.dp
            .toPx()
    val bottom =
        space
            .toScreen(Vec2(0.0, low.toDouble() - VERTICAL_OVERHANG_UNITS))
            .y.dp
            .toPx()
    val brush =
        Brush.verticalGradient(
            colorStops =
                arrayOf(
                    0f to colors.metricLine.copy(alpha = 0f),
                    VERTICAL_FADE to colors.metricLine,
                    1f - VERTICAL_FADE to colors.metricLine,
                    1f to colors.metricLine.copy(alpha = 0f),
                ),
            startY = top,
            endY = bottom,
        )
    for (x in listOf(0.0, lines.advance.toDouble())) {
        val sx =
            space
                .toScreen(Vec2(x, 0.0))
                .x.dp
                .toPx()
        drawLine(brush, Offset(sx, top), Offset(sx, bottom), strokeWidth = 1.dp.toPx())
    }
}

private fun DrawScope.drawGuidelines(
    guidelines: List<Guideline>,
    space: DrawSpace,
    colors: DrawColors,
) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 5.dp.toPx()))
    val reach = (size.width + size.height) * 2f
    for (guide in guidelines) {
        val x = guide.x
        val y = guide.y
        val angle = guide.angle
        val (a, b) =
            when {
                x != null && y != null -> {
                    val at = screenOf(space, Vec2(x, y))
                    val radians = (angle ?: 0.0) * PI / 180.0
                    // Font angles are counter-clockwise from the x axis; the screen's y runs down.
                    val dx = cos(radians).toFloat() * reach
                    val dy = -sin(radians).toFloat() * reach
                    Offset(at.x - dx, at.y - dy) to Offset(at.x + dx, at.y + dy)
                }

                x != null -> {
                    val sx =
                        space
                            .toScreen(Vec2(x, 0.0))
                            .x.dp
                            .toPx()
                    Offset(sx, -reach) to Offset(sx, reach)
                }

                y != null -> {
                    val sy =
                        space
                            .toScreen(Vec2(0.0, y))
                            .y.dp
                            .toPx()
                    Offset(-reach, sy) to Offset(reach, sy)
                }

                else -> {
                    continue
                }
            }
        drawLine(colors.guide, a, b, strokeWidth = 1.dp.toPx(), pathEffect = dash)
    }
}

private fun DrawScope.drawGlyphBody(
    glyph: Glyph,
    space: DrawSpace,
    colors: DrawColors,
    fill: Boolean,
    locked: Boolean,
) {
    val path = Path().apply { fillType = PathFillType.NonZero }
    for (contour in glyph.contours) path.addContour(contour) { screenOf(space, it) }
    path.fillType = PathFillType.NonZero
    val muted = if (locked) LOCKED_ALPHA else 1f
    drawPath(path, colors.ink.copy(alpha = (if (fill) FILL_SOLID_ALPHA else FILL_WASH_ALPHA) * muted))
    drawPath(path, colors.ink.copy(alpha = muted), style = Stroke(width = 1.5.dp.toPx()))
}

private fun DrawScope.drawHandles(
    glyph: Glyph,
    space: DrawSpace,
    colors: DrawColors,
    selection: DrawSelection,
    fill: Boolean,
) {
    val lineWidth = 1.dp.toPx()
    val haloWidth = 3.dp.toPx()
    for ((ci, contour) in glyph.contours.withIndex()) {
        val selectedHere = selection.indicesIn(ci)
        for (info in contour.segmentLayout()) {
            val segment = info.segment
            val ends = handleLinesOf(segment)
            for ((k, pair) in ends.withIndex()) {
                val (from, to) = pair
                if (from == to) continue
                val controlIndex = info.controlPoints.getOrNull(k)
                val active = controlIndex != null && (controlIndex in selectedHere || ownerSelected(contour, controlIndex, selectedHere))
                val a = screenOf(space, from)
                val b = screenOf(space, to)
                if (fill) drawLine(colors.canvas.copy(alpha = HALO_ALPHA), a, b, strokeWidth = haloWidth)
                drawLine(if (active) colors.selected else colors.handleLine, a, b, strokeWidth = lineWidth)
            }
        }
        for ((pi, point) in contour.points.withIndex()) {
            if (point.onCurve || contour.isRetracted(pi)) continue
            val at = screenOf(space, point.point.toVec2())
            val active = pi in selectedHere || ownerSelected(contour, pi, selectedHere)
            val r = (if (active) HANDLE_RADIUS_SELECTED else HANDLE_RADIUS).dp.toPx()
            drawCircle(colors.canvas, r, at)
            drawCircle(
                if (active) colors.selected else colors.offCurve,
                r,
                at,
                style = Stroke(width = (if (active) 1.2 else 1.0).dp.toPx()),
            )
        }
    }
}

/**
 * The mark of each retracted handle of what is selected: a small hollow diamond (a handle is a circle, so the
 * shape says "pull me out" and not "I am a handle") on a short dotted stub from its node.
 */
private fun DrawScope.drawRetractedMarks(
    marks: List<RetractedMark>,
    space: DrawSpace,
    colors: DrawColors,
    selection: DrawSelection,
) {
    val half = RETRACTED_MARK_HALF.dp.toPx()
    val dash = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx()))
    for (mark in marks) {
        val from = screenOf(space, mark.at - mark.offset)
        val at = screenOf(space, mark.at)
        val active = mark.ref in selection.points
        val tint = if (active) colors.selected else colors.offCurve
        drawLine(if (active) colors.selected else colors.handleLine, from, at, strokeWidth = 1.dp.toPx(), pathEffect = dash)
        val diamond =
            Path().apply {
                moveTo(at.x, at.y - half)
                lineTo(at.x + half, at.y)
                lineTo(at.x, at.y + half)
                lineTo(at.x - half, at.y)
                close()
            }
        drawPath(diamond, colors.canvas)
        drawPath(diamond, tint, style = Stroke(width = 1.2.dp.toPx()))
    }
}

private fun ownerSelected(
    contour: Contour,
    handle: Int,
    selected: Set<Int>,
): Boolean = contour.handleOwner(handle)?.let { it in selected } ?: false

/** The handle-line end pairs of a segment: (start, first control) and (second control, end) for a cubic, one pair per control for a quadratic. */
private fun handleLinesOf(segment: CurveSegment): List<Pair<Vec2, Vec2>> =
    when (segment) {
        is CurveSegment.Line -> emptyList()
        is CurveSegment.Quadratic -> listOf(segment.start to segment.control, segment.control to segment.end)
        is CurveSegment.Cubic -> listOf(segment.start to segment.control1, segment.end to segment.control2)
    }

private fun DrawScope.drawNodes(
    glyph: Glyph,
    space: DrawSpace,
    colors: DrawColors,
    selection: DrawSelection,
) {
    val start = 11.dp.toPx()
    for ((ci, contour) in glyph.contours.withIndex()) {
        val onCurve = contour.points.withIndex().filter { it.value.onCurve }
        val screens = onCurve.map { screenOf(space, it.value.point.toVec2()) }
        for ((k, entry) in onCurve.withIndex()) {
            val at = screens[k]
            val selected = PointRef(ci, entry.index) in selection.points
            val previous = screens[(k - 1 + screens.size) % screens.size]
            val next = screens[(k + 1) % screens.size]
            val nearest = minOf((at - previous).getDistance(), (at - next).getDistance())
            val dense = screens.size > 1 && nearest < DENSE_SPACING_DP.dp.toPx() && !selected
            if (dense) {
                // A node crowded by its neighbours is a dot, so a traced outline of a thousand points reads as an outline.
                drawCircle(colors.cornerNode, DENSE_DOT_RADIUS.dp.toPx(), at)
                continue
            }
            val tint = if (entry.value.smooth) colors.smoothNode else colors.cornerNode
            if (entry.value.smooth) {
                val r = SMOOTH_RADIUS.dp.toPx()
                drawCircle(colors.canvas, r, at)
                drawCircle(tint, r, at, style = Stroke(width = 1.5.dp.toPx()))
            } else {
                val half = CORNER_HALF.dp.toPx()
                drawRect(colors.canvas, Offset(at.x - half, at.y - half), Size(2 * half, 2 * half))
                drawRect(tint, Offset(at.x - half, at.y - half), Size(2 * half, 2 * half), style = Stroke(width = 1.5.dp.toPx()))
            }
        }
        // The start point, with the direction the contour runs, as a small solid triangle ahead of its first node.
        val first = contour.segmentLayout().firstOrNull() ?: continue
        val a = screenOf(space, first.segment.start)
        val ahead = screenOf(space, first.segment.pointAt(DIRECTION_PROBE_T))
        val dir = ahead - a
        val length = dir.getDistance()
        if (length > 0.5f) {
            val unit = Offset(dir.x / length, dir.y / length)
            val normal = Offset(-unit.y, unit.x)
            val apex = a + unit * (start + TRIANGLE_LENGTH.dp.toPx())
            val base = a + unit * start
            val half = TRIANGLE_HALF.dp.toPx()
            val triangle =
                Path().apply {
                    moveTo(apex.x, apex.y)
                    lineTo(base.x + normal.x * half, base.y + normal.y * half)
                    lineTo(base.x - normal.x * half, base.y - normal.y * half)
                    close()
                }
            drawPath(triangle, colors.ink)
        }
    }
}

private fun DrawScope.drawSelection(
    glyph: Glyph,
    space: DrawSpace,
    colors: DrawColors,
    selection: DrawSelection,
    marks: List<RetractedMark>,
) {
    for (ref in selection.points) {
        val point =
            glyph.contours
                .getOrNull(ref.contour)
                ?.points
                ?.getOrNull(ref.point) ?: continue
        // A retracted handle is selected at its mark, not under its node.
        val at = screenOf(space, marks.firstOrNull { it.ref == ref }?.at ?: point.point.toVec2())
        if (point.onCurve) {
            drawCircle(colors.selected, SELECT_RING_RADIUS.dp.toPx(), at, style = Stroke(width = 1.2.dp.toPx()))
            drawCircle(colors.selected, SELECT_DOT_RADIUS.dp.toPx(), at)
        } else {
            drawCircle(colors.selected, HANDLE_SELECT_RING.dp.toPx(), at, style = Stroke(width = 1.2.dp.toPx()))
        }
    }
    val key = selection.segment ?: return
    val contour = glyph.contours.getOrNull(key.contour) ?: return
    val info = contour.segmentLayout().getOrNull(key.segment) ?: return
    val path = Path()
    val seg = info.segment
    val begin = screenOf(space, seg.start)
    path.moveTo(begin.x, begin.y)
    for (i in 1..SEGMENT_SAMPLES) {
        val p = screenOf(space, seg.pointAt(i.toDouble() / SEGMENT_SAMPLES))
        path.lineTo(p.x, p.y)
    }
    drawPath(path, colors.selected, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
    val at = screenOf(space, seg.pointAt(selection.segmentT))
    drawCircle(colors.selected, SELECT_DOT_RADIUS.dp.toPx(), at)
}

private fun DrawScope.drawHover(
    glyph: Glyph,
    space: DrawSpace,
    colors: DrawColors,
    hover: Hit,
) {
    when (hover) {
        is Hit.OnPoint -> {
            val point =
                glyph.contours
                    .getOrNull(hover.ref.contour)
                    ?.points
                    ?.getOrNull(hover.ref.point) ?: return
            val at = screenOf(space, point.point.toVec2() + (hover.markOffset ?: Vec2(0.0, 0.0)))
            drawCircle(colors.muted, HOVER_RING_RADIUS.dp.toPx(), at, style = Stroke(width = 1.dp.toPx()))
        }

        is Hit.OnSegment -> {
            val at = screenOf(space, hover.at)
            drawCircle(colors.muted, 3.dp.toPx(), at, style = Stroke(width = 1.dp.toPx()))
        }

        Hit.Nothing -> {
            Unit
        }
    }
}

private fun DrawScope.drawSnapTargets(
    targets: List<SnapTarget>,
    space: DrawSpace,
    colors: DrawColors,
) {
    for (target in targets) {
        val at = screenOf(space, target.at)
        val arm = SNAP_ARM.dp.toPx()
        val width = 1.5.dp.toPx()
        drawLine(colors.snap, Offset(at.x - arm, at.y), Offset(at.x + arm, at.y), strokeWidth = width)
        drawLine(colors.snap, Offset(at.x, at.y - arm), Offset(at.x, at.y + arm), strokeWidth = width)
    }
}

private fun DrawScope.drawSweep(
    sweep: SweepShape,
    colors: DrawColors,
) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
    val loop = sweep.lasso
    if (loop != null && loop.size > 1) {
        val path = Path()
        path.moveTo(loop[0].x.dp.toPx(), loop[0].y.dp.toPx())
        for (p in loop.drop(1)) path.lineTo(p.x.dp.toPx(), p.y.dp.toPx())
        path.lineTo(loop[0].x.dp.toPx(), loop[0].y.dp.toPx())
        drawPath(path, colors.construction, style = Stroke(width = 1.dp.toPx(), pathEffect = dash))
    } else {
        val a = px(sweep.from)
        val b = px(sweep.to)
        drawRect(
            colors.construction,
            topLeft = Offset(minOf(a.x, b.x), minOf(a.y, b.y)),
            size = Size(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.y - a.y)),
            style = Stroke(width = 1.dp.toPx(), pathEffect = dash),
        )
    }
}

/**
 * The pen's path so far: its curves as drawn (a dashed construction stroke), a handle line and dot for every
 * pulled handle (the incoming one mirrored), and every anchor as a node of its kind, a square for a corner and a
 * circle for a smooth point; the first is ringed in the selection colour because a click on it closes the shape.
 */
private fun DrawScope.drawPenTrail(
    path: PenPath,
    live: PenAnchor?,
    space: DrawSpace,
    colors: DrawColors,
) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
    val curve = Path()
    val segments = path.previewSegments(live)
    for ((i, segment) in segments.withIndex()) {
        val a = screenOf(space, segment.start)
        if (i == 0) curve.moveTo(a.x, a.y)
        val c1 = screenOf(space, segment.control1)
        val c2 = screenOf(space, segment.control2)
        val b = screenOf(space, segment.end)
        curve.cubicTo(c1.x, c1.y, c2.x, c2.y, b.x, b.y)
    }
    drawPath(curve, colors.construction, style = Stroke(width = 1.5.dp.toPx(), pathEffect = dash))
    val anchors = path.anchors + listOfNotNull(live)
    for ((i, anchor) in anchors.withIndex()) {
        val at = screenOf(space, anchor.point.toVec2())
        val pull = anchor.pull
        if (pull != null && anchor.hasHandles) {
            for (sign in listOf(1, -1)) {
                val end = screenOf(space, Vec2((anchor.point.x + sign * pull.x).toDouble(), (anchor.point.y + sign * pull.y).toDouble()))
                drawLine(colors.handleLine, at, end, strokeWidth = 1.dp.toPx())
                drawCircle(colors.canvas, HANDLE_RADIUS.dp.toPx(), end)
                drawCircle(colors.offCurve, HANDLE_RADIUS.dp.toPx(), end, style = Stroke(1.5.dp.toPx()))
            }
        }
        val tint = if (i == 0) colors.selected else colors.ink
        if (anchor.hasHandles) {
            val r = SMOOTH_RADIUS.dp.toPx()
            drawCircle(colors.canvas, r, at)
            drawCircle(tint, r, at, style = Stroke(1.5.dp.toPx()))
        } else {
            val half = CORNER_HALF.dp.toPx()
            drawRect(colors.canvas, Offset(at.x - half, at.y - half), Size(2 * half, 2 * half))
            drawRect(tint, Offset(at.x - half, at.y - half), Size(2 * half, 2 * half), style = Stroke(1.5.dp.toPx()))
        }
        if (i == 0 && anchors.size > 1) drawCircle(colors.selected, PEN_CLOSE_RING.dp.toPx(), at, style = Stroke(1.dp.toPx()))
    }
}

/** A node a tidy would remove: a cross (a shape, not only a colour) in ink on a ring of canvas, so it reads on any outline. */
private fun DrawScope.drawRemovalCross(
    at: Offset,
    colors: DrawColors,
) {
    val arm = REMOVAL_ARM.dp.toPx()
    drawCircle(colors.canvas, arm + 2.dp.toPx(), at)
    drawLine(colors.ink, Offset(at.x - arm, at.y - arm), Offset(at.x + arm, at.y + arm), strokeWidth = 1.5.dp.toPx())
    drawLine(colors.ink, Offset(at.x - arm, at.y + arm), Offset(at.x + arm, at.y - arm), strokeWidth = 1.5.dp.toPx())
}

/** The shape the Primitives tool would make: its outline in the construction stroke, so it reads as not yet part of the glyph. */
private fun DrawScope.drawPrimitivePreview(
    shape: PrimitiveShape,
    space: DrawSpace,
    colors: DrawColors,
) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
    val path = Path()
    for ((i, segment) in shape.contour.segments().withIndex()) {
        val cubic = segment as? CurveSegment.Cubic ?: continue
        val a = screenOf(space, cubic.start)
        if (i == 0) path.moveTo(a.x, a.y)
        val c1 = screenOf(space, cubic.control1)
        val c2 = screenOf(space, cubic.control2)
        val b = screenOf(space, cubic.end)
        path.cubicTo(c1.x, c1.y, c2.x, c2.y, b.x, b.y)
    }
    path.close()
    drawPath(path, colors.construction, style = Stroke(width = 1.5.dp.toPx(), pathEffect = dash))
}

/**
 * The anchors: each a ring with a cross through it (a shape no node or handle has), in ink; the selected one is larger and in
 * the selection colour. Their names are written by [AnchorLabels], a text layer above.
 */
private fun DrawScope.drawAnchors(
    anchors: List<com.asoc.typewright.core.geometry.Anchor>,
    selected: String?,
    space: DrawSpace,
    colors: DrawColors,
) {
    for (anchor in anchors) {
        val at = screenOf(space, anchor.point.toVec2())
        val active = anchor.name == selected
        val tint = if (active) colors.selected else colors.ink
        val r = (if (active) ANCHOR_RADIUS_SELECTED else ANCHOR_RADIUS).dp.toPx()
        drawCircle(colors.canvas, r, at)
        drawCircle(tint, r, at, style = Stroke(width = 1.5.dp.toPx()))
        drawLine(tint, Offset(at.x - r * 1.5f, at.y), Offset(at.x + r * 1.5f, at.y), strokeWidth = 1.dp.toPx())
        drawLine(tint, Offset(at.x, at.y - r * 1.5f), Offset(at.x, at.y + r * 1.5f), strokeWidth = 1.dp.toPx())
    }
}

/**
 * The Metrics tool's grips: a pair of short ticks on each line it can move (a baseline is fixed and has none), the line picked
 * thicker and in the selection colour. The overshoot zones are drawn as before and have no grip: they are read-only.
 */
private fun DrawScope.drawMetricGrips(
    metrics: DrawMetrics,
    selected: MetricKind?,
    space: DrawSpace,
    colors: DrawColors,
) {
    for (line in metrics.lines) {
        val y =
            space
                .toScreen(Vec2(0.0, line.y.toDouble()))
                .y.dp
                .toPx()
        if (y < 0f || y > size.height) continue
        val active = line.kind == selected
        if (active) drawLine(colors.selected, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
        if (line.kind == MetricKind.BASELINE) continue
        val x = METRIC_GRIP_X.dp.toPx()
        val tint = if (active) colors.selected else colors.ink
        val half = METRIC_GRIP_HALF.dp.toPx()
        for (dy in listOf(-2.dp.toPx(), 2.dp.toPx())) {
            drawLine(tint, Offset(x - half, y + dy), Offset(x + half, y + dy), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

/** The Stroke tool's centreline as a dashed line with a square on each point, and the outline it would make dashed in the construction stroke. */
private fun DrawScope.drawCentreline(
    line: List<Point>,
    preview: List<Contour>,
    space: DrawSpace,
    colors: DrawColors,
) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
    for (contour in preview) {
        val path = Path()
        for ((i, segment) in contour.segments().withIndex()) {
            val cubic = segment as? CurveSegment.Cubic ?: continue
            val a = screenOf(space, cubic.start)
            if (i == 0) path.moveTo(a.x, a.y)
            val c1 = screenOf(space, cubic.control1)
            val c2 = screenOf(space, cubic.control2)
            val b = screenOf(space, cubic.end)
            path.cubicTo(c1.x, c1.y, c2.x, c2.y, b.x, b.y)
        }
        path.close()
        drawPath(path, colors.construction, style = Stroke(width = 1.5.dp.toPx(), pathEffect = dash))
    }
    val ends = line.map { screenOf(space, it.toVec2()) }
    for (i in 1 until ends.size) drawLine(colors.ink, ends[i - 1], ends[i], strokeWidth = 1.dp.toPx())
    for (at in ends) {
        val half = CORNER_HALF.dp.toPx()
        drawRect(colors.canvas, Offset(at.x - half, at.y - half), Size(2 * half, 2 * half))
        drawRect(colors.ink, Offset(at.x - half, at.y - half), Size(2 * half, 2 * half), style = Stroke(width = 1.5.dp.toPx()))
    }
}

/** The measure tool's ends as crosses and, once both are down, the line between them with a tick at each end. */
private fun DrawScope.drawMeasure(
    measure: MeasureState,
    space: DrawSpace,
    colors: DrawColors,
) {
    val first = measure.first?.let { screenOf(space, it) } ?: return
    val second = measure.second?.let { screenOf(space, it) }
    if (second != null) drawLine(colors.selected, first, second, strokeWidth = 1.5.dp.toPx())
    for (end in listOfNotNull(first, second)) {
        val arm = MEASURE_ARM.dp.toPx()
        drawCircle(colors.canvas, arm + 2.dp.toPx(), end)
        drawLine(colors.selected, Offset(end.x - arm, end.y), Offset(end.x + arm, end.y), strokeWidth = 1.5.dp.toPx())
        drawLine(colors.selected, Offset(end.x, end.y - arm), Offset(end.x, end.y + arm), strokeWidth = 1.5.dp.toPx())
    }
}

/** The knife's first cut end: a diamond ringed in the selection colour, the one place the knife has been put down. */
private fun DrawScope.drawKnifeMark(
    at: Offset,
    colors: DrawColors,
) {
    val r = KNIFE_MARK_RADIUS.dp.toPx()
    val diamond =
        Path().apply {
            moveTo(at.x, at.y - r)
            lineTo(at.x + r, at.y)
            lineTo(at.x, at.y + r)
            lineTo(at.x - r, at.y)
            close()
        }
    drawPath(diamond, colors.canvas)
    drawPath(diamond, colors.selected, style = Stroke(2.dp.toPx()))
}

private const val PEN_CLOSE_RING = 10
private const val ANCHOR_RADIUS = 4.0
private const val ANCHOR_RADIUS_SELECTED = 5.5
private const val METRIC_GRIP_X = 28
private const val METRIC_GRIP_HALF = 8
private const val REMOVAL_ARM = 4
private const val KNIFE_MARK_RADIUS = 7
private const val MEASURE_ARM = 6
private const val VERTICAL_OVERHANG_UNITS = 60.0
private const val VERTICAL_FADE = 0.12f
private const val FILL_SOLID_ALPHA = 0.88f
private const val FILL_WASH_ALPHA = 0.07f
private const val LOCKED_ALPHA = 0.55f
private const val HALO_ALPHA = 0.6f
private const val HANDLE_RADIUS = 2.6
private const val HANDLE_RADIUS_SELECTED = 2.8
private const val SMOOTH_RADIUS = 3.6
private const val CORNER_HALF = 3.4
private const val DENSE_SPACING_DP = 5.0
private const val DENSE_DOT_RADIUS = 1.2
private const val TRIANGLE_LENGTH = 7.0
private const val TRIANGLE_HALF = 3.5
private const val DIRECTION_PROBE_T = 0.05
private const val SELECT_RING_RADIUS = 7.5
private const val SELECT_DOT_RADIUS = 4.2
private const val HANDLE_SELECT_RING = 5.0
private const val RETRACTED_MARK_HALF = 3.2
private const val HOVER_RING_RADIUS = 9.0
private const val SEGMENT_SAMPLES = 24
private const val SNAP_ARM = 12.0
