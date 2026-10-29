// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.goldenpath

import com.asoc.typewright.compile.BackendAvailability
import com.asoc.typewright.compile.platformCompileBackend
import com.asoc.typewright.core.font.sfnt.NameId
import com.asoc.typewright.core.font.sfnt.readSfntFont
import com.asoc.typewright.core.font.ufo.readUfoProject
import com.asoc.typewright.project.CreateResult
import com.asoc.typewright.project.FileSystemProjectStore
import com.asoc.typewright.project.FolderTreeWriter
import com.asoc.typewright.project.ProjectSession
import com.asoc.typewright.project.StyleClass
import com.asoc.typewright.project.imports.ImportReview
import com.asoc.typewright.project.imports.reviewFontFile
import com.asoc.typewright.project.imports.toNewProjectSpec
import com.asoc.typewright.qa.LayerOneAvailability
import com.asoc.typewright.qa.corpus.NodeEconomyCorpus
import com.asoc.typewright.qa.platformLayerOneChecker
import com.asoc.typewright.qa.report.CheckInputs
import com.asoc.typewright.qa.report.CheckReport
import com.asoc.typewright.qa.report.ReviewOrder
import com.asoc.typewright.qa.report.Severity
import com.asoc.typewright.qa.report.buildCheckReport
import com.asoc.typewright.qa.ship.GfCategory
import com.asoc.typewright.qa.ship.ShipMode
import com.asoc.typewright.ui.project.OpenProject
import com.asoc.typewright.ui.ship.ShipController
import com.asoc.typewright.ui.ship.ShipMemory
import com.asoc.typewright.ui.ship.ShipRequest
import com.asoc.typewright.ui.ship.ShipRunState
import com.asoc.typewright.ui.ship.ShipStart
import com.asoc.typewright.ui.ship.ShipSubject
import com.asoc.typewright.ui.ship.ShipTarget
import com.asoc.typewright.ui.ship.shipSubjectOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.minutes

/**
 * Step 6 (Ship the repo folder), driven through the controller the Ship room drives, with the real compiler, the real layer-one checker and the
 * real folder writer, and nothing of the UI's screens. Two seeds:
 *
 * - The failing seed is Hyle Deco opened as a person opens it. Check fails it, so Ship is Blocked and names every fail, and the target
 *   directory the person chose stays empty (the anti-fake: a controller that wrote anyway, or wrote a little before it noticed, fails here).
 * - The clean seed is [CleanSeed]. Check has nothing against it, so the folder is written: every file of a Google Fonts upstream repository,
 *   the UFO exactly as the session wrote it, the compiled TTF, and the request text. The folder is then given to Fontbakery's Google Fonts
 *   profile in the shape Google Fonts' onboarding would copy it to, and must have no fail.
 */
internal object ShipStepRun {
    fun run() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            runBlocking {
                blockedSeed(scope)
                cleanSeed(scope)
            }
        } finally {
            scope.cancel()
        }
    }

    // ---- the failing seed ----------------------------------------------------------------------------

    private suspend fun blockedSeed(scope: CoroutineScope) {
        val review = reviewFontFile("HyleDeco-Regular.ttf", Seeds.sTtfBytes)
        val candidate = (review as? ImportReview.Ready)?.candidate ?: fail("Hyle Deco was not ready to import: $review")
        val spec = candidate.toNewProjectSpec(projectName = "Hyle Deco", newFamily = null)
        // The class a person declares for it; Check judges the economy of its outlines by that.
        val declared = spec.copy(brief = spec.brief.copy(styleClass = StyleClass(declared = "sans-geometric", confirmed = null)))
        val root = ProjectFixtures.tempDir("step6-blocked-project")
        val session =
            (
                ProjectSession.create(
                    FileSystemProjectStore(root.toPath()),
                    declared,
                    ProjectFixtures.env(scope),
                ) as CreateResult.Created
            ).session
        try {
            val subject = subjectOf(OpenProject(session, session.state.value))
            val report = buildCheckReport(subject.check.inputs)
            val failing =
                report.items
                    .filter { it.severity == Severity.FAIL }
                    .map { it.id }
                    .toSet()
            assertTrue(
                failing.isNotEmpty(),
                "a traced font must have fails, or this seed proves nothing; Check says ${report.counts.line()}",
            )

            val parent = ProjectFixtures.tempDir("step6-blocked-target")
            val target = File(parent, "hyle-deco-gf").apply { mkdirs() }
            val controller = ShipController(scope, platformCompileBackend(), platformLayerOneChecker(), ShipMemory())

            // The seed as it is: Blocked, naming every fail.
            val blocked = assertIs<ShipStart.Blocked>(controller.ship(request(subject, report, target)))
            assertEquals(
                failing,
                blocked.gate.blockers
                    .map { it.id }
                    .toSet(),
                "Ship must name every fail Check has",
            )
            assertEquals(report.counts.fail, blocked.gate.failCount, "and count exactly as many")
            assertEquals(ShipRunState.Idle, controller.state.value, "a blocked ship does not start")

            // The seed with its names and its copyright line taken away: two more fails, and Ship names those two as well.
            val inputs = subject.check.inputs
            val stripped = inputs.project.copy(fontInfo = inputs.project.fontInfo.copy(familyName = null, copyright = null))
            val worse =
                buildCheckReport(
                    CheckInputs(stripped, inputs.style, inputs.corpus, inputs.provenance, inputs.otherMasters),
                )
            val blockedWorse = assertIs<ShipStart.Blocked>(controller.ship(request(subject, worse, target)))
            assertEquals(report.counts.fail + 2, blockedWorse.gate.failCount)
            assertContains(blockedWorse.gate.blockers.map { it.id }, ReviewOrder.NAMES)
            assertContains(blockedWorse.gate.blockers.map { it.id }, ReviewOrder.LICENCE)

            // Anti-fake: the target the person chose is as they left it, and no half-written folder sits beside it.
            assertEquals(emptyList(), target.list()?.toList(), "the target directory must stay empty while Ship is blocked")
            assertEquals(listOf(target.name), parent.list()?.toList(), "nothing was staged beside the target either")
            assertEquals(ShipRunState.Idle, controller.state.value)
        } finally {
            session.close()
        }
    }

    // ---- the clean seed ------------------------------------------------------------------------------

    private suspend fun cleanSeed(scope: CoroutineScope) {
        val root = ProjectFixtures.tempDir("step6-clean-project")
        val session =
            (
                ProjectSession.create(
                    FileSystemProjectStore(root.toPath()),
                    CleanSeed.spec(),
                    ProjectFixtures.env(scope),
                ) as CreateResult.Created
            ).session
        try {
            session.flush()
            val project = OpenProject(session, session.state.value)
            val subject = subjectOf(project)
            val report = buildCheckReport(subject.check.inputs)
            val fails = report.items.filter { it.severity == Severity.FAIL }
            assertEquals(emptyList(), fails.map { "${it.id}: ${it.summary}" }, "the clean seed must have nothing Check fails it for")

            // Ship needs a compiler and a checker on this computer; without them it fails, naming what is missing, and never passes.
            val compiler = platformCompileBackend()
            (compiler.availability() as? BackendAvailability.Unavailable)?.let {
                fail(
                    "Step 6 (Ship): no compiler on this computer: ${it.reason}",
                )
            }
            val checker = platformLayerOneChecker()
            (checker.availability() as? LayerOneAvailability.Unavailable)?.let {
                fail(
                    "Step 6 (Ship): no layer-one checker here: ${it.reason}",
                )
            }

            val parent = ProjectFixtures.tempDir("step6-clean-target")
            val target = File(parent, "seed-boxes-gf").apply { mkdirs() }
            val controller = ShipController(scope, compiler, checker, ShipMemory())
            assertEquals(ShipStart.Started, controller.ship(request(subject, report, target, GfCategory.SANS_SERIF)))
            val finished = withTimeout(10.minutes) { controller.state.first { it !is ShipRunState.Building } }
            val done = finished as? ShipRunState.Done ?: fail("Ship did not finish: $finished")

            // Every file of the upstream repository is there.
            val written =
                target
                    .walkTopDown()
                    .filter {
                        it.isFile
                    }.map { it.relativeTo(target).path.replace(File.separatorChar, '/') }
                    .sorted()
                    .toList()
            for (name in listOf("OFL.txt", "DESCRIPTION.en_us.html", "METADATA.pb", "upstream.yaml", "fonts/ttf/SeedBoxes-Regular.ttf")) {
                assertContains(written, name, "the folder has no $name; it has $written")
            }
            assertContains(written, "documentation/GOOGLE_FONTS_REQUEST.md")
            assertTrue(
                written.any {
                    it.startsWith("sources/SeedBoxes-Regular.ufo/")
                },
                "the folder has no sources/SeedBoxes-Regular.ufo; it has $written",
            )
            assertEquals(written.size, done.result.files, "the result counts the files written")
            assertEquals(
                emptyList(),
                done.result.omissions.map { "${it.path}: ${it.reason}" },
                "every fact is stated, so nothing is left out",
            )
            assertEquals(listOf(target.name), parent.list()?.toList(), "no staging folder is left beside the target")

            // sources/…ufo equals the project: the files byte for byte, and what they read back as.
            val master =
                session.state.value.font.masters
                    .single()
            val projectUfo = File(root, master.path)
            val shippedUfo = File(target, "sources/SeedBoxes-Regular.ufo")
            assertEquals(
                ProjectFixtures.hashTree(projectUfo),
                ProjectFixtures.hashTree(shippedUfo),
                "the shipped UFO is the project's UFO, file for file",
            )
            assertEquals(master.ufo, readUfoProject(ProjectFixtures.readUfoDir(shippedUfo)), "and it reads back as the project")

            // fonts/ttf/…ttf parses, and is this font, not another.
            val ttf = File(target, "fonts/ttf/SeedBoxes-Regular.ttf")
            val compiled = readSfntFont(ttf.readBytes())
            assertEquals(CleanSeed.FAMILY, compiled.name.get(NameId.FAMILY))
            assertEquals(master.ufo.glyphs.size, compiled.numGlyphs, "the binary has the project's glyphs")
            for (codepoint in CleanSeed.codepoints) {
                assertTrue(
                    compiled.glyphIdForCodePoint(codepoint) != null,
                    "U+${codepoint.toString(16)} is not in the cmap",
                )
            }
            assertEquals(0, compiled.os2?.fsType, "the binary says it may be embedded (the build's Google Fonts fix-up)")
            assertTrue(
                done.result.withFont && done.result.checkLine?.contains(" 0 fail") == true,
                "the binary was checked: ${done.result.checkLine}",
            )

            // The text is this project's own.
            val ofl = File(target, "OFL.txt").readText()
            assertTrue(ofl.startsWith(CleanSeed.COPYRIGHT + "\n"), "OFL.txt opens with the project's copyright line")
            val metadata = File(target, "METADATA.pb").readText()
            assertContains(metadata, "name: \"Seed Boxes\"")
            assertContains(metadata, "designer: \"${CleanSeed.DESIGNER}\"")
            assertContains(metadata, "filename: \"SeedBoxes-Regular.ttf\"")
            assertTrue(
                "Hyle" !in ofl + metadata + File(target, "DESCRIPTION.en_us.html").readText(),
                "nothing of another font leaks into this folder",
            )
            assertContains(File(target, "documentation/GOOGLE_FONTS_REQUEST.md").readText(), "Seed Boxes")
            assertEquals(done.result.requestText, File(target, "documentation/GOOGLE_FONTS_REQUEST.md").readText())

            // Fontbakery, on the family directory as Google Fonts' onboarding would assemble it from upstream.yaml, finds no fail.
            val googleFonts = assembleFamilyDirectory(target, "seedboxes")
            val family = File(googleFonts, "seedboxes")
            val results = FontbakeryRun.run(File(family, "SeedBoxes-Regular.ttf"))
            assertTrue(results.size > 50, "a real googlefonts run has hundreds of checks, not ${results.size}")
            assertEquals(
                emptyList(),
                results.filter { it.isFail }.map { it.toString() },
                "Fontbakery's Google Fonts profile on the shipped family",
            )
            googleFonts.deleteRecursively()
        } finally {
            session.close()
        }
    }

    // ---- helpers -------------------------------------------------------------------------------------

    private fun subjectOf(project: OpenProject): ShipSubject =
        checkNotNull(shipSubjectOf(project, NodeEconomyCorpus.load())) {
            "the project has no master"
        }

    private fun request(
        subject: ShipSubject,
        report: CheckReport,
        target: File,
        category: GfCategory? = GfCategory.DISPLAY,
    ) = ShipRequest(
        subject,
        ShipMode.GOOGLE_FONTS,
        category,
        ShipTarget(target.path, FolderTreeWriter(target.toPath())),
        report,
        branch = "main",
        dateAdded = "2026-10-24",
    )

    /**
     * A stand-in for Google Fonts' own tree, under a fresh root that is returned: its `<directory>` folder holds each file `upstream.yaml`
     * maps, copied from its place in the repository to its place there. Fontbakery reads the directory's name and the files beside the font,
     * so both have to be right.
     */
    private fun assembleFamilyDirectory(
        repository: File,
        directory: String,
    ): File {
        val root = ProjectFixtures.tempDir("step6-family")
        val family = File(root, directory).apply { mkdirs() }
        val lines = File(repository, "upstream.yaml").readLines()
        val files = lines.dropWhile { it.trim() != "files:" }.drop(1).takeWhile { it.startsWith(" ") }
        assertTrue(files.isNotEmpty(), "upstream.yaml maps no files: $lines")
        for (line in files) {
            val (from, to) = line.trim().split(":", limit = 2).map { it.trim().trim('"', '\'') }
            File(repository, from).copyTo(File(family, to))
        }
        // METADATA.pb, DESCRIPTION, OFL and the font are what Google Fonts keeps; upstream.yaml itself is what its tooling reads.
        File(repository, "upstream.yaml").copyTo(File(family, "upstream.yaml"))
        return root
    }
}
