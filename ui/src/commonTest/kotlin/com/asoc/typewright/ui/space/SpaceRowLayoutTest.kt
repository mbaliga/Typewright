// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.space

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [SpaceRowLayout] and [SpaceRowTarget]: how the string fits its width, and what a press on it takes hold of. */
class SpaceRowLayoutTest {
    private val font = SpaceFixtures.noho()

    @Test
    fun aShortStringIsSetAtTheBoardsFullSize() {
        val string = SpaceString.set(font, "nH")
        val fit = SpaceRowLayout.fit(string, 1000, availableDp = 600.0)
        assertEquals(SpaceRowLayout.MAX_EM_DP / 1000, fit.dpPerUnit)
        assertEquals(2, fit.shown)
        assertEquals(1200 * SpaceRowLayout.MAX_EM_DP / 1000, fit.widthDp, 1e-9)
    }

    @Test
    fun aLongerStringIsMadeSmallerToFillTheWidth() {
        val string = SpaceString.set(font, "nnonnonon")
        val fit = SpaceRowLayout.fit(string, 1000, availableDp = 250.0)
        assertEquals(9, fit.shown)
        assertTrue(fit.widthDp <= 250.5)
        assertTrue(fit.widthDp > 245.0, "it fills the width, not a part of it: ${fit.widthDp}")
        assertTrue(fit.dpPerUnit * 1000 < SpaceRowLayout.MAX_EM_DP)
    }

    @Test
    fun belowTheSmallestSizeLettersAreLeftOffTheRowRatherThanShrunkFurther() {
        val string = SpaceString.set(font, "HOHOHOHOHOHOHO")
        val fit = SpaceRowLayout.fit(string, 1000, availableDp = 300.0)
        assertEquals(SpaceRowLayout.MIN_EM_DP / 1000, fit.dpPerUnit)
        assertTrue(fit.shown in 1 until 14, "only the letters that fit are shown: ${fit.shown}")
        assertTrue(fit.widthDp <= 300.5)
    }

    @Test
    fun anEmptyStringOrNoRoomShowsNothing() {
        assertEquals(0, SpaceRowLayout.fit(SpaceString.EMPTY, 1000, 300.0).shown)
        assertEquals(0, SpaceRowLayout.fit(SpaceString.set(font, "n"), 1000, 0.0).shown)
    }

    @Test
    fun theWiderScreenSetsTheStringLarger() {
        val string = SpaceString.set(font, "nnonnonon")
        val phone = SpaceRowLayout.fit(string, 1000, 250.0).dpPerUnit
        val desktop = SpaceRowLayout.fit(string, 1000, 900.0, maxEmDp = 96.0).dpPerUnit
        assertTrue(desktop > phone)
        assertEquals(96.0 / 1000, desktop)
    }

    @Test
    fun narrowCellsStackTheirTwoSidebearings() {
        assertEquals("46 · 51", SpaceRowLayout.sidebearingLabel(46, 51, 60.0))
        assertEquals("46\n51", SpaceRowLayout.sidebearingLabel(46, 51, 30.0))
        assertEquals("46 · 51", SpaceRowLayout.sidebearingLabel(46, 51, 48.0), "seven characters need about 47 dp")
        assertEquals("46\n51", SpaceRowLayout.sidebearingLabel(46, 51, 46.0))
        assertEquals("100\n100", SpaceRowLayout.sidebearingLabel(100, 100, 55.0), "nine characters need more")
        assertEquals("—\n—", SpaceRowLayout.sidebearingLabel(null, null, 30.0))
    }

    @Test
    fun aPressNearTheSelectedLettersRightLineTakesItsRightSidebearing() {
        val string = SpaceString.set(font, "nnon")
        val dpu = 0.05
        val rightLine = string.cells[1].right * dpu
        val onTheLine = SpaceRowTarget.at(string, SpaceMode.SPACING, selected = 1, xDp = rightLine + 6.0, dpPerUnit = dpu)
        assertEquals(SpaceRowTarget(1, SpaceField.RIGHT), onTheLine)
        // The same press with another letter selected is just a press on whichever letter is there.
        val elsewhere = SpaceRowTarget.at(string, SpaceMode.SPACING, selected = 0, xDp = rightLine + 6.0, dpPerUnit = dpu)
        assertEquals(SpaceField.LEFT, elsewhere?.field)
    }

    @Test
    fun aPressOnTheBodyOfALetterTakesItsLeftSidebearingAndSelectsIt() {
        val string = SpaceString.set(font, "nnon")
        val dpu = 0.05
        val target = SpaceRowTarget.at(string, SpaceMode.SPACING, selected = 0, xDp = string.cells[2].x * dpu + 5.0, dpPerUnit = dpu)
        assertEquals(SpaceRowTarget(2, SpaceField.LEFT), target)
    }

    @Test
    fun inKerningAPressTakesTheNearestJunctionsKerning() {
        val string = SpaceString.set(font, "nnon")
        val dpu = 0.05
        val target = SpaceRowTarget.at(string, SpaceMode.KERNING, selected = 0, xDp = string.cells[2].x * dpu + 2.0, dpPerUnit = dpu)
        assertEquals(SpaceRowTarget(1, SpaceField.KERN), target)
        assertNull(SpaceRowTarget.at(SpaceString.set(font, "n"), SpaceMode.KERNING, 0, 10.0, dpu), "one letter has no pair")
        assertNotNull(SpaceRowTarget.at(string, SpaceMode.SPACING, 0, 10.0, dpu))
    }

    @Test
    fun theGrabZoneIsAFingerWide() {
        assertTrue(SpaceRowLayout.nearLine(100.0 + SpaceRowLayout.HANDLE_ZONE_DP, 100.0))
        assertTrue(!SpaceRowLayout.nearLine(100.0 + SpaceRowLayout.HANDLE_ZONE_DP + 1.0, 100.0))
        assertEquals(40.0, SpaceRowLayout.unitsAt(2.0, 0.05))
    }

    @Test
    fun aScreenReaderIsToldTheStringAndTheSelectedLettersOrPairsNumbers() {
        val kerning =
            com.asoc.typewright.core.font.ufo
                .UfoKerning(kerning = mapOf("n" to mapOf("o" to -12.0)))
        val string = SpaceString.set(SpaceFixtures.noho(kerning), "noH")
        assertEquals(
            "Control string noH, sidebearings under each letter. Selected o: left 47, right 48, advance 520",
            describeRow(string.cells, string, SpaceMode.SPACING, 1),
        )
        assertEquals(
            "Control string noH, pair values between letters. Selected pair n o: kern -12, pair",
            describeRow(string.cells, string, SpaceMode.KERNING, 0),
        )
        assertEquals("Control string noH, sidebearings under each letter", describeRow(string.cells, string, SpaceMode.SPACING, 9))
    }
}
