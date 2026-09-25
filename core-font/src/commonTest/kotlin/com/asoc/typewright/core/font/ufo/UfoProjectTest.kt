// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.font.ufo

import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.ContourPoint
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.GlyphCount
import com.asoc.typewright.core.geometry.Guideline
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.count
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
        assertEquals(mapOf("A" to "A_.glif"), readGlyphFileNames(remapped))
    }

    // --- Curve formats across a whole project ---------------------------------------------------

    private fun on(
        x: Int,
        y: Int,
    ) = ContourPoint(Point(x, y), onCurve = true)

    private fun off(
        x: Int,
        y: Int,
    ) = ContourPoint(Point(x, y), onCurve = false)

    /** A 12-point, straight-sided H as TrueType holds it: the fitted target for Hyle Deco's H (12 on · 0 off). */
    private fun quadraticH(): Glyph =
        Glyph(
            "H",
            600,
            listOf(
                Contour(
                    listOf(
                        on(0, 0),
                        on(0, 700),
                        on(100, 700),
                        on(100, 400),
                        on(400, 400),
                        on(400, 700),
                        on(500, 700),
                        on(500, 0),
                        on(400, 0),
                        on(400, 300),
                        on(100, 300),
                        on(100, 0),
                    ),
                    CurveFormat.QUADRATIC,
                ),
            ),
            unicodes = listOf(0x48),
        )

    private fun quadraticO(): Glyph =
        Glyph(
            "o",
            500,
            listOf(
                Contour(
                    listOf(on(250, 0), off(460, 0), off(460, 300), on(250, 300), off(40, 300), off(40, 0)),
                    CurveFormat.QUADRATIC,
                ),
            ),
            unicodes = listOf(0x6F),
        )

    private fun quadraticProject(): UfoProject =
        UfoProject(
            UfoFontInfo(familyName = "Quadratic", unitsPerEm = 1000),
            listOf(
                Glyph(".notdef", 500, emptyList()),
                quadraticH(),
                quadraticO(),
                Glyph("space", 250, emptyList(), unicodes = listOf(0x20, 0xA0)),
            ),
            lib = UfoLib(lineContourFormat = CurveFormat.QUADRATIC),
        )

    @Test
    fun aCubicProjectRoundTripsAndWritesNoFormatKeyAnywhere() {
        val project =
            UfoProject(
                UfoFontInfo(familyName = "Cubic"),
                listOf(Glyph("A", 600, listOf(triangleCubic()), unicodes = listOf(0x41)), Glyph("o", 550, listOf(lensCubic()))),
            )
        val files = writeUfoProject(project)
        assertEquals(project, readUfoProject(files))
        assertTrue(files.values.none { it.contains(LINE_CONTOUR_FORMAT_LIB_KEY) })
    }

    @Test
    fun aQuadraticProjectRoundTripsWithTheFontKeyOnlyInLibPlist() {
        val project = quadraticProject()
        val files = writeUfoProject(project)
        assertEquals(project, readUfoProject(files))
        assertEquals("quadratic", parsePlistDict(files.getValue("lib.plist")).stringOrNull(LINE_CONTOUR_FORMAT_LIB_KEY))
        assertTrue(files.filterKeys { it.endsWith(".glif") }.values.none { it.contains("<lib>") })
    }

    @Test
    fun hStaysTwelveOnZeroOffAcrossARoundTripInAQuadraticProject() {
        val read = readUfoProject(writeUfoProject(quadraticProject()))
        val h = read.glyphs.single { it.name == "H" }
        assertEquals(CurveFormat.QUADRATIC, h.contours.single().format)
        assertEquals(GlyphCount(onCurveEquivalent = 12, offCurve = 0, contourCount = 1), h.count())
    }

    @Test
    fun withoutTheFontKeyAStraightQuadraticGlyphWouldReadBackCubic() {
        // Why the key exists: the same files, minus lib.plist's key, read H as cubic, 12 on · 24 off.
        val files = writeUfoProject(quadraticProject()).toMutableMap()
        files["lib.plist"] = writePlist(PlistValue.PDict(emptyList()))
        val h = readUfoProject(files).glyphs.single { it.name == "H" }
        assertEquals(GlyphCount(onCurveEquivalent = 12, offCurve = 24, contourCount = 1), h.count())
    }

    @Test
    fun aMixedProjectWritesTheGlyphKeyOnlyForTheStraightGlyphOutsideTheDefault() {
        // A quadratic project in which H has been converted to cubic: H's .glif needs the key, nothing else does.
        val quadraticPoints = quadraticH().contours.single().points
        val cubicPoints =
            quadraticPoints.indices.flatMap { i ->
                val start = quadraticPoints[i]
                val end = quadraticPoints[(i + 1) % quadraticPoints.size]
                listOf(start, start.copy(onCurve = false), end.copy(onCurve = false))
            }
        val cubicH = quadraticH().copy(contours = listOf(Contour(cubicPoints, CurveFormat.CUBIC)))
        val project = quadraticProject().let { it.copy(glyphs = it.glyphs.map { g -> if (g.name == "H") cubicH else g }) }
        val files = writeUfoProject(project)
        assertEquals(project, readUfoProject(files))
        val glifsWithKey = files.filter { (path, content) -> path.endsWith(".glif") && content.contains(LINE_CONTOUR_FORMAT_LIB_KEY) }.keys
        assertEquals(setOf("glyphs/H_.glif"), glifsWithKey)
        assertTrue(files.getValue("glyphs/H_.glif").contains("<string>cubic</string>"))
    }

    // --- lib.plist and glyph order ----------------------------------------------------------------

    @Test
    fun libPlistIsAlwaysWrittenWithTheGlyphOrder() {
        val files = writeUfoProject(UfoProject(UfoFontInfo(), listOf(Glyph("b", 500, emptyList()), Glyph("a", 500, emptyList()))))
        val lib = parsePlistDict(files.getValue("lib.plist"))
        assertEquals(
            PlistValue.PArray(listOf(PlistValue.PString("b"), PlistValue.PString("a"))),
            lib["public.glyphOrder"],
        )
        assertEquals(listOf("public.glyphOrder"), lib.entries.map { it.first })

        val empty = parsePlistDict(writeUfoProject(UfoProject(UfoFontInfo(), emptyList())).getValue("lib.plist"))
        assertEquals(PlistValue.PArray(emptyList()), empty["public.glyphOrder"])
    }

    @Test
    fun readOrdersGlyphsByTheGlyphOrderRatherThanContentsPlist() {
        val project = UfoProject(UfoFontInfo(), listOf("c", "a", "b").map { Glyph(it, 500, emptyList()) })
        val files = writeUfoProject(project).toMutableMap()
        // contents.plist lists the glyphs alphabetically, as fontTools writes it; the lib's order must win.
        files["glyphs/contents.plist"] =
            writePlist(PlistValue.PDict(listOf("a", "b", "c").map { it to PlistValue.PString("$it.glif") }))
        assertEquals(listOf("c", "a", "b"), readUfoProject(files).glyphs.map { it.name })
    }

    @Test
    fun glyphsTheOrderDoesNotListFollowInContentsOrderAndNamesWithNoGlyphAreKeptAsExtras() {
        val project = UfoProject(UfoFontInfo(), listOf("a", "b", "c", "d").map { Glyph(it, 500, emptyList()) })
        val files = writeUfoProject(project).toMutableMap()
        files["lib.plist"] =
            writePlist(
                PlistValue.PDict(
                    listOf("public.glyphOrder" to PlistValue.PArray(listOf("c", "gone", "a", "c").map { PlistValue.PString(it) })),
                ),
            )
        val read = readUfoProject(files)
        assertEquals(listOf("c", "a", "b", "d"), read.glyphs.map { it.name })
        assertEquals(listOf("gone"), read.lib.glyphOrderExtras)
    }

    @Test
    fun glyphOrderNamesWithNoGlyphSurviveASaveAfterTheGlyphs() {
        // A designer's planned glyph set: public.glyphOrder lists glyphs not drawn yet, some between drawn ones.
        val files = writeUfoProject(UfoProject(UfoFontInfo(), listOf("A", "Z").map { Glyph(it, 500, emptyList()) })).toMutableMap()
        files["lib.plist"] =
            writePlist(
                PlistValue.PDict(
                    listOf(
                        "public.glyphOrder" to PlistValue.PArray(listOf("C", "A", "B", "C").map { PlistValue.PString(it) }),
                    ),
                ),
            )
        val read = readUfoProject(files)
        assertEquals(listOf("A", "Z"), read.glyphs.map { it.name })
        assertEquals(listOf("C", "B"), read.lib.glyphOrderExtras)

        fun writtenOrder(files: Map<String, String>) =
            (parsePlistDict(files.getValue("lib.plist"))["public.glyphOrder"] as PlistValue.PArray).items.map {
                (it as PlistValue.PString).value
            }

        val saved = writeUfoProject(read, readGlyphFileNames(files))
        assertEquals(listOf("A", "Z", "C", "B"), writtenOrder(saved))
        assertEquals(read, readUfoProject(saved))
        assertEquals(saved, writeUfoProject(readUfoProject(saved), readGlyphFileNames(saved)))

        // Drawing B: the glyph takes its own place, and the name is written once.
        val withB = read.copy(glyphs = read.glyphs + Glyph("B", 500, emptyList()))
        assertEquals(listOf("A", "Z", "B", "C"), writtenOrder(writeUfoProject(withB)))
        val withBDropped = withB.copy(lib = withB.lib.copy(glyphOrderExtras = listOf("C")))
        assertEquals(writeUfoProject(withB), writeUfoProject(withBDropped))
        assertEquals(withBDropped, readUfoProject(writeUfoProject(withBDropped)))
    }

    @Test
    fun aProjectWithNoLibPlistReadsWithTheDefaultLib() {
        val project = UfoProject(UfoFontInfo(), listOf(Glyph("A", 500, listOf(triangleCubic()))))
        val files = writeUfoProject(project).toMutableMap()
        files.remove("lib.plist")
        assertEquals(project, readUfoProject(files))
    }

    @Test
    fun unknownLibKeysPassThroughInAscendingOrder() {
        val other =
            PlistValue.PDict(
                listOf(
                    "com.example.nested" to PlistValue.PDict(listOf("z" to PlistValue.PInteger(1), "a" to PlistValue.PBoolean(true))),
                    "public.postscriptNames" to PlistValue.PDict(listOf("A" to PlistValue.PString("A"))),
                    "public.skipExportGlyphs" to PlistValue.PArray(listOf(PlistValue.PString("A.alt"))),
                ),
            )
        val project = UfoProject(UfoFontInfo(), listOf(Glyph("A", 500, emptyList())), lib = UfoLib(CurveFormat.QUADRATIC, other))
        val files = writeUfoProject(project)
        assertEquals(project, readUfoProject(files))
        assertEquals(
            listOf(
                "com.asoc.typewright.lineContourFormat",
                "com.example.nested",
                "public.glyphOrder",
                "public.postscriptNames",
                "public.skipExportGlyphs",
            ),
            parsePlistDict(files.getValue("lib.plist")).entries.map { it.first },
        )
    }

    @Test
    fun aForeignLibPlistInAnyKeyOrderReadsBackSortedAndThenRoundTrips() {
        val project = UfoProject(UfoFontInfo(), listOf(Glyph("A", 500, emptyList())))
        val files = writeUfoProject(project).toMutableMap()
        files["lib.plist"] =
            writePlist(
                PlistValue.PDict(
                    listOf(
                        "public.glyphOrder" to PlistValue.PArray(listOf(PlistValue.PString("A"))),
                        "org.b" to PlistValue.PString("b"),
                        "com.asoc.typewright.lineContourFormat" to PlistValue.PString("cubic"),
                        "org.a" to PlistValue.PString("a"),
                    ),
                ),
            )
        val read = readUfoProject(files)
        val sortedOther = PlistValue.PDict(listOf("org.a" to PlistValue.PString("a"), "org.b" to PlistValue.PString("b")))
        assertEquals(UfoLib(CurveFormat.CUBIC, sortedOther), read.lib)
        assertEquals(read, readUfoProject(writeUfoProject(read)))
    }

    @Test
    fun aLibPlistHoldingDataAndDateValuesOpensAndSurvivesASave() {
        val files = writeUfoProject(UfoProject(UfoFontInfo(), listOf(Glyph("A", 500, emptyList())))).toMutableMap()
        files["lib.plist"] =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
              <key>public.openTypeMeta</key>
              <dict>
                <key>appl</key>
                <data>
                AAEC
                </data>
                <key>dlng</key>
                <string>Latn</string>
              </dict>
              <key>com.example.blob</key>
              <data>AAECAw==</data>
              <key>com.example.saved</key>
              <date>2026-09-25T12:00:00Z</date>
              <key>public.glyphOrder</key>
              <array>
                <string>A</string>
              </array>
            </dict>
            </plist>
            """.trimIndent()
        val read = readUfoProject(files)
        assertEquals(
            PlistValue.PDict(
                listOf(
                    "com.example.blob" to PlistValue.PData("AAECAw=="),
                    "com.example.saved" to PlistValue.PDate("2026-09-25T12:00:00Z"),
                    "public.openTypeMeta" to
                        PlistValue.PDict(listOf("appl" to PlistValue.PData("AAEC"), "dlng" to PlistValue.PString("Latn"))),
                ),
            ),
            read.lib.other,
        )
        val saved = writeUfoProject(read, readGlyphFileNames(files))
        val lib = saved.getValue("lib.plist")
        assertTrue("<data>AAECAw==</data>" in lib && "<data>AAEC</data>" in lib, lib)
        assertTrue("<date>2026-09-25T12:00:00Z</date>" in lib, lib)
        assertEquals(read, readUfoProject(saved))
        assertEquals(saved, writeUfoProject(readUfoProject(saved), readGlyphFileNames(saved)))
    }

    @Test
    fun rejectsAMalformedGlyphOrderOrLineContourFormat() {
        val files = writeUfoProject(UfoProject(UfoFontInfo(), emptyList())).toMutableMap()
        files["lib.plist"] = writePlist(PlistValue.PDict(listOf("public.glyphOrder" to PlistValue.PString("A"))))
        assertFailsWith<IllegalArgumentException> { readUfoProject(files) }
        files["lib.plist"] = writePlist(PlistValue.PDict(listOf(LINE_CONTOUR_FORMAT_LIB_KEY to PlistValue.PString("cubic-ish"))))
        assertFailsWith<IllegalArgumentException> { readUfoProject(files) }
    }

    @Test
    fun ufoLibTakesOtherInAnyOrderAndIsEqualByTheFileItWrites() {
        val messy =
            UfoLib(
                other =
                    PlistValue.PDict(
                        listOf(
                            "org.b" to PlistValue.PString("b"),
                            "org.a" to PlistValue.PString("first"),
                            "public.glyphOrder" to PlistValue.PArray(listOf(PlistValue.PString("stale"))),
                            LINE_CONTOUR_FORMAT_LIB_KEY to PlistValue.PString("quadratic"),
                            "org.a" to PlistValue.PString("a"),
                        ),
                    ),
                glyphOrderExtras = listOf("x", "x"),
            )
        val canonical =
            UfoLib(
                other = PlistValue.PDict(listOf("org.a" to PlistValue.PString("a"), "org.b" to PlistValue.PString("b"))),
                glyphOrderExtras = listOf("x"),
            )
        assertEquals(canonical, messy)
        assertEquals(canonical.hashCode(), messy.hashCode())
        assertTrue(canonical != canonical.copy(lineContourFormat = CurveFormat.QUADRATIC))

        val project = UfoProject(UfoFontInfo(), listOf(Glyph("A", 500, emptyList())), lib = messy)
        val files = writeUfoProject(project)
        // The properties win over other's copies of their keys; a repeated key's last entry is written.
        assertEquals(
            PlistValue.PDict(
                listOf(
                    "org.a" to PlistValue.PString("a"),
                    "org.b" to PlistValue.PString("b"),
                    "public.glyphOrder" to PlistValue.PArray(listOf(PlistValue.PString("A"), PlistValue.PString("x"))),
                ),
            ),
            parsePlistDict(files.getValue("lib.plist")),
        )
        val read = readUfoProject(files)
        assertEquals(project, read)
        assertEquals(canonical.other, read.lib.other)
        assertEquals(files, writeUfoProject(read))
    }

    // --- fontinfo.plist -------------------------------------------------------------------------

    private fun fullFontInfo(): UfoFontInfo =
        UfoFontInfo(
            familyName = "Hyle Deco",
            styleName = "Regular",
            unitsPerEm = 1000,
            ascender = 800,
            descender = -200,
            xHeight = 500,
            capHeight = 700,
            versionMajor = 1,
            versionMinor = 2,
            guidelines = listOf(Guideline(y = 0.0, name = "baseline")),
            copyright = "Copyright 2026 A System of Cells",
            trademark = "Hyle Deco is a trademark of A System of Cells",
            openTypeNameDesigner = "Madhav",
            openTypeNameDesignerURL = "https://example.com/designer",
            openTypeNameManufacturer = "A System of Cells",
            openTypeNameManufacturerURL = "https://example.com",
            openTypeNameLicense = "This Font Software is licensed under the SIL Open Font License, Version 1.1.",
            openTypeNameLicenseURL = "https://openfontlicense.org",
            openTypeNamePreferredFamilyName = "Hyle Deco",
            openTypeNamePreferredSubfamilyName = "Regular",
            postscriptFontName = "HyleDeco-Regular",
            openTypeOS2VendorID = "ASOC",
            openTypeHheaAscender = 984,
            openTypeHheaDescender = -292,
            openTypeHheaLineGap = 0,
            openTypeOS2TypoAscender = 800,
            openTypeOS2TypoDescender = -200,
            openTypeOS2TypoLineGap = 276,
            openTypeOS2WinAscent = 984,
            openTypeOS2WinDescent = 292,
            other =
                listOf(
                    "openTypeOS2Selection" to PlistValue.PArray(listOf(PlistValue.PInteger(7))),
                    "postscriptBlueValues" to PlistValue.PArray(listOf(PlistValue.PInteger(-10), PlistValue.PInteger(0))),
                    "styleMapStyleName" to PlistValue.PString("regular"),
                ),
        )

    @Test
    fun everyFontInfoFieldRoundTripsUnderItsExactUfo3KeyInAscendingOrder() {
        val info = fullFontInfo()
        val files = writeUfoProject(UfoProject(info, emptyList()))
        assertEquals(info, readUfoProject(files).fontInfo)
        val keys = parsePlistDict(files.getValue("fontinfo.plist")).entries.map { it.first }
        assertEquals(keys.sorted(), keys)
        assertEquals(
            listOf(
                "ascender",
                "capHeight",
                "copyright",
                "descender",
                "familyName",
                "guidelines",
                "openTypeHheaAscender",
                "openTypeHheaDescender",
                "openTypeHheaLineGap",
                "openTypeNameDesigner",
                "openTypeNameDesignerURL",
                "openTypeNameLicense",
                "openTypeNameLicenseURL",
                "openTypeNameManufacturer",
                "openTypeNameManufacturerURL",
                "openTypeNamePreferredFamilyName",
                "openTypeNamePreferredSubfamilyName",
                "openTypeOS2Selection",
                "openTypeOS2TypoAscender",
                "openTypeOS2TypoDescender",
                "openTypeOS2TypoLineGap",
                "openTypeOS2VendorID",
                "openTypeOS2WinAscent",
                "openTypeOS2WinDescent",
                "postscriptBlueValues",
                "postscriptFontName",
                "styleMapStyleName",
                "styleName",
                "trademark",
                "unitsPerEm",
                "versionMajor",
                "versionMinor",
                "xHeight",
            ),
            keys,
        )
    }

    @Test
    fun unknownFontInfoKeysFromAForeignFileAreKeptSortedAndThenRoundTrip() {
        val files = writeUfoProject(UfoProject(UfoFontInfo(), emptyList())).toMutableMap()
        files["fontinfo.plist"] =
            writePlist(
                PlistValue.PDict(
                    listOf(
                        "styleMapFamilyName" to PlistValue.PString("Foreign"),
                        "familyName" to PlistValue.PString("Foreign"),
                        "italicAngle" to PlistValue.PReal(-12.5),
                    ),
                ),
            )
        val read = readUfoProject(files)
        assertEquals("Foreign", read.fontInfo.familyName)
        assertEquals(
            listOf("italicAngle" to PlistValue.PReal(-12.5), "styleMapFamilyName" to PlistValue.PString("Foreign")),
            read.fontInfo.other,
        )
        assertEquals(read, readUfoProject(writeUfoProject(read)))
    }

    @Test
    fun aModelledKeyWithAValueItsPropertyCannotHoldIsKeptInOther() {
        val files = writeUfoProject(UfoProject(UfoFontInfo(), emptyList())).toMutableMap()
        files["fontinfo.plist"] =
            writePlist(
                PlistValue.PDict(
                    listOf(
                        "ascender" to PlistValue.PReal(750.5),
                        "capHeight" to PlistValue.PString("700"),
                        "unitsPerEm" to PlistValue.PReal(1000.0),
                        "xHeight" to PlistValue.PInteger(500),
                    ),
                ),
            )
        val info = readUfoProject(files).fontInfo
        assertEquals(null, info.ascender)
        assertEquals(null, info.capHeight)
        assertEquals(1000, info.unitsPerEm) // a whole-number <real> is the integer it holds
        assertEquals(500, info.xHeight)
        assertEquals(listOf("ascender" to PlistValue.PReal(750.5), "capHeight" to PlistValue.PString("700")), info.other)
        val written = writeUfoProject(UfoProject(info, emptyList()))
        assertEquals(PlistValue.PInteger(1000), parsePlistDict(written.getValue("fontinfo.plist"))["unitsPerEm"])
        assertEquals(info, readUfoProject(written).fontInfo)
    }

    @Test
    fun settingAPropertyOverThePassedThroughValueOfItsKeyWritesTheProperty() {
        val files = writeUfoProject(UfoProject(UfoFontInfo(), emptyList())).toMutableMap()
        files["fontinfo.plist"] = writePlist(PlistValue.PDict(listOf("ascender" to PlistValue.PReal(750.5))))
        val read = readUfoProject(files)

        // What a font info editor does: copy with the field set. It must not throw.
        val edited = read.copy(fontInfo = read.fontInfo.copy(ascender = 750))
        val saved = writeUfoProject(edited)
        assertEquals(
            PlistValue.PDict(listOf("ascender" to PlistValue.PInteger(750))),
            parsePlistDict(saved.getValue("fontinfo.plist")),
        )
        val reread = readUfoProject(saved)
        assertEquals(750, reread.fontInfo.ascender)
        assertEquals(emptyList(), reread.fontInfo.other)
        assertEquals(edited, reread)
        assertEquals(saved, writeUfoProject(reread))

        // Clearing the property again writes the value the file had.
        val cleared = edited.fontInfo.copy(ascender = null)
        assertEquals(read.fontInfo, cleared)
        assertEquals(files.getValue("fontinfo.plist"), writeUfoProject(UfoProject(cleared, emptyList())).getValue("fontinfo.plist"))
    }

    @Test
    fun ufoFontInfoTakesOtherInAnyOrderButNotAValueAPropertyCanHold() {
        assertFailsWith<IllegalArgumentException> { UfoFontInfo(other = listOf("familyName" to PlistValue.PString("F"))) }
        assertFailsWith<IllegalArgumentException> { UfoFontInfo(other = listOf("ascender" to PlistValue.PInteger(800))) }
        assertFailsWith<IllegalArgumentException> { UfoFontInfo(other = listOf("unitsPerEm" to PlistValue.PReal(1000.0))) }
        assertFailsWith<IllegalArgumentException> { UfoFontInfo(other = listOf("guidelines" to PlistValue.PArray(emptyList()))) }

        // Order, repeats and entries a set property overrides do not change what is written, so not equality either.
        val messy =
            UfoFontInfo(
                ascender = 800,
                other =
                    listOf(
                        "b" to PlistValue.PString("b"),
                        "ascender" to PlistValue.PReal(800.5),
                        "a" to PlistValue.PString("first"),
                        "a" to PlistValue.PString("a"),
                    ),
            )
        val canonical = UfoFontInfo(ascender = 800, other = listOf("a" to PlistValue.PString("a"), "b" to PlistValue.PString("b")))
        assertEquals(canonical, messy)
        assertEquals(canonical.hashCode(), messy.hashCode())
        assertTrue(canonical != canonical.copy(ascender = 801))
        assertTrue(canonical != canonical.copy(other = listOf("a" to PlistValue.PString("a"))))
        val files = writeUfoProject(UfoProject(messy, emptyList()))
        assertEquals(canonical, readUfoProject(files).fontInfo)
        assertEquals(canonical.other, readUfoProject(files).fontInfo.other)
        assertEquals(files, writeUfoProject(readUfoProject(files)))
    }

    @Test
    fun fontInfoAndLibRealsAreWrittenTheSameOnEveryPlatform() {
        val project =
            UfoProject(
                UfoFontInfo(
                    guidelines = listOf(Guideline(x = 1e-5, y = 12345678.9, angle = 0.5)),
                    other =
                        listOf(
                            "italicAngle" to PlistValue.PReal(-12.5),
                            "postscriptBlueScale" to PlistValue.PReal(0.039625),
                            "postscriptBlueShift" to PlistValue.PReal(1e7),
                        ),
                ),
                emptyList(),
                lib =
                    UfoLib(
                        other = PlistValue.PDict(listOf("org.tiny" to PlistValue.PReal(1e-7), "org.huge" to PlistValue.PReal(1.5e20))),
                    ),
            )
        val files = writeUfoProject(project)
        val fontInfo = files.getValue("fontinfo.plist")
        val expected = listOf("1e-05", "12345678.9", "0.5", "-12.5", "0.039625", "10000000.0")
        for (text in expected) assertTrue("<real>$text</real>" in fontInfo, "<real>$text</real> in\n$fontInfo")
        val lib = files.getValue("lib.plist")
        assertTrue("<real>1e-07</real>" in lib && "<real>1.5e+20</real>" in lib, lib)
        assertEquals(project, readUfoProject(files))
    }

    // --- Glyph file names -----------------------------------------------------------------------

    @Test
    fun readGlyphFileNamesReturnsTheContentsPlistMapInFileOrder() {
        val files =
            writeUfoProject(UfoProject(UfoFontInfo(), listOf(Glyph("b", 500, emptyList()), Glyph("A", 500, emptyList()))))
        val names = readGlyphFileNames(files)
        assertEquals(mapOf("b" to "b.glif", "A" to "A_.glif"), names)
        assertEquals(listOf("b", "A"), names.keys.toList())
    }

    @Test
    fun readGlyphFileNamesRejectsAMissingContentsPlist() {
        assertFailsWith<IllegalArgumentException> { readGlyphFileNames(emptyMap()) }
    }

    @Test
    fun hintedFileNamesAreKeptAcrossAReadAndWrite() {
        // A foreign UFO whose file names are not what userNameToFileName would make.
        val project = UfoProject(UfoFontInfo(), listOf(Glyph("A", 500, listOf(triangleCubic())), Glyph("a", 500, emptyList())))
        val written = writeUfoProject(project, fileNameHints = mapOf("A" to "cap-a.glif", "a" to "small a.glif"))
        assertEquals(mapOf("A" to "cap-a.glif", "a" to "small a.glif"), readGlyphFileNames(written))
        assertTrue(written.containsKey("glyphs/cap-a.glif") && written.containsKey("glyphs/small a.glif"))

        val read = readUfoProject(written)
        assertEquals(project, read)
        assertEquals(written, writeUfoProject(read, fileNameHints = readGlyphFileNames(written)))
    }

    @Test
    fun aNewGlyphNeverTakesTheFileNameAnExistingGlyphKeeps() {
        // "a_" would be named "a_.glif", which on a case-insensitive filesystem is A's kept "A_.glif".
        val project = UfoProject(UfoFontInfo(), listOf(Glyph("a_", 500, emptyList()), Glyph("A", 500, emptyList())))
        val names = readGlyphFileNames(writeUfoProject(project, fileNameHints = mapOf("A" to "A_.glif")))
        assertEquals("A_.glif", names["A"])
        assertTrue(names.getValue("a_").lowercase() != "a_.glif")
    }

    @Test
    fun aHintThatIsNotAPlainGlifFileNameOrClashesIsIgnored() {
        val project = UfoProject(UfoFontInfo(), listOf("A", "B", "C", "D").map { Glyph(it, 500, emptyList()) })
        val hints = mapOf("A" to "../escape.glif", "B" to "b.txt", "C" to "same.glif", "D" to "SAME.glif")
        val names = readGlyphFileNames(writeUfoProject(project, fileNameHints = hints))
        assertEquals(mapOf("A" to "A_.glif", "B" to "B_.glif", "C" to "same.glif", "D" to "D_.glif"), names)
    }
}
