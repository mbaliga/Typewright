// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.learn.scenes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StrandSequencerTest {
    private fun lineageScene(
        id: String,
        fromFamily: String,
        toFamily: String,
        exerciseFace: String? = null,
    ): Scene =
        Scene(
            id = id,
            strand = Strand.LINEAGES,
            title = id,
            era = null,
            duration = 30,
            faces = listOf(FaceRef("from", fromFamily), FaceRef("to", toFamily)),
            stage = Stage(sample = "ago", align = Align.XHEIGHT, from = "from", to = "to"),
            caption = Caption(tool = "t", text = "t"),
            exercise =
                exerciseFace?.let {
                    Exercise(word = "Hamburgefonstiv", face = it, options = listOf("A", "B"), answer = "A", giveaway = "g")
                },
        )

    @Test
    fun onStageFamiliesCollectsEveryFromAndToAcrossTheBlock() {
        val scenes =
            listOf(
                lineageScene("1", "Blackletter Face", "EB Garamond"),
                lineageScene("2", "EB Garamond", "Libre Baskerville"),
            )
        assertEquals(setOf("Blackletter Face", "EB Garamond", "Libre Baskerville"), StrandSequencer.onStageFamilies(scenes))
    }

    @Test
    fun aSceneExerciseNamingAFaceUsedOnStageAnywhereInTheBlockIsExcluded() {
        // The scaffold's own worked example collision: era 3's exercise names the exact face
        // it just put on stage as `to`.
        val scenes = listOf(lineageScene("lineages.transitional", "EB Garamond", "Libre Baskerville", exerciseFace = "Libre Baskerville"))
        val plan = StrandSequencer.plan(scenes)
        assertEquals(emptyList(), plan.eligibleSceneExercises)
        assertEquals(1, plan.excludedSceneExercises.size)
        assertEquals(
            "lineages.transitional",
            plan.excludedSceneExercises
                .single()
                .item.id,
        )
        assertEquals("Libre Baskerville", plan.excludedSceneExercises.single().collidingFace)
    }

    @Test
    fun aSceneExerciseCollidesEvenWhenTheOnStageUseIsInALaterSceneOfTheBlock() {
        // "used on stage in that block" (CANON) is read as the whole block, not just earlier
        // scenes -- a face appearing on stage anywhere in the block still disqualifies it.
        val scenes =
            listOf(
                lineageScene("1", "A", "B", exerciseFace = "C"),
                lineageScene("2", "B", "C"),
            )
        val plan = StrandSequencer.plan(scenes)
        assertEquals(emptyList(), plan.eligibleSceneExercises)
        assertEquals(
            "1",
            plan.excludedSceneExercises
                .single()
                .item.id,
        )
    }

    @Test
    fun aSceneExerciseNamingAFaceNeverOnStageIsEligible() {
        val scenes =
            listOf(
                lineageScene("1", "A", "B"),
                lineageScene("2", "B", "C", exerciseFace = "Never On Stage"),
            )
        val plan = StrandSequencer.plan(scenes)
        assertEquals(listOf("2"), plan.eligibleSceneExercises.map { it.id })
        assertEquals(emptyList(), plan.excludedSceneExercises)
    }

    @Test
    fun scenesWithNoExerciseAreAbsentFromBothEligibleAndExcluded() {
        val scenes = listOf(lineageScene("1", "A", "B"))
        val plan = StrandSequencer.plan(scenes)
        assertEquals(emptyList(), plan.eligibleSceneExercises)
        assertEquals(emptyList(), plan.excludedSceneExercises)
        assertFalse(plan.hasAnyEligibleExercise)
    }

    @Test
    fun bankEntriesAreFilteredTheSameWayAsSceneExercises() {
        val scenes =
            listOf(
                lineageScene("1", "Blackletter Face", "EB Garamond"),
                lineageScene("2", "EB Garamond", "Libre Baskerville"),
            )
        val bank =
            listOf(
                ExerciseBankEntry(face = "Libre Baskerville", answer = "Transitional", giveaway = "g1"), // collides
                ExerciseBankEntry(face = "Poppins", answer = "Geometric", giveaway = "g2"), // clear
            )
        val plan = StrandSequencer.plan(scenes, bank)
        assertEquals(listOf("Poppins"), plan.eligibleBankEntries.map { it.face })
        assertEquals(listOf("Libre Baskerville"), plan.excludedBankEntries.map { it.item.face })
        assertEquals("Libre Baskerville", plan.excludedBankEntries.single().collidingFace)
    }

    @Test
    fun hasAnyEligibleExerciseIsTrueWhenOnlyTheBankHasOne() {
        val scenes = listOf(lineageScene("1", "A", "B"))
        val plan = StrandSequencer.plan(scenes, listOf(ExerciseBankEntry("Z", "answer", "giveaway")))
        assertTrue(plan.hasAnyEligibleExercise)
    }

    @Test
    fun worksForAnyStrandLengthNotJustTenLineagesEras() {
        val threeSceneBlock =
            listOf(
                lineageScene("1", "A", "B"),
                lineageScene("2", "B", "C", exerciseFace = "D"),
                lineageScene("3", "C", "D"),
            )
        val plan = StrandSequencer.plan(threeSceneBlock)
        // D is used on stage in scene 3, so scene 2's own D exercise is excluded even though
        // scene 3 comes after scene 2.
        assertEquals(emptyList(), plan.eligibleSceneExercises)

        val oneSceneBlock = listOf(lineageScene("only", "A", "B", exerciseFace = "Z"))
        val onePlan = StrandSequencer.plan(oneSceneBlock)
        assertEquals(listOf("only"), onePlan.eligibleSceneExercises.map { it.id })
    }

    @Test
    fun emptyBlockHasNoEligibleExercises() {
        val plan = StrandSequencer.plan(emptyList())
        assertFalse(plan.hasAnyEligibleExercise)
        assertEquals(emptySet(), StrandSequencer.onStageFamilies(emptyList()))
    }

    @Test
    fun aFromOrToKeyMissingFromFacesIsSkippedRatherThanCrashing() {
        val malformed =
            Scene(
                id = "malformed",
                strand = Strand.LINEAGES,
                title = "Malformed",
                era = null,
                duration = 10,
                faces = listOf(FaceRef("from", "A")),
                // "to" has no matching FaceRef.
                stage = Stage(sample = "a", align = Align.BASELINE, from = "from", to = "missing"),
                caption = Caption(tool = "t", text = "t"),
            )
        assertEquals(setOf("A"), StrandSequencer.onStageFamilies(listOf(malformed)))
    }
}
