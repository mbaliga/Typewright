// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.qa

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LayerOneCheckerTest {
    @Test
    fun countsTallyEachStatusIndependently() {
        val results =
            listOf(
                CheckOutcome("a", CheckStatus.PASS, "ok"),
                CheckOutcome("b", CheckStatus.PASS, "ok"),
                CheckOutcome("c", CheckStatus.WARN, "hm"),
                CheckOutcome("d", CheckStatus.FAIL, "no"),
                CheckOutcome("e", CheckStatus.SKIP, "n/a"),
            )
        val counts = LayerOneCounts.of(results)
        assertEquals(LayerOneCounts(pass = 2, warn = 1, fail = 1, skip = 1), counts)
    }

    @Test
    fun aReportComputesItsCountsFromItsOwnResultsByDefault() {
        val report = LayerOneReport(listOf(CheckOutcome("a", CheckStatus.FAIL, "no")))
        assertEquals(LayerOneCounts(pass = 0, warn = 0, fail = 1, skip = 0), report.counts)
    }

    /**
     * The platform actual never throws, on either target this repository builds for, whether or
     * not a real Fontspector binary happens to be reachable -- CLAUDE.md law 4: a stub reports
     * itself unavailable rather than crashing the caller.
     */
    @Test
    fun thePlatformCheckerNeverThrowsAndAlwaysReturnsAReport() {
        val checker = platformLayerOneChecker()
        assertTrue(checker.name.isNotBlank())
        runSuspend { checker.availability() } // must not throw
        val report = runSuspend { checker.check(LayerOneInput("font.ttf", byteArrayOf(1, 2, 3))) }
        assertTrue(report.results.isNotEmpty())
        assertEquals(report.results.size, report.counts.pass + report.counts.warn + report.counts.fail + report.counts.skip)
    }
}
