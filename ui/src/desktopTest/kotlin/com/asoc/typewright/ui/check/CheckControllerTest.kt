// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.check

import com.asoc.typewright.compile.BackendAvailability
import com.asoc.typewright.compile.CompileLogLine
import com.asoc.typewright.compile.CompileResult
import com.asoc.typewright.compile.CompileSource
import com.asoc.typewright.qa.CheckOutcome
import com.asoc.typewright.qa.CheckStatus
import com.asoc.typewright.qa.LayerOneAvailability
import com.asoc.typewright.qa.LayerOneReport
import com.asoc.typewright.qa.report.ReviewOrder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CheckControllerTest {
    private fun controller(
        compiler: FakeCompiler,
        checker: FakeChecker = FakeChecker(),
        memory: CheckMemory = CheckMemory(),
    ) = CheckController(CheckTestProject.scope(), compiler, checker, memory)

    @Test
    fun layerTwoAlwaysRunsAndCoversEveryCheck() =
        runBlocking<Unit> {
            val c = controller(FakeCompiler.succeeding())
            c.open(CheckTestProject.subject)
            val ready = c.await { it.layerTwo is LayerTwoState.Ready && it.layerOne !is LayerOneState.Running }
            val report = assertIs<LayerTwoState.Ready>(ready.layerTwo).report
            assertEquals(ReviewOrder.ALL.toSet(), report.items.map { it.id }.toSet())
            assertTrue(report.counts.fail > 0, "Hyle Deco, a traced font, fails: ${report.counts.line()}")
        }

    @Test
    fun aCheckThatThrowsIsAnErrorStateWithTheCauseNotAnEscapeAndRunAgainRecovers() =
        runBlocking<Unit> {
            var attempts = 0
            val c =
                CheckController(
                    CheckTestProject.scope(),
                    FakeCompiler.succeeding(),
                    FakeChecker(),
                    CheckMemory(),
                    buildReport = { inputs ->
                        if (attempts++ == 0) throw IllegalStateException("the contour of T has no points")
                        com.asoc.typewright.qa.report
                            .buildCheckReport(inputs)
                    },
                )
            c.open(CheckTestProject.subject)
            val stopped = c.await { it.layerTwo is LayerTwoState.Failed }
            assertEquals("the contour of T has no points", (stopped.layerTwo as LayerTwoState.Failed).cause)
            assertEquals(null, stopped.report, "there is no report to draw")
            assertTrue(!stopped.isRunning, "the right circle is free, so Run again is the way to retry")
            // The throw did not escape the controller's scope, and layer one did not start on a project that layer two choked on.
            c.settled()
            assertIs<LayerOneState.NotRun>(c.state.value.layerOne)

            c.runAgain(CheckTestProject.subject)
            val ready = c.await { it.layerTwo is LayerTwoState.Ready }
            assertTrue(checkNotNull(ready.report).items.isNotEmpty())
            assertEquals(2, attempts)
        }

    @Test
    fun anExceptionWithNoMessageIsNamedByItsType() =
        runBlocking<Unit> {
            val c =
                CheckController(
                    CheckTestProject.scope(),
                    FakeCompiler.succeeding(),
                    FakeChecker(),
                    CheckMemory(),
                    buildReport = { throw NullPointerException() },
                )
            c.open(CheckTestProject.subject)
            val stopped = c.await { it.layerTwo is LayerTwoState.Failed }
            assertEquals("NullPointerException", (stopped.layerTwo as LayerTwoState.Failed).cause)
        }

    @Test
    fun layerOneCompilesTheMastersUfoFromASnapshotAndChecksTheBinary() =
        runBlocking<Unit> {
            val compiler = FakeCompiler.succeeding(byteArrayOf(9, 8, 7))
            val checker = FakeChecker()
            val subject = CheckTestProject.subject
            val c = controller(compiler, checker)
            c.open(subject)
            val done = c.await { it.layerOne is LayerOneState.Done }
            // The compiler was asked for this master's UFO.
            val request = compiler.requests.single()
            assertEquals(CompileSource.Ufo(subject.masterPath), request.source)
            assertTrue(request.project.listFiles().any { it.endsWith("fontinfo.plist") }, "a snapshot of the whole project was handed over")
            // The checker was handed the compiler's bytes, under the binary's own name.
            assertEquals(
                listOf(9, 8, 7),
                checker.checked
                    .single()
                    .bytes
                    .map { it.toInt() },
            )
            assertEquals("Fake.ttf", checker.checked.single().fileName)
            // And the report carries layer one's results next to layer two's.
            val report = checkNotNull(done.report)
            assertEquals(FakeChecker.report(), report.layerOne)
            assertEquals(runtimeOf(done), "Fontbakery 0.12.10 on this computer")
        }

    private fun runtimeOf(state: CheckRunState) = state.runtime?.description

    @Test
    fun aPlatformWithoutLayerOneSaysSoAndNeverCompiles() =
        runBlocking<Unit> {
            val reason = "Layer one needs the desktop app: Fontbakery and Fontspector run on a computer."
            val compiler = FakeCompiler.succeeding()
            val c = controller(compiler, FakeChecker(availability = LayerOneAvailability.Unavailable(reason), runtime = null))
            c.open(CheckTestProject.subject)
            val state = c.await { it.layerOne is LayerOneState.Unavailable }
            assertEquals(reason, (state.layerOne as LayerOneState.Unavailable).reason)
            assertEquals(0, compiler.calls.get(), "nothing was compiled")
            assertIs<LayerTwoState.Ready>(state.layerTwo)
            assertEquals("Layer one needs the desktop app · layer two here", CheckText.runtimeLine(state))
        }

    @Test
    fun aCompilerThatCannotRunMeansLayerOneCannotRunEither() =
        runBlocking<Unit> {
            val c = controller(FakeCompiler(BackendAvailability.Unavailable("Python is missing.")) { _, _ -> error("never called") })
            c.open(CheckTestProject.subject)
            val state = c.await { it.layerOne is LayerOneState.Unavailable }
            assertTrue((state.layerOne as LayerOneState.Unavailable).reason.contains("Python is missing."))
        }

    @Test
    fun aFailedCompileNamesTheCauseAndKeepsTheLog() =
        runBlocking<Unit> {
            val log = listOf(CompileLogLine(CompileLogLine.Level.ERROR, "ufo2ft: glyph T has an open contour"))
            val c = controller(FakeCompiler { _, _ -> CompileResult.Failure("The font source has an open contour in T.", log) })
            c.open(CheckTestProject.subject)
            val failed = c.await { it.layerOne is LayerOneState.Failed }.layerOne as LayerOneState.Failed
            assertEquals(LayerOneStage.COMPILE, failed.stage)
            assertEquals("The font source has an open contour in T.", failed.cause)
            assertEquals(log, failed.log)
        }

    @Test
    fun aStubBackendIsUnavailableNotAnError() =
        runBlocking<Unit> {
            val stub = CompileResult.NotImplemented("stub", "Compiling on this phone is not built in this version.", emptyList())
            val c = controller(FakeCompiler { _, _ -> stub })
            c.open(CheckTestProject.subject)
            val state = c.await { it.layerOne is LayerOneState.Unavailable }.layerOne as LayerOneState.Unavailable
            assertEquals("Compiling on this phone is not built in this version.", state.reason)
        }

    @Test
    fun aCheckerThatOnlySkipsIsUnavailableNotAPass() =
        runBlocking<Unit> {
            val skipped =
                LayerOneReport(listOf(CheckOutcome("layer-one/unavailable", CheckStatus.SKIP, "fontbakery did not run: no network.")))
            val c = controller(FakeCompiler.succeeding(), FakeChecker(report = skipped))
            c.open(CheckTestProject.subject)
            val state = c.await { it.layerOne is LayerOneState.Unavailable }.layerOne as LayerOneState.Unavailable
            assertEquals("fontbakery did not run: no network.", state.reason)
        }

    @Test
    fun anExceptionFromTheCompilerIsAPlainFailureNotACrash() =
        runBlocking<Unit> {
            val c = controller(FakeCompiler { _, _ -> throw IllegalStateException("the disk is full") })
            c.open(CheckTestProject.subject)
            val failed = c.await { it.layerOne is LayerOneState.Failed }.layerOne as LayerOneState.Failed
            assertTrue(failed.cause.contains("the disk is full"), failed.cause)
        }

    @Test
    fun theCompilersLogLinesReachTheScreenWhileItRuns() =
        runBlocking<Unit> {
            val gate = CompletableDeferred<Unit>()
            val c = controller(CheckTestProject.gated(gate))
            c.open(CheckTestProject.subject)
            val running = c.await { (it.layerOne as? LayerOneState.Running)?.log?.isNotEmpty() == true }.layerOne as LayerOneState.Running
            assertEquals(LayerOneStage.COMPILE, running.stage)
            assertEquals("fontmake: starting", running.log.single().message)
            gate.complete(Unit)
            c.await { it.layerOne is LayerOneState.Done }
        }

    @Test
    fun cancellingStopsTheCompileAndReportsCancelledWithLayerTwoIntact() =
        runBlocking<Unit> {
            val cancelledInside = CompletableDeferred<Boolean>()
            val c =
                controller(
                    FakeCompiler { _, _ ->
                        try {
                            awaitCancellation()
                        } finally {
                            cancelledInside.complete(true)
                        }
                    },
                )
            c.open(CheckTestProject.subject)
            c.await { it.layerOne is LayerOneState.Running }
            c.cancel()
            val state = c.await { it.layerOne == LayerOneState.Cancelled }
            assertTrue(withTimeout(10_000) { cancelledInside.await() }, "the compile saw the cancellation and could clean up")
            assertIs<LayerTwoState.Ready>(state.layerTwo, "layer two's report is still there")
            yield()
            assertEquals(LayerOneState.Cancelled, c.state.value.layerOne, "it stays cancelled; it does not start again")
        }

    @Test
    fun cancelDoesNothingWhenNothingIsRunning() =
        runBlocking<Unit> {
            val c = controller(FakeCompiler.succeeding())
            c.open(CheckTestProject.subject)
            val done = c.await { it.layerOne is LayerOneState.Done }
            c.cancel()
            assertEquals(done.layerOne, c.state.value.layerOne)
        }

    @Test
    fun openingAgainOnTheSameDrawingDoesNotCompileAgainButRunAgainDoes() =
        runBlocking<Unit> {
            val compiler = FakeCompiler.succeeding()
            val c = controller(compiler)
            val subject = CheckTestProject.subject
            c.open(subject)
            c.await { it.layerOne is LayerOneState.Done }
            assertEquals(1, compiler.calls.get())
            c.open(subject)
            c.await { it.layerTwo is LayerTwoState.Ready && it.layerOne is LayerOneState.Done }
            assertEquals(1, compiler.calls.get(), "the drawing has not changed, so the result is reused")
            c.runAgain(subject)
            c.await { compiler.calls.get() == 2 && it.layerOne is LayerOneState.Done }
            assertEquals(2, compiler.calls.get(), "Run again always runs")
        }

    @Test
    fun aDrawingThatChangedIsNotGivenTheOldResult() =
        runBlocking<Unit> {
            val compiler = FakeCompiler.succeeding()
            val memory = CheckMemory()
            val c = controller(compiler, memory = memory)
            val subject = CheckTestProject.subject
            c.open(subject)
            c.await { it.layerOne is LayerOneState.Done }
            val changed = CheckSubject(subject.name, subject.inputs, subject.masterPath, subject.snapshot, version = Any())
            c.open(changed)
            c.await { compiler.calls.get() == 2 && it.layerOne is LayerOneState.Done }
            assertEquals(2, compiler.calls.get())
        }

    @Test
    fun runningAgainWhileRunningStopsTheFirstRunBeforeStartingTheSecond() =
        runBlocking<Unit> {
            val order = java.util.Collections.synchronizedList(mutableListOf<String>())
            val started =
                java.util.concurrent.atomic
                    .AtomicInteger()
            val compiler =
                FakeCompiler { _, _ ->
                    val n = started.incrementAndGet()
                    order += "start$n"
                    try {
                        if (n == 1) awaitCancellation()
                        CompileResult.Success(emptyList(), emptyList())
                    } finally {
                        // Slow to clean up, as a real build is: a second run must wait for this to finish.
                        withContext(NonCancellable) { delay(1500) }
                        order += "end$n"
                    }
                }
            val c = controller(compiler)
            val subject = CheckTestProject.subject
            c.open(subject)
            c.await { it.layerOne is LayerOneState.Running }
            c.runAgain(subject)
            c.await { started.get() == 2 && it.layerOne !is LayerOneState.Running }
            assertEquals(
                listOf("start1", "end1", "start2", "end2"),
                order.toList(),
                "the first compile finished stopping before the second began",
            )
        }
}
