// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.compile

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The desktop backend as a whole, against a stand-in interpreter that is a real process: what it writes
 * for fontmake to read, the command it runs, how output reaches the caller, and what happens when the
 * build fails, is cancelled or runs too long. The real fontmake is exercised by
 * [FontmakePythonIntegrationTest].
 */
class SystemPythonFontmakeBackendTest {
    private val fakes = mutableListOf<FakePython>()

    @AfterTest
    fun cleanUp() {
        fakes.forEach { it.cleanUp() }
    }

    private fun fake(
        behaviour: String,
        pathops: Boolean = true,
    ) = FakePython(behaviour, pathops).also { fakes += it }

    private val project = fixtureProject()
    private val request = CompileRequest(project, CompileSource.Ufo("Fixture-Regular.ufo"))

    private fun digest(files: com.asoc.typewright.project.ProjectFiles): Map<String, String> =
        files.listFiles().associateWith { path ->
            MessageDigest.getInstance("SHA-256").digest(files.readBytes(path)).joinToString("") { "%02x".format(it) }
        }

    // --- availability ---------------------------------------------------------------------------

    @Test
    fun aPythonThatHasFontmakeMakesTheBackendAvailable() {
        val python = fake(FakePython.succeedsAfter(0))
        assertEquals(BackendAvailability.Available, runBlocking { python.backend().availability() })
    }

    @Test
    fun withoutAnyPythonAvailabilitySaysSoAndNeverThrows() {
        val none = ProcessRunner { _, _ -> ProcessOutcome(null, "", "could not be started (No such file or directory)") }
        val backend = SystemPythonFontmakeBackend(env = { null }, runner = none, timeoutMillis = 1_000L)
        val availability = runBlocking { backend.availability() }
        assertIs<BackendAvailability.Unavailable>(availability)
        assertContains(availability.reason, "Python 3 was not found on this computer")
        assertContains(availability.reason, "pip install fontmake")
    }

    @Test
    fun aRunnerThatBlowsUpIsReportedAsUnavailableNotThrown() {
        val explodes = ProcessRunner { _, _ -> throw IllegalStateException("the runner broke") }
        val backend = SystemPythonFontmakeBackend(env = { null }, runner = explodes, timeoutMillis = 1_000L)
        val availability = runBlocking { backend.availability() }
        assertIs<BackendAvailability.Unavailable>(availability)
        assertContains(availability.reason, "the runner broke")
    }

    @Test
    fun theBackendSaysItRunsOnThisComputer() {
        val backend = SystemPythonFontmakeBackend()
        assertContains(backend.location.description, "on this computer")
        assertIs<SystemPythonFontmakeBackend>(platformCompileBackend())
    }

    // --- what fontmake is given -------------------------------------------------------------------

    @Test
    fun fontmakeReadsAFreshCubicCopyOfTheUfoAndTheProjectIsNeverTouched() {
        val python = fake(FakePython.succeedsAfter(0))
        val before = digest(project)

        val result = runBlocking { python.backend().compile(request) }

        assertIs<CompileResult.Success>(result)
        assertEquals(before, digest(project), "building changed the project's files")
        val args = assertNotNull(python.arguments())
        assertEquals(listOf("-m", "fontmake", "-u"), args.take(3))
        assertTrue(args[3].endsWith("/source/Fixture-Regular.ufo"), args[3])
        assertEquals(listOf("-o", "ttf", "--output-dir"), args.subList(4, 7))
        // The copy fontmake read has the O as cubic curves, where the project holds it as quadratic qcurve points.
        val seen = File(python.directory, "O.glif").readText()
        assertTrue("type=\"curve\"" in seen && "qcurve" !in seen, seen)
        assertContains(File(python.directory, "glyph-files.txt").readText(), "O_.glif")
        assertTrue("qcurve" in project.readBytes("Fixture-Regular.ufo/glyphs/O_.glif").decodeToString())
    }

    @Test
    fun theBuildDirectoryIsDeletedAfterASuccessAndAfterAFailure() {
        val ok = fake(FakePython.succeedsAfter(0))
        runBlocking { ok.backend().compile(request) }
        assertFalse(ok.workDirectory().exists(), "left ${ok.workDirectory()} behind after a success")

        val bad = fake(FakePython.FAILS)
        runBlocking { bad.backend().compile(request) }
        assertFalse(bad.workDirectory().exists(), "left ${bad.workDirectory()} behind after a failure")
    }

    @Test
    fun overlapsAreRemovedWithPathopsWhenItImports() {
        val python = fake(FakePython.succeedsAfter(0), pathops = true)
        val result = runBlocking { python.backend().compile(request) }
        val args = assertNotNull(python.arguments())
        assertContains(args, "--overlaps-backend")
        assertEquals("pathops", args[args.indexOf("--overlaps-backend") + 1])
        assertFalse("--keep-overlaps" in args)
        assertTrue(result.log.none { it.message.contains("Overlaps were not removed") })
    }

    @Test
    fun withoutPathopsOverlapsAreKeptAndTheLogSaysSo() {
        val python = fake(FakePython.succeedsAfter(0), pathops = false)
        val result = runBlocking { python.backend().compile(request) }
        assertIs<CompileResult.Success>(result)
        val args = assertNotNull(python.arguments())
        assertContains(args, "--keep-overlaps")
        assertFalse("pathops" in args)
        val warning = result.log.single { it.message.startsWith("Overlaps were not removed") }
        assertEquals(CompileLogLine.Level.WARNING, warning.level)
        assertContains(warning.message, "pip install skia-pathops")
    }

    @Test
    fun keepingOverlapsWhenTheOptionsAskNeverWarns() {
        val python = fake(FakePython.succeedsAfter(0))
        val result = runBlocking { python.backend().compile(request.copy(options = CompileOptions(removeOverlaps = false))) }
        assertContains(assertNotNull(python.arguments()), "--keep-overlaps")
        assertTrue(result.log.none { it.level == CompileLogLine.Level.WARNING && it.message.contains("Overlaps were not removed") })
    }

    @Test
    fun withoutTtfautohintTheLogSaysNotHintedAndTheBuildGoesOn() {
        val python = fake(FakePython.succeedsAfter(0))
        val result = runBlocking { python.backend(path = "").compile(request) }
        assertIs<CompileResult.Success>(result)
        assertFalse("--autohint" in assertNotNull(python.arguments()))
        assertEquals(CompileLogLine.Level.INFO, result.log.single { it.message == "not hinted: ttfautohint not found" }.level)
    }

    @Test
    fun withTtfautohintOnPathItRuns() {
        val python = fake(FakePython.succeedsAfter(0))
        val bin = File(python.directory, "bin").apply { mkdirs() }
        File(bin, "ttfautohint").apply {
            writeText("#!/bin/sh\nexit 0\n")
            setExecutable(true)
        }
        val result = runBlocking { python.backend(path = bin.absolutePath).compile(request) }
        assertIs<CompileResult.Success>(result)
        assertContains(assertNotNull(python.arguments()), "--autohint")
        assertTrue(result.log.none { it.message.startsWith("not hinted") })
    }

    @Test
    fun askingForNoHintingNeverMentionsIt() {
        val python = fake(FakePython.succeedsAfter(0))
        val result = runBlocking { python.backend().compile(request.copy(options = CompileOptions(autohint = false))) }
        assertTrue(result.log.none { it.message.contains("ttfautohint") })
    }

    // --- output -------------------------------------------------------------------------------------

    @Test
    fun theFontmakeOutputIsHandedToTheCallerWhileItRunsNotAfter() {
        val python = fake(FakePython.succeedsAfter(2))
        val seenAt = CopyOnWriteArrayList<Pair<Long, CompileLogLine>>()
        val started = System.nanoTime()

        val result = runBlocking { python.backend().compile(request) { seenAt += (System.nanoTime() - started) / 1_000_000 to it } }

        assertIs<CompileResult.Success>(result)
        val building = seenAt.single { it.second.message.startsWith("INFO:fontmake.font_project:Building") }
        val warning = seenAt.single { it.second.message.startsWith("WARNING:ufo2ft") }
        assertTrue(
            warning.first - building.first >= 1_500,
            "the two lines arrived ${warning.first - building.first} ms apart; the script waits 2 s between them",
        )
        assertEquals(CompileLogLine.Level.WARNING, warning.second.level)
        assertEquals(CompileLogLine.Level.INFO, building.second.level)
        // What the caller was told is what the result carries, in the same order.
        assertEquals(result.log, seenAt.map { it.second })
    }

    @Test
    fun aSuccessReturnsTheTtfFontmakeWrote() {
        val python = fake(FakePython.succeedsAfter(0))
        val result = runBlocking { python.backend().compile(request) }
        assertIs<CompileResult.Success>(result)
        val binary = result.binaries.single()
        assertEquals("Fixture-Regular.ttf", binary.fileName)
        assertEquals(OutputFormat.TTF, binary.format)
        assertContentEquals("FAKE-TTF-BYTES".encodeToByteArray(), binary.bytes)
    }

    // --- failures -------------------------------------------------------------------------------------

    @Test
    fun aFailedBuildGivesOnePlainSentenceAndKeepsTheRawLog() {
        val python = fake(FakePython.FAILS)
        val result = runBlocking { python.backend().compile(request) }
        assertIs<CompileResult.Failure>(result)
        assertEquals("fontmake stopped with an error: In 'x.ufo': Compiling UFO failed: boom in glyph A.", result.reason)
        assertFalse('\n' in result.reason)
        val raw = result.log.map { it.message }
        assertContains(raw, "Traceback (most recent call last):")
        assertContains(raw, "ufo2ft.errors.InvalidFontData: boom in glyph A")
        assertEquals(CompileLogLine.Level.ERROR, result.log.first { it.message.startsWith("Traceback") }.level)
    }

    @Test
    fun aBuildThatWritesNoFontIsAFailureNotAnEmptySuccess() {
        val python = fake(FakePython.WRITES_NOTHING)
        val result = runBlocking { python.backend().compile(request) }
        assertIs<CompileResult.Failure>(result)
        assertContains(result.reason, "wrote no font file")
    }

    @Test
    fun aBuildThatRunsPastItsTimeoutIsStoppedAndSaysSo() {
        val python = fake(FakePython.HANGS)
        val started = System.nanoTime()
        val result = runBlocking { python.backend(timeoutMillis = 1_200L).compile(request) }
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000
        assertIs<CompileResult.Failure>(result)
        assertContains(result.reason, "did not finish within 2 seconds and was stopped")
        assertTrue(elapsedMillis < 30_000, "took $elapsedMillis ms")
        assertFalse(python.workDirectory().exists())
        assertDead(File(python.directory, "child.pid"))
    }

    @Test
    fun requestsThatCannotBeBuiltAreRefusedBeforeAnythingRuns() {
        val explodes = ProcessRunner { _, _ -> fail("no process should be started for a request that cannot be built") }
        val backend = SystemPythonFontmakeBackend(env = { null }, runner = explodes, timeoutMillis = 1_000L)

        val noUfo = runBlocking { backend.compile(CompileRequest(project, CompileSource.Ufo("Missing.ufo"))) }
        assertIs<CompileResult.Failure>(noUfo)
        assertContains(noUfo.reason, "no UFO at \"Missing.ufo\"")

        val designspace = runBlocking { backend.compile(CompileRequest(project, CompileSource.Designspace("Fixture.designspace"))) }
        assertIs<CompileResult.Failure>(designspace)
        assertContains(designspace.reason, "designspace")

        val otf =
            runBlocking { backend.compile(request.copy(options = CompileOptions(formats = setOf(OutputFormat.TTF, OutputFormat.OTF)))) }
        assertIs<CompileResult.Failure>(otf)
        assertContains(otf.reason, "Only TTF")
    }

    @Test
    fun noPythonIsAFailureWithTheSentenceAvailabilityGives() {
        val none = ProcessRunner { _, _ -> ProcessOutcome(null, "", "could not be started (No such file or directory)") }
        val backend = SystemPythonFontmakeBackend(env = { null }, runner = none, timeoutMillis = 1_000L)
        val result = runBlocking { backend.compile(request) }
        assertIs<CompileResult.Failure>(result)
        val availability = runBlocking { backend.availability() }
        assertIs<BackendAvailability.Unavailable>(availability)
        assertEquals(availability.reason, result.reason)
    }

    // --- cancelling ---------------------------------------------------------------------------------

    @Test
    fun cancellingStopsFontmakeAndEverythingItStartedAndDeletesTheBuildDirectory() {
        val python = fake(FakePython.HANGS)
        val before = digest(project)
        val pidFile = File(python.directory, "child.pid")

        runBlocking {
            val build = async(Dispatchers.Default, start = CoroutineStart.DEFAULT) { python.backend().compile(request) }
            val deadline = System.nanoTime() + 20_000_000_000L
            val selfPid = File(python.directory, "self.pid")
            while (!(selfPid.isFile && selfPid.readText().isNotBlank() && python.argsFile.isFile) && System.nanoTime() < deadline) delay(50)
            assertTrue(selfPid.isFile, "the stand-in fontmake never started")
            val work = python.workDirectory()
            assertTrue(work.isDirectory, "the build directory should exist while fontmake runs")

            build.cancelAndJoin()

            assertTrue(build.isCancelled)
            assertFalse(work.exists(), "cancelling left the build directory behind")
        }
        assertDead(pidFile)
        assertDead(File(python.directory, "self.pid"))
        assertEquals(before, digest(project), "cancelling changed the project")
    }

    private fun assertDead(pidFile: File) {
        val pid = pidFile.readText().trim().toLong()
        val deadline = System.nanoTime() + 10_000_000_000L
        while (System.nanoTime() < deadline) {
            if (!isRunning(pid)) return
            Thread.sleep(50)
        }
        fail("process $pid (from ${pidFile.name}) is still running")
    }

    /** Running, by /proc: a killed process nobody has reaped yet (state Z) is dead, though the JDK still lists it. */
    private fun isRunning(pid: Long): Boolean {
        val stat = File("/proc/$pid/stat")
        if (!stat.isFile) return false
        val state =
            stat
                .readText()
                .substringAfterLast(')')
                .trim()
                .firstOrNull()
        return state != null && state != 'Z' && state != 'X'
    }
}
