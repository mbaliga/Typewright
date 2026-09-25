// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.learn.scenes

import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Proves the resources -> classpath wiring on its own, independent of [LineagesResources] and
 * [LineagesSceneContentTest] which already read through it — mirrors `qa/corpus`'s own
 * `CorpusDataResourceTest`.
 */
class SceneResourcesJvmTest {
    @Test
    fun everyLineagesResourceIsOnTheClasspath() {
        val paths = LineagesResources.ERA_SCENE_PATHS + LineagesResources.VOCABULARY_SCENE_PATH + LineagesResources.IDENTIFY_IT_BANK_PATH
        for (path in paths) {
            assertNotNull(javaClass.getResourceAsStream("/$path"), "missing from the classpath: $path")
        }
    }
}
