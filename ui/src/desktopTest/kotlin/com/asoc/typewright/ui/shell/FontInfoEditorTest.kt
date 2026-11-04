// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.shell

import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.ContourPoint
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.project.AddGlyph
import com.asoc.typewright.project.ApprovalOrigin
import com.asoc.typewright.project.Approve
import com.asoc.typewright.project.CreateResult
import com.asoc.typewright.project.GlyphRef
import com.asoc.typewright.project.LockState
import com.asoc.typewright.project.ProjectSession
import com.asoc.typewright.project.ProjectWorkspace
import com.asoc.typewright.qa.report.CheckReport
import com.asoc.typewright.qa.report.ReviewOrder
import com.asoc.typewright.qa.report.Severity
import com.asoc.typewright.qa.report.buildCheckReport
import com.asoc.typewright.ui.check.checkSubjectOf
import com.asoc.typewright.ui.project.InMemoryProjectFolderPicker
import com.asoc.typewright.ui.project.OpenProject
import com.asoc.typewright.ui.project.SampleProject
import com.asoc.typewright.ui.project.blankNewProjectSpec
import com.asoc.typewright.ui.project.createProjectWorkspace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Font info's edits against a real project: a font drawn from scratch can clear its Licence fail from the page, each edit is one
 * undo step, and what the project refuses is said in its own words. The project is the blank one Typewright creates for a new font.
 */
class FontInfoEditorTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun blankWorkspace(name: String = "Blank"): ProjectWorkspace =
        runBlocking {
            val workspace = createProjectWorkspace(scope)
            val parent = checkNotNull(InMemoryProjectFolderPicker(workspace).pickNewProjectParent())
            check(workspace.create(parent, blankNewProjectSpec(name)) is CreateResult.Created)
            workspace
        }

    private fun session(workspace: ProjectWorkspace): ProjectSession = checkNotNull(workspace.current.value)

    private fun editor(session: ProjectSession) =
        FontInfoEditor(
            session,
            session.state.value.font.masters
                .first()
                .id,
        )

    private fun reportOf(session: ProjectSession): CheckReport {
        val subject = checkNotNull(checkSubjectOf(OpenProject(session, session.state.value)))
        return buildCheckReport(subject.inputs)
    }

    private fun licence(session: ProjectSession): Severity = checkNotNull(reportOf(session).item(ReviewOrder.LICENCE)).severity

    private fun info(session: ProjectSession) =
        session.state.value.font.masters
            .first()
            .ufo.fontInfo

    @Test
    fun aFontDrawnFromScratchClearsItsLicenceFailFromTheFontInfoPage() {
        val session = session(blankWorkspace())
        val editor = editor(session)
        // A blank project knows nothing it was not told: no copyright, no licence. Check fails it.
        assertNull(info(session).copyright)
        assertEquals(Severity.FAIL, licence(session))

        val copyright = editor.commit(FontInfoField.COPYRIGHT, "Copyright 2026 The Blank Project Authors (https://example.org/blank)")
        assertIs<FontInfoOutcome.Done>(copyright)
        assertEquals(Severity.WARN, licence(session), "the copyright line is there; the licence is still not named")

        assertIs<FontInfoOutcome.Done>(editor.chooseLicence(LicenceChoice.OFL))
        assertEquals(Severity.PASS, licence(session), "the Licence fail is cleared from the page, with nothing invented")
        assertEquals(LicenceStrings.OFL_DESCRIPTION, info(session).openTypeNameLicense)
    }

    @Test
    fun theMetricsAndNamesItemsAreAlsoSatisfiedFromThePage() {
        val session = session(blankWorkspace("Quill"))
        val editor = editor(session)
        // No ascender or descender yet: the compiler would choose the line spacing, and Check warns.
        assertEquals(Severity.WARN, checkNotNull(reportOf(session).item(ReviewOrder.VERTICAL_METRICS)).severity)

        assertIs<FontInfoOutcome.Done>(editor.commit(FontInfoField.ASCENDER, "950"))
        assertIs<FontInfoOutcome.Done>(editor.commit(FontInfoField.DESCENDER, "-250"))
        val metrics = checkNotNull(reportOf(session).item(ReviewOrder.VERTICAL_METRICS))
        assertTrue("120%" !in metrics.summary || metrics.severity != Severity.FAIL, metrics.summary)
        assertEquals("950", FontInfoRules.textIn(info(session), FontInfoField.ASCENDER))
        // A family that Check calls a Google Fonts clash is renamed in place and the report follows.
        assertIs<FontInfoOutcome.Done>(editor.commit(FontInfoField.FAMILY, "Quill Sketch"))
        assertEquals("Quill Sketch", info(session).familyName)
    }

    @Test
    fun eachEditIsOneUndoStepAndUndoPutsTheOldValueBack() {
        val session = session(blankWorkspace())
        val editor = editor(session)
        editor.commit(FontInfoField.COPYRIGHT, "Copyright 2026 X")
        editor.chooseLicence(LicenceChoice.OFL)
        val steps = session.history.value.undoLabels.size
        assertEquals(2, steps, "two edits, two steps: ${session.history.value.undoLabels}")

        assertTrue(session.undo())
        assertNull(info(session).openTypeNameLicense, "undo takes the licence lines back as one step, both of them")
        assertNull(info(session).openTypeNameLicenseURL)
        assertEquals("Copyright 2026 X", info(session).copyright, "and leaves the earlier edit")
        assertTrue(session.undo())
        assertNull(info(session).copyright)
        assertTrue(session.redo())
        assertEquals("Copyright 2026 X", info(session).copyright)
    }

    @Test
    fun aRefusedEditLeavesTheProjectAndItsHistoryAsTheyWere() {
        val session = session(blankWorkspace())
        val editor = editor(session)
        editor.commit(FontInfoField.DESIGNER, "Ada")
        val before = session.state.value.font
        val steps = session.history.value.undoLabels

        val bad = editor.commit(FontInfoField.COPYRIGHT, "Copyright\u0001 2026")
        assertIs<FontInfoOutcome.Refused>(bad)
        assertTrue("cannot carry" in bad.message, bad.message)
        assertIs<FontInfoOutcome.Refused>(editor.commit(FontInfoField.DESCENDER, "250"))
        assertIs<FontInfoOutcome.Refused>(editor.commit(FontInfoField.ASCENDER, "=nonsense"))
        assertEquals(before, session.state.value.font)
        assertEquals(steps, session.history.value.undoLabels, "a refusal is not an undo step")
        // And the same value again is not a step either.
        assertIs<FontInfoOutcome.Unchanged>(editor.commit(FontInfoField.DESIGNER, "Ada"))
        assertEquals(steps, session.history.value.undoLabels)
    }

    @Test
    fun aLockedGlyphDoesNotStandInTheWayBecauseFontInfoIsNotAGlyph() {
        val session = session(blankWorkspace())
        val master =
            session.state.value.font.masters
                .first()
                .id
        // Give the project a glyph and approve it, which locks it (law 1).
        val ref = GlyphRef(master, "A")
        val square =
            Glyph(
                "A",
                600,
                listOf(
                    Contour(
                        listOf(Point(0, 0), Point(0, 700), Point(500, 700), Point(500, 0)).map { ContourPoint(it, onCurve = true) },
                        CurveFormat.QUADRATIC,
                    ),
                ),
                unicodes = listOf(0x41),
            )
        session.execute(AddGlyph(master, square))
        session.execute(Approve(ref, ApprovalOrigin.DRAW))
        assertEquals(LockState.LOCKED, session.lockOf(ref)?.state)
        val outlineBefore =
            session.state.value.font
                .glyph(ref)

        val outcome = editor(session).commit(FontInfoField.X_HEIGHT, "500")

        assertIs<FontInfoOutcome.Done>(outcome)
        assertEquals(500, info(session).xHeight)
        assertEquals(
            outlineBefore,
            session.state.value.font
                .glyph(ref),
            "the approved outline is exactly as it was",
        )
        assertEquals(LockState.LOCKED, session.lockOf(ref)?.state)
    }

    @Test
    fun theSampleFontOpensWithItsOwnValuesAndAnEditToThemIsOneStep() {
        val workspace = SampleProject.workspace()
        val session = session(workspace)
        val editor = editor(session)
        val designerBefore = info(session).openTypeNameDesigner
        assertEquals(designerBefore, FontInfoRules.textIn(editor.info(), FontInfoField.DESIGNER))
        assertIs<FontInfoOutcome.Done>(editor.commit(FontInfoField.DESIGNER, "Test Designer"))
        assertEquals("Test Designer", info(session).openTypeNameDesigner)
        assertTrue(session.undo())
        assertEquals(designerBefore, info(session).openTypeNameDesigner)
        assertNotNull(info(session).familyName)
    }
}
