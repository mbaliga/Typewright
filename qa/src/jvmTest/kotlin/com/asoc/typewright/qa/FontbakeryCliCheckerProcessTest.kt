// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import kotlin.concurrent.thread
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * [FontbakeryCliChecker] against a stand-in `python` that is a real process: the command it is given, the
 * report it reads back, what happens when there is no report, and that an interrupted check stops the
 * process and everything it started. The real Fontbakery is exercised by [FontbakeryPythonIntegrationTest].
 */
class FontbakeryCliCheckerProcessTest {
    private val directories = mutableListOf<File>()

    @AfterTest
    fun cleanUp() {
        directories.forEach { it.deleteRecursively() }
    }

    private val reportJson =
        """{"result": {"FAIL": 1, "PASS": 1}, "sections": [{"checks": [
          {"description": "Names are good.", "key": ["s", "<FontBakeryCheck:com.google.fonts/check/name/good>", []],
           "logs": [{"message": {"code": null, "message": "OK"}, "status": "PASS"}], "result": "PASS"},
          {"description": "Filename is canonical.", "key": ["s", "<FontBakeryCheck:com.google.fonts/check/canonical_filename>", []],
           "logs": [{"message": {"code": "bad-filename", "message": "Expected X.ttf"}, "status": "FAIL"}], "result": "FAIL"}
        ]}]}"""

    /** A stand-in python that answers the probes like a real one, then runs [onCheck] when asked to check a font. */
    private fun fakePython(onCheck: String): File {
        val dir = Files.createTempDirectory("typewright-fake-bakery-").toFile().also { directories += it }
        File(dir, "report.json.template").writeText(reportJson)
        val script = File(dir, "fake-python")
        script.writeText(
            """
            #!/bin/sh
            DIR="${'$'}(cd "${'$'}(dirname "${'$'}0")" && pwd)"
            if [ "${'$'}1" = "-c" ]; then echo "python ok 3.11.0"; exit 0; fi
            if [ "${'$'}3" = "--version" ]; then echo "9.9.9"; exit 0; fi
            if [ "${'$'}3" = "check-googlefonts" ] && [ "${'$'}4" = "--list-checks" ]; then echo "a check"; exit 0; fi
            echo "${'$'}@" > "${'$'}DIR/args.txt"
            JSON=""
            FONT=""
            prev=""
            for arg in "${'$'}@"; do
              if [ "${'$'}prev" = "--json" ]; then JSON="${'$'}arg"; fi
              prev="${'$'}arg"
              FONT="${'$'}arg"
            done
            """.trimIndent() + "\n" + onCheck.trimIndent() + "\n",
        )
        script.setExecutable(true)
        return script
    }

    private fun checkerFor(script: File) =
        FontbakeryCliChecker({ name -> if (name == PYTHON_ENV_VARIABLE) script.absolutePath else null }, RealCommandRunner, 30_000L)

    private val writesReport =
        """
        cp "${'$'}DIR/report.json.template" "${'$'}JSON"
        cp "${'$'}FONT" "${'$'}DIR/font-as-seen.ttf"
        exit 1
        """.trimIndent()

    @Test
    fun theReportFontbakeryWritesIsReadBackEvenThoughItExitsWithOneWhenACheckFails() {
        val script = fakePython(writesReport)
        val bytes = byteArrayOf(0, 1, 0, 0, 42, 7)
        val report = runSuspend { checkerFor(script).check(LayerOneInput("Font-Regular.ttf", bytes)) }

        assertEquals(listOf("com.google.fonts/check/name/good", "com.google.fonts/check/canonical_filename"), report.results.map { it.id })
        assertEquals(1, report.counts.fail)
        assertEquals(1, report.counts.pass)
        assertEquals(LayerOneRuntime("Fontbakery", "9.9.9", "on this computer"), report.tool)
        // The font was handed over byte for byte, under the name the caller gave it.
        assertContentEquals(bytes, File(script.parentFile, "font-as-seen.ttf").readBytes())
        assertTrue(File(script.parentFile, "args.txt").readText().trim().endsWith("/Font-Regular.ttf"))
    }

    @Test
    fun theCommandIsTheGoogleFontsProfileWithTheNetworkOffAndAJsonReport() {
        val script = fakePython(writesReport)
        runSuspend { checkerFor(script).check(LayerOneInput("Font-Regular.ttf", byteArrayOf(1))) }
        val args = File(script.parentFile, "args.txt").readText().trim().split(' ')
        assertEquals(listOf("-m", "fontbakery", "check-googlefonts"), args.take(3))
        assertTrue("--skip-network" in args, "a check the person started must not use the network")
        assertTrue("--json" in args)
    }

    @Test
    fun theTemporaryDirectoryIsDeletedAfterTheCheck() {
        val script = fakePython(writesReport)
        runSuspend { checkerFor(script).check(LayerOneInput("Font-Regular.ttf", byteArrayOf(1))) }
        val workDirectory =
            File(
                File(script.parentFile, "args.txt")
                    .readText()
                    .trim()
                    .split(' ')
                    .last(),
            ).parentFile
        assertFalse(workDirectory.exists(), "left $workDirectory behind")
    }

    @Test
    fun aPathInTheFileNameCannotEscapeTheTemporaryDirectory() {
        val script = fakePython(writesReport)
        runSuspend { checkerFor(script).check(LayerOneInput("../../etc/evil.ttf", byteArrayOf(1))) }
        val font =
            File(
                File(script.parentFile, "args.txt")
                    .readText()
                    .trim()
                    .split(' ')
                    .last(),
            )
        assertEquals("evil.ttf", font.name)
    }

    @Test
    fun noReportIsOneSkippedOutcomeThatQuotesWhatFontbakeryPrinted() {
        val script = fakePython("echo 'Traceback: it fell over'\nexit 2")
        val report = runSuspend { checkerFor(script).check(LayerOneInput("Font-Regular.ttf", byteArrayOf(1))) }
        val only = report.results.single()
        assertEquals(CheckStatus.SKIP, only.status)
        assertTrue("wrote no report" in only.message && "it fell over" in only.message, only.message)
    }

    @Test
    fun aReportWithNoChecksInItIsNotMistakenForAllClear() {
        val script = fakePython("""echo '{"sections": []}' > "${'$'}JSON"; exit 0""")
        val report = runSuspend { checkerFor(script).check(LayerOneInput("Font-Regular.ttf", byteArrayOf(1))) }
        assertEquals(CheckStatus.SKIP, report.results.single().status)
        assertEquals(0, report.counts.pass)
    }

    @Test
    fun aCheckThatRunsPastItsTimeoutIsStoppedAndSkipped() {
        val script = fakePython("sleep 120 &\nwait")
        val quick = FontbakeryCliChecker({ script.absolutePath }, RealCommandRunner, 800L)
        val report = runSuspend { quick.check(LayerOneInput("Font-Regular.ttf", byteArrayOf(1))) }
        val only = report.results.single()
        assertEquals(CheckStatus.SKIP, only.status)
        assertTrue("did not finish within 1 second and was stopped" in only.message, only.message)
    }

    @Test
    fun interruptingTheCallerStopsFontbakeryAndEverythingItStarted() {
        val script = fakePython("sleep 120 &\necho \$! > \"\$DIR/child.pid\"\necho \$\$ > \"\$DIR/self.pid\"\nwait")
        var outcome: Throwable? = null
        val worker =
            thread(name = "layer-one-under-test") {
                outcome =
                    runCatching {
                        runSuspend {
                            checkerFor(
                                script,
                            ).check(LayerOneInput("Font-Regular.ttf", byteArrayOf(1)))
                        }
                    }.exceptionOrNull()
            }
        val self = File(script.parentFile, "self.pid")
        val deadline = System.nanoTime() + 20_000_000_000L
        while (!(self.isFile && self.readText().isNotBlank()) && System.nanoTime() < deadline) Thread.sleep(50)
        assertTrue(self.isFile, "the stand-in fontbakery never started")

        worker.interrupt()
        worker.join(15_000)

        assertFalse(worker.isAlive, "the check did not stop when it was interrupted")
        assertNotNull(outcome, "an interrupted check must not look like a result")
        assertTrue(outcome is CancellationException, "was $outcome")
        for (name in listOf("self.pid", "child.pid")) assertDead(File(script.parentFile, name))
    }

    @Test
    fun cancellingTheCoroutineStopsFontbakeryAndEverythingItStartedAndNeverBlocksTheCaller() =
        runBlocking<Unit> {
            val script = fakePython("sleep 120 &\necho \$! > \"\$DIR/child.pid\"\necho \$\$ > \"\$DIR/self.pid\"\nwait")
            val checker = BackgroundLayerOneChecker(checkerFor(script))
            val job = launch { checker.check(LayerOneInput("Font-Regular.ttf", byteArrayOf(1))) }
            val self = File(script.parentFile, "self.pid")
            withTimeout(20_000) { while (!(self.isFile && self.readText().isNotBlank())) delay(50) }

            // The caller's own thread was free all the while: it could run this loop while Fontbakery ran.
            assertTrue(job.isActive, "the check is running behind the caller")
            job.cancelAndJoin()

            assertTrue(job.isCancelled)
            for (name in listOf("self.pid", "child.pid")) assertDead(File(script.parentFile, name))
        }

    private fun assertDead(pidFile: File) {
        val pid = pidFile.readText().trim().toLong()
        val deadline = System.nanoTime() + 10_000_000_000L
        while (System.nanoTime() < deadline) {
            val stat = File("/proc/$pid/stat")
            val state =
                if (stat.isFile) {
                    stat
                        .readText()
                        .substringAfterLast(')')
                        .trim()
                        .firstOrNull()
                } else {
                    null
                }
            if (state == null || state == 'Z' || state == 'X') return
            Thread.sleep(50)
        }
        fail("process $pid (from ${pidFile.name}) is still running")
    }
}
