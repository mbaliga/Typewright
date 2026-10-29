// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.ship

import com.asoc.typewright.qa.CheckOutcome
import com.asoc.typewright.qa.CheckStatus
import com.asoc.typewright.qa.LayerOneReport
import com.asoc.typewright.qa.report.Basis
import com.asoc.typewright.qa.report.CheckAction
import com.asoc.typewright.qa.report.CheckGroup
import com.asoc.typewright.qa.report.CheckItem
import com.asoc.typewright.qa.report.CheckReport
import com.asoc.typewright.qa.report.CheckRoute
import com.asoc.typewright.qa.report.FontInfoTarget
import com.asoc.typewright.qa.report.ReportFixtures
import com.asoc.typewright.qa.report.ReviewOrder
import com.asoc.typewright.qa.report.Severity
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
    fun noReportBlocksBecauseCheckHasNotRunAndNothingMakesUpForIt() {
        val gate = shipGate(null)
        assertTrue(gate.blocked, "a report that does not exist is not a clean one")
        assertFalse(gate.checkRun)
        assertEquals("Check has not run yet", gate.title())
        assertEquals("Build is blocked until Check has run.", gate.blockedSentence())
        assertEquals("Ship is blocked until Check has run: it has not looked at this project yet.", gate.explanation())
        assertEquals(0, gate.failCount, "it has no fails: it has not been run")
        assertTrue(shipGate(null, layerOne(CheckStatus.PASS)).blocked, "a layer one alone is not Check")
        assertTrue(shipGate(clean).checkRun)
    }

    @Test
    fun theGateOfACompiledBinaryAloneIsItsLayerOneFailsAndNeverBlocksOnWhatItDoesNotHave() {
        val gate = binaryGate(layerOne(CheckStatus.PASS, CheckStatus.FAIL))
        assertTrue(gate.blocked)
        assertTrue(gate.checkRun, "the binary's own gate is not the one that waits for Check")
        assertEquals(listOf("Check 1"), gate.blockers.map { it.title })
        assertFalse(binaryGate(layerOne(CheckStatus.PASS, CheckStatus.WARN)).blocked)
        assertFalse(binaryGate(unavailableReport("no Python")).blocked, "a layer one that did not run counts nothing")
    }

    @Test
    fun layerOneIsLeftOutOfTheGateForARepositoryThatIsNotGoingToGoogleFonts() {
        val binaryFails = layerOne(CheckStatus.FAIL)
        assertEquals(1, shipGate(clean, binaryFails).failCount)
        assertFalse(shipGate(clean, binaryFails, countLayerOne = false).blocked)
        // Layer two still blocks in that mode.
        assertEquals(failing.counts.fail, shipGate(failing, binaryFails, countLayerOne = false).failCount)
    }

    @Test
    fun everyLayerTwoFailNamesWhereItIsPutRight() {
        val blockers = shipGate(failing).blockers
        assertEquals(ShipRoute.FontInfoAt(FontInfoTarget.NAMES), blockers.single { it.id == ReviewOrder.NAMES }.route)
        assertEquals(ShipRoute.FontInfoAt(FontInfoTarget.LICENCE), blockers.single { it.id == ReviewOrder.LICENCE }.route)
        // A fail whose own action is not Font info goes to Check.
        val economy =
            CheckItem(
                id = ReviewOrder.NODE_ECONOMY,
                group = CheckGroup.OUTLINES,
                title = "Node economy",
                detail = null,
                severity = Severity.FAIL,
                summary = "Your T has 1,763 points.",
                reviewerQuote = null,
                action = CheckAction("Open Economy", CheckRoute.OpenEconomy),
                basis = Basis.MEASURED,
                basisNote = "",
                raw = "",
            )
        val gate = shipGate(CheckReport(listOf(economy), null, null))
        assertEquals(ShipRoute.Check, gate.blockers.single().route)
    }

    @Test
    fun aFailOfGoogleFontsProfileOnTheBinaryGoesToTheFontInfoValueItReadsOrToCheck() {
        fun route(id: String) = binaryGate(LayerOneReport(listOf(CheckOutcome(id, CheckStatus.FAIL, "bad")))).blockers.single().route
        assertEquals(ShipRoute.FontInfo(ShipFontInfoField.LICENCE_TEXT), route("com.google.fonts/check/fstype"))
        assertEquals(ShipRoute.FontInfo(ShipFontInfoField.FAMILY), route("com.google.fonts/check/canonical_filename"))
        assertEquals(ShipRoute.FontInfo(ShipFontInfoField.COPYRIGHT), route("com.google.fonts/check/font_copyright"))
        assertEquals(ShipRoute.FontInfo(ShipFontInfoField.VENDOR_ID), route("com.google.fonts/check/vendor_id"))
        assertEquals(ShipRoute.Check, route("com.google.fonts/check/gasp"))
        assertEquals(ShipRoute.Check, route("com.google.fonts/check/something_new"))
    }

    @Test
    fun anEmbeddingRestrictionTheProjectStatesIsNamedAsTheProjectsAndSaysTheBuildLeftIt() {
        val fstype =
            LayerOneReport(
                listOf(
                    CheckOutcome("com.google.fonts/check/fstype", CheckStatus.FAIL, "In this font fsType is set to 2 meaning that:\nmore"),
                ),
            )
        val stated = binaryGate(fstype, embeddingStated = true).blockers.single()
        assertEquals(EMBEDDING_STATED_SENTENCE, stated.sentence)
        assertTrue("openTypeOS2Type" in stated.sentence && "left it as you stated it" in stated.sentence)
        assertEquals(ShipRoute.FontInfo(ShipFontInfoField.LICENCE_TEXT), stated.route)
        val notStated = binaryGate(fstype, embeddingStated = false).blockers.single()
        assertEquals(
            "In this font fsType is set to 2 meaning that:",
            notStated.sentence,
            "when the project says nothing the tool's own words stand",
        )
        val other = LayerOneReport(listOf(CheckOutcome("com.google.fonts/check/gasp", CheckStatus.FAIL, "Missing gasp")))
        assertEquals(
            "Missing gasp",
            binaryGate(other, embeddingStated = true).blockers.single().sentence,
            "only the fsType fail is reworded",
        )
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
