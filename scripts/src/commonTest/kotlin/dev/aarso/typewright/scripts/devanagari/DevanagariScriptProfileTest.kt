package dev.aarso.typewright.scripts.devanagari

import dev.aarso.typewright.scripts.WritingScript
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DevanagariScriptProfileTest {
    @Test
    fun bundlesTheRealDevanagariDataUnchanged() {
        val profile = DevanagariScriptProfile.build()
        assertEquals(DevanagariMetrics.build(), profile.metrics)
        assertEquals(DevanagariGlyphInventory.build(), profile.glyphInventory)
        assertEquals(DevanagariControlCharacters.build(), profile.controlCharacters)
        assertEquals(DevanagariTemplateSheet.build(), profile.templateSheet)
        assertEquals(DevanagariFeaturePlan.build(), profile.featurePlan)
    }

    @Test
    fun scriptIsDevanagariEverywhereInTheProfile() {
        val profile = DevanagariScriptProfile.build()
        assertEquals(WritingScript.DEVANAGARI, profile.script)
        assertEquals(WritingScript.DEVANAGARI, profile.metrics.script)
        assertEquals(WritingScript.DEVANAGARI, profile.glyphInventory.script)
        assertEquals(WritingScript.DEVANAGARI, profile.controlCharacters.script)
        assertEquals(WritingScript.DEVANAGARI, profile.templateSheet.script)
        assertEquals(WritingScript.DEVANAGARI, profile.featurePlan.script)
    }

    @Test
    fun isMarkedAsScaffoldPerHandoffM8sShipOrder() {
        assertTrue(DevanagariScriptProfile.build().scaffold)
    }

    @Test
    fun buildIsPureAndDeterministic() {
        assertEquals(DevanagariScriptProfile.build(), DevanagariScriptProfile.build())
    }
}
