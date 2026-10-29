// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.space

import com.asoc.typewright.compile.FeaCheckResult
import com.asoc.typewright.compile.FeaProblem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [FeaReport]: which check a `.fea` result is the result of, said in the report itself, and the shape check never passed off as a parse. */
class FeaReportTest {
    private val brokenShape = "feature kern {\n    pos n o -12;\n"

    @Test
    fun beforeTheParserAnswersTheQuickShapeCheckIsShownAndSaidToBeWaiting() {
        val report = FeaReport.of(brokenShape, parsed = null)
        assertTrue(report.pending)
        assertEquals("Check · ${FeaCheck.COVERS}", report.label)
        assertEquals(listOf(1), report.findings.map { it.line }, "the block opened on line 1 and never closed")
        assertEquals("Asking fontTools to read it. Until it answers, only this quick check has looked at it.", report.limits)
    }

    @Test
    fun theParsersAnswerIsLabelledAsTheParsersWithItsToolAndPlace() {
        val checked =
            FeaCheckResult.Checked(
                tool = "fontTools 4.67.0 (feaLib)",
                where = "in this computer's Python 3.11.2",
                built = true,
                problems = listOf(FeaProblem(3, 11, "glyph q is missing")),
            )
        val report = FeaReport.of(brokenShape, checked)
        assertFalse(report.pending)
        assertEquals("Check · read by fontTools 4.67.0 (feaLib), with this font's glyph names", report.label)
        assertEquals(listOf(FeaFinding(3, 11, "glyph q is missing")), report.findings, "the parser's findings replace the quick check's")
        assertEquals(setOf(3), report.markedLines)
        assertEquals(
            "Read in this computer's Python 3.11.2, and nothing leaves the computer. Its lookups were built too. The build can still find more.",
            report.limits,
        )
    }

    @Test
    fun aParserThatOnlyReadTheTextSaysItDidNotBuildTheLookups() {
        val checked = FeaCheckResult.Checked("fontTools", "on this computer", built = false, problems = emptyList())
        val report = FeaReport.of("feature kern { } kern;", checked)
        assertTrue(report.findings.isEmpty())
        assertEquals(
            "Read on this computer, and nothing leaves the computer. Its lookups were not built, so the build can still find more.",
            report.limits,
        )
    }

    @Test
    fun whereThereIsNoParserTheShapeCheckIsLabelledAsThatWithTheReasonAndTheLimit() {
        val report = FeaReport.of(brokenShape, FeaCheckResult.Unavailable("Python 3 was not found on this computer."))
        assertFalse(report.pending)
        assertEquals("Check · ${FeaCheck.COVERS}", report.label, "named for what it looks at, not as a parse")
        assertEquals(1, report.findings.size)
        assertTrue(report.limits.startsWith("Python 3 was not found on this computer."), report.limits)
        assertTrue("The language itself is not parsed here" in report.limits && "can still fail then" in report.limits, report.limits)
    }

    @Test
    fun aFindingIsOneLineWithAsMuchOfItsPlaceAsTheToolGave() {
        assertEquals("Line 3, column 11: m", FeaFinding(3, 11, "m").text())
        assertEquals("Line 3: m", FeaFinding(3, null, "m").text())
        assertEquals("m", FeaFinding(null, null, "m").text())
    }

    @Test
    fun aTextThatIsFineHasNoFindingsAndNoMarkedLines() {
        val good = "feature kern {\n    pos n o -12;\n} kern;\n"
        assertTrue(FeaReport.of(good, null).findings.isEmpty())
        assertTrue(FeaReport.of(good, FeaCheckResult.Checked("t", "w", true, emptyList())).markedLines.isEmpty())
    }
}
