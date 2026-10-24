// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.goldenpath

import com.asoc.typewright.compile.CompileLogLine
import com.asoc.typewright.compile.CompileRequest
import com.asoc.typewright.compile.CompileResult
import com.asoc.typewright.compile.CompileSource
import com.asoc.typewright.compile.ProjectDirectory
import com.asoc.typewright.compile.platformCompileBackend
import com.asoc.typewright.core.font.sfnt.NameId
import com.asoc.typewright.core.font.sfnt.SfntFont
import com.asoc.typewright.core.font.sfnt.readSfntFont
import com.asoc.typewright.core.font.ufo.UfoProject
import com.asoc.typewright.core.font.ufo.readKerningPlist
import com.asoc.typewright.core.font.ufo.readUfoProject
import com.asoc.typewright.core.geometry.CurveFormat
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
import com.asoc.typewright.project.Ethos
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
import com.asoc.typewright.project.imports.ImportAudit
import com.asoc.typewright.project.imports.ImportReview
import com.asoc.typewright.project.imports.ImportVerdict
import com.asoc.typewright.project.imports.LicenceKind
import com.asoc.typewright.project.imports.reviewFontFile
import com.asoc.typewright.project.imports.toNewProjectSpec
import com.asoc.typewright.project.scrapbook.ScrapbookPin
import com.asoc.typewright.project.scrapbook.ScrapbookPinKind
import com.asoc.typewright.qa.LayerOneAvailability
import com.asoc.typewright.qa.LayerOneInput
import com.asoc.typewright.qa.checkAlignmentMiss
import com.asoc.typewright.qa.checkNodeEconomy
import com.asoc.typewright.qa.corpus.NodeEconomyCorpus
import com.asoc.typewright.qa.platformLayerOneChecker
import com.asoc.typewright.qa.report.CheckInputs
import com.asoc.typewright.qa.report.CheckStyle
import com.asoc.typewright.qa.report.ImportProvenance
import com.asoc.typewright.qa.report.ReviewOrder
import com.asoc.typewright.qa.report.Severity
import com.asoc.typewright.qa.report.buildCheckReport
import kotlinx.coroutines.CoroutineScope
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
    /**
     * Step 1a (docs/V1_SCOPE.md, PROJECT_MODEL.md §12): S-ttf (Hyle Deco) and S-ttf2 (Poppins) each
     * open as a project through the same importer the app uses, the project is written to a real
     * directory by a real [ProjectSession], and what is read back from disk is checked against the
     * font file itself: glyph order, advances and contours, head/hhea/OS/2 metrics, and every cmap
     * entry. The licence audit names the OFL 1.1 for both, and the two projects differ in glyph
     * count and family, so the step cannot pass on a single canned project.
     */
    fun step1aOpenExistingFont() =
        runBlocking {
            val hyle = openFontAsProject("hyle-deco", "HyleDeco-Regular.ttf", Seeds.sTtfBytes, Seeds.sTtfFont, this)
            val poppins = openFontAsProject("poppins", "Poppins-Regular.ttf", Seeds.sTtf2Bytes, Seeds.sTtf2Font, this)

            for (opened in listOf(hyle, poppins)) {
                assertEquals(LicenceKind.OFL, opened.audit.licence, "${opened.fileName}: the audit must name the OFL")
                assertTrue("Open Font License 1.1" in opened.audit.licenceName, "${opened.fileName}: ${opened.audit.licenceName}")
                assertEquals(ImportVerdict.FINE, opened.audit.verdict, "${opened.fileName}: ${opened.audit.verdictWords}")
            }
            // Anti-fake: two different real fonts give two different projects.
            assertTrue(hyle.glyphCount != poppins.glyphCount, "glyph counts: ${hyle.glyphCount} vs ${poppins.glyphCount}")
            assertEquals("Hyle Deco", hyle.family)
            assertEquals("Poppins", poppins.family)
            assertTrue(
                poppins.glyphCount > 100 && hyle.glyphCount > 100,
                "projects are real: ${hyle.glyphCount} and ${poppins.glyphCount} glyphs",
            )
        }

    /** What step 1a keeps of one opened font. */
    private class OpenedFont(
        val fileName: String,
        val audit: ImportAudit,
        val family: String?,
        val glyphCount: Int,
    )

    private suspend fun openFontAsProject(
        label: String,
        fileName: String,
        bytes: ByteArray,
        font: SfntFont,
        scope: CoroutineScope,
    ): OpenedFont {
        val review = reviewFontFile(fileName, bytes)
        val candidate = (review as? ImportReview.Ready)?.candidate ?: fail("$fileName was not ready to import: $review")
        assertTrue(candidate.audit.canImport, "$fileName: ${candidate.audit.verdictWords}")
        val renamed = if (candidate.audit.mustRename) "${candidate.ufo.fontInfo.familyName} Redraw" else null
        val spec = candidate.toNewProjectSpec(projectName = candidate.ufo.fontInfo.familyName ?: label, newFamily = renamed)

        val root = ProjectFixtures.tempDir("step1a-$label")
        val store = FileSystemProjectStore(root.toPath())
        val session = (ProjectSession.create(store, spec, ProjectFixtures.env(scope)) as CreateResult.Created).session
        val master =
            session.state.value.font.masters
                .single()
        session.flush()
        session.close()

        // The UFO read back from disk equals the session's project.
        val fromDisk = readUfoProject(ProjectFixtures.readUfoDir(File(root, master.path)))
        assertEquals(master.ufo, fromDisk, "$fileName: the UFO on disk must equal the project in the session")
        ProjectFixtures.assertNoTempFiles(root)
        assertTrue(File(root, "typewright.json").isFile, "$fileName: the project folder has no typewright.json")

        // Glyph order, advances and contours equal the font's.
        assertEquals(font.numGlyphs, fromDisk.glyphs.size, "$fileName: glyph count")
        for ((gid, glyph) in fromDisk.glyphs.withIndex()) {
            val original = font.glyph(gid)
            assertEquals(font.glyphName(gid), glyph.name, "$fileName: glyph $gid is out of order")
            assertEquals(original.advanceWidth, glyph.advanceWidth, "$fileName: advance of ${glyph.name}")
            assertEquals(original.contours, glyph.contours, "$fileName: contours of ${glyph.name}")
        }

        // Metrics equal head, hhea and OS/2.
        val info = fromDisk.fontInfo
        assertEquals(font.head.unitsPerEm, info.unitsPerEm, "$fileName: unitsPerEm")
        assertEquals(font.hhea.ascender, info.openTypeHheaAscender, "$fileName: hhea ascender")
        assertEquals(font.hhea.descender, info.openTypeHheaDescender, "$fileName: hhea descender")
        assertEquals(font.hhea.lineGap, info.openTypeHheaLineGap, "$fileName: hhea line gap")
        val os2 = checkNotNull(font.os2) { "$fileName has no OS/2 table" }
        assertEquals(os2.sTypoAscender, info.openTypeOS2TypoAscender, "$fileName: typo ascender")
        assertEquals(os2.sTypoDescender, info.openTypeOS2TypoDescender, "$fileName: typo descender")
        assertEquals(os2.sTypoLineGap, info.openTypeOS2TypoLineGap, "$fileName: typo line gap")
        assertEquals(os2.usWinAscent, info.openTypeOS2WinAscent, "$fileName: win ascent")
        assertEquals(os2.usWinDescent, info.openTypeOS2WinDescent, "$fileName: win descent")
        assertEquals(os2.sxHeight, info.xHeight, "$fileName: x-height")
        assertEquals(os2.sCapHeight, info.capHeight, "$fileName: cap height")

        // Every cmap entry is a unicode of the glyph it maps to.
        assertTrue(font.cmap.bestMapping.isNotEmpty(), "$fileName has an empty cmap")
        for ((codePoint, gid) in font.cmap.bestMapping) {
            assertTrue(codePoint in fromDisk.glyphs[gid].unicodes, "$fileName: U+${codePoint.toString(16)} is not on glyph $gid")
        }
        assertEquals(font.cmap.bestMapping.size, candidate.report.mappedCharacters, "$fileName: the report's cmap coverage")
        return OpenedFont(fileName, candidate.audit, info.familyName, fromDisk.glyphs.size)
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

    /**
     * Fully built (docs/V1_SCOPE.md step 4): the Check report over S-ufo (Hyle Deco opened as a person opens a font, its
     * style declared geometric sans and its provenance recorded as an imported compiled font), then layer one on a compiled
     * binary.
     *
     * Layer two: all fourteen checks are in the report, each with a severity; the T's stem foot at y = 1 is an alignment
     * miss; ⚑ the seed has fails (it is a traced font) and a copy with its names and copyright line taken away has more,
     * exactly in the two items that read them, so the report answers to the data; the report's economy is `checkNodeEconomy`'s
     * own answer; building the report twice gives equal reports.
     *
     * Layer one: the platform checker is Available and its result is not a skip. It runs on S-project compiled by the real
     * backend, and its fail count equals the count a direct run of Fontbakery gives on the same file. With no Python that has
     * fontmake and Fontbakery's googlefonts profile this step fails, naming which is missing; it never passes without them.
     */
    fun step4Check() {
        val seed = readUfoProject(ProjectFixtures.readUfoDir(Seeds.newSUfoDirectory()))
        val corpus = NodeEconomyCorpus.load()
        val outlineGlyphs = seed.glyphs.count { it.contours.isNotEmpty() }

        fun inputs(project: UfoProject) =
            CheckInputs(
                project = project,
                style = CheckStyle("sans-geometric", "geometric sans", heuristic = false),
                corpus = corpus,
                provenance = ImportProvenance(true, outlineGlyphs, outlineGlyphs, "HyleDeco-Regular.ttf"),
            )

        val report = buildCheckReport(inputs(seed))

        // Layer two covers every check with a severity.
        assertEquals(ReviewOrder.ALL.toSet(), report.items.map { it.id }.toSet(), "the fourteen checks")
        assertEquals(14, report.items.size)

        // An alignment miss is found: CLAUDE.md's T stem foot at y = 1.
        val lines = mapOf("baseline" to 0, "x-height" to (seed.fontInfo.xHeight ?: 500), "cap height" to (seed.fontInfo.capHeight ?: 700))
        val tFoot = checkAlignmentMiss(seed.glyphs.first { it.name == "T" }, lines).firstOrNull { it.location.y == 1 }
        assertTrue(tFoot != null, "the T's stem foot at y = 1 should be an alignment miss")
        assertEquals(Severity.WARN, report.item(ReviewOrder.ALIGNMENT_MISS)?.severity)

        // Anti-fake (⚑): the failing seed has fails, and taking the names and the copyright line away adds exactly those two.
        assertTrue(report.counts.fail > 0, "a traced font must have fails; the report says ${report.counts.line()}")
        assertEquals(Severity.PASS, report.item(ReviewOrder.NAMES)?.severity)
        assertEquals(Severity.PASS, report.item(ReviewOrder.LICENCE)?.severity)
        val stripped = seed.copy(fontInfo = seed.fontInfo.copy(familyName = null, copyright = null))
        val worse = buildCheckReport(inputs(stripped))
        assertEquals(Severity.FAIL, worse.item(ReviewOrder.NAMES)?.severity)
        assertEquals(Severity.FAIL, worse.item(ReviewOrder.LICENCE)?.severity)
        assertEquals(report.counts.fail + 2, worse.counts.fail)

        // The report's economy is checkNodeEconomy's own answer, and the report is deterministic.
        assertEquals(checkNodeEconomy(seed, "sans-geometric", corpus), report.economy)
        assertEquals(report, buildCheckReport(inputs(seed)))

        // Layer one, on desktop: Available, and not a skip.
        val checker = platformLayerOneChecker()
        val availability = runBlocking { checker.availability() }
        if (availability is LayerOneAvailability.Unavailable) {
            fail("Step 4 (Check): layer one is not available on this computer: ${availability.reason}")
        }
        val compiled = compileOnTheRealBackend(Seeds.newSProject(), "GoldenPathSeed")
        val binary = compiled.binaries.single()
        val layerOne = runBlocking { checker.check(LayerOneInput(binary.fileName, binary.bytes)) }
        assertTrue(
            layerOne.results.none { it.id == "layer-one/unavailable" },
            "layer one must not be a skip: ${layerOne.results.first().message}",
        )
        assertTrue(layerOne.results.size > 50, "a real googlefonts run has hundreds of checks, not ${layerOne.results.size}")

        // The fail count matches a direct run of the same tool on the same file.
        val tool = layerOne.tool?.tool
        if (tool == "Fontbakery") {
            val direct = directFontbakeryCounts(binary.fileName, binary.bytes)
            val expectedFails = (direct["FAIL"] ?: 0) + (direct["ERROR"] ?: 0) + (direct["FATAL"] ?: 0)
            assertEquals(expectedFails, layerOne.counts.fail, "FAIL count against a direct fontbakery run $direct")
        }
        val full = report.withLayerOne(layerOne)
        assertEquals(report.items, full.items)
    }

    /**
     * Fully built: builds S-project's UFO (S-ttf through the bridge, `familyName = "Golden Path Seed"`, every contour
     * degree-elevated to cubic so it measures compiling, not importing), writes it to a fresh temp project directory,
     * hashes every source file, and really calls [platformCompileBackend]`.compile`: the real fontmake, in the Python
     * `TYPEWRIGHT_PYTHON` names. A failure, including no Python, is a plain [fail] that quotes the backend's own sentence.
     * A success is checked against the seed project: family name, units per em, every glyph's advance and ink box within 2
     * units, the cmap, no ERROR line in the log, and the sources untouched. Then S-ufo itself, which holds Hyle Deco's
     * quadratic outlines as `qcurve` points exactly as an import keeps them, is built the same way (OQ 125's flow), so a
     * font opened from a TTF compiles too.
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
        val success = successOrFail("Compile TTF", result)
        assertCompiledLikeTheProject(success, seedProject, "Golden Path Seed")
        // Anti-fake (⚑): compiling never rewrites the sources it read.
        assertEquals(sourceHashesBefore, hashSourceFiles(projectDirectory), "compiling must not touch the project's own sources")

        // The quadratic flow: S-ufo as an import leaves it, qcurve points and all.
        val quadratic = readUfoProject(ProjectFixtures.readUfoDir(Seeds.newSUfoDirectory()))
        assertTrue(
            quadratic.glyphs.any { g ->
                g.contours.any { it.format == CurveFormat.QUADRATIC }
            },
            "S-ufo should hold quadratic outlines",
        )
        val rewrote = compileOnTheRealBackend(quadratic, "HyleDeco")
        assertCompiledLikeTheProject(rewrote, quadratic, "Hyle Deco")
        assertTrue(
            rewrote.log.any {
                it.message.contains("quadratic to cubic")
            },
            "the build copy should have been rewritten as cubic, and the log say so",
        )
    }

    /** The single [CompileResult.Success] in [result], or a [fail] that quotes the backend's own reason and log. */
    private fun successOrFail(
        what: String,
        result: CompileResult,
    ): CompileResult.Success =
        when (result) {
            is CompileResult.Success -> result
            is CompileResult.NotImplemented -> notBuilt("5", what, result.planned, "P14")
            is CompileResult.Failure -> fail("$what failed: ${result.reason}\n${formatLog(result.log)}")
        }

    /** [project] written as a UFO called [name] in a fresh project directory and built by the platform backend. */
    private fun compileOnTheRealBackend(
        project: UfoProject,
        name: String,
    ): CompileResult.Success {
        val root = Files.createTempDirectory("typewright-golden-path-compile-").toFile()
        writeUfoFiles(project, File(root, "$name.ufo"))
        val request = CompileRequest(FileProjectDirectory(root), CompileSource.Ufo("$name.ufo"))
        return successOrFail("Compile $name", runBlocking { platformCompileBackend().compile(request) })
    }

    /** Checks the compiled font against the project it came from: names, metrics, every glyph's advance and ink, the cmap, the log. */
    private fun assertCompiledLikeTheProject(
        result: CompileResult.Success,
        project: UfoProject,
        family: String,
    ) {
        assertEquals(1, result.binaries.size, "expected exactly one compiled TTF")
        val compiled = readSfntFont(result.binaries.single().bytes)

        // Anti-fake (⚑): the compiled font's own name table carries the project's family name, never a fixture's.
        assertEquals(family, compiled.name.get(NameId.FAMILY), "compiled font's name ID 1")
        assertEquals(project.fontInfo.unitsPerEm, compiled.head.unitsPerEm, "unitsPerEm")

        val codepointByGlyphName = mutableMapOf<String, Int>()
        for ((codepoint, gid) in Seeds.sTtfFont.cmap.bestMapping) {
            codepointByGlyphName.getOrPut(Seeds.sTtfFont.glyphName(gid)) { codepoint }
        }
        assertEquals(project.glyphs.size, compiled.numGlyphs, "glyph count")
        for (glyph in project.glyphs) {
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
    }

    /** `fontbakery check-googlefonts` run directly on [bytes], as a person would, and its own count of each result. */
    private fun directFontbakeryCounts(
        fileName: String,
        bytes: ByteArray,
    ): Map<String, Int> {
        val python = System.getenv("TYPEWRIGHT_PYTHON")?.takeIf { it.isNotBlank() } ?: "python3"
        val work = Files.createTempDirectory("typewright-golden-path-direct-").toFile()
        try {
            val ttf = File(work, fileName).apply { writeBytes(bytes) }
            val json = File(work, "direct.json")
            val process =
                ProcessBuilder(
                    python,
                    "-m",
                    "fontbakery",
                    "check-googlefonts",
                    "-q",
                    "-n",
                    "-C",
                    "--skip-network",
                    "--json",
                    json.path,
                    ttf.path,
                ).redirectErrorStream(true)
                    .start()
            process.inputStream.readBytes()
            process.waitFor()
            assertTrue(json.isFile, "a direct fontbakery run wrote no report")
            // The report's first object is its totals: {"result": {"FAIL": 6, "PASS": 90, ...}, "sections": [...]}.
            val totals =
                Regex("\"result\"\\s*:\\s*\\{([^}]*)}").find(json.readText())?.groupValues?.get(1) ?: fail("the report has no totals")
            return Regex("\"([A-Z]+)\"\\s*:\\s*(\\d+)").findAll(totals).associate { it.groupValues[1] to it.groupValues[2].toInt() }
        } finally {
            work.deleteRecursively()
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
                    // ethos set explicitly, matching styleClass.declared by hand (BriefEdits.kt's
                    // withEthos would do the same): this step's own round-trip assertion below is
                    // about locks/diffs/scrapbook/lessons, not the brief migration a declared
                    // class with no ethos at all would otherwise exercise on reopen.
                    brief =
                        Brief(
                            source = BriefSource.FONT,
                            styleClass = StyleClass(declared = "sans-geometric", confirmed = null),
                            ethos = Ethos(genre = "sans-geometric"),
                        ),
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
