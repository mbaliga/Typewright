package dev.aarso.typewright.learn.scenes

import dev.aarso.typewright.learn.scenes.yaml.YamlParseException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [FaceRef.source]/[FaceRef.path] and [Stage.pipeline] — this P6 content-authoring task's own
 * small, additive extension to the sibling-built [Scene]/[parseScene], needed to express a Craft
 * scene's own before/after shape (this project's own construction pipeline, not two independent
 * typefaces). See `Scene.kt`'s KDoc on [Scene] itself for the full "why". Kept as its own test
 * file, the same way the sibling's [ScaffoldFieldTest] does for `Scene.scaffold`, so this task's
 * addition is easy to find and to remove if a later task gives Craft a different schema.
 */
class CraftSchemaExtensionTest {
    private fun minimalScene(
        extraFaceLines: String = "",
        extraStageLine: String = "",
    ): String =
        """
        id: test.scene
        strand: craft
        title: Test
        duration: 1
        faces:
          - key: x
            family: Some Face
            $extraFaceLines
        stage:
          sample: a
          align: baseline
          from: x
          to: x
          $extraStageLine
        caption:
          tool: t
          text: t
        """.trimIndent()

    @Test
    fun faceRefSourceDefaultsToCorpusWhenAbsent() {
        val scene = parseScene(minimalScene())
        assertEquals(FaceSource.CORPUS, scene.faces.single().source)
        assertNull(scene.faces.single().path)
    }

    @Test
    fun faceRefTwoArgumentConstructorStillDefaultsToCorpusUnchanged() {
        // The exact call shape every existing Lineages scene/test uses (SceneParserTest's own
        // worked-example fixture: FaceRef("garalde", "EB Garamond")) must keep compiling and
        // keep meaning exactly what it meant before this extension existed.
        val ref = FaceRef("garalde", "EB Garamond")
        assertEquals(FaceSource.CORPUS, ref.source)
        assertNull(ref.path)
    }

    @Test
    fun faceRefParsesExplicitProjectSourceAndPath() {
        val scene = parseScene(minimalScene(extraFaceLines = "source: project\n            path: fonts/HyleDeco-Regular.ttf"))
        val face = scene.faces.single()
        assertEquals(FaceSource.PROJECT, face.source)
        assertEquals("fonts/HyleDeco-Regular.ttf", face.path)
    }

    @Test
    fun faceRefParsesProjectSourceWithNoPathForAnIllustrativeOrPipelineFace() {
        val scene = parseScene(minimalScene(extraFaceLines = "source: project"))
        val face = scene.faces.single()
        assertEquals(FaceSource.PROJECT, face.source)
        assertNull(face.path)
    }

    @Test
    fun anUnknownFaceSourceThrowsNamingTheField() {
        val exception =
            assertFailsWith<YamlParseException> {
                parseScene(minimalScene(extraFaceLines = "source: googlefonts"))
            }
        assertTrue(exception.message?.contains("faces[].source") == true)
    }

    @Test
    fun stagePipelineDefaultsToNullWhenAbsent() {
        assertNull(parseScene(minimalScene()).stage.pipeline)
    }

    @Test
    fun stagePipelineParsesAnExplicitModuleDotFunctionName() {
        val scene = parseScene(minimalScene(extraStageLine = "pipeline: core-geometry.fitPolylineToFinishedContour"))
        assertEquals("core-geometry.fitPolylineToFinishedContour", scene.stage.pipeline)
    }

    @Test
    fun stageDefaultConstructorAlsoDefaultsPipelineToNull() {
        assertNull(Stage(sample = "a", align = Align.BASELINE, from = "x", to = "x").pipeline)
    }
}
