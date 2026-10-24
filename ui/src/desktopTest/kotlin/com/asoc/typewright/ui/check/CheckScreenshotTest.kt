// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.check

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toPixelMap
import com.asoc.typewright.compile.CompileLogLine
import com.asoc.typewright.qa.CheckOutcome
import com.asoc.typewright.qa.CheckStatus
import com.asoc.typewright.qa.LayerOneReport
import com.asoc.typewright.qa.report.EconomySort
import com.asoc.typewright.qa.report.buildCheckReport
import com.asoc.typewright.qa.report.economyListing
import com.asoc.typewright.ui.Screenshot
import com.asoc.typewright.ui.ScreenshotHarness
import com.asoc.typewright.ui.TextCapture
import com.asoc.typewright.ui.project.LocalProjectWorkspace
import com.asoc.typewright.ui.project.ProjectDataPacks
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.CanvasTextures
import com.asoc.typewright.ui.tokens.accent
import com.asoc.typewright.ui.tokens.toColor
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * The Check pages drawn from the open project, as PNGs under `ui/build/screenshots/` (phone width is 390 dp at density 2) and
 * checked for what they must hold. Nothing here asserts that a page looks right: the PNGs are for a person to look at against
 * the boards. The assertions are what a picture can answer: that the user's point is drawn where its count says, that the
 * pages change with the state and the project, and that no project means no report.
 */
class CheckScreenshotTest {
    private val texture: CanvasTexture = CanvasTextures.PAPER
    private val project = CheckTestProject.project
    private val subject = CheckTestProject.subject
    private val layerTwo = LayerTwoState.Ready(buildCheckReport(subject.inputs))
    private val needsDesktop =
        LayerOneState.Unavailable("Layer one needs the desktop app: Fontbakery and Fontspector run on a computer, not on this device.")

    private val phoneRun = CheckRunState(layerTwo = layerTwo, layerOne = needsDesktop, probed = true)

    @Composable
    private fun Frame(
        run: CheckRunState,
        page: CheckPageState,
        hasProject: Boolean = true,
    ) {
        CompositionLocalProvider(LocalProjectWorkspace provides CheckTestProject.workspace) {
            CheckFrame(
                texture = texture,
                project = project.takeIf { hasProject },
                subject = subject.takeIf { hasProject },
                run = run,
                page = page,
                corpus = ProjectDataPacks.corpus,
                onRunAgain = {},
                onCancel = {},
            )
        }
    }

    private fun shot(
        name: String,
        run: CheckRunState = phoneRun,
        page: CheckPageState = CheckPageState(),
        hasProject: Boolean = true,
        width: Int = 780,
        height: Int = 1688,
        density: Float = 2f,
    ): Screenshot = ScreenshotHarness.capture(name, width, height, density) { Frame(run, page, hasProject) }

    private fun shotWithText(
        name: String,
        run: CheckRunState = phoneRun,
        page: CheckPageState = CheckPageState(),
        hasProject: Boolean = true,
        width: Int = 780,
        height: Int = 1688,
        density: Float = 2f,
    ): TextCapture = ScreenshotHarness.captureWithText(name, width, height, density) { Frame(run, page, hasProject) }

    private fun inkFraction(shot: Screenshot): Double {
        val pixels = shot.bitmap.toPixelMap()
        val background = pixels[0, 0]
        var ink = 0
        var total = 0
        for (x in 0 until pixels.width step 2) {
            for (y in 0 until pixels.height step 2) {
                total++
                if (pixels[x, y] != background) ink++
            }
        }
        return ink.toDouble() / total
    }

    private fun differentPixels(
        a: Screenshot,
        b: Screenshot,
    ): Int {
        val pa = a.bitmap.toPixelMap()
        val pb = b.bitmap.toPixelMap()
        var different = 0
        for (x in 0 until minOf(pa.width, pb.width) step 2) {
            for (y in 0 until minOf(pa.height, pb.height) step 2) if (pa[x, y] != pb[x, y]) different++
        }
        return different
    }

    /** The centres (x, y) of every run of pixels that are exactly the accent colour, one per drawn point. */
    private fun accentPoints(shot: Screenshot): List<Pair<Double, Double>> {
        val pixels = shot.bitmap.toPixelMap()
        val accent = texture.accent.toColor()
        val seen = HashSet<Long>()
        val points = mutableListOf<Pair<Double, Double>>()
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                if (pixels[x, y] != accent || !seen.add(y.toLong() * pixels.width + x)) continue
                // Flood this blob.
                val stack = ArrayDeque(listOf(x to y))
                var sx = 0.0
                var sy = 0.0
                var n = 0
                while (stack.isNotEmpty()) {
                    val (cx, cy) = stack.removeLast()
                    sx += cx
                    sy += cy
                    n++
                    for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                        val nx = cx + dx
                        val ny = cy + dy
                        if (nx in 0 until pixels.width && ny in 0 until pixels.height && pixels[nx, ny] == accent &&
                            seen.add(ny.toLong() * pixels.width + nx)
                        ) {
                            stack.addLast(nx to ny)
                        }
                    }
                }
                if (n >= MIN_BLOB) points += (sx / n) to (sy / n)
            }
        }
        return points
    }

    @Test
    fun theReportIsDrawnFromTheProjectsOwnNumbersAndTitles() {
        val page = shotWithText("check-report-phone")
        // The page is titled with the project's own name, and says what the project's own T has: 1,763 points.
        assertTrue(page.has("Check · Hyle Deco Regular"), "the title names the open project")
        assertTrue(page.has("Your T has 1,763 points."), "the node-economy sentence quotes the project's own T")
        // Every one of the fourteen checks is on the page under its own title, as the report built from the project has it.
        for (item in layerTwo.report.items) assertTrue(page.has(item.title), "${item.title} is not on the page")
        assertEquals(14, layerTwo.report.items.size)
        // The small plot under the node-economy sentence says whose count it draws.
        assertTrue(page.has("yours 1,763"), "the mini plot is captioned with the project's count")
    }

    @Test
    fun theReportChangesWithTheLayerOneState() {
        val phone = shot("check-report-phone-compare")
        val withLayerOne =
            shot(
                "check-report-layer-one-done",
                CheckRunState(
                    layerTwo = layerTwo,
                    layerOne = LayerOneState.Done(FakeChecker.report(), subject.version),
                    runtime =
                        com.asoc.typewright.qa
                            .LayerOneRuntime("Fontbakery", "0.12.10", "on this computer"),
                    probed = true,
                ),
            )
        assertTrue(differentPixels(phone, withLayerOne) > 3000, "layer one's results should add to the page")
    }

    @Test
    fun rawShowsTheSameFindingsAsThePanelsOfRawText() {
        val plain = shot("check-report-plain-compare")
        val raw = shot("check-report-raw", page = CheckPageState(raw = true))
        assertTrue(differentPixels(plain, raw) > 3000, "Raw should print each finding's raw output")
    }

    @Test
    fun theRunningStateShowsItsStagesAndIsNotTheReportWithNoRun() {
        val running =
            CheckRunState(
                layerTwo = layerTwo,
                layerOne =
                    LayerOneState.Running(
                        LayerOneStage.COMPILE,
                        TimeSource.Monotonic.markNow(),
                        null,
                        listOf(CompileLogLine(CompileLogLine.Level.INFO, "fontmake: compiling HyleDeco-Regular.ufo")),
                    ),
                runtime =
                    com.asoc.typewright.qa
                        .LayerOneRuntime("Fontbakery", "0.12.10", "on this computer"),
                probed = true,
            )
        val notRun = shot("check-report-not-run", CheckRunState(layerTwo = layerTwo, layerOne = LayerOneState.NotRun, probed = true))
        val going = shot("check-running", running, CheckPageState(showOutput = true))
        assertTrue(differentPixels(notRun, going) > 3000)
        // The running block sits at the top of the page, where it is seen without scrolling, and the counts move down to make room.
        val top = going.bitmap.toPixelMap()
        val reference = notRun.bitmap.toPixelMap()
        var different = 0
        for (x in 0 until top.width step 2) for (y in 120 until 700 step 2) if (top[x, y] != reference[x, y]) different++
        assertTrue(different > 2000, "the top of the page should change when a run starts: $different")
    }

    @Test
    fun anErrorNamesTheCauseAndTheStageInPlainWords() {
        val cause = "The font source has an open contour in T."
        val failed =
            CheckRunState(
                layerTwo = layerTwo,
                layerOne = LayerOneState.Failed(LayerOneStage.COMPILE, cause, emptyList()),
                probed = true,
            )
        val page = shotWithText("check-error", failed)
        assertTrue(page.isVisible(cause), "the cause is drawn where it is seen, not only held in the state")
        assertTrue(page.isVisible("Layer one stopped while compiling the project with fontmake"), "the heading is a sentence")
        assertTrue(!page.has("while it was"), "the old heading put a command after 'was'")
        assertTrue(page.isVisible("Run again ›"), "and there is a way to try again")
    }

    @Test
    fun aLayerTwoThatThrewIsAnErrorStateWithItsCauseAndATryAgain() {
        val stopped = CheckRunState(layerTwo = LayerTwoState.Failed("the contour of T has no points"), probed = true)
        val page = shotWithText("check-layer-two-stopped", stopped)
        assertTrue(page.isVisible("Check stopped: the contour of T has no points."), "the cause is on the page")
        assertTrue(page.isVisible("Try again ›"))
        assertTrue(page.isVisible(CheckText.LAYER_TWO_STOPPED_ADVICE))
        // It is not the waiting page, which says it is still checking.
        assertTrue(!page.has("Checking the outlines…"))
    }

    @Test
    fun layerOnesNotesAreNotWarningsAndItsPassesAreListedOnRequest() {
        val tool =
            com.asoc.typewright.qa
                .LayerOneRuntime("Fontbakery", "0.12.10", "on this computer")
        val report =
            LayerOneReport(
                listOf(
                    CheckOutcome("com.google.fonts/check/a_failure", CheckStatus.FAIL, "A check failed."),
                    CheckOutcome("com.google.fonts/check/a_warning", CheckStatus.WARN, "A check warned."),
                    CheckOutcome("com.google.fonts/check/superfamily/list", CheckStatus.INFO, "family-path out-q"),
                    CheckOutcome("com.google.fonts/check/a_pass", CheckStatus.PASS, "OK", title = "Checks something quietly."),
                    CheckOutcome("com.google.fonts/check/a_skip", CheckStatus.SKIP, "No such table."),
                ),
                tool = tool,
                notes = listOf("Fontspector ran but its report could not be read (no checks), so Fontbakery checked the font instead."),
            )
        val run = CheckRunState(layerTwo = layerTwo, layerOne = LayerOneState.Done(report, subject.version), runtime = tool, probed = true)
        val closed = shotWithText("check-layer-one-notes", run)
        assertTrue(
            closed.has("5 checks · 1 fail · 1 warn · 1 pass · 1 skipped · 1 note"),
            "the note has its own count, apart from the warning",
        )
        assertTrue(closed.has("NOTES · NOT PROBLEMS") && closed.has("com.google.fonts/check/superfamily/list"))
        assertTrue(closed.has("so Fontbakery checked the font instead"), "the page says which tool made the report")
        assertTrue(
            closed.has("Show the 1 that passed ›") && !closed.has("com.google.fonts/check/a_pass"),
            "passes are counted, listed on request",
        )
        val open = shotWithText("check-layer-one-passes", run, CheckPageState(showPasses = true))
        assertTrue(
            open.has("Hide the 1 that passed ›") && open.has("com.google.fonts/check/a_pass") && open.has("Checks something quietly."),
        )
    }

    @Test
    fun withNoProjectOpenThereIsNoReportJustAnInvitation() {
        val empty = shot("check-no-project", CheckRunState(), hasProject = false, height = 900)
        val withProject = shot("check-no-project-compare", height = 900)
        assertTrue(inkFraction(empty) < inkFraction(withProject), "a report with no project should hold less than the real one")
    }

    @Test
    fun economyDrawsYourPointWhereYourCountSays() {
        val s = shot("check-economy-phone", page = CheckPageState(view = CheckView.ECONOMY))
        val points = accentPoints(s).sortedBy { it.second }
        assertTrue(points.size >= 6, "a row for each glyph that fits: ${points.size}")
        // Phone: 390 dp wide at density 2. The plot runs from the glyph column to the count column.
        val plotLeft = (14 + 28) * 2.0
        val plotRight = (390 - 10 - 64) * 2.0
        val t = points.first()
        val expected = plotLeft + LogAxis.position(1763.0) * (plotRight - plotLeft)
        assertTrue(abs(t.first - expected) < 6.0, "the T's point is at x = ${t.first}, a count of 1,763 belongs at $expected")
        // A row whose glyph is much closer to the box sits further left.
        assertTrue(points.drop(1).any { it.first < t.first - 100 }, "the other glyphs are not all as far right as the T")
    }

    @Test
    fun economyChangesWhenTheSortOrderChanges() {
        val worst = shot("check-economy-worst-compare", page = CheckPageState(view = CheckView.ECONOMY))
        val alpha =
            shot(
                "check-economy-az",
                page = CheckPageState(view = CheckView.ECONOMY, sort = com.asoc.typewright.qa.report.EconomySort.ALPHABETICAL),
            )
        assertTrue(differentPixels(worst, alpha) > 3000)
    }

    @Test
    fun theDesktopWidthKeepsALineReadable() {
        // On a 1,280 dp screen the sentences are set in a column no wider than the page's own cap, not across the whole window.
        val wide = shotWithText("check-report-desktop", width = 1280, height = 800, density = 1f)
        val sentences = layerTwo.report.items.map { it.summary }
        val widest = sentences.flatMap { sentence -> wide.containing(sentence) }.maxOf { it.bounds.width }
        assertTrue(widest <= 720f, "a sentence was set ${widest.toInt()} dp wide on a 1,280 dp screen; the cap is 720 dp")
        assertTrue(widest > 400f, "the sentences are still set in a real column: ${widest.toInt()} dp")
        // And on a phone the same sentences fit the phone, so the cap is what sets the wide one and not the sentences themselves.
        val narrow = shotWithText("check-report-desktop-compare", width = 780, height = 1688)
        val phoneWidest = sentences.flatMap { sentence -> narrow.containing(sentence) }.maxOf { it.bounds.width } / 2f
        assertTrue(phoneWidest < 390f, "on a phone the sentences stay inside the screen: ${phoneWidest.toInt()} dp")
        assertTrue(widest > phoneWidest, "the wide page sets its column wider than a phone's")
    }

    @Test
    fun economyPutsTheSortAtTheRightOfTheChipLineAndTheLegendAndFenceJustAboveTheDeck() {
        val page = shotWithText("check-economy-board-layout", page = CheckPageState(view = CheckView.ECONOMY))
        val chip = page.containing("GEOMETRIC SANS").first().bounds
        val sort = page.containing("WORST FIRST").first().bounds
        // One line: the sort order is on the chip line (not under it) and at its right-hand end, as the board draws it.
        assertTrue(abs(sort.center.y - chip.center.y) < 12f, "the sort chip is on its own row: ${sort.center.y} against ${chip.center.y}")
        assertTrue(sort.left > 780f / 2, "the sort order is at the right: ${sort.left}")
        // The legend and the fence sentence stand together just above the deck, below the rows, not at the end of the scrolling list.
        val fence = page.containing("Outlier: above the top of the box").first().bounds
        val legend = page.containing("middle half of the 30 faces").first().bounds
        val deckTop = (1688 - 172 * 2).toFloat()
        assertTrue(fence.bottom <= deckTop, "the fence sentence is under the deck's top: ${fence.bottom} > $deckTop")
        assertTrue(fence.bottom > deckTop - 40f * 2, "and close to it: ${fence.bottom}")
        assertTrue(legend.bottom <= fence.top, "the legend sits over the fence sentence")
    }

    @Test
    fun economyListsEveryGlyphAndSaysHowManyAreJudged() {
        val listing = economyListing(checkNotNull(layerTwo.report.economy), EconomySort.ALPHABETICAL)
        val page =
            shotWithText("check-economy-no-box", page = CheckPageState(view = CheckView.ECONOMY, sort = EconomySort.ALPHABETICAL))
        assertEquals(project.font?.glyphs?.size, listing.total, "one line per glyph of the open project")
        assertTrue(listing.judged < listing.total, "Hyle Deco has glyphs the corpus has no box for")
        assertTrue(page.isVisible(listing.coverage()), "the page says how many of the glyphs are judged: ${listing.coverage()}")
        // In alphabetical order the first glyph, .notdef, has no box, and its row says so rather than being left out.
        assertTrue(page.isVisible("NO BOX FOR THIS GLYPH IN THE CORPUS"))
    }

    private companion object {
        const val MIN_BLOB = 12
    }
}
