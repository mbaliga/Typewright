// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.check

import com.asoc.typewright.compile.BackendAvailability
import com.asoc.typewright.compile.CompileBackend
import com.asoc.typewright.compile.CompileLocation
import com.asoc.typewright.compile.CompileLogLine
import com.asoc.typewright.compile.CompileRequest
import com.asoc.typewright.compile.CompileResult
import com.asoc.typewright.compile.FontBinary
import com.asoc.typewright.compile.OutputFormat
import com.asoc.typewright.qa.CheckOutcome
import com.asoc.typewright.qa.CheckStatus
import com.asoc.typewright.qa.LayerOneAvailability
import com.asoc.typewright.qa.LayerOneChecker
import com.asoc.typewright.qa.LayerOneInput
import com.asoc.typewright.qa.LayerOneReport
import com.asoc.typewright.qa.LayerOneRuntime
import com.asoc.typewright.ui.project.OpenProject
import com.asoc.typewright.ui.project.SampleProject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger

/** Tests only: a compiler whose answer, speed and log the test decides, so the controller's every branch can be walked without Python. */
internal class FakeCompiler(
    private val availability: BackendAvailability = BackendAvailability.Available,
    private val answer: suspend (CompileRequest, (CompileLogLine) -> Unit) -> CompileResult,
) : CompileBackend {
    override val name = "fake compiler"
    override val location: CompileLocation = CompileLocation.OnThisComputer("a fake")
    val calls = AtomicInteger()
    val requests = mutableListOf<CompileRequest>()

    override suspend fun availability() = availability

    override suspend fun compile(
        request: CompileRequest,
        onLog: (CompileLogLine) -> Unit,
    ): CompileResult {
        calls.incrementAndGet()
        synchronized(requests) { requests += request }
        return answer(request, onLog)
    }

    companion object {
        fun succeeding(bytes: ByteArray = byteArrayOf(1, 2, 3)) =
            FakeCompiler { _, onLog ->
                onLog(CompileLogLine(CompileLogLine.Level.INFO, "compiling"))
                CompileResult.Success(listOf(FontBinary("Fake.ttf", OutputFormat.TTF, bytes)), emptyList())
            }
    }
}

/** Tests only: a layer-one checker with a fixed report (or a fixed unavailability) that records what it was asked to check. */
internal class FakeChecker(
    private val availability: LayerOneAvailability = LayerOneAvailability.Available,
    private val report: LayerOneReport = FakeChecker.report(),
    private val runtime: LayerOneRuntime? = LayerOneRuntime("Fontbakery", "0.12.10", "on this computer"),
) : LayerOneChecker {
    override val name = "fake checker"
    val checked = mutableListOf<LayerOneInput>()

    override suspend fun availability() = availability

    override suspend fun runtime() = runtime

    override suspend fun check(input: LayerOneInput): LayerOneReport {
        synchronized(checked) { checked += input }
        return report
    }

    companion object {
        fun report() =
            LayerOneReport(
                listOf(
                    CheckOutcome(
                        "com.google.fonts/check/name/trailing_spaces",
                        CheckStatus.FAIL,
                        "Name table entries have trailing spaces.",
                    ),
                    CheckOutcome("com.google.fonts/check/os2/xavgcharwidth", CheckStatus.WARN, "OS/2.xAvgCharWidth differs a little."),
                    CheckOutcome("com.google.fonts/check/fsselection", CheckStatus.PASS, "OK"),
                    CheckOutcome("com.google.fonts/check/fvar", CheckStatus.SKIP, "No fvar table."),
                ),
            )
    }
}

internal object CheckTestProject {
    /** The repository's sample font (Hyle Deco), imported and open, as a screen reads it. */
    val workspace by lazy { SampleProject.workspace() }
    val project: OpenProject get() = SampleProject.openProject(workspace)
    val subject: CheckSubject get() = checkNotNull(checkSubjectOf(project)) { "the sample has a master" }

    fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** A suspended run that the test finishes by completing [gate]. */
    fun gated(gate: CompletableDeferred<Unit>) =
        FakeCompiler { _, onLog ->
            onLog(CompileLogLine(CompileLogLine.Level.INFO, "fontmake: starting"))
            gate.await()
            CompileResult.Success(listOf(FontBinary("Fake.ttf", OutputFormat.TTF, byteArrayOf(1))), emptyList())
        }
}

/** Waits (really, up to [seconds]) for the controller to reach a state [predicate] accepts; fails with the last state otherwise. */
internal suspend fun CheckController.await(
    seconds: Long = 20,
    predicate: (CheckRunState) -> Boolean,
): CheckRunState =
    try {
        withTimeout(seconds * 1000) { state.first(predicate) }
    } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
        throw AssertionError("the controller never reached the state; it was ${state.value}", timeout)
    }
