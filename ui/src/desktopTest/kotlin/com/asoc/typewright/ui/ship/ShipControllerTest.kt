// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.ship

import com.asoc.typewright.compile.BackendAvailability
import com.asoc.typewright.compile.CompileLogLine
import com.asoc.typewright.compile.CompileResult
import com.asoc.typewright.compile.FontBinary
import com.asoc.typewright.compile.OutputFormat
import com.asoc.typewright.project.FolderTreeWriter
import com.asoc.typewright.project.TreeWriteException
import com.asoc.typewright.project.TreeWriteReport
import com.asoc.typewright.project.TreeWriter
import com.asoc.typewright.qa.CheckOutcome
import com.asoc.typewright.qa.CheckStatus
import com.asoc.typewright.qa.LayerOneAvailability
import com.asoc.typewright.qa.LayerOneReport
import com.asoc.typewright.qa.LayerOneRuntime
import com.asoc.typewright.qa.report.CheckReport
import com.asoc.typewright.qa.report.Severity
import com.asoc.typewright.qa.report.buildCheckReport
import com.asoc.typewright.qa.ship.ShipMode
import com.asoc.typewright.ui.check.CheckTestProject
import com.asoc.typewright.ui.check.FakeChecker
import com.asoc.typewright.ui.check.FakeCompiler
import com.asoc.typewright.ui.project.SampleProject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The ship, step by step, against stand-ins for the compiler, the checker and the writer, and once against the real folder writer. What it
 * proves is the order and the guarantees: nothing is built or written while Check has fails, the check runs on the compiled bytes, a fail on
 * them stops the ship, and cancelling, a failure or a block leaves the target as it was.
 */
class ShipControllerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val workspace = SampleProject.workspace()
    private val project get() = SampleProject.openProject(workspace)
    private val subject: ShipSubject = checkNotNull(shipSubjectOf(project))
    private val realReport: CheckReport = buildCheckReport(subject.check.inputs)

    /** A test fixture: the report with its fails flipped to passes. The app never does this. */
    private val cleanReport: CheckReport =
        realReport.copy(items = realReport.items.map { if (it.severity == Severity.FAIL) it.copy(severity = Severity.PASS) else it })

    private val binary = byteArrayOf(0, 1, 0, 0, 7, 7, 7, -1)
    private val passing =
        LayerOneReport(
            listOf(
                CheckOutcome("com.google.fonts/check/ok", CheckStatus.PASS, "fine"),
                CheckOutcome("com.google.fonts/check/w", CheckStatus.WARN, "hm"),
            ),
            tool = LayerOneRuntime("Fontbakery", "0.12.10", "on this computer"),
        )

    /** A writer that keeps what it is given, and can be made to wait or fail. */
    private class Spy(
        val gate: CompletableDeferred<Unit>? = null,
        val failWith: String? = null,
    ) : TreeWriter {
        override val description = "spy"
        var calls = 0
        var files: Map<String, ByteArray> = emptyMap()
        var owned: List<String> = emptyList()

        override suspend fun write(
            files: Map<String, ByteArray>,
            ownedDirectories: Collection<String>,
        ): TreeWriteReport {
            calls++
            gate?.await()
            failWith?.let { throw TreeWriteException(it) }
            this.files = files
            owned = ownedDirectories.toList()
            return TreeWriteReport(files.size, 0, files.values.sumOf { it.size.toLong() })
        }
    }

    private fun controller(
        compiler: FakeCompiler = FakeCompiler.succeeding(binary),
        checker: FakeChecker = FakeChecker(report = passing),
        memory: ShipMemory = ShipMemory(),
    ) = ShipController(scope, compiler, checker, memory)

    private fun request(
        target: ShipTarget,
        report: CheckReport? = cleanReport,
        mode: ShipMode = ShipMode.GOOGLE_FONTS,
    ) = ShipRequest(subject, mode, null, target, report)

    private suspend fun ShipController.finished(): ShipRunState {
        settled()
        return state.value
    }

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    // ---- the block rule ------------------------------------------------------------------------------

    @Test
    fun whileCheckHasFailsNothingIsStartedBuiltOrWritten() =
        runBlocking {
            val compiler = FakeCompiler.succeeding(binary)
            val checker = FakeChecker(report = passing)
            val spy = Spy()
            val c = controller(compiler, checker)
            assertTrue(realReport.counts.fail > 0, "the real Hyle Deco report has fails, or this test proves nothing")

            val started = c.ship(request(ShipTarget("spy", spy), report = realReport))

            val blocked = assertIs<ShipStart.Blocked>(started)
            assertEquals(realReport.counts.fail, blocked.gate.failCount)
            assertEquals(
                realReport.items
                    .filter { it.severity == Severity.FAIL }
                    .map { it.id }
                    .toSet(),
                blocked.gate.blockers
                    .map { it.id }
                    .toSet(),
            )
            assertEquals(ShipRunState.Idle, c.state.value, "a blocked ship never leaves Idle")
            assertEquals(0, compiler.calls.get(), "no compile while blocked")
            assertEquals(0, checker.checked.size)
            assertEquals(0, spy.calls, "no write while blocked")
        }

    @Test
    fun aLayerOneFailFromTheCheckPageBlocksToo() =
        runBlocking {
            val spy = Spy()
            val failing = LayerOneReport(listOf(CheckOutcome("com.google.fonts/check/gasp", CheckStatus.FAIL, "missing gasp")))
            val started = controller().ship(ShipRequest(subject, ShipMode.GOOGLE_FONTS, null, ShipTarget("spy", spy), cleanReport, failing))
            assertIs<ShipStart.Blocked>(started)
            assertEquals(0, spy.calls)
        }

    // ---- the clean build -------------------------------------------------------------------------------

    @Test
    fun aCleanShipWritesTheProjectsUfoTheCompiledBinaryAndTheTextFiles() =
        runBlocking {
            val compiler = FakeCompiler.succeeding(binary)
            val checker = FakeChecker(report = passing)
            val spy = Spy()
            val memory = ShipMemory()
            val c = controller(compiler, checker, memory)

            assertEquals(ShipStart.Started, c.ship(request(ShipTarget("~/Fonts/hyle-deco-gf", spy))))
            val done = assertIs<ShipRunState.Done>(c.finished())

            // The UFO in the folder is the project's own, byte for byte, as the session wrote it.
            val snapshot = subject.check.snapshot()
            val prefix = subject.masterPath + "/"
            val projectUfo = snapshot.listFiles().filter { it.startsWith(prefix) }
            assertTrue(projectUfo.size > 300, "the project's UFO has hundreds of files: ${projectUfo.size}")
            for (path in projectUfo) {
                assertContentEquals(
                    snapshot.readBytes(path),
                    spy.files.getValue("sources/HyleDeco-Regular.ufo/${path.removePrefix(prefix)}"),
                    path,
                )
            }
            assertEquals(
                projectUfo.size,
                spy.files.keys.count { it.startsWith("sources/HyleDeco-Regular.ufo/") },
                "nothing else in the UFO",
            )
            assertEquals(listOf("sources/HyleDeco-Regular.ufo"), spy.owned)
            // The font is exactly what the compiler produced.
            assertContentEquals(binary, spy.files.getValue("fonts/ttf/HyleDeco-Regular.ttf"))
            // The text files are made from the project's own facts.
            val ofl = spy.files.getValue("OFL.txt").decodeToString()
            assertEquals(subject.facts.copyright, ofl.lines().first())
            assertTrue(
                spy.files
                    .getValue("METADATA.pb")
                    .decodeToString()
                    .contains("name: \"Hyle Deco\""),
            )
            assertFalse("AUTHORS.txt" in spy.files, "the sample states no designer, so no authors file")
            // The record of it.
            assertEquals(
                StepState.DONE,
                done.result.steps
                    .single { it.step == ShipStep.UFO }
                    .state,
            )
            assertEquals(
                ShipStep.entries.toSet(),
                done.result.steps
                    .map { it.step }
                    .toSet(),
            )
            assertTrue(done.result.steps.all { it.state == StepState.DONE }, done.result.steps.toString())
            assertEquals("~/Fonts/hyle-deco-gf", done.result.target)
            assertTrue(done.result.withFont)
            assertEquals("Fontbakery 0.12.10: 0 fail · 1 warn · 1 pass", done.result.checkLine)
            assertTrue(done.result.omissions.any { it.path == "AUTHORS.txt" }, "the missing designer is listed, not hidden")
            assertEquals(done.result, memory.resultFor(subject.version), "the drawing that was shipped is remembered")
        }

    @Test
    fun theCheckRunsOnTheCompiledBytesNotOnTheSources() =
        runBlocking {
            val checker = FakeChecker(report = passing)
            val c = controller(checker = checker)
            c.ship(request(ShipTarget("spy", Spy())))
            c.finished()
            val checked = checker.checked.single()
            assertContentEquals(binary, checked.bytes, "layer one must be given the compiler's bytes")
            // Under the name the folder will give the font (the profile judges a file by its name), not the compiler's own.
            assertEquals("${subject.facts.fileStem}.ttf", checked.fileName)
            assertEquals("HyleDeco-Regular.ttf", checked.fileName)
            assertTrue(checked.fileName != "Fake.ttf")
        }

    @Test
    fun theRequestTextCarriesWhatTheBuildFound() =
        runBlocking {
            val spy = Spy()
            val c = controller()
            c.ship(request(ShipTarget("spy", spy)))
            val done = assertIs<ShipRunState.Done>(c.finished())
            val text = checkNotNull(done.result.requestText)
            assertTrue(text.contains("Fontbakery 0.12.10's Google Fonts profile on the compiled font: 0 fail · 1 warn · 1 pass"), text)
            assertEquals(text, spy.files.getValue("documentation/GOOGLE_FONTS_REQUEST.md").decodeToString())
        }

    // ---- the check on the compiled binary -----------------------------------------------------------

    @Test
    fun aFailOnTheCompiledBinaryStopsTheShipAndNothingIsWritten() =
        runBlocking {
            val failing =
                LayerOneReport(
                    listOf(
                        CheckOutcome(
                            "com.google.fonts/check/gasp",
                            CheckStatus.FAIL,
                            "Font is missing the 'gasp' table.",
                            title = "Is gasp set?",
                        ),
                        CheckOutcome("com.google.fonts/check/ok", CheckStatus.PASS, "fine"),
                    ),
                    tool = LayerOneRuntime("Fontbakery", "0.12.10", "on this computer"),
                )
            val spy = Spy()
            val memory = ShipMemory()
            val c = controller(checker = FakeChecker(report = failing), memory = memory)
            c.ship(request(ShipTarget("spy", spy)))
            val stopped = assertIs<ShipRunState.Stopped>(c.finished())
            assertEquals(listOf("Is gasp set?"), stopped.blockers.map { it.title })
            assertEquals(0, spy.calls, "the folder must not be written when the font fails Google Fonts' own checks")
            assertEquals(StepState.FAILED, stopped.steps.single { it.step == ShipStep.CHECK }.state)
            assertEquals(StepState.WAITING, stopped.steps.single { it.step == ShipStep.WRITE }.state)
            assertNull(memory.resultFor(subject.version), "a stopped ship is not remembered as shipped")
        }

    @Test
    fun aLayerOneThatCannotRunDoesNotStopTheShipButIsSaidInWords() =
        runBlocking {
            val spy = Spy()
            val checker =
                FakeChecker(availability = LayerOneAvailability.Unavailable("Fontbakery is not installed. pip install fontbakery"))
            val c = controller(checker = checker)
            c.ship(request(ShipTarget("spy", spy)))
            val done = assertIs<ShipRunState.Done>(c.finished())
            assertEquals(0, checker.checked.size)
            assertEquals("Fontbakery is not installed. pip install fontbakery", done.result.checkSkipped)
            assertNull(done.result.checkLine)
            assertEquals(
                StepState.SKIPPED,
                done.result.steps
                    .single { it.step == ShipStep.CHECK }
                    .state,
            )
            assertEquals(1, spy.calls)
            assertFalse(
                spy.files
                    .getValue("documentation/GOOGLE_FONTS_REQUEST.md")
                    .decodeToString()
                    .contains("Fontbakery"),
            )
        }

    // ---- failures --------------------------------------------------------------------------------------

    @Test
    fun aCompileFailureNamesItsCauseKeepsTheLogAndWritesNothing() =
        runBlocking {
            val compiler =
                FakeCompiler { _, onLog ->
                    onLog(CompileLogLine(CompileLogLine.Level.ERROR, "ufo2ft: boom"))
                    CompileResult.Failure(
                        "fontmake could not read glyph A.",
                        listOf(CompileLogLine(CompileLogLine.Level.ERROR, "ufo2ft: boom")),
                    )
                }
            val spy = Spy()
            val c = controller(compiler)
            c.ship(request(ShipTarget("spy", spy)))
            val failed = assertIs<ShipRunState.Failed>(c.finished())
            assertEquals(ShipStep.COMPILE, failed.step)
            assertEquals("fontmake could not read glyph A.", failed.cause)
            assertEquals(listOf("ufo2ft: boom"), failed.log.map { it.message })
            assertEquals(0, spy.calls)
        }

    @Test
    fun aCompilerThatWritesNoFontIsAFailure() =
        runBlocking {
            val compiler = FakeCompiler { _, _ -> CompileResult.Success(emptyList(), emptyList()) }
            val spy = Spy()
            val c = controller(compiler)
            c.ship(request(ShipTarget("spy", spy)))
            val failed = assertIs<ShipRunState.Failed>(c.finished())
            assertEquals(ShipStep.COMPILE, failed.step)
            assertTrue("wrote no font file" in failed.cause)
            assertEquals(0, spy.calls)
        }

    @Test
    fun aWriteFailureNamesItsCause() =
        runBlocking {
            val spy = Spy(failWith = "Typewright could not write to ~/Fonts/x: the disk is full. Nothing was changed.")
            val memory = ShipMemory()
            val c = controller(memory = memory)
            c.ship(request(ShipTarget("spy", spy)))
            val failed = assertIs<ShipRunState.Failed>(c.finished())
            assertEquals(ShipStep.WRITE, failed.step)
            assertTrue("the disk is full" in failed.cause)
            assertNull(memory.resultFor(subject.version))
        }

    @Test
    fun anUnexpectedCrashIsAStateNamingTheStepNeverAnEscape() =
        runBlocking {
            val compiler = FakeCompiler { _, _ -> throw IllegalStateException("kaboom") }
            val c = controller(compiler)
            c.ship(request(ShipTarget("spy", Spy())))
            val failed = assertIs<ShipRunState.Failed>(c.finished())
            assertEquals(ShipStep.COMPILE, failed.step)
            assertTrue("kaboom" in failed.cause && "Nothing was written" in failed.cause, failed.cause)
        }

    // ---- no compiler ---------------------------------------------------------------------------------------

    @Test
    fun aComputerWithoutACompilerCannotBuildAndSaysWhyInTheBackendsWords() =
        runBlocking {
            val compiler =
                FakeCompiler(
                    BackendAvailability.Unavailable("Python 3 was not found. Install Python 3, then run: pip install fontmake"),
                ) { _, _ ->
                    error("must not compile")
                }
            val spy = Spy()
            val started = controller(compiler).ship(request(ShipTarget("spy", spy), report = cleanReport))
            assertEquals(ShipStart.CannotBuild("Python 3 was not found. Install Python 3, then run: pip install fontmake"), started)
            assertEquals(0, spy.calls)
            assertEquals(0, compiler.calls.get())
        }

    @Test
    fun aPlaceThatTakesAFolderWithoutAFontIsWrittenWithoutOneAndSaysSo() =
        runBlocking {
            val compiler =
                FakeCompiler(BackendAvailability.Unavailable("The web preview does not compile")) { _, _ -> error("must not compile") }
            val checker = FakeChecker(report = passing)
            val spy = Spy()
            val c = controller(compiler, checker)
            assertEquals(ShipStart.Started, c.ship(request(ShipTarget("hyle-deco-gf.zip", spy, withoutFontOk = true))))
            val done = assertIs<ShipRunState.Done>(c.finished())
            assertFalse(done.result.withFont)
            assertTrue(spy.files.keys.none { it.startsWith("fonts/") })
            assertFalse("METADATA.pb" in spy.files, "METADATA describes a font file that is not there")
            assertTrue("OFL.txt" in spy.files && spy.files.keys.any { it.startsWith("sources/") })
            assertEquals(
                StepState.SKIPPED,
                done.result.steps
                    .single { it.step == ShipStep.COMPILE }
                    .state,
            )
            assertEquals(
                StepState.SKIPPED,
                done.result.steps
                    .single { it.step == ShipStep.CHECK }
                    .state,
            )
            assertEquals(0, checker.checked.size)
        }

    // ---- cancelling -----------------------------------------------------------------------------------------

    @Test
    fun cancellingDuringTheCompileStopsItAndWritesNothingAndLeavesTheProjectAsItWas() =
        runBlocking {
            val gate = CompletableDeferred<Unit>()
            val compiler = CheckTestProject.gated(gate)
            val spy = Spy()
            val before = subject.check.snapshot().let { s -> s.listFiles().associateWith { sha(s.readBytes(it)) } }
            val c = controller(compiler)
            c.ship(request(ShipTarget("spy", spy)))
            withTimeout(20_000) {
                c.state.first {
                    it is ShipRunState.Building &&
                        it.steps.any { s -> s.step == ShipStep.COMPILE && s.state == StepState.RUNNING }
                }
            }

            c.cancel()
            assertEquals(ShipRunState.Cancelled, c.state.value)
            c.settled()
            gate.complete(Unit)

            assertEquals(ShipRunState.Cancelled, c.state.value, "a cancelled build never becomes Done")
            assertEquals(0, spy.calls)
            val after = subject.check.snapshot().let { s -> s.listFiles().associateWith { sha(s.readBytes(it)) } }
            assertEquals(before, after, "the project's files are untouched")
        }

    @Test
    fun cancellingDuringTheWriteLeavesTheWriterToUndoItsOwnAndNeverRemembersTheShip() =
        runBlocking {
            val gate = CompletableDeferred<Unit>()
            val spy = Spy(gate = gate)
            val memory = ShipMemory()
            val c = controller(memory = memory)
            c.ship(request(ShipTarget("spy", spy)))
            withTimeout(20_000) {
                c.state.first {
                    it is ShipRunState.Building &&
                        it.steps.any { s -> s.step == ShipStep.WRITE && s.state == StepState.RUNNING }
                }
            }
            withTimeout(20_000) { while (spy.calls == 0) kotlinx.coroutines.delay(10) }

            c.cancel()
            c.settled()

            assertEquals(ShipRunState.Cancelled, c.state.value)
            assertNull(memory.resultFor(subject.version))
        }

    @Test
    fun resetReturnsAFinishedStateToIdleButNeverInterruptsABuild() =
        runBlocking {
            val gate = CompletableDeferred<Unit>()
            val c = controller(CheckTestProject.gated(gate))
            c.ship(request(ShipTarget("spy", Spy())))
            c.reset()
            assertIs<ShipRunState.Building>(c.state.value, "reset must not touch a running build")
            c.cancel()
            c.settled()
            c.reset()
            assertEquals(ShipRunState.Idle, c.state.value)
        }

    // ---- against a real folder -------------------------------------------------------------------------------

    private fun hashes(root: File): Map<String, String> =
        root.walkTopDown().filter { it.isFile }.associate { it.relativeTo(root).path to sha(it.readBytes()) }

    @Test
    fun aBlockedStoppedOrCancelledShipLeavesARealFolderEmpty() =
        runBlocking {
            val parent = Files.createTempDirectory("typewright-ship-").toFile()
            val folder = File(parent, "repo").apply { mkdir() }

            fun real() = ShipTarget("repo", FolderTreeWriter(folder.toPath(), "repo"))

            // Blocked by Check.
            controller().ship(request(real(), report = realReport))
            assertEquals(emptyList(), folder.list()!!.toList(), "blocked: the folder must stay empty")

            // Stopped by the check on the binary.
            val failing = LayerOneReport(listOf(CheckOutcome("com.google.fonts/check/gasp", CheckStatus.FAIL, "missing gasp")))
            val stopped = controller(checker = FakeChecker(report = failing))
            stopped.ship(request(real()))
            assertIs<ShipRunState.Stopped>(stopped.finished())
            assertEquals(emptyList(), folder.list()!!.toList(), "stopped: the folder must stay empty")

            // Cancelled mid-compile.
            val gate = CompletableDeferred<Unit>()
            val cancelled = controller(CheckTestProject.gated(gate))
            cancelled.ship(request(real()))
            withTimeout(20_000) {
                cancelled.state.first {
                    it is ShipRunState.Building &&
                        it.steps.any { s ->
                            s.state == StepState.RUNNING
                        }
                }
            }
            cancelled.cancel()
            cancelled.settled()
            assertEquals(emptyList(), folder.list()!!.toList(), "cancelled: the folder must stay empty")
            assertEquals(listOf("repo"), parent.list()!!.toList(), "no working folder may be left beside it")

            // And the clean ship, to the same folder, writes the whole repository.
            val clean = controller()
            clean.ship(request(real()))
            assertIs<ShipRunState.Done>(clean.finished())
            val written = hashes(folder)
            assertTrue(
                "OFL.txt" in written && "METADATA.pb" in written && "upstream.yaml" in written &&
                    "fonts/ttf/HyleDeco-Regular.ttf" in written,
            )
            assertTrue(written.keys.count { it.startsWith("sources/HyleDeco-Regular.ufo/") } > 300)
            assertEquals(listOf("repo"), parent.list()!!.toList())
        }
}
