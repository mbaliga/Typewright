// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.scripts.arabic

import dev.aarso.typewright.scripts.WritingScript
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArabicScriptProfileTest {
    @Test
    fun bundlesTheRealArabicDataUnchanged() {
        val profile = ArabicScriptProfile.build()
        assertEquals(ArabicMetrics.build(), profile.metrics)
        assertEquals(ArabicGlyphInventory.build(), profile.glyphInventory)
        assertEquals(ArabicControlCharacters.build(), profile.controlCharacters)
        assertEquals(ArabicTemplateSheet.build(), profile.templateSheet)
        assertEquals(ArabicFeaturePlan.build(), profile.featurePlan)
    }

    @Test
    fun scriptIsArabicNaskhEverywhereInTheProfile() {
        val profile = ArabicScriptProfile.build()
        assertEquals(WritingScript.ARABIC_NASKH, profile.script)
        assertEquals(WritingScript.ARABIC_NASKH, profile.metrics.script)
        assertEquals(WritingScript.ARABIC_NASKH, profile.glyphInventory.script)
        assertEquals(WritingScript.ARABIC_NASKH, profile.controlCharacters.script)
        assertEquals(WritingScript.ARABIC_NASKH, profile.templateSheet.script)
        assertEquals(WritingScript.ARABIC_NASKH, profile.featurePlan.script)
    }

    @Test
    fun isRightToLeft() {
        assertTrue(WritingScript.ARABIC_NASKH.rightToLeft)
    }

    @Test
    fun isMarkedAsScaffoldPerHandoffM8sShipOrder() {
        assertTrue(ArabicScriptProfile.build().scaffold)
    }

    @Test
    fun buildIsPureAndDeterministic() {
        assertEquals(ArabicScriptProfile.build(), ArabicScriptProfile.build())
    }
}
