package dev.aarso.typewright.scripts.kana

import dev.aarso.typewright.scripts.WritingScript
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KanaScriptProfilesTest {
    @Test
    fun bothProfilesAreMarkedScaffoldBecauseThisIsNewUnshippedData() {
        assertTrue(HIRAGANA_SCRIPT_PROFILE.scaffold)
        assertTrue(KATAKANA_SCRIPT_PROFILE.scaffold)
    }

    @Test
    fun hiraganaProfileBundlesTheRealHiraganaDataConsistently() {
        assertEquals(WritingScript.HIRAGANA, HIRAGANA_SCRIPT_PROFILE.script)
        assertEquals(HIRAGANA_METRICS, HIRAGANA_SCRIPT_PROFILE.metrics)
        assertEquals(HIRAGANA_GLYPH_INVENTORY, HIRAGANA_SCRIPT_PROFILE.glyphInventory)
        assertEquals(HIRAGANA_CONTROL_CHARACTERS, HIRAGANA_SCRIPT_PROFILE.controlCharacters)
        assertEquals(HIRAGANA_TEMPLATE_SHEET, HIRAGANA_SCRIPT_PROFILE.templateSheet)
        assertEquals(HIRAGANA_FEATURE_PLAN, HIRAGANA_SCRIPT_PROFILE.featurePlan)
    }

    @Test
    fun katakanaProfileBundlesTheRealKatakanaDataConsistently() {
        assertEquals(WritingScript.KATAKANA, KATAKANA_SCRIPT_PROFILE.script)
        assertEquals(KATAKANA_METRICS, KATAKANA_SCRIPT_PROFILE.metrics)
        assertEquals(KATAKANA_GLYPH_INVENTORY, KATAKANA_SCRIPT_PROFILE.glyphInventory)
        assertEquals(KATAKANA_CONTROL_CHARACTERS, KATAKANA_SCRIPT_PROFILE.controlCharacters)
        assertEquals(KATAKANA_TEMPLATE_SHEET, KATAKANA_SCRIPT_PROFILE.templateSheet)
        assertEquals(KATAKANA_FEATURE_PLAN, KATAKANA_SCRIPT_PROFILE.featurePlan)
    }

    @Test
    fun everyProfilesOwnScriptFieldMatchesEveryNestedPieceOwnScriptField() {
        for (profile in listOf(HIRAGANA_SCRIPT_PROFILE, KATAKANA_SCRIPT_PROFILE)) {
            assertEquals(profile.script, profile.metrics.script)
            assertEquals(profile.script, profile.glyphInventory.script)
            assertEquals(profile.script, profile.controlCharacters.script)
            assertEquals(profile.script, profile.templateSheet.script)
            assertEquals(profile.script, profile.featurePlan.script)
        }
    }
}
