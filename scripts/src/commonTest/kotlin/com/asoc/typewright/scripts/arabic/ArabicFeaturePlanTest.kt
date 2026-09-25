// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.scripts.arabic

import com.asoc.typewright.scripts.WritingScript
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArabicFeaturePlanTest {
    @Test
    fun gsubHasTheEightAlwaysAppliedTagsInTheDocumentedOrder() {
        assertEquals(
            listOf("ccmp", "isol", "fina", "medi", "init", "rlig", "rclt", "calt"),
            ArabicFeaturePlan.gsubStagesInOrder,
        )
    }

    @Test
    fun ligaIsNotInGsubStagesInOrderItIsOnByDefaultNotAlwaysApplied() {
        assertTrue("liga" !in ArabicFeaturePlan.gsubStagesInOrder)
        assertTrue("liga" in ArabicFeaturePlan.notes, "notes should still document liga's own default-on status")
    }

    @Test
    fun gposHasTheFourDocumentedTagsInOrder() {
        assertEquals(listOf("curs", "kern", "mark", "mkmk"), ArabicFeaturePlan.gposFeatures)
    }

    @Test
    fun notesDiscloseTheLamAlefConstraint() {
        assertTrue("lam-alef" in ArabicFeaturePlan.notes)
    }

    @Test
    fun notesDiscloseTheMarkClassNonDoublingConstraint() {
        assertTrue("DIAC1" in ArabicFeaturePlan.notes && "DIAC2" in ArabicFeaturePlan.notes)
        assertTrue("doubled" in ArabicFeaturePlan.notes)
    }

    @Test
    fun scriptIsArabicNaskh() {
        assertEquals(WritingScript.ARABIC_NASKH, ArabicFeaturePlan.build().script)
    }

    @Test
    fun buildIsPureAndDeterministic() {
        assertEquals(ArabicFeaturePlan.build(), ArabicFeaturePlan.build())
    }
}
