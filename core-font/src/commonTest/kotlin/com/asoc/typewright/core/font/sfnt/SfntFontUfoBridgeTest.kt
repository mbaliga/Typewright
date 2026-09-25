// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.font.sfnt

import com.asoc.typewright.core.font.ufo.UfoLib
import com.asoc.typewright.core.font.ufo.readUfoProject
import com.asoc.typewright.core.font.ufo.writeUfoProject
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.count
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun anExplicitFamilyNameOverridesTheNameTable() {
        val info = font.toUfoProject(familyName = "Golden Path Seed").fontInfo
        assertEquals("Golden Path Seed", info.familyName)
        assertEquals("Regular", info.styleName)
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
