// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.core.font.ufo

import dev.aarso.typewright.core.geometry.Contour
import dev.aarso.typewright.core.geometry.ContourPoint
import dev.aarso.typewright.core.geometry.CurveFormat
import dev.aarso.typewright.core.geometry.Glyph
import dev.aarso.typewright.core.geometry.Point
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * JVM-only (this is `jvmTest`, not `commonTest`: `core-font` itself never touches a filesystem,
 * see CLAUDE.md law 2, but a *caller* — this test standing in for a future platform module — does)
 * check that [writeUfoProject]'s path-to-content map, written out with plain `java.io.File`, is a
 * real, correctly laid out `.ufo` directory: every path lands where the UFO 3 spec expects it, with
 * `glyphs/` actually created as a subdirectory and every `.glif` file present on disk.
 *
 * This does not by itself prove a *real* UFO reader (RoboFont, FontForge, `fontTools.ufoLib`)
 * accepts the files — that was checked manually once, outside the automated suite, with
 * `fontTools.ufoLib.glifLib.readGlyphFromString` against this exact writer's output and
 * `plistlib.loads` against its plists (see docs/ARCHITECTURE_REVIEW.md section 3 `:core-font`
 * risk 1, "A lossless UFO 3 round-trip") — but it is real, automated, JVM-filesystem evidence that
 * belongs in `jvmTest` rather than duplicating [UfoProjectTest]'s pure in-memory round trip.
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
            for ((path, content) in files) {
                val target = File(ufoDir, path)
                target.parentFile.mkdirs()
                target.writeText(content, Charsets.UTF_8)
            }

            assertTrue(File(ufoDir, "metainfo.plist").isFile)
            assertTrue(File(ufoDir, "fontinfo.plist").isFile)
            assertTrue(File(ufoDir, "layercontents.plist").isFile)
            assertTrue(File(ufoDir, "glyphs").isDirectory)
            assertTrue(File(ufoDir, "glyphs/contents.plist").isFile)

            val glifFiles = File(ufoDir, "glyphs").listFiles { f -> f.extension == "glif" }.orEmpty()
            assertEquals(2, glifFiles.size)

            // Read every file back off disk (not from the in-memory map) into a fresh UfoProject,
            // exercising the same path a real caller (a platform module) would.
            val rereadFiles =
                ufoDir
                    .walkTopDown()
                    .filter { it.isFile }
                    .associate { it.relativeTo(ufoDir).path.replace(File.separatorChar, '/') to it.readText(Charsets.UTF_8) }
            assertEquals(project, readUfoProject(rereadFiles))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
