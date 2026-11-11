// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.report

import com.asoc.typewright.qa.CheckOutcome
import com.asoc.typewright.qa.CheckStatus
import com.asoc.typewright.qa.LayerOneReport
import com.asoc.typewright.qa.checkNodeEconomy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CheckReportTest {
    private val fx = ReportFixtures

    @Test
    fun aCleanFontPassesAllFourteenChecks() {
        val report = fx.report()
        val notPassing = report.items.filter { it.severity != Severity.PASS }.map { "${it.id}: ${it.severity} ${it.summary}" }
        assertEquals(emptyList(), notPassing)
        assertEquals(14, report.items.size)
        assertEquals(CheckCounts(fail = 0, warn = 0, pass = 14, notMeasured = 0), report.counts)
    }

    @Test
    fun theFourteenChecksAreEachOnceAndFallInTheFiveGroups() {
        val report = fx.report()
        assertEquals(ReviewOrder.ALL.sorted(), report.items.map { it.id }.sorted())
        assertEquals(
            14,
            report.items
                .map { it.id }
                .toSet()
                .size,
        )
        assertEquals(
            mapOf(
                CheckGroup.OUTLINES to 10,
                CheckGroup.METRICS to 1,
                CheckGroup.PROVENANCE to 1,
                CheckGroup.NAMES to 1,
                CheckGroup.LICENCE to 1,
            ),
            report.items.groupingBy { it.group }.eachCount(),
        )
        assertEquals(listOf("Outlines", "Metrics", "Provenance", "Names", "Licence"), report.groups.map { it.group.title })
        for (item in report.items) {
            assertTrue(item.summary.isNotBlank() && item.raw.isNotBlank() && item.basisNote.isNotBlank(), item.id)
            assertTrue(item.id.startsWith(item.group.title.lowercase() + "/"), "${item.id} is not in its group ${item.group}")
        }
    }

    @Test
    fun theCountsAreWorkedOutFromTheItemsEveryTimeNotStored() {
        val report = fx.report()
        val worse = report.copy(items = report.items.mapIndexed { i, item -> if (i < 3) item.copy(severity = Severity.FAIL) else item })
        assertEquals(0, report.counts.fail)
        assertEquals(3, worse.counts.fail)
        assertEquals(11, worse.counts.pass)
        assertEquals("3 fail · 0 warn · 11 pass", worse.counts.line())
    }

    @Test
    fun theCountLineAddsNotMeasuredOnlyWhenThereIsOne() {
        assertEquals("4 fail · 4 warn · 6 pass", CheckCounts(4, 4, 6, 0).line())
        assertEquals("4 fail · 4 warn · 5 pass · 1 not measured", CheckCounts(4, 4, 5, 1).line())
    }

    @Test
    fun theSeverityBarIsSolidThenHatchedThenPlainAndNeverColour() {
        val bar = CheckCounts(4, 4, 6, 0).bar()
        assertEquals(4 / 14.0, bar.solid)
        assertEquals(4 / 14.0, bar.hatched)
        assertEquals(6 / 14.0, bar.plain)
        assertEquals(1.0, bar.solid + bar.hatched + bar.plain, 1e-9)
        // What could not be measured is not drawn as a pass or a problem: it joins the plain run.
        assertEquals(7 / 14.0, CheckCounts(4, 3, 6, 1).bar().plain)
        assertEquals(SeverityBar(0.0, 0.0, 0.0), CheckCounts(0, 0, 0, 0).bar())
    }

    @Test
    fun everySeverityIsAShapeAndAWord() {
        assertEquals(listOf("●", "◐", "✓", "○"), Severity.entries.map { it.shape })
        assertEquals(listOf("fail", "warn", "pass", "not measured"), Severity.entries.map { it.word })
    }

    // ---- the order: what a reviewer attacks first -------------------------------------------------

    private fun stub(
        id: String,
        group: CheckGroup,
        severity: Severity,
    ) = CheckItem(id, group, id, null, severity, "s", null, null, Basis.HEURISTIC, "n", "r")

    @Test
    fun groupsRunOutlinesMetricsProvenanceNamesLicenceWhateverTheSeverities() {
        val shuffled =
            listOf(
                stub("licence/a", CheckGroup.LICENCE, Severity.FAIL),
                stub("names/a", CheckGroup.NAMES, Severity.FAIL),
                stub("provenance/a", CheckGroup.PROVENANCE, Severity.WARN),
                stub("metrics/a", CheckGroup.METRICS, Severity.PASS),
                stub("outlines/a", CheckGroup.OUTLINES, Severity.PASS),
            )
        assertEquals(
            listOf(CheckGroup.OUTLINES, CheckGroup.METRICS, CheckGroup.PROVENANCE, CheckGroup.NAMES, CheckGroup.LICENCE),
            ReviewOrder.sorted(shuffled).map { it.group },
        )
    }

    @Test
    fun insideAGroupFailsComeBeforeWarningsBeforeNotMeasuredBeforePasses() {
        val shuffled =
            listOf(
                stub(ReviewOrder.ANCHORS, CheckGroup.OUTLINES, Severity.PASS),
                stub(ReviewOrder.EXTREMA, CheckGroup.OUTLINES, Severity.NOT_MEASURED),
                stub(ReviewOrder.JAGGY, CheckGroup.OUTLINES, Severity.WARN),
                stub(ReviewOrder.CURVES_ARE_POLYGONS, CheckGroup.OUTLINES, Severity.FAIL),
            )
        assertEquals(
            listOf(Severity.FAIL, Severity.WARN, Severity.NOT_MEASURED, Severity.PASS),
            ReviewOrder.sorted(shuffled).map { it.severity },
        )
    }

    @Test
    fun equalSeveritiesKeepTheOrderAReviewerMeetsThem() {
        val allWarn =
            ReviewOrder.ALL.reversed().map { id ->
                stub(id, CheckGroup.entries.first { id.startsWith(it.title.lowercase() + "/") }, Severity.WARN)
            }
        val sorted = ReviewOrder.sorted(allWarn).map { it.id }
        assertEquals(ReviewOrder.ALL, sorted)
        assertEquals(ReviewOrder.NODE_ECONOMY, sorted.first(), "the point count is what a reviewer meets first")
    }

    @Test
    fun theBuiltReportIsInThatOrderForARealMixOfFindings() {
        val polygonO = fx.glyph("o", fx.polygon(0 to 0, 0 to 500, 400 to 500, 400 to 0), unicodes = listOf(0x6F))
        val report = fx.report(glyphs = listOf(fx.cleanT, polygonO))
        val keys = report.items.map { Triple(it.group.ordinal, severityRank(it.severity), ReviewOrder.ALL.indexOf(it.id)) }
        assertEquals(keys.sortedWith(compareBy({ it.first }, { it.second }, { it.third })), keys, report.items.map { it.id }.toString())
        assertTrue(
            report.items
                .map { it.severity }
                .toSet()
                .size > 1,
            "the fixture needs a mix of findings for the order to mean anything",
        )
    }

    private fun severityRank(severity: Severity) =
        listOf(Severity.FAIL, Severity.WARN, Severity.NOT_MEASURED, Severity.PASS).indexOf(severity)

    // ---- determinism, economy, layer one ------------------------------------------------------------

    @Test
    fun theSameProjectGivesAnEqualReportEveryTime() {
        val a = fx.report(glyphs = listOf(fx.cleanT, fx.glyph("o", fx.tracedRectangle(400), unicodes = listOf(0x6F))))
        val b = fx.report(glyphs = listOf(fx.cleanT, fx.glyph("o", fx.tracedRectangle(400), unicodes = listOf(0x6F))))
        assertEquals(a, b)
        assertEquals(a.items.map { it.id }, b.items.map { it.id })
    }

    @Test
    fun theReportsEconomyIsExactlyWhatCheckNodeEconomyAnswers() {
        val inputs = fx.inputs(glyphs = listOf(fx.cleanT, fx.glyph("o", fx.tracedRectangle(400), unicodes = listOf(0x6F))))
        val report = buildCheckReport(inputs)
        assertEquals(checkNodeEconomy(inputs.project, fx.STYLE_KEY, fx.corpus), report.economy)
        assertNotEquals(null, report.economy)
    }

    @Test
    fun withNoClassOrNoCorpusThereIsNoEconomyAndTheItemSaysItWasNotMeasured() {
        for (report in listOf(fx.report(style = null), fx.report(corpus = null), fx.report(style = fx.style.copy(key = "no-such-class")))) {
            assertNull(report.economy)
            assertEquals(Severity.NOT_MEASURED, report.item(ReviewOrder.NODE_ECONOMY)?.severity)
        }
    }

    @Test
    fun layerOneIsAttachedWithoutChangingTheLayerTwoItems() {
        val report = fx.report()
        val layerOne = LayerOneReport(listOf(CheckOutcome("x", CheckStatus.FAIL, "no")))
        val attached = report.withLayerOne(layerOne)
        assertSame(layerOne, attached.layerOne)
        assertEquals(report.items, attached.items)
        assertNull(report.layerOne)
        assertEquals(report.counts, attached.counts, "layer one's results are counted on their own, not folded into the fourteen")
    }

    @Test
    fun whatTheseChecksDoNotMeasureIsHonestAndShort() {
        val text = NotMeasured.PARAGRAPHS.joinToString(" ")
        assertTrue("will not break" in text && "not \"is good\"" in text)
        assertTrue("tangent continuity" in text && "spacing" in text)
        assertTrue("our heuristic" in text)
        assertTrue(NotMeasured.PARAGRAPHS.size == 3 && text.length < 1_500)
    }
}
