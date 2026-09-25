// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.scripts.kana

import com.asoc.typewright.scripts.WritingScript
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KanaFeaturePlanTest {
    @Test
    fun bothPlansCarryTheirOwnScript() {
        assertEquals(WritingScript.HIRAGANA, HIRAGANA_FEATURE_PLAN.script)
        assertEquals(WritingScript.KATAKANA, KATAKANA_FEATURE_PLAN.script)
    }

    @Test
    fun bothPlansAreMinimalOneStageEachMatchingKanasRealLighterWeightMachinery() {
        assertEquals(listOf("ccmp"), HIRAGANA_FEATURE_PLAN.gsubStagesInOrder)
        assertEquals(listOf("ccmp"), KATAKANA_FEATURE_PLAN.gsubStagesInOrder)
        assertEquals(listOf("mark"), HIRAGANA_FEATURE_PLAN.gposFeatures)
        assertEquals(listOf("mark"), KATAKANA_FEATURE_PLAN.gposFeatures)
    }

    @Test
    fun neitherPlanInventsJoiningOrConjunctStagesKanaDoesNotHave() {
        // Contrast Arabic's isol/init/medi/fina and Devanagari's rphf/rkrf/blwf/half/vatu/cjct --
        // named in com.asoc.typewright.scripts.FeatureGenerationPlan's own KDoc -- kana has none
        // of those, and this plan must not invent any of them.
        val joiningOrConjunctStages =
            setOf(
                "isol",
                "init",
                "medi",
                "fina",
                "rlig",
                "rclt",
                "rphf",
                "rkrf",
                "blwf",
                "half",
                "vatu",
                "cjct",
                "akhn",
                "nukt",
                "pref",
                "pstf",
            )
        for (stage in HIRAGANA_FEATURE_PLAN.gsubStagesInOrder) {
            assertFalse(stage in joiningOrConjunctStages, "$stage should not appear in a kana plan")
        }
    }

    @Test
    fun neitherPlanIncludesMkmkBecauseDakutenAndHandakutenNeverStackOnTheSameBase() {
        assertFalse("mkmk" in HIRAGANA_FEATURE_PLAN.gposFeatures)
        assertFalse("mkmk" in KATAKANA_FEATURE_PLAN.gposFeatures)
    }

    @Test
    fun theNotesDiscloseTheRealDakutenHandakutenCompositionMechanism() {
        assertTrue(HIRAGANA_FEATURE_PLAN.notes.contains("dakuten"))
        assertTrue(HIRAGANA_FEATURE_PLAN.notes.contains("ccmp"))
        assertTrue(HIRAGANA_FEATURE_PLAN.notes.contains("mark"))
    }

    @Test
    fun theNotesDiscloseTheMissingDakutenTemplateGapHonestly() {
        assertTrue(HIRAGANA_FEATURE_PLAN.notes.contains("no dedicated dakuten/handakuten glyph template"))
    }

    @Test
    fun theNotesNameVertAsRealButNotActive() {
        assertTrue(HIRAGANA_FEATURE_PLAN.notes.contains("vert"))
        assertFalse("vert" in HIRAGANA_FEATURE_PLAN.gsubStagesInOrder)
        assertFalse("vert" in HIRAGANA_FEATURE_PLAN.gposFeatures)
    }

    @Test
    fun bothPlansShareTheIdenticalNotesText() {
        assertEquals(HIRAGANA_FEATURE_PLAN.notes, KATAKANA_FEATURE_PLAN.notes)
    }
}
