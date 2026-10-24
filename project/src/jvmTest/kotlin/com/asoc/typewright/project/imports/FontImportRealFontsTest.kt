// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project.imports

import com.asoc.typewright.core.font.sfnt.readSfntFont
import com.asoc.typewright.core.font.sfnt.readSfntTableDirectory
import com.asoc.typewright.core.font.ufo.readUfoProject
import com.asoc.typewright.core.font.ufo.writeUfoProject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The importer against the repository's real fonts, read off disk: Hyle Deco (the project's own
 * OFL font), Poppins and the learn faces. Every expectation was read from the same file with
 * fontTools 4.66, not from this code.
 */
class FontImportRealFontsTest {
    private val repoRoot: File =
        generateSequence(File("").absoluteFile) { it.parentFile }.firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("no settings.gradle.kts above ${File("").absolutePath}")

    private fun bytes(path: String) = File(repoRoot, path).readBytes()

    private fun ready(
        name: String,
        path: String,
    ): ImportCandidate {
        val review = reviewFontFile(name, bytes(path))
        assertIs<ImportReview.Ready>(review, "$name: $review")
        return review.candidate
    }

    @Test
    fun hyleDecoReportsItsGlyphsMetricsAndCoverage() {
        val candidate = ready("HyleDeco-Regular.ttf", "fonts/HyleDeco-Regular.ttf")
        val report = candidate.report
        assertEquals(338, report.glyphCount)
        assertEquals(323, report.mappedCharacters)
        assertEquals(1000, report.metrics.unitsPerEm)
        assertEquals(984, report.metrics.ascender)
        assertEquals(-292, report.metrics.descender)
        assertEquals(0, report.metrics.lineGap)
        assertEquals(984, report.metrics.typoAscender)
        assertEquals(-292, report.metrics.typoDescender)
        assertEquals(984, report.metrics.winAscent)
        assertEquals(292, report.metrics.winDescent)
        assertEquals(500, report.metrics.xHeight)
        assertEquals(700, report.metrics.capHeight)
        assertEquals(listOf(ScriptCoverage("Latn", "Latin", 221)), report.scripts)
        assertEquals(OutlineKind.QUADRATIC, report.outlines)
        assertEquals(ImportFormat.FONT_FILE, report.format)
        assertEquals(ImportVerdict.FINE, candidate.audit.verdict)
        assertEquals("SIL Open Font License 1.1", candidate.audit.licenceName)
    }

    @Test
    fun hyleDecoKeepsItsQuadraticOutlinesExactlyAsDrawn() {
        val project = ready("HyleDeco-Regular.ttf", "fonts/HyleDeco-Regular.ttf").ufo
        val byName = project.glyphs.associateBy { it.name }
        // CLAUDE.md's shipped counts: on-curve, off-curve.
        val counts = listOf("T" to (1763 to 0), "o" to (80 to 0), "n" to (44 to 0), "H" to (1252 to 0))
        for ((name, expected) in counts) {
            val glyph = byName.getValue(name)
            val points = glyph.contours.flatMap { it.points }
            assertEquals(expected, points.count { it.onCurve } to points.count { !it.onCurve }, name)
        }
        assertTrue(project.glyphs.flatMap { it.contours }.all { it.format == com.asoc.typewright.core.geometry.CurveFormat.QUADRATIC })
    }

    @Test
    fun poppinsIsOflAndHasLatinAndDevanagari() {
        val candidate = ready("Poppins-Regular.ttf", "data/learn-faces/poppins/Poppins-Regular.ttf")
        assertEquals(LicenceKind.OFL, candidate.audit.licence)
        assertEquals("SIL Open Font License 1.1", candidate.audit.licenceName)
        assertEquals(1060, candidate.report.glyphCount)
        assertEquals(471, candidate.report.mappedCharacters)
        assertEquals(
            listOf(ScriptCoverage("Latn", "Latin", 244), ScriptCoverage("Deva", "Devanagari", 94)),
            candidate.report.scripts,
        )
        assertEquals("Ninad Kale (Devanagari), Jonny Pinhorn (Latin)", candidate.audit.designer)
        assertTrue(candidate.report.notes.any { "Kerning and layout features" in it })
    }

    @Test
    fun everyLearnFaceIsFineOrRenameAndTheReservedNamesAreTheOnesFontToolsShows() {
        val reserved =
            mapOf(
                "josefinsans/JosefinSans[wght].ttf" to listOf("Josefin Sans"),
                "limelight/Limelight-Regular.ttf" to listOf("Limelight"),
                "playfairdisplay/PlayfairDisplay[wght].ttf" to listOf("Playfair Display"),
                "sourcesans3/SourceSans3[wght].ttf" to listOf("Source"),
                "unifrakturmaguntia/UnifrakturMaguntia-Book.ttf" to listOf("UnifrakturMaguntia"),
            )
        val faces = File(repoRoot, "data/learn-faces").walkTopDown().filter { it.extension == "ttf" }.toList()
        assertEquals(17, faces.size)
        for (file in faces) {
            val relative = file.relativeTo(File(repoRoot, "data/learn-faces")).path
            val review = reviewFontFile(file.name, file.readBytes())
            assertIs<ImportReview.Ready>(review, "$relative: $review")
            val audit = review.candidate.audit
            val expected = reserved[relative]
            assertEquals(expected ?: emptyList(), audit.reservedFontNames, relative)
            assertEquals(if (expected != null) ImportVerdict.RENAME else ImportVerdict.FINE, audit.verdict, relative)
            val kind = if (relative.startsWith("robotoslab")) LicenceKind.APACHE else LicenceKind.OFL
            assertEquals(kind, audit.licence, relative)
        }
    }

    @Test
    fun theWholeOflTextBesideEachFaceDeclaresExactlyTheNamesItsFirstLineDoes() {
        // The licence files the repository ships are whole OFL texts: a copyright line, the licence's own definitions and its
        // conditions. Reading a full text must find the names its copyright line declares (read by eye from each file's first
        // line) and none of the licence's own words ("Reserved Font Name" refers to..., may use the Reserved Font Name(s) unless...).
        val declared =
            mapOf(
                "josefinsans" to listOf("Josefin Sans"),
                "librebaskerville" to listOf("Libre Baskerville"),
                "limelight" to listOf("Limelight"),
                "playfairdisplay" to listOf("Playfair Display"),
                "sourcesans3" to listOf("Source"),
                "unifrakturmaguntia" to listOf("UnifrakturMaguntia"),
            )
        val dirs = File(repoRoot, "data/learn-faces").listFiles().orEmpty().filter { File(it, "OFL.txt").isFile }
        assertTrue(dirs.size >= 15, "the faces' OFL texts were not found: " + dirs.map { it.name })
        for (dir in dirs) {
            assertEquals(declared[dir.name].orEmpty(), reservedFontNames(File(dir, "OFL.txt").readText()), dir.name + "/OFL.txt")
        }
        assertEquals(
            listOf("Source"),
            reservedFontNames(File(repoRoot, "scripts/templates/OFL.txt").readText()),
            "the template's first line reserves Source, and none of the licence's own words add a name",
        )
        assertEquals(emptyList(), reservedFontNames(File(repoRoot, "fonts/OFL.txt").readText()), "Hyle Deco reserves no name")
    }

    @Test
    fun aVariableFaceImportsItsDefaultInstanceAndSaysSo() {
        val candidate = ready("WorkSans[wght].ttf", "data/learn-faces/worksans/WorkSans[wght].ttf")
        assertTrue(candidate.report.notes.any { "variable font" in it && "default instance" in it }, candidate.report.notes.toString())
        assertEquals(1349, candidate.report.glyphCount)
        assertEquals("WEI", candidate.ufo.fontInfo.openTypeOS2VendorID)
        for ((path, text) in writeUfoProject(candidate.ufo)) {
            assertTrue(text.none { it < ' ' && it != '\n' && it != '\t' && it != '\r' }, "$path holds a control character")
        }
    }

    @Test
    fun compositesAreFlattenedAndCounted() {
        val candidate = ready("HyleDeco-Regular.ttf", "fonts/HyleDeco-Regular.ttf")
        assertTrue(
            candidate.report.notes.any { it.startsWith("161 glyphs are built from other glyphs") },
            candidate.report.notes.toString(),
        )
    }

    // ---- Refusals ---------------------------------------------------------------------------------

    /** [fontBytes] with every name record for [nameId] rewritten to [text], padded with spaces or cut to the record's own length. */
    private fun withNameRecord(
        fontBytes: ByteArray,
        nameId: Int,
        text: String,
    ): ByteArray {
        val out = fontBytes.copyOf()
        val table = checkNotNull(readSfntTableDirectory(out)["name"]).offset

        fun u16(at: Int) = ((out[at].toInt() and 0xFF) shl 8) or (out[at + 1].toInt() and 0xFF)
        val count = u16(table + 2)
        val stringOffset = u16(table + 4)
        var patched = 0
        for (i in 0 until count) {
            val record = table + 6 + i * 12
            if (u16(record + 6) != nameId) continue
            val platform = u16(record)
            val length = u16(record + 8)
            val start = table + stringOffset + u16(record + 10)
            if (platform == 1) {
                val padded = text.padEnd(length).take(length)
                padded.forEachIndexed { k, c -> out[start + k] = c.code.toByte() }
            } else {
                val padded = text.padEnd(length / 2).take(length / 2)
                padded.forEachIndexed { k, c ->
                    out[start + 2 * k] = (c.code shr 8).toByte()
                    out[start + 2 * k + 1] = c.code.toByte()
                }
            }
            patched++
        }
        assertTrue(patched > 0, "the font has no name record $nameId to rewrite")
        return out
    }

    @Test
    fun aFontWithAProprietaryLicenceIsRefusedAndNothingElseIsRead() {
        val poppins = bytes("data/learn-faces/poppins/Poppins-Regular.ttf")
        val proprietary =
            withNameRecord(
                withNameRecord(
                    poppins,
                    13,
                    "This font is proprietary software of Example Foundry. It may not be modified or redistributed.",
                ),
                14,
                "http://example.invalid/eula",
            )
        val review = reviewFontFile("Proprietary.ttf", proprietary)
        assertIs<ImportReview.Refused>(review)
        assertEquals(LicenceKind.PROPRIETARY, review.audit.licence)
        assertEquals(ImportVerdict.REFUSED, review.audit.verdict)
        assertEquals(false, review.audit.canImport)
        assertTrue("Import stays off" in review.audit.verdictWords)
        // The same bytes, with the licence put back, are fine: the refusal is the licence, not the file.
        assertIs<ImportReview.Ready>(reviewFontFile("Poppins.ttf", poppins))
    }

    @Test
    fun aRefusedFontIsRefusedFromItsNameTableAloneSoNoOutlineIsDecoded() {
        val poppins = bytes("data/learn-faces/poppins/Poppins-Regular.ttf")
        val proprietary =
            withNameRecord(
                withNameRecord(
                    poppins,
                    13,
                    "This font is proprietary software of Example Foundry. It may not be modified or redistributed.",
                ),
                14,
                "http://example.invalid/eula",
            )

        fun withGlyfRuined(font: ByteArray): ByteArray {
            val glyf = checkNotNull(readSfntTableDirectory(font)["glyf"])
            return font.copyOf().also { ruined -> for (i in glyf.offset until glyf.offset + glyf.length) ruined[i] = 0x7F }
        }
        // Every outline in the file is junk. A refused font is still refused, because its outlines are never looked at...
        val refused = reviewFontFile("Proprietary.ttf", withGlyfRuined(proprietary))
        assertIs<ImportReview.Refused>(refused)
        assertEquals(LicenceKind.PROPRIETARY, refused.audit.licence)
        // ...while the same ruin under an open licence is unreadable, which shows the outlines are decoded for a font that may be imported.
        assertIs<ImportReview.Unreadable>(reviewFontFile("Poppins.ttf", withGlyfRuined(poppins)))
    }

    @Test
    fun aFontWithNoLicenceAtAllIsAllowedWithAWarning() {
        val poppins = bytes("data/learn-faces/poppins/Poppins-Regular.ttf")
        val unlicensed = withNameRecord(withNameRecord(poppins, 13, ""), 14, "")
        val review = reviewFontFile("Unlicensed.ttf", unlicensed)
        assertIs<ImportReview.Ready>(review)
        assertEquals(ImportVerdict.UNCERTAIN, review.candidate.audit.verdict)
        assertEquals(LicenceKind.NONE, review.candidate.audit.licence)
    }

    /** Glyph [gid]'s offset in the `glyf` table of [out], read through `head` and `loca`. */
    private fun glyphStart(
        out: ByteArray,
        gid: Int,
    ): Int {
        val directory = readSfntTableDirectory(out)

        fun u16(at: Int) = ((out[at].toInt() and 0xFF) shl 8) or (out[at + 1].toInt() and 0xFF)

        fun u32(at: Int) = (u16(at).toLong() shl 16 or u16(at + 2).toLong()).toInt()
        val long = u16(checkNotNull(directory["head"]).offset + 50) == 1
        val loca = checkNotNull(directory["loca"]).offset
        val glyf = checkNotNull(directory["glyf"]).offset
        return glyf + if (long) u32(loca + gid * 4) else u16(loca + gid * 2) * 2
    }

    private fun firstCompositeGid(out: ByteArray): Int {
        val glyphs = readSfntFont(out).numGlyphs
        return (0 until glyphs).first { gid ->
            val start = glyphStart(out, gid)
            val next = glyphStart(out, gid + 1)
            next > start && (((out[start].toInt() shl 8) or (out[start + 1].toInt() and 0xFF)).toShort() < 0)
        }
    }

    @Test
    fun aCompositeGlyphThatIncludesItselfIsUnreadableNotAnException() {
        val hyle = bytes("fonts/HyleDeco-Regular.ttf")
        val gid = firstCompositeGid(hyle)
        val cyclic = hyle.copyOf()
        val componentGlyphIndex = glyphStart(cyclic, gid) + 12
        cyclic[componentGlyphIndex] = (gid shr 8).toByte()
        cyclic[componentGlyphIndex + 1] = gid.toByte()
        val review = reviewFontFile("Cyclic.ttf", cyclic)
        assertIs<ImportReview.Unreadable>(review)
        assertTrue("couldn't read" in review.message && "Nothing was imported" in review.message, review.message)
    }

    @Test
    fun aComponentPointingPastTheLastGlyphIsUnreadableNotAnException() {
        val hyle = bytes("fonts/HyleDeco-Regular.ttf")
        val broken = hyle.copyOf()
        val at = glyphStart(broken, firstCompositeGid(hyle)) + 12
        broken[at] = 0xEA.toByte()
        broken[at + 1] = 0x60
        assertIs<ImportReview.Unreadable>(reviewFontFile("Broken.ttf", broken))
    }

    @Test
    fun aTruncatedFileIsUnreadable() {
        val half = bytes("data/learn-faces/poppins/Poppins-Regular.ttf").copyOf(20_000)
        assertIs<ImportReview.Unreadable>(reviewFontFile("Half.ttf", half))
        assertIs<ImportReview.Unreadable>(reviewFontFile("Empty.ttf", ByteArray(0)))
        assertIs<ImportReview.Unreadable>(reviewFontFile("Text.ttf", "this is not a font at all".repeat(5).encodeToByteArray()))
    }

    @Test
    fun cffOutlinesAreRefusedWithAMessageSayingTheyAreNotBuiltYet() {
        val review = reviewFontFile("Fake.otf", "OTTO".encodeToByteArray() + ByteArray(200))
        assertIs<ImportReview.Unreadable>(review)
        assertTrue("CFF" in review.message && "not built yet" in review.message, review.message)
    }

    @Test
    fun webFontsAndCollectionsAreRefusedWithAPlainMessage() {
        val woff = reviewFontFile("a.woff", "wOFF".encodeToByteArray() + ByteArray(100))
        assertIs<ImportReview.Unreadable>(woff)
        assertTrue("web font" in woff.message)
        val ttc = reviewFontFile("a.ttc", "ttcf".encodeToByteArray() + ByteArray(100))
        assertIs<ImportReview.Unreadable>(ttc)
        assertTrue("collection" in ttc.message)
    }

    // ---- Through the workspace, onto a real disk -----------------------------------------------------

    @Test
    fun anImportedFontIsWrittenAsUfoThatReadsBackAsTheSessionsProjectAndTheSfntsGlyphs() {
        val sfnt = readSfntFont(bytes("data/learn-faces/poppins/Poppins-Regular.ttf"))
        val candidate = ready("Poppins-Regular.ttf", "data/learn-faces/poppins/Poppins-Regular.ttf")
        val dir =
            kotlin.io.path
                .createTempDirectory("typewright-import-test")
                .toFile()
        try {
            val session =
                kotlinx.coroutines.runBlocking {
                    val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob())
                    val ws = DiskWorkspace.of(scope)
                    val outcome =
                        ProjectImporter(
                            ws,
                        ).create(
                            candidate,
                            com.asoc.typewright.project.ProjectLocation
                                .FileSystem(dir.path),
                            "Poppins",
                        )
                    assertIs<ImportOutcome.Imported>(outcome)
                    outcome.session.flush()
                    outcome.session
                }
            val ufoDir = File(dir, "Poppins/Poppins-Regular.ufo")
            assertTrue(ufoDir.isDirectory, "the UFO is on disk at ${ufoDir.path}")
            val onDisk =
                ufoDir
                    .walkTopDown()
                    .filter { it.isFile }
                    .associate { it.relativeTo(ufoDir).invariantSeparatorsPath to it.readText() }
            val read = readUfoProject(onDisk)
            val inSession =
                session.state.value.font.masters
                    .single()
                    .ufo
            assertEquals(inSession, read, "the UFO read back from disk equals the session's project")
            assertEquals(sfnt.allGlyphs().map { it.name }, read.glyphs.map { it.name })
            assertEquals(sfnt.allGlyphs().map { it.advanceWidth }, read.glyphs.map { it.advanceWidth })
            assertEquals(sfnt.allGlyphs().map { it.contours }, read.glyphs.map { it.contours })
            val unicodes = read.glyphs.flatMap { g -> g.unicodes.map { it to g.name } }.toMap()
            for ((codePoint, gid) in sfnt.cmap.bestMapping) {
                if (gid == 0) continue
                assertEquals(sfnt.glyphName(gid), unicodes[codePoint], "U+${codePoint.toString(16)}")
            }
            assertEquals(sfnt.head.unitsPerEm, read.fontInfo.unitsPerEm)
            assertEquals(sfnt.hhea.ascender, read.fontInfo.openTypeHheaAscender)
            assertEquals(sfnt.os2?.sxHeight, read.fontInfo.xHeight)
            assertNotNull(File(dir, "Poppins/typewright.json").takeIf { it.isFile })
        } finally {
            dir.deleteRecursively()
        }
    }
}
