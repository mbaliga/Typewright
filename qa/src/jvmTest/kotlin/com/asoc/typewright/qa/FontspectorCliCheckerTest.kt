// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
    fun cancellingTheCallerStopsFontspectorAndEverythingItStarted() =
        runBlocking<Unit> {
            val pids = File(createTempDirectory("fontspector-pids-").toFile().also { workDirs += it }, "pids")
            val script =
                fakeScript(
                    """
                    #!/bin/sh
                    if [ "${'$'}1" = "--version" ]; then exit 0; fi
                    sleep 120 &
                    echo "${'$'}! ${'$'}${'$'}" > "${pids.absolutePath}"
                    wait
                    """.trimIndent() + "\n",
                )
            val checker = BackgroundLayerOneChecker(FontspectorCliChecker(binaryName = script))
            val job = launch { checker.check(LayerOneInput("font.ttf", byteArrayOf(1))) }
            withTimeout(20_000) { while (!(pids.isFile && pids.readText().isNotBlank())) delay(50) }
            val (child, self) =
                pids
                    .readText()
                    .trim()
                    .split(' ')
                    .map { it.toLong() }
            job.cancelAndJoin()
            for (pid in listOf(child, self)) {
                withTimeout(10_000) { while (isRunning(pid)) delay(50) }
            }
        }

    private fun isRunning(pid: Long): Boolean {
        val stat = File("/proc/$pid/stat")
        if (!stat.isFile) return false
        val state =
            stat
                .readText()
                .substringAfterLast(')')
                .trim()
                .firstOrNull()
        return state != 'Z' && state != 'X'
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
    fun parseFontspectorReportKeepsInfoAsANoteAndMapsErrorToFail() {
        val json =
            Json.parseToJsonElement(
                """[{"id": "a", "status": "info", "message": "m1"}, {"id": "b", "status": "error", "message": "m2"}]""",
            )
        val outcomes = parseFontspectorReport(json)
        assertEquals(CheckStatus.INFO, outcomes.single { it.id == "a" }.status, "a note is not a warning")
        assertEquals(CheckStatus.FAIL, outcomes.single { it.id == "b" }.status)
        assertEquals(0, LayerOneCounts.of(outcomes).warn)
    }

    /** A fake binary that prints [bytes] bytes to standard output and to standard error, then writes a one-check report. */
    private fun chattyScript(bytes: Int): String =
        fakeScript(
            """
            #!/bin/sh
            if [ "${'$'}1" = "--version" ]; then exit 0; fi
            prev=""
            jsonpath=""
            for arg in "${'$'}@"; do
              if [ "${'$'}prev" = "--json" ]; then jsonpath="${'$'}arg"; fi
              prev="${'$'}arg"
            done
            head -c $bytes /dev/zero | tr '\0' 'x'
            head -c $bytes /dev/zero | tr '\0' 'y' >&2
            echo '[{"id": "a/b", "status": "pass", "message": "ok"}]' > "${'$'}jsonpath"
            """.trimIndent() + "\n",
        )

    @Test
    fun aToolThatPrintsMoreThanAPipeHoldsIsReadWhileItRunsAndDoesNotStall() {
        // 300 KB on each stream is far past the 64 KiB a pipe holds. A checker that waits for the tool to exit before it
        // reads would leave the tool blocked on its own output until the time limit, and return no report.
        val checker = FontspectorCliChecker(binaryName = chattyScript(300_000), timeoutSeconds = 30)
        val started = System.nanoTime()
        val report = runSuspend { checker.check(LayerOneInput("font.ttf", byteArrayOf(1))) }
        val seconds = (System.nanoTime() - started) / 1_000_000_000.0
        assertEquals(listOf(CheckStatus.PASS), report.results.map { it.status }, report.results.toString())
        assertTrue(seconds < 20, "the tool finished at once; reading it took $seconds s")
    }

    @Test
    fun aToolThatRunsPastTheTimeIsStoppedWithEverythingItStarted() {
        val pids = File(createTempDirectory("fontspector-pids-").toFile().also { workDirs += it }, "pids")
        val script =
            fakeScript(
                """
                #!/bin/sh
                if [ "${'$'}1" = "--version" ]; then exit 0; fi
                sleep 120 &
                echo "${'$'}! ${'$'}${'$'}" > "${pids.absolutePath}"
                wait
                """.trimIndent() + "\n",
            )
        val checker = FontspectorCliChecker(binaryName = script, timeoutSeconds = 1)
        val report = runSuspend { checker.check(LayerOneInput("font.ttf", byteArrayOf(1))) }
        assertTrue(report.isUnavailable, "a tool that never finishes gives no report: ${report.results}")
        assertTrue("did not finish within 1 second" in report.results.single().message, report.results.single().message)
        val (child, self) =
            pids
                .readText()
                .trim()
                .split(' ')
                .map { it.toLong() }
        runBlocking {
            for (pid in listOf(child, self)) {
                withTimeout(10_000) { while (isRunning(pid)) delay(50) }
            }
        }
    }
}
