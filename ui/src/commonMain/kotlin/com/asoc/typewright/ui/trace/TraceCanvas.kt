// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.trace

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.engine.trace.TraceStage
import com.asoc.typewright.scripts.sheets.PaperSize
import com.asoc.typewright.scripts.sheets.SheetLayout
import com.asoc.typewright.ui.capture.InkOutline
import com.asoc.typewright.ui.capture.TraceSource
import com.asoc.typewright.ui.capture.mono
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.MeaningColors
import com.asoc.typewright.ui.tokens.accent
import com.asoc.typewright.ui.tokens.toColor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val METRIC_ALPHA = 0.45f
private const val ADVANCE_ALPHA = 0.3f
private const val RASTER_GHOST_ALPHA = 0.2f
private const val FILL_ALPHA = 0.13f
private const val MARGIN_DP = 10f
private const val NODE_DP = 7f
private const val RASTER_SIDE = 360

/**
 * The drawing and its outline at the selected stage, in font space: the cell's own window (the room the letter was drawn in), the
 * metric lines (from [metricLines], so they are there before the first run ends) and the advance lines at full strength (they never fade, C4), the raster the outline came from when [showRaster] is
 * on, and the stage's output on top. Capture shows the drawing alone; Clean and Contour show the edge that was followed; Corners
 * mark where the edge turns; Fit onward draw the outline with its points, Snap marking the ones it moved.
 */
@Composable
fun TraceCanvas(
    texture: CanvasTexture,
    source: TraceSource,
    outcome: TraceOutcome?,
    stage: TraceStage?,
    showRaster: Boolean,
    metricLabels: Map<Int, String>,
    metricLines: List<Int> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val view = remember(source) { TraceView.viewBox(source) }
    val raster = remember(source) { (source as? TraceSource.Raster)?.let { InkOutline.of(it.raster, RASTER_SIDE) } }
    val guides = remember(source.script) { SheetLayout.of(PaperSize.A4, source.script).guides }
    val ink = texture.ink.toColor()
    val muted = texture.muted.toColor()
    val canvasColor = texture.canvas.toColor()
    val snap = MeaningColors.CYAN.forTexture(texture).toColor()
    val summary = remember(outcome, stage) { describe(source, outcome, stage) }
    Canvas(modifier.semantics { contentDescription = summary }) {
        val margin = MARGIN_DP.dp.toPx()
        val k = min((size.width - 2 * margin) / view.width.toFloat(), (size.height - 2 * margin) / view.height.toFloat())
        val ox = (size.width - view.width.toFloat() * k) / 2f
        val oy = (size.height - view.height.toFloat() * k) / 2f

        fun sx(x: Double) = ox + ((x - view.left) * k).toFloat()

        fun sy(y: Double) = oy + ((view.top - y) * k).toFloat()

        fun at(p: Vec2) = Offset(sx(p.x), sy(p.y))

        // Metric lines and advance lines first, so the drawing sits on top of them. Full strength everywhere.
        val labelStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 8.5.sp, color = muted)
        val lines = metricLines.ifEmpty { outcome?.parameters?.metricLines.orEmpty() }
        for (y in lines) {
            val py = sy(y.toDouble())
            drawLine(ink.copy(alpha = METRIC_ALPHA), Offset(0f, py), Offset(size.width, py), 1.dp.toPx())
            val label = measurer.measure(metricLabels[y] ?: "$y", labelStyle)
            drawText(label, topLeft = Offset(size.width - label.size.width - 12.dp.toPx(), py - label.size.height - 2.dp.toPx()))
        }
        for (x in listOf(guides.advanceLeft, guides.advanceRight)) {
            drawLine(ink.copy(alpha = ADVANCE_ALPHA), Offset(sx(x.toDouble()), 0f), Offset(sx(x.toDouble()), size.height), 1.dp.toPx())
        }
        val onlyRaster = stage == TraceStage.CAPTURE
        if (raster != null && source is TraceSource.Raster && (showRaster || onlyRaster)) {
            drawRasterGhost(raster, source, ::at, ink.copy(alpha = if (onlyRaster) 0.85f else RASTER_GHOST_ALPHA))
        }
        if (outcome == null || stage == null) return@Canvas
        val result = outcome.stage(stage)
        when (stage) {
            TraceStage.CAPTURE -> {
                Unit
            }

            TraceStage.CLEAN, TraceStage.CONTOUR -> {
                val loops = outcome.stage(TraceStage.CONTOUR)?.polylines.orEmpty()
                val path = polylinePath(loops, ::at)
                drawPath(path, ink.copy(alpha = FILL_ALPHA), style = androidx.compose.ui.graphics.drawscope.Fill)
                if (stage == TraceStage.CONTOUR) drawPath(path, ink, style = Stroke(1.dp.toPx(), join = StrokeJoin.Round))
            }

            TraceStage.CORNERS -> {
                val path = polylinePath(result?.polylines.orEmpty(), ::at)
                drawPath(path, ink.copy(alpha = 0.6f), style = Stroke(1.dp.toPx(), join = StrokeJoin.Round))
                val half = NODE_DP.dp.toPx() / 2f
                for (p in result?.cornerPoints.orEmpty()) {
                    val c = at(p)
                    drawRect(ink, Offset(c.x - half, c.y - half), Size(half * 2, half * 2))
                }
            }

            else -> {
                val contours = result?.contours.orEmpty()
                drawOutline(contours, ::at, ink, canvasColor)
                if (stage == TraceStage.SNAP) {
                    val before = outcome.stage(TraceStage.FIT)?.contours.orEmpty()
                    val moved = TraceView.movedBySnap(before, contours)
                    val arm = 9.dp.toPx()
                    for (p in moved) {
                        val c = at(Vec2(p.x.toDouble(), p.y.toDouble()))
                        drawLine(snap, Offset(c.x - arm, c.y), Offset(c.x + arm, c.y), 1.5.dp.toPx())
                        drawLine(snap, Offset(c.x, c.y - arm), Offset(c.x, c.y + arm), 1.5.dp.toPx())
                    }
                    if (moved.isNotEmpty()) {
                        val label =
                            measurer.measure(
                                "${moved.size} SNAPPED",
                                TextStyle(fontFamily = FontFamily.Monospace, fontSize = 8.5.sp, color = snap),
                            )
                        drawText(label, topLeft = Offset(12.dp.toPx(), size.height - label.size.height - 12.dp.toPx()))
                    }
                }
            }
        }
    }
}

private fun describe(
    source: TraceSource,
    outcome: TraceOutcome?,
    stage: TraceStage?,
): String {
    if (outcome == null || stage == null) return "${source.glyphName}: tracing"
    val count = outcome.countAt(stage)
    return "${source.glyphName} at the ${stage.label} stage" + (count?.let { ": ${it.total} points, ${it.onCurve} on the curve" } ?: "")
}

private fun DrawScope.drawRasterGhost(
    outline: InkOutline,
    source: TraceSource.Raster,
    at: (Vec2) -> Offset,
    color: Color,
) {
    if (outline.isEmpty) return
    val sx = source.raster.width.toDouble() / outline.width
    val sy = source.raster.height.toDouble() / outline.height
    val path =
        Path().apply {
            fillType = PathFillType.EvenOdd
            for (loop in outline.loops) {
                loop.forEachIndexed { i, p ->
                    val units = source.frame.unitsAt(p.x * sx - 0.5, p.y * sy - 0.5)
                    val o = at(units)
                    if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
                }
                close()
            }
        }
    drawPath(path, color)
}

private fun polylinePath(
    loops: List<List<Vec2>>,
    at: (Vec2) -> Offset,
): Path =
    Path().apply {
        fillType = PathFillType.EvenOdd
        for (loop in loops) {
            loop.forEachIndexed { i, p ->
                val o = at(p)
                if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
            }
            close()
        }
    }

private fun DrawScope.drawOutline(
    contours: List<Contour>,
    at: (Vec2) -> Offset,
    ink: Color,
    canvasColor: Color,
) {
    val path = Path().apply { fillType = PathFillType.NonZero }
    for (contour in contours) {
        val n = contour.points.size / 3
        for (i in 0 until n) {
            val p0 = contour.points[3 * i].point
            val p1 = contour.points[3 * i + 1].point
            val p2 = contour.points[3 * i + 2].point
            val p3 = contour.points[(3 * i + 3) % contour.points.size].point
            val a = at(Vec2(p0.x.toDouble(), p0.y.toDouble()))
            if (i == 0) path.moveTo(a.x, a.y)
            val c1 = at(Vec2(p1.x.toDouble(), p1.y.toDouble()))
            val c2 = at(Vec2(p2.x.toDouble(), p2.y.toDouble()))
            val e = at(Vec2(p3.x.toDouble(), p3.y.toDouble()))
            if (p1 == p0 && p2 == p3) path.lineTo(e.x, e.y) else path.cubicTo(c1.x, c1.y, c2.x, c2.y, e.x, e.y)
        }
        path.close()
    }
    drawPath(path, ink.copy(alpha = FILL_ALPHA))
    drawPath(path, ink, style = Stroke(1.5.dp.toPx(), join = StrokeJoin.Round))
    val half = NODE_DP.dp.toPx() / 2f
    val handle = ink.copy(alpha = 0.45f)
    for (contour in contours) {
        val n = contour.points.size / 3
        for (i in 0 until n) {
            val p0 = contour.points[3 * i].point
            val p1 = contour.points[3 * i + 1].point
            val p2 = contour.points[3 * i + 2].point
            val p3 = contour.points[(3 * i + 3) % contour.points.size].point
            if (p1 != p0 || p2 != p3) {
                val a = at(Vec2(p0.x.toDouble(), p0.y.toDouble()))
                val b = at(Vec2(p3.x.toDouble(), p3.y.toDouble()))
                val c1 = at(Vec2(p1.x.toDouble(), p1.y.toDouble()))
                val c2 = at(Vec2(p2.x.toDouble(), p2.y.toDouble()))
                drawLine(handle, a, c1, 1.dp.toPx())
                drawLine(handle, b, c2, 1.dp.toPx())
                drawCircle(canvasColor, 2.6.dp.toPx(), c1)
                drawCircle(ink, 2.6.dp.toPx(), c1, style = Stroke(1.dp.toPx()))
                drawCircle(canvasColor, 2.6.dp.toPx(), c2)
                drawCircle(ink, 2.6.dp.toPx(), c2, style = Stroke(1.dp.toPx()))
            }
        }
        for (p in contour.points.filter { it.onCurve }) {
            val c = at(Vec2(p.point.x.toDouble(), p.point.y.toDouble()))
            drawRect(canvasColor, Offset(c.x - half, c.y - half), Size(half * 2, half * 2))
            drawRect(ink, Offset(c.x - half, c.y - half), Size(half * 2, half * 2), style = Stroke(1.5.dp.toPx()))
        }
    }
}

/** The scrubber: the stages as words, the selected one an ink block, and a track under them whose thumb is the accent and can be dragged or tapped. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Scrubber(
    texture: CanvasTexture,
    outcome: TraceOutcome,
    page: TracePageState,
    selected: TraceStage,
) {
    val stages = TraceView.scrubberStages(outcome, page.all)
    val ink = texture.ink.toColor()
    val paper = texture.canvas.toColor()
    val muted = texture.muted.toColor()
    val accent = texture.accent.toColor()
    val index = max(0, stages.indexOf(selected))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (stage in stages) {
                val on = stage == selected
                BasicText(
                    text = stage.label,
                    style =
                        com.asoc.typewright.ui.capture.sentence(
                            if (on) {
                                paper
                            } else {
                                ink.copy(
                                    alpha =
                                        if (stage in
                                            outcome.unavailable
                                        ) {
                                            0.32f
                                        } else {
                                            0.7f
                                        },
                                )
                            },
                            12.0,
                        ),
                    modifier =
                        Modifier
                            .background(if (on) ink else Color.Transparent)
                            .clickable { page.stage = stage }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                )
            }
            val note = TraceView.hiddenStagesNote(outcome, page.all)
            if (note.isNotEmpty()) BasicText(note.uppercase(), style = mono(muted, 9.0), modifier = Modifier.padding(vertical = 8.dp))
        }
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .semantics {
                    role = Role.RadioButton
                    contentDescription = "Trace stage"
                    stateDescription = "${selected.label}, ${index + 1} of ${stages.size}"
                }.pointerInput(stages) {
                    fun pick(x: Float) {
                        if (stages.size < 2) return
                        val fraction = (x / size.width).coerceIn(0f, 1f)
                        page.stage = stages[(fraction * (stages.size - 1)).roundToInt()]
                    }
                    detectTapGestures { pick(it.x) }
                }.pointerInput(stages) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        if (stages.size >= 2) {
                            val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            page.stage = stages[(fraction * (stages.size - 1)).roundToInt()]
                        }
                    }
                },
        ) {
            val thumb = 14.dp
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(ink.copy(alpha = 0.18f)),
            )
            val travel = maxWidth - thumb
            val x = if (stages.size < 2) 0.dp else travel * (index.toFloat() / (stages.size - 1))
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = x)
                    .size(thumb)
                    .background(accent, CircleShape),
            )
        }
    }
}
