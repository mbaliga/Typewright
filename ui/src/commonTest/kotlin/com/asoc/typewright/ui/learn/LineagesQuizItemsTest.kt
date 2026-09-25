// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.learn

import com.asoc.typewright.learn.scenes.Align
import com.asoc.typewright.learn.scenes.Caption
import com.asoc.typewright.learn.scenes.Exercise
import com.asoc.typewright.learn.scenes.ExerciseBankEntry
import com.asoc.typewright.learn.scenes.FaceRef
import com.asoc.typewright.learn.scenes.Scene
import com.asoc.typewright.learn.scenes.Stage
import com.asoc.typewright.learn.scenes.Strand
import com.asoc.typewright.learn.scenes.StrandExercisePlan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Portable (desktop + wasmJs/browser) unit tests for [LineagesTab.kt]'s Compose-free half:
 * [buildQuizItems], [optionsFor], [clampScrub], [selectScene], [stressDialLabel]. Every fixture
 * here is hand-built, never [com.asoc.typewright.learn.scenes.LineagesResources] itself —
 * that reads `data/learn-faces/manifest.json`/scene YAML through
 * [com.asoc.typewright.learn.scenes.readSceneResourceText], whose wasmJs actual is Node-`fs`-
 * based (`docs/OPEN_QUESTIONS.md` item 47) and throws under this module's own
 * `:ui:wasmJsBrowserTest` (headless Chrome, not Node — `ui/build.gradle.kts`'s own doc comment).
 * A commonTest that called it unguarded would fail on that one target for an environment reason
 * having nothing to do with the logic under test; [LineagesRealContentTest] (`desktopTest`, JVM
 * only) is where the real, checked-in content gets its own regression coverage instead.
 */
class LineagesQuizItemsTest {
    private val classNames =
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

    private fun fixtureScene(exercise: Exercise? = null): Scene =
        Scene(
            id = "lineages.fixture",
            strand = Strand.LINEAGES,
            title = "Fixture",
            era = "1900",
            duration = 40,
            faces = listOf(FaceRef(key = "a", family = "Family A"), FaceRef(key = "b", family = "Family B")),
            stage = Stage(sample = "ago", align = Align.XHEIGHT, from = "a", to = "b"),
            caption = Caption(tool = "a tool", text = "some text"),
            exercise = exercise,
        )

    // ---- clampScrub / selectScene / stressDialLabel ----

    @Test
    fun clampScrubClampsToTheUnitRange() {
        assertEquals(0.0, clampScrub(-1.0))
        assertEquals(1.0, clampScrub(2.0))
        assertEquals(0.42, clampScrub(0.42))
        assertEquals(0.0, clampScrub(0.0))
        assertEquals(1.0, clampScrub(1.0))
    }

    @Test
    fun selectSceneClampsToAValidIndex() {
        assertEquals(0, selectScene(-3, 5))
        assertEquals(4, selectScene(9, 5))
        assertEquals(2, selectScene(2, 5))
        assertEquals(0, selectScene(0, 5))
    }

    @Test
    fun stressDialLabelMatchesTheExplorersOwnWording() {
        // ui/typewright-explorer.html's own update(): null -> "no stress axis"; a rounded angle
        // of 0 -> "vertical stress" (not "stress 0°"); anything else -> "stress N°".
        assertEquals("no stress axis", stressDialLabel(null))
        assertEquals("vertical stress", stressDialLabel(0.0))
        assertEquals("vertical stress", stressDialLabel(0.4))
        assertEquals("vertical stress", stressDialLabel(-0.4))
        assertEquals("stress 30°", stressDialLabel(30.0))
        assertEquals("stress 12°", stressDialLabel(12.4))
    }

    // ---- optionsFor ----

    @Test
    fun optionsForAlwaysIncludesTheRealAnswerAmongThreeRealClassNames() {
        val entry = ExerciseBankEntry(face = "Libre Bodoni", answer = "Didone", giveaway = "hairlines.")
        val options = optionsFor(entry, classNames, index = 0)

        assertEquals(3, options.size)
        assertEquals(1, options.count { it == "Didone" })
        assertTrue(options.all { it in classNames }, "every option must be a real class name this strand taught: $options")
    }

    @Test
    fun optionsForNeverProposesTheAnswerItselfAsADistractor() {
        val entry = ExerciseBankEntry(face = "Roboto Slab", answer = "Slab", giveaway = "square serifs.")
        for (index in 0..8) {
            val options = optionsFor(entry, classNames, index)
            assertEquals(1, options.count { it == "Slab" }, "index $index: options were $options")
        }
    }

    @Test
    fun optionsForSlotsTheAnswerByIndexModuloThree() {
        val entry = ExerciseBankEntry(face = "Poppins", answer = "Geometric", giveaway = "circular o.")
        assertEquals(0, optionsFor(entry, classNames, index = 0).indexOf("Geometric"))
        assertEquals(1, optionsFor(entry, classNames, index = 1).indexOf("Geometric"))
        assertEquals(2, optionsFor(entry, classNames, index = 2).indexOf("Geometric"))
        // and it cycles
        assertEquals(0, optionsFor(entry, classNames, index = 3).indexOf("Geometric"))
    }

    @Test
    fun optionsForIsDeterministicForTheSameInputs() {
        val entry = ExerciseBankEntry(face = "Open Sans", answer = "Humanist sans", giveaway = "open apertures.")
        assertEquals(optionsFor(entry, classNames, 4), optionsFor(entry, classNames, 4))
    }

    @Test
    fun optionsForRequiresAtLeastThreeClassNames() {
        val entry = ExerciseBankEntry(face = "X", answer = "Y", giveaway = "z")
        assertFailsWith<IllegalArgumentException> { optionsFor(entry, listOf("A", "B"), 0) }
    }

    // ---- buildQuizItems ----

    @Test
    fun buildQuizItemsCarriesAnEligibleSceneExerciseThroughVerbatim() {
        val exercise =
            Exercise(
                word = "Hamburgefonstiv",
                face = "Zilla Slab",
                options = listOf("Slab", "Grotesque", "Didone"),
                answer = "Slab",
                giveaway = "square serifs, low contrast.",
            )
        val plan =
            StrandExercisePlan(
                eligibleSceneExercises = listOf(fixtureScene(exercise = exercise)),
                excludedSceneExercises = emptyList(),
                eligibleBankEntries = emptyList(),
                excludedBankEntries = emptyList(),
            )

        val items = buildQuizItems(plan, classNames)

        assertEquals(1, items.size)
        assertEquals(
            LineagesQuizItem(
                word = "Hamburgefonstiv",
                faceFamily = "Zilla Slab",
                options = listOf("Slab", "Grotesque", "Didone"),
                answer = "Slab",
                giveaway = "square serifs, low contrast.",
            ),
            items.single(),
        )
    }

    @Test
    fun buildQuizItemsSynthesizesABankQuizItemWithTheSharedWordAndSyntheticOptions() {
        val entry = ExerciseBankEntry(face = "Cormorant", answer = "Garalde", giveaway = "oblique stress, small x-height.")
        val plan =
            StrandExercisePlan(
                eligibleSceneExercises = emptyList(),
                excludedSceneExercises = emptyList(),
                eligibleBankEntries = listOf(entry),
                excludedBankEntries = emptyList(),
            )

        val items = buildQuizItems(plan, classNames)

        assertEquals(1, items.size)
        val item = items.single()
        assertEquals(BANK_QUIZ_WORD, item.word)
        assertEquals("Cormorant", item.faceFamily)
        assertEquals("Garalde", item.answer)
        assertEquals(entry.giveaway, item.giveaway)
        assertEquals(3, item.options.size)
        assertTrue("Garalde" in item.options)
    }

    @Test
    fun buildQuizItemsOrdersSceneExercisesBeforeBankEntries() {
        val exercise = Exercise(word = "W", face = "F", options = listOf("A", "B", "C"), answer = "B", giveaway = "G")
        val plan =
            StrandExercisePlan(
                eligibleSceneExercises = listOf(fixtureScene(exercise = exercise)),
                excludedSceneExercises = emptyList(),
                eligibleBankEntries = listOf(ExerciseBankEntry(face = "Josefin Sans", answer = "Art Deco", giveaway = "low crossbars.")),
                excludedBankEntries = emptyList(),
            )

        val items = buildQuizItems(plan, classNames)

        assertEquals(2, items.size)
        assertEquals("W", items[0].word)
        assertEquals(BANK_QUIZ_WORD, items[1].word)
    }

    @Test
    fun buildQuizItemsIgnoresAnExcludedSceneWithNoExercise() {
        // A scene with exercise == null must never appear as a quiz item even if it were
        // (incorrectly) passed in eligibleSceneExercises -- mapNotNull's own defensive skip.
        val plan =
            StrandExercisePlan(
                eligibleSceneExercises = listOf(fixtureScene(exercise = null)),
                excludedSceneExercises = emptyList(),
                eligibleBankEntries = emptyList(),
                excludedBankEntries = emptyList(),
            )

        assertTrue(buildQuizItems(plan, classNames).isEmpty())
    }

    @Test
    fun buildQuizItemsIsEmptyForAnEmptyPlan() {
        val plan =
            StrandExercisePlan(
                eligibleSceneExercises = emptyList(),
                excludedSceneExercises = emptyList(),
                eligibleBankEntries = emptyList(),
                excludedBankEntries = emptyList(),
            )
        assertTrue(buildQuizItems(plan, classNames).isEmpty())
    }
}
