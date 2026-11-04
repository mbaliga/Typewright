// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.check

import com.asoc.typewright.qa.corpus.Quartiles
import com.asoc.typewright.qa.report.FaceCount
import com.asoc.typewright.qa.report.FaceSpread
import com.asoc.typewright.qa.report.statText
import com.asoc.typewright.qa.report.withThousands
import kotlin.math.ln
import kotlin.math.max

/**
 * The log axis every box plot in the app shares, 3 to 3,000 points: a traced T sits two orders of
 * magnitude to the right of a drawn one, and only a log scale keeps both on the page (UI_SPEC §3). A
 * position is a fraction of the axis width, 0 at 3 and 1 at 3,000, so a screen multiplies by whatever
 * width it has and the maths here never knows about pixels.
 */
object LogAxis {
    const val MIN = 3.0
    const val MAX = 3000.0

    /** The labelled ticks, a power of two apiece. */
    val TICKS: List<Int> = listOf(4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048)

    private val SPAN = ln(MAX / MIN)

    /** Where [value] sits along the axis, 0 to 1; a value off either end is held at that end. */
    fun position(value: Double): Double = (ln(value.coerceIn(MIN, MAX) / MIN) / SPAN)

    /** Whether [value] is off the right-hand end, where its point is held at the edge and the number beside it carries the truth. */
    fun isClipped(value: Double): Boolean = value > MAX

    /** A tick's label as the axis prints it: `4`, `512`, `1k`, `2k`. */
    fun label(tick: Int): String = if (tick >= 1000) "${tick / 1000}k" else tick.toString()
}

/** One glyph's count and the box it is judged against: the five numbers of the corpus's box and the user's own [value]. */
data class BoxPlotSpec(
    val min: Double,
    val q1: Double,
    val median: Double,
    val q3: Double,
    val max: Double,
    val value: Int,
) {
    companion object {
        fun of(
            box: Quartiles,
            value: Int,
        ) = BoxPlotSpec(box.min, box.q1, box.med, box.q3, box.max, value)
    }
}

/**
 * Where a box plot's marks go along the axis, as fractions of its width: the whiskers' ends, the box
 * from [q1] to [q3] with its median, and the user's point. [pointClipped] is true when the point is
 * held at the right-hand end because the count is beyond the axis.
 */
data class BoxPlotMarks(
    val whiskerLow: Double,
    val q1: Double,
    val median: Double,
    val q3: Double,
    val whiskerHigh: Double,
    val point: Double,
    val pointClipped: Boolean,
)

/** [BoxPlotSpec] laid out on [LogAxis]. */
fun BoxPlotSpec.marks(): BoxPlotMarks =
    BoxPlotMarks(
        whiskerLow = LogAxis.position(min),
        q1 = LogAxis.position(q1),
        median = LogAxis.position(median),
        q3 = LogAxis.position(q3),
        whiskerHigh = LogAxis.position(max),
        point = LogAxis.position(value.toDouble()),
        pointClipped = LogAxis.isClipped(value.toDouble()),
    )

/** The width of the drawn box in [axisWidth] units, never narrower than [minimum] so a box whose quartiles coincide is still seen. */
fun BoxPlotMarks.boxWidth(
    axisWidth: Double,
    minimum: Double,
): Double = max(minimum, (q3 - q1) * axisWidth)

/** What a screen reader says for a row: the glyph, the count, the box and the whiskers, in the numbers a person reads. */
fun BoxPlotSpec.description(glyph: String): String =
    "$glyph: ${value.withThousands()} against a box of ${q1.statText()} to ${q3.statText()}, whiskers ${min.statText()} to ${max.statText()}"

/** One reference face's point in the opened row, at [fraction] along the axis and [y] units down from the row's top. */
data class FaceDot(
    val family: String,
    val count: Int,
    val fraction: Double,
    val y: Double,
)

/**
 * The reference faces of an opened row as dots. Faces with the same count sit in one column at one
 * position (the position is the count, so it is never nudged sideways), stacked from [top] down to at
 * most [bottom]: [step] apart, closer when there are so many that they would run past [bottom]. A face
 * alone at its count is set at a height that depends only on where it comes in the order, spread over the
 * room between [top] and [bottom] so neighbours do not hide each other; the height means nothing.
 */
fun faceDots(
    spread: FaceSpread,
    top: Double,
    bottom: Double,
    step: Double = 3.0,
): List<FaceDot> {
    val room = (bottom - top).coerceAtLeast(0.0)
    var order = 0
    return spread.faces
        .groupBy { it.count }
        .entries
        .sortedBy { it.key }
        .flatMap { (count, faces) ->
            val fraction = LogAxis.position(count.toDouble())
            if (faces.size == 1) {
                val y = top + room * ((order++ * SCATTER_STRIDE) % SCATTER_SLOTS) / (SCATTER_SLOTS - 1)
                listOf(FaceDot(faces.single().family, count, fraction, y))
            } else {
                order++
                val gap = minOf(step, room / (faces.size - 1))
                faces.mapIndexed { index, face -> FaceDot(face.family, count, fraction, top + gap * index) }
            }
        }
}

private const val SCATTER_STRIDE = 7
private const val SCATTER_SLOTS = 17

/**
 * The line under an opened row naming the extremes, as the board prints it: `FEWEST 8 · ALATA + 21 · MOST 28 · MONOTON`.
 * A `+ N` is how many other faces share that count.
 */
fun facesCaption(spread: FaceSpread): String {
    fun named(
        label: String,
        face: FaceCount,
        also: Int,
    ): String {
        val others = if (also > 0) " + $also" else ""
        return "$label ${face.count.withThousands()} · ${face.family.uppercase()}$others"
    }
    return named("FEWEST", spread.fewest, spread.fewestAlso) + " · " + named("MOST", spread.most, spread.mostAlso)
}
