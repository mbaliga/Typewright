// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.trace

import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.OutlineBounds
import com.asoc.typewright.core.geometry.OutlineFitParameters
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.TypeConstraintParameters
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.core.geometry.detectOutlineCorners
import com.asoc.typewright.core.geometry.enforceContourDirections
import com.asoc.typewright.core.geometry.fitOutline
import com.asoc.typewright.core.geometry.lineSegmentCount
import com.asoc.typewright.core.geometry.measureEdgeRoughness
import com.asoc.typewright.core.geometry.outlineBounds
import com.asoc.typewright.core.geometry.polygonContour
import com.asoc.typewright.core.geometry.snapOutline
import com.asoc.typewright.core.geometry.writtenCount
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The stages of the trace chain (handoff M1), in order. The Trace screen shows [shownByDefault] ones (Clean, Fit,
 * Review) and keeps the other five behind "all stages".
 */
enum class TraceStage(
    val label: String,
    val shownByDefault: Boolean,
) {
    CAPTURE("Capture", false),
    CLEAN("Clean", true),
    CONTOUR("Contour", false),
    CORNERS("Corner detect", false),
    FIT("Fit", true),
    SNAP("Snap", false),
    REGULARISE("Regularise", false),
    REVIEW("Review", true),
}

/** How many points a stage's outline holds: [onCurve] on the curve, [offCurve] handles off it. A dense polygon is all on-curve. */
data class NodeCount(
    val onCurve: Int,
    val offCurve: Int,
) {
    val total: Int get() = onCurve + offCurve
}

/**
 * One stage's output. [polylines] is the dense boundary the stages before Fit work on (font units, y up); [contours]
 * is the fitted outline from Fit on. [nodes] is the stage's point count (null before there is an outline) and
 * [sentence] says in plain words what the stage did to this drawing. [parameters] are the settings the stage
 * reads, as data the screen can edit; change one with [TraceParameters.withParameter] and trace again.
 */
class StageResult(
    val stage: TraceStage,
    val polylines: List<List<Vec2>>,
    val cornerPoints: List<Vec2>,
    val contours: List<Contour>,
    val nodes: NodeCount?,
    val sentence: String,
    val parameters: List<ParameterSpec>,
)

/** One adjustable setting of a stage: what it is called, its value, its range, and whether the screen shows it without "adjust". */
data class ParameterSpec(
    val id: String,
    val stage: TraceStage,
    val label: String,
    val unit: String,
    val value: Double,
    val min: Double,
    val max: Double,
    val step: Double,
    val shownByDefault: Boolean,
    val explanation: String,
)

/** The Snap stage's settings: the band around a metric line within which a point is moved onto it, and the angle within which a side is made axis-aligned. */
data class SnapParameters(
    val bandUnits: Int = 2,
    val axisDegrees: Double = 1.5,
    val minimumExtremumBulge: Double = 0.5,
)

/**
 * Every setting of the chain, in one value: [clean], [outline] (corner detection and fit tolerance, with the fitter's
 * scale choices), [snap], the [metricLines] the Snap stage moves points onto, and whether Regularise is [regularise]d
 * (off, and not built yet). A trace is a pure function of a raster, its frame and one of these, so changing a setting
 * and tracing again is the whole of "adjust".
 */
data class TraceParameters(
    val clean: CleanParameters = CleanParameters(),
    val outline: OutlineFitParameters = OutlineFitParameters(),
    val snap: SnapParameters = SnapParameters(),
    val metricLines: List<Int> = emptyList(),
    val regularise: Boolean = false,
    /**
     * Whether the fit tolerance is raised for a drawing whose edge is rough: a pen line photographed by hand wobbles by a unit or
     * more, and a tolerance set for a clean render would then spend a node on every wobble. The setting is the floor; the
     * tolerance used is the larger of it and five times the measured wobble of the edge ([measureEdgeRoughness]).
     */
    val adaptTolerance: Boolean = true,
) {
    /** The adjustable settings of all stages, the three the screen shows by default first. */
    fun specs(): List<ParameterSpec> =
        listOf(
            ParameterSpec(
                "corner.turn",
                TraceStage.CORNERS,
                "Corner detection",
                "degrees",
                outline.cornerTurnDegrees,
                10.0,
                80.0,
                1.0,
                true,
                "How sharp a turn must be, measured over ${oneDecimal(outline.cornerLookUnits)} units each side, to count as a corner.",
            ),
            ParameterSpec(
                "fit.tolerance",
                TraceStage.FIT,
                "Fit tolerance",
                "units",
                outline.fitTolerance,
                0.5,
                8.0,
                0.5,
                true,
                "The most the outline may stray from the edge of your drawing. Smaller keeps more points; larger keeps fewer.",
            ),
            ParameterSpec(
                "snap.band",
                TraceStage.SNAP,
                "Snap band",
                "units",
                snap.bandUnits.toDouble(),
                0.0,
                8.0,
                1.0,
                true,
                "A point this close to a metric line is moved onto it, unless it is the top of a round shape.",
            ),
            ParameterSpec(
                "fit.adapt",
                TraceStage.FIT,
                "Raise tolerance for rough edges",
                "on/off",
                if (adaptTolerance) 1.0 else 0.0,
                0.0,
                1.0,
                1.0,
                false,
                "On, the tolerance is raised when the edge of the drawing is rough, so a photograph does not cost a point for every wobble.",
            ),
            ParameterSpec(
                "corner.look",
                TraceStage.CORNERS,
                "Corner look distance",
                "units",
                outline.cornerLookUnits,
                4.0,
                40.0,
                1.0,
                false,
                "How far either side of a point the corner test looks. A rounded corner much larger than this is not a corner.",
            ),
            ParameterSpec(
                "snap.axis",
                TraceStage.SNAP,
                "Axis snap",
                "degrees",
                snap.axisDegrees,
                0.0,
                5.0,
                0.5,
                false,
                "A straight side within this angle of horizontal or vertical is made exactly so.",
            ),
            ParameterSpec(
                "clean.specks",
                TraceStage.CLEAN,
                "Remove specks",
                "pixels",
                clean.despeckleMinArea.toDouble(),
                0.0,
                200.0,
                1.0,
                false,
                "Ink marks smaller than this are taken for noise.",
            ),
            ParameterSpec(
                "clean.pinholes",
                TraceStage.CLEAN,
                "Fill pinholes",
                "pixels",
                clean.holeFillMaxArea.toDouble(),
                0.0,
                200.0,
                1.0,
                false,
                "Gaps in the ink smaller than this are filled.",
            ),
        )

    /** These parameters with the setting [id] (one of [specs]'s ids) changed to [value], clamped to its range. */
    fun withParameter(
        id: String,
        value: Double,
    ): TraceParameters {
        val spec = specs().firstOrNull { it.id == id } ?: throw IllegalArgumentException("no trace parameter called '$id'")
        val v = value.coerceIn(spec.min, spec.max)
        return when (id) {
            "corner.turn" -> copy(outline = outline.copy(cornerTurnDegrees = v))
            "fit.tolerance" -> copy(outline = outline.copy(fitTolerance = v, minLineLength = 24.0 * v))
            "snap.band" -> copy(snap = snap.copy(bandUnits = v.roundToInt()))
            "fit.adapt" -> copy(adaptTolerance = v >= 0.5)
            "corner.look" -> copy(outline = outline.copy(cornerLookUnits = v))
            "snap.axis" -> copy(snap = snap.copy(axisDegrees = v))
            "clean.specks" -> copy(clean = clean.copy(despeckleMinArea = v.roundToInt()))
            "clean.pinholes" -> copy(clean = clean.copy(holeFillMaxArea = v.roundToInt()))
            else -> this
        }
    }

    private fun specsFor(stage: TraceStage): List<ParameterSpec> = specs().filter { it.stage == stage }

    internal fun forStage(stage: TraceStage): List<ParameterSpec> = specsFor(stage)
}

/** What the Review stage measured, for the screen and for the tests that hold the chain to account. */
data class ReviewFindings(
    /** The box of the ink in the cleaned cell, in font units; null when there is none. */
    val inkBounds: OutlineBounds?,
    /** The box of the fitted outline, in font units. */
    val outlineBounds: OutlineBounds?,
    /** The largest difference between a side of [inkBounds] and the same side of [outlineBounds], in font units. */
    val boundsDifference: Int,
    /** The share of pixels on which the outline, redrawn at the cell's resolution, and the cleaned ink disagree, of the ink's own area. */
    val mismatchFraction: Double,
    /** The largest distance, in font units, from the traced edge to the fitted outline. */
    val maxDeviation: Double,
    /** Whether the glyph's top is a flat straight side, so overshoot does not apply there. */
    val flatTop: Boolean,
    /** Whether the glyph's bottom is a flat straight side. */
    val flatBottom: Boolean,
    /** Whether the outline holds a curve at all (a pure polygon has no overshoot to speak of). */
    val hasCurves: Boolean,
)

/**
 * A traced cell: every stage's [StageResult] (so the scrubber can show each), the [contours] the last one produced
 * (font units, y up, outer counter-clockwise and inner clockwise, ready for `AcceptTrace`), what Review [review]ed
 * and a [suggestedAdvanceWidth] (the ink's left side bearing mirrored on the right: a starting point for Space, not a
 * judgement).
 */
class GlyphTrace(
    val frame: RasterFrame,
    val parameters: TraceParameters,
    val cleaned: CleanedCell,
    val stages: List<StageResult>,
    val contours: List<Contour>,
    val review: ReviewFindings?,
    val suggestedAdvanceWidth: Int,
) {
    val inkFound: Boolean get() = cleaned.inkFound && contours.isNotEmpty()

    fun stage(stage: TraceStage): StageResult = stages.first { it.stage == stage }

    /** The node count the glyph ends with, as the file will hold it (a straight side has no off-curve point). */
    val finalNodes: NodeCount? get() = stages.lastOrNull { it.nodes != null }?.nodes
}

/**
 * Runs the whole chain on one cell: Capture (the raster as given), Clean, Contour, Corner detect, Fit, Snap,
 * Regularise (off) and Review. Every stage keeps its own output and a plain sentence, no stage's output depends on a
 * later one, and the node count never rises from Contour to Fit: a fit that came out with more points than the
 * boundary it was fitted to is replaced by that boundary's own corners.
 */
fun traceCell(
    raster: GrayscaleRaster,
    frame: RasterFrame,
    params: TraceParameters = TraceParameters(),
): GlyphTrace {
    val stages = mutableListOf<StageResult>()
    stages +=
        StageResult(
            TraceStage.CAPTURE,
            emptyList(),
            emptyList(),
            emptyList(),
            null,
            "The photograph of the cell, ${grouped(raster.width)} by ${grouped(raster.height)} pixels; one pixel is " +
                "${oneDecimal(frame.unitsPerPx)} font units.",
            emptyList(),
        )
    val cleaned = cleanCell(raster, params.clean)
    val cleanSentence =
        if (!cleaned.inkFound) {
            "No ink found in this cell."
        } else {
            "Found ink with a stroke about ${grouped((cleaned.strokeWidthPx * frame.unitsPerPx).roundToInt())} units wide; " +
                "removed ${plural(cleaned.specksRemoved, "speck")} and filled ${plural(cleaned.pinholesFilled, "pinhole")}."
        }
    stages += StageResult(TraceStage.CLEAN, emptyList(), emptyList(), emptyList(), null, cleanSentence, params.forStage(TraceStage.CLEAN))
    if (!cleaned.inkFound) return GlyphTrace(frame, params, cleaned, stages, emptyList(), null, 0)

    val polylines = boundaryPolylines(cleaned, frame)
    val boundaryPoints = polylines.sumOf { it.size }
    stages +=
        StageResult(
            TraceStage.CONTOUR,
            polylines,
            emptyList(),
            emptyList(),
            NodeCount(boundaryPoints, 0),
            "Followed the edge of the ink: ${plural(polylines.size, "outline")}, ${grouped(boundaryPoints)} boundary points. " +
                "Still a polygon.",
            emptyList(),
        )

    val detections = polylines.map { detectOutlineCorners(it, params.outline) }
    val cornerPoints = detections.flatMap { d -> d.cornerIndices.map { d.samples[it] } }
    stages +=
        StageResult(
            TraceStage.CORNERS,
            detections.map { it.samples },
            cornerPoints,
            emptyList(),
            NodeCount(cornerPoints.size, 0),
            "Found ${plural(cornerPoints.size, "corner")} where the edge turns ${oneDecimal(params.outline.cornerTurnDegrees)} degrees " +
                "or more over ${oneDecimal(params.outline.cornerLookUnits)} units.",
            params.forStage(TraceStage.CORNERS),
        )

    val roughness = detections.maxOfOrNull { measureEdgeRoughness(it.samples, params.outline.sampleSpacing) } ?: 0.0
    val adapted = if (params.adaptTolerance) (TOLERANCE_PER_WOBBLE * roughness).coerceAtMost(MAX_ADAPTED_TOLERANCE) else 0.0
    val outline =
        if (adapted >
            params.outline.fitTolerance
        ) {
            params.outline.copy(fitTolerance = adapted, minLineLength = 24.0 * adapted)
        } else {
            params.outline
        }
    val fitted =
        detections.mapIndexed { index, detected ->
            val fit = fitOutline(detected, outline)
            if (fit.contour.writtenCount().let { it.onCurveEquivalent + it.offCurve } > polylines[index].size) {
                val ring = detected.cornerIndices.map { detected.samples[it] }.ifEmpty { detected.samples }
                OutlineResult(
                    polygonContour(ring.map { Point(it.x.roundToInt(), it.y.roundToInt()) }, CurveFormat.CUBIC),
                    0,
                    fit.maxDeviation,
                )
            } else {
                OutlineResult(fit.contour, fit.curveCount, fit.maxDeviation)
            }
        }
    val fitContours = fitted.map { it.contour }
    val fitNodes = nodesOf(fitContours)
    stages +=
        StageResult(
            TraceStage.FIT,
            emptyList(),
            emptyList(),
            fitContours,
            fitNodes,
            fitSentence(boundaryPoints, fitContours, fitNodes) +
                if (outline.fitTolerance > params.outline.fitTolerance) {
                    " The tolerance was raised to ${oneDecimal(outline.fitTolerance)} units because this edge wobbles by about " +
                        "${oneDecimal(roughness)}."
                } else {
                    ""
                },
            params.forStage(TraceStage.FIT),
        )

    val snapParams = TypeConstraintParameters(params.snap.axisDegrees, params.snap.bandUnits, params.snap.minimumExtremumBulge)
    val snapped =
        enforceContourDirections(
            fitContours.map { snapOutline(it, params.metricLines, snapParams, params.snap.axisDegrees) },
        )
    val snapNodes = nodesOf(snapped)
    val moved = movedNodeCount(fitContours, snapped)
    stages +=
        StageResult(
            TraceStage.SNAP,
            emptyList(),
            emptyList(),
            snapped,
            snapNodes,
            snapSentence(moved, snapNodes.total - fitNodes.total),
            params.forStage(TraceStage.SNAP),
        )
    stages +=
        StageResult(
            TraceStage.REGULARISE,
            emptyList(),
            emptyList(),
            snapped,
            snapNodes,
            "Off. Regularise would even out the stroke weight and, if you ask, make the glyph symmetric; nothing was changed.",
            emptyList(),
        )

    val findings = review(cleaned, frame, snapped, fitted.maxOf { it.maxDeviation })
    stages +=
        StageResult(
            TraceStage.REVIEW,
            emptyList(),
            emptyList(),
            snapped,
            snapNodes,
            reviewSentence(snapNodes, findings),
            emptyList(),
        )
    val bounds = findings.outlineBounds
    val advance = if (bounds == null) 0 else bounds.xMax + max(bounds.xMin, 0)
    return GlyphTrace(frame, params, cleaned, stages, snapped, findings, advance)
}

private class OutlineResult(
    val contour: Contour,
    val curves: Int,
    val maxDeviation: Double,
)

/** The dense boundary of the cleaned ink, one polyline per outline (outer and inner), in font units. */
private fun boundaryPolylines(
    cleaned: CleanedCell,
    frame: RasterFrame,
): List<List<Vec2>> {
    val w = cleaned.width
    val h = cleaned.height
    // A one-pixel border of paper all round, so ink that touches the edge still closes into a loop.
    val padded = w + 2
    val loops =
        traceContours(
            padded,
            h + 2,
            ScalarField { x, y ->
                if (x in 1..w && y in 1..h) cleaned.field[(y - 1) * w + (x - 1)] else 0.0
            },
        )
    return loops.map { loop -> loop.map { frame.unitsAt(it.x - 1.0, it.y - 1.0) } }.filter { it.size >= 3 }
}

private fun nodesOf(contours: List<Contour>): NodeCount {
    var on = 0
    var off = 0
    for (c in contours) {
        val count = c.writtenCount()
        on += count.onCurveEquivalent
        off += count.offCurve
    }
    return NodeCount(on, off)
}

private fun movedNodeCount(
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

private fun fitSentence(
    boundaryPoints: Int,
    contours: List<Contour>,
    nodes: NodeCount,
): String {
    val lines = contours.sumOf { it.lineSegmentCount() }
    val segments = contours.sumOf { it.points.size / 3 }
    val curves = segments - lines
    val head = "${grouped(boundaryPoints)} boundary points become "
    return when {
        nodes.total == 0 -> {
            "Nothing to fit."
        }

        curves == 0 -> {
            head + "${plural(nodes.onCurve, "corner")} joined by straight lines. No curves, so no off-curve points."
        }

        lines == 0 -> {
            head + "${plural(nodes.onCurve, "point")} on the curve and ${plural(nodes.offCurve, "handle")} off it: all curves."
        }

        else -> {
            head + "${plural(nodes.onCurve, "point")} on the outline and ${plural(nodes.offCurve, "handle")} off it: " +
                "${plural(lines, "straight side")} and ${plural(curves, "curve")}."
        }
    }
}

private fun snapSentence(
    moved: Int,
    added: Int,
): String {
    val parts = mutableListOf<String>()
    if (moved > 0) parts += "${plural(moved, "point")} moved onto the axes or a metric line"
    if (added > 0) parts += "${plural(added, "point")} added at curve extremes"
    return if (parts.isEmpty()) "Nothing needed snapping." else parts.joinToString("; ") + "."
}

private fun reviewSentence(
    nodes: NodeCount,
    findings: ReviewFindings,
): String {
    val agree = ((1.0 - findings.mismatchFraction) * 1000.0).roundToInt() / 10.0
    val head =
        "${plural(nodes.total, "point")}. Redrawn from the outline it matches $agree% of your ink, and it never strays more than " +
            "${oneDecimal(findings.maxDeviation)} units from your drawing."
    val flat =
        when {
            findings.hasCurves && findings.flatTop && findings.flatBottom -> " Flat top and bottom: overshoot does not apply."
            findings.hasCurves && findings.flatTop -> " Flat top: overshoot does not apply there."
            findings.hasCurves && findings.flatBottom -> " Flat bottom: overshoot does not apply there."
            else -> ""
        }
    return head + flat
}

private fun review(
    cleaned: CleanedCell,
    frame: RasterFrame,
    contours: List<Contour>,
    maxDeviation: Double,
): ReviewFindings {
    val glyph = Glyph("trace", 0, contours)
    val outline = glyph.outlineBounds()
    val ink = inkBounds(cleaned, frame)
    val difference =
        if (ink == null || outline == null) {
            0
        } else {
            maxOf(abs(ink.xMin - outline.xMin), abs(ink.xMax - outline.xMax), abs(ink.yMin - outline.yMin), abs(ink.yMax - outline.yMax))
        }
    val coverage = coverageOf(contours, frame, cleaned.width, cleaned.height)
    var disagree = 0
    var inkArea = 0
    for (i in coverage.indices) {
        val inside = coverage[i] >= 0.5
        if (cleaned.mask.ink[i]) inkArea++
        if (inside != cleaned.mask.ink[i]) disagree++
    }
    val top = outline?.yMax
    val bottom = outline?.yMin
    return ReviewFindings(
        inkBounds = ink,
        outlineBounds = outline,
        boundsDifference = difference,
        mismatchFraction = if (inkArea == 0) 0.0 else disagree.toDouble() / inkArea,
        maxDeviation = maxDeviation,
        flatTop = top != null && contours.any { hasFlatSideAt(it, top) },
        flatBottom = bottom != null && contours.any { hasFlatSideAt(it, bottom) },
        hasCurves = contours.any { it.lineSegmentCount() < it.points.size / 3 },
    )
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

private fun inkBounds(
    cleaned: CleanedCell,
    frame: RasterFrame,
): OutlineBounds? {
    var minX = Int.MAX_VALUE
    var minY = Int.MAX_VALUE
    var maxX = -1
    var maxY = -1
    for (y in 0 until cleaned.height) {
        for (x in 0 until cleaned.width) {
            if (!cleaned.mask.ink[y * cleaned.width + x]) continue
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
    }
    if (maxX < 0) return null
    return OutlineBounds(
        xMin = (frame.leftUnits + minX / frame.pxPerUnit).roundToInt(),
        yMin = (frame.topUnits - (maxY + 1) / frame.pxPerUnit).roundToInt(),
        xMax = (frame.leftUnits + (maxX + 1) / frame.pxPerUnit).roundToInt(),
        yMax = (frame.topUnits - minY / frame.pxPerUnit).roundToInt(),
    )
}

/** The most the adaptive tolerance may rise to, in font units: past this a drawing is too rough to fit and the person should redraw or rescan it. */
private const val MAX_ADAPTED_TOLERANCE = 8.0

/** How many times the measured wobble of an edge the tolerance is raised to (our heuristic, set on the synthetic photographs in the capture tests). */
private const val TOLERANCE_PER_WOBBLE = 5.0

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

internal fun oneDecimal(x: Double): String {
    val tenths = (x * 10.0).roundToInt()
    return "${tenths / 10}.${abs(tenths % 10)}".let { if (tenths < 0 && tenths > -10) "-$it" else it }
}

private fun plural(
    n: Int,
    word: String,
): String = "${grouped(n)} $word" + (if (n == 1) "" else "s")
