// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa

import kotlinx.serialization.json.Json
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The real `fontspector` binary could not be installed in this build environment (see
 * [FontspectorCliChecker]'s own KDoc for exactly what was tried), so these tests exercise the same
 * two paths a real run would take: a fake script standing in for the binary (proving the
 * process-invocation and JSON-parsing plumbing end to end) and the binary genuinely absent (the
 * path this repository's own `:qa:check` actually exercises today).
 */
class FontspectorCliCheckerTest {
    private val workDirs = mutableListOf<File>()

    @AfterTest
    fun cleanup() {
        workDirs.forEach { it.deleteRecursively() }
    }

    private fun fakeScript(body: String): String {
        val dir = createTempDirectory("fontspector-fake-").toFile()
        workDirs += dir
        val script = File(dir, "fontspector")
        script.writeText(body)
        script.setExecutable(true)
        return script.absolutePath
    }

    @Test
    fun availabilityIsUnavailableWhenTheBinaryIsNotOnPath() {
        val checker = FontspectorCliChecker(binaryName = "typewright-definitely-not-a-real-binary")
        val availability = runSuspend { checker.availability() }
        assertIs<LayerOneAvailability.Unavailable>(availability)
        assertTrue(availability.reason.contains("not found on PATH"))
    }

    @Test
    fun checkReturnsASkipOutcomeWhenTheBinaryIsMissing() {
        val checker = FontspectorCliChecker(binaryName = "typewright-definitely-not-a-real-binary")
        val report = runSuspend { checker.check(LayerOneInput("font.ttf", byteArrayOf(1, 2, 3))) }
        assertEquals(1, report.results.size)
        assertEquals(CheckStatus.SKIP, report.results.single().status)
        assertEquals(1, report.counts.skip)
        assertEquals(0, report.counts.pass + report.counts.warn + report.counts.fail)
    }

    @Test
    fun availabilityIsAvailableWhenAFakeBinaryAnswersVersion() {
        val script = fakeScript("#!/bin/sh\nexit 0\n")
        val checker = FontspectorCliChecker(binaryName = script)
        assertEquals(LayerOneAvailability.Available, runSuspend { checker.availability() })
    }

    @Test
    fun checkParsesAFakeBinarysJsonReportIntoOutcomes() {
        val script =
            fakeScript(
                """
                #!/bin/sh
                if [ "$1" = "--version" ]; then exit 0; fi
                prev=""
                jsonpath=""
                for arg in "$@"; do
                  if [ "${'$'}prev" = "--json" ]; then jsonpath="${'$'}arg"; fi
                  prev="${'$'}arg"
                done
                cat > "${'$'}jsonpath" <<'REPORT'
                {
                  "result": {
                    "font.ttf": {
                      "Metadata Checks": [
                        {
                          "check_id": "com.google.fonts/check/name/family_name",
                          "logs": [
                            {"status": "PASS", "message": "Name looks fine."}
                          ]
                        },
                        {
                          "check_id": "com.google.fonts/check/glyf_nested_components",
                          "logs": [
                            {"status": "FAIL", "message": "Some glyphs reference nested components."}
                          ]
                        }
                      ]
                    }
                  }
                }
                REPORT
                exit 1
                """.trimIndent(),
            )
        val checker = FontspectorCliChecker(binaryName = script)
        val report = runSuspend { checker.check(LayerOneInput("font.ttf", byteArrayOf(1, 2, 3, 4))) }

        assertTrue(report.results.any { it.id == "com.google.fonts/check/name/family_name" && it.status == CheckStatus.PASS })
        assertTrue(
            report.results.any {
                it.id == "com.google.fonts/check/glyf_nested_components" && it.status == CheckStatus.FAIL
            },
        )
        assertEquals(1, report.counts.pass)
        assertEquals(1, report.counts.fail)
    }

    @Test
    fun checkFallsBackToAnUnavailableReportWhenTheJsonHasNoRecognisableShape() {
        val script =
            fakeScript(
                """
                #!/bin/sh
                if [ "$1" = "--version" ]; then exit 0; fi
                prev=""
                jsonpath=""
                for arg in "$@"; do
                  if [ "${'$'}prev" = "--json" ]; then jsonpath="${'$'}arg"; fi
                  prev="${'$'}arg"
                done
                echo '{"nothing_recognisable": true}' > "${'$'}jsonpath"
                exit 0
                """.trimIndent(),
            )
        val checker = FontspectorCliChecker(binaryName = script)
        val report = runSuspend { checker.check(LayerOneInput("font.ttf", byteArrayOf(1))) }
        assertEquals(1, report.results.size)
        assertEquals(CheckStatus.SKIP, report.results.single().status)
    }

    @Test
    fun parseFontspectorReportFindsOutcomesAtAnyNestingDepth() {
        val json =
            Json.parseToJsonElement(
                """{"a": {"b": [{"id": "x/y", "status": "warn", "reason": "heads up"}]}}""",
            )
        val outcomes = parseFontspectorReport(json)
        assertEquals(listOf(CheckOutcome("x/y", CheckStatus.WARN, "heads up")), outcomes)
    }

    @Test
    fun parseFontspectorReportMapsInfoToWarnAndErrorToFail() {
        val json =
            Json.parseToJsonElement(
                """[{"id": "a", "status": "info", "message": "m1"}, {"id": "b", "status": "error", "message": "m2"}]""",
            )
        val outcomes = parseFontspectorReport(json)
        assertEquals(CheckStatus.WARN, outcomes.single { it.id == "a" }.status)
        assertEquals(CheckStatus.FAIL, outcomes.single { it.id == "b" }.status)
    }
}
