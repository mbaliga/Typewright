// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.font.sfnt

import com.asoc.typewright.core.font.ufo.PlistValue
import com.asoc.typewright.core.font.ufo.UfoLib
import com.asoc.typewright.core.font.ufo.readUfoProject
import com.asoc.typewright.core.font.ufo.writeUfoProject
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.count
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [toUfoProject] against the same embedded `fonts/HyleDeco-Regular.ttf` bytes
 * [HyleDecoCrossCheckTest] uses (see [HyleDecoRegularTtfBase64]'s own KDoc for why the font is
 * embedded rather than read off disk here): real glyph count, one real glyph's contours and
 * advance, the real `head`/`hhea`/`OS/2`/`name` metrics and the real `cmap`, cross-checked against
 * fontTools 4.66.0 reading the same file directly; then the whole font through UFO files and back.
 */
class SfntFontUfoBridgeTest {
    private val font by lazy { readSfntFont(HyleDecoRegularTtfBase64.bytes) }

    @Test
    fun carriesEveryGlyphInFontOrder() {
        val project = font.toUfoProject()
        assertEquals(font.numGlyphs, project.glyphs.size)
        assertEquals((0 until font.numGlyphs).map { font.glyphName(it) }, project.glyphs.map { it.name })
    }

    @Test
    fun oneGlyphsContoursAndAdvanceMatchTheSfnt() {
        val project = font.toUfoProject()
        val n = project.glyphs.single { it.name == "n" }
        assertEquals(1, n.contours.size, "n contours")
        assertEquals(
            44,
            n.contours
                .single()
                .points
                .count { it.onCurve },
            "n on-curve points",
        )
        assertEquals(
            0,
            n.contours
                .single()
                .points
                .count { !it.onCurve },
            "n off-curve points",
        )
        assertEquals(469, n.advanceWidth, "n advance width")
    }

    @Test
    fun fontInfoComesFromHeadHheaOs2AndName() {
        val info = font.toUfoProject().fontInfo
        assertEquals("Hyle Deco", info.familyName)
        assertEquals("Regular", info.styleName)
        assertEquals(1000, info.unitsPerEm)
        assertEquals(984, info.ascender)
        assertEquals(-292, info.descender)
        assertEquals(500, info.xHeight)
        assertEquals(700, info.capHeight)
    }

    @Test
    fun fontInfoCarriesRightsPeopleAndTheRestOfTheMetrics() {
        // Every value is what fontTools 4.66.0 reads from the same file.
        val info = font.toUfoProject().fontInfo
        assertEquals("Copyright 2026 The Hyle Deco Project Authors (https://github.com/mbaliga/HyleDeco-gf)", info.copyright)
        assertEquals(
            "This Font Software is licensed under the SIL Open Font License, Version 1.1. " +
                "This license is available with a FAQ at: https://openfontlicense.org",
            info.openTypeNameLicense,
        )
        assertEquals("https://openfontlicense.org", info.openTypeNameLicenseURL)
        assertEquals("HyleDeco-Regular", info.postscriptFontName)
        assertEquals("????", info.openTypeOS2VendorID)
        assertEquals(1, info.versionMajor)
        assertEquals(24, info.versionMinor)
        assertEquals(984, info.openTypeHheaAscender)
        assertEquals(-292, info.openTypeHheaDescender)
        assertEquals(0, info.openTypeHheaLineGap)
        assertEquals(984, info.openTypeOS2TypoAscender)
        assertEquals(-292, info.openTypeOS2TypoDescender)
        assertEquals(984, info.openTypeOS2WinAscent)
        assertEquals(292, info.openTypeOS2WinDescent)
        val other = info.other.toMap()
        assertEquals(PlistValue.PInteger(400), other["openTypeOS2WeightClass"])
        assertEquals(PlistValue.PInteger(5), other["openTypeOS2WidthClass"])
        assertNull(other["openTypeOS2Type"], "fsType 0 sets no embedding bit")
        assertNull(other["styleMapFamilyName"], "legacy and typographic names agree")
    }

    @Test
    fun theSourcesUniqueIdAndFullNameAreLeftForTheCompilerToBuild() {
        val keys =
            font
                .toUfoProject()
                .fontInfo.other
                .map { it.first }
        assertTrue("openTypeNameUniqueID" !in keys)
        assertTrue("postscriptFullName" !in keys)
    }

    @Test
    fun compositeGlyphsAreCountedAndComeBackFlattened() {
        // fontTools 4.66.0: 161 of Hyle Deco's 338 glyphs are composites (uni00C1 is A and an acute).
        assertEquals(161, font.compositeGlyphCount)
        val aacute = font.toUfoProject().glyphs.single { it.name == "uni00C1" }
        assertTrue(aacute.contours.size > 1, "uni00C1 is the A and the acute, flattened to plain contours")
    }

    @Test
    fun tableTagsListWhatTheFontHolds() {
        assertTrue("GPOS" in font.tableTags)
        assertTrue("glyf" in font.tableTags)
        assertTrue("fvar" !in font.tableTags)
    }

    @Test
    fun anExplicitFamilyNameOverridesTheNameTable() {
        val info = font.toUfoProject(familyName = "Golden Path Seed").fontInfo
        assertEquals("Golden Path Seed", info.familyName)
        assertEquals("Regular", info.styleName)
        assertNull(info.postscriptFontName, "the source's PostScript name would contradict the new family")
    }

    @Test
    fun unicodesComeFromTheCmapInAscendingOrder() {
        val glyphs = font.toUfoProject().glyphs.associateBy { it.name }
        assertEquals(listOf(0x41), glyphs.getValue("A").unicodes)
        assertEquals(listOf(0x20, 0xA0), glyphs.getValue("space").unicodes)
        assertEquals(listOf(0x22, 0x201D), glyphs.getValue("quotedblright").unicodes)
        assertEquals(emptyList(), glyphs.getValue(".notdef").unicodes)
    }

    @Test
    fun everyCmapEntryToARealGlyphLandsOnExactlyThatGlyph() {
        val project = font.toUfoProject()
        val fromGlyphs = project.glyphs.flatMap { glyph -> glyph.unicodes.map { it to glyph.name } }.toMap()
        val fromCmap =
            font.cmap.bestMapping
                .filterValues { it != 0 }
                .mapValues { (_, glyphId) -> font.glyphName(glyphId) }
        assertEquals(fromCmap, fromGlyphs)
        // fontTools 4.66.0 getBestCmap() on the same file: 323 entries.
        assertEquals(323, fromGlyphs.size)
    }

    @Test
    fun theProjectDeclaresItsStraightContoursQuadratic() {
        assertEquals(UfoLib(lineContourFormat = CurveFormat.QUADRATIC), font.toUfoProject().lib)
    }

    @Test
    fun theWholeFontRoundTripsThroughUfoFilesAndRewritesByteIdentically() {
        val project = font.toUfoProject()
        val files = writeUfoProject(project)
        val read = readUfoProject(files)
        assertEquals(project, read)
        assertEquals(files, writeUfoProject(read))
    }

    @Test
    fun theFixtureCountsSurviveTheRoundTrip() {
        val read = readUfoProject(writeUfoProject(font.toUfoProject())).glyphs.associateBy { it.name }
        // CLAUDE.md's shipped counts, on-curve · off-curve.
        assertEquals(1763 to 0, read.getValue("T").count().let { it.onCurveEquivalent to it.offCurve })
        assertEquals(80 to 0, read.getValue("o").count().let { it.onCurveEquivalent to it.offCurve })
        assertEquals(44 to 0, read.getValue("n").count().let { it.onCurveEquivalent to it.offCurve })
        assertEquals(1252 to 0, read.getValue("H").count().let { it.onCurveEquivalent to it.offCurve })
    }
}
