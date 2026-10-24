// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.ship

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.asoc.typewright.compile.CompileLocation
import com.asoc.typewright.compile.CompileLogLine
import com.asoc.typewright.project.TreeWriteReport
import com.asoc.typewright.project.TreeWriter
import com.asoc.typewright.qa.report.CheckReport
import com.asoc.typewright.qa.report.Severity
import com.asoc.typewright.qa.report.buildCheckReport
import com.asoc.typewright.qa.ship.BlockerSource
import com.asoc.typewright.qa.ship.Omission
import com.asoc.typewright.qa.ship.ShipBlocker
import com.asoc.typewright.qa.ship.ShipCapability
import com.asoc.typewright.qa.ship.ShipMode
import com.asoc.typewright.ui.ScreenshotHarness
import com.asoc.typewright.ui.TextCapture
import com.asoc.typewright.ui.project.LocalProjectWorkspace
import com.asoc.typewright.ui.project.OpenProject
import com.asoc.typewright.ui.project.SampleProject
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.CanvasTextures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The Ship pages drawn from the open project, as PNGs under `ui/build/screenshots/` (phone width is 390 dp at density 2), and checked for what
 * a picture can answer: that the words on the page are the project's own, that a blocked page names every fail the report holds, and that each
 * state says what the board says. Nothing here asserts that a page looks right: the PNGs are for a person to put beside the boards.
 */
class ShipScreenshotTest {
    private val texture: CanvasTexture = CanvasTextures.PAPER
    private val workspace = SampleProject.workspace()
    private val project: OpenProject get() = SampleProject.openProject(workspace)
    private val subject: ShipSubject = checkNotNull(shipSubjectOf(project)) { "the sample has a master" }

    /** Hyle Deco's real layer-two report: four fails, because the sample is a traced font. */
    private val realReport: CheckReport = buildCheckReport(subject.check.inputs)

    /** The same report with its fails flipped to passes, for the pages that show a project Check has nothing against. A test fixture; the app never does this. */
    private val cleanReport: CheckReport =
        realReport.copy(
            items = realReport.items.map { if (it.severity == Severity.FAIL) it.copy(severity = Severity.PASS) else it },
        )

    private val compiles = ShipCapability(true, "Compiled on this computer", "fontmake runs here when you build · nothing is sent")
    private val desktop = ShipPlatform(ShipExit.FOLDER, compiles, CompileLocation.OnThisComputer("fontmake"))
    private val web =
        ShipPlatform(
            ShipExit.ZIP,
            ShipCapability(
                false,
                "Not compiled here",
                "The web preview does not compile (D4); export the UFO and Ship folder and build on the desktop app",
            ),
            null,
        )
    private val noTree =
        object : TreeWriter {
            override val description = "nowhere"

            override suspend fun write(
                files: Map<String, ByteArray>,
                ownedDirectories: Collection<String>,
            ) = TreeWriteReport(files.size, 0, 0)
        }
    private val target = ShipTarget("~/Fonts/hyle-deco-gf", noTree)
    private val actions = ShipFrameActions({}, {}, {}, {}, {}, {})

    @Composable
    private fun Frame(
        report: CheckReport?,
        run: ShipRunState = ShipRunState.Idle,
        page: ShipPageState = ShipPageState(target = target),
        platform: ShipPlatform = desktop,
    ) {
        CompositionLocalProvider(LocalProjectWorkspace provides workspace) {
            ShipFrame(
                texture = texture,
                project = project,
                subject = subject,
                report = report,
                run = run,
                page = page,
                platform = platform,
                actions = actions,
                onChoice = { _, _ -> },
                onRoute = {},
            )
        }
    }

    private fun shot(
        name: String,
        report: CheckReport? = cleanReport,
        run: ShipRunState = ShipRunState.Idle,
        page: ShipPageState = ShipPageState(target = target),
        platform: ShipPlatform = desktop,
    ): TextCapture = ScreenshotHarness.captureWithText(name, 780, 1688, 2f) { Frame(report, run, page, platform) }

    private val steps =
        listOf(
            ShipStepStatus(ShipStep.UFO, StepState.DONE, 400),
            ShipStepStatus(ShipStep.COMPILE, StepState.DONE, 6200),
            ShipStepStatus(ShipStep.CHECK, StepState.RUNNING),
            ShipStepStatus(ShipStep.WRITE, StepState.WAITING),
        )

    @Test
    fun theReadyPageIsTheBoardsChecklistFromTheProjectsOwnFacts() {
        val page = shot("ship-ready-phone")
        assertTrue(page.has("Ship · Hyle Deco"), "the title names the open project")
        assertTrue(page.has("A Google Fonts repository, written to a folder"))
        assertTrue(page.has("Check has no fails"))
        assertTrue(page.has("Names are unique"))
        assertTrue(page.has("Hyle Deco · Regular · HyleDeco-Regular"), "the names are the project's own")
        assertTrue(page.has("Copyright 2026 The Hyle Deco Project Authors"), "the copyright line is the project's own")
        // The sample states no designer, so the line says so and offers Font info; nothing is invented.
        assertTrue(page.has("Designer is not set"))
        assertTrue(page.has("METADATA needs one"))
        assertTrue(page.has("DESCRIPTION.en_us.html"))
        assertTrue(page.has("A draft from your brief, ready to edit"))
        assertTrue(page.has("Compiled on this computer"))
        assertTrue(page.has("fontmake runs here when you build · nothing is sent"))
        assertTrue(page.has("~/Fonts/hyle-deco-gf"), "the chosen folder is named")
        assertTrue(page.has("fonts/ttf/HyleDeco-Regular.ttf") && page.has("sources/HyleDeco-Regular.ufo/") && page.has("METADATA.pb"))
        assertTrue(page.has("GitHub push is not in V1".uppercase()), "the one-line note about GitHub")
        assertFalse(page.has("Build is blocked"), "nothing is blocked")
    }

    @Test
    fun theBlockedPageNamesEveryFailTheReportHolds() {
        val page = shot("ship-blocked-phone", report = realReport)
        val fails = realReport.items.filter { it.severity == Severity.FAIL }
        assertTrue(fails.size >= 3, "the real Hyle Deco report must have fails for this page to mean anything: ${realReport.counts.line()}")
        assertTrue(page.has("Check has ${fails.size} fails"))
        for (fail in fails) assertTrue(page.has(fail.title.uppercase()), "${fail.title} is a fail and must be listed")
        assertTrue(page.has("Build is blocked while Check has fails. ${fails.size} to go."))
        assertTrue(page.has("Fix them in Check"))
    }

    @Test
    fun theBuildingPageStreamsTheStepsAndSaysCancelIsSafe() {
        val running =
            ShipRunState.Building(
                steps,
                TimeSource.Monotonic.markNow() - 8.seconds,
                listOf(CompileLogLine(CompileLogLine.Level.INFO, "INFO:fontmake.font_project:Building TTF for HyleDeco-Regular")),
            )
        val page = shot("ship-building-phone", run = running)
        assertTrue(page.has("Building Hyle Deco"))
        assertTrue(page.has("On this computer · nothing leaves it"))
        for (step in ShipStep.entries) assertTrue(page.has(step.title), "${step.title} is a step")
        assertTrue(page.has("0.4 s") && page.has("6.2 s"), "the finished steps say how long they took")
        assertTrue(page.has("running"))
        assertTrue(page.has("ELAPSED 00:08"))
        assertTrue(page.has("CANCEL LEAVES THE PROJECT UNTOUCHED"))
        assertTrue(page.has("Show fontmake output"))
    }

    @Test
    fun theBuildingPageShowsTheCompilersOutputWhenAsked() {
        val running =
            ShipRunState.Building(
                steps,
                TimeSource.Monotonic.markNow() - 3.seconds,
                listOf(CompileLogLine(CompileLogLine.Level.WARNING, "WARNING:ufo2ft:a gentle warning")),
            )
        val page = shot("ship-building-output-phone", run = running, page = ShipPageState(target = target, showOutput = true))
        assertTrue(page.has("Hide fontmake output"))
        assertTrue(page.has("warning: WARNING:ufo2ft:a gentle warning"))
    }

    @Test
    fun aFailureNamesItsCauseAndSaysNothingWasWritten() {
        val failed =
            ShipRunState.Failed(
                ShipStep.COMPILE,
                "Python 3.11 (python3) is missing fontmake. Install it with: pip install fontmake",
                steps.take(1) + ShipStepStatus(ShipStep.COMPILE, StepState.FAILED) + steps.drop(2),
                listOf(CompileLogLine(CompileLogLine.Level.ERROR, "ModuleNotFoundError: No module named 'fontmake'")),
            )
        val page = shot("ship-failed-phone", run = failed)
        assertTrue(page.has("Ship stopped while compiling the project with fontmake"))
        assertTrue(page.has("pip install fontmake"))
        assertTrue(page.has("Nothing was written. Your project and the folder are as they were."))
        assertTrue(page.has("Try again"))
    }

    @Test
    fun aStoppedBuildListsTheFailsOnTheCompiledFont() {
        val stopped =
            ShipRunState.Stopped(
                listOf(
                    ShipBlocker(
                        BlockerSource.LAYER_ONE,
                        "com.google.fonts/check/gasp",
                        "Is the gasp table set?",
                        "Font is missing the 'gasp' table.",
                    ),
                    ShipBlocker(
                        BlockerSource.LAYER_ONE,
                        "com.google.fonts/check/fstype",
                        "fsType does not restrict",
                        "fsType is set to 4.",
                    ),
                ),
                steps,
                emptyList(),
            )
        val page = shot("ship-stopped-phone", run = stopped)
        assertTrue(page.has("The compiled font has 2 fails in Google Fonts' own checks"))
        assertTrue(page.has("IS THE GASP TABLE SET?") && page.has("Font is missing the 'gasp' table."))
        assertTrue(page.has("Nothing was written, because Ship stops while Check has fails."))
    }

    @Test
    fun theDonePageSaysWhereItWentWhatWasWrittenAndWhatStillNeedsThePerson() {
        val sheet = ShipSheet(subject, ShipMode.GOOGLE_FONTS, cleanReport, null, desktop)
        val result =
            ShipResult(
                target = "~/Fonts/hyle-deco-gf",
                tree = sheet.preview.tree,
                files = 19,
                bytes = 3_400_000,
                omissions = listOf(Omission("AUTHORS.txt", "the project names no designer")),
                requestText = "# Add Hyle Deco to Google Fonts\n\nI would like to add **Hyle Deco** to Google Fonts.\n",
                withFont = true,
                mode = ShipMode.GOOGLE_FONTS,
                compileMillis = 6200,
                checkLine = "Fontbakery 0.12.10: 0 fail · 12 warn · 147 pass",
                checkSkipped = null,
                totalMillis = 24_000,
                steps = steps,
            )
        val page = shot("ship-done-phone", run = ShipRunState.Done(result))
        assertTrue(page.has("Written to ~/Fonts/hyle-deco-gf"))
        assertTrue(page.has("19 files · 3.2 MB"))
        assertTrue(page.has("The compiled font was checked: Fontbakery 0.12.10: 0 fail · 12 warn · 147 pass."))
        assertTrue(page.has("STILL NEEDS YOU") && page.has("Designer is not set"))
        assertTrue(page.has("AUTHORS.txt: the project names no designer"))
        assertTrue(page.has("Copy the text"))
    }

    @Test
    fun theWebPageOffersAZipAndSaysWhyInTheBackendsWords() {
        val page = shot("ship-web-phone", platform = web, page = ShipPageState(target = null))
        assertTrue(page.has("A Google Fonts repository, written to a folder".replace("written to a folder", "exported as a zip")))
        assertTrue(page.has("The web preview does not compile (D4); export the UFO and Ship folder and build on the desktop app"))
        assertTrue(page.has("A zip your browser downloads"))
        assertFalse(page.has("fonts/ttf/"), "no font is made here, so none is promised")
        assertFalse(page.has("Choose a folder"), "a zip needs no folder")
    }

    @Test
    fun ownLicenceDropsTheGoogleFontsFilesAndOffersTheThreeLicences() {
        val page = shot("ship-own-licence-phone", page = ShipPageState(mode = ShipMode.OWN_LICENCE, target = target))
        assertTrue(page.has("A repository under your own licence, written to a folder"))
        assertTrue(page.has("OFL") && page.has("PROPRIETARY") && page.has("OTHER"))
        assertFalse(
            page.has("METADATA.pb") || page.has("upstream.yaml") || page.has("DESCRIPTION.en_us.html"),
            "nothing here is for Google Fonts only",
        )
        assertTrue(page.has("OFL.txt"))
    }

    @Test
    fun withNoProjectThePageSaysSoAndNeverShipsAStandIn() {
        val page =
            ScreenshotHarness.captureWithText("ship-no-project-phone", 780, 1688, 2f) {
                ShipFrame(
                    texture = texture,
                    project = null,
                    subject = null,
                    report = null,
                    run = ShipRunState.Idle,
                    page = ShipPageState(),
                    platform = desktop,
                    actions = actions,
                    onChoice = { _, _ -> },
                    onRoute = {},
                )
            }
        assertTrue(page.has("Open or start a project to see the repository folder it would write."))
        assertEquals(false, page.has("Hyle Deco"))
    }
}
