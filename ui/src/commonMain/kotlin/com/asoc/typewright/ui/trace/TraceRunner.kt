// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.trace

import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.TypeConstraintParameters
import com.asoc.typewright.core.geometry.enforceContourDirections
import com.asoc.typewright.core.geometry.lineSegmentCount
import com.asoc.typewright.core.geometry.outlineBounds
import com.asoc.typewright.core.geometry.snapOutline
import com.asoc.typewright.core.geometry.writtenCount
import com.asoc.typewright.engine.trace.GlyphTrace
import com.asoc.typewright.engine.trace.NodeCount
import com.asoc.typewright.engine.trace.StageResult
import com.asoc.typewright.engine.trace.TraceParameters
import com.asoc.typewright.engine.trace.TraceStage
import com.asoc.typewright.engine.trace.traceCell
import com.asoc.typewright.scripts.sheets.PaperSize
import com.asoc.typewright.scripts.sheets.PrintScript
import com.asoc.typewright.scripts.sheets.SheetLayout
import com.asoc.typewright.ui.capture.TraceSource
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * What the Trace screen reads from one run of the chain on one drawing: every stage's output (so the scrubber can show each),
 * the outline the last stage ended with, and what Review found.
 *
 * A stage in [unavailable] does not exist for this drawing: an SVG drawing is vector already, so it starts at Fit, and the
 * stages before are shown as not applying rather than invented.
 */
class TraceOutcome(
    /** The drawing this was traced from, so Accept can refuse an outcome that belongs to another drawing. */
    val source: TraceSource,
    val parameters: TraceParameters,
    val stages: List<StageResult>,
    val unavailable: Set<TraceStage>,
    /** The outline the chain ended with (font units, y up), ready for `AcceptTrace`; empty when no ink was found. */
    val contours: List<Contour>,
    val suggestedAdvance: Int,
    val flatTop: Boolean,
    val flatBottom: Boolean,
    val hasCurves: Boolean,
    /** The raster chain's own trace, when the drawing was a picture; null for an SVG drawing. */
    val trace: GlyphTrace?,
) {
    val inkFound: Boolean get() = contours.isNotEmpty()

    fun stage(stage: TraceStage): StageResult? = stages.firstOrNull { it.stage == stage }

    /**
     * The point count to show with [stage] selected. Before there is an outline (Capture, Clean) it is the boundary of the ink the
     * Contour stage followed, which is the number the fit is about to reduce; a stage with no count and no boundary is null.
     */
    fun countAt(stage: TraceStage): NodeCount? = stage(stage)?.nodes ?: stage(TraceStage.CONTOUR)?.nodes

    /** The count the glyph ends with, as the file will hold it. */
    val finalCount: NodeCount? get() = stages.lastOrNull { it.nodes != null }?.nodes

    /** The raw boundary count, "was 1,763", or null for a drawing that was never a picture. */
    val boundaryCount: Int? get() = stage(TraceStage.CONTOUR)?.nodes?.total

    /** Whether the glyph is round at a flat top or bottom, so the brief's overshoot line applies as a note. */
    val flatRound: Boolean get() = hasCurves && (flatTop || flatBottom)
}

/** Runs the trace chain on a [TraceSource]: the picture chain for a photograph or scan, and Fit onward for an SVG drawing. */
object TraceRunner {
    fun run(
        source: TraceSource,
        parameters: TraceParameters,
    ): TraceOutcome =
        when (source) {
            is TraceSource.Raster -> fromRaster(source, parameters)
            is TraceSource.Vector -> fromVector(source, parameters)
        }

    private fun fromRaster(
        source: TraceSource.Raster,
        parameters: TraceParameters,
    ): TraceOutcome {
        val trace = traceCell(source.raster, source.frame, parameters)
        val review = trace.review
        return TraceOutcome(
            source = source,
            parameters = parameters,
            stages = trace.stages,
            unavailable = emptySet(),
            contours = trace.contours,
            suggestedAdvance = trace.suggestedAdvanceWidth,
            flatTop = review?.flatTop ?: false,
            flatBottom = review?.flatBottom ?: false,
            hasCurves = review?.hasCurves ?: false,
            trace = trace,
        )
    }

    /**
     * An SVG drawing is the fitted outline already, so Fit keeps it exactly as drawn (law 1: the drawing is the source of truth,
     * it is not fitted again), Snap moves points that sit within the snap band of a metric line onto it, and Review measures.
     */
    private fun fromVector(
        source: TraceSource.Vector,
        parameters: TraceParameters,
    ): TraceOutcome {
        val drawn = source.contours
        val drawnCount = countOf(drawn)
        val snapParams =
            TypeConstraintParameters(parameters.snap.axisDegrees, parameters.snap.bandUnits, parameters.snap.minimumExtremumBulge)
        val snapped =
            enforceContourDirections(drawn.map { snapOutline(it, parameters.metricLines, snapParams, parameters.snap.axisDegrees) })
        val snapCount = countOf(snapped)
        val moved = movedPoints(drawn, snapped)
        val stages = mutableListOf<StageResult>()
        for (stage in listOf(TraceStage.CAPTURE, TraceStage.CLEAN, TraceStage.CONTOUR, TraceStage.CORNERS)) {
            stages += StageResult(stage, emptyList(), emptyList(), emptyList(), null, NOT_FOR_VECTOR, emptyList())
        }
        val specs = parameters.specs()
        stages +=
            StageResult(
                TraceStage.FIT,
                emptyList(),
                emptyList(),
                drawn,
                drawnCount,
                "Your SVG already has its curves, so they are kept exactly as drawn: ${points(drawnCount)}.",
                emptyList(),
            )
        stages +=
            StageResult(
                TraceStage.SNAP,
                emptyList(),
                emptyList(),
                snapped,
                snapCount,
                if (moved == 0) "Nothing needed snapping." else "${plural(moved, "point")} moved onto a metric line or an axis.",
                specs.filter { it.stage == TraceStage.SNAP },
            )
        stages +=
            StageResult(
                TraceStage.REGULARISE,
                emptyList(),
                emptyList(),
                snapped,
                snapCount,
                "Off. Regularise would even out the stroke weight and, if you ask, make the glyph symmetric; nothing was changed.",
                emptyList(),
            )
        val glyph = Glyph("trace", 0, snapped)
        val bounds = glyph.outlineBounds()
        val top = bounds?.yMax
        val bottom = bounds?.yMin
        val hasCurves = snapped.any { it.lineSegmentCount() < it.points.size / 3 }
        val flatTop = top != null && snapped.any { hasFlatSideAt(it, top) }
        val flatBottom = bottom != null && snapped.any { hasFlatSideAt(it, bottom) }
        stages +=
            StageResult(
                TraceStage.REVIEW,
                emptyList(),
                emptyList(),
                snapped,
                snapCount,
                "${points(snapCount)}, as you drew them." + overshootNote(hasCurves, flatTop, flatBottom),
                emptyList(),
            )
        val advance = suggestedAdvance(bounds?.xMax, source.script)
        return TraceOutcome(
            source = source,
            parameters = parameters,
            stages = stages,
            unavailable = setOf(TraceStage.CAPTURE, TraceStage.CLEAN, TraceStage.CONTOUR, TraceStage.CORNERS),
            contours = snapped,
            suggestedAdvance = advance,
            flatTop = flatTop,
            flatBottom = flatBottom,
            hasCurves = hasCurves,
            trace = null,
        )
    }

    /**
     * The advance a drawing made on the template suggests: the distance between the template's two advance guides, widened to the
     * ink's right edge when the drawing runs past the right guide. Zero when there is no ink. Space is where it is settled.
     */
    internal fun suggestedAdvance(
        inkRight: Int?,
        script: PrintScript,
    ): Int {
        if (inkRight == null) return 0
        val guides = SheetLayout.of(PaperSize.A4, script).guides
        return max(guides.advanceRight - guides.advanceLeft, inkRight)
    }

    private fun overshootNote(
        hasCurves: Boolean,
        flatTop: Boolean,
        flatBottom: Boolean,
    ): String =
        when {
            hasCurves && flatTop && flatBottom -> " Flat top and bottom: overshoot does not apply."
            hasCurves && flatTop -> " Flat top: overshoot does not apply there."
            hasCurves && flatBottom -> " Flat bottom: overshoot does not apply there."
            else -> ""
        }

    private const val NOT_FOR_VECTOR = "Not used: an SVG drawing is vector already, so it starts at Fit."

    private fun countOf(contours: List<Contour>): NodeCount {
        var on = 0
        var off = 0
        for (c in contours) {
            val count = c.writtenCount()
            on += count.onCurveEquivalent
            off += count.offCurve
        }
        return NodeCount(on, off)
    }

    private fun points(count: NodeCount): String =
        if (count.offCurve == 0) {
            plural(count.onCurve, "point")
        } else {
            "${plural(count.onCurve, "point")} on the outline and ${plural(count.offCurve, "handle")} off it"
        }

    private fun movedPoints(
        before: List<Contour>,
        after: List<Contour>,
    ): Int {
        var moved = 0
        for (i in before.indices) {
            val a =
                before[i]
                    .points
                    .filter { it.onCurve }
                    .map { it.point }
                    .toSet()
            val b =
                after
                    .getOrNull(i)
                    ?.points
                    ?.filter { it.onCurve }
                    ?.map { it.point }
                    ?.toSet() ?: continue
            moved += (a - b).size
        }
        return moved
    }

    private fun hasFlatSideAt(
        contour: Contour,
        y: Int,
    ): Boolean {
        val n = contour.points.size
        for (i in 0 until n / 3) {
            val a = contour.points[3 * i].point
            val b = contour.points[(3 * i + 3) % n].point
            val isLine = contour.points[3 * i + 1].point == a && contour.points[3 * i + 2].point == b
            if (isLine && abs(a.y - y) <= 1 && abs(b.y - y) <= 1 && a.x != b.x) return true
        }
        return false
    }

    internal fun plural(
        n: Int,
        word: String,
    ): String = "${grouped(n)} $word" + (if (n == 1) "" else "s")

    /** [n] with thousands separators: 1763 reads "1,763". */
    internal fun grouped(n: Int): String {
        val digits = abs(n).toString()
        val out = StringBuilder()
        for ((i, c) in digits.withIndex()) {
            if (i > 0 && (digits.length - i) % 3 == 0) out.append(',')
            out.append(c)
        }
        return (if (n < 0) "-" else "") + out
    }

    /** Rounds to one decimal, for the sentences that quote a measured number. */
    internal fun oneDecimal(x: Double): String {
        val tenths = (x * 10.0).roundToInt()
        return "${tenths / 10}.${abs(tenths % 10)}"
    }
}
