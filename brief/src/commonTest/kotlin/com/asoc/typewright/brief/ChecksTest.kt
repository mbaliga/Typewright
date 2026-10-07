// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.brief

import com.asoc.typewright.core.geometry.Glyph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChecksTest {
    private fun font(vararg glyphs: Pair<Char, Glyph>) = FontView(1000, glyphs.toMap())

    @Test
    fun tabularFiguresShareOneWidth() {
        val same = font('0' to ringGlyph("zero", 600, 500, 80), '1' to barGlyph("one", 600, 80, 500))
        assertEquals(CheckStatus.PASS, UseCheck.TabularFigures.run(same).status)
        val different = font('0' to ringGlyph("zero", 600, 500, 80), '1' to barGlyph("one", 400, 80, 500))
        val result = UseCheck.TabularFigures.run(different)
        assertEquals(CheckStatus.FAIL, result.status)
        assertTrue("400 units (1)" in result.detail && "600 (0)" in result.detail, result.detail)
        assertEquals(CheckStatus.UNMEASURED, UseCheck.TabularFigures.run(font()).status)
    }

    @Test
    fun aMonospaceHasOneAdvance() {
        val mono = font('i' to barGlyph("i", 600, 80, 500), 'm' to barGlyph("m", 600, 500, 500))
        assertEquals(CheckStatus.PASS, UseCheck.FixedPitch.run(mono).status)
        val proportional = font('i' to barGlyph("i", 250, 80, 500), 'm' to barGlyph("m", 800, 500, 500))
        assertEquals(CheckStatus.FAIL, UseCheck.FixedPitch.run(proportional).status)
    }

    @Test
    fun signStrokesSitBetweenTenAndThirtyPercentOfTheHeightOfI() {
        val check = UseCheck.StrokeShareOfHeight(0.10, 0.30)
        val sturdy = check.run(font('I' to barGlyph("I", 200, 80, 700)))
        assertEquals(CheckStatus.PASS, sturdy.status)
        assertTrue("11.4%" in sturdy.detail, sturdy.detail)
        val thin = check.run(font('I' to barGlyph("I", 200, 50, 700)))
        assertEquals(CheckStatus.FAIL, thin.status)
        assertTrue("7.1%" in thin.detail && "the minimum is 10%" in thin.detail, thin.detail)
        assertEquals(CheckStatus.UNMEASURED, check.run(font()).status)
    }

    @Test
    fun signOsSitBetweenFiftyFiveAndOneHundredTenPercentOfTheHeightOfI() {
        val check = UseCheck.OWidthShareOfIHeight(0.55, 1.10)
        val i = 'I' to barGlyph("I", 200, 80, 700)
        assertEquals(CheckStatus.PASS, check.run(font(i, 'O' to ringGlyph("O", 700, 600, 80))).status)
        assertEquals(CheckStatus.FAIL, check.run(font(i, 'O' to ringGlyph("O", 400, 300, 60))).status)
    }

    @Test
    fun heavyFiguresUseUpTheAmbientBudget() {
        val check = UseCheck.AmbientLitShare()
        // 0 and 8 as rings 700 tall with 100-unit walls, 1 a 100-unit bar: about 16% of the dial.
        val heavy =
            font(
                '0' to ringGlyph("zero", 700, 700, 100),
                '8' to ringGlyph("eight", 700, 700, 100),
                '1' to barGlyph("one", 700, 100, 700),
            )
        val h = check.run(heavy)
        assertEquals(CheckStatus.FAIL, h.status)
        assertTrue("16.1%" in h.detail, h.detail)
        // 40-unit strokes: about 7%, under half the budget.
        val light =
            font(
                '0' to ringGlyph("zero", 700, 700, 40),
                '8' to ringGlyph("eight", 700, 700, 40),
                '1' to barGlyph("one", 700, 40, 700),
            )
        assertEquals(CheckStatus.PASS, check.run(light).status)
        assertEquals(CheckStatus.UNMEASURED, check.run(font('0' to ringGlyph("zero", 700, 700, 40))).status)
    }

    @Test
    fun percentagesReadTheSameOnEveryTarget() {
        assertEquals("15%", percent(0.15))
        assertEquals("7.5%", percent(0.075))
        assertEquals("70.7%", percent(0.7071))
    }
}
