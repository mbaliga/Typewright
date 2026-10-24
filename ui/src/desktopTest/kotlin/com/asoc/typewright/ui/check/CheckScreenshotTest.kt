// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.check

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toPixelMap
import com.asoc.typewright.compile.CompileLogLine
import com.asoc.typewright.qa.report.buildCheckReport
import com.asoc.typewright.ui.Screenshot
import com.asoc.typewright.ui.ScreenshotHarness
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
    fun theReportIsDrawnFromTheProjectAndIsNotBlank() {
        val report = shot("check-report-phone")
        assertTrue(inkFraction(report) > 0.05, "the report is nearly blank")
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
    fun anErrorNamesTheCause() {
        val failed =
            CheckRunState(
                layerTwo = layerTwo,
                layerOne = LayerOneState.Failed(LayerOneStage.COMPILE, "The font source has an open contour in T.", emptyList()),
                probed = true,
            )
        assertTrue(inkFraction(shot("check-error", failed)) > 0.05)
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
        val wide = shot("check-report-desktop", width = 1280, height = 800, density = 1f)
        assertTrue(inkFraction(wide) > 0.01)
        val narrow = shot("check-report-desktop-compare", width = 780, height = 1688)
        assertEquals(wide.bitmap.width, 1280)
        assertTrue(narrow.bitmap.width < wide.bitmap.width)
    }

    private companion object {
        const val MIN_BLOB = 12
    }
}
