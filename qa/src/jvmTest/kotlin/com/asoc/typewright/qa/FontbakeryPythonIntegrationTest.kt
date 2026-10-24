// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The real Fontbakery, run by the Python `TYPEWRIGHT_PYTHON` names, on a real font. Never skipped: without
 * that variable, or with a Python that lacks Fontbakery's googlefonts profile, these fail and say which. The
 * count of failing checks must be the one a direct `fontbakery` run on the same file reports, which is what
 * the Check page promises. Their own Gradle task is `:qa:pythonIntegrationTest`.
 */
class FontbakeryPythonIntegrationTest {
    private fun python(): String {
        val python = System.getenv(PYTHON_ENV_VARIABLE)
        if (python.isNullOrBlank()) {
            fail(
                "$PYTHON_ENV_VARIABLE is not set. Point it at a Python that has Fontbakery's " +
                    "googlefonts profile: $FONTBAKERY_INSTALL_COMMAND",
            )
        }
        return python
    }

    private fun font(): ByteArray {
        val root = System.getProperty("typewright.repoRoot") ?: fail("system property typewright.repoRoot is not set by the build")
        val file = File(root, "fonts/HyleDeco-Regular.ttf")
        assertTrue(file.isFile, "fonts/HyleDeco-Regular.ttf is missing")
        return file.readBytes()
    }

    /** `python -m fontbakery check-googlefonts` run directly, as a person would; the counts in its own JSON. */
    private fun directRun(bytes: ByteArray): Map<String, Int> {
        val work = createTempDirectory("direct-fontbakery-").toFile()
        try {
            val ttf = File(work, "HyleDeco-Regular.ttf").apply { writeBytes(bytes) }
            val json = File(work, "direct.json")
            val process =
                ProcessBuilder(
                    python(),
                    "-m",
                    "fontbakery",
                    "check-googlefonts",
                    "-q",
                    "-n",
                    "-C",
                    "--skip-network",
                    "--json",
                    json.path,
                    ttf.path,
                ).redirectErrorStream(true)
                    .start()
            process.inputStream.readBytes()
            process.waitFor()
            assertTrue(json.isFile, "a direct fontbakery run wrote no report")
            return Json
                .parseToJsonElement(json.readText())
                .jsonObject
                .getValue("result")
                .jsonObject
                .mapValues {
                    it.value.jsonPrimitive.content
                        .toInt()
                }
        } finally {
            work.deleteRecursively()
        }
    }

    @Test
    fun theRealFontbakeryIsFoundAndSaysWhichVersion() {
        python()
        val checker = FontbakeryCliChecker()
        assertEquals(LayerOneAvailability.Available, runSuspend { checker.availability() })
        val runtime = runSuspend { checker.runtime() }
        assertTrue(runtime != null && runtime.tool == "Fontbakery" && !runtime.version.isNullOrBlank(), "$runtime")
    }

    @Test
    fun theFailCountEqualsADirectFontbakeryRunOnTheSameFile() {
        python()
        val bytes = font()
        val report = runSuspend { FontbakeryCliChecker().check(LayerOneInput("HyleDeco-Regular.ttf", bytes)) }
        val direct = directRun(bytes)

        assertTrue(report.results.size > 50, "a real googlefonts run has hundreds of checks, not ${report.results.size}")
        // Fontbakery's FAIL, plus the checks that broke (ERROR) or found the font unusable (FATAL), which are failures here too.
        val expectedFail = (direct["FAIL"] ?: 0) + (direct["ERROR"] ?: 0) + (direct["FATAL"] ?: 0)
        assertEquals(expectedFail, report.counts.fail, "FAIL count: ours against a direct run $direct")
        assertEquals(direct["PASS"] ?: 0, report.counts.pass, "PASS count")
        assertEquals(direct["SKIP"] ?: 0, report.counts.skip, "SKIP count")
        // A note is not a warning: Fontbakery's INFO is its own count, as its own report tallies it.
        assertEquals(direct["WARN"] ?: 0, report.counts.warn, "WARN count")
        assertEquals(direct["INFO"] ?: 0, report.counts.info, "INFO count")
        assertTrue(
            report.results.none { it.status == CheckStatus.SKIP && it.id == "layer-one/unavailable" },
            "layer one must not be a skip",
        )
        assertEquals("Fontbakery", report.tool?.tool)
    }

    @Test
    fun aCheckIsRepeatableTheSameFontGivesTheSameReport() {
        python()
        val bytes = font()
        val first = runSuspend { FontbakeryCliChecker().check(LayerOneInput("HyleDeco-Regular.ttf", bytes)) }
        val second = runSuspend { FontbakeryCliChecker().check(LayerOneInput("HyleDeco-Regular.ttf", bytes)) }
        assertEquals(first.results.map { it.id to it.status }, second.results.map { it.id to it.status })
    }

    @Test
    fun thePlatformCheckerOnThisComputerFindsItAndIsNotASkip() {
        python()
        val checker = platformLayerOneChecker()
        assertIs<DesktopLayerOneChecker>(assertIs<BackgroundLayerOneChecker>(checker).delegate)
        assertEquals(LayerOneAvailability.Available, runSuspend { checker.availability() })
    }
}
