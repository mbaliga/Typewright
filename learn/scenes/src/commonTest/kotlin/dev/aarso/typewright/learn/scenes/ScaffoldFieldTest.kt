package dev.aarso.typewright.learn.scenes

import dev.aarso.typewright.learn.scenes.yaml.YamlParseException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [Scene.scaffold] — the P6 content-authoring task's own small extension to the sibling-built
 * [Scene]/[parseScene] (see `Scene.kt`'s KDoc on the field for why it exists). Kept as its own
 * test file rather than folded into `SceneParserTest` so this task's addition is easy to find
 * and to remove if a later task gives the schema a different SCAFFOLD convention.
 */
class ScaffoldFieldTest {
    private fun minimalScene(extraLine: String = ""): String =
        """
        id: test.scene
        strand: lineages
        title: Test
        duration: 1
        faces: []
        stage:
          sample: a
          align: baseline
          from: x
          to: x
        caption:
          tool: t
          text: t
        $extraLine
        """.trimIndent()

    @Test
    fun defaultsToFalseWhenAbsent() {
        assertFalse(parseScene(minimalScene()).scaffold)
    }

    @Test
    fun parsesExplicitTrue() {
        assertTrue(parseScene(minimalScene("scaffold: true")).scaffold)
    }

    @Test
    fun parsesExplicitFalse() {
        assertFalse(parseScene(minimalScene("scaffold: false")).scaffold)
    }

    @Test
    fun anInvalidValueThrows() {
        val exception = assertFailsWith<YamlParseException> { parseScene(minimalScene("scaffold: maybe")) }
        assertTrue(exception.message?.contains("scaffold") == true)
    }

    @Test
    fun sceneDefaultConstructorAlsoDefaultsToFalse() {
        assertFalse(
            Scene(
                id = "x",
                strand = Strand.LINEAGES,
                title = "x",
                era = null,
                duration = 1,
                faces = emptyList(),
                stage = Stage(sample = "a", align = Align.BASELINE, from = "x", to = "x"),
                caption = Caption(tool = "t", text = "t"),
            ).scaffold,
        )
    }
}
