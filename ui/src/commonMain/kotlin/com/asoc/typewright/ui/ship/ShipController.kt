// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.ship

import com.asoc.typewright.compile.BackendAvailability
import com.asoc.typewright.compile.CompileBackend
import com.asoc.typewright.compile.CompileLogLine
import com.asoc.typewright.compile.CompileRequest
import com.asoc.typewright.compile.CompileResult
import com.asoc.typewright.compile.CompileSource
import com.asoc.typewright.compile.OutputFormat
import com.asoc.typewright.project.TreeWriteException
import com.asoc.typewright.project.TreeWriter
import com.asoc.typewright.qa.LayerOneAvailability
import com.asoc.typewright.qa.LayerOneChecker
import com.asoc.typewright.qa.LayerOneInput
import com.asoc.typewright.qa.LayerOneReport
import com.asoc.typewright.qa.report.CheckReport
import com.asoc.typewright.qa.ship.GfCategory
import com.asoc.typewright.qa.ship.LayerOneSummary
import com.asoc.typewright.qa.ship.Omission
import com.asoc.typewright.qa.ship.ShipBlocker
import com.asoc.typewright.qa.ship.ShipGate
import com.asoc.typewright.qa.ship.ShipInputs
import com.asoc.typewright.qa.ship.ShipMode
import com.asoc.typewright.qa.ship.buildShipPlan
import com.asoc.typewright.qa.ship.shipFileStem
import com.asoc.typewright.qa.ship.shipGate
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
import kotlin.time.Clock
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** The four steps of a build, in order, with the words the building page lists them in. */
enum class ShipStep(
    val title: String,
    val activity: String,
) {
    UFO("Write the UFO from the project", "taking the project's files"),
    COMPILE("fontmake · UFO to TTF", "compiling the project with fontmake"),
    CHECK("Check the compiled binary", "checking the compiled binary"),
    WRITE("Write the repository folder", "writing the repository folder"),
}

/** Where one step is: not yet, running, done, left out on this device (with why), or stopped by an error. */
enum class StepState(
    val shape: String,
) {
    WAITING("○"),
    RUNNING("◐"),
    DONE("✓"),
    SKIPPED("–"),
    FAILED("●"),
}

/** One line of the building page: the [step], where it is, how long it took in milliseconds once it is over, and a [note] when there is one to say. */
data class ShipStepStatus(
    val step: ShipStep,
    val state: StepState,
    val millis: Long? = null,
    val note: String? = null,
)

/** What a finished write comes to, for the page that follows the build. */
data class ShipResult(
    /** Where it went, in words (`~/Fonts/hyle-deco-gf`, or `hyle-deco-gf.zip`). */
    val target: String,
    /** The folder as a person reads it: every file, with the UFO folded into one line. */
    val tree: List<String>,
    val files: Int,
    val bytes: Long,
    /** What the plan left out and why: the unmet facts the person still has to supply. */
    val omissions: List<Omission>,
    /** The request text for Google Fonts, or null in a mode that asks for nothing. */
    val requestText: String?,
    /** Whether a font was compiled and written. */
    val withFont: Boolean,
    val mode: ShipMode,
    val compileMillis: Long?,
    /** The one-line tally of the check on the binary, or null where it did not run. */
    val checkLine: String?,
    /** Why the check on the binary did not run, when it did not. */
    val checkSkipped: String?,
    val totalMillis: Long,
    val steps: List<ShipStepStatus>,
)

/** Where a ship stands. */
sealed interface ShipRunState {
    /** Nothing has been started. */
    data object Idle : ShipRunState

    /** A build is running: its [steps], when it [startedAt], and what the compiler has printed so far. */
    data class Building(
        val steps: List<ShipStepStatus>,
        val startedAt: TimeMark,
        val log: List<CompileLogLine>,
    ) : ShipRunState

    /** It finished and the folder is written. */
    data class Done(
        val result: ShipResult,
    ) : ShipRunState

    /** It stopped at [step] for [cause], one plain sentence; nothing was written. [log] is the compiler's output. */
    data class Failed(
        val step: ShipStep,
        val cause: String,
        val steps: List<ShipStepStatus>,
        val log: List<CompileLogLine>,
    ) : ShipRunState

    /** The check on the compiled binary found [blockers], so nothing was written. */
    data class Stopped(
        val blockers: List<ShipBlocker>,
        val steps: List<ShipStepStatus>,
        val log: List<CompileLogLine>,
    ) : ShipRunState

    /** The person cancelled. The project and the target are as they were. */
    data object Cancelled : ShipRunState
}

/**
 * Where the repository goes: [label] in words, and the writer that puts the files there. [withoutFontOk] says the place takes a folder with
 * no font in it when there is no compiler: a zip a browser downloads, or a document tree on a phone. A computer's folder does not: there,
 * a missing compiler is something to fix, not something to write around.
 */
class ShipTarget(
    val label: String,
    val writer: TreeWriter,
    val withoutFontOk: Boolean = false,
)

/** What a ship is asked to do. */
class ShipRequest(
    val subject: ShipSubject,
    val mode: ShipMode,
    val category: GfCategory?,
    val target: ShipTarget,
    /** Layer two's report for [subject], which the block rule reads. Without one nothing is built. */
    val report: CheckReport?,
    /** Layer one's result for this drawing from the Check page, if it has run, which counts toward the block as well. */
    val layerOne: LayerOneReport? = null,
)

/** The answer to [ShipController.ship]: it began, or it did not and why. */
sealed interface ShipStart {
    data object Started : ShipStart

    /** Check has fails, so nothing was started and the target was not touched. */
    data class Blocked(
        val gate: ShipGate,
    ) : ShipStart

    /** It cannot be done here, for [reason] in the backend's words. */
    data class CannotBuild(
        val reason: String,
    ) : ShipStart
}

/** Remembers which drawing was last written, so the deck's Ship label can show ✓ and the page can say so after coming back. */
class ShipMemory {
    private var last: Pair<Any, ShipResult>? = null

    /** The last result when it was written for [version], else null. */
    fun resultFor(version: Any): ShipResult? = last?.takeIf { it.first === version || it.first == version }?.second

    fun remember(
        version: Any,
        result: ShipResult,
    ) {
        last = version to result
    }

    companion object {
        /** The one the app shares. */
        val shared = ShipMemory()
    }
}

/**
 * Runs Ship: the block rule, then the build (the project's files, fontmake, the check on the compiled binary), then the write. It holds no
 * Compose and no platform code: the compiler, the checker and the writer come in as interfaces, so the same class builds on a computer, where
 * all three are real, and on a phone or in a browser, where there is no compiler and it writes the folder without a font.
 *
 * **Nothing is written until everything before it has passed.** [ship] refuses while Check has fails and never starts; the build reads a
 * snapshot of the project and writes only to the compiler's temporary directory; the check on the binary stops the ship when it finds a
 * fail; and the writer puts the whole folder in place at once. So cancelling at any point, a failure, or a block leaves the project and the
 * target as they were. [cancel] stops a build in progress.
 */
class ShipController(
    private val scope: CoroutineScope,
    private val compile: CompileBackend,
    private val layerOne: LayerOneChecker,
    private val memory: ShipMemory = ShipMemory.shared,
    private val clock: Clock = Clock.System,
    private val work: CoroutineDispatcher = Dispatchers.Default,
) {
    private val mutable = MutableStateFlow<ShipRunState>(ShipRunState.Idle)

    /** What the page draws. */
    val state: StateFlow<ShipRunState> = mutable.asStateFlow()

    private var job: Job? = null

    /**
     * Starts shipping [request]. Answers [ShipStart.Blocked] without starting while Check has fails (layer two's, and layer one's when
     * [ShipRequest.layerOne] holds them), and [ShipStart.CannotBuild] where there is no compiler and the target cannot take a folder without a
     * font. Otherwise the page follows [state].
     */
    suspend fun ship(request: ShipRequest): ShipStart {
        val gate = shipGate(request.report, request.layerOne)
        if (gate.blocked) return ShipStart.Blocked(gate)
        val compiler = compile.availability()
        if (compiler is BackendAvailability.Unavailable && !request.target.withoutFontOk) {
            return ShipStart.CannotBuild(compiler.reason)
        }
        job?.cancel()
        mutable.value =
            ShipRunState.Building(initialSteps(compiler is BackendAvailability.Available), TimeSource.Monotonic.markNow(), emptyList())
        job = scope.launch { run(request, compiler) }
        return ShipStart.Started
    }

    /** Stops a build in progress. The project and the target are as they were. */
    fun cancel() {
        if (mutable.value !is ShipRunState.Building) return
        job?.cancel()
        mutable.value = ShipRunState.Cancelled
    }

    /** Back to nothing started, from a finished or stopped state. */
    fun reset() {
        if (mutable.value !is ShipRunState.Building) mutable.value = ShipRunState.Idle
    }

    /** Returns when the run in hand has stopped and cleaned up after itself. A test waits on this instead of on the clock. */
    internal suspend fun settled() {
        job?.join()
    }

    private fun initialSteps(compiles: Boolean): List<ShipStepStatus> =
        ShipStep.entries.map { step ->
            val left = !compiles && (step == ShipStep.COMPILE || step == ShipStep.CHECK)
            ShipStepStatus(step, if (left) StepState.SKIPPED else StepState.WAITING)
        }

    private suspend fun run(
        request: ShipRequest,
        compiler: BackendAvailability,
    ) {
        val startedAt = TimeSource.Monotonic.markNow()
        val log = MutableStateFlow(emptyList<CompileLogLine>())
        val steps = initialSteps(compiler is BackendAvailability.Available).toMutableList()

        fun publish() {
            mutable.update { now ->
                if (now is ShipRunState.Building) now.copy(steps = steps.toList(), log = log.value.takeLast(LOG_KEPT)) else now
            }
        }

        fun set(
            step: ShipStep,
            state: StepState,
            millis: Long? = null,
            note: String? = null,
        ) {
            steps[step.ordinal] = ShipStepStatus(step, state, millis, note)
            publish()
        }

        fun fail(
            step: ShipStep,
            cause: String,
        ) {
            steps[step.ordinal] = ShipStepStatus(step, StepState.FAILED, note = cause)
            mutable.value = ShipRunState.Failed(step, cause, steps.toList(), log.value)
        }
        try {
            // 1. The project's files, as one consistent snapshot, so an autosave cannot race the build.
            set(ShipStep.UFO, StepState.RUNNING)
            val stepStart = TimeSource.Monotonic.markNow()
            val snapshot = withContext(work) { request.subject.check.snapshot() }
            val prefix = request.subject.masterPath.trim('/') + "/"
            val ufo = snapshot.listFiles().filter { it.startsWith(prefix) }.associate { it.removePrefix(prefix) to snapshot.readBytes(it) }
            if (ufo.isEmpty()) {
                fail(ShipStep.UFO, "The project has no UFO at “${request.subject.masterPath}”, so there is nothing to ship.")
                return
            }
            set(ShipStep.UFO, StepState.DONE, stepStart.elapsedNow().inWholeMilliseconds)

            // 2 and 3. The font, and Google Fonts' own profile run on it, when this device can compile.
            var binary: ByteArray? = null
            var compileMillis: Long? = null
            var checkLine: String? = null
            var checkSkipped: String? = null
            var layerOneReport: LayerOneReport? = null
            if (compiler is BackendAvailability.Available) {
                set(ShipStep.COMPILE, StepState.RUNNING)
                val compileStart = TimeSource.Monotonic.markNow()
                val result =
                    compile.compile(CompileRequest(snapshot, CompileSource.Ufo(request.subject.masterPath))) { line ->
                        log.update { it + line }
                        publish()
                    }
                when (result) {
                    is CompileResult.Failure -> {
                        log.update { result.log }
                        fail(ShipStep.COMPILE, result.reason)
                        return
                    }

                    is CompileResult.NotImplemented -> {
                        log.update { result.log }
                        fail(ShipStep.COMPILE, result.planned)
                        return
                    }

                    is CompileResult.Success -> {
                        val compiled = result.binaries.firstOrNull { it.format == OutputFormat.TTF } ?: result.binaries.firstOrNull()
                        if (compiled == null) {
                            fail(ShipStep.COMPILE, "The compiler finished but wrote no font file, so there is nothing to ship.")
                            return
                        }
                        log.update { result.log }
                        compileMillis = compileStart.elapsedNow().inWholeMilliseconds
                        set(ShipStep.COMPILE, StepState.DONE, compileMillis)
                        binary = compiled.bytes
                        val checkStart = TimeSource.Monotonic.markNow()
                        val availability = layerOne.availability()
                        if (availability is LayerOneAvailability.Unavailable) {
                            checkSkipped = availability.reason
                            set(ShipStep.CHECK, StepState.SKIPPED, note = availability.reason)
                        } else {
                            set(ShipStep.CHECK, StepState.RUNNING)
                            // Under the name the folder will give it: the profile judges a file by its name, and the UFO it came from
                            // may be called anything.
                            val shippedName = shipFileStem(request.subject.facts, request.subject.ufoFolderName) + ".ttf"
                            val checked = layerOne.check(LayerOneInput(shippedName, compiled.bytes))
                            if (checked.isUnavailable) {
                                checkSkipped = checked.results.single().message
                                set(ShipStep.CHECK, StepState.SKIPPED, note = checkSkipped)
                            } else {
                                layerOneReport = checked
                                val binaryGate = shipGate(null, checked)
                                checkLine = layerOneLine(checked)
                                if (binaryGate.blocked) {
                                    steps[ShipStep.CHECK.ordinal] =
                                        ShipStepStatus(
                                            ShipStep.CHECK,
                                            StepState.FAILED,
                                            checkStart.elapsedNow().inWholeMilliseconds,
                                            checkLine,
                                        )
                                    mutable.value = ShipRunState.Stopped(binaryGate.blockers, steps.toList(), log.value)
                                    return
                                }
                                set(ShipStep.CHECK, StepState.DONE, checkStart.elapsedNow().inWholeMilliseconds, checkLine)
                            }
                        }
                    }
                }
            }

            // 4. The folder, all at once.
            set(ShipStep.WRITE, StepState.RUNNING)
            val writeStart = TimeSource.Monotonic.markNow()
            val plan =
                withContext(work) {
                    buildShipPlan(
                        ShipInputs(
                            mode = request.mode,
                            facts = request.subject.facts,
                            font = request.subject.font,
                            brief = request.subject.brief,
                            ufoFolderName = request.subject.ufoFolderName,
                            ufo = ufo,
                            binary = binary,
                            category = request.category,
                            layerOne = layerOneReport?.let(::summaryOf),
                            today = clock.now().toString().take(DATE_LENGTH),
                        ),
                    )
                }
            val report =
                try {
                    request.target.writer.write(plan.files, plan.ownedDirectories)
                } catch (e: TreeWriteException) {
                    fail(ShipStep.WRITE, e.message ?: "The folder could not be written.")
                    return
                }
            set(ShipStep.WRITE, StepState.DONE, writeStart.elapsedNow().inWholeMilliseconds)
            val result =
                ShipResult(
                    target = request.target.label,
                    tree = plan.tree,
                    files = report.files,
                    bytes = report.bytes,
                    omissions = plan.omissions,
                    requestText = plan.requestText,
                    withFont = binary != null,
                    mode = request.mode,
                    compileMillis = compileMillis,
                    checkLine = checkLine,
                    checkSkipped = checkSkipped,
                    totalMillis = startedAt.elapsedNow().inWholeMilliseconds,
                    steps = steps.toList(),
                )
            memory.remember(request.subject.version, result)
            mutable.value = ShipRunState.Done(result)
        } catch (cancelled: CancellationException) {
            mutable.update { if (it is ShipRunState.Building) ShipRunState.Cancelled else it }
            throw cancelled
        } catch (failure: Throwable) {
            val running = steps.firstOrNull { it.state == StepState.RUNNING }?.step ?: ShipStep.UFO
            fail(running, "Ship stopped while ${running.activity}: ${failure.message ?: failure::class.simpleName}. Nothing was written.")
        }
    }

    private companion object {
        const val LOG_KEPT = 200
        const val DATE_LENGTH = 10
    }
}

/** `Fontbakery 0.12.10: 0 fail · 12 warn · 147 pass`: the tally of the check on the binary as one line. */
internal fun layerOneLine(report: LayerOneReport): String {
    val c = report.counts
    val tool = report.tool?.let { listOfNotNull(it.tool, it.version).joinToString(" ") }
    return (if (tool != null) "$tool: " else "") + "${c.fail} fail · ${c.warn} warn · ${c.pass} pass"
}

private fun summaryOf(report: LayerOneReport): LayerOneSummary {
    val tool = report.tool?.let { listOfNotNull(it.tool, it.version).joinToString(" ") } ?: "A layer-one tool"
    return LayerOneSummary(tool, report.counts.fail, report.counts.warn, report.counts.pass)
}
