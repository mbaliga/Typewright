// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.project

import com.asoc.typewright.core.geometry.count
import com.asoc.typewright.project.ApprovalOrigin
import com.asoc.typewright.project.GlyphRef
import com.asoc.typewright.project.ResumePoint
import com.asoc.typewright.project.StyleClass
import com.asoc.typewright.qa.corpus.NodeEconomyVerdict
import com.asoc.typewright.ui.deck.DeckSection
import com.asoc.typewright.ui.deck.StationStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What Home, the Glyphs grid and the deck's stations say, measured on the real sample font opened as a project. */
class ProjectInsightsTest {
    private val declared = SampleProject.workspace()
    private val declaredProject = SampleProject.openProject(declared)
    private val font = checkNotNull(declaredProject.font)

    private fun economy() =
        checkNotNull(projectEconomy(declaredProject.manifest, font)) {
            "the sample must be judgeable against its declared class"
        }

    @Test
    fun theProjectHoldsTheSamplesOwnOutlinesWithCLAUDEmdsShippedCounts() {
        assertEquals("Hyle Deco", font.ufo.fontInfo.familyName)
        // CLAUDE.md's fixture table: on-curve points of the shipped glyphs (a polygon font, no off-curve points).
        for ((name, onCurve) in listOf("T" to 1763, "o" to 80, "n" to 44, "H" to 1252)) {
            val counted = checkNotNull(font.glyph(name)).count()
            assertEquals(onCurve, counted.onCurveEquivalent, name)
            assertEquals(0, counted.offCurve, name)
        }
    }

    @Test
    fun theCountsLineIsTheSumOfTheGlyphsOwnCounts() {
        val counts = projectCounts(font)
        assertEquals(font.glyphs.size, counts.glyphs)
        assertEquals(font.glyphs.sumOf { it.count().onCurveEquivalent }, counts.points)
        assertEquals(0, counts.curves, "a polygon font has no curved segment")
    }

    @Test
    fun outliersAreMeasuredAgainstTheDeclaredClassAndAddUp() {
        val economy = economy()
        assertEquals("sans-geometric", economy.style.key)
        assertEquals(StyleOrigin.DECLARED, economy.style.origin)
        assertTrue(economy.classFaces > 0)
        assertTrue(economy.judged > 0)
        assertEquals(economy.judged, economy.outliers + economy.above + economy.inRange)
        assertEquals(
            NodeEconomyVerdict.OUTLIER,
            economy.verdict("T"),
            "the shipped T has 1,763 on-curve points against a box of about a dozen",
        )
        assertNull(economy.verdict("no-such-glyph"))
    }

    @Test
    fun aConfirmedClassBeatsADeclaredOneAndANeitherIsReadByOurHeuristic() {
        val manifest = declaredProject.manifest
        val confirmed =
            manifest.copy(
                brief = manifest.brief.copy(styleClass = StyleClass(declared = "serif-oldstyle", confirmed = "sans-geometric")),
            )
        assertEquals(ProjectStyle("sans-geometric", StyleOrigin.CONFIRMED), projectStyle(confirmed, font))
        assertEquals(ProjectStyle("sans-geometric", StyleOrigin.DECLARED), projectStyle(manifest, font))

        val undeclared = SampleProject.openProject(SampleProject.workspace(declaredStyleClass = null))
        val detected =
            assertNotNull(projectStyle(undeclared.manifest, checkNotNull(undeclared.font)), "the detector reads the sample's letters")
        assertEquals(StyleOrigin.DETECTED, detected.origin)
        assertTrue(detected.isHeuristic)
        assertTrue("our heuristic" in detected.provenance)
        // What opening the sample as a person opens a font (no class declared) gives: the detector's own reading, which the app calls a
        // heuristic, is not the geometric class the sample's own brief declares, so Task 4 is judged against another box than the one
        // it was first measured against. Pinned so a change to the detector shows up here.
        assertEquals("sans-humanist", detected.key, "the class the detector reads from Hyle Deco's own letters")
        assertEquals("sans-geometric", declaredProject.manifest.brief.styleClass.declared)
    }

    @Test
    fun homeAndTheTraceStationAreJudgedByTheOneStyleFunction() {
        val undeclared = SampleProject.openProject(SampleProject.workspace(declaredStyleClass = null))
        val undeclaredFont = checkNotNull(undeclared.font)
        val style = assertNotNull(projectStyle(undeclared.manifest, undeclaredFont))
        val economy = projectEconomy(undeclared.manifest, undeclaredFont)
        if (economy == null) {
            // The detector may name a class the corpus has no data for; then neither screen judges.
            assertNull(stationStatusFor(DeckSection.TRACE, undeclared.state))
        } else {
            assertEquals(style, economy.style, "Home reads the class from the same function the station does")
            val expected = if (economy.outliers > 0) StationStatus.TO_REFIT else StationStatus.DONE
            assertEquals(expected, stationStatusFor(DeckSection.TRACE, undeclared.state))
        }
        // With a declared class both screens agree on it, outliers and all.
        val expected = if (economy().outliers > 0) StationStatus.TO_REFIT else StationStatus.DONE
        assertEquals(expected, stationStatusFor(DeckSection.TRACE, declaredProject.state))
        assertEquals(StationStatus.TO_REFIT, expected, "the sample has outliers, so Trace has glyphs to refit")
    }

    @Test
    fun drawIsNotDoneJustBecauseTheImportApprovedEveryGlyph() {
        val state = declaredProject.state
        assertTrue(
            state.font.locks.values.all {
                it.origin == ApprovalOrigin.IMPORT &&
                    it.state == com.asoc.typewright.project.LockState.LOCKED
            },
        )
        assertEquals(DrawProgress.NOT_STARTED, drawProgress(font, state.font.locks))
        assertEquals(StationStatus.NOT_STARTED, stationStatusFor(DeckSection.DRAW, state))

        // The person looks over every drawn glyph and approves it themselves: done.
        val approvedByPerson = state.font.locks.mapValues { (_, lock) -> lock.copy(origin = ApprovalOrigin.DRAW) }
        val allLooked = state.copy(font = state.font.copy(locks = approvedByPerson))
        assertEquals(DrawProgress.DONE, drawProgress(font, allLooked.font.locks))
        assertEquals(StationStatus.DONE, stationStatusFor(DeckSection.DRAW, allLooked))

        // Only one of them: partway, which has no shape rather than a wrong one.
        val first = font.glyphs.first { it.contours.isNotEmpty() }
        val one =
            state.font.locks.mapValues { (ref, lock) ->
                if (ref ==
                    GlyphRef(font.master.id, first.name)
                ) {
                    lock.copy(origin = ApprovalOrigin.TRACE)
                } else {
                    lock
                }
            }
        val partial = state.copy(font = state.font.copy(locks = one))
        assertEquals(DrawProgress.PARTIAL, drawProgress(font, partial.font.locks))
        assertNull(stationStatusFor(DeckSection.DRAW, partial))
    }

    @Test
    fun stationsTheProjectCannotSpeakForHaveNoShapeAndTheRouterFunctionReadsTheLiveState() {
        for (section in DeckSection.entries) {
            if (section == DeckSection.DRAW || section == DeckSection.TRACE) continue
            assertNull(stationStatusFor(section, declaredProject.state), "$section")
        }
        val session = checkNotNull(declared.current.value)
        val statusOf = projectStationStatus(session)
        assertEquals(StationStatus.NOT_STARTED, statusOf(DeckSection.DRAW))
        assertEquals(StationStatus.TO_REFIT, statusOf(DeckSection.TRACE))
        assertNull(statusOf(DeckSection.CHECK))
        assertNull(statusOf(DeckSection.SHIP))
    }

    @Test
    fun theGlyphsGridFiltersByVerdictLockAndSearch() {
        val economy = economy()
        val cells = glyphCells(font, economy, declaredProject.state.font.locks)
        assertEquals(font.glyphs.size, cells.size)
        assertTrue(cells.all { it.locked }, "an imported font is locked glyph by glyph (Law 1), so the Locked chip lists them all")

        val counts = filterCounts(cells)
        assertEquals(cells.size, counts.getValue(GlyphFilter.ALL))
        assertEquals(cells.size, counts.getValue(GlyphFilter.LOCKED))
        assertEquals(economy.outliers, counts.getValue(GlyphFilter.OUTLIERS))
        assertEquals(economy.above, counts.getValue(GlyphFilter.ABOVE))
        assertEquals(economy.inRange, counts.getValue(GlyphFilter.IN_RANGE))
        assertTrue(filterCells(cells, GlyphFilter.OUTLIERS, "").all { it.verdict == NodeEconomyVerdict.OUTLIER })

        assertEquals(listOf("T"), filterCells(cells, GlyphFilter.ALL, "T").map { it.glyph.name }.filter { it == "T" })
        val byCharacter = filterCells(cells, GlyphFilter.ALL, "Ö")
        assertTrue(
            byCharacter.isNotEmpty() && byCharacter.all { 'Ö'.code in it.glyph.unicodes || "Ö".lowercase() in it.glyph.name.lowercase() },
        )
        assertTrue(filterCells(cells, GlyphFilter.ALL, "zzz-no-such-glyph").isEmpty())
    }

    @Test
    fun theHeaviestGlyphsComeMostPointsFirst() {
        val cells = glyphCells(font, economy(), declaredProject.state.font.locks)
        val heaviest = heaviestGlyphs(cells, 5)
        assertEquals(5, heaviest.size)
        assertEquals(cells.maxOf { it.count.onCurveEquivalent }, heaviest.first().count.onCurveEquivalent)
        assertEquals(heaviest.map { it.count.onCurveEquivalent }.sortedDescending(), heaviest.map { it.count.onCurveEquivalent })
    }

    @Test
    fun aSpaceIsMeantToBeEmptyAndALetterIsNot() {
        assertTrue(isMeantToBeEmpty(checkNotNull(font.glyphFor(' '))))
        assertFalse(isMeantToBeEmpty(checkNotNull(font.glyph("T"))))
    }

    @Test
    fun theResumePointNamesSectionMasterAndGlyphAndOnlyRestoresAGlyphTheProjectStillHas() {
        val state = declaredProject.state
        assertEquals(ResumePoint("draw", font.master.id, "T"), resumePointFor(DeckSection.DRAW, "T", state))
        assertEquals(ResumePoint("home", font.master.id, null), resumePointFor(DeckSection.HOME, null, state))
        assertEquals("T", glyphToRestore(ResumePoint("draw", font.master.id, "T"), state))
        assertNull(glyphToRestore(ResumePoint("draw", font.master.id, "deleted-glyph"), state))
        assertNull(glyphToRestore(ResumePoint("draw", font.master.id, null), state))
        assertNull(glyphToRestore(null, state))
    }
}
