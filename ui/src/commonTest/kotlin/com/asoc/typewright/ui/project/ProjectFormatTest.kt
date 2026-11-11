// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.project

import com.asoc.typewright.core.font.ufo.PlistValue
import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.qa.GlyphNodeEconomyResult
import com.asoc.typewright.qa.corpus.NodeEconomyVerdict
import com.asoc.typewright.qa.corpus.Quartiles
import com.asoc.typewright.ui.imports.PickResult
import com.asoc.typewright.ui.imports.pickResultFromBrowserAnswer
import com.asoc.typewright.ui.shell.glyphsSubtitle
import com.asoc.typewright.ui.shell.notJudgedReason
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectFormatTest {
    @Test
    fun numbersAreGroupedInThousandsAndNegativesUseARealMinus() {
        assertEquals("0", groupThousands(0))
        assertEquals("999", groupThousands(999))
        assertEquals("1,763", groupThousands(1763))
        assertEquals("25,991", groupThousands(25991))
        assertEquals("1,000,000", groupThousands(1_000_000))
        assertEquals("−1,234", groupThousands(-1234))
        assertEquals("−292", unitsText(-292))
        assertEquals("984", unitsText(984))
    }

    @Test
    fun theVersionIsMajorDotMinorToThreeDigitsOrNothing() {
        assertEquals("1.024", fontVersionText(UfoFontInfo(versionMajor = 1, versionMinor = 24)))
        assertEquals("2.000", fontVersionText(UfoFontInfo(versionMajor = 2)))
        assertEquals("0.005", fontVersionText(UfoFontInfo(versionMinor = 5)))
        assertNull(fontVersionText(UfoFontInfo()))
    }

    @Test
    fun lineSpacingIsShownToThreeDecimals() {
        assertEquals("1.276", threeDecimals(1.276))
        assertEquals("1.276", threeDecimals((984 + 292) / 1000.0))
        assertEquals("1.000", threeDecimals(1.0))
        assertEquals("0.050", threeDecimals(0.05))
        assertEquals("1.276", threeDecimals(1.2764))
        assertEquals("1.277", threeDecimals(1.2766))
        assertEquals("−0.500", threeDecimals(-0.5))
    }

    private val geometric = ProjectStyle("sans-geometric", StyleOrigin.DECLARED)

    private fun result(
        verdict: NodeEconomyVerdict?,
        box: Quartiles?,
    ) = GlyphNodeEconomyResult("T", 1763, 0, box, null, verdict, null, "")

    @Test
    fun aLongPressSaysWhereTheGlyphSitsInItsBoxInWordsAsWellAsAShape() {
        val box = Quartiles(min = 4.0, q1 = 8.0, med = 8.0, q3 = 11.5, max = 16.0, n = 30)
        val text = glyphBoxSentence("T", 1763, result(NodeEconomyVerdict.OUTLIER, box), geometric)
        assertEquals(
            "T · 1763 on-curve points. ● Outlier. Box over 30 Geometric sans faces: 8–11.5, median 8, range 4–16.",
            text,
        )
        assertTrue("◐ Above the box" in glyphBoxSentence("o", 20, result(NodeEconomyVerdict.ABOVE, box), geometric))
        assertTrue("✓ In range" in glyphBoxSentence("n", 10, result(NodeEconomyVerdict.IN_RANGE, box), geometric))
        assertTrue("1 on-curve point." in glyphBoxSentence("i", 1, result(NodeEconomyVerdict.IN_RANGE, box), geometric))
    }

    @Test
    fun aGlyphWithNoBoxOrNoClassSaysSoInsteadOfInventingOne() {
        assertEquals(
            "ß · 40 on-curve points. The Geometric sans data has no box for this glyph.",
            glyphBoxSentence("ß", 40, null, geometric),
        )
        assertEquals("ß · 40 on-curve points. There is no style class to judge it against yet.", glyphBoxSentence("ß", 40, null, null))
    }

    @Test
    fun theGlyphsHeaderNamesWhatTheLettersAreJudgedAgainstAndSaysWhenThatIsOurHeuristic() {
        val detected = ProjectStyle("sans-geometric", StyleOrigin.DETECTED)
        assertEquals("Latin · no style class to judge against", glyphsSubtitle(listOf("Latn"), null, null))
        assertEquals("Latin · Geometric sans, with no measured faces here", glyphsSubtitle(listOf("Latn"), null, geometric))
        assertEquals(
            "Latin · Geometric sans, with no measured faces here · class read by our heuristic",
            glyphsSubtitle(listOf("Latn"), null, detected),
        )
        assertEquals("Devanagari · no style class to judge against", glyphsSubtitle(listOf("Deva"), null, null))
    }

    @Test
    fun theReasonNothingWasJudgedIsSpecific() {
        assertTrue("cannot read the measured fonts" in notJudgedReason(corpusAvailable = false, style = geometric))
        assertTrue("declare one in the Brief" in notJudgedReason(corpusAvailable = true, style = null))
        assertTrue("no Geometric sans class" in notJudgedReason(corpusAvailable = true, style = geometric))
    }

    @Test
    fun theStyleSaysWhereItCameFromAndOnlyADetectedOneIsOurHeuristic() {
        assertEquals("confirmed by you", ProjectStyle("sans-geometric", StyleOrigin.CONFIRMED).provenance)
        assertEquals("declared by you", geometric.provenance)
        val detected = ProjectStyle("sans-geometric", StyleOrigin.DETECTED)
        assertEquals("read from your letters by our heuristic", detected.provenance)
        assertTrue(detected.isHeuristic && !geometric.isHeuristic)
        assertEquals("Geometric sans", geometric.name)
    }

    @Test
    fun theNameTablePreviewBuildsTheFourNamesFromFamilyAndStyleUnlessTheFontStatesThem() {
        val plain = nameTablePreview(UfoFontInfo(familyName = "Hyle Deco", styleName = "Regular"))
        assertEquals(
            listOf(
                NameTableRow(1, "Hyle Deco"),
                NameTableRow(2, "Regular"),
                NameTableRow(4, "Hyle Deco Regular"),
                NameTableRow(6, "HyleDeco-Regular"),
            ),
            plain,
        )
        assertEquals(listOf("1 Hyle Deco · 2 Regular", "4 Hyle Deco Regular · 6 HyleDeco-Regular"), nameTableLines(plain))

        val stated =
            nameTablePreview(
                UfoFontInfo(
                    familyName = "Lumen",
                    styleName = "Bold",
                    postscriptFontName = "Lumen-Bd",
                    openTypeNamePreferredFamilyName = "Lumen Display",
                    openTypeNamePreferredSubfamilyName = "Bold",
                    other = listOf("styleMapFamilyName" to PlistValue.PString("Lumen Bold Face")),
                ),
            )
        assertEquals(
            listOf(1 to "Lumen Bold Face", 2 to "Bold", 4 to "Lumen Bold", 6 to "Lumen-Bd", 16 to "Lumen Display", 17 to "Bold"),
            stated.map { it.id to it.text },
        )
        assertTrue(nameTablePreview(UfoFontInfo()).isEmpty(), "no family name, no names")
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun aBrowserAnswerIsDecodedAndEveryBadAnswerIsAPlainMessage() {
        val bytes = byteArrayOf(0, 1, 0, 0, 5, 6, 7, 8, 9)
        val ok = pickResultFromBrowserAnswer("Lumen.ttf\u0000" + Base64.encode(bytes))
        assertIs<PickResult.Font>(ok)
        assertEquals("Lumen.ttf", ok.name)
        assertContentEquals(bytes, ok.bytes)

        assertEquals(PickResult.Cancelled, pickResultFromBrowserAnswer(null))
        assertIs<PickResult.Failed>(pickResultFromBrowserAnswer("no-separator"))
        assertIs<PickResult.Failed>(pickResultFromBrowserAnswer("\u0000AAAA"))
        val notBase64 = pickResultFromBrowserAnswer("Lumen.ttf\u0000@@@@")
        assertIs<PickResult.Failed>(notBase64)
        assertTrue("could not be read" in notBase64.message, notBase64.message)
        val huge = pickResultFromBrowserAnswer("Big.ttf\u0000" + Base64.encode(ByteArray(300)), maxBytes = 100)
        assertIs<PickResult.Failed>(huge)
        assertTrue("too large to be a font" in huge.message, huge.message)
    }
}
