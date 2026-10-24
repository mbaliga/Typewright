// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.report

import com.asoc.typewright.core.geometry.Anchor
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.polygonContour
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OutlineChecksTest {
    private val fx = ReportFixtures

    private fun item(
        id: String,
        report: CheckReport,
    ): CheckItem = assertNotNull(report.item(id), id)

    // ---- node economy ----------------------------------------------------------------------------

    /** A T drawn as [points] on-curve points: a polygon of that many corners. */
    private fun tWith(points: Int) =
        fx.glyph("T", fx.tracedRectangle(points), unicodes = listOf(0x54), anchors = listOf(Anchor("top", Point(10, 10))))

    @Test
    fun theSentenceQuotesTheLettersOwnCountAndTheMeasuredBox() {
        val report = fx.report(glyphs = listOf(tWith(1764)))
        val economy = item(ReviewOrder.NODE_ECONOMY, report)
        assertEquals(Severity.FAIL, economy.severity)
        assertEquals(
            "Your T has 1,764 points. In 4 geometric sans faces a T has 8 to 12.5, at most 28. " +
                "That is more than 10 times the most any of those faces has: a point at every step of a pixel " +
                "is the footprint a tracer leaves (our heuristic).",
            economy.summary,
        )
        // The 10 times is ours and is said so wherever it is used, not only in the code: the basis note names it too.
        assertTrue("more than 10 times the box's largest" in economy.basisNote && "our heuristic" in economy.basisNote, economy.basisNote)
        assertEquals("1 of 1 letters are outliers", economy.detail)
        assertEquals("See every glyph against its box", economy.action?.label)
        assertEquals(CheckRoute.OpenEconomy, economy.action?.route)
        assertEquals("lots of precision, many nodes", economy.reviewerQuote)
    }

    @Test
    fun theMiniPlotCarriesTheWorstLettersCountAndItsBox() {
        val economy = item(ReviewOrder.NODE_ECONOMY, fx.report(glyphs = listOf(tWith(1764))))
        val plot = assertNotNull(economy.plot)
        assertEquals("T", plot.glyph)
        assertEquals(1764, plot.count)
        assertEquals(12.5, plot.box.q3)
        // A project with nothing past the fence has no outlier to draw.
        val clean = item(ReviewOrder.NODE_ECONOMY, fx.report(glyphs = lettersWithOutliers(listOf("T", "o", "H", "e"), outliers = 0)))
        assertNull(clean.plot)
    }

    @Test
    fun aClassTheCorpusHasNoBoxForSaysSoInsteadOfClaimingNoClassIsSet() {
        val formal = CheckStyle("script-formal", "formal script", heuristic = false)
        val economy = item(ReviewOrder.NODE_ECONOMY, fx.report(glyphs = listOf(tWith(1764)), style = formal))
        assertEquals(Severity.NOT_MEASURED, economy.severity)
        assertTrue("formal script, has no measured faces" in economy.summary, economy.summary)
        assertTrue("No style class is set" !in economy.summary, economy.summary)
    }

    @Test
    fun aClassWithNoBoxFallsBackToTheRoundLettersAndSaysItDoes() {
        val formal = CheckStyle("script-formal", "formal script", heuristic = false)
        val polygons = item(ReviewOrder.CURVES_ARE_POLYGONS, fx.report(glyphs = listOf(fx.cleanT, polygonO()), style = formal))
        assertEquals(Severity.FAIL, polygons.severity, "an all-polygon o is judged by the round-letters list, not skipped")
        assertEquals(Basis.HEURISTIC, polygons.basis)
        assertTrue("formal script class has no measured faces" in polygons.basisNote, polygons.basisNote)
        assertTrue("our heuristic" in polygons.basisNote, polygons.basisNote)
    }

    @Test
    fun aCheckThatCouldNotJudgeCarriesNoReviewerQuoteAndNoAction() {
        // No class, and only a digit: node economy, curves and anchors each have nothing to judge.
        val report = fx.report(glyphs = listOf(bare("one", 0x31)), style = null)
        val notMeasured = report.items.filter { it.severity == Severity.NOT_MEASURED }
        assertTrue(
            notMeasured.map { it.id }.containsAll(listOf(ReviewOrder.NODE_ECONOMY, ReviewOrder.CURVES_ARE_POLYGONS, ReviewOrder.ANCHORS)),
            notMeasured.map { it.id }.toString(),
        )
        for (item in notMeasured) {
            assertNull(item.reviewerQuote, "${item.id} did not judge, so no reviewer's words describe it")
            assertNull(item.action, "${item.id} did not judge, so there is no fix to offer")
        }
    }

    @Test
    fun anItemThatCouldNotJudgeRefusesToCarryAQuoteOrAnAction() {
        fun notMeasured(
            quote: String? = null,
            action: CheckAction? = null,
        ) = CheckItem(
            "x/y",
            CheckGroup.OUTLINES,
            "x",
            null,
            Severity.NOT_MEASURED,
            "s",
            quote,
            action,
            Basis.HEURISTIC,
            "n",
            "r",
        )
        notMeasured()
        assertFailsWith<IllegalArgumentException> { notMeasured(quote = "lots of precision, many nodes") }
        assertFailsWith<IllegalArgumentException> { notMeasured(action = CheckAction("Open", CheckRoute.OpenEconomy)) }
    }

    @Test
    fun theTracerClauseIsLeftOutWhenTheCountIsOnlyJustOverTheFence() {
        // The fence for this box is 12.5 + max(1.5 * 4.5, 0.25 * 12.5) = 19.25, so 20 points is an outlier but not a tracer's work.
        val report = fx.report(glyphs = listOf(tWith(20)))
        val economy = item(ReviewOrder.NODE_ECONOMY, report)
        assertTrue(economy.summary.startsWith("Your T has 20 points."), economy.summary)
        assertTrue("tracer" !in economy.summary, economy.summary)
    }

    /** [names] drawn so that the first [outliers] of them are traced polygons far past the fence and the rest are plain (8 points). */
    private fun lettersWithOutliers(
        names: List<String>,
        outliers: Int,
    ) = names.mapIndexed { i, name ->
        fx.glyph(
            name,
            fx.tracedRectangle(
                if (i <
                    outliers
                ) {
                    400
                } else {
                    8
                },
            ),
            unicodes = listOf(0x41 + i),
            anchors = listOf(Anchor("top", Point(10, 10))),
        )
    }

    @Test
    fun aQuarterOfTheJudgedLettersBeyondTheFenceIsAFail() {
        // Four letters the fixture box covers; one outlier is 25%, which is the line.
        val four = lettersWithOutliers(listOf("T", "o", "H", "e"), outliers = 1)
        val economy = item(ReviewOrder.NODE_ECONOMY, fx.report(glyphs = four))
        assertEquals("1 of 4 letters are outliers", economy.detail)
        assertEquals(Severity.FAIL, economy.severity)
    }

    @Test
    fun underAQuarterIsOnlyAWarning() {
        // The same single outlier among five judged letters is 20%.
        val five = lettersWithOutliers(listOf("T", "o", "H", "e", "n"), outliers = 1)
        val economy = item(ReviewOrder.NODE_ECONOMY, fx.report(glyphs = five))
        assertEquals("1 of 5 letters are outliers", economy.detail)
        assertEquals(Severity.WARN, economy.severity)
    }

    @Test
    fun noOutlierIsAPassAndSaysHowTheLettersSit() {
        val economy = item(ReviewOrder.NODE_ECONOMY, fx.report(glyphs = lettersWithOutliers(listOf("T", "o", "H", "e"), outliers = 0)))
        assertEquals(Severity.PASS, economy.severity)
        assertEquals("no outliers among 4 letters", economy.detail)
        assertTrue(economy.summary.startsWith("All 4 letters are inside the fence of 4 geometric sans faces"), economy.summary)
    }

    @Test
    fun theWorstLetterIsTheOneFurthestAboveItsBoxOnALogScale() {
        // T: 400 over a q3 of 12.5 is 32 times; e: 400 over 30 is 13 times. T is named.
        val letters = lettersWithOutliers(listOf("e", "T", "o", "H"), outliers = 2)
        val economy = item(ReviewOrder.NODE_ECONOMY, fx.report(glyphs = letters))
        assertTrue(economy.summary.startsWith("Your T has 400 points."), economy.summary)
    }

    @Test
    fun withNoClassTheItemSaysItHadNothingToJudgeByAndPointsToTheBrief() {
        val economy = item(ReviewOrder.NODE_ECONOMY, fx.report(style = null))
        assertEquals(Severity.NOT_MEASURED, economy.severity)
        assertTrue("Declare one in the Brief" in economy.summary, economy.summary)
        assertNull(economy.action)
    }

    @Test
    fun aClassReadByTheHeuristicIsSaidToBe() {
        val report = fx.report(glyphs = listOf(tWith(1764)), style = fx.style.copy(heuristic = true))
        assertTrue("read from your letters by our heuristic" in item(ReviewOrder.NODE_ECONOMY, report).summary)
    }

    // ---- curves are polygons -----------------------------------------------------------------------

    private fun curvedO() = fx.cleanO

    private fun polygonO() = fx.glyph("o", fx.polygon(0 to 0, 0 to 500, 400 to 500, 400 to 0), unicodes = listOf(0x6F))

    @Test
    fun aFontWithNoOffCurvePointsAnywhereIsAFailThatSaysSo() {
        val report = fx.report(glyphs = listOf(fx.cleanT, polygonO()))
        val polygons = item(ReviewOrder.CURVES_ARE_POLYGONS, report)
        assertEquals(Severity.FAIL, polygons.severity)
        assertEquals("0 off-curve points in 2 glyphs", polygons.detail)
        assertTrue(polygons.summary.startsWith("Every curve is a many-sided polygon pretending."), polygons.summary)
        assertTrue(polygons.summary.endsWith("this font has none."), polygons.summary)
        assertEquals(CheckRoute.OpenTrace(listOf("o")), polygons.action?.route)
        assertEquals("Refit o", polygons.action?.label)
    }

    @Test
    fun aCurvedLetterPasses() {
        val polygons = item(ReviewOrder.CURVES_ARE_POLYGONS, fx.report(glyphs = listOf(fx.cleanT, curvedO())))
        assertEquals(Severity.PASS, polygons.severity)
        assertNull(polygons.action)
        assertEquals("4 off-curve points in 2 glyphs", polygons.detail)
    }

    @Test
    fun aCubicOutlineOfStraightSidesIsStillAPolygonEvenThoughItsControlsAreWrittenOut() {
        val cubicPolygon = polygonContour(listOf(Point(0, 0), Point(0, 500), Point(400, 500), Point(400, 0)), CurveFormat.CUBIC)
        val o = fx.glyph("o", cubicPolygon, unicodes = listOf(0x6F))
        assertEquals(0, realOffCurvePoints(o))
        assertEquals(Severity.FAIL, item(ReviewOrder.CURVES_ARE_POLYGONS, fx.report(glyphs = listOf(fx.cleanT, o))).severity)
    }

    @Test
    fun theLettersThatShouldBeCurvedComeFromTheMeasuredBoxNotFromAList() {
        // H has no curves in at least a quarter of the fixture faces, so a polygon H is correct and is not named.
        val report =
            fx.report(
                glyphs =
                    listOf(
                        fx.cleanT,
                        fx.cleanO,
                        fx.glyph("H", fx.polygon(0 to 0, 0 to 700, 100 to 700, 100 to 0), unicodes = listOf(0x48)),
                    ),
            )
        assertEquals(Severity.PASS, item(ReviewOrder.CURVES_ARE_POLYGONS, report).severity)
    }

    @Test
    fun withNoMeasuredBoxTheRoundLettersListIsUsedAndSaidToBeOurHeuristic() {
        val polygons = item(ReviewOrder.CURVES_ARE_POLYGONS, fx.report(glyphs = listOf(polygonO()), corpus = null, style = null))
        assertEquals(Severity.FAIL, polygons.severity)
        assertEquals(Basis.HEURISTIC, polygons.basis)
        assertTrue("o c e s O C G S Q: our heuristic" in polygons.basisNote, polygons.basisNote)
    }

    @Test
    fun noCurvedLetterPresentMeansNothingToJudge() {
        val item = item(ReviewOrder.CURVES_ARE_POLYGONS, fx.report(glyphs = listOf(fx.cleanT)))
        assertEquals(Severity.NOT_MEASURED, item.severity)
    }

    // ---- overshoot ----------------------------------------------------------------------------------

    @Test
    fun aRoundShapeOnTheLineIsAWarningAndAFlatOneIsExempt() {
        val e =
            fx.glyph(
                "e",
                fx.quadratic(
                    fx.on(250, 0),
                    fx.off(500, 0),
                    fx.on(500, 250),
                    fx.off(500, 500),
                    fx.on(250, 500),
                    fx.off(0, 500),
                    fx.on(0, 250),
                    fx.off(0, 0),
                ),
                fx.quadratic(
                    fx.on(250, 100),
                    fx.off(300, 100),
                    fx.on(300, 250),
                    fx.off(300, 400),
                    fx.on(250, 400),
                    fx.off(200, 400),
                    fx.on(200, 250),
                    fx.off(200, 100),
                ),
                unicodes = listOf(0x65),
            )
        // An o with a flat top and a flat bottom: two points share the extreme height.
        val flatO =
            fx.glyph(
                "o",
                fx.quadratic(
                    fx.on(100, 0),
                    fx.on(300, 0),
                    fx.off(480, 0),
                    fx.on(480, 250),
                    fx.off(480, 500),
                    fx.on(300, 500),
                    fx.on(100, 500),
                    fx.off(20, 500),
                    fx.on(20, 250),
                    fx.off(20, 0),
                ),
                // The counter makes it two contours, which is what the roundness heuristic reads as a bowl.
                fx.quadratic(
                    fx.on(250, 100),
                    fx.off(300, 100),
                    fx.on(300, 250),
                    fx.off(300, 400),
                    fx.on(250, 400),
                    fx.off(200, 400),
                    fx.on(200, 250),
                    fx.off(200, 100),
                ),
                unicodes = listOf(0x6F),
            )
        val overshoot = item(ReviewOrder.OVERSHOOT, fx.report(glyphs = listOf(e, flatO)))
        assertEquals(Severity.WARN, overshoot.severity)
        assertEquals("e", overshoot.detail)
        assertEquals(
            "Round shapes sit exactly on 0 and 500, so they read smaller than flat letters. " +
                "The o is exempt: flat top and bottom, overshoot does not apply.",
            overshoot.summary,
        )
        assertEquals(CheckRoute.OpenDraw("e"), overshoot.action?.route)
        assertEquals(Basis.HEURISTIC, overshoot.basis)
    }

    @Test
    fun overshootThatIsThereIsAPass() {
        assertEquals(Severity.PASS, item(ReviewOrder.OVERSHOOT, fx.report()).severity)
    }

    // ---- alignment ------------------------------------------------------------------------------------

    @Test
    fun aPointOneUnitOffTheBaselineIsFoundAndNamed() {
        val t =
            fx.glyph(
                "T",
                fx.polygon(200 to 1, 200 to 620, 20 to 620, 20 to 700, 480 to 700, 480 to 620, 300 to 620, 300 to 0),
                unicodes = listOf(0x54),
            )
        val miss = item(ReviewOrder.ALIGNMENT_MISS, fx.report(glyphs = listOf(t, fx.cleanO)))
        assertEquals(Severity.WARN, miss.severity)
        assertEquals("1 place in 1 glyph", miss.detail)
        assertTrue("The T has a point at y = 1, 1 unit off the baseline." in miss.summary, miss.summary)
        assertEquals(CheckRoute.OpenDraw("T"), miss.action?.route)
        assertEquals(Basis.PUBLISHED, miss.basis)
    }

    @Test
    fun pointsExactlyOnTheLinesAreFine() {
        assertEquals(Severity.PASS, item(ReviewOrder.ALIGNMENT_MISS, fx.report()).severity)
    }

    // ---- the four small outline heuristics ---------------------------------------------------------------

    private fun only(
        contour: com.asoc.typewright.core.geometry.Contour,
        id: String,
    ): CheckItem = item(id, fx.report(glyphs = listOf(fx.glyph("x", contour, unicodes = listOf(0x78)))))

    @Test
    fun threePointsNearlyInALineAreCollinear() {
        val found = only(fx.polygon(0 to 0, 100 to 0, 200 to 1, 200 to 300, 0 to 300), ReviewOrder.COLLINEAR)
        assertEquals(Severity.WARN, found.severity)
        assertEquals("1 place in 1 glyph", found.detail)
    }

    @Test
    fun aSmallKinkIsJaggyButNotCollinear() {
        val shape = fx.polygon(0 to 0, 100 to 0, 200 to 20, 200 to 300, 0 to 300)
        assertEquals(Severity.WARN, only(shape, ReviewOrder.JAGGY).severity)
        assertEquals(Severity.PASS, only(shape, ReviewOrder.COLLINEAR).severity)
    }

    @Test
    fun aTwoUnitSegmentIsShort() {
        val found = only(fx.polygon(0 to 0, 300 to 0, 300 to 2, 301 to 300, 0 to 300), ReviewOrder.SHORT_SEGMENTS)
        assertEquals(Severity.WARN, found.severity)
    }

    @Test
    fun aSegmentHalfADegreeOffHorizontalIsSemiVertical() {
        val found = only(fx.polygon(0 to 0, 300 to 2, 300 to 300, 0 to 300), ReviewOrder.SEMI_VERTICAL)
        assertEquals(Severity.WARN, found.severity)
        assertEquals(Severity.PASS, only(fx.polygon(0 to 0, 300 to 0, 300 to 300, 0 to 300), ReviewOrder.SEMI_VERTICAL).severity)
    }

    @Test
    fun aSquareHasNoneOfTheFour() {
        val square = fx.polygon(0 to 0, 300 to 0, 300 to 300, 0 to 300)
        for (id in listOf(ReviewOrder.COLLINEAR, ReviewOrder.JAGGY, ReviewOrder.SHORT_SEGMENTS, ReviewOrder.SEMI_VERTICAL)) {
            assertEquals(Severity.PASS, only(square, id).severity, id)
        }
    }

    @Test
    fun aTracedFontIsStillListedWhereFontbakeryItselfWouldStaySilent() {
        val traced = fx.tracedRectangle(400)
        val found = only(traced, ReviewOrder.COLLINEAR)
        assertEquals(Severity.WARN, found.severity)
        assertTrue("more than 100 places" in found.summary && "Typewright lists them anyway" in found.summary, found.summary)
    }

    @Test
    fun aFewFindingsDoNotCarryTheFontbakeryNote() {
        val found = only(fx.polygon(0 to 0, 100 to 0, 200 to 1, 200 to 300, 0 to 300), ReviewOrder.COLLINEAR)
        assertTrue("more than 100" !in found.summary, found.summary)
    }

    // ---- extrema --------------------------------------------------------------------------------------

    @Test
    fun aCurveWhoseTopIsBetweenTwoPointsIsFlagged() {
        // One quadratic from (0,0) over a control at (150,400) to (300,0): its top, y = 200, is on no point.
        val arch = fx.quadratic(fx.on(0, 0), fx.off(150, 400), fx.on(300, 0))
        val found = only(arch, ReviewOrder.EXTREMA)
        assertEquals(Severity.WARN, found.severity)
        assertTrue(found.summary.startsWith("1 curve in 1 glyph reach"), found.summary)
    }

    @Test
    fun curvesWhoseExtremesAreOnPointsPass() {
        assertEquals(Severity.PASS, item(ReviewOrder.EXTREMA, fx.report()).severity)
    }

    // ---- anchors ----------------------------------------------------------------------------------------

    private fun bare(
        name: String,
        code: Int,
    ) = fx.glyph(name, fx.polygon(0 to 0, 0 to 500, 400 to 500, 400 to 0), unicodes = listOf(code))

    @Test
    fun accentedLettersWithNoAnchorAnywhereIsAFailWithTheReviewersWords() {
        val anchors = item(ReviewOrder.ANCHORS, fx.report(glyphs = listOf(bare("A", 0x41), bare("e", 0x65), bare("Aacute", 0xC1))))
        assertEquals(Severity.FAIL, anchors.severity)
        assertEquals("the diacritics may need some work.", anchors.reviewerQuote)
        assertTrue(
            anchors.summary.startsWith("1 accented letter is in this font and none of the 2 base letters carries an anchor."),
            anchors.summary,
        )
        assertEquals("2 of 2 bases", anchors.detail)
    }

    @Test
    fun withNoAccentsYetMissingAnchorsAreOnlyAWarning() {
        val anchors = item(ReviewOrder.ANCHORS, fx.report(glyphs = listOf(bare("A", 0x41), bare("e", 0x65))))
        assertEquals(Severity.WARN, anchors.severity)
        assertTrue("no accented letters yet" in anchors.summary, anchors.summary)
    }

    @Test
    fun someBasesAnchoredAndSomeNotIsAWarningEvenWithAccents() {
        val withAnchor = fx.cleanT
        val anchors = item(ReviewOrder.ANCHORS, fx.report(glyphs = listOf(withAnchor, bare("e", 0x65), bare("Aacute", 0xC1))))
        assertEquals(Severity.WARN, anchors.severity)
        assertEquals("1 of 2 bases", anchors.detail)
    }

    @Test
    fun noBaseLetterAtAllMeansNothingToCheck() {
        val anchors = item(ReviewOrder.ANCHORS, fx.report(glyphs = listOf(bare("one", 0x31))))
        assertEquals(Severity.NOT_MEASURED, anchors.severity)
    }

    @Test
    fun theAccentedRangeCoversLatin1AndExtendedLettersButNotTheMathSigns() {
        assertTrue(isAccentedLetter(0xC1) && isAccentedLetter(0xE9) && isAccentedLetter(0x105) && isAccentedLetter(0x24F))
        assertTrue(!isAccentedLetter(0xD7) && !isAccentedLetter(0xF7) && !isAccentedLetter(0x41) && !isAccentedLetter(0x250))
    }
}
