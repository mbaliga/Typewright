// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.campaign

import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.core.font.ufo.UfoProject
import com.asoc.typewright.core.geometry.Anchor
import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.ContourPoint
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.qa.corpus.FamilyEntry
import com.asoc.typewright.qa.corpus.GlyphDist
import com.asoc.typewright.qa.corpus.NodeEconomyCorpus
import com.asoc.typewright.qa.corpus.NodeEconomyPack
import com.asoc.typewright.qa.corpus.Quartiles
import com.asoc.typewright.qa.corpus.StyleClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Mirrors `WorkbookGatesTest.kt`'s own `rectContour` fixture exactly -- a clean, straight-sided, all-on-curve polygon. */
private fun rectContour(
    x0: Int,
    y0: Int,
    x1: Int,
    y1: Int,
): Contour =
    Contour(
        listOf(
            ContourPoint(Point(x0, y0), true),
            ContourPoint(Point(x1, y0), true),
            ContourPoint(Point(x1, y1), true),
            ContourPoint(Point(x0, y1), true),
        ),
        CurveFormat.QUADRATIC,
    )

private fun fixtureFontInfo(familyName: String = "Fixture") = UfoFontInfo(familyName = familyName, xHeight = 500, capHeight = 700)

/** A tiny node-economy corpus with one style class ([DEFAULT_TASK4_STYLE_KEY]) and one glyph ("n"), its box centred on 4 on-curve points -- matches [rectContour]'s own 4-point rectangle exactly, so a fixture "n" built from one rectangle reads IN_RANGE. */
private fun fixtureCorpus(): NodeEconomyCorpus =
    NodeEconomyCorpus(
        NodeEconomyPack(
            source = "test",
            glyphs = "on",
            styles =
                mapOf(
                    DEFAULT_TASK4_STYLE_KEY to
                        StyleClass(
                            tags = listOf("/Test"),
                            families = listOf(FamilyEntry("Alpha", "alpha.ttf", "quadratic", 90.0, mapOf("n" to listOf(4, 0, 1)))),
                            dist =
                                mapOf(
                                    "n" to
                                        GlyphDist(
                                            on = Quartiles(min = 2.0, q1 = 3.0, med = 4.0, q3 = 4.0, max = 6.0, n = 1),
                                            off = Quartiles(min = 0.0, q1 = 0.0, med = 0.0, q3 = 0.0, max = 0.0, n = 1),
                                        ),
                                ),
                        ),
                ),
        ),
    )

/** Real fixture [WorkbookGateResult]s this file builds by hand (no project/corpus needed) to test [campaignProgress] in isolation from [runWorkbookGate]. */
private fun passingGate(index: Int): WorkbookGateResult =
    WorkbookGateResult(index, "fixture", listOf(GateCheckResult("x", GateCheckStatus.PASS, "ok")))

private fun failingGate(index: Int): WorkbookGateResult =
    WorkbookGateResult(index, "fixture", listOf(GateCheckResult("x", GateCheckStatus.FAIL, "no")))

private fun notImplementedGate(index: Int): WorkbookGateResult = WorkbookGates.notImplementedGate(index, "fixture", "no check")

class WorkbookProgressTest {
    // -- isFullyPassing --

    @Test
    fun fullyPassingRequiresAtLeastOneCheckAndAllPass() {
        assertTrue(passingGate(1).isFullyPassing())
        assertFalse(failingGate(1).isFullyPassing())
        assertFalse(notImplementedGate(1).isFullyPassing())
        assertFalse(WorkbookGateResult(1, "empty", emptyList()).isFullyPassing())
    }

    @Test
    fun aMixOfPassAndWarnIsNotFullyPassing() {
        val mixed =
            WorkbookGateResult(
                5,
                "mixed",
                listOf(GateCheckResult("a", GateCheckStatus.PASS, "ok"), GateCheckResult("b", GateCheckStatus.WARN, "hm")),
            )
        assertFalse(mixed.isFullyPassing())
    }

    // -- campaignProgress --

    @Test
    fun firstNonDoneTaskIsCurrentAndEveryTaskAfterItIsTodoRegardlessOfItsOwnGate() {
        val tasks = WorkbookLatinContent.load()
        // Tasks 1-3 pass, task 4 fails, task 12 (later in index order) would pass on its own --
        // it must still read TODO, never jump ahead of task 4.
        val gates =
            tasks.associate { task ->
                task.index to
                    when (task.index) {
                        1, 2, 3 -> passingGate(task.index)
                        4 -> failingGate(4)
                        else -> passingGate(task.index)
                    }
            }

        val progress = campaignProgress(tasks, gates)

        assertEquals(WorkbookTaskState.DONE, progress.single { it.task.index == 1 }.state)
        assertEquals(WorkbookTaskState.DONE, progress.single { it.task.index == 2 }.state)
        assertEquals(WorkbookTaskState.DONE, progress.single { it.task.index == 3 }.state)
        assertEquals(WorkbookTaskState.CURRENT, progress.single { it.task.index == 4 }.state)
        for (index in 5..12) {
            assertEquals(WorkbookTaskState.TODO, progress.single { it.task.index == index }.state, "task $index should be TODO")
        }
        assertEquals(4, currentWorkbookTaskIndex(progress))
    }

    @Test
    fun aTaskWithNoImplementedGateCanNeverReadDone() {
        val tasks = WorkbookLatinContent.load()
        // Every task reports NOT_IMPLEMENTED, including the ones that really do have gates --
        // this test is only about campaignProgress's own rule, not runWorkbookGate.
        val gates = tasks.associate { it.index to notImplementedGate(it.index) }

        val progress = campaignProgress(tasks, gates)

        assertTrue(progress.none { it.state == WorkbookTaskState.DONE })
        assertEquals(WorkbookTaskState.CURRENT, progress.first().state)
        assertEquals(1, currentWorkbookTaskIndex(progress))
    }

    @Test
    fun whenEveryTaskIsDoneTheLastTaskStaysTheReportedCurrentOne() {
        val tasks = WorkbookLatinContent.load()
        val gates = tasks.associate { it.index to passingGate(it.index) }

        val progress = campaignProgress(tasks, gates)

        assertTrue(progress.all { it.state == WorkbookTaskState.DONE })
        assertEquals(12, currentWorkbookTaskIndex(progress))
    }

    @Test
    fun aMissingGateResultIsTreatedAsHonestlyNotImplementedRatherThanCrashing() {
        val tasks = WorkbookLatinContent.load()
        val progress = campaignProgress(tasks, gates = emptyMap())
        assertTrue(progress.all { it.state != WorkbookTaskState.DONE })
        assertEquals(
            GateCheckStatus.NOT_IMPLEMENTED,
            progress
                .first()
                .gate.checks
                .single()
                .status,
        )
    }

    // -- confirmedTasks (§10.3) --

    @Test
    fun isTaskConfirmableIsTrueForAJudgmentCallTaskAndForAllInfoChecksOnly() {
        val tasks = WorkbookLatinContent.load()
        // Tasks 1/2/7/8/10 have no implemented gate at all -- confirmable regardless of the
        // (fixture) result passed in, since the rule is "!task.gate.isImplemented OR all INFO".
        assertTrue(isTaskConfirmable(task(1), notImplementedGate(1)))
        // Task 3's real gate is always INFO-only by its own documented contract.
        assertTrue(isTaskConfirmable(task(3), WorkbookGateResult(3, "fixture", listOf(GateCheckResult("o", GateCheckStatus.INFO, "-")))))
        // Task 4 has a real, implemented gate whose checks are PASS/WARN/FAIL, never INFO.
        assertFalse(isTaskConfirmable(task(4), passingGate(4)))
        // Task 11 is implemented but, without a compiled font, reads NOT_IMPLEMENTED -- neither branch.
        assertFalse(isTaskConfirmable(task(11), notImplementedGate(11)))
        assertFalse(isTaskConfirmable(task(3), WorkbookGateResult(3, "fixture", emptyList())))
    }

    @Test
    fun aConfirmedConfirmableTaskReadsDoneAndUnblocksTheTasksAfterIt() {
        val tasks = WorkbookLatinContent.load()
        // Every task reports not-implemented (so none pass a real gate); task 1 is confirmed.
        val gates = tasks.associate { it.index to notImplementedGate(it.index) }

        val progress = campaignProgress(tasks, gates, confirmedTasks = setOf(1))

        assertEquals(WorkbookTaskState.DONE, progress.single { it.task.index == 1 }.state)
        assertEquals(WorkbookTaskState.CURRENT, progress.single { it.task.index == 2 }.state)
    }

    @Test
    fun confirmingTask11NeverMakesItDoneBecauseItIsNotConfirmable() {
        val tasks = WorkbookLatinContent.load()
        val gates = tasks.associate { it.index to notImplementedGate(it.index) }

        val progress = campaignProgress(tasks, gates, confirmedTasks = setOf(11))

        assertTrue(progress.none { it.state == WorkbookTaskState.DONE })
    }

    @Test
    fun confirmedTasksDefaultsToEmptySoExistingCallersAreUnaffected() {
        val tasks = WorkbookLatinContent.load()
        val gates = tasks.associate { it.index to notImplementedGate(it.index) }

        assertEquals(campaignProgress(tasks, gates), campaignProgress(tasks, gates, confirmedTasks = emptySet()))
    }

    // -- runWorkbookGate dispatch --

    private val realTasks = WorkbookLatinContent.load()

    private fun task(index: Int): WorkbookTask = realTasks.single { it.index == index }

    @Test
    fun dispatchesJudgmentCallTasksToTheirOwnNotImplementedReasonVerbatim() {
        val project = UfoProject(fixtureFontInfo(), emptyList())
        val corpus = fixtureCorpus()
        for (index in listOf(1, 2, 7, 8, 10)) {
            val t = task(index)
            val result = runWorkbookGate(t, project, corpus)
            assertEquals(GateCheckStatus.NOT_IMPLEMENTED, result.checks.single().status)
            assertEquals(
                t.gate.notImplementedReason?.trim(),
                result.checks
                    .single()
                    .detail
                    .trim(),
            )
        }
    }

    @Test
    fun dispatchesTask3ToOvershootPresence() {
        val roundGlyph = Glyph("o", advanceWidth = 500, contours = listOf(rectContour(0, 0, 400, 500)))
        val project = UfoProject(fixtureFontInfo(), listOf(roundGlyph))

        val result = runWorkbookGate(task(3), project, fixtureCorpus())

        assertEquals(3, result.taskIndex)
        assertEquals(GateCheckStatus.INFO, result.checks.single { it.label == "o" }.status)
    }

    @Test
    fun dispatchesTask4ToNodeEconomyAgainstTheDefaultStyleKey() {
        val inRangeN = Glyph("n", advanceWidth = 500, contours = listOf(rectContour(0, 0, 4, 4)))
        val project = UfoProject(fixtureFontInfo(), listOf(inRangeN))

        val result = runWorkbookGate(task(4), project, fixtureCorpus())

        assertEquals(4, result.taskIndex)
        assertTrue(result.gateSummary.contains(DEFAULT_TASK4_STYLE_KEY))
        assertEquals(GateCheckStatus.PASS, result.checks.single { it.label == "n" }.status)
    }

    @Test
    fun dispatchesTask5And6ToGeometricSanity() {
        val clean = Glyph("b", advanceWidth = 500, contours = listOf(rectContour(0, 0, 100, 500)))
        val project = UfoProject(fixtureFontInfo(), listOf(clean))

        val result5 = runWorkbookGate(task(5), project, fixtureCorpus())
        assertEquals(5, result5.taskIndex)
        assertEquals(GateCheckStatus.PASS, result5.checks.single { it.label == "b" }.status)

        val result6 = runWorkbookGate(task(6), project, fixtureCorpus())
        assertEquals(6, result6.taskIndex)
    }

    @Test
    fun dispatchesTask9ToAnchorPresence() {
        val withAnchor = Glyph("A", advanceWidth = 500, contours = emptyList(), anchors = listOf(Anchor("top", Point(250, 700))))
        val project = UfoProject(fixtureFontInfo(), listOf(withAnchor))

        val result = runWorkbookGate(task(9), project, fixtureCorpus())

        assertEquals(9, result.taskIndex)
        assertEquals(GateCheckStatus.PASS, result.checks.single { it.label == "A" }.status)
    }

    @Test
    fun task11NeverCallsTheRealCheckerAndAlwaysReportsNoCompiledFont() {
        val project = UfoProject(fixtureFontInfo(), emptyList())

        val result = runWorkbookGate(task(11), project, fixtureCorpus())

        assertEquals(11, result.taskIndex)
        val check = result.checks.single()
        assertEquals(GateCheckStatus.NOT_IMPLEMENTED, check.status)
        assertTrue(check.detail.contains("no compiled font"), check.detail)
    }

    @Test
    fun task12UsesTheSuppliedShipMetadataNotThePlaceholderDefault() {
        val project = UfoProject(fixtureFontInfo("Hyle Deco"), emptyList())
        val metadata = ShipMetadata(family = "Hyle Deco", designer = "Madhav", year = 2026, gitUrl = "https://github.com/mbaliga/hyle-deco")

        val result = runWorkbookGate(task(12), project, fixtureCorpus(), shipMetadata = metadata)

        assertEquals(12, result.taskIndex)
        assertTrue(
            result.checks.all {
                it.status == GateCheckStatus.PASS
            },
            "expected every ship check to pass on matching metadata: ${result.checks}",
        )
        val oflCheck = result.checks.single { it.label == "OFL.txt" }
        assertTrue(oflCheck.detail.contains("Copyright 2026 The Hyle Deco Project Authors (https://github.com/mbaliga/hyle-deco)"))
    }

    @Test
    fun task12FallsBackToAPlaceholderShipMetadataWhenNoneIsSupplied() {
        val metadata = ShipMetadata.from(fixtureFontInfo("Fixture Font"))
        assertEquals("Fixture Font", metadata.family)
        assertEquals(ShipMetadata.UNKNOWN_YEAR, metadata.year)
    }

    @Test
    fun runAllWorkbookGatesCoversEveryTaskIndexOnceEach() {
        val project = UfoProject(fixtureFontInfo(), emptyList())
        val gates = runAllWorkbookGates(realTasks, project, fixtureCorpus())
        assertEquals((1..12).toSet(), gates.keys)
    }
}
