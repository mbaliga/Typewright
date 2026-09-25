// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.campaign

import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.core.font.ufo.UfoProject
import com.asoc.typewright.qa.corpus.NodeEconomyCorpus

/**
 * The style class [runWorkbookGate] compares task 4's control characters against when
 * [WorkbookTask.gate] names no other -- `sans-geometric`, the same box
 * `HyleDecoTask4GateTest.kt`/`docs/OPEN_QUESTIONS.md` item 18 already use for this project's own
 * reference font. [WorkbookGateSpec] itself carries no style key (task 4's own YAML gate entry
 * has none -- `WorkbookTask.kt`'s own KDoc on [WorkbookGateSpec.glyphs]), so *some* caller has to
 * decide one; item 18's own closing line says this is "campaign['s]... call," not a decided
 * product fact, so this constant is that call, made once, here, rather than re-guessed at every
 * call site.
 */
const val DEFAULT_TASK4_STYLE_KEY: String = "sans-geometric"

/**
 * What task 12's ship-text gate ([WorkbookGates.task12Ship]) needs beyond a project's own
 * [UfoFontInfo]: the four extra strings/numbers real Google Fonts submission text always carries
 * (family, designer, year, git URL) but [UfoFontInfo] itself has no fields for. [from] defaults
 * every field to the most honest guess available from [UfoFontInfo] alone -- [UfoFontInfo.
 * familyName] for [family] when present, and placeholder values that read as placeholders rather
 * than invented facts (CLAUDE.md law 5) for the rest -- so [runWorkbookGate] never has to invent a
 * designer's name or a repository URL for a project it knows nothing about; a caller that has
 * real values (this build's own Hyle Deco stand-in, in `ui`) passes its own [ShipMetadata]
 * instead.
 */
data class ShipMetadata(
    val family: String,
    val designer: String,
    val year: Int,
    val gitUrl: String,
) {
    companion object {
        /** The placeholder [ShipMetadata] used when a caller has no real ship metadata for [fontInfo]'s own project. */
        fun from(fontInfo: UfoFontInfo): ShipMetadata =
            ShipMetadata(
                family = fontInfo.familyName ?: "Untitled",
                designer = "Unknown",
                year = UNKNOWN_YEAR,
                gitUrl = "(no repository yet)",
            )

        /** Not a real year -- deliberately not "this build's current year" either, since `campaign` has no clock dependency and inventing one would be a fabricated fact (CLAUDE.md law 5), not a placeholder. */
        const val UNKNOWN_YEAR: Int = 0
    }
}

/**
 * Runs [task]'s own real gate against [project] (this task's own instruction: "gates call qa and
 * the boxes"), dispatching to the already-real [WorkbookGates] function [WorkbookTask.gate] names
 * -- or [WorkbookGates.notImplementedGate] when [WorkbookGateSpec.isImplemented] is false, using
 * that spec's own [WorkbookGateSpec.notImplementedReason] rather than inventing a different one.
 *
 * **Task 11 is a special case, deliberately not calling [WorkbookGates.task11Test] for real.**
 * That function needs a real compiled font ([com.asoc.typewright.qa.LayerOneInput]) to check
 * anything beyond its own checker's own availability, and this dispatcher -- like every other
 * caller in this codebase so far -- has no compile pipeline to reach from a [UfoProject] alone
 * (`docs/OPEN_QUESTIONS.md` item 86, the still-missing `core-font` SFNT-to-[UfoProject] bridge, is
 * the same-shaped gap the other direction). Calling the real, `suspend`,
 * [com.asoc.typewright.qa.LayerOneChecker]-needing function here would only ever return the
 * identical "no compiled font to check yet" [WorkbookGateResult] [WorkbookGates.task11Test] itself
 * already gives for a `null` compiled input, on every platform, so this function returns that
 * exact honest result directly and synchronously instead -- never a fabricated pass, and never a
 * different message than the real function would give.
 */
fun runWorkbookGate(
    task: WorkbookTask,
    project: UfoProject,
    corpus: NodeEconomyCorpus,
    task4StyleKey: String = DEFAULT_TASK4_STYLE_KEY,
    shipMetadata: ShipMetadata = ShipMetadata.from(project.fontInfo),
): WorkbookGateResult {
    val spec = task.gate
    if (!spec.isImplemented) {
        return WorkbookGates.notImplementedGate(task.index, spec.summary, spec.notImplementedReason.orEmpty())
    }
    return when (task.index) {
        3 -> {
            WorkbookGates.task3SetMetrics(project, glyphNamesOrDefault(spec, WorkbookGates.TASK3_DEFAULT_GLYPHS))
        }

        4 -> {
            WorkbookGates.task4ControlCharacters(
                project,
                corpus,
                task4StyleKey,
                glyphNamesOrDefault(spec, WorkbookGates.TASK4_DEFAULT_GLYPHS),
            )
        }

        5 -> {
            WorkbookGates.task5DeriveTheFamily(project, glyphNamesOrDefault(spec, WorkbookGates.TASK5_DEFAULT_GLYPHS))
        }

        6 -> {
            WorkbookGates.task6HardLetters(project, glyphNamesOrDefault(spec, WorkbookGates.TASK6_DEFAULT_GLYPHS))
        }

        9 -> {
            WorkbookGates.task9DiacriticsAndAnchors(project)
        }

        11 -> {
            WorkbookGates.notImplementedGate(
                11,
                spec.summary,
                "no compiled font to check yet -- compile the project first.",
            )
        }

        12 -> {
            WorkbookGates.task12Ship(
                project.fontInfo,
                family = shipMetadata.family,
                designer = shipMetadata.designer,
                year = shipMetadata.year,
                gitUrl = shipMetadata.gitUrl,
            )
        }

        else -> {
            WorkbookGates.notImplementedGate(task.index, spec.summary, "no gate runner is wired for task ${task.index} yet.")
        }
    }
}

private fun glyphNamesOrDefault(
    spec: WorkbookGateSpec,
    default: List<String>,
): List<String> = spec.glyphs.ifEmpty { default }

/** [runWorkbookGate] for every one of [tasks], keyed by [WorkbookTask.index]. */
fun runAllWorkbookGates(
    tasks: List<WorkbookTask>,
    project: UfoProject,
    corpus: NodeEconomyCorpus,
    task4StyleKey: String = DEFAULT_TASK4_STYLE_KEY,
    shipMetadata: ShipMetadata = ShipMetadata.from(project.fontInfo),
): Map<Int, WorkbookGateResult> = tasks.associate { it.index to runWorkbookGate(it, project, corpus, task4StyleKey, shipMetadata) }

/**
 * Where one workbook task's own gate currently stands (`ui/typewright-explorer.html`'s own
 * `#s-workbook` `.prog` strip: "3 done, 1 current, 8 todo in the mockup" -- but computed for real
 * here, never that hardcoded pattern, per this task's own instruction).
 */
enum class WorkbookTaskState { DONE, CURRENT, TODO }

/** One task, its real gate result, and the real [WorkbookTaskState] [campaignProgress] computed for it. */
data class WorkbookTaskProgress(
    val task: WorkbookTask,
    val gate: WorkbookGateResult,
    val state: WorkbookTaskState,
)

/**
 * True when [WorkbookGateResult] genuinely, fully passes: at least one real check, and every one
 * of them [GateCheckStatus.PASS] -- never [GateCheckStatus.WARN], [GateCheckStatus.FAIL],
 * [GateCheckStatus.INFO] (task 3's overshoot-presence gate is *always* this shape -- it is
 * informational by its own documented contract, so it can never itself read [DONE]) or
 * [GateCheckStatus.NOT_IMPLEMENTED] (a judgment-call task, 1/2/7/8/10, and task 11 without a
 * compiled font, are always this shape).
 */
fun WorkbookGateResult.isFullyPassing(): Boolean = checks.isNotEmpty() && checks.all { it.status == GateCheckStatus.PASS }

/**
 * The twelve tasks' own real completion state (this task's own instruction: "base this on real
 * gate-pass results where the campaign module can compute them, an honest 'todo' everywhere it
 * can't"), computed purely from [gates] (each task's own already-run [WorkbookGateResult], e.g.
 * from [runAllWorkbookGates]) -- this function itself touches no project, corpus or font, so it is
 * trivially testable against hand-built [WorkbookGateResult] fixtures.
 *
 * Rule, in [WorkbookTask.index] order: a task whose gate [isFullyPassing] is [WorkbookTaskState.DONE];
 * the *first* task that is not [WorkbookTaskState.DONE] is [WorkbookTaskState.CURRENT]; every task
 * after that is [WorkbookTaskState.TODO] regardless of its own gate (a later task's gate happening
 * to pass on its own, e.g. task 12's pure string generation almost always does, does not let it
 * jump ahead of an earlier, still-open task -- the workbook is twelve tasks *in order*, brief
 * section 9).
 *
 * **A real, honest finding, not a bug in this rule:** a task with no implemented gate at all
 * (1, 2, 7, 8, 10) can *never* read [WorkbookTaskState.DONE] by this logic -- there is no
 * automated signal this function, or anything in `campaign` today, could use to tell "the learner
 * judged this reference font and understood why" apart from "they have not started." On this
 * build's own real Hyle Deco reference project ([gates] built from it), that means task 1 itself
 * is always [WorkbookTaskState.CURRENT] -- not a hardcoded default (this task's own instruction
 * explicitly warns against hardcoding "task 4," the explorer mockup's own worked example), but the
 * real, computed answer this data model gives today. The campaign module has no persisted "the
 * learner manually marked this judgment-call task done" flag anywhere in [WorkbookTask]/
 * [WorkbookGateSpec] -- a real, disclosed gap for whoever next gives the workbook a real
 * completion state to write to (`docs/OPEN_QUESTIONS.md`).
 */
fun campaignProgress(
    tasks: List<WorkbookTask>,
    gates: Map<Int, WorkbookGateResult>,
): List<WorkbookTaskProgress> {
    val ordered = tasks.sortedBy { it.index }
    // Once the first non-DONE task is found, every task after it is TODO regardless of its own
    // gate -- checked *before* isFullyPassing() for exactly that reason: a later task's gate
    // passing on its own (task 12's pure string generation almost always does) must never let it
    // read DONE while an earlier task is still open, or "twelve tasks in order" stops being true.
    var blocked = false
    return ordered.map { task ->
        val gate =
            gates[task.index]
                ?: WorkbookGates.notImplementedGate(task.index, task.gate.summary, "no gate result was computed for this task.")
        val state =
            when {
                blocked -> {
                    WorkbookTaskState.TODO
                }

                gate.isFullyPassing() -> {
                    WorkbookTaskState.DONE
                }

                else -> {
                    blocked = true
                    WorkbookTaskState.CURRENT
                }
            }
        WorkbookTaskProgress(task, gate, state)
    }
}

/**
 * The task [campaignProgress] reports as current/next -- [WorkbookTaskState.CURRENT]'s own
 * [WorkbookTask.index], or, in the edge case every task reads [WorkbookTaskState.DONE] (this
 * data model has no "the whole workbook is complete" state of its own), the last task in
 * [progress] -- honest ("nothing left to point at but the end") rather than falling back to a
 * hardcoded task 1.
 */
fun currentWorkbookTaskIndex(progress: List<WorkbookTaskProgress>): Int =
    progress.firstOrNull { it.state == WorkbookTaskState.CURRENT }?.task?.index
        ?: progress.lastOrNull()?.task?.index
        ?: 1
