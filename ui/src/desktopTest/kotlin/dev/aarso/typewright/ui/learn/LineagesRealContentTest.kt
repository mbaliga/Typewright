// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.ui.learn

import dev.aarso.typewright.learn.scenes.LineagesResources
import dev.aarso.typewright.learn.scenes.StrandSequencer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * JVM-only (real classpath resources — same reason [AnatomyLensDataHyleDecoTest] and
 * `LearnFaceFontsScreenshotTest` are `desktopTest`, not `commonTest`): pins [LineagesTab]'s own
 * wiring of the strand's *real, checked-in* content — [LineagesResources.loadFullBlockInPlayOrder],
 * [LineagesResources.loadIdentifyItBank], [StrandSequencer.plan] and [buildQuizItems] — against
 * actual numbers, the same "measured, not invented" spirit CLAUDE.md law 5 asks of anything a
 * user is judged by (here: what the identify-it quiz actually shows). If the real YAML content
 * ever changes these counts, this test is meant to fail and be looked at, not silently drift.
 */
class LineagesRealContentTest {
    @Test
    fun fullBlockIsElevenScenesInEraOrderWithTheVocabularyAsideAfterEraSix() {
        val scenes = LineagesResources.loadFullBlockInPlayOrder()
        assertEquals(11, scenes.size)
        assertEquals("lineages.grotesque", scenes[5].id)
        assertEquals("lineages.vocabulary", scenes[6].id)
        assertEquals("lineages.artdeco", scenes[7].id)
        assertNull(scenes[6].era, "the vocabulary aside is not tied to one era's own date")
    }

    @Test
    fun theOnlyInlineExerciseCollidesAndIsExcludedPerStrandSequencersOwnBlockWideRule() {
        // docs/OPEN_QUESTIONS.md items 35-36 / StrandSequencer.kt's own KDoc: the transitional
        // scene's own inline exercise names Libre Baskerville, the exact face that same scene put
        // on stage as stage.to -- StrandSequencer.plan correctly excludes it.
        val scenes = LineagesResources.loadFullBlockInPlayOrder()
        val bank = LineagesResources.loadIdentifyItBank()
        val plan = StrandSequencer.plan(scenes, bank)

        assertTrue(plan.eligibleSceneExercises.isEmpty(), "the transitional scene's own exercise should collide, not be eligible")
        assertEquals(1, plan.excludedSceneExercises.size)
        assertEquals("Libre Baskerville", plan.excludedSceneExercises.single().collidingFace)
    }

    @Test
    fun sevenOfTheEightBankEntriesAreEligibleOnlyLibreBaskervilleCollides() {
        val scenes = LineagesResources.loadFullBlockInPlayOrder()
        val bank = LineagesResources.loadIdentifyItBank()
        val plan = StrandSequencer.plan(scenes, bank)

        assertEquals(8, bank.size)
        assertEquals(7, plan.eligibleBankEntries.size)
        assertEquals(1, plan.excludedBankEntries.size)
        assertEquals(
            "Libre Baskerville",
            plan.excludedBankEntries
                .single()
                .item.face,
        )
        assertTrue(plan.eligibleBankEntries.none { it.face == "Libre Baskerville" })
    }

    @Test
    fun buildQuizItemsOnTheRealPlanIsSevenBankCardsNoSceneCards() {
        val scenes = LineagesResources.loadFullBlockInPlayOrder()
        val bank = LineagesResources.loadIdentifyItBank()
        val plan = StrandSequencer.plan(scenes, bank)
        val classNames = scenes.filter { it.era != null }.map { it.title }

        val items = buildQuizItems(plan, classNames)

        assertEquals(7, items.size)
        assertTrue(items.all { it.word == BANK_QUIZ_WORD })
        val families = items.map { it.faceFamily }.toSet()
        assertEquals(setOf("Libre Bodoni", "Poppins", "Libre Franklin", "Roboto Slab", "Cormorant", "Open Sans", "Josefin Sans"), families)
        // every synthesised answer is a real class name this strand actually taught
        assertTrue(items.all { it.answer in classNames })
        assertTrue(items.all { it.options.size == 3 && it.answer in it.options })
    }

    @Test
    fun learnFaceKeyForFamilyResolvesEveryRealBankFamilyToItsRealManifestKey() {
        // The exact family -> key pairing data/learn-faces/manifest.json's own "exercise-bank"
        // role entries carry (read directly from that file while writing this test, not guessed).
        val expected =
            mapOf(
                "Libre Bodoni" to "librebodoni",
                "Poppins" to "poppins",
                "Libre Franklin" to "librefranklin",
                "Roboto Slab" to "robotoslab",
                "Cormorant" to "cormorant",
                "Open Sans" to "opensans",
                "Josefin Sans" to "josefinsans",
            )
        for ((family, key) in expected) {
            assertEquals(key, learnFaceKeyForFamily(family), "family \"$family\"")
        }
    }

    @Test
    fun learnFaceKeyForFamilyResolvesEveryRealOnStageFamilyToo() {
        val expected =
            mapOf(
                "UnifrakturMaguntia" to "blackletter",
                "EB Garamond" to "garalde",
                "Libre Baskerville" to "transitional",
                "Playfair Display" to "didone",
                "Zilla Slab" to "slab",
                "Work Sans" to "grotesque",
                "Limelight" to "artdeco",
                "Jost" to "geometric",
                "Source Sans 3" to "humanist",
                "Inter" to "neogrotesque",
            )
        for ((family, key) in expected) {
            assertEquals(key, learnFaceKeyForFamily(family), "family \"$family\"")
        }
    }

    @Test
    fun learnFaceKeyForFamilyIsNullForAFamilyNotInTheManifest() {
        assertNull(learnFaceKeyForFamily("Not A Real Family"))
    }

    @Test
    fun everyEraSceneCarriesARealResolvableFromAndToKey() {
        // Sanity check on the real YAML: LineagesTab's own StageArea calls
        // learnFaceFontFamily(scene.stage.from)/(scene.stage.to) directly with these keys.
        for (scene in LineagesResources.loadFullBlockInPlayOrder()) {
            assertNotNull(scene.familyForKey(scene.stage.from), "${scene.id}: stage.from=\"${scene.stage.from}\" has no matching FaceRef")
            assertNotNull(scene.familyForKey(scene.stage.to), "${scene.id}: stage.to=\"${scene.stage.to}\" has no matching FaceRef")
        }
    }
}
