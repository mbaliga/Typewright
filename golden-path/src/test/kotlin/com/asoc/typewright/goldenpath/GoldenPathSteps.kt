// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.goldenpath

import com.asoc.typewright.compile.CompileLogLine
import com.asoc.typewright.compile.CompileRequest
import com.asoc.typewright.compile.CompileResult
import com.asoc.typewright.compile.CompileSource
import com.asoc.typewright.compile.ProjectDirectory
import com.asoc.typewright.compile.platformCompileBackend
import com.asoc.typewright.core.font.sfnt.NameId
import com.asoc.typewright.core.font.sfnt.readSfntFont
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.core.geometry.extrema
import com.asoc.typewright.core.geometry.pointAt
import com.asoc.typewright.core.geometry.segments
import com.asoc.typewright.qa.LayerOneAvailability
import com.asoc.typewright.qa.platformLayerOneChecker
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * A golden-path step that is not built yet (PROMPTS_V1.md P10 step 6): `build-logic`'s
 * `GoldenPathReportTask`/`GoldenPathRatchetTask` (`golden-path/build.gradle.kts`'s `goldenPath`/
 * `goldenPathRatchet` tasks) recognise this class by name -- a `<failure>` whose `type` ends
 * `StepNotBuilt` -- to tell "honestly not built yet" apart from every other kind of test failure.
 * Never thrown from a step that partially works -- [notBuilt] is the one place this class is
 * constructed.
 */
class StepNotBuilt(
    message: String,
) : AssertionError(message)

/**
 * Fails the current step with [StepNotBuilt], in the one fixed message shape every unbuilt step
 * uses (`golden-path/README.md`): `"Step <step> (<name>) not built: <missing>; closes in
 * <closesIn>"`. [missing] says what real thing is absent (never "not implemented" alone); [closesIn]
 * names the prompt(s) that build it.
 */
fun notBuilt(
    step: String,
    name: String,
    missing: String,
    closesIn: String,
): Nothing = throw StepNotBuilt("Step $step ($name) not built: $missing; closes in $closesIn")

/**
 * Thrown by [runChain] for the first step in its sequence that fails, naming how many steps of the
 * chain completed first ([reachedSteps], `0` if the very first one failed) and which step id it
 * stopped at; [cause] is the step's own real failure (very often a [StepNotBuilt]). `build-logic`'s
 * `GoldenPathReportTask` reads this exact message shape back out to build its one-line chain
 * summary (see `GoldenPathResults.kt`'s own `CHAIN_FAILURE_REGEX`).
 */
class ChainFailure(
    chainLabel: String,
    reachedSteps: Int,
    stoppedAtStep: String,
    cause: Throwable,
) : AssertionError("chain ($chainLabel): reached step $reachedSteps; stopped at step $stoppedAtStep: ${cause.message}", cause)

/**
 * Runs [steps] (step id to action, in the chain's real order) one at a time against real state
 * carried forward between them; on the first exception, wraps it as a [ChainFailure] and stops --
 * the chain never runs a later step against a seed a failed earlier one never really produced.
 */
fun runChain(
    chainLabel: String,
    steps: List<Pair<String, () -> Unit>>,
) {
    for ((reached, entry) in steps.withIndex()) {
        val (stepId, action) = entry
        try {
            action()
        } catch (t: Throwable) {
            throw ChainFailure(chainLabel, reached, stepId, t)
        }
    }
}

/**
 * The golden path's real step logic, one function per `GoldenPathTest` id, shared by that id's own
 * test and by [runChain] so a chain runs exactly the same code a lone step test does. Every
 * still-unbuilt step's real assertions are sketched as comments on its own `GoldenPathTest` test
 * method, not here; this object holds only what actually runs today.
 */
object GoldenPathSteps {
    fun step1aOpenExistingFont() {
        notBuilt(
            "1a",
            "Open TTF/OTF/UFO",
            "no font→project import into a project directory (no project dir, no licence audit; " +
                "glyphs carry no unicodes (OPEN_QUESTIONS 124); the UFO writer is cubic-only, so a " +
                "TrueType import can't be saved (OPEN_QUESTIONS 125))",
            "P11 + P12",
        )
    }

    fun step1bNewProjectFromScan() {
        notBuilt(
            "1b",
            "New project from a scan",
            "no fiducial/homography/cell detection, no Latin template manifest, no photo fixtures",
            "P16",
        )
    }

    fun step2CaptureTraceAccept() {
        notBuilt(
            "2",
            "Capture, trace, accept",
            "trace output is in raster pixels, no cell→glyph pipeline, no accept, no glyph lock",
            "P16",
        )
    }

    fun step3DrawSpaceEdit() {
        notBuilt(
            "3",
            "Draw/Space edit",
            "no undo/redo, and no edit or kerning command API outside Compose state",
            "P11 (history) + P17",
        )
    }

    fun step4Check() {
        val availability = runBlocking { platformLayerOneChecker().availability() }
        val layerOneText =
            when (availability) {
                is LayerOneAvailability.Available -> "Available"
                is LayerOneAvailability.Unavailable -> availability.reason
            }
        notBuilt(
            "4",
            "Check report and severities",
            "no Check report or severities; layer one: $layerOneText",
            "P14",
        )
    }

    /**
     * Fully built (PROMPTS_V1.md P10 step 6's own design): builds S-project's UFO (S-ttf through
     * the bridge, `familyName = "Golden Path Seed"`), writes it to a fresh temp project directory,
     * hashes every source file, and really calls [platformCompileBackend]`.compile`. A stub backend
     * (today: `SystemPythonFontmakeBackend`, desktop) answers [CompileResult.NotImplemented], which
     * becomes [notBuilt] carrying the backend's own planned text -- not a hand-written message, so
     * this step's failure text changes the moment the backend's own plan does. A real failure is a
     * plain [fail]; a real [CompileResult.Success] is checked against the seed project for real.
     */
    fun step5CompileTtf() {
        val seedProject = Seeds.newSProject()
        val projectRoot = Files.createTempDirectory("typewright-golden-path-step5-").toFile()
        val ufoRelativePath = "GoldenPathSeed.ufo"
        writeUfoFiles(seedProject, File(projectRoot, ufoRelativePath))

        val projectDirectory = FileProjectDirectory(projectRoot)
        val sourceHashesBefore = hashSourceFiles(projectDirectory)

        val request = CompileRequest(project = projectDirectory, source = CompileSource.Ufo(ufoRelativePath))
        val result = runBlocking { platformCompileBackend().compile(request) }

        when (result) {
            is CompileResult.NotImplemented -> {
                notBuilt("5", "Compile TTF", result.planned, "P14")
            }

            is CompileResult.Failure -> {
                fail("Compile TTF failed: ${result.reason}\n${formatLog(result.log)}")
            }

            is CompileResult.Success -> {
                assertEquals(1, result.binaries.size, "expected exactly one compiled TTF")
                val binary = result.binaries.single()
                val compiled = readSfntFont(binary.bytes)

                // Anti-fake (⚑): the compiled font's own name table carries the seed's family
                // name, never a fixture's.
                assertEquals("Golden Path Seed", compiled.name.get(NameId.FAMILY), "compiled font's name ID 1")

                assertEquals(seedProject.fontInfo.unitsPerEm, compiled.head.unitsPerEm, "unitsPerEm")

                val codepointByGlyphName = mutableMapOf<String, Int>()
                for ((codepoint, gid) in Seeds.sTtfFont.cmap.bestMapping) {
                    codepointByGlyphName.getOrPut(Seeds.sTtfFont.glyphName(gid)) { codepoint }
                }

                for (glyph in seedProject.glyphs) {
                    val codepoint = codepointByGlyphName[glyph.name]
                    val compiledGlyph = codepoint?.let { compiled.glyphForCodePoint(it) } ?: compiled.glyph(glyph.name)
                    assertTrue(compiledGlyph != null, "compiled font is missing glyph '${glyph.name}'")
                    assertEquals(glyph.advanceWidth, compiledGlyph.advanceWidth, "advance width of '${glyph.name}'")
                    assertBoundsWithinTolerance(glyph.inkBounds(), compiledGlyph.inkBounds(), glyph.name)
                }

                // Anti-fake (⚑): cmap really maps the control characters, not merely present by name.
                for (ch in listOf('H', 'n', 'o')) {
                    assertTrue(compiled.glyphIdForCodePoint(ch.code) != null, "cmap should map '$ch'")
                }

                assertTrue(result.log.none { it.level == CompileLogLine.Level.ERROR }, "compile log should have no ERROR lines")

                // Anti-fake (⚑): compiling never rewrites the sources it read.
                assertEquals(sourceHashesBefore, hashSourceFiles(projectDirectory), "compiling must not touch the project's own sources")
            }
        }
    }

    fun step6ShipFolder() {
        notBuilt(
            "6",
            "Ship the repo folder",
            "ship generators return strings only; nothing writes the folder, re-checks the binary or blocks on fails",
            "P15",
        )
    }

    fun step7CloseAndReopen() {
        notBuilt(
            "7",
            "Close and reopen",
            "no ProjectSession; UfoProject touches disk only in core-font's jvmTest",
            "P11",
        )
    }
}

private fun formatLog(log: List<CompileLogLine>): String = log.joinToString("\n") { "[${it.level}] ${it.message}" }

private fun hashSourceFiles(project: ProjectDirectory): Map<String, String> =
    project.listFiles().associateWith { path -> sha256Hex(project.readBytes(path)) }

private fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/** A glyph's own ink bounding box, exact over its curves (endpoints plus every [extrema] point), not merely its control points. */
private data class Bounds(
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double,
)

private fun Glyph.inkBounds(): Bounds? {
    var bounds: Bounds? = null

    fun include(v: Vec2) {
        val current = bounds
        bounds =
            if (current == null) {
                Bounds(v.x, v.y, v.x, v.y)
            } else {
                Bounds(minOf(current.minX, v.x), minOf(current.minY, v.y), maxOf(current.maxX, v.x), maxOf(current.maxY, v.y))
            }
    }
    for (contour in contours) {
        for (segment in contour.segments()) {
            include(segment.pointAt(0.0))
            include(segment.pointAt(1.0))
        }
        for (extremum in contour.extrema()) include(extremum.segment.pointAt(extremum.t))
    }
    return bounds
}

private fun assertBoundsWithinTolerance(
    expected: Bounds?,
    actual: Bounds?,
    glyphName: String,
    toleranceUnits: Double = 2.0,
) {
    if (expected == null && actual == null) return
    assertTrue(expected != null && actual != null, "ink bbox presence mismatch for '$glyphName' (expected=$expected, actual=$actual)")
    for (
    (label, e, a) in
    listOf(
        Triple("minX", expected.minX, actual.minX),
        Triple("minY", expected.minY, actual.minY),
        Triple("maxX", expected.maxX, actual.maxX),
        Triple("maxY", expected.maxY, actual.maxY),
    )
    ) {
        assertTrue(abs(e - a) <= toleranceUnits, "$label for '$glyphName': expected $e, got $a (tolerance $toleranceUnits units)")
    }
}
