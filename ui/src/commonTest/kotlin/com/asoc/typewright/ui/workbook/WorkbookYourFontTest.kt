// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.workbook

import com.asoc.typewright.campaign.Demonstration
import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.core.font.ufo.UfoProject
import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.ContourPoint
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.project.Master
import com.asoc.typewright.ui.project.ProjectFont
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The figures the Workbook shows for the open font, on fonts built here from rectangles so that every expected
 * number is plain arithmetic on the coordinates below. None of them is one of the lesson's sample figures (stem 44,
 * counter 292, overshoot 0): a figure that came from the lesson instead of the font would not match.
 */
class WorkbookYourFontTest {
    private fun polygon(vararg corners: Pair<Int, Int>): Contour =
        Contour(corners.map { ContourPoint(Point(it.first, it.second), onCurve = true) }, CurveFormat.QUADRATIC)

    private fun rectangle(
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
    ): Contour = polygon(x0 to y0, x1 to y0, x1 to y1, x0 to y1)

    private fun glyph(
        char: Char,
        advance: Int,
        vararg contours: Contour,
    ) = Glyph(char.toString(), advance, contours.toList(), unicodes = listOf(char.code))

    /** n: two stems of 52 (x 60 to 112 and 240 to 292), 520 high; advance 352, so sidebearings 60 and 60. */
    private val n = glyph('n', 352, rectangle(60, 0, 112, 520), rectangle(240, 0, 292, 520))

    /** o: a ring from x 40 to 460 and y -12 to 532 with a counter from x 104 to 388: a left stroke of 64, a right one of 72, a counter of 284. */
    private val o =
        glyph(
            'o',
            500,
            rectangle(40, -12, 460, 532),
            rectangle(40 + 64, 50, 460 - 72, 470),
        )

    /** H: stems of 58 (60 to 118 and 262 to 320), a crossbar 40 high (y 330 to 370) between them, 700 high. */
    private val h =
        glyph(
            'H',
            380,
            polygon(
                60 to 0,
                118 to 0,
                118 to 330,
                262 to 330,
                262 to 0,
                320 to 0,
                320 to 700,
                262 to 700,
                262 to 370,
                118 to 370,
                118 to 700,
                60 to 700,
            ),
        )

    /** b: a single stem of 47. */
    private val b = glyph('b', 300, rectangle(50, 0, 97, 700))

    private fun fontOf(
        glyphs: List<Glyph>,
        xHeight: Int? = 520,
    ): ProjectFont =
        ProjectFont(
            Master(
                "regular",
                "Regular.ufo",
                "Regular",
                isDefault = true,
                ufo = UfoProject(UfoFontInfo(familyName = "Fixture", unitsPerEm = 1000, xHeight = xHeight), glyphs),
            ),
        )

    private val sampleFigures = listOf("44", "292", "43", "46", "51", "500")

    @Test
    fun theFiguresAreReadOffTheOpenFontsOwnLetters() {
        val reading = measureYourFont(fontOf(listOf(n, o, h, b)))
        assertEquals(listOf(52.0, 52.0), reading.nStems)
        assertEquals(listOf(58.0, 58.0), reading.hStems)
        assertEquals(64.0, assertNotNull(reading.oStroke), 1e-9)
        // The counter runs from x 104 to 388.
        assertEquals(284.0, assertNotNull(reading.oCounter), 1e-9)
        assertEquals(12.0, assertNotNull(reading.oOvershootTop), 1e-9)
        assertEquals(12.0, assertNotNull(reading.oOvershootBottom), 1e-9)
        assertEquals(520, reading.xHeight)
        assertEquals(60.0 to 60.0, reading.nSidebearings)
        assertEquals(40.0 to 40.0, reading.oSidebearings)
        assertEquals(47.0, assertNotNull(reading.probe.stemUnits), 1e-9)
        assertEquals(40.0, assertNotNull(reading.probe.barUnits), 1e-9)
        assertEquals(setOf('n', 'o', 'H', 'b'), reading.has)
    }

    @Test
    fun task4SetsNoHOInTheirLettersAndStatesTheirStemCounterAndOvershoot() {
        val demo = assertNotNull(yourFontDemonstration(4, measureYourFont(fontOf(listOf(n, o, h, b)))))
        assertEquals("noHO", demo.heroText)
        assertEquals(
            listOf(
                "stem 64 -- counter 284 (o, through its middle)",
                "overshoot +12 at the top, +12 at the bottom",
            ),
            demo.lines,
        )
    }

    @Test
    fun task3StatesTheirXHeightStemAndOvershoot() {
        val demo = assertNotNull(yourFontDemonstration(3, measureYourFont(fontOf(listOf(n, o, h, b)))))
        assertEquals(
            listOf(
                "baseline 0 -- x-height 520 (your font's own figure)",
                "stem 52 units (n)",
                "o: overshoot +12 at the top, +12 at the bottom",
            ),
            demo.lines,
        )
    }

    @Test
    fun task5StatesTheStemsOfNAndH() {
        val demo = assertNotNull(yourFontDemonstration(5, measureYourFont(fontOf(listOf(n, o, h, b)))))
        assertEquals(listOf("n's stems: 52 and 52; H's stems: 58 and 58"), demo.lines)
    }

    @Test
    fun task2UsesTheOverlayTabsOwnProbe() {
        val demo = assertNotNull(yourFontDemonstration(2, measureYourFont(fontOf(listOf(n, o, h, b)))))
        val line = demo.lines.single()
        assertTrue(line.startsWith("stem 47 (b) -- bar 40 (H) -- contrast "), line)
        assertTrue(line.endsWith(" (o)"), line)
    }

    @Test
    fun task7StatesTheSidebearingsOfNAndOAndTheirRatios() {
        val demo = assertNotNull(yourFontDemonstration(7, measureYourFont(fontOf(listOf(n, o, h, b)))))
        assertEquals("n: LSB 60 -- RSB 60", demo.lines[0])
        assertEquals("o: LSB 40 -- RSB 40", demo.lines[1])
        // o's sidebearings 40 against a counter of 284; n's 60 against o's 40.
        assertEquals("o's sidebearings are 0.14 of its counter width; n's are 1.5 times o's", demo.lines[2])
    }

    @Test
    fun noFigureOfTheLessonsSampleAppearsInTheOpenFontsFigures() {
        val reading = measureYourFont(fontOf(listOf(n, o, h, b)))
        for (task in listOf(2, 3, 4, 5, 7)) {
            val text = assertNotNull(yourFontDemonstration(task, reading)).lines.joinToString("\n")
            // The numbers in these lines are all this font's. Match whole numbers only, so 52 is not read as containing a 5.
            val numbers = Regex("""[0-9]+(?:\.[0-9]+)?""").findAll(text).map { it.value }.toSet()
            for (sample in sampleFigures) assertFalse(sample in numbers, "task $task: the lesson's $sample appears in $text")
        }
    }

    @Test
    fun aFlatTopAndBottomIsSaidSo() {
        val flat = glyph('o', 500, rectangle(40, 0, 460, 520), rectangle(100, 60, 400, 460))
        val reading = measureYourFont(fontOf(listOf(n, flat, h, b)))
        val demo = assertNotNull(yourFontDemonstration(4, reading))
        assertEquals("overshoot 0, flat top and bottom", demo.lines[1])
    }

    @Test
    fun anOThatFallsShortIsSignedNotHidden() {
        val short = glyph('o', 500, rectangle(40, 4, 460, 515), rectangle(100, 60, 400, 460))
        val demo = assertNotNull(yourFontDemonstration(4, measureYourFont(fontOf(listOf(n, short, h, b)))))
        assertEquals("overshoot −5 at the top, −4 at the bottom", demo.lines[1])
    }

    @Test
    fun aFontThatStatesNoXHeightDoesNotGetAnInventedOne() {
        val reading = measureYourFont(fontOf(listOf(n, o, h, b), xHeight = null))
        assertNull(reading.xHeight)
        assertNull(reading.oOvershootTop)
        val lines = assertNotNull(yourFontDemonstration(3, reading)).lines
        assertEquals("baseline 0 -- x-height not stated in your font's info", lines[0])
        assertTrue("the top is not measured because your font states no x-height" in lines[2], lines[2])
        assertTrue("+12 below the baseline" in lines[2], lines[2])
    }

    @Test
    fun aLetterTheFontLacksIsNamedNotGuessed() {
        val reading = measureYourFont(fontOf(listOf(o, b)))
        assertEquals(setOf('o', 'b'), reading.has)
        assertTrue(reading.nStems.isEmpty())
        assertEquals(
            "n's stems: no n in your font; H's stems: no H in your font",
            assertNotNull(yourFontDemonstration(5, reading)).lines.single(),
        )
        assertEquals("not measured: your font has no n", assertNotNull(yourFontDemonstration(7, reading)).lines[0])
        assertEquals("not measured: your font has no n", assertNotNull(yourFontDemonstration(3, reading)).lines[1])
        val noO = measureYourFont(fontOf(listOf(n, h, b)))
        assertEquals("not measured: your font has no o", assertNotNull(yourFontDemonstration(4, noO)).lines[0])
    }

    @Test
    fun aFontWithNoLettersAtAllMeasuresNothingAndSaysSo() {
        val reading = measureYourFont(fontOf(emptyList()))
        assertTrue(reading.has.isEmpty())
        assertNull(reading.oStroke)
        assertNull(reading.probe.stemUnits)
        assertTrue("not measured" in assertNotNull(yourFontDemonstration(2, reading)).lines.single())
    }

    @Test
    fun onlyTheTasksThatQuoteTheSamplesFiguresGetAMeasurementOfTheirOwn() {
        val reading = measureYourFont(fontOf(listOf(n, o, h, b)))
        for (task in listOf(2, 3, 4, 5, 7)) assertNotNull(yourFontDemonstration(task, reading), "task $task")
        for (task in listOf(1, 6, 8, 9, 10, 11, 12)) assertNull(yourFontDemonstration(task, reading), "task $task")
    }

    @Test
    fun theLessonsOwnFiguresAreHeadedAsNotTheirs() {
        val sample = Demonstration.StatDemonstration("noHO", listOf("stem 44 -- counter 292"))
        assertEquals("Demonstration · the lesson's sample font, not your font", lessonSampleHeading(sample, quotesTheSampleFont = true))
        val published =
            Demonstration.StatDemonstration(
                "on-curve range across 13 open fonts (docs/RESEARCH_font_quality.md)",
                listOf("S: 12-28"),
            )
        assertEquals("Demonstration · from the lesson, not your font", lessonSampleHeading(published, quotesTheSampleFont = false))
        assertEquals(
            "Demonstration · lesson scene",
            lessonSampleHeading(Demonstration.SceneDemonstration("craft.c1-baked-composites"), quotesTheSampleFont = false),
        )
    }

    @Test
    fun theViewKeepsTheLessonsFiguresAsTheyAreAndAddsTheOpenFontsBesideThem() {
        val sample = Demonstration.StatDemonstration("noHO", listOf("stem 44 -- counter 292", "overshoot 0 (flat tops)"))
        val view = demonstrationView(4, sample, fontOf(listOf(n, o, h, b)))
        assertEquals(sample, view.lesson, "the lesson's own text is passed through untouched")
        assertEquals("stem 64 -- counter 284 (o, through its middle)", assertNotNull(view.yourFont).lines.first())
        val scene = demonstrationView(1, Demonstration.SceneDemonstration("lineages.transitional"), fontOf(listOf(n, o, h, b)))
        assertNull(scene.yourFont, "a lesson scene has no figures of the person's to show")
    }

    @Test
    fun unitsAreWholeNumbersWhenTheyAreAndOneDecimalWhenNot() {
        assertEquals("44", units(44.0))
        assertEquals("44", units(44.04))
        assertEquals("291.5", units(291.52))
        assertEquals("88.4", units(88.43))
        assertEquals("not measured", units(null))
        assertEquals("1.25", ratio(1.2512))
        assertEquals("not measured", ratio(null))
    }
}
