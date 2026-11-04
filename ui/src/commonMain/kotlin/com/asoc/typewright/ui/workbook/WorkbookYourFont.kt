// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.workbook

import com.asoc.typewright.campaign.Demonstration
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.qa.corpus.style.InkInterval
import com.asoc.typewright.qa.corpus.style.inkBounds
import com.asoc.typewright.qa.corpus.style.inkIntervals
import com.asoc.typewright.qa.corpus.style.lineCrossings
import com.asoc.typewright.ui.learn.StrokeProbeReading
import com.asoc.typewright.ui.learn.strokeProbe
import com.asoc.typewright.ui.project.ProjectFont
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What a workbook task's demonstration quotes about the lesson's sample font, measured instead on the
 * open project's own letters. The lesson text states figures for the sample (stem 44, counter 292…);
 * those are the lesson's and are shown as such. These are the person's, read off their `n`, `o`, `H` and
 * `b` by straight probe lines through the outlines: nothing here is a figure from another font, and a
 * value the font cannot give (it has no `n`, or states no x-height) is `null` and shown as "not measured",
 * never a stand-in.
 *
 * Where each probe line is cast: `n`'s stems 35% of the way up the letter, `H`'s stems 20% up (below the
 * crossbar), `o` through its middle; the stem, bar and contrast probe is the Overlay tab's own
 * ([strokeProbe] on `b`, `H` and `o`), so the Workbook and the Overlay agree.
 */
class YourFontReading(
    /** The x-height the font's own info states, or null when it states none. */
    val xHeight: Int?,
    /** The widths of the ink a probe line crosses on `n`, left to right: its two stems. */
    val nStems: List<Double>,
    /** The same on `H`. */
    val hStems: List<Double>,
    /** `o`'s left stroke where the probe through its middle crosses it. */
    val oStroke: Double?,
    /** The gap between `o`'s two strokes on that line: its counter's width. */
    val oCounter: Double?,
    /** How far `o`'s top lies above the stated x-height (negative when it falls short), or null without an `o` or an x-height. */
    val oOvershootTop: Double?,
    /** How far `o`'s bottom lies below the baseline (negative when it stops short), or null without an `o`. */
    val oOvershootBottom: Double?,
    /** `n`'s left and right sidebearings, from its ink and its advance. */
    val nSidebearings: Pair<Double, Double>?,
    /** `o`'s. */
    val oSidebearings: Pair<Double, Double>?,
    /** The Overlay tab's reading: stem (`b`), bar (`H`) and contrast (`o`). */
    val probe: StrokeProbeReading,
    /** Which of the letters read here the font has a glyph for. */
    val has: Set<Char>,
)

/** How far up a letter's ink the probe line for its stems is cast, as a fraction of the ink's height. */
internal const val N_STEM_PROBE_HEIGHT = 0.35
internal const val H_STEM_PROBE_HEIGHT = 0.2
internal const val MIDDLE_PROBE_HEIGHT = 0.5

/** Measures [font]'s own `n`, `o`, `H` and `b`; see [YourFontReading]. */
fun measureYourFont(font: ProjectFont): YourFontReading {
    val n = font.glyphFor('n')
    val o = font.glyphFor('o')
    val h = font.glyphFor('H')
    val b = font.glyphFor('b')
    val oMiddle = o?.let { horizontalInk(it, MIDDLE_PROBE_HEIGHT) }.orEmpty()
    val strokesOfO = oMiddle.takeIf { it.size == 2 }
    val xHeight = font.ufo.fontInfo.xHeight
    val oBounds = o?.inkBounds()
    return YourFontReading(
        xHeight = xHeight,
        nStems = n?.let { horizontalInk(it, N_STEM_PROBE_HEIGHT) }.orEmpty().map { it.length },
        hStems = h?.let { horizontalInk(it, H_STEM_PROBE_HEIGHT) }.orEmpty().map { it.length },
        oStroke = strokesOfO?.first()?.length,
        oCounter = strokesOfO?.let { it[1].start - it[0].end },
        oOvershootTop = if (oBounds != null && xHeight != null) oBounds.maxY - xHeight else null,
        oOvershootBottom = oBounds?.let { -it.minY },
        nSidebearings = n?.let { sidebearings(it) },
        oSidebearings = o?.let { sidebearings(it) },
        probe = strokeProbe(b, h, o),
        has = listOf('n' to n, 'o' to o, 'H' to h, 'b' to b).filter { it.second != null }.map { it.first }.toSet(),
    )
}

/** The ink a horizontal line crosses, left to right, [heightFraction] of the way up [glyph]'s ink. */
private fun horizontalInk(
    glyph: Glyph,
    heightFraction: Double,
): List<InkInterval> {
    val bounds = glyph.inkBounds() ?: return emptyList()
    val y = bounds.minY + bounds.height * heightFraction
    return inkIntervals(glyph.lineCrossings(Vec2(bounds.minX - 1.0, y), Vec2(1.0, 0.0))).sortedBy { it.start }
}

private fun sidebearings(glyph: Glyph): Pair<Double, Double>? {
    val bounds = glyph.inkBounds() ?: return null
    return bounds.minX to (glyph.advanceWidth - bounds.maxX)
}

/**
 * The person's own version of a task's demonstration: [heroText] set large in their letters where the
 * demonstration has such a sample (task 4's `noHO`), and the measured [lines] beside it.
 */
class YourFontDemonstration(
    val heroText: String?,
    val lines: List<String>,
)

/**
 * What the open font measures for [taskIndex]'s demonstration, or null where that task's demonstration is not
 * about a font's measurements (a lesson scene, published figures, a Fontbakery tally, a repository pattern): the
 * lesson's own text stands alone there and says whose it is.
 */
fun yourFontDemonstration(
    taskIndex: Int,
    reading: YourFontReading,
): YourFontDemonstration? =
    when (taskIndex) {
        2 -> YourFontDemonstration(null, listOf(probeLine(reading)))
        3 -> YourFontDemonstration(null, metricsLines(reading))
        4 -> YourFontDemonstration("noHO", controlCharacterLines(reading))
        5 -> YourFontDemonstration(null, listOf(stemsLine(reading)))
        7 -> YourFontDemonstration(null, spacingLines(reading))
        else -> null
    }

/**
 * What a task's demonstration shows, decided apart from drawing it: [yourFont] (the open font's own
 * measurements, or null where the task has none), the lesson's own [lesson] demonstration, and the [lessonHeading]
 * that says whose the lesson's figures are. The screen draws these strings as they are; nothing about the lesson's
 * sample is set in the person's letters or worded as theirs.
 */
class DemonstrationView(
    val yourFont: YourFontDemonstration?,
    val lessonHeading: String,
    val lesson: Demonstration,
)

/** [DemonstrationView] for [taskIndex]'s [demonstration], measuring [font] where the task quotes the sample's figures. */
fun demonstrationView(
    taskIndex: Int,
    demonstration: Demonstration,
    font: ProjectFont,
): DemonstrationView {
    val yourFont =
        if (demonstration is Demonstration.StatDemonstration) yourFontDemonstration(taskIndex, measureYourFont(font)) else null
    return DemonstrationView(yourFont, lessonSampleHeading(demonstration, quotesTheSampleFont = yourFont != null), demonstration)
}

/**
 * The heading over the lesson's own figures: whose they are, in words. A task that quotes the sample font's
 * measurements ([quotesTheSampleFont]: the open font has figures of its own beside them) says "the lesson's sample
 * font"; the other lesson figures (published ranges, a Fontbakery tally, a repository pattern) say they come from the
 * lesson. Either way they are said not to be the person's.
 */
internal fun lessonSampleHeading(
    demonstration: Demonstration,
    quotesTheSampleFont: Boolean,
): String =
    when {
        demonstration is Demonstration.SceneDemonstration -> "Demonstration · lesson scene"
        quotesTheSampleFont -> "Demonstration · the lesson's sample font, not your font"
        else -> "Demonstration · from the lesson, not your font"
    }

private const val NOT_MEASURED = "not measured"

private fun missing(letter: Char): String = "not measured: your font has no $letter"

/** [value] in font units: a whole number when it is one to within a twentieth of a unit, else one decimal; "not measured" when null. */
internal fun units(value: Double?): String {
    if (value == null) return NOT_MEASURED
    val whole = value.roundToInt()
    return if (abs(value - whole) < WHOLE_WITHIN) whole.toString() else ((value * 10).roundToInt() / 10.0).toString()
}

private const val WHOLE_WITHIN = 0.05

/** A ratio such as contrast, to two decimals; "not measured" when null. */
internal fun ratio(value: Double?): String = if (value == null) NOT_MEASURED else ((value * 100).roundToInt() / 100.0).toString()

private fun signedUnits(value: Double): String {
    val text = units(value)
    return when {
        text == "0" -> "0"
        value > 0 -> "+$text"
        else -> "−${text.removePrefix("-")}"
    }
}

private fun probeLine(r: YourFontReading): String =
    "stem ${units(r.probe.stemUnits)} (b) -- bar ${units(r.probe.barUnits)} (H) -- contrast ${ratio(r.probe.contrast)} (o)"

private fun metricsLines(r: YourFontReading): List<String> =
    buildList {
        add(
            if (r.xHeight != null) {
                "baseline 0 -- x-height ${r.xHeight} (your font's own figure)"
            } else {
                "baseline 0 -- x-height not stated in your font's info"
            },
        )
        add(if ('n' in r.has) "stem ${units(r.nStems.firstOrNull())} units (n)" else missing('n'))
        add(overshootLine(r, prefix = "o: overshoot "))
    }

private fun controlCharacterLines(r: YourFontReading): List<String> =
    buildList {
        add(
            if ('o' in r.has) {
                "stem ${units(r.oStroke)} -- counter ${units(r.oCounter)} (o, through its middle)"
            } else {
                missing('o')
            },
        )
        add(overshootLine(r, prefix = "overshoot "))
    }

private fun overshootLine(
    r: YourFontReading,
    prefix: String,
): String {
    if ('o' !in r.has) return missing('o')
    val bottom = r.oOvershootBottom
    val top = r.oOvershootTop
    if (bottom == null) return "${prefix}not measured"
    if (top ==
        null
    ) {
        return "${prefix}${signedUnits(bottom)} below the baseline; the top is not measured because your font states no x-height"
    }
    return if (abs(top) < FLAT_WITHIN && abs(bottom) < FLAT_WITHIN) {
        "${prefix}0, flat top and bottom"
    } else {
        "${prefix}${signedUnits(top)} at the top, ${signedUnits(bottom)} at the bottom"
    }
}

private const val FLAT_WITHIN = 0.5

private fun stemsLine(r: YourFontReading): String {
    fun stems(
        letter: Char,
        widths: List<Double>,
    ): String =
        when {
            letter !in r.has -> "no $letter in your font"
            widths.size < 2 -> "not measured"
            else -> widths.joinToString(" and ") { units(it) }
        }
    return "n's stems: ${stems('n', r.nStems)}; H's stems: ${stems('H', r.hStems)}"
}

private fun spacingLines(r: YourFontReading): List<String> =
    buildList {
        add(sidebearingLine('n', r.nSidebearings, 'n' in r.has))
        add(sidebearingLine('o', r.oSidebearings, 'o' in r.has))
        val oSides = r.oSidebearings
        val nSides = r.nSidebearings
        val counter = r.oCounter
        if (oSides != null && nSides != null && counter != null && counter > 0 && oSides.first + oSides.second > 0) {
            val o = (oSides.first + oSides.second) / 2
            val n = (nSides.first + nSides.second) / 2
            add("o's sidebearings are ${ratio(o / counter)} of its counter width; n's are ${ratio(n / o)} times o's")
        }
    }

private fun sidebearingLine(
    letter: Char,
    sides: Pair<Double, Double>?,
    present: Boolean,
): String =
    when {
        !present -> "not measured: your font has no $letter"
        sides == null -> "$letter: not measured"
        else -> "$letter: LSB ${units(sides.first)} -- RSB ${units(sides.second)}"
    }
