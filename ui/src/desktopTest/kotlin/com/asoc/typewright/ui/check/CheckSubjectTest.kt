// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.check

import com.asoc.typewright.qa.report.ReviewOrder
import com.asoc.typewright.qa.report.Severity
import com.asoc.typewright.qa.report.buildCheckReport
import com.asoc.typewright.ui.deck.StationStatus
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** What Check reads from the open project: the sample font (Hyle Deco) opened the way a person's font is opened. */
class CheckSubjectTest {
    private val project = CheckTestProject.project
    private val subject = CheckTestProject.subject

    @Test
    fun theTitleNamesTheFontFromItsOwnFontInfo() {
        assertEquals("Hyle Deco Regular", subject.name)
        assertEquals("Hyle-Deco-Regular.ttf", subject.binaryName)
    }

    @Test
    fun theUfoMeasuredIsTheDefaultMastersAndTheSnapshotHoldsItsFiles() =
        runBlocking<Unit> {
            val master = checkNotNull(project.master)
            assertSame(master.ufo, subject.inputs.project)
            assertEquals(master.path, subject.masterPath)
            val files = subject.snapshot().listFiles()
            assertTrue(files.any { it == "${master.path}/fontinfo.plist" }, "the master's UFO is in the snapshot: ${files.take(5)}")
            assertTrue(files.any { it == "typewright.json" })
        }

    @Test
    fun theClassIsTheOneTheBriefDeclaresAndIsNotMarkedHeuristic() {
        val style = assertNotNull(subject.inputs.style)
        assertEquals("sans-geometric", style.key)
        assertEquals("geometric sans", style.name)
        assertEquals(false, style.heuristic)
    }

    @Test
    fun aProjectOpenedFromAFontIsReadAsAnImportedCompiledFontWithEveryOutlineStillAsImported() {
        val provenance = assertNotNull(subject.inputs.provenance)
        val outlines =
            subject.inputs.project.glyphs
                .count { it.contours.isNotEmpty() }
        assertTrue(provenance.fromCompiledFont, "Hyle Deco's outlines are TrueType quadratics")
        assertEquals(outlines, provenance.outlineGlyphs)
        assertEquals(outlines, provenance.untouchedGlyphs, "nothing has been redrawn since the import")
        assertEquals(Severity.FAIL, buildCheckReport(subject.inputs).item(ReviewOrder.SOURCE_FOSSIL)?.severity)
    }

    @Test
    fun theVersionIsTheFontStateSoAnEditIsANewDrawing() {
        assertSame(project.state.font, subject.version)
        assertTrue(subject.hasGlyphs)
    }

    @Test
    fun theDecksCheckLabelShowsTheShapeOfWhatTheReportFound() {
        assertNull(checkStationStatus(null))
        assertEquals(StationStatus.TO_REFIT, checkStationStatus(buildCheckReport(subject.inputs)), "a traced font fails")
        val clean = buildCheckReport(subject.inputs).copy(items = emptyList())
        assertEquals(StationStatus.DONE, checkStationStatus(clean))
    }
}
