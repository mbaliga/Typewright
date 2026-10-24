// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.ship

import com.asoc.typewright.qa.CheckOutcome
import com.asoc.typewright.qa.CheckStatus
import com.asoc.typewright.qa.LayerOneReport
import com.asoc.typewright.qa.report.ReportFixtures
import com.asoc.typewright.qa.report.ReviewOrder
import com.asoc.typewright.qa.unavailableReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShipGateTest {
    private val clean = ReportFixtures.report()

    /** A report with a licence fail and a names fail: no copyright line, no family name. */
    private val failing =
        ReportFixtures.report(info = ReportFixtures.cleanInfo.copy(copyright = null, familyName = null))

    private fun layerOne(vararg statuses: CheckStatus) =
        LayerOneReport(
            statuses.mapIndexed {
                i,
                status,
                ->
                CheckOutcome("com.google.fonts/check/c$i", status, "message $i\nmore", title = "Check $i")
            },
        )

    @Test
    fun aCleanReportDoesNotBlock() {
        assertEquals(0, clean.counts.fail, "the fixture must be clean for this test to mean anything")
        val gate = shipGate(clean)
        assertFalse(gate.blocked)
        assertEquals("Check has no fails", gate.title())
        assertEquals("Nothing blocks the build.", gate.explanation())
    }

    @Test
    fun everyLayerTwoFailBlocksAndIsNamed() {
        assertEquals(2, failing.counts.fail)
        val gate = shipGate(failing)
        assertTrue(gate.blocked)
        assertEquals(failing.counts.fail, gate.failCount)
        assertEquals(setOf(ReviewOrder.NAMES, ReviewOrder.LICENCE), gate.blockers.map { it.id }.toSet())
        assertTrue(gate.blockers.all { it.source == BlockerSource.LAYER_TWO })
        assertEquals("Check has 2 fails", gate.title())
        assertEquals("Build is blocked while Check has fails. 2 to go.", gate.blockedSentence())
        for (blocker in gate.blockers) {
            assertEquals(failing.item(blocker.id)?.title, blocker.title)
            assertEquals(firstSentence(failing.item(blocker.id)!!.summary), blocker.sentence)
            assertTrue(blocker.sentence.length <= 161, "a fail is one line: ${blocker.sentence}")
        }
    }

    @Test
    fun oneFailIsSingular() {
        val one = ReportFixtures.report(info = ReportFixtures.cleanInfo.copy(copyright = null))
        assertEquals(1, one.counts.fail)
        assertEquals("Check has 1 fail", shipGate(one).title())
    }

    @Test
    fun aCheckThatCouldNotMeasureNeverBlocks() {
        val noCorpus = ReportFixtures.report(corpus = null)
        assertTrue(noCorpus.counts.notMeasured > 0, "the fixture must hold a check that could not measure")
        assertEquals(0, noCorpus.counts.fail)
        assertFalse(shipGate(noCorpus).blocked)
    }

    @Test
    fun aLayerOneFailOnTheCompiledBinaryBlocksToo() {
        val gate = shipGate(clean, layerOne(CheckStatus.PASS, CheckStatus.FAIL, CheckStatus.WARN, CheckStatus.FAIL))
        assertEquals(2, gate.failCount)
        assertTrue(gate.blockers.all { it.source == BlockerSource.LAYER_ONE })
        assertEquals(listOf("Check 1", "Check 3"), gate.blockers.map { it.title })
        assertEquals("message 1", gate.blockers.first().sentence, "only the first line of a tool's message is a sentence")
    }

    @Test
    fun aLayerOneReportAttachedToTheReportCounts() {
        val gate = shipGate(clean.withLayerOne(layerOne(CheckStatus.FAIL)))
        assertEquals(1, gate.failCount)
    }

    @Test
    fun layerOneWarnsInfoAndSkipsDoNotBlock() {
        val gate = shipGate(clean, layerOne(CheckStatus.WARN, CheckStatus.INFO, CheckStatus.SKIP, CheckStatus.PASS))
        assertFalse(gate.blocked)
    }

    @Test
    fun aLayerOneThatDidNotRunContributesNothingAndIsNeverAPass() {
        val gate = shipGate(failing, unavailableReport("Layer one needs the desktop app"))
        assertEquals(failing.counts.fail, gate.failCount)
        val cleanGate = shipGate(clean, unavailableReport("Layer one needs the desktop app"))
        assertFalse(cleanGate.blocked)
    }

    @Test
    fun layerTwoAndLayerOneFailsAddUp() {
        val gate = shipGate(failing, layerOne(CheckStatus.FAIL))
        assertEquals(3, gate.failCount)
        assertEquals(listOf(BlockerSource.LAYER_TWO, BlockerSource.LAYER_TWO, BlockerSource.LAYER_ONE), gate.blockers.map { it.source })
    }

    @Test
    fun theTapExplanationNamesEveryFail() {
        val gate = shipGate(failing)
        val words = gate.explanation()
        for (blocker in gate.blockers) assertTrue(blocker.title in words, "${blocker.title} should be named in: $words")
        assertTrue(words.startsWith("Ship is blocked while Check has fails"))
    }

    @Test
    fun noReportMeansNoBlockersBecauseThereIsNothingToCount() {
        assertFalse(shipGate(null).blocked)
    }

    @Test
    fun aFindingIsReducedToItsFirstSentence() {
        assertEquals(
            "Your T has 1,763 points.",
            firstSentence("Your T has 1,763 points. In 30 geometric sans faces a T has 8 to 12.5, at most 28."),
        )
        assertEquals("A value of 12.5 is not a sentence end", firstSentence("A value of 12.5 is not a sentence end"))
        assertEquals("First line only.", firstSentence("First line only.\nSecond line."))
    }

    @Test
    fun aLongToolMessageIsCutAtAWord() {
        val long = "word ".repeat(100)
        val cut = firstSentence(long)
        assertTrue(cut.length <= 162, cut.length.toString())
        assertTrue(cut.endsWith("…"))
        assertFalse(cut.contains("wor…"), "cut at a word, not inside one")
        assertEquals("short", firstSentence("\n\n  short  \nsecond"))
    }
}
