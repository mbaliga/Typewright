// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.campaign

import com.asoc.typewright.core.font.sfnt.readSfntFont
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.qa.corpus.style.contrastRatio
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Every number the workbook quotes as a property of the sample font (Hyle Deco, the repository's
 * OFL fixture) is read back out of the workbook text and compared with the font itself, so a
 * quoted figure cannot drift from the file it describes. The figures come from `workbook-latin.yaml`,
 * not from this test: edit the workbook and this fails if the font does not agree.
 *
 * Demonstrations that are not about the font file are listed in [NOT_FROM_THE_FONT] with the reason,
 * and a new demonstration labelled with the sample's name must be added to one list or the other.
 */
class WorkbookMeasuredNumbersTest {
    private val tasks = WorkbookLatinContent.load()
    private val font = readSfntFont(File("../fonts/HyleDeco-Regular.ttf").readBytes())

    private fun glyph(char: Char): Glyph = assertNotNull(font.glyphForCodePoint(char.code), "the font has no '$char'")

    private fun stats(
        taskIndex: Int,
        label: String,
    ): List<String> =
        tasks
            .single { it.index == taskIndex }
            .let { it.demonstration as Demonstration.StatDemonstration }
            .also { assertEquals(label, it.label, "task $taskIndex's demonstration") }
            .stats

    /** The first capture group of [pattern] in the one stat line that matches it, as numbers. */
    private fun quoted(
        lines: List<String>,
        pattern: String,
    ): List<Double> {
        val match = lines.mapNotNull { Regex(pattern).find(it) }.firstOrNull() ?: error("no stat line matches $pattern in $lines")
        return match.groupValues.drop(1).map { it.toDouble() }
    }

    // ---- Measuring the outline: the sample is a polygon font (straight edges only), so a scan line is exact. ----

    private fun edges(glyph: Glyph): List<Pair<Pair<Double, Double>, Pair<Double, Double>>> =
        glyph.contours.flatMap { contour ->
            val points = contour.points.map { it.point.x.toDouble() to it.point.y.toDouble() }
            points.indices.map { points[it] to points[(it + 1) % points.size] }
        }

    /** The ink along the horizontal line at [y], as (start, end) pairs left to right. */
    private fun inkAtY(
        glyph: Glyph,
        y: Double,
    ): List<Pair<Double, Double>> {
        val crossings =
            edges(glyph)
                .filter { (a, b) -> a.second != b.second && y >= minOf(a.second, b.second) && y < maxOf(a.second, b.second) }
                .map { (a, b) -> a.first + (y - a.second) * (b.first - a.first) / (b.second - a.second) }
                .sorted()
        return crossings.chunked(2).map { it[0] to it[1] }
    }

    /** The ink along the vertical line at [x], as (bottom, top) pairs. */
    private fun inkAtX(
        glyph: Glyph,
        x: Double,
    ): List<Pair<Double, Double>> {
        val crossings =
            edges(glyph)
                .filter { (a, b) -> a.first != b.first && x >= minOf(a.first, b.first) && x < maxOf(a.first, b.first) }
                .map { (a, b) -> a.second + (x - a.first) * (b.second - a.second) / (b.first - a.first) }
                .sorted()
        return crossings.chunked(2).map { it[0] to it[1] }
    }

    private fun bounds(glyph: Glyph): List<Double> {
        val points = glyph.contours.flatMap { it.points }.map { it.point }
        return listOf(
            points
                .minOf {
                    it.x
                }.toDouble(),
            points.minOf { it.y }.toDouble(),
            points.maxOf { it.x }.toDouble(),
            points.maxOf { it.y }.toDouble(),
        )
    }

    private fun stemsOf(glyph: Glyph): List<Double> = inkAtY(glyph, 100.0).map { it.second - it.first }

    private fun assertClose(
        expected: Double,
        actual: Double,
        what: String,
        tolerance: Double = 0.5,
    ) = assertTrue(abs(expected - actual) <= tolerance, "$what: the workbook says $expected, the font measures $actual")

    // ---- The claims ----------------------------------------------------------------------------------------------

    @Test
    fun task7QuotesNsSidebearingsAndTheFontAgrees() {
        val (lsb, rsb) = quoted(stats(7, "Hyle Deco 'n'"), """LSB (\d+) -- RSB (\d+)""")
        val n = glyph('n')
        val (xMin, _, xMax, _) = bounds(n)
        assertEquals(lsb, xMin, "n's left sidebearing")
        assertEquals(rsb, n.advanceWidth - xMax, "n's right sidebearing")
        // The same numbers by the other route a person would use: the advance is the ink plus both sidebearings.
        assertEquals(n.advanceWidth.toDouble(), lsb + (xMax - xMin) + rsb)
    }

    @Test
    fun task3QuotesBaselineXHeightStemAndOvershootAndTheFontAgrees() {
        val lines = stats(3, "Hyle Deco metrics")
        val (baseline, xHeight) = quoted(lines, """baseline (\d+) -- x-height (\d+)""")
        val (stem) = quoted(lines, """stem (\d+) units""")
        val (overshoot) = quoted(lines, """o: overshoot (\d+)""")
        val o = glyph('o')
        val n = glyph('n')
        val (_, oBottom, _, oTop) = bounds(o)
        assertEquals(baseline, bounds(n)[1], "n sits on the baseline")
        assertEquals(xHeight, bounds(n)[3], "n's height is the x-height")
        assertEquals(xHeight, checkNotNull(font.os2?.sxHeight).toDouble(), "OS/2 sxHeight agrees with the drawing")
        assertEquals(overshoot, oTop - xHeight, "o's top against the x-height")
        assertEquals(overshoot, baseline - oBottom, "o's bottom against the baseline")
        for (width in stemsOf(n)) assertClose(stem, width, "n's stems")
    }

    @Test
    fun task4QuotesStemAndCounterAndTheFontAgrees() {
        val lines = stats(4, "noHO")
        val (stem, counter) = quoted(lines, """stem (\d+) -- counter (\d+)""")
        val (overshoot) = quoted(lines, """overshoot (\d+)""")
        val o = glyph('o')
        for (y in listOf(150.0, 250.0, 350.0)) {
            val ink = inkAtY(o, y)
            assertEquals(2, ink.size, "o at height $y")
            assertClose(stem, ink[0].second - ink[0].first, "o's left stroke at $y")
            // The sample is drawn by hand, so the counter varies by under a unit along the height of the o: 291.9 near the bottom to 291.2 near the top.
            assertClose(counter, ink[1].first - ink[0].second, "o's counter at $y", tolerance = 1.0)
        }
        assertEquals(overshoot, bounds(o)[3] - checkNotNull(font.os2?.sxHeight).toDouble(), "o has no overshoot")
    }

    @Test
    fun task2QuotesTheOverlayProbeAndTheFontAgrees() {
        val (stem, bar, contrast) =
            quoted(
                stats(2, "overlay probe, Hyle Deco vs. two neighbours"),
                """stem (\d+) -- bar (\d+) -- contrast ([0-9.]+)""",
            )
        for (width in stemsOf(glyph('n'))) assertClose(stem, width, "n's stems")
        // The crossbar of H is one stroke between its stems; measured mid-way across it.
        val h = glyph('H')
        val crossingMiddle = inkAtX(h, (bounds(h)[0] + bounds(h)[2]) / 2)
        assertEquals(1, crossingMiddle.size, "H's crossbar")
        assertClose(bar, crossingMiddle.single().let { it.second - it.first }, "H's crossbar")
        assertEquals(contrast, (assertNotNull(contrastRatio(glyph('o'))) * 100).roundToInt() / 100.0, "o's thick-to-thin ratio")
    }

    @Test
    fun task5QuotesTheStemItCopiesForwardAndTheFontAgrees() {
        val lines = stats(5, "what a derivation copies forward")
        val (stem, hLeft, hRight) = quoted(lines, """stem (\d+) \(n's two stems; H's two measure (\d+) and (\d+)\)""")
        val n = glyph('n')
        for (width in stemsOf(n)) assertClose(stem, width, "n's stems")
        val hStems = stemsOf(glyph('H'))
        assertEquals(listOf(hLeft, hRight), hStems.map { it.roundToInt().toDouble() }, "H's two stems")
        assertClose(stem, hStems.average(), "the mean of H's stems", tolerance = 0.5)
    }

    @Test
    fun everyDemonstrationLabelledWithTheSampleIsEitherCheckedAboveOrSaysWhyItIsNot() {
        val labelled =
            tasks
                .mapNotNull { (it.demonstration as? Demonstration.StatDemonstration)?.let { d -> it.index to d.label } }
                .filter { (_, label) -> "Hyle Deco" in label || label == "noHO" }
                .toSet()
        val covered = CHECKED + NOT_FROM_THE_FONT.keys
        assertEquals(
            labelled,
            labelled.intersect(covered),
            "a demonstration about the sample is neither checked nor explained: ${labelled - covered}",
        )
        assertTrue(NOT_FROM_THE_FONT.values.all { it.isNotBlank() })
    }

    private companion object {
        /** (task, label) of the demonstrations the tests above compare with the font. */
        val CHECKED =
            setOf(
                2 to "overlay probe, Hyle Deco vs. two neighbours",
                3 to "Hyle Deco metrics",
                4 to "noHO",
                5 to "what a derivation copies forward",
                7 to "Hyle Deco 'n'",
            )

        /** Demonstrations that name the sample but quote something a font file does not hold. */
        val NOT_FROM_THE_FONT =
            mapOf(
                (8 to "Hyle Deco kern preview") to
                    "pairs named for a preview and published figures from docs/RESEARCH_font_quality.md; no number is the font's",
                (11 to "Hyle Deco Regular -- Fontbakery googlefonts + Typewright") to
                    "a Fontbakery run's tally, not a property of the file; it could not be re-run here (docs/OPEN_QUESTIONS.md)",
                (12 to "Hyle Deco repo scaffold") to "the repository folder's copyright pattern, text rather than a measurement",
            )
    }
}
