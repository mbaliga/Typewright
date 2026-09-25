// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.learn.scenes

/**
 * Paths and loaders for the Lineages strand's real content: the ten on-stage era scenes
 * (`docs/LESSONS_SCAFFOLD.md` section 2's era table), the vocabulary aside placed "after era
 * 6", and the identify-it exercise bank — all checked in under
 * `learn/scenes/src/commonMain/resources/scenes/lineages/` and read back through
 * [readSceneResourceText] ([SceneResources.kt]'s KDoc explains the jvm/wasmJs split). Every
 * scene these paths name has `scaffold: true` (`Scene.scaffold`'s KDoc) — first-draft content
 * that ships marked SCAFFOLD in the UI until Madhav's material replaces or augments it.
 *
 * File numbering runs one higher than section 2's own era "#" column from era 7 (Art Deco)
 * onward, to make room for [VOCABULARY_SCENE_PATH] at position 7 — the table has no "#" of its
 * own for that aside. [loadFullBlockInPlayOrder] is what actually places it correctly (after
 * the sixth *era* scene, not by file name), so a caller never has to know about the offset.
 */
public object LineagesResources {
    private const val DIR = "scenes/lineages"

    /** The ten on-stage era scenes, in era order (section 2's own "#" column, 1 through 10). */
    public val ERA_SCENE_PATHS: List<String> =
        listOf(
            "$DIR/01-blackletter.yaml",
            "$DIR/02-garalde.yaml",
            "$DIR/03-transitional.yaml",
            "$DIR/04-didone.yaml",
            "$DIR/05-slab.yaml",
            "$DIR/06-grotesque.yaml",
            "$DIR/08-artdeco.yaml",
            "$DIR/09-geometric.yaml",
            "$DIR/10-humanist.yaml",
            "$DIR/11-neogrotesque.yaml",
        )

    /** How many of [ERA_SCENE_PATHS], from the front, play before the vocabulary aside. */
    public const val ERAS_BEFORE_VOCABULARY: Int = 6

    /** The "gothic" naming-ambiguity aside, section 2: "Vocabulary scene (after era 6)". */
    public const val VOCABULARY_SCENE_PATH: String = "$DIR/07-vocabulary.yaml"

    /** The identify-it exercise bank (section 2's eight-entry list), parsed by [parseExerciseBank]. */
    public const val IDENTIFY_IT_BANK_PATH: String = "$DIR/identify-it-bank.yaml"

    /** Every [ERA_SCENE_PATHS] entry, parsed, in era order. */
    public fun loadEraScenes(): List<Scene> = ERA_SCENE_PATHS.map { parseScene(readSceneResourceText(it)) }

    /** [VOCABULARY_SCENE_PATH], parsed. */
    public fun loadVocabularyScene(): Scene = parseScene(readSceneResourceText(VOCABULARY_SCENE_PATH))

    /** [IDENTIFY_IT_BANK_PATH], parsed. */
    public fun loadIdentifyItBank(): List<ExerciseBankEntry> = parseExerciseBank(readSceneResourceText(IDENTIFY_IT_BANK_PATH))

    /**
     * All eleven Lineages scenes in the order a learner actually plays them: the first
     * [ERAS_BEFORE_VOCABULARY] eras, then the vocabulary aside, then the remaining four eras —
     * the single ordered `List<Scene>` [StrandSequencer.plan] expects for "the whole block".
     */
    public fun loadFullBlockInPlayOrder(): List<Scene> {
        val eras = loadEraScenes()
        return eras.subList(0, ERAS_BEFORE_VOCABULARY) + loadVocabularyScene() + eras.subList(ERAS_BEFORE_VOCABULARY, eras.size)
    }
}
