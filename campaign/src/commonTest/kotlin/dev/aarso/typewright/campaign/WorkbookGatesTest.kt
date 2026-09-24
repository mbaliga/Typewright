package dev.aarso.typewright.campaign

import dev.aarso.typewright.core.font.ufo.UfoFontInfo
import dev.aarso.typewright.core.font.ufo.UfoProject
import dev.aarso.typewright.core.geometry.Anchor
import dev.aarso.typewright.core.geometry.Contour
import dev.aarso.typewright.core.geometry.ContourPoint
import dev.aarso.typewright.core.geometry.CurveFormat
import dev.aarso.typewright.core.geometry.Glyph
import dev.aarso.typewright.core.geometry.Point
import dev.aarso.typewright.qa.CheckOutcome
import dev.aarso.typewright.qa.CheckStatus
import dev.aarso.typewright.qa.LayerOneAvailability
import dev.aarso.typewright.qa.LayerOneChecker
import dev.aarso.typewright.qa.LayerOneInput
import dev.aarso.typewright.qa.LayerOneReport
import dev.aarso.typewright.qa.corpus.FamilyEntry
import dev.aarso.typewright.qa.corpus.GlyphDist
import dev.aarso.typewright.qa.corpus.NodeEconomyCorpus
import dev.aarso.typewright.qa.corpus.NodeEconomyPack
import dev.aarso.typewright.qa.corpus.Quartiles
import dev.aarso.typewright.qa.corpus.StyleClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A closed rectangle from (x0,y0) to (x1,y1), all four points on-curve -- a clean, straight-sided
 * polygon fixture. [CurveFormat.QUADRATIC], not CUBIC: an all-on-curve polygon has no (on, off,
 * off) triples for CUBIC's own invariant to hold ([Contour]'s own `init`), and QUADRATIC has no
 * such requirement (a TrueType contour may be all on-curve, exactly like these real, traced-
 * polygon fixtures mean to represent).
 */
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

private fun fixtureFontInfo() = UfoFontInfo(familyName = "Fixture", xHeight = 500, capHeight = 700)

private fun fixtureNodeEconomyCorpus(): NodeEconomyCorpus =
    NodeEconomyCorpus(
        NodeEconomyPack(
            source = "test",
            glyphs = "on",
            styles =
                mapOf(
                    "test-style" to
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

/** A fake [LayerOneChecker] this test controls, mirroring `qa`'s own `LayerOneCheckerTest` pattern. */
private class FakeLayerOneChecker(
    override val name: String = "Fake",
    private val availabilityResult: LayerOneAvailability = LayerOneAvailability.Available,
    private val checkResult: LayerOneReport = LayerOneReport(emptyList()),
) : LayerOneChecker {
    override suspend fun availability(): LayerOneAvailability = availabilityResult

    override suspend fun check(input: LayerOneInput): LayerOneReport = checkResult
}

class WorkbookGatesTest {
    @Test
    fun task3ReportsOvershootFindingsAsInfoAndFlagsAMissingGlyphAsNotImplemented() {
        val roundGlyph =
            Glyph(
                "o",
                advanceWidth = 500,
                contours = listOf(rectContour(0, 0, 400, 500), rectContour(100, 100, 300, 400)),
            )
        val project = UfoProject(fixtureFontInfo(), listOf(roundGlyph))

        val result = WorkbookGates.task3SetMetrics(project, glyphNames = listOf("o", "missing"))

        assertEquals(3, result.taskIndex)
        val oCheck = result.checks.single { it.label == "o" }
        assertEquals(GateCheckStatus.INFO, oCheck.status)
        assertTrue(oCheck.detail.contains("flat"), "expected a flat-top/flat-bottom finding, got: ${oCheck.detail}")

        val missingCheck = result.checks.single { it.label == "missing" }
        assertEquals(GateCheckStatus.NOT_IMPLEMENTED, missingCheck.status)
    }

    @Test
    fun task4MapsNodeEconomyVerdictsToPassWarnFailAndReportsAMissingGlyphAsNotImplemented() {
        val inRangeGlyph = Glyph("n", advanceWidth = 500, contours = listOf(rectContour(0, 0, 4, 4)))
        // 4 on-curve points from one rectangle -> matches the fixture box's own median (4), IN_RANGE.
        val project = UfoProject(fixtureFontInfo(), listOf(inRangeGlyph))

        val result =
            WorkbookGates.task4ControlCharacters(
                project,
                fixtureNodeEconomyCorpus(),
                "test-style",
                glyphNames = listOf("n", "missing"),
            )

        assertEquals(4, result.taskIndex)
        val nCheck = result.checks.single { it.label == "n" }
        assertEquals(GateCheckStatus.PASS, nCheck.status)
        assertTrue(nCheck.detail.contains("'n'"))

        val missingCheck = result.checks.single { it.label == "missing" }
        assertEquals(GateCheckStatus.NOT_IMPLEMENTED, missingCheck.status)
    }

    @Test
    fun task4FlagsAFarOversizedGlyphAsFail() {
        // 40 on-curve points (via 10 stacked rectangles is overkill; build one long polygon).
        val manyPointsContour =
            Contour(
                (0 until 40).map { i -> ContourPoint(Point(i, i % 2), true) },
                CurveFormat.QUADRATIC,
            )
        val glyph = Glyph("n", advanceWidth = 500, contours = listOf(manyPointsContour))
        val project = UfoProject(fixtureFontInfo(), listOf(glyph))

        val result = WorkbookGates.task4ControlCharacters(project, fixtureNodeEconomyCorpus(), "test-style", glyphNames = listOf("n"))

        assertEquals(GateCheckStatus.FAIL, result.checks.single().status)
    }

    @Test
    fun task5And6ReportPassForACleanRectangleAndWarnForAnAlignmentMiss() {
        val clean = Glyph("b", advanceWidth = 500, contours = listOf(rectContour(0, 0, 100, 500)))
        val nearMissContour =
            Contour(
                listOf(
                    ContourPoint(Point(0, 1), true),
                    ContourPoint(Point(100, 0), true),
                    ContourPoint(Point(100, 500), true),
                    ContourPoint(Point(0, 500), true),
                ),
                CurveFormat.QUADRATIC,
            )
        val nearMiss = Glyph("d", advanceWidth = 500, contours = listOf(nearMissContour))
        val project = UfoProject(fixtureFontInfo(), listOf(clean, nearMiss))

        val result5 = WorkbookGates.task5DeriveTheFamily(project, glyphNames = listOf("b", "d", "missing"))
        assertEquals(5, result5.taskIndex)
        assertEquals(GateCheckStatus.PASS, result5.checks.single { it.label == "b" }.status)
        assertEquals(GateCheckStatus.WARN, result5.checks.single { it.label == "d" }.status)
        assertEquals(GateCheckStatus.NOT_IMPLEMENTED, result5.checks.single { it.label == "missing" }.status)

        val result6 = WorkbookGates.task6HardLetters(project, glyphNames = listOf("b", "d"))
        assertEquals(6, result6.taskIndex)
        assertEquals(GateCheckStatus.PASS, result6.checks.single { it.label == "b" }.status)
        assertEquals(GateCheckStatus.WARN, result6.checks.single { it.label == "d" }.status)
    }

    @Test
    fun task9PassesAGlyphWithATopAnchorAndWarnsOnOneWithNone() {
        val withAnchor = Glyph("A", advanceWidth = 500, contours = emptyList(), anchors = listOf(Anchor("top", Point(250, 700))))
        val withoutAnchor = Glyph("B", advanceWidth = 500, contours = emptyList())
        val project = UfoProject(fixtureFontInfo(), listOf(withAnchor, withoutAnchor))

        val result = WorkbookGates.task9DiacriticsAndAnchors(project)

        assertEquals(9, result.taskIndex)
        assertEquals(GateCheckStatus.PASS, result.checks.single { it.label == "A" }.status)
        assertEquals(GateCheckStatus.WARN, result.checks.single { it.label == "B" }.status)
    }

    @Test
    fun task11ReportsNotImplementedWhenTheCheckerIsUnavailable() {
        val checker = FakeLayerOneChecker(availabilityResult = LayerOneAvailability.Unavailable("no binary on PATH"))

        val result = runSuspend { WorkbookGates.task11Test(checker, compiled = null) }

        assertEquals(11, result.taskIndex)
        val check = result.checks.single()
        assertEquals(GateCheckStatus.NOT_IMPLEMENTED, check.status)
        assertEquals("no binary on PATH", check.detail)
    }

    @Test
    fun task11ReportsNotImplementedWhenAvailableButNothingWasCompiledYet() {
        val checker = FakeLayerOneChecker(availabilityResult = LayerOneAvailability.Available)

        val result = runSuspend { WorkbookGates.task11Test(checker, compiled = null) }

        assertEquals(GateCheckStatus.NOT_IMPLEMENTED, result.checks.single().status)
    }

    @Test
    fun task11MapsEveryRealCheckStatusThroughToTheWorkbookVocabulary() {
        val checker =
            FakeLayerOneChecker(
                availabilityResult = LayerOneAvailability.Available,
                checkResult =
                    LayerOneReport(
                        listOf(
                            CheckOutcome("a", CheckStatus.PASS, "ok"),
                            CheckOutcome("b", CheckStatus.WARN, "hm"),
                            CheckOutcome("c", CheckStatus.FAIL, "no"),
                            CheckOutcome("d", CheckStatus.SKIP, "n/a"),
                        ),
                    ),
            )

        val result = runSuspend { WorkbookGates.task11Test(checker, compiled = LayerOneInput("font.ttf", byteArrayOf(1))) }

        assertEquals(
            mapOf(
                "a" to GateCheckStatus.PASS,
                "b" to GateCheckStatus.WARN,
                "c" to GateCheckStatus.FAIL,
                "d" to GateCheckStatus.NOT_IMPLEMENTED,
            ),
            result.checks.associate { it.label to it.status },
        )
    }

    @Test
    fun task12GeneratesRealFilesThatCarryTheRealFamilyDesignerAndCopyright() {
        val fontInfo = UfoFontInfo(familyName = "Test Sans", styleName = "Regular")

        val result =
            WorkbookGates.task12Ship(
                fontInfo,
                family = "Test Sans",
                designer = "A Designer",
                year = 2026,
                gitUrl = "https://github.com/x/test-sans",
            )

        assertEquals(12, result.taskIndex)
        assertEquals(3, result.checks.size)
        assertTrue(
            result.checks.all {
                it.status == GateCheckStatus.PASS
            },
            "expected every ship sanity check to pass on matching inputs: ${result.checks}",
        )
        val oflCheck = result.checks.single { it.label == "OFL.txt" }
        assertTrue(oflCheck.detail.contains("Copyright 2026 The Test Sans Project Authors (https://github.com/x/test-sans)"))
    }

    @Test
    fun notImplementedGateCarriesExactlyOneNotImplementedCheck() {
        val result = WorkbookGates.notImplementedGate(1, "no automated check yet", "a judgment call")
        assertEquals(1, result.checks.size)
        assertEquals(GateCheckStatus.NOT_IMPLEMENTED, result.checks.single().status)
        assertEquals(false, result.isImplemented)
    }

    @Test
    fun gateResultCountsTallyEachStatusIndependently() {
        val result =
            WorkbookGateResult(
                4,
                "s",
                listOf(
                    GateCheckResult("a", GateCheckStatus.PASS, "x"),
                    GateCheckResult("b", GateCheckStatus.PASS, "x"),
                    GateCheckResult("c", GateCheckStatus.FAIL, "x"),
                ),
            )
        assertEquals(2, result.counts[GateCheckStatus.PASS])
        assertEquals(1, result.counts[GateCheckStatus.FAIL])
        assertEquals(0, result.counts[GateCheckStatus.WARN])
        assertTrue(result.isImplemented)
    }
}
