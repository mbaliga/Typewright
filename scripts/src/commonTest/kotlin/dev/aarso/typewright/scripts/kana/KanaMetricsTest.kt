package dev.aarso.typewright.scripts.kana

import dev.aarso.typewright.scripts.WritingScript
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KanaMetricsTest {
    @Test
    fun bothMetricSystemsUseTheRealOneThousandUnitsPerEm() {
        assertEquals(1000, HIRAGANA_METRICS.unitsPerEm)
        assertEquals(1000, KATAKANA_METRICS.unitsPerEm)
        assertEquals(KANA_UNITS_PER_EM, HIRAGANA_METRICS.unitsPerEm)
    }

    @Test
    fun bothMetricSystemsCarryTheirOwnScript() {
        assertEquals(WritingScript.HIRAGANA, HIRAGANA_METRICS.script)
        assertEquals(WritingScript.KATAKANA, KATAKANA_METRICS.script)
    }

    @Test
    fun neitherKanaScriptIsRightToLeft() {
        assertFalse(HIRAGANA_METRICS.rightToLeft)
        assertFalse(KATAKANA_METRICS.rightToLeft)
    }

    @Test
    fun hiraganaAndKatakanaShareTheIdenticalLineValues() {
        // "Hiragana and Katakana ... share one metric system per the research" -- this task's
        // own instruction, and the real template guides bear it out (identical across all 112
        // real files); the two ScriptMetricSystem instances differ only in `script`.
        val hiraganaValues = HIRAGANA_METRICS.lines.map { it.name to it.value }
        val katakanaValues = KATAKANA_METRICS.lines.map { it.name to it.value }
        assertEquals(hiraganaValues, katakanaValues)
    }

    @Test
    fun theFourRealLinesFromTheTemplateGuidesAreAllPresentWithTheirRealValues() {
        val byName = HIRAGANA_METRICS.lines.associateBy { it.name }
        assertEquals(4, HIRAGANA_METRICS.lines.size)
        assertEquals(0, byName.getValue("baseline").value)
        assertEquals(-120, byName.getValue("virtual body bottom").value)
        assertEquals(880, byName.getValue("virtual body top").value)
        assertEquals(380, byName.getValue("virtual body centre").value)
    }

    @Test
    fun theVirtualBodyCentreIsExactlyTheMidpointOfTopAndBottom() {
        val byName = HIRAGANA_METRICS.lines.associateBy { it.name }
        val top = byName.getValue("virtual body top").value
        val bottom = byName.getValue("virtual body bottom").value
        val centre = byName.getValue("virtual body centre").value
        assertEquals(centre, (top + bottom) / 2)
    }

    @Test
    fun onlyBaselineIsMarkedFixedAsAStructuralConstantOfTheScript() {
        // The research states plainly that how far the virtual body/letter face sits inside the
        // em "varies by design and for which no percentage is published" -- unlike Devanagari's
        // two genuinely fixed levels (headline, baseline), kana structurally has only the
        // universal font-format glyph origin (y=0) fixed; the body top/bottom/centre are this
        // template pack's own design choice, not a script-wide constant.
        val byName = HIRAGANA_METRICS.lines.associateBy { it.name }
        assertTrue(byName.getValue("baseline").fixed)
        assertFalse(byName.getValue("virtual body bottom").fixed)
        assertFalse(byName.getValue("virtual body top").fixed)
        assertFalse(byName.getValue("virtual body centre").fixed)
    }

    @Test
    fun everyLineCarriesANonBlankNoteExplainingItsSource() {
        for (line in HIRAGANA_METRICS.lines) {
            assertTrue(line.note?.isNotBlank() == true, "${line.name} should carry a sourced note")
        }
    }

    @Test
    fun theTemplateAdvanceWidthIsTheRealUniformValueMeasuredFromAllOneHundredTwelveTemplates() {
        assertEquals(720, KANA_TEMPLATE_ADVANCE_WIDTH)
    }

    @Test
    fun theVirtualBodyHeightIsExactlyOneFullEm() {
        val byName = HIRAGANA_METRICS.lines.associateBy { it.name }
        val top = byName.getValue("virtual body top").value
        val bottom = byName.getValue("virtual body bottom").value
        assertEquals(KANA_UNITS_PER_EM, top - bottom)
    }
}
