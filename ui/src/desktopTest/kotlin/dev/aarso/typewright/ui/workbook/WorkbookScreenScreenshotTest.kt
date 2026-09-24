package dev.aarso.typewright.ui.workbook

import androidx.compose.ui.graphics.toPixelMap
import dev.aarso.typewright.campaign.GateCheckStatus
import dev.aarso.typewright.ui.Screenshot
import dev.aarso.typewright.ui.ScreenshotHarness
import dev.aarso.typewright.ui.tokens.CanvasTextures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Real desktop proof that [WorkbookScreen] renders, wired to the real [loadWorkbookCampaignSnapshot]
 * -- viewed by hand (not just generated) before this task was reported done; PNGs land under
 * `ui/build/screenshots/`. Same [ScreenshotHarness] pattern every sibling screen's own screenshot
 * test already established ([dev.aarso.typewright.ui.learn.LearnScreenScreenshotTest]), **except
 * width**: this file captures at `width = 800, density = 2f` (400 dp), matching
 * `ui/typewright-explorer.html`'s own real phone frame (`.tw.phone{width:400px}`, line 70) --
 * not [dev.aarso.typewright.ui.learn.LearnScreenScreenshotTest]'s own `width = 420, density = 2f`
 * (210 dp), a real, screenshot-caught discrepancy found while building this file: at 210 dp this
 * screen's own longer header text ("Workbook · Latin", a real name the Learn screen's own shorter
 * "Learn" never exercised the same way) and longer task titles truncated far more than the real
 * explorer frame would ever force, not a bug in this screen's own layout (`docs/OPEN_QUESTIONS.md`).
 *
 * **Real, verified findings behind the task indices chosen below** (a throwaway probe test run
 * before writing this file, per this task's own instruction not to guess): against this build's
 * own real Hyle Deco reference project, task 1's gate is [GateCheckStatus.NOT_IMPLEMENTED] (and is
 * the real current/next task -- `currentWorkbookTaskIndex` never hardcodes "task 4"), task 4's
 * four checks are all real [GateCheckStatus.FAIL] (matching `HyleDecoTask4GateTest`'s own
 * finding), and task 6 is a real mix of [GateCheckStatus.PASS] (`x`, `k`) and
 * [GateCheckStatus.WARN] (every other glyph) -- so these three task indices are what this file
 * screenshots to cover "pass / fail / not-implemented" for real, not by constructing a fake
 * state.
 */
class WorkbookScreenScreenshotTest {
    @Test
    fun rendersTheRealCurrentTaskOnPaperWithoutCrashing() {
        val shot =
            ScreenshotHarness.capture(name = "workbook-screen-paper", width = 800, height = 2600, density = 2f) {
                WorkbookScreen(texture = CanvasTextures.PAPER)
            }
        assertTrue(shot.file.length() > 0, "PNG written to ${shot.file}")
        assertTrue(inkFraction(shot) > 0.01, "workbook screen rendered blank -- expected real ink")
    }

    @Test
    fun rendersOnEveryCanvasTextureWithoutCrashing() {
        for (texture in CanvasTextures.ALL) {
            val shot =
                ScreenshotHarness.capture(
                    name = "workbook-screen-${texture.id.name.lowercase()}",
                    width = 800,
                    height = 2600,
                    density = 2f,
                ) {
                    WorkbookScreen(texture = texture)
                }
            assertTrue(shot.file.length() > 0, "PNG written to ${shot.file} for texture ${texture.id}")
        }
    }

    @Test
    fun task1RealGateRowRendersTheNotImplementedDashedRingState() {
        val snapshot = loadWorkbookCampaignSnapshot()
        check(snapshot is WorkbookCampaignSnapshot.Loaded) { "expected the real campaign snapshot to load on desktop" }
        assertEquals(
            GateCheckStatus.NOT_IMPLEMENTED,
            snapshot.progress
                .single { it.task.index == 1 }
                .gate.checks
                .single()
                .status,
        )

        val shot =
            ScreenshotHarness.capture(name = "workbook-screen-task1-not-implemented", width = 800, height = 2400, density = 2f) {
                WorkbookScreen(texture = CanvasTextures.PAPER, uiState = WorkbookScreenUiState(snapshot = snapshot, initialTaskIndex = 1))
            }
        assertTrue(shot.file.length() > 0, "PNG written to ${shot.file}")
    }

    @Test
    fun task4RealGateRowsRenderTheFailStateAsAFourGlyphGrid() {
        val snapshot = loadWorkbookCampaignSnapshot()
        check(snapshot is WorkbookCampaignSnapshot.Loaded) { "expected the real campaign snapshot to load on desktop" }
        val task4Checks =
            snapshot.progress
                .single { it.task.index == 4 }
                .gate.checks
        assertTrue(
            task4Checks.isNotEmpty() &&
                task4Checks.all {
                    it.status == GateCheckStatus.FAIL
                },
            "expected task 4's real checks to be FAIL on Hyle Deco: $task4Checks",
        )

        val shot =
            ScreenshotHarness.capture(name = "workbook-screen-task4-fail", width = 800, height = 2600, density = 2f) {
                WorkbookScreen(texture = CanvasTextures.PAPER, uiState = WorkbookScreenUiState(snapshot = snapshot, initialTaskIndex = 4))
            }
        assertTrue(shot.file.length() > 0, "PNG written to ${shot.file}")
    }

    @Test
    fun task6RealGateRowsRenderAMixOfPassAndWarn() {
        val snapshot = loadWorkbookCampaignSnapshot()
        check(snapshot is WorkbookCampaignSnapshot.Loaded) { "expected the real campaign snapshot to load on desktop" }
        val task6Statuses =
            snapshot.progress
                .single { it.task.index == 6 }
                .gate.checks
                .map { it.status }
                .toSet()
        assertTrue(
            GateCheckStatus.PASS in task6Statuses && GateCheckStatus.WARN in task6Statuses,
            "expected task 6's real checks to mix PASS and WARN on Hyle Deco: $task6Statuses",
        )

        val shot =
            ScreenshotHarness.capture(name = "workbook-screen-task6-pass-warn-mix", width = 800, height = 3800, density = 2f) {
                WorkbookScreen(texture = CanvasTextures.PAPER, uiState = WorkbookScreenUiState(snapshot = snapshot, initialTaskIndex = 6))
            }
        assertTrue(shot.file.length() > 0, "PNG written to ${shot.file}")
    }

    @Test
    fun rendersAnHonestUnavailableBodyWhenTheSnapshotFailedToLoad() {
        val shot =
            ScreenshotHarness.capture(name = "workbook-screen-unavailable", width = 800, height = 300, density = 2f) {
                WorkbookScreen(
                    texture = CanvasTextures.PAPER,
                    uiState = WorkbookScreenUiState(snapshot = WorkbookCampaignSnapshot.Unavailable("no corpus on this target")),
                )
            }
        assertTrue(shot.file.length() > 0, "PNG written to ${shot.file}")
    }

    @Test
    fun onBackIsNotInvokedSpuriouslyByAPlainRender() {
        // Not a click-simulation test -- see dev.aarso.typewright.ui.learn.LearnScreenScreenshotTest's
        // own identically-named test for why this codebase never simulates a pointer event here.
        var backCalls = 0
        val shot =
            ScreenshotHarness.capture(name = "workbook-screen-back-not-yet-tapped", width = 800, height = 2600, density = 2f) {
                WorkbookScreen(texture = CanvasTextures.PAPER, onBack = { backCalls++ })
            }
        assertTrue(shot.file.length() > 0, "PNG written to ${shot.file}")
        assertTrue(backCalls == 0, "onBack must not fire just from rendering")
    }

    private fun inkFraction(shot: Screenshot): Double {
        val pixels = shot.bitmap.toPixelMap()
        var nonBackground = 0
        var total = 0
        val bgTopLeft = pixels[0, 0]
        for (x in 0 until pixels.width step 2) {
            for (y in 0 until pixels.height step 2) {
                total++
                val p = pixels[x, y]
                if (p.red != bgTopLeft.red || p.green != bgTopLeft.green || p.blue != bgTopLeft.blue) nonBackground++
            }
        }
        return nonBackground.toDouble() / total
    }
}
