// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Finding Fontbakery, and saying plainly what is missing; every command is answered by a fake, so no Python is started. */
class FontbakeryLocatorTest {
    /** Answers commands from [script]; any command it does not know cannot be started. */
    private class Fake(
        private val script: (List<String>) -> Captured?,
    ) : CommandRunner {
        val asked = mutableListOf<List<String>>()

        override fun run(
            command: List<String>,
            timeoutMillis: Long,
        ): Captured {
            asked += command
            return script(command) ?: Captured(null, "", "could not be started (No such file or directory)")
        }
    }

    private val pythonOk = Captured(0, "python ok 3.11.17\n")
    private val fontbakeryVersion = Captured(0, "0.12.10\n")
    private val profileOk = Captured(0, "com.google.fonts/check/foo\n")

    private fun checker(
        runner: CommandRunner,
        variable: String? = null,
    ) = FontbakeryCliChecker({ name -> if (name == PYTHON_ENV_VARIABLE) variable else null }, runner, 1_000L)

    private fun fullInstall(python: String = "python3") =
        Fake { cmd ->
            when {
                cmd.first() != python -> null
                cmd[1] == "-c" -> pythonOk
                cmd.drop(1) == listOf("-m", "fontbakery", "--version") -> fontbakeryVersion
                cmd.drop(1) == listOf("-m", "fontbakery", "check-googlefonts", "--list-checks") -> profileOk
                else -> null
            }
        }

    private fun reason(
        runner: CommandRunner,
        variable: String? = null,
    ): String {
        val availability = runSuspend { checker(runner, variable).availability() }
        assertIs<LayerOneAvailability.Unavailable>(availability)
        return availability.reason
    }

    @Test
    fun aPythonWithFontbakeryAndItsProfileIsAvailableAndSaysWhichVersion() {
        val c = checker(fullInstall())
        assertEquals(LayerOneAvailability.Available, runSuspend { c.availability() })
        val runtime = assertNotNull(runSuspend { c.runtime() })
        assertEquals(LayerOneRuntime("Fontbakery", "0.12.10", "on this computer"), runtime)
        assertEquals("Fontbakery 0.12.10 on this computer", runtime.description)
    }

    @Test
    fun aPythonThatWasFoundIsRememberedSoTheProbesAreNotRepeated() {
        val runner = fullInstall()
        val c = checker(runner)
        runSuspend { c.availability() }
        val probes = runner.asked.size
        assertTrue(probes >= 3, "the first look asks Python, Fontbakery and its profile")
        runSuspend { c.runtime() }
        runSuspend { c.availability() }
        assertEquals(probes, runner.asked.size, "found once, asked once")
    }

    @Test
    fun aMissIsNeverRememberedSoInstallingTheToolThenAskingAgainFindsIt() {
        var installed = false
        val runner =
            Fake { cmd ->
                when {
                    cmd.first() != "python3" -> null
                    cmd[1] == "-c" -> pythonOk
                    !installed -> Captured(1, "No module named fontbakery")
                    cmd.drop(1) == listOf("-m", "fontbakery", "--version") -> fontbakeryVersion
                    cmd.drop(1) == listOf("-m", "fontbakery", "check-googlefonts", "--list-checks") -> profileOk
                    else -> null
                }
            }
        val c = checker(runner)
        assertIs<LayerOneAvailability.Unavailable>(runSuspend { c.availability() })
        installed = true
        assertEquals(LayerOneAvailability.Available, runSuspend { c.availability() })
    }

    @Test
    fun aRememberedPythonIsNotUsedOnceTheVariableNamesAnotherOne() {
        var variable: String? = null
        val runner = Fake { cmd -> fullInstall(cmd.first()).run(cmd, 1L).takeIf { cmd.first() in setOf("python3", "/venv/bin/python") } }
        val c = FontbakeryCliChecker({ name -> if (name == PYTHON_ENV_VARIABLE) variable else null }, runner, 1_000L)
        runSuspend { c.availability() }
        variable = "/venv/bin/python"
        runSuspend { c.availability() }
        assertTrue(runner.asked.any { it.first() == "/venv/bin/python" }, "the new variable was looked at")
    }

    @Test
    fun theVariablesPythonIsTriedFirst() {
        val runner = fullInstall("/venv/bin/python")
        val c = checker(runner, variable = "/venv/bin/python")
        assertEquals(LayerOneAvailability.Available, runSuspend { c.availability() })
        assertEquals("/venv/bin/python", runner.asked.first().first())
        assertTrue(runner.asked.none { it.first() == "python3" })
    }

    @Test
    fun noPythonSaysSoAndNamesTheInstallCommand() {
        val text = reason(Fake { null }, variable = "/nope/python")
        assertTrue(text.startsWith("Fontbakery was not found: Python 3 was not found on this computer."), text)
        assertTrue("TYPEWRIGHT_PYTHON (/nope/python) could not be started" in text, text)
        assertTrue("python3 could not be started" in text, text)
        assertTrue(text.endsWith("Install Python 3, then run: pip install \"fontbakery[googlefonts]\""), text)
    }

    @Test
    fun aPythonWithoutFontbakeryNamesItAndTheExactPipCommand() {
        val runner =
            Fake { cmd ->
                when {
                    cmd[1] == "-c" -> pythonOk
                    cmd.contains("--version") -> Captured(1, "/usr/bin/python3: No module named fontbakery\n")
                    else -> null
                }
            }
        assertEquals(
            "Fontbakery is not installed in Python 3.11.17 (python3). Install it with: pip install \"fontbakery[googlefonts]\"",
            reason(runner),
        )
    }

    @Test
    fun theInstallCommandNamesTheInterpreterWhenItIsNotPlainPython3() {
        val runner = Fake { cmd -> if (cmd[1] == "-c") pythonOk else Captured(1, "No module named fontbakery") }
        assertTrue(reason(runner, "/venv/bin/python").endsWith("/venv/bin/python -m pip install \"fontbakery[googlefonts]\""))
    }

    @Test
    fun fontbakeryWithoutItsGoogleFontsExtrasNamesTheMissingPackage() {
        val runner =
            Fake { cmd ->
                when {
                    cmd[1] == "-c" -> pythonOk
                    cmd.contains("--version") -> fontbakeryVersion
                    cmd.contains("--list-checks") -> Captured(1, "ModuleNotFoundError: No module named 'glyphsets'\n")
                    else -> null
                }
            }
        val text = reason(runner)
        assertTrue("Fontbakery 0.12.10 is installed in Python 3.11.17 (python3)" in text, text)
        assertTrue("the package \"glyphsets\" is missing" in text, text)
        assertTrue(text.endsWith("pip install \"fontbakery[googlefonts]\""), text)
    }

    @Test
    fun aProfileThatFailsToLoadForAnotherReasonIsStillNamedNotHidden() {
        val runner =
            Fake { cmd ->
                when {
                    cmd[1] == "-c" -> pythonOk
                    cmd.contains("--version") -> fontbakeryVersion
                    else -> Captured(1, "ValueError: Check 'googlefonts/glyphsets/shape_languages' not found\n")
                }
            }
        assertTrue("its checks could not be loaded" in reason(runner))
    }

    @Test
    fun withNoToolTheCheckIsOneSkippedOutcomeThatNamesThePipCommand() {
        val report = runSuspend { checker(Fake { null }).check(LayerOneInput("Font-Regular.ttf", byteArrayOf(1, 2, 3))) }
        val only = report.results.single()
        assertEquals(CheckStatus.SKIP, only.status)
        assertTrue("pip install \"fontbakery[googlefonts]\"" in only.message, only.message)
        assertEquals(LayerOneCounts(pass = 0, warn = 0, fail = 0, skip = 1), report.counts)
    }
}
