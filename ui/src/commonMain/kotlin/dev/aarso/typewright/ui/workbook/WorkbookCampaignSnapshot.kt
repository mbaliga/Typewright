package dev.aarso.typewright.ui.workbook

import dev.aarso.typewright.campaign.DEFAULT_TASK4_STYLE_KEY
import dev.aarso.typewright.campaign.ShipMetadata
import dev.aarso.typewright.campaign.WorkbookLatinContent
import dev.aarso.typewright.campaign.WorkbookTask
import dev.aarso.typewright.campaign.WorkbookTaskProgress
import dev.aarso.typewright.campaign.campaignProgress
import dev.aarso.typewright.campaign.currentWorkbookTaskIndex
import dev.aarso.typewright.campaign.runAllWorkbookGates
import dev.aarso.typewright.qa.NodeEconomyReport
import dev.aarso.typewright.qa.checkNodeEconomy
import dev.aarso.typewright.qa.corpus.NodeEconomyCorpus

/**
 * This build's own real ship metadata for [hyleDecoReferenceProject] (`campaign`'s
 * [ShipMetadata]): the exact family, year and git URL task 12's own real workbook content already
 * quotes as its demonstration (`workbook-latin.yaml`, task 12's `demonstration.stats`:
 * `"Copyright 2026 The Hyle Deco Project Authors (https://github.com/mbaliga/hyle-deco)"`) --
 * reused, not re-guessed. [designer] has no equivalent real source anywhere in this codebase (the
 * explorer's own Ship screen never names one); "Madhav" is this build's own product owner, named
 * throughout `TYPEWRIGHT_BUILD_BRIEF.md` (section 16, "What Madhav owes the build") and
 * `docs/OPEN_QUESTIONS.md` as the one real person attached to this project -- a reasonable,
 * disclosed stand-in, not a measured fact (CLAUDE.md law 5 is about numbers judged against the
 * user; a placeholder designer name for a stand-in demo project is not that).
 */
private val HYLE_DECO_SHIP_METADATA =
    ShipMetadata(
        family = "Hyle Deco",
        designer = "Madhav",
        year = 2026,
        gitUrl = "https://github.com/mbaliga/hyle-deco",
    )

/**
 * The real campaign data [WorkbookScreen] renders: the twelve real [WorkbookTask]s
 * ([WorkbookLatinContent]), each one's real gate run for real against [hyleDecoReferenceProject]
 * ([campaignProgress]), and the real current/next task ([currentWorkbookTaskIndex]) -- or an
 * honest [Unavailable] when any of that fails to load on this target.
 *
 * **Why this can fail, and on which target.** Parsing [hyleDecoReferenceProject] is pure common
 * Kotlin and works everywhere `ui` runs. [NodeEconomyCorpus.load] does not: its `wasmJs` actual
 * reads its JSON pack with Node's own `fs` module resolved from `import.meta.url`
 * (`qa/corpus`'s `CorpusResources.wasmJs.kt`), which only exists under Node -- `ui`'s own `wasmJs`
 * target is a real **browser** (`ui/build.gradle.kts`'s own comment: "Skiko's Wasm runtime does
 * load in a real browser"), where `process` is undefined and that call throws. This is the same
 * gap `docs/OPEN_QUESTIONS.md` item 18 already discloses ("the JVM/Node half is solved, the
 * browser half is not... a future `app-web`... needs its own strategy") -- `ui` is now that
 * "future `app-web`" caller, so this function [runCatching]s the whole load and reports it
 * honestly instead of crashing, the same "not available on this target" convention
 * [dev.aarso.typewright.ui.learn.loadDefaultOverlayLayers] already established for a different,
 * sibling gap on the exact same target.
 */
sealed interface WorkbookCampaignSnapshot {
    /**
     * The real, loaded campaign data. [task4Report] is [checkNodeEconomy] run once, the same
     * real call [dev.aarso.typewright.campaign.WorkbookGates.task4ControlCharacters] itself makes
     * internally for task 4's own [WorkbookTaskProgress.gate] -- kept here too, separately, only
     * because it carries the raw per-glyph on-curve count and [dev.aarso.typewright.qa.corpus.
     * Quartiles] box that [WorkbookGateRow]'s own compact `#s-workbook`-style display needs and a
     * [dev.aarso.typewright.campaign.GateCheckResult]'s own prose [dev.aarso.typewright.campaign.
     * GateCheckResult.detail] does not expose as separate fields.
     */
    data class Loaded(
        val tasks: List<WorkbookTask>,
        val progress: List<WorkbookTaskProgress>,
        val currentTaskIndex: Int,
        val task4StyleKey: String,
        val task4Report: NodeEconomyReport,
    ) : WorkbookCampaignSnapshot

    /** The real, honest reason loading failed on this target -- never a fabricated task list. */
    data class Unavailable(
        val reason: String,
    ) : WorkbookCampaignSnapshot
}

/** Loads [WorkbookCampaignSnapshot] for real -- see that type's own KDoc for what can fail, and why. Not `@Composable`; a caller wraps it in `remember` so the real font parse and corpus load run once per composition, not on every recomposition. */
fun loadWorkbookCampaignSnapshot(): WorkbookCampaignSnapshot =
    runCatching {
        val tasks = WorkbookLatinContent.load()
        val project = hyleDecoReferenceProject()
        val corpus = NodeEconomyCorpus.load()
        val gates = runAllWorkbookGates(tasks, project, corpus, DEFAULT_TASK4_STYLE_KEY, HYLE_DECO_SHIP_METADATA)
        val progress = campaignProgress(tasks, gates)
        WorkbookCampaignSnapshot.Loaded(
            tasks = tasks,
            progress = progress,
            currentTaskIndex = currentWorkbookTaskIndex(progress),
            task4StyleKey = DEFAULT_TASK4_STYLE_KEY,
            task4Report = checkNodeEconomy(project, DEFAULT_TASK4_STYLE_KEY, corpus),
        )
    }.getOrElse { error ->
        WorkbookCampaignSnapshot.Unavailable(error.message ?: error::class.simpleName ?: "unknown error loading the campaign data")
    }

/**
 * `#s-home`'s own `.taskcard` copy shape (`ui/typewright-explorer.html` line 607: `"Task 4 ·
 * Control characters"`), for whichever task [WorkbookCampaignSnapshot.Loaded.currentTaskIndex]
 * reports -- real data, not the mockup's own hardcoded "Task 4". Falls back to the plain word
 * "Workbook" when [snapshot] is [WorkbookCampaignSnapshot.Unavailable]: there is no real next-task
 * fact left to show, so this says the honest, generic thing rather than inventing one.
 */
fun nextTaskLabel(snapshot: WorkbookCampaignSnapshot): String =
    when (snapshot) {
        is WorkbookCampaignSnapshot.Loaded -> {
            val task = snapshot.tasks.first { it.index == snapshot.currentTaskIndex }
            "Task ${task.index} · ${task.title}"
        }

        is WorkbookCampaignSnapshot.Unavailable -> {
            "Workbook"
        }
    }
