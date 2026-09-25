// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.learn.scenes

/**
 * Paths and loaders for the Craft strand's real content: the four before/after scenes P6's own
 * content-authoring prompt names explicitly (`docs/LESSONS_SCAFFOLD.md` section 4's table, rows
 * A1/B1/B2/C1) — all checked in under
 * `learn/scenes/src/commonMain/resources/scenes/craft/` and read back through
 * [readSceneResourceText] ([SceneResources.kt]'s KDoc explains the jvm/wasmJs split, and this
 * follows [LineagesResources]'s own established shape rather than inventing a second one). Every
 * scene these paths name has `scaffold: true` (`Scene.scaffold`'s KDoc) — first-draft content
 * that ships marked SCAFFOLD in the UI until real (Madhav's Domestika) material replaces or
 * augments it, and every one of them also uses this task's own [FaceSource]/[Stage.pipeline]
 * extension (`Scene.kt`'s KDoc) rather than CANON's plain two-typeface crossfade, since a Craft
 * scene's `from`/`to` are this project's own glyph before/after one of its own construction-
 * pipeline stages, not two independently-drawn fonts.
 *
 * Unlike [LineagesResources], Craft has no separate strand-level exercise bank
 * (`docs/LESSONS_SCAFFOLD.md` section 4 describes no "identify-it" mechanic for Craft at all —
 * that is specific to Lineages' "name the era" exercise, section 1's own CANON text) and none of
 * these four scenes carries an inline [Scene.exercise] either, so there is no
 * [parseExerciseBank]/[StrandSequencer.plan] wiring here to mirror.
 */
public object CraftResources {
    private const val DIR = "scenes/craft"

    /** Row A1: "Tracing destroys the drawing" — the shipped 1,763-point T versus the 8-on-curve fit. */
    public const val A1_TRACING_DESTROYS_THE_DRAWING_PATH: String = "$DIR/a1-tracing-destroys-the-drawing.yaml"

    /** Row B1: "Inflating a bold" — the naive offset's counter collapse versus stem growth. */
    public const val B1_INFLATING_A_BOLD_PATH: String = "$DIR/b1-inflating-a-bold.yaml"

    /** Row B2: "Shearing an italic" — naive outline shear versus centreline-shear-then-restroke. */
    public const val B2_SHEARING_AN_ITALIC_PATH: String = "$DIR/b2-shearing-an-italic.yaml"

    /** Row C1: "Baked composites" — bounding-box placement versus a named, round-tripped anchor. */
    public const val C1_BAKED_COMPOSITES_PATH: String = "$DIR/c1-baked-composites.yaml"

    /** All four paths above, in the scaffold table's own row order (A1, B1, B2, C1). */
    public val SCENE_PATHS: List<String> =
        listOf(
            A1_TRACING_DESTROYS_THE_DRAWING_PATH,
            B1_INFLATING_A_BOLD_PATH,
            B2_SHEARING_AN_ITALIC_PATH,
            C1_BAKED_COMPOSITES_PATH,
        )

    /** [SCENE_PATHS], parsed, in the same order. */
    public fun loadScenes(): List<Scene> = SCENE_PATHS.map { parseScene(readSceneResourceText(it)) }
}
