// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.learn.scenes

/**
 * One candidate identify-it question from a strand's exercise bank — a pool of faces kept
 * separate from any scene's own inline [Exercise], because `docs/LESSONS_SCAFFOLD.md` section 2
 * describes exactly that shape for Lineages: eight bank faces "not on stage", distinct from the
 * ten on-stage era scenes, each of which may *also* carry its own inline [Exercise]. [face] is a
 * family name, matched against [Scene.fromFamily]/[Scene.toFamily] the same way an inline
 * [Exercise.face] is.
 */
public data class ExerciseBankEntry(
    val face: String,
    val answer: String,
    val giveaway: String,
)

/**
 * The result of [StrandSequencer.plan] for one ordered block of scenes: which of the block's own
 * inline scene exercises, and which of an optional [ExerciseBankEntry] pool, are actually safe to
 * present, plus the ones filtered out and why.
 */
public data class StrandExercisePlan(
    val eligibleSceneExercises: List<Scene>,
    val excludedSceneExercises: List<ExclusionReason<Scene>>,
    val eligibleBankEntries: List<ExerciseBankEntry>,
    val excludedBankEntries: List<ExclusionReason<ExerciseBankEntry>>,
) {
    /** True once the block has at least one exercise it can actually show, from either source. */
    public val hasAnyEligibleExercise: Boolean get() = eligibleSceneExercises.isNotEmpty() || eligibleBankEntries.isNotEmpty()
}

/** Why one candidate ([item]) was filtered out of a [StrandExercisePlan]. */
public data class ExclusionReason<T>(
    val item: T,
    val collidingFace: String,
)

/**
 * Strand sequencing and exercise gating (`docs/LESSONS_SCAFFOLD.md` section 1's "Renderer rules"
 * paragraph, CANON): "the exercise appears after the last scene of a strand block and never shows
 * a face used on stage in that block." General logic — it works on any ordered [List] of [Scene]s
 * for any [Strand], any length, not a hardcoded ten-scene Lineages pipeline.
 *
 * **What "that block" is.** The CANON schema has no separate "block" field on [Scene]; a block is
 * simply whatever ordered list of scenes a caller passes to [plan] — normally every [Scene] for
 * one strand, but a strand with an internal chapter break (`docs/LESSONS_SCAFFOLD.md` section 2's
 * "Vocabulary scene, after era 6") can also call [plan] once per chapter by passing only that
 * chapter's scenes.
 *
 * **"Used on stage in that block" is read as the whole block, not "before this scene".** The task
 * brief that requested this file paraphrased the rule as "earlier in that same strand block"; the
 * CANON text quoted above has no "earlier" in it — it says only "used on stage in that block". The
 * two readings matter here because the scaffold's own worked example (`docs/LESSONS_SCAFFOLD.md`
 * section 1, the `lineages.transitional` scene) actually exercises the difference: that scene's own
 * `stage.to` face is `transitional` (family "Libre Baskerville"), and that same scene's own inline
 * `exercise.face` is *also* "Libre Baskerville" — literally the face the learner just watched
 * crossfade onto stage a moment before being asked to identify it. Under an "earlier than this
 * scene" reading that collision would pass (nothing *before* era 3 put Libre Baskerville on stage);
 * under the CANON "used on stage in that block" reading — the one implemented here — it correctly
 * fails, because exercises for a block only ever surface *after* the block's last scene has played,
 * by which point every scene's stage in the block, including this one's own, is already "used".
 * That also matches section 2's own framing of its bank as "faces not on stage" at all, and it
 * exposes a real content bug worth flagging to whoever authors the actual Lineages YAML: section
 * 2's identify-it bank lists "Libre Baskerville → Transitional" as its very first entry, despite
 * Libre Baskerville being one of the ten on-stage faces (section 2's own era table, row 3). [plan]
 * filters that collision out by construction rather than trusting scene content to avoid it, which
 * is exactly the point of building this as real logic instead of hand-checking each YAML file.
 */
public object StrandSequencer {
    /**
     * Plans one strand block's exercises: [eligibleSceneExercises] are the scenes in [scenes]
     * whose own [Scene.exercise] names a face not used on stage anywhere in [scenes];
     * [eligibleBankEntries] are the [bank] entries (default empty — not every strand has a
     * separate bank) whose [ExerciseBankEntry.face] clears the same check. A scene with no
     * [Scene.exercise] is simply absent from both the eligible and excluded scene lists.
     */
    public fun plan(
        scenes: List<Scene>,
        bank: List<ExerciseBankEntry> = emptyList(),
    ): StrandExercisePlan {
        val onStageFaces = onStageFamilies(scenes)

        val eligibleScenes = mutableListOf<Scene>()
        val excludedScenes = mutableListOf<ExclusionReason<Scene>>()
        for (scene in scenes) {
            val exercise = scene.exercise ?: continue
            if (exercise.face in onStageFaces) {
                excludedScenes.add(ExclusionReason(scene, exercise.face))
            } else {
                eligibleScenes.add(scene)
            }
        }

        val eligibleBank = mutableListOf<ExerciseBankEntry>()
        val excludedBank = mutableListOf<ExclusionReason<ExerciseBankEntry>>()
        for (entry in bank) {
            if (entry.face in onStageFaces) {
                excludedBank.add(ExclusionReason(entry, entry.face))
            } else {
                eligibleBank.add(entry)
            }
        }

        return StrandExercisePlan(
            eligibleSceneExercises = eligibleScenes,
            excludedSceneExercises = excludedScenes,
            eligibleBankEntries = eligibleBank,
            excludedBankEntries = excludedBank,
        )
    }

    /**
     * Every face family used as [Stage.from] or [Stage.to] by any scene in [scenes], resolved
     * through each scene's own [Scene.faces]. A `from`/`to` key with no matching [FaceRef] is
     * silently skipped (defensive: a malformed scene should not crash exercise planning for the
     * rest of the block; [parseScene] is where that malformed key is actually caught and reported).
     */
    public fun onStageFamilies(scenes: List<Scene>): Set<String> {
        val families = mutableSetOf<String>()
        for (scene in scenes) {
            scene.fromFamily?.let { families.add(it) }
            scene.toFamily?.let { families.add(it) }
        }
        return families
    }
}
