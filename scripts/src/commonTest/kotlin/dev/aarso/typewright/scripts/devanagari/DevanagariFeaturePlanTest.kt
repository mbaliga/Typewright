// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.scripts.devanagari

import dev.aarso.typewright.scripts.WritingScript
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DevanagariFeaturePlanTest {
    @Test
    fun gsubStageOneIsTheRealPreBaseGroupInOrder() {
        val plan = DevanagariFeaturePlan.build()
        assertEquals(
            listOf("locl", "nukt", "akhn", "rphf", "rkrf", "blwf", "half", "vatu", "cjct"),
            plan.gsubStagesInOrder.take(9),
        )
    }

    @Test
    fun gsubStageTwoIsTheRealPostBaseGroupInOrder() {
        val plan = DevanagariFeaturePlan.build()
        assertEquals(listOf("pres", "abvs", "blws", "psts", "haln", "calt"), plan.gsubStagesInOrder.drop(9))
    }

    @Test
    fun gsubHasExactlyFifteenStagesTotal() {
        assertEquals(15, DevanagariFeaturePlan.build().gsubStagesInOrder.size)
    }

    @Test
    fun pstfIsNotInTheStageListButTheAmbiguityIsDisclosedInNotes() {
        val plan = DevanagariFeaturePlan.build()
        assertFalse("pstf" in plan.gsubStagesInOrder)
        assertTrue("pstf" in plan.notes, "notes should disclose the pstf ambiguity")
        assertTrue("Microsoft omits it" in plan.notes)
        assertTrue("n8willis includes it" in plan.notes)
    }

    @Test
    fun gposIsTheRealThreeTagOrder() {
        assertEquals(listOf("kern/dist", "abvm", "blwm"), DevanagariFeaturePlan.build().gposFeatures)
    }

    @Test
    fun noStageAppearsTwice() {
        val stages = DevanagariFeaturePlan.build().gsubStagesInOrder
        assertEquals(stages.size, stages.toSet().size)
    }

    @Test
    fun notesAreNotBlank() {
        assertTrue(DevanagariFeaturePlan.build().notes.isNotBlank())
    }

    @Test
    fun scriptIsDevanagari() {
        assertEquals(WritingScript.DEVANAGARI, DevanagariFeaturePlan.build().script)
    }

    @Test
    fun buildIsPureAndDeterministic() {
        assertEquals(DevanagariFeaturePlan.build(), DevanagariFeaturePlan.build())
    }
}
