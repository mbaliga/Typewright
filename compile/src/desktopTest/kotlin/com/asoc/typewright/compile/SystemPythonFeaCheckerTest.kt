// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.compile

import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The desktop `.fea` checker's own work: what it hands Python, what it makes of the answer, and what it says when there is none. No real Python runs. */
class SystemPythonFeaCheckerTest {
    /** Answers the probe like a Python with fontTools, and a check by [answer], which gets the text and the glyph names it was given. */
    private class FakePython(
        private val probe: String = PROBE_OK,
        private val answer: (text: String, names: List<String>) -> ProcessOutcome,
    ) : ProcessRunner {
        var probes = 0
        var checks = 0
        val textFiles = mutableListOf<File>()
        var lastCommand: List<String> = emptyList()

        override fun run(
            command: List<String>,
            timeoutMillis: Long,
        ): ProcessOutcome {
            lastCommand = command
            if (command[2] == SystemPythonFeaChecker.SCRIPT) {
                checks++
                val text = File(command[3]).readText()
                textFiles += File(command[3])
                return answer(text, File(command[4]).readText().split("\n").filter { it.isNotEmpty() })
            }
            probes++
            return ProcessOutcome(0, probe)
        }
    }

    private fun checker(
        python: FakePython,
        clock: () -> Long = { 0L },
    ) = SystemPythonFeaChecker({ name -> if (name == PYTHON_ENV_VARIABLE) "/venv/bin/python" else null }, python, clock, 5_000L)

    private fun said(json: String) = ProcessOutcome(0, "some warning\n${SystemPythonFeaChecker.RESULT_PREFIX}$json\n")

    @Test
    fun theTextAndTheFontsGlyphNamesGoToPythonAndAreReadBackExactly() {
        val text = "feature kern {\r\n    pos n o -12;\r\n} kern;\r\né 中"
        val python =
            FakePython { given, names ->
                assertEquals(text, given, "the bytes of the text, CRLF and non-ASCII included, are what feaLib reads")
                assertEquals(listOf("n", "o", "H.alt"), names)
                said("""{"tool": "fontTools 4.67.0 (feaLib)", "built": true, "problems": []}""")
            }
        val result = runBlocking { checker(python).check(text, listOf("n", "o", "H.alt")) }
        val checked = assertIs<FeaCheckResult.Checked>(result)
        assertEquals("fontTools 4.67.0 (feaLib)", checked.tool)
        assertTrue(checked.built)
        assertTrue(checked.problems.isEmpty())
        assertEquals("/venv/bin/python", python.lastCommand[0])
        assertEquals("in this computer's Python 3.11.17", checked.where)
    }

    @Test
    fun theDesktopCheckerMayReadSoTheEditorWaitsForIt() {
        assertEquals(null, SystemPythonFeaChecker().neverReads)
    }

    @Test
    fun aProblemComesBackWithItsLineAndColumnAndAGlyphProblemWithNoneKeepsItsMessage() {
        val python =
            FakePython { _, _ ->
                said(
                    """{"tool": "t", "built": false, "problems": [""" +
                        """{"line": 3, "column": 5, "message": "Expected a name"}, """ +
                        """{"line": null, "column": null, "message": "glyph q is missing"}]}""",
                )
            }
        val checked = assertIs<FeaCheckResult.Checked>(runBlocking { checker(python).check("x", emptyList()) })
        assertFalse(checked.built)
        assertEquals(listOf(FeaProblem(3, 5, "Expected a name"), FeaProblem(null, null, "glyph q is missing")), checked.problems)
    }

    @Test
    fun theTemporaryFilesAreGoneWhenTheCheckIsDone() {
        val python = FakePython { _, _ -> said("""{"tool": "t", "built": true, "problems": []}""") }
        runBlocking { checker(python).check("x", listOf("n")) }
        assertTrue(python.textFiles.isNotEmpty())
        assertTrue(python.textFiles.none { it.exists() || it.parentFile.exists() }, "the text was left on disk")
    }

    @Test
    fun withNoPythonTheAnswerIsTheReasonAndNothingIsClaimedChecked() {
        val runner = ProcessRunner { _, _ -> ProcessOutcome(null, "", "could not be started (No such file or directory)") }
        val result = runBlocking { SystemPythonFeaChecker({ null }, runner, { 0L }, 5_000L).check("x", emptyList()) }
        val unavailable = assertIs<FeaCheckResult.Unavailable>(result)
        assertTrue("Python 3 was not found" in unavailable.reason, unavailable.reason)
    }

    @Test
    fun aPythonWithoutFontToolsSaysWhatToInstall() {
        val python = FakePython(probe = PROBE_NO_FONTTOOLS) { _, _ -> error("must not be run") }
        val unavailable = assertIs<FeaCheckResult.Unavailable>(runBlocking { checker(python).check("x", emptyList()) })
        assertTrue("fontTools" in unavailable.reason && "pip install" in unavailable.reason, unavailable.reason)
    }

    @Test
    fun thePythonFoundIsRememberedAcrossChecksButAMissingOneIsLookedForAgainLater() {
        val python = FakePython { _, _ -> said("""{"tool": "t", "built": true, "problems": []}""") }
        val steady = checker(python)
        runBlocking {
            steady.check("a", emptyList())
            steady.check("b", emptyList())
        }
        assertEquals(1, python.probes, "typing does not search for Python again")
        assertEquals(2, python.checks)

        var now = 0L
        val missing = FakePython(probe = PROBE_NO_FONTTOOLS) { _, _ -> error("must not be run") }
        val retrying = checker(missing) { now }
        runBlocking {
            retrying.check("a", emptyList())
            retrying.check("b", emptyList())
            assertEquals(1, missing.probes, "a Python that was just found wanting is not asked again at once")
            now += 31_000_000_000L
            retrying.check("c", emptyList())
        }
        assertEquals(2, missing.probes, "but is, after a while, in case it has been installed")
    }

    @Test
    fun aPythonThatDiesMidCheckIsReportedAndLookedForAgainNextTime() {
        var dies = true
        val python =
            FakePython { _, _ ->
                if (dies) {
                    ProcessOutcome(
                        null,
                        "",
                        "did not finish within 5 seconds and was stopped",
                    )
                } else {
                    said("""{"tool": "t", "built": true, "problems": []}""")
                }
            }
        val c = checker(python)
        val first = assertIs<FeaCheckResult.Unavailable>(runBlocking { c.check("x", emptyList()) })
        assertTrue("did not finish" in first.reason && "not checked" in first.reason, first.reason)
        dies = false
        assertIs<FeaCheckResult.Checked>(runBlocking { c.check("x", emptyList()) })
        assertEquals(2, python.probes)
    }

    @Test
    fun outputWithNoResultLineIsNotMistakenForAGoodText() {
        val python =
            FakePython {
                _,
                _,
                ->
                ProcessOutcome(1, "Traceback (most recent call last):\nImportError: no module named fontTools.feaLib\n")
            }
        val unavailable = assertIs<FeaCheckResult.Unavailable>(runBlocking { checker(python).check("x", emptyList()) })
        assertTrue("ImportError" in unavailable.reason && "not checked" in unavailable.reason, unavailable.reason)
        val garbled = FakePython { _, _ -> said("not json") }
        assertIs<FeaCheckResult.Unavailable>(runBlocking { checker(garbled).check("x", emptyList()) })
    }

    @Test
    fun theScriptItRunsNamesFeaLibAndTakesTheTextAndGlyphNamesFromItsArguments() {
        val script = SystemPythonFeaChecker.SCRIPT
        assertTrue("fontTools.feaLib.parser import Parser" in script)
        assertTrue("sys.argv[1]" in script && "sys.argv[2]" in script)
        assertTrue("followIncludes=False" in script, "an include of a file the project does not have is not a mistake in the text")
        assertTrue(SystemPythonFeaChecker.RESULT_PREFIX.trim() in script)
    }

    private companion object {
        val PROBE_OK =
            listOf(
                "fontmake ok 3.12.2",
                "ufo2ft ok 3.9.1",
                "fontTools ok 4.67.0",
                "pathops ok 0.9.2",
                "python ok 3.11.17",
            ).joinToString("\n")
        val PROBE_NO_FONTTOOLS =
            listOf("fontmake ok 3.12.2", "ufo2ft ok 3.9.1", "fontTools missing ModuleNotFoundError", "python ok 3.11.17").joinToString("\n")
    }
}
