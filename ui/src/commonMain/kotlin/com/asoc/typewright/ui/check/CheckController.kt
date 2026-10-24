// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.check

import com.asoc.typewright.compile.BackendAvailability
import com.asoc.typewright.compile.CompileBackend
import com.asoc.typewright.compile.CompileLogLine
import com.asoc.typewright.compile.CompileRequest
import com.asoc.typewright.compile.CompileResult
import com.asoc.typewright.compile.CompileSource
import com.asoc.typewright.compile.OutputFormat
import com.asoc.typewright.qa.LayerOneAvailability
import com.asoc.typewright.qa.LayerOneChecker
import com.asoc.typewright.qa.LayerOneInput
import com.asoc.typewright.qa.LayerOneReport
import com.asoc.typewright.qa.LayerOneRuntime
import com.asoc.typewright.qa.report.CheckInputs
import com.asoc.typewright.qa.report.CheckReport
import com.asoc.typewright.qa.report.buildCheckReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * The two steps layer one takes: compiling the project, then checking the compiled binary. [title] names the step in a
 * list of steps; [activity] finishes the sentence "Layer one stopped while ...".
 */
enum class LayerOneStage(
    val title: String,
    val activity: String,
) {
    COMPILE("Compile the project with fontmake", "compiling the project with fontmake"),
    CHECK("Check the compiled binary", "checking the compiled binary"),
}

/** Where layer two stands. It is quick, but it is still kept off the screen's thread. */
sealed interface LayerTwoState {
    data object Running : LayerTwoState

    data class Ready(
        val report: CheckReport,
    ) : LayerTwoState

    /** One of the checks threw, so there is no report; [cause] is what it said, for the page to print and the person to report. */
    data class Failed(
        val cause: String,
    ) : LayerTwoState
}

/** Where layer one stands: not run, cannot run here, running, done, stopped by an error, or cancelled. */
sealed interface LayerOneState {
    /** Nothing has run yet, and it can. */
    data object NotRun : LayerOneState

    /** It cannot run here, and [reason] says why in the words the backend gave. */
    data class Unavailable(
        val reason: String,
    ) : LayerOneState

    /**
     * It is running at [stage]. [startedAt] is when the whole run began and [compileSeconds] how long the compile took once
     * that stage is over; [log] is what the compiler has printed so far.
     */
    data class Running(
        val stage: LayerOneStage,
        val startedAt: TimeMark,
        val compileSeconds: Int?,
        val log: List<CompileLogLine>,
    ) : LayerOneState

    /** It finished: [report] is the tool's own, and [version] names the drawing it ran on. */
    data class Done(
        val report: LayerOneReport,
        val version: Any,
    ) : LayerOneState

    /** It stopped at [stage] with [cause], one plain sentence naming what went wrong; [log] is the raw output. */
    data class Failed(
        val stage: LayerOneStage,
        val cause: String,
        val log: List<CompileLogLine>,
    ) : LayerOneState

    /** The person cancelled it. Nothing was written to the project. */
    data object Cancelled : LayerOneState
}

/** Everything the Check screen draws: layer two, layer one, and what runs layer one on this computer. */
data class CheckRunState(
    val layerTwo: LayerTwoState = LayerTwoState.Running,
    val layerOne: LayerOneState = LayerOneState.NotRun,
    /** The tool layer one would run, when it can run; null otherwise. */
    val runtime: LayerOneRuntime? = null,
    /** True once the first look at what this computer can run has finished. */
    val probed: Boolean = false,
) {
    /** The layer-two report with layer one's results attached when it has finished. */
    val report: CheckReport?
        get() {
            val ready = (layerTwo as? LayerTwoState.Ready)?.report ?: return null
            return ready.withLayerOne((layerOne as? LayerOneState.Done)?.report)
        }

    val isRunning: Boolean get() = layerTwo is LayerTwoState.Running || layerOne is LayerOneState.Running
}

/** The last layer-one result and the drawing it ran on, kept so that leaving Check and coming back does not compile again. */
class CheckMemory {
    private var last: LayerOneState.Done? = null

    /** The remembered result when it ran on [version], else null. */
    fun resultFor(version: Any): LayerOneState.Done? = last?.takeIf { it.version === version || it.version == version }

    fun remember(done: LayerOneState.Done) {
        last = done
    }

    companion object {
        /** The one the app shares. */
        val shared = CheckMemory()
    }
}

/**
 * Runs Check: layer two (Typewright's own fourteen checks over the project's UFO) and layer one (compile the
 * project with the platform's compiler, then run the googlefonts profile of Fontspector or Fontbakery on the
 * binary). It holds no Compose and no platform code: the compiler and the checker come in as interfaces, so
 * the same class runs on a computer, where both are real, and on a phone or in a browser, where they say they
 * cannot and layer two still runs.
 *
 * [open] shows a page: layer two runs, and layer one runs too unless it has already run on this exact drawing.
 * [runAgain] runs both whatever has run. [cancel] stops layer one and leaves the project exactly as it was: the
 * compile works on a copy of the project's files in a temporary place and removes it.
 */
class CheckController(
    private val scope: CoroutineScope,
    private val compile: CompileBackend,
    private val layerOne: LayerOneChecker,
    private val memory: CheckMemory = CheckMemory.shared,
    private val work: CoroutineDispatcher = Dispatchers.Default,
    /** How layer two's report is built from what the page reads; the app's own checks unless a test stands one in that throws. */
    private val buildReport: (CheckInputs) -> CheckReport = ::buildCheckReport,
) {
    private val mutable = MutableStateFlow(CheckRunState())

    /** What the screen draws. */
    val state: StateFlow<CheckRunState> = mutable.asStateFlow()

    private var job: Job? = null

    /** Shows [subject]: layer two always, layer one when it can run and has not already run on this drawing. */
    fun open(subject: CheckSubject) = start(subject, force = false)

    /** Runs layer two and layer one again on [subject], whatever ran before. */
    fun runAgain(subject: CheckSubject) = start(subject, force = true)

    /** Stops layer one if it is running. The project is untouched; the temporary copy the compile made is removed. */
    fun cancel() {
        if (mutable.value.layerOne !is LayerOneState.Running) return
        job?.cancel()
        mutable.update { it.copy(layerOne = LayerOneState.Cancelled) }
    }

    /**
     * Returns when the run in hand has stopped and cleaned up after itself: finished, failed or cancelled. A test (or a
     * caller about to start again) waits on this instead of on the clock.
     */
    internal suspend fun settled() {
        job?.join()
    }

    private fun start(
        subject: CheckSubject,
        force: Boolean,
    ) {
        val previous = job
        previous?.cancel()
        mutable.update { it.copy(layerTwo = LayerTwoState.Running) }
        job =
            scope.launch {
                // The run being replaced has finished stopping, and cleaned up after itself, before this one begins.
                previous?.join()
                val report =
                    try {
                        withContext(work) { buildReport(subject.inputs) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Throwable) {
                        // A check that throws is a state the page shows, with the cause and a way to try again, never an escape.
                        mutable.update { it.copy(layerTwo = LayerTwoState.Failed(describe(failure))) }
                        return@launch
                    }
                mutable.update { it.copy(layerTwo = LayerTwoState.Ready(report)) }
                runLayerOne(subject, force)
            }
    }

    private fun describe(failure: Throwable): String =
        failure.message?.takeIf { it.isNotBlank() } ?: failure::class.simpleName ?: "an error"

    private suspend fun runLayerOne(
        subject: CheckSubject,
        force: Boolean,
    ) {
        val unavailable = whyLayerOneCannotRun()
        val runtime = if (unavailable == null) runCatching { layerOne.runtime() }.getOrNull() else null
        if (unavailable != null) {
            mutable.update { it.copy(layerOne = LayerOneState.Unavailable(unavailable), runtime = null, probed = true) }
            return
        }
        val remembered = if (force) null else memory.resultFor(subject.version)
        if (remembered != null) {
            mutable.update { it.copy(layerOne = remembered, runtime = runtime, probed = true) }
            return
        }
        mutable.update { it.copy(runtime = runtime, probed = true) }
        val startedAt = TimeSource.Monotonic.markNow()
        val log = MutableStateFlow(emptyList<CompileLogLine>())

        fun running(
            stage: LayerOneStage,
            compileSeconds: Int? = null,
        ) = LayerOneState.Running(stage, startedAt, compileSeconds, log.value.takeLast(LOG_KEPT))

        mutable.update { it.copy(layerOne = running(LayerOneStage.COMPILE)) }
        try {
            val request = CompileRequest(subject.snapshot(), CompileSource.Ufo(subject.masterPath))
            val result =
                compile.compile(request) { line ->
                    log.update { it + line }
                    mutable.update { now ->
                        val state = now.layerOne
                        if (state is LayerOneState.Running && state.stage == LayerOneStage.COMPILE) {
                            now.copy(layerOne = state.copy(log = log.value.takeLast(LOG_KEPT)))
                        } else {
                            now
                        }
                    }
                }
            when (result) {
                is CompileResult.NotImplemented -> {
                    mutable.update { it.copy(layerOne = LayerOneState.Unavailable(result.planned), runtime = null) }
                }

                is CompileResult.Failure -> {
                    mutable.update { it.copy(layerOne = LayerOneState.Failed(LayerOneStage.COMPILE, result.reason, result.log)) }
                }

                is CompileResult.Success -> {
                    val binary = result.binaries.firstOrNull { it.format == OutputFormat.TTF } ?: result.binaries.firstOrNull()
                    if (binary == null) {
                        mutable.update {
                            it.copy(
                                layerOne =
                                    LayerOneState.Failed(
                                        LayerOneStage.COMPILE,
                                        "The compiler finished but wrote no font file, so there was nothing to check.",
                                        result.log,
                                    ),
                            )
                        }
                        return
                    }
                    val seconds = startedAt.elapsedNow().inWholeSeconds.toInt()
                    mutable.update { it.copy(layerOne = running(LayerOneStage.CHECK, seconds)) }
                    val checked = layerOne.check(LayerOneInput(binary.fileName.ifBlank { subject.binaryName }, binary.bytes))
                    val next =
                        if (checked.isUnavailable) {
                            LayerOneState.Unavailable(checked.results.single().message)
                        } else {
                            LayerOneState.Done(checked, subject.version).also { memory.remember(it) }
                        }
                    mutable.update { it.copy(layerOne = next) }
                }
            }
        } catch (cancelled: CancellationException) {
            mutable.update { if (it.layerOne is LayerOneState.Running) it.copy(layerOne = LayerOneState.Cancelled) else it }
            throw cancelled
        } catch (failure: Throwable) {
            val stage = (mutable.value.layerOne as? LayerOneState.Running)?.stage ?: LayerOneStage.COMPILE
            val cause = "Layer one stopped: ${failure.message ?: failure::class.simpleName}."
            mutable.update { it.copy(layerOne = LayerOneState.Failed(stage, cause, log.value)) }
        }
    }

    /** Why layer one cannot run on this platform now, in the backend's own words, or null when it can. */
    private suspend fun whyLayerOneCannotRun(): String? {
        (layerOne.availability() as? LayerOneAvailability.Unavailable)?.let { return it.reason }
        (compile.availability() as? BackendAvailability.Unavailable)?.let { return "Layer one needs a compiled font, and ${it.reason}" }
        return null
    }

    private companion object {
        const val LOG_KEPT = 200
    }
}
