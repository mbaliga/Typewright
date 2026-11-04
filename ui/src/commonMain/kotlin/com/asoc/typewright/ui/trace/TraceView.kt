// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.trace

import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.engine.trace.ParameterSpec
import com.asoc.typewright.engine.trace.TraceStage
import com.asoc.typewright.qa.corpus.NodeEconomyVerdict
import com.asoc.typewright.qa.corpus.Quartiles
import com.asoc.typewright.qa.corpus.verdictFor
import com.asoc.typewright.scripts.sheets.PaperSize
import com.asoc.typewright.scripts.sheets.SheetLayout
import com.asoc.typewright.ui.capture.TraceSource
import kotlin.math.roundToLong

/**
 * The words and numbers the Trace screen shows, worked out in one place so they can be tested without a screen: the header's
 * count, the stages the scrubber offers, the parameters under the selected stage, and the line under the box plot.
 */
object TraceView {
    /** The stages the scrubber offers: Clean, Fit and Review, or all eight behind "All stages" (an SVG drawing starts at Fit). */
    fun scrubberStages(
        outcome: TraceOutcome,
        all: Boolean,
    ): List<TraceStage> = TraceStage.entries.filter { (all || it.shownByDefault) && it !in outcome.unavailable }

    /** The stage to show when a drawing is first opened, or when the stage that was selected is no longer offered: Fit, the one the board opens on. */
    fun defaultStage(
        outcome: TraceOutcome,
        all: Boolean,
        previous: TraceStage? = null,
    ): TraceStage {
        val offered = scrubberStages(outcome, all)
        return when {
            previous != null && previous in offered -> previous
            TraceStage.FIT in offered -> TraceStage.FIT
            else -> offered.firstOrNull() ?: TraceStage.FIT
        }
    }

    /** "+5 in all stages" when the scrubber is showing three of eight, otherwise empty. */
    fun hiddenStagesNote(
        outcome: TraceOutcome,
        all: Boolean,
    ): String {
        if (all) return ""
        val hidden = TraceStage.entries.count { !it.shownByDefault && it !in outcome.unavailable }
        return if (hidden > 0) "+$hidden in all stages" else ""
    }

    /**
     * The settings to show with [stage] selected. With every stage on, the stage's own; otherwise the three the screen leads with
     * (corner detection, fit tolerance, snap band) on Fit and Review, which is where their effect is seen, and none on Clean.
     * A setting that does not apply to the drawing (corner detection and fit tolerance for an SVG) is left out.
     */
    fun parametersFor(
        outcome: TraceOutcome,
        stage: TraceStage,
        all: Boolean,
    ): List<ParameterSpec> {
        val specs =
            outcome.parameters.specs().filter {
                it.stage !in outcome.unavailable &&
                    !(it.stage == TraceStage.FIT && outcome.trace == null)
            }
        return if (all) {
            specs.filter { it.stage == stage }
        } else {
            when (stage) {
                TraceStage.FIT, TraceStage.REVIEW -> specs.filter { it.shownByDefault }
                else -> specs.filter { it.stage == stage && it.shownByDefault }
            }
        }
    }

    /** [value] as the inspector prints it: a whole number as one, otherwise one decimal. */
    fun valueText(spec: ParameterSpec): String = valueText(spec.value, spec)

    fun valueText(
        value: Double,
        spec: ParameterSpec,
    ): String {
        if (spec.unit == "on/off") return if (value >= 0.5) "on" else "off"
        val tenths = (value * 10.0).roundToLong()
        val text = if (tenths % 10L == 0L) (tenths / 10L).toString() else "${tenths / 10L}.${kotlin.math.abs(tenths % 10L)}"
        return "$text ${spec.unit}"
    }

    /** [spec]'s value stepped by [direction] (+1 or -1) times its step, held to its range. */
    fun stepped(
        spec: ParameterSpec,
        direction: Int,
    ): Double = (spec.value + direction * spec.step).coerceIn(spec.min, spec.max)

    /** "T · 1,763 points": the glyph and the count at the selected stage. */
    fun title(
        glyph: String,
        count: Int?,
    ): String = if (count == null) glyph else "$glyph · ${TraceRunner.plural(count, "point")}"

    /** "was 1,763 · sheet 1 · cell 20 of 24": what the count was before the fit, then where the drawing came from. */
    fun subtitle(
        outcome: TraceOutcome?,
        shownCount: Int?,
        origin: String,
    ): String {
        val was = outcome?.boundaryCount?.takeIf { shownCount != null && it != shownCount }
        return listOfNotNull(was?.let { "was ${TraceRunner.grouped(it)}" }, origin).joinToString(" · ")
    }

    /**
     * The line under the box plot: how many faces the box is over and what it says about this glyph, and the live count's verdict
     * as a shape and a word, never colour alone. [styleName] is the class the box belongs to; [heuristic] says the class itself
     * was read by our heuristic (law 5).
     */
    fun boxLine(
        glyph: String,
        count: Int,
        box: Quartiles,
        styleName: String,
        heuristic: Boolean,
    ): String {
        val verdict =
            when (verdictFor(count, box)) {
                NodeEconomyVerdict.IN_RANGE -> "✓ in range"
                NodeEconomyVerdict.ABOVE -> "◐ above the middle half"
                NodeEconomyVerdict.OUTLIER -> "● outlier"
            }
        val span = "${plain(box.q1)}–${plain(box.q3)}"
        return "${box.n} $styleName · $glyph $span · max ${plain(box.max)} · $verdict" + if (heuristic) " · class: our heuristic" else ""
    }

    private fun plain(x: Double): String {
        val halves = (x * 2.0).roundToLong()
        return if (halves % 2L == 0L) (halves / 2L).toString() else "${halves / 2L}.5"
    }

    /** The window the drawing is shown in, in font units with y up: the template cell's own, which is the whole of the room the letter was drawn in. */
    fun viewBox(source: TraceSource): ViewBox {
        val layout = SheetLayout.of(PaperSize.A4, source.script)
        val box = layout.cellBoxes.first()
        val frame = layout.frame
        val width = box.width * frame.unitsPerMm
        val height = box.height * frame.unitsPerMm
        return ViewBox(frame.leftUnits, frame.topUnits, width, height)
    }

    /** The on-curve points that Snap moved, found by comparing the outline before and after; empty when the lists do not line up. */
    fun movedBySnap(
        before: List<Contour>,
        after: List<Contour>,
    ): List<com.asoc.typewright.core.geometry.Point> {
        val out = ArrayList<com.asoc.typewright.core.geometry.Point>()
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
            out += (b - a)
        }
        return out
    }
}

/** A window onto font space: its left edge, its top edge (y up), and its size, all in font units. */
data class ViewBox(
    val left: Double,
    val top: Double,
    val width: Double,
    val height: Double,
)
