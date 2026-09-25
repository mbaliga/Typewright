// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.font.ufo

import com.asoc.typewright.core.font.sfnt.HyleDecoRegularTtfBase64
import com.asoc.typewright.core.font.sfnt.readSfntFont
import com.asoc.typewright.core.font.sfnt.toUfoProject
import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.ContourPoint
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Guideline
import com.asoc.typewright.core.geometry.Point
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * JVM-only (this is `jvmTest`, not `commonTest`: `core-font` itself never touches a filesystem,
 * see CLAUDE.md law 2, but a *caller* — this test standing in for a platform module — does)
 * check that [writeUfoProject]'s path-to-content map, written out with plain `java.io.File`, is a
 * real, correctly laid out `.ufo` directory: every path lands where the UFO 3 spec expects it, with
 * `glyphs/` actually created as a subdirectory and every `.glif` file present on disk.
 *
 * It also leaves two real-font UFOs, TrueType through [toUfoProject], in
 * `core-font/build/ufo-crosscheck/` (Hyle Deco, all straight lines, and Poppins Regular, whose
 * curves are `qcurve` and whose `o` starts on an off-curve point), and a synthetic one carrying
 * foreign lib and font info values, so they can be checked with the reference implementation,
 * fontTools' `ufoLib`, outside this JVM suite.
 */
class UfoProjectDiskWriteTest {
    @Test
    fun writesARealUfoDirectoryLayoutToDisk() {
        val triangle =
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
        val project =
            UfoProject(
                fontInfo = UfoFontInfo(familyName = "Hyle Deco Test", unitsPerEm = 1000),
                glyphs = listOf(Glyph("A", 600, listOf(triangle)), Glyph("space", 300, emptyList())),
            )
        val files = writeUfoProject(project)

        val tempDir = createTempDirectory("typewright-ufo-write-test").toFile()
        val ufoDir = File(tempDir, "Test.ufo")
        try {
            writeFiles(files, ufoDir)

            assertTrue(File(ufoDir, "metainfo.plist").isFile)
            assertTrue(File(ufoDir, "fontinfo.plist").isFile)
            assertTrue(File(ufoDir, "lib.plist").isFile)
            assertTrue(File(ufoDir, "layercontents.plist").isFile)
            assertTrue(File(ufoDir, "glyphs").isDirectory)
            assertTrue(File(ufoDir, "glyphs/contents.plist").isFile)

            val glifFiles = File(ufoDir, "glyphs").listFiles { f -> f.extension == "glif" }.orEmpty()
            assertEquals(2, glifFiles.size)

            // Read every file back off disk (not from the in-memory map) into a fresh UfoProject,
            // exercising the same path a real caller (a platform module) would.
            assertEquals(project, readUfoProject(readFiles(ufoDir)))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun writesTheBridgedHyleDecoAndPoppinsUfosForTheUfoLibCrossCheck() {
        val outputRoot = File("build/ufo-crosscheck").absoluteFile
        val fonts =
            mapOf(
                "HyleDeco-Regular.ufo" to HyleDecoRegularTtfBase64.bytes,
                "Poppins-Regular.ufo" to File(repoRoot(), "data/learn-faces/poppins/Poppins-Regular.ttf").readBytes(),
            )
        for ((directoryName, ttf) in fonts) {
            val project = readSfntFont(ttf).toUfoProject()
            val ufoDir = File(outputRoot, directoryName)
            ufoDir.deleteRecursively()
            val files = writeUfoProject(project)
            writeFiles(files, ufoDir)

            val onDisk = readFiles(ufoDir)
            assertEquals(files, onDisk, "$directoryName: the files on disk are the files written")
            assertEquals(project, readUfoProject(onDisk), "$directoryName: reading the files on disk gives the project back")
        }
    }

    @Test
    fun writesASyntheticUfoWithForeignLibAndFontInfoValuesForTheUfoLibCrossCheck() {
        // What a UFO from another tool carries and Typewright must keep: <data> and <date> lib
        // values, fractional and huge reals, a glyph order naming glyphs not drawn yet, and a
        // fractional guideline, all written as fontTools writes them.
        val quadratic =
            Contour(
                listOf(
                    ContourPoint(Point(460, 0), false),
                    ContourPoint(Point(460, 300), false),
                    ContourPoint(Point(250, 300), true),
                    ContourPoint(Point(40, 300), false),
                    ContourPoint(Point(40, 0), false),
                    ContourPoint(Point(250, 0), true),
                ),
                CurveFormat.QUADRATIC,
            )
        val project =
            UfoProject(
                fontInfo =
                    UfoFontInfo(
                        familyName = "Synthetic",
                        styleName = "Regular",
                        unitsPerEm = 1000,
                        ascender = 750,
                        descender = -250,
                        guidelines = listOf(Guideline(x = 12.5, y = 1e-5, angle = 0.25, name = "fine")),
                        other =
                            listOf(
                                "italicAngle" to PlistValue.PReal(-12.5),
                                "postscriptBlueScale" to PlistValue.PReal(0.039625),
                                "xHeight" to PlistValue.PReal(500.5),
                            ),
                    ),
                glyphs =
                    listOf(
                        Glyph("o", 500, listOf(quadratic), unicodes = listOf(0x6F)),
                        Glyph("space", 250, emptyList(), guidelines = listOf(Guideline(y = 1.5e20)), unicodes = listOf(0x20, 0xA0)),
                    ),
                lib =
                    UfoLib(
                        lineContourFormat = CurveFormat.QUADRATIC,
                        other =
                            PlistValue.PDict(
                                listOf(
                                    "com.example.blob" to PlistValue.PData("AAECAw=="),
                                    "com.example.saved" to PlistValue.PDate("2026-09-25T12:00:00Z"),
                                    "com.example.tiny" to PlistValue.PReal(1e-7),
                                    "public.openTypeMeta" to
                                        PlistValue.PDict(listOf("appl" to PlistValue.PData("AAEC"), "dlng" to PlistValue.PString("Latn"))),
                                ),
                            ),
                        glyphOrderExtras = listOf("a", "b"),
                    ),
            )
        val ufoDir = File(File("build/ufo-crosscheck").absoluteFile, "Synthetic-Regular.ufo")
        ufoDir.deleteRecursively()
        val files = writeUfoProject(project)
        writeFiles(files, ufoDir)
        val onDisk = readFiles(ufoDir)
        assertEquals(files, onDisk)
        assertEquals(project, readUfoProject(onDisk))
        assertEquals(files, writeUfoProject(readUfoProject(onDisk), readGlyphFileNames(onDisk)))
    }

    private fun writeFiles(
        files: Map<String, String>,
        ufoDir: File,
    ) {
        for ((path, content) in files) {
            val target = File(ufoDir, path)
            target.parentFile.mkdirs()
            target.writeText(content, Charsets.UTF_8)
        }
    }

    private fun readFiles(ufoDir: File): Map<String, String> =
        ufoDir
            .walkTopDown()
            .filter { it.isFile }
            .associate { it.relativeTo(ufoDir).path.replace(File.separatorChar, '/') to it.readText(Charsets.UTF_8) }

    /** The repository root: the nearest directory, from the test's working directory up, holding `settings.gradle.kts`. */
    private fun repoRoot(): File =
        generateSequence(File("").absoluteFile) { it.parentFile }.firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("no settings.gradle.kts above ${File("").absolutePath}; this test runs from inside the repository")
}
