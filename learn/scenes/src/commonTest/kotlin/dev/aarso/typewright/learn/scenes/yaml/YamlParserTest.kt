// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.learn.scenes.yaml

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YamlParserTest {
    private fun mapping(yaml: String): YamlValue.Mapping = parseYaml(yaml) as YamlValue.Mapping

    @Test
    fun parsesPlainAndQuotedScalars() {
        val doc =
            mapping(
                """
                a: plain value
                b: "quoted, with a comma"
                c: "it's got an apostrophe"
                d: 'single quoted'
                """.trimIndent(),
            )
        assertEquals("plain value", doc.scalarOrNull("a"))
        assertEquals("quoted, with a comma", doc.scalarOrNull("b"))
        assertEquals("it's got an apostrophe", doc.scalarOrNull("c"))
        assertEquals("single quoted", doc.scalarOrNull("d"))
    }

    @Test
    fun stripsTrailingAndStandaloneComments() {
        val doc =
            mapping(
                """
                # a leading comment
                a: 1            # trailing comment
                # a standalone comment between entries
                b: 2
                """.trimIndent(),
            )
        assertEquals("1", doc.scalarOrNull("a"))
        assertEquals("2", doc.scalarOrNull("b"))
    }

    @Test
    fun aHashInsideAQuotedScalarIsNotAComment() {
        val doc = mapping("""a: "not a # comment"""")
        assertEquals("not a # comment", doc.scalarOrNull("a"))
    }

    @Test
    fun explicitAndEmptyValuesAreNull() {
        val doc =
            mapping(
                """
                a: ~
                b: null
                c: NULL
                d:
                """.trimIndent(),
            )
        assertNull(doc.scalarOrNull("a"))
        assertNull(doc.scalarOrNull("b"))
        assertNull(doc.scalarOrNull("c"))
        assertNull(doc.scalarOrNull("d"))
        assertTrue(doc.keys.containsAll(listOf("a", "b", "c", "d")))
    }

    @Test
    fun parsesANestedMapping() {
        val doc =
            mapping(
                """
                outer:
                  inner: value
                  another: 2
                """.trimIndent(),
            )
        val outer = doc.requireMapping("outer")
        assertEquals("value", outer.scalarOrNull("inner"))
        assertEquals("2", outer.scalarOrNull("another"))
    }

    @Test
    fun parsesABlockSequenceOfScalars() {
        val doc =
            mapping(
                """
                items:
                  - one
                  - two
                  - three
                """.trimIndent(),
            )
        val items = doc.sequenceOrEmpty("items").map { (it as YamlValue.Scalar).text }
        assertEquals(listOf("one", "two", "three"), items)
    }

    @Test
    fun parsesABlockSequenceOfMappings() {
        val doc =
            mapping(
                """
                faces:
                  - key: garalde
                    family: EB Garamond
                  - key: transitional
                    family: Libre Baskerville
                """.trimIndent(),
            )
        val faces = doc.sequenceOrEmpty("faces").map { it as YamlValue.Mapping }
        assertEquals(2, faces.size)
        assertEquals("garalde", faces[0].scalarOrNull("key"))
        assertEquals("EB Garamond", faces[0].scalarOrNull("family"))
        assertEquals("transitional", faces[1].scalarOrNull("key"))
        assertEquals("Libre Baskerville", faces[1].scalarOrNull("family"))
    }

    @Test
    fun parsesAFlowSequenceOfNumbers() {
        val doc = mapping("stress: [30, 12]")
        val items = doc.sequenceOrEmpty("stress").map { (it as YamlValue.Scalar).text }
        assertEquals(listOf("30", "12"), items)
    }

    @Test
    fun parsesAFlowSequenceOfMultiWordScalars() {
        val doc = mapping("tags: [near-vertical stress, higher contrast, finer serifs]")
        val items = doc.sequenceOrEmpty("tags").map { (it as YamlValue.Scalar).text }
        assertEquals(listOf("near-vertical stress", "higher contrast", "finer serifs"), items)
    }

    @Test
    fun aFlowSequenceMissingItsClosingBracketThrows() {
        assertFailsWith<YamlParseException> { parseYaml("stress: [30, 12") }
    }

    @Test
    fun parsesAFoldedBlockScalarByJoiningLinesWithASpace() {
        val doc =
            mapping(
                """
                text: >
                  Baskerville. Sharper, higher contrast, the stress standing up.
                  Printing catches up with engraving.
                """.trimIndent(),
            )
        assertEquals(
            "Baskerville. Sharper, higher contrast, the stress standing up. Printing catches up with engraving.",
            doc.scalarOrNull("text"),
        )
    }

    @Test
    fun parsesALiteralBlockScalarPreservingNewlines() {
        val doc =
            mapping(
                """
                text: |
                  line one
                  line two
                """.trimIndent(),
            )
        assertEquals("line one\nline two", doc.scalarOrNull("text"))
    }

    @Test
    fun aFoldedBlockScalarBlankLineBecomesAParagraphBreak() {
        val doc =
            mapping(
                """
                text: >
                  first paragraph
                  still first

                  second paragraph
                """.trimIndent(),
            )
        assertEquals("first paragraph still first\nsecond paragraph", doc.scalarOrNull("text"))
    }

    @Test
    fun aFieldAfterABlockScalarIsParsedNormally() {
        val doc =
            mapping(
                """
                text: >
                  body text here.
                tags: [a, b]
                """.trimIndent(),
            )
        assertEquals("body text here.", doc.scalarOrNull("text"))
        assertEquals(listOf("a", "b"), doc.sequenceOrEmpty("tags").map { (it as YamlValue.Scalar).text })
    }

    @Test
    fun tabIndentationThrows() {
        assertFailsWith<YamlParseException> { parseYaml("a:\n\tb: 1") }
    }

    @Test
    fun aMissingKeyValueLineThrows() {
        assertFailsWith<YamlParseException> {
            mapping(
                """
                a: 1
                not a key value line without a colon
                """.trimIndent(),
            )
        }
    }

    @Test
    fun roundTripsLessonsScaffoldsWorkedExampleVerbatim() {
        // docs/LESSONS_SCAFFOLD.md section 1 (CANON), the `lineages.transitional` YAML block,
        // copied verbatim (see SceneParserTest for the full typed round trip through parseScene).
        val doc = mapping(LESSONS_SCAFFOLD_WORKED_EXAMPLE_YAML)

        assertEquals("lineages.transitional", doc.scalarOrNull("id"))
        assertEquals("lineages", doc.scalarOrNull("strand"))
        assertEquals("Transitional", doc.scalarOrNull("title"))
        assertEquals("1757", doc.scalarOrNull("era"))
        assertEquals("40", doc.scalarOrNull("duration"))

        val faces = doc.sequenceOrEmpty("faces").map { it as YamlValue.Mapping }
        assertEquals(
            listOf("garalde" to "EB Garamond", "transitional" to "Libre Baskerville"),
            faces.map {
                it.scalarOrNull("key") to
                    it.scalarOrNull("family")
            },
        )

        val stage = doc.requireMapping("stage")
        assertEquals("ago", stage.scalarOrNull("sample"))
        assertEquals("xheight", stage.scalarOrNull("align"))
        assertEquals("garalde", stage.scalarOrNull("from"))
        assertEquals("transitional", stage.scalarOrNull("to"))
        assertEquals(listOf("30", "12"), stage.sequenceOrEmpty("stress").map { (it as YamlValue.Scalar).text })

        val caption = doc.requireMapping("caption")
        assertEquals("the engraver's burin on copper", caption.scalarOrNull("tool"))
        assertEquals(
            "Baskerville. Sharper, higher contrast, the stress standing up. Printing catches up with engraving.",
            caption.scalarOrNull("text"),
        )
        assertEquals(
            listOf("near-vertical stress", "higher contrast", "finer serifs"),
            caption.sequenceOrEmpty("tags").map { (it as YamlValue.Scalar).text },
        )

        val callouts = doc.sequenceOrEmpty("callouts").map { it as YamlValue.Mapping }
        assertEquals(1, callouts.size)
        assertEquals("a", callouts[0].scalarOrNull("glyph"))
        assertEquals("terminal", callouts[0].scalarOrNull("part"))
        assertEquals("finer, but still bracketed", callouts[0].scalarOrNull("label"))

        val exercise = doc.requireMapping("exercise")
        assertEquals("Hamburgefonstiv", exercise.scalarOrNull("word"))
        assertEquals("Libre Baskerville", exercise.scalarOrNull("face"))
        assertEquals(listOf("Garalde", "Transitional", "Didone"), exercise.sequenceOrEmpty("options").map { (it as YamlValue.Scalar).text })
        assertEquals("Transitional", exercise.scalarOrNull("answer"))
        assertEquals(
            "stress almost vertical, but the serifs are still bracketed and the contrast is moderate. Baskerville, 1757.",
            exercise.scalarOrNull("giveaway"),
        )
    }
}

/**
 * `docs/LESSONS_SCAFFOLD.md` section 1's own worked example, copied byte-for-byte (verified against
 * the source file with `diff` while writing this fixture). Shared with `SceneParserTest`.
 */
internal val LESSONS_SCAFFOLD_WORKED_EXAMPLE_YAML =
    """
    # scenes/lineages/03-transitional.yaml
    id: lineages.transitional
    strand: lineages            # lineages | anatomy | craft | scripts | reading
    title: Transitional
    era: "1757"
    duration: 40                # seconds at the default pace; scrubbing ignores it
    faces:                      # OFL/Apache faces from the corpus, by family name
      - key: garalde
        family: EB Garamond
      - key: transitional
        family: Libre Baskerville
    stage:
      sample: "ago"             # the letters on stage
      align: xheight            # xheight | capheight | baseline
      from: garalde             # crossfade endpoints
      to: transitional
      stress: [30, 12]          # degrees from vertical at from/to; null = no stress axis
    caption:
      tool: "the engraver's burin on copper"
      text: >
        Baskerville. Sharper, higher contrast, the stress standing up.
        Printing catches up with engraving.
      tags: [near-vertical stress, higher contrast, finer serifs]
    callouts:                   # optional labels on the stage, resolved on the 'to' face
      - glyph: a
        part: terminal
        label: "finer, but still bracketed"
    exercise:                   # optional; identify-it
      word: Hamburgefonstiv
      face: Libre Baskerville   # must not appear earlier in the strand
      options: [Garalde, Transitional, Didone]
      answer: Transitional
      giveaway: >
        stress almost vertical, but the serifs are still bracketed and the contrast
        is moderate. Baskerville, 1757.
    """.trimIndent()
