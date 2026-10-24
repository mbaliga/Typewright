// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.compile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Finding a Python that can run fontmake, and saying what is missing when there is none; no real Python is started. */
class PythonLocatorTest {
    /** Answers each interpreter named in [pythons] with its probe output; any other command cannot be started. */
    private class FakeRunner(
        private val pythons: Map<String, String>,
    ) : ProcessRunner {
        val asked = mutableListOf<String>()

        override fun run(
            command: List<String>,
            timeoutMillis: Long,
        ): ProcessOutcome {
            asked += command.first()
            val output = pythons[command.first()] ?: return ProcessOutcome(null, "", "could not be started (No such file or directory)")
            return ProcessOutcome(0, output)
        }
    }

    private fun probe(
        fontmake: Boolean = true,
        ufo2ft: Boolean = true,
        fontTools: Boolean = true,
        pathops: Boolean = true,
        python: String = "3.11.17",
    ): String =
        listOf(
            "fontmake " + if (fontmake) "ok 3.12.2" else "missing ModuleNotFoundError",
            "ufo2ft " + if (ufo2ft) "ok 3.9.1" else "missing ModuleNotFoundError",
            "fontTools " + if (fontTools) "ok 4.67.0" else "missing ModuleNotFoundError",
            "pathops " + if (pathops) "ok 0.9.2" else "missing ModuleNotFoundError",
            "python ok $python",
        ).joinToString("\n")

    private fun locator(
        runner: ProcessRunner,
        variable: String? = null,
    ) = PythonLocator({ name -> if (name == PYTHON_ENV_VARIABLE) variable else null }, runner)

    @Test
    fun theEnvironmentVariableComesFirstAndPython3OnPathIsNotAsked() {
        val runner = FakeRunner(mapOf("/venv/bin/python" to probe(), "python3" to probe(fontmake = false)))
        val found = locator(runner, variable = "/venv/bin/python").resolve()

        assertIs<FontmakeEnvironment.Ready>(found)
        assertEquals("/venv/bin/python", found.python.python)
        assertEquals("3.12.2", found.python.fontmakeVersion)
        assertEquals("3.11.17", found.python.pythonVersion)
        assertEquals(listOf("/venv/bin/python"), runner.asked)
    }

    @Test
    fun python3OnPathIsUsedWhenTheVariableIsNotSet() {
        val runner = FakeRunner(mapOf("python3" to probe()))
        val found = locator(runner).resolve()
        assertIs<FontmakeEnvironment.Ready>(found)
        assertEquals("python3", found.python.python)
    }

    @Test
    fun aVariableThatCannotBeRunFallsBackToPython3() {
        val runner = FakeRunner(mapOf("python3" to probe()))
        val found = locator(runner, variable = "/nope/python").resolve()
        assertIs<FontmakeEnvironment.Ready>(found)
        assertEquals(listOf("/nope/python", "python3"), runner.asked)
        assertEquals("python3", found.python.python)
    }

    @Test
    fun noPythonAtAllSaysSoPlainlyAndNamesTheInstallCommand() {
        val found = locator(FakeRunner(emptyMap()), variable = "/nope/python").resolve()
        assertIs<FontmakeEnvironment.NotReady>(found)
        assertTrue(found.reason.startsWith("Python 3 was not found on this computer"), found.reason)
        assertTrue("TYPEWRIGHT_PYTHON (/nope/python) could not be started" in found.reason, found.reason)
        assertTrue("python3 could not be started" in found.reason, found.reason)
        assertTrue(found.reason.endsWith("Install Python 3, then run: pip install fontmake"), found.reason)
    }

    @Test
    fun withoutTheVariableTheMessageSaysItWasNotSet() {
        val found = locator(FakeRunner(emptyMap())).resolve()
        assertIs<FontmakeEnvironment.NotReady>(found)
        assertTrue("python3 on PATH; TYPEWRIGHT_PYTHON is not set" in found.reason, found.reason)
    }

    @Test
    fun aMissingFontmakeIsNamedWithTheExactPipCommand() {
        val found = locator(FakeRunner(mapOf("python3" to probe(fontmake = false, ufo2ft = false)))).resolve()
        assertIs<FontmakeEnvironment.NotReady>(found)
        assertEquals("Python 3.11.17 (python3) is missing fontmake and ufo2ft. Install them with: pip install fontmake", found.reason)
    }

    @Test
    fun aMissingUfo2ftAloneIsNamedAndInstalledOnItsOwn() {
        val found = locator(FakeRunner(mapOf("python3" to probe(ufo2ft = false)))).resolve()
        assertIs<FontmakeEnvironment.NotReady>(found)
        assertEquals("Python 3.11.17 (python3) is missing ufo2ft. Install it with: pip install ufo2ft", found.reason)
    }

    @Test
    fun aMissingFontToolsUsesPipsLowerCaseName() {
        val found = locator(FakeRunner(mapOf("python3" to probe(fontTools = false)))).resolve()
        assertIs<FontmakeEnvironment.NotReady>(found)
        assertTrue(found.reason.endsWith("pip install fonttools"), found.reason)
        assertTrue("is missing fontTools." in found.reason, found.reason)
    }

    @Test
    fun theInstallCommandNamesTheInterpreterWhenItIsNotThePlainPython3() {
        val found = locator(FakeRunner(mapOf("/venv/bin/python" to probe(fontmake = false))), variable = "/venv/bin/python").resolve()
        assertIs<FontmakeEnvironment.NotReady>(found)
        assertTrue(found.reason.endsWith("Install it with: /venv/bin/python -m pip install fontmake"), found.reason)
    }

    @Test
    fun skiaPathopsIsOptionalAndOnlyChangesWhetherOverlapsCanBeRemoved() {
        val found = locator(FakeRunner(mapOf("python3" to probe(pathops = false)))).resolve()
        assertIs<FontmakeEnvironment.Ready>(found)
        assertFalse(found.python.pathops)
        val withIt = locator(FakeRunner(mapOf("python3" to probe()))).resolve()
        assertIs<FontmakeEnvironment.Ready>(withIt)
        assertTrue(withIt.python.pathops)
    }

    @Test
    fun aProgramThatRunsButIsNotPythonIsNotAccepted() {
        val runner = FakeRunner(mapOf("python3" to "hello from a shell script\n"))
        val found = locator(runner).resolve()
        assertIs<FontmakeEnvironment.NotReady>(found)
        assertTrue("did not answer like Python 3" in found.reason, found.reason)
    }

    @Test
    fun theProbeOutputIsReadBackExactlyAndJunkIsNull() {
        val facts = assertNotNull(PythonLocator.parseProbe(probe(ufo2ft = false)))
        assertEquals(true to "3.12.2", facts["fontmake"])
        assertEquals(false, facts["ufo2ft"]?.first)
        assertEquals(true to "3.11.17", facts["python"])
        assertNull(PythonLocator.parseProbe("Traceback (most recent call last):\n  File \"x\", line 1\n"))
    }

    @Test
    fun theProbeScriptAsksAboutEveryPackageTheBackendNeeds() {
        // The probe is real Python, run by real interpreters in the integration test; here its package list is kept in step with the messages.
        for (module in listOf("fontmake", "ufo2ft", "fontTools", "pathops")) {
            assertTrue("probe(\"$module\"" in PythonLocator.PROBE_SCRIPT, module)
        }
        assertTrue("sys.version_info" in PythonLocator.PROBE_SCRIPT)
    }
}
