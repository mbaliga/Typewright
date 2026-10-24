// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.shell

import com.asoc.typewright.core.font.ufo.PlistValue
import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.core.font.ufo.UfoProject
import com.asoc.typewright.core.font.ufo.parsePlistDict
import com.asoc.typewright.core.font.ufo.writeUfoProject
import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.ContourPoint
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.qa.report.CheckInputs
import com.asoc.typewright.qa.report.FontInfoTarget
import com.asoc.typewright.qa.report.ReviewOrder
import com.asoc.typewright.qa.report.Severity
import com.asoc.typewright.qa.report.buildCheckReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What the Font info page lets through, and what it turns away, with no page and no project: only the rules. */
class FontInfoRulesTest {
    private val blank = UfoFontInfo(familyName = "Untitled", styleName = "Regular", unitsPerEm = 1000)

    private fun set(
        info: UfoFontInfo,
        field: FontInfoField,
        text: String,
    ): FontInfoChange.Set = assertIs<FontInfoChange.Set>(FontInfoRules.change(info, field, text), "$field <- \"$text\"")

    private fun glyphSquare(
        name: String,
        left: Int,
        bottom: Int,
        right: Int,
        top: Int,
    ) = Glyph(
        name,
        600,
        listOf(
            Contour(
                listOf(
                    Point(left, bottom),
                    Point(left, top),
                    Point(right, top),
                    Point(right, bottom),
                ).map { ContourPoint(it, onCurve = true) },
                CurveFormat.QUADRATIC,
            ),
        ),
    )

    private fun refused(
        info: UfoFontInfo,
        field: FontInfoField,
        text: String,
    ): String = assertIs<FontInfoChange.Refused>(FontInfoRules.change(info, field, text), "$field <- \"$text\"").message

    // ---- text ------------------------------------------------------------------------------------

    @Test
    fun aTypedValueIsTrimmedAndSetOnTheKeyItBelongsTo() {
        assertEquals("Hylé Deco", set(blank, FontInfoField.FAMILY, "  Hylé Deco ").info.familyName)
        assertEquals("Bold", set(blank, FontInfoField.STYLE, "Bold").info.styleName)
        assertEquals("A. Designer", set(blank, FontInfoField.DESIGNER, "A. Designer").info.openTypeNameDesigner)
        assertEquals("Copyright 2026 The X Authors", set(blank, FontInfoField.COPYRIGHT, "Copyright 2026 The X Authors").info.copyright)
        assertEquals("https://example.org", set(blank, FontInfoField.DESIGNER_URL, "https://example.org").info.openTypeNameDesignerURL)
        assertEquals("GOOG", set(blank, FontInfoField.VENDOR_ID, "GOOG").info.openTypeOS2VendorID)
    }

    @Test
    fun valuesTheFontCannotKnowStartEmptyAndAreNeverInvented() {
        // Nothing but what the person typed is ever written: a blank project has no designer, no copyright, no licence.
        for (field in listOf(FontInfoField.DESIGNER, FontInfoField.COPYRIGHT, FontInfoField.LICENCE_TEXT, FontInfoField.VENDOR_ID)) {
            assertNull(FontInfoRules.textIn(blank, field), "$field starts empty")
        }
        assertNull(FontInfoRules.licenceOf(blank))
        // Clearing an optional value takes it out of the file again.
        val withDesigner = set(blank, FontInfoField.DESIGNER, "Ada").info
        assertNull(set(withDesigner, FontInfoField.DESIGNER, "  ").info.openTypeNameDesigner)
    }

    @Test
    fun aFamilyOrStyleCannotBeLeftBlank() {
        assertEquals("A font needs a family name; type one.", refused(blank, FontInfoField.FAMILY, "   "))
        assertEquals("A font needs a style name; type one.", refused(blank, FontInfoField.STYLE, ""))
    }

    @Test
    fun aValueThatWouldWriteAControlCharacterOrAnInvalidPlistIsRefusedInEveryTextField() {
        val bad =
            listOf(
                "a\u0001b",
                "tab\there",
                "two\nlines",
                "nul\u0000",
                "del\u007F",
                "next\u0085line",
                // A lone surrogate and a non-character: XML 1.0 cannot carry either, so a plist with them does not open elsewhere.
                "half\uD800",
                "end￿",
            )
        val textFields = FontInfoField.entries.filter { !it.numeric }
        for (field in textFields) {
            for (text in bad) {
                val message = refused(blank, field, text)
                assertTrue("cannot carry" in message, "$field accepted or mis-explained $text: $message")
            }
        }
    }

    @Test
    fun whatIsWrittenIsAlwaysAPlistTheOtherToolsCanRead() {
        // Everything the rules let through survives the round trip through fontinfo.plist as exactly itself.
        val info =
            set(
                set(blank, FontInfoField.COPYRIGHT, "Copyright 2026 A & B <ab@example.org> \"quoted\"").info,
                FontInfoField.DESIGNER,
                "Jörg あ",
            ).info
        val files = writeUfoProject(UfoProject(info, emptyList()))
        val plist = parsePlistDict(files.getValue("fontinfo.plist"))
        assertEquals("Copyright 2026 A & B <ab@example.org> \"quoted\"", (plist["copyright"] as PlistValue.PString).value)
        assertTrue(files.values.all { text -> text.none { it < ' ' && it != '\n' && it != '\t' && it != '\r' } })
    }

    @Test
    fun aWebAddressStartsWithHttpAndAVendorIdIsFourPrintableCharacters() {
        assertEquals(
            "The designer's web address starts with https:// (or http://).",
            refused(blank, FontInfoField.DESIGNER_URL, "example.org"),
        )
        assertEquals("The licence's web address starts with https:// (or http://).", refused(blank, FontInfoField.LICENCE_URL, "ftp://x"))
        assertEquals("A vendor ID is up to four letters or digits, such as GOOG.", refused(blank, FontInfoField.VENDOR_ID, "GOOGLE"))
        assertEquals("A vendor ID is up to four letters or digits, such as GOOG.", refused(blank, FontInfoField.VENDOR_ID, "GÖOG"))
    }

    @Test
    fun theVersionIsMajorDotThreeDigitsAsTheScreenPrintsIt() {
        val v = set(blank, FontInfoField.VERSION, "1.024").info
        assertEquals(1, v.versionMajor)
        assertEquals(24, v.versionMinor)
        assertEquals("1.024", FontInfoRules.versionText(v))
        for (text in listOf("1", "1.5", "1.0245", "one.000", "1.02a", "1.024.1", ".024")) {
            assertEquals(
                "Write the version as a number, a dot and three digits, like 1.024.",
                refused(blank, FontInfoField.VERSION, text),
                text,
            )
        }
        assertNull(set(v, FontInfoField.VERSION, "").info.versionMajor)
    }

    @Test
    fun theSameValueAgainChangesNothing() {
        val named = set(blank, FontInfoField.DESIGNER, "Ada").info
        assertIs<FontInfoChange.Unchanged>(FontInfoRules.change(named, FontInfoField.DESIGNER, "Ada"))
        assertIs<FontInfoChange.Unchanged>(FontInfoRules.change(blank, FontInfoField.FAMILY, "Untitled"))
    }

    // ---- numbers ---------------------------------------------------------------------------------

    @Test
    fun aMetricTakesANumberOrAnExpressionOverTheFontsOwnNames() {
        val withHeights = blank.copy(capHeight = 700, xHeight = 500)
        assertEquals(950, set(blank, FontInfoField.ASCENDER, "950").info.ascender)
        assertEquals(600, set(withHeights, FontInfoField.CAP_HEIGHT, "=xheight+100").info.capHeight)
        assertEquals(-250, set(withHeights, FontInfoField.DESCENDER, "=-(capheight-xheight)-50").info.descender)
        assertEquals(350, set(withHeights, FontInfoField.X_HEIGHT, "=capheight/2").info.xHeight)
        assertEquals(1000, set(blank, FontInfoField.ASCENDER, "=upm").info.ascender)
    }

    @Test
    fun anExpressionCanNameAGlyphsSidebearing() {
        val info = blank.copy(capHeight = 700)
        val square = glyphSquare("n", 100, 0, 500, 500)
        val scope = ExprScopeOf(info, listOf(square))
        val change = FontInfoRules.change(info, FontInfoField.X_HEIGHT, "=n.lsb+400", scope)
        assertEquals(500, assertIs<FontInfoChange.Set>(change).info.xHeight)
    }

    @Test
    fun aMistakeInANumberComesBackAsThePlainWordsTheDrawInspectorUses() {
        assertEquals("\"abc\" is not a number. Start with = to use a name such as =xheight.", refused(blank, FontInfoField.ASCENDER, "abc"))
        assertEquals("Type a number, or = and an expression.", refused(blank, FontInfoField.ASCENDER, " "))
        assertEquals("Cannot divide by zero.", refused(blank, FontInfoField.ASCENDER, "=1/0"))
    }

    @Test
    fun aMetricThatMakesNoSenseIsRefusedWithTheReason() {
        val spaced = blank.copy(ascender = 800, descender = -200)
        assertEquals(
            "The descender is below the baseline, so it is a negative number (-200, not 200).",
            refused(spaced, FontInfoField.DESCENDER, "200"),
        )
        assertEquals("The ascender is the height above the baseline, so it is above 0.", refused(spaced, FontInfoField.ASCENDER, "-300"))
        assertEquals("The ascender is the height above the baseline, so it is above 0.", refused(blank, FontInfoField.ASCENDER, "0"))
        // A file from elsewhere can state a descender above the baseline; the ascender still has to clear it.
        assertEquals(
            "The ascender has to be above the descender (100).",
            refused(blank.copy(descender = 100), FontInfoField.ASCENDER, "50"),
        )
        assertEquals("The cap height is the height above the baseline, so it is above 0.", refused(blank, FontInfoField.CAP_HEIGHT, "-5"))
        assertEquals("The line gap is extra space between lines, so it is 0 or more.", refused(blank, FontInfoField.LINE_GAP, "-1"))
        assertTrue("far outside the em" in refused(blank, FontInfoField.ASCENDER, "99999"))
    }

    @Test
    fun anAscenderMovesTheHheaAndTypoValuesTheFontStatesSoTheyStayEqual() {
        val imported = blank.copy(ascender = 800, descender = -200, openTypeHheaAscender = 900, openTypeOS2TypoAscender = 880)
        val change = set(imported, FontInfoField.ASCENDER, "950")
        assertEquals(950, change.info.ascender)
        assertEquals(950, change.info.openTypeHheaAscender)
        assertEquals(950, change.info.openTypeOS2TypoAscender)
        assertEquals("The hhea and typo ascender are set to match.", change.note)
        // A font that states none of them gets only the generic key, which the compiler copies to the others.
        val plain = set(blank, FontInfoField.ASCENDER, "950")
        assertNull(plain.info.openTypeHheaAscender)
        assertNull(plain.note)
        // The value the page shows is the one that was set.
        assertEquals("950", FontInfoRules.textIn(change.info, FontInfoField.ASCENDER))
    }

    @Test
    fun aLineGapIsSetOnHheaAndOnTypoWhenTheFontStatesIt() {
        val change = set(blank.copy(openTypeOS2TypoLineGap = 0), FontInfoField.LINE_GAP, "50")
        assertEquals(50, change.info.openTypeHheaLineGap)
        assertEquals(50, change.info.openTypeOS2TypoLineGap)
        assertEquals("0", FontInfoRules.textIn(blank, FontInfoField.LINE_GAP))
    }

    // ---- licence ---------------------------------------------------------------------------------

    @Test
    fun theOflAndApacheChoicesWriteTheirStandardLinesAndTheAuditReadsThemBack() {
        val ofl = assertIs<FontInfoChange.Set>(FontInfoRules.chooseLicence(blank, LicenceChoice.OFL)).info
        assertEquals(LicenceStrings.OFL_DESCRIPTION, ofl.openTypeNameLicense)
        assertEquals("https://openfontlicense.org", ofl.openTypeNameLicenseURL)
        assertEquals(LicenceChoice.OFL, FontInfoRules.licenceOf(ofl))
        val apache = assertIs<FontInfoChange.Set>(FontInfoRules.chooseLicence(ofl, LicenceChoice.APACHE)).info
        assertEquals(LicenceChoice.APACHE, FontInfoRules.licenceOf(apache))
        assertIs<FontInfoChange.Unchanged>(FontInfoRules.chooseLicence(ofl, LicenceChoice.OFL))
    }

    @Test
    fun aProprietaryOrOtherLicenceIsTheOwnersOwnWordsAndTypewrightWritesNone() {
        for (choice in listOf(LicenceChoice.PROPRIETARY, LicenceChoice.OTHER)) {
            val change = FontInfoRules.chooseLicence(blank, choice)
            assertIs<FontInfoChange.Unchanged>(change, "$choice writes nothing")
            assertTrue("does not write a licence for you" in change.reason)
        }
        val proprietary = set(blank, FontInfoField.LICENCE_TEXT, "Proprietary. Not for redistribution.").info
        assertEquals(LicenceChoice.PROPRIETARY, FontInfoRules.licenceOf(proprietary))
    }

    @Test
    fun theStandardOflLinesAreExactlyWhatTheLicenceCheckAsksFor() {
        fun licence(info: UfoFontInfo) = buildCheckReport(CheckInputs(UfoProject(info, emptyList()))).item(ReviewOrder.LICENCE)?.severity
        assertEquals(Severity.FAIL, licence(blank), "no copyright line fails")
        val withCopyright = set(blank, FontInfoField.COPYRIGHT, "Copyright 2026 The Untitled Project Authors (https://example.org)").info
        assertEquals(Severity.WARN, licence(withCopyright), "a copyright alone still does not name the licence")
        val done = assertIs<FontInfoChange.Set>(FontInfoRules.chooseLicence(withCopyright, LicenceChoice.OFL)).info
        assertEquals(Severity.PASS, licence(done))
    }

    // ---- where a Check action lands ---------------------------------------------------------------

    @Test
    fun aCheckActionLandsOnTheFirstValueTheFindingIsAboutThatIsNotYetSet() {
        assertEquals(FontInfoField.ASCENDER, FontInfoRules.landing(FontInfoTarget.METRICS, blank))
        assertEquals(FontInfoField.DESCENDER, FontInfoRules.landing(FontInfoTarget.METRICS, blank.copy(ascender = 800)))
        assertEquals(
            FontInfoField.LINE_GAP,
            FontInfoRules.landing(FontInfoTarget.METRICS, blank.copy(ascender = 800, descender = -200, openTypeHheaLineGap = 90)),
        )
        assertEquals(FontInfoField.FAMILY, FontInfoRules.landing(FontInfoTarget.NAMES, blank.copy(familyName = null)))
        assertEquals(FontInfoField.STYLE, FontInfoRules.landing(FontInfoTarget.NAMES, blank.copy(styleName = " ")))
        assertEquals(FontInfoField.COPYRIGHT, FontInfoRules.landing(FontInfoTarget.LICENCE, blank))
        assertEquals(FontInfoField.LICENCE_TEXT, FontInfoRules.landing(FontInfoTarget.LICENCE, blank.copy(copyright = "Copyright 2026 X")))
        assertNotNull(FontInfoField.entries.firstOrNull { it == FontInfoRules.landing(FontInfoTarget.LICENCE, blank) })
    }
}
