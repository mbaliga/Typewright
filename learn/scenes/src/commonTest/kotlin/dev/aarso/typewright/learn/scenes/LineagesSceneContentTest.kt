package dev.aarso.typewright.learn.scenes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Round-trips every real Lineages YAML file this P6 content-authoring task wrote
 * (`learn/scenes/src/commonMain/resources/scenes/lineages/`) through the sibling-built
 * [parseScene]/[parseExerciseBank], and checks the parsed content against
 * `docs/LESSONS_SCAFFOLD.md` section 2's own era table, vocabulary note and identify-it bank —
 * not just "it parses without throwing". [LineagesResources] is what wires the file paths to
 * these loaders; this test also proves [StrandSequencer.plan], run on the real content, produces
 * exactly the collisions the sibling's own `docs/OPEN_QUESTIONS.md` items 35-36 predicted.
 */
class LineagesSceneContentTest {
    @Test
    fun allTenEraScenesParseInOrderWithTheDocumentedFields() {
        val scenes = LineagesResources.loadEraScenes()
        assertEquals(10, scenes.size)

        val expectedIds =
            listOf(
                "lineages.blackletter",
                "lineages.garalde",
                "lineages.transitional",
                "lineages.didone",
                "lineages.slab",
                "lineages.grotesque",
                "lineages.artdeco",
                "lineages.geometric",
                "lineages.humanist",
                "lineages.neogrotesque",
            )
        assertEquals(expectedIds, scenes.map { it.id })

        val expectedTitles =
            listOf(
                "Blackletter",
                "Garalde",
                "Transitional",
                "Didone",
                "Slab",
                "Grotesque",
                "Art Deco",
                "Geometric",
                "Humanist sans",
                "Neo-grotesque",
            )
        assertEquals(expectedTitles, scenes.map { it.title })

        val expectedEras =
            listOf("c. 1450", "c. 1530", "1757", "c. 1790", "1815", "c. 1830", "1925", "1927", "1928", "1957")
        assertEquals(expectedEras, scenes.map { it.era })

        val expectedTools =
            listOf(
                "a broad nib held steep; then Gutenberg's metal",
                "a broad nib, cut into steel punches",
                "the engraver's burin on copper",
                "the pointed pen, then the compass",
                "wood type for the poster wall",
                "the same posters, serifs dropped",
                "the draughtsman's set square",
                "compass and ruler",
                "the pen, remembered",
                "the phototypesetter",
            )
        assertEquals(expectedTools, scenes.map { it.caption.tool })

        // Every scene this task authored is marked SCAFFOLD (Scene.scaffold's KDoc).
        assertTrue(scenes.all { it.scaffold })

        // Sample on stage: "ago" (section 2's own header), aligned at x-height throughout.
        assertTrue(scenes.all { it.stage.sample == "ago" })
        assertTrue(scenes.all { it.stage.align == Align.XHEIGHT })
    }

    @Test
    fun stageFromToChainsConsecutiveErasByFamily() {
        val scenes = LineagesResources.loadEraScenes()
        val expectedChain =
            listOf(
                "UnifrakturMaguntia" to "UnifrakturMaguntia", // era 1: no earlier era, from == to
                "UnifrakturMaguntia" to "EB Garamond",
                "EB Garamond" to "Libre Baskerville",
                "Libre Baskerville" to "Playfair Display",
                "Playfair Display" to "Zilla Slab",
                "Zilla Slab" to "Work Sans",
                "Work Sans" to "Limelight",
                "Limelight" to "Jost",
                "Jost" to "Source Sans 3",
                "Source Sans 3" to "Inter",
            )
        assertEquals(expectedChain, scenes.map { it.fromFamily to it.toFamily })
    }

    @Test
    fun stressAxisIsNullUntilEra3ThenChainsTheTablesOwnDegrees() {
        // docs/LESSONS_SCAFFOLD.md section 2's own stress column: none, 30, 12, 0, 0, 6, 0, 0, 10, 0.
        // stage.stress pairs the *from* face's own angle with the *to* face's own angle, so era 1
        // (no earlier era) and era 2 (its "from", Blackletter, has no angle at all) are both null —
        // see 02-garalde.yaml's own header comment for why. Era 3 onward is real data from the table.
        val scenes = LineagesResources.loadEraScenes()
        val expectedStress: List<Pair<Double, Double>?> =
            listOf(
                null,
                null,
                30.0 to 12.0,
                12.0 to 0.0,
                0.0 to 0.0,
                0.0 to 6.0,
                6.0 to 0.0,
                0.0 to 0.0,
                0.0 to 10.0,
                10.0 to 0.0,
            )
        assertEquals(expectedStress, scenes.map { it.stage.stress })
    }

    @Test
    fun tagsMatchTheEraTablesTagsColumnSplitOnTheMiddleDot() {
        val scenes = LineagesResources.loadEraScenes()
        val expectedTags =
            listOf(
                listOf("broad-nib strokes", "no round shapes", "tight texture"),
                listOf("oblique stress", "two-storey a and g", "bracketed serifs"),
                listOf("near-vertical stress", "higher contrast", "finer serifs"),
                listOf("vertical stress", "hairline serifs", "ball terminals"),
                listOf("square serifs", "low contrast", "even colour"),
                listOf("no serifs", "slight contrast", "narrow apertures"),
                listOf("high or low crossbars", "inline stripes", "extreme widths"),
                listOf("circular o", "single-storey a", "monoline"),
                listOf("open apertures", "pen proportions", "two-storey a and g"),
                listOf("horizontal terminals", "even colour", "closed apertures"),
            )
        assertEquals(expectedTags, scenes.map { it.caption.tags })
    }

    @Test
    fun transitionalSceneMatchesTheCanonWorkedExampleExactly() {
        // docs/LESSONS_SCAFFOLD.md section 1's own worked example, re-checked here as this
        // strand's real era-3 file rather than only as a fixture string in SceneParserTest/
        // YamlParserTest.
        val scene = LineagesResources.loadEraScenes()[2]
        assertEquals("lineages.transitional", scene.id)
        assertEquals(1, scene.callouts.size)
        assertEquals(Callout(glyph = "a", part = "terminal", label = "finer, but still bracketed"), scene.callouts.single())
        assertEquals("Libre Baskerville", scene.exercise?.face)
        assertEquals("Transitional", scene.exercise?.answer)
    }

    @Test
    fun onlyTheTransitionalEraSceneCarriesAnInlineExercise() {
        // Section 2 gives no per-era inline exercise beyond section 1's own worked example.
        val scenes = LineagesResources.loadEraScenes()
        assertEquals(listOf("lineages.transitional"), scenes.filter { it.exercise != null }.map { it.id })
    }

    @Test
    fun onlyTheTransitionalEraSceneCarriesCallouts() {
        // Section 2's table gives no per-era callout data beyond section 1's own worked example.
        val scenes = LineagesResources.loadEraScenes()
        assertEquals(listOf("lineages.transitional"), scenes.filter { it.callouts.isNotEmpty() }.map { it.id })
    }

    @Test
    fun vocabularySceneCarriesTheNotesTextVerbatimAndIsMarkedScaffold() {
        val scene = LineagesResources.loadVocabularyScene()
        assertEquals("lineages.vocabulary", scene.id)
        assertEquals(Strand.LINEAGES, scene.strand)
        assertNull(scene.era)
        assertTrue(scene.scaffold)
        assertEquals("gothic", scene.stage.sample)
        assertEquals("Work Sans", scene.fromFamily)
        assertEquals("UnifrakturMaguntia", scene.toFamily)
        assertNull(scene.stage.stress)
        assertEquals(
            "\"Gothic\" means sans in American usage (Franklin Gothic, News Gothic) and blackletter " +
                "in European usage. The app uses the Google Fonts taxonomy names; this is why.",
            scene.caption.text,
        )
    }

    @Test
    fun identifyItBankHasTheDocumentedEightEntries() {
        val bank = LineagesResources.loadIdentifyItBank()
        val expected =
            listOf(
                "Libre Baskerville" to "Transitional",
                "Libre Bodoni" to "Didone",
                "Poppins" to "Geometric",
                "Libre Franklin" to "Grotesque",
                "Roboto Slab" to "Slab",
                "Cormorant" to "Garalde",
                "Open Sans" to "Humanist sans",
                "Josefin Sans" to "Art Deco",
            )
        assertEquals(expected, bank.map { it.face to it.answer })
        // "Each exercise names the give-away and the year" (section 2's own closing line).
        assertTrue(bank.all { entry -> entry.giveaway.any { it.isDigit() } })
    }

    @Test
    fun realContentReproducesTheDocumentedCollisions() {
        // docs/OPEN_QUESTIONS.md items 35-36, verified here against the actual authored YAML
        // rather than only against StrandSequencerTest's synthetic fixtures.
        val block = LineagesResources.loadFullBlockInPlayOrder()
        val bank = LineagesResources.loadIdentifyItBank()
        val plan = StrandSequencer.plan(block, bank)

        // Era 3's own inline exercise collides with its own stage.to face.
        assertEquals(emptyList(), plan.eligibleSceneExercises)
        assertEquals(listOf("lineages.transitional"), plan.excludedSceneExercises.map { it.item.id })

        // The bank's first entry (Libre Baskerville) collides with the same on-stage face; the
        // other seven do not, since data/learn-faces/manifest.json fetched a distinct
        // exercise-bank face for every other answer class.
        assertEquals(7, plan.eligibleBankEntries.size)
        assertEquals(listOf("Libre Baskerville"), plan.excludedBankEntries.map { it.item.face })
        assertTrue(plan.hasAnyEligibleExercise)
    }
}
