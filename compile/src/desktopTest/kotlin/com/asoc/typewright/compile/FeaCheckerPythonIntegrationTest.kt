// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.compile

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The real feaLib, run by the Python `TYPEWRIGHT_PYTHON` names, reads real feature text with a font's glyph names as the glyph set
 * and answers with the line and column it stopped at. Like the other `*PythonIntegrationTest`s these never skip: without a Python that
 * has fontTools they fail and say which is missing (`:compile:pythonIntegrationTest`).
 */
class FeaCheckerPythonIntegrationTest {
    private val glyphs = listOf("n", "o", "H", "O")

    private fun check(text: String): FeaCheckResult.Checked {
        if (System.getenv(PYTHON_ENV_VARIABLE).isNullOrBlank()) {
            fail("$PYTHON_ENV_VARIABLE is not set. Point it at a Python that has fontmake (which brings fontTools).")
        }
        val result = runBlocking { SystemPythonFeaChecker().check(text, glyphs) }
        if (result is FeaCheckResult.Unavailable) fail("the check could not run: ${result.reason}")
        return assertIs<FeaCheckResult.Checked>(result)
    }

    @Test
    fun aGoodKernFeatureIsReadAndBuiltWithNothingWrong() {
        val checked = check("feature kern {\n    pos n o -12;\n} kern;\n")
        assertTrue(checked.problems.isEmpty(), "${checked.problems}")
        assertTrue(checked.built, "the lookups were built as well as read")
        assertTrue(checked.tool.startsWith("fontTools "), checked.tool)
    }

    @Test
    fun aMissingSemicolonIsFoundAtItsLineAndColumn() {
        val problem = check("feature kern {\n    pos n o -12\n} kern;\n").problems.single()
        assertEquals(3, problem.line, "feaLib notices at the closing brace on line 3, where the statement should have ended")
        assertNotNull(problem.column)
        assertTrue(problem.message.isNotBlank())
    }

    @Test
    fun aBlockLeftOpenAtTheEndOfTheTextSaysTheTextEndedInsteadOfGotNoneNone() {
        val problem = check("feature kern {\n    pos n o -12;\n").problems.single()
        assertTrue(problem.message.endsWith(", but the text ended"), problem.message)
        assertTrue("None" !in problem.message, problem.message)
        assertNotNull(problem.line)
    }

    @Test
    fun aGlyphTheFontDoesNotHaveIsNamedWithTheLineItFirstAppearsOn() {
        val problem = check("# kerning\nfeature kern {\n    pos n q -12;\n} kern;\n").problems.single()
        assertEquals(3, problem.line)
        assertEquals(11, problem.column)
        assertTrue(" q " in problem.message || "q (" in problem.message, problem.message)
        assertTrue("line 3, column 11" in problem.message, "the tool's <features>:3:11 is put in words: ${problem.message}")
        assertTrue('\n' !in problem.message, "one line")
    }

    @Test
    fun anUnknownLookupIsFoundByTheBuildStepAndNotJustTheParser() {
        val problem = check("languagesystem DFLT dflt;\nfeature kern {\n    lookup nothere;\n} kern;\n").problems.single()
        assertEquals(3, problem.line)
        assertTrue("nothere" in problem.message, problem.message)
    }

    @Test
    fun anIncludeOfAFileTheProjectDoesNotHaveIsNotAMistakeInTheText() {
        val checked = check("include(other.fea);\nfeature kern { pos n o -3; } kern;\n")
        assertTrue(checked.problems.isEmpty(), "${checked.problems}")
    }

    @Test
    fun windowsLineEndingsAndNonAsciiCommentsAreReadAndCountedLikeUnixOnes() {
        val unix = check("# é\nfeature kern {\n    pos n q -12;\n} kern;\n").problems.single()
        val windows = check("# é\r\nfeature kern {\r\n    pos n q -12;\r\n} kern;\r\n").problems.single()
        assertEquals(unix.line, windows.line)
        assertEquals(3, windows.line)
    }
}
