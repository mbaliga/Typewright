// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.learn.scenes

import com.asoc.typewright.learn.scenes.yaml.LESSONS_SCAFFOLD_WORKED_EXAMPLE_YAML
import com.asoc.typewright.learn.scenes.yaml.YamlParseException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SceneParserTest {
    @Test
    fun parsesLessonsScaffoldsWorkedExampleIntoTheExpectedScene() {
        val scene = parseScene(LESSONS_SCAFFOLD_WORKED_EXAMPLE_YAML)

        assertEquals(
            Scene(
                id = "lineages.transitional",
                strand = Strand.LINEAGES,
                title = "Transitional",
                era = "1757",
                duration = 40,
                faces =
                    listOf(
                        FaceRef("garalde", "EB Garamond"),
                        FaceRef("transitional", "Libre Baskerville"),
                    ),
                stage =
                    Stage(
                        sample = "ago",
                        align = Align.XHEIGHT,
                        from = "garalde",
                        to = "transitional",
                        stress = 30.0 to 12.0,
                    ),
                caption =
                    Caption(
                        tool = "the engraver's burin on copper",
                        text =
                            "Baskerville. Sharper, higher contrast, the stress standing up. " +
                                "Printing catches up with engraving.",
                        tags = listOf("near-vertical stress", "higher contrast", "finer serifs"),
                    ),
                callouts = listOf(Callout(glyph = "a", part = "terminal", label = "finer, but still bracketed")),
                exercise =
                    Exercise(
                        word = "Hamburgefonstiv",
                        face = "Libre Baskerville",
                        options = listOf("Garalde", "Transitional", "Didone"),
                        answer = "Transitional",
                        giveaway =
                            "stress almost vertical, but the serifs are still bracketed and the contrast " +
                                "is moderate. Baskerville, 1757.",
                    ),
            ),
            scene,
        )
    }

    @Test
    fun resolvesFromAndToFamiliesThroughFaces() {
        val scene = parseScene(LESSONS_SCAFFOLD_WORKED_EXAMPLE_YAML)
        assertEquals("EB Garamond", scene.fromFamily)
        assertEquals("Libre Baskerville", scene.toFamily)
    }

    @Test
    fun eraAndStressAndCalloutsAndExerciseAreOptional() {
        val scene =
            parseScene(
                """
                id: anatomy.stem
                strand: anatomy
                title: Stem
                duration: 20
                faces:
                  - key: subject
                    family: Inter
                stage:
                  sample: n
                  align: baseline
                  from: subject
                  to: subject
                caption:
                  tool: a broad nib
                  text: the vertical stroke.
                """.trimIndent(),
            )
        assertNull(scene.era)
        assertNull(scene.stage.stress)
        assertEquals(emptyList(), scene.callouts)
        assertNull(scene.exercise)
        assertEquals(emptyList(), scene.caption.tags)
    }

    @Test
    fun anUnknownStrandThrowsAClearError() {
        val exception =
            assertFailsWith<YamlParseException> {
                parseScene(
                    """
                    id: bad.strand
                    strand: nonsense
                    title: Bad
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
                    """.trimIndent(),
                )
            }
        assertEquals(true, exception.message?.contains("strand") == true)
    }

    @Test
    fun anUnknownAlignThrows() {
        assertFailsWith<YamlParseException> {
            parseScene(
                """
                id: bad.align
                strand: anatomy
                title: Bad
                duration: 1
                faces: []
                stage:
                  sample: a
                  align: middle
                  from: x
                  to: x
                caption:
                  tool: t
                  text: t
                """.trimIndent(),
            )
        }
    }

    @Test
    fun aStressListWithTheWrongCountThrows() {
        assertFailsWith<YamlParseException> {
            parseScene(
                """
                id: bad.stress
                strand: lineages
                title: Bad
                duration: 1
                faces: []
                stage:
                  sample: a
                  align: baseline
                  from: x
                  to: x
                  stress: [30]
                caption:
                  tool: t
                  text: t
                """.trimIndent(),
            )
        }
    }

    @Test
    fun aMissingRequiredFieldThrowsNamingTheField() {
        val exception =
            assertFailsWith<YamlParseException> {
                parseScene(
                    """
                    strand: lineages
                    title: Missing id
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
                    """.trimIndent(),
                )
            }
        assertEquals(true, exception.message?.contains("id") == true)
    }

    @Test
    fun aNonIntegerDurationThrows() {
        assertFailsWith<YamlParseException> {
            parseScene(
                """
                id: bad.duration
                strand: lineages
                title: Bad
                duration: forty
                faces: []
                stage:
                  sample: a
                  align: baseline
                  from: x
                  to: x
                caption:
                  tool: t
                  text: t
                """.trimIndent(),
            )
        }
    }
}
