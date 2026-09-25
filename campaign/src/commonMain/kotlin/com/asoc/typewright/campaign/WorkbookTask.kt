// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.campaign

/**
 * One task in the twelve-task Latin workbook (docs/TYPEWRIGHT_HANDOFF.md "M5. Campaign, the
 * workbook", `[SCAFFOLD, awaiting the Domestika lessons]`; TYPEWRIGHT_BUILD_BRIEF.md §4.3, §9).
 * M5's own words: "Each task has: a why, a demonstration on a sample font, the task itself, the
 * gate, and a reflection prompt that saves to the scrapbook." -- this type's five non-identifying
 * fields ([why], [demonstration], [task], [gate], [reflection]) are exactly those five, in that
 * order.
 *
 * The whole workbook ships `[SCAFFOLD, awaiting the Domestika lessons]` per M5's own section
 * header, "until the designer's lessons replace it" (this module's own README). [scaffold]
 * represents that honestly per task rather than dropping it -- every task [WorkbookLatinContent]
 * parses today has `scaffold: true`, and stays that way until Madhav's material replaces or
 * augments it (docs/TYPEWRIGHT_HANDOFF.md "What Madhav owes the build").
 *
 * **Demonstration shape, decided per task, not forced (this task's own instruction: "do not force
 * every task through Scene machinery it doesn't fit").** Two tasks reuse a real, already-authored
 * `learn:scenes` [com.asoc.typewright.learn.scenes.Scene] because their own content genuinely is
 * a live before/after geometry scene:
 * - task 1 (choose a reference, understand why) -> `lineages.transitional`, the Lineages strand's
 *   own worked mechanism for "what makes a face itself" (a scrubbed crossfade between two real OFL
 *   faces, the stress dial turning, the caption naming the tool that made the shape);
 * - task 9 (diacritics and anchors) -> `craft.c1-baked-composites`, an exact content match:
 *   bounding-box placement versus a named, round-tripped anchor, the same mechanism task 9's own
 *   instructions teach.
 *
 * Every other task's demonstration is real measured numbers on a sample word/font instead (the
 * explorer's own shape for task 4's demo block, `#s-workbook`'s `.demo`
 * `<span class="dl">noHO</span><span class="dc">stem 44 ...`) -- either this project's own real
 * Hyle Deco measurements already established elsewhere in this codebase (the explorer's own
 * content; TYPEWRIGHT_BUILD_BRIEF.md §7's fixture table), or real published numbers quoted from
 * docs/RESEARCH_font_quality.md when no Hyle Deco measurement exists for that step (task 6's
 * cross-font on-curve ranges; task 7/8's spacing/kerning numbers; task 10's script metric facts).
 * No task's demonstration numbers are invented (CLAUDE.md law 5).
 *
 * See [WorkbookLatinContent] for the real YAML content parsed into these, and [WorkbookGates] for
 * the real Kotlin functions that run a task's [gate] against a real
 * [com.asoc.typewright.core.font.ufo.UfoProject]/font input.
 *
 * @property index the task's fixed position, 1-12 (M5's own numbered list has no other name for a
 *   task than its number).
 * @property title the task's short name, e.g. "Control characters" (`#s-workbook`'s own `<h2>`
 *   text for task 4).
 * @property scaffold see this type's own KDoc.
 * @property why why this step matters -- real prose from M5's own numbered list and
 *   docs/RESEARCH_font_quality.md's synthesis.
 * @property demonstration a demonstration on a sample font/word -- see this type's own KDoc.
 * @property task the task instructions themselves; may quote a real control string
 *   ([controlString]).
 * @property controlString a control string this task's own instructions name, e.g. `"nnonnonon"`
 *   (M5 item 7; `#s-workbook`'s own Task prose for task 4); null when a task names none, or names
 *   more than one and states them in [task]'s own prose instead (task 7 also names `"HHOHOHOH"`
 *   and Paul Barnes'/Hannes Famira's fuller permutations there).
 * @property gate this task's gate as its own instructions name it -- see [WorkbookGateSpec]'s own
 *   KDoc. Running one for real against a project is [WorkbookGates]' job, not this type's.
 * @property reflection the reflection prompt, which "saves to the scrapbook" (M5's own words;
 *   `#s-workbook`'s own "Reflection - saves to the scrapbook" heading).
 */
public data class WorkbookTask(
    val index: Int,
    val title: String,
    val scaffold: Boolean,
    val why: String,
    val demonstration: Demonstration,
    val task: String,
    val controlString: String?,
    val gate: WorkbookGateSpec,
    val reflection: String,
) {
    init {
        require(index in 1..12) { "WorkbookTask.index must be 1..12, found $index" }
        require(title.isNotBlank()) { "task $index: title must not be blank" }
        require(why.isNotBlank()) { "task $index: why must not be blank" }
        require(task.isNotBlank()) { "task $index: task must not be blank" }
        require(reflection.isNotBlank()) { "task $index: reflection must not be blank" }
    }
}

/**
 * A task's demonstration on a sample font/word (M5's own phrase). See [WorkbookTask]'s own KDoc
 * for which of the twelve tasks took which shape, and why.
 */
public sealed interface Demonstration {
    /**
     * Reuses a real, already-authored `learn:scenes`
     * [com.asoc.typewright.learn.scenes.Scene] by its stable [sceneId] (e.g.
     * `"craft.c1-baked-composites"`, `"lineages.transitional"` -- both real ids
     * [com.asoc.typewright.learn.scenes.CraftResources]/[com.asoc.typewright.learn.scenes.LineagesResources]
     * already load).
     */
    public data class SceneDemonstration(
        val sceneId: String,
    ) : Demonstration {
        init {
            require(sceneId.isNotBlank()) { "SceneDemonstration.sceneId must not be blank" }
        }
    }

    /**
     * Real measured stats on a sample word/font: a short [label] (`#s-workbook`'s own
     * `<span class="dl">noHO</span>`) plus the stat lines themselves (`<span class="dc">stem 44
     * -- counter 292 ...`), one per [stats] entry.
     */
    public data class StatDemonstration(
        val label: String,
        val stats: List<String>,
    ) : Demonstration {
        init {
            require(label.isNotBlank()) { "StatDemonstration.label must not be blank" }
            require(stats.isNotEmpty()) { "StatDemonstration '$label': stats must not be empty" }
        }
    }
}

/**
 * A workbook task's gate as its own instructions name it: a static declaration parsed from this
 * task's own YAML content ([WorkbookLatinContent]), never a computed result -- see [WorkbookGates]
 * for the real Kotlin functions that run one of these against a real project and return a real,
 * structured, pass/fail/not-implemented outcome ([WorkbookGateResult]).
 *
 * Exactly one of [qaFunction] or [notImplementedReason] is set: either a real, already-implemented
 * `qa` function backs this gate ([qaFunction] names it), or it honestly does not yet
 * ([notImplementedReason] says why, following [com.asoc.typewright.qa.LayerOneChecker]'s own
 * `Unavailable`-stub wording precedent) -- this task's own instruction: "that task's own gate data
 * must say so honestly ... never a fabricated pass/fail".
 *
 * @property summary the gate's short description, e.g. `"node economy against the geometric box"`
 *   (`#s-workbook`'s own `<h5>Gate - node economy against the geometric box</h5>` for task 4).
 * @property qaFunction the real `qa` function(s) this gate is wired to, by qualified name, or null
 *   when [notImplementedReason] is set instead.
 * @property glyphs the glyph names this gate checks, when it takes a caller-supplied glyph list
 *   (task 3/4/5/6's gates do; task 9's own allow-list is internal to
 *   [com.asoc.typewright.qa.checkAnchorsPresent], and tasks 11/12 check a whole compiled
 *   font/repo scaffold rather than named glyphs) -- empty when not applicable.
 * @property notImplementedReason why this gate has no real `qa` function yet, or null when
 *   [qaFunction] is set instead.
 */
public data class WorkbookGateSpec(
    val summary: String,
    val qaFunction: String?,
    val glyphs: List<String> = emptyList(),
    val notImplementedReason: String? = null,
) {
    init {
        require(summary.isNotBlank()) { "WorkbookGateSpec.summary must not be blank" }
        require((qaFunction == null) != (notImplementedReason == null)) {
            "WorkbookGateSpec '$summary' must set exactly one of qaFunction or notImplementedReason " +
                "(qaFunction=$qaFunction, notImplementedReason=$notImplementedReason)"
        }
    }

    /** True when a real `qa` function backs this gate ([qaFunction] non-null). */
    val isImplemented: Boolean get() = qaFunction != null
}
