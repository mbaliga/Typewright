// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.report

import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.polygonContour
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecordChecksTest {
    private val fx = ReportFixtures
    private val clean = fx.cleanInfo

    private fun item(
        id: String,
        info: UfoFontInfo = clean,
        provenance: ImportProvenance? = null,
        glyphs: List<com.asoc.typewright.core.geometry.Glyph> = fx.cleanGlyphs,
        otherMasters: List<MasterNames> = emptyList(),
    ): CheckItem = assertNotNull(fx.report(glyphs = glyphs, info = info, provenance = provenance, otherMasters = otherMasters).item(id), id)

    // ---- vertical metrics ----------------------------------------------------------------------------

    private fun metrics(info: UfoFontInfo) = item(ReviewOrder.VERTICAL_METRICS, info)

    private fun withMetrics(
        ascender: Int,
        descender: Int,
        gap: Int = 0,
    ) = clean.copy(
        ascender = ascender,
        descender = descender,
        openTypeHheaAscender = ascender,
        openTypeHheaDescender = descender,
        openTypeHheaLineGap = gap,
        openTypeOS2TypoAscender = ascender,
        openTypeOS2TypoDescender = descender,
        openTypeOS2TypoLineGap = gap,
        openTypeOS2WinAscent = ascender,
        openTypeOS2WinDescent = -descender,
    )

    @Test
    fun metricsThatFollowTheGoogleFontsRulesPassAndSayHowTall() {
        val ok = metrics(clean)
        assertEquals(Severity.PASS, ok.severity)
        assertEquals("127% of the em", ok.detail)
        assertTrue("1,270 units, 127% of the 1,000-unit em" in ok.summary, ok.summary)
    }

    @Test
    fun aLineHeightUnderOrOverTheRangeIsAFail() {
        assertEquals(Severity.FAIL, metrics(withMetrics(800, -200)).severity, "100% is under 120%")
        assertEquals(Severity.FAIL, metrics(withMetrics(1300, -800)).severity, "210% is over 200%")
        assertEquals(Severity.PASS, metrics(withMetrics(950, -250)).severity, "exactly 120% is inside the range")
    }

    @Test
    fun aLineHeightAboveOneAndAHalfIsOnlyAWarning() {
        val loose = metrics(withMetrics(1200, -400))
        assertEquals(Severity.WARN, loose.severity)
        assertTrue("160%" in loose.summary, loose.summary)
    }

    @Test
    fun typoMetricsThatDifferFromHheaAreAFail() {
        val differs = metrics(clean.copy(openTypeOS2TypoAscender = 900))
        assertEquals(Severity.FAIL, differs.severity)
        assertTrue("typo" in differs.summary && "differ" in differs.summary, differs.summary)
    }

    @Test
    fun aLineGapIsAWarning() {
        val gap = metrics(clean.copy(openTypeHheaLineGap = 90, openTypeOS2TypoLineGap = 90))
        assertEquals(Severity.WARN, gap.severity)
        assertTrue("line gap is not 0" in gap.summary, gap.summary)
    }

    @Test
    fun useTypoMetricsUnsetIsAWarning() {
        val unset = metrics(clean.copy(other = emptyList()))
        assertEquals(Severity.WARN, unset.severity)
        assertTrue("bit 7" in unset.summary, unset.summary)
    }

    @Test
    fun windowsMetricsThatCutOffTheInkAreAFail() {
        val cut = metrics(clean.copy(openTypeOS2WinAscent = 600))
        assertEquals(Severity.FAIL, cut.severity)
        assertTrue("cut off ink that reaches 700 and -10" in cut.summary, cut.summary)
    }

    @Test
    fun noUnitsPerEmOrAnAbsurdOneIsAFail() {
        assertEquals(Severity.FAIL, metrics(clean.copy(unitsPerEm = null)).severity)
        assertEquals(Severity.FAIL, metrics(clean.copy(unitsPerEm = 8)).severity)
        assertEquals(Severity.FAIL, metrics(clean.copy(unitsPerEm = 20_000)).severity)
    }

    @Test
    fun metricsTheFontNeverStatedAreAWarningNotAGuess() {
        val unset =
            metrics(
                clean.copy(
                    ascender = null,
                    descender = null,
                    openTypeHheaAscender = null,
                    openTypeHheaDescender = null,
                    openTypeOS2TypoAscender = null,
                    openTypeOS2TypoDescender = null,
                ),
            )
        assertEquals(Severity.WARN, unset.severity)
        assertTrue("will choose the line spacing" in unset.summary, unset.summary)
    }

    @Test
    fun theWorstProblemSetsTheSeverityAndOnlyItsOwnSentencesAreShown() {
        // 100% is a fail; the line gap is only a warning and must not be mixed into the fail's sentence.
        val both = metrics(withMetrics(800, -200, gap = 50))
        assertEquals(Severity.FAIL, both.severity)
        assertTrue("line gap is not 0" !in both.summary, both.summary)
    }

    @Test
    fun theMetricsItemSaysWhichRulesArePublishedAndWhichSeverityIsOurs() {
        val ok = metrics(clean)
        assertEquals(Basis.PUBLISHED, ok.basis)
        assertTrue("Which of them stops a release is our heuristic" in ok.basisNote, ok.basisNote)
        assertEquals(CheckRoute.OpenFontInfo(FontInfoTarget.METRICS), ok.action?.route)
    }

    // ---- provenance ---------------------------------------------------------------------------------------

    private fun provenance(
        untouched: Int,
        of: Int = 10,
        file: String? = "HyleDeco-Regular.ttf",
    ) = ImportProvenance(fromCompiledFont = true, untouchedGlyphs = untouched, outlineGlyphs = of, sourceFileName = file)

    @Test
    fun aProjectThatIsStillTheImportedBinaryIsAFossilWithTheReviewersWords() {
        val fossil = item(ReviewOrder.SOURCE_FOSSIL, provenance = provenance(untouched = 10))
        assertEquals(Severity.FAIL, fossil.severity)
        assertEquals("One source, a Regular UFO with TT curves.", fossil.reviewerQuote)
        assertTrue(
            fossil.summary.startsWith("HyleDeco-Regular.ttf was opened as this project, so its outlines are the binary's"),
            fossil.summary,
        )
        assertEquals("none of 10 glyphs redrawn", fossil.detail)
        assertEquals(CheckRoute.OpenCapture, fossil.action?.route)
    }

    @Test
    fun partlyRedrawnIsAWarningThatCountsWhatIsLeft() {
        val partly = item(ReviewOrder.SOURCE_FOSSIL, provenance = provenance(untouched = 4))
        assertEquals(Severity.WARN, partly.severity)
        assertEquals("4 of 10 glyphs as imported", partly.detail)
    }

    @Test
    fun everyGlyphRedrawnPassesEvenThoughTheProjectBeganAsAFont() {
        val redrawn = item(ReviewOrder.SOURCE_FOSSIL, provenance = provenance(untouched = 0))
        assertEquals(Severity.PASS, redrawn.severity)
        assertNull(redrawn.action)
    }

    @Test
    fun aProjectNotImportedAsACompiledFontHasNothingToFlag() {
        val drawn = item(ReviewOrder.SOURCE_FOSSIL, provenance = ImportProvenance(false, 0, 10))
        assertEquals(Severity.PASS, drawn.severity)
        // With no record at all, cubic outlines (what Typewright draws in) give nothing to flag either.
        val cubic = fx.glyph("T", polygonContour(listOf(Point(0, 0), Point(0, 700), Point(300, 700), Point(300, 0)), CurveFormat.CUBIC))
        assertEquals(Severity.PASS, item(ReviewOrder.SOURCE_FOSSIL, glyphs = listOf(cubic)).severity)
    }

    @Test
    fun quadraticOutlinesWithNoRecordOfWhereTheyCameFromAreAWarning() {
        val quad = fx.glyph("T", fx.polygon(0 to 0, 0 to 700, 300 to 700, 300 to 0), unicodes = listOf(0x54))
        assertEquals(CurveFormat.QUADRATIC, quad.contours.single().format)
        val unknown = item(ReviewOrder.SOURCE_FOSSIL, glyphs = listOf(quad))
        assertEquals(Severity.WARN, unknown.severity)
        assertTrue("TrueType-style quadratic curves" in unknown.summary, unknown.summary)
    }

    // ---- names ----------------------------------------------------------------------------------------------

    private fun names(
        info: UfoFontInfo = clean,
        masters: List<MasterNames> = emptyList(),
    ) = item(ReviewOrder.NAMES, info, otherMasters = masters)

    @Test
    fun presentShortUniqueNamesPass() {
        val ok = names()
        assertEquals(Severity.PASS, ok.severity)
        assertEquals("Fixture Sans Regular", ok.detail)
        assertNull(ok.action)
    }

    @Test
    fun aMissingFamilyOrStyleNameIsAFail() {
        assertEquals(Severity.FAIL, names(clean.copy(familyName = null)).severity)
        assertEquals(Severity.FAIL, names(clean.copy(styleName = " ")).severity)
    }

    @Test
    fun aFamilyAndStyleOverThirtyTwoCharactersIsAFail() {
        val long = names(clean.copy(familyName = "A Very Long Family Name Indeed", styleName = "Regular"))
        assertEquals(Severity.FAIL, long.severity)
        assertTrue("38 characters" in long.summary, long.summary)
        assertEquals(Severity.PASS, names(clean.copy(familyName = "A".repeat(20) + " " + "b".repeat(5), styleName = "Reg")).severity)
    }

    @Test
    fun hyphensAccentsCapitalsAndCamelCaseAreWarnings() {
        for (family in listOf("Hyle-Deco", "Hylé Deco", "HYLE DECO", "HyleDeco")) {
            assertEquals(Severity.WARN, names(clean.copy(familyName = family)).severity, family)
        }
        assertEquals(listOf("a hyphen", "all capitals"), familyNameOddities("HYLE-DECO"))
        assertEquals(emptyList(), familyNameOddities("Fixture Sans"))
    }

    @Test
    fun aKeptReservedFontNameIsAFailThatNamesIt() {
        val reserved =
            names(
                clean.copy(
                    familyName = "Fixture Sans",
                    copyright = "Copyright 2026 The Authors (https://example.org), with Reserved Font Name \"Fixture\".",
                ),
            )
        assertEquals(Severity.FAIL, reserved.severity)
        assertTrue("still uses \"Fixture\"" in reserved.summary, reserved.summary)
        assertEquals(Severity.PASS, names(clean.copy(copyright = "Copyright 2026 The Authors, with no Reserved Font Name")).severity)
    }

    @Test
    fun aFamilyThatIsAlreadyAGoogleFontsFamilyIsAFailAndSaysWhichKind() {
        val clash = names(clean.copy(familyName = "alpha"))
        assertEquals(Severity.FAIL, clash.severity)
        assertTrue("already the name of a Google Fonts family (Alpha)" in clash.summary, clash.summary)
    }

    @Test
    fun anotherMasterWithTheSameFamilyNameIsAClash() {
        val clash = names(masters = listOf(MasterNames("Fixture Sans", "Bold")))
        assertEquals(Severity.FAIL, clash.severity)
        assertTrue("another master in this project" in clash.summary, clash.summary)
    }

    // ---- licence ----------------------------------------------------------------------------------------------

    private fun licence(info: UfoFontInfo) = item(ReviewOrder.LICENCE, info)

    @Test
    fun aCopyrightLineAndTheOflStringsPass() {
        assertEquals(Severity.PASS, licence(clean).severity)
    }

    @Test
    fun noCopyrightLineIsAFail() {
        val none = licence(clean.copy(copyright = null))
        assertEquals(Severity.FAIL, none.severity)
        assertEquals(Severity.FAIL, licence(clean.copy(copyright = "  ")).severity)
    }

    @Test
    fun aCopyrightLineNotInTheExpectedFormIsAWarning() {
        val odd = licence(clean.copy(copyright = "Hyle Deco by me"))
        assertEquals(Severity.WARN, odd.severity)
        assertTrue("does not start with Copyright and a year" in odd.summary, odd.summary)
    }

    @Test
    fun aLicenceThatIsNotTheOflOrHasTheWrongUrlIsAWarning() {
        val other = licence(clean.copy(openTypeNameLicense = "All rights reserved."))
        assertEquals(Severity.WARN, other.severity)
        assertTrue("does not name the SIL Open Font License" in other.summary, other.summary)
        assertEquals(Severity.WARN, licence(clean.copy(openTypeNameLicenseURL = "https://example.org/licence")).severity)
        assertEquals(Severity.WARN, licence(clean.copy(openTypeNameLicenseURL = null)).severity)
    }

    @Test
    fun theLicenceItemSaysItIsForTheOflRouteAndPointsToFontInfo() {
        val bad = licence(clean.copy(copyright = null))
        assertEquals(CheckRoute.OpenFontInfo(FontInfoTarget.LICENCE), bad.action?.route)
        assertTrue("under its own licence chooses that on Ship" in bad.basisNote, bad.basisNote)
    }
}
