// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.compile

import com.asoc.typewright.core.font.sfnt.readSfntFont
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.outlineBounds
import com.asoc.typewright.project.CreateResult
import com.asoc.typewright.project.InMemoryProjectFiles
import com.asoc.typewright.project.InMemoryProjectStore
import com.asoc.typewright.project.ProjectFiles
import com.asoc.typewright.project.ProjectSession
import com.asoc.typewright.project.SessionEnvironment
import com.asoc.typewright.project.imports.ImportReview
import com.asoc.typewright.project.imports.reviewFontFile
import com.asoc.typewright.project.imports.toNewProjectSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The real thing: a font opened through the importer is built by the real fontmake in the Python that
 * `TYPEWRIGHT_PYTHON` names, and the TTF that comes back is read with core-font and compared with the
 * project. These tests never skip. Without `TYPEWRIGHT_PYTHON` pointing at a Python that has fontmake they
 * fail, saying which of those is missing, because a build that was never run proves nothing. They are
 * their own Gradle task, `:compile:pythonIntegrationTest`, so a machine without Python still has a green
 * `desktopTest`.
 */
class FontmakePythonIntegrationTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun repoFile(path: String): File {
        val root = System.getProperty("typewright.repoRoot") ?: fail("system property typewright.repoRoot is not set by the build")
        return File(root, path).also { assertTrue(it.isFile, "$path is missing from the repository") }
    }

    /** The backend on the real environment, after proving the environment is what these tests need. */
    private fun realBackend(): SystemPythonFontmakeBackend {
        val python = System.getenv(PYTHON_ENV_VARIABLE)
        if (python.isNullOrBlank()) {
            fail(
                "$PYTHON_ENV_VARIABLE is not set. Point it at a Python that has fontmake and skia-pathops, for example: pip install 'fontmake[pathops]'",
            )
        }
        val backend = SystemPythonFontmakeBackend()
        val availability = runBlocking { backend.availability() }
        if (availability is BackendAvailability.Unavailable) fail("$PYTHON_ENV_VARIABLE=$python cannot build fonts: ${availability.reason}")
        return backend
    }

    /** [bytes] opened as a font file into a project, as a person's font is, and the snapshot a build reads. */
    private fun openAsProject(
        fileName: String,
        bytes: ByteArray,
    ): OpenedProject {
        val ready = reviewFontFile(fileName, bytes) as? ImportReview.Ready ?: fail("$fileName was not ready to import")
        val renamed = if (ready.candidate.audit.mustRename) "${ready.candidate.ufo.fontInfo.familyName} Redraw" else null
        val spec = ready.candidate.toNewProjectSpec(projectName = fileName.substringBefore('-'), newFamily = renamed)
        val env = SessionEnvironment(scope = scope, io = Dispatchers.IO)
        return runBlocking {
            val session = (ProjectSession.create(InMemoryProjectStore(fileName), spec, env) as CreateResult.Created).session
            val master =
                session.state.value.font.masters
                    .single()
            OpenedProject(session.buildSnapshot(includeBuild = false), master.path, master.ufo.glyphs, master.ufo.fontInfo.unitsPerEm)
                .also { session.close() }
        }
    }

    private class OpenedProject(
        val snapshot: ProjectFiles,
        val ufoPath: String,
        val glyphs: List<Glyph>,
        val unitsPerEm: Int?,
    )

    private fun assertWithin(
        expected: Glyph,
        actual: Glyph?,
        label: String,
    ) {
        assertTrue(actual != null, "the compiled font is missing $label")
        assertEquals(expected.advanceWidth, actual.advanceWidth, "advance width of $label")
        val e = expected.outlineBounds()
        val a = actual.outlineBounds()
        if (e == null || a == null) {
            assertEquals(e == null, a == null, "ink presence of $label")
            return
        }
        for ((side, x, y) in listOf(
            Triple("xMin", e.xMin, a.xMin),
            Triple("yMin", e.yMin, a.yMin),
            Triple("xMax", e.xMax, a.xMax),
            Triple("yMax", e.yMax, a.yMax),
        )) {
            assertTrue(abs(x - y) <= 2, "$side of $label: project $x, compiled $y")
        }
    }

    private fun compileAndCompare(
        fileName: String,
        path: String,
    ): CompileResult.Success {
        val backend = realBackend()
        val project = openAsProject(fileName, repoFile(path).readBytes())
        assertTrue(
            project.glyphs.any { g ->
                g.contours.any { it.format == CurveFormat.QUADRATIC }
            },
            "the project should hold the font's quadratic outlines",
        )

        val streamed = mutableListOf<CompileLogLine>()
        val result = runBlocking { backend.compile(CompileRequest(project.snapshot, CompileSource.Ufo(project.ufoPath)), streamed::add) }
        if (result !is CompileResult.Success) {
            fail("the build did not succeed: $result\n" + result.log.joinToString("\n") { "[${it.level}] ${it.message}" })
        }
        assertEquals(result.log, streamed)
        val compiled = readSfntFont(result.binaries.single().bytes)

        assertEquals(project.glyphs.size, compiled.numGlyphs, "glyph count")
        assertEquals(project.unitsPerEm, compiled.head.unitsPerEm, "units per em")
        for (glyph in project.glyphs) {
            val byCodePoint = glyph.unicodes.firstOrNull()?.let { compiled.glyphForCodePoint(it) }
            assertWithin(glyph, byCodePoint ?: compiled.glyph(glyph.name), "'${glyph.name}'")
        }
        assertTrue(result.log.none { it.level == CompileLogLine.Level.ERROR }, "the log has ERROR lines")
        assertTrue(result.log.none { it.message.startsWith("Overlaps were not removed") }, "pathops should be installed for these tests")
        assertTrue(result.log.any { it.message.contains("RemoveOverlapsFilter") }, "fontmake should have removed overlaps")
        return result
    }

    @Test
    fun anAccentedFamilyBuildsForRealAndTheFontKeepsItsNamesAndLetters() {
        // The build copy and the font fontmake writes have plain ASCII names, so this holds in a Java whose file-name encoding is ASCII
        // too. The glyphs are named as fontmake wants them (production names, which a post table can hold); the family and the UFO
        // folder are what carry the accent.
        val project = accentedProject(listOf("eacute" to 0xE9, "uni3042" to 0x3042))
        val result = runBlocking { realBackend().compile(CompileRequest(project, CompileSource.Ufo(ACCENTED_UFO))) }
        if (result !is CompileResult.Success) {
            fail("the build did not succeed: $result\n" + result.log.joinToString("\n") { "[${it.level}] ${it.message}" })
        }
        val binary = result.binaries.single()
        assertEquals("HyléDeco-Regular.ttf", binary.fileName, "the project's own name is put back on the binary")
        val compiled = readSfntFont(binary.bytes)
        assertEquals(
            ACCENTED_FAMILY,
            compiled.name.get(com.asoc.typewright.core.font.sfnt.NameId.FAMILY),
            "the name table carries the real family",
        )
        assertEquals(2, compiled.numGlyphs - 1, "the two glyphs and .notdef")
        assertTrue(
            compiled.glyphIdForCodePoint(0xE9) != null && compiled.glyphIdForCodePoint(0x3042) != null,
            "both letters are in the cmap",
        )
    }

    @Test
    fun theRealPythonIsFoundAndSaysFontmakeIsThere() {
        val backend = realBackend()
        assertEquals(BackendAvailability.Available, runBlocking { backend.availability() })
        assertContains(backend.location.description, "on this computer")
    }

    @Test
    fun hyleDecoOpenedFromItsTtfBuildsToATtfWithTheSameGlyphsAdvancesAndUnitsPerEm() {
        val result = compileAndCompare("HyleDeco-Regular.ttf", "fonts/HyleDeco-Regular.ttf")
        assertTrue(result.log.any { it.message.startsWith("Rewrote ") && "quadratic to cubic" in it.message })
    }

    @Test
    fun theBinaryCarriesTheGoogleFontsFixUpsAndTheLogListsThem() {
        val project = openAsProject("HyleDeco-Regular.ttf", repoFile("fonts/HyleDeco-Regular.ttf").readBytes())
        val request = CompileRequest(project.snapshot, CompileSource.Ufo(project.ufoPath))
        val plain = runBlocking { realBackend().compile(request.copy(options = CompileOptions(googleFontsFixes = false))) }
        val fixed = runBlocking { realBackend().compile(request) }
        assertIs<CompileResult.Success>(plain)
        assertIs<CompileResult.Success>(fixed)

        // What fontmake alone wrote: embedding restricted, no gasp, no prep. This is what the fix-ups are for.
        val before = readSfntFont(plain.binaries.single().bytes)
        assertTrue(before.os2!!.fsType != 0, "fontmake alone writes a restricted fsType, or this test proves nothing")
        assertFalse("gasp" in before.tableTags || "prep" in before.tableTags, "fontmake alone writes neither gasp nor prep")
        assertTrue(plain.log.none { it.message.contains("Google Fonts fix-up") })

        val after = readSfntFont(fixed.binaries.single().bytes)
        assertEquals(0, after.os2!!.fsType, "fsType is installable embedding")
        assertTrue(after.os2!!.fsSelection and (1 shl 7) != 0, "USE_TYPO_METRICS is set")
        assertTrue(
            "gasp" in after.tableTags && "prep" in after.tableTags,
            "an unhinted font has gasp and the smart-dropout prep: ${after.tableTags}",
        )
        val said = fixed.log.map { it.message }.filter { it.startsWith("Google Fonts fix-up") }
        assertTrue(
            said.any {
                "fsType" in it
            } && said.any { "USE_TYPO_METRICS" in it } && said.any { "gasp" in it } && said.any { "prep" in it },
            said.toString(),
        )
        // Nothing else about the font moved: the same glyphs, advances and units per em.
        assertEquals(before.numGlyphs, after.numGlyphs)
        assertEquals(before.head.unitsPerEm, after.head.unitsPerEm)
        for (gid in 0 until before.numGlyphs) {
            assertEquals(before.glyph(gid).contours, after.glyph(gid).contours, "outline of glyph $gid")
            assertEquals(before.glyph(gid).advanceWidth, after.glyph(gid).advanceWidth, "advance of glyph $gid")
        }
    }

    @Test
    fun poppinsWithRealCurvesSurvivesTheQuadraticToCubicToQuadraticTripWithinTwoUnits() {
        val result = compileAndCompare("Poppins-Regular.ttf", "data/learn-faces/poppins/Poppins-Regular.ttf")
        // The curves really were curves: the compiled 'o' has off-curve points, not only corners.
        val compiled = readSfntFont(result.binaries.single().bytes)
        val o = compiled.glyphForCodePoint('o'.code)!!
        assertTrue(o.contours.sumOf { c -> c.points.count { !it.onCurve } } >= 8, "Poppins' o has curves")
    }

    @Test
    fun theProjectIsNotChangedByBuildingIt() {
        val backend = realBackend()
        val project = openAsProject("HyleDeco-Regular.ttf", repoFile("fonts/HyleDeco-Regular.ttf").readBytes())
        val before = project.snapshot.listFiles().associateWith { project.snapshot.readBytes(it).toList() }
        runBlocking { backend.compile(CompileRequest(project.snapshot, CompileSource.Ufo(project.ufoPath))) }
        val after = project.snapshot.listFiles().associateWith { project.snapshot.readBytes(it).toList() }
        assertEquals(before, after)
        assertTrue(project.snapshot.readBytes("${project.ufoPath}/glyphs/contents.plist").isNotEmpty())
    }

    @Test
    fun aSourceFontmakeRejectsGivesOnePlainSentenceAndTheRawLog() {
        val backend = realBackend()
        val project = openAsProject("HyleDeco-Regular.ttf", repoFile("fonts/HyleDeco-Regular.ttf").readBytes())
        // A feature file that is not valid OpenType feature syntax: our codec carries it through, fontmake refuses it.
        val files =
            project.snapshot.listFiles().associateWith { project.snapshot.readBytes(it) } +
                ("${project.ufoPath}/features.fea" to "feature liga { sub f i by".encodeToByteArray())
        val result =
            runBlocking { backend.compile(CompileRequest(InMemoryProjectFiles("Broken", files), CompileSource.Ufo(project.ufoPath))) }

        assertIs<CompileResult.Failure>(result)
        assertFalse('\n' in result.reason, result.reason)
        assertTrue(result.reason.endsWith("."), result.reason)
        assertTrue(result.log.any { it.level == CompileLogLine.Level.ERROR }, "the raw log should keep fontmake's own error lines")
    }

    @Test
    fun cancellingARealBuildStopsFontmakeAndLeavesNothingBehind() {
        val backend = realBackend()
        val project = openAsProject("Poppins-Regular.ttf", repoFile("data/learn-faces/poppins/Poppins-Regular.ttf").readBytes())
        val seen = java.util.concurrent.CopyOnWriteArrayList<CompileLogLine>()
        val me = ProcessHandle.current()
        val before = buildDirectories()

        runBlocking {
            val build =
                async(
                    Dispatchers.Default,
                ) { backend.compile(CompileRequest(project.snapshot, CompileSource.Ufo(project.ufoPath)), seen::add) }
            val deadline = System.nanoTime() + 60_000_000_000L
            while (seen.none { it.message.startsWith("Running fontmake") } && System.nanoTime() < deadline) delay(20)
            assertTrue(seen.any { it.message.startsWith("Running fontmake") }, "the build never reached fontmake")
            delay(300)
            assertTrue(
                me.descendants().anyMatch {
                    it
                        .info()
                        .commandLine()
                        .orElse("")
                        .contains("fontmake")
                },
                "fontmake should be running",
            )

            build.cancelAndJoin()
        }

        val deadline = System.nanoTime() + 10_000_000_000L
        while (me.descendants().anyMatch {
                it.isAlive &&
                    it
                        .info()
                        .commandLine()
                        .orElse("")
                        .contains("fontmake")
            } &&
            System.nanoTime() < deadline
        ) {
            Thread.sleep(50)
        }
        assertFalse(
            me.descendants().anyMatch {
                it.isAlive &&
                    it
                        .info()
                        .commandLine()
                        .orElse("")
                        .contains("fontmake")
            },
            "fontmake outlived the cancelled build",
        )
        assertEquals(emptySet(), buildDirectories() - before, "build directories left behind")
    }

    private fun buildDirectories(): Set<String> =
        File(System.getProperty("java.io.tmpdir"))
            .listFiles { f -> f.isDirectory && f.name.startsWith("typewright-build-") }
            .orEmpty()
            .map { it.name }
            .toSet()
}
