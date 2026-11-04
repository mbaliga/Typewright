// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.font.sfnt

import com.asoc.typewright.core.font.ufo.PlistValue
import com.asoc.typewright.core.font.ufo.parsePlistDict
import com.asoc.typewright.core.font.ufo.readUfoProject
import com.asoc.typewright.core.font.ufo.writeUfoProject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [toUfoProject] against the real, licensed-for-reuse faces in `data/learn-faces`, read off disk
 * (this is `jvmTest`: the bytes are too large to embed for the other targets). The facts checked
 * are the ones fontTools 4.66 reports for the same files: the `OS/2` vendor id of Work Sans is
 * `WEI` followed by a NUL, which must not reach `fontinfo.plist`.
 */
class SfntFontUfoBridgeRealFontsTest {
    private fun face(path: String): SfntFont = readSfntFont(File(repoRoot(), "data/learn-faces/$path").readBytes())

    @Test
    fun workSansVendorIdLosesItsNulPaddingAndTheFontInfoWritesAPlistWithNoControlCharacter() {
        val project = face("worksans/WorkSans[wght].ttf").toUfoProject()
        assertEquals("WEI", project.fontInfo.openTypeOS2VendorID)

        val files = writeUfoProject(project)
        for ((path, text) in files) {
            val bad = text.firstOrNull { it < ' ' && it != '\n' && it != '\t' && it != '\r' }
            assertNull(bad, "$path holds the control character U+${bad?.code?.toString(16)}")
        }
        val plist = parsePlistDict(files.getValue("fontinfo.plist"))
        assertEquals(PlistValue.PString("WEI"), plist.entries.toMap()["openTypeOS2VendorID"])
        assertEquals(project, readUfoProject(files))
    }

    /** The smallest x of any point of [glyph]. */
    private fun leftEdge(glyph: com.asoc.typewright.core.geometry.Glyph): Int = glyph.contours.minOf { c -> c.points.minOf { it.point.x } }

    @Test
    fun anOutlineIsDrawnWhereTheLeftSideBearingPutsItNotWhereGlyfStoredIt() {
        // fonts/HyleDeco-Regular.ttf stores n at x = 61..433 in glyf but gives it a left side bearing of 46 in
        // hmtx (head.flags bit 1 unset), so every rasteriser (and fontTools' glyph set) draws it at 46..418.
        val sample = readSfntFont(File(repoRoot(), "fonts/HyleDeco-Regular.ttf").readBytes())
        val n = assertNotNull(sample.glyph("n"))
        val xs = n.contours.flatMap { it.points }.map { it.point.x }
        assertEquals(46, xs.min())
        assertEquals(418, xs.max())
        assertEquals(469, n.advanceWidth)
        assertEquals(51, n.advanceWidth - xs.max(), "the right side bearing the workbook quotes")
        // Every glyph with an outline now starts on its left side bearing, composites included.
        var checked = 0
        for (gid in 0 until sample.numGlyphs) {
            val glyph = sample.glyph(gid)
            if (glyph.contours.isEmpty()) continue
            assertEquals(sample.hmtx[gid].leftSideBearing, leftEdge(glyph), "glyph ${glyph.name}")
            checked++
        }
        assertTrue(checked > 300, "only $checked glyphs were checked")
        // A font that keeps glyf and hmtx equal is left exactly as stored.
        val poppins = face("poppins/Poppins-Regular.ttf")
        for (gid in 0 until poppins.numGlyphs) {
            val glyph = poppins.glyph(gid)
            if (glyph.contours.isNotEmpty()) assertEquals(poppins.hmtx[gid].leftSideBearing, leftEdge(glyph), "glyph ${glyph.name}")
        }
    }

    @Test
    fun everyLearnFaceBridgesToAFontInfoWithNoControlCharacters() {
        val faces = File(repoRoot(), "data/learn-faces").walkTopDown().filter { it.extension == "ttf" }.toList()
        assertTrue(faces.size >= 15, "expected the learn faces, found ${faces.size}")
        for (file in faces) {
            val info = readSfntFont(file.readBytes()).toUfoProject().fontInfo
            val text = writeUfoProject(readSfntFont(file.readBytes()).toUfoProject()).getValue("fontinfo.plist")
            assertFalse(text.any { it < ' ' && it != '\n' && it != '\t' }, "${file.name}: fontinfo.plist holds a control character")
            assertNotNull(info.familyName, "${file.name}: family name")
            assertNotNull(info.copyright, "${file.name}: copyright")
        }
    }

    @Test
    fun poppinsCarriesItsRightsAndPeopleAndMetrics() {
        val font = face("poppins/Poppins-Regular.ttf")
        val info = font.toUfoProject().fontInfo
        assertEquals("Poppins", info.familyName)
        assertEquals("Regular", info.styleName)
        assertEquals("Copyright 2020 The Poppins Project Authors (https://github.com/itfoundry/Poppins)", info.copyright)
        assertEquals("Ninad Kale (Devanagari), Jonny Pinhorn (Latin)", info.openTypeNameDesigner)
        assertEquals("Indian Type Foundry", info.openTypeNameManufacturer)
        assertEquals("https://indiantypefoundry.com", info.openTypeNameManufacturerURL)
        assertEquals("https://scripts.sil.org/OFL", info.openTypeNameLicenseURL)
        assertTrue(info.openTypeNameLicense.orEmpty().startsWith("This Font Software is licensed under the SIL Open Font License"))
        assertEquals("ITFO", info.openTypeOS2VendorID)
        assertEquals("Poppins-Regular", info.postscriptFontName)
        assertEquals(1000, info.unitsPerEm)
        assertEquals(1050, info.ascender)
        assertEquals(-350, info.descender)
        assertEquals(1050, info.openTypeHheaAscender)
        assertEquals(4, info.versionMajor)
        assertEquals(4, info.versionMinor)
    }

    @Test
    fun theUniqueIdAndFullNameOfTheSourceAreNotCarriedOver() {
        val keys =
            face("poppins/Poppins-Regular.ttf")
                .toUfoProject()
                .fontInfo.other
                .map { it.first }
        assertFalse("openTypeNameUniqueID" in keys)
        assertFalse("postscriptFullName" in keys)
    }

    @Test
    fun aVariableFaceReportsItsFvarTable() {
        assertTrue("fvar" in face("worksans/WorkSans[wght].ttf").tableTags)
        assertFalse("fvar" in face("poppins/Poppins-Regular.ttf").tableTags)
        assertTrue("GPOS" in face("poppins/Poppins-Regular.ttf").tableTags)
    }

    @Test
    fun theLegacyFamilyBecomesStyleMapFamilyNameOnlyWhenItDiffersFromTheTypographicOne() {
        // Poppins Regular has no nameID 16, so family comes from nameID 1 and there is nothing to map.
        val poppins = face("poppins/Poppins-Regular.ttf").toUfoProject().fontInfo
        assertNull(poppins.other.firstOrNull { it.first == "styleMapFamilyName" })
    }

    private fun repoRoot(): File =
        generateSequence(File("").absoluteFile) { it.parentFile }.firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("no settings.gradle.kts above ${File("").absolutePath}; this test runs from inside the repository")
}
