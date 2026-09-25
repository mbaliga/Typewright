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
import com.asoc.typewright.core.font.ufo.readKerningPlist
import com.asoc.typewright.core.font.ufo.readUfoProject
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.core.geometry.count
import com.asoc.typewright.core.geometry.extrema
import com.asoc.typewright.core.geometry.pointAt
import com.asoc.typewright.core.geometry.segments
import com.asoc.typewright.project.ApprovalOrigin
import com.asoc.typewright.project.Approve
import com.asoc.typewright.project.Brief
import com.asoc.typewright.project.BriefSource
import com.asoc.typewright.project.CreateResult
import com.asoc.typewright.project.EditResult
import com.asoc.typewright.project.FileSystemProjectStore
import com.asoc.typewright.project.GlyphRef
import com.asoc.typewright.project.LockState
import com.asoc.typewright.project.MetaChange
import com.asoc.typewright.project.MovePoints
import com.asoc.typewright.project.NewMaster
import com.asoc.typewright.project.NewProjectSpec
import com.asoc.typewright.project.OpenResult
import com.asoc.typewright.project.PointRef
import com.asoc.typewright.project.ProjectSession
import com.asoc.typewright.project.Refusal
import com.asoc.typewright.project.ReplaceOutline
import com.asoc.typewright.project.SetKerning
import com.asoc.typewright.project.StyleClass
import com.asoc.typewright.project.Unlock
import com.asoc.typewright.project.UpdateFontInfo
import com.asoc.typewright.project.scrapbook.ScrapbookPin
import com.asoc.typewright.project.scrapbook.ScrapbookPinKind
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

    /**
     * Fully built (docs/PROJECT_MODEL.md §12): S-ufo through a real [ProjectSession] over a
     * temp [FileSystemProjectStore], driving exactly the scenario `:project`'s own
     * `GoldenPathScenariosTest.step3PointMovesEconomyKerningAndLocksBehaveAsSpecified` proves
     * against the same API -- a point move, a live-economy check, kerning flushed to
     * `kerning.plist`, a locked-glyph refusal and its diff, and undo-all/redo-all byte identity.
     */
    fun step3DrawSpaceEdit() =
        runBlocking {
            val root = ProjectFixtures.tempDir("step3")
            val sUfo = readUfoProject(ProjectFixtures.readUfoDir(Seeds.newSUfoDirectory()))
            val spec =
                NewProjectSpec(
                    name = "Hyle Deco",
                    brief = Brief(source = BriefSource.FONT, styleClass = StyleClass(declared = "sans-geometric", confirmed = null)),
                    scripts = listOf("Latn"),
                    masters = listOf(NewMaster(id = "regular", styleName = "Regular", ufo = sUfo)),
                )
            val store = FileSystemProjectStore(root.toPath())
            val session = (ProjectSession.create(store, spec, ProjectFixtures.env(this)) as CreateResult.Created).session
            val initial = session.state.value.font
            val treeAfterCreate = ProjectFixtures.hashTree(root)
            val glyphN = GlyphRef("regular", "n")
            val glyphH = GlyphRef("regular", "H")

            // A point move changes only that point.
            val before = initial.glyph(glyphN)!!
            session.execute(MovePoints(glyphN, setOf(PointRef(0, 0)), 10, 0))
            val afterMove = session.state.value.font
            val movedGlyph = afterMove.glyph(glyphN)!!
            assertEquals(
                before.contours[0]
                    .points[0]
                    .point.x + 10,
                movedGlyph.contours[0]
                    .points[0]
                    .point.x,
            )
            for (master in afterMove.masters) {
                for (glyph in master.ufo.glyphs) {
                    if (GlyphRef(master.id, glyph.name) == glyphN) continue
                    assertEquals(
                        initial
                            .master(master.id)!!
                            .ufo.glyphs
                            .single { it.name == glyph.name },
                        glyph,
                    )
                }
            }

            // Live economy: replacing an outline changes the count, and the session's own economy agrees with it.
            val economyBefore = session.economy.value.count(glyphN)
            val newContours = movedGlyph.contours + movedGlyph.contours
            session.execute(ReplaceOutline(glyphN, newContours, "Duplicate contour"))
            assertEquals(
                session.state.value.font
                    .glyph(glyphN)!!
                    .count(),
                session.economy.value.count(glyphN),
            )
            assertTrue(economyBefore != session.economy.value.count(glyphN))

            // Kerning, then flush and read back kerning.plist from disk.
            session.execute(SetKerning("regular", "T", "o", -40.0))
            session.flush()
            val masterPath =
                session.state.value.font.masters
                    .single()
                    .path
            val kerningText = store.read("$masterPath/kerning.plist")!!.decodeToString()
            assertEquals(-40.0, readKerningPlist(kerningText).getValue("T").getValue("o"))

            // Locked glyph: refused, then unlocked, moved, flushed and a diff appears containing the change.
            session.execute(Approve(glyphH, ApprovalOrigin.DRAW))
            val refusal = session.execute(MovePoints(glyphH, setOf(PointRef(0, 0)), 1, 0))
            assertEquals(EditResult.Refused(Refusal.Locked(glyphH)), refusal)
            session.execute(Unlock(glyphH))
            session.execute(MovePoints(glyphH, setOf(PointRef(0, 0)), 1, 0))
            session.flush()
            val diffPaths = store.list().filter { it.startsWith("locks/regular/H_.") && it.endsWith(".diff") }
            assertEquals(1, diffPaths.size)
            val diffText = store.read(diffPaths.single())!!.decodeToString()
            assertTrue(diffText.contains("-") && diffText.contains("+"))

            // Undo-all restores the initial font and, once flushed, the initial file tree (kerning.plist and locks/ gone).
            while (session.undo()) { /* keep undoing */ }
            assertEquals(initial, session.state.value.font)
            session.flush()
            assertEquals(treeAfterCreate, ProjectFixtures.hashTree(root))

            while (session.redo()) { /* keep redoing */ }
            session.flush()
            assertEquals(
                -40.0,
                session.state.value.font.masters
                    .single()
                    .ufo.kerningInfo.kerning
                    .getValue("T")
                    .getValue("o"),
            )
            // H was approved, then unlocked and moved (never re-approved), so it ends UNLOCKED with one open episode.
            assertEquals(
                LockState.UNLOCKED,
                session.state.value.font.locks
                    .getValue(glyphH)
                    .state,
            )
            assertEquals(
                1,
                session.state.value.font.locks
                    .getValue(glyphH)
                    .episodes.size,
            )

            session.close()
            ProjectFixtures.assertNoTempFiles(root)
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

    /**
     * Fully built (docs/PROJECT_MODEL.md §12): S-ufo through a real [ProjectSession] over a
     * temp [FileSystemProjectStore], driving exactly the scenario `:project`'s own
     * `GoldenPathScenariosTest.step7CloseAndReopenRoundTripsExactlyAndReSavingWritesNothing`
     * proves against the same API -- edits, a frozen diff, an open diff, kerning, font info, a
     * pin, a reflection and a task confirmation, closed and reopened byte-identically, with a
     * no-op save writing nothing and `rewriteAll()` re-encoding byte-identically.
     */
    fun step7CloseAndReopen() =
        runBlocking {
            val root = ProjectFixtures.tempDir("step7")
            val sUfo = readUfoProject(ProjectFixtures.readUfoDir(Seeds.newSUfoDirectory()))
            val spec =
                NewProjectSpec(
                    name = "Hyle Deco",
                    brief = Brief(source = BriefSource.FONT, styleClass = StyleClass(declared = "sans-geometric", confirmed = null)),
                    scripts = listOf("Latn"),
                    masters = listOf(NewMaster(id = "regular", styleName = "Regular", ufo = sUfo)),
                )
            val store = FileSystemProjectStore(root.toPath())
            val a = (ProjectSession.create(store, spec, ProjectFixtures.env(this)) as CreateResult.Created).session
            val glyphT = GlyphRef("regular", "T")
            val glyphH = GlyphRef("regular", "H")

            a.execute(Approve(glyphT, ApprovalOrigin.TRACE))
            a.execute(Unlock(glyphT))
            a.execute(MovePoints(glyphT, setOf(PointRef(0, 0)), 0, -1))
            a.execute(Approve(glyphT, ApprovalOrigin.DRAW)) // frozen diff: the episode just opened is relocked

            a.execute(Approve(glyphH, ApprovalOrigin.DRAW))
            a.execute(Unlock(glyphH))
            a.execute(MovePoints(glyphH, setOf(PointRef(0, 0)), 5, 0)) // left open

            a.execute(SetKerning("regular", "T", "o", -40.0))
            val fontInfo =
                a.state.value.font.masters
                    .single()
                    .ufo.fontInfo
            a.execute(UpdateFontInfo("regular", fontInfo.copy(openTypeNameDesigner = "Golden Path")))

            a.update(MetaChange.AddPin(ScrapbookPin("pin-1", ScrapbookPinKind.NOTE, "Note", "note", noteText = "A note"), null, null))
            a.update(MetaChange.AddReflection("latn", 4, "Round ends everywhere, or nowhere."))
            a.update(MetaChange.SetTaskConfirmed("latn", 1, true))

            val expected = a.state.value
            a.close()

            ProjectFixtures.assertNoTempFiles(root)
            val hashes = ProjectFixtures.hashTree(root)

            val b = (ProjectSession.open(FileSystemProjectStore(root.toPath()), ProjectFixtures.env(this)) as OpenResult.Opened).session
            assertEquals(expected, b.state.value) // project, locks, diffs, typewright.json, scrapbook and lessons
            assertTrue(b.openReport.externalEdits.isEmpty())
            b.close()
            assertEquals(hashes, ProjectFixtures.hashTree(root)) // saving with no changes: no writes

            val c = (ProjectSession.open(FileSystemProjectStore(root.toPath()), ProjectFixtures.env(this)) as OpenResult.Opened).session
            c.rewriteAll()
            c.close()
            assertEquals(hashes, ProjectFixtures.hashTree(root)) // re-encoding is byte-identical
            ProjectFixtures.assertNoTempFiles(root)
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
