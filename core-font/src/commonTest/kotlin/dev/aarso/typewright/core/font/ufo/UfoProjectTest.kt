package dev.aarso.typewright.core.font.ufo

import dev.aarso.typewright.core.geometry.Contour
import dev.aarso.typewright.core.geometry.ContourPoint
import dev.aarso.typewright.core.geometry.CurveFormat
import dev.aarso.typewright.core.geometry.Glyph
import dev.aarso.typewright.core.geometry.Guideline
import dev.aarso.typewright.core.geometry.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The task's required round-trip test: build synthetic [Glyph]s, write as a UFO project, read back, assert equality. */
class UfoProjectTest {
    private fun triangleCubic(): Contour =
        Contour(
            listOf(
                ContourPoint(Point(0, 0), true),
                ContourPoint(Point(0, 0), false),
                ContourPoint(Point(100, 0), false),
                ContourPoint(Point(100, 0), true),
                ContourPoint(Point(100, 0), false),
                ContourPoint(Point(50, 100), false),
                ContourPoint(Point(50, 100), true),
                ContourPoint(Point(50, 100), false),
                ContourPoint(Point(0, 0), false),
            ),
            CurveFormat.CUBIC,
        )

    private fun lensCubic(): Contour =
        Contour(
            listOf(
                ContourPoint(Point(0, 0), true),
                ContourPoint(Point(20, 10), false),
                ContourPoint(Point(40, 30), false),
                ContourPoint(Point(50, 50), true),
                ContourPoint(Point(40, 70), false),
                ContourPoint(Point(20, 90), false),
            ),
            CurveFormat.CUBIC,
        )

    @Test
    fun writesAndReadsBackTwoSyntheticGlyphsLosslessly() {
        val project =
            UfoProject(
                fontInfo =
                    UfoFontInfo(
                        familyName = "Hyle Deco Test",
                        styleName = "Regular",
                        unitsPerEm = 1000,
                        ascender = 800,
                        descender = -200,
                        xHeight = 500,
                        capHeight = 700,
                        versionMajor = 1,
                        versionMinor = 0,
                    ),
                glyphs =
                    listOf(
                        Glyph("A", 600, listOf(triangleCubic())),
                        Glyph("o", 550, listOf(lensCubic())),
                    ),
            )

        val files = writeUfoProject(project)
        val readBack = readUfoProject(files)
        assertEquals(project, readBack)
    }

    @Test
    fun writesEveryRequiredUfo3File() {
        val project = UfoProject(UfoFontInfo(), listOf(Glyph("A", 500, listOf(triangleCubic()))))
        val files = writeUfoProject(project)
        assertTrue(files.containsKey("metainfo.plist"))
        assertTrue(files.containsKey("fontinfo.plist"))
        assertTrue(files.containsKey("layercontents.plist"))
        assertTrue(files.containsKey("glyphs/contents.plist"))
        assertTrue(files.keys.any { it.startsWith("glyphs/") && it.endsWith(".glif") })
    }

    @Test
    fun metaInfoDeclaresFormatVersionThree() {
        val files = writeUfoProject(UfoProject(UfoFontInfo(), emptyList()))
        val metaInfo = parsePlistDict(files.getValue("metainfo.plist"))
        assertEquals(3L, metaInfo.longOrNull("formatVersion"))
    }

    @Test
    fun fontInfoRoundTripsXHeightAndCapHeightLikeAscenderAndDescender() {
        val files = writeUfoProject(UfoProject(UfoFontInfo(xHeight = 500, capHeight = 700), emptyList()))
        val fontInfo = parsePlistDict(files.getValue("fontinfo.plist"))
        assertEquals(500L, fontInfo.longOrNull("xHeight"))
        assertEquals(700L, fontInfo.longOrNull("capHeight"))
        assertEquals(UfoFontInfo(xHeight = 500, capHeight = 700), readUfoProject(files).fontInfo)
    }

    @Test
    fun fontInfoOmitsNullFields() {
        val files = writeUfoProject(UfoProject(UfoFontInfo(familyName = "Only Family"), emptyList()))
        val fontInfo = parsePlistDict(files.getValue("fontinfo.plist"))
        assertEquals("Only Family", fontInfo.stringOrNull("familyName"))
        assertEquals(null, fontInfo.get("unitsPerEm"))
        assertEquals(1, fontInfo.entries.size)
    }

    @Test
    fun writingIsByteStable() {
        val project = UfoProject(UfoFontInfo(familyName = "F"), listOf(Glyph("A", 500, listOf(triangleCubic()))))
        assertEquals(writeUfoProject(project), writeUfoProject(project))
    }

    @Test
    fun glyphNameToFileNameCollisionsAreResolvedAcrossTheWholeProject() {
        // "A" escapes to "A_"; a *different* glyph literally named "a_" also escapes to "a_" --
        // "A_.glif" and "a_.glif" are the same file on a case-insensitive filesystem, so the
        // second one written must be renamed rather than silently overwrite the first (see
        // userNameToFileName's own KDoc on why writeUfoProject keeps one running "used" set
        // across all glyphs instead of mapping each name independently).
        val project =
            UfoProject(
                UfoFontInfo(),
                listOf(
                    Glyph("A", 500, listOf(triangleCubic())),
                    Glyph("a_", 500, listOf(lensCubic())),
                ),
            )
        val files = writeUfoProject(project)
        val contents = parsePlistDict(files.getValue("glyphs/contents.plist"))
        val fileNameForA = contents.stringOrNull("A")
        val fileNameForALowerUnderscore = contents.stringOrNull("a_")
        assertEquals("A_.glif", fileNameForA)
        assertTrue(fileNameForALowerUnderscore != null && fileNameForALowerUnderscore != "a_.glif")
        assertTrue(files.containsKey("glyphs/$fileNameForA"))
        assertTrue(files.containsKey("glyphs/$fileNameForALowerUnderscore"))

        val readBack = readUfoProject(files)
        assertEquals(project.glyphs.toSet(), readBack.glyphs.toSet())
    }

    @Test
    fun readRejectsAProjectWithNoMetaInfo() {
        assertFailsWith<IllegalArgumentException> { readUfoProject(emptyMap()) }
    }

    @Test
    fun readRejectsAnUnsupportedFormatVersion() {
        val metaInfo = writePlist(PlistValue.PDict(listOf("creator" to PlistValue.PString("x"), "formatVersion" to PlistValue.PInteger(2))))
        assertFailsWith<IllegalArgumentException> { readUfoProject(mapOf("metainfo.plist" to metaInfo)) }
    }

    @Test
    fun readRejectsContentsReferencingAMissingGlifFile() {
        val files =
            mutableMapOf(
                "metainfo.plist" to
                    writePlist(
                        PlistValue.PDict(listOf("creator" to PlistValue.PString("x"), "formatVersion" to PlistValue.PInteger(3))),
                    ),
                "glyphs/contents.plist" to
                    writePlist(PlistValue.PDict(listOf("A" to PlistValue.PString("A_.glif")))),
            )
        assertFailsWith<IllegalArgumentException> { readUfoProject(files) }
    }

    @Test
    fun readFallsBackToTheGlyphsDirectoryWhenLayerContentsIsAbsent() {
        val project = UfoProject(UfoFontInfo(), listOf(Glyph("A", 500, listOf(triangleCubic()))))
        val files = writeUfoProject(project).toMutableMap()
        files.remove("layercontents.plist")
        assertEquals(project, readUfoProject(files))
    }

    @Test
    fun fontInfoRoundTripsGuidelinesOfAllThreeShapes() {
        val guidelines =
            listOf(
                Guideline(x = 500.0), // vertical
                Guideline(y = 0.0, name = "baseline"), // horizontal
                Guideline(x = 100.0, y = 200.0, angle = 12.5, name = "italic", color = "1,0,0,1", identifier = "g1"), // angled
            )
        val files = writeUfoProject(UfoProject(UfoFontInfo(guidelines = guidelines), emptyList()))
        val fontInfo = parsePlistDict(files.getValue("fontinfo.plist"))
        assertTrue(fontInfo.arrayOrNull("guidelines") != null)
        assertEquals(UfoFontInfo(guidelines = guidelines), readUfoProject(files).fontInfo)
    }

    @Test
    fun fontInfoOmitsTheGuidelinesKeyWhenNull() {
        val files = writeUfoProject(UfoProject(UfoFontInfo(guidelines = null), emptyList()))
        val fontInfo = parsePlistDict(files.getValue("fontinfo.plist"))
        assertEquals(null, fontInfo.get("guidelines"))
        assertEquals(null, readUfoProject(files).fontInfo.guidelines)
    }

    @Test
    fun fontInfoDistinguishesAnAbsentGuidelinesKeyFromAnEmptyOne() {
        val files = writeUfoProject(UfoProject(UfoFontInfo(guidelines = emptyList()), emptyList()))
        val fontInfo = parsePlistDict(files.getValue("fontinfo.plist"))
        assertTrue(fontInfo.arrayOrNull("guidelines") != null)
        assertEquals(emptyList(), readUfoProject(files).fontInfo.guidelines)
    }

    @Test
    fun writeUfoProjectOmitsGroupsKerningAndFeaturesFilesWhenTheProjectHasNone() {
        val files = writeUfoProject(UfoProject(UfoFontInfo(), emptyList()))
        assertEquals(false, files.containsKey("groups.plist"))
        assertEquals(false, files.containsKey("kerning.plist"))
        assertEquals(false, files.containsKey("features.fea"))
        assertEquals(UfoKerning(), readUfoProject(files).kerningInfo)
    }

    @Test
    fun writeUfoProjectRoundTripsGroupsKerningAndFeatures() {
        val kerningInfo =
            UfoKerning(
                groups =
                    mapOf(
                        "public.kern1.A" to listOf("A", "Aacute", "Acircumflex"),
                        "public.kern2.O" to listOf("O", "Odieresis"),
                        "Group1" to listOf("A", "A.alt"),
                    ),
                kerning =
                    mapOf(
                        "public.kern1.A" to mapOf("public.kern2.O" to 7.0, "V" to -25.0),
                        "A" to mapOf("V" to -18.5),
                    ),
                features = "# comment\nfeature kern {\n    pos A V -25;\n} kern;\n",
            )
        val project = UfoProject(UfoFontInfo(), listOf(Glyph("A", 500, listOf(triangleCubic()))), kerningInfo)
        val files = writeUfoProject(project)
        assertTrue(files.containsKey("groups.plist"))
        assertTrue(files.containsKey("kerning.plist"))
        assertEquals(kerningInfo.features, files["features.fea"])
        assertEquals(project, readUfoProject(files))
    }

    @Test
    fun readHonoursANonDefaultLayerDirectoryName() {
        val project = UfoProject(UfoFontInfo(), listOf(Glyph("A", 500, listOf(triangleCubic()))))
        val standard = writeUfoProject(project)
        val remapped =
            standard.entries
                .associate { (path, content) ->
                    if (path.startsWith("glyphs/")) "letters/" + path.removePrefix("glyphs/") to content else path to content
                }.toMutableMap()
        remapped["layercontents.plist"] =
            writePlist(
                PlistValue.PArray(listOf(PlistValue.PArray(listOf(PlistValue.PString("public.default"), PlistValue.PString("letters"))))),
            )
        assertEquals(project, readUfoProject(remapped))
    }
}
