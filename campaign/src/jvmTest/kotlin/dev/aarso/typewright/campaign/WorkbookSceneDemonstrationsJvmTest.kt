// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.campaign

import dev.aarso.typewright.learn.scenes.CraftResources
import dev.aarso.typewright.learn.scenes.LineagesResources
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cross-checks task 1's and task 9's [Demonstration.SceneDemonstration] `sceneId`s against
 * `learn:scenes`' own real, loadable scenes -- `jvmTest` only, not `commonTest`: `learn:scenes`'
 * `readSceneResourceText` wasmJs actual reads Node `fs` relative to *its own* compiled module's
 * `import.meta.url`; called from a different module's own compiled `wasmJs` test bundle (this
 * module's), it throws for an environment/bundling reason that has nothing to do with the
 * content under test (verified empirically: `:campaign:wasmJsNodeTest` fails exactly these two
 * assertions with a `node:fs` `JsException`, while every other assertion in this module passes on
 * both targets). `ui`'s own `LineagesQuizItemsTest` documents and works around the identical
 * situation the identical way (`docs/OPEN_QUESTIONS.md` item 47) -- `WorkbookLatinContentTest`
 * (`commonTest`) checks the exact `sceneId` string these scenes are expected to carry; this class
 * is what proves those strings are real, loadable scenes, not just plausible-looking text.
 */
class WorkbookSceneDemonstrationsJvmTest {
    private val tasks = WorkbookLatinContent.load()

    @Test
    fun task1sSceneDemonstrationReferencesARealLineagesSceneThatActuallyLoads() {
        val task1 = tasks.single { it.index == 1 }
        val demo = task1.demonstration as Demonstration.SceneDemonstration
        val realSceneIds = LineagesResources.loadEraScenes().map { it.id }
        assertTrue(
            demo.sceneId in realSceneIds,
            "task 1's demonstration sceneId '${demo.sceneId}' is not a real, loadable Lineages scene id",
        )
    }

    @Test
    fun task9sSceneDemonstrationReferencesTheRealBakedCompositesCraftScene() {
        val task9 = tasks.single { it.index == 9 }
        val demo = task9.demonstration as Demonstration.SceneDemonstration
        val realSceneIds = CraftResources.loadScenes().map { it.id }
        assertTrue(demo.sceneId in realSceneIds, "task 9's demonstration sceneId '${demo.sceneId}' is not a real, loadable Craft scene id")
        assertEquals("craft.c1-baked-composites", demo.sceneId)
    }
}
